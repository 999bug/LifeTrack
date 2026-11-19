# 记录模块 Compose 统一落地计划

**Goal:** 在保持数据与导航不变的前提下，把六个非咖啡模块落到已批准的统一视觉体系。
**Spec:** `docs/specs/2026-11-19-module-ui-compose-design.md`

## Global Constraints

- 注释中文；日志英文；提交信息不含 AI 署名。
- 不改 Room schema、备份格式、数据字段、统计口径和依赖。
- 四个子任务写集互斥；禁止修改 `core/`、导航、全局主题和其他任务目录。
- 不启动 `chrome-headless-shell`；UI 运行时验证由主代理使用 Android 模拟器完成。
- 每个子任务至少通过 `:app:compileDebugKotlin`，并报告未做的运行验证。
- 保存、错误、危险动作继续使用全局语义色。

### Task 1: 饮水 / 茶

**Files:** `feature/water/WaterEditorDialog.kt`、`WaterRecordItem.kt`；`feature/tea/TeaEditorDialog.kt`、`TeaRecordItem.kt`
- [ ] 全屏摘要式编辑页；饮水仅使用 amount/unit，茶仅使用 variety/cups/amount/calories。
- [ ] 快捷份量、自定义茶种、固定保存区、深浅色与 200% 字体适配。
- [ ] 保留函数签名与 RecordDraft 字段口径。

### Task 2: 运动

**Files:** `feature/sport/SportScreen.kt`、`SportEditorDialog.kt`、`SportRecordItem.kt`、`SportVisuals.kt`
- [ ] 周摘要、青绿色图表、三类运动卡片、时长/强度编辑统一到原型视觉。
- [ ] 保留历史类型只读兼容和筛选功能。
- [ ] 编辑页全屏化并保持原签名。

### Task 3: 排便 / 亲密关系

**Files:** `feature/poop/PoopEditorDialog.kt`、`PoopRecordItem.kt`；`feature/intimacy/IntimacyEditorDialog.kt`、`IntimacyRecordItem.kt`、`IntimacyDaySection.kt`
- [ ] 排便暖棕、亲密关系梅紫，保存动作全局绿色。
- [ ] 只使用现有字段；不加入原型中的排便量/费力程度或伴侣信息。
- [ ] 全屏编辑、底部固定保存、200% 字体适配。

### Task 4: 经期

**Files:** `feature/menstruation/PeriodScreen.kt`、`PeriodVisuals.kt`、`PeriodDayForm.kt`、`PeriodCalendarView.kt`、`MenstruationEditorDialog.kt`、`MenstruationRecordItem.kt`
- [ ] 砖玫瑰周期摘要、日历与日表单；红色不承担错误语义。
- [ ] 保留经期起止修正、自动延续、撤销语义、敏感信息与 200% 字体适配。
- [ ] 不修改 Room 或周期推算纯函数。

### Task 5: 集成、模拟器与发布

- [x] 合并四个写集，运行 full build/test/lint。
- [x] 模拟器走查浅色、深色、200% 字体和所有模块主路径。
- [x] 更新 README / PROGRESS / VersionHistory。
- [ ] 自动提交、push、触发 Release workflow。
- [ ] 验证 GitHub Release、Gitee 镜像和产物哈希。

