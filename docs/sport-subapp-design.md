# 运动子应用 · 设计文档（v0.7 已实现）

> 建立日期：2026-11-13 · 状态：**代码已落地并自测通过（编译 + 单测）；未上真机走查，未发版**
> 配套可交互原型：`docs/sport-subapp-prototype.html`（深浅色 / 200% 字体 / 空态 / 记录弹层，可直接浏览器打开）
> 决策来源：2026-11-13 四个方向由用户拍板——节奏型、共用「时长 + 强度」、整卡进子应用、界面只留瑜伽 / 骑行 / 跑步
> 代码基线：`main` v0.6.11 之后；Room 仍 version 3，备份正文 `SCHEMA_VERSION = 2`、加密容器 `ENCRYPTED_SCHEMA_VERSION = 3` 均未变

---

## 1. 目标、边界与非目标

**目标**：在已有的 `sport` 记录类型之上，做一个**隐藏底导的运动子应用**（`composable("sport")`），回答三个问题：

1. 这周动了几次、合计多久？
2. 瑜伽 / 骑行 / 跑步各占多少？
3. 断了几天？

**它不是第二个记录表单**。记录弹层（`SportEditorDialog`）早就存在，再套一层表单没有价值；子应用补的是首页与统计页都看不到的「节奏」这一层。

| 项目硬约束 | 本模块的处理 |
| --- | --- |
| Local First、不升 Room | 不新增表、不新增字段、不升 Room 版本 |
| 不改备份格式 | 详情仍是 `RecordDetail.Generic`，键仍为 `sportType` / `durationMin` / `intensity` |
| 不做热量 / 配速推算（既有文案承诺） | 不新增距离、配速、卡路里；只做次数、分钟数、天数 |
| 颜色只做识别不做装饰 | 三类用同色相三档明度 + 图标 + 文字图例，不引入新色相 |
| 无障碍 | 200% 字体全部堆叠、对比度沿用 WCAG AA 校验过的模块色、点击区 ≥ 44dp |

**非目标**：训练计划、目标打卡与连续天数排行榜、距离 / 配速 / 心率 / 卡路里、地图轨迹、第三方（Strava / 佳明等）同步、社交分享。

---

## 2. 现状核对（动手前的事实）

| 位置 | 事实 | 对本设计的影响 |
| --- | --- | --- |
| `core/model/SportFields.kt` | 原 `SPORTS = walk/run/ride/swim/strength/other`，无 `yoga` | 需新增 `yoga`；其余四项从界面下线 |
| `core/backup/DetailSpec.kt:69` | 校验器 `{ it in SportFields.SPORTS }`，`BackupCodec` 解析详情时调 `isFieldsValid()`，失败返回 `INVALID_DETAIL_FIELD` | **直接删枚举**会让任何含旧类型记录的备份整份恢复失败（备份恢复是整体替换事务） |
| `feature/sport/SportEditorDialog.kt` | 默认值取 `SPORTS.first()`；`ChoiceChipRow(SPORTS, …)` | 旧记录打开时三个 chip 全不选中，用户一保存就静默改值 |
| `feature/home/TodayScreen.kt` | 模块卡整卡点击 = 打开编辑器，动作槽同义 | 运动卡要改成「整卡进子应用 + 动作槽记一次」 |
| `core/navigation/LifeTrackApp.kt` | 已有 `cycle` 二级页先例；编辑器按 `typeCode` 分发 | 照同构加 `sport` 路由与 `initialSport` 预选 |
| 经期子应用（`PeriodScreen`） | 隐藏底导、首页卡进入、派生数据不落库、洞察折叠在页尾 | 交互与代码骨架直接对齐，学习成本最低 |

---

## 3. 已拍板的四个决策

| # | 决策点 | 结论 | 后果 |
| --- | --- | --- | --- |
| 1 | 信息架构 | **A 节奏型**：本周概览 → 三类快记 → 最近记录 → 简报 | 与统计页不重复；全部数字可从现有事件派生 |
| 2 | 录入字段 | **共用「时长 + 强度」**，不加距离 | 不改 `DetailSpec` 字段面，不推翻「不做配速」的承诺 |
| 3 | 首页入口 | **整卡进子应用**，动作槽保留「记一次」 | 高频的记录路径仍然一步到位 |
| 4 | 类型范围 | **界面只留三类**：瑜伽 / 骑行 / 跑步 | 选择器只剩三项；数据层另留只读兼容白名单（见 §4） |

