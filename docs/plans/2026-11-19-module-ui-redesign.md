# 记录模块 UI 统一重设计实现计划

**Goal:** 咖啡页先落地 Compose，并行产出其他模块的高保真原型。
**Spec:** `docs/specs/2026-11-19-module-ui-redesign-design.md`

## Global Constraints

- 注释中文；日志英文；提交信息不含 AI 署名。
- 不改 Room schema、备份格式、业务字段和统计口径。
- 子任务写集必须互斥；原型只写 `docs/ui-redesign-prototypes/`。
- 原型无外部依赖，必须支持浅 / 深色、390 / 768 / 1440 宽、键盘焦点和 reduced motion。
- 每个 Task 完成后更新 `.sdd/2026-11-19-module-ui-redesign/task-*-report.md`。
- 提交前执行 `./gradlew :app:assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug`。

### Task 1: 咖啡 Compose 全屏编辑页

**Files:** Modify `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeEditorDialog.kt` · 可选 Modify `app/src/main/java/com/example/lifetrack/feature/coffee/CoffeeRecordItem.kt`
**Interfaces:** 保持 `CoffeeEditorDialog(record, customVarieties, onAddVariety, onDismiss, onSave)` 签名不变；`RecordDraft` 字段写入口径不变。

- [x] 用全屏 Dialog + 固定保存区替换通用 AlertDialog 壳。
- [x] 添加顶部咖啡摘要、品种多行流式布局、偏好分组、份量/热量折叠区。
- [x] 保留日期、时间、备注、旧记录编辑和自定义品种。
- [x] 编译、单测、lint；无设备时明确标注设备项未验证。

### Task 2: 饮水 / 茶原型

**Files:** Create `docs/ui-redesign-prototypes/water-tea.html`
**Interfaces:** 输出供后续 Compose 使用的色彩 token、组件状态和交互说明。

- [x] 展示饮水与茶的模块切换、主摘要、快捷规格、记录列表。
- [x] 完成 390 / 768 / 1440 布局检查和浅 / 深色切换。

### Task 3: 运动原型

**Files:** Create `docs/ui-redesign-prototypes/sport.html`
**Interfaces:** 输出运动模块的模块色、三类运动选择、时长/强度录入和周节奏卡片。

- [x] 保留现有运动字段，不新增业务字段。
- [x] 完成三档布局检查和交互验证。

### Task 4: 排便 / 经期 / 亲密关系原型

**Files:** Create `docs/ui-redesign-prototypes/body-cycle.html`
**Interfaces:** 输出身体类记录的共享组件与三套模块色变体。

- [x] 排便用暖棕；经期用低饱和砖玫瑰；亲密关系用梅紫。
- [x] 经期与亲密关系必须有明显色相与图标差异，红色不作为错误色。
- [x] 完成三档布局检查和交互验证。

### Task 5: 汇总与后续开发

**Files:** Modify `docs/PROGRESS.md` · Create `.sdd/2026-11-19-module-ui-redesign/progress.md`

- [x] 汇总咖啡构建结果和三个原型入口。
- [x] 用户确认原型后，再为其他模块建立独立 Compose 子任务。

