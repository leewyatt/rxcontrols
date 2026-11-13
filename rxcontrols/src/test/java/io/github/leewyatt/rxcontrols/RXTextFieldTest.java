package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTextField slots: migration between sides, slot sizing, and text padding.
 */
public class RXTextFieldTest {

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
    public void settingANodeOnOneSideClearsItFromTheOtherSide() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            Region icon = new Region();
            field.setLeading(icon);
            field.setTrailing(icon);
            assertNull(field.getLeading());
            assertSame(icon, field.getTrailing());
        });
    }

    @Test
    public void aBoundOppositeSlotIsNotCleared() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            Region icon = new Region();
            field.leadingProperty().bind(new SimpleObjectProperty<>(icon));
            field.setTrailing(icon);
            assertSame(icon, field.getLeading());
            assertSame(icon, field.getTrailing());
        });
    }

    @Test
    public void aBoundSlotGetsItsNodeBackWhenTheOtherSlotLetsGo() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            Region icon = new Region();
            field.leadingProperty().bind(new SimpleObjectProperty<>(icon));
            inScene(field);
            field.setTrailing(icon);
            field.setTrailing(null);
            assertSame(field.lookup(".leading-wrapper"), icon.getParent());
        });
    }

    @Test
    public void textPaddingIsStyleable() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            StackPane root = new StackPane(field);
            Scene scene = new Scene(root);
            String css = ".rx-text-field { -rx-text-padding: 0 0 0 20; }";
            scene.getStylesheets().add("data:text/css;base64,"
                    + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8)));
            root.applyCss();
            assertEquals(new Insets(0, 0, 0, 20), field.getTextPadding());
        });
    }

    @Test
    public void aWideSlotShrinksTowardItsMinWidthInsideTheField() {
        runOnFx(() -> {
            RXTextField field = new RXTextField("abc");
            Region wide = new Region();
            wide.setMinWidth(10);
            wide.setPrefWidth(400);
            field.setLeading(wide);
            field.setMaxWidth(200);
            inScene(field);

            Region wrapper = (Region) field.lookup(".leading-wrapper");
            assertEquals(200, field.getWidth(), 0.5);
            assertTrue(wrapper.getWidth() < field.getWidth(),
                    "the slot must fit inside the field, was " + wrapper.getWidth());
            assertTrue(wrapper.getWidth() >= wrapper.minWidth(-1),
                    "the slot must not shrink below its min, was " + wrapper.getWidth());
        });
    }

    @Test
    public void slotsShrinkOnlyAsFarAsTheFieldIsSqueezed() {
        runOnFx(() -> {
            RXTextField field = new RXTextField("abc");
            Region icon = new Region();
            icon.setMinWidth(10);
            icon.setPrefWidth(40);
            field.setLeading(icon);
            inScene(field);

            Region wrapper = (Region) field.lookup(".leading-wrapper");
            assertEquals(40, wrapper.getWidth(), 0.5, "a field with room keeps the slot at pref");
        });
    }

    @Test
    public void negativeOrNonFiniteTextPaddingCountsAsZero() {
        runOnFx(() -> {
            RXTextField plain = inScene(new RXTextField("abc"));
            plain.setTextPadding(Insets.EMPTY);
            plain.getParent().layout();
            double prefHeight = plain.prefHeight(-1);

            RXTextField negative = inScene(new RXTextField("abc"));
            negative.setTextPadding(new Insets(-8));
            negative.getParent().layout();
            assertEquals(prefHeight, negative.prefHeight(-1), 0.01);

            RXTextField nonFinite = inScene(new RXTextField("abc"));
            nonFinite.setTextPadding(new Insets(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NEGATIVE_INFINITY));
            nonFinite.getParent().layout();
            assertEquals(prefHeight, nonFinite.prefHeight(-1), 0.01);
            assertEquals(plain.prefWidth(-1), nonFinite.prefWidth(-1), 0.01);
        });
    }

    @Test
    public void changingOneSlotKeepsTheOtherSlotsWrapper() {
        runOnFx(() -> {
            RXTextField field = new RXTextField("abc");
            field.setLeading(new Region());
            field.setTrailing(new Region());
            inScene(field);
            Node trailingWrapper = field.lookup(".trailing-wrapper");

            field.setLeading(new Region());
            field.setLeading(null);

            assertSame(trailingWrapper, field.lookup(".trailing-wrapper"));
        });
    }

    private static RXTextField inScene(RXTextField field) {
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
