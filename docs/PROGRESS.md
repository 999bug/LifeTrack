# 项目进度 · 剩余工作子任务台账

> 维护规则：每完成一项先更新本文件再提交代码；进行中标 🔄，完成标 ✅。发版时同步在 `VersionHistory.kt` 最前面补一条用户可见的变化。
> 建立 2026-11-09。范围：README「下一阶段清单」+「安全边界」+ 各会话发现的欠账。
> **本文件只记「状态」与「还没做的事」**；逐项的实施细节、取舍与验收记录在各自的 `docs/*-confirmation.md`，改动过程在 `git log`。

---

## 0. 待办（中断恢复必读）

| 状态 | 事项 | 依赖 / 阻塞 | 下一步 |
| --- | --- | --- | --- |
| 🟡 | B3 TalkBack 实机朗读复核 | 需要真机 + 读屏 | 依次走 首页 → 历史 → 统计 → 设置，确认分段选中态、月历格、分布卡汇总句 |
| 🟡 | B7 真机「卸载重装丢数据」验证 | 需要真机 | 装 0.6.5 → 记录 → 卸载重装 → 确认数据丢失（预期丢失，验的是 README 表述是否准确） |
| 🟡 | 运动子应用真机走查 | 需要真机 | 点类型卡预选 → 保存后概览 / 今日记录同步 → 深色 / 200% / 360dp；口径见 `docs/sport-subapp-design.md` §7 |
| 🟡 | B8 / C8 厂商 ROM 迁移走查 | 需要真机 | 与 B7 合并执行，重点看厂商 ROM 的设备迁移是否带走业务库 |
| 🟡 | R11 CI 仪器测试任务未实跑 | `reactivecircus/android-emulator-runner` + KVM 链路 | 手动 dispatch 或给 PR 打 `run-instrumented` 标签；本地 `connectedDebugAndroidTest` 已全绿 |
| 🔵 | A9 月经 E2 / E3 | E1 已于 2026-11-12 交付 | 体重 / 体温、自定义症状、完整报告（E2）；通知提醒、备孕 / 怀孕模式（E3），见 `docs/menstruation-module-design.md` |
| 🔵 | 备份流程抽 `BackupController` | 无 | 把 `SettingsScreen` 里导出 / 恢复 / 换口令的 30 多个 state 与 4 套 launcher 抽成 state holder |
| 🔵 | 按屏拆 ViewModel | 需先引入 `TodayProvider` 之类的共享日期源 | `todayDate` 同时驱动首页 / 历史 / 月历 / 统计，各自维护会跨天不一致，先抽共享源再拆 |
| 🔵 | `latest.json` 首次生效验证 | 依赖下一次发版 | 发版后确认：文件已生成、`apkUrl` 能 HEAD 到 200、客户端能走到 `Available` |
| 🔵 | baseline profile 真机生成 | 需要 arm 设备 | 按 `docs/baseline-profile.md` §4 用 Macrobenchmark 生成，会整体替换手写版 |
| 🔵 | 0.6.6 发版前补 changelog | — | `VersionHistory.kt` 首条补 0.6.6，否则 CI 会按设计在构建前拦下 |

图例：🟡 需要设备 / 外部条件，🔵 待排期的改进，⛔ 明确挂起。

---

## 1. 已完成归档

