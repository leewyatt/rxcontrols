package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.util.StringConverter;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Locale-aware texts, formatters and default converters shared by the date / time
 * views and pickers. Every resolver tolerates {@code null} inputs and falls back to
 * the platform default, so controls can keep their properties lenient.
 */
public final class DateTimeTexts {

    /** Matches a run of {@code y} (year-of-era) letters that strict parsing cannot resolve without an era. */
    private static final Pattern YEAR_RUN = Pattern.compile("y+");
    /** Matches exactly-doubled numeric fields, which parse as fixed width; a single letter accepts 1 or 2 digits. */
    private static final Pattern DOUBLED_NUMERIC = Pattern.compile("(?<![dMHh])([dMHh])\\1(?![dMHh])");

    private DateTimeTexts() {
    }

    // ==================== Resolvers ====================

    /**
     * Resolves a nullable locale.
     *
     * @param locale the locale, or {@code null}
     * @return the locale, or the default format locale when {@code null}
     */
    public static Locale locale(Locale locale) {
        return locale != null ? locale : Locale.getDefault(Locale.Category.FORMAT);
    }

    /**
     * Resolves a nullable clock.
     *
     * @param clock the clock, or {@code null}
     * @return the clock, or the system default zone clock when {@code null}
     */
    public static Clock clock(Clock clock) {
        return clock != null ? clock : Clock.systemDefaultZone();
    }

    /**
     * Resolves nullable week fields.
     *
     * @param weekFields the week fields, or {@code null}
     * @param locale     the locale used when {@code weekFields} is {@code null}
     * @return the week fields
     */
    public static WeekFields weekFields(WeekFields weekFields, Locale locale) {
        return weekFields != null ? weekFields : WeekFields.of(locale(locale));
    }

    // ==================== Names ====================

    /**
     * Returns the weekday header text: narrow for CJK locales (one glyph), short otherwise.
     *
     * @param day    the weekday
     * @param locale the locale, or {@code null}
     * @return the header text
     */
    public static String weekdayHeader(DayOfWeek day, Locale locale) {
        Locale resolved = locale(locale);
        TextStyle style = isCjk(resolved) ? TextStyle.NARROW_STANDALONE : TextStyle.SHORT_STANDALONE;
        return day.getDisplayName(style, resolved);
    }

    /**
     * Returns the short stand-alone month name, such as {@code Nov} or {@code 11月}.
     *
     * @param month  the month
     * @param locale the locale, or {@code null}
     * @return the month name
     */
    public static String monthShort(Month month, Locale locale) {
        return month.getDisplayName(TextStyle.SHORT_STANDALONE, locale(locale));
    }

    /**
     * Returns the year label, such as {@code 2026} or {@code 2026年}.
     *
     * @param year   the year
     * @param locale the locale, or {@code null}
     * @return the year label
     */
    public static String yearLabel(int year, Locale locale) {
        Locale resolved = locale(locale);
        String language = resolved.getLanguage();
        if ("zh".equals(language) || "ja".equals(language)) {
            return year + "年";
        }
        if ("ko".equals(language)) {
            return year + "년";
        }
        return Integer.toString(year);
    }

    /**
     * Returns the quarter label, such as {@code Q3}.
     *
     * @param quarter the quarter, 1 to 4
     * @return the quarter label
     */
    public static String quarterLabel(int quarter) {
        return "Q" + quarter;
    }

    /**
     * Returns whether month-and-year headers put the year first (CJK convention).
     *
     * @param locale the locale, or {@code null}
     * @return {@code true} if the year comes first
     */
    public static boolean yearFirst(Locale locale) {
        return isCjk(locale(locale));
    }

    private static boolean isCjk(Locale locale) {
        String language = locale.getLanguage();
        return "zh".equals(language) || "ja".equals(language) || "ko".equals(language);
    }

    // ==================== Patterns ====================

