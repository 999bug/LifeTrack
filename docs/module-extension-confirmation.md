# 模块扩展第二批（A3 / A4 / A5）设计前确认单

> 建立时间：2026-11-09
> 依据：`docs/PROGRESS.md` 台账 A3 / A4 / A5
> 状态：本单先落盘，随后按推荐值实施（用户已用「继续」授权开工）；若有口径需改，随时打断

## 1. 现状基线（逐行核对，非推测）

| 位置 | 现状 |
| --- | --- |
| `AppDatabase.kt` | `@Database(version = 1, exportSchema = true)`，三张表：`track_type` / `track_event` / `poop_detail` |
| `app/schemas/.../1.json` | 只有 version 1 基线 |
| `TrackEventDao.kt` | 所有业务查询都带 `WHERE typeCode = 'poop'`；`getAllPoopEvents()` 返回 `List<PoopRecordRelation>` |
| `PoopRecordRelation` | Room `@Relation`，**编译期绑定** `poop_detail`，无法承载其他类型 |
| `BackupPayload.events` | `List<PoopRecordRelation>`，备份格式与 Room 关系类耦合 |
| `BackupCodec.SCHEMA_VERSION` | `1`，decode 里 `file.schemaVersion != 1` 直接 `UNSUPPORTED_VERSION` |
| `BackupEventDto` | 只有 `poop: BackupPoopDto?` 一个详情字段 |
| `BackupCodecTest` | 14 项，含一条 `rejects an unknown schema version`（硬编码 `schemaVersion = 2` 应被拒绝） |
| `PoopEditorDialog.kt` | 303 行，日期/时间/备注/时区/选择器与排便详情字段混在一个 Composable 里 |
| `MainViewModel` | `buildBackupJson()` / `decodeBackupJson()` / `restoreBackup()` 直连 `BackupCodec` |
| `DEFAULT_TYPES` | 当前仅 `poop` 一个内置类型（A1 已补 `ensureDefaultTypes()`） |

## 2. 本轮目标与非目标

**目标**
- A3：抽出通用记录编辑器壳，详情字段由各类型提供；排便编辑器行为不变。
- A4：`BackupPayload` 脱离 `PoopRecordRelation`，用「事件 + 可选详情」表达任意类型。
- A5：备份 `schemaVersion` 1 → 2，新增通用详情通道与按类型分派的校验链，v1 旧备份仍能恢复。

**非目标（明确不做）**
- 不改 Room schema、不写 Migration、不新增详情表（属 A6）。
- 不新增记录类型（饮水 / 咖啡茶 / 运动属 A6–A8）。
- 不改统计、月历、历史列表的查询口径（它们仍只统计 `poop`，A11 再适配）。
- 不做数据库加密 / 导出加密（C 包，用户指定最后）。
- 不改导航、不改 `PoopEditorDialog` 对外签名、不改 `PoopRecordDraft`。
- 不引入新依赖。

## 3. 关键设计口径

### Q1 通用详情存哪里？→ 本轮只在「传输/领域层」，不落库

`RecordDetail` 是 sealed 领域模型（`Poop` 与 `Generic(typeCode, fields)`），`BackupRecord = event + detail?`。
生产路径上 `readBackup()` 只产出 `Poop` 变体；`Generic` 仅供备份格式与校验链使用，A6 加表后才真正落库。

**理由**：A4 台账只要求备份快照支持多类型，A6 才要求新增详情表。提前升 Room v2 会与 C3 的加密迁移撞版本（台账 R3），且对已发布 v0.1.0 的存量库是不可逆操作。

### Q2 v2 里 v1 的 `poop` 字段怎么办？→ 保留，双通道并存

v2 事件 DTO 保留 `poop: BackupPoopDto?`（排便详情继续走强类型校验），另加 `detail: BackupDetailDto?` 作为通用通道。
- `poop` 类型的事件只允许 `poop` 字段，带 `detail` 视为格式错误。
- 非 `poop` 类型只允许 `detail`，带 `poop` 视为格式错误。
- 同一事件两者都为空 = 快速打卡，合法。

