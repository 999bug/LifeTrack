# A9 月经记录模块 · 设计文档（待评审）

> 建立日期：2026-11-12 · 对应台账 `docs/PROGRESS.md` A9 · 状态：**设计评审中，本轮不写任何代码**
> 修订 v2（2026-11-12）：按「定位与布局诊断」修正 UI 落点——首页只留一句周期状态 + 入口，预测日历改到新增的 `cycle` 二级页；**历史页与 `CalendarMonth` 完全不动**。配套可交互原型 `docs/menstruation-module-prototype.html`。改动集中在 §3 / §4 P6 / §5.4 / §5.7–§5.10 / §6。
> 修订 v3（2026-11-12 13:39，用户拍板）：把 `cycle` 二级页从「轻量周期页」**升级为完整的经期子应用**——点「记录月经」模块卡直接进入，首屏信息架构接近参考 App，让使用者无缝使用。范围 = 经期核心闭环（不含账号 / 云同步 / 社区 / 电商 / 资讯 / 孕产模式 / 推送）；数据仍走 `track_event` + `event_detail`，不另起库。本轮只改文档 v3 与原型 v3，**仍不写 Compose 代码**。改动集中在 §1 / §4 P6、P10、P11 / §5.4 / §5.7–§5.10 / §6。
> 修订 v4（2026-11-12 14:03，用户指示）：把经期子应用内的**交互形态改成参考 App 的方式**——日历置顶选日期，下方一行一行填当天内容，每行带图示，并**新增一行日记**；点行展开选项、即点即存，取消子应用内的底部弹层编辑器。日记复用 `TrackEventEntity.note`，不新增详情字段、不改备份格式。本轮只改文档 v4 与原型 v4，**仍不写 Compose 代码**。改动集中在 §1 / §2 #7 / §4 P4、P12 / §5.3 / §5.4 / §5.7–§5.10 / §6 / §7。
> 修订 v5（2026-11-12 14:47，用户拍板）：**今天页关于月经只保留一张「月经」模块卡**，删除 v2 起加在模块网格下方的周期状态行（「经期第 3 天 / 打开经期 → / 按最近 6 个周期估算」）与紧随其后的「已开启截屏保护」提示行；**其它四张模块卡、今日记录、底部导航全部保留**。周期状态只在经期子应用内呈现；`FLAG_SECURE` 机制本身不变，仅首页不再重复说明。改动集中在 §4 P6/P7/P10 / §5.4 ① / §5.5 / §5.7 / §5.8 / §5.9 / §5.10 / §6 / §7。本轮只改文档 v5 与原型 v5，**仍不写 Compose 代码**。
> 用户指示：「把记录月经的功能加上，参考美佑 app，他的那些功能抄过来，先出设计文档」。参考对象按**美柚（Meiyou，经期记录 / 女性健康管理 App）**理解，下文简称「参考 App」。
> 解禁说明：A9 原为 ⛔（用户 2026-11-10「先不开发，留着」，见 `PROGRESS.md:17,109`），本次指示视为解禁动议。**台账状态不改**，等本设计评审通过、代码落地时再一并从 ⛔ 改起，避免「文档已改、功能未做」的状态错位。
> 代码基线：`main` `d7de0cb`；`app/build.gradle.kts:19-20` versionCode 14 / versionName 0.6.8，最高 tag `v0.6.8`；`AppDatabase.kt:22` Room **version 3**；备份正文 `SCHEMA_VERSION = 2`（`BackupCodec.kt:37`）、加密容器 `ENCRYPTED_SCHEMA_VERSION = 3`（`:41`）。
> 规范依据：`AGENTS.md` 1.13.0 分流 → `lang-android.md` / `discipline.md` / `commit.md`；项目事实以 `.agents/project-profile.md` 为准。设计体例沿用 `docs/intimacy-module-confirmation.md`（设计前确认单）。

---

## 1. 目标、约束与非目标

**目标**：在现有 Local First 记录框架内，新增第 6 个记录类型 `menstruation`，并做一个**隐藏底导的经期子应用**（`composable("cycle")`）：点首页「记录月经」模块卡直接进入（首页关于月经只保留这一张卡，见 P6 / P10），按参考 App 的交互方式——**日历置顶选日期，下方一行一行填当天内容，每行带图示，末行为日记**——承载「按天记录 → 归并经期段 → 推算周期 → 日历与报告」的完整闭环，让熟悉参考 App 的用户无需重新学习。子应用内点行展开选项、即点即存；数据仍落在同一套 `track_event` + `event_detail`，日记复用 `TrackEventEntity.note`，并完成高敏感数据应有的隐私收敛、备份往返与测试覆盖。

**项目硬约束（决定哪些功能不能抄）**，依据 `README.md:159-181`、`project-profile.md §9`、`docs/security-hardening-confirmation.md`：

| 约束 | 含义 |
| --- | --- |
| Local First / 无服务端 | 数据只在本地 SQLCipher 加密库；`allowBackup=false`；无账号、无云同步、无社区 |
| 网络唯一例外 | `INTERNET` 只服务于「应用内检查更新」（`AndroidManifest.xml:8-9`）。新增任何网络能力需重走安全边界评审 |
| 数据最小化 | A10 已确立：不记录可识别第三方身份、不按类型排除备份、不在日志/异常里拼记录内容 |
| 不升依赖 / 不加中间件 | 已发布的加密库与备份格式对兼容性极敏感，能不升 Room、不改备份格式就不动 |
| 免责定位 | `README.md` 页脚已有免责文案；不做诊断、不给医疗结论、不宣称预测准确率 |

**非目标（本模块一律不做）**：备孕 / 怀孕 / 育儿三模式、社区与内容资讯、电商返现、云账号与多设备同步、亲友共享、AI 健康建议与调理方案、拍照识别排卵试纸、蓝牙体温计与 Health Kit 接入、应用外推送通知（见 P7）。

---

## 2. 参考 App 功能盘点与取舍

盘点来源：美柚官网功能列表、`view.seeyouyima.com/help/help_jilu.html` 官方帮助页「如何记录经期 / 心情 / 爱爱 / 体重体温 / 分析」、以及第三方软件站的功能说明（后者含营销成分，只用于补全条目，不作为字段或算法依据）。

| # | 参考 App 功能 | 取舍 | 期次 | 理由 / 落点 |
| --- | --- | --- | --- | --- |
| 1 | 日历选日 →「大姨妈来了 / 走了」 | **抄** | E1 | 核心交互；本项目改为「按天记录 + 起止动作自动补齐中间日」，见 §5.2 |
| 2 | 日历按天标注经期 / 预测经期 / 排卵期 / 易孕期 / 安全期 | **部分抄** | E1 只做前两项 | 排卵期/易孕期/安全期属避孕相关结论，医学可靠性有争议且本项目无医师审核，不做；v2 如要做仅作「记录参考」并强免责 |
| 3 | 月经流量（水滴按钮，轻 / 中 / 重） | **抄** | E1 | `flow` 四档（含点滴） |
| 4 | 痛经程度 | **抄** | E1 | `pain` 四档 |
| 5 | 身体症状多选（腹痛、腰酸、头痛、情绪波动等） | **抄（收敛）** | E1 | 固定 12 项白名单，不开放自由输入；自定义标签推 v2（需新的持久化结构） |
| 6 | 自定义症状标签 | 暂不抄 | E2 | 需可配置存储，会外溢到备份格式 |
| 7 | 心情日记（表情 + 事项 + 图片） | **抄纯文本日记，不抄图片**（v4 调整） | E1 | `mood` 枚举 + 新增「日记」行（自由文本，复用 `TrackEventEntity.note`）；不引入图片、富文本与媒体存储，见 P4 / §5.3 |
| 8 | 分泌物 / 宫颈黏液 | **抄** | E1 | `discharge` 五档，纯枚举，成本极低 |
| 9 | 体重 / 体温记录与趋势图 | 暂不抄 | E2 | 数值范围 + 趋势 Canvas 是独立工作量；v1 不改变现有统计页结构 |
| 10 | 爱爱记录与保护措施 | **不重复抄** | — | 已由 A10 `intimacy` 覆盖（`feature/intimacy/`） |
| 11 | 周期智能预测（越记越准） | **抄机制，不抄话术** | E1 | 用可解释的中位数算法，不宣称「98% 准确率」，见 §5.1 |
| 12 | 经期 / 排卵日提醒推送 | **不抄（v1）** | E3 | 需 `POST_NOTIFICATIONS` + WorkManager/AlarmManager，当前全仓零通知代码，且通知内容会泄露高敏感数据，须重走 C9 |
| 13 | 健康分析报告（周期波动、症状统计、身体变化追踪） | **抄（简化）** | E1 简报 / E2 报告 | E1 只做子应用首屏的「本周期简报」（周期长度中位数 / 最长 / 最短、已记录天数、常见症状 Top 3）；完整报告页放 E2 |
| 14 | 四模式切换（经期 / 备孕 / 怀孕 / 育儿） | **不抄** | — | 与「通用个人记录工具」定位不符，且孕期/育儿是另两套数据模型 |
| 15 | 云同步 / 隐私密码 / 亲友共享 | **不抄（云）/ 已有（锁）** | — | 云同步违反 Local First；应用锁与截屏保护已有（C7 / A10） |
| 16 | 她她圈社区、购物返现、知识资讯流 | **不抄** | — | 需账号与网络，且与本项目隐私定位冲突 |
| 17 | 食物能不能吃、B 超解读、产检提醒 | **不抄** | — | 医疗内容，超出记录工具范围 |

