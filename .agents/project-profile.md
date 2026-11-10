# 项目档案（project-profile）

> 每个项目只改这一个文件。写 `未定` 表示还没确认——去确认，不要猜。
> 这里的事实优先于通用规范；通用规范不写项目事实。
>
> 安装时已按探测结果填过初稿。还剩「未定」的，跑 `/ai-dev-standards:profile` 补齐。
> **升级不会改这个文件**；需要按新模板重整时，手动跑同一条命令。
>
> 生成方式：detect-profile.sh 探测 + 用户确认（2026-11-06）。未跑通的验证项与待确认项见 §9。

## 0. 遇到「未定」怎么办

**【红线】不许猜。** 没填完的 profile 比没有更危险：AI 会拿示例值当事实，一路错下去。

遇到 `未定` 就停下来问，一次问清。只有三种情况可以不等：用户已给出等价信息、该项与当前任务无关、用户说"先按默认走"。

判定标准：**猜错的代价 > 多问一句的成本时，必须问。**

## 1. 基本信息

```yaml
project: LifeTrack
type: oss                          # GitHub 开源项目
visibility: public
description: 「生活记」：Local First 的个人生活与身体状态记录 App
```

`type` 决定提交身份、敏感信息尺度、发布链路。下面各节按类型给默认值，不符就显式写出来。

## 2. 技术栈

```yaml
languages:                         # 只填实际用到的，版本写实际值
  kotlin: 2.4.21                   # 64 个 .kt（main）+ 16 个单测 + 2 个仪器测试，0 个 .java
  java: 17                         # 仅 JVM target / compileOptions 版本
framework: Android (Jetpack Compose + Material 3)   # Room 2.8.5 + KSP、DataStore、Navigation Compose
package_manager: gradle
build_command: ./gradlew :app:assembleDebug     # 已验证通过（2026-11-09，产物 app/build/outputs/apk/debug/LifeTrack-0.4.0-debug.apk）
test_command: ./gradlew :app:testDebugUnitTest   # 已验证通过（2026-11-09，115 项）
dev_command: ./gradlew :app:installDebug        # 已通过 adb install -r 实测（emulator-5554，API 37）
```

## 3. 提交与推送

```yaml
git_user_name: saul
git_user_email: 57277097+999bug@users.noreply.github.com
remote: https://github.com/999bug/LifeTrack   # origin，main 为默认分支
commit_style: conventional         # feat: / fix: / chore:
issue_tracker: GitHub Issues
auto_commit: true                  # 改完自动提交，不必每次问
push_policy: on-request            # 只提交，push 等用户授权
```

## 4. 交付与发布

```yaml
deploy_target: GitHub Releases（权威）+ Gitee Releases（国内分发镜像）  # 同一份签名 APK，CI 同步附件，无服务端
release_trigger: manual            # Actions workflow_dispatch 手动填版本号触发
version_scheme: 语义化 semver       # versionName 0.5.0 / versionCode 5（待发布），已发布 v0.1.0 ~ v0.4.0
registry: none
pages: no
```

## 5. 敏感信息

```yaml
secrets_location: GitHub Repository secrets   # 4 个签名 secret + GITEE_TOKEN（Gitee 私人令牌），见 §9
never_commit:
  - local.properties               # 本机 SDK 路径
  - captures/                      # 设备截图
  - "*.iml"
internal_endpoints:
  allowed_in_docs: false
```

## 6. 作用域与验证

```yaml
ai_scope:
  - app/src/main/java
  - app/src/main/res
log_language: en
verify_build: ./gradlew :app:assembleDebug       # 已验证（2026-11-09）
verify_test: ./gradlew :app:testDebugUnitTest    # 已验证（2026-11-09，115 项全绿）
verify_lint: ./gradlew :app:lintDebug            # 已验证（2026-11-09，无 error）
verify_instrumented: ./gradlew :app:connectedDebugAndroidTest   # 已验证（2026-11-09，emulator API 37，5 项全绿）
verify_test_policy: full
test_port: 9099
# 有冒烟/版本/UI 检查就补：verify_smoke、verify_version、verify_ui，写命令
```

