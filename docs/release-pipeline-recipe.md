# 发版流水线配方 · 供规范归纳的事实底稿

> 用途：把本项目「手动触发 → CI 自动递增版本 → 构建 → 回写版本提交 → 打 tag → 发 Release → 同步镜像」这条链路，沉淀成可被 `ai-dev-standards` 按项目类型归纳的结构化输入。
> **不是操作手册**：怎么点按钮、密钥怎么配、失败怎么补，见 [发布与签名指南](release-guide.md)。
> **不要手改「事实快照」**：那一节由 `scripts/export_release_pipeline.py` 从真实源文件生成，`ci.yml` 会校验它没漂移。

---

## 0. 这份文档怎么被消费

| 消费者 | 入口 | 得到什么 |
| --- | --- | --- |
| 人 | §2–§6 | 设计理由、按项目类型的移植矩阵、红线、归纳建议 |
| 规范归纳工具 | `python scripts/export_release_pipeline.py --facts` | `key=value` 事实流，风格对齐 `ai-dev-standards/scripts/detect-profile.sh` |
| CI 自检 | `python scripts/export_release_pipeline.py --check` | 实现改了但文档没重生成就返回非 0 |
| 维护者 | `python scripts/export_release_pipeline.py --write` | 重写 §1 快照区块 |

脚本只依赖 Python 标准库，输出**不含任何 secret 值、邮箱、内网地址**，可安全贴进对话或公开仓库。

---

## 1. 事实快照（自动生成）

<!-- BEGIN AUTO-SNAPSHOT -->
> 由 `scripts/export_release_pipeline.py --write` 从 `.github/workflows/release.yml` 与 `app/build.gradle.kts` 生成，**不要手改**。
> 输入指纹：`release.yml` = `f4cd4d7b41f8` · `build.gradle.kts` = `3037715b4813`。

### 机器可读事实

```text
# 只含公开字段；不含 secret 值、邮箱、内网地址
project_type=android
project_type_evidence=app/src/main/AndroidManifest.xml
package_manager=gradle
build_file=app/build.gradle.kts
application_id=com.example.lifetrack
min_sdk=26
version_name=0.6.0
version_code=6
version_scheme=semver_auto_patch
version_source=highest_vX.Y.Z_tag
version_code_source=gradle_plus_one
version_input=optional_override
version_write_back=yes
version_write_back_target=app/build.gradle.kts
release_trigger=workflow_dispatch
release_branch_guard=main_only
checkout_fetch_depth=0
ci_version_commit=yes
ci_version_commit_timing=after_build
ci_version_commit_skip_ci=yes
concurrency_group=release-apk
concurrency_cancel_in_progress=false
tag_conflict_guard=yes
release_target=version_commit_sha
artifact_pattern=LifeTrack-*-release.apk
checksum=sha256
signature_verify=apksigner
build_command=./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon
mirror=gitee_openapi
mirror_tag_source=needs.release.outputs.tag
contents_permission=write
secrets=GITEE_TOKEN,GITHUB_TOKEN,SIGNING_KEYSTORE_BASE64,SIGNING_KEYSTORE_PASSWORD,SIGNING_KEY_ALIAS,SIGNING_KEY_PASSWORD
workflow_sha256_12=f4cd4d7b41f8
build_file_sha256_12=3037715b4813
step_count=16
```

### 流水线步骤（按 workflow 顺序）

1. `Checkout`
2. `Set up JDK 21`
3. `Install required SDK packages`
4. `Prepare release signing config`
5. `Resolve version`
6. `Bump version in build file`
7. `Build and test`
8. `Upload lint report`
9. `Verify signature and write checksum`
10. `Upload build artifact`
11. `Commit version bump to main`
12. `Create GitHub Release`
13. `Remove decoded keystore`
14. `Download release APK`
15. `Check Gitee configuration`
16. `Create Gitee release and upload attachments`
<!-- END AUTO-SNAPSHOT -->

---

## 2. 流水线时序与设计理由

顺序本身就是规范，任意一步失败即停：

1. **Checkout（`fetch-depth: 0`）**：必须拿到全部历史 tag，否则算不出下一个版本。
2. **解析版本（Resolve version）**：从仓库里最高的 `vX.Y.Z` tag 推 `patch+1`；从构建文件读 `versionCode+1`；校验触发分支、tag / Release 未占用、手填值格式与高低。纯计算、不写文件。
3. **回写版本（Bump version in build file）**：在 runner 工作副本里改构建文件的两个字段，读回校验。此时**尚未推送**，后面任何失败都不留垃圾提交。
4. **构建 / 测试 / lint / 验签 / 校验和**：用新版本构建，所以产物名、包内版本、tag 三者一致；`apksigner verify` 并显式拒绝 debug 证书。
5. **提交版本改动（Commit version bump to main）**：放在所有可能失败的步骤之后。提交信息带 `[skip ci]`；推送前用 `git ls-remote` 确认 `main` 没被推进，有并发就中止重跑，不做 rebase / merge。
6. **创建 Release**：`--target` 指向上一步的版本提交，而不是触发时的旧 SHA。
7. **镜像 job**：通过 `needs.release.outputs.tag` 拿 tag（**不能**读 `inputs.version`，留空触发时那是空字符串），复用同一份 artifact 上传附件。

