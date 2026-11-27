package io.github.leewyatt.rxcontrols.internal.color;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ColorMath} and {@link HsbaCursor}: 8-bit round trips,
 * HSB conversion, hex parsing and formatting, display text, contrast checks, and the cursor
 * rules that keep hue and saturation through grays and black.
 */
public class ColorMathTest {

    private static final double EPSILON = 1e-9;

    // ==================== Quantization ====================

    @Test
    public void everyByteSurvivesAQuantizeRoundTrip() {
        for (int v = 0; v <= 255; v++) {
            Color color = Color.rgb(v, 255 - v, v / 2, v / 255.0);
            Color quantized = ColorMath.quantize(color);
            assertEquals(color, quantized, "byte " + v);
            assertEquals(v, ColorMath.toByte(quantized.getRed()));
            assertEquals(v, ColorMath.toByte(quantized.getOpacity()));
        }
    }

    @Test
    public void quantizeIsIdempotentAndSnapsOffGridValues() {
        Color offGrid = Color.color(0.123456, 0.5, 0.501, 0.3333);
        Color once = ColorMath.quantize(offGrid);
        assertEquals(once, ColorMath.quantize(once));
        assertEquals(Color.rgb(31, 128, 128, 85 / 255.0), once);
        assertNull(ColorMath.quantize(null));
    }

    @Test
    public void gridColorsSurviveAnHsbRoundTrip() {
        for (int r = 0; r <= 255; r += 15) {
            for (int g = 0; g <= 255; g += 15) {
                for (int b = 0; b <= 255; b += 15) {
                    Color color = Color.rgb(r, g, b);
                    double[] hsb = ColorMath.toHsb(color);
                    assertEquals(color, ColorMath.quantize(ColorMath.hsb(hsb[0], hsb[1], hsb[2], 1.0)),
                            r + "," + g + "," + b);
                }
            }
        }
    }

    @Test
    public void sameOnGridComparesBytesAndCanIgnoreOpacity() {
        assertTrue(ColorMath.sameOnGrid(Color.color(0.5, 0.5, 0.501), Color.rgb(128, 128, 128), false));
        assertFalse(ColorMath.sameOnGrid(Color.rgb(1, 2, 3, 0.5), Color.rgb(1, 2, 3), false));
        assertTrue(ColorMath.sameOnGrid(Color.rgb(1, 2, 3, 0.5), Color.rgb(1, 2, 3), true));
        assertFalse(ColorMath.sameOnGrid(null, Color.WHITE, false));
        assertFalse(ColorMath.sameOnGrid(Color.WHITE, null, false));
    }

    // ==================== HSB ====================

    @Test
    public void toHsbMatchesTheColorGetters() {
        Color[] colors = {Color.web("#1677ff"), Color.web("#ff0001"), Color.web("#2ddcb4"), Color.GRAY,
                Color.BLACK, Color.WHITE, Color.rgb(12, 200, 99, 0.4)};
        for (Color color : colors) {
            double[] hsb = ColorMath.toHsb(color);
            assertEquals(color.getHue(), hsb[0], EPSILON, color.toString());
            assertEquals(color.getSaturation(), hsb[1], EPSILON, color.toString());
            assertEquals(color.getBrightness(), hsb[2], EPSILON, color.toString());
        }
    }

    @Test
    public void hsbCleansComponentsThatColorHsbWouldReject() {
        Color color = ColorMath.hsb(370.0, 1.0000000000000002, -0.1, Double.NaN);
        assertEquals(Color.hsb(360.0, 1.0, 0.0, 0.0), color);
        assertEquals(0.0, ColorMath.cleanHue(Double.NaN));
        assertEquals(360.0, ColorMath.cleanHue(400.0));
    }

    @Test
    public void aHueJustBelow360StaysRed() {
        double hue = ColorMath.toHsb(Color.web("#ff0001"))[0];
        assertTrue(hue > 359.0 && hue < 360.0);
        Color rebuilt = ColorMath.quantize(ColorMath.hsb(hue, 1.0, 1.0, 1.0));
        assertEquals(Color.web("#ff0001"), rebuilt);
        assertEquals(0, ColorMath.toDegrees(hue));
        assertEquals(0, ColorMath.toDegrees(360.0));
        assertEquals(359, ColorMath.toDegrees(359.4));
    }

