package io.github.leewyatt.rxcontrols.layout;

import javafx.beans.NamedArg;

import java.util.Objects;

/**
 * Immutable responsive column override for a breakpoint.
 *
 * <p>Unset fields inherit the value resolved from the previous breakpoint or
 * from the column's base property. Use {@link #of(int, int)} with
 * {@code offset=0} to clear an inherited offset at a breakpoint, and use
 * {@link Builder#hidden(boolean)} with {@code false} to clear an inherited
 * hidden state.</p>
 */
public final class RXColSpec {

    private static final String KEY_SPAN = "span";
    private static final String KEY_OFFSET = "offset";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";

    private final Integer span;
    private final Integer offset;
    private final Integer order;
    private final Boolean hidden;

    /**
     * Creates a spec with independently optional breakpoint overrides.
     *
     * <p>This constructor primarily supports FXML {@code @NamedArg}
     * construction. Java code should prefer {@link #of(int)}, {@link #of(int, int)}
     * or {@link #builder()} for clearer call sites.</p>
     *
     * @param span   the span override, or {@code null} to inherit
     * @param offset the offset override, or {@code null} to inherit
     * @param order  the visual order override, or {@code null} to inherit
     * @param hidden the hidden override, or {@code null} to inherit
     * @throws IllegalArgumentException if {@code span} or {@code offset} is negative
     */
    public RXColSpec(@NamedArg(KEY_SPAN) Integer span,
                     @NamedArg(KEY_OFFSET) Integer offset,
                     @NamedArg(KEY_ORDER) Integer order,
                     @NamedArg(KEY_HIDDEN) Boolean hidden) {
        validateNonNegative(span, "span");
        validateNonNegative(offset, "offset");
        this.span = span;
        this.offset = offset;
        this.order = order;
        this.hidden = hidden;
    }

    /**
     * Creates a spec that overrides only the span.
     *
     * <p>Offset, order, and hidden remain unset and therefore inherit the
     * previous breakpoint's resolved values. Use {@link #of(int, int)} with
     * {@code offset=0} to clear an inherited offset.</p>
     *
     * @param span the span override
     * @return the spec
     * @throws IllegalArgumentException if {@code span < 0}
     */
    public static RXColSpec of(int span) {
        return new RXColSpec(span, null, null, null);
    }

    /**
     * Creates a spec that overrides span and offset.
     *
     * <p>Order and hidden remain unset and therefore inherit the previous
     * breakpoint's resolved values.</p>
     *
     * @param span   the span override
     * @param offset the offset override
     * @return the spec
     * @throws IllegalArgumentException if either value is negative
     */
    public static RXColSpec of(int span, int offset) {
        return new RXColSpec(span, offset, null, null);
    }

    /**
     * Parses an FXML-friendly responsive spec string.
     *
     * <p>The first positional value is {@code span}; the second positional
     * value is {@code offset}. Keyword tokens use {@code key=value} with keys
     * {@code span}, {@code offset}, {@code order}, and {@code hidden}. Positional
     * tokens must appear before keyword tokens. Missing fields remain unset and
     * therefore inherit from the previous breakpoint or base column property.</p>
     *
     * @param value the spec string
     * @return the parsed spec
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if the string is blank or malformed
     */
    public static RXColSpec valueOf(String value) {
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("value cannot be blank");
        }

