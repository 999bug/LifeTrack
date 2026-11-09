# 统计增强包 · 设计前确认单

- 版本：v0.1（待确认）
- 日期：2026-11-09
- 范围：README「当前尚未实现 / 下一阶段清单」中的「月历视图、形态分布图、时间段分析」
- 状态：**未动手写代码**，勾选后开工
- 上一包：记录体验包（`2b30495`）；本包之后的排序为安全加固包

---

## 0. 一句话目标

在不动 Room schema、不加依赖、不改备份格式的前提下，补齐三块可视化：**历史页月历视图**、**统计页形态分布**、**统计页完整时段分布**，并与现有 Hero 总数、指标卡保持数学自洽。

---

## 1. 现状基线（已核对代码，不是推测）

| 项 | 现状 | 出处 |
| --- | --- | --- |
| 历史页 | `LazyColumn` 按 `localDate` 倒序分组列表；有范围筛选与「加载更早 30 天」，无日历 | `HistoryScreen.kt:57-119` |
| 历史查询 | `historyExtraPages` / `historyFilter` 驱动，默认最近 30 天，筛选区间可任意 | `MainViewModel.kt:45-67` |
| 统计页 | 周/月/年胶囊 + Pager、Hero、Canvas 柱图、2×2 指标、洞察卡、免责 | `StatisticsScreen.kt:78-282` |
| 统计聚合 | 纯函数 `StatsAggregator.build(window, today, dailyCounts, timestamps)`，输出总数/环比/日均/峰值/连续/常见时段/柱图 | `StatsAggregator.kt:48-112` |
| 常见时段 | 滑动 2 小时窗口 `hourly[h] + hourly[h+1]` 取 argmax，并列时优先起始小时有记录的窗口 | `StatsAggregator.kt:170-199` |
| 按天计数 | `observeDailyCounts(start, end)` 已支持任意区间 `GROUP BY localDate` | `TrackEventDao.kt:52-57` |
| 时刻投影 | `observeEventTimestamps(start, end)` 只取 `occurredAtEpochMillis` + `zoneId` | `TrackEventDao.kt:75-83` |
| 详情字段 | `bristolType` / `amountCode` / `effortCode` / `urgency` / `abdominalDiscomfort` / `incompleteEvacuation`，**全部可空** | `PoopDetailEntity.kt:18-28` |
| 详情参与统计 | 否。统计链路从未读 `poop_detail` | `TrackRepository.kt:10-26` |
| 数据库 | `version = 1`，`exportSchema = true`，`app/schemas/` 只有 `1.json` | `AppDatabase.kt:13-17` |
| 主题 | 深/浅两套薄荷绿，22 个 M3 角色已补齐；统计页另有 `StatsColors` 暗夜仪表盘色板 | `Theme.kt:13-59`、`StatisticsScreen.kt:583-620` |
| 依赖 | 只有 Compose BOM + material3 + Room + DataStore + serialization，**无图表库** | `app/build.gradle.kts` |
| 现有单测 | 33 项；`StatsAggregatorTest` 11 项含「07:30 单条 → 07:00–09:00」时区用例 | `StatsAggregatorTest.kt:118-139` |

**关键结论：三块功能都只需要新增查询与纯函数，不需要迁移、不需要新表、不需要第三方库。**

---

## 2. 非目标（明确不做）

- 不做年热力图、不做点击柱下钻详情、不做导出图片（统计改版确认单里已延后的 v1.1 项）。
- 不做健康判断：全站禁用「正常 / 异常 / 健康度」类措辞。
- 不改 Room schema（version 保持 1）、不加 Gradle 依赖、不改备份 JSON 格式、不改底部导航结构。
- 不做加密、应用锁、模块管理页、新记录类型——它们属于另外的包。
- 不引入渐变、玻璃拟态、装饰性 emoji、外链图片、第二种强调色。
- 不把「未填写」当成 0 型或异常值参与分布。

---

## 3. 必须先拍板的口径

### D1 · 月历放在哪

| 方案 | 说明 | 取舍 |
| --- | --- | --- |
| **历史页加「列表 / 月历」切换（推荐）** | 月历是浏览记录的方式，点某天直接复用已有 `setHistoryFilter(day, day)` | 与现有筛选链路自洽；需处理两个视图的状态切换 |
| 统计页新增月历卡片 | 和周/月/年柱图放一起 | 与月视图柱图信息重复，且统计页已经很长 |
| 新建独立 tab | 导航从 4 个变 5 个 | 改动面最大，收益不明确 |

