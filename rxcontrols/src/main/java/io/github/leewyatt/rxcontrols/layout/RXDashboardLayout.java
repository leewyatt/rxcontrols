package io.github.leewyatt.rxcontrols.layout;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of an {@link RXDashboardPane} layout: the column count it was taken at and, for every
 * card that has a stable {@link RXDashboardCard#cardIdProperty() cardId}, the cell area that card occupies.
 *
 * <p>A snapshot holds no nodes, pixels, row height or gaps, only card IDs and cell coordinates, so it can be
 * saved and restored across runs. {@link #encode()} turns it into a single line of text and {@link #decode(String)}
 * reads that text back; an application that prefers its own format (JSON, database columns) can read the
 * placements and rebuild a snapshot with {@link #builder(int)}.</p>
 *
 * <pre>{@code
 * RXDashboardLayout layout = RXDashboardLayout.builder(4)
 *         .card("trend", 0, 0, 2, 1)
 *         .card("customers", 2, 0, 1, 1)
 *         .card("kpi", 2, 1, 1, 1)
 *         .build();
 * String text = layout.encode();   // rxdash/1;columns=4;trend=0,0,2,1;customers=2,0,1,1;kpi=2,1,1,1
 * }</pre>
 *
 * <p>Placements are stored as given, including values that do not fit the grid (a negative row, a span wider than
 * the column count, two cards on the same cells). {@link RXDashboardPane#applyLayout(RXDashboardLayout)} corrects
 * such values with fixed rules when it applies the snapshot.</p>
 *
 * <p>Two snapshots are equal when they have the same column count and the same placement for every card ID; the
 * order in which the cards were added does not matter.</p>
 */
public final class RXDashboardLayout {

    // ==================== Text format ====================

    private static final String FORMAT_VERSION = "rxdash/1";
    private static final String COLUMNS_KEY = "columns";
    private static final char SEGMENT_SEPARATOR = ';';
    private static final char KEY_SEPARATOR = '=';
    private static final char VALUE_SEPARATOR = ',';
    private static final char ESCAPE = '%';
    private static final int PLACEMENT_FIELDS = 4;

    // Row first, then column: the order cards are read on screen. Ties (cards stored on the same cell) fall back
    // to the ID, so the text of a snapshot never depends on how it was built.
    private static final Comparator<Map.Entry<String, Placement>> READING_ORDER =
            Comparator.comparingInt((Map.Entry<String, Placement> entry) -> entry.getValue().row())
                    .thenComparingInt(entry -> entry.getValue().column())
                    .thenComparing(Map.Entry::getKey);

    // ==================== State ====================

    private final int columnCount;
    private final Map<String, Placement> placements;

    private RXDashboardLayout(int columnCount, Map<String, Placement> placements) {
        this.columnCount = columnCount;
        List<Map.Entry<String, Placement>> entries = new ArrayList<>(placements.entrySet());
        entries.sort(READING_ORDER);
        Map<String, Placement> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Placement> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        this.placements = Collections.unmodifiableMap(ordered);
    }

    // ==================== Factory ====================

    /**
     * Returns a builder for a snapshot taken at the given column count.
     *
     * @param columnCount the column count the placements refer to, at least 1
     * @return a new builder
     * @throws IllegalArgumentException if {@code columnCount} is less than 1
     */
    public static Builder builder(int columnCount) {
        return new Builder(columnCount);
    }

    // ==================== Accessors ====================

    /**
     * Returns the column count the placements refer to. Applying the snapshot to a pane with another column count
     * converts every placement to the pane's columns.
     *
     * @return the column count, at least 1
     */
    public int getColumnCount() {
        return columnCount;
    }

    /**
     * Returns the placement of every card in the snapshot, keyed by card ID, in reading order: row first, then
     * column.
     *
     * @return an unmodifiable map from card ID to placement
     */
    public Map<String, Placement> getPlacements() {
        return placements;
    }

    /**
     * Returns the placement stored for a card ID.
     *
     * @param cardId the card ID
     * @return the placement, or {@code null} if the snapshot has no card with this ID
     */
    public Placement getPlacement(String cardId) {
        return placements.get(cardId);
    }

    // ==================== Text encoding ====================

    /**
     * Encodes the snapshot as a single line of text: the format version, the column count, then one
     * {@code id=column,row,columnSpan,rowSpan} segment per card in reading order, all separated by {@code ;}.
     * The characters {@code %}, {@code ;}, {@code =} and {@code ,} in a card ID, and control characters, are
     * percent-encoded. The same snapshot always encodes to the same text.
     *
     * @return the encoded text, for example {@code rxdash/1;columns=4;trend=0,0,3,2;kpi=3,1,1,1}
     */
    public String encode() {
        StringBuilder text = new StringBuilder(FORMAT_VERSION);
        text.append(SEGMENT_SEPARATOR).append(COLUMNS_KEY).append(KEY_SEPARATOR).append(columnCount);
        for (Map.Entry<String, Placement> entry : placements.entrySet()) {
            Placement placement = entry.getValue();
            text.append(SEGMENT_SEPARATOR);
            encodeId(entry.getKey(), text);
            text.append(KEY_SEPARATOR).append(placement.column())
                    .append(VALUE_SEPARATOR).append(placement.row())
                    .append(VALUE_SEPARATOR).append(placement.columnSpan())
                    .append(VALUE_SEPARATOR).append(placement.rowSpan());
        }
        return text.toString();
    }

    /**
     * Reads a snapshot from text written by {@link #encode()}.
     *
     * @param text the encoded text
     * @return the snapshot
     * @throws NullPointerException     if {@code text} is {@code null}
     * @throws IllegalArgumentException if the text is damaged or written in a format version this library does
     *                                  not know
     */
    public static RXDashboardLayout decode(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        List<String> segments = split(text, SEGMENT_SEPARATOR);
        if (!FORMAT_VERSION.equals(segments.get(0))) {
            throw new IllegalArgumentException("Unknown dashboard layout format: " + abbreviate(segments.get(0)));
        }
        if (segments.size() < 2) {
            throw new IllegalArgumentException("Missing column count");
        }
        String columns = segments.get(1);
        String columnsPrefix = COLUMNS_KEY + KEY_SEPARATOR;
        if (!columns.startsWith(columnsPrefix)) {
            throw new IllegalArgumentException("Missing column count: " + abbreviate(columns));
        }
        int columnCount = parseInt(columns.substring(columnsPrefix.length()), "column count");
        if (columnCount < 1) {
            throw new IllegalArgumentException("Column count must be at least 1: " + columnCount);
        }
        Builder builder = new Builder(columnCount);
        for (int i = 2; i < segments.size(); i++) {
            String segment = segments.get(i);
            int separator = segment.indexOf(KEY_SEPARATOR);
            if (separator < 0) {
                throw new IllegalArgumentException("Card segment without '=': " + abbreviate(segment));
            }
            String id = decodeId(segment.substring(0, separator));
            List<String> fields = split(segment.substring(separator + 1), VALUE_SEPARATOR);
            if (fields.size() != PLACEMENT_FIELDS) {
                throw new IllegalArgumentException("Card segment needs four numbers: " + abbreviate(segment));
            }
            builder.card(id, parseInt(fields.get(0), "column"), parseInt(fields.get(1), "row"),
                    parseInt(fields.get(2), "column span"), parseInt(fields.get(3), "row span"));
        }
        return builder.build();
    }

    // ==================== Object ====================

    /**
     * Returns whether another object is a snapshot with the same column count and the same placement for every
     * card ID, regardless of the order the cards were added in.
     *
     * @param other the object to compare with
     * @return {@code true} if the snapshots are equal
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RXDashboardLayout layout)) {
            return false;
        }
        return columnCount == layout.columnCount && placements.equals(layout.placements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 31 * columnCount + placements.hashCode();
    }

    /**
     * Returns the encoded text of the snapshot, see {@link #encode()}.
     *
     * @return the encoded text
     */
    @Override
    public String toString() {
        return encode();
    }

    // ==================== Placement ====================

    /**
     * The cell area a card occupies: the column and row of its top-left cell, and how many columns and rows it
     * spans, all counted from 0 for positions and from 1 for spans.
     *
     * @param column     the column of the card's left edge
     * @param row        the row of the card's top edge
     * @param columnSpan the number of columns the card spans
     * @param rowSpan    the number of rows the card spans
     */
    public record Placement(int column, int row, int columnSpan, int rowSpan) {

        /**
         * Returns a placement with the same spans at another position.
         *
         * @param column the new column
         * @param row    the new row
         * @return the moved placement
         */
        public Placement withPosition(int column, int row) {
            return new Placement(column, row, columnSpan, rowSpan);
        }

        /**
         * Returns a placement at the same position with other spans.
         *
         * @param columnSpan the new column span
         * @param rowSpan    the new row span
         * @return the resized placement
         */
        public Placement withSpan(int columnSpan, int rowSpan) {
            return new Placement(column, row, columnSpan, rowSpan);
        }
    }

    // ==================== Builder ====================

    /**
     * Collects card placements for a new {@link RXDashboardLayout}.
     */
    public static final class Builder {

        private final int columnCount;
        private final Map<String, Placement> placements = new HashMap<>();

        private Builder(int columnCount) {
            if (columnCount < 1) {
                throw new IllegalArgumentException("Column count must be at least 1: " + columnCount);
            }
            this.columnCount = columnCount;
        }

        /**
         * Adds the placement of a card.
         *
         * @param cardId     the card ID, neither {@code null} nor empty
         * @param column     the column of the card's left edge
         * @param row        the row of the card's top edge
         * @param columnSpan the number of columns the card spans
         * @param rowSpan    the number of rows the card spans
         * @return this builder
         * @throws NullPointerException     if {@code cardId} is {@code null}
         * @throws IllegalArgumentException if {@code cardId} is empty or was already added
         */
        public Builder card(String cardId, int column, int row, int columnSpan, int rowSpan) {
            return card(cardId, new Placement(column, row, columnSpan, rowSpan));
        }

        /**
         * Adds the placement of a card.
         *
         * @param cardId    the card ID, neither {@code null} nor empty
         * @param placement the placement
         * @return this builder
         * @throws NullPointerException     if {@code cardId} or {@code placement} is {@code null}
         * @throws IllegalArgumentException if {@code cardId} is empty or was already added
         */
        public Builder card(String cardId, Placement placement) {
            Objects.requireNonNull(cardId, "cardId cannot be null");
            Objects.requireNonNull(placement, "placement cannot be null");
            if (cardId.isEmpty()) {
                throw new IllegalArgumentException("cardId cannot be empty");
            }
            if (placements.putIfAbsent(cardId, placement) != null) {
                throw new IllegalArgumentException("Duplicate cardId: " + cardId);
            }
            return this;
        }

        /**
         * Builds the snapshot. The builder can be used again afterwards; later additions do not change snapshots
         * already built.
         *
         * @return the snapshot
         */
        public RXDashboardLayout build() {
            return new RXDashboardLayout(columnCount, placements);
        }
    }

    // ==================== Helpers ====================

    private static void encodeId(String id, StringBuilder text) {
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == ESCAPE || c == SEGMENT_SEPARATOR || c == KEY_SEPARATOR || c == VALUE_SEPARATOR
                    || Character.isISOControl(c)) {
                // An escape stands for one UTF-8 byte; the C1 controls take two.
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    text.append(ESCAPE).append(Character.toUpperCase(Character.forDigit(b >> 4 & 0xF, 16)))
                            .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
                }
            } else {
                text.append(c);
            }
        }
    }

    // Percent escapes stand for UTF-8 bytes, so text written by hand with escaped non-ASCII characters reads back.
    private static String decodeId(String encoded) {
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Empty card ID");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encoded.length());
        int i = 0;
        while (i < encoded.length()) {
            char c = encoded.charAt(i);
            if (c == ESCAPE) {
                if (i + 2 >= encoded.length()) {
                    throw new IllegalArgumentException("Incomplete escape in card ID: " + abbreviate(encoded));
                }
                int high = Character.digit(encoded.charAt(i + 1), 16);
                int low = Character.digit(encoded.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Invalid escape in card ID: " + abbreviate(encoded));
                }
                bytes.write(high << 4 | low);
                i += 3;
            } else {
                int end = i + 1;
                if (Character.isHighSurrogate(c) && end < encoded.length()
                        && Character.isLowSurrogate(encoded.charAt(end))) {
                    end++;
                }
                bytes.writeBytes(encoded.substring(i, end).getBytes(StandardCharsets.UTF_8));
                i = end;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Card ID is not valid UTF-8: " + abbreviate(encoded), e);
        }
    }

    // Splits on every separator, keeping empty parts, so a stray separator shows up as an empty segment.
    private static List<String> split(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == separator) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    // Only plain decimal digits with an optional minus sign: Integer.parseInt also takes a leading plus sign and
    // non-ASCII digits, which encode() never writes.
    private static int parseInt(String value, String what) {
        boolean digits = !value.isEmpty();
        for (int i = 0; i < value.length() && digits; i++) {
            char c = value.charAt(i);
            digits = (c >= '0' && c <= '9') || (c == '-' && i == 0 && value.length() > 1);
        }
        if (digits) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException outOfRange) {
                // Falls through to the error below.
            }
        }
        throw new IllegalArgumentException("Invalid " + what + ": " + abbreviate(value));
    }

    private static String abbreviate(String value) {
        final int limit = 40;
        return value.length() <= limit ? "\"" + value + "\"" : "\"" + value.substring(0, limit) + "...\"";
    }
}
