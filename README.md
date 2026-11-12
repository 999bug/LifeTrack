# 生活记（LifeTrack）Android 工程骨架

> 最新已发布：0.6.0（2026-11-10，versionCode 6）· `main` 分支含 A10 亲密关系与 A9 月经模块，尚未发版 · 文档核对日期：2026-11-12 · 许可证：MIT

「生活记」是一个 Local First 的个人生活与身体状态记录 App。当前实现排便、饮水、咖啡 / 茶、运动、亲密关系、月经六类事件：排便走专用 `poop_detail` 表，其余五类走通用 `event_detail` 表；数据层按「通用事件 + 可选详情」组织，新增类型只需注册类型与字段规格，不必再升 Room 版本。亲密关系与月经为高敏感模块，**默认停用**，需在「设置 → 记录项目」主动开启；月经另提供一个隐藏底导的经期子应用（日历选日 + 逐行填写）。

## 下载安装

权威发布：https://github.com/999bug/LifeTrack/releases
国内分发镜像（应用内「检查更新」使用）：https://gitee.com/saul999/LifeTrack/releases

- 每个 Release 附 `LifeTrack-<版本名>-release.apk` 与同名 `.sha256` 校验文件；两端是同一份 CI 产物，签名一致。
- 正式签名包，Android 8.0（API 26）及以上可直接安装；系统可能要求允许「安装未知应用」。
- 最新已发布版本 `0.6.0`（versionCode 6，2026-11-10）；校验示例：`Get-FileHash .\LifeTrack-0.6.0-release.apk -Algorithm SHA256`。
- 应用内检查更新以 Gitee 为唯一分发源：只有 APK 与 `.sha256` 两份附件都同步完成、且下载地址预检通过后才提示「可下载」。同步窗口期内只显示「安装包同步中」并按 30s → 1m → 2m → 5m 自动退避重试，不会误报可更新，也不会回退到 GitHub 下载（避免国内用户去下境外包）。Gitee 完全不可用时仅用 GitHub 提供版本信息，需手动前往发布页。
- **发版无需手填版本号**：在 Actions 手动触发 `Release APK`，CI 从最高 tag 自动推 patch+1、从 gradle 推 `versionCode+1`，构建成功后把版本改动提交回 `main`、打 tag 并同步 Gitee。想跳小版本时才在 `version` 输入框手填（如 `v0.7.0`）。详见 [发布与签名指南](docs/release-guide.md) §2。

> 本 README 描述 `main` 分支的当前状态；已发布 APK 的功能可能落后于下文「当前已实现」列表，以下载到的 Release 说明为准。**目前 0.6.0 不含 A10 亲密关系与 A9 月经模块**（两者已合入 `main`，将在下一次发版进入 Release）。

Release 包用固定 keystore 签名，后续版本可覆盖安装。本项目 `allowBackup="false"`，应用内提供 JSON 备份导出 / 恢复（设置页「数据与备份」，默认折叠）；**升级时不要卸载旧版本**——卸载会永久丢失本地记录。发版与密钥管理见 [发布与签名指南](docs/release-guide.md)。

## 当前已实现

