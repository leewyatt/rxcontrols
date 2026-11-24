# RXControls

中文 | [English](README.md)

RXControls 是一个面向桌面应用的 JavaFX 17 控件库，提供 120 个 UI 组件和 6 个布局容器，补充了标准控件集之外的常用能力：虚拟化数据视图、日期与时间选择器、动画按钮、内容转场、加载与空状态、图片展示、导航、场景覆盖层，以及可复用的交互基础设施。

核心模块唯一的 JavaFX 依赖是 `javafx-controls`。需要样式的控件会自动提供兼容 Modena 的 user-agent stylesheet；应用可以通过 style class 和 `-rx-*` CSS 属性定制外观。使用 AtlantaFX 的应用还可以安装不引入编译期依赖的可选主题桥。

> **发布状态：** 首个 Maven Central 版本尚未发布。当前 Maven 版本以及计划中的首发坐标为 `io.github.leewyatt:rxcontrols:1.0.0`。正式发布前，请按照下文从源码构建并安装到本地 Maven 仓库。

## 主要特性

- Java 17 和 JavaFX 17 基线。
- 120 个具体的 public UI 组件和 6 个具体的 public 布局容器。
- 遵循 JavaFX 的 Property、CSS metadata、伪类、事件、选择模型、Cell 和 Skin 机制。
- 面向普通场景图节点的响应式布局与动画布局。
- 自建虚拟化 List、Tile、Masonry 和 Kanban 视图。
- 包含内嵌 View 与弹层 Picker 的完整日期时间家族。
- Button、Toggle、Chip、Slider、Progress、Skeleton、Badge、Transition、Dialog、Snackbar 和导航组件家族。
- 可复用的页面转场、填充、线条和粒子爆发动画策略。
- 长时间动画会在控件不再实际显示时自动暂停。
- 默认适配 Modena，并提供可选的 AtlantaFX 主题桥。
- 带有可搜索的 Sampler 应用、简洁 Sample 和完整 Showcase。

## 环境要求

- JDK 17 或更高版本。
- JavaFX 17 或更高版本；项目当前使用 JavaFX `17.0.13` 构建。
- Maven。

## 安装

### 从当前源码构建

克隆仓库后，将父项目和核心模块安装到本地 Maven 仓库：

```bash
mvn -pl rxcontrols -am install
```

随后即可在本机项目中引用：

```xml
<dependency>
    <groupId>io.github.leewyatt</groupId>
    <artifactId>rxcontrols</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Maven Central

首个版本发布后，同一份依赖声明即可直接从 Maven Central 解析，不需要额外声明仓库。

### Java 模块

模块名为 `rxcontrols`：

```java
module com.example.application {
    requires javafx.controls;
    requires rxcontrols;
}
```

主要导出包如下：

```text
io.github.leewyatt.rxcontrols
io.github.leewyatt.rxcontrols.animation.burst
io.github.leewyatt.rxcontrols.animation.fill
io.github.leewyatt.rxcontrols.animation.line
io.github.leewyatt.rxcontrols.animation.page
io.github.leewyatt.rxcontrols.carousel
io.github.leewyatt.rxcontrols.event
io.github.leewyatt.rxcontrols.layout
io.github.leewyatt.rxcontrols.lrc
io.github.leewyatt.rxcontrols.skins
io.github.leewyatt.rxcontrols.spectrum
io.github.leewyatt.rxcontrols.theme
io.github.leewyatt.rxcontrols.utils
```

## 快速开始

需要库样式的 RXControls 会自行暴露内置 user-agent stylesheet，因此普通 Modena 应用不需要手动安装库样式表。

```java
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXListView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class HelloRxControls extends Application {

    @Override
    public void start(Stage stage) {
        RXListView<String> list = new RXListView<>();
        list.getItems().setAll("响应式布局", "虚拟化视图", "动画控件");

        RXButton addButton = new RXButton("添加项目");
        addButton.setOnAction(event -> list.getItems().add("新项目"));

        BorderPane root = new BorderPane(list);
        root.setBottom(addButton);

        stage.setScene(new Scene(root, 640, 420));
        stage.setTitle("RXControls");
        stage.show();
    }
}
```

## 主题与 CSS

每个组件都会通过 JavaFX style class 暴露根节点和内部结构。自定义 styleable property 使用 `-rx-*` 前缀，JavaFX 标准属性仍使用 `-fx-*` 名称。

```css
.rx-button.primary {
    -fx-background-color: -fx-accent;
    -fx-text-fill: white;
}

