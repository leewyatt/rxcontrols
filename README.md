# RXControls

[中文](README_zh.md) | English

RXControls is a JavaFX 17 library with 120 UI components and 6 layout containers for desktop applications that need more than the standard control set: virtualized data views, date and time pickers, animated buttons, content transitions, loading and empty states, image presentation, navigation, overlays, and reusable interaction infrastructure.

The core module's only JavaFX dependency is `javafx-controls`. It ships a Modena-compatible user-agent stylesheet with the controls, supports author CSS through `-rx-*` properties and style classes, and provides an optional bridge for applications using AtlantaFX.

> **Release status:** the first Maven Central release has not been published yet. The current Maven version and planned initial release coordinate are `io.github.leewyatt:rxcontrols:1.0.0`. Until that release is available, build and install the artifact from source as described below.

## Highlights

- Java 17 and JavaFX 17 baseline.
- 120 concrete public UI components and 6 concrete public layout containers.
- Standard JavaFX properties, CSS metadata, pseudo-classes, events, selection models, cells, and skins.
- Responsive and animated layout panes for ordinary scene-graph nodes.
- Custom virtualized list, tile, masonry, and Kanban views.
- A complete date/time family with inline views and popup pickers.
- Button, toggle, chip, slider, progress, skeleton, badge, transition, dialog, snackbar, and navigation families.
- Reusable page, fill, line, and particle-burst animation strategies.
- Long-running animations pause when their control is no longer showing.
- Modena styling by default and an optional, dependency-free AtlantaFX theme bridge.
- A searchable Sampler application containing examples and interactive showcases.

## Requirements

- JDK 17 or later.
- JavaFX 17 or later. The project currently builds against JavaFX `17.0.13`.
- Maven to build this repository.

## Installation

### Build the current source

Clone the repository and install the parent and core module into your local Maven repository:

```bash
mvn -pl rxcontrols -am install
```

Then use the locally installed artifact:

```xml
<dependency>
    <groupId>io.github.leewyatt</groupId>
    <artifactId>rxcontrols</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Maven Central

After the first release is published, the same dependency declaration will resolve directly from Maven Central. No additional repository declaration will be required.

### Java modules

The module name is `rxcontrols`:

```java
module com.example.application {
    requires javafx.controls;
    requires rxcontrols;
}
```

The main exported packages are:

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

## Quick start

Styled RXControls expose the bundled user-agent stylesheet themselves, so a basic Modena application does not need to install a library stylesheet manually.

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
        list.getItems().setAll("Responsive layouts", "Virtualized views", "Animated controls");

        RXButton addButton = new RXButton("Add item");
        addButton.setOnAction(event -> list.getItems().add("Another item"));

        BorderPane root = new BorderPane(list);
        root.setBottom(addButton);

        stage.setScene(new Scene(root, 640, 420));
        stage.setTitle("RXControls");
        stage.show();
    }
}
```

## Themes and CSS

Every component exposes its root and substructure through JavaFX style classes. Custom styleable properties use the `-rx-*` prefix; standard JavaFX properties retain `-fx-*` names.

```css
.rx-button.primary {
    -fx-background-color: -fx-accent;
    -fx-text-fill: white;
}

.rx-badge.danger > .indicator {
    -fx-font-size: 11px;
}
```

The default theme derives its colors from the Modena palette. An AtlantaFX application can install the optional bridge after setting its application user-agent stylesheet:

```java
Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
RXAtlantaFXThemeBridge.install(scene);
```

The bridge does not add a compile-time AtlantaFX dependency to the core module. Install it on every Scene that displays RXControls, and uninstall it when returning to Modena.

## Run the Sampler

The `rxcontrols-samples` module contains a searchable component Sampler, focused Samples, and full Showcases. The samples module is not published to Maven Central.

```bash
mvn -pl rxcontrols -am install -DskipTests
mvn -pl rxcontrols-samples javafx:run
```

The Sampler is the best place to compare visually similar components, inspect configurable properties, switch themes, and browse the page-animation presets.

