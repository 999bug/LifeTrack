# C 包安全加固 · 设计前确认单

> 覆盖台账 `docs/PROGRESS.md` 第 4 节 C1–C10。日期：2026-11-10。
> 用户指示：C 包解禁，开始做数据库加密，列出子任务逐个完成。
> 本文件是动生产代码前的口径与风险约定；按项目惯例「先写口径 → 再改代码 → 最后实测」。

---

## 1. 现状与边界

| 项 | 当前状态 |
| --- | --- |
| 数据库 | Room `version = 2`，明文 SQLite 文件 `life_track.db`（`app/schemas/1.json` + `2.json`） |
| 备份 | `schemaVersion` 1/2 明文 JSON，SAF 导出 / 恢复，`BackupCodec` 逐字段校验 |
| 存储策略 | `android:allowBackup="false"` + `fullBackupContent` + Android 12+ `dataExtractionRules` |
| 网络 | `INTERNET` 仅用于检查更新（Gitee 优先 / GitHub 兜底），无遥测 |
| 已发布 | v0.1.0 ~ v0.5.0，用户设备上**存在明文库**，不能只做「新装加密」 |
| 版本 | 0.5.0 / versionCode 5；加密随下一个版本发布（0.6.0 / 6） |

**红线**：不擅自加中间件 / 升级依赖大版本（先给收益、代价、回滚方案）；不改写 git 历史；不删测试、不放宽断言；破坏性操作二次确认；C3 是**不可逆**的存量库搬迁，必须先备份、可回滚、不静默丢数据。

---

## 2. C1 · 加密方案选型结论

### 2.1 三个候选

| 方案 | 做法 | 收益 | 代价 | 结论 |
| --- | --- | --- | --- | --- |
| **A. SQLCipher for Android 4.20.0**（采用） | Room `openHelperFactory(SupportOpenHelperFactory(...))`，整库 AES-256 加密 | 全字段 / 索引 / WAL 都加密；DAO、SQL、实体、备份领域模型全部不用改 | 引入 native 依赖，debug APK +7.4 MiB；Keystore 密钥丢失 = 数据不可读；需处理存量明文迁移 | ✅ 采用 |
| B. 应用层字段级加密 | 敏感字段 AES-GCM 后存 TEXT | 不加 native 依赖、包体零增长 | 只覆盖部分字段；索引 / 范围查询失效；每加一个记录类型都要改加解密；备份格式要重做 | ❌ 否，达不到「数据库静态加密」 |
| C. 升 Room 3 + `SQLCipherDriver` | 用新驱动 API | 面向未来 | Room 2 → 3 属大版本升级，违反「不擅自升级依赖大版本」，当前收益为零 | ❌ 否 |

### 2.2 兼容性核查（均有出处，非推测）

| 检查项 | 结果 | 出处 |
| --- | --- | --- |
| 社区版最新版本 | `net.zetetic:sqlcipher-android:4.20.0`（2026-10-27 发布） | Maven Central `maven-metadata.xml` `<latest>4.20.0</latest>`；GitHub Release `v4.20.0` |
| 与本项目 minSdk | 上游要求 API 23+，项目 minSdk 26 ✓ | 上游 README「Compatibility」 |
| Room 版本 | 上游明确同时支持 Room 2（`SupportOpenHelperFactory`）与 Room 3，项目 2.8.5 ✓ | 上游 README「Room Integration」 |
| compileSdk | 4.18.0 起 compileSdk 37，项目 37 ✓ | Zetetic 4.18.0 发布说明 |
| AGP / Gradle | 上游仓库自身用 AGP 9.3.0 + Gradle 9.5.0，项目 9.3.1 / 9.6.1 ✓ | 上游仓库 `gradle.properties` 提交记录 |
| 16 KB 页大小 | 4.6.1 起支持；实测 4.20.0 的 `arm64-v8a` 与 `x86_64` 的 `libsqlcipher.so` PT_LOAD `p_align = 0x4000`（16 KB）✓ | Zetetic 16KB 支持公告 + 本地 ELF 解析 |
| 许可证 | Community Edition 为 **BSD-style**，**要求在应用内用户可访问的位置提供归属声明与许可证文本** | `zetetic.net/sqlcipher/license/` |

### 2.3 构建级 POC（已实测）

方法：用 `git archive HEAD` 导出到隔离目录（不碰工作区），加两行依赖并让 `AppDatabase.create` 走 `SupportOpenHelperFactory` 后跑 `:app:assembleDebug`。