| 任务 | 时间 | 结果 |
| --- | --- | --- |
| 发布链路 | `c16684d` → `331f6be` | 签名 APK + GitHub Actions + v0.1.0 验收 |
| 统计周 / 月 / 年改版 | `c95309f` | 暗夜仪表盘、`StatsWindow`、`StatsAggregator` |
| 数据安全包（明文） | `4deb6e0` | JSON 导出 / 恢复、事务替换、校验链 |
| 记录体验包 | `2b30495` | 原生日期时间选择器、历史分页与筛选 |
| 统计增强包 | `35e1fe9` | 月历视图、时段分布、形态分布；48 项单测 |
| 统计增强确认 | `b86314a` | Q1–Q9 全部勾选，与已实施推荐值一致 |
| 剩余工作台账 | `98a643a` | 33 项子任务、依赖、顺序与风险 |
| A1/A2 模块管理 | 2026-11-09 | 设置页「记录项目」、启用状态生效、`ModuleOrdering` 纯函数；53 项单测 |
| A3/A4/A5 通用编辑器与备份 v2 | 2026-11-09 | `RecordDetail` / `BackupRecord` 领域模型、备份 v2 兼容 v1；71 项单测；Room 仍 v1 |
| A6 饮水模块（升 Room v2） | 2026-11-09 | `event_detail` 表 + `MIGRATION_1_2` + `2.json`；饮水全闭环；83 项单测 |
| 应用内检查更新 + A6 合并 | `33ef32d` | 另一会话完成更新功能并一并提交 A6 代码；90 项单测全绿 |
| A6 台账与确认单 | `88c20d1` | 确认单 §6 实施记录、B1 双基线就绪、R8/R9 登记 |
| A7 咖啡 / 茶 | 2026-11-09 | 单类型 + 四字段规格；103 项单测；Room 仍 v2（验证「加类型不必升 Room」） |
| A8 / A11 / A12 + B 包门禁 | 2026-11-09 | 运动模块、跨面板口径统一、字段校验收口、B1 迁移测试、B2 仪器测试、B11 CI 门禁 |
| 发布 v0.2.0 | `b419afc` | versionCode 2；APK 14.4 MB；签名证书 `6A6CC4…59D9` 可覆盖安装 |
| 发布 v0.3.0 | `df10075` | versionCode 3；流水线 4m05s，新增 lint 门禁 |
| 设置页重构（v1） | 2026-11-09 | 按「记录 / 数据 / 外观 / 关于」四段，记录项目拆独立页，触摸目标 ≥48dp |
| B3 / B7 语义与备份验收 | 2026-11-09 | 分段切换 `selectable + Tab`、月历格 48dp 带播报、分布图一句汇总；SAF 导出恢复走通 |
| 发布 v0.4.0 | `24b4624` | versionCode 4；APK 13.8 MiB；sha256 与 API digest 一致 |
| 记录详情弹窗 | 2026-11-10 | `RecordDetailPresentation` + 底部弹窗；11 项新单测（共 126 项） |
| 应用内更新接国内源 | 2026-11-10 | Gitee 作镜像，`release.yml` 同步附件；检查更新 Gitee 优先、GitHub 兜底 |
| 发布 v0.5.0 | `408cc13` | versionCode 5；GitHub + Gitee 双端 Release；`mirror-gitee` job 16m54s |
| 0.5.0 真机验收 | 2026-11-10（用户） | 更新走国内网络；0.4.0 → 0.5.0 覆盖安装数据未丢 |
| C1 加密选型与 POC | 2026-11-10 | SQLCipher 4.20.0 + `SupportOpenHelperFactory`；debug APK +7.40 MiB |
| C2 Keystore 密钥管理 | 2026-11-10 | `PassphraseEnvelope` / `DatabaseKeyStore` / `DatabaseKeyRepository`；4 类失效原因明确报错 |
| C3 存量明文库迁移 | 2026-11-10 | 明文 → 加密搬迁，保留 `user_version`、WAL checkpoint、失败自动还原 |
| C4 导出文件加密 | 2026-11-10 | PBKDF2 210000 次 + AES-256-GCM，v3 容器头部纳入 AAD；v1/v2 仍可读 |
| C5 完整性校验与轮换 | 2026-11-10 | `BackupCodec.reencrypt`，换口令走「旧解 → 新编 → 自检解回」 |
| C6 恢复流程打磨 | 2026-11-10 | `PassphraseQuality` 四档强度 + `RetryBackoff`（2/4/8/16/30 秒冷却） |
| C7 应用锁与预览遮蔽 | 2026-11-10 | `AppLockGate`；API 30+ BiometricPrompt，API 26–29 走 Keyguard；`FLAG_SECURE` 遮最近任务 |
| C8 备份 / 迁移评审 | 2026-11-10 | 五个数据域在云备份与设备迁移两条链路全部排除；真机 ROM 走查待做 |
| C9 隐私审查 | 2026-11-10 | 全仓仅 3 条日志、无记录内容；无崩溃上报 / 剪贴板 / 通知 / 临时导出 |
| C10 发布前回归 | 2026-11-10 | `ThirdPartyNotices` + 设置页许可入口；安全边界 5 项改写；0.6.0 已发布 |
| A10 亲密关系模块 | 2026-11-10 | 默认停用、三项可空字段、L1 收敛、启用期间无条件 `FLAG_SECURE` |
| 发版版本号自动递增 | 2026-11-10 | CI 从最高 tag 推 patch+1、gradle `versionCode+1`，构建成功后才提交并打 tag |
| 流水线配方与自动快照 | 2026-11-10 | `docs/release-pipeline-recipe.md` + `export_release_pipeline.py`，`ci.yml` 接 `--check` |
| 月历按天查看 | 2026-11-11 | 底导「记录」改名「历史」；格子改类型图标；点日期弹 `DayRecordsSheet` |
| 首页模块网格重构 | 2026-11-11 | 「页头 + 双列模块卡 + 今日时间轴」；整卡即记录 |
| 设置页主题三档 + 统计页 token 收口 | 2026-11-11 | `ThemeMode` 浅 / 深 / 跟随系统，同步旧 `dark_theme` 键，回退不丢偏好；统计页卡片圆角与柱图 alpha 对齐 token |
| 国内更新改制品就绪判定 | 2026-11-11 | `.apk` + `.apk.sha256` 齐备才算就绪；缺附件进 `Pending` 退避重查，不回退 GitHub 下载 |
| 设置页新增更新日志 | 2026-11-11 | `VersionHistory.kt` 集中 0.1.0–0.6.4，弹窗展示最近 6 条，当前版本标「当前」 |
| 设置页重构 v2 原型 | 2026-11-11 | `docs/settings-redesign-v2-prototype.html` + 设计方案，按使用频率分层 |
| 设置页重构 v2 落地 | 2026-11-11 | 主卡 / 安全 / 数据与备份折叠 / 更多 / 应用更新 / 页脚；250 项单测 |
| 发版校验更新日志 | 2026-11-11 | `Resolve version` 校验 `VersionHistory.kt` 首条与本次版本号一致，构建前失败 |
| Release 说明与日志同源 | 2026-11-11 | GitHub 与 Gitee 的说明都取自更新日志首条 `summary` |
| 包体：R8 + ABI 过滤 | 2026-11-11 | 22 MB → 5.57 MB；dex 49 MB（3 个）→ 3.20 MB（1 个） |
| 运行时：统计聚合移出主线程 | 2026-11-11 | `statistics` 加 `flowOn(Default)`；历史分组的 `groupBy` 包进 `remember` |
| 启动：数据库打开移出主线程 | 2026-11-11 | `databaseState` 改 `StateFlow` + `Loading` 态；首帧 1.26s，开库 0.72s 并行 |
| 架构：设置页拆主卡与「更多」 | 2026-11-11 | 抽出 `PrimarySection` / `MoreSection`，状态仍留父层 |
| 架构：统计流提取 | 2026-11-11 | `observeStatistics(...)` 纯函数，调度与 `stateIn` 仍由 ViewModel 决定 |
| P0 `localDate` 索引（Room v3） | `357bd48` | 修范围查询全表扫；补 2→3 迁移测试 |
| P2 更新检查 24h 节流 | `b46800e` | 不再每次冷启动打 Gitee API；失败不写时间戳 |
| P3 图标转 WebP | `7e51066` | `res` 487 KB → 91 KB；`-v26` 目录合并回滚 |
| P4 整理数据库 | `0732bbb` | `wal_checkpoint(TRUNCATE)`，实测回收 286 KB；放弃 VACUUM |
| P5 配置缓存 | `fdaaadc` | 二次 `assembleDebug` 24s → 14s |
| P1 baseline profile | `4e08610` | 手写规则 + `profileinstaller`；未验证 AOT 生效 |
| 国内源改 latest.json | `f6837cd` | 绕开 Gitee REST 限流；`ManifestReleaseChecker` |
| 加固 latest.json 解析 | `98fd306` | version 空串、URL 域名白名单、Cache-buster |
| 发布 0.6.0（加密首发） | `3b5ea49` | APK 22,283,347 B；真机加密迁移确认通过 |
| 发布 0.6.5 | `274607a` | CI 自动递增到 versionCode 11；`changelog` 校验首次在真实 CI 通过 |
| 发布 0.6.6 | `12399b6` | APK 5.19 MB（22 MB 的 1/4）；GitHub 侧全绿，Gitee 镜像写 `latest.json` 时 jq 报错失败，已修根因并补写 |
| 发布 0.6.7 | `a657ad5` | **mirror-gitee 首次全绿**：jq 修复生效，`latest.json` 由 CI 自动从 0.6.6 更新到 0.6.7；APK 5.19 MB，两个 job 均 success |
| A9 月经模块（E1） | 2026-11-12 | 经期子应用（日历置顶 + 逐行填写 + 日记）、`CycleCalculator` 周期派生、起止动作自动补齐；66 项新单测（共 336 项 / 0 失败）；模拟器走查通过（启用 / 停用、FLAG_SECURE、补齐、持久化、200% 字体 / 360dp / 深色）；Room 仍 v3、备份格式不变；`VersionHistory.kt` 条目留待发版补 |
| A9 经期自动延续 + 移除截屏限制 | 2026-11-12 | ① 点一次「来了」后 `ongoingPeriodStart` 记起点，`syncOngoingPeriod` 每天把 `[起点..昨天]` 补成仅标记事件、今天由 `PeriodOngoing.span` 派生，点「走了」按 `closingDates` 收尾；日历「经期中的今天」改模块色实心。② 删除 `FLAG_SECURE`：`ScreenCapturePolicy` → `SensitiveTypes`，设置页截屏状态行 / 模块启用弹窗一并删除。单测 339 项 / 0 失败，`lintDebug` 全绿；Room 仍 v3、备份格式不变 |
| A13 运动子应用（节奏型） | 2026-11-13 | 新增隐藏底导的 `sport` 二级页：本周概览（次数 / 合计时长 / 三类占比条 / 7 天节奏）、三类快记卡（瑜伽 / 骑行 / 跑步，点卡开弹层并预选类型）、类型筛选记录列表、本期简报折叠；派生层 `SportStats` 纯函数不落库。`SPORTS` 收敛三类 + `LEGACY_SPORTS` 只读白名单（备份校验改用 `ALL_SPORT_VALUES`），旧记录与旧备份仍可读、编辑器不再静默改类型。单测 351 项 / 0 失败（新增 15 项）；Room 仍 v3、备份格式不变；设计文档 `docs/sport-subapp-design.md`、原型 `docs/sport-subapp-prototype.html` |

