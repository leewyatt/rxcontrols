package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static, lenient parser for standard line-level LRC text.
 */
public final class RXLrcParser {

    private static final String BOM = "\uFEFF";
    private static final String OFFSET_KEY = "offset";

    private static final int FIRST_LINE_NUMBER = 1;
    private static final int DOCUMENT_WARNING_LINE = -1;
    private static final int MAX_SECONDS = 59;
    private static final long DECIMAL_BASE = 10L;
    private static final long MS_PER_SECOND = 1000L;
    private static final long MS_PER_MINUTE = 60_000L;

    private static final Pattern LINE_SEPARATOR_REGEX = Pattern.compile("\\r\\n|\\r|\\n");
    private static final Pattern TIME_TAG_REGEX =
            Pattern.compile("\\[(\\d{1,5}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern ID_TAG_REGEX =
            Pattern.compile("\\[([a-zA-Z]+):(.*?)\\]");
    private static final Set<String> KNOWN_METADATA_KEYS = Set.of(
            "ti", "ar", "al", "au", "lr", "length", "by", OFFSET_KEY, "re", "tool", "ve");

    private static final Comparator<NormalizedLine> NORMALIZED_LINE_ORDER =
            Comparator.comparingLong(NormalizedLine::timeMillis)
                    .thenComparingInt(NormalizedLine::sourceLineNumber)
                    .thenComparingInt(NormalizedLine::tagOrder);

    private RXLrcParser() {
    }

    /**
     * Parses raw LRC text into an immutable document and parse warnings.
     *
     * <p>Content errors are reported as warnings instead of exceptions. Blank or
     * whitespace-only input returns {@link RXLrcDocument#empty()} with no
     * warnings.</p>
     *
     * @param text the raw LRC text, already decoded by the caller
     * @return the parse result; its document is never {@code null}
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public static RXLrcParseResult parse(String text) {
        Objects.requireNonNull(text, "text");

        String source = stripBom(text);
        if (source.isBlank()) {
            return new RXLrcParseResult(RXLrcDocument.empty(), List.of());
        }

        Map<String, String> tags = new HashMap<>();
        List<ParsedLine> parsedLines = new ArrayList<>();
        List<RXLrcParseWarning> warnings = new ArrayList<>();
        String[] sourceLines = LINE_SEPARATOR_REGEX.split(source, -1);
        for (int i = 0; i < sourceLines.length; i++) {
            parseLine(sourceLines[i], i + FIRST_LINE_NUMBER, tags, parsedLines, warnings);
        }

        RXLrcMetadata metadata = new RXLrcMetadata(tags);
        if (parsedLines.isEmpty()) {
            warnings.add(new RXLrcParseWarning(DOCUMENT_WARNING_LINE, "",
                    RXLrcWarningCode.NO_TIMED_LINES, "No timed lyric lines were found."));
            return new RXLrcParseResult(new RXLrcDocument(metadata, List.of()), warnings);
        }

        List<NormalizedLine> normalizedLines = normalizeLines(parsedLines, metadata.getOffset());
        normalizedLines.sort(NORMALIZED_LINE_ORDER);
        addDuplicateWarnings(normalizedLines, warnings);

        List<RXLrcLine> lines = new ArrayList<>(normalizedLines.size());
        for (int i = 0; i < normalizedLines.size(); i++) {
            NormalizedLine line = normalizedLines.get(i);
            lines.add(new RXLrcLine(
                    i,
                    Duration.millis(line.timeMillis()),
                    durationAt(normalizedLines, i),
                    line.text(),
                    null,
                    null,
                    line.rawLine(),
                    line.sourceLineNumber()));
        }

        return new RXLrcParseResult(new RXLrcDocument(metadata, lines), warnings);
    }

    private static String stripBom(String text) {
        if (text.startsWith(BOM)) {
            return text.substring(BOM.length());
        }
        return text;
    }

    private static void parseLine(String line, int lineNumber, Map<String, String> tags,
                                  List<ParsedLine> parsedLines,
                                  List<RXLrcParseWarning> warnings) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (parseMetadataLine(trimmed, lineNumber, line, tags, warnings)) {
            return;
        }
        if (looksLikeBrokenKnownMetadata(trimmed)) {
            warnings.add(new RXLrcParseWarning(lineNumber, line,
                    RXLrcWarningCode.INVALID_METADATA, "Malformed metadata tag."));
            return;
        }

        TimeScan scan = scanLeadingTimeTags(trimmed, lineNumber, line, warnings);
        if (scan.sawTimeTag()) {
            if (!scan.times().isEmpty()) {
                String lyricText = trimmed.substring(scan.endIndex()).trim();
                for (int i = 0; i < scan.times().size(); i++) {
                    parsedLines.add(new ParsedLine(scan.times().get(i), lyricText, line, lineNumber, i));
                }
            }
            return;
        }

        warnings.add(new RXLrcParseWarning(lineNumber, line,
                RXLrcWarningCode.UNTIMED_TEXT, "Untimed text line was skipped."));
    }

    private static boolean parseMetadataLine(String trimmed, int lineNumber, String rawLine,
                                             Map<String, String> tags,
                                             List<RXLrcParseWarning> warnings) {
        Matcher matcher = ID_TAG_REGEX.matcher(trimmed);
        int position = 0;
        boolean found = false;
        Map<String, String> parsedTags = new HashMap<>();
        List<RXLrcParseWarning> parsedWarnings = new ArrayList<>();
        while (matcher.find()) {
            if (!trimmed.substring(position, matcher.start()).isBlank()) {
                return false;
            }
            found = true;
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2).trim();
            parsedTags.put(key, value);
            if (OFFSET_KEY.equals(key) && !isValidOffset(value)) {
                parsedWarnings.add(new RXLrcParseWarning(lineNumber, rawLine,
                        RXLrcWarningCode.INVALID_OFFSET, "Invalid LRC offset metadata."));
            }
            position = matcher.end();
        }
        if (!found || !trimmed.substring(position).isBlank()) {
            return false;
        }
        tags.putAll(parsedTags);
        warnings.addAll(parsedWarnings);
        return true;
    }

    private static boolean looksLikeBrokenKnownMetadata(String trimmed) {
        if (!trimmed.startsWith("[")) {
            return false;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 1) {
            return false;
        }
        String key = trimmed.substring(1, colon).toLowerCase(Locale.ROOT);
        return KNOWN_METADATA_KEYS.contains(key) && !trimmed.endsWith("]");
    }

    private static TimeScan scanLeadingTimeTags(String trimmed, int lineNumber, String rawLine,
                                                List<RXLrcParseWarning> warnings) {
        List<Long> times = new ArrayList<>();
        int position = 0;
        boolean sawTimeTag = false;
        while (position < trimmed.length()) {
            while (position < trimmed.length() && Character.isWhitespace(trimmed.charAt(position))) {
                position++;
            }
            if (position >= trimmed.length() || trimmed.charAt(position) != '[') {
                break;
            }

            int closing = trimmed.indexOf(']', position);
            if (closing < 0) {
                break;
            }

            String tag = trimmed.substring(position, closing + 1);
            Matcher matcher = TIME_TAG_REGEX.matcher(tag);
            if (matcher.matches()) {
                sawTimeTag = true;
                Long time = parseTimestampMillis(matcher, lineNumber, rawLine, warnings);
                if (time != null) {
                    times.add(time);
                }
                position = closing + 1;
            } else if (isTimestampLike(tag)) {
                sawTimeTag = true;
                warnings.add(new RXLrcParseWarning(lineNumber, rawLine,
                        RXLrcWarningCode.INVALID_TIMESTAMP, "Invalid timestamp tag was skipped."));
                position = closing + 1;
            } else {
                break;
            }
        }
        return new TimeScan(List.copyOf(times), position, sawTimeTag);
    }

    private static Long parseTimestampMillis(Matcher matcher, int lineNumber, String rawLine,
                                             List<RXLrcParseWarning> warnings) {
        int seconds = Integer.parseInt(matcher.group(2));
        if (seconds > MAX_SECONDS) {
            warnings.add(new RXLrcParseWarning(lineNumber, rawLine,
                    RXLrcWarningCode.INVALID_TIMESTAMP, "Timestamp seconds must be between 0 and 59."));
            return null;
        }

        long minutes = Long.parseLong(matcher.group(1));
        return minutes * MS_PER_MINUTE
                + seconds * MS_PER_SECOND
                + parseFractionMillis(matcher.group(3));
    }

    private static long parseFractionMillis(String digits) {
        if (digits == null) {
            return 0L;
        }
        long denominator = 1L;
        for (int i = 0; i < digits.length(); i++) {
            denominator *= DECIMAL_BASE;
        }
        return Math.round(Integer.parseInt(digits) * (double) MS_PER_SECOND / denominator);
    }

    private static boolean isTimestampLike(String tag) {
        String content = tag.substring(1, tag.length() - 1).trim();
        return content.contains(":");
    }

    private static boolean isValidOffset(String value) {
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static List<NormalizedLine> normalizeLines(List<ParsedLine> parsedLines, Duration offset) {
        long offsetMillis = Math.round(offset.toMillis());
        List<NormalizedLine> normalizedLines = new ArrayList<>(parsedLines.size());
        for (ParsedLine line : parsedLines) {
            long normalizedTime = Math.max(0L, line.rawTimeMillis() - offsetMillis);
            normalizedLines.add(new NormalizedLine(
                    normalizedTime,
                    line.text(),
                    line.rawLine(),
                    line.sourceLineNumber(),
                    line.tagOrder()));
        }
        return normalizedLines;
    }

    private static void addDuplicateWarnings(List<NormalizedLine> lines,
                                             List<RXLrcParseWarning> warnings) {
        int index = 0;
        while (index < lines.size()) {
            NormalizedLine first = lines.get(index);
            int next = index + 1;
            while (next < lines.size() && lines.get(next).timeMillis() == first.timeMillis()) {
                next++;
            }
            if (next - index > 1) {
                warnings.add(new RXLrcParseWarning(first.sourceLineNumber(), first.rawLine(),
                        RXLrcWarningCode.DUPLICATE_TIMESTAMP,
                        "Duplicate timestamp after offset normalization."));
            }
            index = next;
        }
    }

    private static Duration durationAt(List<NormalizedLine> lines, int index) {
        long current = lines.get(index).timeMillis();
        for (int i = index + 1; i < lines.size(); i++) {
            long next = lines.get(i).timeMillis();
            if (next != current) {
                return Duration.millis(next - current);
            }
        }
        return Duration.UNKNOWN;
    }

    private record ParsedLine(long rawTimeMillis, String text, String rawLine,
                              int sourceLineNumber, int tagOrder) {
    }

    private record NormalizedLine(long timeMillis, String text, String rawLine,
                                  int sourceLineNumber, int tagOrder) {
    }

    private record TimeScan(List<Long> times, int endIndex, boolean sawTimeTag) {
    }
}
