# Task 3 报告 · 排便 / 亲密关系 Compose UI

## 完成范围

- 排便编辑器改为暖棕全屏分组表单，包含模块色摘要、发生时间、用时、形态与感受、备注及底部固定保存。
- 亲密关系编辑器改为梅紫全屏私密记录页，保留措施宫格、时间滚轮、备注与固定保存。
- 排便记录卡、亲密关系记录卡、经期页亲密关系区同步使用模块色、统一圆角、边框与摘要层级。
- 未新增排便量、费力程度、伴侣信息或其他数据字段；所有展示数据均来自现有模型。

## 改动文件

- `app/src/main/java/com/example/lifetrack/feature/poop/PoopEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/poop/PoopRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/intimacy/IntimacyEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/intimacy/IntimacyRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/intimacy/IntimacyDaySection.kt`

未修改 `core/`、导航、Room、备份、依赖、共同设计文档或其他模块。

## 约束核对

- 排便模块色为暖棕，亲密关系模块色为梅紫。
- 保存按钮统一使用全局 `MaterialTheme.colorScheme.primary`。
- `PoopEditorDialog`、`IntimacyEditorDialog`、两个记录项和 `IntimacyDaySection` 的公开函数签名保持不变。
- 亲密关系隐私文案原样保留：
  - `单选，可不选。只记录措施本身，不记录伴侣或任何可识别身份的信息。`
  - `建议在「设置 → 安全」开启应用锁；本月记录只保存在本机。`
- 亲密关系列表继续保持 L1 隐私口径：文字区仅暴露「已记录一条亲密关系记录」，不拼措施、备注或其他字段。
- 大字号下措施选项常规为 5 列、大字号降为 2 列并允许两行文案；排便选择使用 `FlowRow` 自动换行；备注输入使用 `minLines`，未写固定文字高度。
- 未启动 `chrome-headless-shell`、浏览器或浏览器自动化，未执行 `git commit`。

## 验证

执行：

```text
.\gradlew.bat :app:compileDebugKotlin
```

结果：

```text
BUILD SUCCESSFUL in 1m 47s
8 actionable tasks: 2 executed, 6 up-to-date
```

编译仅剩两条既有 / 并行模块警告，均不在本任务改动文件内：

- `LifeTrackTokens.kt` 使用已弃用的 `Icons.Filled.DirectionsRun`。
- `SportEditorDialog.kt` 存在冗余转换调用。

静态检查：

- `git diff --check`：通过，无空白错误。
- 本任务文件未发现 `TODO`、`FIXME`、调试输出或浏览器自动化引用。
- 修改后 Kotlin 编译无本任务文件报错。

## 未验证

- 未在 Android 模拟器实际点击排便 / 亲密关系主路径，也未截图验证浅色、深色和 200% 字体效果。
- 未做真机触控、TalkBack 全量朗读、系统字体缩放下的运行时视觉验收。
- 未运行单元测试、仪器测试或 lint；本任务为 Compose 展示层改造，未新增业务逻辑与测试。