**一句话结论**：能抄且值得抄的是「记录 → 归并 → 预测 → 日历 → 概览」这条数据闭环（#1–#5、#7–#8、#11、#13），以及 v4 新增的「日历在上、逐行填写、每行有图示、末行日记」这套交互形态（P12）；其余要么是另一个产品（社区 / 电商 / 孕产），要么会破坏本项目的隐私与离线边界。

---

## 3. 现状核对（代码依据，非推测）

| 结论 | 依据 |
| --- | --- |
| **核心矛盾：现有模型是「时刻点事件」，月经是「跨天区间」** | `TrackEventEntity` 存 `occurredAtEpochMillis` + `localDate` + `zoneId`，一天可多条；`RecordDetail.Generic` 只有 `Map<String,String>`（`RecordDetail.kt:23-26`）。全仓**未找到**周期区间表、周期长度、相邻记录间隔类函数（仅 `StatsAggregator.kt:88-102` 有「窗口内连续有记录天数」的 `longestStreak`） |
| 新增类型可不动 Room 与备份格式 | `DefaultTypes.kt:70-76` 注册 → `RoomTrackRepository.kt:79-81` `insertIgnoreAll`；`EventDetailEntity` / `RecordDetailCodec` / `BackupCodec.parseDetail():366-401` 都不认具体 typeCode；`DetailSpec.kt:93` `SUPPORTED_DETAIL_TYPES` 由 `builtIn.keys` 派生 |
| 但表现层有 8 处硬编码分派必须同步 | `LifeTrackApp.kt:158-201`（**`else -> PoopEditorDialog`，漏改会把月经记录当排便草稿落库**）、`TodayScreen.kt:194-226`（两处 `else -> onWaterRecord`）、`RecordRow.kt:45-92`、`RecordDetailPresentation.kt:90-99`、`RecordSummary.kt:38-99`、`LifeTrackTokens.kt:76-111`、`ModuleManagementScreen.kt:237-242`、`DetailSpec.kt:36-86` |
| 字段规格只支持字符串值 | `DetailSpec.kt:18-32` validator 为 `(String) -> Boolean`；`DetailLimits`（`:105-118`）`MAX_FIELDS=16`、`MAX_VALUE_LENGTH=256`、key 需 `[a-z][a-zA-Z0-9_]*`。多选值必须自行编码，见 P8 |
| 备份单向兼容 | `BackupCodec.kt:388-390` 未注册类型 → `UNSUPPORTED_DETAIL_TYPE` 明确拒绝；`RoomTrackRepository.kt:208-211` 恢复旧备份时按 `DEFAULT_TYPES` 补齐缺失类型行 |
| 高敏感机制目前**硬绑 intimacy** | `ScreenCapturePolicy.kt:17-28` `Reason` 只有 `APP_LOCK` / `INTIMACY_MODULE`，判定写死 `IntimacyFields.TYPE_CODE`；`RecordDetailSheet.kt:49-55`、`RecordSummary.kt:83-84`、`IntimacyRecordItem.kt:56-60` 为 L1 收敛样板；`SettingsScreen.kt:650-667` 与 `SettingsComponents.kt:217-222` 文案硬编码「亲密关系」 |
| 日历**可借鉴但不能直接复用**（v2 修正） | `CalendarMonth.kt:51-66` 固定 42 格 / `withCounts` / `withTypes`、`CalendarMonthView.kt:153-223` 的类型图标 / 今日高亮 / 读屏汇总 / 点击回调、`DayRecordsSheet.kt:49-106` 的当天记录弹层，布局与交互都能照抄；但 `of()` 禁止翻未来，须另写 `PeriodMonth`。DAO `observeRecordsInRange(start,end,typeCode?)`（`TrackEventDao.kt:71-82`）已支持按类型取区间 |
| 通知能力为零 | `SettingsRepository.kt:19-24` 仅 6 个键，无提醒键；`app/build.gradle.kts:118-158` 无 `androidx.work`；Manifest 无 `POST_NOTIFICATIONS`、无 `<receiver>` / `<service>`；全仓无 Notification / AlarmManager / WorkManager 代码 |
| 注册新类型必然踩的测试 | `DetailSpecTest.kt:59-70` 硬编码 `expected = {water, coffee, sport, intimacy}` 断言放行集合，注册后必失败；负向样例统一用 `mood`（`project-profile.md §9`），不得改成真实类型名；`ScreenCapturePolicyTest.kt:45-67` 锁定「只有 intimacy 是高敏感」与「reasons 恰为两条」 |
| 统计口径是「按条计数」 | `StatsAggregator.kt:59-127` 以事件条数算总次数 / 日均 / 单日最高；首页次数卡、历史、月历、统计共用全部类型口径（A11）。按天记经期会直接抬高「总次数」，见 P2 |
| 数据库为加密库 | `AppDatabase.kt:33-39` SQLCipher `SupportOpenHelperFactory`；`DatabaseEncryption.kt:48` `TABLES` 硬编码四张表用于迁移行数比对。**新增独立表会牵动这条链路** |
| **历史月历只能回看，翻不到未来（v2 新增，决定布局）** | `CalendarMonth.kt:51-52` `of()` 有 `require(offset <= 0) { "calendar cannot be shifted into the future" }`；`MainViewModel.kt:228` `shiftCalendarMonth()` 也 `coerceAtMost(0)`；`HistoryScreen.kt:115` `canGoNext` 只到本月。而经期预测的核心就是看**未来**预计经期，两者方向相反 |
| 历史页的两态是刚确认过的信息架构 | `HistoryScreen.kt:249` `ViewSwitcher` 硬编码 `listOf(false to "列表", true to "月历")`；`docs/ui-redesign.md:212` 明确「同一份数据的两种密度，不是两个页面」。再加第三档「经期」等于把 Room 真实记录与推算结果混进同一页 |
| 四个底部页签刚定稿 | `LifeTrackApp.kt:55-60` `destinations` 为今天 / 历史 / 统计 / 设置；`docs/ui-redesign.md:231` 记录了「记录 → 历史」改名理由。`LifeTrackApp.kt:90,149-153` 已有「记录项目」这种**隐藏底导的二级页**惯例，可直接复用 |

---

## 4. 必须先拍板的决策

> 每个决策给推荐值与备选；推荐值即 §5 展开的方案。未拍板前不写任何代码。