### D2 · 形态分布的分母（最容易出「数字对不上」的地方）

`poop_detail` 六个字段均可空，快速打卡的记录**没有任何详情**。因此：

- **推荐**：每个字段独立用自己的「已填写条数」作分母，卡片标题显式写「已填写 N / 共 M 条」，并在卡片内说明「未填写不计入」。
- 不推荐：把「未填写」画成一个分箱。它会让 Bristol 分布凭空多出第 8 根柱，且与 Hero 总数口径混淆。

自洽约束：`sum(bristolCounts) == filledBristol <= total`，`filledBristol` 与 `total` 的关系必须在 UI 上可见，不允许用户自行推断。

### D3 · 时段分布与「常见时段」如何自洽（重要）

现有 `commonBandStartHour` 是**滑动 2 小时窗口**（0–1、1–2、…、22–23 共 23 个候选），单测锁定了「Asia/Tokyo 07:30 → 07:00–09:00」。

- **推荐（不改动算法）**：时段分布画 **24 根逐小时柱**（00–23），并把 `hourly: IntArray` 提升为 `PeriodStats` 的公开字段；UI 高亮 `commonBandStartHour` 对应的相邻两根柱，标注如「07:00–09:00」。自洽式：`argmax(hourly[h] + hourly[h+1]) == commonBandStartHour`，由单测直接断言。现有 11 项统计单测一条都不用改。
- 备选：改成固定 12 个 2 小时桶（00–02 … 22–24）。柱图更矮更整齐，但**会改变现有单测预期**（07:30 会落到 06:00–08:00 桶），属于口径变更，需要单独批准。

### D4 · 分布卡的数据窗口

- **推荐**：跟随当前周/月/年窗口（含翻页后的历史周期）。用户翻到上个月，分布也应是上个月的。
- 备选：固定近 30 天。实现更简单，但和顶部周期标签矛盾。

---

## 4. 信息架构

### 4.1 历史页（新增月历视图）

```
① 标题行      「历史记录」+ 筛选按钮
② 视图切换    列表 / 月历（复用现有 PeriodSwitcher 的胶囊风格）
③ 区间信息    沿用「区间 · 共 N 条」+ 清除筛选
④ 月历卡      月份标题 + 左右翻月（禁止翻到未来）
              周一为首的 7 列网格，每格显示日期 + 当日次数
              0 次 = 中性底；1/2/3+ 次 = 主色四级透明度；今天 = 1dp 描边
              前后补白格不可点击、不参与计数
⑤ 列表        点某天 → 筛选该日并切回列表（推荐）；月历视图下仍显示筛选后的记录
⑥ 加载更早    仅列表视图、非筛选态显示
```

### 4.2 统计页（在指标网格之后、洞察卡之前插入两张卡）

```
⑦ 时段分布卡   24 根逐小时柱，高亮常见 2 小时窗口；空周期显示占位文案
               卡片底部：「共 N 条记录计入，时区按记录保存时的 zoneId」
⑧ 形态分布卡   Bristol 1–7 型横向/纵向分布；下方三行小分布：排便量 1–3、费力程度 1–3
               身体感受三项（便意急迫 / 腹部不适 / 排不净感）以「有 N 条 / 已填写 M 条」文字条呈现
               每个分组标题带「已填写 N / 共 M 条」
⑨ 洞察 + 免责  保持原位，文案不改
```

---

## 5. 技术方案与影响面

### 5.1 数据层（只加查询，不改 schema）

| 新增 | 作用 |
| --- | --- |
| `core/database/model/StatDetailProjection.kt` | 轻投影：`bristolType` / `amountCode` / `effortCode` / 三个 `Boolean?` |
| `TrackEventDao.observeStatDetails(start, end)` | `track_event LEFT JOIN poop_detail`，按 `localDate` 区间过滤；无 detail 的行字段全为 NULL |
| `TrackRepository` / `RoomTrackRepository` | 各加 1 个转发方法 |
| `MainViewModel.statistics` | `combine` 由两路变三路（dailyCounts + timestamps + details），查询区间沿用现有「最远上一周期 → 最远本周期」，不新增 Flow |
| `MainViewModel.calendarMonth` / `calendarCounts` | 月份绝对偏移（0 = 本月），`calendarCounts` 复用已有 `observeDailyCounts(monthStart, monthEnd)` |

