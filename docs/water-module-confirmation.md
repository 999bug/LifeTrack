# 模块扩展第三批（A6 饮水模块试点）设计前确认单

> 建立时间：2026-11-09
> 依据：`docs/PROGRESS.md` 台账 A6；前置 A3/A4/A5 已于 `4a48dc4` 完成
> 状态：本单先落盘，随后按推荐值实施（用户已用「继续」授权开工）

## 1. 现状基线

| 位置 | 现状 |
| --- | --- |
| `AppDatabase` | `@Database(version = 1, exportSchema = true)`，三表；`app/schemas/1.json` 是唯一基线 |
| `DEFAULT_TYPES` | 只有 `poop`；`ensureDefaultTypes()` 用 `insertIgnoreAll` 补齐，不覆盖用户设置 |
| `BackupCodec` | v2，`SUPPORTED_VERSIONS = {1,2}`；`BackupDecodeOptions.Default.supportedDetailTypes` 为空集 |
| `DetailSpecRegistry.builtIn` | 空 map，A5 只留扩展点 |
| `BackupPayload.events` | `List<BackupRecord>`，`RecordDetail.Generic` 已可编解码，但**数据库无处落库** |
| DAO 查询 | 全部带 `WHERE typeCode = 'poop'`，`PoopRecordRelation` 是 Room `@Relation` |
| `MainViewModel.todayRecords` | `StateFlow<List<PoopRecordRelation>>` |
| `TodayScreen` | 只有「快速打卡 / 详细记录」两个入口，`canRecord` 只看 poop 是否启用 |
| `LifeTrackApp` | `editingRecord: PoopRecordRelation?`，只挂 `PoopEditorDialog` |
| 模拟器当前状态 | 安装的是 Room **v1** 包，库里有 4 条记录（含详情），正好可做真实 1→2 升级实测 |

## 2. 目标与非目标

**目标**：把 A5 铺好的 `RecordDetail.Generic` 通道真正落到数据库，用「饮水」验证一次完整闭环 —— 启用、记录、今日可见、编辑、删除、导出备份、恢复备份、Room 升级不丢数据。

**非目标**
- 不改备份 `schemaVersion`（保持 v2，不升 v3）。
- 不改**历史页、月历、统计页**的查询口径：它们仍只统计 `poop`。类型筛选与首页聚合是 A11。
- 不做 A7 咖啡/茶、A8 运动。
- 不做排序 UI 的真实拖动回归（现在有两个类型，本轮会顺带验证箭头可用性）。
- 不做数据库加密（C 包，用户指定最后）。
- 不新增第三方依赖；不引入 `room-testing`（正式 `MigrationTestHelper` 仪器测试归 B1/B2）。

## 3. 关键口径

### W1 通用详情表形态 → 单表 JSON，不做 `water_detail`

```
event_detail(eventId TEXT PK, typeCode TEXT, payload TEXT, FK eventId → track_event.id CASCADE)
             + INDEX(typeCode)
```
`payload` 是 `RecordDetail.Generic.fields` 的 JSON 对象，由 `RecordDetailCodec` 用显式 `MapSerializer` 编解码。

**理由**：A5 的格式层已经是「typeCode + Map<String,String>」，落库对齐这个词条；A7/A8 只需加 `DEFAULT_TYPES` 与 `DetailSpec`，**不再触发 Room 迁移**。若做专用表，每加一个模块都要升 Room，正好踩中台账 R4。

### W2 Room v2 迁移 → 只加表，零数据改写

`MIGRATION_1_2` 执行 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，SQL 与 KSP 生成的 `2.json` 逐字对齐；`AppDatabase.create()` 挂 `.addMigrations(MIGRATION_1_2)`，`2.json` 随代码入库。

**风险与回滚**：这是对已发布 v0.1.0 用户数据库的首次 schema 升级，属不可逆操作。因为只新增表、不改列、不删数据，v1 的数据结构仍是 v2 的子集；但 Room 不允许版本号降级，若必须回退只能卸载重装或手工删表后改版本。验收必须在**带存量数据的设备上覆盖安装**，不能只靠全新安装。

### W3 water 类型与启用状态