- **结果：`BUILD SUCCESSFUL in 2m15s`**，`SupportOpenHelperFactory` 与 KSP / Room 2.8.5 / `androidx.sqlite:sqlite:2.7.0` 无冲突，`checkDebugAarMetadata`、`compileDebugKotlin`、`mergeDebugNativeLibs` 全过。
- 有一条非致命提示：`stripDebugDebugSymbols` 无法 strip `libsqlcipher.so`（与项目已有的 `libandroidx.graphics.path.so`、`libdatastore_shared_counter.so` 同类），按原样打包，不是错误。
- **包体增量**：基线 APK 21,187,285 B → 加密 APK 28,949,306 B，**+7,762,021 B ≈ 7.40 MiB**（debug 包、四个 ABI 全打包）。AAR 本体 4,050,842 B，`classes.jar` 134,816 B，`.so` 分别 arm64 2,110,992 / armv7 1,057,676 / x86 2,260,832 / x86_64 2,249,352 B。
- 减小包体的可选手段：按 buildType 配 `abiFilters`（debug 保留 x86_64 供仪器测试，release 只留 arm64-v8a + armeabi-v7a），预计可省约 3–4 MiB；是否采用见 D5。
- **未实测**：真机加解密性能（整库加密的理论开销需在 C10 用真机基准确认，本文不编数字）、release 包实际增量、16 KB 页设备上的运行。

### 2.4 收益 / 代价 / 回滚

- **收益**：全库静态加密，`README 安全边界`第 1 条销项；A9 / A10 高敏感模块具备上线前提；DAO / 统计 / 备份领域模型零改动。
- **代价**：native 依赖 + 包体；Keystore 密钥与设备绑定，换机只能靠加密备份恢复；存量明文迁移不可逆；生产必须关掉 SQLCipher 默认 Logcat 输出（见 C9）。
- **回滚**：
  1. 代码层：`DatabaseKeyStore` 保留一个明文回退开关（DataStore），加密链路异常时可退回原 `AppDatabase.create` 明文路径；
  2. 数据层：C3 迁移前把明文库复制为 `life_track.db.plaintext.bak`，校验通过才原子替换，失败自动还原，**备份文件至少保留到下一次成功启动之后**再清理；
  3. 发布层：加密随 0.6.0 发布，出问题只能发更高 versionCode 的回退版本（Android 不允许降级安装）；**已迁移用户无法自动退回明文库**，这是最大的不可逆点，必须真机灰度后再全量。

### 2.5 对既有台账判断的一处更正

台账 R3 / C3 原写「C3 加密迁移顺延 Room v3」。**实际不需要升 Room 版本**：明文 → 加密只换文件格式与 openHelper，schema 不变，`MIGRATION_1_2` 与 `2.json` 保持不动。若把密钥元数据放进数据库表才需要升 v3；本方案把密钥材料放 DataStore（C2），因此 **Room 保持 version 2**，少一次不可逆迁移。C3 的验收口径相应改为「文件格式迁移 + 存量数据逐字段比对」，而不是「版本升级」。

---

## 2.6 0.5.0+ 版本兼容性矩阵（C3 硬约束，用户 2026-11-10 指示）

> 用户原话：「0.5.0 以后版本都要考虑兼容性」。以下边界在 C3 起生效，任何后续改动不得破坏已发布用户的数据通路。

| 维度 | 0.1.0–0.3.0 | 0.4.0–0.5.0 | 0.6.0（C3 加密） | 0.7.0+（C4 起） |
| --- | --- | --- | --- | --- |
| 数据库文件 | 明文 SQLite，Room v1 | 明文 SQLite，Room v2 | SQLCipher 加密，**保留原 `user_version`** | 加密，Room schema 迁移照常 |
| 覆盖安装到 0.6.0 | 先做加密搬迁（保留 v1）→ Room 自动补跑 `MIGRATION_1_2` | 加密搬迁（保留 v2）→ 无需 schema 迁移 | — | — |
| 导出备份 | 明文 v1 | 明文 v2 | **仍导出明文 v2**（C4 前不改格式） | 加密 v3（需口令） |
| 恢复备份 | 读 v1 / v2 | 读 v1 / v2 | 读 v1 / v2 明文（含 0.5.0 导出的文件） | 读 v1 / v2 明文 + v3 加密 |
| 降级回 0.5.0 | — | — | **不支持**：旧版会把加密库当成损坏文件。Android 不允许降 `versionCode`，但卸载后重装旧 APK 会命中 | 不支持 |
| 卸载重装 | 数据全丢（`allowBackup=false`） | 数据全丢 | 数据全丢 + Keystore 密钥一并丢失 | 同 |
| 应用内检查更新 | — | 0.5.0 检查更新 → 下载 0.6.0 → 安装 → 首次启动迁移 | 正常 | 正常 |

**四条必须守住的约束**

1. **`user_version` 不能写死**。`sqlcipher_export` 官方文档明确「不会改变目标库的 `user_version`」，必须手动从明文库读出再写回；否则从 0.1.0 直接跳到 0.6.0 的用户会失去 `MIGRATION_1_2` 的触发条件，而写死 2 会让 v1 库被误判为已迁移。
2. **迁移必须在 Room 打开之前完成**，且明文库备份 `.bak` 一直保留到加密库首次成功读取之后才删。进程被杀 / 断电后，下次启动要能自动回滚或续做，不能停在半加密状态。
3. **C3 阶段不改备份格式**：0.5.0 与 0.6.0 的备份保持双向可读。加密备份 v3 从 C4 起引入，且新版必须永久保留读取 v1 / v2 明文备份的能力。
4. **明文 `-wal` / `-shm` 必须在替换前清理**，否则残留的明文 WAL 会被套到加密库上，表现为 `file is not a database`。

