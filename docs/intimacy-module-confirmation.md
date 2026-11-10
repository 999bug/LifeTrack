# A10 亲密关系模块 · 设计前确认单

> 建立日期：2026-11-10 · 对应台账 `docs/PROGRESS.md` A10 · 状态：**已完成并验收（2026-11-10，按 V1–V10 推荐值）**
> 解禁依据：用户 2026-11-10 反馈「0.6.0 安装成功」，C10 真机回归销项，A9 / A10 的加密前置解除。
> 用户指示：**本包只做 A10 亲密关系；A9 月经保留 ⛔，不开发、不提前占位。**
> 前置基线：`3b5ea49`（0.6.0 代码基线，该版本已于 2026-11-10 发布）；191 项单测 + 14 项仪器测试全绿、`lintDebug` 无 error、`assembleRelease` 成功；工作区干净。
> 本包性质：**高敏感模块**。隐私口径优先于字段完备度，确认前不写任何代码。

## 1. 目标与非目标

**目标**：沿用 A6 铺好的「通用事件 + 可选详情」扩展点，新增第 5 个记录类型 `intimacy`（亲密关系），做到设置页可启用、首页可新增 / 编辑 / 删除、列表与详情可读、备份可往返，并对展示面做与敏感度相称的收敛。

**非目标（本轮不做）**：

- 不开发 A9 月经模块；不新增 `menstruation` 类型、不预建 A9 字段、不改 `StatsPeriod` / 周期计算相关结构。
- 不升 Room（仍 `version = 2`），不动 `MIGRATION_1_2` 与 `app/schemas/1.json` / `2.json`。
- 不升备份 `schemaVersion`（v1 / v2 明文 / v3 加密三版不变），不改 `BackupCodec` DTO、`BASE_TYPE`、`BackupDecodeOptions` 默认集合。
- 不改 DAO、`RoomTrackRepository`、`StatsAggregator`、月历、历史页查询、统计页类型筛选（均为数据驱动，自动生效）。
- 不给 `DetailSpec` 引入日期、布尔、跨记录关联等新字段种类，仍只用「字符串枚举 + 整数区间」。
- 不做一键打卡：`quickRecord()` 硬编码 `addPoopRecord`，改动会外溢到首页双按钮与 ViewModel 专用 API。
- 不加通知 / 提醒（会新增 Notification 泄露面，需重走 C9）、不加健康建议、不加第三方依赖、不加任何网络能力。
- 不按类型排除备份内容：静默丢数据违反项目红线。

## 2. 现状核对（代码依据，非推测）

