# Task 2 · 运动 Compose

## 必读

- `docs/specs/2026-11-19-module-ui-compose-design.md`
- `docs/ui-redesign-prototypes/sport.html`
- `docs/sport-subapp-design.md`
- 当前 `feature/sport/`
- 咖啡参考：`feature/coffee/CoffeeEditorDialog.kt`

## 允许修改

- `app/src/main/java/com/example/lifetrack/feature/sport/SportScreen.kt`
- `app/src/main/java/com/example/lifetrack/feature/sport/SportEditorDialog.kt`
- `app/src/main/java/com/example/lifetrack/feature/sport/SportRecordItem.kt`
- `app/src/main/java/com/example/lifetrack/feature/sport/SportVisuals.kt`
- 对应测试目录的新增/修改（如有）

## 硬约束

- 运动字段只有 `sportType`、`durationMin`、`intensity`。
- 保留 yoga/ride/run 选择面与旧类型只读兼容。
- 不改 `LifeTrackApp.kt`、`core/`、Room、备份。
- 不启动浏览器 / chrome-headless-shell。

## 交付

- 统一青绿色模块骨架，保留周摘要、7 天节奏、类型筛选。
- 编辑器改全屏，时长/强度大字号可换行。
- 运行 `./gradlew.bat :app:compileDebugKotlin`。
- 在 `.sdd/2026-11-19-module-ui-compose/task-2-report.md` 汇报。
