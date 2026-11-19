# Task 4 · 经期 Compose

## 必读

- `docs/specs/2026-11-19-module-ui-compose-design.md`
- `docs/menstruation-module-design.md`
- `docs/ui-redesign-prototypes/body-cycle.html`
- 当前 `feature/menstruation/`
- 咖啡参考：`feature/coffee/CoffeeEditorDialog.kt`

## 允许修改

- `feature/menstruation/PeriodScreen.kt`
- `feature/menstruation/PeriodVisuals.kt`
- `feature/menstruation/PeriodDayForm.kt`
- `feature/menstruation/PeriodCalendarView.kt`
- `feature/menstruation/MenstruationEditorDialog.kt`
- `feature/menstruation/MenstruationRecordItem.kt`
- 对应测试目录的新增/修改（如有）

## 硬约束

- 砖玫瑰只做周期识别、日历状态和选中态；保存/错误/删除沿用全局语义色。
- 保留经期开始/结束修正、自动延续、隐私展示与现有字段。
- 不改周期纯函数、Room、备份、`LifeTrackApp.kt` 或 `core/`。
- 不启动浏览器 / chrome-headless-shell。

## 交付

- 温和私密的周期摘要、日历、日记录表单与历史记录视觉。
- 200% 字体下日历图例、日表单、操作按钮自动换行。
- 运行 `./gradlew.bat :app:compileDebugKotlin`。
- 在 `.sdd/2026-11-19-module-ui-compose/task-4-report.md` 汇报。