**用户可见边界**：README 必须写明「0.6.0 起数据库加密，不能回退到 0.5.0 或更早；需要回退请先导出备份」。

---

## 2.7 备份格式 v3（加密）与兼容性矩阵（C4 起生效）

> 背景：0.5.0 及更早只认识 v1 / v2 明文备份。C4 引入 v3 加密容器后，新版必须永久保留读取旧格式的能力，旧版则不可能认识新格式。

| 版本 | 导出的备份 | 能恢复的备份 |
| --- | --- | --- |
| 0.1.0–0.3.0 | v1 明文 | v1 |
| 0.4.0–0.5.0 | v2 明文 | v1 / v2 |
| 0.6.0（C3 + C4 + C5） | **默认 v3 加密**，可显式选 v2 明文；设置页可换口令（另存新文件） | v1 / v2 明文 + v3 加密 |
| 0.7.0+ | 同 0.6.0；后续只增加恢复流程打磨，不改容器 | 同 0.6.0 |

**v3 容器结构**

```json
{
  "app": "LifeTrack",
  "schemaVersion": 3,
  "exportedAt": "<ISO-8601>",
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 210000,
  "salt": "<Base64, 16 B>",
  "iv": "<Base64, 12 B>",
  "cipher": "AES-256-GCM",
  "payload": "<Base64, 密文 + 16 B GCM tag>"
}
```

- **正文是完整的 v2 明文 JSON**：解密后直接复用 `BackupCodec` 现有校验链，不维护第二套 DTO；v3 只是外壳。
- **AAD** 为 `app|schemaVersion|exportedAt|kdf|iterations|salt|iv|cipher`，改任何一个头部字段都会让 GCM 校验失败；`exportedAt` 也绑定，防止把头部时间换成别的值。
- **判定顺序**：先按 `schemaVersion == 3` 认加密容器，再要求 `payload` 非空；v3 缺 payload 属于文件损坏（`INVALID_ENVELOPE`），不是「版本不支持」。
- **不做嵌套**：解密出来的正文只接受 v1 / v2，出现 v3 直接拒绝，避免递归。
- **上限**：明文 ≤ 5 MiB（Base64 后约 6.8 MiB），导出后再校验整份文本 ≤ `BackupIo.MAX_BYTES`（8 MiB），保证「本应用导出的文件本应用一定能读回」。
- **口令策略**：每次导出 / 恢复由用户输入，不落盘、不进日志；UI 要求 ≥ 8 位并二次确认。口令遗失没有后门，这是 D2 的必然代价。
- **明文互操作**：只有需要与 0.5.0 及更早版本互换备份时才选明文导出，且必须经过红色风险确认。

四条硬约束：

1. v1 / v2 明文的读取能力**永不删除**；新增格式只能加分支，不能改旧分支。
2. 加密失败与明文校验失败必须给出不同原因；GCM 认证失败统一提示「口令错误，或文件已损坏、被改动过」，不猜是哪一种。
3. 迭代次数限定 100000–600000：低于下限等于没加固，高于上限的畸形文件可在 KDF 阶段拖死恢复流程。
4. 口令只以 `CharArray` 在内存中传递，用完立即清零；不得写入 DataStore、SharedPreferences、日志或崩溃报告。

---

## 3. 需要拍板的决策（已给推荐值）

| 编号 | 决策 | 推荐值 | 理由 |
| --- | --- | --- | --- |
| D1 | 数据库密钥是否绑定生物识别 | **不绑定**；生物识别只用于 C7 应用锁 | `setUserAuthenticationRequired(true)` 会让冷启动、后台、自动备份都要先解锁；新增指纹 / 锁屏方式变更会触发 `KeyPermanentlyInvalidatedException`，等于密钥失效 = 数据全丢 |
| D2 | 备份加密口令来源 | **导出时用户自设口令**；设备密钥加密的自动备份作为可选，不作为换机途径 | `allowBackup=false` 且 Keystore 密钥不出设备，设备密钥加密的文件换机后解不开；换机恢复必须有可记忆的口令 |
| D3 | 密钥丢失降级 | **明确报错 + 引导从加密备份恢复**；禁止静默新建空库；「清除全部数据重新开始」需二次确认 | 静默新建 = 用户看到空列表却不知道为什么，且旧数据再也找不回 |
| D4 | 应用锁默认状态 | **默认关**，设置页可开，超时可选 立即 / 1 分钟 / 5 分钟 | 不打断 0.5.0 存量用户的启动流程 |
| D5 | ABI 裁剪 | **先全保留**，C10 发布前再按实测决定 | 项目仪器测试跑 x86_64 模拟器，裁掉会失去 CI 验证能力；7.4 MiB 增量可接受 |

---

## 4. C1–C10 · 子任务逐个拆解

状态：✅ 完成 / 🔄 进行中 / ⬜ 未开始。每步「构建 + 单测 + 模拟器主路径」通过才进下一步。

### C1 加密方案选型与 POC — ✅ 已完成（本轮）

1. ✅ 调研候选方案与兼容资料（Maven Central / 上游 README / 发布说明）
2. ✅ AAR 结构、ABI、16 KB 对齐与许可证核查
3. ✅ 隔离目录构建级 POC：AGP 9.3.1 + Room 2.8.5 + SQLite 2.7.0 编译通过，包体增量实测
4. ✅ 出选型结论、收益/代价/回滚，写进本确认单