---

## 2. A · 模块扩展包

| 编号 | 子任务 | 状态 |
| --- | --- | --- |
| A1 | 模块管理页：列出 `track_type`、启用 / 停用、调整排序 | ✅ |
| A2 | 启用状态生效：首页按状态显示入口，停用不影响历史与统计 | ✅ |
| A3 | 通用记录编辑器抽象：抽出日期时间 / 备注 / 时区壳 | ✅ |
| A4 | 通用详情存储：事件 + 各类型可选详情 | ✅ |
| A5 | 备份格式 v2：导出 / 恢复新类型，旧备份可读 | ✅ |
| A6 | 饮水模块（试点，本项起升 Room v2） | ✅ |
| A7 | 咖啡 / 茶模块（只加类型与规格，不升 Room） | ✅ |
| A8 | 运动模块（同上） | ✅ |
| A9 | 月经模块（E1 核心闭环） | ✅ 2026-11-12；E2 / E3 未做 |
| A10 | 亲密关系模块 | ✅ 0.6.0 发布 |
| A11 | 统计 / 首页按类型适配，跨面板自洽 | ✅ |
| A12 | 新模块字段校验与备份范围 | ✅ |
| A13 | 运动子应用（节奏型）：本周概览 + 三类快记 + 筛选列表 + 简报，只留瑜伽 / 骑行 / 跑步 | ✅ 2026-11-13，真机走查待做 |

