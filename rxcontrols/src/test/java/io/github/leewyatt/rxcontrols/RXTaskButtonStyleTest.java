package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTaskButton.Run;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the default theme rules of {@link RXTaskButton}: the sizes and shapes
 * of the indicators, the color of the stop mark and the colors of the result
 * states.
 */
public class RXTaskButtonStyleTest {

    private static final Color SUCCESS = Color.web("#67c23a");
    private static final Color DANGER = Color.web("#f56c6c");
    private static final Duration LONG = Duration.seconds(30);

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

    // ==================== Indicators ====================

    @Test
    public void indicatorsAreSizedFromTheFontOfTheButton() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = new RXTaskButton("Save");
            button.setFont(Font.font(20.0));
            styled(button);
            Region ring = indicator(button, ".pending-face");
            Region check = indicator(button, ".succeeded-face");
            Region cross = indicator(button, ".failed-face");

            assertEquals(22.0, ring.prefWidth(-1), 0.5);
            assertEquals(22.0, ring.prefHeight(-1), 0.5);
            assertEquals(22.0, check.prefWidth(-1), 0.5);
            assertEquals(16.3, check.prefHeight(-1), 0.5);
            assertEquals(18.0, cross.prefWidth(-1), 0.5);
            assertEquals(18.0, cross.prefHeight(-1), 0.5);
            // The idle face has no indicator to show.
            assertEquals(0.0, indicator(button, ".idle-face").prefWidth(-1));

