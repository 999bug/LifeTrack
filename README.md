# 生活记（LifeTrack）Android 工程骨架

> 版本：0.1.0 · 文档核对日期：2026-11-06

「生活记」是一个 Local First 的个人生活与身体状态记录 App。MVP 只实现排便事件，但数据层按通用事件 + 业务详情表组织，为后续咖啡、饮茶、饮水、月经、运动和亲密关系模块预留扩展点。

## 当前已实现

- Android 原生工程：Kotlin + Jetpack Compose + Material 3。
- 四个底部入口：今天、记录、统计、设置。
- 快速打卡：点击后立即记录当前时刻。
- 详细记录：时间、布里斯托 1–7 型、排便量、费力程度、便意急迫感、腹部不适、排不净感和备注；详情字段均可空。
- 历史：最近 30 天，按记录发生时保存的本地日期分组，支持编辑和删除。
- 统计：最近 7 天每日次数、总次数和日均次数。
- Room 数据持久化、DataStore 深色模式设置。
- UTC 时间戳 + IANA zoneId + 发生时 localDate；本地日期映射有单元测试。
- Manifest 不申请 INTERNET 权限；显式关闭 Auto Backup，并同时提供旧版备份规则和 Android 12+ 数据提取规则。

## 版本基线（固定版本，不使用动态版本）

| 组件 | 工程版本 | 说明 |
|---|---:|---|
| Android Gradle Plugin | 8.13.2 | 稳定版兼容路线 |
| Gradle | 8.13 | 与 AGP 8.13 系列匹配 |
| JDK | 17 | AGP 要求的最低/默认 JDK 线 |
| Kotlin | 2.4.21 | 2026-10-08 发布的稳定补丁版 |
| Compose Compiler Gradle Plugin | 2.4.21 | 与 Kotlin 插件版本对齐 |
| Compose BOM | 2026.09.00 | 使用稳定 BOM，不单独锁 Compose UI 子库版本 |
| Room | 2.8.5 | 使用 KSP 生成代码 |
| KSP | 2.3.10 | Kotlin 官方 KSP 快速开始示例使用的版本 |
| DataStore Preferences | 1.2.1 | 官方发布页列出的稳定版本 |
| Navigation Compose | 2.10.2 | 稳定版本 |
| Lifecycle | 2.11.0 | 稳定版本 |
| Activity Compose | 1.13.0 | 稳定版本 |
| compileSdk / targetSdk | 36.1 / 36 | minSdk 26 |

为什么没有采用当前最新 AGP 9.4.x：AGP 9 引入 built-in Kotlin，Kotlin/KSP 插件应用方式与 AGP 8.x 有差异。首版以减少构建链复杂度为优先，选用仍处于官方兼容范围的 AGP 8.13.2，并配合 Kotlin 2.4.21。Kotlin 官方兼容表说明 Kotlin 2.4 字节码需要 AGP 8.5.2 或更高；本工程选择的 AGP 8.13.2 高于该下限。后续可以单独升级 AGP 9，不建议在第一次导入工程时同时升级构建链。

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
│       │       │   ├── database/       # Room Entity / DAO / Database / Relation
│       │       │   ├── datastore/      # DataStore 设置
│       │       │   ├── model/          # 时间与通用模型
│       │       │   ├── navigation/     # Compose Navigation 根入口
│       │       │   └── ui/             # Theme
│       │       ├── data/repository/    # Room Repository 实现
│       │       ├── domain/             # Draft / Repository 抽象
│       │       └── feature/
│       │           ├── home/
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

1. 安装 Android Studio 稳定版，并安装 JDK 17、Android SDK Platform 36.1（Android 16 QPR2）和 Build Tools 36。
2. 解压本项目 ZIP，在项目根目录先运行一次 `./gradlew wrapper --gradle-version 8.13`（Windows：`gradlew.bat wrapper --gradle-version 8.13`）。本压缩包内的脚本会引导下载 Gradle 8.13，并生成标准 Gradle Wrapper 文件（包括官方 wrapper JAR）。
3. 在 Android Studio 中选择 **Open**，打开包含 `settings.gradle.kts` 的项目根目录，等待 Gradle Sync。首次同步需要从 Google Maven 和 Maven Central 下载依赖。
4. 连接 Android 8.0（API 26）或更高版本的设备，打开 USB 调试。
5. 选择 `app` 运行配置，点击 Run。

命令行构建（首次运行需要下载 Gradle 8.13）：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Windows 使用 `gradlew.bat`。