## 3. B · 测试与质量收尾包

| 编号 | 子任务 | 状态 |
| --- | --- | --- |
| B1 | Room Migration 正式测试（`MigrationTestHelper` + schema 基线） | ✅ |
| B2 | 仪器测试跑通 `connectedDebugAndroidTest` | ✅ |
| B3 | 无障碍回归：读屏顺序、48dp、汇总句 | 🟡 语义树达标，缺 TalkBack 实机 |
| B4 | 字体缩放 200% 全页回归 | ✅ |
| B5 | 横屏 / 平板 / 多尺寸回归 | ✅ |
| B6 | 深 / 浅色全页回归 | ✅ |
| B7 | 真机验收：安装、SAF 导出恢复、飞行模式、卸载重装 | 🟡 剩「卸载重装丢数据」 |
| B8 | 系统迁移 / 厂商 ROM 行为 | 🟡 与 C8 合并执行 |
| B9 | 文档欠账校准 | ✅ |
| B10 | 建立本台账 | ✅ |
| B11 | CI 门禁：lint + 仪器测试触发条件 | ✅ |

## 4. C · 安全加固包

| 编号 | 子任务 | 状态 |
| --- | --- | --- |
| C1 | 加密方案选型与 POC | ✅ |
| C2 | Keystore 密钥管理 | ✅ |
| C3 | 存量明文库迁移 | ✅ |
| C4 | 导出文件加密 | ✅ |
| C5 | 完整性校验与密钥轮换 | ✅ |
| C6 | 加密备份恢复流程 | ✅ |
| C7 | 应用锁 + 预览遮蔽 | ✅ |
| C8 | 备份 / 迁移 / ROM 评审 | 🟡 配置复核与模拟换机完成，真机 ROM 走查待做 |
| C9 | 隐私审查 | ✅ |
| C10 | 发布前安全回归 | ✅ 0.6.0 已发布并真机确认 |