| 编号 | 问题 | 推荐值 | 备选 | 关键影响 |
| --- | --- | --- | --- | --- |
| **P1** | **数据模型**：经期是跨天区间，怎么落库 | **A：按天一条 `menstruation` 事件，周期段与预测全部由纯函数派生，不新增表、不升 Room、不改备份格式** | B：新增 `menstrual_cycle` 表存起止（Room v4 + 备份 v4 + 加密迁移比对）；C：独立子系统另起表与页面 | B 会同时改动 `AppDatabase` / `Migrations` / `schemas/4.json` / `DatabaseEncryption.TABLES` / 备份 DTO / 迁移测试，且旧版无法读新备份；C 破坏「首页 / 历史 / 月历 / 统计同口径」 |
| **P2** | 统计口径 | **一条 = 一天，如实计入「全部」次数，不为此类型开特例；周期页另给「经期天数 / 周期长度」等专属指标**，并在 README 写清口径 | 从次数统计中排除月经；或给 `track_type.trackingMode` 引入 `DAY` 让统计按天去重 | 排除会破坏 A11 跨面板自洽；`DAY` 是新概念，外溢到 `modeLabel` 与所有聚合查询 |
| **P3** | 隐私等级与默认启用 | **沿用 A10 的 L1：默认停用、列表只显示类型与时间、读屏一句汇总、启用期间无条件 `FLAG_SECURE`、明文导出风险文案点名**；把 `ScreenCapturePolicy` 从硬编码改成数据驱动的高敏感类型集合 | L0 不作遮蔽；L2 全遮蔽 + 强制应用锁 | 月经数据与 A10 同级敏感；L2 会改变已发布用户的全局锁行为 |
| **P4** | v1 字段集（v4 调整） | **5 个枚举/列表字段 + 1 行日记**：`flow` / `pain` / `symptoms` / `mood` / `discharge` 全部可空；日记为自由文本，**复用 `TrackEventEntity.note`，不新增 detail key** | 极简（仅 `flow` + `pain`）；或新增 `diary` detail 字段；或一步到位加体重 / 体温 / 基础体温曲线 | 日记走 note 可保持「零存储改动 / 备份格式不动」结论；新增 detail key 会外溢到 `DetailSpec`、备份规格与校验；体重体温推 E2 |
| **P5** | 预测算法与免责 | **纯 JVM `CycleCalculator` + 中位数，样本不足时明确显示「记录不足，暂不预测」；只预测经期，不做排卵日 / 易孕期 / 安全期结论；不宣称准确率，标注「仅供记录参考，不用于避孕或医疗判断」** | 抄参考 App 的「自适应算法 / 3 个周期后 98%」；或加排卵期估算 | 无法验证的准确率话术属虚假陈述；避孕相关结论有实质风险 |
| **P6** | 界面落点（v3 升级 / v5 收敛） | **首页只保留「月经」模块卡一个入口指向 `composable("cycle")`（v5 起不再加周期状态行、也不加截屏保护提示行）；该二级页为完整经期子应用（精简状态条 / 置顶可翻月日历 / 选中日逐行填写与日记 / 简报 / 趋势 / 设置，详见 P12）；其它四张模块卡、今日记录、底部四页导航、历史页与 `CalendarMonthView` 全部不动** | 历史页加第三档「经期」（v1 已否决）；新增第 5 个底部 Tab；全局重设计成双主线 | 见 §3「历史月历只能回看」；二级页沿用 `LifeTrackApp.kt:90,149-153` 既有惯例，把内容做深比改全局 IA 代价小得多 |
| **P7** | 经期提醒 | **v1 不做推送**，只在子应用精简状态条显示「距预计下次经期 X 天」（v5 起首页不再显示周期状态）；提醒放 E3 且需重走 C9 隐私评审 | v1 就做通知（+ `POST_NOTIFICATIONS` + WorkManager） | 通知标题/内容在锁屏可见，是高敏感数据最直接的泄露面 |
| **P8** | 症状多选编码 | **`symptoms` 单字段存 `\|` 分隔的 code 串**，validator 校验每个 token 在白名单、无重复、≤ 8 项、总长 ≤ 256 | 为每个症状各开一个布尔字段；或改 `DetailSpec` 支持 List | 现有 `fields: Map<String,String>` 不支持重复 key，也不支持 List；改规格表会外溢到备份校验 |
| **P9** | 用户设置 | **DataStore 增 2 键**：`cycle_length_days`（默认 28，15–60）、`period_length_days`（默认 5，1–14），只作为样本不足时的回退值 / 用户覆盖；**预测结果不落库** | 完全不设，写死 28 / 5；或把预测结果存表 | 落库派生数据必然出现「记录变了、预测没变」的一致性问题 |
| **P10** | 入口语义（v3 新增，v5 收敛） | **点「记录月经」模块卡 = 进入子应用，且这是首页唯一入口**；日历默认选中今天、下方紧接逐行填写区，进来即可填；日历点任意日期可补记 | 点卡仍打开编辑器、子应用改由设置页进入；或卡上加小按钮 / 长按快速记 | 入口语义改变后记一笔会多走一层，由「默认选中今天 + 紧邻表单」补回；v5 删除 v2 加的周期状态行后，首页不再有第二个入口，也不叠加第二张卡 |
| **P11** | 子应用范围（v3 新增，v4 调整） | **经期核心闭环**：精简状态条、置顶可翻月日历、逐行填写（含图示与日记）、按天记录、周期趋势与简化报告、周期设置；抄信息架构与交互，**不复制图标 / 文案 / 视觉素材 / 品牌元素** | 加备孕 / 怀孕 / 育儿四模式；或连社区 / 资讯 / 电商也做 | 四模式是另一套数据模型 + 医疗内容；社区电商会突破 Local First 与网络唯一例外，须重开安全边界评审。数据仍走 `track_event` + `event_detail`，不另起库、不升 Room、不改备份 |
| **P12** | 子应用内交互形态（v4 新增，用户 14:03 指示） | **日历置顶选日期，下方逐行填写当天内容，每行带图示，末行日记**；点行展开选项、选中即存，同一天只记一条；子应用内**不再用底部弹层编辑器** | 保留 v3 的「状态大卡 + 主按钮 + 弹层表单」；或做成整页多步骤向导 | 参考 App 用户最熟悉的是「日历 + 当日逐行记录」；弹层会把日历盖住、选日与填写割裂。改动只在子应用内部，不影响首页 / 历史 / 统计骨架；但通用编辑器仍须保留给其它入口，见 §5.4 |

---

## 5. 推荐方案（按 P1–P12 推荐值展开）

### 5.1 领域模型：周期全部派生，不落库

新增纯 JVM 类 `core/model/MenstrualCycle.kt`（无 Android 依赖，可被 `testDebugUnitTest` 直接覆盖）：

```text
CycleCalculator.calculate(
    recordedDates: Set<LocalDate>,      // 所有 menstruation 事件的 localDate，去重
    today: LocalDate,
    configuredCycleDays: Int,          // P9 回退值
    configuredPeriodDays: Int,
    sampleSize: Int = 6
): CycleSnapshot

CycleSegment(start: LocalDate, end: LocalDate, lengthDays: Int)
CycleSnapshot(
    segments: List<CycleSegment>,       // 连续日期归并后的经期段
    cycleLengths: List<Int>,            // 相邻段首日之差
    medianCycleDays: Int?,
    medianPeriodDays: Int?,
    currentPhase: Phase,                // IN_PERIOD(dayIndex) / BEFORE_NEXT(days) / DELAYED(days) / INSUFFICIENT
    predictedPeriods: List<DateRange>,  // 未来最多 3 个预测经期窗口
    sampleReliable: Boolean
)
```

规则（全部用 `LocalDate`，不涉及时刻与时区，天然避开 `RecordTimeMapper` 的跨时区复杂度）：