`verify_*` 为空时按通用规范兜底：构建 + 测试 + 起服务实测。填了就必须跑填的那条。

## 7. 项目自有文档

```yaml
related_docs:                      # 先读这些再动手，优先级高于通用建议
  - README.md                      # 已实现范围、版本基线、安全边界、验收建议
  - docs/PROGRESS.md               # 剩余工作子任务台账与风险，跨会话接手先看这个
  - docs/release-guide.md          # 签名密钥、出包步骤、下载校验与回滚
  - docs/local-verification-and-upgrade.md   # 模拟器验证与手机升级流程
  - docs/statistics-enhancement-confirmation.md   # 统计口径与实施记录
  - docs/module-extension-confirmation.md        # 通用编辑器 / 通用详情 / 备份 v2
  - docs/water-module-confirmation.md            # 饮水模块与 Room v2
  - docs/coffee-module-confirmation.md           # 咖啡 / 茶模块
```

## 8. 覆盖与例外

通用规范里不适用或需要偏离的，逐条写「规则 / 偏离 / 理由」。

```yaml
overrides:
  - 规则: lang-java.md（Java 编码规范）
    偏离: 不直接适用——项目为 Kotlin/Android，无 .java 源文件
    理由: Kotlin/Compose 代码按 .agents/lang-android.md 与官方 Kotlin 风格执行；lang-java.md 保留但不在分流表引用
```

## 9. 已知的雷区

