package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable metadata tag map from a parsed LRC document.
 *
 * <p>Known and unknown tags are preserved in {@link #tags()} with lower-case keys.
 * Convenience getters project the common LRC tags without removing the original
 * entries.</p>
 *
 * @param tags all parsed metadata tags keyed by lower-case tag name
 * @throws NullPointerException if {@code tags}, one of its keys, or one of its values is {@code null}
 */
public record RXLrcMetadata(Map<String, String> tags) {

    private static final String TITLE_KEY = "ti";
    private static final String ARTIST_KEY = "ar";
    private static final String ALBUM_KEY = "al";
    private static final String CREATOR_KEY = "au";
    private static final String LENGTH_KEY = "length";
    private static final String OFFSET_KEY = "offset";

    /**
     * Creates immutable LRC metadata.
     */
    public RXLrcMetadata {
        Objects.requireNonNull(tags, "tags");
        tags = Map.copyOf(tags);
    }

    /**
     * Returns the title tag value.
     *
     * @return the {@code ti} tag value, or {@code null} if absent
     */
    public String getTitle() {
        return tags.get(TITLE_KEY);
    }

    /**
     * Returns the artist tag value.
     *
     * @return the {@code ar} tag value, or {@code null} if absent
     */
    public String getArtist() {
        return tags.get(ARTIST_KEY);
    }

    /**
     * Returns the album tag value.
     *
     * @return the {@code al} tag value, or {@code null} if absent
     */
    public String getAlbum() {
        return tags.get(ALBUM_KEY);
    }

    /**
     * Returns the creator tag value.
     *
     * @return the {@code au} tag value, or {@code null} if absent
     */
    public String getCreator() {
        return tags.get(CREATOR_KEY);
    }

    /**
     * Returns the length tag value.
     *
     * @return the {@code length} tag value, or {@code null} if absent
     */
    public String getLength() {
        return tags.get(LENGTH_KEY);
    }

    /**
     * Returns the original signed LRC offset.
     *
     * <p>The returned value is the raw metadata offset. Parsed line times have
     * already baked this offset into their normalized display time.</p>
     *
     * @return the {@code offset} tag value as a duration, or {@link Duration#ZERO}
     *         if absent or invalid
     */
    public Duration getOffset() {
        String value = tags.get(OFFSET_KEY);
        if (value == null) {
            return Duration.ZERO;
        }
        try {
            return Duration.millis(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return Duration.ZERO;
        }
    }
}
