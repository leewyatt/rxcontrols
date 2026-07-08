package io.github.leewyatt.rxcontrols.internal;

import javafx.application.Platform;
import javafx.css.ParsedValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link EchoCharConverter}: the first character of a parsed
 * string wins, and absent / empty / non-string values fall back to the
 * configured character instead of propagating {@code null}.
 */
public class EchoCharConverterTest {

    /**
     * Starts the toolkit: the converter's class initialization touches
     * {@code RXPasswordField} (Control hierarchy), which requires it.
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
    }

    @Test
    public void firstCharacterOfTheParsedStringWins() {
        assertEquals('*', EchoCharConverter.withFallback('●').convert(parsed("*x"), null));
        assertEquals('#', EchoCharConverter.withFallback('●').convert(parsed("#"), null));
    }

    @Test
    public void emptyOrNonStringValuesFallBack() {
        assertEquals('●', EchoCharConverter.withFallback('●').convert(parsed(""), null));
        assertEquals('●', EchoCharConverter.withFallback('●')
                .convert(new ParsedValue<String, Character>(null, null) { }, null));
    }

    private static ParsedValue<String, Character> parsed(String value) {
        return new ParsedValue<>(value, null) { };
    }
}
