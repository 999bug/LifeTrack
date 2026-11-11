# 项目进度 · 剩余工作子任务台账

> 维护规则：每完成一个功能先更新本文档再提交代码；进行中的任务标 🔄，完成标 ✅。每次发版同步在 `VersionHistory.kt` 最前面补一条用户可见的变化。
> 建立日期：2026-11-09。范围：README「当前尚未实现 / 下一阶段清单」+「安全边界」+ 各会话发现的欠账。
> **历史指示（2026-11-09）：安全加固包（C）优先级最低；月经、亲密关系模块依赖 C，一并后置。**
> **最新指示（2026-11-10）：C 包解禁，开始做数据库加密，按子任务逐个完成；0.5.0 以后版本都要考虑兼容性。细化清单、选型结论与兼容性矩阵见 `docs/security-hardening-confirmation.md`。**

## 0. 当前状态

| 状态 | 任务 | 进度 | 下一步 |
| --- | --- | --- | --- |
| 🔄 进行中 | 性能优化二期（索引 / 节流 / 资源 / 维护） | T9 `localDate` 索引与 Room v3 已完成；T10 更新节流、T11 图标 WebP、T12 VACUUM、T13 configuration cache、T14 baseline profile 待做 | 继续 T10（更新检查节流） |
| 🔄 进行中 | 性能优化（包体 / 启动 / 架构） | P0 包体（22 MB → 5.57 MB）、P1 线程调度、P2 数据库异步打开、P3 的设置页两段拆分与统计流提取均已完成并实测 | 剩余两项需单独一轮：备份流程抽 `BackupController`、按屏拆 ViewModel（需先引入共享日期源） |
| 🔄 进行中 | A 模块扩展包 | A1–A8、A10、A11、A12 已完成并验收（五类记录全闭环、统计类型筛选、字段校验收口）；A10 按 L1 隐私收敛落地，**已合入 `main` 但尚未发版**（0.6.0 不含）；仅 A9 月经按用户指示保留后置 | A10 随下一次发版进入 Release；A9 待后续指示 |
| ✅ 已完成 | 应用内检查更新（另一会话） | `33ef32d`：GitHub Releases 版本检查、APK 下载与安装、设置页 UpdateCard、自动检查一次 | — |
| ✅ 已完成 | 并发合并收尾 | 双方改动全部进入 HEAD，`testDebugUnitTest` 90 项 + `assembleDebug` 全绿；A6 代码无需再补提交 | — |
| ✅ 已完成 | 子任务台账建立 | 全部剩余工作已拆为 A/B/C 三包共 33 项 | — |
| 🔄 进行中 | B 测试与质量收尾包（11 项） | B1、B2、B4、B5、B6、B9、B10、B11 已完成；B3 语义树达标（缺 TalkBack 实机）；B7 仅剩「卸载重装丢数据」真机验证；B8 随 C8 | B7 真机剩余项 → B3 TalkBack 实机 → B8 随 C8 |
| ✅ 已完成 | C 安全加固包（10 项） | **C1–C10 已完成**：0.6.0 于 2026-11-10 发布（GitHub + Gitee 双端 Release、tag `v0.6.0`），用户真机覆盖安装与加密库迁移确认通过；许可证入口、README 安全边界、versionCode 6 齐备 | C8 / B8 真机厂商 ROM 走查仍为 🟡 |
| ✅ 已完成 | 记录详情弹窗 | 2026-11-10 新增：今天 / 历史卡片整行可点 → 只读底部弹窗；126 项单测、lint、assembleDebug 全绿；模拟器验证浅 / 深色、200% 字体、360dp 窄屏 | — |
| ✅ 已完成 | 发布 0.5.0（含 Gitee 国内分发） | versionCode 5 / versionName 0.5.0；GitHub + Gitee 双端 Release 与 APK / `.sha256` 附件齐备；Actions run `42751727141` 全绿（含 `mirror-gitee` job，16m54s） | — |
| ✅ 已完成 | 0.5.0 真机验收 | 用户 2026-11-10 反馈：应用内「检查更新」正常完成且走国内网络；0.4.0 → 0.5.0 覆盖安装升级成功 | — |
| ✅ 已完成 | 发布 0.6.0（加密首发） | 2026-11-10 16:42 由用户自行触发：GitHub Release `v0.6.0` + Gitee 同名 Release，`LifeTrack-0.6.0-release.apk`（22,283,347 B）+ `.sha256` 齐备，tag `v0.6.0` → `3b5ea49`；用户反馈真机安装与加密迁移成功。18:04 另一次触发因手填 `v0.6.0` 撞已存在 tag 在 `Check tag and version` 失败（head `40ee2ba`，未产出 Release） | 下一次发版带入 A10 |
| ✅ 已完成 | 发版版本号自动递增 | 2026-11-10 改造 `release.yml`：CI 从最高 tag 推 patch+1、从 gradle 推 `versionCode+1`，runner 内改 `build.gradle.kts` 后构建，成功后把版本改动提交回 `main`、打 tag、发 Release；`mirror-gitee` 改读 `needs.release.outputs.tag`。YAML 解析、11 段 shell 语法与 5 类版本分支已本地模拟验证 | 首次真实运行待用户触发；`main` 若开分支保护需放行 `github-actions[bot]` |
| ✅ 已完成 | 发版流水线配方与自动快照 | 新增 `docs/release-pipeline-recipe.md`（机器可读事实、按项目类型的移植矩阵、通用不变量、红线、给 `ai-dev-standards` 的归纳建议）与 `scripts/export_release_pipeline.py`（`--facts` / `--write` / `--check`，零第三方依赖、输出脱敏）；`ci.yml` 接 `--check`，`release.yml` 的版本提交会自动 `--write`，实测版本递增后自检报错 → 重生成 → 一致 | 待 `ai-dev-standards` 按项目类型归纳 |

A 包按「小步可验证」分批交付：第一批 A1+A2，第二批 A3+A4+A5（备份 v2、Room 仍 v1），第三批 A6（首次升 Room v2，新增通用详情表 + 饮水闭环），第四批 A7（只注册类型与规格，咖啡 / 茶闭环）。A7/A8 已验证「加类型不必升 Room」的扩展点；后续模块沿用同一路径。

## 1. 已完成归档

