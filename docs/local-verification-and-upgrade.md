# 本地验证与在线升级方案

> 适用版本 0.2.0 · 核对日期 2026-11-06 · 仓库 https://github.com/999bug/LifeTrack

这份文档解决两件事：

1. 改完代码后，怎么在电脑上（模拟器/真机）验证这次改动没把东西改坏；
2. App 装到用户手机之后，怎么把新版本送上去。

---

## 一、改完代码后怎么在电脑上验证

### 0. 一次性准备（只做一次）

| 项 | 本机现状 | 说明 |
|---|---|---|
| JDK | 21.0.7（`JAVA_HOME` 已指向） | AGP 要求 17+，21 可用 |
| Android SDK | `C:\Users\SERVER\AppData\Local\Android\Sdk` | 已写进 `local.properties`，不会入库 |
| 模拟器 | AVD `Medium_Phone_API_37.0`（API 37 / x86_64） | 镜像 `android-37.0` 已安装 |
| Gradle | 9.6.1，由 `gradlew` 管理 | 不需要单独装 Gradle |

Android Studio 里还需要确认一件事：`Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` 选 **jbr-21** 或本机 JDK 17。选错会报 `Unsupported class file major version`。

### 1. 四层验证，从快到慢

| 层 | 命令 | 耗时（本机实测） | 什么时候跑 |
|---|---|---|---|
| L1 编译 | `./gradlew :app:assembleDebug` | 增量 25s / clean 46s | 每次改完 |
| L2 单元测试 | `./gradlew :app:testDebugUnitTest` | 37s | 碰了 `RecordTimeMapper`、domain 层 |
| L3 装到设备跑主路径 | `./gradlew :app:installDebug` | 22–50s | 碰了 UI、导航、Room |
| L4 设备端集成测试 | `./gradlew :app:connectedDebugAndroidTest` | 分钟级 | 改数据库 schema 前后 |

原则：**能 L1/L2 抓到的问题不要留到 L3**。L3 的价值只有两个——看界面真的长对了，以及确认数据库真的写进去了。

### 2. 命令速查

```bash
# 编译 + 出包
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/LifeTrack-0.1.0-debug.apk

# 单元测试（报告在 app/build/reports/tests/testDebugUnitTest/index.html）
./gradlew :app:testDebugUnitTest

# 装到已连接设备并启动
./gradlew :app:installDebug
adb shell am start -n com.example.lifetrack/.MainActivity

# 设备端 Room 集成测试（需要设备在线）
./gradlew :app:connectedDebugAndroidTest

# 只验证「全新检出」能否构建（排除增量缓存侥幸）
./gradlew clean :app:assembleDebug
```

多台设备同时在线时，所有 `adb` 命令加 `-s emulator-5554` 指定目标。

### 3. 模拟器的生命周期

```bash
# 启动（也可以从 Android Studio 的 Device Manager 点）
emulator -avd Medium_Phone_API_37.0

# 看谁在线
adb devices

# 结束
adb emu kill
```

**卡死时的处理**（本机踩过两次）：现象是截图一直是同一张花屏图、`adb reboot` 后 `adb shell` 超时无响应。处理顺序：

```bash
adb kill-server
taskkill //F //IM emulator.exe
taskkill //F //IM qemu-system-x86_64.exe
# 然后重新启动 AVD
```

不要反复 `adb reboot`，卡在渲染/启动循环时重启模拟器比重启系统快。

### 4. 三个观察手段

**日志**

```bash
# 只看崩溃
adb logcat -s AndroidRuntime
# 只看自己的进程
adb logcat --pid=$(adb shell pidof com.example.lifetrack)
```

Android Studio 的 Logcat 窗口过滤框输入 `package:com.example.lifetrack` 等价。

**数据库**（调 Room 最有效）

首选 Android Studio：`View → Tool Windows → App Inspection → Database Inspector`，选中进程后可以直接看 `track_event`、`poop_detail`、`track_type` 三张表，能跑 SQL，勾上 Live updates 后点一次按钮就能看到行数变化。

命令行兜底（debug 包是 debuggable，可以用 `run-as`）：

```bash
adb exec-out run-as com.example.lifetrack cat databases/life_track.db > life_track.db
adb exec-out run-as com.example.lifetrack cat databases/life_track.db-wal > life_track.db-wal
```

Room 默认 WAL 模式，只拉主库会丢最近写入的数据，必须连 `-wal` 一起拉，或用 DB Browser for SQLite 打开时同级放好。

**画面**

```bash
adb exec-out screencap -p > shot.png
```

注意：Git Bash 下要用 `exec-out` 重定向，不要用 `adb shell screencap -p /sdcard/x.png` 再 pull——`/sdcard` 会被 MSYS 当成 Windows 路径转换掉，表现为 `Error opening file: D:/.../sdcard/x.png`。

### 5. 改哪类代码，验什么