.rx-badge.danger > .indicator {
    -fx-font-size: 11px;
}
```

默认主题从 Modena 调色板派生颜色。AtlantaFX 应用在设置 Application user-agent stylesheet 后，可以安装主题桥：

```java
Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
RXAtlantaFXThemeBridge.install(scene);
```

主题桥不会让核心模块产生 AtlantaFX 编译期依赖。每个显示 RXControls 的 Scene 都需要安装一次；切回 Modena 时应卸载主题桥。

## 运行 Sampler

`rxcontrols-samples` 模块包含可搜索的组件 Sampler、聚焦真实用法的 Sample，以及覆盖主要属性的完整 Showcase。该模块不会发布到 Maven Central。

```bash
mvn -pl rxcontrols -am install -DskipTests
mvn -pl rxcontrols-samples javafx:run
```

Sampler 适合比较职责相近的组件、交互式验证属性、切换主题，以及浏览全部页面转场预设。

## 组件目录

下面列出可以直接使用的公开 UI 组件。配套模型、策略、事件、Skin 和工具类在后面的完整公开 API 索引中列出。

### 布局容器

| 组件 | 用途 |
|---|---|
| `RXBox` | 可切换横向/纵向的一维布局，支持 grow、margin、单项 alignment 和 baseline。 |
| `RXFlowPane` | 可换行流式布局，分别控制整体、run 和 item 的对齐。 |
| `RXTilePane` | 面向现有节点的非虚拟化响应式瓦片网格，支持分布与重排动画。 |
| `RXMasonryPane` | 面向现有节点的非虚拟化瀑布流，支持跨列和响应式列数。 |
| `RXDashboardPane` | 可编辑仪表盘：固定列数、固定行高的网格，最终用户拖动、拉伸卡片，布局可保存为快照并恢复。 |
| `RXDashboardCard` | 与 `RXDashboardPane` 配套的卡片容器：内容、稳定 ID、锁定、跨度上下限和可选的拖动把手。 |
| `RXRow` | 根据断点解析列规格的响应式栅格行。 |
| `RXCol` | 与 `RXRow` 配套使用的栅格列和内容宿主。 |

### 按钮、切换与紧凑选择

| 组件 | 用途 |
|---|---|
| `RXButton` | 保留 JavaFX Button 语义，并增加 bounded ripple 和状态覆盖层。 |
| `RXFloatingActionButton` | 基于 `RXButton` 的浮动操作按钮。 |
| `RXFillButton` | 带可逆填充装饰动画的按钮。 |
| `RXLineButton` | 带可逆线条装饰动画的按钮。 |
| `RXTransitionButton` | 通过页面转场连接普通面和 alternate content 的双面按钮。 |
| `RXCopyButton` | 生成并复制 `ClipboardContent`，随后短暂显示复制成功反馈。 |
| `RXTaskButton` | 显示外部任务的 pending、succeeded 和 failed 状态面。 |
| `RXHoldButton` | 用户持续按住到指定时长后才触发。 |
| `RXToggleButton` | 带 RXControls ripple 和状态反馈的 ToggleButton。 |
| `RXRadioToggleButton` | Button 外观的单选项，已选中时不会被用户再次点击取消。 |
| `RXReactionButton` | 用于 like/favorite/bookmark 的切换按钮，带图标 pop 和粒子 burst。 |
| `RXCheckBox` | 保留 JavaFX CheckBox 二态/三态语义，并提供自定义视觉与反馈。 |
| `RXRadioButton` | 保留 JavaFX RadioButton 语义，并提供自定义视觉与反馈。 |
| `RXSwitchButton` | 用于即时设置的 track/thumb 开关。 |
| `RXSegmentedControl<T>` | 由 `RXSegmentedItem<T>` 驱动的紧凑分段选择。 |
| `RXChip` | 可选择、可触发或可关闭的紧凑标签。 |
| `RXChipSet` | 一组 `RXChip` 节点的布局和选择管理器。 |
| `RXChipInput<T>` | 将用户提交的多个值转换为 Chip 的文本输入控件。 |
| `RXMenuButton` | 打开 RXControls 弹出菜单的按钮。 |
| `RXSpeedDial` | 主 FAB 展开一组相关次级操作。 |

### 文本、数字与建议输入

| 组件 | 用途 |
|---|---|
| `RXTextField` | 带 leading/trailing 节点槽和独立 text padding 的 TextField。 |
| `RXPasswordField` | 带节点槽和密码显示开关的 PasswordField。 |
| `RXIntegerField` | 带解析、提交和范围约束的强类型 `Integer` 输入。 |
| `RXLongField` | 强类型 `Long` 输入。 |
| `RXDoubleField` | 强类型 `Double` 输入。 |
| `RXDecimalField` | 面向精确十进制与本地化格式的 `BigDecimal` 输入。 |
| `RXMaterialTextField` | 带浮动标签外观的 Material 文本框。 |
| `RXMaterialPasswordField` | Material 密码框。 |
| `RXMaterialIntegerField` | Material `Integer` 输入。 |
| `RXMaterialLongField` | Material `Long` 输入。 |
| `RXMaterialDoubleField` | Material `Double` 输入。 |
| `RXMaterialDecimalField` | Material 精确十进制输入。 |
| `RXAutoCompleteField` | 面向 String、适合 FXML 的本地同步自动完成文本框。 |

`RXAutoCompletion<T>` 是泛型、代码优先的替代方案，可以把同一套建议弹层设施附着到任意现有 JavaFX `TextField`。

### 数值、范围与进度编辑

| 组件 | 用途 |
|---|---|
| `RXSlider` | 带刻度、halo 和可配置数值气泡的 Material 单值 Slider。 |
| `RXRangeSlider` | 带整段拖动和最小间距的 low/high 双 thumb 范围编辑器。 |
| `RXSeekBar` | 带主进度、secondary/buffer 进度和 seeking 状态的归一化媒体进度条。 |
| `RXRating` | 星级评分：任意小数精确绘制，输入支持整星、半星或任意小数，悬停只预览不提交。 |
| `RXSelectionBox<T>` | 支持搜索和 section 的应用值选择框。 |
| `RXDigit` | 纯展示的单个七段数码字形。 |

### 日期与时间 View

View 可以直接嵌入页面；对应 Picker 会增加文本字段、解析、焦点/提交行为和弹层生命周期。

| 组件 | 值 |
|---|---|
| `RXCalendarView` | 单个 `LocalDate` 月历。 |
| `RXWeekView` | 使用 `LocalDate` 表示的整周选择。 |
| `RXMonthView` | `YearMonth` 选择。 |
| `RXQuarterView` | `RXYearQuarter` 选择。 |
| `RXYearView` | `Year` 选择。 |
| `RXTimeView` | 滚动列式 `LocalTime` 选择。 |
| `RXDateTimeView` | 组合式 `LocalDateTime` 选择。 |
| `RXDateRangeView` | `RXDateRange` 选择。 |
| `RXWeekRangeView` | 使用 `RXDateRange` 表示的整周范围。 |
| `RXMonthRangeView` | `RXMonthRange` 选择。 |
| `RXQuarterRangeView` | `RXQuarterRange` 选择。 |
| `RXYearRangeView` | `RXYearRange` 选择。 |

### 日期与时间 Picker

| 组件 | 值 |
|---|---|
| `RXDatePicker` | `LocalDate`。 |
| `RXWeekPicker` | 使用 `LocalDate` 表示的整周。 |
| `RXMonthPicker` | `YearMonth`。 |
| `RXQuarterPicker` | `RXYearQuarter`。 |
| `RXYearPicker` | `Year`。 |
| `RXTimePicker` | `LocalTime`。 |
| `RXDateTimePicker` | `LocalDateTime`。 |
| `RXDateRangePicker` | `RXDateRange`。 |
| `RXWeekRangePicker` | 整周 `RXDateRange`。 |
| `RXMonthRangePicker` | `RXMonthRange`。 |
| `RXQuarterRangePicker` | `RXQuarterRange`。 |
| `RXYearRangePicker` | `RXYearRange`。 |
| `RXTimeRangePicker` | `RXTimeRange`。 |
| `RXDateTimeRangePicker` | `RXDateTimeRange`。 |

### 虚拟化数据视图

| 组件 | 用途 |
|---|---|
| `RXListView<T>` | 支持可变行高、section、sticky/collapsible header 和多选的单列虚拟列表。 |
| `RXTileView<T>` | 支持 section、marquee 多选和列数变化动画的按行虚拟化瓦片网格。 |
| `RXMasonryView<T>` | 面向不等高 Cell 的虚拟化瀑布流。 |
| `RXKanbanView<T>` | 支持卡片操作与移动事件的列式 Kanban 视图。 |
| `RXCascaderView<T>` | 支持选择、勾选、禁用继承和懒加载的内嵌多列层级浏览器。 |
| `RXCascader<T>` | 在 `RXCascaderView<T>` 外提供字段和 popup 外壳。 |

公开 Cell 类型包括 `RXListCell<T>`、`RXListSectionCell`、`RXTileCell<T>`、`RXTileSectionCell`、`RXMasonryCell<T>`、`RXKanbanCardCell<T>` 和 `RXCascaderCell<T>`。

### 内容、图片与文本展示

| 组件 | 用途 |
|---|---|
| `RXContentPane` | 最小化、可绑定的单内容 Region。 |
| `RXImageView` | 支持 contain/cover/fill、insets 和圆角的 Region 图片显示区。 |
| `RXImagePane` | 同时承载图片背景和普通子内容的 Region。 |
| `RXClipPathImageView` | 使用内置或自定义 SVG path 裁剪的 cover-fit 图片。 |
| `RXAvatar` | 带图片、首字母和默认图标回退的圆形/圆角头像。 |
| `RXTextView` | 可选择、可复制、自动换行的只读文本。 |
| `RXHighlightTextView` | 在 `RXTextView` 上增加字面或正则关键词高亮。 |
| `RXFillLabel` | 带可逆填充装饰动画的 Label。 |
| `RXLineLabel` | 带可逆线条装饰动画的 Label。 |
| `RXTransitionLabel` | 对文本替换播放动画的文本显示控件。 |
| `RXLrcView` | 支持手动浏览和恢复跟随的滚动同步歌词视图。 |
| `RXLrcLineView` | 使用页面转场显示当前单行歌词。 |
| `RXAudioSpectrum` | 接收 magnitude 数组并委托可插拔 renderer 的 Canvas 频谱视图。 |
| `RXTimelineView` | 由 `RXTimelineItem` 驱动的等距事件/顺序/历史时间线。 |
| `RXCarousel` | 支持直接页面或懒加载 page factory 的索引式轮播。 |

### 内容转场与状态容器

| 组件 | 用途 |
|---|---|
| `RXTransitionPane` | 每次替换任意 content Node 时播放转场。 |
| `RXDualPane` | 在两个持久内容面之间切换，并保持稳定首选尺寸。 |
| `RXSkeletonPane` | 在 skeleton 和真实内容槽之间切换。 |
| `RXStatePane` | content/empty/error 替换轴，加一条独立 loading overlay 轴。 |
| `RXPlaceholder` | 用于空态或错误态的 graphic、title、description 和 actions 展示。 |
| `RXRipplePane` | 给任意内容添加 bounded ripple 与 hover 反馈，不伪造 Button 语义。 |
| `RXBorderBeam` | 沿内容边框循环移动的装饰性高光。 |
| `RXBackdrop` | 可独立使用的动画遮罩和输入阻挡层。 |

### Loading、状态与反馈

| 组件 | 用途 |
|---|---|
| `RXBarSpinner` | 由动画竖条组成的纯装饰 indeterminate activity。 |
| `RXDotPulse` | 由动画圆点组成的纯装饰 indeterminate activity。 |
| `RXCircularProgressIndicator` | 可在中心显示内容的确定/不确定圆环 ProgressIndicator。 |
| `RXWaveProgressIndicator` | 使用动画水位和波浪显示进度的圆形 ProgressIndicator。 |
| `RXSegmentedProgressBar` | 确定或不确定的水平分段进度条。 |
| `RXSegmentedStepIndicator` | 通过事件报告交互请求的被动式 completed/current 步骤展示。 |
| `RXSkeleton` | 带可选 shimmer 的矩形、圆形或文本行 skeleton。 |
| `RXBadge` | 附着于内容角落或独立使用的 count、text、dot indicator。 |
| `RXRibbonBadge` | 横跨内容顶部 START/END 侧的折角文字缎带。 |

`RXBursts` 是一个 Scene overlay facade，可以从任意节点或局部坐标播放粒子爆发；`RXBurstOptions` 用于完整配置。

### 导航、折叠、菜单与覆盖层

| 组件 | 用途 |
|---|---|
| `RXBreadcrumbBar<T>` | 带导航请求、分支菜单和中段折叠的 root-to-current 路径。 |
| `RXSidebar` | 支持 expanded/mini 模式的永久分层侧边栏。 |
| `RXTabPane` | 由可样式化 `RXTab` 模型驱动的标签页容器。 |
| `RXTitledPane` | 支持懒内容的独立可折叠 header/body 面板。 |
| `RXAccordion` | 协调多个 `RXTitledPane` 的单开或多开展开规则。 |
| `RXMenuList` | 内联展示 RXControls 菜单模型。 |
| `RXMenuButton` | 菜单触发按钮。 |
| `RXDrawerPane` | 覆盖在主内容之上的临时场景内侧边抽屉。 |
| `RXDialog<R>` | 带可拒绝关闭和异步结果回调的场景内模态 Dialog。 |
| `RXDialogContent` | `RXDialog` 常用的标题/正文内容。 |
| `RXDialogContentBase` | 自定义 Dialog 内容使用的 dialog-aware Region 基类。 |
| `RXSnackbarHost` | 管理 Snackbar 展示、队列、替换和 dismissal 生命周期。 |

这个家族中的非 Node facade 包括 `RXPopupMenu`、`RXDialogs` 和 `RXSnackbars`。

## 完整公开 API 索引

上面的组件目录与下面各节合在一起，覆盖 `module-info.java` 导出包中的全部 public 顶层类型。嵌套类型请查阅其所属类的 Javadoc。

### 核心模型、配置与支持类型

- 动画和显示配置：`AnimationTrigger`、`DisplayMode`、`ImageFit`、`ItemsJustify`、`RXPlacement`、`RXSliderIndicatorDisplay`、`RXSliderIndicatorPosition`。
- 滚动配置：`ScrollAlignment`、`ScrollAxis`、`ScrollBoundaryPolicy`、`SmoothScrollMode`、`RXSmoothScrollOptions`、`RXSmoothScrollSupport`、`RXSmoothScroller`。
- Dialog 配置和 facade：`DialogActionsLayout`、`DialogTransition`、`RXDialogAware`、`RXDialogs`。
- 日期时间值：`RXDateRange`、`RXDateTimeRange`、`RXMonthRange`、`RXQuarterRange`、`RXTimeRange`、`RXYearQuarter`、`RXYearRange`。
- 虚拟视图支持：`CellHeightContext`、`CellHeightProvider`、`RXIndexedSelectionModel`、`RXListSelectionVisualMode`、`RXListSection`、`RXListVisibleRange`、`RXMasonryVisibleBounds`、`RXTileSection`、`RXTileVisibleRange`。
- Cascader 模型：`RXCascaderItem`、`RXCascaderPath`。
- Kanban 模型：`RXKanbanColumn`、`RXKanbanCardDropContext`。
- 菜单模型：`RXMenuItem`、`RXMenuHeader`、`RXMenuSeparator`。
- SegmentedControl 模型：`RXSegmentedItem`。
- Sidebar 模型：`RXSidebarItemBase`、`RXSidebarItem`、`RXSidebarCaption`、`RXSidebarSeparator`。
- Snackbar 模型和配置：`RXSnackbarRequest`、`RXSnackbarSeverity`、`RXSnackbarStrategy`。
- SpeedDial 模型：`RXSpeedDialAction`。
- Tab 模型：`RXTab`、`RXTabEvent`、`RXTabPaneSelectionModel`。
- Timeline 模型：`RXTimelineItem`。
- 动画按钮基类：`RXAnimatedButton`、`RXAnimatedLabel`。
- 日期时间基类：`RXPickerBase`、`RXTemporalViewBase`。

### 响应式布局支持

包 `io.github.leewyatt.rxcontrols.layout`：

- `RXBreakpoint`
- `RXBreakpointProfile`
- `RXColSpec`
- `RXBox`
- `RXFlowPane`
- `RXTilePane`
- `RXMasonryPane`
- `RXDashboardPane`
- `RXDashboardCard`
- `RXDashboardLayout`
- `RXRow`
- `RXCol`

### Carousel 支持

包 `io.github.leewyatt.rxcontrols.carousel`：

- `CarouselNavigator`
- `DefaultNavigator`
- `SegmentedProgressNavigator`
- `RXPageLifecycleEvent`

### LRC 解析

包 `io.github.leewyatt.rxcontrols.lrc`：

- `LrcDocument`
- `LrcLine`
- `LrcMetadata`
- `LrcParseResult`
- `LrcParseWarning`
- `LrcParser`
- `LrcWarningCode`

### 频谱可视化

包 `io.github.leewyatt.rxcontrols.spectrum`：

- `BandLayout`
- `SpectrumContext`
- `SpectrumVisualization`
- `SpectrumVisualizationBase`
- `VisBars`
- `VisBarsMirror`
- `VisLine`
- `VisRadial`

### Burst 动画

包 `io.github.leewyatt.rxcontrols.animation.burst`：

- 契约与帧数据：`BurstAnimation`、`BurstFrame`。
- 预设：`BurstAnimBubbles`、`BurstAnimConfetti`、`BurstAnimDots`、`BurstAnimExplosion`、`BurstAnimFireworks`、`BurstAnimHearts`、`BurstAnimRays`、`BurstAnimRing`、`BurstAnimShapes`、`BurstAnimSparkles`。

### Fill 动画

包 `io.github.leewyatt.rxcontrols.animation.fill`：

- 契约：`FillAnimation`。
- 预设：`FillAnimBottomToTop`、`FillAnimCenterOut`、`FillAnimCenterOutVertical`、`FillAnimCircle`、`FillAnimCornersIn`、`FillAnimEdgesIn`、`FillAnimEdgesInVertical`、`FillAnimLeftToRight`、`FillAnimRightToLeft`、`FillAnimTopToBottom`、`FillAnimZigzag`、`FillAnimZigzagVertical`。

### Line 动画

包 `io.github.leewyatt.rxcontrols.animation.line`：

- 契约与配置：`LineAnimation`、`LineEdges`、`LineOrigin`。
- 预设：`LineAnimExtend`、`LineAnimSlide`。

### 页面转场

包 `io.github.leewyatt.rxcontrols.animation.page`：

- 契约与上下文：`PageAnimation`、`PageAnimationBase`、`TransitionContext`、`TransitionDirection`、`TransitionResolver`。
- 预设：`AnimAccordion`、`AnimAround`、`AnimBarn`、`AnimBlend`、`AnimBlinds`、`AnimBounce`、`AnimBox`、`AnimCards`、`AnimCascade`、`AnimCheckerboard`、`AnimCircle`、`AnimCircleWave`、`AnimCircles`、`AnimComb`、`AnimConveyor`、`AnimCorner`、`AnimCross`、`AnimCube`、`AnimCube4`、`AnimCurtain`、`AnimDiamond`、`AnimDipToColor`、`AnimDissolve`、`AnimDomino`、`AnimDoorway`、`AnimDrain`、`AnimFade`、`AnimFlash`、`AnimFlip`、`AnimFold`、`AnimGallery`、`AnimGaussianBlur`、`AnimGlitch`、`AnimHoneycomb`、`AnimIris`、`AnimLouver`、`AnimMelt`、`AnimMosaic`、`AnimMotionBlur`、`AnimNewspaper`、`AnimNone`、`AnimParallax`、`AnimPeel`、`AnimPinwheel`、`AnimPixelate`、`AnimRandomTiles`、`AnimRectangle`、`AnimReveal`、`AnimRipple`、`AnimRotate`、`AnimSector`、`AnimSelector`、`AnimSerpentine`、`AnimShapeReveal`、`AnimShatter`、`AnimShatterRadial`、`AnimShred`、`AnimShrink`、`AnimShutter`、`AnimSlide`、`AnimSlideIn`、`AnimSpinStrips`、`AnimSpiralTiles`、`AnimSplit`、`AnimSplitWipe`、`AnimSqueeze`、`AnimStack`、`AnimSwap`、`AnimSwing`、`AnimTiltSlide`、`AnimWave`、`AnimWedge`、`AnimWhipPan`、`AnimWind`、`AnimWipe`、`AnimZigzagWipe`、`AnimZoom`。

### 事件

包 `io.github.leewyatt.rxcontrols.event`：

- `RXAnimationEvent`
- `RXAutoCompleteEvent`
- `RXBreadcrumbEvent`
- `RXChipEvent`
- `RXDashboardEvent`
- `RXDialogEvent`
- `RXDrawerEvent`
- `RXKanbanCardActionEvent`
- `RXKanbanCardMovedEvent`
- `RXKanbanColumnMovedEvent`
- `RXListViewActionEvent`
- `RXLrcLineEvent`
- `RXMasonryViewActionEvent`
- `RXMenuEvent`
- `RXSegmentInteractionEvent`
- `RXSnackbarEvent`
- `RXSpeedDialEvent`
- `RXTileViewActionEvent`
- `RXTimelineItemEvent`
- `RXTransitionPaneEvent`

`RXTabEvent` 从根包导出，`RXPageLifecycleEvent` 从 carousel 包导出。

### 公开 Skin 与 Skin 基础设施

包 `io.github.leewyatt.rxcontrols.skins` 导出默认 Skin，应用可以在确有需要时继承或替换：

- 基础设施与基类：`RXSkinBase`、`SkinDisposer`、`RXFieldBaseSkin`、`RXMaterialFieldBaseSkin`。
- 通用控件：`RXAccordionSkin`、`RXAudioSpectrumSkin`、`RXAvatarSkin`、`RXBadgeSkin`、`RXBarSpinnerSkin`、`RXBorderBeamSkin`、`RXBreadcrumbBarSkin`、`RXCarouselSkin`、`RXCircularProgressIndicatorSkin`、`RXDialogSkin`、`RXDigitSkin`、`RXDotPulseSkin`、`RXDrawerPaneSkin`、`RXDualPaneSkin`、`RXPlaceholderSkin`、`RXRibbonBadgeSkin`、`RXSkeletonSkin`、`RXSnackbarHostSkin`、`RXStatePaneSkin`、`RXTimelineViewSkin`、`RXTitledPaneSkin`。
- 按钮与选择：`RXButtonSkin`、`RXCheckBoxSkin`、`RXChipSkin`、`RXChipInputSkin`、`RXChipSetSkin`、`RXCopyButtonSkin`、`RXFillButtonSkin`、`RXFillLabelSkin`、`RXFloatingActionButtonSkin`、`RXHoldButtonSkin`、`RXLineButtonSkin`、`RXLineLabelSkin`、`RXMenuButtonSkin`、`RXMenuListSkin`、`RXRadioButtonSkin`、`RXReactionButtonSkin`、`RXSegmentedControlSkin`、`RXSelectionBoxSkin`、`RXSidebarSkin`、`RXSpeedDialSkin`、`RXSwitchButtonSkin`、`RXTabPaneSkin`、`RXTaskButtonSkin`、`RXToggleButtonSkin`、`RXTransitionButtonSkin`、`RXTransitionLabelSkin`。
- 文本和数字字段：`RXAutoCompleteFieldSkin`、`RXMaterialPasswordFieldSkin`、`RXMaterialTextFieldSkin`、`RXPasswordFieldSkin`、`RXTextFieldSkin`。
- 日期时间控件：`RXCalendarViewSkin`、`RXDatePickerSkin`、`RXDateRangePickerSkin`、`RXDateRangeViewSkin`、`RXDateTimePickerSkin`、`RXDateTimeRangePickerSkin`、`RXDateTimeViewSkin`、`RXMonthPickerSkin`、`RXMonthRangePickerSkin`、`RXMonthRangeViewSkin`、`RXMonthViewSkin`、`RXQuarterPickerSkin`、`RXQuarterRangePickerSkin`、`RXQuarterRangeViewSkin`、`RXQuarterViewSkin`、`RXTimePickerSkin`、`RXTimeRangePickerSkin`、`RXTimeViewSkin`、`RXWeekPickerSkin`、`RXWeekRangePickerSkin`、`RXWeekRangeViewSkin`、`RXWeekViewSkin`、`RXYearPickerSkin`、`RXYearRangePickerSkin`、`RXYearRangeViewSkin`、`RXYearViewSkin`。
- 数据视图与 Cell：`RXCascaderSkin`、`RXCascaderViewSkin`、`RXKanbanCardCellSkin`、`RXKanbanViewSkin`、`RXListCellSkin`、`RXListSectionCellSkin`、`RXListViewSkin`、`RXMasonryViewSkin`、`RXTileViewSkin`。
- 媒体、文本、转场与数值控件：`RXHighlightTextViewSkin`、`RXLrcLineViewSkin`、`RXLrcViewSkin`、`RXRangeSliderSkin`、`RXRatingSkin`、`RXSeekBarSkin`、`RXSegmentedProgressBarSkin`、`RXSegmentedStepIndicatorSkin`、`RXSliderSkin`、`RXTextViewSkin`、`RXTransitionPaneSkin`、`RXWaveProgressIndicatorSkin`。

### 工具与主题集成

- `RXFormat` —— 共用 UI 格式化工具。
- `RXMath` —— 有限值清洗与 clamp 工具。
- `RXMouse` —— 鼠标与输入辅助方法。
- `RXOS` —— 操作系统识别。
- `RXStyles` —— style class 与 stylesheet 列表辅助方法。
- `RXTreeShowingProperty` —— 观察节点通过祖先、Scene 和 Window 链是否实际显示。
- `RXAtlantaFXThemeBridge` —— 可选的逐 Scene AtlantaFX 颜色 token 桥。

## 构建与测试

编译核心模块：

```bash
mvn -pl rxcontrols compile
```

运行整个 reactor 的测试：

```bash
mvn test
```

构建所有模块但不安装：

```bash
mvn package
```

## 项目结构

```text
rxcontrols/
├── rxcontrols/          核心控件库
├── rxcontrols-samples/  Sampler、Sample 与 Showcase
├── devdoc/              设计、研究与维护文档
├── pom.xml              Maven reactor
└── LICENSE              Apache License 2.0
```

## 稳定性

项目正在准备首次公开发布。组件集合和主要 API 已经基本完成，目前仍在进行 bug 修复和小范围完善。在 `1.0.0` 正式发布前，评估使用时建议固定到明确的源码 revision。

## 许可证

RXControls 使用 [Apache License 2.0](LICENSE) 许可证。