| 结论 | 依据 |
| --- | --- |
| 存储层零改动成立 | `DefaultTypes.kt:53` 注释「新增内置类型时只改这里」；`DetailSpec.kt:79` `SUPPORTED_DETAIL_TYPES = DetailSpecRegistry.builtIn.keys` 为派生集合；`BackupCodec.parseDetail():366-401` 是通用分派，加规格即生效 |
| 数据驱动的部分自动生效 | 首页次数卡 `TodayScreen.kt:51-53,103-124`、统计筛选 `StatisticsScreen.kt:141-167`、设置页 `ModuleManagementScreen.kt:45,82-92`、月历与历史查询 均读 `trackTypes` Flow |
| 表现层仍有 5 处编译期分派 | `TodayScreen.kt:148-172`（入口）、`LifeTrackApp.kt:154-188`（编辑器）、`RecordRow.kt:49-83`（列表项）、`RecordDetailPresentation.kt:90-98`（详情字段）、`DetailSpec.kt:34-72`（规格表） |
| 最易漏的一处 | `RecordDetailPresentation.kt` 是 A7 之后「记录详情弹窗」新增的文件，A7 确认单的文件清单里没有；漏改会让详情页显示「该类型来自更新的版本」 |
| 编辑器 `else` 会落到排便 | `LifeTrackApp.kt:180-187`，漏加分支时编辑亲密关系记录会打开 `PoopEditorDialog` |
| 放行集合断言是硬编码 | `DetailSpecTest.kt:57-63` `expected = setOf(water, coffee, sport)`，注册新类型后必然失败；负向样例已统一用永不注册的 `mood`（`project-profile.md §9`） |
| 现有类型全部默认启用 | `DefaultTypes.kt` 四个 `TrackTypeEntity` 均 `enabled = true`；`RoomTrackRepository.kt:70-72,199-203` 用 `insertIgnoreAll(DEFAULT_TYPES)` 补齐，不覆盖已存的 `enabled` / `sortOrder` |
| 停用只隐藏首页新增入口 | `ModuleManagementScreen.kt:67`：已有历史记录、月历、统计不受影响；统计筛选 chip 仍会出现该类型 |
| 应用锁默认关闭 | `SettingsRepository.kt:29-32` 默认 `false`；`MainActivity.kt:23-32` `FLAG_SECURE` 仅在开关开启时加 |
| C9 隐私结论有前提 | `security-hardening-confirmation.md:361`「没有发现需要改代码的隐私问题」是在**只有 4 个非敏感模块**的前提下得出的；当前代码**没有任何按类型分级遮蔽**的能力 |
| 高密度的展示面 | `RecordDetailSheet.kt:94-96` 把「字段标签：值」写入读屏 `contentDescription`；`CoffeeRecordItem.kt:61-65` 等列表摘要直接拼备注原文 `record.event.note`；`TodayScreen.kt:68-76` 次数卡整句念出类型名与次数 |
| A9 的耦合边界 | `BackupCodec.kt:47` `BASE_TYPE = "poop"`、`TrackEventDao.kt:127-138` `observeStatDetails` 写死 `'poop'` + `poop_detail`、`MainViewModel.kt:136-140` 分布卡按 `DEFAULT_POOP_TYPE.code` 判定。A10 一律不碰这三处 |

## 3. 必须先拍板的口径（隐私优先）

### P1 · 隐私等级（本包最核心的决策）

| 档 | 列表摘要 | 详情弹窗 | 读屏 | 应用锁 / 截屏 | 改动量 |
| --- | --- | --- | --- | --- | --- |
| L0 无额外遮蔽 | 显示字段与备注原文 | 全字段 | 逐字段朗读 | 沿用现状 | 最小（同 A7/A8） |
| **L1 列表级收敛（推荐）** | 只显示「亲密关系 · 时间」，不显示任何详情字段与备注 | 用户主动点开后正常展示 | 列表与详情各收敛为一句汇总 | A10 启用时无条件加 `FLAG_SECURE`，首次启用提示开启应用锁 | 中等，+3 个文件 |
| L2 全遮蔽 + 强制验证 | 列表只显示占位 | 需二次生物验证才展开 | 完全不念内容 | 强制开启应用锁（全局行为变更） | 大，需重走 C9 评审 |

推荐 **L1**：A10 与既有四类不同，列表与读屏是「旁人瞥一眼 / 系统朗读」就能拿到内容的高频泄露面；详情弹窗是用户主动点开，保留完整信息不影响可用性。L2 会改变 0.6.0 已发布用户的全局锁行为，收益与代价不对等，本轮不做。

### P2 · 默认启用状态

推荐 **默认停用**（`DEFAULT_INTIMACY_TYPE.enabled = false`）：高敏感模块不应在升级后突然出现在首页、统计筛选和读屏汇总里，应由用户在「设置 → 记录项目」主动开启。`insertIgnore` 不覆盖已存设置，老用户升级与恢复旧备份都只会补进一条停用行，符合预期。

### P3 · 应用锁与截屏

推荐 **不强制改全局开关**，但：A10 启用期间无条件给 `MainActivity` 加 `FLAG_SECURE`（即使应用锁关闭，最近任务预览与系统截屏也不泄露当前页面）；首次启用时给一次性提示「建议开启应用锁」并附设置入口，不阻断使用。

### P4 · 字段集（数据最小化）

推荐 **3 个详情字段 + 通用备注**，值统一字符串，不记录任何可识别第三方身份的信息：

