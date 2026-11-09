# 发布与签名指南

> 适用版本 0.1.0 · 分发渠道：GitHub Releases · 流水线：`.github/workflows/release.yml`

这份文档说明怎么把「生活记」的签名 APK 发到 GitHub Releases，以及为什么签名密钥一旦丢失就无法挽回。

---

## 0. 为什么必须固定签名

- Release 包必须用同一个 keystore 签名。签名不一致时，Android 拒绝覆盖安装。
- 本项目 `android:allowBackup="false"`，且 MVP 尚未实现导出/恢复（见 README「本地存储、自动备份和主动导出边界」）。因此「卸载旧版 → 装新版」等于**永久丢失全部记录**。
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

---

## 2. 每次发版

1. 改 `app/build.gradle.kts`：`versionCode` **单调递增**，`versionName` 改成与 tag 一致（如 `0.1.1`）。
2. 如改过 Room schema，先导出 schema 并写 `Migration` + 迁移测试；README 与本文档的版本号同步。
3. 提交并推送到 `main`（流水线以触发时 `main` 的代码为准）。
4. 打开 `Actions → Release APK → Run workflow`，填 `version`，格式 `v<versionName>`，例如 `v0.1.1`。
5. 流水线依次执行：单元测试 → 构建 release APK → `apksigner` 验签并拒绝 debug 证书 → 生成 `.sha256` → 校验 tag 未被占用 → 创建 Release 并上传两个附件。

流水线入口：https://github.com/999bug/LifeTrack/actions/workflows/release.yml

---

## 3. 下载与校验

Release 页面：https://github.com/999bug/LifeTrack/releases

附件：

- `LifeTrack-<versionName>-release.apk`：正式签名包，Android 8.0（API 26）及以上可直接安装，系统可能要求允许「安装未知应用」。
- `LifeTrack-<versionName>-release.apk.sha256`：SHA-256 校验值。

Windows 校验：

```powershell
Get-FileHash .\LifeTrack-0.1.0-release.apk -Algorithm SHA256
```

把输出与 `.sha256` 文件内容比对。`sha256` 只证明下载完整，安装包身份由系统在校验 APK 签名时保证。

---

## 4. 回滚与约束

- **不复用版本号 / tag**：流水线检测到同名 tag 已存在会直接失败；不要用 `gh release upload --clobber` 覆盖旧附件。
- 需要回退时，发一个**更高 `versionCode`** 的回退版本，别让用户降级安装（Android 不允许）。
- 覆盖安装前确认 Room `version` 与 `Migration` 已就位，否则启动即崩。
- 密钥轮换意味着用户必须卸载重装 → 数据全丢，非必要不做。

---

## 5. 发布前检查清单

- [ ] 4 个 Repository secrets 齐备
- [ ] `versionCode` 递增、`versionName` 与 tag 一致
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` 本地通过
- [ ] README / 本文档版本号已同步
- [ ] Room schema 与 Migration 已就绪（若改表）
- [ ] 真机做过一次「旧版 → 新版」覆盖安装，确认数据完整
- [ ] Release 页面两个附件可下载，`Get-FileHash` 与 `.sha256` 一致
