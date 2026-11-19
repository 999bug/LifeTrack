# Task 2 Brief · 饮水 / 茶模块原型

## 目标

创建 `docs/ui-redesign-prototypes/water-tea.html`，作为饮水与茶记录模块的单文件高保真交互原型。

## 必读

- `docs/ui-redesign.md` §1–§4，沿用「林间日志」基础 token 与三档视觉重量。
- `docs/specs/2026-11-19-module-ui-redesign-design.md`。
- 现有 `app/src/main/java/com/example/lifetrack/feature/water/WaterEditorDialog.kt` 与 `feature/tea/TeaEditorDialog.kt` 的字段和信息架构。
- 可参考 `docs/ui-redesign-prototype.html`，但不要复制其中的参考品牌素材。

## 交付要求

- 单文件 HTML/CSS/JS，无网络依赖。
- 顶部可切换「饮水 / 茶」，用一个页面展示两种模块色变体。
- 饮水：蓝色系；茶：低饱和茶绿色系。基础背景、卡片、字阶和保存按钮全局一致。
- 至少展示：实时摘要、快捷份量、温度/口味等相关字段、今日记录列表、固定保存动作。
- 交互必须真实可点：切换模块、选择规格、切换浅深色、保存提示。
- 390×844、768 宽、1440×1000 三档无横向溢出；字体 200% 时选项换行不裁字。
- 代码注释中文；无 TODO、无假外部资源、无 AI 署名。

## 报告

完成后创建 `.sdd/2026-11-19-module-ui-redesign/task-2-report.md`，列出：
- 改动文件
- 使用的色值和 token
- 三档布局与交互验证结果
- 未验证项
