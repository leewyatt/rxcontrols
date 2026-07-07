package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXSelectionBoxSkin;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless (window-independent) tests for {@link RXSelectionBox}: the control's
 * selection model, popup lifecycle state and gating, property defaults, and the
 * skin's display summary and pseudo-class rendering. Window-dependent popup
 * behavior (keyboard navigation, source-index bridge, click toggle) is covered in
 * {@code RXSelectionBoxPopupTest}.
 */
public class RXSelectionBoxTest {

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass SINGLE = PseudoClass.getPseudoClass("single");
    private static final PseudoClass MULTIPLE = PseudoClass.getPseudoClass("multiple");
    private static final PseudoClass READONLY = PseudoClass.getPseudoClass("readonly");
    private static final PseudoClass SEARCHABLE = PseudoClass.getPseudoClass("searchable");
    private static final PseudoClass FILTERED = PseudoClass.getPseudoClass("filtered");

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Defaults ====================

    @Test
    public void defaultsAreSingleSelectableSearchableAndClosed() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        assertTrue(box.getStyleClass().contains("rx-selection-box"));
        assertEquals(SelectionMode.SINGLE, box.getSelectionMode());
        assertFalse(box.isShowing());
        assertTrue(box.isSearchable());
        assertTrue(box.isAutoHideOnSelection());
        assertTrue(box.isClearSearchOnHide());
        assertFalse(box.isReadOnly());
        assertEquals(8, box.getMaxVisibleRows());
        assertNotNull(box.getSelectionModel());
        assertEquals("", box.getSearchText());
    }

    // ==================== Popup lifecycle state ====================

    @Test
    public void showAndHideUpdateShowingState() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        assertFalse(box.isShowing());
        box.show();
        assertTrue(box.isShowing());
        box.hide();
        assertFalse(box.isShowing());
    }

    @Test
    public void showIsIgnoredWhenDisabled() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        box.setDisable(true);
        box.show();
        assertFalse(box.isShowing());
    }

    @Test
    public void showIsIgnoredWhenReadOnly() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        box.setReadOnly(true);
        box.show();
        assertFalse(box.isShowing());
    }

    // ==================== Selection model ====================

    @Test
    public void singleSelectionReplaces() {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "c"));
        box.getSelectionModel().select(0);
        assertEquals("a", box.getSelectedItem());
        box.getSelectionModel().select(2);
        assertEquals("c", box.getSelectedItem());
        assertEquals(List.of("c"), new ArrayList<>(box.getSelectedItems()));
    }

    @Test
    public void multipleSelectionAccumulates() {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "c"));
        box.setSelectionMode(SelectionMode.MULTIPLE);
        box.getSelectionModel().select(0);
        box.getSelectionModel().select(2);
        assertEquals(List.of(0, 2), new ArrayList<>(box.getSelectionModel().getSelectedIndices()));
        assertEquals(List.of("a", "c"), new ArrayList<>(box.getSelectedItems()));
    }

    @Test
    public void selectionModeIsPushedToTheModel() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        box.setSelectionMode(SelectionMode.MULTIPLE);
        assertEquals(SelectionMode.MULTIPLE, box.getSelectionModel().getSelectionMode());
    }

    @Test
    public void selectionModeIsPushedToASwappedModel() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        box.setSelectionMode(SelectionMode.MULTIPLE);
        RXIndexedSelectionModel<String> replacement = new RXIndexedSelectionModel<>(box.itemsProperty());
        box.setSelectionModel(replacement);
        assertSame(replacement, box.getSelectionModel());
        assertEquals(SelectionMode.MULTIPLE, replacement.getSelectionMode());
    }

    @Test
    public void nullSelectionModeCoercesToSingleOnModelSwap() {
        RXSelectionBox<String> box = new RXSelectionBox<>();
        box.setSelectionMode(null);
        assertEquals(SelectionMode.SINGLE, box.getSelectionModel().getSelectionMode(),
                "null mode is coerced to SINGLE on the current model");

        RXIndexedSelectionModel<String> replacement = new RXIndexedSelectionModel<>(box.itemsProperty());
        box.setSelectionModel(replacement);
        assertEquals(SelectionMode.SINGLE, replacement.getSelectionMode(),
                "null mode is coerced to SINGLE when the model is swapped");
    }

    @Test
    public void clearSelectionEmptiesTheModel() {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b"));
        box.getSelectionModel().select(0);
        box.clearSelection();
        assertTrue(box.getSelectedItems().isEmpty());
        assertNull(box.getSelectedItem());
    }

    @Test
    public void selectAllOnlyAppliesInMultipleMode() {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "c"));
        box.selectAll();
        assertTrue(box.getSelectedItems().isEmpty(), "selectAll is a no-op in single mode");

        box.setSelectionMode(SelectionMode.MULTIPLE);
        box.selectAll();
        assertEquals(List.of("a", "b", "c"), new ArrayList<>(box.getSelectedItems()));
    }

    @Test
    public void duplicateItemsAreSelectableByIndex() {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "a"));
        box.setSelectionMode(SelectionMode.MULTIPLE);
        box.getSelectionModel().select(0);
        box.getSelectionModel().select(2);
        assertEquals(List.of(0, 2), new ArrayList<>(box.getSelectionModel().getSelectedIndices()));
        assertEquals(List.of("a", "a"), new ArrayList<>(box.getSelectedItems()));
    }

    // ==================== CSS metadata ====================

    @Test
    public void rippleStyleablesAreExposed() {
        List<String> names = RXSelectionBox.getClassCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .collect(Collectors.toList());
        assertTrue(names.contains("-rx-ripple-fill"));
        assertTrue(names.contains("-rx-ripple-opacity"));
        assertTrue(names.contains("-rx-ripple-enabled"));
        assertTrue(names.contains("-rx-ripple-state-overlay-enabled"));
    }

    // ==================== Display summary (skinned) ====================

    @Test
    public void summaryShowsPromptWhenEmpty() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b"));
            box.setPromptText("Pick one");
            attach(box);
            assertEquals("Pick one", summaryText(box));
            assertTrue(box.getPseudoClassStates().contains(EMPTY));
        });
    }

    @Test
    public void summaryShowsSingleItemText() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("apple", "pear"));
            box.getSelectionModel().select(0);
            attach(box);
            assertEquals("apple", summaryText(box));
            assertFalse(box.getPseudoClassStates().contains(EMPTY));
        });
    }

    @Test
    public void summaryShowsCountForMultipleSelection() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "c"));
            box.setSelectionMode(SelectionMode.MULTIPLE);
            box.getSelectionModel().select(0);
            box.getSelectionModel().select(1);
            attach(box);
            assertEquals("2 selected", summaryText(box));
        });
    }

    @Test
    public void summaryUsesSelectedItemsConverterForMultiple() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b", "c"));
            box.setSelectionMode(SelectionMode.MULTIPLE);
            box.setSelectedItemsConverter(new StringConverter<>() {
                @Override
                public String toString(List<String> items) {
                    return String.join("+", items);
                }

                @Override
                public List<String> fromString(String string) {
                    return List.of();
                }
            });
            box.getSelectionModel().select(0);
            box.getSelectionModel().select(2);
            attach(box);
            assertEquals("a+c", summaryText(box));
        });
    }

    @Test
    public void summaryUsesConverterForSingleItemText() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b"));
            box.setConverter(new StringConverter<>() {
                @Override
                public String toString(String item) {
                    return "[" + item + "]";
                }

                @Override
                public String fromString(String string) {
                    return string;
                }
            });
            box.getSelectionModel().select(1);
            attach(box);
            assertEquals("[b]", summaryText(box));
        });
    }

    @Test
    public void summaryUpdatesWhenSelectionChangesAfterAttach() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b"));
            attach(box);
            assertEquals("", summaryText(box));
            box.getSelectionModel().select(1);
            assertEquals("b", summaryText(box));
        });
    }

    // ==================== Pseudo-classes (skinned) ====================

    @Test
    public void pseudoClassesReflectModeReadOnlyAndSearchable() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a"));
            attach(box);
            assertTrue(box.getPseudoClassStates().contains(SINGLE));
            assertFalse(box.getPseudoClassStates().contains(MULTIPLE));
            assertTrue(box.getPseudoClassStates().contains(SEARCHABLE));
            assertFalse(box.getPseudoClassStates().contains(READONLY));

            box.setSelectionMode(SelectionMode.MULTIPLE);
            assertTrue(box.getPseudoClassStates().contains(MULTIPLE));
            assertFalse(box.getPseudoClassStates().contains(SINGLE));

            box.setReadOnly(true);
            assertTrue(box.getPseudoClassStates().contains(READONLY));

            box.setSearchable(false);
            assertFalse(box.getPseudoClassStates().contains(SEARCHABLE));
        });
    }

    @Test
    public void filteredPseudoClassTracksSearchText() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a", "b"));
            attach(box);
            assertFalse(box.getPseudoClassStates().contains(FILTERED));
            box.setSearchText("a");
            assertTrue(box.getPseudoClassStates().contains(FILTERED));
            box.setSearchText("   ");
            assertFalse(box.getPseudoClassStates().contains(FILTERED), "blank query is not a filter");
        });
    }

    @Test
    public void graphicAppearsInDisplay() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList("a"));
            Label graphic = new Label("g");
            box.setGraphic(graphic);
            attach(box);
            StackPane holder = (StackPane) box.lookup(".graphic");
            assertNotNull(holder, "graphic holder should exist");
            assertTrue(holder.isVisible());
            assertSame(graphic, holder.getChildren().get(0));
        });
    }

    // ==================== Helpers ====================

    private static RXSelectionBox<String> attach(RXSelectionBox<String> box) {
        box.setSkin(new RXSelectionBoxSkin<>(box));
        StackPane root = new StackPane(box);
        new Scene(root, 260.0, 60.0);
        root.applyCss();
        root.layout();
        return box;
    }

    private static String summaryText(RXSelectionBox<String> box) {
        Label summary = (Label) box.lookup(".summary");
        assertNotNull(summary, "summary label should exist");
        return summary.getText();
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