### C2 Keystore 密钥管理 — ✅ 已完成（2026-11-10，依赖 C1）

**实际交付**：`core/security/` 下 3 个类 + 1 个 JVM 单测 + 1 个仪器测试。此阶段**不接生产链路**，App 行为完全不变。

| 文件 | 作用 |
| --- | --- |
| `PassphraseEnvelope.kt` | 纯 JVM：信封格式 `lt-enc-v1:<base64 IV>:<base64 密文>`，只做结构校验（前缀 / 段数 / Base64 / IV 12 B / 密文 ≥ 口令 32 B + tag 16 B） |
| `DatabaseKeyStore.kt` | Android Keystore AES-256-GCM 主密钥（不可导出、`setUserAuthenticationRequired(false)` 见 D1、`setRandomizedEncryptionRequired(true)` 让系统生成 IV）；`sealNewPassphrase()` / `unseal()` / `hasKey()` |
| `DatabaseKeyRepository.kt` | 信封持久化 + `Mutex` 串行化的「取或创建口令」，避免并发生成两份互不相认的口令；`wipe()` 由调用方用完清零 |

- **D3 落地**：失败原因分 4 类（`KEY_MISSING` / `INVALID_ENVELOPE` / `DECRYPT_FAILED` / `KEYSTORE_ERROR`），全部抛 `KeyUnavailableException`，**不静默新建空库**。用户可见文案留到 C6/C7 接入 UI 时写。
- **与初版计划的两处偏差**（如实记录）：① 没有复用 `SettingsRepository`，改用独立 DataStore `life_track_security`，让密钥材料与主题设置隔离，便于 C9 隐私审查；② 没有单独的 `KeyUnavailableException.kt`，改为 `DatabaseKeyStore` 的嵌套类，减少一次跨文件跳转。
- **验证**：`testDebugUnitTest` **147 项全绿**（新增 `PassphraseEnvelopeTest` 6 项：往返、8 类畸形信封拒绝、参数校验、明文不泄漏、GCM 篡改与错误密钥检测）；`lintDebug` 无 error；`assembleDebug` 与 `assembleDebugAndroidTest` 均 BUILD SUCCESSFUL。
- **未验证**：`DatabaseKeyStoreTest` 5 项仪器测试（真实 Keystore 生成 / 解封 / 篡改 / 密钥缺失）只编译通过，**尚未在设备上实跑**，随 C3 的模拟器验证一起执行；`adb shell run-as` 检查偏好文件只有密文、应用重启后口令仍可读，也一并留到 C3。
- 一个实现细节：口令长度常量放在纯 JVM 的 `PassphraseEnvelope`，避免 JVM 单测加载引用了 `android.security.keystore` 的类。

### C3 存量明文库迁移 — ✅ 已完成（2026-11-10，依赖 C1 C2）

**交付**：`app/build.gradle.kts` 两行依赖、`core/database/SqliteFileHeader.kt`、`core/database/DatabaseEncryption.kt`；`AppDatabase.create` 走 `SupportOpenHelperFactory`，`LifeTrackApplication` 编排启动顺序，`MainActivity` + `DatabaseUnavailableScreen` 处理密钥失效。

| 关键点 | 实现 |
| --- | --- |
| 方向 | `ATTACH DATABASE '<明文>' AS plaintext KEY ''` + `SELECT sqlcipher_export('main','plaintext')`，**第一个参数是目标、第二个是源**（官方文档 Example 5 核实） |
| `user_version` | `sqlcipher_export` 官方明确不复制；从明文库读出后 `setVersion()` 写回加密库。Room v1 库保持 1，v2 库保持 2，后续 `MIGRATION_1_2` 照常触发 |
| WAL | 迁移前 `PRAGMA wal_checkpoint(TRUNCATE)`，备份主库 + `-wal` + `-shm`；替换后删除明文的 `-wal` / `-shm` / `-journal` |
| 原子性 | `dbFile → .swap → .encrypted.tmp → dbFile` 两次 rename；导出阶段失败不动原文件，替换阶段失败自动 `restore()`；原路径缺失但有 `.bak` 时自动恢复并重做（`recovered=true`） |
| 校验 | 导出前后逐表比对行数（v1 缺 `event_detail` 记 -1，两侧一致即可）+ `user_version`，不一致即回滚抛错 |
| 备份清理 | `.plaintext.bak` 保留到加密库首次 DAO 读取成功后，由 `confirmEncryptionIfNeeded()` 删除 |
| 降级 | 密钥失效 → `DatabaseState.Unavailable`，显示终止页，**不新建空库**；迁移失败且明文已还原 → `createPlaintextFallback()` 保证存量用户仍可用，并标 `degraded` |
| 日志 | 生产构建设 `Logger.setTarget(NoopTarget())`（C9 的日志项提前落地）；启动只记录版本与行数，不含记录内容或密钥 |

**实测（模拟器 `Medium_Phone_API_37.0`）**