1. **归并**：日期升序去重，相邻差 1 天合并为一段；孤立单日也算一段；忽略未来日期。
2. **周期长度**：相邻两段 `start` 之差；落在 `[15, 60]` 之外视为异常样本剔除，不参与中位数，但段本身保留展示。
3. **中位数**：取最近 6 个有效样本，偶数个取中间两数均值向下取整；**有效样本 < 3 段 → `sampleReliable = false`**，用 P9 配置值并在 UI 明确标「按设定值估算」。
4. **经期长度**：最近 6 段 `lengthDays` 的中位数，同样不足则取配置值。
5. **预测**：`lastStart + medianCycleDays` 为下次预计开始，按 `medianPeriodDays` 展开窗口；最多给未来 3 个周期，不做无限外推。
6. **当前阶段**：今天落在某段内 → 「经期第 N 天」；否则算与下次预计开始的天数差，为负显示「已推迟 N 天」；样本不可靠或窗口外 → 「记录暂不足，继续记录后可预测」。
7. **不产出**：受孕概率、排卵日、安全期、健康风险提示、任何调理建议。

单测清单：空集 / 单日 / 连续 5 天 / 中间漏记 1 天断段 / 跨月跨年 / 异常周期剔除 / 偶数样本中位数 / 样本不足回退 / 今天在经期中 / 已推迟 / 未来日期忽略 / 夏令时无关的 `LocalDate` 用例。

### 5.2 记录写入：一条事件 = 一个经期日

- 新增 `DEFAULT_MENSTRUATION_TYPE`：`code = "menstruation"`，名称「月经」，`category = "body"`，`trackingMode = "EVENT"`，`enabled = false`（P3），`sortOrder = 60`，`schemaVersion = 1`。用已有 `body` 类别可避免 `ModuleManagementScreen.categoryLabel():237-242` 显示原始英文串。
- 「大姨妈来了 / 走了」是**写当天一条事件**的快捷动作，不是独立实体：
  - 「来了」：当天已有同类型事件则转为编辑，否则写入；`detail` 可为 `null`（只标记，未填体征）。
  - 「走了」：若存在最近一次「来了」且间隔 ≤ 14 天，**自动为中间缺失日期各补一条「仅标记」事件**（`detail = null`），保证经期段连续、经期天数准确；UI 明确提示「已补齐 N 天，可逐日补充流量与症状」。补齐按 `(typeCode, localDate)` 幂等，已有事件一律跳过、不覆盖已有详情；超过 14 天不自动补，要求用户确认，防止误触批量写入。
  - 用户也可在任意一天直接打开月经编辑器逐日记录，不强制走起止动作。
- 编辑 / 删除沿用现有链路（`saveGenericRecord` / `RecordRow` 的删除）；删除区间中间某日会打断经期段，弹窗里提示一次，不阻止操作。
- 不做一键打卡：`MainViewModel` 的 `quickRecord()` 硬编码 `addPoopRecord`（A10 已列为非目标），本模块同样不碰。

### 5.3 字段规格（5 项 `DetailSpec` + 日记行，零存储改动）

`core/model/MenstruationFields.kt` 定义常量与 validator，编辑器与备份规格共用一处（沿用 A6 经验）。字段值统一字符串，全部可空：

| key | 取值 | 校验 | 展示 |
| --- | --- | --- | --- |
| `flow` | `spotting` / `light` / `medium` / `heavy` | 枚举白名单 | 点滴 / 少 / 中 / 多 |
| `pain` | `none` / `mild` / `moderate` / `severe` | 枚举白名单 | 无 / 轻 / 中 / 重 |
| `symptoms` | `\|` 分隔 code | token ∈ 12 项白名单、无重复、≤ 8 项、串长 ≤ 256 | 腹痛、腰酸、头痛、乳房胀痛、腹胀、恶心、乏力、失眠、情绪波动、食欲变化、痤疮、头晕 |
| `mood` | `calm` / `happy` / `sensitive` / `low` / `irritable` | 枚举白名单 | 平静 / 愉快 / 敏感 / 低落 / 易怒 |
| `discharge` | `dry` / `sticky` / `creamy` / `watery` / `eggwhite` | 枚举白名单 | 干燥 / 黏稠 / 乳白 / 水样 / 蛋清 |
| `note`（**日记行，不在 detail 内**） | 纯文本自由输入 | 复用 `TrackEventEntity.note`，长度上限沿用现有备注约束（实现时以 `RecordEditorDialog` 为准，不新增常量）；不新增 `DetailSpec` 字段 | 行内截断预览；列表 / 读屏收敛区内不拼原文；明文导出风险文案点名 |

**v4 变化说明**：日记是 UI 上的第 6 行，**数据上就是现有备注字段**，不新增 detail key，因此 `DetailSpec.builtIn`、`SUPPORTED_DETAIL_TYPES`、备份 DTO 与校验规则全部无 diff，§5.6 的「零存储改动」结论不变。原型按 500 字演示输入上限，实现时须以代码中的实际备注约束为准（W22）。

除 `note` 承载的纯文本日记外，不新增自由文本字段；伴侣 / 第三方身份、图片、富文本、位置一律不记。日记输入框需提示「请勿记录他人身份信息」，并按 L1 收敛处理：列表与详情读屏只给一句汇总、不拼原文，明文导出风险文案点名（W21）。

### 5.4 UI 落点（v5：首页单卡入口 + 子应用日历置顶逐行填写）

**结论：不动全局布局，只重排这个二级页的内部交互。** 底部仍是四个页签（`LifeTrackApp.kt:55-60`），`docs/ui-redesign.md` 的四页信息架构不改；`composable("cycle")` 与已有「记录项目」页同级（`LifeTrackApp.kt:90,149-153`），隐藏底导。历史页及其两态、`CalendarMonthView`、`DayRecordsSheet`、`CalendarMonth.of()` **全部无 diff**。配套可点击原型见 `docs/menstruation-module-prototype.html`。

用户口径累计四轮：

| 决策 | 结论 |
| --- | --- |
| 范围（P11，13:39 拍板） | **经期核心闭环**：可翻月日历、按天记录、周期趋势与简化报告、周期设置；不做账号 / 云同步 / 社区 / 电商 / 资讯 / 孕产模式 / 推送 |
| 入口（P10，13:39 拍板 / v5 收敛） | **首页「月经」模块卡点击 = 进入子应用，且是首页唯一入口**（不再是打开编辑器；v5 删除状态行与截屏提示行） |
| 首页呈现（14:47 拍板） | **只保留「月经」模块卡**；删除「经期第 N 天 / 打开经期 → / 按最近 6 个周期估算」状态行与「已开启截屏保护」提示行；其它四张模块卡、今日记录、底部导航全部保留 |
| 交互形态（P12，14:03 指示） | **日历置顶选日期 → 下方一行一行填内容 → 每行有图示 → 末行加日记**；点行展开、即点即存，子应用内不用弹层 |
| 数据 | 仍走 `track_event` + `event_detail`，日记复用 `note`；不另起库、不升 Room v3、不改备份 v2/v3。子应用只是「同一份数据的另一张脸」 |

**① 今天页 · 模块卡改为导航入口**。`TodayScreen.kt:81-187` 的 `LazyColumn` + `item` 结构不变，模块网格 `enabledTypes.chunked(columns)` 仍数据驱动。

