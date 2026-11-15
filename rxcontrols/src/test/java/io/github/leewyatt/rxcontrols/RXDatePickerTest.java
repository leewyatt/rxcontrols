package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field, commit and constraint behavior of {@link RXDatePicker} that needs no shown window.
 */
public class RXDatePickerTest {

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Verifies the field shows the value in the locale format and programmatic writes fire no action.
     */
    @Test
    public void fieldShowsValueWithoutAction() {
        RXDatePicker picker = newPicker();
        AtomicInteger actions = countActions(picker);
        picker.setValue(LocalDate.of(2026, 11, 14));
        assertEquals("11/14/2026", editor(picker).getText());
        picker.setLocale(Locale.GERMANY);
        assertEquals("14.11.2026", editor(picker).getText());
        assertEquals(0, actions.get());
    }

    /**
     * Verifies Enter commits valid typed text once and fires one action.
     */
    @Test
    public void enterCommitsTypedText() {
        RXDatePicker picker = newPicker();
        AtomicInteger actions = countActions(picker);
        editor(picker).setText("3/5/2026");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 3, 5), picker.getValue());
        assertEquals("3/5/2026", editor(picker).getText());
        assertEquals(1, actions.get());
    }

    /**
     * Verifies unparsable and disallowed text is flagged, not committed, and Escape reverts it.
     */
    @Test
    public void invalidTextIsFlaggedAndReverted() {
        RXDatePicker picker = newPicker();
        picker.setValue(LocalDate.of(2026, 11, 14));
        picker.setMaxDate(LocalDate.of(2026, 12, 31));
        AtomicInteger actions = countActions(picker);

        editor(picker).setText("13/45/2026");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 11, 14), picker.getValue());
        assertTrue(hasPseudoClass(picker, "invalid"));

        editor(picker).setText("1/1/2027");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 11, 14), picker.getValue());
        assertTrue(hasPseudoClass(picker, "invalid"));

        press(editor(picker), KeyCode.ESCAPE);
        assertEquals("11/14/2026", editor(picker).getText());
        assertFalse(hasPseudoClass(picker, "invalid"));
        assertEquals(0, actions.get());
    }

    /**
     * Verifies clearing the text and pressing Enter commits null.
     */
    @Test
    public void blankTextCommitsNull() {
        RXDatePicker picker = newPicker();
        picker.setValue(LocalDate.of(2026, 11, 14));
        editor(picker).setText("");
        press(editor(picker), KeyCode.ENTER);
        assertNull(picker.getValue());
    }

    /**
     * Verifies text typed and then restored commits nothing.
     */
    @Test
    public void restoredTextCommitsNothing() {
        RXDatePicker picker = newPicker();
        AtomicInteger actions = countActions(picker);
        editor(picker).setText("1");
        editor(picker).setText("");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(0, actions.get());

        picker.setValue(LocalDate.of(2026, 11, 14));
        editor(picker).setText("11/14/20261");
        editor(picker).setText("11/14/2026");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(0, actions.get());
    }

    /**
     * Verifies the field's own ActionEvent never reaches the picker's onAction.
     */
    @Test
    public void editorActionEventDoesNotLeak() {
        RXDatePicker picker = newPicker();
        AtomicInteger actions = countActions(picker);
        editor(picker).fireEvent(new ActionEvent());
        assertEquals(0, actions.get());
    }

    /**
     * Verifies a programmatic out-of-bounds value is kept and flagged invalid.
     */
    @Test
    public void outOfBoundsValueIsKeptAndFlagged() {
        RXDatePicker picker = newPicker();
        picker.setMinDate(LocalDate.of(2026, 1, 1));
        picker.setValue(LocalDate.of(2025, 6, 1));
        assertEquals(LocalDate.of(2025, 6, 1), picker.getValue());
        assertTrue(hasPseudoClass(picker, "invalid"));
        picker.setMinDate(null);
        assertFalse(hasPseudoClass(picker, "invalid"));
    }

    /**
     * Verifies a commit on a bound value fires the action and leaves the value alone.
     */
    @Test
    public void commitOnBoundValueFiresActionAndKeepsValue() {
        RXDatePicker picker = newPicker();
        RXDatePicker source = new RXDatePicker(LocalDate.of(2026, 1, 1));
        picker.valueProperty().bind(source.valueProperty());
        AtomicInteger actions = countActions(picker);
        editor(picker).setText("3/5/2026");
        press(editor(picker), KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 1, 1), picker.getValue());
        assertEquals(1, actions.get());
    }

    /**
     * Verifies the clear button stays hidden until a clearable picker with a value is hovered.
     */
    @Test
    public void clearButtonHiddenByDefault() {
        RXDatePicker picker = newPicker();
        picker.setClearable(true);
        picker.setValue(LocalDate.of(2026, 11, 14));
        assertFalse(picker.lookup(".clear-button").isVisible());
        assertTrue(picker.lookup(".icon").isVisible());
    }

    /**
     * Verifies a leading node sits before the field and widens the picker, and removing it undoes both.
     */
    @Test
    public void leadingNodeTakesSpaceBeforeField() {
        RXDatePicker picker = newPicker();
        double plainWidth = picker.prefWidth(-1);
        Region icon = newIcon();

        picker.setLeading(icon);
        layout(picker);
        Node holder = picker.lookup(".leading");
        assertSame(holder, icon.getParent());
        assertTrue(hasPseudoClass(picker, "has-leading"));
        assertTrue(holder.prefWidth(-1) >= icon.getPrefWidth());
        assertEquals(plainWidth + holder.prefWidth(-1), picker.prefWidth(-1), 0.01);
        assertEquals(holder.getBoundsInParent().getMaxX(), editor(picker).getLayoutX(), 1.0);

        picker.setLeading(null);
        layout(picker);
        assertNull(icon.getParent());
        assertFalse(hasPseudoClass(picker, "has-leading"));
        assertEquals(plainWidth, picker.prefWidth(-1), 0.01);
    }

    /**
     * Verifies disposing the skin releases the leading node.
     */
    @Test
    public void disposedSkinReleasesLeadingNode() {
        RXDatePicker picker = newPicker();
        Region icon = newIcon();
        picker.setLeading(icon);
        picker.setSkin(null);
        assertNull(icon.getParent());
        assertSame(icon, picker.getLeading());
    }

    // ==================== Helpers ====================

    static RXDatePicker newPicker() {
        RXDatePicker picker = new RXDatePicker();
        picker.setLocale(Locale.US);
        Scene scene = new Scene(new StackPane(picker), 300, 200);
        picker.applyCss();
        scene.getRoot().layout();
        return picker;
    }

    private static Region newIcon() {
        Region icon = new Region();
        icon.setPrefSize(16.0, 16.0);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    private static void layout(RXDatePicker picker) {
        picker.applyCss();
        picker.getScene().getRoot().layout();
    }

    static TextField editor(RXPickerBase<?> picker) {
        return (TextField) picker.lookup(".text-field");
    }

    static AtomicInteger countActions(RXPickerBase<?> picker) {
        AtomicInteger count = new AtomicInteger();
        picker.setOnAction(event -> count.incrementAndGet());
        return count;
    }

    static void press(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    static boolean hasPseudoClass(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals(name));
    }
}