- Android 原生工程：Kotlin + Jetpack Compose + Material 3。
- 四个底部入口：今天、历史、统计、设置。
- 快速打卡：点击后立即记录当前时刻。
- 详细记录：原生日期与时间选择器、布里斯托 1–7 型、排便量、费力程度、便意急迫感、腹部不适、排不净感和备注；详情字段均可空。编辑器已抽出通用「事件壳」（日期 / 时间 / 时区 / 备注 / 选择器），各记录类型只需提供详情插槽。
- 历史：默认最近 30 天，可向前分页加载更早记录；**列表按类型分派展示全部模块**（排便、饮水、咖啡 / 茶、运动、亲密关系、月经），支持按日期范围筛选并一键清除，按记录发生时保存的本地日期分组，支持编辑和删除；另有月历视图，格子内显示当天出现过的类型图标（去重后最多 3 个，今天实心高亮），点按任意日期从底部弹出当天全部记录，无记录日显示空态说明。
- 记录详情：今天页与历史页的记录卡片整行可点击，弹出只读底部弹窗，按类型展示完整信息（发生时间与本地日期 / 时区、备注、各类型专属详情字段、创建 / 更新时间与记录 ID）；未填写的字段显式显示「未记录」，卡片原有的编辑 / 删除按钮不变。字段文案由 `RecordDetailPresentation` 统一生成并配单测，时间沿用记录自身的 zoneId；200% 字体下键值行自动上下堆叠。
- 统计：周 / 月 / 年三档切换，可上下翻页查看历史周期；**顶部可按类型筛选（全部 / 各模块），默认全部类型**，与首页、历史、月历同一口径；包含总次数与环比、日均（年视图为月均）、单日最高、最长连续、常见 2 小时时段，图表为自绘 Canvas。新增时段分布（24 根逐小时柱，高亮常见 2 小时窗口）与形态分布（布里斯托、排便量、费力程度、身体感受），跟随当前周期统计；**形态分布只在「排便」口径下展示**，避免与其他类型总数串分母。详情字段可空，按各字段「已填写条数」分别计分母，未填写不计入也不画 0 型柱。
- 首页次数卡：显示今日总次数，并按已启用模块给出各自次数，不再只数排便。
- 备份与恢复：设置页可把全部记录导出为单个 JSON 文件，或从备份恢复；导出/导入走系统文件选择器（SAF），恢复前校验应用标识、schemaVersion、条数、ID 唯一性、时区、本地日期与字段范围，替换在单个数据库事务内完成。默认导出 **v3 加密备份**（口令派生密钥 + AES-256-GCM），也可显式选择明文 v2；仍可读取 v1 / v2 旧备份。加密备份可「更换备份口令」：旧口令解出后用新口令重加密，另存为新文件，原文件保留。v2 增加通用详情通道，遇到本版本尚未落库的详情类型会明确拒绝导入，不会静默丢数据。
- 记录项目：设置 → 记录项目（独立页）列出已注册类型，可启用/停用并调整顺序；启用状态持久化，停用只隐藏新增记录入口，已有历史记录仍可查；调整后的顺序同时决定首页记录入口的排列；全部停用时首页给出提示与设置入口。
- 应用锁：设置页「安全」可开启，默认关闭；可选切到后台立即 / 1 分钟 / 5 分钟后要求验证，使用系统指纹或锁屏密码，应用不保存任何生物识别信息。进程被杀后重新打开必须先解锁；截屏与最近任务预览不做限制。
- 设置页：按使用频率分层——无标题主卡（记录项目 / 外观）、「安全」、默认折叠的「数据与备份」、「更多」（更新日志 / 开源许可 / 数据与隐私）、紧跟其后的「应用更新」独立卡、页脚（版本号 / 免责文案）。行式布局、行内 20dp 图标；外观改为单行入口 + 三档弹层，主题切换即时生效；备份的导出 / 恢复 / 换口令与风险提示收在折叠区内，导出或恢复进行中、完成、失败时会自动展开，不把状态藏起来；应用更新紧跟「更多」最后一行「数据与隐私」，版本号与免责文案才落在整页最底部，有可下载新版本时「更多 → 更新日志」行同步出现标记；「更多 → 更新日志」点开只列最近 6 个版本并注明完整历史随版本累积，当前安装版本标「当前」。
- 截屏与录屏：**不做任何限制**。应用锁与高敏感模块（「亲密关系」「月经」）都不会给窗口加 `FLAG_SECURE`，系统截屏 / 录屏照常可用，最近任务预览也显示页面内容；高敏感记录只在列表与详情做读屏收敛，是否把截图发给别人由使用者自己判断。设置页因此不再有「截屏与录屏」状态行，启用敏感模块时也不再弹截屏说明。
- 饮水模块（试点）：新增「记录饮水」入口，可填写数量与显示单位；今日记录列表按类型分派展示，支持编辑与删除。数据落在通用 `event_detail` 表，导出/恢复走备份 v2 的通用详情通道。历史页、月历与统计均按全部类型口径统计，可在统计页切到「饮水」单独查看。
- 咖啡 / 茶模块：新增「记录咖啡 / 茶」入口，可填写饮品类型（咖啡 / 茶）、份量、单位与咖啡因标记（含咖啡因 / 低因 / 无咖啡因）；与饮水共用通用详情表与备份 v2 通道，字段取值由规格表逐项校验。新增该类型未升 Room、未改备份格式。
- 运动模块：新增「记录运动」入口，可填写运动类型（步行 / 跑步 / 骑行 / 游泳 / 力量 / 其他）、时长（1–1440 分钟）与强度（低 / 中 / 高）；时长展示超过 60 分钟自动换算为小时，不做热量或配速推算。沿用同一扩展点，**只注册类型与字段规格**，Room 仍 version 2、备份仍 schemaVersion 2。
- 亲密关系模块（A10）：新增「记录亲密关系」入口，三项均可不填——保护措施（是 / 否 / 不适用）、主观感受（好 / 一般 / 差）、时长（1–1440 分钟）；**不记录伴侣姓名或任何可识别身份的信息**。默认停用，启用后今日列表只显示类型与时间、不显示详情或备注，需点开详情弹窗才展开；读屏对列表与详情各收敛为一句汇总。明文导出确认会点明文件包含亲密关系记录。存储层仍只注册类型与字段规格，Room 仍 version 2、备份版本不变。
- 月经模块（A9，E1 核心闭环）：新增第 6 个类型 `menstruation` 与一个**隐藏底导的经期子应用**——首页「月经」模块卡（右下角为「打开」）整卡点击进入，不是编辑器。子应用日历置顶、可翻到未来月份，点任意一天在下方**逐行填写**流量 / 痛经 / 症状 / 心情 / 分泌物与日记（日记即该条记录的备注 `note`），点行展开、即点即存；「大姨妈来了 / 走了」与参考 App 同款交互：点一次「来了」记下起点，之后**每天自动标记**、日历整段连续染色，不需要逐日操作；点「走了」才结束，结束日以点的那天为准，中间缺失日一次性补齐（仅标记，已有记录与详情不覆盖）；起点距今天超过 14 天时只标当天、不向后延续，避免误触把整月染成经期。周期段、周期 / 经期长度中位数、当前阶段与未来最多 3 个预测窗口全部由 `CycleCalculator` 从已记录日期派生、**不落库**；有效样本 <3 段时按设置值估算并明确标「按设定值估算」。本周期简报与周期长度趋势只统计已填写内容，不推算健康结论、不给调理建议，不做排卵期 / 安全期 / 受孕概率。默认停用；启用后列表与读屏收敛、明文导出点名，与 A10 共用 `SensitiveTypes` 登记。只注册类型与字段规格，Room 仍 version 3、备份格式不变。
- Room 数据持久化、DataStore 深色模式设置。
- UTC 时间戳 + IANA zoneId + 发生时 localDate；本地日期映射有单元测试。
- 为应用内「检查更新」与安装，申请了 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES` 权限；该网络能力**仅**用于查询 Gitee Releases 最新版本、预检并下载本应用 APK 及其 `.sha256`（Gitee 完全不可用时只向 GitHub 查询版本信息，不下载），不发送任何用户数据、设备标识或遥测。应用锁另申请普通权限 `USE_BIOMETRIC`，只用于调用系统指纹 / 锁屏密码验证，不读取或保存生物特征数据。显式关闭 Auto Backup，并同时提供旧版备份规则和 Android 12+ 数据提取规则。

## 版本基线（固定版本，不使用动态版本）

| 组件 | 工程版本 | 说明 |
|---|---:|---|
| Android Gradle Plugin | 9.3.1 | 满足 AndroidX 依赖要求的 AGP ≥ 9.1；仍在 Kotlin 2.4.21 官方兼容表（AGP 上限 9.3.1）之内 |
| Gradle | 9.6.1 | AGP 9.3.1 要求 Gradle ≥ 9.5.0；官方 Wrapper JAR |
| JDK | 17 | AGP 要求的最低/默认 JDK 线（本机实测用 JDK 21 运行 Gradle 亦可） |
| Kotlin | 2.4.21 | 2026-10-08 发布的稳定补丁版；AGP 9 内置 Kotlin，版本经 buildscript classpath 覆盖 |
| Compose Compiler Gradle Plugin | 2.4.21 | 与 Kotlin 插件版本对齐 |
| Compose BOM | 2026.09.00 | 使用稳定 BOM，不单独锁 Compose UI 子库版本 |
| Room | 2.8.5 | 使用 KSP 生成代码 |
| KSP | 2.3.13 | AGP 9 内置 Kotlin 需要 KSP ≥ 2.3.1 |
| DataStore Preferences | 1.2.1 | 官方发布页列出的稳定版本 |
| kotlinx.serialization JSON | 1.12.0 | 备份文件编解码；插件版本与 Kotlin 2.4.21 对齐 |
| Navigation Compose | 2.10.2 | 稳定版本 |
| Lifecycle | 2.11.0 | 稳定版本 |
| Activity Compose | 1.13.0 | 稳定版本 |
| compileSdk / targetSdk | 37 / 36 | minSdk 26；Compose BOM 2026.09.00、Lifecycle 2.11.0、Navigation 2.10.2 均要求 compileSdk ≥ 37 |

为什么最终采用 AGP 9：初版曾按「减少构建链复杂度」的思路固定 AGP 8.13.2 + compileSdk 36.1，但当前 AndroidX 全线（Compose BOM 2026.09.00 解析出 compose-ui 1.12.1、Lifecycle 2.11.0 的 `*-compose` 构件、Navigation Compose 2.10.2）的 AAR 元数据明确要求 **AGP ≥ 9.1.0 且 compileSdk ≥ 37**，`:app:checkDebugAarMetadata` 会因此失败。因此改为 AGP 9.3.1 + compileSdk 37，并完成 built-in Kotlin 迁移：

- 移除 `org.jetbrains.kotlin.android` 插件（AGP 9 重复应用会直接报错），保留 `org.jetbrains.kotlin.plugin.compose`。
- AGP 9 自带的 KGP 版本低于工程目标，故在根 `build.gradle.kts` 的 `buildscript` classpath 中显式指定 `kotlin-gradle-plugin:2.4.21`。
- targetSdk 保持 36，不与 compileSdk 一起跳，避免提前引入运行时行为变更。

官方核对入口：

- AGP 版本 / Gradle 兼容表：https://developer.android.com/build/releases/about-agp
- Kotlin 发布记录：https://kotlinlang.org/docs/releases.html
- Kotlin 与 AGP 兼容表：https://developer.android.com/build/kotlin-support
- Compose Compiler 插件：https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM：https://developer.android.com/develop/ui/compose/bom
- Room：https://developer.android.com/jetpack/androidx/releases/room
- DataStore：https://developer.android.com/jetpack/androidx/releases/datastore
- Navigation：https://developer.android.com/jetpack/androidx/releases/navigation
- Lifecycle：https://developer.android.com/jetpack/androidx/releases/lifecycle
- KSP：https://kotlinlang.org/docs/ksp-quickstart.html

以上版本按 2026-11-06 官方发布页核对。升级任何插件或依赖后，都应执行完整构建和真机回归，不要直接使用 `+` 动态版本。

## 工程结构

```text
LifeTrack/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/example/lifetrack/
│       │       ├── core/
│       │       │   ├── backup/         # 备份编解码、校验与读写边界
│       │       │   ├── database/       # Room Entity / DAO / Database / Relation
│       │       │   ├── datastore/      # DataStore 设置
│       │       │   ├── model/          # 时间与通用模型
│       │       │   ├── navigation/     # Compose Navigation 根入口
│       │       │   └── ui/             # Theme
│       │       ├── data/repository/    # Room Repository 实现
│       │       ├── domain/             # Draft / Repository 抽象
│       │       └── feature/
│       │           ├── home/
│       │           ├── menstruation/   # 经期子应用（A9）
│       │           ├── poop/
│       │           ├── history/
│       │           ├── statistics/
│       │           └── settings/
│       ├── test/                       # 本地单元测试
│       └── androidTest/                # Room 集成测试
├── gradle/wrapper/gradle-wrapper.properties
├── settings.gradle.kts
└── build.gradle.kts
```

首版保持单 Gradle Module，包按功能和职责划分。未来出现多团队协作或构建时间问题时，再考虑拆成独立 Gradle Module。

## 导入和运行

1. 安装 Android Studio 稳定版，并安装 JDK 17、Android SDK Platform 37.0 和 Build Tools 36。
2. 在 Android Studio 中选择 **Open**，打开包含 `settings.gradle.kts` 的项目根目录，等待 Gradle Sync。首次同步需要从 Google Maven 和 Maven Central 下载依赖。
3. 连接 Android 8.0（API 26）或更高版本的设备，打开 USB 调试。
4. 选择 `app` 运行配置，点击 Run。

命令行构建（首次运行需要下载 Gradle 9.6.1）：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Windows 使用 `gradlew.bat`。

说明：`gradle/wrapper/` 内包含官方 `gradle-wrapper.jar`，`gradlew` 是官方脚本，不再依赖自制的下载引导脚本。本机实测（2026-11-09，Windows 11 + JDK 21）：

- `./gradlew :app:assembleDebug` 通过，产物 `app/build/outputs/apk/debug/LifeTrack-0.6.0-debug.apk`（约 30 MB）。APK 命名规则为 `LifeTrack-<versionName>-<buildType>.apk`，由 `androidComponents.onVariants` 设置。
- `./gradlew :app:testDebugUnitTest` 通过。2026-11-09 基线 151 项：`RecordTimeMapperTest` 3、`StatsAggregatorTest` 14、`BackupCodecTest` 14、`BackupCodecV2Test` 13、`BackupWaterDetailTest` 6、`BackupCoffeeDetailTest` 7、`BackupSportDetailTest` 5、`DetailSpecTest` 11、`PickerDateMapperTest` 5、`CalendarMonthTest` 6、`RecordDetailCodecTest` 5、`CoffeeFieldsTest` 5、`SportFieldsTest` 3、`DetailDistributionTest` 6、`ModuleOrderingTest` 5、`GitHubReleaseCheckerTest` 7、`RecordDetailPresentationTest` 11、`ReleasePayloadParserTest` 6、`ReleaseFetcherTest` 6、`ApkFileValidatorTest` 3、`PassphraseEnvelopeTest` 6、`SqliteFileHeaderTest` 4。2026-11-10 A10 实测 **29 个类 / 204 项 / 0 失败**（新增 `IntimacyFieldsTest` 4、`BackupIntimacyDetailTest` 6，`DetailSpecTest` +1、`RecordDetailPresentationTest` +2，以及 C 包新增用例）。2026-11-12 A9 实测 **336 项 / 0 失败**（新增 `MenstruationFieldsTest` 9、`PeriodMonthTest` 7、`PeriodDayFormTest` 13、`MenstrualCycleTest` 16、`PeriodFillTest` 9、`BackupMenstruationDetailTest` 12，共 66 项）。
- `./gradlew :app:lintDebug` 通过（无 error，仅版本提示类 warning），已接入 CI 与发布流水线。
- `./gradlew :app:connectedDebugAndroidTest` 通过，共 14 个用例（`LocalDatabaseTest` 3 + `MigrationTest` 2 + `DatabaseKeyStoreTest` 5 + `DatabaseEncryptionTest` 4）：1→2 真实迁移、按类型过滤查询、通用详情读写与级联删除、Keystore 封装/篡改/密钥缺失、明文 v1/v2 库加密搬迁、中断恢复与错误口令拒绝。

未验证项：仪器测试目前只在模拟器（API 37）跑过，真机仪器测试尚未执行；真机「卸载重装丢数据」验证仍待补。真机安装、应用内检查更新、0.4.0 → 0.5.0 覆盖安装，以及 **0.5.0 → 0.6.0 加密版覆盖安装与首次启动迁移**均已由用户 2026-11-10 确认通过；模拟器上用 0.4.0 明文库（含未落盘 WAL）做的加密覆盖安装迁移也已实测通过。

## 数据模型

- `track_type`：功能类型元数据，MVP 初始化 `poop`。
- `track_event`：通用事件；UUID 主键，保存 typeCode、UTC epoch millis、localDate、zoneId、备注和创建/更新时间。
- `poop_detail`：排便专属详情，以 eventId 一对一关联通用事件；布里斯托类型、排便量、费力程度和身体感受不进入通用表。

每天多次记录就是多条 `track_event`。当天次数通过 COUNT 查询得出，不冗余维护每日计数。

Room 现在是 **version 3**（`app/schemas/1.json` + `2.json` + `3.json`）。`MIGRATION_1_2` 新增 `event_detail` 通用详情表与索引，`MIGRATION_2_3` 补 `localDate` 索引以修范围查询全表扫，均不改写存量数据；后续新增记录类型（含 A9 月经）只需注册类型与字段规格，不必再升 Room 版本。备份快照使用独立的 `BackupRecord = 事件 + 可选 RecordDetail` 模型，与 Room 的 `@Relation` 解耦。

### 时区规则

保存事件时，根据事件发生时所用的 IANA ZoneId 计算 `localDate`。例如同一 UTC instant 在 `Asia/Tokyo` 与 `America/Los_Angeles` 可能属于不同本地日。展示旧记录时使用事件本身的 `zoneId`；修改事件时间时沿用该记录的 zoneId。若用户出国后想按新当地时区修改历史记录，可在之后的版本增加明确的“使用当前时区”选项。

## 本地存储、自动备份和主动导出边界

- 业务数据库位于应用私有目录，不提供数据上传接口；`INTERNET` 权限仅用于应用内「检查更新」向 `gitee.com` 查询 Gitee Releases 最新版本、对 APK 直链发 HEAD 预检、下载 APK 与 `.sha256`（仅 Gitee 源；Gitee 完全不可用时只向 `api.github.com` 查询版本信息），不发起任何其它网络请求、不上传数据。设置页的「查看发布页」是用户主动点击后调起浏览器，不是应用自身发起的请求。
- `android:allowBackup="false"`，同时提供 `fullBackupContent` 与 Android 12+ `dataExtractionRules`，将数据库、偏好设置、文件和根目录排除在云备份及设备迁移之外。不同厂商设备仍应实机验证系统迁移行为。
- 自动备份关闭意味着用户不能依靠 Google/厂商备份恢复数据。卸载应用、清除应用数据或丢失手机，都可能导致未主动导出的数据永久丢失。
- **已实现主动导出 / 恢复**：设置页 → 数据与备份（默认折叠），通过 Storage Access Framework 由用户选择保存位置。文件含 `schemaVersion`、类型表与全部记录（含详情）；恢复时校验应用标识、版本号、事件条数、ID 唯一性、时区、本地日期与字段范围，任一不通过即整体拒绝，替换在单个 Room 事务内完成。
- **默认导出加密备份（v3）**：`PBKDF2WithHmacSHA256`（210000 次迭代）派生 256 位密钥，`AES-256-GCM` 加密正文；算法、迭代次数、随机盐（16 B）、随机 IV（12 B）与导出时间都纳入 AAD，改动任何头部字段或密文都会导致解密失败。口令不落盘、不进日志，**遗失后无法恢复**；设置页导出时输入两次，恢复时输一次，并按长度与字符种类提示「偏弱 / 可用 / 较强」。连续输错会触发等待（2s → 4s → 8s → 16s → 30s），冷却期间按钮置灰。
- **明文导出需二次确认**：明文文件不加密，能读到文件的人就能查看全部记录，导出前会给红色风险提示。需要与 0.5.0 及更早版本互通时才建议使用。
- **更换备份口令**：设置页选一份 v3 加密备份，输入当前口令与新口令，应用会用新口令重新加密（换新的盐与 IV），自检能解回后才通过 SAF 另存为 `...-rekeyed.json`；**原文件不删不改**，确认新文件能恢复后再自行删除。明文备份没有口令可换，会被明确拒绝。
- **版本兼容性（0.5.0 及更早）**：`main` 分支已接入 SQLCipher 4.20.0 整库加密，并已随 0.6.0（versionCode 6，2026-11-10）发布；0.5.0 及更早版本仍是明文库。升级时首次启动自动把明文 Room v2 库原地加密搬迁，保留 `user_version`、WAL 中未落盘的数据与全部记录，升级不卸载不丢数据；从 Room v1 直接跳过来的用户会自动补跑 `MIGRATION_1_2`。0.6.0 起仍可恢复 v1 / v2 明文备份，但**默认导出的 v3 加密备份无法被 0.5.0 打开**：需要与旧版互通时请显式选择明文导出。**加密库也无法被 0.5.0 或更早版本打开**：需要回退请先导出备份，不要卸载重装旧版。详见 [安全加固设计前确认单](docs/security-hardening-confirmation.md) §2.6 与 §2.7。
- **A10 记录的单向兼容**：含 `intimacy` 详情的备份在 0.6.0 及更早版本会被明确拒绝导入并提示升级，不会静默丢数据；旧版备份在新版恢复时会补进一条默认停用的「亲密关系」类型。需要与旧版互通时，请先导出不含该类记录的备份。
- **A9 记录的单向兼容**：含 `menstruation` 详情的备份在不含 A9 的旧版本会被明确拒绝导入并提示升级，不会静默丢数据；旧版备份在新版恢复时会补进一条默认停用的「月经」类型。需要与旧版互通时，请先导出不含该类记录的备份。

## 安全边界（重要）

数据库加密已随 **0.6.0（2026-11-10）发布**（SQLCipher 4.20.0 + Android Keystore 密钥管理）；0.5.0 及更早版本仍是明文库。导出备份默认加密，应用锁与隐私审查已完成。下列评审项除真机厂商 ROM 迁移走查（C8 / B8）外均已销项。

1. ✅ 数据库静态加密（0.6.0 已发布，用户真机覆盖安装与加密库迁移已确认）。
2. ✅ 数据加密密钥的 Keystore 管理（不绑定生物识别，避免密钥失效导致数据不可读）。
3. ✅ 主动导出文件加密、完整性校验与口令轮换（v3，AES-256-GCM + AAD；C4–C6）。
4. 🟡 Android 自动备份、设备迁移、卸载重装、换机恢复：`allowBackup=false` 与两份排除规则已复核，「卸载重装 + 加密备份恢复」已模拟通过；**真机厂商迁移 / ROM 差异仍待走查**（C8 / B8）。
5. ✅ 日志、崩溃报告、剪贴板与通知内容的隐私审查（C9；截屏与最近任务预览不做限制）。
6. 应用内「检查更新」的网络行为（HTTPS GET `gitee.com/api/v5/repos/saul999/LifeTrack/releases/latest`；对 Gitee APK 直链发 HEAD；下载 Gitee 的 APK 与 `.sha256`；Gitee 完全不可用时 HTTPS GET `api.github.com/repos/999bug/LifeTrack/releases/latest` 仅取版本信息）已评审：不携带用户数据、设备标识、Cookie 或遥测，符合 Local First 原则——用户数据仍只在本地。这是项目放开 `INTERNET` 权限的唯一场景；新增任何其它网络能力仍需回到本安全边界重新评审。

**系统备份 / 迁移边界**：`allowBackup=false`，`fullBackupContent` 与 Android 12+ `dataExtractionRules` 把 `database` / `sharedpref` / `file` / `external` / `root` 五个域在「云备份」与「设备迁移」两条链路都排除。Keystore 密钥不属于应用数据，不会随备份离开设备；因此系统迁移既不会带走加密库，也不会留下「有库没钥匙」的半套数据。换机唯一可靠的路径是**导出加密备份 → 新设备安装 → 输入口令恢复**；卸载或清除数据仍会丢失未导出的记录。

C8 / B8 的真机厂商 ROM 迁移走查尚未完成，在此之前不要依赖系统备份 / 换机迁移来保管数据；换机请走「导出加密备份 → 新设备安装 → 输入口令恢复」。

## 当前尚未实现 / 下一阶段清单

> 2026-11-10 更新：安全加固包（C）C1–C10 已完成并随 0.6.0 发布，仅 C8 / B8 的真机厂商 ROM 迁移走查仍为 🟡；选型结论、子任务拆解与回滚方案见 [安全加固设计前确认单](docs/security-hardening-confirmation.md)，进度见 [剩余工作子任务台账](docs/PROGRESS.md) 第 4 节。下列功能**仍未实现**。

- C8 / B8：真机厂商 ROM 的系统备份、设备迁移与换机恢复行为走查（C9 隐私审查已完成）。
- 月经模块 E2 / E3（E1 核心闭环已于 2026-11-12 完成并自测通过）：体重 / 体温等额外字段、自定义症状、完整报告（E2），以及通知提醒、备孕 / 怀孕模式（E3）均未做；分期与取舍见 [A9 设计文档](docs/menstruation-module-design.md)。
- 无障碍读屏顺序与横屏 / 平板 / 厂商 ROM 的完整回归（字体缩放 200% 已在统计页、历史页、三个编辑器与经期子应用上修复并实测，其余页面仍需逐页复核）。
- 真机验收（安装、SAF 导出恢复、飞行模式持久化、卸载重装数据丢失验证）。

## 验收建议

- 开启飞行模式，新增多条记录，强制停止后重新打开，数据仍存在。
- 对同一天进行多次快速打卡，次数应准确累加。
- 修改一条记录的时间跨越午夜，历史分组及统计日期应随之变化。
- 在历史页加载更早的分页、筛选一个空区间（应显示空状态）、再清除筛选（应恢复到最近 30 天）。
- 分别在东京与洛杉矶时区测试同一 UTC instant 的 `localDate`。
- 修改备注、形态和详情后，退出并重进应用检查持久化。
- 导出备份后新增一条记录，再从该备份恢复，确认记录数与详情回到导出时的状态；用其他 App 的 JSON 或手工改坏的备份恢复，应被拒绝并给出原因。
- 导出时保持默认「加密」，确认文件里看不到任何记录明文、`schemaVersion` 为 3；用错误口令恢复应被拒绝并提示口令错误或文件损坏，用正确口令应能进入恢复确认。
- 选择「明文」导出应出现风险确认；导出的 v2 文件恢复时不询问口令，可直接进入恢复确认。
- 对一份加密备份执行「更换备份口令」：旧口令输错应被拒绝；成功后新文件只能用新口令恢复，旧文件仍可用旧口令恢复，且两份文件的盐 / IV 不同。
- 导出时输入 8 位纯字母口令应提示「偏弱」，加长或混合数字后变「较强」；恢复时连续输错 4 次应出现「请等待 N 秒」且按钮不可点，倒计时结束后恢复可点。
- 设置 → 安全开启应用锁（设备需已设 PIN / 指纹）：按所选超时在切后台后要求验证；强停后重开必须先解锁；锁屏页不应出现任何记录文本；开启期间系统截屏与最近任务预览应照常可用。
- 把导出文件里的 `schemaVersion` 改成 `1` 并删掉 `detail` 字段当作旧备份恢复，应成功且详情保真；在 v2 文件里塞入当前版本不支持的 `detail`，应被明确拒绝并提示升级，而不是「恢复成功」。
- 删除记录后，今日次数、历史列表和统计页都应更新。
- 切到月历视图翻到上个月再翻回，格子次数应与统计页「全部」口径下对应月份一致；点按有记录的日期，列表应只剩当天记录清除筛选后恢复最近 30 天。
- 在「排便」口径下，只有快速打卡（无详情）的周期，形态分布应显示「已填写 0 / 共 N 条」而不是画出 0 型柱；有详情的周期，Bristol 各柱之和应等于卡面标注的已填写数且不超过总次数。
- 新增运动记录（80 分钟 / 骑行 / 中等强度）后，首页次数卡总数应 +1 且「运动」+1，历史列表应出现「1 小时 20 分 · 中等强度」，统计页切到「运动」应看到同一条。
- 统计页把口径从「全部」切到某个类型，「共记录」「日均」「单日最高」「常见时段」应一起变化；切到「排便」才出现形态分布卡，其他口径不显示。
- 在设置 → 记录项目中停用某个模块后，首页应隐藏对应入口并显示提示；重新启用或重启应用后按钮恢复，已有记录不丢失；排序箭头在只有一个项目/首尾项时按预期禁用；上移「运动」后首页入口顺序应同步变化。
- 新增饮水、咖啡 / 茶或运动记录后，其余模块的今日次数不应增加（首页按类型分列，统计按口径过滤）；导出备份中该记录应带 `detail.fields`，把份量 / 时长改成越界值、把枚举改成非法值或塞入未注册字段后恢复，应被拒绝且不破坏现有数据。
- 检查 APK Manifest 确认 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES` 仅用于「检查更新」、`USE_BIOMETRIC` 仅用于应用锁调用系统验证（代码注释与 README 已声明用途），且不存在其它数据上传或生物特征读取行为。
- 开启 TalkBack 走一遍首页 → 记录 → 统计 → 设置：首页次数卡应只念一句汇总，周 / 月 / 年与列表 / 月历应念出当前选中项，月历格应念出「几月几日、几次」，形态分布与时段分布应各念一句汇总而不是逐个柱子。
- 在 Android 12+ 设备上验证云备份与设备迁移都不会带走业务数据库。
- 在「设置 → 记录项目」启用「亲密关系」：首次启用出现一次性提示；首页出现第五个入口；停用后入口消失、已有记录仍可在历史查看。
- 新增一条三项都不填的亲密关系记录，今日列表只显示类型与时间；点开详情弹窗，三项均显示「未记录」；TalkBack 在列表与详情弹窗各只念一句汇总，不逐字段朗读。
- 开启「亲密关系」且关闭应用锁时，系统截屏与最近任务预览应正常显示页面内容；明文导出确认文案点明包含亲密关系记录。
- 「设置 → 安全」不再有「截屏与录屏」状态行；启用「亲密关系」时不弹任何截屏说明，系统截屏立即可用。
- 在「设置 → 记录项目」启用「月经」：首页出现「月经」模块卡（右下角为「打开」而非「详细」），点整卡进入经期子应用而非编辑器；日历默认选中今天，下方逐行填写区可见；停用后入口消失、已有记录仍可在历史查看。
- 在子应用点「大姨妈来了」：当天格子立刻变成经期色实心、状态条显示「经期第 1 天」；此后不再做任何操作，次日打开应自动变成「第 2 天」且日历连续染色，中间缺失日已入库；点「大姨妈走了」后停在结束日，之后不再延伸；起点距今超过 14 天时只标当天、不延续。
- 在子应用选一个更早的未记录日再点「走了」，应自动补齐中间缺失日并提示补齐天数，已有详情的日期不被覆盖。
- 点行展开流量 / 症状 / 日记并选择或输入，应即时保存且连续点选不重置滚动；强停重进后选中日的填写内容仍在；翻到未来月份点未来日期应只读、不可填写。
- 启用「月经」且关闭应用锁时系统截屏应正常出图；明文导出二次确认文案点明包含月经记录。

