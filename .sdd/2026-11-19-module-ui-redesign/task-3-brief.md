# Task 3 Brief · 运动模块原型

## 目标

创建 `docs/ui-redesign-prototypes/sport.html`，重做运动记录模块的高保真交互原型。

## 必读

- `docs/ui-redesign.md` §1–§4。
- `docs/sport-subapp-design.md` 与 `docs/sport-subapp-prototype.html`。
- `docs/specs/2026-11-19-module-ui-redesign-design.md`。
- 现有运动字段与类型定义：`core/model/SportFields.kt`、`feature/sport/`。

## 交付要求

- 单文件 HTML/CSS/JS，无网络依赖。
- 沿用森林绿基础体系，运动模块使用更鲜明的青绿强调；不得新增业务字段。
- 页面至少包含：本周摘要、三类运动选择、时长/强度录入、最近运动列表、固定保存动作。
- 模块内可使用运动色作为顶部摘要卡和图表主色，但保存、错误等系统动作色保持全局一致。
- 交互必须真实可点：运动类型、时长滑杆或步进、强度、浅深色切换、保存提示。
- 390×844、768 宽、1440×1000 三档无横向溢出；200% 字体不裁字。
- 代码注释中文；无 TODO、无假外部资源、无 AI 署名。

## 报告

完成后创建 `.sdd/2026-11-19-module-ui-redesign/task-3-report.md`，列出改动文件、token、三档验证结果和未验证项。
