# 饮品模块拆分与扩展（v0.7）· 设计确认单

> 建立日期：2026-11-19 · 对应台账：原 A7「咖啡 / 茶」的升级改造 · 状态：**已实现，编译 / 单测 / lint 全绿**
> 原型：`.workbuddy/prototypes/coffee-tea-prototype.html`（交互稿，未入库）
> 前置：用户对原型确认「符合需求」，并对四个关键决策全部选推荐值。

## 1. 目标与非目标

**目标**：把 v0.6 的「咖啡 / 茶」单类型拆成 `coffee` / `tea` 两个独立类型；咖啡补齐瑞幸点单式字段（品种 / 杯型 / 温度 / 豆子 / 浓度 / 糖度 / 奶油 / 热量），茶有独立字段（茶种 / 几杯 / 毫升 / 热量）；两者都支持自定义品种；历史数据自动迁移且不丢。

**非目标（本轮不做）**：

- 不升 Room schema：类型仍是 `track_type` 的数据行，字段仍在 `event_detail.payload`（JSON Map）。
- 不改备份格式：`schemaVersion` 仍为 2（加密外壳 3）。
- 不记价格：截图里的 `+¥3` 只作选项说明文案，不落金额、不进账本。
- 不做热量跨记录累计、不给健康建议。

## 2. 四项决策（用户已确认，均取推荐）

| 编号 | 决策 | 选定值 |
| --- | --- | --- |
| D1 | 拆开粒度 | 两个独立 `track_type`（`coffee` / `tea`），设置页两个开关、列表统计天然分开 |
| D2 | 历史数据 | 启动时按 `drinkType=tea` 一次性迁移到 tea 类型；恢复旧备份时同样转换；不改 Room 表结构 |
| D3 | 自定义品种 | 存成可复用清单（DataStore），设置页「饮品品种」可增删；不进备份（与 `ongoingPeriodStart` 同理） |
| D4 | 热量 | 选填 + 预设品种默认值（美式约 15、拿铁约 190、茶约 5），可手改；范围 0–3000 大卡 |

## 3. 数据模型

### coffee 字段（key → 说明 / 校验）

| key | 取值 / 范围 | 说明 |
| --- | --- | --- |
| `variety` | 非空、≤40 字 | 品种名，预设 + 自定义，动态值域只做长度校验 |
| `cupSize` | medium / large / xlarge | 杯型，映射 350 / 450 / 596 ml |
| `temperature` | ice / hot | 温度 |
| `beans` | espresso / dark | 咖啡豆 |
| `concentration` | normal / extra | 浓度 |
| `sugar` | standard / less / lighter / micro / none | 糖度 |
| `cream` | none / added | 奶油 |
| `amountMl` | 1–2000 | 份量，随杯型带出可改 |
| `calories` | 0–3000（选填） | 热量，大卡 |

### tea 字段

| key | 取值 / 范围 | 说明 |
| --- | --- | --- |
| `teaVariety` | 非空、≤40 字 | 茶种，预设 + 自定义 |
| `cups` | 1–20 | 几杯 |
| `amountMl` | 1–5000 | 毫升 |
| `calories` | 0–3000（选填） | 热量，大卡 |

### 兼容旧键

`coffee` 规格的白名单仍保留 `drinkType` / `unit` / `caffeine`，让历史记录与旧备份可解码、详情可展示；新编辑器不再写入这三个键。`DrinkOptionCard` / 编辑器用 `takeIf { it in 枚举 }` 兜底旧值。

## 4. 历史数据迁移

- **纯函数**：`core/model/TeaMigration.kt` 判定 `drinkType=tea` 并转换字段，`convertLegacyTea` 对非旧茶返回 null；`TeaMigrationTest` 覆盖 5 个场景。
  - ml 口径：保留 `amountMl`，丢掉旧三键。
  - cup 口径：旧值当杯数折进 `cups`，`amountMl` 按 `杯数×250ml` 估出。
- **存量库**：`MainViewModel.init` 在 `ensureDefaultTypes()` 之后调 `repository.migrateLegacyTeaRecords()`；`RoomTrackRepository` 用 `getAllRecords()` 逐条筛出旧茶，事务内先确保 tea 父行存在（外键 RESTRICT），再 `updateEvent` + `putEventDetail` 同步改 typeCode。无旧记录时不写库，重复调用安全（幂等）。
- **恢复备份**：`restoreBackup()` 在 payload 层把 coffee + `drinkType=tea` 的事件与详情一起转成 tea，并把老备份里名为「咖啡 / 茶」的 coffee 类型行改名为「咖啡」。
- 未升 Room：`app/schemas/` 仍只有 1/2/3/4.json，`MIGRATION_3_4` 不变。

## 5. 自定义品种

- `SettingsRepository` 用两个 `stringPreferencesKey` 存 JSON 数组，只存用户自加的项，去重 / 裁剪 / 上限 50 个（预设不入库）。
- `MainViewModel` 暴露 `customCoffeeVarieties` / `customTeaVarieties` 两个 StateFlow 与增删方法。
- 编辑器里「＋ 自定义」现场添加并立即选中；设置 →「饮品品种」独立弹层可删。
- 不进备份：换设备 / 重装后自定义清单需重新添加，记录里的品种名字符串本身随备份保留。

## 6. UI 与导航

- 两套编辑器复用 `RecordEditorDialog` 事件壳；咖啡新增 `DrinkOptionCard` 选项卡片（角标 / 描述 / 价格说明，两列排布、`heightIn` 防 200% 字体裁字）。
- `TodayScreen` / `LifeTrackApp` / `RecordRow` / `RecordSummary` / `RecordDetailPresentation` / `LifeTrackTokens` 全部加了 tea 分支；`moduleAction`/`moduleDetail` 的 `else -> onWaterRecord` 兜底不再被 tea 命中（否则会错开水编辑器）。
- 历史页 / 月历 / 统计页数据驱动，tea 类型自动出现在筛选与格子图标里，无需改动。

## 7. 验收结果

- `testDebugUnitTest`：**全绿**（基础 386 项 + 新增 TeaMigrationTest 5 / TeaFieldsTest 3 / DetailSpecTest +2 / BackupTeaDetailTest 4 / CoffeeFieldsTest +3 / RecordDetailPresentationTest +2 / BackupCoffeeDetailTest +1）。
- `assembleDebug`：通过；`lintDebug`：通过（无 error）。
- `compileDebugKotlin`：多次通过，仅一条既有 `DirectionsRun` deprecation 警告。
- **未验证**：真机 / 模拟器交互（本轮无连接设备）、`connectedDebugAndroidTest`、200% 字体与深 / 浅色的实机截图。复原后需在有设备环境补跑。

## 8. 遗留与后续

- 原型所示的「今日热量合计统计卡」未做（本轮只做记录层，统计适配留待后续）。
- 品种默认值表目前写死在 `CoffeeFields` / `TeaFields`，要按用户口味调整直接改这两处。
- 原 A7 确认单（单类型设计）保留为历史记录，不代表当前实现。
