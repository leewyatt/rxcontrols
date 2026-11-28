package io.github.leewyatt.rxcontrols.internal.color;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Color conversions shared by the color view and picker: HSB, 8-bit
 * quantization, hex text and the contrast checks used for check marks.
 *
 * <p>Hex text is parsed here rather than with {@link Color#web(String)}:
 * {@code Color.web} rejects surrounding whitespace and reads {@code hsl()} as
 * HSB, and its {@code toString()} format is not specified.
 */
public final class ColorMath {

    // ==================== Constants ====================

    /** The largest hue, in degrees; it shows the same color as {@code 0}. */
    public static final double MAX_HUE = 360.0;

    private static final int MAX_BYTE = 255;
    private static final int MAX_PERCENT = 100;
    /** Luma above which a check mark drawn on the color must be dark. */
    private static final double BRIGHT_LUMA = 192.0;
    /** Opacity at or below which the color counts as translucent. */
    private static final double TRANSLUCENT_OPACITY = 0.5;

    private ColorMath() {
    }

    // ==================== Cleaning ====================

    /**
     * Clamps a unit component to {@code [0, 1]}; {@code NaN} becomes {@code 0}.
     *
     * @param value the component
     * @return the clean component
     */
    public static double clean01(double value) {
        return RXMath.clamp0To1(value);
    }

    /**
     * Clamps a hue to {@code [0, 360]}; {@code NaN} becomes {@code 0}. The hue is
     * clamped, not wrapped, so a slider dragged to its end keeps {@code 360}.
     *
     * @param hue the hue in degrees
     * @return the clean hue
     */
    public static double cleanHue(double hue) {
        if (Double.isNaN(hue) || hue < 0.0) {
            return 0.0;
        }
        return Math.min(hue, MAX_HUE);
    }

    // ==================== Quantization ====================

    /**
     * Returns a unit component as an 8-bit value, rounded.
     *
     * @param value the component in {@code [0, 1]}
     * @return the byte value in {@code [0, 255]}
     */
    public static int toByte(double value) {
        return (int) Math.round(clean01(value) * MAX_BYTE);
    }

    /**
     * Returns a unit component as a whole percentage, rounded.
     *
     * @param value the component in {@code [0, 1]}
     * @return the percentage in {@code [0, 100]}
     */
    public static int toPercent(double value) {
        return (int) Math.round(clean01(value) * MAX_PERCENT);
    }

    /**
     * Returns a hue as whole degrees for display: rounded, with {@code 360}
     * shown as {@code 0}.
     *
     * @param hue the hue in degrees
     * @return the degrees in {@code [0, 359]}
     */
    public static int toDegrees(double hue) {
        return (int) Math.round(cleanHue(hue)) % (int) MAX_HUE;
    }

    /**
     * Snaps a color to the 8-bit RGBA grid, opacity included.
     *
     * @param color the color, or {@code null}
     * @return the snapped color, or {@code null}
     */
    public static Color quantize(Color color) {
        if (color == null) {
            return null;
        }
        return Color.rgb(toByte(color.getRed()), toByte(color.getGreen()), toByte(color.getBlue()),
                toByte(color.getOpacity()) / (double) MAX_BYTE);
    }

    /**
     * Returns whether two non-null colors are the same on the 8-bit RGBA grid.
     * When {@code ignoreOpacity} is set, only the RGB bytes are compared.
     *
     * @param first         the first color, or {@code null}
     * @param second        the second color, or {@code null}
     * @param ignoreOpacity whether to compare RGB only
     * @return {@code false} if either color is {@code null}
     */
    public static boolean sameOnGrid(Color first, Color second, boolean ignoreOpacity) {
        if (first == null || second == null) {
            return false;
        }
        return toByte(first.getRed()) == toByte(second.getRed())
                && toByte(first.getGreen()) == toByte(second.getGreen())
                && toByte(first.getBlue()) == toByte(second.getBlue())
                && (ignoreOpacity || toByte(first.getOpacity()) == toByte(second.getOpacity()));
    }

    // ==================== HSB ====================

    /**
     * Builds a color from HSB components after cleaning them, so values a
     * keyboard step pushed past a bound never reach {@link Color#hsb}, which
     * rejects them. The result is not quantized.
     *
     * @param hue        the hue in degrees
     * @param saturation the saturation in {@code [0, 1]}
     * @param brightness the brightness in {@code [0, 1]}
     * @param opacity    the opacity in {@code [0, 1]}
     * @return the color
     */
    public static Color hsb(double hue, double saturation, double brightness, double opacity) {
        return Color.hsb(cleanHue(hue), clean01(saturation), clean01(brightness), clean01(opacity));
    }

    /**
     * Returns hue (degrees, {@code [0, 360)}), saturation and brightness of a
     * color. A gray has hue {@code 0}; black also has saturation {@code 0}.
     *
     * @param color the color
     * @return {@code {hue, saturation, brightness}}
     */
    public static double[] toHsb(Color color) {
        double r = color.getRed();
        double g = color.getGreen();
        double b = color.getBlue();
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double saturation = max == 0.0 ? 0.0 : (max - min) / max;
        double hue = 0.0;
        if (saturation != 0.0) {
            double delta = max - min;
            double redShare = (max - r) / delta;
            double greenShare = (max - g) / delta;
            double blueShare = (max - b) / delta;
            if (r == max) {
                hue = blueShare - greenShare;
            } else if (g == max) {
                hue = 2.0 + redShare - blueShare;
            } else {
                hue = 4.0 + greenShare - redShare;
            }
            hue /= 6.0;
            if (hue < 0.0) {
                hue += 1.0;
            }
        }
        return new double[] {hue * MAX_HUE, saturation, max};
    }

    // ==================== Hex ====================

    /**
     * Formats the RGB bytes of a color as six uppercase hex digits, without
     * {@code #}, followed by the opacity byte when {@code withOpacity} is set.
     *
     * @param color       the color
     * @param withOpacity whether to append the opacity byte
     * @return the hex digits
     */
    public static String toHex(Color color, boolean withOpacity) {
        String rgb = String.format(Locale.ROOT, "%02X%02X%02X",
                toByte(color.getRed()), toByte(color.getGreen()), toByte(color.getBlue()));
        if (!withOpacity) {
            return rgb;
        }
        return rgb + String.format(Locale.ROOT, "%02X", toByte(color.getOpacity()));
    }

    /**
     * Removes every character that is not a hex digit, so pasted text such as
     * {@code " #1677ff\n"} is kept as its digits rather than rejected.
     *
     * @param text the text, or {@code null}
     * @return the hex digits, possibly empty
     */
    public static String hexDigits(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.digit(c, 16) >= 0 && c < 128) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    /**
     * Parses hex digits as a color. Six or eight digits are always accepted;
     * three or four digit shorthand only when {@code allowShorthand} is set (the
     * field accepts it on commit, not while typing a longer value). The last
     * pair (or digit) of an eight (or four) digit value is the opacity.
     *
     * @param digits         the digits, without {@code #} or other characters
     * @param allowShorthand whether three and four digits are accepted
     * @return the parsed value, or {@code null} if the digits are not a color
     */
    public static ParsedHex parseHex(String digits, boolean allowShorthand) {
        if (digits == null) {
            return null;
        }
        int length = digits.length();
        boolean shorthand = length == 3 || length == 4;
        if (!(length == 6 || length == 8 || (allowShorthand && shorthand))) {
            return null;
        }
        int step = shorthand ? 1 : 2;
        int[] parts = new int[length / step];
        for (int i = 0; i < parts.length; i++) {
            int part = Integer.parseInt(digits.substring(i * step, i * step + step), 16);
            parts[i] = shorthand ? part * 17 : part;
        }
        boolean hasOpacity = parts.length == 4;
        double opacity = hasOpacity ? parts[3] / (double) MAX_BYTE : 1.0;
        return new ParsedHex(Color.rgb(parts[0], parts[1], parts[2], opacity), hasOpacity);
    }

    /**
     * A color parsed from hex digits.
     *
     * @param color      the color; opaque when the digits carry no opacity
     * @param hasOpacity whether the digits carried an opacity
     */
    public record ParsedHex(Color color, boolean hasOpacity) {
    }

    // ==================== Text ====================

    /**
     * Formats a color for display in the given format: {@code #1677FF} /
     * {@code #1677FF80} (the opacity byte appended when translucent),
     * {@code rgb(22, 119, 255)} / {@code rgba(22, 119, 255, 0.5)}, or
     * {@code hsb(215, 91%, 100%)} / {@code hsba(215, 91%, 100%, 0.5)}. The opacity
     * in the function forms has two decimals, or three where two would read as
     * fully opaque or fully clear ({@code 0.996}, {@code 0.004}). The hex and RGB
     * forms are accepted by {@link Color#web(String)}; the HSB forms are for
     * display only.
     *
     * @param color  the color
     * @param format the format; {@code null} means hex
     * @return the text
     */
    public static String toText(Color color, RXColorFormat format) {
        int opacityByte = toByte(color.getOpacity());
        boolean opaque = opacityByte == MAX_BYTE;
        BigDecimal exact = BigDecimal.valueOf(color.getOpacity());
        BigDecimal rounded = exact.setScale(2, RoundingMode.HALF_UP);
        // Bytes 254 and 1 round to 1 and 0: the text would claim an opaque or clear
        // color, and parse back as one. Three decimals keep their byte.
        if ((!opaque && rounded.compareTo(BigDecimal.ONE) == 0) || (opacityByte > 0 && rounded.signum() == 0)) {
            rounded = exact.setScale(3, RoundingMode.HALF_UP);
        }
        String opacity = rounded.stripTrailingZeros().toPlainString();
        if (format == RXColorFormat.RGB) {
            String rgb = toByte(color.getRed()) + ", " + toByte(color.getGreen()) + ", " + toByte(color.getBlue());
            return opaque ? "rgb(" + rgb + ")" : "rgba(" + rgb + ", " + opacity + ")";
        }
        if (format == RXColorFormat.HSB) {
            double[] hsb = toHsb(color);
            String text = toDegrees(hsb[0]) + ", " + toPercent(hsb[1]) + "%, " + toPercent(hsb[2]) + "%";
            return opaque ? "hsb(" + text + ")" : "hsba(" + text + ", " + opacity + ")";
        }
        return "#" + toHex(color, !opaque);
    }

    // ==================== Contrast ====================

    /**
     * Returns whether a mark drawn on the color must be dark: the BT.601 luma of
     * its RGB bytes is above 192. Opacity is ignored; see {@link #isTranslucent}.
     *
     * @param color the color
     * @return {@code true} for a bright color
     */
    public static boolean isBright(Color color) {
        double luma = toByte(color.getRed()) * 0.299 + toByte(color.getGreen()) * 0.587
                + toByte(color.getBlue()) * 0.114;
        return luma > BRIGHT_LUMA;
    }

    /**
     * Returns whether the color is mostly transparent, so what shows through it
     * decides the contrast rather than the color itself.
     *
     * @param color the color
     * @return {@code true} when the opacity is at most one half
     */
    public static boolean isTranslucent(Color color) {
        return color.getOpacity() <= TRANSLUCENT_OPACITY;
    }
}