| 包 | 提交 | 摘要 |
| --- | --- | --- |
| Release 发布链路 | `c16684d` → `331f6be` 等 | 签名 APK + GitHub Actions + v0.1.0 发布验收 |
| 统计周/月/年改版 | `c95309f` | 暗夜仪表盘、StatsWindow、StatsAggregator |
| 数据安全包（明文） | `4deb6e0` | JSON 导出/恢复、事务替换、校验链 |
| 记录体验包 | `2b30495` | 原生日期时间选择器、历史分页与筛选 |
| 统计增强包 | `35e1fe9` | 月历视图、时段分布、形态分布；48 项单测 |
| 统计增强包确认 | `b86314a` | Q1–Q9 全部勾选，与已实施推荐值一致 |
| 剩余工作台账 | `98a643a` | 33 项子任务、依赖、顺序与风险 |
| 模块管理 A1/A2 | 2026-11-09 | 设置页记录项目卡片、启用状态生效、`ModuleOrdering` 纯函数；53 项单测全绿，模拟器验证停用/启用/重启持久化 |
| 模块扩展第二批 A3/A4/A5 | 2026-11-09 | 通用编辑器壳、`RecordDetail`/`BackupRecord` 领域模型、备份 v2 与 v1 兼容；71 项单测全绿，模拟器验证导出/恢复/坏文件拒绝；Room 仍 version 1 |
| 模块扩展第三批 A6 | 2026-11-09 | Room v2 + `event_detail` 通用详情表 + `MIGRATION_1_2` + `2.json`；饮水启用/记录/编辑/删除/备份往返全闭环；83 项单测全绿，模拟器验证深/浅色、200% 字体、宽屏；按用户指示不做存量数据实测。代码由 `33ef32d` 与更新功能一并提交（混合提交，见 R10） |
| 应用内检查更新 + A6 代码合并 | `33ef32d` | 另一会话完成更新功能并一并提交 A6 全部代码/测试/2.json；双方共管文件均保留；合并后 90 项单测与 assembleDebug 全绿 |
| A6 台账与确认单 | `88c20d1` | 确认单 §6 实施记录、台账 A6 标完成、B1 双基线就绪、R8/R9 登记 |
| 模块扩展第四批 A7 | 2026-11-09 | 单类型 `coffee` + 四字段规格，咖啡 / 茶新增/编辑/删除/备份往返全闭环；103 项单测全绿，模拟器验证三个模块、深/浅色、200% 字体、宽屏与三种坏文件拒绝；Room 仍 v2、备份仍 v2 |
| A 包收尾 + B 包质量门禁 | 2026-11-09 | A8 运动模块（三字段，不升 Room）、A11 跨面板类型口径（首页分列 / 历史全类型 / 月历全类型 / 统计类型筛选）、A12 字段校验收口；B1 Room 1→2 迁移测试、B2 仪器测试跑通、B4 切换条 200% 字体修复、B9 profile 欠账校准、B11 lint + CI 工作流。**115 项单测 + 5 项仪器测试全绿**，`lintDebug` 无 error |
| 发布 v0.2.0 | `b419afc` / Release `v0.2.0` | versionCode 2、versionName 0.2.0；CI 3m43s 全绿，签名证书与 v0.1.0 同一把（`6A6CC4…59D9`）可覆盖安装；APK 14.4 MB + `.sha256` 已挂 Release，校验通过与接口 `releases/latest` 返回 v0.2.0 |
| 发布 v0.3.0 | `df10075` / Release `v0.3.0` | versionCode 3、versionName 0.3.0；流水线 4m05s 全绿（含新增 lint 门禁），签名证书仍为 `6A6CC4…59D9` 可覆盖安装；APK 14.4 MB + `.sha256`，sha256 `191b2c12…be1381` 与 API digest 一致；`releases/latest` 返回 v0.3.0；发布说明已改写为中文 |
| 设置页重构 | 2026-11-09 | 设置页按「记录 / 数据 / 外观 / 关于」四段组织，统一行式布局；记录项目管理拆成独立页，首页入口按 sortOrder 渲染；更新与隐私收进「关于」；无障碍触摸目标全部 ≥48dp；横屏/平板限宽 640dp 居中；深浅色与 200% 字体复验 |
| B3 / B7 语义与备份验收 | 2026-11-09 | 统计/历史分段切换改 `selectable + Role.Tab`（48dp，选中态可被读屏识别），月历格 48dp 并朗读「几月几日几次」，形态/时段图各收敛为一句汇总；模拟器完成 SAF 导出→改数据→恢复全链路与飞行模式持久化验证 |
| 发布 v0.4.0 | `24b4624` / Release `v0.4.0` | versionCode 4、versionName 0.4.0；流水线 4m 全绿（含 lint），签名证书仍为 `6A6CC4…59D9` 可覆盖安装；APK 13.8 MiB + `.sha256`，sha256 `9c1ed1d2…6023e8` 与 API digest 一致；`releases/latest` 返回 v0.4.0；发布说明为中文，含设置页重构与无障碍改进 |
| 记录详情弹窗 | 2026-11-10 | `RecordDetailPresentation` 纯函数 + `RecordDetailSheet` 底部弹窗 + `RecordRow` 整卡点击链路；11 项新单测（共 126 项）；模拟器验证点编辑不误弹详情、四类型字段、浅 / 深色、200% 字体堆叠与滚动、360dp 窄屏；Room、备份格式与数据模型均未改动 |
| 应用内更新切换国内分发源 | 2026-11-10 | 代码与发布仍留 GitHub；Gitee Release 作为国内分发镜像，由 `release.yml` 用同一份签名 APK 同步附件；App 检查更新 Gitee 优先、GitHub 兜底，下载增加 HTML / ZIP 头校验；15 项新单测（共 141 项） |
| 发布 v0.5.0 | `408cc13` / Release `v0.5.0` | versionCode 5、versionName 0.5.0；Actions run `42751727141` 16m54s 全绿，GitHub Release 附 `LifeTrack-0.5.0-release.apk`（14,455,754 B）+ `.sha256`；Gitee Release `v0.5.0` 由 `mirror-gitee` job 自动创建并上传同两份附件；两端 `releases/latest` 均返回 v0.5.0，附件直链返回 `application/zip` |
| 0.5.0 真机验收 | 2026-11-10（用户） | 应用内「检查更新」正常完成且走国内网络；0.4.0 → 0.5.0 覆盖安装升级成功，数据未丢 |
| C1 加密选型与 POC | 2026-11-10 | 选型 SQLCipher for Android 4.20.0 + `SupportOpenHelperFactory`；隔离目录 `:app:assembleDebug` BUILD SUCCESSFUL（AGP 9.3.1 / Room 2.8.5 / sqlite 2.7.0）；debug APK 21,187,285 B → 28,949,306 B（+7.40 MiB），64 位 `.so` 的 PT_LOAD 16 KB 对齐通过；新增 `docs/security-hardening-confirmation.md` |
| C2 Keystore 密钥管理 | 2026-11-10 | 新增 `core/security/` 三个类（`PassphraseEnvelope` / `DatabaseKeyStore` / `DatabaseKeyRepository`）：AES-256-GCM 主密钥不绑定生物识别、口令信封只落密文、失效按 4 类原因明确报错；6 项新单测（共 147 项）+ 5 项仪器测试 |
| C3 存量明文库迁移 | 2026-11-10 | 接入 SQLCipher 4.20.0 + sqlite 2.7.0；新增 `SqliteFileHeader` / `DatabaseEncryption`，`AppDatabase` 走加密 openHelper，`AppContainer` 编排启动、降级与密钥失效终止页。保留 `user_version`、WAL checkpoint、逐表行数校验、原子替换、中断可恢复；备份格式未改。151 项单测 + 14 项仪器测试全绿；模拟器用 0.4.0 明文库（主库 61,440 B + WAL 16,512 B）做覆盖安装实测，WAL 内第 3 条记录保真、重启可用密钥重开 |
| C4 导出文件加密 | 2026-11-10 | 新增 `BackupCrypto`（PBKDF2-HMAC-SHA256 210000 次 + AES-256-GCM）与 `BackupEnvelope`（v3 容器，头部纳入 AAD）；`BackupCodec` 增加 `encodeEncrypted` 与带口令的 `decode`，v1/v2 明文继续可读；设置页导出默认加密、口令二次确认、明文导出红色风险确认，恢复 v3 时弹口令框。169 项单测全绿（新增 18 项）；模拟器实测加密导出 4,190 B → 错误口令被拒 → 正确口令恢复 3 条；明文导出/恢复兼容；200% 字体与深色模式对话框无截断 |
| C5 完整性校验与口令轮换 | 2026-11-10 | AAD 覆盖头部已在 C4 落地；新增 `BackupCodec.reencrypt` + `BackupReencryptResult`，设置页「更换备份口令」走「旧口令解出 → 新口令重编码（新盐 / 新 IV）→ 自检解回比对 → 另存 `-rekeyed.json`」，原文件不动。176 项单测全绿（新增 7 项）；模拟器实测错误旧口令被拒、新文件仅新口令可解、旧文件旧口令仍可恢复、盐 / IV / 密文全部更换 |
| C6 恢复流程打磨 | 2026-11-10 | 新增 `PassphraseQuality`（长度 + 字符种类四档强度）与 `RetryBackoff`（前 2 次免费，之后 2/4/8/16/30 秒）；导出、恢复、轮换三处口令框统一强度提示与冷却倒计时，冷却中按钮置灰；错误文案补「区分大小写和全半角」。185 项单测全绿（新增 9 项）；模拟器实测弱→强提示变化、第 4 次失败显示「请等待 3 秒」且按钮禁用、结束后恢复可点 |
| C7 应用锁与预览遮蔽 | 2026-11-10 | 新增 `AppLockTimeout` / `AppLockPolicy` / `AppLockAuthenticator` / `AppLockGate`；设置页「安全」分区（开关 + 立即/1/5 分钟），API 30+ 系统 BiometricPrompt 合并设备凭据，API 26–29 走 Keyguard；锁定用全屏 Dialog 盖住正文并清空语义树，正文不销毁以保住 SAF 写盘；Activity 绑定 `FLAG_SECURE`，进程被杀后冷启动必锁。191 项单测全绿（新增 6 项）；模拟器实测 PIN 解锁、返回键拦截、1 分钟不误锁、锁屏无正文泄漏、移除锁屏后的兜底、200% 字体布局 |
| C8 备份 / 迁移评审 | 2026-11-10 | 复核 `allowBackup=false` + `fullBackupContent` + `dataExtractionRules`：五个数据域在云备份与设备迁移两条链路全部排除；Keystore 密钥不属于应用数据；`.plaintext.bak` 同在 `databases/` 且首次读取后删除。模拟换机：卸载重装后 0 条记录、新库文件头为随机密文；用加密备份在新 Keystore 上恢复 3 条成功。真机 ROM 走查待做（B8 同步改 🟡） |
| C9 隐私审查 | 2026-11-10 | 非 debug 构建已关 SQLCipher Logcat；全仓仅 3 条日志（失效枚举 / 迁移失败 / 版本与行数），无记录内容、口令或密钥；无崩溃上报 SDK、无剪贴板、无通知、无临时导出文件；FileProvider 仅暴露更新 APK 且 `exported=false`；网络只走 Gitee / GitHub Releases；`.plaintext.bak` 清理路径复核。无需改代码 |
| C10 发布前回归（代码就绪） | 2026-11-10 | 新增 `ThirdPartyNotices` 与设置页「关于 → 开源许可」可滚动全文（BSD-style），README 补第三方许可章节；安全边界 5 项逐条改写；`versionCode 6` / `versionName 0.6.0`。191 项单测 + 14 项仪器测试全绿，lint 无 error，`assembleRelease` 成功（22,275,155 B）；200% 字体许可证弹窗无截断。真机覆盖安装由用户 2026-11-10 确认通过；0.6.0 已发布 |
| A10 亲密关系模块 | 2026-11-10 | 按 `docs/intimacy-module-confirmation.md` V1–V10 推荐值落地：默认停用、三项可空字段、L1 列表级收敛与读屏一句汇总、启用期间无条件 `FLAG_SECURE`、首次启用提示、明文导出加强提示；Room 仍 v2、备份 v1/v2/v3 不变；A9 月经保留 ⛔，未动排便专用链路。204 项单测全绿，`40ee2ba` 提交 |
| 发布机制改造（自动递增） | 2026-11-10 | `release.yml` 去掉必填版本号：新增 `Resolve version`（最高 tag + patch、gradle versionCode +1、tag / Release 冲突与分支校验）、`Bump version in build file`、`Commit version bump to main`（构建成功后才推送，`[skip ci]`，并发时中止）、`Create GitHub Release` 改指向版本提交；`mirror-gitee` 的 `TAG` 改读 job outputs。`docs/release-guide.md` §2 重写 | 未在真实 CI 跑过；需 `main` 允许 bot 推送 |
| 月历按天查看 + 界面改造第一批 | 2026-11-11 | 底导「记录」改名「历史」；月历格由「日期 + 次数」改为「日期 + 最多 3 个类型图标」，今天实心高亮；点日期弹出 `DayRecordsSheet` 列出当天记录，无记录日走空态；新增 `observeDailyTypeCounts`（按日期+类型聚合，按当天首次出现排序）、`DayCalendarData` 纯函数与 `RecordSummary` 一行摘要；新增 `LifeTrackTokens` 设计常量。210 项单测全绿，`lintDebug` 无 error；模拟器验证浅/深色、200% 字体、360dp 窄屏与弹层三态 | 首页模块网格重构、设置/统计 token 对齐未做，见 `docs/ui-redesign.md` §6 |
| 首页模块网格重构 | 2026-11-11 | `TodayScreen` 重写为「页头 + 双列模块卡 + 今日时间轴」，删除顶部大计数卡与按钮列表说明段；新增 `ModuleCard`（整卡即记录：排便/饮水一键落库，咖啡/运动直接进编辑器；右下「详细」为独立入口）、`CompactRecordRow`（时间轴行，编辑/删除收进尾部菜单）、`MainViewModel.quickWaterRecord`（默认一杯 250 ml）；停用模块不再渲染；日期固定 `Locale.CHINA`。200% 字体下模块卡降为单列、`widthIn` 避免「详细」被压成单字。210 项单测与 `lintDebug` 全绿，模拟器验证一键记录 / 详细入口 / 单列布局 | 设置页与统计页的 token 对齐未做 |
| 设置页主题三档 + 统计页 token 收口 | 2026-11-11 | 新增 `ThemeMode`（浅色 / 深色 / 跟随系统）与 `theme_mode` 持久化，写入时同步旧 `dark_theme` 键，回退到 0.6.x 不丢深色偏好；`MainActivity` 用 `isSystemInDarkTheme()` 解析跟随系统；设置页外观区改三段选择，设置行加 20dp 图标、分组标题改 11.5sp/600 带字距、卡片圆角 18→16；统计页卡片圆角 18→16、指标卡 14→13、柱图非高亮柱 alpha 提高。210 项单测与 lint 全绿，模拟器验证三档切换即时生效 | TalkBack 实机朗读仍未验 |
| 国内更新改为制品就绪判定 | 2026-11-11 | 修复 Gitee 同步窗口期误报：`ReleaseInfo` 引入 `ReleaseOrigin` / `ApkArtifact`，`.apk` + `.apk.sha256` 齐备才算就绪（sha256 是 CI 最后上传的完成标记）；新增 `UpdatePolicy` / `ArtifactProbe` / `PendingBackoff`，缺附件进 `Pending` 并按 30s→1m→2m→5m 退避重查，`ReleaseFetcher` 缺包不再回退 GitHub 下载、兜底只取版本信息；`ApkDownloader` 把状态码 / 类型 / 长度 / ZIP 头校验全部前置到进度条出现之前，加停滞看门狗与写盘后 sha256 校验；UI 呈现同步中、来源标注与真实错误。236 项单测全绿（更新模块 48 项，夹具用线上真实窗口期响应），`testDebugUnitTest` / `assembleDebug` / `lintDebug` 全绿；模拟器验证启动、自动检查、UpToDate 行渲染、默认与 200% 字体窄屏截图。**未验证**：`Pending` / `Available` / `Downloading` 三态真机截图与国内手机网络端到端下载（本机 debug 0.6.2 与线上最新版本一致，无法自然触发），建议 0.6.3 发版窗口期补验 | 流水线 P1（先传附件后发布 + 末尾自检）本次未做 |
| 设置页新增更新日志 | 2026-11-11 | 「关于」内新增一行「更新日志」入口（History 图标），点开是可滚动弹窗：`VersionHistory.kt` 集中 0.1.0–0.6.4 每个版本一条（版本号 + 日期 + 一句用户可见变化），当前安装版本标「当前」并染主色。初版曾铺成独立分区，用户反馈影响美观后收成单行入口 + 弹窗，与「开源许可」交互一致；版本号用 `weight(1f)` 让日期先测量，大字号只换行不挤压。`compileDebugKotlin` / `assembleDebug` / `lintDebug` 全绿、250 项单测全通过、lint 0 error；模拟器实测默认字体收起页、200% 字体弹窗与窄屏：无重叠与截断。200% 下「v0.6.4 · 当前」曾被日期挤成「当 / 前」两行，改为独立 `softWrap=false` 的「当前」短标签后，默认字体弹窗与 200% 字体弹窗均复验通过 | 发版时需同步补一条；未做分页/搜索 |
| 设置页重构 v2 原型（设计评审稿） | 2026-11-11 | 交付 `docs/settings-redesign-v2-prototype.html`（单文件可交互）与 `docs/settings-redesign-v2.md`（设计方案）。按使用频率分层：数据与备份默认折叠、五段重排为「无标题主卡 / 安全 / 数据与备份 / 更多 / 页脚」，版本与检查更新沉到页脚并保留「不提供医疗诊断」免责文案；有新版时首屏「更新日志」行同步出现标记兜底可发现性，页脚按钮触摸目标上调到 48dp。Chrome 无头实测 21 + 18 + 7 项断言全通过、0 JS 异常、0 外链资源，浅色 / 深色展开 / 有新版本 / 390dp 窄屏截图核对。**未改动任何 Compose 代码** | 与现网实现有 6 项差异待评审拍板（外观弹层、隐私二级页、更新日志形态等，见设计方案 §7）；TalkBack、200% 字体、真机 Compose 渲染、平板限宽下页脚位置未验 |
| 设置页重构 v2 落地（Compose） | 2026-11-11 | 用户「开搞」批准落地。`SettingsScreen.kt` 重排为无标题主卡 / 安全 / 数据与备份折叠 / 更多 / 应用更新卡 / 页脚；`SettingsComponents.kt` 新增 `SettingsCard` / `CollapsibleSettingsSection` / `SettingsFooter` / `UpdateBadge` 与 `stateDescription` 参数；`VersionHistory.kt` 弹窗只列最近 6 个版本 + 截断说明，全量数据不变。外观改单行 + 弹层（复用 `ThemeModeRow`），隐私与更新日志沿用折叠行 / 弹窗，未新增导航路由。导出 / 恢复 / 失败时 `LaunchedEffect` 自动展开折叠区；有新版时「更新日志」行显示标记。**验收两处修正**：应用更新卡从版本号下方移到「数据与隐私」下方、版本号沉到最底部；更新日志由 10 条截断为 6 条；原型 HTML 同步。250 项单测 + lint 全绿；模拟器实测分组 / 折叠 / 外观弹层 / 页脚顺序（y 坐标 1542→1761→1975→2032）/ 更新日志 6 条，浅深 / 200% 字体 / 2000×1200 宽屏截图，设备状态已还原 | TalkBack 实机、物理真机 / 真实平板、360dp 窄屏、更新三态真机截图未验 |
| 发版校验更新日志 + Release 说明同源 | 2026-11-11 | `release.yml` 的 `Resolve version` 增加更新日志校验：解析 `VersionHistory.kt` 首条 `version`，与 CI 算出的版本号不一致就在构建前失败——漏写不会报错，但用户装上新包后弹窗首条还是上一版、也没有任何一条标「当前」，属静默失效。同时抽出首条 `summary`，经 job outputs 传给两个 Release：GitHub 的 `--generate-notes` 换成 summary + compare 链接（原 commit 列表移到 compare 页），Gitee body 首行由镜像文案改为 summary，应用内「应用更新」卡看到的说明与更新日志一致。`docs/release-guide.md` §2 改为「三件事」并把过时的版本号示例纠正到 0.6.4 / versionCode 10，失败表补一行；`docs/release-pipeline-recipe.md` 快照由 `--write` 重生成。验证：YAML 解析通过、16 个步骤未增减、11 个 run 块 `bash -n` 全过、校验逻辑实测两例（0.6.4 放行并生成两段式 Gitee body；0.6.5 拦截且错误信息含补齐指引）、`--check` 通过 | 未在真实 CI 跑过；APK 构建与两个 Release 的创建未实测；下次发版前须先补 0.6.5 那条，否则会按设计在构建前失败 |
| 包体优化：R8 + 资源压缩 + ABI 过滤 | 2026-11-11 | release 构建开启 `isMinifyEnabled` / `isShrinkResources`，新增 `app/proguard-rules.pro`（只保留枚举 `values`/`valueOf`、注解与泛型签名，不整包 keep——Room / kotlinx-serialization / SQLCipher 的 AAR 都自带 consumer rules，重复 keep 会把包体撑回去）。根因是 `material-icons-extended` 的 11,105 个图标类在无 R8 时全进 dex：基线 release APK 22 MB / 解压 57 MB / dex 49 MB（3 个 dex）。开启后 10 MB / 12.07 MB / dex 3.20 MB（1 个），dex 内 `material/icons` 残留 0。再对 release 加 `ndk.abiFilters` 只留 arm64-v8a + armeabi-v7a（x86/x86_64 占 4.5 MB），最终 **5.57 MB / 解压 7.52 MB**，较基线 -75% / -87%。debug 不过滤保留全 ABI，另加 `-Plifetrack.fullAbi` 开关，让 x86_64 模拟器也能跑「开了 R8 的全架构包」做功能回归。验证：R8 包用 debug keystore 签名后覆盖安装 emulator-5554，无崩溃、`AppContainer: database ready: AlreadyEncrypted` 证明 SQLCipher + Keystore + Room 正常；首页控件树完整（5 条记录、模块卡、时间轴）；导出 5 条加密记录成功（6502 B），再选同一文件解密提示「备份包含 5 条记录」并恢复成功，覆盖 kotlinx.serialization 编解码与 Room 事务写。CI 增 `Upload R8 mapping` artifact（mapping.txt 44 MB）。`release-pipeline-recipe.md` 快照重生成（step_count 16 → 17） | 运行验证用的是 arm 全架构 R8 包，arm-only 发布包未在真机跑（本地只有 x86_64 模拟器）；mapping 未做真实崩溃栈还原实测；未做 baseline profile |
| 运行时：统计聚合移出主线程 | 2026-11-11 | `MainViewModel.statistics` 在 `stateIn` 前加 `flowOn(Dispatchers.Default)`：原先 4 个周期的 `StatsAggregator.build` 跑在 `stateIn` 的收集上下文，也就是 `viewModelScope` 默认的 Main，每写一条记录 Room 重发一次就重算一遍，数据攒到几千条会在主线程掉帧。`HistoryScreen` 的 `groupBy{}.toSortedMap()` 原在 composable 函数体内、未 remember，每次重组都重建 Map，改为 `remember(records)`。验证：`testDebugUnitTest` + `assembleDebug` 全绿；模拟器上今天页 5 次 / 4 个模块卡、历史页「共 5 条」、统计页共记录 5 次 / 日均 0.7 / 单日最高 5 / 时段 02:00–04:00，三页口径一致 | 未做 Macrobenchmark 或帧率实测，「主线程不再计算」是代码层结论；flowOn 只影响调度，未改变聚合结果（三页数字一致已佐证） |
| 启动：数据库打开移出主线程 | 2026-11-11 | `AppContainer.databaseState` 从同步 `val`（构造期就 `openDatabase()`）改为 `StateFlow`，新增 `Loading` 态；打开流程放进 Application 级的 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`，覆盖 `System.loadLibrary`、Keystore 解封口令、`migrateIfNeeded` 整库复制三件阻塞操作。`openDatabase` 去掉 `runBlocking` 改为 `suspend`；意料外异常兜底成 `Unavailable(KEYSTORE_ERROR)`，避免界面永久停在 Loading。`LifeTrackApplication.onCreate` 只创建容器后立即返回，默认类型补齐与明文备份清理改到 Ready 之后、同一协程内串行执行（DAO 抛异常时不清理备份，保留原语义）。`MainActivity` 增加 Loading 分支并新增 `DatabaseLoadingScreen`，`FLAG_SECURE` 的收集随 Ready 一起进入组合（类型表要等数据库就绪才能读）。实测 release 包：进程启动 → `database ready` 724 ms → 首帧 1.26 s，数据库打开与 Compose 组合已并行，不再累加；旋转触发 Activity 重建后没有新的 `database ready` 日志，数据完整 | 未做改动前后 A/B 对照（旧实现已被覆盖），「首帧提前约 0.7 s」是按并行关系推算；升级迁移慢路径（整库复制）未在本机复现，Loading 页在该场景的实际停留时长未验；debug 包首帧仍 3.5 s，与本次改动无关（28 MB 未压缩 dex） |
| 架构：设置页拆出主卡与「更多」分区 | 2026-11-11 | `SettingsScreen` 的约 1000 行函数抽出 `PrimarySection`（记录项目 / 外观，4 个参数）与 `MoreSection`（更新日志 / 开源许可 / 数据与隐私，5 个参数）。**只搬运渲染代码与回调**，展开态与弹窗可见性仍留在 `SettingsScreen`——它们和安全区、「数据与备份」的 30 多个 state、4 套 launcher（导出 / 恢复 / 换口令 / 选取文件）共用同一个重组作用域，改状态提升方向的风险高于收益。验证：`compileDebugKotlin` / `assembleDebug` 通过；模拟器复验主卡文案（已启用 4/5）、外观弹层三档、数据与隐私展开、更新日志 6 条、开源许可全文 | 安全区与「数据与备份」未拆：后者应整体抽成 `BackupController`（state holder）而不是继续在 composable 间传参，属独立一轮重构；本次未做重组次数或帧率量化 |
| 架构：统计数据流提取为纯函数 | 2026-11-11 | MainViewModel 里 40 行的统计流水线（四周期窗口解析 + 三路查询合并 + `StatsAggregator` 聚合）提成 `feature/statistics/StatisticsStream.kt` 的 `observeStatistics(repository, today, offsets, typeFilter)`；`emptyStatistics` 一并移出并显式接收 `today`，避免流构建处再取一次系统时间。**纯提取、不改状态归属**：调度（`flowOn`）与 `stateIn` 仍由 ViewModel 决定。完整拆出 `StatisticsViewModel` 需要先引入 `TodayProvider` 这类共享日期源——`todayDate` 同时驱动首页、历史、月历与统计，各自维护会跨天不一致。验证：`testDebugUnitTest` + `assembleDebug` 通过；统计页与提取前逐项一致（共记录 5 次、日均 0.7、单日最高 5、最长连续 1 天） | 未新增单测覆盖 `observeStatistics`（纯搬移，无新逻辑）；MainViewModel 仍是 400 行量级，按屏拆分与备份流程重构留待独立一轮 |
| P0 补 `localDate` 单列索引（Room v3） | 2026-11-11 | 历史页「全部类型」、月历与统计的范围条件只带 `localDate`，而 `track_event` 只有 `(typeCode, localDate)` 复合索引和 `occurredAtEpochMillis`；`typeCode` 为 null 时复合索引前导列缺失，只能全表扫 + 按时间排序。`TrackEventEntity` 增加 `Index(value = ["localDate"])`，`AppDatabase` v2 → v3，`MIGRATION_2_3` 只 `CREATE INDEX index_track_event_localDate`、不动表与数据。新增仪器测试 `migrate2To3_addsLocalDateIndexAndKeepsRecords`：`runMigrationsAndValidate` 按 `3.json` 的 identityHash 校验索引名与结构，再查 `PRAGMA index_list` 与存量行。验证：`MigrationTest` 3 项在 emulator-5554 全绿；全新安装建 v3 加密库正常（`Outcome.Fresh`）、写入 3 条饮水记录成功 | **未在真实加密库上跑过 v2 → v3 升级**：`connectedDebugAndroidTest` 结束时会卸载 APK 并清掉旧库数据；迁移正确性由仪器测试覆盖，加密库上只验证了 v3 建库与读写 |

## 2. A · 模块扩展包（README 清单 2 项）

| 编号 | 子任务 | 交付物 / 验收 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| A1 | 模块管理页 | 设置页新增「记录项目」入口，列出 `track_type` 并可启用/停用、调整排序；`TrackTypeEntity` 已有 `enabled` / `sortOrder`，不改 schema | — | ✅ |
| A2 | 启用状态生效 | 今天页按启用状态显示新增记录入口；停用不产生新记录，但历史列表、今日次数与统计仍照常展示已有数据 | A1 | ✅ |
| A3 | 通用记录编辑器抽象 | 抽出 `PoopEditorDialog` 公共部分（日期时间、备注、时区提示），详情字段由各类型提供 | A1 | ✅ |
| A4 | 通用详情存储扩展 | `BackupPayload.events` 现为 `List<PoopRecordRelation>`，需支持「事件 + 各类型可选详情」 | A1 | ✅ |
| A5 | 备份格式 v2 | `schemaVersion` 1 → 2，导出/恢复新类型详情，旧备份仍可读，校验链按类型分派 | A4 | ✅ |
| A6 | 饮水模块（试点） | 新增详情表 / 实体 / DAO / Repository / 编辑器 / 列表项，最小字段（量 + 单位）；**本项开始才升 Room v2** | A3 A4 | ✅ |
| A7 | 咖啡 / 茶模块 | 饮品类型、份量、咖啡因标记；沿用 A6 的扩展点，只加 `DEFAULT_TYPES` + `DetailSpec`，不再升 Room | A6 | ✅ |
| A8 | 运动模块 | 类型、时长、强度；沿用 A6 的扩展点 | A6 | ✅ |
| A9 | 月经模块 | **高敏感，README 明确禁止在数据库加密前上线** | C1 C2 C3 | ⛔ 保留后置（加密前置已解除；用户 2026-11-10 明确「月经先不开发，留着」） |
| A10 | 亲密关系模块 | 默认停用、三项可空字段、L1 列表级收敛、启用期间 `FLAG_SECURE`、读屏收敛；确认单 `docs/intimacy-module-confirmation.md` | C1 C2 C3 | ✅ 已完成并验收（2026-11-10，按 V1–V10 推荐值） |
| A11 | 统计 / 首页按类型适配 | 统计页类型筛选、首页按启用模块聚合，保持跨面板自洽 | A2 A6 | ✅ |
| A12 | 新模块校验与备份范围 | 各类型详情字段范围校验、备份逐字段校验扩展、坏文件拒绝用例 | A5 | ✅ |

## 3. B · 测试与质量收尾包（README 清单 2 项 + 欠账）

| 编号 | 子任务 | 交付物 / 验收 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| B1 | Room Migration 正式测试 | `MigrationTestHelper` + `app/schemas/1.json` 基线校验；有 v2 后补真实迁移用例与回滚用例 | A5 | ✅ |
| B2 | 仪器测试跑通 | `connectedDebugAndroidTest` 执行 `LocalDatabaseTest`，README「未验证项」销项 | — | ✅ |
| B3 | 无障碍回归 | 全页 `contentDescription`、触摸目标 ≥48dp、读屏顺序；读屏能听到月历格与分布卡汇总 | A11 | 🟡 语义树已达标：分段切换 `selectable+Tab` 带选中态、月历格 48dp 且报「几月几日几次」、分布/时段图各有汇总句、记录卡图标 48dp；仅缺 TalkBack 实机朗读复核 |
| B4 | 字体缩放 200% 全页回归 | 全页截图无截断；**修复已知遗留：统计页 `PeriodSwitcher` 选中块压住「周 / 月 / 年」文字** | — | ✅ |
| B5 | 横屏 / 平板 / 多尺寸回归 | 月历 7 列自适应、统计长页滚动、无溢出 | A11 | ✅ |
| B6 | 深 / 浅色全页回归 | 含新模块，无 M3 默认紫色回退 | A11 | ✅ |
| B7 | 真机验收 | 安装、SAF 导出恢复、飞行模式持久化、卸载重装数据丢失验证 | — | 🟡 真机 0.2.0→0.3.0、0.4.0→0.5.0 覆盖安装均已确认；模拟器已验证 SAF 导出/恢复与飞行模式下新增+强停重开数据保留；剩真机「卸载重装丢数据」验证 |
| B8 | 系统迁移 / 厂商 ROM 行为 | 设备迁移不带走业务数据库；与 C8 重叠，可合并执行 | C8 | 🟡 与 C8 同步：配置复核 + 模拟换机完成，真机迁移 / ROM 走查待做 |
| B9 | 文档欠账校准 | profile §6 `verify_build/test` 仍写「未验证」、§7 `related_docs: 无`、§9「尚未实现主动导出/恢复」已过时；README 版本核对日期 | — | ✅ |
| B10 | 建立本台账 | 已完成，跨会话接手依据 | — | ✅ |
| B11 | CI 门禁 | 现有 workflow 只跑单测 + assembleRelease；补 lint 与仪器测试触发条件 | B1 B2 | ✅ |

## 4. C · 安全加固包（🔄 进行中：用户 2026-11-10 指示解禁）

> 2026-11-10 启动。按项目惯例先出设计前确认单 `docs/security-hardening-confirmation.md`：C1 选型结论与兼容性核查、C1–C10 逐项子任务拆解、收益/代价/回滚方案、需拍板的决策点、0.5.0+ 版本兼容性矩阵。
> README「安全边界」要求的 5 项评审仍未全部完成，R1 风险在 C10 前持续存在。

| 编号 | 子任务 | 交付物 / 验收 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| C1 | 加密方案选型与 POC | 评估 SQLCipher / 加密 Room 方案，核对 AGP 9 兼容、包体与性能，出选型结论 | — | ✅ |
| C2 | Keystore 密钥管理 | 密钥生成/存储、设备锁定与生物识别绑定、密钥丢失降级与用户提示 | C1 | ✅ |
| C3 | 存量明文库迁移 | 明文 → 加密文件格式搬迁（**Room 保持 v2，不升版本**，见确认单 §2.5）、逐字段校验、失败自动还原、明文库备份；升级不卸载不丢数据 | C1 C2 | ✅ |
| C4 | 导出文件加密 | 口令派生密钥 + 认证加密，文件格式与 `schemaVersion` 兼容当前备份 | C2 A5 | ✅ |
| C5 | 完整性校验与密钥轮换 | 篡改可检出、轮换后旧备份仍可恢复 | C4 | ✅ |
| C6 | 加密备份恢复流程 | 口令输入、错误提示、重试节流、UI 明文警告改写 | C4 C5 | ✅ |
| C7 | 应用锁 + 预览遮蔽 | BiometricPrompt / 系统凭据、超时策略、`FLAG_SECURE` 遮住最近任务预览 | C2 | ✅ |
| C8 | 备份 / 迁移 / ROM 评审 | 自动备份、设备迁移、卸载重装、换机恢复、`allowBackup` 策略复核（与 B8 合并） | C3 | 🟡 配置复核与模拟换机完成，真机 ROM 走查待做 |
| C9 | 隐私审查 | 日志（SQLCipher 默认输出 Logcat，C3 已提前关闭生产日志）、崩溃报告、剪贴板、通知内容、最近任务屏幕预览 | C7 | ✅ |
| C10 | 发布前安全回归 | 全链路回归 + 应用内可访问的 SQLCipher 许可证归属入口 + README「安全边界」从「未实现」改写为「已实现 + 边界说明」+ 升 0.6.0 / versionCode 6 | C1–C9 | ✅ 0.6.0 已发布，用户真机覆盖安装与加密迁移确认通过；C8 / B8 真机 ROM 走查仍 🟡 |

## 5. 依赖与推荐顺序

- A9 / A10 曾**硬阻塞于 C1–C3**：README「安全边界」写明加密完成前不得上线高敏感模块。C1–C3 与 C10 真机验证均已完成，A10 已于 2026-11-10 解禁并交付；A9 按用户指示继续保留 ⛔，不设解禁日期。
- A4 / A5 依赖 A1 的类型模型稳定；**B1 依赖 A6 的 Room v2**（备份 schemaVersion v2 不等于 Room 迁移，A5 未改数据库）；C4 依赖 A5 的备份格式。A6 已完成，`1.json` / `2.json` 双基线齐备，B1 已完成并通过模拟器实跑。
- C3 依赖 C1/C2；C4/C5/C6/C7 依赖 C2；C10 依赖 C1–C9。
- B3–B6 不是一次性任务，每个新模块合并后都要回归一次。
- **推荐顺序（2026-11-10 更新）**：A1–A8、A11、A12、B1、B2、B4、B5、B6、B9、B10、B11 已完成；设置页重构与 v0.5.0 发布（含真机验收）已完成 → 剩 B7（真机剩余项）→ B3 读屏实机 →（B8 随 C8）。**C 包按 C1 ✅ → C2 ✅ → C3 ✅ → C4 ✅ → C5 ✅ → C6 ✅ → C7 ✅ → C8 🟡 → C9 ✅ → C10 ✅（0.6.0 已发布，真机安装验证完成）逐个完成；A10 于 2026-11-10 按 V1–V10 推荐值交付并合入 `main`，随下一次发版进入 Release；发版机制已改为 CI 自动递增版本；A9 保留 ⛔。每个子任务构建 + 测试通过再进下一步。**
- 一包一交付：每包先出设计前确认单，确认后再写代码，验收通过后更新本台账并单独提交。A/B 收尾包用户曾明确「逐个完成不需要确认」（见 `docs/module-remaining-confirmation.md`）；C 包按 2026-11-10 指示逐个完成，关键取舍与兼容性矩阵集中在 `docs/security-hardening-confirmation.md`。

## 6. 风险与已知遗留

| 编号 | 风险 | 说明 |
| --- | --- | --- |
| R1 | ~~已发布但未加密~~ **已解除** | 0.6.0（2026-11-10）已发布，加密库迁移由用户真机确认通过；0.5.0 及更早的历史版本仍是明文库，但不能覆盖安装到已加密的库上。 |
| R2 | 备份兼容性 | ~~A5 升 `schemaVersion` 会让旧备份失效~~ → 已缓解：v1/v2 共用一个 DTO、版本白名单兼容，v1 旧备份实测恢复成功。C3 未改备份格式；**C4 起默认导出 v3 加密备份，0.5.0 及更早版本无法读取 v3**，需要与旧版互换时显式选明文导出。新版永久保留读取 v1 / v2 明文备份的能力，README 已标注单向风险。 |
| R3 | ~~schema 与加密迁移撞车~~ 已澄清 | Room v2 已于 A6 落地（只加 `event_detail` 表）。C1 选型确认：明文 → 加密只换文件格式与 openHelper，schema 不变、密钥材料放 DataStore，因此 **C3 不升 Room 版本、仍为 v2**，少一次不可逆迁移；详见确认单 §2.5。已实测 v1 库保留 `user_version=1`。 |
| R8 | ~~存量库 1→2 升级未实测~~ 已缓解 | B1 用 `MigrationTestHelper` 按 `1.json` 建真实 v1 库跑迁移并逐字段校验，模拟器 2 项迁移用例全绿；真机存量升级仍归 B7。 |
| R9 | ~~多会话并发改动~~ 已解除 | 另一会话的「应用内检查更新」半成品曾在 17:28–17:33 阻断全仓编译；`33ef32d` 修复并合并双方改动，当前 90 项单测 + assembleDebug 全绿、工作区干净。并发教训保留：共享文件混改动时不做 stash / 回退，用 `git archive HEAD` 隔离验证，只 add 具体路径。 |
| R10 | **A6 与更新功能混合提交** | `33ef32d` 同时含 A6 的 22 个代码/测试/schema 文件与更新功能，原因是共管文件交织、单独提交会让 HEAD 不可编译。按仓库 git 安全规则禁止历史改写，不拆分；今后按包回溯请用 `git log -- <路径>` 过滤，别依赖单提交边界。 |
| R4 | 通用事件模型膨胀 | 每加一个类型都要动 DAO / Repository / 备份 / 统计四处，A3/A4 的抽象不做干净会返工。A8 实测只需加类型 + 规格 + 编辑器 + 分派，DAO/备份/统计已无需改动。 |
| R5 | ~~200% 字体遗留~~ 已修复 | 统计页 `PeriodSwitcher` 与历史页 `ViewSwitcher` 改为按字号抬高的自适应切换条，类型筛选 chips 改 `FlowRow`；200% 字体实测首页、历史、统计、运动编辑器均无截断（B4）。 |
| R11 | **CI 的仪器测试任务未实跑** | `ci.yml` 的 build job 已在 `df10075`、`2fc7e5a` 两次 push 上 success（单测 + lint + assembleDebug）；instrumented job 按设计 skipped。`reactivecircus/android-emulator-runner@v2` + API 36 镜像 + KVM 这条链路仍未真正执行，需手动 dispatch 或给 PR 打 `run-instrumented` 标签验证。本地 `connectedDebugAndroidTest` 14 项已全绿。 |
| R12 | **加密后不可降级** | 0.6.0 起的加密库无法被 0.5.0 及更早版本打开；Android 不允许降 `versionCode`，但用户卸载后重装旧 APK 会命中。README 已写明「需要回退请先导出备份」，0.6.0 发布说明需同步。 |
| R13 | **CI 自动提交版本改动** | 发版流水线会把 `chore: release …` 提交推回 `main`，本地会落后一个提交，下次开发前须 `git pull --ff-only`；`main` 若启用分支保护要放行 `github-actions[bot]`。构建失败发生在版本提交之前时无副作用；若版本提交已推送而 Release 未建，重跑会再升一次 `versionCode`，须先看日志。详见 `docs/release-guide.md` §2.2 / §2.3。 |
| R6 | 台账维护 | 中断恢复只认本文件；进度只在会话里说、不写进来，下次会话必然重议。 |
| R7 | `track_type` 首次启动为空 | 初版只在第一次打卡时 `insertIgnore(DEFAULT_POOP_TYPE)`，模块管理页首次打开会是空列表。已用 `ensureDefaultTypes()` 在 ViewModel 初始化时补内置类型（`IGNORE` 不覆盖用户的启用/排序设置），恢复备份的兜底也改为补齐 `DEFAULT_TYPES`。 |