| 字段 key | 取值 | 校验 | 说明 |
| --- | --- | --- | --- |
| `protection` | `yes` / `no` / `na` | 枚举白名单 | 是否采取保护措施；`na` 表示不适用 / 不想记录 |
| `experience` | `good` / `okay` / `bad` | 枚举白名单 | 主观感受三态；不做健康判断、不换算成分数 |
| `durationMin` | `1..1440` 整数字符串 | 范围 + 纯数字 | 可选；与运动模块同口径，超过 60 分钟展示时换算小时，不做热量或配速推算 |

备注沿用 `TrackEventEntity.note`（所有类型共用），字段常量与校验集中在 `core/model/IntimacyFields.kt`，编辑器与备份规格共用一处，避免两处漂移（A6 经验）。全部字段可空，缺字段合法，`detail = null` 也合法，展示层用「未记录」占位。

备选：加第四个字段 `partner`（`regular` / `other` / 不填的枚举代号）——**不推荐**，理由见 P5。

### P5 · 是否记录伴侣信息

推荐 **不记录姓名、备注外的自由文本或任何可识别身份的标识**。这是 Local First 的个人记录工具，记录第三方身份既超出必要范围，也会在明文备份、SAF 导出、读屏里外溢。若确需区分，后续再评估「不可识别的枚举代号」，本轮不做。

### P6 · 统计口径与停用后可见性

推荐 **计入「全部」次数，保持跨面板自洽**（A11 的硬口径：首页分列 / 历史全类型 / 月历全类型 / 统计类型筛选一致），不为该类型开特例。停用后沿用项目既定行为：只隐藏首页新增入口，统计筛选 chip 与已有记录仍可见。若 P1 选 L2，再单独评估「停用时完全隐藏」。

### P7 · 明文备份导出

推荐 **全量导出 + 加强风险文案**：v3 加密备份已是默认；用户显式选明文导出时，风险确认里点明「导出的文件将包含亲密关系记录，任何拿到文件的人都能查看」。**不按类型排除**——`restoreBackup` 是整体替换事务，静默跳过会让用户在「恢复成功」下永久丢数据（项目红线）。

## 4. 技术方案与影响面

### 4.1 数据层（确认零改动）

`AppDatabase` 仍 `version = 2`；`app/schemas/` 仍只有 `1.json` + `2.json` 且内容不变；`Migrations.kt` 不动；`BackupCodec.SCHEMA_VERSION = 2` / `ENCRYPTED_SCHEMA_VERSION = 3` / `SUPPORTED_VERSIONS = {1,2}` 不动；`BackupCodec.kt`、`BackupPayload.kt`、`BackupEnvelope.kt`、`BackupIo.kt` 不改；DAO 与 `RoomTrackRepository` 不改。新增类型只是 `track_type` 一行数据 + `event_detail` 里带 `typeCode` 的行。

### 4.2 注册与规格

- `DefaultTypes.kt`：新增 `DEFAULT_INTIMACY_TYPE`（`code = "intimacy"`，名称「亲密关系」，`category = "activity"`，`sortOrder = 50`，`enabled = false`，`schemaVersion = 1`），加入 `DEFAULT_TYPES`。用已有 `activity` 类别可避免 `ModuleManagementScreen.categoryLabel():202-207` 显示原始英文串。
- `DetailSpec.kt`：`builtIn` 增加 `intimacy` 规格（3 个 allowedFields + 3 个 validator）。`SUPPORTED_DETAIL_TYPES` 与 `BackupDecodeOptions.Default` 自动包含它。
- 负向测试样例保持 `mood`，不得改成 `intimacy`（`project-profile.md §9` 已两次记录同类踩坑）。

### 4.3 UI 分派清单（5 处必须同步）

1. `TodayScreen.kt`：加 `onIntimacyRecord` 回调与 `when (type.code)` 入口分支。
2. `LifeTrackApp.kt`：`TodayScreen` 传参 + `when (editorTypeCode)` 加 `IntimacyEditorDialog` 分支（防 `else` 落到排便编辑器）。
3. `RecordRow.kt`：加 `IntimacyRecordItem` 分支（防落到只读 `UnknownRecordItem`）。
4. `RecordDetailPresentation.kt`：`detailFields()` 加 `intimacyFields()`，按 P1 决定详情展示粒度。
5. `DetailSpec.kt`：规格表（见 4.2）。

