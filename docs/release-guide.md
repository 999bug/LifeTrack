# 发布与签名指南

> 适用版本 0.4.0 · 权威发布：GitHub Releases · 国内分发：Gitee Releases · 流水线：`.github/workflows/release.yml`

这份文档说明怎么把「生活记」的签名 APK 发到 GitHub Releases 并同步到 Gitee，以及为什么签名密钥一旦丢失就无法挽回。代码开发与发布始终以 GitHub 为准，Gitee 只承担国内手机的更新下载。

---

## 0. 为什么必须固定签名

- Release 包必须用同一个 keystore 签名。签名不一致时，Android 拒绝覆盖安装。
- 本项目 `android:allowBackup="false"`，但应用内已提供 JSON 导出 / 恢复（设置页「备份与恢复」）。没主动导出过备份就升级失败/装错包时，「卸载旧版 → 装新版」仍等于**永久丢失全部记录**。
- 结论：keystore 文件、keystore 口令、key 口令从第一个 Release 起就要备份到离机的安全位置，不能只留在开发机上。

`app/build.gradle.kts` 的 release 签名配置是条件式的：仅当 `LIFETRACK_KEYSTORE` 等 4 个环境变量存在时启用，本地没有密钥照样能 `assembleDebug` / 出 unsigned 包。流水线会强制验签，unsigned 包和 debug 证书包都不会被发布。

---

## 1. 一次性准备（只需做一次）

### 1.1 生成正式 keystore

在**本机、非仓库目录**（例如 `$HOME/lifetrack-keys/`）执行，口令交互式输入，不要写进命令行或脚本：

```powershell
$keyDir = Join-Path $HOME "lifetrack-keys"
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
keytool -genkeypair -v `
  -keystore (Join-Path $keyDir "lifetrack-release.jks") `
  -alias lifetrack `
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` 会依次询问 keystore 口令、key 口令（直接回车即沿用 keystore 口令）和证书信息。**把两个口令记进密码管理器**，不要写进仓库、文档或聊天记录。

`*.jks` / `*.keystore` / `keystore.properties` 已写进 `.gitignore`；提交前用 `git status` 再确认一次。

### 1.2 配置 4 个 Repository secrets

在仓库 `Settings → Secrets and variables → Actions` 添加，或用 `gh` CLI（已登录 999bug）：

```powershell
$repo = "999bug/LifeTrack"
$jks  = Join-Path $HOME "lifetrack-keys\lifetrack-release.jks"

# keystore 内容（base64 单行）
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jks))
gh secret set SIGNING_KEYSTORE_BASE64 --repo $repo --body $b64

# 口令用安全输入；PowerShell 历史里只记录变量名，不记录明文
$storePassSecure = Read-Host "Keystore password" -AsSecureString
$storePass = [Net.NetworkCredential]::new("", $storePassSecure).Password
gh secret set SIGNING_KEYSTORE_PASSWORD --repo $repo --body $storePass

$keyPassSecure = Read-Host "Key password (Enter = same as keystore)" -AsSecureString
$keyPass = [Net.NetworkCredential]::new("", $keyPassSecure).Password
if ([string]::IsNullOrEmpty($keyPass)) { $keyPass = $storePass }
gh secret set SIGNING_KEY_PASSWORD --repo $repo --body $keyPass