- **构建工具链已升级到 AGP 9（2026-11-06 修复，勿回退）**：现象 → `:app:checkDebugAarMetadata` 报 28 项 AAR 元数据错误，Compose BOM 2026.09.00（compose-ui 1.12.1）、Navigation 2.10.2、Lifecycle 2.11.0 要求 AGP ≥ 9.1.0 且 compileSdk ≥ 37。根因 → 工程原先固定 AGP 8.13.2 / compileSdk 36.1，与当前 AndroidX 依赖的元数据要求不匹配。修复 → AGP 9.3.1 + Gradle 9.6.1 + compileSdk 37，移除 `org.jetbrains.kotlin.android`（AGP 9 内置 Kotlin），Kotlin 2.4.21 通过根 `buildscript` classpath 覆盖，KSP 升到 2.3.13。教训 → AGP 9.3.1 硬性要求 Gradle ≥ 9.5.0（9.4.1 会直接失败）；KSP 必须 ≥ 2.3.1 才能配合 built-in Kotlin；升级这批依赖时先看 AAR 元数据里的 AGP/compileSdk 下限。
- **`gradle/wrapper/` 已是官方 Wrapper（含 `gradle-wrapper.jar`）**：旧的 `gradlew` / `gradlew.bat` 是自制下载引导脚本，硬编码 Gradle 8.13 且只认 `~/.gradle/life-track-bootstrap/`，升级版本时必须同步改脚本里的 `VERSION`。现已替换为官方脚本，改版本只改 `gradle-wrapper.properties` 的 `distributionUrl`。
- **数据库未加密**：不得声称 Room 已加密；应用私有目录只是沙箱隔离。面向真实用户发布前必须先完成加密选型、Android Keystore 密钥管理、导出文件加密与备份/迁移评审（见 README「安全边界」）。
- **Manifest 的 INTERNET 权限例外（已评审）**：原本 Local First 硬约束是「不申请 INTERNET」，但应用内「检查更新」需要联网查询 Release 清单并下载 APK，已作为**唯一例外**放开——新增 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES` 权限（见 `app/src/main/AndroidManifest.xml`）。该网络能力仅用于更新（HTTPS GET `gitee.com/api/v5/repos/saul999/LifeTrack/releases/latest`，失败时回退 `api.github.com/repos/999bug/LifeTrack/releases/latest`，以及下载 APK），不携带任何用户数据/设备标识/遥测；对应的 README「本地存储」「安全边界」第 6 条与隐私评审已同步更新。新增任何其它网络能力仍需回到本约束重新评审。
- **`allowBackup=false`，主动导出 / 恢复已实现但未加密**：设置页「备份与恢复」走 SAF 导出单个 JSON（schemaVersion 2，可读 v1），恢复在单事务内整体替换；卸载、清除数据或丢机仍会丢数据，备份文件本身是明文，加密与密钥管理见 README「安全边界」与台账 C4–C6。
- **发布签名密钥由 Repository secrets 注入**：流水线从 `LIFETRACK_KEYSTORE*` 环境变量读取，首次发版前需按 docs/release-guide.md 配置 4 个 secrets；keystore 与口令绝不入库，密钥丢失后用户只能卸载重装且数据全丢。
- **CI 不能用 `android-actions/setup-android@v3`（2026-11-09 修复）**：现象 → `Set up Android SDK` 步骤 15 秒失败，日志 `Warning: Failed to find package 'tools'` 与 `sdkmanager failed with exit code 1`。根因 → 该 action 固定执行 `sdkmanager tools`，而 2026 年 ubuntu-latest 预装的 cmdline-tools 16.0 已移除 `tools` 包。修复 → 去掉 action，直接定位 `$ANDROID_HOME/cmdline-tools/*/bin/sdkmanager`，并把 ANDROID_HOME 写进 GITHUB_ENV、显式校验安装目录。教训 → 第三方 setup action 会随 runner 镜像漂移，优先用镜像自带工具并验证结果目录存在。
- **compileSdk 37 的 sdkmanager 包名不是 `platforms;android-37`（2026-11-09 修复）**：现象 → `sdkmanager "platforms;android-37"` 报 `Failed to find package`。根因 → 官方主清单 `repository2-1.xml` 最高只到 `android-36`；API 37 在分片 `repository2-3.xml` 中，包名是 `platforms;android-37.0`（另有 37.1 / 37.2），本机 SDK 目录同样是 `platforms/android-37.0`。修复 → 从 `sdkmanager --list` 动态取最低的 `platforms;android-37.x` 安装，并用解析出的包名校验目录。教训 → compileSdk 的数字不等于 sdkmanager 包名；新平台先在 `--list` 里核对再写死。
- **PowerShell 下不要给 `keytool` 传 `-J-Duser.language=en`（2026-11-09）**：现象 → 证书指纹自校验脚本在 PowerShell 里输出 `FAILED: 打不开 keystore，检查口令`，实际 `keytool` exit=1 且捕获到的输出全是 `非法选项:  .language=en`；同一命令在 Git Bash 下正常。根因 → PowerShell 对原生命令的参数传递把该选项拆坏，Java 收到非法参数后只输出错误文本，脚本匹配不到 `SHA256:` 却误判成口令错误。修复 → 去掉 `-J` 选项；中文 JDK 输出里 `SHA256:` 标签仍是英文，脚本改做中英兼容并显式检查 `$LASTEXITCODE`、失败时打印真实报错。教训 → 给用户 PowerShell 命令必须在 PowerShell 里实测，不能用 bash 的结果代替；捕获原生命令输出时先看 exit code 和原始文本，别把命令失败当成凭据错误。校验命令已写进 `docs/release-guide.md` §1.3。

- **Compose 固定 dp 高度会在 200% 字体下裁掉第二行文字（2026-11-09 修复）**：现象 → 月历有次数的格子只显示底色不显示次数数字，统计卡标题两栏挤成一行。根因 → `sp` 随系统 `fontScale` 放大而 `Modifier.height(N.dp)` 的 dp 不变，两行文字超出固定高度后被 `clip` 裁掉；单纯把 48dp 加到 72dp 仍会裁。修复 → 格子改 `heightIn(min = …)` 让内容自己撑高，并给无次数的格子补一行透明占位保证等高；标题 / 分组行在 `fontScale > 1.3f` 时由 `Row(SpaceBetween)` 改上下堆叠；Canvas 轴标签的 gutter 按 `10.sp.toPx() * 2.2f` 计算而不是写死 dp。教训 → 凡是「文字行数 × 字体缩放」的容器都不要用固定 `height`，用 `heightIn` 或按 `LocalDensity.fontScale` 分档；改完必须在 200% 字体下截图，常规字号看不出这类裁切。
- **`track_type` 表在首次启动时是空的（2026-11-09 修复）**：现象 → 新增「记录项目」设置页后，全新安装的设备上卡片显示「正在载入」且没有可管理项。根因 → 初版只在 `addPoopRecord()` 里 `insertIgnore(DEFAULT_POOP_TYPE)`，数据库 `create()` 没有初始化回调，没打过卡就没有任何类型行。修复 → `MainViewModel` 初始化时调 `ensureDefaultTypes()` 批量 `insertIgnoreAll(DEFAULT_TYPES)`，用 IGNORE 保证不覆盖用户改过的 `enabled` / `sortOrder`；`restoreBackup` 的兜底一并改成补齐 `DEFAULT_TYPES`。教训 → 把「元数据表在首次启动就有数据」当成前提前，先去 `AppDatabase.create()` 和 Repository 写入点确认；新增依赖元数据表的 UI 必须自己保证初始化。
- **Kotlin `internal` 类型不能出现在 `public` 函数签名里（2026-11-09 修复）**：现象 → `compileDebugKotlin` 报 `EXPOSED_PARAMETER_TYPE`，"'public' function exposes its 'internal' parameter type 'StatsColors'"。根因 → 抽出共用色板时把 `StatsColors` 标成 internal，但 `TimeOfDayCard` / `DetailDistributionCard` 仍是 public。修复 → 把只在模块内调用的 Composable 一并标为 internal（比把色板改成 public 更收口）。教训 → 抽公共 UI 组件时，可见性要连调用方一起过一遍。
- **备份格式升级不等于数据库迁移（2026-11-09 备份 v2 落地）**：现象 → 台账里「备份 schemaVersion v2」一度被当成「Room v2」，B1 的 Migration 测试被误挂在 A5 上。根因 → 备份文件版本号与 `@Database(version = …)` 是两套独立的版本线；A4/A5 只把 `BackupPayload.events` 从 Room 的 `PoopRecordRelation` 换成独立的 `BackupRecord`，完全没动表结构。修复 → `AppDatabase` 保持 version 1、`app/schemas/` 仍只有 `1.json`，B1 依赖改成 A6 新增详情表时的 Room v2，C3 加密迁移顺延 v3。教训 → 看到「v2」先确认是哪条版本线；对已发布版本，能不升 Room 就不升，存量库迁移是不可逆操作。
- **新增带默认值的参数后，测试里的位置参数会静默错位（2026-11-09）**：现象 → 备份 v2 首轮 5 项单测全报 `NOT_JSON`，看起来像 codec 解析挂了。根因 → 测试 helper 签名是 `waterEvent(typeCode: String = "water", detail: String? = …)`，用例写成 `waterEvent(detail)`，字符串被当成 `typeCode` 塞进 JSON 值，拼出未转义引号。修复 → 统一改命名参数 `waterEvent(detail = detail)`。教训 → 多个同类型默认参数的 helper 一律用命名参数调用；`NOT_JSON` 先打印生成的文本再怀疑解析器。
- **备份里「暂时存不下的数据」必须拒绝，不能忽略（2026-11-09）**：现象 → `restoreBackup` 是整体替换事务，若对未知详情 `mapNotNull` 跳过，用户会在「恢复成功」提示下永久丢数据。修复 → `decode` 增加 `supportedDetailTypes`（生产默认空集），遇到未落库的通用详情返回 `UNSUPPORTED_DETAIL_TYPE` 并带上记录 ID；v1 文件被塞入 v2 详情同样拒绝。Repository 恢复时再 `error()` 挡一层。教训 → 格式先行、存储后补时，解码层要按「当前版本能力」收紧，而不是按「格式是否合法」放行。

- **Room 迁移 SQL 必须逐字对齐 KSP 生成的 schema（2026-11-09 A6）**：现象 → 首次升 Room v2 新增 `event_detail` 表时，若 Migration 的 `CREATE TABLE` 列顺序 / 外键 / 索引名与 `2.json` 不一致，Room 打开时会抛「Migration didn't properly handle」。根因 → Room 用 schema identity 校验迁移结果，不是只看表是否存在。修复 → 先写实体与 version 2、编译让 KSP 生成 `2.json`，再照 `createSql` 写 `MIGRATION_1_2`，最后 `addMigrations`；`2.json` 随代码入库。教训 → 迁移 SQL 不要凭记忆手写，先生成 schema 再抄；只加表、不改列、不删数据，是对已发布库最安全的升级形态。
- **同一工作区多会话并发时，共享文件会互相叠加（2026-11-09）**：现象 → A6 收尾时全仓 `compileDebugKotlin` 突然失败，报 `jsonPrimitive` / junit 未解析与 `No value passed for parameter 'updateRepository'`，但本包改动此前刚全绿。根因 → 另一会话同时在开发「应用内检查更新」，改了 `MainActivity` / `LifeTrackApplication` / `AndroidManifest` / `SettingsScreen` / `README` / `profile`，并在 `LifeTrackApp` 与 `MainViewModel` 上叠加了 `checkForUpdate` 与 `updateRepository`；其测试文件误放进 main 源集。修复 → 不修改、不回退、不 stash 对方文件；用 `git archive HEAD` 到系统临时目录、只叠加本包文件做隔离验证；提交时只 add 具体路径。教训 → 构建突然失败先看 `git status` 与文件时间戳，别急着改自己的代码；共享文件里混着两边改动时，不能把整文件当「自己的成果」提交。**结局（17:55）**：对方 `33ef32d` 修好编译并把两边改动一并提交，合并后 90 项单测 + assembleDebug 全绿；原计划的「补提交 A6 代码」因此取消——共享文件被别人提交后，先 `git show --stat` 核对内容再决定是否还要补提交，不要重复 commit。

- **横向滚动的 chips 在 200% 字体下会把最后一项裁成半截（2026-11-09 A7 修复）**：现象 → 咖啡因三态 chips（含咖啡因 / 低因 / 无咖啡因）在大字号下初始只露出「无」，看起来像文字截断。根因 → `Row + horizontalScroll` 只保证「可滑动到达」，不保证「初始完整可见」；字号放大后前两项已占满卡片宽度。修复 → `LocalDensity.current.fontScale > 1.3f` 时用 `values.chunked(2)` 拆成多行 `Row`，常规字号保留横向滚动。教训 → 枚举项少（≤4）且文案短时优先分行展示；横向滚动只适合项目较多、用户明确知道能滑的场景；大字体回归要看「首屏是否完整」，不能只看「能否滚到」。
- **注册新类型会打破以它为「未注册样例」的既有测试（2026-11-09 A7，A8 再次复现）**：现象 → 注册 `coffee` / 之后注册 `sport` 时，`BackupCodecV2Test` / `DetailSpecTest` 里「生产默认拒绝未注册类型」用例必然失败。根因 → 测试用路线图里的真实类型名代表「未来才做的类型」，一旦它被实现就失去语义。修复 → 负向样例统一改用不会注册的 `mood`，并同步更新生产放行集合断言。教训 → 负向用例要用「永远不会注册」的占位名（如 `mood` / `unknown_type`），别用路线图里的真实类型名。
- **切换条用固定 dp 高度，200% 字体下选中块会压住文字（2026-11-09 B4 修复）**：现象 → 统计页「周 / 月 / 年」在 200% 字体下文字被压扁/裁切，历史页「列表 / 月历」同构问题。根因 → 整条固定 `height(38.dp)`，而 13sp 文字随 `fontScale` 变成 26sp，行高超过容器。修复 → 按 `LocalDensity.current.fontScale > 1.3f` 把整条抬到 52dp，圆角与指示块改由 `barHeight` 推导，并给文字加 `maxLines = 1, softWrap = false`。教训 → 分段控件的高度必须从字号推导，不能只写死一次；改完在 200% 字体下截图核对。
- **`MigrationTestHelper` 需要把 `app/schemas/` 注册进 androidTest assets（2026-11-09 B1）**：现象 → 迁移测试报「Cannot find schema file for version 1」。根因 → KSP 只把 schema 导出到工程目录，androidTest APK 里没有这些资源。修复 → `android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")` + `androidTestImplementation("androidx.room:room-testing")`。教训 → Room 迁移测试是「拿历史 schema 建库」，schema 文件必须随测试打包，不能只留在仓库里。
- **内存库测试插入子表前必须先有父表行（2026-11-09 B1/B2）**：现象 → `LocalDatabaseTest` 插入 `track_event` 报 `FOREIGN KEY constraint failed (code 787)`。根因 → `track_event.typeCode` 外键指向 `track_type.code`，测试只插事件没插类型。修复 → 先 `trackTypeDao().insertIgnoreAll(DEFAULT_TYPES)`。教训 → 开外键的 Room 库里，测试夹具要按父表 → 子表顺序造数据；`insertEvents(list)` 是批量插入，不会逐行容错。
- **`lintDebug` 是发布门禁的硬前置（2026-11-09 B11）**：现象 → 首次把 lint 接进 CI 时 `BUILD FAILED`，唯一 error 是 `android:windowLightNavigationBar requires API level 27 (current min is 26)`。根因 → 该主题属性 API 27 才有，minSdk 是 26。修复 → 在 `<item>` 上加 `tools:targetApi="27"`（系统在 26 上会忽略该属性，不必复制整份 `values-v27` 主题）。教训 → 新接 lint 前先本地跑一次 `:app:lintDebug`；历史工程接门禁时，先分清「新引入的 error」与「一直存在的 error」，后者单独修，别为了让门禁变绿而全局关规则。

- **`wm size` 改的是像素，不是 dp（2026-11-09 设置页重构）**：现象 → 用 `wm size 1200x800` 以为在测平板，结果 `widthIn(max = 640.dp)` 完全不生效，页面仍是全宽。根因 → 该设备 density 约 2.625，1200px 只有约 457dp，比 640dp 窄，约束自然不触发；要测平板得让「像素 ÷ density」超过目标 dp（如 2000px 宽 ≈ 762dp）。修复 → 改用 `wm size 2000x1200` 复测，正文正确居中限宽。教训 → 用 `wm size` 造尺寸前先 `wm density` 换算 dp；「看起来像平板」不等于 dp 宽度够。改完尺寸/字号一定 `wm size reset` + `settings put system font_scale 1.0` 还原。
- **设置类页面要先定信息架构再写卡片（2026-11-09 设置页重构）**：现象 → 设置页六张卡片各写一套内边距，加一个模块就长一截，用户反馈「看着比较乱」。根因 → 每个设置项自己决定 Card 与排版，没有「分区 / 行 / 分隔线」的公共骨架，层级只能靠堆卡片表达。修复 → 抽出 `SettingsSection` / `SettingsRow` / `SettingsDivider` / `SettingsNote`，重活（记录项目管理）拆独立页，次要信息（隐私）折叠，状态类信息（更新）收成一行按需展开。教训 → 设置页先按「用户要完成什么」分组，再决定每项是行、是开关还是入口；任何「一个功能一张卡」的平铺都会随功能数量线性变乱。

- **系统文件选择器的「Large files」筛选会把小文件藏起来（2026-11-09 B7 验收）**：现象 → 从备份恢复时打开 SAF，Downloads 里明明有刚导出的 1.8 KB JSON，界面却显示「No matches in Downloads」。根因 → 上一轮误点了筛选 chip「Large files」，该筛选会把小于阈值的文件过滤掉，且选中态只在 chip 上显示一个小勾。修复 → 取消筛选后文件立刻出现，正常选中即可；自动化时不要盲点卡片区域，先按文本定位。教训 → 系统选择器不是只读列表，顶部筛选/视图切换会改变可见文件；写 UI 走查脚本前先 dump 一遍节点，别假设列表结构。
- **Compose 的 `clickable` 不携带选中态，分段控件要用 `selectable`（2026-11-09 B3）**：现象 → 自绘的周/月/年切换用 `Modifier.clickable` 实现，读屏无法知道哪一档被选中，uiautomator 里也看不到 `selected`。修复 → 改用 `Modifier.selectable(selected, role = Role.Tab)`，uiautomator 能读到 `selected="true"` 且随点击转移；同时把整条高度抬到 48dp 满足触摸目标。教训 → 任何「多选一」的控件都用 `selectable` / `toggleable`，不要用裸 `clickable` 自己维护状态。
- **图表类 Composable 要给一句汇总语义（2026-11-09 B3）**：现象 → Canvas 画的分段柱图对读屏是一堆无意义节点，或逐个念出坐标轴标签。修复 → 外层用 `clearAndSetSemantics { contentDescription = "形态分布，已填写 N / 共 M 条；布里斯托 …" }` 包住，读屏只念一句。教训 → 图表先想「读屏用户需要知道什么结论」，再把它写成一句话；`clearAndSetSemantics` 比补 `contentDescription` 更干净，能避免子节点噪声。
- **Gitee 仓库镜像不同步 Release 附件（2026-11-10）**：现象 → 配好 Gitee「仓库镜像管理」后代码分支和 tag 都同步了，APK 附件却没过去，App 检查更新找不到下载地址。根因 → 官方镜像只同步分支、标签、提交记录，Release 附件不在同步范围。修复 → `release.yml` 增加 `mirror-gitee` job，复用同一份 `release-apk` artifact，调 Gitee OpenAPI 创建 Release 并上传 APK 与 `.sha256`。教训 → 「镜像仓库」不等于「镜像发布产物」；分发链路必须自己上传二进制，并在 CI 里校验返回的 `browser_download_url`。
- **Gitee `/releases/latest` 指向最后创建的 Release（2026-11-10）**：现象 → 补发旧版本或创建顺序错乱时，API 返回的不是语义版本最高的 Release。根因 → Gitee 没有「设为 latest」的 API，按创建时间取最后创建的一个。修复 → 按版本递增正常发版、不补发旧版本；客户端 `isNewer()` 再比一次版本号，最坏提示「已是最新」，不会误装旧包。教训 → 依赖第三方 `latest` 语义时，客户端必须自己做版本比较兜底。
- **纯 JVM 单测的依赖链上不能出现 Android 框架类，断言也不要当表达式用（2026-11-10 C2）**：现象 → `PassphraseEnvelope.MIN_CIPHERTEXT_LENGTH` 原本引用 `DatabaseKeyStore.PASSPHRASE_LENGTH`，会让本地单测加载带 `android.security.keystore` import 的类；`val restored = assertNotNull(decode(text))` 直接编译失败。根因 → Kotlin 的 `const val` 跨类访问仍会触发目标类初始化；JUnit 4 的 `assertNotNull` 返回 `Unit`，不是被断言的对象。修复 → 口令长度常量下沉到纯 JVM 的 `PassphraseEnvelope`，`DatabaseKeyStore` 反向引用它；断言拆成取值 + `assertNotNull` + `!!`。教训 → 想被 `testDebugUnitTest` 覆盖的类，依赖链必须干净；先取值再断言，别把断言结果赋给变量。

## 10. 附：从 /init 产物抽取的原文（可选）
本项目没有安装前的 CLAUDE.md / AGENTS.md 原文需要归档，本节删除。
