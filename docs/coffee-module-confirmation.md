# A7 咖啡 / 茶模块 · 设计前确认单

> 建立日期：2026-11-09 · 对应台账 `docs/PROGRESS.md` A7 · 状态：**按推荐值开工**
> 前置：`33ef32d`（A6 Room v2 + 饮水试点 + 应用内检查更新）、`1cda585`（并发合并文档校准）。合并后基线：90 项单测全绿、`assembleDebug` 通过。

## 1. 目标与非目标

**目标**：沿用 A6 铺好的通用 `event_detail` 扩展点，新增「咖啡 / 茶」记录类型，覆盖饮品类型、份量、咖啡因标记，做到首页可新增 / 编辑 / 删除、列表可读、备份可往返。

**非目标（本轮不做）**：

- 不改 Room schema（仍 **version 2**），不动 `MIGRATION_1_2` 与 `2.json`。
- 不改备份格式（仍 **schemaVersion 2**），不改 v1 兼容链与 `BackupCodec` DTO。
- 不改历史页、月历、统计页 —— 按类型适配统一归 A11；**这三个页面当前仍只统计排便**。
- 不改导航结构、不加依赖、不改 `track_type` 表结构、不改 `INTERNET` 权限相关代码。
- 不做健康建议、咖啡因摄入量换算或跨记录累计（跨面板聚合归 A11）。

## 2. 现状核对（代码依据，非推测）

| 结论 | 依据 |
| --- | --- |
| 新类型无需 Room 迁移 | `EventDetailEntity` / `EventWithDetails` 已是「事件 + 通用 JSON 详情」单表；`Migrations.kt` 只 CREATE TABLE/INDEX |
| `DEFAULT_TYPES` 是唯一类型注册点 | `RoomTrackRepository.typeFor()` 从 `DEFAULT_TYPES` 查；`ensureDefaultTypes()` 与备份恢复兜底都遍历它 |
| 规格表是唯一校验注册点 | `DetailSpecRegistry.builtIn` 现只含 `water`；`SUPPORTED_DETAIL_TYPES = builtIn.keys`，加一项即生产放行 |
| 备份解码按 typeCode 分派 | `BackupCodec.parseDetail` 对非 `poop` 走 `detail` 通道，逐字段过 `DetailLimits` + `DetailSpec.validators` |
| 首页已按类型分派 | `TodayScreen` 的 `when (record.event.typeCode)` 已有 `poop` / `water` / `else 只读`；入口区按 `trackTypes.enabled` 显隐 |
| 编辑器壳可复用 | `RecordEditorDialog` 提供日期/时间/时区/备注/选择器与 `saveEnabled`，详情作为 `content` 插槽注入 |
| 次数卡口径 | `poopCount = records.count { typeCode == poop }`，饮水不计入；统计页 `StatsAggregator` 只查 `poop` |
| 并发风险已解除 | `33ef32d` 合并双方改动，HEAD 可编译；工作区干净 |

## 3. 设计决策

**D1 · 单类型 `coffee`，名称「咖啡 / 茶」**，不拆成 `coffee` / `tea` 两个 `track_type`。
理由：台账 A7 把「饮品类型」列为**字段**；拆两个类型会让设置页出现两个近乎相同的开关，且 A7/A8 之后每加一种饮品都要动 `DEFAULT_TYPES`。饮品类型作为枚举字段存在 `event_detail` 内，A11 做按类型筛选时仍可按字段聚合。若需独立开关，后续加类型即可，格式层不用动。

**D2 · 四个字段，值统一字符串**

| 字段 key | 取值 | 校验 | 说明 |
| --- | --- | --- | --- |
| `drinkType` | `coffee` / `tea` | 枚举白名单 | 咖啡 / 茶 |
| `amountMl` | `1..2000` 整数字符串 | 范围 + 纯数字 | 份量；与饮水同口径，不做 ml↔杯换算 |
| `unit` | `ml` / `cup` | 枚举白名单 | 只影响显示，不换算数值 |
| `caffeine` | `yes` / `low` / `no` | 枚举白名单 | 含咖啡因 / 低因 / 无咖啡因 |

字段常量与校验函数集中在 `core/model/CoffeeFields.kt`，**编辑器输入校验与备份规格校验共用同一处**，避免两处范围不一致（A6 的经验）。

**D3 · 生产放行走规格表**：`DetailSpecRegistry.builtIn` 增加 `coffee`，`SUPPORTED_DETAIL_TYPES` 自动包含它；未注册字段、越界份量、非法枚举一律 `INVALID_DETAIL_FIELD` 并带记录 ID。不新增 `BackupError` 枚举。