- 第 6 张「月经」模块卡的 `moduleAction` / `moduleDetail` 分支改为 `onOpenPeriod()` → 导航到 `composable("cycle")`，**不再 `openEditor()`**；`TodayScreen.kt:52-65` 需新增一个导航回调，由 `LifeTrackApp.kt:118-133` 传入。卡片外观仍是「今日 N 次 + 右下角『打开』」，但行为从「记一条」变成「打开经期」。
- **v5 起首页不再加任何周期状态行**：删除 v2 引入的「经期第 N 天 / 打开经期 → / 按最近 6 个周期估算」整张卡，同时删除其下方「已开启截屏保护：启用高敏感模块后全应用禁止截屏」这行提示。周期状态只在子应用精简状态条呈现（见 ②）；`FLAG_SECURE` 机制本身**不变**，说明收敛到「设置 → 记录项目」的首次启用提示（§5.5）。首页因此不再有第二个经期入口，也不叠加第二张卡。
- 首页**不放**日历、趋势、报告，也不放第二张与月经有关的信息卡：这些全部归子应用；`ui-redesign.md:204` 刚删掉重复的计数大卡，首页不再堆信息卡。
- 模块停用时首页与 0.6.8 完全一致（`ui-redesign.md:207`）；启用后只多一张「月经」卡，网格 `enabledTypes.chunked(columns)` 自动排布，不改任何布局代码。
- 入口语义改变后「快速记一笔」多走一层，由子应用**默认选中今天 + 日历下方紧接逐行填写区**补回：进来无需再点任何按钮就能开始填（见 ②）。v3 的「今天记一笔」主按钮在 v4 取消。

**② 经期子应用首屏（`PeriodScreen`，本模块主视图）**：顶部返回箭头 + 标题「经期」+ 设置入口，隐藏底部导航。自上而下四块，**第一屏要能同时看到日历和逐行填写区的开头**：

1. **精简状态条**：一行阶段主句（「经期第 3 天」）+ 一行本期区间 / 第几天 / 下次预计；样本不足时降级为「记录暂不足」并隐藏预测数字。v4 取消 v3 的状态大卡与主按钮，把垂直空间让给日历和表单（原型实测：v3 的大卡 + 1:1 正方形日历格会把填写区整体挤出首屏）。
2. **经期日历置顶（本页主控件）**：可向后翻到未来月份，另写 `PeriodMonth` 纯模型与 `PeriodCalendarView`，**不修改 `CalendarMonth.of()` 的 `require(offset <= 0)` 语义**（改了会影响既有月历与 `CalendarMonthTest` 6 项）。格子状态：实际经期（浅底 + 实线）、预测经期（虚线）、今天（主色实心）、已记录（小圆点）、**当前选中日（描边）**。格子用固定 `heightIn` 而非 1:1 正方形，保证 200% 字体下仍留出填写区。**点日期 = 切换下方选中日，不再打开弹层**；选中未来日期时下方整块只读。
3. **选中日标题 + 起止动作**：「11月12日 · 周四 · 今天」+「大姨妈来了 / 走了」两个带图示的次级动作（§5.2 自动补齐中间日）；未来日期不渲染动作。
4. **逐行填写区 `PeriodDayForm`（v4 交互核心）**：一行一字段，顺序为流量 / 痛经 / 症状 / 心情 / 分泌物 / **日记**：
   - 每行：行首功能图示、字段名、当前值（未填显示引导语）、展开箭头；**已填值本身带图示**（等级格 / 简笔表情 / 圆点标签 / 纸笔）。
   - 点行展开该行选项，选项也带同一套图示，选中即保存、再点可取消；同一时刻只展开一行；症状 12 项自动分行、最多 8 项、不横向滚动（A7 教训）。
   - 日记行展开为多行纯文本框 + 字数提示，输入即写 `note`；UI 提示「请勿记录他人身份信息」。
   - 未来日期整块替换为只读提示，不给可填控件，不编造记录。
   - **切换选中日期或点选项后必须保持页面滚动位置**：列表重建（Compose `LazyColumn` 用 `key` + 状态提升；原型 `innerHTML` 重建）若把滚动重置回顶部，用户每点一个选项都要重滚到表单，交互直接失效（原型实测踩过，已修）。
   - 底部「完成」收起展开行并给一次保存反馈；**子应用内不做「今日记录列表」**（A11，首页已有时间轴，事件级回看走历史页）。
5. **本周期简报 / 周期趋势 / 设置折叠行 / 常驻免责**：沿用 v3 口径，位置下移到逐行表单之后。简报只呈现用户自己记录的数据（中位数 / 最长 / 最短、已记录天数、常见症状 Top 3），不做健康判断、不写调理建议；趋势用等宽数字 + 柱条；周期 / 经期长度（P9 两键）收在本页折叠行，**不进设置页新建卡片**（`docs/settings-redesign-v2.md:27`、`profile §9`），默认 28 / 5，改动后标注「按设定值估算」；免责「仅供记录参考，不用于避孕或医疗判断」。

**③ 图示规范**：全部为项目自绘矢量（Compose `Icon` / `Painter`），不复制参考 App 的图标、文案、视觉素材与品牌元素，不用装饰性 emoji、不用渐变。语义固定：流量 / 痛经 / 分泌物 = n 格等级格，心情 = 简笔表情，症状 = 圆点标签，日记 = 纸笔，选中 = 对勾；**可选项与已填值用同一套图示**，避免「选的和显示的不一样」。图示的 `contentDescription` 由文字承担、不重复播报；深色与 200% 字体各验一次。

**④ 两套编辑入口，数据同源**：
- 子应用内：`PeriodDayForm` 页面内逐行填写，由日历选中日驱动，无底部弹层。
- 其它入口：首页时间轴 / 历史页 `DayRecordsSheet` / 记录列表点编辑，仍走 `MenstruationEditorDialog`（复用 `RecordEditorDialog` 的日期 / 时间 / 时区 / 备注壳 + `ChoiceChipRow`），带 `initialDate: LocalDate`。
- 两者共用 `MenstruationFields` 的枚举与 validator、共用 `saveGenericRecord` 写入，**不写两份校验、不写两份字段白名单**。日记在弹层里就是「备注」输入框，UI 文案统一叫「日记」；`RecordDetailPresentation` / `RecordSummary` 的 `menstruation` 分支对两条写入路径一视同仁。

**列表与详情**：`MenstruationRecordItem.kt` 按 L1 只显示「月经 · 日期」，编辑/删除按钮保留；`RecordDetailPresentation` 与 `RecordSummary` 加 `menstruation` 分支；`RecordDetailSheet` 的读屏收敛从硬编码 `intimacy` 改为按高敏感集合判定。

**视觉约束**：沿用 `ui-redesign.md` §2 的 token 与三档视觉重量；**抄信息架构与交互，不复制参考 App 的图标、文案、视觉素材与品牌元素**；月经识别色只用于色点、3dp 竖条、日历状态描边与已填值图示，不做大面积填色；不用渐变、纯白底、装饰图形；200% 字体下逐行表单的选项、已填值标签、起止动作与简报指标自动分行，日历格与行容器用 `heightIn` 而非固定 `height`；行首图示固定尺寸，不随字号拉伸。

### 5.5 隐私改造（把 A10 的一次性硬编码收口）

- `ScreenCapturePolicy.kt`：把 `Reason.INTIMACY_MODULE` 扩为数据驱动 `HIGH_SENSITIVITY_TYPES = setOf(intimacy, menstruation)`，`reasons()` 按启用中的高敏感类型生成原因文案；`MainActivity.kt:46-49` 只调 `isBlocked`，无需改动。
- `ModuleManagementScreen.kt:101-115` 的首次启用提示、`SettingsScreen.kt:412/657-658` 与 `SettingsComponents.kt:217-222` 的文案，改为遍历高敏感类型生成，避免第三次加模块又改四处。
- `RecordDetailSheet.kt:49`、`RecordSummary.kt:84` 的收敛判定同步改为集合判定。
- **v5 说明收敛**：首页删掉「已开启截屏保护」提示行后，这句说明由 `ModuleManagementScreen.kt:101-115` 的首次启用提示与 `SettingsScreen.kt:412/657-658` 的安全区文案承担，用户仍能知道「启用高敏感模块后全应用禁止截屏」；`ScreenCapturePolicy` / `MainActivity` 的判定与 `FLAG_SECURE` 机制一行不改。
- 明文导出全量包含月经记录，不按类型排除（项目红线：静默丢数据），只在风险确认文案中点名。
- 不写日志、不进异常文本、通知为零（P7）。

### 5.6 存储与兼容性

