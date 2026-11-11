# 发版教训清单

> 用途：把每次发版踩到的坑固化成可执行规则，供 `ai-dev-standards` 归纳成通用条款。
> 与 `docs/release-pipeline-recipe.md` 的分工：那份是「流水线现在长什么样」的事实快照，**这份是「为什么会这么设计、什么情况会炸」的经验**。
> 编号只增不改，失效条目标「已失效」保留，避免旧引用断链。

## 0. 怎么用

- 每条 = **现象 → 根因 → 规则 → 自检**。规则用祈使句，能直接落成 CI 检查或 runbook 步骤。
- 新坑按 L 编号追加；跨项目可复用的，标注 `[通用]`，只适用于本项目 CI 的标注 `[本项目]`。

### 与其他坑库的分工

| 文件 | 范畴 | 能否直接喂规范 |
| --- | --- | --- |
| **本文件** `docs/release-lessons.md` | 发版流程的教训 + 可复用规则（L1–L12） | ✅ 直接可抽取 |
| `.agents/project-profile.md` §9 | 全项目雷区库：构建链路、Compose 布局、Room 迁移、并发写入… | ❌ 是本项目事实，不是通用条款 |
| `docs/github-release-to-gitee-sync.md` §10 | Gitee 同步方案本身的坑（换项目可复用） | 已收敛到本文件的 L9–L12，§10 只留三条高频 |

改这三份中的任何一份前先看这张表：**同一件事只在一个地方展开**，别处留指针。

---

## 1. 教训

### L1 `[通用]` 第三方 API 的「不存在」不一定是 404

**现象**：2026-11-11 发 v0.6.6，`mirror-gitee` 最后一步写 `latest.json` 报

```
jq: error (at <stdin>:0): Cannot index array with string "sha"
##[error]Process completed with exit code 5.
```

**根因**：Gitee 的 `GET /repos/{owner}/{repo}/contents/{path}` 对**不存在的文件**返回 `[]`（目录列表语义），不是 404。代码写的是

```bash
EXISTING_SHA=$(curl -sS "$API" | jq -r '.sha // empty')
```

在数组上取 `.sha` 直接抛错；脚本开头是 `set -euo pipefail`，管道失败直接终止整步。

**规则**：

- 解析第三方响应前先判类型，别假设成功分支一定是对象：
  ```bash
  jq -r 'if type == "object" then (.sha // empty) else empty end' 2>/dev/null || true
  ```
- 任何「先查再写」的幂等逻辑，必须显式覆盖「资源不存在」的形态；`404`、`[]`、`null`、`{"message":"not found"}` 都要能落到「走创建分支」。
- `set -euo pipefail` 下，`curl | jq` 的管道失败会终止整步。对**预期可能失败**的探测要显式兜底（`|| echo ''`），但**写入后的校验**不能兜底——那会把真失败吃掉。

**自检**：本地 `curl` 打一次**不存在的**路径，看返回体的 JSON 类型，再写解析逻辑。

---

### L2 `[通用]` 「流水线失败」≠「产物没发布」

**现象**：v0.6.6 的 run 结论是 failure，但 GitHub Release、Gitee Release（id=1297709）、APK、`.sha256` **全部已就绪**，只差一份 1 KB 的清单文件。

```
09:44:40 Uploaded LifeTrack-0.6.6-release.apk
09:44:47 Uploaded LifeTrack-0.6.6-release.apk.sha256
09:44:49 jq: error ... → exit 5
```

**根因**：失败发生在**最后新增的一步**上；前面步骤已经产生了不可回滚的外部副作用（创建 Release、上传附件）。workflow 的失败状态描述的是「脚本没跑完」，不是「产物不存在」。

**规则**：

- 发版失败后**先查产物，再决定补什么**，不要条件反射地重跑：
  ```bash
  gh release view <tag> --json assets     # GitHub 侧有没有包
  gh run view <id> --log-failed           # 失败到底在哪一步
  ```
- 重跑整个 release 会撞 tag（tag 不能复用）、重复递增 versionCode。**能补单步就补单步**。
- 新增的收尾步骤要设计成**可独立重跑**：单独的 workflow，或 `workflow_dispatch` 的 `*_only` 输入。项目里的 `gitee-manifest.yml` 就是为此存在。
- 补写类操作必须先校验前置事实（附件真实可达），再写指针文件——避免写出指向死链的清单。

**自检**：`gh release view <tag> --json assets` + 两条直链 `curl -I`。

---

### L3 `[通用]` 唯一主源的新链路，第一次必须当灰度

**现象**：为了让客户端绕开 Gitee REST 的匿名限流（403 Rate Limit），把国内主源换成了仓库里的一份 `latest.json`。v0.6.6 首次发版时这份文件没写成功，于是：