---

## 4. 数据兼容口径（本设计唯一有数据风险的地方）

界面上「删除」了步行 / 游泳 / 力量 / 其他，但数据层不能真的删：

```kotlin
val SPORTS = listOf("yoga", "ride", "run")            // 选择面：选择器 / 快记卡 / 筛选
val LEGACY_SPORTS = listOf("walk", "swim", "strength", "other")  // 只读，不进任何选择器
val ALL_SPORT_VALUES = SPORTS + LEGACY_SPORTS         // 备份校验与标签映射的完整白名单
```

三条护栏，缺一条就会出问题：

1. **备份校验**：`DetailSpec` 改用 `ALL_SPORT_VALUES`。只用 `SPORTS` 的话，含 `walk` 的旧备份会整份恢复失败，而不是丢那几条。
2. **标签映射**：`sportLabel()` 的旧值分支只增不减。删掉会让历史记录在列表、详情、统计里一起退化成「运动」。
3. **编辑器**：打开旧类型记录时不预选任何 chip，显式提示「这条记录的类型是「步行」，该类型已从界面下线」，并要求重新选择后才可保存；直接关掉不改动原值。

口径统一为一句：**界面按「删除」呈现，数据层只留一条只读兼容白名单。**

---

## 5. 首屏结构

| 区块 | 内容 | 数据来源 |
| --- | --- | --- |
| ① 页头 | 返回 + 「运动」+ 「近 90 天」 | — |
| ② 本周概览卡 | 大数字「本周 N 次」、合计时长、距上次；三色占比条 + 图例；7 天节奏格 | `SportStats.week()` |
| ③ 记一次 | 三张类型卡（图标 / 名称 / 上次 X 分钟），点卡开弹层并预选类型 | `SportStats.lastOf()` |
| ④ 最近记录 | 「全部 / 瑜伽 / 骑行 / 跑步」筛选 + 记录行（类型 · 时长 / 日期 · 强度 · 备注），行内编辑 / 删除 | `SportStats.sessions()` |
| ⑤ 本期简报（折叠） | 本周合计时长 / 本周平均每次 / 近 4 周次数 / 近 4 周最常见 | `SportStats.brief()` |
| ⑥ 页脚 | 「只用于回看节奏，不构成训练建议」 | — |

**周口径**：周一起算（`TemporalAdjusters.previousOrSame(MONDAY)`），7 格固定「一…日」，未来的格子用更淡的底槽，今天加一圈描边。

**占比条口径**：按本周分钟数归一；旧类型合并成一段中性色，不冒充三类之一，图例里单独一行「其他旧类型 N 分」。

**取数窗口**：近 90 天（`SportStats.WINDOW_DAYS`），一个 `flatMapLatest` 查询，ViewModel 与界面共用 `windowStart()`，避免两处口径漂移。列表一次最多画 30 条，超出提示去「历史」页查。

---

## 6. 视觉规范

| 项 | 取值 |
| --- | --- |
| 页面内边距 / 卡片间距 / 卡片内边距 | `LifeTrackTokens.Spacing.page 20` / `gap 10` / `card 16` |
| 圆角 | `LifeTrackTokens.Shapes.card` = 16dp |
| 运动模块色 | 浅色 `#4C7A57`、深色 `#9CC7A6`（`LifeTrackTokens.ModuleColors`） |
| 三类区分 | 同色相三档：`sportShade()` 用 `lerp(moduleColor, background, 0 / 0.34 / 0.58)`；恒定配图标 + 文字图例 |
| 图标 | 瑜伽 `SelfImprovement`、骑行 `AutoMirrored.DirectionsBike`、跑步 `AutoMirrored.DirectionsRun` |
| 卡片描边 | `outlineVariant.copy(alpha = 0.6f)`，与首页模块卡一致 |

**200% 字体（fontScale > 1.3）**：三张类型卡由横排改竖排行（图标 + 名称 + 上次时长 + 「记一次」）、记录行的编辑 / 删除挪到下一行右对齐、简报四项由两列改单列，节奏格保持 7 列只加高。