    /**
     * Returns the localized short date pattern with a four-digit year.
     *
     * @param locale the locale, or {@code null}
     * @return the pattern, using {@code uuuu} for the year
     */
    public static String datePattern(Locale locale) {
        String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale(locale));
        return YEAR_RUN.matcher(pattern).replaceAll("uuuu");
    }

    /**
     * Returns the time pattern for the given column configuration.
     *
     * @param showSeconds whether seconds are shown
     * @param twelveHour  whether a 12-hour clock with an AM/PM marker is used
     * @return the pattern
     */
    public static String timePattern(boolean showSeconds, boolean twelveHour) {
        String base = twelveHour ? "hh:mm" : "HH:mm";
        String seconds = showSeconds ? ":ss" : "";
        return twelveHour ? base + seconds + " a" : base + seconds;
    }

    // ==================== Converters ====================

    /**
     * Default date converter for the locale.
     *
     * @param locale the locale, or {@code null}
     * @return the converter
     */
    public static StringConverter<LocalDate> dateConverter(Locale locale) {
        return converter(datePattern(locale), locale, LocalDate::from);
    }

    /**
     * Default time converter.
     *
     * @param locale      the locale, or {@code null}
     * @param showSeconds whether seconds are shown
     * @param twelveHour  whether a 12-hour clock is used
     * @return the converter
     */
    public static StringConverter<LocalTime> timeConverter(Locale locale, boolean showSeconds, boolean twelveHour) {
        return converter(timePattern(showSeconds, twelveHour), locale, LocalTime::from);
    }

    /**
     * Default date-time converter: the date pattern, a space, then the time pattern.
     *
     * @param locale      the locale, or {@code null}
     * @param showSeconds whether seconds are shown
     * @param twelveHour  whether a 12-hour clock is used
     * @return the converter
     */
    public static StringConverter<LocalDateTime> dateTimeConverter(Locale locale, boolean showSeconds,
                                                                   boolean twelveHour) {
        return converter(datePattern(locale) + " " + timePattern(showSeconds, twelveHour), locale,
                LocalDateTime::from);
    }

    /**
     * Default year-month converter ({@code uuuu-MM}).
     *
     * @param locale the locale, or {@code null}
     * @return the converter
     */
    public static StringConverter<YearMonth> yearMonthConverter(Locale locale) {
        return converter("uuuu-MM", locale, YearMonth::from);
    }

    /**
     * Default year converter ({@code uuuu}).
     *
     * @param locale the locale, or {@code null}
     * @return the converter
     */
    public static StringConverter<Year> yearConverter(Locale locale) {
        return converter("uuuu", locale, Year::from);
    }

    /**
     * Default year-quarter converter, using the {@code 2026-Q1} text form.
     *
     * @return the converter
     */
    public static StringConverter<RXYearQuarter> yearQuarterConverter() {
        return new TextConverter<>(RXYearQuarter::toString, RXYearQuarter::parse);
    }

    /**
     * Creates a converter that formats with {@code pattern} and parses strictly, also
     * accepting single-digit day, month and hour fields.
     *
     * @param pattern the format pattern
     * @param locale  the locale, or {@code null}
     * @param query   the query extracting the value from a parsed result
     * @param <T>     the value type
     * @return the converter
     */
    public static <T extends TemporalAccessor> StringConverter<T> converter(String pattern, Locale locale,
                                                                          TemporalQuery<T> query) {
        Locale resolved = locale(locale);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern, resolved);
        String parsePattern = DOUBLED_NUMERIC.matcher(pattern).replaceAll("$1");
        DateTimeFormatter parse = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(parsePattern)
                .toFormatter(resolved)
                .withResolverStyle(ResolverStyle.STRICT);
        return new TextConverter<>(format::format, text -> parse.parse(text, query));
    }

    /**
     * Parses text with a converter, mapping blank text to {@code null} and every
     * parse failure to an empty result.
     *
     * @param converter the converter
     * @param text      the text to parse
     * @param <T>       the value type
     * @return the parse outcome
     */
    public static <T> ParseResult<T> parse(StringConverter<T> converter, String text) {
        if (text == null || text.isBlank()) {
            return ParseResult.empty();
        }
        try {
            T value = converter.fromString(text.trim());
            return value == null ? ParseResult.invalid() : ParseResult.valid(value);
        } catch (RuntimeException ex) {
            return ParseResult.invalid();
        }
    }

    /**
     * Formats a value with a converter, mapping {@code null} values and results to the empty string.
     *
     * @param converter the converter
     * @param value     the value, or {@code null}
     * @param <T>       the value type
     * @return the text, never {@code null}
     */
    public static <T> String format(StringConverter<T> converter, T value) {
        if (value == null) {
            return "";
        }
        String text = converter.toString(value);
        return text == null ? "" : text;
    }

    // ==================== Types ====================

    /**
     * Outcome of parsing field text.
     *
     * @param status the parse status
     * @param value  the parsed value; non-null only when {@code status} is {@code VALID}
     * @param <T>    the value type
     */
    public record ParseResult<T>(Status status, T value) {

        /** Parse status of field text. */
        public enum Status {
            /** The text is blank. */
            EMPTY,
            /** The text parsed to a value. */
            VALID,
            /** The text does not parse. */
            INVALID
        }

        /**
         * Returns the empty outcome.
         *
         * @param <T> the value type
         * @return the outcome for blank text
         */
        public static <T> ParseResult<T> empty() {
            return new ParseResult<>(Status.EMPTY, null);
        }

        /**
         * Returns the invalid outcome.
         *
         * @param <T> the value type
         * @return the outcome for text that does not parse
         */
        public static <T> ParseResult<T> invalid() {
            return new ParseResult<>(Status.INVALID, null);
        }

        /**
         * Returns a valid outcome.
         *
         * @param value the parsed value
         * @param <T>   the value type
         * @return the outcome carrying {@code value}
         */
        public static <T> ParseResult<T> valid(T value) {
            return new ParseResult<>(Status.VALID, value);
        }
    }

    private static final class TextConverter<T> extends StringConverter<T> {

        private final Function<T, String> formatter;
        private final Function<String, T> parser;

        private TextConverter(Function<T, String> formatter, Function<String, T> parser) {
            this.formatter = Objects.requireNonNull(formatter);
            this.parser = Objects.requireNonNull(parser);
        }

        @Override
        public String toString(T value) {
            return value == null ? "" : formatter.apply(value);
        }

        @Override
        public T fromString(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            return parser.apply(text.trim());
        }
    }
}
