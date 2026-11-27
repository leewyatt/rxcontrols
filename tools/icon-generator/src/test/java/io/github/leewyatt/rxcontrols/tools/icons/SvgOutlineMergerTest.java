package io.github.leewyatt.rxcontrols.tools.icons;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXSvgIcons;
import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the SVG path parser and the outline merger used for icons that mix fill
 * rules or use transforms.
 */
public class SvgOutlineMergerTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    @TempDir
    Path folder;

    // ==================== Path data ====================

    @Test
    public void absoluteAndRelativeCommandsAreParsed() {
        Path2D path = SvgPathParser.parse("M1 1h4v4H1z m6 0 l4 0 0 4 -4 0z");
        assertTrue(path.contains(3, 3));
        assertTrue(path.contains(9, 3));
        assertFalse(path.contains(6, 3));
        assertEquals(new Rectangle2D.Double(1, 1, 10, 4), path.getBounds2D());
    }

    @Test
    public void arcsBecomeCurvesOnTheCircle() {
        // A circle of radius 5 drawn as two half arcs with packed flags.
        Path2D circle = SvgPathParser.parse("M0 5a5 5 0 1 0 10 0a5 5 0 1 0-10 0z");
        Rectangle2D bounds = circle.getBounds2D();
        assertEquals(0.0, bounds.getMinX(), 1.0e-3);
        assertEquals(10.0, bounds.getMaxX(), 1.0e-3);
        assertEquals(0.0, bounds.getMinY(), 0.02);
        assertEquals(10.0, bounds.getMaxY(), 0.02);
        assertTrue(circle.contains(5, 5));
        assertFalse(circle.contains(0.6, 0.6), "a corner of the square around the circle");

        Path2D packed = SvgPathParser.parse("M0 0a1 1 0 011 1");
        assertEquals(1.0, packed.getCurrentPoint().getX(), 1.0e-9);
        assertEquals(1.0, packed.getCurrentPoint().getY(), 1.0e-9);
    }

    @Test
    public void smoothCurvesReflectTheLastControlPoint() {
        Path2D path = SvgPathParser.parse("M0 0C0 4 4 4 4 0S8-4 8 0Q10 4 12 0T16 0z");
        assertEquals(16.0, path.getBounds2D().getMaxX(), 1.0e-9);
        assertThrows(IllegalArgumentException.class, () -> SvgPathParser.parse("1 1"));
        assertThrows(IllegalArgumentException.class, () -> SvgPathParser.parse("M0 0a1 1 0 2 1 1 1"));
    }

    // ==================== Merging ====================

    @Test
    public void mixedFillRulesAreMergedIntoOneNonZeroOutline() throws IOException {
        // A ring filled even-odd beside a square filled non-zero.
        Path file = svg("mixed.svg", "<path fill-rule=\"evenodd\" d=\"M1 1h10v10H1z M4 4h4v4H4z\"/>"
                + "<path d=\"M13 1h4v4h-4z\"/>");
        assertThrows(IllegalArgumentException.class, () -> RXSvgIcons.read("t", file), "the library rejects it");

        RXIconData data = SvgOutlineMerger.merge("t-mixed", file);
        assertEquals(FillRule.NON_ZERO, data.getFillRule());
        assertEquals(24.0, data.getWidth());
        Path2D outline = SvgPathParser.parse(data.getPath());
        assertTrue(outline.contains(2, 2), "the ring");
        assertFalse(outline.contains(6, 6), "the hole of the ring");
        assertTrue(outline.contains(15, 3), "the square");
    }

    @Test
    public void transformsAreApplied() throws IOException {
        Path file = svg("flipped.svg", "<rect width=\"4\" height=\"12\" transform=\"matrix(1 0 0 -1 6 15)\"/>");
        RXIconData data = SvgOutlineMerger.merge("t-flipped", file);
        Rectangle2D bounds = SvgPathParser.parse(data.getPath()).getBounds2D();
        assertEquals(new Rectangle2D.Double(6, 3, 4, 12), bounds);
    }

    @Test
    public void overlappingElementsAreDetectedAndMerged() throws IOException {
        // The same even-odd square twice: joined into one path, the copies cancel out.
        Path file = svg("twice.svg", "<path fill-rule=\"evenodd\" d=\"M4 4h16v16H4z\"/>"
                + "<path fill-rule=\"evenodd\" d=\"M4 4h16v16H4z\"/>");
        RXIconData joined = RXSvgIcons.read("t", file);
        assertFalse(SvgOutlineMerger.drawsLikeTheFile(joined, file));
        RXIconData merged = SvgOutlineMerger.merge("t", file);
        assertTrue(SvgOutlineMerger.drawsLikeTheFile(merged, file));
        assertTrue(SvgPathParser.parse(merged.getPath()).contains(12, 12));

        Path apart = svg("apart.svg", "<path d=\"M1 1h4v4H1z\"/><path d=\"M10 10h4v4h-4z\"/>");
        assertTrue(SvgOutlineMerger.drawsLikeTheFile(RXSvgIcons.read("t", apart), apart));
    }

    @Test
    public void partialOpacityIsStillRejected() throws IOException {
        Path file = svg("two-tone.svg", "<path fill-opacity=\".4\" d=\"M1 1h4v4H1z\"/><path d=\"M6 6h4v4H6z\"/>");
        assertThrows(IllegalArgumentException.class, () -> SvgOutlineMerger.merge("t", file));
    }

    @Test
    public void theGeneratorMergesOnlyWhenAsked() throws IOException {
        Path svgs = Files.createDirectory(folder.resolve("icons"));
        Files.copy(svg("mixed.svg", "<path fill-rule=\"evenodd\" d=\"M1 1h10v10H1z M4 4h4v4H4z\"/>"
                + "<path d=\"M13 1h4v4h-4z\"/>"), svgs.resolve("mixed.svg"));
        IconPackGenerator.Options strict = new IconPackGenerator.Options(svgs, "tm", "T", "t.p", "T",
                folder.resolve("java"), folder.resolve("t.txt"), null, false, false);
        assertThrows(IllegalArgumentException.class, () -> IconPackGenerator.generate(strict));

        IconPackGenerator.Options merging = new IconPackGenerator.Options(svgs, "tm", "T", "t.p", "T",
                folder.resolve("java"), folder.resolve("t.txt"), null, false, true);
        IconPackGenerator.Result result = IconPackGenerator.generate(merging);
        assertEquals(List.of("mixed"), result.merged());
        assertTrue(Files.readString(folder.resolve("t.txt"), StandardCharsets.UTF_8)
                .contains("merged into one outline because they mix fill rules, use transforms or overlap: 1"));
    }

    private Path svg(String name, String body) throws IOException {
        return Files.writeString(folder.resolve(name), "<svg " + NS + " viewBox=\"0 0 24 24\">" + body + "</svg>",
                StandardCharsets.UTF_8);
    }
}