## Component catalog

The following sections list the directly usable public UI components. Companion models, strategies, events, skins, and utilities are listed afterward in the complete public API index.

### Layouts

| Component | Purpose |
|---|---|
| `RXBox` | One-dimensional horizontal or vertical layout with grow, margin, per-child alignment, and baseline support. |
| `RXFlowPane` | Wrapping flow layout with separate block, run, and item alignment controls. |
| `RXTilePane` | Non-virtualized responsive tile grid for existing nodes, with distribution and reorder animation. |
| `RXMasonryPane` | Non-virtualized masonry layout for existing nodes, including spans and breakpoint-aware columns. |
| `RXDashboardPane` | Editable dashboard: cards on a grid of fixed columns and row height that users drag and resize, with layout snapshots to save and restore. |
| `RXDashboardCard` | Card container used with `RXDashboardPane`: content, stable ID, lock, span limits and an optional drag handle. |
| `RXRow` | Responsive grid row that resolves breakpoint-specific column specifications. |
| `RXCol` | Grid column/content host used with `RXRow`. |

### Buttons, toggles, and compact choices

| Component | Purpose |
|---|---|
| `RXButton` | Standard JavaFX Button semantics with bounded ripple and state overlay feedback. |
| `RXFloatingActionButton` | Floating-action-button presentation built on `RXButton`. |
| `RXFillButton` | Button with a reversible fill decoration. |
| `RXLineButton` | Button with a reversible line decoration. |
| `RXTransitionButton` | Button with normal and alternate content faces joined by a page transition. |
| `RXCopyButton` | Copies generated `ClipboardContent` and shows temporary copied feedback. |
| `RXTaskButton` | Displays pending, succeeded, and failed faces for externally managed work. |
| `RXHoldButton` | Fires only after the user holds it for a configured duration. |
| `RXToggleButton` | ToggleButton semantics with RXControls ripple and state feedback. |
| `RXRadioToggleButton` | Button-shaped radio choice that does not deselect itself when already selected. |
| `RXReactionButton` | Like/favorite/bookmark toggle with icon-pop and particle-burst feedback. |
| `RXCheckBox` | JavaFX CheckBox two/three-state semantics with custom feedback and visuals. |
| `RXRadioButton` | JavaFX RadioButton semantics with custom feedback and visuals. |
| `RXSwitchButton` | Track-and-thumb on/off switch for immediate settings. |
| `RXSegmentedControl<T>` | Compact choice group backed by `RXSegmentedItem<T>` values. |
| `RXChip` | Selectable, actionable, or closable compact label. |
| `RXChipSet` | Layout and selection manager for a collection of `RXChip` nodes. |
| `RXChipInput<T>` | Multi-value text input that turns committed values into chips. |
| `RXMenuButton` | Button that opens an RXControls popup menu. |
| `RXSpeedDial` | Main floating action button that expands a group of related actions. |

### Text, number, and suggestion inputs

| Component | Purpose |
|---|---|
| `RXTextField` | TextField with leading/trailing node slots and independent text padding. |
| `RXPasswordField` | PasswordField with node slots and a password-reveal option. |
| `RXIntegerField` | Strongly typed `Integer` input with parsing, commit, and range rules. |
| `RXLongField` | Strongly typed `Long` input. |
| `RXDoubleField` | Strongly typed `Double` input. |
| `RXDecimalField` | Precise decimal input for `BigDecimal`-style models and localized formatting. |
| `RXMaterialTextField` | Material-style text field with floating-label presentation. |
| `RXMaterialPasswordField` | Material-style password field. |
| `RXMaterialIntegerField` | Material-style `Integer` input. |
| `RXMaterialLongField` | Material-style `Long` input. |
| `RXMaterialDoubleField` | Material-style `Double` input. |
| `RXMaterialDecimalField` | Material-style precise decimal input. |
| `RXAutoCompleteField` | String-oriented, FXML-friendly local autocomplete text field. |

