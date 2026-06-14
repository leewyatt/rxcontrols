package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.control.IndexRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Computes the merged highlight ranges for a piece of text against a set of keywords.
 *
 * <p>Every keyword is matched independently for all of its non-overlapping occurrences
 * (literally or as a regular expression). The resulting ranges are merged where they
 * overlap or touch, so overlapping keywords (e.g. {@code "abc"} and {@code "bcd"} over
 * {@code "abcdefg"}) produce a single contiguous highlighted range ({@code "abcd"})
 * rather than one keyword swallowing the others.
 *
 * <p>Matching runs on the caller's thread; a catastrophically backtracking regular
 * expression over very large text can stall it, so callers should keep patterns simple.
 */
public final class HighlightSegmenter {

    private HighlightSegmenter() {
    }

    /**
     * Computes the merged highlight ranges for {@code text} against the given keywords.
     *
     * <p>Each keyword is matched for all of its non-overlapping occurrences (literally
     * or as a regular expression); ranges from different keywords that overlap or touch
     * are then merged, so the result is an ordered list of non-overlapping
     * {@link IndexRange ranges} that covers every highlighted span exactly once.
     * Zero-width matches (e.g. {@code "a*"}) and invalid-regex keywords are skipped.
     *
     * @param text       the source text; {@code null} is treated as empty
     * @param keywords   keywords to match; {@code null} / blank entries are ignored
     * @param regex      {@code true} to treat each keyword as a regular expression,
     *                   {@code false} to match it literally
     * @param ignoreCase {@code true} to match case-insensitively
     * @return an ordered, non-overlapping, unmodifiable list of highlight ranges;
     *         empty when nothing matches
     */
    public static List<IndexRange> highlightRanges(String text, List<String> keywords,
                                                   boolean regex, boolean ignoreCase) {
        String source = text == null ? "" : text;
        List<int[]> ranges = mergeRanges(collectRanges(source, keywords, regex, ignoreCase));
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<IndexRange> result = new ArrayList<>(ranges.size());
        for (int[] range : ranges) {
            result.add(new IndexRange(range[0], range[1]));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<int[]> collectRanges(String source, List<String> keywords,
                                             boolean regex, boolean ignoreCase) {
        List<int[]> ranges = new ArrayList<>();
        if (source.isEmpty() || keywords == null) {
            return ranges;
        }
        for (String keyword : keywords) {
            Pattern pattern = compile(keyword, regex, ignoreCase);
            if (pattern == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                if (matcher.end() == matcher.start()) {
                    // Skip zero-width matches (e.g. "a*"); Matcher.find() advances
                    // past them on its own, so this cannot loop forever.
                    continue;
                }
                ranges.add(new int[]{matcher.start(), matcher.end()});
            }
        }
        return ranges;
    }

    private static Pattern compile(String keyword, boolean regex, boolean ignoreCase) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        int flags = ignoreCase ? (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : 0;
        try {
            return Pattern.compile(regex ? keyword : Pattern.quote(keyword), flags);
        } catch (PatternSyntaxException e) {
            // Invalid regex keyword: skip it, the others still apply.
            return null;
        }
    }

    private static List<int[]> mergeRanges(List<int[]> ranges) {
        if (ranges.size() < 2) {
            return ranges;
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        List<int[]> merged = new ArrayList<>();
        int[] current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            int[] next = ranges.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}
