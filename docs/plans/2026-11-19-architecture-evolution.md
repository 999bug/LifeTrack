# 架构演进与可维护性优化实现计划

**Goal:** 在不改数据库、备份格式和核心业务口径的前提下，按独立子任务降低状态层耦合、修复日期一致性并补齐自动化验证。
**Spec:** `docs/specs/2026-11-19-architecture-evolution-design.md`

## Global Constraints

- 注释中文；日志与异常消息英文；提交信息不含 AI 署名。
- 每个 Task 单独提交；提交前跑对应单测、lint 和 assembleDebug。
- 不改 Room schema、备份 schema、统计口径和发布签名链路。
- 不升级框架大版本；不引入 Hilt / Koin；不拆 Gradle feature 模块。
- `feature` 不直接依赖数据库实体是本轮方向，但按 Task 逐批迁移，禁止一次性大改。
- 所有新纯逻辑必须可在 JVM 单测中验证。

### Task 1: 统一“今天”业务日期源

**Files:** Create `core/time/TodaySource.kt`、`test/.../TodaySourceTest.kt`；Modify `feature/home/MainViewModel.kt`、`feature/home/TodayScreen.kt`、`feature/history/HistoryScreen.kt`、`feature/statistics/StatisticsScreen.kt`、`feature/menstruation/PeriodScreen.kt`、`feature/sport/SportScreen.kt`、`docs/PROGRESS.md`
**Interfaces:** Produces（后续 ViewModel 拆分消费）`TodaySource.today: StateFlow<LocalDate>`、`TodaySource.refresh()`、`TodaySource.millisUntilNextDay()`

- [x] 用可注入 `Clock` 的 `TodaySource` 替换 `MainViewModel` 内部可变日期。
- [x] `ON_START` 和跨午夜时刷新；首页、历史、统计、经期、运动统一消费 `viewModel.today`。
- [x] 覆盖跨午夜刷新和下一日等待时长单测。
- [x] 跑 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`。

### Task 2: 抽出备份状态机

**Files:** Create `feature/settings/backup/BackupController.kt`、对应单测；Modify `feature/settings/SettingsScreen.kt`、`MainViewModel.kt`
**Interfaces:** Produces（Settings 页面消费）`BackupController.state: StateFlow<BackupUiState>` 与导出 / 恢复 / 换口令动作。

- [x] 把口令、确认、错误、冷却和 SAF 回调后的编排移出 Composable。
- [x] 保持备份文件格式、错误文案和危险动作确认不变。
- [x] 覆盖导出、恢复、换口令的成功与拒绝路径。
- [x] 跑单测、lint、构建；设置页做模拟器主路径走查。

### Task 3: 拆分 `MainViewModel`

**Files:** Create `feature/{home,history,statistics,settings,menstruation}/...ViewModel.kt`；Modify `core/navigation/LifeTrackApp.kt`、`MainActivity.kt`、对应页面和测试
**Interfaces:** Produces 各页面独立状态流；消费共享 `TodaySource`、repository 和 controllers。

- [x] 3A：拆出 `AppLockViewModel` 与 `UpdateViewModel`，共享 `AppContainer` 的 `TodaySource`，设置页和启动链完成接线。
- [x] 3B：拆出 `PeriodViewModel`，迁移经期流、周期快照与写入逻辑。
- [x] 3C：拆出首页、历史、统计 ViewModel。
- [x] 3D：删除剩余页面对 `feature.home.MainViewModel` 的反向依赖。
- [x] 保持导航参数、页面行为和状态恢复不变。
- [x] 删除非 home feature 对 `feature.home.MainViewModel` 的依赖。
- [x] 补每屏核心状态流测试（转入 Task 8）。

### Task 4: 统一写操作错误反馈

**Files:** Create `core/ui/ActionState.kt`、对应测试；Modify 各 ViewModel / controller 与页面 Snackbar 展示
**Interfaces:** Produces 统一 `ActionState` 或 `Result` 映射，供所有写操作消费。

- [x] 覆盖保存、编辑、删除、类型排序、品种管理和备份恢复。
- [x] 取消静默吞错；保留 `CancellationException` 语义。
- [x] 增加失败路径单测。

### Task 5: 拆分仓储接口与领域边界

**Files:** Modify `domain/repository/TrackRepository.kt`、`data/repository/RoomTrackRepository.kt`、`AppContainer.kt` 及消费方
**Interfaces:** Produces `RecordRepository`、`StatisticsRepository`、`TypeCatalogRepository`、`BackupRepository`、`DatabaseMaintenanceRepository`；可暂时由同一 Room 实现组合。

- [x] 先拆统计、类型目录、备份和维护接口，再拆记录 CRUD。
- [x] 逐步用领域模型替代 feature 对 Room entity / projection 的直接依赖。
- [x] 保持 Room schema 与查询语义不变。

### Task 6: 记录类型注册表

**Files:** Create `core/model/RecordTypeDefinition.kt`、`core/ui/RecordTypeUiRegistry.kt`；Modify `DetailSpecRegistry`、`LifeTrackApp.kt`、`LifeTrackTokens.kt`、`RecordDetailPresentation.kt`、`SensitiveTypes.kt`、`TodayScreen.kt`、`RecordSummary.kt`
**Interfaces:** Produces 单一类型描述与 UI 贡献注册；新增类型时由完整性测试检查所有注册项。

- [x] 收敛类型元数据、图标、颜色、详情和评估入口。
- [x] 用测试保证每个内置类型都有字段规格、UI 描述和敏感等级。
- [x] 不改变现有类型码和备份兼容性。

### Task 7: 历史记录 Keyset 分页

**Files:** Modify `TrackEventDao.kt`、`TrackRepository` 新接口、`HistoryViewModel`、`HistoryScreen.kt`；Create paging 单测
**Interfaces:** Produces 固定页大小、基于时间戳与 ID 的游标分页。

- [x] 历史默认列表每次最多查固定条数。
- [x] 筛选范围和月历查询保持原语义。
- [x] 覆盖重复时间戳、跨日期分组和空页。

### Task 8: 测试与 CI 门禁

**Files:** Modify `app/build.gradle.kts`、`app/src/test`、`app/src/androidTest`、`.github/workflows/ci.yml`
**Interfaces:** Produces ViewModel Flow 测试、Compose 主路径测试和稳定的设备测试触发。

- [x] 加入可用的协程测试依赖，补 `TodaySource` / ViewModel / 页面测试。
- [x] 仪器测试至少 nightly 或 release 前强制执行。
- [x] 失败时上传报告。

### Task 9: 构建与导航收尾

**Files:** Create `gradle/libs.versions.toml`；Modify 根 / app Gradle、导航路由、baseline profile 文档与验证记录
**Interfaces:** Produces 集中依赖版本、类型安全路由和 profile 验证路径。

- [x] 迁移插件与依赖版本到 version catalog。
- [x] 评估并迁移类型安全路由，保持深链和返回栈行为一致。
- [ ] 在 arm 设备上生成并比较 baseline profile（当前只有 x86_64 模拟器，保留为外部硬件验收项）。