`RXAutoCompletion<T>` is the generic, code-first alternative that attaches the same suggestion infrastructure to any existing JavaFX `TextField`.

### Values, ranges, and progress editing

| Component | Purpose |
|---|---|
| `RXSlider` | Material single-value Slider with ticks, halo, and configurable value indicator. |
| `RXRangeSlider` | Two-thumb low/high range editor with range dragging and minimum gap support. |
| `RXSeekBar` | Normalized media seek bar with primary progress, secondary/buffer progress, and seeking state. |
| `RXRating` | Star rating that draws any fraction exactly, takes whole, half or free-fraction steps, and previews on hover without committing. |
| `RXSelectionBox<T>` | Searchable, section-capable selection field for application values. |
| `RXDigit` | Decorative single seven-segment numeric glyph. |

### Date and time views

Inline views can be embedded directly; the matching pickers add text fields, parsing, focus/commit behavior, and popup lifecycle.

| Component | Value |
|---|---|
| `RXCalendarView` | Single `LocalDate` calendar. |
| `RXWeekView` | Whole-week selection represented by `LocalDate`. |
| `RXMonthView` | `YearMonth` selection. |
| `RXQuarterView` | `RXYearQuarter` selection. |
| `RXYearView` | `Year` selection. |
| `RXTimeView` | Scrolling-column `LocalTime` selection. |
| `RXDateTimeView` | Combined `LocalDateTime` selection. |
| `RXDateRangeView` | `RXDateRange` selection. |
| `RXWeekRangeView` | Whole-week range represented by `RXDateRange`. |
| `RXMonthRangeView` | `RXMonthRange` selection. |
| `RXQuarterRangeView` | `RXQuarterRange` selection. |
| `RXYearRangeView` | `RXYearRange` selection. |

### Date and time pickers

| Component | Value |
|---|---|
| `RXDatePicker` | `LocalDate`. |
| `RXWeekPicker` | Whole week represented by `LocalDate`. |
| `RXMonthPicker` | `YearMonth`. |
| `RXQuarterPicker` | `RXYearQuarter`. |
| `RXYearPicker` | `Year`. |
| `RXTimePicker` | `LocalTime`. |
| `RXDateTimePicker` | `LocalDateTime`. |
| `RXDateRangePicker` | `RXDateRange`. |
| `RXWeekRangePicker` | Whole-week `RXDateRange`. |
| `RXMonthRangePicker` | `RXMonthRange`. |
| `RXQuarterRangePicker` | `RXQuarterRange`. |
| `RXYearRangePicker` | `RXYearRange`. |
| `RXTimeRangePicker` | `RXTimeRange`. |
| `RXDateTimeRangePicker` | `RXDateTimeRange`. |

### Virtualized data views

| Component | Purpose |
|---|---|
| `RXListView<T>` | Single-column virtualized list with variable-height rows, sections, sticky/collapsible headers, and multiple selection. |
| `RXTileView<T>` | Row-virtualized tile grid with sections, marquee selection, and animated column-count changes. |
| `RXMasonryView<T>` | Virtualized masonry view for unequal-height cells. |
| `RXKanbanView<T>` | Column-and-card Kanban view with card actions and movement events. |
| `RXCascaderView<T>` | Inline multi-column hierarchy browser with selection, checks, disabled inheritance, and lazy loading. |
| `RXCascader<T>` | Field and popup shell around `RXCascaderView<T>`. |

Public cell types are `RXListCell<T>`, `RXListSectionCell`, `RXTileCell<T>`, `RXTileSectionCell`, `RXMasonryCell<T>`, `RXKanbanCardCell<T>`, and `RXCascaderCell<T>`.

### Content, images, and text presentation

