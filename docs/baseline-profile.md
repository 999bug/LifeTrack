# Baseline Profile

> 状态：已接入 `androidx.profileinstaller` 与**手写**的 `app/src/main/baseline-prof.txt`。
> 手写版只覆盖启动链路的包级规则，收益未量化；正式做法是在 arm 真机上生成。

---

## 1. 它解决什么问题

Compose 应用的冷启动有相当一部分花在「首次执行时的 JIT 编译」上。Baseline Profile 是一份
「启动时会走到的类与方法」清单，系统在安装时据此做 AOT 编译，首次启动就不用边跑边编译。

典型收益：冷启动快 15–25%，首屏滚动卡顿减少。代价是安装后的 odex 变大、安装耗时略增，
APK 体积不变（`baseline.prof` 只有几 KB）。

## 2. 当前实现

| 文件 | 作用 |
| --- | --- |
| `app/src/main/baseline-prof.txt` | 手写规则，AGP 在构建时编译成 `assets/dexopt/baseline.prof` + `.profm` |
| `app/build.gradle.kts` | `implementation("androidx.profileinstaller:profileinstaller:1.4.1")` |

规则分两类，**格式不能混**：

- **类级规则**：`Lcom/example/lifetrack/MainActivity;` —— 不能带 `HSP` 前缀，否则报
  `Class rules don't support flags, but 'HSP' were specified`
- **方法/包通配规则**：`HSPLcom/example/lifetrack/core/ui/**->**(**)` —— 可以带 `H`/`S`/`P` 标志

`H` = hot、`S` = startup、`P` = post-startup。启动相关一律用 `HSP`。

规则在 R8 之前消费，所以写**混淆前**的原始类名（例如 `net/zetetic/database/**`
而不是混淆后的短名），R8 会负责重写。

## 3. 什么时候要重新生成

手写版是「按代码结构猜的」，收益有限。以下情况应该换成生成版：

- 首屏结构变化（换导航、改首页布局）
- 冷启动明显变慢，而常规排查没找到原因
- 想量化收益，需要前后对比

## 4. 用 Macrobenchmark 生成（需要 arm 真机）

**前置条件**：一台 arm64 真机（或 arm64 模拟器镜像）、Android Studio Hedgehog 以上。
本地这台 x86_64 模拟器**不能**用来生成——BaselineProfileRule 要求被测应用与宿主同架构，
且需要可控的编译状态。

### 4.1 新建 benchmark 模块

```
File → New → Module → Macrobenchmark
```

默认生成 `:baselineprofile` 模块，`build.gradle.kts` 里会有：

```kotlin
plugins { id("androidx.baselineprofile") }
targetProjectPath = ":app"
```

并在 `:app` 里自动加：

```kotlin
baselineProfile { saveInSrc = true }
```

`saveInSrc = true` 很关键：生成的 profile 会直接覆盖
`app/src/main/baseline-prof.txt`，不用手工搬。

### 4.2 写生成用例

```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.lifetrack",
        includeInStartupProfile = true
    ) {
        // 冷启动到首屏
        pressHome()
        startActivityAndWait()
        // 首屏需要把数据拉出来，否则首屏数据链路不会进 profile
        device.wait(Until.hasObject(By.textContains("今天")), 5_000)
        // 各页走一遍，让导航与统计聚合进 profile
        device.findObject(By.text("历史"))?.click()
        device.waitForIdle()
        device.findObject(By.text("统计"))?.click()
        device.waitForIdle()
        device.findObject(By.text("设置"))?.click()
        device.waitForIdle()
        pressHome()
    }
}
```

注意：`BaselineProfileRule` 需要设备处于可写入 profile 的状态，跑之前建议
`adb shell cmd package compile -m verify -f com.example.lifetrack` 清掉旧编译产物。

### 4.3 执行

```bash
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
```

生成的 `baseline-prof.txt` 会写到 `app/src/main/`，随后重新构建 release 包即可。

### 4.4 验证收益

```bash
./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.lifetrack.benchmark.StartupBenchmark
```

对比 `StartupTimingMetric` 的 `timeToInitialDisplayMs`；同一台设备、同一编译状态下至少跑 5 次取中位数。

## 5. 本次未做的事

- 没有 arm 设备，**没有生成真实 profile**，也没有量化启动收益
- 手写规则的覆盖范围是按代码结构推的，未用 `aapt2` / `dexopt` 产物反查
- 只在 APK 内确认了 `assets/dexopt/baseline.prof` 存在，未在设备上核对 odex 状态

如果后续要正式做，按 §4 走一遍，手写文件会被生成版整体替换。