    @Test
    public void percentsRound() {
        assertEquals(50, ColorMath.toPercent(128 / 255.0));
        assertEquals(100, ColorMath.toPercent(1.0));
        assertEquals(0, ColorMath.toPercent(Double.NaN));
    }

    // ==================== Hex ====================

    @Test
    public void hexIsUppercaseWithAnOptionalOpacityByte() {
        Color color = Color.web("#1677ff80");
        assertEquals("1677FF", ColorMath.toHex(color, false));
        assertEquals("1677FF80", ColorMath.toHex(color, true));
        assertEquals("000000FF", ColorMath.toHex(Color.BLACK, true));
    }

    @Test
    public void sixAndEightDigitsParse() {
        ColorMath.ParsedHex six = ColorMath.parseHex("1677ff", false);
        assertNotNull(six);
        assertEquals(Color.rgb(0x16, 0x77, 0xff), six.color());
        assertFalse(six.hasOpacity());

        ColorMath.ParsedHex eight = ColorMath.parseHex("1677FF80", false);
        assertNotNull(eight);
        assertEquals(Color.rgb(0x16, 0x77, 0xff, 0x80 / 255.0), eight.color());
        assertTrue(eight.hasOpacity());
    }

    @Test
    public void shorthandParsesOnlyWhenAllowed() {
        assertNull(ColorMath.parseHex("abc", false));
        assertNull(ColorMath.parseHex("1234", false));
        assertEquals(Color.web("#aabbcc"), ColorMath.parseHex("abc", true).color());
        ColorMath.ParsedHex four = ColorMath.parseHex("1234", true);
        assertEquals(Color.web("#11223344"), four.color());
        assertTrue(four.hasOpacity());
    }

    @Test
    public void otherLengthsDoNotParse() {
        for (String digits : new String[] {"", "1", "12", "12345", "1234567", "123456789"}) {
            assertNull(ColorMath.parseHex(digits, true), digits);
        }
        assertNull(ColorMath.parseHex(null, true));
    }

    @Test
    public void hexDigitsStripsEverythingElse() {
        assertEquals("1677ff", ColorMath.hexDigits(" #1677ff\n"));
        assertEquals("ABC", ColorMath.hexDigits("0xABC".substring(2)));
        assertEquals("", ColorMath.hexDigits("٣٤"));
        assertEquals("", ColorMath.hexDigits(null));
    }

    // ==================== Text ====================

    @Test
    public void textUsesEachFormatAndHexAndRgbParseBack() {
        Color opaque = Color.rgb(22, 119, 255);
        Color translucent = Color.rgb(22, 119, 255, 128 / 255.0);
        assertEquals("#1677FF", ColorMath.toText(opaque, RXColorFormat.HEX));
        assertEquals("#1677FF80", ColorMath.toText(translucent, RXColorFormat.HEX));
        assertEquals("#1677FF80", ColorMath.toText(translucent, null));
        assertEquals("rgb(22, 119, 255)", ColorMath.toText(opaque, RXColorFormat.RGB));
        assertEquals("rgba(22, 119, 255, 0.5)", ColorMath.toText(translucent, RXColorFormat.RGB));
        assertEquals("hsb(215, 91%, 100%)", ColorMath.toText(opaque, RXColorFormat.HSB));
        assertEquals("hsba(215, 91%, 100%, 0.5)", ColorMath.toText(translucent, RXColorFormat.HSB));
        for (Color color : new Color[]{opaque, translucent}) {
            assertEquals(color, Color.web(ColorMath.toText(color, RXColorFormat.HEX)));
            assertTrue(ColorMath.sameOnGrid(color, Color.web(ColorMath.toText(color, RXColorFormat.RGB)), false));
        }
    }

    // ==================== Contrast ====================

    @Test
    public void brightnessUsesBt601Luma() {
        assertTrue(ColorMath.isBright(Color.WHITE));
        assertTrue(ColorMath.isBright(Color.YELLOW));
        assertFalse(ColorMath.isBright(Color.BLACK));
        assertFalse(ColorMath.isBright(Color.web("#1677ff")));
        assertTrue(ColorMath.isTranslucent(Color.rgb(0, 0, 0, 0.5)));
        assertFalse(ColorMath.isTranslucent(Color.rgb(0, 0, 0, 0.51)));
    }

    // ==================== Cursor ====================

