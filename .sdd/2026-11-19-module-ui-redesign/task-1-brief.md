# Task 1 Brief · 咖啡 Compose 全屏编辑页

## 目标

将 `CoffeeEditorDialog` 从通用 AlertDialog 表单改为全屏咖啡记录页，视觉对齐 2026-11-19 已确认的「咖啡点单笺」原型。

## 必读

- `docs/specs/2026-11-19-module-ui-redesign-design.md`
- `docs/coffee-module-confirmation.md`
- `docs/drink-split-confirmation.md`
- `docs/ui-redesign.md` §2 的全局 token 与字体缩放约束
- `app/src/main/java/com/example/lifetrack/core/model/CoffeeFields.kt`
- `app/src/main/java/com/example/lifetrack/feature/record/RecordEditorDialog.kt`
- 原型：`C:\Users\SERVER\.codex\visualizations\2026\11\19\01a1f1c2-7226-7640-9fba-324d95e68acc\coffee-record-prototype.html`

## 写集

- Modify `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeEditorDialog.kt`
- 可选 Modify `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeRecordItem.kt`
- 不得改 Room、备份格式、CoffeeFields 字段定义和导航签名。

## 交互要求

- 全屏 Dialog，顶部关闭 / 标题 / 日期 / 时间，底部固定保存。
- 实时摘要展示品种、杯型、温度、糖度、毫升、热量、浓缩份数。
- 品种使用 FlowRow 多行展示，自定义品种就地输入。
- 杯型、温度、豆子、浓度、糖度、奶油按支持区域分组。
- 份量与热量默认折叠，可展开编辑。
- 保留备注、编辑历史记录、字段校验和 `RecordDraft` 输出口径。
- 200% 字体下不裁字、不重叠；保存按钮始终可触达。

## 验证

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- 无模拟器时明确标注未做真机 / 截图验证。