**读屏**：占比条整体给一句「本周占比：瑜伽 30 分钟，骑行 60 分钟…」；节奏格给一句「本周节奏：一 45 分钟，二 无记录…」；类型卡与折叠头分别给 `contentDescription` 与 `stateDescription`（已展开 / 已收起）。

---

## 7. 验收清单

- [x] 首页运动卡整卡进入子应用，右下角动作槽仍直接开记录弹层
- [x] 点「瑜伽」开弹层时类型已预选，只需填时长即可保存
- [x] 新建记录必须显式选类型（不再用 `SPORTS.first()` 兜底）
- [x] 打开 `walk` 旧记录时三个 chip 均不选中 + 红色提示 + 保存禁用
- [x] 含 `walk / swim / strength / other` 的备份仍能完整恢复（单测覆盖四个值）
- [x] 旧类型历史记录仍显示原标签（步行 / 游泳 / 力量 / 其他），并在占比条里单列为「其他旧类型」
- [x] 本周 0 条时概览卡退化为空态：次数 0、底槽占比条、7 格空节奏，不出现假分段
- [x] 200% 字体下三类卡、记录行、简报均无截断或重叠（原型已截图核对；真机待走查）
- [x] `./gradlew :app:compileDebugKotlin` 通过
- [x] `./gradlew :app:testDebugUnitTest` 全绿
- [ ] 真机（API 37）走查：点卡预选、保存后概览即时更新、返回首页今日记录同步、深色 / 200% / 360dp
- [ ] 发版时补 `VersionHistory.kt` 条目

---

## 8. 落地清单

**新增**

| 文件 | 职责 |
| --- | --- |
| `feature/sport/SportStats.kt` | 纯函数派生层：`SportSession` / `SportDay` / `SportWeek` / `SportBrief`，不落库 |
| `feature/sport/SportScreen.kt` | 子应用首屏（六块结构、筛选状态、30 条上限） |
| `feature/sport/SportVisuals.kt` | 概览卡、占比条、7 天节奏、快记卡、记录行、简报折叠 |
| `app/src/test/.../feature/sport/SportStatsTest.kt` | 周口径 / 旧类型 / 空态 / 简报 / 距上次 共 10 项 |
| `docs/sport-subapp-design.md`（本文） | 设计说明 |
| `docs/sport-subapp-prototype.html` | 可交互原型 |

**修改**

| 文件 | 改动 |
| --- | --- |
| `core/model/SportFields.kt` | `SPORTS` 收敛三类 + `yoga`；新增 `LEGACY_SPORTS` / `ALL_SPORT_VALUES` / `isLegacy` / `isSelectable` / `minutesLabel` |
| `core/backup/DetailSpec.kt` | 运动 `sportType` 校验改用 `ALL_SPORT_VALUES` |
| `feature/sport/SportEditorDialog.kt` | 新增 `initialSport`；新建必须选类型；旧类型显式提示 + 保存禁用 |
| `feature/home/MainViewModel.kt` | 新增 `sportRecords`（近 90 天，仅 `sport`） |
| `feature/home/TodayScreen.kt` | 新增 `onOpenSport`；运动卡整卡进子应用、动作槽记一次 |
| `core/navigation/LifeTrackApp.kt` | 新增 `sport` 路由；`openEditor` 支持 `sport` 预选 |
| `app/src/test/.../SportFieldsTest.kt` | 断言改为三类 + 旧值标签护栏 |
| `app/src/test/.../BackupSportDetailTest.kt` | 新增「旧类型仍可恢复」用例 |

---

## 9. 风险与后续

1. **未上真机**：排版按原型与 200% 规则写，触摸手感、系统返回键、深色真机观感仍需走查。
2. **未验证发版**：`VersionHistory.kt` 条目留到发版时按用户可见变化补写。
3. **旧类型不可再选**：用户若确实需要步行 / 游泳，需要后续把 `LEGACY_SPORTS` 里某一项重新提回 `SPORTS`——一处改动，不涉数据。
4. **90 天窗口**：更早的记录在子应用里看不到，需要走「历史」页；如果以后要看年度节奏，再扩窗口或加按月聚合。