            // A font size from a style sheet, here an inline style. (JavaFX
            // does not convert em sizes of sub-nodes again when setFont is
            // called on a control that is already styled.)
            button.setStyle("-fx-font-size: 10px;");
            button.getParent().applyCss();
            assertEquals(11.0, ring.prefWidth(-1), 0.5);
            assertEquals(11.0, check.prefWidth(-1), 0.5);
            assertEquals(8.15, check.prefHeight(-1), 0.5);
            assertEquals(9.0, cross.prefWidth(-1), 0.5);
        });
    }

    @Test
    public void checkAndCrossAreShapesAndTheRingIsNot() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = styled(new RXTaskButton("Save"));

            assertNotNull(indicator(button, ".succeeded-face").getShape());
            assertNotNull(indicator(button, ".failed-face").getShape());
            assertNull(indicator(button, ".pending-face").getShape());
            assertNull(indicator(button, ".idle-face").getShape());
        });
    }

    @Test
    public void customPendingIndicatorKeepsItsOwnSize() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = new RXTaskButton("Save");
            Region custom = new Region();
            custom.setPrefSize(30.0, 6.0);
            button.setPendingIndicator(custom);
            styled(button);

            assertEquals(30.0, custom.prefWidth(-1));
            assertEquals(6.0, custom.prefHeight(-1));
        });
    }

    // ==================== Stop mark ====================

    @Test
    public void stopMarkIsRedAndTakesTheTextColorOnADefaultButton() throws InterruptedException {
        onFx(() -> {
            RXTaskButton plain = pendingAndCancellable(new RXTaskButton("Save"));
            assertEquals(DANGER, fill(stop(plain)));

            RXTaskButton filled = new RXTaskButton("Save");
            filled.setDefaultButton(true);
            pendingAndCancellable(filled);
            assertNotEquals(DANGER, fill(stop(filled)));
            assertEquals(filled.getTextFill(), fill(stop(filled)));
            filled.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            filled.getParent().applyCss();
            assertEquals(filled.getTextFill(), fill(stop(filled)));
        });
    }

    // ==================== Result colors ====================

    @Test
    public void resultStatesColorTheTextAndTheIndicators() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = styled(new RXTaskButton("Save"));
            button.setPendingDelay(Duration.ZERO);
            button.setPendingMinDuration(Duration.ZERO);
            button.setSucceededDuration(LONG);
            button.setFailedDuration(LONG);
            Paint idle = button.getTextFill();
            assertNotEquals(SUCCESS, idle);
            assertNotEquals(DANGER, idle);

            Run run = button.start();
            button.getParent().applyCss();
            assertEquals(idle, button.getTextFill());

            run.succeed();
            button.getParent().applyCss();
            assertEquals(SUCCESS, button.getTextFill());
            assertEquals(SUCCESS, fill(indicator(button, ".succeeded-face")));

            button.start().fail();
            button.getParent().applyCss();
            assertEquals(DANGER, button.getTextFill());
            assertEquals(DANGER, fill(indicator(button, ".failed-face")));

            button.resetFeedback();
            button.getParent().applyCss();
            assertEquals(idle, button.getTextFill());
        });
    }

    @Test
    public void defaultButtonCarriesTheResultOnItsBody() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = new RXTaskButton("Save");
            button.setDefaultButton(true);
            styled(button);
            button.setPendingDelay(Duration.ZERO);
            button.setPendingMinDuration(Duration.ZERO);
            button.setSucceededDuration(LONG);
            button.setFailedDuration(LONG);
            List<Paint> idleBody = fills(button);

            button.start().succeed();
            button.getParent().applyCss();
            List<Paint> succeededBody = fills(button);
            assertNotEquals(idleBody, succeededBody);
            // The text is whatever reads on the new body, not the result color.
            assertNotEquals(SUCCESS, button.getTextFill());

            button.start().fail();
            button.getParent().applyCss();
            assertNotEquals(idleBody, fills(button));
            assertNotEquals(succeededBody, fills(button));
            assertNotEquals(DANGER, button.getTextFill());

            button.resetFeedback();
            button.getParent().applyCss();
            assertEquals(idleBody, fills(button));
        });
    }

    @Test
    public void everyStateResolvesItsColors() throws InterruptedException {
        CollectingHandler handler = new CollectingHandler();
        handler.setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        root.addHandler(handler);
        try {
            onFx(() -> {
                for (boolean defaultButton : new boolean[] {false, true}) {
                    RXTaskButton button = new RXTaskButton("Save");
                    button.setDefaultButton(defaultButton);
                    button.setCancellable(true);
                    styled(button);
                    button.setPendingDelay(Duration.ZERO);
                    button.setPendingMinDuration(Duration.ZERO);
                    button.setSucceededDuration(LONG);
                    button.setFailedDuration(LONG);
                    Run run = button.start();
                    button.getParent().applyCss();
                    run.succeed();
                    button.getParent().applyCss();
                    button.start().fail();
                    button.getParent().applyCss();
                    button.resetFeedback();
                }
            });
        } finally {
            root.removeHandler(handler);
        }

        List<String> failures = new ArrayList<>();
        for (String message : handler.messages) {
            if (message.contains("while converting value for")
                    || message.contains("while resolving lookups for")
                    || message.contains("Could not resolve")) {
                failures.add(message);
            }
        }
        assertEquals(List.of(), failures);
    }

    // ==================== Helpers ====================

    private static RXTaskButton styled(RXTaskButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 300.0, 120.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static Region indicator(RXTaskButton button, String faceSelector) {
        Region face = (Region) button.lookup(faceSelector);
        Node indicator = face.getChildrenUnmodifiable().get(0);
        return (Region) indicator;
    }

    private static RXTaskButton pendingAndCancellable(RXTaskButton button) {
        button.setCancellable(true);
        button.setPendingDelay(Duration.ZERO);
        styled(button);
        button.start();
        button.getParent().applyCss();
        return button;
    }

    private static Region stop(RXTaskButton button) {
        return (Region) indicator(button, ".pending-face").lookup(".stop");
    }

    private static Paint fill(Region region) {
        return region.getBackground().getFills().get(0).getFill();
    }

    private static List<Paint> fills(Region region) {
        List<Paint> fills = new ArrayList<>();
        region.getBackground().getFills().forEach(each -> fills.add(each.getFill()));
        return fills;
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static final class CollectingHandler extends Handler {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null && record.getMessage() != null) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