**理由**：v1 备份与 v2 的排便部分字节结构一致，旧文件用一个 DTO 就能读（`ignoreUnknownKeys = true`），无需两套解析器；排便既有 6 字段范围校验也不降级成字符串。

### Q3 通用详情的字段表示？→ `Map<String, String>` + 按类型分派的规格表

`BackupDetailDto(typeCode, fields: Map<String, String>)`，值统一字符串（数值由校验器 parse）。
`DetailSpecRegistry` 按 `typeCode` 提供「允许的 key + 每个 key 的校验器」；A5 内置结构级校验（key 命名、数量、值长度），业务级规格由 A6/A7/A8 注册，A12 补齐逐字段校验。

**理由**：避免 `Any` / `JsonElement` 在备份里造成不可校验的野值；`Map<String,String>` 可直接用现有 kotlinx.serialization，且 Room 未落地前不需要实体类。

### Q4 遇到「本版本还存不下的通用详情」怎么办？→ 明确拒绝，绝不静默丢弃

`decode(..., supportedDetailTypes: Set<String> = emptySet())`：
- 生产默认空集 → 任何 `Generic` 详情都返回 `UNSUPPORTED_DETAIL_TYPE`，UI 提示「备份包含当前版本尚不支持的记录类型」。
- 单测传入自定义集合与规格，验证合法路径的往返与分派。
- A6 加表后把 `water` 加入集合即自动解开，无需再改格式。

**理由**：`restoreBackup` 是整体替换事务，若静默忽略未知详情，用户会在「恢复成功」提示下永久丢数据。宁可拒绝。

### Q5 v1 备份怎么兼容？→ 版本号白名单 + 按版本收紧校验

`SUPPORTED_VERSIONS = setOf(1, 2)`，`CURRENT_VERSION = 2`（只导出 v2）。
- v1：忽略 `detail` 字段（即便被塞进去也不生效），只按 `poop` 解析。
- v2：按 Q2 双通道解析 + Q3 分派校验。
- 其他版本：`UNSUPPORTED_VERSION`。

**注意**：`BackupCodecTest` 现有用例 `rejects an unknown schema version` 传的是 `2`，v2 上线后该用例必须改为 `3` 或 `99`，否则会失败——这是预期要改的断言，不是实现问题。

### Q6 编辑器抽象到什么程度？→ 只抽「事件壳」，不抽象领域草稿

`RecordEditorDialog` 负责：标题、日期时间选择（含 DatePicker / TimePicker / 大字号堆叠）、时区提示、备注输入框、保存/取消按钮、滚动容器。
详情区以 `content: @Composable ColumnScope.() -> Unit` 注入，各类型自己管 state 并组装自己的 draft。
`ChoiceRow` / `TriStateRow` 上移到公共文件供各类型复用。

**理由**：领域草稿强类型各不相同，强行统一会引入 `Map<String, Any>` 或反射，得不偿失。壳层统一即可保证各模块 UI 一致。

## 4. 文件清单（精确到路径）

**新增**
- `core/model/RecordDetail.kt` — `RecordDetail` sealed、`BackupRecord`、`RecordDetail.poop()` 转换
- `core/backup/DetailSpec.kt` — `DetailSpec`、`DetailSpecRegistry`、`validateDetailFields()`
- `feature/record/RecordEditorDialog.kt` — 通用壳、`PickerField`、`TimeDialog`
- `feature/record/RecordChoiceFields.kt` — `ChoiceRow`、`TriStateRow`
- `test/.../core/backup/BackupCodecV2Test.kt` — v2 往返、v1 兼容、分派、篡改、非法字段
- `test/.../core/backup/DetailSpecTest.kt` — 规格表与结构校验

