# Task 1 报告 · 饮水 / 茶 Compose UI 重构

## 状态

- 代码改造：完成。
- 指定编译：已执行，但整体任务未通过；失败点位于其他并行写集，不在本任务四个文件。
- 浏览器自动化：未启动，符合 brief 要求。
- Git commit：未执行，符合 brief 要求。

## 改动文件

- `app/src/main/java/com/example/lifetrack/feature/water/WaterEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/water/WaterRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/tea/TeaEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/tea/TeaRecordItem.kt`

## 实现内容

### 饮水

- 改为全屏 Dialog，保留 `WaterEditorDialog(record, onDismiss, onSave)` 签名。
- 增加清澈蓝摘要区、快捷份量、数量步进与固定保存区。
- 深色模式使用独立蓝灰 token；大字号时顶部日期/时间、摘要统计和保存区改为纵向布局。
- 仅写入既有字段 `amountMl`、`unit`。
- 快捷份量与步进在 `ml` 下使用毫升档位，在 `cup` 下使用杯数档位。
- 列表项使用同一蓝色语汇，并在大字号下把编辑/删除动作移到下一行。

### 茶

- 改为与饮水一致的全屏摘要骨架，保留 `TeaEditorDialog(record, customVarieties, onAddVariety, onDismiss, onSave)` 签名。
- 增加低饱和茶绿摘要区、快捷杯量、茶种 FlowRow、自定义茶种、杯数/毫升步进、热量输入和固定保存区。
- 深色模式与大字号均有布局降级；保存按钮继续使用全局主题主色。
- 仅写入既有字段 `teaVariety`、`cups`、`amountMl`、`calories`。
- 列表项使用同一茶绿色语汇，并在大字号下把编辑/删除动作移到下一行。

## 验证

执行命令：

```powershell
./gradlew.bat :app:compileDebugKotlin
```

结果：`BUILD FAILED`，最终编译诊断如下：

- `feature/menstruation/PeriodVisuals.kt:66`：`Unresolved reference 'luminance' on receiver of type 'Color'`
- `feature/sport/SportVisuals.kt:798`：`Unresolved reference 'Instant'`

编译器没有报告本任务四个文件中的错误。由于其他并行写集的修改正处于未完成状态，无法在本任务范围内获得整包绿色编译。

静态检查：

- `git diff --check` 通过。
- 四个文件大括号配对正常。
- 未发现 `TODO`、`FIXME`、`println(`。
- 公开编辑器函数签名未改变。
- 饮水字段引用仅覆盖 `amountMl`、`unit`。
- 茶字段引用仅覆盖 `teaVariety`、`cups`、`amountMl`、`calories`。

## 未验证

- 整包 Kotlin 编译绿色：被其他并行任务的编译错误阻塞。
- Android 模拟器实际点击、数据落盘、返回与取消行为。
- 浅色/深色、360dp 窄屏、200% 系统字号的真实截图与视觉走查。
- TalkBack 完整流程、Compose UI 自动化测试。

## 文件写入范围

本任务仅修改上述四个 Compose 文件，并新增本报告；未修改 core、导航、Room、备份、依赖、咖啡页或其他任务目录。
