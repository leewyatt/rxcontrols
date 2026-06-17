package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Control-level tests for {@link RXHighlightTextView}'s {@code highlightFill} property and
 * CSS metadata: the default value, the inherited {@link RXTextView} colour metadata, the
 * removal of the old {@code highlightTextFill} / {@code -rx-highlight-text-fill} capability,
 * and the {@code null} pass-through. All logic is on the control and headless-testable.
 */
public class RXHighlightTextViewTest {

    /**
     * Starts the JavaFX toolkit so control instances can be created off a live runtime.
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

    @Test
    public void controlCssMetaDataContainsHighlightFill() {
        assertTrue(cssPropertyNames(new RXHighlightTextView()).contains("-rx-highlight-fill"));
    }

    @Test
    public void controlCssMetaDataInheritsTextViewColors() {
        Set<String> names = cssPropertyNames(new RXHighlightTextView());
        assertTrue(names.contains("-rx-text-fill"));
        assertTrue(names.contains("-rx-selection-fill"));
        assertTrue(names.contains("-rx-selected-text-fill"));
    }

    @Test
    public void controlCssMetaDataHasNoHighlightTextFill() {
        assertFalse(cssPropertyNames(new RXHighlightTextView()).contains("-rx-highlight-text-fill"));
    }

    @Test
    public void highlightFillAcceptsNullWithoutThrowing() {
        RXHighlightTextView control = new RXHighlightTextView("hello");
        assertDoesNotThrow(() -> control.setHighlightFill(null));
        assertNull(control.getHighlightFill());
    }

    @Test
    public void noHighlightTextFillApiExists() {
        // The keyword foreground capability was intentionally removed; only a background
        // highlight remains. Guard against it creeping back in as a public method.
        for (Method method : RXHighlightTextView.class.getMethods()) {
            String name = method.getName();
            assertFalse(name.equals("highlightTextFillProperty")
                            || name.equals("getHighlightTextFill")
                            || name.equals("setHighlightTextFill"),
                    "highlightTextFill API must not exist: " + name);
        }
    }

    private static Set<String> cssPropertyNames(RXHighlightTextView control) {
        Set<String> names = new HashSet<>();
        for (CssMetaData<? extends Styleable, ?> metaData : control.getControlCssMetaData()) {
            names.add(metaData.getProperty());
        }
        return names;
    }
}