### 4.4 L1 隐私收敛的实现点

- `IntimacyRecordItem.kt`：摘要只输出「亲密关系 · 时间」，不拼 `fields` 与 `record.event.note`。
- `RecordRow.kt` / `RecordDetailSheet.kt`：对 `intimacy` 分支用 `clearAndSetSemantics { contentDescription = "已记录一条亲密关系记录" }`，避免逐字段朗读（沿用 B3 的图表汇总做法）。
- `MainActivity.kt`：`FLAG_SECURE` 条件由「应用锁开启」扩为「应用锁开启 **或** intimacy 类型已启用」；`enabled` 变化需可观察（读 `trackTypes` Flow 或设置页开启时即时生效），不开后台常驻监听。
- `ModuleManagementScreen.kt`（或设置页）：首次启用 `intimacy` 时一次性提示，不阻断。
- `SettingsScreen.kt`：明文导出的红色风险文案追加高敏感记录提示。

### 4.5 兼容与边界

- 旧版（0.6.0 及更早）备份里没有 `intimacy` 类型：恢复时 `DEFAULT_TYPES` 兜底补进停用行，不报错。
- 新版备份恢复到旧版：旧版 `supportedDetailTypes` 不含 `intimacy`，会明确 `UNSUPPORTED_DETAIL_TYPE` 并拒绝导入，不静默丢数据；README 需补这条单向兼容说明（与 v3 加密备份同理）。
- `200%` 字体 / `360dp` 窄屏：三态 chips 在 `fontScale > 1.3f` 时分两行（A7 的 `ChipRow` 经验），不复用横向滚动。
- 深浅色：沿用现有 `SettingsSection` 与 `RecordEditorDialog` 骨架，不新增配色。
- A9 隔离：本包不碰 `BASE_TYPE`、`observeStatDetails`、`quickRecord()`、`StatsAggregator` 与形态分布卡，避免把月经的周期 / 分布需求堵死。

## 5. 文件清单

新增：

- `app/src/main/java/com/example/lifetrack/core/model/IntimacyFields.kt`
- `app/src/main/java/com/example/lifetrack/feature/intimacy/IntimacyEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/intimacy/IntimacyRecordItem.kt`
- `app/src/test/java/com/example/lifetrack/core/model/IntimacyFieldsTest.kt`
- `app/src/test/java/com/example/lifetrack/core/backup/BackupIntimacyDetailTest.kt`
- 本确认单 `docs/intimacy-module-confirmation.md`

修改（L1 方案）：

- `core/database/DefaultTypes.kt`、`core/backup/DetailSpec.kt`
- `feature/home/TodayScreen.kt`、`core/navigation/LifeTrackApp.kt`、`feature/record/RecordRow.kt`
- `core/model/RecordDetailPresentation.kt`、`feature/record/RecordDetailSheet.kt`
- `core/navigation/MainActivity.kt`（按 P3）、`feature/settings/ModuleManagementScreen.kt`（按 P3）、`feature/settings/SettingsScreen.kt`（按 P7）
- `app/src/test/java/com/example/lifetrack/core/backup/DetailSpecTest.kt`（放行集合 + 规格断言）
- `app/src/test/java/com/example/lifetrack/core/model/RecordDetailPresentationTest.kt`（详情映射 / 读屏汇总）
- `README.md`（已实现范围、兼容说明、相关文档）、`docs/PROGRESS.md`（A10 状态、C10 销项、A9 保留 ⛔）、`.agents/project-profile.md §9`（如有新雷区）、工作日志