gh secret set SIGNING_KEY_ALIAS --repo $repo --body "lifetrack"
```

| Secret | 含义 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 base64 单行文本 |
| `SIGNING_KEYSTORE_PASSWORD` | keystore 口令 |
| `SIGNING_KEY_ALIAS` | key 别名（上面用的是 `lifetrack`） |
| `SIGNING_KEY_PASSWORD` | key 口令（与 keystore 口令相同也要单独填） |

检查：`gh secret list --repo 999bug/LifeTrack` 应列出 4 项。Secret 值无法再读出，只能覆盖；流水线缺任意一项会直接失败并打印缺失项名称，不会产出 unsigned 包。

### 1.3 校验本地 keystore 与已发布包是否同一把密钥

签名证书一旦变化，Android 拒绝覆盖安装；本项目 `allowBackup=false`，用户只能卸载重装再手工恢复备份，等于把丢数据的风险直接推给用户。所以**每次发版前、每次备份恢复后**都要核一次证书指纹。指纹是公开信息（随 APK 一起分发），可以写进文档。

v0.1.0 起所有已发布包的签名证书 SHA-256（用 `apksigner verify --print-certs <apk>` 可查）：

```text
6A6CC4C13B5055E505DCF9BC2F6ACD213A6B87AC45BC7039E1252627B51459D9
```

在 PowerShell 里整段执行，口令交互式输入，经 `-storepass:env` 传给 keytool，不进命令行历史、用完即删：

```powershell
$jks = Join-Path $HOME "lifetrack-keys\lifetrack-release.jks"
$expected = "6A6CC4C13B5055E505DCF9BC2F6ACD213A6B87AC45BC7039E1252627B51459D9"
$sec = Read-Host "Keystore password" -AsSecureString
$env:LT_KS_PASS = [Net.NetworkCredential]::new("", $sec).Password
$info = keytool -list -v -keystore $jks -storetype PKCS12 -storepass:env LT_KS_PASS 2>&1
$ok = $LASTEXITCODE
Remove-Item Env:\LT_KS_PASS -ErrorAction SilentlyContinue
if ($ok -ne 0) {
  "FAILED: keytool exit=$ok"; $info | Select-Object -First 3
} else {
  $info | Select-String "别名:|Alias name:|有效起始|Valid from"
  $line = $info | Select-String "SHA256:" | Select-Object -First 1
  if ($line) {
    $got = (($line.ToString() -split 'SHA256:\s*', 2)[1] -replace '[^0-9A-Fa-f]', '').ToUpper()
    if ($got -eq $expected) { "MATCH: 与已发布版本（v0.1.0 起）是同一把密钥" } else { "MISMATCH: $got" }
  } else { "FAILED: 没找到 SHA256 指纹" }
}
```

输出 `MATCH` 即通过；`MISMATCH` 说明本地 jks 不是同一把，**不要发版**，先查是不是拿错了文件。只要密钥不轮换，这个指纹对后续所有版本都适用；一旦主动轮换密钥，必须同步更新这里的值并公告用户。

只想肉眼核对时一条命令即可，看输出里的 `SHA256:` 是否等于下面这串：

```powershell
keytool -list -v -keystore "$HOME\lifetrack-keys\lifetrack-release.jks" -storetype PKCS12
```

```text
6A:6C:C4:C1:3B:50:55:E5:05:DC:F9:BC:2F:6A:CD:21:3A:6B:87:AC:45:BC:70:39:E1:25:26:27:B5:14:59:D9
```

注意：

- keystore 实际是 PKCS12（扩展名虽为 `.jks`），显式写 `-storetype PKCS12`；生成时 key 口令回车沿用了 keystore 口令，实际只需记一个口令。口令明文只应存在密码管理器里，不要贴进聊天、仓库或文档。
- **不要加 `-J-Duser.language=en`**：PowerShell 会把它拆成 `.language=en`，keytool 报「非法选项」并退出 1，脚本会误导成「口令错了」。中文 JDK 输出里 `SHA256:` 标签仍是英文，上面的脚本已做中英兼容；同一命令在 Git Bash 下反而正常。
- 出现 `FAILED: keytool exit=N` 时先看脚本打印出的真实报错，不要据此改口令或重生成密钥；密钥轮换 = 用户卸载重装 = 数据全丢。

### 1.4 配置 Gitee 国内分发（一次性）

1. 在 Gitee 建公开仓库（本项目为 `saul999/LifeTrack`），初始化 `main` 分支即可，**不需要把代码推过去**。
2. Gitee → 设置 → 私人令牌，生成只勾选 `projects` 权限的令牌。
3. 在 GitHub 仓库 `Settings → Secrets and variables → Actions` 添加 Repository secret `GITEE_TOKEN`，值为上一步令牌。可选：用 Repository variables `GITEE_OWNER` / `GITEE_REPO` / `GITEE_BRANCH` 覆盖默认值（`saul999` / `LifeTrack` / `main`）。
4. 检查：`gh secret list --repo 999bug/LifeTrack` 应列出 5 项（4 个签名 secret + `GITEE_TOKEN`）。Secret 值无法再读出，只能覆盖。

令牌只存在 GitHub Secrets，绝不写进 workflow、文档或仓库；App 读取公开仓库的 `releases/latest` 不需要任何令牌。

---

## 2. 每次发版

1. 改 `app/build.gradle.kts`：`versionCode` **单调递增**，`versionName` 改成与 tag 一致（如 `0.1.1`）。
2. 如改过 Room schema，先导出 schema 并写 `Migration` + 迁移测试；README 与本文档的版本号同步。
3. 提交并推送到 `main`（流水线以触发时 `main` 的代码为准）。
4. 打开 `Actions → Release APK → Run workflow`，填 `version`，格式 `v<versionName>`，例如 `v0.1.1`。
5. `release` job 依次执行：单元测试 → 构建 release APK → `apksigner` 验签并拒绝 debug 证书 → 生成 `.sha256` → 校验 tag 未被占用 → 创建 GitHub Release 并上传两个附件。
6. 随后 `mirror-gitee` job 下载同一份 `release-apk` artifact，创建同名 tag 的 Gitee Release 并上传 APK 与 `.sha256`。该 job 失败会让 workflow 标红，但**不会回滚已发布的 GitHub Release**；此时按 §6 人工补传，不要重复打 tag。

流水线入口：https://github.com/999bug/LifeTrack/actions/workflows/release.yml

---

## 3. 下载与校验

Release 页面（权威）：https://github.com/999bug/LifeTrack/releases
国内分发镜像：https://gitee.com/saul999/LifeTrack/releases

附件：

- `LifeTrack-<versionName>-release.apk`：正式签名包，Android 8.0（API 26）及以上可直接安装，系统可能要求允许「安装未知应用」。
- `LifeTrack-<versionName>-release.apk.sha256`：SHA-256 校验值。

两端附件来自同一次构建的同一个文件，签名证书相同，可互相替代安装。

Windows 校验：

```powershell
Get-FileHash .\LifeTrack-0.4.0-release.apk -Algorithm SHA256
```

把输出与 `.sha256` 文件内容比对。`sha256` 只证明下载完整，安装包身份由系统在校验 APK 签名时保证。

---

## 4. 回滚与约束

- **不复用版本号 / tag**：流水线检测到同名 tag 已存在会直接失败；不要用 `gh release upload --clobber` 覆盖旧附件。
- 需要回退时，发一个**更高 `versionCode`** 的回退版本，别让用户降级安装（Android 不允许）。
- 覆盖安装前确认 Room `version` 与 `Migration` 已就位，否则启动即崩。
- 密钥轮换意味着用户必须卸载重装 → 数据全丢，非必要不做。
- **Gitee 同步失败不回滚 GitHub Release**：`mirror-gitee` 失败时 GitHub 侧已发布成功，流水线标红只是提示国内镜像缺失；按 §6 人工补传，不要重跑整个 workflow（会撞同名 tag）。

---

## 5. 发布前检查清单

- [ ] 4 个签名 secret + `GITEE_TOKEN` 齐备
- [ ] 按 §1.3 核对本地 keystore 证书指纹与已发布包一致（换密钥 = 用户数据全丢）
- [ ] `versionCode` 递增、`versionName` 与 tag 一致
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 本地通过
- [ ] README / 本文档版本号已同步
- [ ] Room schema 与 Migration 已就绪（若改表）
- [ ] 真机做过一次「旧版 → 新版」覆盖安装，确认数据完整
- [ ] 两端 Release 页面附件可下载，`Get-FileHash` 与 `.sha256` 一致
- [ ] 真机跑一次应用内「检查更新」，确认命中 Gitee 且下载后能调起安装器

---

## 6. 国内分发（Gitee）的设计与限制

- **为什么不用 Gitee 仓库镜像**：官方「仓库镜像管理」只同步分支、标签和提交记录，不同步 Release 附件。APK 必须由 `release.yml` 的 `mirror-gitee` job 调 OpenAPI 上传。
- **代码不落地 Gitee**：Gitee 仓库只作为 Release 附件的载体，`target_commitish` 指向其 `main` 分支即可；本地与 CI 都不向 Gitee 推代码。
- **App 查询链路**：优先 HTTPS GET `gitee.com/api/v5/repos/saul999/LifeTrack/releases/latest`（公开仓库免令牌）；请求失败、暂无 Release 或 Release 未附 APK 时回退 GitHub。主源命中时不会请求 GitHub，避免国内网络多等一次超时。
- **Gitee 的 `latest` 指向「最后创建」的 Release，不是语义版本最高的**：按版本递增正常发版不会出问题；客户端 `isNewer()` 还会再比一次版本号，最坏是提示「已是最新」，不会误装旧包。不要补发旧版本。
- **附件配额**：社区版单附件 ≤100MB、单仓库附件总量 ≤1GB（GVP 分别 200MB / 5GB）。APK 只走 Release 附件，不要提交进 git（git 单文件上限 50MB、单仓 500MB）。长期发版需定期清理旧 Release 的附件。
- **下载内容校验**：`ApkDownloader` 会拒绝 `text/html` 响应，写盘后校验 ZIP 魔数与最小体积，防止网关返回登录页却被当成 APK 交给安装器。这层校验不替代系统对 APK 签名的校验。
- **未验证项**：Gitee 上传附件的 `browser_download_url` 在国内手机网络下能否免登录直连下载，需在首次真实发版后用真机端到端验证；CI 只保证附件上传成功。