**D4 · 跨面板自洽**：今日次数卡仍只数 `poop`，咖啡记录不计入；统计 / 历史 / 月历也只数 `poop`。因此「新增一杯咖啡后今日排便次数不变」是**预期行为**而非 bug。咖啡记录只出现在今日记录列表（与 A6 饮水一致），历史页不显示，归 A11。

**D5 · 无详情记录仍合法**：编辑器要求份量必填，但恢复路径允许 `coffee` 事件没有 `detail`（与 `water` 一致），列表显示「尚未填写详情」，不假装能编辑出原本没有的数据。

## 4. 文件清单

新增：

- `app/src/main/java/com/example/lifetrack/core/model/CoffeeFields.kt`
- `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeRecordItem.kt`
- `app/src/test/java/com/example/lifetrack/core/model/CoffeeFieldsTest.kt`
- `app/src/test/java/com/example/lifetrack/core/backup/BackupCoffeeDetailTest.kt`
- 本确认单 `docs/coffee-module-confirmation.md`

修改：

- `core/database/DefaultTypes.kt`、`core/backup/DetailSpec.kt`、`feature/home/TodayScreen.kt`、`core/navigation/LifeTrackApp.kt`
- `app/src/test/java/com/example/lifetrack/core/backup/DetailSpecTest.kt`（注册断言）
- `README.md`、`docs/PROGRESS.md`、`.agents/project-profile.md`（如有新雷区）、工作日志

## 5. 验收标准

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿，单测数由 90 增至 ≥ 100。
2. `AppDatabase` 仍 `version = 2`，`app/schemas/` 仍只有 `1.json` + `2.json`，`2.json` 内容不变。
3. 设置页「记录项目」出现三项（排便 / 饮水 / 咖啡·茶），排序箭头三项均可用，启用/停用生效且重启保持。
4. 首页按启用状态显示第三个入口；全部停用时仍显示空态与「去设置」。
5. 新增咖啡记录：选茶 / 300 ml / 无咖啡因 / 备注，保存后今日列表显示正确摘要；今日排便次数不增加。
6. 重新打开该记录，四个字段与时间全部回填；修改份量与饮品类型后保存生效；删除后列表清空。
7. 导出备份 JSON：`schemaVersion` 仍为 2，`coffee` 事件带 `detail.fields` 四字段；恢复后摘要完整回来；把 `amountMl` 改成 `99999`、把 `caffeine` 改成非法值、或塞入未知字段，均明确拒绝且不破坏现有数据。
8. 深 / 浅色、200% 字体、1200×800 宽屏下编辑器与列表项无截断、无重叠、无横向溢出。
9. `git status` 干净（提交后），模拟器分辨率 / 字号 / 主题还原，无遗留后台任务与测试文件。

## 6. 风险

| 编号 | 风险 | 缓解 |
| --- | --- | --- |
| W1 | `BackupWaterDetailTest` 用 `caffeine` 当「water 的未知字段」、用 `coffee` 当「类型不一致」的样例 | 这两条断言针对 `water` 的规格与 typeCode 一致性，注册 `coffee` 规格不会改变其行为；开工后跑全量单测确认 |
| W2 | 设置页新增 UpdateCard 后布局变化，模拟器点击坐标偏移 | 一律用 `uiautomator dump` 取节点 bounds，不靠截图目测 |
| W3 | 三个入口在 200% 字体 / 窄屏下挤压 | 入口仍为纵向 `Column` + 全宽按钮；实测 200% 字体 |
| W4 | 新增类型后 `DEFAULT_TYPES` 顺序影响既有用户排序 | 只在 `ensureDefaultTypes` 用 `insertIgnore`，不覆盖已存的 `enabled` / `sortOrder` |
| W5 | 恢复旧备份（无 coffee 类型）后设置页缺项 | `restoreBackup` 兜底遍历 `DEFAULT_TYPES` 补齐，已有实现，实测覆盖 |

## 7. 待确认口径（推荐值，未勾选即按推荐执行）

| 编号 | 问题 | 推荐值 |
| --- | --- | --- |
| V1 | 一个类型还是两个 | [x] 单类型 `coffee` + `drinkType` 字段 |
| V2 | 份量范围 | [x] `1..2000`，与饮水同口径、不换算 |
| V3 | 咖啡因标记 | [x] 三态 `yes` / `low` / `no`，不做累计 |
| V4 | 历史页是否显示咖啡记录 | [x] 本轮不显示，归 A11 统一适配 |
| V5 | 备份版本 | [x] 保持 v2，不升版本 |
| V6 | 是否新增统计卡 | [x] 不新增，归 A11 |

## 8. 实施记录

待本轮验收后填写。
