package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window-dependent popup behavior for {@link RXSelectionBox}: the display-click
 * toggle, keyboard cursor navigation and activation, the source-index bridge
 * (including duplicate items), single vs multiple activation semantics, and
 * search filtering of the popup list.
 *
 * <p>These need a real shown {@link Stage}: without a window the skin bounces
 * {@code show()} straight back to hidden, so the popup logic cannot be exercised
 * otherwise. Tagged {@code "ui"} so a headless CI can exclude it
 * ({@code -DexcludedGroups=ui}); it runs by default locally.
 */
@Tag("ui")
public class RXSelectionBoxPopupTest {

    private Stage stage;

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
        Platform.setImplicitExit(false);
    }

    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            PopupControl popup = findPopup();
            if (popup != null) {
                popup.hide();
            }
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    @Test
    public void displayClickTogglesPopup() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b", "c");
            Node display = box.lookup(".display");
            assertNotNull(display, "display should exist");

            fireClick(display);
            assertTrue(box.isShowing(), "first display click opens the popup");

            fireClick(display);
            assertFalse(box.isShowing(), "second display click closes the popup");
        });
    }

    @Test
    public void readOnlyBlocksDisplayClick() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b");
            box.setReadOnly(true);
            fireClick(box.lookup(".display"));
            assertFalse(box.isShowing(), "read-only display click must not open the popup");
        });
    }

    @Test
    public void turningReadOnlyWhileOpenClosesThePopup() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b", "c");
            box.show();
            assertTrue(box.isShowing(), "precondition: popup is open");
            box.setReadOnly(true);
            assertFalse(box.isShowing(), "turning read-only closes an open popup");
        });
    }

    @Test
    public void escapeClosesThePopup() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b");
            box.show();
            assertTrue(box.isShowing());
            fireKey(box, KeyCode.ESCAPE);
            assertFalse(box.isShowing(), "Escape closes the popup");
        });
    }

    @Test
    public void keyboardActivationSelectsAndClosesInSingleMode() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b", "c");
            box.show();
            fireKey(box, KeyCode.DOWN); // cursor -> row 0
            fireKey(box, KeyCode.DOWN); // cursor -> row 1
            fireKey(box, KeyCode.ENTER); // activate cursor
            assertEquals("b", box.getSelectedItem(), "Enter selects the cursor row");
            assertFalse(box.isShowing(), "single-mode activation auto-hides");
        });
    }

    @Test
    public void keyboardActivationMapsToSourceIndexForDuplicates() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b", "a");
            box.show();
            fireKey(box, KeyCode.DOWN); // row 0
            fireKey(box, KeyCode.DOWN); // row 1
            fireKey(box, KeyCode.DOWN); // row 2 (the second "a")
            fireKey(box, KeyCode.ENTER);
            assertEquals(List.of(2), new java.util.ArrayList<>(box.getSelectionModel().getSelectedIndices()),
                    "activation selects the exact source index, not the first duplicate");
        });
    }

    @Test
    public void multipleActivationTogglesAndKeepsPopupOpen() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("a", "b", "c");
            box.setSelectionMode(SelectionMode.MULTIPLE);
            box.show();
            fireKey(box, KeyCode.DOWN); // cursor -> row 0
            fireKey(box, KeyCode.ENTER);
            assertEquals(List.of("a"), new java.util.ArrayList<>(box.getSelectedItems()));
            assertTrue(box.isShowing(), "multiple-mode activation keeps the popup open");

            fireKey(box, KeyCode.ENTER); // toggle the same row off
            assertTrue(box.getSelectedItems().isEmpty(), "second activation deselects");
            assertTrue(box.isShowing());
        });
    }

    @Test
    public void searchFieldIsEditableAndFocusableWhenSearchable() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("apple", "banana", "avocado");
            box.show();
            TextInputControl field = popupSearchField();
            assertNotNull(field, "a searchable popup exposes a real search field");
            // The core of the design: a genuine editable, focusable input (shows a caret
            // and takes typed characters natively), not a display-only label.
            assertTrue(field.isEditable(), "the search field is editable");
            assertTrue(field.isFocusTraversable(), "the search field can take key focus");
        });
    }

    @Test
    public void editingTheSearchFieldFiltersTheListAndSyncsSearchText() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("apple", "banana", "avocado");
            box.show();
            TextInputControl field = popupSearchField();
            assertNotNull(field);

            field.setText("ap"); // user typing into the field
            assertEquals("ap", box.getSearchText(), "field edits flow into searchText");
            RXListView<?> list = popupList();
            assertNotNull(list, "popup list should exist");
            assertEquals(1, list.getItems().size(), "only apple matches 'ap'");
            assertEquals("apple", list.getItems().get(0));

            box.setSearchText("ban"); // programmatic set flows back into the field
            assertEquals("ban", field.getText(), "searchText and the field are bidirectionally bound");
        });
    }

    @Test
    public void activationMapsAFilteredViewRowToTheCorrectSourceIndex() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("xa", "xb", "ya", "yb");
            box.show();
            // Filter so view rows no longer line up with source indices: the filtered
            // view is [ya (src 2), yb (src 3)]. This is the core scheme-B invariant —
            // activation must go through getSourceIndex, not select the view index.
            box.setSearchText("y");
            RXListView<?> list = popupList();
            assertNotNull(list);
            assertEquals(2, list.getItems().size(), "only 'ya' and 'yb' match 'y'");

            fireKey(popupSearchField(), KeyCode.ENTER); // activate view row 0 = ya
            assertEquals("ya", box.getSelectedItem());
            assertEquals(List.of(2),
                    new java.util.ArrayList<>(box.getSelectionModel().getSelectedIndices()),
                    "activation selects source index 2, not the view index 0");
        });
    }

    @Test
    public void navigationKeysOnTheSearchFieldDriveTheCursor() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("apple", "banana", "avocado");
            box.show();
            Node field = popupSearchField();
            assertNotNull(field);
            fireKey(field, KeyCode.DOWN);  // cursor -> row 0
            fireKey(field, KeyCode.DOWN);  // cursor -> row 1
            fireKey(field, KeyCode.ENTER); // activate
            assertEquals("banana", box.getSelectedItem(), "Down/Enter on the field select the row");
            assertFalse(box.isShowing(), "single-mode activation auto-hides");
        });
    }

    @Test
    public void escapeOnTheSearchFieldClosesThePopup() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("apple", "avocado");
            box.show();
            Node field = popupSearchField();
            assertNotNull(field);
            fireKey(field, KeyCode.ESCAPE);
            assertFalse(box.isShowing(), "Escape on the search field closes the popup");
        });
    }

    @Test
    public void searchIsClearedWhenPopupHides() throws InterruptedException {
        runOnFx(() -> {
            RXSelectionBox<String> box = newShownBox("apple", "avocado");
            box.show();
            box.setSearchText("a");
            assertEquals("a", box.getSearchText());
            box.hide();
            assertEquals("", box.getSearchText(), "clearSearchOnHide wipes the query");
        });
    }

    // ==================== Helpers ====================

    private RXSelectionBox<String> newShownBox(String... items) {
        RXSelectionBox<String> box = new RXSelectionBox<>(FXCollections.observableArrayList(items));
        stage = new Stage();
        stage.setScene(new Scene(new StackPane(box), 320, 220));
        stage.show();
        box.applyCss();
        box.layout();
        box.requestFocus();
        return box;
    }

    private static TextInputControl popupSearchField() {
        PopupControl popup = findPopup();
        if (popup == null || popup.getScene() == null || popup.getScene().getRoot() == null) {
            return null;
        }
        popup.getScene().getRoot().applyCss();
        popup.getScene().getRoot().layout();
        return (TextInputControl) popup.getScene().getRoot().lookup(".search-field");
    }

    private static RXListView<?> popupList() {
        PopupControl popup = findPopup();
        if (popup == null || popup.getScene() == null || popup.getScene().getRoot() == null) {
            return null;
        }
        popup.getScene().getRoot().applyCss();
        popup.getScene().getRoot().layout();
        return (RXListView<?>) popup.getScene().getRoot().lookup(".rx-list-view");
    }

    private static PopupControl findPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl) {
                PopupControl popup = (PopupControl) window;
                if (popup.getStyleClass().contains("rx-selection-box-popup")) {
                    return popup;
                }
            }
        }
        return null;
    }

    private static void fireClick(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, true, null));
    }

    private static void fireKey(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code,
                false, false, false, false));
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