        Builder builder = builder();
        ParseState state = new ParseState();
        for (String token : text.split(",", -1)) {
            parseToken(token.trim(), builder, state);
        }
        return builder.build();
    }

    /**
     * Creates a builder for a partial spec.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the span override.
     *
     * @return the span, or {@code null} to inherit
     */
    public Integer getSpan() {
        return span;
    }

    /**
     * Returns the offset override.
     *
     * @return the offset, or {@code null} to inherit
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns the visual order override.
     *
     * @return the order, or {@code null} to inherit
     */
    public Integer getOrder() {
        return order;
    }

    /**
     * Returns the hidden override.
     *
     * @return the hidden flag, or {@code null} to inherit
     */
    public Boolean getHidden() {
        return hidden;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RXColSpec other)) {
            return false;
        }
        return Objects.equals(span, other.span)
                && Objects.equals(offset, other.offset)
                && Objects.equals(order, other.order)
                && Objects.equals(hidden, other.hidden);
    }

    @Override
    public int hashCode() {
        return Objects.hash(span, offset, order, hidden);
    }

    @Override
    public String toString() {
        return "RXColSpec[span=" + span
                + ", offset=" + offset
                + ", order=" + order
                + ", hidden=" + hidden
                + "]";
    }

    private static void validateNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static void parseToken(String token, Builder builder, ParseState state) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("spec token cannot be blank");
        }

        int firstEquals = token.indexOf('=');
        if (firstEquals >= 0) {
            parseKeywordToken(token, firstEquals, builder, state);
        } else {
            parsePositionalToken(token, builder, state);
        }
    }

    private static void parseKeywordToken(String token, int firstEquals,
                                          Builder builder, ParseState state) {
        if (firstEquals != token.lastIndexOf('=')) {
            throw new IllegalArgumentException("spec token must contain exactly one '=': " + token);
        }
        String key = token.substring(0, firstEquals).trim();
        String rawValue = token.substring(firstEquals + 1).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("spec key cannot be blank: " + token);
        }
        if (rawValue.isEmpty()) {
            throw new IllegalArgumentException("spec value cannot be blank: " + token);
        }

        state.keywordSeen = true;
        switch (key) {
            case KEY_SPAN -> setSpan(builder, state, parseInteger(rawValue, key));
            case KEY_OFFSET -> setOffset(builder, state, parseInteger(rawValue, key));
            case KEY_ORDER -> setOrder(builder, state, parseInteger(rawValue, key));
            case KEY_HIDDEN -> setHidden(builder, state, parseBoolean(rawValue, key));
            default -> throw new IllegalArgumentException("unknown spec key: " + key);
        }
    }

    private static void parsePositionalToken(String token, Builder builder, ParseState state) {
        if (state.keywordSeen) {
            throw new IllegalArgumentException(
                    "positional spec token cannot follow keyword token: " + token);
        }

        int value = parseInteger(token, "positional");
        if (state.positionalIndex == 0) {
            setSpan(builder, state, value);
        } else if (state.positionalIndex == 1) {
            setOffset(builder, state, value);
        } else {
            throw new IllegalArgumentException("too many positional spec tokens: " + token);
        }
        state.positionalIndex++;
    }

    private static int parseInteger(String rawValue, String name) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer: " + rawValue, exception);
        }
    }

    private static boolean parseBoolean(String rawValue, String name) {
        if ("true".equalsIgnoreCase(rawValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false: " + rawValue);
    }

    private static void setSpan(Builder builder, ParseState state, int value) {
        if (state.spanSet) {
            throw new IllegalArgumentException("span is already set");
        }
        builder.span(value);
        state.spanSet = true;
    }

    private static void setOffset(Builder builder, ParseState state, int value) {
        if (state.offsetSet) {
            throw new IllegalArgumentException("offset is already set");
        }
        builder.offset(value);
        state.offsetSet = true;
    }

    private static void setOrder(Builder builder, ParseState state, int value) {
        if (state.orderSet) {
            throw new IllegalArgumentException("order is already set");
        }
        builder.order(value);
        state.orderSet = true;
    }

    private static void setHidden(Builder builder, ParseState state, boolean value) {
        if (state.hiddenSet) {
            throw new IllegalArgumentException("hidden is already set");
        }
        builder.hidden(value);
        state.hiddenSet = true;
    }

    private static final class ParseState {

        private boolean spanSet;
        private boolean offsetSet;
        private boolean orderSet;
        private boolean hiddenSet;
        private boolean keywordSeen;
        private int positionalIndex;
    }

    /**
     * Builder for {@link RXColSpec}.
     */
    public static final class Builder {

        private Integer span;
        private Integer offset;
        private Integer order;
        private Boolean hidden;

        private Builder() {
        }

        /**
         * Sets the span override.
         *
         * @param span the span
         * @return this builder
         * @throws IllegalArgumentException if {@code span < 0}
         */
        public Builder span(int span) {
            validateNonNegative(span, "span");
            this.span = span;
            return this;
        }

        /**
         * Sets the offset override.
         *
         * @param offset the offset
         * @return this builder
         * @throws IllegalArgumentException if {@code offset < 0}
         */
        public Builder offset(int offset) {
            validateNonNegative(offset, "offset");
            this.offset = offset;
            return this;
        }

        /**
         * Sets the visual order override. Lower values are laid out first.
         *
         * @param order the order
         * @return this builder
         */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /**
         * Sets the hidden override.
         *
         * @param hidden whether the column is hidden
         * @return this builder
         */
        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        /**
         * Builds the immutable spec.
         *
         * @return the spec
         */
        public RXColSpec build() {
            return new RXColSpec(span, offset, order, hidden);
        }
    }
}
