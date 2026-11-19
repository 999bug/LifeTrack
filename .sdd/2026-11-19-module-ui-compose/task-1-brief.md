# Task 1 · 饮水 / 茶 Compose

## 必读

- `docs/specs/2026-11-19-module-ui-compose-design.md`
- `docs/plans/2026-11-19-module-ui-compose.md`
- `docs/ui-redesign-prototypes/water-tea.html`
- 当前 `feature/water/`、`feature/tea/`
- 咖啡参考：`feature/coffee/CoffeeEditorDialog.kt`

## 允许修改

- `app/src/main/java/com/example/lifetrack/feature/water/WaterEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/water/WaterRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/tea/TeaEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/tea/TeaRecordItem.kt`
- 对应测试目录的新增/修改（如有）

## 硬约束

- 饮水字段只有 `amountMl`、`unit`。
- 茶字段只有 `teaVariety`、`cups`、`amountMl`、`calories`。
- 原型中的温度、口感、浓淡不要写入模型或 UI。
- 保持 `WaterEditorDialog` / `TeaEditorDialog` 签名。
- 不改 `LifeTrackApp.kt`、`core/`、Room、备份。
- 不启动任何浏览器 / chrome-headless-shell。

## 交付

- 全屏 Dialog，顶部模块色摘要，快捷份量，茶种 FlowRow + 自定义，固定保存区。
- 浅深色与大字号适配；列表项使用同一模块色语汇。
- 运行 `./gradlew.bat :app:compileDebugKotlin`。
- 在 `.sdd/2026-11-19-module-ui-compose/task-1-report.md` 汇报改动、编译结果、未验证项。