`DEFAULT_WATER_TYPE`（code `water` / 名称「饮水」/ category `intake` / sortOrder 20）加入 `DEFAULT_TYPES`。`ensureDefaultTypes()` 的 `IGNORE` 语义保证老用户升级后自动获得该类型且**不覆盖**既有的启用/排序设置。默认 `enabled = true`，停用后首页隐藏入口但已有记录仍可查。

### W4 规格注册与生产放行

`DetailSpecRegistry.builtIn` 注册 `water`：`amountMl` 取 1..5000 的整数、`unit` 只接受 `ml` / `cup`，其余 key 拒绝。
`BackupDecodeOptions.Default.supportedDetailTypes` 从空集改为 `SUPPORTED_DETAIL_TYPES = builtIn.keys`（即 `{water}`）。
**A5 的「明确拒绝」语义不变**：未在集合里的类型仍拒绝并带记录 ID，只是 water 现在合法了。`DetailSpecTest` 里「生产默认不支持任何类型」的断言要相应改写。

### W5 首页范围 → 按启用类型分派，今日列表显示所有类型

- 已启用且有编辑器的类型各给一个入口：poop 保留「快速打卡 / 详细记录」，water 给「记录饮水」。
- `todayRecords` 换成 `List<BackupRecord>`，列表项按 `event.typeCode` 分派渲染：poop 复用 `PoopRecordItem`，water 用新的 `WaterRecordItem`，未知类型用兜底项（只读，不假装能编辑）。
- `LifeTrackApp.editingRecord` 统一为 `BackupRecord?`，历史页的 `PoopRecordRelation` 在导航层转换成 `BackupRecord`；按 typeCode 打开 `PoopEditorDialog` / `WaterEditorDialog`。
- 全部类型停用时，空态提示与「去设置」入口保持 A2 行为。

**理由**：不把记录入口做完整聚合（那是 A11），但保证新类型「记得进去、看得到、改得动、删得掉」，不交付只写库的假功能。

### W6 编辑器与草稿

`WaterEditorDialog` 复用 A3 的 `RecordEditorDialog` 壳，详情插槽为「饮用量（数字）+ 单位 chips」；壳新增 `saveEnabled` 参数，量不合法时禁用保存（poop 默认 true，行为不变）。
新增 `RecordDraft(typeCode, occurredAtEpochMillis, zoneId, note, detail)` 作为通用草稿，`PoopRecordDraft` 保持不变，在导航层转换。
Repository 新增通用 `addRecord / updateRecord`，**保留现有 poop 专用方法不动**，避免动已验证路径。

### W7 备份闭环

- `readBackup()` 改为查全部事件（`EventWithDetails` 带两个 `@Relation`），poop 详情来自 `poop_detail`、water 详情来自 `event_detail`，统一映射成 `BackupRecord`。
- `restoreBackup()` 在事务里同时写 `track_event`、`poop_detail`、`event_detail`；删除事件时详情随外键级联。
- 备份格式仍是 v2，water 详情走既有 `detail` 通道，字节结构不变；v1 旧备份仍可读。

### W8 迁移验证方式

用当前模拟器（Room v1 + 4 条存量记录）**覆盖安装**新 APK，验证：升级后 4 条记录与详情逐项保真、能新增饮水、导出 v2 含 water 详情、恢复该文件后 water 详情回来。
正式 `MigrationTestHelper` 仪器测试与 `connectedDebugAndroidTest` 归 B1/B2，本轮不宣称已完成。

## 4. 文件清单

**新增**
- `core/database/entity/EventDetailEntity.kt`、`core/database/model/EventWithDetails.kt`
- `core/database/Migrations.kt`、`app/schemas/.../2.json`（KSP 生成）
- `core/model/RecordDetailCodec.kt`、`domain/model/RecordDraft.kt`
- `feature/water/WaterEditorDialog.kt`、`feature/water/WaterRecordItem.kt`
- `test/.../core/model/RecordDetailCodecTest.kt`、`test/.../core/backup/BackupWaterDetailTest.kt`