- `connectedDebugAndroidTest`：**14 项全绿**（原有 5 + C2 密钥 5 + C3 迁移 4），覆盖 v2 迁移行数/字段保真、v1 保留 `user_version=1`、中断恢复、错误口令打不开、Keystore 生成/解封/篡改/缺失。
- **真实覆盖安装兼容性验证**：备份模拟器上 0.4.0 的明文 Room v2 库（主库 61,440 B + WAL 16,512 B，WAL 内含一条只写在 WAL 里的记录），卸载后装新 APK、还原明文库、冷启动。日志 `Migrated(originalVersion=2, rowCounts={track_type=4, track_event=3, poop_detail=1, event_detail=1}, recovered=false)` —— **WAL 里的第 3 条记录保住了**，`user_version=2` 保留；主库头变为随机密文；`.plaintext.bak` 已清理；无崩溃。
- 强停后重启：`AlreadyEncrypted(needsConfirmation=false)`，密钥从 Keystore 正常恢复，无崩溃。
- 单测 **151 项全绿**（新增 `SqliteFileHeaderTest` 4 项）；`lintDebug` 无 error；`assembleDebug` / `assembleDebugAndroidTest` 通过；debug APK 29,748,054 B。

**未验证**：真机（用户自己的设备）0.5.0 → 0.6.0 覆盖安装，需发版后由用户确认；磁盘写满 / 迁移中途断电的失败注入只做了代码路径与「原路径缺失 + 备份存在」的恢复用例，未做物理断电；大库迁移耗时与 ANR 风险未压测（本次冷启动含迁移约 4.3 s）。

**版本边界**：C3 阶段**未改备份格式**，0.5.0 与当前 main 的备份仍双向可读；加密改造尚未升版本号、未发版，正式发布为 0.6.0 / versionCode 6。

### C4 导出文件加密 — ✅ 已完成（2026-11-10，依赖 C2 A5）

**交付**：`core/security/BackupCrypto.kt`、`core/backup/BackupEnvelope.kt`；`BackupCodec` 增加 v3 编解码，`MainViewModel` 透传口令，`SettingsScreen` 增加导出方式选择、口令输入与恢复口令弹窗。

| 关键点 | 实现 |
| --- | --- |
| 容器 | v3 外层 JSON：`app` / `schemaVersion` / `exportedAt` / `kdf` / `iterations` / `salt` / `iv` / `cipher` / `payload`；正文仍是完整 v2 明文 JSON，解密后走原校验链 |
| KDF | `PBKDF2WithHmacSHA256`，210000 次（允许 100000–600000），随机盐 16 B；256 位密钥 |
| 加密 | `AES/GCM/NoPadding`，随机 IV 12 B，128 位 tag；头部全部字段参与 AAD，`exportedAt` 也绑定 |
| 错误 | 新增 `WRONG_PASSPHRASE` / `INVALID_ENVELOPE` / `UNSUPPORTED_ENCRYPTION` / `EMPTY_PASSPHRASE` / `BACKUP_TOO_LARGE`；GCM 失败不区分口令错与篡改 |
| 导出 UI | 默认「加密（推荐）」，口令 ≥ 8 位并二次确认；选「明文」需过红色风险确认；取消 SAF 时清零口令 |
| 恢复 UI | 读到 v3 先返回 `PassphraseRequired`，弹口令框；错误口令在框内提示且不清空输入；v1 / v2 明文不询问口令 |
| 上限 | 明文 ≤ 5 MiB；整份加密文本再校验 ≤ `BackupIo.MAX_BYTES`（8 MiB），避免自产文件读不回 |
| 口令生命周期 | 只用 `CharArray` 传递，用后 `fill('\u0000')`；不落盘、不写日志、不进崩溃信息 |

**与计划的偏差（如实记录）**：C4 一并把恢复端的口令输入做了最小可用版本（否则「能导出、不能恢复」等于制造数据丢失）；C6 仍保留强度提示、重试节流与状态文案打磨。C5 的「AAD 覆盖头部」在 C4 已提前落地，C5 只剩口令轮换与交叉恢复用例。

**验证（模拟器 `Medium_Phone_API_37.0`）**

- 单测 **169 项全绿**（新增 18 项：`BackupCryptoTest` 7 + `BackupEncryptedCodecTest` 11），覆盖往返、错误口令、密文篡改、AAD 篡改、密文截断、参数越界、超大明文、明文 v2 兼容、v3 缺 payload、迭代次数越界在 KDF 前拒绝。
- `lintDebug` 通过，9 条 warning 均为既有版本 / 图标提示，无新增；`installDebug` 成功。
- 加密导出实测：3 条记录 → 4,190 B 文件，头部 `schemaVersion=3` / `iterations=210000` / `AES-256-GCM`，正文与备注明文均未出现；状态提示「已导出 3 条加密记录」。
- 加密恢复实测：错误口令 → 提示「口令错误，或备份文件已损坏、被改动过」；正确口令 → 确认框「备份包含 3 条，当前 4 条」→ 恢复成功，记录数回到 3。
- 明文导出与恢复实测：风险确认 → 2.92 kB v2 文件；导入时不询问口令，直接进入恢复确认。
- 200% 字体：导出对话框内容完整、按钮可见（内容可滚动）；深色模式：对话框与输入框对比度正常，无 M3 默认紫色。