---

## 5. 风险与已知遗留

| 编号 | 风险 | 说明 |
| --- | --- | --- |
| R2 | 备份兼容性 | C4 起默认导出 v3 加密备份，0.5.0 及更早版本读不了；需要与旧版互换时显式选明文导出。新版永久保留读 v1 / v2 明文的能力 |
| R4 | 通用事件模型膨胀 | 每加一个类型都要动 DAO / Repository / 备份 / 统计四处。A8 实测只需加类型 + 规格 + 编辑器 + 分派，抽象已稳定 |
| R6 | 台账维护 | 中断恢复只认本文件；进度只留在会话里不写进来，下次必然重议 |
| R7 | `track_type` 首次启动为空 | 已用 `ensureDefaultTypes()` 在 ViewModel 初始化时补内置类型（`IGNORE` 不覆盖用户设置） |
| R10 | A6 与更新功能混合提交 | `33ef32d` 同时含 A6 与更新功能，共管文件交织导致无法拆分；今后按包回溯请用 `git log -- <路径>`，别依赖单提交边界 |
| R11 | CI 仪器测试任务未实跑 | `ci.yml` 的 build job 已多次 success（单测 + lint + assembleDebug），instrumented job 按设计 skipped；emulator-runner 链路仍未真正执行 |
| R12 | 加密后不可降级 | 0.6.0 起的加密库无法被 0.5.0 及更早版本打开；用户卸载重装旧 APK 会命中。README 已写明「回退前先导出备份」 |
| R13 | CI 自动提交版本改动 | 发版会把 `chore: release …` 推回 `main`，本地会落后一个提交，下次开发前须 `git pull --ff-only`；`main` 若开分支保护要放行 `github-actions[bot]`。详见 `docs/release-guide.md` §2.2 / §2.3 |
| R14 | 国内主源只剩 latest.json 一条路（**v0.6.6 首次应验**） | `f6837cd` 后不再调用 Gitee REST API；若 CI 写 `latest.json` 那步失败，用户在应用内将无法下载更新（旧版被限流、新版读不到清单）。v0.6.6 发版时该步骤果然失败：Gitee contents API 对不存在的文件返回数组，`jq '.sha'` 报 "Cannot index array with string"，`set -e` 让 `mirror-gitee` exit 5 —— 而 APK 与 sha256 其实都上传成功了，只有清单没写成，国内用户将无法在应用内下载。已修（先判 `type == object`）并新增 `gitee-manifest.yml` 手工补写入口。0.6.7 发版时修复生效：`mirror-gitee` 全绿，`latest.json` 自动更新到 0.6.7（PUT 分支首次跑通），直链 302 可达。**仍建议每次发版后确认一次清单版本号与直链** |

> 已解除的历史风险（保留编号以免旧引用断链）：R1 加密缺失、R3 schema 与加密迁移撞车、R5 200% 字体遗留、R8 存量库 1→2 未实测、R9 多会话并发改动。详见各自 confirmation 文档与 `git log`。

---

## 6. 依赖与推荐顺序

- A 包全部完成（A9 于 2026-11-12 交付 E1，E2 / E3 待排期）；B 包剩 B3 / B7 / B8（都要真机）；C 包剩 C8（与 B8 合并）。
- 一包一交付：每包先出设计前确认单，确认后再写代码，验收通过后更新本台账并单独提交。A/B 收尾包用户曾明确「逐个完成不需要确认」，C 包同样按逐个完成执行。
- 下一轮优先级建议：**B7 + B8 + C8 合并一次真机走查** → B3 读屏复核 → 两个架构重构（`BackupController`、ViewModel 拆分）。