**为什么版本必须落回源码**：`versionCode` 决定 Android 能否覆盖安装，`versionName` 决定 APK 文件名，应用内「检查更新」又拿它和 `releases/latest` 比大小。只在 CI 里算一个 tag、不回写构建文件，装到手机上仍是旧版本。这是「自动递增」与「纯 CI 版本号」两种方案的分水岭，也是本项目 2026-11-10 一次撞 tag 失败的根因。

**为什么版本提交要放在最后**：CI 写 `main` 是越界能力。构建失败不产生提交，才能保证 `main` 上每一个 `chore: release …` 都对应一个真实可发布的产物。

---

## 3. 按项目类型的移植矩阵

归纳的关键不是语言，而是**「版本号是否影响产物身份或运行时行为」**：影响就必须回写源码，不影响就只需 CI 算 tag。

| 项目类型 | 版本真源 | 递增规则 | 回写源码 | 产物 | 发布前校验 |
| --- | --- | --- | --- | --- | --- |
| **Android / Gradle**（本项目） | `app/build.gradle.kts`：`versionCode` + `versionName` | 最高 tag 的 patch+1；code 取当前值+1 | **必须**：覆盖安装、APK 文件名、应用内检查更新都依赖它 | 签名 APK + `.sha256` | `apksigner verify` + 拒绝 debug 证书 |
| Maven / Spring Boot | `pom.xml` `<version>` | patch+1；或 SNAPSHOT → release | 产物名 / `MANIFEST` 带版本就要回写；只用 tag 命名产物可不回写 | jar + `.sha256` | 单测 + 启动冒烟 |
| Node / 前端 | `package.json` `version` | patch+1 | 产物名、`__VERSION__` 注入或运行时读 package 时要回写 | dist / npm tarball | 单测 + build |
| Python | `pyproject.toml` 或 `__version__` | patch+1 | 静态版本要回写；用 `setuptools-scm` / `hatch-vcs` 从 tag 生成则**不用** | wheel / sdist | `twine check` + 安装冒烟 |
| Docker / 常驻服务 | 无（源码版本固定在 pom/package） | `v1.0.<run_number>` 或日期 | **不需要**：版本号只作镜像 tag 与 Release 名 | 镜像 + 离线 tar | 拉镜像跑一次冒烟 |
| 静态站点 / Pages | 无 | 通常无版本号 | 不需要 | dist 目录 | 构建 + 链接检查 |

> 对照参考：`F:/ai/kudu-console` 是上表「Docker / 常驻服务」那一行的实例——`pom.xml` 固定 `1.1.0`，CI 留空就取 `v1.0.${GITHUB_RUN_NUMBER}`，版本只用于镜像 tag 和 Release 名，**从不写回源码**。照搬到 Android 会导致用户装到的还是旧版本。

### 按类型必改的字段

| 字段 | Android | Maven | Node | Python | Docker |
| --- | --- | --- | --- | --- | --- |
| 版本真源文件 | `app/build.gradle.kts` | `pom.xml` | `package.json` | `pyproject.toml` | 无 |
| 回写用的替换命令 | `sed -E` 两处 | `mvn versions:set` | `npm version --no-git-tag-version` | `sed` / VCS 插件 | 无 |
| 产物 glob | `LifeTrack-*-release.apk` | `target/*.jar` | `dist/*` | `dist/*.whl` | 镜像 tag |
| 验签 / 完整性 | `apksigner verify` + sha256 | sha256 | sha256 | sha256 | digest |
| 是否需要「CI 提交版本」授权 | 是 | 视回写而定 | 视回写而定 | 视回写而定 | 否 |

### 与类型无关的通用骨架

手动 `workflow_dispatch` 触发 → 单一 `concurrency` 组且不取消进行中 → 构建前算版本并校验 tag / Release 未占用 → 构建 + 测试 + 完整性校验 → 成功后才提交版本（若需回写）→ 以该提交为 target 建 tag 与 Release → 镜像 job 从 `needs.<job>.outputs` 取 tag，复用同一份 artifact。

---

## 4. 通用不变量（换项目也别改）

