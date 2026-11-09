# A 包收尾 + B 包质量门禁 · 设计取舍与实施记录

> 覆盖台账：A8、A11、A12、B1、B2、B4、B9、B11。
> 用户指示：本轮不逐项停下确认，按台账顺序一次做完；仍按「先写口径、再改代码、最后实测」执行。
> 日期：2026-11-09。

## 1. A8 运动模块

### 字段规格

| 字段 | key | 取值 | 校验 |
| --- | --- | --- | --- |
| 运动类型 | `sportType` | `walk` / `run` / `ride` / `swim` / `strength` / `other` | 枚举 |
| 时长 | `durationMin` | 1–1440（分钟） | 整数 + 范围 |
| 强度 | `intensity` | `low` / `medium` / `high` | 枚举 |

- 沿用 A6 的通用详情表：只加 `DEFAULT_SPORT_TYPE`（sortOrder 40）与 `DetailSpecRegistry` 一项，
  `SUPPORTED_DETAIL_TYPES` 自动放行；**Room 仍 version 2、备份仍 schemaVersion 2**。
- 时长是唯一必填项；类型默认「步行」、强度默认「中等」，避免大字号下多一步才能保存。
- 展示超过 60 分钟换算为「1 小时 20 分」；不做热量 / 配速推算，避免引入未经验证的公式。
- 台账只要求类型 / 时长 / 强度，距离与配速留到确有需要时再加——新增字段仍不必升 Room。

### 共用组件

- 把 `CoffeeEditorDialog` 里的私有 `ChipRow` 提升为 `feature/record/RecordChoiceFields.kt` 的
  `ChoiceChipRow`，咖啡与运动共用：常规字号横向滚动，`fontScale > 1.3f` 时两列分行。
- 新增 `feature/record/RecordRow.kt` 统一按 `typeCode` 分派记录卡片，今天页与历史页共用，
  避免「历史里看不到运动」这类口径漂移；未知类型只读展示 + 允许删除。

## 2. A11 跨面板类型口径

### 口径定义

| 面板 | 口径 |
| --- | --- |
| 首页次数卡 | 今日总次数 + 各已启用模块分列次数 |
| 历史列表 | 全部类型（停用模块的历史仍可回看） |
| 月历格子 | 全部类型的每日合计 |
| 统计 | 顶部筛选：全部 / 各类型，默认「全部」 |
| 形态分布卡 | 仅在「排便」口径下展示 |

- 「全部」是四处默认值，因此首页总次数 = 历史条数 = 月历格子合计 = 统计默认总数；
  统计切到单类型只影响统计页自身，不会让首页与历史跟着变。
- 形态分布依赖 `poop_detail` 的强类型字段；在其他口径下分母（总次数）会包含无此字段的类型，
  必然出现「已填写 0 / 共 N 条」的误导，因此直接隐藏。当所选口径为排便时，
  查询也只在 `typeCode == "poop"` 时拉取详情投影，避免空转查询。

### 数据层

- DAO 三处查询改为可空类型过滤：`(:typeCode IS NULL OR typeCode = :typeCode)`，
  历史 / 月历 / 统计共用，不再维护「排便专用」和「全部类型」两套 SQL。
- `observeStatDetails` 保持排便专用（LEFT JOIN `poop_detail`），只在排便口径调用。
- `historyRecords` 的类型从 `PoopRecordRelation` 换成 `BackupRecord`，由 `RecordRow` 分派。

## 3. A12 字段校验收口

- `DetailSpec` 增加 `init` 约束：**声明了字段就必须有校验器**，漏写直接抛错，杜绝「只加 allowedFields
  忘了 validator」导致脏值从备份通道入库。
- 新增 `DetailSpec.isFieldsValid(fields)`：键必须已声明 + 每个值通过对应校验器，替代 `BackupCodec` 里的内联判断。
- `BackupCodec` 收紧：受支持类型若查不到字段规格，直接按 `INVALID_DETAIL_FIELD` 拒绝，
  不再存在「无规格 = 不校验」的路径。
