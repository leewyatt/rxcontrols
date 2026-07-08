package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
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
 * {@code revealPassword}.
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
