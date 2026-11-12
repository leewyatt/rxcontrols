package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sizing-contract tests for {@link RXFieldBaseSkin}: vertical text padding must
 * keep the min &lt;= pref invariant (TextFieldSkin maps min height to the
 * padding-inclusive pref via virtual dispatch — the skin must not re-add the
 * padding on top), and the reported baseline must shift with the tpTop editor
 * offset exactly like {@code getIndex} does, and keep sitting on the text when a
 * taller slot stretches the editor band.
 */
public class RXFieldBaseSkinSizingTest {

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
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void verticalTextPaddingKeepsMinWithinPref() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            field.setTextPadding(new Insets(4, 6, 4, 6));
            inScene(field);

            double min = field.minHeight(-1);
            double pref = field.prefHeight(-1);
            assertTrue(min <= pref, "min " + min + " must not exceed pref " + pref);
            // The user-visible consequence: a parent with spare height settles
            // the field at pref, not at an inflated min. Parents ceil the
            // resize (Region.snapSize), so allow [pref, ceil(pref)].
            double h = field.getHeight();
            assertTrue(h >= pref - 0.001 && h <= Math.ceil(pref) + 0.001,
                    "settled height " + h + " must be pref " + pref + " snapped up");
        });
    }

    @Test
    public void baselineOffsetShiftsByTextPaddingTop() {
        runOnFx(() -> {
            RXTextField plain = inScene(new RXTextField());
            RXTextField padded = new RXTextField();
            padded.setTextPadding(new Insets(8, 0, 0, 0));
            inScene(padded);

            double delta = padded.getBaselineOffset() - plain.getBaselineOffset();
            assertEquals(8.0, delta, 0.5,
                    "the baseline must shift by tpTop, mirroring the editor's layout shift");
        });
    }

    @Test
    public void baselineFollowsTheTextWhenATallerSlotStretchesTheField() {
        runOnFx(() -> {
            for (Pos alignment : new Pos[]{Pos.TOP_LEFT, Pos.CENTER_LEFT, Pos.BOTTOM_LEFT}) {
                RXTextField field = new RXTextField("text");
                field.setAlignment(alignment);
                field.setLeading(slot(40.0));
                inScene(field);
                assertEquals(textBaseline(field), field.getBaselineOffset(), 0.5,
                        "reported baseline sits on the text under " + alignment);
            }
        });
    }

    @Test
    public void materialBaselineFollowsTheTextWhenATallerSlotStretchesTheField() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setText("text");
            field.setLabelText("Label");
            field.setLeading(slot(40.0));
            inScene(field);
            assertEquals(textBaseline(field), field.getBaselineOffset(), 0.5,
                    "reported baseline sits on the text below the label band");
        });
    }

    private static Region slot(double size) {
        Region slot = new Region();
        slot.setPrefSize(size, size);
        return slot;
    }

    /** The editor text's baseline in the field's coordinates (Text draws from its baseline). */
    private static double textBaseline(TextField field) {
        Text text = (Text) field.lookupAll(".text").stream()
                .filter(n -> n instanceof Text t && t.layoutXProperty().isBound())
                .findFirst()
                .orElseThrow();
        return field.sceneToLocal(text.localToScene(0, text.getY())).getY();
    }

    private static <F extends TextField> F inScene(F field) {
        StackPane root = new StackPane(field);
        new Scene(root, 300, 200);
        root.applyCss();
        root.layout();
        return field;
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