- 新增 `SportFieldsTest`、`BackupSportDetailTest`、`DetailSpecTest` 的规格完备性断言
  （每个内置类型的 `allowedFields` 与 `validators.keys` 必须相等）。

## 4. B1 / B2 迁移测试与仪器测试

- 依赖：`androidTestImplementation("androidx.room:room-testing:2.8.5")` + `androidx.test:runner:1.6.2`。
- `android.sourceSets.androidTest.assets.srcDir("$projectDir/schemas")`：让 `MigrationTestHelper`
  能用 `1.json` / `2.json` 按版本建库。
- `MigrationTest`（2 项）：
  1. `migrate1To2_keepsExistingRecords`：按 1.json 建库 → 写类型 / 事件 / 排便详情 → 跑迁移并校验
     schema identity → 断言数据逐字段不变、`event_detail` 带外键。
  2. `migrate1To2_acceptsGenericDetailAndCascadesDelete`：迁移后可写通用详情，删除事件时级联删除详情。
- `LocalDatabaseTest` 扩到 3 项：新增「按类型过滤查询」与「通用详情读写 + 级联删除」，
  直接覆盖 A11 的三条 SQL。
- 结果：`connectedDebugAndroidTest` 5 项全绿（emulator-5554，API 37）。真机仍未跑。

## 5. B4 字体缩放

- 统计页 `PeriodSwitcher`：固定 38dp → `fontScale > 1.3f` 时 52dp，圆角 / 指示块由高度推导；
  文字加 `maxLines = 1, softWrap = false`。历史页 `ViewSwitcher` 同构修复。
- 统计页类型筛选 chips 由横向滚动改为 `FlowRow`：大字号下自动换行，避免最后一项被裁成半截。
- 200% 字体实测：首页、历史、统计（含 5 个筛选 chip 全部完整）、运动编辑器（6 个类型 chip 三行）
  均无截断 / 重叠；测试后字号与分辨率已还原。

## 6. B9 文档校准

- `.agents/project-profile.md`：`verify_build` / `verify_test` 从「未验证」改为实测命令与日期，
  新增 `verify_lint` / `verify_instrumented`；`related_docs` 补齐 8 个文档；
  §8 删掉「套件没有 Kotlin 语言模块」的过时理由；§9「尚未实现主动导出 / 恢复」改为现状（已实现、仍明文）。
- `.agents/project-profile.md` §9 新增 5 条本轮踩坑（固定高度切换条、schema assets、外键顺序、
  lint 门禁、负向样例命名）。

## 7. B11 CI 门禁

- `release.yml`：发布前增加 `:app:lintDebug`，失败时上传 lint 报告。
- 新增 `ci.yml`：
  - push / PR 到 main：`testDebugUnitTest + lintDebug + assembleDebug`；
  - 仪器测试默认不跑，仅在 **workflow_dispatch** 或 PR 带 `run-instrumented` 标签时启动，
    用 `reactivecircus/android-emulator-runner@v2`（API 36, google_apis, x86_64）执行
    `connectedDebugAndroidTest`。
- 首次接 lint 时修掉唯一 error：`android:windowLightNavigationBar` 是 API 27 属性，
  在 `<item>` 上加 `tools:targetApi="27"`（minSdk 26 上系统会忽略），不复制整份 `values-v27` 主题。

## 8. 验收结果

- `testDebugUnitTest`：**115 项全绿**（103 → 115：SportFieldsTest 3、BackupSportDetailTest 5、
  DetailSpecTest +4）。
- `assembleDebug` / `assembleRelease`：通过。
- `lintDebug`：通过（仅依赖版本提示类 warning）。
- `connectedDebugAndroidTest`：5 项全绿。
- 模拟器实测：运动新增 → 编辑回填 → 首页分列计数 → 历史展示 → 统计切换口径 → 200% 字体无截断。
- 未验证：真机安装与回归；CI 新工作流尚未在 GitHub 上实跑（本地等价命令已全绿）。
