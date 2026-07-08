package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window-dependent tests for {@link RXMaterialTextField}'s animation gate. The
 * snap-vs-animate decision in the skin is only meaningful when the control is
 * showing (a not-showing field always snaps, which is what the headless
 * {@code RXMaterialTextFieldTest} relies on); these tests show a real
 * {@link Stage} so {@code isShowing()} is true and the {@code animated} flag /
 * {@code animationDuration} actually decide whether a transition snaps or runs.
 * <p>
 * Tagged {@code "ui"} so a headless CI without Monocle can exclude it
 * ({@code -DexcludedGroups=ui}); it runs by default locally.
 */
@Tag("ui")
public class RXMaterialTextFieldUITest {

    private Stage stage;

    /**
     * Starts the toolkit and disables implicit exit so hiding the last test
     * window does not shut the toolkit down for later test classes in the fork.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    /**
     * Hides the test stage so windows do not leak across tests.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * With a shown window, {@code animated=false} and {@code duration<=0} snap to
     * the end value immediately, while {@code animated=true} runs a transition
     * (so the value is not yet at the end on the same pulse). This is what
     * distinguishes the gate from the headless not-showing snap.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void animatedFlagAndDurationGateSnapWhenShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMaterialTextField snapField = floatedField(false, RXMaterialTextField.DEFAULT_ANIMATION_DURATION);
            RXMaterialTextField animField = floatedField(true, RXMaterialTextField.DEFAULT_ANIMATION_DURATION);
            RXMaterialTextField zeroDurField = floatedField(true, Duration.ZERO);
            VBox root = new VBox(snapField, animField, zeroDurField);
            stage = new Stage();
            stage.setScene(new Scene(root, 320, 260));
            stage.show();
            root.applyCss();
            root.layout();

            // All start floated (text "x", snapped at construction while not showing).
            assertTrue(floatingLabel(snapField).getTranslateY() < 0.0, "precondition: snap field floated");
            assertTrue(floatingLabel(animField).getTranslateY() < 0.0, "precondition: anim field floated");
            assertTrue(floatingLabel(zeroDurField).getTranslateY() < 0.0, "precondition: zero-dur field floated");

            // Clear the text while showing -> resting target. Assert immediately
            // (before any pulse): snapping fields land at the end value at once;
            // the animating field is still at the floated value (Timeline pending).
            snapField.setText("");
            animField.setText("");
            zeroDurField.setText("");

            assertEquals(0.0, floatingLabel(snapField).getTranslateY(), 0.001,
                    "animated=false must snap to the resting end value immediately when showing");
            assertEquals(0.0, floatingLabel(zeroDurField).getTranslateY(), 0.001,
                    "duration<=0 must snap immediately even when animated and showing");
            assertTrue(floatingLabel(animField).getTranslateY() < 0.0,
                    "animated=true must animate (not snap); translateY="
                            + floatingLabel(animField).getTranslateY());
        });
    }

    /**
     * The remaining duration guards are only reachable while showing: null falls
     * back to the default and still animates, while negative / INDEFINITE /
     * UNKNOWN durations must snap straight to the end value (an INDEFINITE tween
     * would otherwise never complete and leave the label stuck mid-flight).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void durationEdgeValuesSnapWhenShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMaterialTextField indefiniteField = floatedField(true, Duration.INDEFINITE);
            RXMaterialTextField unknownField = floatedField(true, Duration.UNKNOWN);
            RXMaterialTextField negativeField = floatedField(true, Duration.millis(-50));
            RXMaterialTextField nullField = floatedField(true, null);
            VBox root = new VBox(indefiniteField, unknownField, negativeField, nullField);
            stage = new Stage();
            stage.setScene(new Scene(root, 320, 320));
            stage.show();
            root.applyCss();
            root.layout();

            assertTrue(floatingLabel(indefiniteField).getTranslateY() < 0.0, "precondition: floated");
            assertTrue(floatingLabel(unknownField).getTranslateY() < 0.0, "precondition: floated");
            assertTrue(floatingLabel(negativeField).getTranslateY() < 0.0, "precondition: floated");
            assertTrue(floatingLabel(nullField).getTranslateY() < 0.0, "precondition: floated");

            indefiniteField.setText("");
            unknownField.setText("");
            negativeField.setText("");
            nullField.setText("");

            assertEquals(0.0, floatingLabel(indefiniteField).getTranslateY(), 0.001,
                    "INDEFINITE duration must snap, not start a never-ending tween");
            assertEquals(0.0, floatingLabel(unknownField).getTranslateY(), 0.001,
                    "UNKNOWN (NaN) duration must snap");
            assertEquals(0.0, floatingLabel(negativeField).getTranslateY(), 0.001,
                    "negative duration must snap");
            assertTrue(floatingLabel(nullField).getTranslateY() < 0.0,
                    "null duration must fall back to the 180ms default and animate");
        });
    }

    /**
     * Disposing the skin mid-transition stops the running Timeline cleanly and
     * detaches the float listener, so a later float-state change does not move
     * the orphaned label.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void disposeMidAnimationIsClean() throws InterruptedException {
        runOnFx(() -> {
            RXMaterialTextField field = floatedField(true, RXMaterialTextField.DEFAULT_ANIMATION_DURATION);
            VBox root = new VBox(field);
            stage = new Stage();
            stage.setScene(new Scene(root, 320, 120));
            stage.show();
            root.applyCss();
            root.layout();

            // Start a float transition (showing + animated). The Timeline is now
            // running; dispose mid-flight must stop it without throwing.
            field.setText("");
            // Capture the label before dispose: dispose removes the decoration nodes
            // it added, so the lookup would return null afterwards. The Label object
            // itself stays alive (we hold the reference).
            Label label = floatingLabel(field);
            assertNotNull(label, "floating label missing before dispose");
            // Detach the field before disposing its skin: a manually-disposed skin
            // that is still in a shown scene would be laid out on the next pulse and
            // NPE inside SkinBase (control == null) — a test-only hazard, not a
            // production path (real dispose happens when the control drops the skin).
            root.getChildren().clear();
            field.getSkin().dispose();

            double frozen = label.getTranslateY();
            // The float listener is detached, so a float-state change must not move
            // the orphaned label. (Text is not mutated post-dispose, which would
            // trip JavaFX TextFieldSkin's own internals.)
            field.setFloatingLabel(false);
            assertEquals(frozen, label.getTranslateY(), 0.001,
                    "after dispose the float listener / timeline must be detached");
        });
    }

    /**
     * While showing + animated, the built-in clear button fades in across the
     * empty -&gt; non-empty boundary instead of snapping (the headless tests only
     * see the snap path).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void clearButtonFadesInWhenShowing() throws InterruptedException {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setFocusTraversable(false);
            VBox root = new VBox(field);
            stage = new Stage();
            stage.setScene(new Scene(root, 320, 120));
            stage.show();
            root.applyCss();
            root.layout();

            StackPane clear = clearButton(field);
            assertNotNull(clear, "clear button must be present (editable + showClearButton)");
            assertEquals(0.0, clear.getOpacity(), 0.001, "empty -> clear hidden");

            // Crossing the boundary while showing + animated must run the fade,
            // not snap: on the same pulse the opacity has not reached 1 yet.
            field.setText("a");
            assertTrue(clear.getOpacity() < 1.0,
                    "clear button must animate in (not snap) while showing; opacity=" + clear.getOpacity());
        });
    }

    // ==================== Helpers ====================

    private static StackPane clearButton(RXMaterialTextField field) {
        Node node = field.lookup(".clear-button");
        return node instanceof StackPane stackPane ? stackPane : null;
    }

    private static RXMaterialTextField floatedField(boolean animated, Duration duration) {
        RXMaterialTextField field = new RXMaterialTextField("x");
        field.setLabelText("Name");
        field.setAnimated(animated);
        field.setAnimationDuration(duration);
        // Keep it unfocused on show so float state is driven purely by text.
        field.setFocusTraversable(false);
        return field;
    }

    private static Label floatingLabel(RXMaterialTextField field) {
        for (Node node : field.lookupAll(".label")) {
            if (node instanceof Label label && !inSupporting(node)) {
                return label;
            }
        }
        return null;
    }

    private static boolean inSupporting(Node node) {
        for (Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p.getStyleClass().contains("supporting")) {
                return true;
            }
        }
        return false;
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete in time");
        }
        Throwable t = failure.get();
        if (t instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
