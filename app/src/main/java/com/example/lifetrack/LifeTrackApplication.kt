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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

class LifeTrackApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 数据库的实际打开在 AppContainer 的后台协程里进行：读 Keystore、解封口令、
        // 必要时整库加密迁移，都不能压在主线程上。这里只创建容器，立即返回。
        container = AppContainer(this)
    }
}

class AppContainer(private val application: Application) {

    sealed interface DatabaseState {
        /** 正在后台打开数据库；UI 只显示启动占位，不渲染任何依赖数据库的界面。 */
        object Loading : DatabaseState

        /** [degraded] 为 true 表示迁移失败后已回退到明文，加密目标本包未达成。 */
        data class Ready(val database: AppDatabase, val degraded: Boolean) : DatabaseState

        /** 密钥不可用：不新建空库，由 UI 给出明确指引。 */
        data class Unavailable(val failure: DatabaseKeyStore.Failure) : DatabaseState
    }

    private val keyRepository = DatabaseKeyRepository(application)
    private val encryption = DatabaseEncryption(application)
    private var confirmationPending = false
    private var degraded = false

    private val _databaseState = MutableStateFlow<DatabaseState>(DatabaseState.Loading)

    /** 与进程同生命周期：打开数据库的过程不能被界面生命周期打断。 */
    val databaseState: StateFlow<DatabaseState> = _databaseState.asStateFlow()

    private val openScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        openScope.launch { openAndPublish() }
    }

    val database: AppDatabase
        get() = (_databaseState.value as? DatabaseState.Ready)?.database
            ?: error("database is unavailable")

    val trackRepository by lazy { RoomTrackRepository(database) }
    val settingsRepository = SettingsRepository(application)
    val updateRepository = UpdateRepository(application, settingsRepository)

    /**
     * 打开数据库并发布状态。整段跑在 IO 线程：Keystore 解封在部分机型要几十到几百毫秒，
     * 老用户升级首启还要整库复制加密，放在 Application.onCreate 的主线程会直接拖慢首帧。
     */
    private suspend fun openAndPublish() {
        val state = try {
            openDatabase()
        } catch (t: Throwable) {
            // 兜底：意料外的初始化异常不能把界面永久停在 Loading。
            Log.e(TAG, "database open failed unexpectedly", t)
            DatabaseState.Unavailable(DatabaseKeyStore.Failure.KEYSTORE_ERROR)
        }
        _databaseState.value = state
        if (state !is DatabaseState.Ready) return

        // 加密库首次成功读取之后才清掉明文备份；DAO 抛异常时备份会继续保留。
        try {
            state.database.trackTypeDao().insertIgnore(DEFAULT_POOP_TYPE)
        } catch (t: Throwable) {
            Log.e(TAG, "seed default type failed", t)
            return
        }
        confirmEncryptionIfNeeded()
    }

    private fun confirmEncryptionIfNeeded() {
        if (confirmationPending && !degraded) {
            encryption.confirmMigration(AppDatabase.NAME)
            confirmationPending = false
        }
    }

    private suspend fun openDatabase(): DatabaseState {
        System.loadLibrary("sqlcipher")
        // SQLCipher 默认把日志送 Logcat，生产构建关掉（C9 隐私审查项提前落地）。
        if (!BuildConfig.DEBUG) Logger.setTarget(NoopTarget())

        val passphrase = try {
            keyRepository.getOrCreatePassphrase(DatabaseKeyStore())
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
