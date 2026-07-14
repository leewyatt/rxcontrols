# RXSpeedDial 实施报告

## 完成状态

- PR1–PR6 已全部落地：`RXFloatingActionButton`、`RXSpeedDial`、action model、事件、Skin、动画、键盘/a11y、Demo、Showcase、AppShowcase、ThemeGallery、三主题样式均已实现。
- headless 测试已通过；最终验证命令见“测试结果”。
- samples 模块可编译、可测试；新增 `AppShowcaseSmokeTest` 覆盖 `main.fxml` 加载、Speed Dial 页面实例化和样例 CSS 资源。
- 最终跨 PR 只读复审已完成；成立问题已修复并补测试。
- 无外部阻塞。

## 文件变更

新增生产代码：
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/RXFloatingActionButton.java`：FAB 原子控件。
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/skins/RXFloatingActionButtonSkin.java`：FAB Skin。
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/RXSpeedDial.java`：Speed Dial 控件 API、状态机和 CSS metadata。
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/RXSpeedDialAction.java`：action model。
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/event/RXSpeedDialEvent.java`：事件族。
- `rxcontrols/src/main/java/io/github/leewyatt/rxcontrols/skins/RXSpeedDialSkin.java`：布局、动画、交互、键盘和 a11y。

新增/修改测试：
- `RXFloatingActionButtonTest`、`RXSpeedDialTest`、`RXSpeedDialA11yTest`：核心行为、事件、CSS、dispose、键盘/a11y。
- `rxcontrols-samples/src/test/java/io/github/leewyatt/rxcontrols/samples/AppShowcaseSmokeTest.java`：AppShowcase smoke。
- `rxcontrols-samples/pom.xml`：samples 测试依赖和单 fork surefire 配置。

新增/修改样例：
- `RXSpeedDialDemo`、`RXSpeedDialShowcase` 及对应 CSS。
- `ThemeGalleryCards` 增加 FAB / SpeedDial 卡片。
- `MainController`、`main.fxml` 接入 Speed Dial 页面。

修改主题：
- `rx-controls.css`：新增 `.rx-fab` / `.rx-speed-dial` 根和子结构样式。
- `rx-controls-dark.css`、`rx-controls-atlantafx.css`：三主题 token 清单登记新根。

新增交付文档：
- `rxspeeddial-implementation-report_zh.md`：本报告和真机验证清单。

删除文件：无。

## 核心实现

- FAB API：`RXFloatingActionButton extends RXButton`，提供 `Size { SMALL, STANDARD, LARGE }` styleable 属性 `-rx-fab-size`，构造器锁定 min/max 为 pref，圆形用 `50%`。
- SpeedDial API：`RXSpeedDial extends Control`，公开 actions list、`showing` 只读属性、`open/close/toggle`、方向、触发、label mode、关闭策略、动画时长和 stagger。
- action model：`RXSpeedDialAction` 提供 text/graphic/onAction/disable/visible/closeOnAction，getter/setter 保持 pass-through。
- Skin 结构：`RXSkinBase` + `SkinDisposer`；主 FAB 为 managed 节点，actions layer unmanaged；action cell 缓存并可释放。
- 状态机和事件：`SHOWING -> SHOWN`、`CLOSE_REQUEST -> HIDING -> HIDDEN`；veto 在 `showing=false` 前发生；close reason 覆盖 TOGGLE/ACTION/ESCAPE/FOCUS_LOST/CLICK_OUTSIDE/MOUSE_EXIT。
- 动画：手写 `Timeline`，scale + fade + stagger；icon/openIcon 交叉淡入淡出和旋转；invalid duration、null、负值、`animated=false` 走 snap。
- 键盘：方向键沿轴移动，Home/End 边界聚焦，ESC 关闭；SPACE 由 Button 行为提供，ENTER 保持 JavaFX 平台契约。
- 可访问性：主 FAB 和 action FAB 均为按钮语义；主 FAB/控件回答 `EXPANDED`；action accessible text 绑定 `action.textProperty()`。
- CSS 与三主题：新根登记进 core/dark/AtlantaFX token 清单；SpeedDial 子结构使用 `.rx-speed-dial > .actions > .action` 作用域；颜色走角色 token。
- Demo / Showcase：Demo 保持真实轻量场景；Showcase 暴露 V1 属性和命令；AppShowcase 和 ThemeGallery 已接入。

## 测试结果

已执行并通过：
- `mvn -pl rxcontrols -Dtest=RXSpeedDialTest test`：46 tests，0 failures/errors。
- `mvn -pl rxcontrols-samples -am -Dtest=AppShowcaseSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test`：samples smoke 2 tests，0 failures/errors。
- `mvn -pl rxcontrols -Dtest=RXFloatingActionButtonTest,RXSpeedDialTest,RXSpeedDialA11yTest,RxControlsThemeBaselineTest test`：65 tests，0 failures/errors。
- `mvn -pl rxcontrols-samples -am test`：core 2672 tests + samples 2 tests，0 failures/errors，reactor `BUILD SUCCESS`。
- `mvn test`：core 2672 tests + samples 2 tests，0 failures/errors/skips，reactor `BUILD SUCCESS`。

测试隔离：
- core 模块沿用既有 surefire 多 fork 隔离。
- samples 新增 smoke 测试使用单 fork，避免 JavaFX toolkit 状态并发污染。

日志说明：
- JavaFX native-access / Unsafe warnings、既有 CSS probe warnings 和 PasswordMaskSupport fallback warnings 仍会出现；本次没有测试失败。

## subagent 审核

PR checkpoint 审核：
- PR1：审核 FAB API、size metadata、圆形 CSS、theme token 和尺寸反拉伸；成立问题已在实现阶段修复。
- PR2：审核 actions list、cell 生命周期、紧凑布局、accessibleText 绑定和三主题登记；成立问题已修复并由 headless 测试覆盖。
- PR3：审核状态机、事件顺序、close veto、close reason、ESC/focus/outside/action 路径；成立问题已修复。
- PR4：审核 animation latest-wins、snap 分支、Duration 守卫、icon morph、dispose；成立问题已修复。
- PR5：审核键盘/a11y、Mac ENTER 契约、scene key filter、disabled/hidden action skip、focusTraversable；成立问题已修复，Mac ENTER workaround 按 research §16 F10 驳回。
- PR6：审核 Demo/Showcase/AppShowcase/ThemeGallery；成立问题包括命令按钮与 focus-loss/outside-click 冲突、extended FAB disabled 同步、ThemeGallery skin/CSS 初始化，均已修复。

最终跨 PR 审核：
- 契约一致性：发现水平 label 布局不符合 §6.3、`notifyShown/notifyHidden` 暴露 public API；已修复，新增测试。
- 生命周期与泄漏：发现 dispose 未移除 Skin children、移除 action 未释放 graphic、actions 重建未停止 in-flight timeline；已修复，新增测试。
- CSS 与三主题：无契约级问题。
- 测试与交付：发现 AppShowcase smoke、报告、真机清单缺失；已补齐。

驳回或不作为项：
- sample / showcase 内部普通按钮使用 `setOnAction` 属演示代码，不是 Skin 内部接线。
- macOS 非 default Button 的 ENTER 行为保持 JavaFX 平台契约，Core 不补 workaround。
- hardcoded sample 色值仅用于 sample CSS，不进入控件 UA 主题契约。

## 偏离与跳过

最小偏离：
- 终端事件由 Skin 在动画/snap 结束后直接 fire，不通过 Control 暴露 public `notifyShown/notifyHidden`。这收窄了 API 面，避免应用代码提前触发 SHOWN/HIDDEN。
- focus-loss 判断用 scene focus owner + descendant 检测表达 `focusWithin` 语义；真实溢出层焦点行为仍列入真机验证。

未实现 / 保持 Later：
- circular / semi-circle / quarter-circle 布局。
- draggable FAB。
- mask / scrim / scene service。
- reduced-motion 系统钩子。
- action groups、separators、nested dials。
- hover intent 延迟、防抖和触屏回退精细化。

未跳过任何 Core / V1 必做项。

## 真机验证清单

以下项目没有为了 headless 测试而修改核心生产代码，需要用户在真实窗口、Showcase、目标 DPI、目标操作系统和读屏环境中手动验证。

### 视觉与布局

- [ ] SMALL / STANDARD / LARGE 三种 FAB 尺寸视觉正确。
- [ ] 圆形 FAB 没有被父布局拉伸。
- [ ] extended 胶囊形态正确。
- [ ] 上、下、左、右四个方向的布局正确。
- [ ] action 间距和 label pill 间距自然。
- [ ] invisible action 不留空位。
- [ ] 三种宿主布局中的 footprint 正确。
- [ ] 不同窗口缩放和 DPI 下无明显 snap 缝隙。
- [ ] RTL 下方向、对齐和键盘导航合理。

### 动画

- [ ] scale + fade + stagger 观感自然。
- [ ] 快速连续 open / close 不闪烁。
- [ ] plus↔close morph 自然。
- [ ] label pill 与 action 同步。
- [ ] `animated=false` 无残留中间状态。
- [ ] 三主题下动画期间无错误背景或透明边缘。

### 输入与焦点

- [ ] CLICK 打开 / 关闭正确。
- [ ] HOVER 打开正确。
- [ ] 鼠标移出但焦点仍在内部时不会误关。
- [ ] 鼠标移出且内部失焦后正确关闭。
- [ ] click outside 正确关闭。
- [ ] focus loss 正确关闭。
- [ ] ESC 正确关闭。
- [ ] action click 后 close reason 正确。
- [ ] picking 缝隙不会导致意外 click-through。
- [ ] 溢出层中的 `focusWithin` 实际行为正确。

### 键盘

- [ ] 展开后方向键沿轴移动。
- [ ] Home 聚焦第一个可用 action。
- [ ] End 聚焦最后一个可用 action。
- [ ] hidden / disabled action 被正确跳过。
- [ ] ESC 返回主 FAB 或按文档语义关闭。
- [ ] SPACE 激活正确。
- [ ] ENTER 在目标平台上符合文档结论。
- [ ] 折叠时 Tab 顺序只包含主 FAB。
- [ ] 展开后焦点顺序自然。

### 可访问性

- [ ] 主 FAB 被读屏识别为按钮。
- [ ] expanded 状态朗读正确。
- [ ] 每个 action 被识别为按钮。
- [ ] action 文本变化后读屏名称同步变化。
- [ ] hidden action 不被朗读。
- [ ] disabled action 状态朗读正确。
- [ ] 关闭后焦点回归合理。

### 主题

- [ ] core 主题配色正确。
- [ ] dark 主题配色正确。
- [ ] AtlantaFX 主题配色正确。
- [ ] elevation 在三个主题下自然。
- [ ] ripple 在三个主题下清晰但不过强。
- [ ] label pill 对比度足够。
- [ ] hover / pressed / disabled 状态清晰。

## Git 状态提醒

- 未执行 `git add`。
- 未执行 `git commit`。
- 未执行 `git push`。
- 请用户完成真机验证和代码审核后自行提交。
