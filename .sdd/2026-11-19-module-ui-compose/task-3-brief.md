# Task 3 · 排便 / 亲密关系 Compose

## 必读

- `docs/specs/2026-11-19-module-ui-compose-design.md`
- `docs/ui-redesign-prototypes/body-cycle.html`
- `docs/intimacy-module-confirmation.md`
- 当前 `feature/poop/`、`feature/intimacy/`
- 咖啡参考：`feature/coffee/CoffeeEditorDialog.kt`

## 允许修改

- `feature/poop/PoopEditorDialog.kt`
- `feature/poop/PoopRecordItem.kt`
- `feature/intimacy/IntimacyEditorDialog.kt`
- `feature/intimacy/IntimacyRecordItem.kt`
- `feature/intimacy/IntimacyDaySection.kt`
- 对应测试目录的新增/修改（如有）

## 硬约束

- 排便暖棕；亲密关系梅紫；保存动作使用全局 primary。
- 不新增原型里的排便量、费力程度、伴侣信息等不存在字段。
- 保持所有编辑器函数签名与隐私文案。
- 不改 `LifeTrackApp.kt`、`core/`、Room、备份。
- 不启动浏览器 / chrome-headless-shell。

## 交付

- 全屏编辑器、模块色摘要、现有字段分组、底部固定保存。
- 大字号下选项和备注不被裁切。
- 运行 `./gradlew.bat :app:compileDebugKotlin`。
- 在 `.sdd/2026-11-19-module-ui-compose/task-3-report.md` 汇报。
