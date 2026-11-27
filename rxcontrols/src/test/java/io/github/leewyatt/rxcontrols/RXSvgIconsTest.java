package io.github.leewyatt.rxcontrols;

import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSvgIcons}: the view box, joined geometry, basic shapes,
 * fill rules, skipped invisible elements, and rejected markup.
 */
public class RXSvgIconsTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    @TempDir
    Path folder;

    // ==================== View box and paths ====================

    @Test
    public void materialStyleFileKeepsTheVisiblePath() {
        RXIconData data = RXSvgIcons.parse("t-home", "<svg " + NS + " height=\"24\" viewBox=\"0 0 24 24\" width=\"24\">"
                + "<path d=\"M0 0h24v24H0z\" fill=\"none\"/><path d=\"M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z\"/></svg>");
        assertEquals("t-home", data.getName());
        assertEquals(0.0, data.getMinX());
        assertEquals(0.0, data.getMinY());
        assertEquals(24.0, data.getWidth());
        assertEquals(24.0, data.getHeight());
        assertEquals("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z", data.getPath());
        assertEquals(FillRule.NON_ZERO, data.getFillRule());
    }

    @Test
    public void widthAndHeightStandInForAMissingViewBox() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " width=\"16px\" height=\"12\"><path d=\"M0 0h1v1z\"/></svg>");
        assertEquals(16.0, data.getWidth());
        assertEquals(12.0, data.getHeight());
    }

    @Test
    public void viewBoxMayUseCommasAndAnOffset() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"-2,-1, 20,10\"><path d=\"M0 0h1v1z\"/></svg>");
        assertEquals(-2.0, data.getMinX());
        assertEquals(-1.0, data.getMinY());
        assertEquals(20.0, data.getWidth());
        assertEquals(10.0, data.getHeight());
    }

    @Test
    public void groupsAndShapesAreJoinedIntoOnePath() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 24 24\" fill=\"currentColor\">"
                + "<title>t</title><desc>d</desc><defs><linearGradient id=\"g\"/></defs>"
                + "<g><rect x=\"1\" y=\"2\" width=\"3\" height=\"4\"/>"
                + "<circle cx=\"12\" cy=\"12\" r=\"2\"/></g>"
                + "<ellipse cx=\"5\" cy=\"6\" rx=\"2\" ry=\"1\"/>"
                + "<polygon points=\"0,0 4,0 2,3\"/><polyline points=\"1 1 2 2 3 1\"/>"
                + "<line x1=\"0\" y1=\"0\" x2=\"5\" y2=\"5\"/>"
                + "<path d=\"M20 20h1v1z\"/></svg>");
        assertEquals("M1 2H4V6H1Z"
                + " M10 12A2 2 0 1 0 14 12A2 2 0 1 0 10 12Z"
                + " M3 6A2 1 0 1 0 7 6A2 1 0 1 0 3 6Z"
                + " M0 0L4 0L2 3Z"
                + " M1 1L2 2L3 1"
                + " M20 20h1v1z", data.getPath());
    }

    @Test
    public void roundedRectanglesUseArcs() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 24 24\">"
                + "<rect x=\"0\" y=\"0\" width=\"10\" height=\"4\" rx=\"3\"/></svg>");
        // ry follows rx; each is limited to half its side.
        assertEquals("M3 0H7A3 2 0 0 1 10 2V2A3 2 0 0 1 7 4H3A3 2 0 0 1 0 2V2A3 2 0 0 1 3 0Z", data.getPath());
    }

    @Test
    public void styleAttributesAreRead() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 24 24\">"
                + "<path style=\"fill: none\" d=\"M0 0h24v24H0z\"/>"
                + "<path style=\"fill-rule:evenodd\" d=\"M2 2h20v20H2z M6 6h12v12H6z\"/></svg>");
        assertEquals("M2 2h20v20H2z M6 6h12v12H6z", data.getPath());
        assertEquals(FillRule.EVEN_ODD, data.getFillRule());
    }

    @Test
    public void hiddenElementsAreSkipped() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 24 24\">"
                + "<g display=\"none\"><path d=\"M0 0h1v1z\"/></g>"
                + "<path visibility=\"hidden\" d=\"M2 2h1v1z\"/>"
                + "<path d=\"M4 4h1v1z\"/></svg>");
        assertEquals("M4 4h1v1z", data.getPath());
    }

    @Test
    public void aRelativeMovetoStartsAtTheOriginOfItsElement() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 24 24\">"
                + "<path d=\"m2 2h4v4H2z\"/><path d=\"m10 10h4v4h-4z\"/></svg>");
        assertEquals("m2 2h4v4H2z M0 0m10 10h4v4h-4z", data.getPath());
    }

    @Test
    public void evenOddIsInheritedFromAGroup() {
        RXIconData data = RXSvgIcons.parse("t", "<svg " + NS + " viewBox=\"0 0 16 16\">"
                + "<g fill-rule=\"evenodd\"><path d=\"M0 0h16v16H0z M4 4h8v8H4z\"/></g></svg>");
        assertEquals(FillRule.EVEN_ODD, data.getFillRule());
    }

    // ==================== Rejected markup ====================

    @Test
    public void markupOneFilledPathCannotShowIsRejected() {
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\">"
                + "<path d=\"M0 0L24 24\"/></svg>", "Strokes");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path transform=\"rotate(45)\" d=\"M0 0h1v1z\"/></svg>",
                "transform");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><g clip-path=\"url(#c)\"><path d=\"M0 0h1v1z\"/></g></svg>",
                "clip-path");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path fill=\"url(#g)\" d=\"M0 0h1v1z\"/></svg>",
                "Gradient");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path opacity=\".3\" d=\"M0 0h1v1z\"/></svg>",
                "opacity");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><style>.a{fill:red}</style><path d=\"M0 0h1v1z\"/></svg>",
                "<style>");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><text>A</text></svg>", "<text>");
        assertRejected("<svg " + NS + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 24 24\">"
                + "<use xlink:href=\"#a\"/></svg>", "xlink:href");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path d=\"M0 0h1v1z\"/>"
                + "<path fill-rule=\"evenodd\" d=\"M2 2h1v1z\"/></svg>", "mixes");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path d=\"M0 0h24v24H0z\" fill=\"none\"/></svg>",
                "no filled shape");
        assertRejected("<svg " + NS + "><path d=\"M0 0h1v1z\"/></svg>", "neither a viewBox");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24\"><path d=\"M0 0h1v1z\"/></svg>", "viewBox");
        assertRejected("<html><body/></html>", "<html>");
        assertRejected("<svg " + NS + " viewBox=\"0 0 24 24\"><path d=", "Not valid SVG");
    }

    @Test
    public void externalEntitiesAreNotProcessed() throws IOException {
        Path secret = Files.writeString(folder.resolve("secret.txt"), "M0 0h24v24H0z", StandardCharsets.UTF_8);
        String svg = "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                + "<svg " + NS + " viewBox=\"0 0 24 24\"><path d=\"&xxe;\"/></svg>";
        assertThrows(IllegalArgumentException.class, () -> RXSvgIcons.parse("t", svg));
    }

    // ==================== Files ====================

    @Test
    public void filesAreReadAndMissingFilesFailWithAnIoError() throws IOException {
        Path file = Files.writeString(folder.resolve("dot.svg"),
                "<svg " + NS + " viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"4\"/></svg>",
                StandardCharsets.UTF_8);
        assertEquals("M8 12A4 4 0 1 0 16 12A4 4 0 1 0 8 12Z", RXSvgIcons.read("t-dot", file).getPath());
        assertEquals("t-dot", RXSvgIcons.read("t-dot", file.toUri().toURL()).getName());
        assertThrows(NoSuchFileException.class, () -> RXSvgIcons.read("t", folder.resolve("missing.svg")));
    }

    private static void assertRejected(String svg, String reason) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> RXSvgIcons.parse("t", svg));
        assertTrue(error.getMessage().contains(reason), "expected '" + reason + "' in: " + error.getMessage());
    }
}