| Component | Purpose |
|---|---|
| `RXContentPane` | Minimal bindable single-content Region. |
| `RXImageView` | Region-based image presentation with contain/cover/fill, insets, and rounded corners. |
| `RXImagePane` | Image-backed Region that also lays out ordinary child content. |
| `RXClipPathImageView` | Cover-fit image clipped by a built-in or custom SVG path. |
| `RXAvatar` | Circular or rounded avatar with image, initials, and default-icon fallback. |
| `RXTextView` | Selectable and copyable wrapping read-only text. |
| `RXHighlightTextView` | `RXTextView` with literal or regular-expression keyword highlighting. |
| `RXFillLabel` | Label with a reversible fill decoration. |
| `RXLineLabel` | Label with a reversible line decoration. |
| `RXTransitionLabel` | Text display that animates text replacement. |
| `RXLrcView` | Scrolling synchronized lyric view with manual browsing and follow recovery. |
| `RXLrcLineView` | Single-current-line lyric view with page transitions. |
| `RXAudioSpectrum` | Canvas spectrum visualizer fed with magnitude arrays and a pluggable renderer. |
| `RXTimelineView` | Equally spaced event/order/history timeline backed by `RXTimelineItem`. |
| `RXCarousel` | Indexed page carousel with direct pages or lazy page factories. |

### Content transitions and state containers

| Component | Purpose |
|---|---|
| `RXTransitionPane` | Animates each replacement of one arbitrary content node. |
| `RXDualPane` | Switches between two persistent content faces without changing its preferred size. |
| `RXSkeletonPane` | Switches between skeleton and real-content slots. |
| `RXStatePane` | Content/empty/error replacement axis plus an independent loading overlay. |
| `RXPlaceholder` | Graphic, title, description, and action presentation for empty or error states. |
| `RXRipplePane` | Adds bounded ripple and hover feedback to arbitrary content without inventing button semantics. |
| `RXBorderBeam` | Decorative highlights travelling around a content node's border. |
| `RXBackdrop` | Standalone animated dimming and input-blocking layer. |

### Loading, status, and feedback

| Component | Purpose |
|---|---|
| `RXBarSpinner` | Decorative indeterminate activity made from animated bars. |
| `RXDotPulse` | Decorative indeterminate activity made from animated dots. |
| `RXCircularProgressIndicator` | Determinate/indeterminate circular ProgressIndicator with optional center content. |
| `RXWaveProgressIndicator` | Circular water-level ProgressIndicator with animated wave layers. |
| `RXSegmentedProgressBar` | Determinate or indeterminate horizontal segmented progress. |
| `RXSegmentedStepIndicator` | Passive completed/current step presentation with interaction-request events. |
| `RXSkeleton` | Rectangle, circle, or text-line skeleton with optional shimmer. |
| `RXBadge` | Count, text, or dot indicator attached to a content corner or used standalone. |
| `RXRibbonBadge` | Folded text ribbon crossing the top start/end edge of content. |

`RXBursts` is a scene-overlay facade that can play particle bursts from any node or local coordinate; `RXBurstOptions` configures a burst.

### Navigation, disclosure, menus, and overlays

| Component | Purpose |
|---|---|
| `RXBreadcrumbBar<T>` | Root-to-current path with navigation requests, branch menus, and middle-item overflow. |
| `RXSidebar` | Permanent hierarchical sidebar with expanded and mini modes. |
| `RXTabPane` | Tab container backed by styleable `RXTab` models. |
| `RXTitledPane` | Standalone collapsible header/body panel with lazy content support. |
| `RXAccordion` | Coordinates single or multiple expansion across `RXTitledPane` children. |
| `RXMenuList` | Inline list of RXControls menu models. |
| `RXMenuButton` | Menu invoker button. |
| `RXDrawerPane` | Temporary in-scene side drawer over a main content slot. |
| `RXDialog<R>` | In-scene modal overlay dialog with vetoable close and asynchronous result delivery. |
| `RXDialogContent` | Ready-made title/body content for `RXDialog`. |
| `RXDialogContentBase` | Dialog-aware base Region for custom dialog content. |
| `RXSnackbarHost` | Snackbar display, queue, replacement, and dismissal lifecycle host. |

Non-Node facades in this family are `RXPopupMenu`, `RXDialogs`, and `RXSnackbars`.

## Complete public API index