- 客户端读不到清单 → 回退 GitHub；
- 回退只提供**版本事实**、剥掉制品（不跨境下载）；
- UI 显示「更新信息获取中」，**没有下载入口**。

用户看到的是「检查得到新版本，但装不了」——比「检查不到更新」更糟。

**根因**：新链路的写入步骤从未在真实 CI 跑过，却直接成了**唯一**主源。

**规则**：

- 任何「唯一主源 / 唯一分发通道」的替换，第一次上线单独发一版验证，不要跟功能发布捆在一起。
- 客户端必须区分三种状态：**没更新的版本信息** / **有版本但制品未就绪（可重试）** / **源不可用**。三者的文案与交互不能混用。
- 服务端要有独立的修复入口（补写清单、补传附件），不依赖重跑主流程。

**自检**：发版后 `curl -s '<manifest>?t=$(date +%s)'`，确认 `version` 等于刚发的版本；客户端实机点一次「检查更新」。

---

### L4 `[通用]` 缓存会吃掉「刚发布」这个事实

**现象**：清单放在文件 CDN 上（`raw/<branch>/latest.json`），发版后可能长时间读到旧版本，客户端据此判断「有没有新版」就会滞后。

**根因**：CDN 没有主动失效接口，且 `raw` 路径的缓存时长不可控。

**规则**：

- 客户端请求加时间戳绕开缓存：`"$URL?t=${System.currentTimeMillis()}"`，并置 `useCaches = false`。
- 用**已存在的文件**先验证 query 参数不被服务端拒绝（本项目实测 `README.md?t=...` 正常返回）。
- 判断「是否有新版本」的请求，一律不走任何可缓存层。

---

### L5 `[通用]` 网络报错不等于操作没生效

**现象**：发 v0.6.8 时 `git push` 报

```
fatal: unable to access 'https://github.com/...': CONNECT tunnel failed, response 502
```

但服务端其实**已经接受了该提交**；重试时报

```
! [remote rejected] main -> main (cannot lock ref 'refs/heads/main':
  is at 1603181875... but expected 20dfc7c0...)
```

**根因**：客户端在收到响应前就断开了，本地判定失败、远端已经成功。

**规则**：

- push / clone 报网络错误后，**先核实远端状态**再重试：
  ```bash
  git fetch origin && git log --oneline origin/main -1
  ```
- 看到 `cannot lock ref` / `fetch first` / `non-fast-forward` 时**停止重试**，先对齐远端。
- CI 侧同理：`curl` 写接口超时不代表没写成功，重试前先 GET 一次确认。

---

### L6 `[通用]` 版本号是三方一致的契约

**现象**：历史上一度要求人工同时维护「tag 输入」「`versionCode`」「`versionName`」三处，任一滞后就撞号或版本不一致（2026-11-10 18:04 那次 `v0.6.0` 撞已存在 tag 即此因）。

**规则**：

- 版本号由 CI 从**最高 tag + patch+1** 推导，`versionCode` 从 `build.gradle.kts` 读当前值 +1，构建成功后才把新版本写回源码并打 tag。
- 应用内更新日志是**编译进 APK 的静态列表**，CI 递增版本号不会替它补条目。构建前必须校验「`VersionHistory.kt` 首条 == 本次要发的版本号」，不一致直接失败。
- 不要手改 `app/build.gradle.kts` 的 `versionName` / `versionCode`——会和已发布 tag 脱节。

---

### L7 `[本项目]` 改了流水线必须重生成事实快照

**现象**：并发会话改了 `release.yml` 但没重生成 `docs/release-pipeline-recipe.md`，`ci.yml` 的 `--check` 直接红。

**规则**：改 `.github/workflows/release.yml` 或 `app/build.gradle.kts` 后，跑

```bash
python scripts/export_release_pipeline.py --write
```

否则快照里的 `workflow_sha256_12` / `step_count` 与实现漂移。

---

### L8 `[通用]` 验证命令本身会撒谎：curl 默认不跟随重定向

**现象**：用 `curl -s '<manifest>'` 验证清单，拿到的是

```html
<a href="https://raw.giteeusercontent.com/...&amp;t=...">Found</a>.
```

看着像「文件损坏 / 返回了 HTML」。实际是 Gitee 把 `raw` 路径 **302** 到了 CDN。

**根因**：`curl` 不加 `-L` 不跟随重定向；而客户端用的 `HttpURLConnection` 默认 `instanceFollowRedirects = true`。**验证工具的行为和被测客户端不一致**，于是误判。

**规则**：

- 验证脚本里的 curl 一律显式 `-L`，同时打印最终 URL，避免把重定向页当内容：
  ```bash
  curl -sL -o /dev/null -w '%{http_code} %{url_effective}\n' <url>
  ```
