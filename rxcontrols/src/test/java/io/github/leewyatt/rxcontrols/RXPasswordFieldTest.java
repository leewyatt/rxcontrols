package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link RXPasswordField}'s construction contract (a null
 * initial text yields {@code getText() == null}, matching
 * {@code TextField(String)}) and the {@code :revealed} pseudo-class driven by
 * {@code revealPassword}, and that revealing a long password keeps its end in view.
 */
public class RXPasswordFieldTest {

    private static final PseudoClass REVEALED = PseudoClass.getPseudoClass("revealed");

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

    @Test
    public void constructionTextMatchesTextFieldSemantics() {
        runOnFx(() -> {
            assertNull(new RXPasswordField().getText(),
                    "no-arg construction yields null text, matching TextField(String) semantics");
            assertEquals("x", new RXPasswordField("x").getText());
        });
    }

    @Test
    public void revealingALongPasswordKeepsItsEndInView() {
        runOnFx(() -> {
            RXPasswordField field = new RXPasswordField("averyveryverylongpassword123");
            field.setMaxWidth(120);
            StackPane root = new StackPane(field);
            new Scene(root, 320, 160);
            root.applyCss();
            root.layout();
            field.end();
            field.setRevealPassword(true);
            root.applyCss();
            root.layout();

            Text text = editorText(field);
            double clipWidth = ((Rectangle) text.getParent().getClip()).getWidth();
            double textEnd = text.getLayoutX() + text.getLayoutBounds().getWidth();
            assertTrue(textEnd > clipWidth - 4,
                    "the revealed text must end at the right edge next to the caret, not scroll out (end at "
                            + textEnd + " of " + clipWidth + ")");
        });
    }

    @Test
    public void revealingKeepsACaretClickedAtTheEndDrawnAtTheEnd() {
        runOnFx(() -> {
            RXPasswordField field = new RXPasswordField("secret");
            StackPane root = new StackPane(field);
            new Scene(root, 320, 160);
            root.applyCss();
            root.layout();
            Text text = editorText(field);
            Point2D blank = field.localToScene(field.getWidth() / 2, field.getHeight() / 2);
            field.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, blank.getX(), blank.getY(), 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, true, null));
            assertEquals(6, field.getCaretPosition(), "precondition: the click put the caret at the end");

            field.setRevealPassword(true);
            root.applyCss();
            root.layout();

            double caretX = ((MoveTo) text.getCaretShape()[0]).getX();
            assertEquals(text.getLayoutBounds().getWidth(), caretX, 0.5,
                    "the caret must stay drawn after the last character");
        });
    }

    @Test
    public void revealedPseudoClassFollowsRevealPassword() {
        runOnFx(() -> {
            RXPasswordField field = new RXPasswordField();
            assertFalse(field.getPseudoClassStates().contains(REVEALED));
            field.setRevealPassword(true);
            assertTrue(field.getPseudoClassStates().contains(REVEALED));
            field.setRevealPassword(false);
            assertFalse(field.getPseudoClassStates().contains(REVEALED));
        });
    }

    private static Text editorText(RXPasswordField field) {
        for (Node node : field.lookupAll(".text")) {
            if (node instanceof Text text && text.layoutXProperty().isBound()
                    && text.getParent() instanceof Pane pane && pane.getClip() instanceof Rectangle) {
                return text;
            }
        }
        throw new AssertionError("editor text node not found");
    }

    private static void runOnFx(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for FX task", e);
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }
}