**修改**
- `core/backup/BackupPayload.kt` — `events: List<BackupRecord>`，新增 4 个 `BackupError`
- `core/backup/BackupCodec.kt` — v2 编解码、版本白名单、通用详情通道
- `domain/repository/TrackRepository.kt` / `data/repository/RoomTrackRepository.kt` — 快照转换
- `feature/poop/PoopEditorDialog.kt` — 改为复用通用壳
- `feature/home/MainViewModel.kt` — 解码参数接线（默认空集）
- `test/.../core/backup/BackupCodecTest.kt` — 适配 v2、修正 Q5 的断言、构造类型调整
- `README.md` / `docs/PROGRESS.md` / `.agents/project-profile.md` / `.workbuddy/memory/2026-11-09.md`

**不动**：`AppDatabase.kt`、`app/schemas/1.json`、所有 DAO 查询、`StatisticsScreen`、`HistoryScreen`、`CalendarMonthView`、`LifeTrackApp.kt`。

## 5. 验收标准

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿，原有 53 项用例不减少。
2. v1 备份文件（用旧版 codec 生成的 JSON 文本）能被新版 decode 成功，`poop` 详情字段逐项保真。
3. v2 导出 → decode → 恢复，`poop` 详情与事件字段全部保真；`schemaVersion == 2`。
4. v2 带 `Generic` 详情：传入支持集合时往返保真；生产默认参数下被 `UNSUPPORTED_DETAIL_TYPE` 拒绝。
5. 坏文件：未知 typeCode 详情、`detail.typeCode` 与事件不一致、非法 key / 超长值 / 过多字段、v1 文件塞入 `detail`、字段数声明不符 —— 全部拒绝且不抛异常。
6. 排便编辑器外观与交互不变：深/浅色、200% 字体、日期/时间选择器、备注、详情 chips 均可操作。
7. 模拟器实测：导出 → 卸载重装或清空数据 → 恢复，历史/统计/今日次数自洽。
8. Room 仍为 version 1，`app/schemas/` 未新增文件。

## 6. 风险与对策

| 编号 | 风险 | 对策 |
| --- | --- | --- |
| D1 | 升 v2 后旧版 App 无法读新备份 | README 明确标注单向兼容；v2 文件的 `poop` 部分结构与 v1 一致，必要时可手工降级 |
| D2 | `BackupPayload` 换类型波及恢复事务 | 转换集中在 `RoomTrackRepository`，事务内先删事件再写类型/详情的顺序不变 |
| D3 | `Generic` 详情被拒绝但用户不知原因 | UI 文案明确指向「版本过旧」，不显示笼统的「文件损坏」 |
| D4 | A3 重构改动 Composable 状态管理，导致编辑时丢输入 | 壳层 state 用 `remember(record)` 与现状一致；截图回归新增/编辑两种入口 |
| D5 | 通用规格表设计过度，A6 又要重写 | A5 只做结构级校验 + 注册扩展点，业务级规则留给 A6/A12 |

## 7. 待确认项（按推荐值实施，可随时改）

| 编号 | 项 | 推荐值 |
| --- | --- | --- |
| V1 | 本轮是否升 Room v2 | 否，A6 再升 |
| V2 | v2 是否保留 `poop` 专用字段 | 保留 |
| V3 | 通用详情字段表示 | `Map<String, String>` |
| V4 | 生产默认支持的通用详情类型 | 空集（明确拒绝） |
| V5 | 导出版本 | 固定 v2，读 v1 + v2 |
| V6 | 编辑器抽象边界 | 只抽壳与公共 chips，草稿保持强类型 |
| V7 | 统计/历史是否按类型适配 | 否，A11 |
| V8 | 通用详情字段上限 | 每事件最多 16 个 key、key ≤ 32 字符、value ≤ 256 字符 |
| V9 | `BackupError` 新增项命名 | `UNSUPPORTED_DETAIL_TYPE` / `DETAIL_TYPE_MISMATCH` / `INVALID_DETAIL_FIELD` / `DETAIL_NOT_ALLOWED` |

## 8. 实施记录

待本轮验收后填写。