说明：此压缩包为源码工程。由于生成环境没有 Android SDK、没有可用的 Gradle 安装且无法联网下载构建依赖，**本次未实际执行 Android Gradle 构建或设备运行**。包内 `gradlew` / `gradlew.bat` 是轻量引导脚本，会在首次执行时下载固定版本 Gradle 8.13；它们不是官方 wrapper JAR 版本。若企业环境不允许脚本下载，请在本机安装 Gradle 8.13 后执行构建，或在 Android Studio 中重新生成官方 Gradle Wrapper。

## 数据模型

- `track_type`：功能类型元数据，MVP 初始化 `poop`。
- `track_event`：通用事件；UUID 主键，保存 typeCode、UTC epoch millis、localDate、zoneId、备注和创建/更新时间。
- `poop_detail`：排便专属详情，以 eventId 一对一关联通用事件；布里斯托类型、排便量、费力程度和身体感受不进入通用表。

每天多次记录就是多条 `track_event`。当天次数通过 COUNT 查询得出，不冗余维护每日计数。

### 时区规则

保存事件时，根据事件发生时所用的 IANA ZoneId 计算 `localDate`。例如同一 UTC instant 在 `Asia/Tokyo` 与 `America/Los_Angeles` 可能属于不同本地日。展示旧记录时使用事件本身的 `zoneId`；修改事件时间时沿用该记录的 zoneId。若用户出国后想按新当地时区修改历史记录，可在之后的版本增加明确的“使用当前时区”选项。

## 本地存储、自动备份和主动导出边界

- 业务数据库位于应用私有目录，不提供网络接口；Manifest 不声明 `INTERNET`。
- `android:allowBackup="false"`，同时提供 `fullBackupContent` 与 Android 12+ `dataExtractionRules`，将数据库、偏好设置、文件和根目录排除在云备份及设备迁移之外。不同厂商设备仍应实机验证系统迁移行为。
- 自动备份关闭意味着用户不能依靠 Google/厂商备份恢复数据。卸载应用、清除应用数据或丢失手机，都可能导致未主动导出的数据永久丢失。
- **当前 MVP 尚未实现主动导出 / 恢复**，设置页面会明确提示这一点。下一迭代应采用用户主动触发的 Storage Access Framework，导出带 schemaVersion 的文件；在加密方案确定后再允许导出敏感数据。恢复时必须校验版本、字段范围和完整性，并在数据库事务中完成导入。

## 安全边界（重要）

本工程目前**没有实现数据库加密**，也没有声称 Room 默认加密。应用私有目录只能提供 Android 沙箱隔离，并不等于数据库静态加密。

在增加月经、性生活等高度敏感模块或面向真实用户发布之前，必须先完成：

1. 数据库静态加密选型与实现（例如 SQLCipher 或经审查的加密数据库方案）。
2. 数据加密密钥的 Android Keystore 管理、设备锁定/生物识别策略和密钥丢失处理。
3. 主动导出文件加密、完整性校验、恢复与密钥轮换方案。
4. Android 自动备份、设备迁移、卸载重装、换机恢复和厂商 ROM 行为评审。
5. 日志、崩溃报告、剪贴板、最近任务屏幕预览及通知内容的隐私审查。

不要在尚未完成上述评审前把真实的敏感数据交给此开发骨架长期保管。

## 当前尚未实现 / 下一阶段清单

- 主动导出 JSON / 加密备份与恢复。
- 应用锁（BiometricPrompt / 系统凭据）。
- 数据库加密。
- 记录编辑页面的原生日期和时间选择器（目前使用 `yyyy-MM-dd HH:mm` 文本输入）。
- 最近 30 天之外的历史分页与日期筛选。
- 月历视图、形态分布图、时间段分析。
- 用户可启用/排序记录项目的模块管理页。
- 咖啡、茶、饮水、运动、月经和亲密关系模块。
- Room schema 导出归档与正式 Migration 测试。
- 无障碍、字体缩放、横屏、深色主题和多尺寸设备完整回归。

## 验收建议

- 开启飞行模式，新增多条记录，强制停止后重新打开，数据仍存在。
- 对同一天进行多次快速打卡，次数应准确累加。
- 修改一条记录的时间跨越午夜，历史分组及统计日期应随之变化。
- 分别在东京与洛杉矶时区测试同一 UTC instant 的 `localDate`。
- 修改备注、形态和详情后，退出并重进应用检查持久化。
- 删除记录后，今日次数、历史列表和 7 天统计都应更新。
- 检查 APK Manifest 确认没有 `android.permission.INTERNET`。
- 在 Android 12+ 设备上验证云备份与设备迁移都不会带走业务数据库。
