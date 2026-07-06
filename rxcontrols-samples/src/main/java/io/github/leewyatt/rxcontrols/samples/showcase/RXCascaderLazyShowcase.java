package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Showcase application for lazy-loaded {@link RXCascader} trees.
 *
 * <p>Exercises the asynchronous children loader, load failure callback, reload
 * operation, eager/lazy switching, selection mode, and custom cells.
 */
public class RXCascaderLazyShowcase extends RXShowcaseApplication {

    private static final long LAZY_LOAD_DELAY_MILLIS = 800L;

    private RXCascader<CascaderOption> cascader;
    private CheckBox failLoadsBox;
    private Label statusLabel;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCascader Lazy";
    }

    @Override
    protected String subtitle() {
        return "Async children loading and retry behavior";
    }

    @Override
    protected String windowTitle() {
        return "RXCascader Lazy Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-cascader-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        failLoadsBox = new CheckBox("Fail loads (simulate a loader error)");

        statusLabel = new Label("Lazy mode - expand \"Remote Source\" to load.");
        statusLabel.getStyleClass().add("field-readout");
        statusLabel.setWrapText(true);

        cascader = new RXCascader<>();
        cascader.setMaxWidth(Double.MAX_VALUE);
        cascader.setPromptText("Expand to load");
        cascader.setClearable(true);
        cascader.setItemTextFactory(CascaderOption::label);
        cascader.setPathTextFactory(path -> String.join(
                CascaderShowcaseSupport.SEPARATOR,
                CascaderShowcaseSupport.pathTexts(cascader.getItemTextFactory(), path)));
        cascader.setOnChildrenLoadError((failedItem, error) -> statusLabel.setText(
                "Load failed for \"" + failedItem.getValue().label() + "\": " + errorMessage(error)
                        + "\nUncheck \"Fail loads\" and click the row again to retry."));
        cascader.setChildrenLoader(lazyLoader());
        cascader.getRootItems().setAll(CascaderShowcaseSupport.lazyRoots());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> CascaderShowcaseSupport.describeSelection(cascader),
                cascader.selectedPathProperty(),
                cascader.getCheckedPaths(),
                cascader.selectionModeProperty()));

        VBox box = new VBox(16.0, cascader, statusLabel, readout);
        box.getStyleClass().add("cascader-preview");
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Loading", buildLoadingGrid()),
                section("Selection", buildSelectionGrid()));
    }

    // ==================== Sections ====================

    private Node buildLoadingGrid() {
        ComboBox<LoaderMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(LoaderMode.values());
        modeBox.setValue(LoaderMode.LAZY);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldMode, newMode) -> applyLoaderMode(newMode));

        Button reload = new Button("Reload");
        reload.setMaxWidth(Double.MAX_VALUE);
        reload.setOnAction(event -> {
            cascader.reload();
            statusLabel.setText("Reloaded - expand \"Remote Source\" to fetch again.");
        });

        Label hint = new Label("Unloaded nodes stay expandable while a loader is "
                + "set. Reload resets the lazy tree and keeps the same loader. "
                + "Eager mode clears the loader and keeps the current tree static.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(failLoadsBox),
                row(reload),
                row(hint));
    }

    private Node buildSelectionGrid() {
        ComboBox<SelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(SelectionMode.values());
        modeBox.setValue(cascader.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        cascader.selectionModeProperty().bind(modeBox.valueProperty());

        CheckBox clearableBox = new CheckBox("Show clear button");
        clearableBox.selectedProperty().bindBidirectional(cascader.clearableProperty());

        CheckBox customCell = new CheckBox("Custom cell (colored dot + text)");
        customCell.selectedProperty().addListener((obs, was, on) ->
                cascader.setCellFactory(on ? CascaderShowcaseSupport.DotCell::new : null));

        Button clearButton = new Button("Clear selection");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(event -> cascader.clearSelection());

        return createGrid(
                row("Mode", modeBox),
                row(clearableBox),
                row(customCell),
                row(clearButton));
    }

    // ==================== Loading ====================

    private void applyLoaderMode(LoaderMode mode) {
        if (mode == null || mode == LoaderMode.LAZY) {
            cascader.setChildrenLoader(lazyLoader());
            cascader.getRootItems().setAll(CascaderShowcaseSupport.lazyRoots());
            statusLabel.setText("Lazy mode - expand \"Remote Source\" to load.");
        } else {
            cascader.setChildrenLoader(null);
            statusLabel.setText("Eager mode - loader cleared, current tree kept static.");
        }
    }

    private Function<RXCascaderItem<CascaderOption>, CompletionStage<List<RXCascaderItem<CascaderOption>>>>
            lazyLoader() {
        return loadItem -> {
            boolean fail = failLoadsBox.isSelected();
            CascaderOption value = loadItem.getValue();
            return CompletableFuture.supplyAsync(() -> {
                sleepBriefly();
                if (fail) {
                    throw new IllegalStateException("simulated network error");
                }
                if ("source".equals(value.id())) {
                    return List.of(
                            CascaderShowcaseSupport.item("group-a", "Group A"),
                            CascaderShowcaseSupport.item("group-b", "Group B"));
                }
                return List.of(
                        CascaderShowcaseSupport.leaf(value.id() + "-1", value.label() + " 1"),
                        CascaderShowcaseSupport.leaf(value.id() + "-2", value.label() + " 2"));
            });
        };
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(LAZY_LOAD_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
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

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
