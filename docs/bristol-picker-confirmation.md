# 布里斯托图示选择器 · 落地确认单

> 日期：2026-11-11。状态：已实现并验证。
> 设计原型：`docs/bristol-picker-prototype.html`（v0，已提交 `1831f92`）。
> 批准记录：原型评审后用户「开搞」（2026-11-11）授权落地 Compose 代码。

## 1. 背景与范围

现状 `PoopEditorDialog` 里布里斯托只有「1 型…7 型」七个横向滚动的数字 chip，用户不看对照表无法判断自己属于哪一型。本包把这一块换成「照图选」的图示选择器，其余编辑器字段保持原样。

原型右侧列了四项待拍板决策，用户未逐项回复即批准开工，按最小侵入原则取以下默认值：

| 决策 | 本包取值 | 说明 |
| --- | --- | --- |
| A 显式「跳过」卡 | 保留 | 默认选中，与其它字段「未记录」chip 口径一致 |
| B 列表 / 详情追加形态短描述 | **暂不做** | 会动到既有文案与单测；需要时另起一包 |
| C 七型对照入口 | 常驻字段标题行 | 原型里从底部挪到标题行，避免被滚动区裁掉 |
| D 图形落地方式 | Compose Canvas 自绘 | 不新增 drawable 资源、不加依赖、自动跟随主题 |

## 2. 非目标

- 不动 `poop_detail` 表结构、`bristolType: Int?` 可空语义、备份 1–7 范围校验、统计形态分布「未填写单独计分母」口径。
- 不动 `PoopRecordItem` 列表摘要与 `RecordDetailPresentation` 详情文案，因此保存后仍显示「布里斯托 4 型」。
- 不做医学诊断、不新增网络能力、不升级依赖、不改 Room 版本。

## 3. 改动文件

| 文件 | 变更 |
| --- | --- |
| `feature/poop/BristolTypes.kt` | 新增。1–7 型短标签 / 分组 / 形态描述，纯 Kotlin，供 JVM 单测覆盖 |
| `feature/poop/BristolArt.kt` | 新增。Canvas 绘制七种形态，等比居中，颜色跟随主题 |
| `feature/poop/BristolPicker.kt` | 新增。4 列网格（大字号 2 列）、选中描述条、跳过卡、含水量轴、对照入口 |
| `feature/poop/BristolGuideDialog.kt` | 新增。三段分组的七型对照弹层，可滚动 |
| `feature/poop/PoopEditorDialog.kt` | 修改。数字 chip 行替换为 `BristolTypePicker`，新增 `showGuide` 状态；六个字段与草稿结构不变 |
| `feature/poop/BristolCatalogTest.kt`（test） | 新增。6 项纯 JVM 单测：序号连续、文案非空、分组轴、区间无重叠、越界返回 null |
| `docs/bristol-picker-confirmation.md` | 新增。本确认单 |

## 4. 设计规格与实现约束

- **颜色只做识别**：形态图用排便模块识别色（`LifeTrackTokens.ModuleColors.of`）；选中态用 M3 `primaryContainer` + 2dp `primary` 描边 + 勾选圆点 + 描述条，四重信号不只靠颜色。分组用「偏干端 / 中间段 / 偏稀端」轴向词，不贴诊断标签。
- **大字号**：`fontScale > 1.3` 时 4 列改 2 列、标题行上下堆叠、轴文案收成一行；卡片用 `heightIn(min = 92.dp)` 而非固定 `height`，避免 200% 下裁字（profile §9 既有教训）。
- **读屏**：每卡用 `selectable(selected, role = Role.RadioButton)`，uiautomator 可读到 `checked=true`；`contentDescription` 是一句完整描述并带「已选中」，图形对读屏隐藏。
- **状态**：`onSelect` 仍只产出 `Int?`；再点已选卡片回到 `null`，跳过卡同义。`BristolCatalog.of` 越界返回 null。

## 5. 验证结果

**隔离验证**：本包开发期间工作区有另一会话正在改「应用内检查更新」相关文件（README / PROGRESS / profile / `core/update/` 等），按 profile §9 的并发教训，用 `git archive HEAD` 导出干净基线到系统临时目录、只叠加本包 6 个源文件后跑门禁：

- `./gradlew :app:assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug` 均 BUILD SUCCESSFUL。
- 单测 **34 suites / 242 tests，0 failures / 0 errors / 0 skipped**；新增 `BristolCatalogTest` 6 项全过。
- 无新增 warning：初版 `Path.quadraticBezierTo` 触发弃用告警，已改为 `quadraticTo` 后复跑干净。既有的 `Icons.Filled.DirectionsRun` 弃用告警属 `LifeTrackTokens.kt`，不在本包范围。

**装机实测**（emulator-5554，隔离基线 APK，debug 覆盖安装）：主路径 15 项断言全 PASS——首页「详细」入口、编辑器打开、7 张卡片、跳过卡、点 4 型后描述条与选中态、读屏 `checked=true` 与「已选中」、跳过卡取消选中、再点取消回未记录、跳过卡恢复选中、七型对照弹层、关闭弹层回编辑器、保存后编辑器关闭与新记录可见。

**截图留证**（浅色 / 选中态 / 深色 / 对照弹层 / 保存后 / 200% 字号 / 360dp 窄屏，共 7 张，存本机临时目录随交付附上）：
- 深色主题图形与文字对比度正常，无黑底黑字。
- 200% 字号网格变 2 列，卡片文字完整无截断；内容区需滚动才能看全七型，属 `AlertDialog` 既有 `heightIn(max = 560.dp)` 约束。
- 360dp 窄屏（density 420，`wm size 945x2100`）4 列网格无挤压、无横向溢出。
- 设备已还原：`wm size reset`、`font_scale 1.0`、应用主题回到浅色。

## 6. 未验证 / 已知现象

- **TalkBack 真机朗读未实测**：用 uiautomator 控件树验证了 `checked=true` 与带「已选中」的 `contentDescription`，未接真机读屏逐句听。
- **既有问题（本包未改）**：常规字号下日期字段末字被裁（通用壳 `RecordEditorDialog` 里两个 `PickerField` 并排、宽度不足），200% 字号下因上下堆叠反而完整。
- 模拟器上保留一条验证用记录（04:40、布里斯托 4 型），未删除，避免误删同屏其它测试数据。
- 未跑 `connectedDebugAndroidTest`：本包未碰数据库与迁移。

## 7. 回滚

数据层零改动，回滚只需把 `PoopEditorDialog.kt` 恢复为 HEAD 版本、删除四个新增 kt 文件与测试；已保存的 `bristolType` 值（1–7 或 null）无需迁移，旧 UI 仍能读。