The component catalog above together with the sections below lists every public top-level type in the packages exported by `module-info.java`. Nested types are documented with their owning class in Javadoc.

### Core models, configuration, and support types

- Animation and display configuration: `AnimationTrigger`, `DisplayMode`, `ImageFit`, `ItemsJustify`, `RXPlacement`, `RXSliderIndicatorDisplay`, `RXSliderIndicatorPosition`.
- Scrolling configuration: `ScrollAlignment`, `ScrollAxis`, `ScrollBoundaryPolicy`, `SmoothScrollMode`, `RXSmoothScrollOptions`, `RXSmoothScrollSupport`, `RXSmoothScroller`.
- Dialog configuration and facade: `DialogActionsLayout`, `DialogTransition`, `RXDialogAware`, `RXDialogs`.
- Date/time values: `RXDateRange`, `RXDateTimeRange`, `RXMonthRange`, `RXQuarterRange`, `RXTimeRange`, `RXYearQuarter`, `RXYearRange`.
- Virtual-view support: `CellHeightContext`, `CellHeightProvider`, `RXIndexedSelectionModel`, `RXListSelectionVisualMode`, `RXListSection`, `RXListVisibleRange`, `RXMasonryVisibleBounds`, `RXTileSection`, `RXTileVisibleRange`.
- Cascader models: `RXCascaderItem`, `RXCascaderPath`.
- Kanban models: `RXKanbanColumn`, `RXKanbanCardDropContext`.
- Menu models: `RXMenuItem`, `RXMenuHeader`, `RXMenuSeparator`.
- Segmented-control model: `RXSegmentedItem`.
- Sidebar models: `RXSidebarItemBase`, `RXSidebarItem`, `RXSidebarCaption`, `RXSidebarSeparator`.
- Snackbar models and configuration: `RXSnackbarRequest`, `RXSnackbarSeverity`, `RXSnackbarStrategy`.
- Speed-dial model: `RXSpeedDialAction`.
- Tab models: `RXTab`, `RXTabEvent`, `RXTabPaneSelectionModel`.
- Timeline model: `RXTimelineItem`.
- Animation-button bases: `RXAnimatedButton`, `RXAnimatedLabel`.
- Date/time bases: `RXPickerBase`, `RXTemporalViewBase`.

### Responsive layout support

Package `io.github.leewyatt.rxcontrols.layout`:

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

### Carousel support

Package `io.github.leewyatt.rxcontrols.carousel`:

- `CarouselNavigator`
- `DefaultNavigator`
- `SegmentedProgressNavigator`
- `RXPageLifecycleEvent`

### LRC parsing

Package `io.github.leewyatt.rxcontrols.lrc`:

- `LrcDocument`
- `LrcLine`
- `LrcMetadata`
- `LrcParseResult`
- `LrcParseWarning`
- `LrcParser`
- `LrcWarningCode`

### Spectrum visualization

Package `io.github.leewyatt.rxcontrols.spectrum`:

- `BandLayout`
- `SpectrumContext`
- `SpectrumVisualization`
- `SpectrumVisualizationBase`
- `VisBars`
- `VisBarsMirror`
- `VisLine`
- `VisRadial`

### Burst animations

Package `io.github.leewyatt.rxcontrols.animation.burst`:

- Contracts and frame data: `BurstAnimation`, `BurstFrame`.
- Presets: `BurstAnimBubbles`, `BurstAnimConfetti`, `BurstAnimDots`, `BurstAnimExplosion`, `BurstAnimFireworks`, `BurstAnimHearts`, `BurstAnimRays`, `BurstAnimRing`, `BurstAnimShapes`, `BurstAnimSparkles`.

### Fill animations

Package `io.github.leewyatt.rxcontrols.animation.fill`:

- Contract: `FillAnimation`.
- Presets: `FillAnimBottomToTop`, `FillAnimCenterOut`, `FillAnimCenterOutVertical`, `FillAnimCircle`, `FillAnimCornersIn`, `FillAnimEdgesIn`, `FillAnimEdgesInVertical`, `FillAnimLeftToRight`, `FillAnimRightToLeft`, `FillAnimTopToBottom`, `FillAnimZigzag`, `FillAnimZigzagVertical`.