**未验证**：真机上的 PBKDF2 210000 次派生耗时与恢复体验（模拟器可完成但未计时）；> 5 MiB 大备份只做了单测拒绝，未用真实大库实测；中文 / 表情口令只做了 JVM 往返，未验证 Android 与 JVM 的 `PBEKeySpec` 编码一致性（发版前用真机补一次跨端恢复）；口令遗忘后的恢复路径按设计不存在。

### C5 完整性校验与密钥轮换 — ✅ 已完成（2026-11-10，依赖 C4）

> C4 已落地 GCM tag + AAD 覆盖头部（原第 1 项），本项只剩口令轮换与交叉恢复。

**交付**：`BackupCodec.reencrypt` + `BackupReencryptResult`、`MainViewModel.reencryptBackup`、设置页「更换备份口令」入口与三段口令对话框。

| 关键点 | 实现 |
| --- | --- |
| 完整性 | GCM 128 位 tag 覆盖正文，AAD 覆盖 `app / schemaVersion / exportedAt / kdf / iterations / salt / iv / cipher`；改盐、改导出时间、改密文都会在解密时被拒 |
| 轮换流程 | 旧口令解出完整 payload → 新口令重新编码（新盐、新 IV、新 `exportedAt`）→ **立刻用新口令解回并逐字段比对** → 返回完整新文件文本 |
| 落盘策略 | 通过 SAF 的 `CreateDocument` 另存为 `<原名>-rekeyed.json`，**原文件不删不改**；文案明确「确认新文件能恢复后再删除旧文件」 |
| 拒绝场景 | 明文备份（`NOT_ENCRYPTED_BACKUP`）、错误旧口令（`WRONG_PASSPHRASE`）、损坏容器（`INVALID_ENVELOPE`）、新口令过短或与旧口令相同（UI 拦截） |
| 口令生命周期 | 旧 / 新口令都以 `CharArray` 传入核心层，调用方在 `finally` 清零；核心层不落盘、不记日志 |

**与计划的偏差（如实记录）**：原计划写「解旧 → 重加密 → 原子替换」。SAF 的 document URI 没有原子 rename，就地 `wt` 覆盖在进程被杀 / 磁盘写满时会留下半截文件，且无法回滚。改为**另存新文件**：旧备份始终完好，用户验证新文件可恢复后再自行删除，数据安全性高于「原地替换」的字面要求。

**验证**

- 单测 **176 项全绿**（新增 `BackupRekeyTest` 7 项）：轮换后旧口令失效 / 新口令可解且字段保真、错误旧口令、明文备份拒绝、损坏容器拒绝、盐 / IV / 密文全部更换、篡改盐与导出时间被拒。
- 模拟器实测：用 `OldPass-2026` 导出加密备份（4,190 B）→ 更换口令时先输错旧口令，对话框内提示「口令错误…」且不关闭 → 改对后生成 `...-rekeyed.json`（同为 4,190 B，盐 / IV / 密文均不同）→ 新文件用 `NewPass-2026` 可进入恢复确认；旧文件仍可用 `OldPass-2026` 恢复；旧文件用新口令被拒。
- `lintDebug` 无新增问题、`assembleDebug` / `installDebug` 通过。

**未验证**：真机耗时（一次轮换含两次 PBKDF2 派生 + 一次自检解密，模拟器可完成但未计时）；> 5 MiB 大备份的轮换（受同一 5 MiB 明文上限约束，超限返回 `BACKUP_TOO_LARGE`，未用真实大库实测）。

### C6 加密备份恢复流程 — ✅ 已完成（2026-11-10，依赖 C4 C5）

> C4 已交付口令输入 / 确认、错误原因映射与明文风险确认；C6 补齐强度提示、失败节流与文案复核。

**交付**：`core/security/PassphraseQuality.kt`、`core/security/RetryBackoff.kt`；设置页导出、恢复、轮换三处口令框统一接入。

| 关键点 | 实现 |
| --- | --- |
| 强度提示 | 分数 = 长度 + 2 × 字符种类（小写 / 大写 / 数字 / 符号 / 非 ASCII）；<13 偏弱、13–17 可用、≥18 较强；空输入只显示规则，不预先判弱 |
| 中入口令 | 非 ASCII 记一类，长度按字符数算，12 字中文口令落在「可用」，不会被误判成弱口令 |
| 最小长度 | 单一口径 `PassphraseQuality.MIN_LENGTH = 8`，`BackupCodec` 引用它，避免两处常量漂移 |
| 重试节流 | 前 2 次失败不等待，第 3 次起 2s → 4s → 8s → 16s → 30s；恢复与轮换各自独立计数 |
| 交互 | 冷却期间确认按钮置灰，文案变「请等待 N 秒」并逐秒递减；成功、重新打开文件、取消弹窗都会清零计数 |
| 错误映射 | 口令错误统一提示「口令错误，或文件已损坏、被改动过；口令区分大小写和全半角」，结构类错误走 `INVALID_ENVELOPE` / `UNSUPPORTED_ENCRYPTION` |
| 明文警告 | C4 已改为导出前红色风险确认 + 设置页底注改写；本轮复核文案，无遗留「备份不加密」的旧表述 |

