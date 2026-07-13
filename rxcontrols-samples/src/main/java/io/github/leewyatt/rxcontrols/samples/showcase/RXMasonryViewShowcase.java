package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.CellHeightProvider;
import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Showcase for {@link RXMasonryView}. Renders a virtualized wall of {@value #ITEM_COUNT}
 * note cards whose heights stagger into the shortest column, and exposes every visual knob —
 * column width / count / max, gaps, the two height paths (precise {@code cellHeightProvider}
 * vs estimated measure-and-repack), responsive breakpoint column overrides, column span,
 * selection mode, reflow animation and alignment — plus scroll-to-item and a live readout
 * of the resolved column count and visible item range, so virtualization, both height
 * paths and the reorder glide can be exercised at scale.
 */
public class RXMasonryViewShowcase extends RXShowcaseApplication {

    // Opens instantly and stays responsive at every knob (including the O(N) resize /
    // column-count rebuild). Bump locally to stress-test the virtualization at scale.
    private static final int ITEM_COUNT = 10_000;
    // "Inherit" selection value for the per-breakpoint column ComboBox.
    private static final int CONTROL_INHERIT = -1;
    private static final double APPROX_LINE_HEIGHT = 21.0;
    private static final double TILE_CHROME_HEIGHT = 58.0;
    private static final String CELL_STYLE = "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 12;";

    private RXMasonryView<Integer> masonry;
    private CellHeightProvider<Integer> provider;
    private final Label actionStatus = new Label("Click to select · double-click to open");

    private static int paragraphs(int index) {
        return 1 + (index % 5);
    }

    private static boolean featured(int index) {
        return index % 9 == 0;
    }

    @Override
    protected String title() {
        return "RXMasonryView";
    }

    @Override
    protected String subtitle() {
        return ITEM_COUNT + " virtualized waterfall tiles — toggle the height path or change the columns";
    }

    @Override
    protected String windowTitle() {
        return "RXMasonryView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 430.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-masonry-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(i);
        }

        masonry = new RXMasonryView<>(items);
        masonry.setColumnWidth(220.0);
        masonry.setCellFactory(view -> new NoteCell());
        masonry.setColumnSpanFactory(index -> featured(index) ? 2 : 1);
        masonry.setOnAction(event -> actionStatus.setText("Opened: Note " + (event.getItem() + 1)));

        // The precise path: a per-item height approximating the rendered paragraph.
        provider = context -> TILE_CHROME_HEIGHT + paragraphs(context.index()) * APPROX_LINE_HEIGHT * 2.0;
        masonry.setCellHeightProvider(provider);

        Label placeholder = new Label("No notes");
        placeholder.getStyleClass().add("masonry-placeholder");
        masonry.setPlaceholder(placeholder);
        return masonry;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Columns", columnsGrid()),
                section("Gaps", gapsGrid()),
                section("Height path", heightGrid()),
                section("Responsive (breakpoints, 0 = auto)", responsiveGrid()),
                section("Selection", selectionGrid()),
                section("Animation", animationGrid()),
                section("Smooth scroll", smoothScrollGrid()),
                section("Layout", layoutGrid()),
                section("Scroll", scrollGrid()),
                section("Metrics", metricsGrid()));
    }

    // ==================== Sections ====================

    private Node columnsGrid() {
        Slider columnWidth = createSlider(120.0, 400.0, masonry.getColumnWidth());
        masonry.columnWidthProperty().bind(columnWidth.valueProperty());

        Slider columnCount = intSlider(0, 8, masonry.getColumnCount());
        columnCount.valueProperty().addListener((obs, old, value) -> masonry.setColumnCount(value.intValue()));

        Slider maxColumns = intSlider(0, 10, masonry.getMaxColumns());
        maxColumns.valueProperty().addListener((obs, old, value) -> masonry.setMaxColumns(value.intValue()));

        CheckBox fillWidth = new CheckBox("Stretch columns to fill the width");
        fillWidth.setSelected(masonry.isFillWidth());
        masonry.fillWidthProperty().bind(fillWidth.selectedProperty());

        CheckBox span = new CheckBox("Featured notes span two columns");
        span.setSelected(true);
        span.selectedProperty().addListener((obs, old, on) ->
                masonry.setColumnSpanFactory(on ? index -> featured(index) ? 2 : 1 : null));

        return createGrid(
                row("Column width", columnWidth, createValueLabel(columnWidth, "%.0f px")),
                row("Column count", columnCount, sentinelLabel(columnCount, "auto")),
                row("Max columns", maxColumns, sentinelLabel(maxColumns, "none")),
                row(fillWidth),
                row(span));
    }

    private Node gapsGrid() {
        Slider hgap = createSlider(0.0, 40.0, masonry.getHgap());
        masonry.hgapProperty().bind(hgap.valueProperty());
        Slider vgap = createSlider(0.0, 40.0, masonry.getVgap());
        masonry.vgapProperty().bind(vgap.valueProperty());
        return createGrid(
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")));
    }

    private Node heightGrid() {
        CheckBox measured = new CheckBox("Measured heights (estimated path)");
        measured.setSelected(false);
        measured.selectedProperty().addListener((obs, old, value) ->
                masonry.setCellHeightProvider(value ? null : provider));

        Slider estimated = createSlider(60.0, 360.0, masonry.getEstimatedCellHeight());
        masonry.estimatedCellHeightProperty().bind(estimated.valueProperty());

        return createGrid(
                row(measured),
                row("Estimate", estimated, createValueLabel(estimated, "%.0f px")),
                row(hint("Unchecked: exact height from a cellHeightProvider (never jumps). Checked: the skin "
                        + "estimates, measures each realized cell and re-packs to converge — the estimate is the "
                        + "placeholder before a cell is measured.")));
    }

    private Node responsiveGrid() {
        ComboBox<RXBreakpointProfile> profile = new ComboBox<>(FXCollections.observableArrayList(
                RXBreakpointProfile.ANT_DESIGN, RXBreakpointProfile.ELEMENT, RXBreakpointProfile.BOOTSTRAP));
        profile.setValue(masonry.getBreakpointProfile());
        profile.setMaxWidth(Double.MAX_VALUE);
        profile.setConverter(converter(this::profileName));
        masonry.breakpointProfileProperty().bind(profile.valueProperty());

        Label active = new Label();
        active.getStyleClass().add("resolved-label");
        active.textProperty().bind(Bindings.createStringBinding(
                () -> "Active breakpoint: " + breakpointName(masonry.getActiveBreakpoint()),
                masonry.activeBreakpointProperty()));

        return createGrid(
                row("Profile", profile, new Label()),
                breakpointRow("xs", masonry::setXs),
                breakpointRow("sm", masonry::setSm),
                breakpointRow("md", masonry::setMd),
                breakpointRow("lg", masonry::setLg),
                breakpointRow("xl", masonry::setXl),
                breakpointRow("xxl", masonry::setXxl),
                breakpointRow("xxxl", masonry::setXxxl),
                row(active));
    }

    private Node[] breakpointRow(String name, Consumer<Integer> setter) {
        ComboBox<Integer> box = new ComboBox<>(FXCollections.observableArrayList(
                CONTROL_INHERIT, RXMasonryView.AUTO_COLUMNS, 1, 2, 3, 4, 5, 6, 7, 8));
        box.setValue(CONTROL_INHERIT);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(converter(value -> {
            if (value == null || value == CONTROL_INHERIT) {
                return "Inherit";
            }
            return value == RXMasonryView.AUTO_COLUMNS ? "Auto" : String.valueOf(value);
        }));
        box.valueProperty().addListener((obs, old, value) ->
                setter.accept(value == null || value == CONTROL_INHERIT ? null : value));
        return row(name + " columns", box, new Label());
    }

    private Node selectionGrid() {
        ComboBox<SelectionMode> mode = new ComboBox<>(
                FXCollections.observableArrayList(SelectionMode.SINGLE, SelectionMode.MULTIPLE));
        mode.setValue(masonry.getSelectionModel().getSelectionMode());
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.valueProperty().addListener((obs, old, value) -> masonry.getSelectionModel().setSelectionMode(value));
        return createGrid(
                row("Mode", mode, new Label()),
                row(hint("Click, arrow-navigate, Shift / Ctrl extend; Enter or double-click activates. "
                        + "In MULTIPLE, drag from blank space to marquee-select; Escape cancels.")),
                row(actionStatus));
    }

    private Node animationGrid() {
        CheckBox animated = new CheckBox("Glide tiles on a column-count change");
        animated.setSelected(masonry.isAnimated());
        masonry.animatedProperty().bind(animated.selectedProperty());

        Slider duration = createSlider(0.0, 600.0, masonry.getAnimationDuration().toMillis());
        duration.valueProperty().addListener((obs, old, value) ->
                masonry.setAnimationDuration(Duration.millis(Math.round(value.doubleValue()))));

        ComboBox<Interpolator> interpolator = new ComboBox<>(FXCollections.observableArrayList(
                Interpolator.EASE_BOTH, Interpolator.EASE_OUT, Interpolator.EASE_IN, Interpolator.LINEAR));
        interpolator.setValue(masonry.getAnimationInterpolator());
        interpolator.setMaxWidth(Double.MAX_VALUE);
        interpolator.setConverter(converter(this::interpolatorName));
        masonry.animationInterpolatorProperty().bind(interpolator.valueProperty());

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")),
                row("Interpolator", interpolator, new Label()));
    }

    private Node smoothScrollGrid() {
        CheckBox enabled = new CheckBox("Smooth wheel scrolling");
        enabled.setSelected(masonry.isSmoothScrolling());
        enabled.selectedProperty().addListener((obs, old, value) -> masonry.setSmoothScrolling(value));

        ComboBox<SmoothScrollMode> mode = new ComboBox<>(
                FXCollections.observableArrayList(SmoothScrollMode.values()));
        mode.setValue(masonry.getSmoothScrollMode());
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.valueProperty().addListener((obs, old, value) -> masonry.setSmoothScrollMode(value));

        return createGrid(
                row(enabled),
                row("Mode", mode, new Label()));
    }

    private Node layoutGrid() {
        ComboBox<Pos> alignment = new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignment.setValue(masonry.getAlignment());
        alignment.setMaxWidth(Double.MAX_VALUE);
        masonry.alignmentProperty().bind(alignment.valueProperty());
        return createGrid(
                row("Alignment", alignment, new Label()),
                row(hint("Horizontal only (the columns position within the content width when not filling).")));
    }

    private Node scrollGrid() {
        TextField index = new TextField("5000");
        index.setPrefColumnCount(6);
        ComboBox<ScrollAlignment> alignment = new ComboBox<>(FXCollections.observableArrayList(ScrollAlignment.values()));
        alignment.setValue(ScrollAlignment.START);
        Button go = new Button("Scroll to item");
        go.setOnAction(e -> scrollToItem(index.getText(), alignment.getValue()));
        HBox box = new HBox(8.0, index, alignment, go);
        box.setAlignment(Pos.CENTER_LEFT);
        return createGrid(row(box));
    }

    private void scrollToItem(String text, ScrollAlignment alignment) {
        try {
            masonry.scrollTo(Integer.parseInt(text.trim()), alignment);
        } catch (NumberFormatException ignored) {
            // Leave an unparseable index alone rather than disrupting the view.
        }
    }

    private Node metricsGrid() {
        Label columns = new Label();
        columns.textProperty().bind(masonry.actualColumnCountProperty().asString());
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(
                () -> "items " + masonry.getFirstVisibleIndex() + ".." + masonry.getLastVisibleIndex(),
                masonry.firstVisibleIndexProperty(), masonry.lastVisibleIndexProperty()));
        range.setWrapText(true);
        return createGrid(
                row("Columns", columns),
                row("Visible", range));
    }

    // ==================== Helpers ====================

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private String profileName(RXBreakpointProfile profile) {
        if (profile == RXBreakpointProfile.ANT_DESIGN) {
            return "ANT DESIGN (xs..xxxl)";
        }
        if (profile == RXBreakpointProfile.ELEMENT) {
            return "ELEMENT (xs..xl)";
        }
        if (profile == RXBreakpointProfile.BOOTSTRAP) {
            return "BOOTSTRAP (xs..xxl)";
        }
        return String.valueOf(profile);
    }

    private String breakpointName(RXBreakpoint breakpoint) {
        return breakpoint == null ? "—" : breakpoint.cssName();
    }

    private String interpolatorName(Interpolator interpolator) {
        if (interpolator == Interpolator.EASE_BOTH) {
            return "EASE_BOTH";
        }
        if (interpolator == Interpolator.EASE_OUT) {
            return "EASE_OUT";
        }
        if (interpolator == Interpolator.EASE_IN) {
            return "EASE_IN";
        }
        if (interpolator == Interpolator.LINEAR) {
            return "LINEAR";
        }
        return String.valueOf(interpolator);
    }

    private Slider intSlider(int min, int max, int value) {
        Slider slider = createSlider(min, max, value);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);
        return slider;
    }

    private Label sentinelLabel(Slider slider, String zeroText) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.textProperty().bind(Bindings.createStringBinding(() -> {
            int v = (int) Math.round(slider.getValue());
            return v <= 0 ? zeroText : Integer.toString(v);
        }, slider.valueProperty()));
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    /**
     * A tile that paints a rounded card tinted by the note's index, with the title and a
     * paragraph of its own length wrapped inside; featured notes carry a marker and span
     * two columns.
     */
    private static final class NoteCell extends RXMasonryCell<Integer> {

        private NoteCell() {
            setAlignment(Pos.TOP_LEFT);
            setWrapText(true);
            setStyle(CELL_STYLE);
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(CELL_STYLE);
            } else {
                setText(blurb(item));
                double hue = (item * 37.0) % 360.0;
                String saturation = featured(item) ? "70%" : "55%";
                setStyle(CELL_STYLE + " -fx-background-color: hsb(" + hue + ", " + saturation + ", 70%);"
                        + " -fx-background-radius: 12; -fx-border-radius: 12;");
            }
        }

        private static String blurb(int index) {
            StringBuilder builder = new StringBuilder(featured(index) ? "★ " : "").append("Note ").append(index + 1);
            for (int i = 0; i < paragraphs(index); i++) {
                builder.append("\n\nLorem ipsum dolor sit amet, consectetur adipiscing elit.");
            }
            return builder.toString();
        }
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