| 改动类型 | 必验项 | 别漏的点 |
|---|---|---|
| 时间/时区（`RecordTimeMapper`） | L2 单元测试；跨午夜、跨时区各测一次 | `localDate` 错不会抛异常，只会让统计默默错 |
| Compose 界面 | L3 手动点一遍 + 截图 | 窄屏（如 360dp）和深色模式各看一次 |
| Room 实体 / 字段 | 先升 `version` 并写 `Migration`，再 L3 | 不写迁移会启动崩溃，见下方故障表 |
| 图标 / 资源 / Manifest | `installDebug` 后看桌面图标；`aapt2 dump badging` 看权限 | 图标有桌面缓存，卸载重装才刷新 |
| Gradle 脚本 / 依赖版本 | `clean` 后完整构建一次 | 增量构建会掩盖依赖解析问题 |

### 6. 常见故障对照

| 现象 | 原因 | 处理 |
|---|---|---|
| `checkDebugAarMetadata` 报一堆 AAR 元数据错误 | 依赖要求的 AGP / compileSdk 高于工程配置 | 对照 README「版本基线」表，或降依赖 |
| `Room cannot verify the data integrity` | 改了实体但没升 `version` / 没写 Migration | 改表必须升版本 + 写迁移；开发期可卸载重装 |
| `Unsupported class file major version` | Gradle JDK 低于 17 | 换 Gradle JDK |
| 装机后图标还是旧的 | 启动器缓存 | 卸载重装，或换一个启动器验证 |
| 构建卡在 `Downloading ...` | 网络访问 Google Maven 慢 | 配代理，或加 `--offline` 用已有缓存 |
| 截图内容不更新 | 模拟器渲染管线挂了 | 见第 3 节「卡死时的处理」 |

### 7. 每次改动的最小验收清单

- [ ] `assembleDebug` 通过，没有新增 warning
- [ ] 相关单元测试通过（改了逻辑就补测试，不是"应该没问题"）
- [ ] 主路径在模拟器上手动走过一遍
- [ ] 涉及数据库：DB Inspector 里确认数据真的落盘
- [ ] 工作区干净：没有临时脚本、调试 `println`、构建产物进库

---

## 二、装到手机之后的在线升级

### 0. 先划一条红线

这个 App 当前**不申请 `INTERNET` 权限**，README 里把它写成了硬约束（Local First / 数据不出设备）。而"在线升级"天然要联网，所以这不是技术选型问题，是产品承诺问题。

按联网需求分三档：

- **A. 完全离线**：用户自己下载新 APK 覆盖安装。App 一行网络代码都不用加。
- **B. 交给应用商店**：商店客户端负责下载安装，App 自身仍然不需要 `INTERNET`。
- **C. 应用内自更新**：App 自己拉清单、下 APK、拉起安装器。**必须加 `INTERNET`**，还要加安装权限，并且要改 README 的安全边界章节。

### 1. 方案对比

| 方案 | 分发渠道 | App 需要联网 | 用户操作 | 国内可用 | 工程量 |
|---|---|---|---|---|---|
| Google Play 应用内更新 | 上架 Play | 不需要自己维护下载（由 Play 商店负责） | 应用内弹窗，点一下 | 否 | 低 |
| 国内应用商店自动更新 | 各厂商商店 | 不需要 | 无感/商店提示 | 是 | 中（逐家上架 + 备案） |
| 自建更新 + `PackageInstaller` | 官网/GitHub/自建 CDN | **需要 `INTERNET`** | 下载 → 授权"安装未知应用" → 确认安装 | 是 | 高 |
| 手动下载覆盖安装 | 手动分发 | 不需要 | 自己找包、自己装 | 是 | 极低 |

### 2. 建议路线（分阶段，不要一步到位）

| 阶段 | 做法 | 理由 |
|---|---|---|
| v0.x 内测（现在） | GitHub Releases 挂 APK，手动覆盖安装 | 用户就是你自己/少数人，自更新纯属过度设计 |
| 面向公众 | 优先上架应用商店（国内）/ Play，用商店做更新 | 不破坏"零网络权限"的承诺，还顺带解决备案与分发 |
| 确实要绕开商店 | 再考虑 C，并同步修改隐私说明 | 只有"内测分发/企业内部分发/应用商店不收录"这类场景才值得 |

如果这个 App 的定位始终是"个人本地工具、不上架"，那 A 就是终局方案，不需要做自更新。

### 3. 如果确实要做自更新（方案 C）的最小设计