| 项 | 结论 |
| --- | --- |
| Room | **保持 `version = 3`**，`Migrations.kt` / `app/schemas/1,2,3.json` / `DatabaseEncryption.TABLES` 全部无 diff |
| 备份 | `SCHEMA_VERSION = 2` / `ENCRYPTED_SCHEMA_VERSION = 3` / `SUPPORTED_VERSIONS = {1,2}` / `BASE_TYPE = "poop"` / 全部 DTO **无 diff**；只在 `DetailSpecRegistry.builtIn` 注册 `menstruation` |
| 旧备份 → 新版 | `restoreBackup():208-211` 补进一条默认停用的月经类型行，不报错 |
| 新版备份 → 0.6.8 及更早 | 含 `menstruation` 详情的备份被旧版以 `UNSUPPORTED_DETAIL_TYPE` 明确拒绝，不静默丢数据；README 补单向兼容说明（与 A10 同口径） |
| 覆盖安装 | `insertIgnoreAll(DEFAULT_TYPES)` 不覆盖用户已设的 `enabled` / `sortOrder`；老用户升级后该模块保持默认停用 |
| 排便专用链路 | `BASE_TYPE` / `observeStatDetails`（写死 `'poop'`）/ 形态分布卡 / `quickRecord()` 一律不碰（A10 确认单 §2 列为 A9 耦合边界，本轮同样避开） |

### 5.7 文件清单（预估，实现时按实际收敛）

**新增**

- `app/src/main/java/com/example/lifetrack/core/model/MenstruationFields.kt`
- `app/src/main/java/com/example/lifetrack/core/model/MenstrualCycle.kt`（`CycleCalculator` / `CycleSnapshot`，纯 JVM）
- `app/src/main/java/com/example/lifetrack/feature/menstruation/MenstruationEditorDialog.kt`（含 `initialDate`）/ `MenstruationRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/menstruation/PeriodScreen.kt` / `PeriodCalendarView.kt`（**经期子应用首屏**：精简状态条 / 置顶日历 / 选中日动作 / 简报 / 趋势 / 设置）
- `app/src/main/java/com/example/lifetrack/feature/menstruation/PeriodDayForm.kt`（**v4 逐行填写区**：6 行、点行展开、即点即存、未来日期只读、滚动位置保持）
- `app/src/main/java/com/example/lifetrack/feature/menstruation/PeriodDayIcons.kt`（自绘矢量图示：等级格 / 表情 / 圆点标签 / 纸笔 / 对勾，选项与已填值共用）
- `app/src/main/java/com/example/lifetrack/core/model/PeriodMonth.kt`（**独立的可翻未来月纯模型，不复用也不改 `CalendarMonth`**）
- `app/src/test/java/.../core/model/MenstrualCycleTest.kt`、`PeriodMonthTest.kt`（未来月 / 跨年 / 补白）、`MenstruationFieldsTest.kt`、`PeriodDayFormTest.kt`（选中日切换 / 展开互斥 / 症状 8 项上限 / 日记写入 `note` / 未来日期只读）、`core/backup/BackupMenstruationDetailTest.kt`
- 本文档 `docs/menstruation-module-design.md` 与配套原型 `docs/menstruation-module-prototype.html`

**修改**

- 注册与规格：`core/database/DefaultTypes.kt`、`core/backup/DetailSpec.kt`
- 硬编码分派 + 新路由：`feature/home/TodayScreen.kt`（**仅**模块卡改 `onOpenPeriod()` 导航 + 新回调；**不再 `openEditor()`，v5 起也不新增状态行 / 提示行，首页其余结构无 diff**）、`core/navigation/LifeTrackApp.kt`（加 `composable("cycle")` 子应用路由、导航回调与编辑器分支）、`feature/record/RecordRow.kt`、`core/model/RecordDetailPresentation.kt`、`feature/record/RecordSummary.kt`、`core/ui/LifeTrackTokens.kt`
- 隐私：`core/security/ScreenCapturePolicy.kt`、`feature/record/RecordDetailSheet.kt`、`feature/settings/ModuleManagementScreen.kt`、`feature/settings/SettingsScreen.kt`、`feature/settings/SettingsComponents.kt`
- 设置：`core/datastore/SettingsRepository.kt`（P9 两键）
- 测试：`core/backup/DetailSpecTest.kt`（放行集合）、`core/security/ScreenCapturePolicyTest.kt`（原因集合）、`core/model/RecordDetailPresentationTest.kt`
- 文档：`README.md`（已实现范围、统计口径、单向兼容、相关文档）、`docs/PROGRESS.md`（A9 状态）、`app/.../feature/settings/VersionHistory.kt`（实现发版时补条目，非本轮）

**明确不改**：`feature/history/` 下全部文件、`core/model/CalendarMonth.kt`、`CalendarMonthTest.kt`、底部导航 `destinations`、`docs/ui-redesign.md` 的四页 IA。

### 5.8 测试与验收

**单测**（纯 JVM，新增 ≥ 30 项）：`CycleCalculator` 12 项边界、`MenstruationFields`（含 `symptoms` 编码：空 / 单项 / 多项 / 重复 / 超 8 项 / 超长 / 非法 token）7 项、备份往返与坏文件拒绝 6 项、`PeriodDayForm` 状态机（选中日切换 / 展开互斥 / 未来日期只读 / 日记写 `note` / 两套编辑入口同源）5 项、详情与摘要映射、`ScreenCapturePolicy` 双模块原因。既有 `DetailSpecTest:59-70` 与 `ScreenCapturePolicyTest:45-67` 按新增集合同步扩断言，**改的是样例，不放宽校验**；负向样例保持 `mood`。

**验收命令**（`lang-android.md` 四层）：

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 全绿；基线单测数不回退、lint 无新增 error。
2. 版本核对：Room 仍 v3、`app/schemas/` 只有 1/2/3.json 且无 diff、备份三个版本常量与 DTO 无 diff、DAO / Repository 无 diff。
3. 装机主路径：默认停用 → 启用 → **点首页「月经」模块卡进入子应用（不是编辑器）** → 日历默认选中今天、下方逐行填写区可见 → 记「来了」→ 记「走了」自动补齐 → 点行展开填流量 / 症状 / 日记 → 点日历切到过去某日补记 → 状态条、日历染色、简报数字同步正确 → 编辑 / 删除 / 重启保持。
4. 备份往返：v3 加密与 v2 明文各导一次并恢复，字段与补齐事件完整回来；坏字段（`symptoms` 含非法 token、未知字段、超长）明确拒绝且不破坏现有数据。
5. 隐私：`FLAG_SECURE` 生效期间截图全黑（按 `profile §9` 用 `uiautomator dump` 与 `dumpsys window` 作替代证据），TalkBack 列表与详情各只一句汇总，应用锁关闭时仍遮挡，明文导出文案点名；**首页虽无截屏提示行，「设置 → 记录项目」的首次启用提示仍明确说明全应用禁止截屏**。
6. UI：窄屏 360dp / 深色 / 200% 字体 / 宽屏各走一遍；行式表单的选项与已填值标签自动分行、无横向裁切；日历格与行容器用 `heightIn` 不用固定 `height`；200% 字体下起止动作、图示、日记输入框与「完成」按钮分行不重叠；**首屏能同时看到日历与逐行填写区开头**（原型实测基线：压缩日历格后首屏可见 3 行）。
7. 兼容：0.6.8 备份恢复成功且模块保持停用；含月经的备份在旧版被明确拒绝（双版本环境具备则实测，否则单测覆盖）。
8. 布局零回归：底部导航仍 4 项；子应用隐藏底导且返回正常；**历史页两态、`CalendarMonthView`、`DayRecordsSheet`、`CalendarMonth.of()` 与 `CalendarMonthTest` 均无 diff**；子应用能连续向后翻 2 个月并正确显示预测经期；模块停用时今天页与 0.6.8 截图一致，**启用后首页只多一张「月经」卡，无周期状态行、无截屏保护提示行，其余四张模块卡与今日记录位置不变**。
9. 子应用入口与记录闭环：**首页只有「月经」模块卡一个入口**，点击进入 `cycle` route（不再有状态行第二入口，删掉后无残留可点区域）；日历默认选中今天；点日历的过去日期切换下方填写区内容（**不弹层**），点未来日期整块只读、不给可填控件；保存后日历染色、简报数字与首页次数口径一致；**子应用内没有第二份「今日记录列表」**。
10. 逐行填写与日记（v4 新增）：6 行顺序与图示齐全，选项与已填值用同一套图示；点行展开、同时只展开一行、选中即存、再点可取消；症状 12 项自动分行且 ≤ 8 项；日记输入落在 `note`、重启后仍在、读屏与列表不拼原文；**连续点 5 个选项页面滚动位置不跳回顶部**；首页时间轴与历史页的编辑入口打开 `MenstruationEditorDialog`，改完与子应用内数据一致。
11. 收尾：工作区只留本次改动，模拟器字号 / 分辨率 / 主题还原，无遗留后台任务。

