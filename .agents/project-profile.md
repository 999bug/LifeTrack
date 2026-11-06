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
  kotlin: 2.4.21                   # 25 个 .kt，0 个 .java
  java: 17                         # 仅 JVM target / compileOptions 版本
framework: Android (Jetpack Compose + Material 3)   # Room 2.8.5 + KSP、DataStore、Navigation Compose
package_manager: gradle
build_command: ./gradlew :app:assembleDebug     # 已验证通过（2026-11-06，产物 app/build/outputs/apk/debug/LifeTrack-<version>-debug.apk）
test_command: ./gradlew :app:testDebugUnitTest   # 已验证通过（RecordTimeMapperTest 3 例）
dev_command: ./gradlew :app:installDebug        # 未验证，需连接 API 26+ 设备
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
deploy_target: none                # 本地 Android App，无服务端
release_trigger: manual            # 尚无 CI；发布走手动
version_scheme: 语义化 semver       # versionName 0.1.0，尚无 tag
registry: none
pages: no
```

## 5. 敏感信息

```yaml
secrets_location: GitHub Repository secrets   # 尚未配置 Actions/secrets，见 §9
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
verify_build: 未验证（构建失败，见 §9）
verify_test: 未验证（见 §9）
verify_test_policy: full
test_port: 9099
# 有冒烟/版本/UI 检查就补：verify_smoke、verify_version、verify_ui，写命令
```

`verify_*` 为空时按通用规范兜底：构建 + 测试 + 起服务实测。填了就必须跑填的那条。

## 7. 项目自有文档

```yaml
related_docs:                      # 先读这些再动手，优先级高于通用建议
  - 无                             # 根目录只有 README.md / AGENTS.md / CLAUDE.md
```

## 8. 覆盖与例外

通用规范里不适用或需要偏离的，逐条写「规则 / 偏离 / 理由」。

```yaml
overrides:
  - 规则: lang-java.md（Java 编码规范）
    偏离: 不直接适用——项目为 Kotlin/Android，无 .java 源文件
    理由: 套件没有 Kotlin 语言模块；Kotlin 与 Compose 代码按官方 Kotlin 风格和项目现有写法执行
```

## 9. 已知的雷区

- **构建工具链已升级到 AGP 9（2026-11-06 修复，勿回退）**：现象 → `:app:checkDebugAarMetadata` 报 28 项 AAR 元数据错误，Compose BOM 2026.09.00（compose-ui 1.12.1）、Navigation 2.10.2、Lifecycle 2.11.0 要求 AGP ≥ 9.1.0 且 compileSdk ≥ 37。根因 → 工程原先固定 AGP 8.13.2 / compileSdk 36.1，与当前 AndroidX 依赖的元数据要求不匹配。修复 → AGP 9.3.1 + Gradle 9.6.1 + compileSdk 37，移除 `org.jetbrains.kotlin.android`（AGP 9 内置 Kotlin），Kotlin 2.4.21 通过根 `buildscript` classpath 覆盖，KSP 升到 2.3.13。教训 → AGP 9.3.1 硬性要求 Gradle ≥ 9.5.0（9.4.1 会直接失败）；KSP 必须 ≥ 2.3.1 才能配合 built-in Kotlin；升级这批依赖时先看 AAR 元数据里的 AGP/compileSdk 下限。
- **`gradle/wrapper/` 已是官方 Wrapper（含 `gradle-wrapper.jar`）**：旧的 `gradlew` / `gradlew.bat` 是自制下载引导脚本，硬编码 Gradle 8.13 且只认 `~/.gradle/life-track-bootstrap/`，升级版本时必须同步改脚本里的 `VERSION`。现已替换为官方脚本，改版本只改 `gradle-wrapper.properties` 的 `distributionUrl`。
- **数据库未加密**：不得声称 Room 已加密；应用私有目录只是沙箱隔离。面向真实用户发布前必须先完成加密选型、Android Keystore 密钥管理、导出文件加密与备份/迁移评审（见 README「安全边界」）。
- **Manifest 不申请 INTERNET**：这是 Local First 的硬约束，新增网络能力必须先改 README 并评审隐私影响。
- **`allowBackup=false` 且尚未实现主动导出/恢复**：卸载、清除数据或丢机即可能永久丢数据；设置页已有提示。
- **发布签名密钥尚未配置**：目前无 CI、无 secrets；按 GitHub Repository secrets 约定管理，不要提交 keystore 或口令。

## 10. 附：从 /init 产物抽取的原文（可选）

本项目没有安装前的 CLAUDE.md / AGENTS.md 原文需要归档，本节删除。