**服务端只要一个 JSON 清单**（放 GitHub Releases、对象存储、任意静态托管都行）：

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "minSupportedVersionCode": 1,
  "apkUrl": "https://example.com/LifeTrack-0.2.0-release.apk",
  "sha256": "3f6a...（64 位十六进制）",
  "sizeBytes": 14217690,
  "changelog": "修复跨午夜记录日期错误",
  "force": false
}
```

字段含义：

- `versionCode`：**唯一用于比较的字段**，不要拿 `versionName` 字符串比大小（"0.10.0" 会小于 "0.9.0"）
- `minSupportedVersionCode`：低于这个版本的客户端强制更新（用于接口不兼容/安全修复）
- `force`：单次发布是否强制；和 `minSupportedVersionCode` 是两套策略，选一个用
- `sha256`：校验下载完整性，防止半包、CDN 缓存错内容

**客户端七步流程**

1. 读本地 `BuildConfig.VERSION_CODE`
2. 拉清单（带 `If-None-Match`，避免每次冷启动都下 JSON）
3. 比较 `versionCode`，不比 `versionName`
4. 下载 APK 到 app 私有 `cacheDir`，**边下边算 sha256**，下完比对 `sizeBytes` 和 `sha256`
5. `packageManager.canRequestPackageInstalls()` 检查"安装未知应用"权限（API 26+）
6. 用 `PackageInstaller` 开会话写入 APK
7. 监听会话状态：`STATUS_PENDING_USER_ACTION` 时拉起系统确认界面；成功/失败分别上报

**核心代码骨架**

```kotlin
val installer = context.packageManager.packageInstaller
val params = PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_FULL_INSTALL
).apply {
    // 限定包名，安装器会拒绝包名不匹配的 APK
    setAppPackageName(context.packageName)
}
val sessionId = installer.createSession(params)
installer.openSession(sessionId).use { session ->
    session.openWrite("base.apk", 0, apk.length()).use { out ->
        apk.inputStream().use { it.copyTo(out) }
        session.fsync(out)
    }
    session.commit(statusSender) // statusSender 来自 PendingIntent
}
```

要点：

- `ACTION_INSTALL_PACKAGE` 从 **API 29 起已废弃**，不要再照抄老教程，直接上 `PackageInstaller`
- 普通应用**无法静默安装**（那是设备所有者/系统应用才有的能力），系统一定会弹确认框，这是正常状态，不要当成失败
- 用户可能在任何时候关掉"安装未知应用"授权，所以**每次都要重新检查**，不能只在引导页检查一次

### 4. 五个必须守住的约束

1. **签名必须一致**
   覆盖安装要求新包签名与已装版本相同。一旦更换 keystore，用户只能卸载重装，而卸载会（因为本项目 `allowBackup=false` 且没有导出功能）**永久丢数据**。keystore 和口令从第一天就要备份到安全的地方，不能只放开发机。

2. **`versionCode` 单调递增**
   Android 不允许安装更低 `versionCode` 的包。所谓"回滚"实际是**发一个 versionCode 更大的旧功能版本**，不是把用户降级。

3. **Room 迁移必须写**
   升级安装会保留数据，`version` 变了却没写 `Migration`，启动就会崩在 `Room cannot verify the data integrity`。当前 `version = 1`，schema 已导出在 `app/schemas/`，改表时：
   - `version` +1
   - 写 `Migration(1, 2)` 并加进 `databaseBuilder`
   - 补一个迁移测试
   宁可让它崩、让用户看到错误，也不要用 `fallbackToDestructiveMigration()` 静默清库。

4. **`sha256` 不等于"可信"**
   它只证明字节没坏，不证明来源。真正的身份边界是 APK 签名校验，由系统在安装时完成。所以清单里的 `apkUrl` 与签名私钥的保护等级要同等对待。

5. **权限与合规**
   `REQUEST_INSTALL_PACKAGES` 是敏感权限，国内商店上架时通常要求说明用途；同时 `INTERNET` 一加，README 里"不联网"的承诺就必须改写，否则就是文档与实现不符。

### 5. 发布前检查清单

- [ ] `versionCode` 与 `versionName` 都已更新（当前 `2` / `0.2.0`）
- [ ] release 包用**正式 keystore** 签名（现在 debug/release 都还没配签名，产物装不上也发不了）
- [ ] Room schema 已导出并入库，`Migration` 有对应测试
- [ ] 真机做一次"旧版 → 新版"覆盖安装，确认数据完整
- [ ] 清单 JSON 带 `sha256` 与 `minSupportedVersionCode`
- [ ] 回滚方案写清楚（发更高 versionCode 的回退版本）
- [ ] 若引入网络：README 安全边界章节、隐私说明同步更新，Manifest 权限变更记录在案

---

## 附：一页速查

```bash
./gradlew :app:assembleDebug          # 编译，产物 LifeTrack-0.2.0-debug.apk
./gradlew :app:testDebugUnitTest      # 单元测试
./gradlew :app:installDebug           # 装到设备
adb shell am start -n com.example.lifetrack/.MainActivity
adb logcat --pid=$(adb shell pidof com.example.lifetrack)
adb exec-out screencap -p > shot.png
```

升级路线一句话：**内测阶段手动发 APK，公开阶段交给应用商店，只有绕不开商店时才自己做 `PackageInstaller` 自更新——并且那时要改写隐私承诺。**
