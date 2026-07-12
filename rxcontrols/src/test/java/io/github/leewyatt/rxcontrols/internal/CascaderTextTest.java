package io.github.leewyatt.rxcontrols.internal;

import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the value-to-text fallback contract of {@link CascaderText#resolve}:
 * a null value or a converter returning null both yield the empty string, and a null
 * value never reaches the converter.
 */
public class CascaderTextTest {

    /** No converter + null value falls back to the empty string, not {@code "null"}. */
    @Test
    public void nullValueWithoutConverterYieldsEmptyString() {
        assertEquals("", CascaderText.resolve(null, null));
    }

    /** No converter + non-null value uses {@code String.valueOf}. */
    @Test
    public void nonNullValueWithoutConverterUsesStringValueOf() {
        assertEquals("42", CascaderText.resolve(null, 42));
    }

    /** A converter returning null falls back to the empty string. */
    @Test
    public void converterReturningNullYieldsEmptyString() {
        assertEquals("", CascaderText.resolve(display(value -> null), "x"));
    }

    /**
     * A null value short-circuits to the empty string and never reaches the converter,
     * so method-reference display functions (e.g. {@code Option::label}) do not throw.
     */
    @Test
    public void nullValueWithConverterYieldsEmptyStringWithoutCallingConverter() {
        assertEquals("", CascaderText.resolve(display(String::toUpperCase), null));
    }

    /** A converter transforms a non-null value. */
    @Test
    public void converterTransformsNonNullValue() {
        assertEquals("ABC", CascaderText.resolve(display(String::toUpperCase), "abc"));
    }

    private static StringConverter<String> display(Function<String, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(String value) {
                return toText.apply(value);
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        };
    }
}
