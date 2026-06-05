package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Showcase application for {@link RXCascader}.
 *
 * <p>Exercises the public knobs: single vs multiple selection, the clear
 * affordance, the path-to-text factory (full path / last level / first-to-last),
 * the visible-row-count, and CSS sizing presets (column width / row height are
 * controlled by CSS via {@code .rx-cascader-column}, not Java properties). The
 * sample tree contains a disabled leaf so the locked tri-state rollup is directly
 * observable: checking its enabled siblings leaves the ancestors indeterminate.
 *
 * <p>The value type is an {@link Option} record carrying id + label; the visible
 * node text comes from {@code setTextFactory(Option::label)}. The path field
 * formatter ({@code pathTextFactory}) then receives the already-resolved per-node
 * texts.
 *
 * <p>For a minimal "few lines of code" example see {@link RXCascaderDemo}.
 */
public class RXCascaderShowcase extends RXShowcaseApplication {

    private static final double MIN_VISIBLE_ROWS = 3.0;
    private static final double MAX_VISIBLE_ROWS = 10.0;
    private static final String SEPARATOR = " / ";
    private static final long LAZY_LOAD_DELAY_MILLIS = 800L;

    private RXCascader<Option> cascader;
    private RXCascader<Option> lazyCascader;
    private CheckBox failLoadsBox;
    private Label lazyReadout;