**修改**
- `AppDatabase.kt`（version 2、新 entity、addMigrations）、`DefaultTypes.kt`
- `TrackEventDao.kt`（通用查询与 event_detail 写入）、`TrackRepository.kt`、`RoomTrackRepository.kt`
- `DetailSpec.kt`（注册 water、生产支持集合）、`BackupCodec.kt`（不改格式，只让默认选项引用新集合，若放在 DetailSpec 内则不动）
- `MainViewModel.kt`（todayRecords 类型、通用保存）、`TodayScreen.kt`、`LifeTrackApp.kt`
- `feature/record/RecordEditorDialog.kt`（`saveEnabled`）
- `test/.../core/backup/DetailSpecTest.kt`（生产默认断言改写）
- `README.md`、`docs/PROGRESS.md`、本确认单 §6、`profile §9`、工作日志

**不动**：`HistoryScreen.kt`、`CalendarMonthView.kt`、`StatisticsScreen.kt`、`StatsAggregator.kt`、`BackupCodecTest.kt`、`BackupCodecV2Test.kt` 的格式断言、`PoopEditorDialog.kt`。

## 5. 验收标准

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿，原有 71 项用例不减少。
2. `AppDatabase` 为 version 2，`app/schemas/` 新增 `2.json` 并入库；`MIGRATION_1_2` 的 SQL 与 `2.json` 的 `createSql` / index 一致。
3. 模拟器**覆盖安装**（非全新安装）：升级后原有 4 条记录与排便详情完整，无 Room schema 校验异常。
4. 设置页「记录项目」出现两项，排序箭头可点且顺序持久化；停用饮水后首页隐藏「记录饮水」，已有饮水记录仍在今日列表。
5. 饮水新增 → 今日列表显示「250 ml」与时间 → 编辑回填 → 删除生效 → 重启后仍在。
6. 导出备份 JSON 里 water 事件带 `detail.fields`，恢复后 water 详情逐项保真；v1 旧备份仍可恢复。
7. 坏文件：water 详情 `amountMl` 越界 / 未知字段 / `typeCode` 不一致 → 明确拒绝。
8. 深/浅色、200% 字体、1200×800 宽屏下，首页两个入口与两类列表项不挤压、不截断。
9. 统计页、历史页、月历行为与本轮之前完全一致（只统计排便），并在文档里写明这是 A11 的边界。

## 6. 实施记录

**状态**：A6 代码与模拟器验收全部完成；Room 升 version 2，`2.json` 已生成。

### 落地内容

| 项 | 实际落地 |
| --- | --- |
| Room v2 | 新增 `EventDetailEntity`（`eventId` / `typeCode` / `payload`）、`EventWithDetails`（两个 `@Relation`）、`MIGRATION_1_2`；`AppDatabase` 升 version 2 并挂 `addMigrations`；KSP 生成 `2.json`，其 `createSql` / index 与 Migration SQL 逐字一致 |
| 通用详情落库 | 新增 `RecordDetailCodec`（显式 `MapSerializer`，解析失败返回 null）、`WaterFields`（字段与范围单一来源）、`RecordDraft` + `PoopRecordDraft.toRecordDraft()`；DAO 新增通用查询与 `event_detail` 写入；Repository 新增 `observeRecordsForDate` / `addRecord` / `updateRecord`，快照读写同时处理两类详情 |
| water 放行 | `DEFAULT_TYPES` 增加 `DEFAULT_WATER_TYPE`（category `drink`）；`DetailSpecRegistry.builtIn` 注册 water（amountMl 1..5000、unit ml/cup）；`SUPPORTED_DETAIL_TYPES` 由空集改为 `{water}`，`BackupDecodeOptions.Default` 随之放行 |
| UI | `WaterEditorDialog` 复用通用壳（壳新增 `saveEnabled`）、`WaterRecordItem`；`TodayScreen` 按启用类型给三个入口、今日列表按 typeCode 分派、次数卡只数排便；`LifeTrackApp` 统一 `BackupRecord` 编辑态并按类型分派 dialog；`RecordEditorDialog` 加 `saveEnabled` 不改变 poop 行为 |
| 单测 | 新增 `RecordDetailCodecTest` 5 项、`BackupWaterDetailTest` 6 项；`DetailSpecTest` 改为 6 项（新增内置规格与范围断言）；`BackupCodecV2Test` 把「生产默认拒绝」改为「仍拒绝未注册类型」。全项目 **83 项全绿**（71 → 83） |
| 未改 | `HistoryScreen` / `CalendarMonthView` / `StatisticsScreen` / `StatsAggregator` / `PoopEditorDialog`；备份 `schemaVersion` 仍为 v2，字节格式未变 |