    @Test
    public void theCursorSeedIsOpaqueRed() {
        HsbaCursor cursor = new HsbaCursor();
        assertEquals(Color.RED, cursor.toColor());
    }

    @Test
    public void absorbingAGrayKeepsTheHue() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.absorb(Color.web("#1677ff"));
        double hue = cursor.getHue();
        cursor.absorb(Color.gray(0.6));
        assertEquals(hue, cursor.getHue(), EPSILON);
        assertEquals(0.0, cursor.getSaturation());
        assertEquals(0.6, cursor.getBrightness(), 1e-6);
    }

    @Test
    public void absorbingBlackKeepsHueAndSaturation() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.absorb(Color.web("#1677ff"));
        double hue = cursor.getHue();
        double saturation = cursor.getSaturation();
        cursor.absorb(Color.BLACK);
        assertEquals(hue, cursor.getHue(), EPSILON);
        assertEquals(saturation, cursor.getSaturation(), EPSILON);
        assertEquals(0.0, cursor.getBrightness());

        cursor.changeBrightness(1.0);
        assertEquals(Color.web("#1677ff"), cursor.toColor());
    }

    @Test
    public void absorbingWhiteFromTheSeedLeavesAHueThatDoesNotShow() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.absorb(Color.WHITE);
        assertEquals(0.0, cursor.getSaturation());
        assertTrue(cursor.matches(Color.WHITE));
        cursor.changeHue(200.0);
        assertTrue(cursor.matches(Color.WHITE));
    }

    @Test
    public void startingOnBlackBrightensToGrayNotTheSeedRed() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.startAt(Color.BLACK);
        cursor.changeBrightness(0.2);
        assertEquals(Color.rgb(51, 51, 51), cursor.toColor());

        HsbaCursor empty = new HsbaCursor();
        empty.startAt(null);
        assertEquals(Color.RED, empty.toColor());
    }

    @Test
    public void absorbAlwaysTakesTheOpacityAndIgnoresNull() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.absorb(Color.rgb(0, 0, 0, 0.25));
        assertEquals(0.25, cursor.getOpacity(), 1e-6);
        cursor.absorb(null);
        assertEquals(0.25, cursor.getOpacity(), 1e-6);
    }

    @Test
    public void anAbsorbedGridColorIsMatchedByTheCursor() {
        HsbaCursor cursor = new HsbaCursor();
        for (Color color : new Color[] {Color.web("#2ddcb4"), Color.web("#ff0001"), Color.rgb(3, 4, 5, 0xb3 / 255.0)}) {
            cursor.absorb(color);
            assertTrue(cursor.matches(color), color.toString());
        }
        assertFalse(cursor.matches(null));
    }

    @Test
    public void anOffGridValueIsNotMatchedButReabsorbingItIsStable() {
        HsbaCursor cursor = new HsbaCursor();
        Color offGrid = Color.rgb(3, 4, 5, 0.7);
        cursor.absorb(offGrid);
        assertFalse(cursor.matches(offGrid));
        double hue = cursor.getHue();
        double opacity = cursor.getOpacity();
        cursor.absorb(offGrid);
        assertEquals(hue, cursor.getHue(), EPSILON);
        assertEquals(opacity, cursor.getOpacity(), EPSILON);
    }

    @Test
    public void steppingSaturationUpAndBackReturnsToTheSameColor() {
        HsbaCursor cursor = new HsbaCursor();
        Color start = Color.web("#5b8ab0");
        cursor.absorb(start);
        for (int i = 0; i < 40; i++) {
            cursor.changeSaturation(cursor.getSaturation() + 0.01);
        }
        for (int i = 0; i < 40; i++) {
            cursor.changeSaturation(cursor.getSaturation() - 0.01);
        }
        assertEquals(start, cursor.toColor());
    }

    @Test
    public void componentsAreClampedWhenChanged() {
        HsbaCursor cursor = new HsbaCursor();
        cursor.changeHue(-5.0);
        cursor.changeSaturation(1.5);
        cursor.changeBrightness(Double.NaN);
        cursor.changeOpacity(2.0);
        assertEquals(0.0, cursor.getHue());
        assertEquals(1.0, cursor.getSaturation());
        assertEquals(0.0, cursor.getBrightness());
        assertEquals(1.0, cursor.getOpacity());
    }
}