### 5.9 风险与对策

| 编号 | 风险 | 缓解 |
| --- | --- | --- |
| W1 | 跨天区间塞进点事件模型，漏记中间日导致经期天数失真 | §5.2 起止动作自动补齐（≤14 天、幂等、不覆盖），`CycleCalculator` 明确按「连续有记录日」分段并在 UI 说明口径 |
| W2 | 按天记事件抬高「总次数」，用户误读统计 | P2 明确口径并写进 README；周期专属指标单独呈现；不从统计里偷偷排除 |
| W3 | `LifeTrackApp.kt:192-199` 的 `else` 落到排便编辑器，月经记录被当排便草稿落库 | 文件清单单独标出；验收覆盖「点编辑打开的是月经编辑器」 |
| W4 | 漏改 `RecordDetailPresentation` / `RecordSummary` / `LifeTrackTokens` | 三处列入硬编码分派清单；验收逐页走详情弹窗与月历图标 |
| W5 | 注册新类型后 `DetailSpecTest` 必然失败；误把 `menstruation` 当负向样例 | 扩放行集合断言；负向样例一律保持 `mood` / `unknown_type`（`profile §9` 已两次记录同类踩坑） |
| W6 | 扩 `ScreenCapturePolicy` 影响 0.6.8 既有用户、或漏掉某处硬编码「亲密关系」 | 改成集合驱动后 `MainActivity` 无需改；按 §5.5 列全 6 处文案并逐处验收；`ScreenCapturePolicyTest` 同步更新 |
| W7 | 预测被当成医疗/避孕依据 | 只做经期窗口，不做排卵/易孕/安全期；不做健康建议；页面常驻免责一句；不写准确率 |
| W8 | 症状多选超出 `DetailLimits.MAX_VALUE_LENGTH = 256` | P8 编码 + 8 项上限 + validator 长度校验 + 单测覆盖 |
| W9 | 引入通知导致高敏感数据出现在锁屏 | P7 v1 明确不做；E3 需重走 C9 并单独评审通知文案 |
| W10 | 为支持周期而升 Room v4 / 备份 v4，牵动加密库迁移与旧版兼容 | P1 选 A，从设计上避免；验收第 2 条强制核对无 diff |
| W11 | 自动补齐写入用户没主动记的事件，被理解为「造假数据」 | 补齐事件 `detail = null` 且 UI 明示「已补齐 N 天，可逐日补充」；上限 14 天、超长需确认、可逐条删除 |
| W12 | 工作区存在其他会话的暂存改动，提交时误卷入 | 只 `git add <本文档路径>`，提交前核对 `git status`；必要时用 `git commit --only <路径>` 隔离 |
| W13 | **把预测数据塞进历史页**（v1 方案，已否决）：现有月历禁止翻未来，两态被定义为同一份数据的两种密度 | v2 修正为独立 `cycle` 二级页；历史页与 `CalendarMonth` 全部无 diff；验收第 8 条强制核对 |
| W14 | 为复用而放宽 `CalendarMonth.of()` 的 `require(offset <= 0)`，导致既有月历能翻到未来、`CalendarMonthTest` 语义变化 | 另写 `PeriodMonth` 纯模型，原函数与其测试保持不动；两套月历各自有单测，跨年 / 补白分别覆盖 |
| W15 | 首页状态行把 6 个记录入口挤出首屏，或停用状态下仍占一行（**v5 已消除**） | 用户 14:47 拍板删除整张状态行与其下方的截屏提示行，首页只剩一张「月经」模块卡；验收第 8 条改为核对「启用 / 停用态只差一张卡」 |
| W16 | **入口改为导航后，快速记一条多走一层**（v3 引入，v4 改法） | 子应用日历默认选中今天、下方紧接逐行填写区，起止动作在选中日标题下；验收第 3、9 条覆盖「进来第一屏就能填」 |
| W17 | 子应用做得太像参考 App，与 LifeTrack 视觉割裂，或误用其图标 / 文案 / 素材 | 只抄信息架构与交互，沿用 `ui-redesign.md` 的 token、三档视觉重量与自有文案；不复制图标、视觉素材、品牌元素；原型深浅色各走一遍 |
| W18 | 子应用内再做一份「今日记录列表」，与首页时间轴重复且口径打架 | §5.4 明确不做；子应用只呈现周期视角，事件级回看走历史页；验收第 9 条核对 |
| W19 | 子应用内逐行表单与通用弹层编辑器两套 UI，日期 / 时区 / 字段校验漂移（v4 改写） | 共用 `MenstruationFields` 枚举与 validator、共用 `saveGenericRecord`；弹层带 `initialDate` 并沿用 `RecordEditorDialog` 的日期时间壳；补记历史日与记今天各一条单测 |
| W20 | **列表重建把滚动位置重置回顶部**，用户每点一个选项都要重滚到表单，逐行填写交互失效（v4 原型实测踩过） | Compose `LazyColumn` 给 item 稳定 `key` + 状态提升；原型 `innerHTML` 重建时保存并恢复 `.screen.scrollTop`；验收第 10 条连续点 5 个选项核对 |
| W21 | **日记是高敏感自由文本**，可能写入第三方身份 / 医疗细节，读屏或明文导出泄露 | 复用 `note` 而非新建字段，沿用 L1：列表与详情读屏只一句汇总、不拼原文，明文导出风险文案点名，输入框提示「请勿记录他人身份信息」；不引图片 / 富文本 / 位置 |
| W22 | 原型日记按 500 字演示，与代码里 `note` 的实际长度约束不一致 | 实现时以 `RecordEditorDialog` / `RecordDetail` 的既有约束为准，不新增常量；原型数字只作交互演示，验收核对两者一致 |
| W23 | 图示用成装饰性 emoji、复制参考 App 图标素材，或「可选项图示」与「已填值图示」不一致 | 统一 `PeriodDayIcons` 自绘矢量，语义固定且选项 / 已填值共用一套；不复制素材、不用渐变；深色与 200% 各验一次，读屏不重复播报 |
| W24 | 日历置顶后格子过高，把逐行填写区挤出首屏；或为压缩而让 200% 字体下日期数字挤压 | 格子用固定 `heightIn` 而非 1:1 正方形，200% 下单独抬高度；原型以「首屏可见日历 + 至少 3 行表单」为基线，验收第 6 条核对 |
| W25 | **首页删掉截屏提示行后，用户遇到全应用截屏黑屏却不知道原因**（v5 引入） | 说明收敛到「设置 → 记录项目」首次启用提示与设置页安全区文案（§5.5），验收第 5 条核对；`ScreenCapturePolicy` / `MainActivity` 判定与 `FLAG_SECURE` 机制一行不改 |
| W26 | 首页不再显示「经期第 N 天 / 距下次 X 天」，用户须进子应用才能看到周期状态 | 用户 14:47 明确取舍，不是遗漏；子应用精简状态条承担全部周期状态，P7 提醒文案同步改为「只在子应用显示」；**不以任何形式在首页补回第二张卡** |