### Line animations

Package `io.github.leewyatt.rxcontrols.animation.line`:

- Contract and configuration: `LineAnimation`, `LineEdges`, `LineOrigin`.
- Presets: `LineAnimExtend`, `LineAnimSlide`.

### Page transitions

Package `io.github.leewyatt.rxcontrols.animation.page`:

- Contracts and context: `PageAnimation`, `PageAnimationBase`, `TransitionContext`, `TransitionDirection`, `TransitionResolver`.
- Presets: `AnimAccordion`, `AnimAround`, `AnimBarn`, `AnimBlend`, `AnimBlinds`, `AnimBounce`, `AnimBox`, `AnimCards`, `AnimCascade`, `AnimCheckerboard`, `AnimCircle`, `AnimCircleWave`, `AnimCircles`, `AnimComb`, `AnimConveyor`, `AnimCorner`, `AnimCross`, `AnimCube`, `AnimCube4`, `AnimCurtain`, `AnimDiamond`, `AnimDipToColor`, `AnimDissolve`, `AnimDomino`, `AnimDoorway`, `AnimDrain`, `AnimFade`, `AnimFlash`, `AnimFlip`, `AnimFold`, `AnimGallery`, `AnimGaussianBlur`, `AnimGlitch`, `AnimHoneycomb`, `AnimIris`, `AnimLouver`, `AnimMelt`, `AnimMosaic`, `AnimMotionBlur`, `AnimNewspaper`, `AnimNone`, `AnimParallax`, `AnimPeel`, `AnimPinwheel`, `AnimPixelate`, `AnimRandomTiles`, `AnimRectangle`, `AnimReveal`, `AnimRipple`, `AnimRotate`, `AnimSector`, `AnimSelector`, `AnimSerpentine`, `AnimShapeReveal`, `AnimShatter`, `AnimShatterRadial`, `AnimShred`, `AnimShrink`, `AnimShutter`, `AnimSlide`, `AnimSlideIn`, `AnimSpinStrips`, `AnimSpiralTiles`, `AnimSplit`, `AnimSplitWipe`, `AnimSqueeze`, `AnimStack`, `AnimSwap`, `AnimSwing`, `AnimTiltSlide`, `AnimWave`, `AnimWedge`, `AnimWhipPan`, `AnimWind`, `AnimWipe`, `AnimZigzagWipe`, `AnimZoom`.

### Events

Package `io.github.leewyatt.rxcontrols.event`:

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

`RXTabEvent` is exported from the root package, and `RXPageLifecycleEvent` from the carousel package.

### Public skins and skin infrastructure

Package `io.github.leewyatt.rxcontrols.skins` exports the default skins so applications can subclass or replace them when necessary:

- Infrastructure and bases: `RXSkinBase`, `SkinDisposer`, `RXFieldBaseSkin`, `RXMaterialFieldBaseSkin`.
- General controls: `RXAccordionSkin`, `RXAudioSpectrumSkin`, `RXAvatarSkin`, `RXBadgeSkin`, `RXBarSpinnerSkin`, `RXBorderBeamSkin`, `RXBreadcrumbBarSkin`, `RXCarouselSkin`, `RXCircularProgressIndicatorSkin`, `RXDialogSkin`, `RXDigitSkin`, `RXDotPulseSkin`, `RXDrawerPaneSkin`, `RXDualPaneSkin`, `RXPlaceholderSkin`, `RXRibbonBadgeSkin`, `RXSkeletonSkin`, `RXSnackbarHostSkin`, `RXStatePaneSkin`, `RXTimelineViewSkin`, `RXTitledPaneSkin`.
- Buttons and choices: `RXButtonSkin`, `RXCheckBoxSkin`, `RXChipSkin`, `RXChipInputSkin`, `RXChipSetSkin`, `RXCopyButtonSkin`, `RXFillButtonSkin`, `RXFillLabelSkin`, `RXFloatingActionButtonSkin`, `RXHoldButtonSkin`, `RXLineButtonSkin`, `RXLineLabelSkin`, `RXMenuButtonSkin`, `RXMenuListSkin`, `RXRadioButtonSkin`, `RXReactionButtonSkin`, `RXSegmentedControlSkin`, `RXSelectionBoxSkin`, `RXSidebarSkin`, `RXSpeedDialSkin`, `RXSwitchButtonSkin`, `RXTabPaneSkin`, `RXTaskButtonSkin`, `RXToggleButtonSkin`, `RXTransitionButtonSkin`, `RXTransitionLabelSkin`.
- Text and number fields: `RXAutoCompleteFieldSkin`, `RXMaterialPasswordFieldSkin`, `RXMaterialTextFieldSkin`, `RXPasswordFieldSkin`, `RXTextFieldSkin`.
- Date/time controls: `RXCalendarViewSkin`, `RXDatePickerSkin`, `RXDateRangePickerSkin`, `RXDateRangeViewSkin`, `RXDateTimePickerSkin`, `RXDateTimeRangePickerSkin`, `RXDateTimeViewSkin`, `RXMonthPickerSkin`, `RXMonthRangePickerSkin`, `RXMonthRangeViewSkin`, `RXMonthViewSkin`, `RXQuarterPickerSkin`, `RXQuarterRangePickerSkin`, `RXQuarterRangeViewSkin`, `RXQuarterViewSkin`, `RXTimePickerSkin`, `RXTimeRangePickerSkin`, `RXTimeViewSkin`, `RXWeekPickerSkin`, `RXWeekRangePickerSkin`, `RXWeekRangeViewSkin`, `RXWeekViewSkin`, `RXYearPickerSkin`, `RXYearRangePickerSkin`, `RXYearRangeViewSkin`, `RXYearViewSkin`.
- Data views and cells: `RXCascaderSkin`, `RXCascaderViewSkin`, `RXKanbanCardCellSkin`, `RXKanbanViewSkin`, `RXListCellSkin`, `RXListSectionCellSkin`, `RXListViewSkin`, `RXMasonryViewSkin`, `RXTileViewSkin`.
- Media, text, transition, and value controls: `RXHighlightTextViewSkin`, `RXLrcLineViewSkin`, `RXLrcViewSkin`, `RXRangeSliderSkin`, `RXRatingSkin`, `RXSeekBarSkin`, `RXSegmentedProgressBarSkin`, `RXSegmentedStepIndicatorSkin`, `RXSliderSkin`, `RXTextViewSkin`, `RXTransitionPaneSkin`, `RXWaveProgressIndicatorSkin`.

### Utilities and theme integration

- `RXFormat` — shared UI formatting helpers.
- `RXMath` — finite-value cleaning and clamp helpers.
- `RXMouse` — mouse/input helper methods.
- `RXOS` — operating-system identification.
- `RXStyles` — style-class and stylesheet list helpers.
- `RXTreeShowingProperty` — observes whether a node is effectively showing through its ancestor/Scene/Window chain.
- `RXAtlantaFXThemeBridge` — optional per-Scene AtlantaFX color-token bridge.

## Build and test

Compile the core module:

```bash
mvn -pl rxcontrols compile
```

Run the full reactor test suite:

```bash
mvn test
```

Build all modules without installing them:

```bash
mvn package
```

## Project structure

```text
rxcontrols/
├── rxcontrols/          Core library
├── rxcontrols-samples/  Sampler, Samples, and Showcases
├── devdoc/              Design, research, and maintenance documents
├── pom.xml              Maven reactor
└── LICENSE              Apache License 2.0
```

## Stability

The project is preparing its first public release. The component set and primary APIs are in place, while bug fixes and small refinements are still being completed. Until `1.0.0` is published, consumers should pin a source revision when evaluating the library.

## License

RXControls is licensed under the [Apache License 2.0](LICENSE).
