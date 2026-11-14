package io.github.leewyatt.rxcontrols;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the sizing of the date / time views.
 */
public class RXTemporalViewSizingTest {

    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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
     * Verifies every view's preferred size is its content's plus its padding.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void preferredSizeIncludesPadding() throws InterruptedException {
        runOnFx(() -> {
            List<Control> views = List.of(new RXCalendarView(), new RXDateRangeView(), new RXTimeView(),
                    new RXDateTimeView(), new RXDateTimeRangeView(), new RXMonthView(), new RXQuarterView(),
                    new RXYearView());
            for (Control view : views) {
                show(view);
                Region content = (Region) view.getChildrenUnmodifiable().get(0);
                String name = view.getClass().getSimpleName();
                double horizontal = view.snappedLeftInset() + view.snappedRightInset();
                double vertical = view.snappedTopInset() + view.snappedBottomInset();
                assertTrue(horizontal > 0.0 && vertical > 0.0, name);
                assertEquals(content.prefWidth(-1) + horizontal, view.prefWidth(-1), 0.01, name);
                assertEquals(content.prefHeight(-1) + vertical, view.prefHeight(-1), 0.01, name);
            }
        });
    }

    /**
     * Verifies the time columns of a view at its preferred size are not squeezed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void timeColumnsKeepPreferredWidth() throws InterruptedException {
        runOnFx(() -> {
            for (Control view : List.of(new RXTimeView(), new RXDateTimeView(), new RXDateTimeRangeView())) {
                show(view);
                for (Node node : view.lookupAll(".time-column")) {
                    Region column = (Region) node;
                    assertTrue(column.getWidth() >= column.prefWidth(-1), view.getClass().getSimpleName()
                            + ": column " + column.getWidth() + " < " + column.prefWidth(-1));
                }
            }
        });
    }

    /**
     * Verifies the keyboard cursor's border leaves a time cell wide enough for its text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void focusedTimeCellFitsItsText() throws InterruptedException {
        runOnFx(() -> {
            RXDateTimeRangeView view = new RXDateTimeRangeView();
            view.setValue(RXDateTimeRange.of(LocalDateTime.of(2026, 11, 3, 9, 0), LocalDateTime.of(2026, 12, 25, 19, 32)));
            VBox root = show(view);
            Label selected = null;
            for (Node node : view.lookupAll(".time-cell")) {
                if (node.getPseudoClassStates().contains(SELECTED)) {
                    selected = (Label) node;
                    break;
                }
            }
            assertFalse(selected == null);
            selected.pseudoClassStateChanged(FOCUSED, true);
            root.applyCss();
            root.layout();
            Text text = new Text(selected.getText());
            text.setFont(selected.getFont());
            double room = selected.getWidth() - selected.snappedLeftInset() - selected.snappedRightInset();
            assertTrue(room >= text.getLayoutBounds().getWidth(),
                    "room " + room + " < text " + text.getLayoutBounds().getWidth());
        });
    }

    private static VBox show(Control view) {
        VBox root = new VBox(view);
        root.setFillWidth(false);
        new Scene(root, 1200, 800);
        root.applyCss();
        root.layout();
        return root;
    }
}