## 6. 验收标准

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 全绿，单测数由 191 增至 ≥ 200；`connectedDebugAndroidTest` 14 项不回退。
2. `AppDatabase` 仍 `version = 2`，`app/schemas/` 仍只有 `1.json` + `2.json` 且内容不变；`BackupCodec` 三个版本常量不变；DAO / Repository / `BackupCodec.kt` 无 diff。
3. 全新安装与 0.6.0 覆盖安装：设置页出现第五项「亲密关系」且**默认停用**；主动开启后首页出现入口，重启保持；停用时首页无入口。
4. 新增记录：三字段 + 备注，保存后今日列表按 L1 只显示类型与时间；今日其余模块次数不变；重新打开三字段与时间全部回填；修改、删除生效。
5. 备份往返：默认 v3 加密导出后恢复，三字段与备注完整回来；明文 v2 导出同样往返；把 `durationMin` 改成 `99999`、`protection` 改成非法值、塞入未知字段 `partnerName`，三种坏文件均明确拒绝且带记录 ID，且不破坏现有数据。
6. 隐私走查：开启 A10 后，最近任务预览与系统截屏不显示列表内容；TalkBack 在首页 / 历史 / 详情弹窗只听到一句汇总，不逐字段朗读；应用锁关闭状态下 `FLAG_SECURE` 仍生效；明文导出确认文案点明含亲密关系记录。
7. 旧版备份（无 `intimacy` 类型）恢复成功且该项保持停用；新版含 `intimacy` 的备份在 0.6.0 上被明确拒绝（若具备双版本环境则实测，否则以单测覆盖）。
8. 深 / 浅色、200% 字体、`360dp` 窄屏、宽屏下编辑器、列表项、详情弹窗无截断、无重叠、无横向溢出；三态 chips 首屏完整可见。
9. A9 仍为 ⛔，README / 台账无「月经已实现」类表述；排便专用链路（`BASE_TYPE` / `observeStatDetails` / `quickRecord`）无 diff。
10. `git status` 干净（提交后），模拟器分辨率 / 字号 / 主题还原，无遗留后台任务与测试文件。

## 7. 风险与对策

| 编号 | 风险 | 缓解 |
| --- | --- | --- |
| W1 | `DetailSpecTest` 硬编码放行集合，注册后必然失败 | 扩成 `{water, coffee, sport, intimacy}` 并加规格断言；**改的是样例，不是放宽校验** |
| W2 | 负向样例误用 `intimacy` 当「未注册类型」 | 统一保持 `mood` / `unknown_type`，沿用 `project-profile.md §9` 的教训 |
| W3 | 漏改 `RecordDetailPresentation.kt` | 文件清单单独标出；验收第 4 条强制走一遍详情弹窗 |
| W4 | `LifeTrackApp` 的 `else` 落到排便编辑器 | 加显式分支，验收覆盖「点编辑打开的是亲密关系编辑器」 |
| W5 | 默认停用被 `insertIgnore` 或备份恢复覆盖成启用 | 核对 `ensureDefaultTypes` 与 `restoreBackup` 两处补齐路径；验收第 3 条覆盖覆盖安装与旧备份恢复 |
| W6 | `FLAG_SECURE` 条件扩展影响 0.6.0 既有用户 | 只在 `intimacy` 已启用时额外生效，其余用户行为不变；不做全局强制应用锁 |
| W7 | 明文导出按类型排除会静默丢数据 | 明确不排除，只加强提示；验收第 5 条覆盖全量往返 |
| W8 | 为 A10 改 `BASE_TYPE` / 分布卡 / `quickRecord` 会堵死 A9 | 本包列为非目标，验收第 9 条检查无 diff |
| W9 | 高敏感字段出现在日志或异常文案 | 不在 `SettingsScreen.describeError` 或任何 `Log` 里拼 payload；沿用 C9 的三条日志口径 |
| W10 | 新增类型让统计筛选 chip 在停用态仍显示 | 按 P6 沿用既定行为并记录在 README；若确认要改，另开子任务，不在本包顺手做 |

## 8. 待确认清单

> 用户 2026-11-10 回复「按推荐值开动」，V1–V10 全部按推荐值落地。以下保留原始推荐值供回溯。

