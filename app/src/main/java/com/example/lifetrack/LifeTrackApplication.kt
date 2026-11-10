package com.example.lifetrack

import android.app.Application
import android.util.Log
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.DEFAULT_POOP_TYPE
import com.example.lifetrack.core.database.DatabaseEncryption
import com.example.lifetrack.core.datastore.SettingsRepository
import com.example.lifetrack.core.security.DatabaseKeyRepository
import com.example.lifetrack.core.security.DatabaseKeyStore
import com.example.lifetrack.core.update.UpdateRepository
import com.example.lifetrack.data.repository.RoomTrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

class LifeTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.databaseState !is AppContainer.DatabaseState.Ready) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // 加密库首次成功读取之后才清掉明文备份；DAO 抛异常时备份会继续保留。
            container.database.trackTypeDao().insertIgnore(DEFAULT_POOP_TYPE)
            container.confirmEncryptionIfNeeded()
        }
    }
}

class AppContainer(private val application: Application) {

    sealed interface DatabaseState {
        /** [degraded] 为 true 表示迁移失败后已回退到明文，加密目标本包未达成。 */
        data class Ready(val database: AppDatabase, val degraded: Boolean) : DatabaseState

        /** 密钥不可用：不新建空库，由 UI 给出明确指引。 */
        data class Unavailable(val failure: DatabaseKeyStore.Failure) : DatabaseState
    }

    private val keyRepository = DatabaseKeyRepository(application)
    private val encryption = DatabaseEncryption(application)
    private var confirmationPending = false
    private var degraded = false

    val databaseState: DatabaseState = openDatabase()

    val database: AppDatabase
        get() = (databaseState as? DatabaseState.Ready)?.database
            ?: error("database is unavailable")

    val trackRepository by lazy { RoomTrackRepository(database) }
    val settingsRepository = SettingsRepository(application)
    val updateRepository = UpdateRepository(application)

    fun confirmEncryptionIfNeeded() {
        if (confirmationPending && !degraded) {
            encryption.confirmMigration(AppDatabase.NAME)
            confirmationPending = false
        }
    }

    private fun openDatabase(): DatabaseState {
        System.loadLibrary("sqlcipher")
        // SQLCipher 默认把日志送 Logcat，生产构建关掉（C9 隐私审查项提前落地）。
        if (!BuildConfig.DEBUG) Logger.setTarget(NoopTarget())

        val passphrase = try {
            runBlocking { keyRepository.getOrCreatePassphrase(DatabaseKeyStore()) }
        } catch (e: DatabaseKeyStore.KeyUnavailableException) {
            Log.e(TAG, "database key unavailable: ${e.failure}")
            return DatabaseState.Unavailable(e.failure)
        }

        val outcome = try {
            encryption.migrateIfNeeded(AppDatabase.NAME, passphrase)
        } catch (e: DatabaseEncryption.MigrationException) {
            // migrateIfNeeded 内部已还原明文；用明文兜底保证存量用户仍能打开 App、不丢数据。
            degraded = true
            Log.e(TAG, "encryption migration failed, plaintext restored: ${e.message}")
            return DatabaseState.Ready(AppDatabase.createPlaintextFallback(application), degraded = true)
        }

        confirmationPending = outcome.needsConfirmation
        // 只记录版本与表行数，不含任何记录内容或密钥材料。
        Log.i(TAG, "database ready: $outcome")
        return DatabaseState.Ready(AppDatabase.create(application, passphrase), degraded = false)
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