- 判断「接口行为」以**客户端实际使用的 HTTP 栈**为准（本项目是 `HttpURLConnection`，不是 curl）。
- 返回体像 HTML、像登录页、像空串时，先怀疑重定向 / 反爬 / 缓存，再怀疑内容损坏。

**自检**：`curl -sL` 取到的 JSON 能直接 `jq .` 成功，才算清单可读。

---

### L9 `[通用]` 「镜像仓库」不等于「镜像发布产物」

**现象**：Gitee 的仓库镜像配好了，代码和 tag 都同步过去，但 Releases 页面没有 APK。

**根因**：Gitee 官方镜像只同步**分支、标签、提交**，Release 附件不在同步范围内。同理，两端包哈希不一致，往往是因为在镜像端重新构建或人工传错，而不是复制同一份产物。

**规则**：

- 附件必须由 CI 调 OpenAPI 单独上传，不能指望仓库镜像。
- 两端必须是**同一份 artifact**：从已发布的 Release 下载再上传，不要重新打包。
- 上传成功后把返回的 `browser_download_url` 记下来，后续清单/文档都以它为准。

---

### L10 `[通用]` 第三方 `latest` 是「最后创建」，不是「语义最高」

**现象**：补发一个旧版本后，`/releases/latest` 返回的是旧版本。

**根因**：Gitee 没有「设为 latest」的 API，按创建时间取最后一条。

**规则**：

- 按版本递增正常发版，**不补发旧版本**；确实要补，先确认客户端的版本比较能兜住。
- 客户端拿到 `latest` 后必须自己比一次版本号（`isNewer`），最坏结果是提示「已是最新」，不能反向提示降级。

---

### L11 `[通用]` 建 Release 的 404 / 422 各有明确成因

**现象**：`POST /releases` 返回 404 或 422。

**根因**：

- 404：`owner` / `repo` 写错；或 `target_commitish` 用了 GitHub 的 `main` 而 Gitee 仓库默认分支是 `master`。
- 422：同名 tag 已存在；或 `prerelease` 写成了 `False` / `1`（Gitee 只认小写 `false` / `true`）。

**规则**：

- 分支名、owner、repo 全部走仓库变量，不要硬编码。
- 建 Release 前先 `GET /releases` 确认仓库可访问、同名 tag 不存在；**拒绝覆盖**而不是覆盖。
- 布尔值统一小写字符串。

---

### L12 `[通用]` 上传成功的判据是「能下载」，不是「返回 200」

**现象**：上传接口返回 200，但网页上看不到附件。

**根因**：只判断了 HTTP 状态码，没解析响应体里的下载地址。

**规则**：

- 以响应里的 `browser_download_url`（或等价字段）存在为成功凭据，缺失即报错退出——和 L2 的原则一致：**用产物本身证明成功**。
- 上传 401 先查令牌是否过期、权限是否勾全；上传 413 是附件额度问题，清理旧 Release 附件或拆包，别把产物提交进 git。

---

## 2. 发版后必查清单

可直接抄进 runbook，四项全绿才算发完：

| # | 检查 | 命令 | 期望 |
| --- | --- | --- | --- |
| 1 | GitHub Release 资产 | `gh release view vX.Y.Z --json assets` | APK + `.sha256` 都在 |
| 2 | Gitee 直链可达 | `curl -s -o /dev/null -w '%{http_code}' -I <apkUrl>` | `200` 或 `302` |
| 3 | 清单版本号 | `curl -sL '<manifest>?t=$(date +%s)' \| jq -r .version` | 等于刚发的版本 |
| 4 | 客户端实机 | 装上一版 → 设置页「检查更新」 | 提示新版本 **且** 能下载安装 |

第 3 项最容易漏：前三项过了只说明「产物在」，第 3 项才说明「客户端能发现它」。第 4 项是唯一无法自动化的，只能人工点一次。

---

## 3. 本次发版的时间线（供复盘参照）

| 版本 | 结果 | 关键事实 |
| --- | --- | --- |
| v0.6.5 | ✅ | 更新日志校验首次在真实 CI 通过 |
| v0.6.6 | ⚠️ | 产物全部就绪，`latest.json` 写入失败（L1）；手工补写恢复 |
| v0.6.7 | ✅ | 修复生效，`latest.json` 由 CI 自动更新（PUT 分支首次跑通） |
| v0.6.8 | ✅ | 第三次连续自动更新成功，链路稳定；过程中 `git push` 遇 502 但服务端已接受（L5） |

四个版本连发暴露的共性：**问题都出在「新增的收尾步骤」和「新增的唯一依赖」上，核心构建链路一次没红过。** 新增收尾步骤时，默认它一定会失败一次，提前准备「不重跑主流程」的补救路径。