### 5.10 分期

| 期次 | 范围 | 退出条件 |
| --- | --- | --- |
| **E1（本设计对应）** | 类型注册、`CycleCalculator`、字段 5 项 + 日记行（复用 `note`）、起止与补齐；**经期子应用首屏**（精简状态条 / 置顶可翻月日历 / 选中日起止动作 / **6 行逐行填写与自绘图示** / 本周期简报 / 趋势 / 设置 / 免责）；首页模块卡改导航入口（**v5 起不加状态行 / 截屏提示行，首页关于月经只有这一张卡**）；`initialDate` 通用弹层编辑器；L1 隐私收敛、备份往返、单测与验收 | §5.8 全部通过（含布局零回归、子应用入口闭环与逐行填写 / 日记验收），README / 台账同步 |
| **E2** | 体重 / 体温数值字段与趋势、自定义症状标签、完整周期健康报告、症状频次统计、排卵日「记录参考」（强免责） | 单独出补充设计，评估是否仍不升 Room |
| **E3（需单独评审）** | 提醒通知（`POST_NOTIFICATIONS` + WorkManager）、备孕 / 怀孕 / 育儿四模式 | 重走 C9 隐私评审与 Local First 边界评审；四模式还涉及另一套数据模型，不承诺实施 |

---

## 6. 待确认清单

> V6 / V10–V14 / V16 已由用户 2026-11-12 13:39、14:03、14:47 三轮指示拍板并回填（标 `[x]`）；其余为推荐值，已写入 §5，若无异议可直接按推荐值开动。

| 编号 | 问题 | 推荐值 | 需要用户特别留意的取舍 |
| --- | --- | --- | --- |
| V1 | P1 数据模型 | A：按天事件 + 派生周期，不升 Room | 区间段由「连续有记录日」推导，起止动作会自动补齐中间日 |
| V2 | P2 统计口径 | 一条 = 一天，如实计入全部次数 | 首页 / 统计的「总次数」会随经期天数上升 |
| V3 | P3 隐私与默认状态 | 默认停用 + L1 收敛 + 启用期间截屏遮挡 | 升级后需主动到「设置 → 记录项目」开启 |
| V4 | P4 字段集（v4 调整） | 5 字段（流量 / 痛经 / 症状 / 心情 / 分泌物）+ **日记行（复用 `note`）** | 体重体温、自定义症状推 E2；日记不新增 detail key，不引入图片 / 富文本 |
| V5 | P5 预测范围 | 只预测经期，中位数，不做排卵期 / 易孕期 / 安全期 | 与参考 App 日历相比会少几种颜色标注 |
| V6 | P6 界面落点（v3 升级 / v5 收敛） | [x] **`cycle` 二级页为完整经期子应用；首页只有「月经」模块卡一个入口指向它（v5 删除状态行与截屏提示行）；历史页与底部四页导航不动** | 不新增第 5 个底部入口；日历与报告全在子应用，首页不显示周期状态 |
| V7 | P7 提醒 | v1 不做推送 | 少了参考 App 的「提前 N 天提醒」 |
| V8 | 功能范围 | 只抄 §2 中标「抄」的条目 | 社区 / 电商 / 云同步 / 孕产模式 / 内容资讯明确不做 |
| V9 | 分期 | 先做 E1（子应用首屏），E2/E3 另开 | 参考 App 的「健康分析报告」E1 只有简报，完整报告页放 E2 |
| V10 | 本轮交付边界（v5） | [x] **只交设计文档 v5 + 可交互原型 v5，不改代码、不改台账、不发版** | 用户说「开动」后再进入实现 |
| V11 | 布局方案 | [x] **不做全局重设计**，子应用仍是隐藏底导的二级页 | 若仍想让周期成为第 5 个底部入口，需单独推翻 `ui-redesign.md` 的四页 IA 并重走全量回归 |
| V12 | 子应用范围（v3，用户 13:39 拍板） | [x] **经期核心闭环**：置顶可翻月日历 / 逐行填写与日记 / 按天记录 / 趋势与简报 / 设置；不做账号、云同步、社区、电商、资讯、孕产模式、推送 | 四模式与商业化模块会突破 Local First 与网络唯一例外，需另开评审 |
| V13 | 入口语义（v3，用户 13:39 拍板） | [x] **点「记录月经」模块卡直接进子应用** | 首页不再承担「记一笔」；v4 改由「日历默认选中今天 + 下方紧接逐行填写区」补回，主按钮取消 |
| V14 | 子应用内交互形态（v4，用户 14:03 指示） | [x] **日历置顶选日期 → 下方一行一行填内容 → 每行有图示 → 末行加日记**；点行展开、即点即存，子应用内不用弹层 | 通用弹层编辑器仍保留给首页时间轴 / 历史页入口，两条路径数据同源 |
| V15 | 日记的存储位置（v4 新增，推荐值） | **复用 `TrackEventEntity.note`，不新增 detail 字段** | 若改为新增 `diary` detail key，会外溢到 `DetailSpec`、备份规格与校验，且 §5.6「零存储改动」结论失效；日记为高敏感自由文本，按 L1 收敛 |
| V16 | 今天页清理（v5，用户 14:47 拍板） | [x] **删除周期状态行（「经期第 3 天 / 打开经期 → / 按最近 6 个周期估算」）与「已开启截屏保护」提示行；其它四张模块卡、今日记录、底部导航全部保留** | 周期状态只在子应用内呈现；`FLAG_SECURE` 机制不变，说明改由「设置 → 记录项目」首次启用提示承担 |

---

## 7. 参考来源

- 美柚官网功能说明：https://www.meiyou.com/mobile.html 、https://meiyou.com/
- 美柚官方帮助「如何记录经期 / 心情 / 爱爱 / 体重体温 / 分析」：http://view.seeyouyima.com/help/help_jilu.html?lang=zh
- 第三方功能条目（用于补全盘点，含营销描述，未作为字段或算法依据）：https://www.onlinedown.net/soft/601897.htm 、https://m.pc6.com/s/1706710 、https://g.pconline.com.cn/x/200/20065576.html
- 项目内部依据：`README.md`、`docs/PROGRESS.md`、`docs/intimacy-module-confirmation.md`、`docs/security-hardening-confirmation.md`、`docs/ui-redesign.md`、`docs/settings-redesign-v2.md`、`.agents/project-profile.md §9`
- 配套原型：`docs/menstruation-module-prototype.html`（单文件，浏览器直接打开；可切深浅色、200% 字体、三种数据场景；v5 经期页为「置顶日历 + 6 行逐行填写 + 自绘图示 + 日记行」，点日历换日期、点行展开选项、即点即存；今天页只保留「月经」模块卡，无状态行与截屏提示行）。可用 `?page=cycle&row=diary&scroll=form` 直达日记行，`&date=YYYY-MM-DD` 指定选中日，`&month=1` 翻月。
- 用户决策记录（2026-11-12 13:39）：「月经记录是一个大工程，点击记录月经的时候进入一个新的页面，这个页面做得几乎和美柚差不多，让使用者无缝使用」。同轮拍板：范围 = 经期核心闭环、入口 = 点模块卡直接进子应用、本轮 = 改文档 v3 + 重做原型 v3。
- 用户决策记录（2026-11-12 14:03）：「进入经期这个二级页后，你要做成和美柚相同的交互方式。上层显示日历。下面一行一行的填内容。填写的内容有图示，增加一行日记的功能」。本轮据此产出文档 v4 + 原型 v4；日记复用 `note`，仍不写 Compose 代码。
- 用户决策记录（2026-11-12 14:47，附截图圈定范围）：「今天页只留月经这个栏目，去掉下面的那个框」；随后明确「删除掉这些：一行『经期第 3 天』右边挂『打开经期 →』，下面一行小字『按最近 6 个周期估算 · 仅供记录参考』和『已开启截屏保护：启用高敏感模块后全应用禁止截屏』；其他保留」。本轮据此产出文档 v5 + 原型 v5。