## 相关文档

- [本地验证与在线升级方案](docs/local-verification-and-upgrade.md)：改完代码怎么在模拟器上验证、装到手机后怎么升级。
- [发布与签名指南](docs/release-guide.md)：签名密钥准备、版本号自动递增与 CI 提交机制、Release 出包步骤、下载校验与回滚。
- [发版流水线配方](docs/release-pipeline-recipe.md)：供规范归纳的机器可读事实、按项目类型的移植矩阵、通用不变量与自动快照生成 / 自检方式。
- [统计增强包设计前确认单](docs/statistics-enhancement-confirmation.md)：月历视图、形态分布与时段分布的口径、影响面与实施记录。
- [模块扩展第二批设计前确认单](docs/module-extension-confirmation.md)：通用编辑器壳、通用详情模型、备份 v2 兼容口径与实施记录。
- [饮水模块试点设计前确认单](docs/water-module-confirmation.md)：Room v2 迁移、通用详情表、water 规格注册与验收结果。
- [A7 咖啡 / 茶模块设计前确认单](docs/coffee-module-confirmation.md)：单类型 + 枚举字段的取舍、四字段规格、跨面板口径与验收结果。
- [A8/A11/A12 + B1/B2/B4/B9/B11 收尾确认单](docs/module-remaining-confirmation.md)：运动字段规格、统计类型筛选口径、字段校验收口、迁移测试与 CI 门禁的实施记录。
- [设置页重构记录](docs/settings-redesign-confirmation.md)：信息架构、记录项目独立页、宽屏限宽与无障碍触摸目标的取舍与验收。
- [设置页重构 v2 设计与原型](docs/settings-redesign-v2.md)：按使用频率分层、数据折叠、版本沉底的设计依据、可交互原型与落地验收。
- [剩余工作子任务台账](docs/PROGRESS.md)：A/B/C 三包共 33 项子任务的交付物、依赖与状态。
- [C 包安全加固设计前确认单](docs/security-hardening-confirmation.md)：加密选型与构建级 POC 结论、C1–C10 逐项子任务、收益/代价/回滚与待拍板决策。
- [A10 亲密关系模块设计前确认单](docs/intimacy-module-confirmation.md)：高敏感模块的隐私等级、默认停用、三项可空字段、按类型分级遮蔽与备份兼容口径。
- [A9 月经模块设计文档](docs/menstruation-module-design.md)：经期子应用的信息架构、周期派生算法、日历置顶 + 逐行填写交互、隐私收敛与 E1/E2/E3 分期；E1 已实现。

## 许可证

本项目采用 [MIT License](LICENSE)。可以自由使用、修改、分发，包括商业用途，但需保留版权与许可声明。

数据模型与产品设计同样开源，欢迎基于它做自己的本地优先记录工具。

## 第三方许可

本应用使用 **SQLCipher Community Edition**（`net.zetetic:sqlcipher-android`）实现数据库静态加密。Community Edition 采用 BSD-style 许可，要求在用户可访问的位置提供版权声明与许可全文；应用内入口为 **设置 → 更多 → 开源许可**。

```text
Copyright (c) 2008-2026, ZETETIC, LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

SQLCipher 内含或依赖的开源组件：

| 组件 | 许可 |
| --- | --- |
| SQLite / SQLite Android Bindings | 公有领域（SQLite Project） |
| OpenSSL | Apache License 2.0 或 OpenSSL / SSLeay 双许可（OpenSSL Project） |
| LibTomCrypt | 公有领域（LibTomCrypt project） |
| Android 平台库 | Apache License 2.0（Android Open Source Project） |

许可原文：<https://www.zetetic.net/sqlcipher/license/SQLCIPHER_COMMUNITY_EDITION_LICENSE.txt>
