package io.github.leewyatt.rxcontrols.internal;

import javafx.util.Callback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the value-to-text fallback contract of {@link CascaderText#resolve}:
 * a null value or a factory returning null both yield the empty string, and a null
 * value never reaches the factory.
 */
public class CascaderTextTest {

    /** No factory + null value falls back to the empty string, not {@code "null"}. */
    @Test
    public void nullValueWithoutFactoryYieldsEmptyString() {
        assertEquals("", CascaderText.resolve(null, null));
    }

    /** No factory + non-null value uses {@code String.valueOf}. */
    @Test
    public void nonNullValueWithoutFactoryUsesStringValueOf() {
        assertEquals("42", CascaderText.resolve(null, 42));
    }

    /** A factory returning null falls back to the empty string. */
    @Test
    public void factoryReturningNullYieldsEmptyString() {
        Callback<String, String> factory = value -> null;
        assertEquals("", CascaderText.resolve(factory, "x"));
    }

    /**
     * A null value short-circuits to the empty string and never reaches the factory,
     * so method-reference factories (e.g. {@code Option::label}) do not throw.
     */
    @Test
    public void nullValueWithFactoryYieldsEmptyStringWithoutCallingFactory() {
        Callback<String, String> factory = String::toUpperCase;
        assertEquals("", CascaderText.resolve(factory, null));
    }

    /** A factory transforms a non-null value. */
    @Test
    public void factoryTransformsNonNullValue() {
        Callback<String, String> factory = String::toUpperCase;
        assertEquals("ABC", CascaderText.resolve(factory, "abc"));
    }
}
