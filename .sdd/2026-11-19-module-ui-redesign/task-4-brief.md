# Task 4 Brief · 排便 / 经期 / 亲密关系原型

## 目标

创建 `docs/ui-redesign-prototypes/body-cycle.html`，用统一骨架展示三种身体类记录的模块色变体。

## 必读

- `docs/ui-redesign.md` §1–§4。
- `docs/menstruation-module-design.md` 的视觉约束、周期信息架构和隐私边界。
- `docs/intimacy-module-confirmation.md`。
- 现有 `feature/poop/`、`feature/menstruation/`、`feature/intimacy/`。
- `docs/specs/2026-11-19-module-ui-redesign-design.md`。

## 交付要求

- 单文件 HTML/CSS/JS，无网络依赖。
- 顶部切换「排便 / 经期 / 亲密关系」，展示三种页面状态。
- 排便用暖棕；经期用低饱和砖玫瑰 / 灰粉 / 暖酒红；亲密关系用梅紫。经期与亲密关系必须通过色相、图标和文案区分。
- 经期红色只做识别与状态强调，不作为错误、删除、危险按钮色；保存动作继续使用全局绿色。
- 经期页至少展示周期摘要、日历或日状态、经量/症状/日记入口；亲密关系页至少展示日期、防护方式、备注和已记录列表；排便页至少展示时间、形态、用时和备注。
- 交互必须真实可点：模块切换、关键选项、浅深色切换、保存提示。
- 390×844、768 宽、1440×1000 三档无横向溢出；200% 字体不裁字。
- 代码注释中文；无 TODO、无假外部资源、无 AI 署名。

## 报告

完成后创建 `.sdd/2026-11-19-module-ui-redesign/task-4-report.md`，列出改动文件、三套 token、三档验证结果和未验证项。