| 编号 | 问题 | 推荐值 | 备选 |
| --- | --- | --- | --- |
| V1 | 隐私等级 | [x] **L1 列表级收敛** | L0 无遮蔽 / L2 全遮蔽 + 强制验证 |
| V2 | 默认启用状态 | [x] **默认停用，用户主动开启** | 默认启用（与前四类一致） |
| V3 | 应用锁 / 截屏 | [x] **启用期间无条件 FLAG_SECURE + 首次启用一次性提示，不强制改全局开关** | 仅提示 / 强制开启应用锁 |
| V4 | 字段集 | [x] **3 字段：`protection` / `experience` / `durationMin`，均可空** | 4 字段加 `partner` / 极简 1 字段 |
| V5 | 伴侣信息 | [x] **不记录可识别身份，备注之外的自由文本也不加** | 可选枚举代号 `regular` / `other` |
| V6 | 统计口径 | [x] **计入「全部」，保持跨面板自洽；停用后沿用既定可见性** | 从首页 / 统计排除 |
| V7 | 列表与读屏 | [x] **列表不显示详情与备注，读屏收敛为一句汇总** | 完整显示 |
| V8 | 明文备份 | [x] **全量导出 + 加强风险文案，不按类型排除** | 排除该类型（不推荐，破坏完整性） |
| V9 | 一键打卡 | [x] **不做** | 做（需新增 ViewModel 方法 + 首页双按钮） |
| V10 | A9 月经 | [x] **保留 ⛔，本包不碰排便专用链路** | — |
| V11 | 版本 | 硬约束：Room v2 / 备份 v1·v2·v3 不变，无需确认 | — |

## 9. 实施记录

- 状态：**已完成并验收**（2026-11-10）。代码、测试与文档同批落地，按 V1–V10 推荐值，无偏离推荐值的决策。

### 落地文件

- 新增：`core/model/IntimacyFields.kt`、`feature/intimacy/IntimacyEditorDialog.kt`、`feature/intimacy/IntimacyRecordItem.kt`、`core/model/IntimacyFieldsTest.kt`、`core/backup/BackupIntimacyDetailTest.kt`、本确认单。
- 修改：`DefaultTypes.kt`、`DetailSpec.kt`、`TodayScreen.kt`、`LifeTrackApp.kt`、`RecordRow.kt`、`RecordDetailPresentation.kt`、`RecordDetailSheet.kt`、`MainActivity.kt`、`ModuleManagementScreen.kt`、`SettingsScreen.kt`、`DetailSpecTest.kt`、`RecordDetailPresentationTest.kt`、`README.md`、`docs/PROGRESS.md`、`.agents/project-profile.md §9`。

### 版本核对

`AppDatabase` 仍 `version = 2`，`app/schemas/` 仍只有 `1.json` + `2.json` 且内容不变；`BackupCodec` 的 `SCHEMA_VERSION = 2` / `ENCRYPTED_SCHEMA_VERSION = 3` / `SUPPORTED_VERSIONS = {1,2}` 未改；DAO、`RoomTrackRepository`、`BackupCodec`、`StatsAggregator` 与排便专用链路（`BASE_TYPE` / `observeStatDetails` / `quickRecord`）均无 diff。A9 月经保持 ⛔。

### 相对设计的修正

1. 确认单 §5 文件清单把 `MainActivity.kt` 写成 `core/navigation/MainActivity.kt`，实际在根包 `app/src/main/java/com/example/lifetrack/MainActivity.kt`；其余路径无误。
2. 「三项均可空」用编辑器里的「未记录」占位 chip 实现（`NO_ENTRY = ""`，只参与选中态，绝不落库）；三个字段都未填时草稿 `detail = null`，与恢复路径「无详情仍合法」一致。复用 `ChoiceChipRow` 的大字号分行，**未改共用组件**。
3. 首次启用提示用 `rememberSaveable` 在页面生命周期内保持；进程重启后若再次从停用切启用会再提示一次。为不引入新的 DataStore 状态源，未做跨进程持久化，记为已知偏差。
4. `FLAG_SECURE` 用 `combine(appLockEnabled, observeTrackTypes())` 实现，带 `onStart(false)` 与 `catch(false)` 兜底，不强制改应用锁开关。
5. 明文导出提示加在确认对话框正文，未改导出链路与备份内容。