**偏差说明**：节流只作用于 UI，不是密码学防线——拿到文件的人仍可离线无限次尝试，真正的成本来自 PBKDF2 210000 次迭代。代码注释与文档都按这个事实写，不夸大。

**验证**

- 单测 **185 项全绿**（新增 `PassphrasePolicyTest` 9 项：四档边界、中英文口令、节流序列与秒数取整）。
- 模拟器实测：导出对话框输入 `abcdefgh` 显示「偏弱：再长一些…」，追加 `1234XYZ` 后变「较强」；恢复时连续输错，第 4 次触发「请等待 3 秒」且按钮节点 `enabled=false`，倒计时结束后恢复为「解密」；轮换对话框新口令输入 `abc12345` 同样显示偏弱提示。
- `lintDebug` 无新增问题、`assembleDebug` / `installDebug` 通过；200% 字体与深色模式沿用 C4 的对话框布局，未出现截断。

**未验证**：真机上单次 PBKDF2 派生耗时对冷却体验的影响（模拟器可完成但未计时）；节流对脚本化点击的实际拦截效果未做自动化压测。

### C7 应用锁 + 预览遮蔽 — ✅ 已完成（2026-11-10，依赖 C2）

**交付**：`core/model/AppLockTimeout.kt`、`core/security/AppLockPolicy.kt`、`feature/lock/AppLockAuthenticator.kt`、`feature/lock/AppLockGate.kt`；`SettingsRepository` 增加开关与超时，`MainViewModel` 持有锁状态，`MainActivity` 绑定 `FLAG_SECURE`，设置页新增「安全」分区。

| 关键点 | 实现 |
| --- | --- |
| 认证 | API 30+ 用平台 `BiometricPrompt`，`BIOMETRIC_WEAK + DEVICE_CREDENTIAL` 合并成一个系统弹窗；API 26–29 用 `KeyguardManager` 系统凭据页兜底（平台旧版不支持把设备凭据并进同一弹窗） |
| 默认与超时 | 默认关（D4）；可选立即 / 1 分钟 / 5 分钟，立即为默认值 |
| 锁状态 | ViewModel 持有 `Boolean?`：null = 设置未读出，true = 锁定。冷启动 / 进程被杀后只要开关是开的就先锁；开关关闭立即解除 |
| 生命周期 | `ON_STOP` 记后台时间，`ON_START` 按策略判定；读设置前拿到 null 时不解锁（这条曾导致冷启动绕过，已修） |
| 预览遮蔽 | 开关开启即给 Activity 加 `FLAG_SECURE`，关闭即清除；最近任务与截屏都拿不到内容 |
| 锁屏实现 | 全屏 `Dialog` 盖住正文，同时给正文 `clearAndSetSemantics {}`：视觉挡住、读屏读不到；正文里的 `AlertDialog` 是独立窗口，普通遮罩压不住，后创建的 Dialog 天然在上层 |
| 不销毁正文 | 正文保持 composition，避免 `rememberCoroutineScope` 被取消导致 SAF 导出写到一半；用户解锁后看到的是原页面与原状态 |
| 返回键 | 锁屏期间 `BackHandler` 拦截，避免在系统验证弹窗上按返回把 Activity 直接结束 |
| 兜底 | 设备锁屏被移除时无法验证，锁屏页给出明确文案与「关闭应用锁并进入」，避免把自己永久锁在门外 |
| 无障碍 | 锁屏只暴露「生活记 / 应用已锁定 / 解锁」等 4 个节点；uiautomator 验证锁定状态下读不到设置页任何文本 |

**偏差说明**

1. **未引入 `androidx.biometric`**：稳定版仍是 2021 年的 1.1.0，新版只有 beta；平台 `BiometricPrompt` 在 API 30+ 已能满足「生物识别 + 设备凭据」合并弹窗，API 30 以下用系统凭据页，不额外增加依赖。
2. **锁屏用 Dialog 而不是销毁正文**：安全性上「销毁」更彻底，但会取消 SAF 流程中的协程，可能把导出文件写成半截；Dialog + 语义清除在保证不可见 / 不可读的同时保住进行中的写盘。
3. **FLAG_SECURE 的代价**：开启应用锁后，`adb screencap` 与系统截屏都会得到黑图，锁屏页的深色 / 浅色只能靠控件树与主题取值核对，无法截图留证。
4. **生命周期用 `ON_STOP` / `ON_START`，不是 `onPause` / `onResume`**：`onPause` 会被权限弹窗、SAF 选择器等短暂遮挡触发，按「立即」策略会在用户选文件时反复上锁；`ON_STOP` 才代表真正离开前台。代价是下拉通知栏等「部分遮挡」不会上锁，这是有意取舍。

**验证（模拟器 `Medium_Phone_API_37.0`，设备 PIN 1234）**