1. **tag / 版本号 / 产物名三者一致**，且不复用、不 `--clobber`。
2. **只在 `main`（或约定发布分支）触发**；从其它分支跑出来的版本提交没有意义。
3. **构建前算版本、构建后提交版本**，中间任何失败都不污染主分支。
4. **并发保护**：`cancel-in-progress: false` + 推送前 `ls-remote` 比对；发现主分支前进就中止，绝不自动 rebase / merge。
5. **secret 先校验再构建**，缺哪个报哪个名字；值不进日志、不进文档、不进产物名。
6. **产物字节一致**：双端分发复用同一份 artifact，不各自构建。
7. **镜像 job 的 tag 来自 job outputs**，不来自原始 input（input 可能为空）。
8. **失败按阶段分类补救**：版本提交前失败 = 无副作用可直接重跑；版本提交后、Release 前失败 = 需人工确认；Release 后镜像失败 = 只补镜像，绝不重跑整条流水线。
9. **产物校验只证明完整性，不证明来源**；身份由签名 / digest 保证。
10. **CI 写主分支必须显式授权**，并在文档里写清分支保护与本地 `pull --ff-only` 的后果。

---

## 5. 红线与已知代价

- **分支保护**：`GITHUB_TOKEN` 默认不能绕过分支保护。`main` 若要求 PR / status check，需放行 `github-actions[bot]` 或配置 bypass，否则版本提交推送失败。
- **本地会落后一个提交**：CI 推了 `chore: release …` 之后，开发者继续 push 会被 non-fast-forward 拒绝，下一轮开发前必须 `git pull --ff-only`。
- **重跑不是幂等的**：若版本提交已推送而 Release 未建，重跑会再升一次 `versionCode` 并跳过一个 patch，必须先查 `main` 与 Actions 日志。
- **签名密钥不可轮换**：`allowBackup=false` 的本地优先应用，换密钥等于用户卸载重装、数据全丢。
- **不降级**：需要回退就再发一个更高 `versionCode` 的版本。
- 自动递增只处理 `patch`；要跳 minor/major 时手填 `v0.7.0`，手填值必须严格高于最高 tag。

---

## 6. 给 `ai-dev-standards` 的归纳建议

以下是**建议**，本轮不修改那个仓库；把 `--facts` 输出喂给它即可开始归纳。

- **按「是否回写版本」二分，而不是按语言罗列**：这是决定流水线形状的唯一分叉。回写型才有「CI 提交版本 + 分支保护 + 本地落后」这一整组约束；不回写型（Docker / Pages）可以完全不碰主分支。
- **`detect-profile.sh` 可新增的探测 key**（判据都是 grep，不读值）：
  `version_write_back`（workflow 里是否有 `Bump version in build file` / `versions:set` / `npm version`）、
  `version_source`（`highest_semver_tag` / `run_number` / `date`）、
  `ci_version_commit`（是否有 `git push origin HEAD:main`）、
  `trigger_input_required`（`workflow_dispatch.inputs.*.required`）、
  `mirror_tag_source`（`needs.*.outputs.tag` vs `github.ref_name`）、
  `signature_verify`（`apksigner` / `cosign` / `gpg` / none）。
  项目类型判据沿用现有 `app/src/main/AndroidManifest.xml` 优先于 `build.gradle` 的规则。
- **`project-profile.md §4`** 的 `version_scheme` 枚举建议扩为 `semver_manual | semver_auto_patch | run_number | date | none`，并补 `trigger_input: none | optional | required`；`release_trigger` 现有三值仍够用。
- **`.agents/release-android.md §5`** 现在写「【默认】用 `workflow_dispatch` 手动触发，**参数是版本号**」，与本项目现状冲突；建议改成「手动触发；版本号可手填或留空由 CI 递增」，并新增一节讲 CI 回写版本的授权边界与重跑非幂等。
- **`.agents/release-github.md`** 建议补「CI 用 `GITHUB_TOKEN` 提交版本改动」的边界：只在构建成功后提交、`[skip ci]`、分支保护放行、并发中止、失败分阶段补救。
- **`.agents/release-android.md §4` / §9** 里「`versionCode` 递增、`versionName` 与 tag 一致」仍然成立，但可以补一句「这两项可由 CI 自动保证，人不要再手改构建文件」。

---

## 7. 维护方式

```bash
# 看机器可读事实（只输出公开字段）
python scripts/export_release_pipeline.py --facts

# 改了 release.yml 或 build.gradle.kts 之后重生成 §1
python scripts/export_release_pipeline.py --write

# 只校验是否漂移（CI 用；不相等返回 1）
python scripts/export_release_pipeline.py --check
```

`ci.yml` 的 `build` job 已接入 `--check`：改了流水线或版本文件却没跑 `--write`，PR 会直接标红。**发版流水线在提交版本改动前会先跑一次 `--write`**，把新版本号同步进快照并一起提交，所以 CI 自动回写版本不会留下漂移。脚本只读取 `.github/workflows/release.yml` 与 `app/build.gradle.kts`，不联网、不读 git 历史，浅克隆下结果也稳定。