### 验收结果

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` **BUILD SUCCESSFUL**（2m29s）。本地单测 **29 个类 / 204 项 / 0 失败**（基线 191 + 新增 13：`IntimacyFieldsTest` 4、`BackupIntimacyDetailTest` 6、`DetailSpecTest` +1、`RecordDetailPresentationTest` +2）；`lintDebug` 无 error；debug APK 覆盖安装成功（`LifeTrack-0.6.0-debug.apk`，签名一致，未卸载、未丢数据）。
- 模拟器 `emulator-5554` 主路径实测：设置页显示「已启用 4 / 共 5 项」，记录项目页第五项「亲密关系 · 活动 · 事件记录」且开关 `checked=false` → 启用后出现一次性提示 → 首页出现第五个入口与次数卡第五类 → 编辑器三字段与「未记录」选项齐全、三项全空时保存可用 → 保存后次数卡 1 次、列表语义树只有一句「已记录一条亲密关系记录」、无备注与字段值 → 详情弹窗语义树只有一句汇总且关闭按钮仍在语义树内。
- 隐私实测：模块启用、应用锁关闭时 `adb screencap` 得到全黑图（FLAG_SECURE 生效）；停用该模块后截图恢复，证明开关随启用状态动态切换。详情弹窗截图显示标题「亲密关系 · 09:43」、基本信息 / 记录详情 / 元数据三段，三项字段均为「未记录」，无截断、无重叠——即「视觉可见、读屏不念」的 L1 目标达成。
- 200% 字体实测：保护措施与主观感受两组 chips 各自分成两行且文字完整，无横向裁切；常规分辨率下编辑器全字段完整可见。
- 负向用例（单测）：`durationMin` 越界 / 非法枚举 / 未知字段 `partnerName` 均被 `INVALID_DETAIL_FIELD` 拒绝；部分字段与全空字段均放行；无详情记录仍可往返。

### 踩坑

- Git Bash 下 `adb shell cat /sdcard/*.xml` 与 `uiautomator dump /sdcard/...` 的设备侧路径被 MSYS 改写成本机 Windows 路径，导致 dump 读空。加 `MSYS_NO_PATHCONV=1` 后恢复（`profile §9` 已有同类记录，本轮复现）。
- 写 adb 走查脚本时，把命令替换和依赖它的变量放在同一条 `local` 赋值里（如 `local attr="$1" val="$2" b=$(grep ... "$attr")`），`$attr` 在展开阶段仍为空，会匹配到根节点并算出屏幕中心坐标，连续误触。拆成逐条赋值后正常。
- `clearAndSetSemantics` 会让 `uiautomator dump` 读不到被收敛区的任何文字，验证视觉必须另走截图通道；已写入 `profile §9`。

### 未验证 / 遗留

- `connectedDebugAndroidTest` 14 项本轮未跑（本轮只装 debug 包做 UI 走查，未执行仪器测试）；真机、TalkBack 实机朗读、备份往返的 UI 实操也未做，编解码链路已有单测覆盖。
- 宽屏（2000×1200）下首页渲染正常，但列表项需滚动才能进入视口，编辑器未单独在宽屏截图；200% 字体 + 宽屏同时施加时共用壳的对话框会被配置变更重建。
- **200% 字体下编辑器内容超过共用壳 `RecordEditorDialog` 的 `heightIn(max = 560.dp)`**：时长输入框与备注需向下滚动才能看到，主观感受第二行 chips 在首屏底部被滚动边界裁到。chips 分行本身完整、无横向裁切（A7 的修复目标达成）。共用壳影响全部记录类型，本包未擅自扩大改动范围，留待单独评估是否按 `fontScale` 分档抬高上限。
- ~~0.6.0 是否已从 Release 发版待确认~~ **已确认**：由用户 2026-11-10 自行触发，`v0.6.0` 于 16:42 发布（GitHub + Gitee 双端 Release 与 APK / `.sha256` 附件齐备），真机安装成功。README 第 3 / 14 行「尚未发布」文案已在发版机制改造那一轮同步。**A10 已合入 `main` 但不在 0.6.0 内**，将随下一次发版（CI 自动递增版本号）进入 Release。

### 环境收尾

字号还原 1.0、分辨率还原 1080×2400、主题未改；模块回到默认停用，测试记录已删除（今日 0 次），设备侧 `lt_*.xml` 调试文件已清理；本会话无遗留后台任务。