- 单测 **191 项全绿**（新增 `AppLockPolicyTest` 6 项：立即 / 1 分钟 / 5 分钟边界、缺后台时间、时钟回拨、超时映射）。
- 开启：设置页「安全 → 应用锁」打开后状态变「已开启」，出现「自动锁定」行；`dumpsys window` 中 Activity 窗口 `fl` 含 `SECURE`。
- 冷启动 / 强停后重开：先出系统「解锁生活记」凭据弹窗，取消后停留在锁屏页；输入 PIN 后回到原页面。锁屏页 uiautomator 只有 4 个文本节点，`导出备份` 等正文文本数量为 0。
- 超时：切后台 3 秒回来（1 分钟策略）不锁定；改回「立即」后切后台再回来进入锁屏；返回键被拦截，不会退出 Activity。
- 关锁：清除设备 PIN 后点「解锁」，出现「系统锁屏已关闭…」与「关闭应用锁并进入」；点击后回到正文，设置变「未开启」，窗口 `SECURE` 标志被清除。
- 200% 字体：锁屏标题 / 说明 / 解锁按钮均在屏内，无重叠（标题 y 956–1099，按钮 y 1441–1540）。
- `lintDebug` 无新增问题、`assembleDebug` / `installDebug` 通过；首轮漏加 `USE_BIOMETRIC` 导致 `canAuthenticate` 抛 `SecurityException`，已补权限并复测通过。

**未验证**：真机指纹 / 面容（模拟器只有设备凭据路径）；API 26–29 的 Keyguard 分支只有代码路径，未在旧系统实跑；深色模式锁屏的视觉截图（FLAG_SECURE 全黑）；旋转 / 分屏下的锁状态与 TalkBack 实机朗读。

### C8 备份 / 迁移 / ROM 评审 — ⬜（依赖 C3，与 B8 合并）

1. 复核 `allowBackup=false` + `fullBackupContent` + `dataExtractionRules` 对加密库 + Keystore 的组合影响
2. 真机走查：设备迁移、换机恢复（用加密备份）、卸载重装、厂商 ROM 差异
3. 结论写进 README 安全边界；台账 B8 / R1 同步销项

### C9 隐私审查 — ⬜（依赖 C7）

1. **SQLCipher 默认把日志送 Logcat** → 生产构建设 `Logger.setTarget(NoopTarget())`（上游 README 明确）
2. 审查崩溃报告、剪贴板、通知内容、最近任务预览、临时文件与 `.plaintext.bak` 清理
3. 确认 `INTERNET` 仍只用于检查更新，无遥测；确认备份口令不进日志

### C10 发布前安全回归 — ⬜（依赖 C1–C9）

1. 全链路：141+ 项单测、仪器测试、lint、`assembleRelease`、真机、深浅色 / 200% 字体 / 宽屏
2. **许可证义务**：设置页「关于」加用户可访问的 SQLCipher Community Edition 归属与许可证文本入口，README 补第三方许可章节（BSD-style 要求，见 §2.2）
3. README「安全边界」从「没有实现数据库加密」改写为「已实现 + 边界说明」，5 项评审逐条销项；`project-profile.md §9` 补雷区
4. 版本号 0.6.0 / versionCode 6，发布前真机灰度、记录回滚路径

---

## 5. 预计影响的文件（动代码前先列）

| 类型 | 路径 |
| --- | --- |
| 构建 | `app/build.gradle.kts`（2 行依赖；D5 决定后可能加 `abiFilters`） |
| 数据库 | `core/database/AppDatabase.kt`、`LifeTrackApplication.kt`；新增 `core/database/DatabaseEncryption.kt` |
| 新增安全模块 | `core/security/DatabaseKeyStore.kt`、`DatabasePassphrase.kt`、`KeyUnavailableException.kt`、`core/security/BackupCrypto.kt`、`core/backup/BackupEnvelope.kt` |
| 备份 | `core/backup/BackupCodec.kt`、`BackupPayload.kt`、`BackupIo.kt` |
| UI / 状态 | `MainViewModel.kt`（导出恢复）、`SettingsScreen.kt`（应用锁、口令、明文警告、许可证入口） |
| 测试 | `app/src/test/` 新增加密与 envelope 单测；`app/src/androidTest/` 新增 Keystore 与迁移测试 |
| 文档 | `README.md`、`docs/PROGRESS.md`、`docs/release-guide.md`（版本号）、`.agents/project-profile.md §9` |
| 可能 | `.github/workflows/*.yml`（release 包的 native 库 16 KB 对齐校验） |

**不改动**：Room 实体 / DAO / `MIGRATION_1_2` / `app/schemas/`、统计与首页口径、更新链路（Gitee / GitHub）。

---

## 6. 顺序与验收

```
C1 ✅ → C2 ✅ → C3 ✅（⚠️ 真机灰度待发版）→ C4 ✅ → C5 ✅ → C6 ✅ → C7 ✅ → C8（合 B8）→ C9 → C10 → 发 0.6.0
                                    └─ C1–C3 完成才解禁 A9 / A10
```

- 一子任务一交付：构建 + 测试通过 → 更新台账 → 单独提交（只 add 具体路径，不 push）。
- C3 之后每个新模块合并都要重跑 B3–B6 的回归（200% 字体、深浅色、横屏、读屏语义）。
- C1–C3 完成前，A9 月经 / A10 亲密关系模块继续保持 ⛔。
