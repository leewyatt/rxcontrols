package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.editor;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.findPopup;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hideAllPopups;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two-field and popup behavior of {@link RXDateRangePicker}.
 */
@Tag("ui")
public class RXDateRangePickerPopupTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private Stage stage;

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        PickerTestSupport.startToolkit();
    }

    /**
     * Hides popups and the stage between tests.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            hideAllPopups();
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * Verifies two picks commit the whole range once, after previewing the start.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void twoPicksCommitWholeRange() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = newShownPicker();
            AtomicInteger changes = new AtomicInteger();
            picker.valueProperty().addListener(observable -> changes.incrementAndGet());
            AtomicInteger actions = new AtomicInteger();
            picker.setOnAction(event -> actions.incrementAndGet());
            editor(picker, 0).requestFocus();
            picker.show();
            Node root = findPopup().getScene().getRoot();
            click(dayCell(root, LocalDate.of(2026, 11, 20)));
            assertEquals("11/20/2026", editor(picker, 0).getText());
            assertTrue(stage.getScene().getFocusOwner() == editor(picker, 1));
            assertNull(picker.getValue());
            click(dayCell(root, LocalDate.of(2026, 12, 2)));
            assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 2)), picker.getValue());
            assertEquals(1, changes.get());
            assertEquals(1, actions.get());
            assertFalse(picker.isShowing());
            assertEquals("12/2/2026", editor(picker, 1).getText());
        });
    }

    /**
     * Verifies closing after one pick discards it and restores the fields.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void halfPickIsDiscardedOnClose() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = newShownPicker();
            RXDateRange old = RXDateRange.of(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));
            picker.setValue(old);
            editor(picker, 0).requestFocus();
            picker.show();
            click(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 5)));
            press(editor(picker, 1), KeyCode.ESCAPE);
            assertEquals(old, picker.getValue());
            assertEquals("11/1/2026", editor(picker, 0).getText());
            assertEquals("11/10/2026", editor(picker, 1).getText());
            picker.show();
            click(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 25)));
            click(dayCell(findPopup().getScene().getRoot(), LocalDate.of(2026, 11, 26)));
            assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26)), picker.getValue());
        });
    }

    /**
     * Verifies typed endpoints commit together in order, and a lone endpoint is reverted.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void typedEndpointsCommitTogether() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.requestFocus();
            start.setText("12/5/2026");
            press(start, KeyCode.ENTER);
            assertTrue(stage.getScene().getFocusOwner() == end, "Enter with an empty end moves to the end field");
            assertNull(picker.getValue());
            end.setText("12/1/2026");
            press(end, KeyCode.ENTER);
            assertEquals(RXDateRange.of(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)), picker.getValue());

            end.setText("");
            press(end, KeyCode.ENTER);
            assertTrue(hasPseudoClass(picker, "invalid"));
            stage.getScene().getRoot().lookup(".button").requestFocus();
            assertEquals("12/5/2026", end.getText());
            assertFalse(hasPseudoClass(picker, "invalid"));
        });
    }

    /**
     * Verifies moving focus between the two fields neither commits nor reverts.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void switchingFieldsKeepsTypedText() throws InterruptedException {
        runOnFx(() -> {
            RXDateRangePicker picker = newShownPicker();
            TextField start = editor(picker, 0);
            TextField end = editor(picker, 1);
            start.requestFocus();
            start.setText("12/5/2026");
            end.requestFocus();
            assertEquals("12/5/2026", start.getText());
            assertTrue(picker.isFocused());
        });
    }

    private RXDateRangePicker newShownPicker() {
        RXDateRangePicker picker = new RXDateRangePicker();
        picker.setLocale(Locale.US);
        picker.setClock(CLOCK);
        stage = new Stage();
        stage.setScene(new Scene(new VBox(picker, new Button("other")), 520, 240));
        stage.show();
        picker.applyCss();
        picker.layout();
        return picker;
    }
}
