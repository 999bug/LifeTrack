# Task 2 报告 · 运动 Compose UI 重构

## 改动文件

- `app/src/main/java/com/example/lifetrack/feature/sport/SportScreen.kt`
  - 统一运动页页头、青绿模块识别色、本周概览、三类快记与最近记录骨架。
  - 类型筛选改为可换行 FlowRow，保留全部 / 瑜伽 / 骑行 / 跑步。
  - 200% 字体时快记卡与历史记录改为纵向布局。
- `app/src/main/java/com/example/lifetrack/feature/sport/SportVisuals.kt`
  - 新增运动专用青绿 palette 与同色相类型差异。
  - 本周概览改为深色青绿 Hero，保留周摘要、占比条和可点击 7 天节奏。
  - 历史记录操作收进更多菜单，保留详情、编辑、删除入口。
- `app/src/main/java/com/example/lifetrack/feature/sport/SportEditorDialog.kt`
  - 从通用 AlertDialog 壳改为全屏运动录入页。
  - 保留日期、时间、时区、备注及原函数签名。
  - 类型卡、时长输入/步进/滑杆/预设、强度卡均支持大字号纵向降级。
  - 固定底部保存区，摘要实时跟随类型、时长、强度。
- `app/src/main/java/com/example/lifetrack/feature/sport/SportRecordItem.kt`
  - 历史页运动记录行同步青绿视觉、类型图标、时长与更多菜单。

## 字段与兼容性

- 保存详情仍只有 `sportType`、`durationMin`、`intensity` 三个字段。
- 选择面仍只提供 `yoga` / `ride` / `run`。
- 打开 `walk` / `swim` / `strength` / `other` 旧记录时不预选类型，显示下线提示并禁用保存；直接关闭不改原值。
- 未修改 `core/`、导航、Room、备份、依赖或其他模块。

## 编译验证

命令：

```powershell
./gradlew.bat :app:compileDebugKotlin
```

最终结果：

```text
BUILD SUCCESSFUL in 5s
8 actionable tasks: 2 executed, 6 up-to-date
```

首次编译曾报 `matchParentSize` 多余导入和 `Instant` 缺导入；修正后重新编译通过。

## 未验证

- 未在 Android 模拟器/真机走查实际排版、触控与保存落库。
- 未验证浅色 / 深色、360dp 窄屏、200% 系统字体下的实际截图。
- 未执行 TalkBack、键盘导航与全量 WCAG 审计。
- 非目标内的单元测试、lint、assemble 未在本任务中执行。
- 按要求未启动 `chrome-headless-shell`、Chrome 或任何浏览器自动化，也未执行 `git commit`。