`LEFT JOIN` 后个人数据量级（一年数百到数千条）全表扫可接受；`track_event.localDate` 无索引，本包不加索引——加索引要改 schema，收益暂不值得。

### 5.2 纯函数层（新增单测的主要落点）

| 新增 | 作用 |
| --- | --- |
| `core/model/CalendarMonth.kt` | `CalendarMonth.of(anchor, offset)`：周一为首的 6×7 格子、`leadingBlank` / `trailingBlank`、月初月末、`label`；`offset > 0` 拒绝 |
| `feature/statistics/DetailDistribution.kt` | `from(details, total)`：`bristolCounts[7]` / `amountCounts[3]` / `effortCounts[3]` / 三个感受计数 + 各自 `filled` 数；非法值（如 bristol 9）忽略而不是崩溃 |
| `StatsAggregator.build` | 增加 `details` 参数；`PeriodStats` 增加 `hourlyCounts: List<Int>` 与 `distribution: DetailDistribution` |

### 5.3 UI 层

- `feature/history/CalendarMonthView.kt`：新增 Composable，7 列 `Row`/`Column` 网格或单次 Canvas 绘制；补白格用 `Modifier.semantics` 标为不可用。
- `feature/history/HistoryScreen.kt`：加视图切换状态、月历卡、点击日期回调。
- `feature/statistics/DistributionCards.kt`：新增两张卡，复用 `StatsColors` / `CardShape` / `MetricShape`；柱图沿用单个 `Animatable<Float>` 驱动，禁止每根柱各持一个 `animateFloatAsState`。
- `feature/statistics/StatisticsScreen.kt`：`StatsPage` 插入两张卡。
- **不改**：Theme.kt、导航、备份、实体类、Gradle 依赖。

### 5.4 兼容与边界

| 项 | 处理 |
| --- | --- |
| 200% 字体 | 月历 7 列在 360dp 窄屏每格约 48dp，日期数字改为 `maxLines=1` + 按 fontScale 降字号；统计页沿用现有 `fontScale > 1.3f` 分支 |
| 横屏 / 平板宽屏 | 月历格高度固定 44dp，宽度按 `weight(1f)` 自适应，不写死 dp |
| 读屏 | 月历格 `contentDescription = "11月9日，2 次"`；分布卡 `contentDescription` 给出汇总句 |
| 时区 | 时段柱用记录自身的 `zoneId` 还原小时，不用 SQLite `localtime`（现有逻辑已如此） |
| 空周期 | 分布卡显示「这个周期还没有记录」，不画一排 0 柱 |
| 动画 | 系统「移除动画」时跳过入场动画（沿用统计页现有约定） |

---

## 6. 文件清单（精确到路径）

**Create**

- `docs/statistics-enhancement-confirmation.md`（本文件）
- `app/src/main/java/com/example/lifetrack/core/model/CalendarMonth.kt`
- `app/src/main/java/com/example/lifetrack/core/database/model/StatDetailProjection.kt`
- `app/src/main/java/com/example/lifetrack/feature/statistics/DetailDistribution.kt`
- `app/src/main/java/com/example/lifetrack/feature/history/CalendarMonthView.kt`
- `app/src/main/java/com/example/lifetrack/feature/statistics/DistributionCards.kt`
- `app/src/test/java/com/example/lifetrack/core/model/CalendarMonthTest.kt`
- `app/src/test/java/com/example/lifetrack/feature/statistics/DetailDistributionTest.kt`

**Modify**

- `core/database/dao/TrackEventDao.kt`（+1 查询）
- `domain/repository/TrackRepository.kt`、`data/repository/RoomTrackRepository.kt`（+1 方法各）
- `feature/home/MainViewModel.kt`（月历状态 + 统计第三路数据）
- `feature/statistics/StatsAggregator.kt`（+ `hourlyCounts` + `distribution`）
- `feature/statistics/StatisticsScreen.kt`（+ 2 张卡）
- `feature/history/HistoryScreen.kt`（+ 视图切换 + 月历）
- `app/src/test/java/com/example/lifetrack/feature/statistics/StatsAggregatorTest.kt`（只增用例，不改既有用例）
- `README.md`（「当前已实现」补三块；清单移除对应项，保留未做的）

