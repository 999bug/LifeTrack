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

### C2 Keystore 密钥管理 — ⬜（依赖 C1）

1. `core/security/DatabaseKeyStore.kt`：Android Keystore 生成 AES-256-GCM 主密钥（别名固定、不可导出、`setUserAuthenticationRequired(false)`，见 D1）
2. `core/security/DatabasePassphrase.kt`：随机 32 字节数据库口令 + 主密钥封装后的 envelope（Base64），密文写入现有 `SettingsRepository`（DataStore），**绝不落明文**
3. `core/security/KeyUnavailableException.kt` 与用户可见错误文案（D3）
4. 单测：envelope 编解码往返、篡改检测；仪器测试：密钥生成 → 封装 → 解封 → 应用重启后仍可读
- **验收**：设备文件里 grep 不到口令明文；密钥失效时报错而不是打开一个空库；`adb shell run-as` 拉取的偏好文件只有密文。

### C3 存量明文库迁移 — ⬜（依赖 C1 C2，⚠️ 不可逆）

1. `core/database/DatabaseEncryption.kt`：检测文件头 `SQLite format 3\0` 判断明文；`ATTACH ... KEY` + `sqlcipher_export` 导出到临时加密库
2. 明文库备份 → 迁移 → 逐表逐字段校验（条数、UUID、字段值）→ 原子替换 → 下一次成功启动后才清理备份；任何一步失败自动还原
3. `AppDatabase.create` 接入 `SupportOpenHelperFactory`，**Room 保持 version 2**（见 §2.5）；`LifeTrackApplication` 启动顺序调整
4. 仪器测试：构造 v2 明文库 → 迁移 → 数据比对 → 用口令重开；失败注入（磁盘写满 / 中途中断）验证回滚
5. 真机 0.5.0 → 0.6.0 覆盖安装验证（需用户配合）
- **验收**：升级不卸载、不丢数据；迁移失败时 App 仍能打开原明文库；无「恢复成功但数据没了」的路径。

### C4 导出文件加密 — ⬜（依赖 C2 A5）

1. 新格式 envelope（建议 `schemaVersion = 3` 或外层包装，须与 v1/v2 白名单共存）：KDF 参数、salt、IV、密文、AAD 绑定文件头
2. `PBKDF2WithHmacSHA256`（迭代 ≥ 210000，salt 16 B 随机）+ `AES-256-GCM`（IV 12 B 随机），口令不落盘
3. 兼容：仍可恢复 v1/v2 明文备份；导出默认加密，明文导出必须二次确认
4. 单测：错误口令、密文篡改、文件截断、旧格式兼容、超大文件拒绝（`BackupIo.MAX_BYTES` 仍生效）

### C5 完整性校验与密钥轮换 — ⬜（依赖 C4）

1. GCM tag + AAD 覆盖头部，任何字节改动都能检出并给出明确原因
2. 设置页「更换备份口令」：解旧 → 用新口令重加密 → 原子替换；旧备份仍可用旧口令恢复
3. 单测：轮换前后交叉恢复、篡改头字段被拒

### C6 加密备份恢复流程 — ⬜（依赖 C4 C5）

1. 导出 / 恢复 UI：口令输入与确认、强度提示、错误原因映射、重试节流（指数退避）
2. 明文导出的二次确认 + 红色风险提示；`SettingsScreen` 现有明文警告文案改写
3. README「本地存储 / 安全边界」同步；`MainViewModel.buildBackupJson` / `decodeBackupJson` 接入新编解码

### C7 应用锁 + 预览遮蔽 — ⬜（依赖 C2）

1. `BiometricPrompt` + 设备凭据兜底，默认关（D4），超时策略即时 / 1 分钟 / 5 分钟
2. 锁屏开启时 `FLAG_SECURE` 遮住最近任务预览；进程被杀后重新验证
3. 生命周期：`onPause` 记时、`onResume` 判定；200% 字体 / 深浅色回归
4. 设置页开关与状态文案；无障碍语义（读屏不能念出锁屏内容）

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
| 新增安全模块 | `core/security/DatabaseKeyStore.kt`、`DatabasePassphrase.kt`、`KeyUnavailableException.kt`、`BackupCrypto.kt` |
| 备份 | `core/backup/BackupCodec.kt`、`BackupPayload.kt`、`BackupIo.kt` |
| UI / 状态 | `MainViewModel.kt`（导出恢复）、`SettingsScreen.kt`（应用锁、口令、明文警告、许可证入口） |
| 测试 | `app/src/test/` 新增加密与 envelope 单测；`app/src/androidTest/` 新增 Keystore 与迁移测试 |
| 文档 | `README.md`、`docs/PROGRESS.md`、`docs/release-guide.md`（版本号）、`.agents/project-profile.md §9` |
| 可能 | `.github/workflows/*.yml`（release 包的 native 库 16 KB 对齐校验） |

**不改动**：Room 实体 / DAO / `MIGRATION_1_2` / `app/schemas/`、统计与首页口径、更新链路（Gitee / GitHub）。

---

## 6. 顺序与验收

```
C1 ✅ → C2 → C3（⚠️ 真机灰度）→ C4 → C5 → C6 → C7 → C8（合 B8）→ C9 → C10 → 发 0.6.0
                                    └─ C1–C3 完成才解禁 A9 / A10
```

- 一子任务一交付：构建 + 测试通过 → 更新台账 → 单独提交（只 add 具体路径，不 push）。
- C3 之后每个新模块合并都要重跑 B3–B6 的回归（200% 字体、深浅色、横屏、读屏语义）。
- C1–C3 完成前，A9 月经 / A10 亲密关系模块继续保持 ⛔。