    /**
     * Backend-style value carrying an id and a display label.
     *
     * @param id stable identifier
     * @param label human-facing text rendered by {@code textFactory}
     */
    public record Option(String id, String label) {
    }

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCascader";
    }

    @Override
    protected String subtitle() {
        return "Cascading multi-column selector";
    }

    @Override
    protected String windowTitle() {
        return "RXCascader Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-cascader-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        cascader = new RXCascader<>();
        cascader.setPromptText("Choose a location");
        cascader.setClearable(true);
        cascader.setTextFactory(Option::label);
        cascader.setPathTextFactory(pathFactory(PathFormat.FULL_PATH));
        cascader.getRootItems().setAll(sampleOptions());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                this::describeSelection,
                cascader.selectedPathProperty(),
                cascader.getCheckedPaths(),
                cascader.selectionModeProperty()));

        VBox box = new VBox(16.0, cascader, readout);
        box.getStyleClass().add("cascader-preview");
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", buildSelectionGrid()),
                section("Display text", buildDisplayGrid()),
                section("Dimensions", buildDimensionGrid()),
                section("Lazy loading", buildLazyGrid()));
    }

    // ==================== Sections ====================

    private Node buildSelectionGrid() {
        ComboBox<RXCascaderSelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(RXCascaderSelectionMode.values());
        modeBox.setValue(cascader.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        cascader.selectionModeProperty().bind(modeBox.valueProperty());

        CheckBox clearableBox = new CheckBox("Show clear button");
        clearableBox.selectedProperty().bindBidirectional(cascader.clearableProperty());

        CheckBox customCell = new CheckBox("Custom cell (colored dot + text)");
        customCell.selectedProperty().addListener((obs, was, on) ->
                cascader.setCellFactory(on ? DotCell::new : null));

        Label hint = new Label("\"Disabled City\" under Asia / China is a locked "
                + "leaf. In multiple mode it keeps China and Asia indeterminate "
                + "even when every enabled sibling is checked.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(clearableBox),
                row(customCell),
                row(hint));
    }

    private Node buildDisplayGrid() {
        ComboBox<PathFormat> formatBox = new ComboBox<>();
        formatBox.getItems().setAll(PathFormat.values());
        formatBox.setValue(PathFormat.FULL_PATH);
        formatBox.setMaxWidth(Double.MAX_VALUE);
        formatBox.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setPathTextFactory(newV == null ? null : pathFactory(newV)));

        return createGrid(row("Path text", formatBox));
    }

    /**
     * Wraps a {@link PathFormat} into a path-text factory: it resolves each
     * node's display text through the cascader's {@code textFactory} (via
     * {@code getPathTexts}, so the field stays consistent with the columns) and
     * lets the format join them.
     */
    private Callback<RXCascaderPath<Option>, String> pathFactory(PathFormat format) {
        return path -> format.format(cascader.getView().getPathTexts(path));
    }

    private Node buildDimensionGrid() {
        Slider visibleRows = createSlider(MIN_VISIBLE_ROWS, MAX_VISIBLE_ROWS, cascader.getVisibleRowCount());
        visibleRows.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setVisibleRowCount((int) Math.round(newV.doubleValue())));
        Label visibleValue = createValueLabel(visibleRows, "%.0f");

        ComboBox<SizePreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(SizePreset.values());
        presetBox.setValue(SizePreset.DEFAULT);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> applyPreset(newV));

        Label hint = new Label("Column width / row height are controlled by CSS "
                + "(.rx-cascader-column / .rx-cascader-column-N). The preset toggles "
                + "demo style classes that override them.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Visible rows", visibleRows, visibleValue),
                row("CSS preset", presetBox),
                row(hint));
    }

    private void applyPreset(SizePreset preset) {
        RXCascaderView<Option> view = cascader.getView();
        view.getStyleClass().removeAll(SizePreset.WIDE_COL2.styleClass(), SizePreset.TALL_ROWS.styleClass());
        if (preset != null && !preset.styleClass().isEmpty()) {
            view.getStyleClass().add(preset.styleClass());
        }
    }

    // ==================== Lazy loading ====================

    private Node buildLazyGrid() {
        lazyReadout = new Label("Expand \"Remote Source\" to load its children.");
        lazyReadout.getStyleClass().add("field-readout");
        lazyReadout.setWrapText(true);

        lazyCascader = new RXCascader<>();
        lazyCascader.setPromptText("Expand to load");
        lazyCascader.setClearable(true);
        lazyCascader.setTextFactory(Option::label);
        lazyCascader.setPathTextFactory(path ->
                String.join(SEPARATOR, lazyCascader.getView().getPathTexts(path)));
        lazyCascader.setOnChildrenLoadError((failedItem, error) -> lazyReadout.setText(
                "Load failed for \"" + failedItem.getValue().label() + "\": " + error.getMessage()
                        + "\nUncheck \"Fail loads\" and click the row again to retry."));
        lazyCascader.selectedPathProperty().addListener((obs, oldPath, newPath) -> {
            if (newPath != null) {
                lazyReadout.setText("selected: "
                        + String.join(SEPARATOR, lazyCascader.getView().getPathTexts(newPath)));
            }
        });
        // Configure the loader before seeding roots (see RXCascader Javadoc): a
        // non-null loader resets the tree, so roots provided afterwards survive.
        lazyCascader.setChildrenLoader(lazyLoader());
        lazyCascader.getRootItems().setAll(lazyRoots());

        failLoadsBox = new CheckBox("Fail loads (simulate a loader error)");

        Button reload = new Button("Reload");
        reload.setMaxWidth(Double.MAX_VALUE);
        reload.setOnAction(event -> {
            lazyCascader.reload();
            lazyReadout.setText("Reloaded — expand \"Remote Source\" to fetch again.");
        });

        ComboBox<LoaderMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(LoaderMode.values());
        modeBox.setValue(LoaderMode.LAZY);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldMode, newMode) -> applyLoaderMode(newMode));

        Label hint = new Label("A children loader makes this a lazy tree: unloaded "
                + "nodes stay branches until expanded. \"Reload\" resets and "
                + "refetches from the same loader. Switching to Eager clears the "
                + "loader and keeps the current tree as a static one; switching "
                + "back to Lazy resets the tree.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(lazyCascader),
                row(failLoadsBox),
                row(reload),
                row(lazyReadout),
                row(hint));
    }

    private void applyLoaderMode(LoaderMode mode) {
        if (mode == null || mode == LoaderMode.LAZY) {
            // A fresh non-null loader resets the tree; re-seed the roots after.
            lazyCascader.setChildrenLoader(lazyLoader());
            lazyCascader.getRootItems().setAll(lazyRoots());
            lazyReadout.setText("Lazy mode — expand \"Remote Source\" to load.");
        } else {
            // Clearing the loader keeps the current tree as a static eager one.
            lazyCascader.setChildrenLoader(null);
            lazyReadout.setText("Eager mode — loader cleared, current tree kept static.");
        }
    }

    private Function<RXCascaderItem<Option>, CompletionStage<List<RXCascaderItem<Option>>>> lazyLoader() {
        return loadItem -> {
            boolean fail = failLoadsBox.isSelected();
            Option value = loadItem.getValue();
            return CompletableFuture.supplyAsync(() -> {
                sleepBriefly();
                if (fail) {
                    throw new IllegalStateException("simulated network error");
                }
                if ("source".equals(value.id())) {
                    return List.of(item("group-a", "Group A"), item("group-b", "Group B"));
                }
                return List.of(
                        leaf(value.id() + "-1", value.label() + " 1"),
                        leaf(value.id() + "-2", value.label() + " 2"));
            });
        };
    }

    private static List<RXCascaderItem<Option>> lazyRoots() {
        // Unloaded with a loader set: a branch by Default B, no flags needed.
        return List.of(item("source", "Remote Source"));
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(LAZY_LOAD_DELAY_MILLIS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Readout ====================

    private String describeSelection() {
        if (cascader.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
            List<RXCascaderPath<Option>> checked = cascader.getCheckedPaths();
            if (checked.isEmpty()) {
                return "checked: (none)";
            }
            StringJoiner joiner = new StringJoiner("\n");
            for (RXCascaderPath<Option> path : checked) {
                joiner.add("- " + String.join(SEPARATOR, cascader.getView().getPathTexts(path)));
            }
            return "checked (" + checked.size() + "):\n" + joiner;
        }
        RXCascaderPath<Option> path = cascader.getSelectedPath();
        if (path == null) {
            return "selected: (none)";
        }
        return "selected: " + String.join(SEPARATOR, cascader.getView().getPathTexts(path));
    }

    // ==================== Sample data ====================

    private static List<RXCascaderItem<Option>> sampleOptions() {
        RXCascaderItem<Option> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisabled(true);

        RXCascaderItem<Option> china = item("china", "China");
        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity));

        RXCascaderItem<Option> japan = item("japan", "Japan");
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")));

        RXCascaderItem<Option> asia = item("asia", "Asia");
        asia.getChildren().setAll(List.of(china, japan));

        RXCascaderItem<Option> germany = item("germany", "Germany");
        germany.getChildren().setAll(List.of(
                item("berlin", "Berlin"),
                item("munich", "Munich")));

        RXCascaderItem<Option> europe = item("europe", "Europe");
        europe.getChildren().setAll(List.of(germany));

        return List.of(asia, europe);
    }

    private static RXCascaderItem<Option> item(String id, String label) {
        return new RXCascaderItem<>(new Option(id, label));
    }

    private static RXCascaderItem<Option> leaf(String id, String label) {
        RXCascaderItem<Option> leaf = item(id, label);
        leaf.setLeafHint(true);
        return leaf;
    }

    // ==================== Custom cell ====================

    /**
     * Cell that overrides only the content area with a colored dot plus the item
     * text, keeping the built-in check box / arrow / loading and interaction.
     *
     * @param <T> application value type
     */
    private static final class DotCell<T> extends RXCascaderCell<T> {

        private DotCell(RXCascaderView<T> view) {
            super(view);
        }

        @Override
        protected Node createContent(RXCascaderItem<T> item) {
            Region dot = new Region();
            dot.getStyleClass().add("demo-cell-dot");
            dot.setMinSize(8.0, 8.0);
            dot.setPrefSize(8.0, 8.0);
            dot.setMaxSize(8.0, 8.0);
            HBox box = new HBox(8.0, dot, new Label(getDisplayText(item.getValue())));
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }
    }

    // ==================== Path format ====================

    private enum PathFormat {
        FULL_PATH("Full path (A / B / C)") {
            @Override
            String format(List<String> texts) {
                return String.join(SEPARATOR, texts);
            }
        },
        LAST_LEVEL("Last level only (C)") {
            @Override
            String format(List<String> texts) {
                return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
            }
        },
        FIRST_TO_LAST("First -> last (A -> C)") {
            @Override
            String format(List<String> texts) {
                if (texts.isEmpty()) {
                    return "";
                }
                String first = texts.get(0);
                String last = texts.get(texts.size() - 1);
                return first.equals(last) ? first : first + " -> " + last;
            }
        };

        private final String label;

        PathFormat(String label) {
            this.label = label;
        }

        abstract String format(List<String> texts);

        @Override
        public String toString() {
            return label;
        }
    }

    // ==================== Loader mode ====================

    private enum LoaderMode {
        LAZY("Lazy (loader on)"),
        EAGER("Eager (loader off)");

        private final String label;

        LoaderMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ==================== Size preset ====================

    private enum SizePreset {
        DEFAULT("Default", ""),
        WIDE_COL2("Second column 300px", "demo-wide-col2"),
        TALL_ROWS("Row height 44px", "demo-tall");

        private final String label;
        private final String styleClass;

        SizePreset(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }

        String styleClass() {
            return styleClass;
        }

        @Override
        public String toString() {
            return label;
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