### 验收结果

- 83 项单测全绿、`assembleDebug` 通过（最后一次完整单测运行）；之后仅把 water 的 `category` 从 `intake` 改成已有的 `drink` 映射以消除中英混排，`assembleDebug` 再次通过。
- 模拟器 `emulator-5554` 实测闭环：
  - 设置页「记录项目」显示排便 + 饮水两项、均启用、排序箭头可用、「已启用 2 / 共 2 项」，中文分类显示「饮食 · 事件记录」。
  - 首页出现「记录饮水」；新增 250 ml 保存成功，今日记录显示「09:25 饮水 / 250 ml」，**今日排便次数仍为 0**（与统计口径一致）；编辑回填 250 + ml 高亮 + 09:25，改成 300 保存后列表更新为 300 ml；删除后列表回到空态。
  - 导出 JSON：`schemaVersion: 2`、types 含 poop/water、water 事件带 `detail.fields {amountMl:"300", unit:"ml"}`、`poop: null`；恢复该文件后 300 ml 完整回来（「已恢复 1 条记录」）；`amountMl=99999` 的坏文件被明确拒绝「记录的详情字段不合法或取值超出范围。（记录 <id>）」。
  - 深/浅色、200% 字体（首页三入口 + 编辑器日期时间上下堆叠、保存置灰）、1200×800 宽屏（滚动后三入口与列表项完整）均无截断或溢出。
- 测试文件、截图、临时构建目录已清理；字号 1.0、分辨率 1080×2400、深色模式已还原。

### 相对设计的修正与过程说明

1. **W2 存量数据实测未做**：过程中误执行 `pm clear` 清空了模拟器存量数据；用户随后明确「不需要考虑旧数据」「目前项目处于开发阶段，破坏性更新允许」，因此停止补验（已导出的 v1 工程与临时 APK 均已删除）。改为静态核对 `MIGRATION_1_2` 与 `2.json` 一致 + 全新安装验证 Room v2 能正常建库。**真机存量库 1→2 升级仍未实测**，正式 `MigrationTestHelper` 仪器测试归 B1/B2。
2. **`unit` 不做换算**：chips 只作为显示单位，同一数值在列表、备份、恢复中保持一致，卡面明确写出，避免出现两套数字。
3. **未知类型记录只读**：备份可能带入当前版本没有编辑器的类型，列表给只读卡 + 删除按钮，不假装能编辑。
4. **`category` 用已有映射**：`ModuleSettingsCard.categoryLabel` 已有 `drink → 饮食`，把 water 的 category 从新造的 `intake` 改成 `drink`，比新增映射更收口。

### 并发冲突（影响提交，非功能问题）

本轮收尾时另一会话同时在同一工作区开发「应用内检查更新」，改动了 `MainActivity` / `LifeTrackApplication` / `AndroidManifest.xml` / `SettingsScreen` / `README` / `profile`，并在 `LifeTrackApp` 与 `MainViewModel` 上叠加了 `LaunchedEffect { checkForUpdate(auto = true) }` 与 `updateRepository` 构造参数。其 `core/update/GitHubReleaseCheckerTest.kt` 被放进 main 源集且缺 import，导致全仓 `compileDebugKotlin` 失败。按并发规则未修改、未回退、未 stash 对方文件；本包改动已在「HEAD 干净基线 + 仅叠加本包文件」的临时目录中单独核对，失败原因确认为共享文件混改后 `MainActivity` 与 `MainViewModel.Factory` 签名不一致，属对方进行中的改动。

### 遗留 / 下一包

- A7 咖啡/茶、A8 运动只需加 `DEFAULT_TYPES` 与 `DetailSpec` 注册，**不再触发 Room 迁移**；A11 再把历史/统计/月历按类型适配（本轮它们仍只统计排便）。
- B1 的 `MigrationTestHelper` 现在有 `1.json` / `2.json` 两级基线，可以做真实迁移用例了；B2 的 `connectedDebugAndroidTest` 仍未跑。
- 真机、千条数据帧率未测。