**Not touched**：`AppDatabase.kt`、`PoopDetailEntity.kt`、`Theme.kt`、`LifeTrackApp.kt`、`core/backup/*`、`app/build.gradle.kts`、`app/schemas/`。

---

## 7. 验收标准

- [ ] `./gradlew :app:testDebugUnitTest` 全绿；新增用例覆盖：周一为首 / 跨年 12 月 / 闰年 2 月 / 补白格 / 禁止翻到未来 / 空值不计入分母 / 非法字段忽略 / 分布合计 = 已填写数 / `hourly` 与 `commonBand` 自洽
- [ ] `./gradlew :app:assembleDebug` 通过，未新增任何依赖、schema 仍为 version 1
- [ ] **跨面板自洽**：Hero 总数 == 柱图各柱之和；Bristol 分布合计 == 卡片标注的已填写数 <= Hero 总数；常见时段 == 24 柱中相邻两柱之和的最大值
- [ ] 月历翻到上个月，格子次数与统计页翻到上月的月柱一致
- [ ] 点击月历某天 → 列表只显示该日记录；清除筛选恢复最近 30 天
- [ ] 无任何详情的「快速打卡」记录：分布卡显示「已填写 0 / 共 N 条」，不崩溃、不画 0 型柱
- [ ] 模拟器实测：深/浅色各一张、200% 字体一张、360dp 窄屏与平板宽屏各一张
- [ ] 读屏能听到月历格与分布卡的汇总描述

---

## 8. 风险

| 编号 | 风险 | 应对 |
| --- | --- | --- |
| R1 | 月历的「月」与统计月视图的「月」翻页状态独立，用户可能以为联动 | 月历明确放在「记录」tab，卡内独立显示月份；不声称联动 |
| R2 | 详情字段全可空，分母口径不统一会出现「分布合计 5 但总数 12」的观感矛盾 | 每张卡显式标注「已填写 N / 共 M 条」，并写「未填写不计入」 |
| R3 | 滑动 2 小时窗口与固定桶混淆 | 采用 §3 D3 推荐方案，柱图按逐小时、高亮按滑动窗口，单测锁死等式 |
| R4 | 200% 字体 + 360dp 下月历 7 列挤压 | 降字号 + 固定行高 + `maxLines=1`，实测截图验收 |
| R5 | 统计查询区间最长覆盖 2 年，新增 LEFT JOIN 可能拖慢切页 | 单条 Flow 复用既有区间；若实测卡顿再考虑索引（需单独批准改 schema） |
| R6 | 统计页继续变长 | 两张卡可折叠或置于指标网格之后；v1 先不折叠，实测滚动手感再定 |

---

## 9. 待确认清单（勾选后开工）

- [ ] **Q1** 月历归属：历史页「列表 / 月历」切换（推荐） / 统计页新增卡 / 独立 tab
- [ ] **Q2** 点击月历某天：筛选该日并切回列表（推荐） / 只高亮不筛选 / 弹当天摘要
- [ ] **Q3** 翻月范围：可回看全部历史、禁止翻到未来（推荐） / 限最近 12 个月
- [ ] **Q4** 分布字段：Bristol + 排便量 + 费力程度 + 三项身体感受（推荐） / 只做 Bristol
- [ ] **Q5** 空值分母：每字段独立「已填写 N / 共 M 条」并显式标注（推荐） / 未填写算作一个分箱
- [ ] **Q6** 时段口径：24 根小时柱 + 高亮现有滑动窗口、不改单测（推荐） / 改固定 12 桶（需批准口径变更）
- [ ] **Q7** 分布数据窗口：跟随当前周/月/年周期（推荐） / 固定近 30 天
- [ ] **Q8** 视觉：沿用暗夜仪表盘单色薄荷绿（推荐） / 另定
- [ ] **Q9** 范围：按第 2 节非目标执行，不夹带其它清单项（推荐） / 顺带做热力图

---

## 10. 实施记录

待开工后填写。
