package io.github.leewyatt.rxcontrols.tools.icons;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;
import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the icon pack generator on a small folder of SVG files: names,
 * constants, the text resource read back by the core library, and failures.
 */
public class IconPackGeneratorTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\"";

    @TempDir
    Path folder;

    @Test
    public void namesAndConstantsFollowTheFileNames() {
        assertEquals("account-circle", IconPackGenerator.localName("account_circle"));
        assertEquals("alarm-fill", IconPackGenerator.localName("alarm-fill"));
        assertEquals("ACCOUNT_CIRCLE", IconPackGenerator.constantName("account-circle"));
        assertEquals("_10K", IconPackGenerator.constantName("10k"));
        assertThrows(IllegalArgumentException.class, () -> IconPackGenerator.localName("bad name"));
    }

    @Test
    public void writesAResourceTheLibraryReadsBackAndAConstantsClass() throws IOException {
        Path svgs = Files.createDirectory(folder.resolve("svg"));
        svg(svgs, "search.svg", "<path d=\"M0 0h24v24H0z\" fill=\"none\"/><path d=\"M9 9h6v6H9z\"/>");
        svg(svgs, "10k.svg", "<circle cx=\"12\" cy=\"12\" r=\"4\"/>");
        svg(svgs, "grid_view.svg", "<path fill-rule=\"evenodd\" d=\"M2 2h20v20H2z\n M6 6h12v12H6z\"/>");
        Files.writeString(svgs.resolve("notes.txt"), "not an icon", StandardCharsets.UTF_8);

        IconPackGenerator.Options options = options(svgs, false);
        IconPackGenerator.Result result = IconPackGenerator.generate(options);
        assertEquals(List.of("10k", "grid-view", "search"), result.icons());
        assertTrue(result.skipped().isEmpty());

        RXIconPack pack = RXIconPack.fromResource("tg", "Test", options.resourceOut().toUri().toURL());
        assertEquals(List.of("tg-10k", "tg-grid-view", "tg-search"), pack.names());
        RXIconData grid = pack.find("tg-grid-view");
        assertEquals("M2 2h20v20H2z M6 6h12v12H6z", grid.getPath(), "line breaks become spaces");
        assertEquals(FillRule.EVEN_ODD, grid.getFillRule());
        assertEquals("M9 9h6v6H9z", pack.find("tg-search").getPath());
        assertEquals(24.0, pack.find("tg-10k").getWidth());

        String java = Files.readString(options.javaFile(), StandardCharsets.UTF_8);
        assertTrue(java.startsWith("package test.icons;"), java);
        assertTrue(java.contains("public final class TestIcons {"), java);
        assertTrue(java.contains("public static final String _10K = \"tg-10k\";"), java);
        assertTrue(java.contains("public static final String GRID_VIEW = \"tg-grid-view\";"), java);
        assertTrue(java.contains("new RXIcon(TestIcons.SEARCH)"), java);
        assertTrue(java.contains("Source: Test &lt;fixtures&gt;."), java);
    }

    @Test
    public void unsupportedIconsFailOrAreSkipped() throws IOException {
        Path svgs = Files.createDirectory(folder.resolve("mixed"));
        svg(svgs, "ok.svg", "<path d=\"M9 9h6v6H9z\"/>");
        svg(svgs, "outline.svg", "<path fill=\"none\" stroke=\"black\" d=\"M0 0L24 24\"/>");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> IconPackGenerator.generate(options(svgs, false)));
        assertTrue(error.getMessage().startsWith("outline.svg: "), error.getMessage());

        IconPackGenerator.Result result = IconPackGenerator.generate(options(svgs, true));
        assertEquals(List.of("ok"), result.icons());
        assertTrue(result.skipped().containsKey("outline.svg"));
        String java = Files.readString(options(svgs, true).javaFile(), StandardCharsets.UTF_8);
        assertTrue(!java.contains("OUTLINE"), "skipped icons get no constant");
    }

    @Test
    public void namesThatCollideAreRejected() throws IOException {
        Path svgs = Files.createDirectory(folder.resolve("collide"));
        svg(svgs, "a_b.svg", "<path d=\"M9 9h6v6H9z\"/>");
        svg(svgs, "a-b.svg", "<path d=\"M9 9h6v6H9z\"/>");
        assertThrows(IllegalArgumentException.class, () -> IconPackGenerator.generate(options(svgs, false)));
    }

    @Test
    public void commandLineOptionsAreParsed() {
        IconPackGenerator.Options options = IconPackGenerator.Options.parse(new String[]{
                "--svg", "in", "--prefix", "mi", "--title", "Material Icons", "--package", "a.b",
                "--class", "C", "--java-out", "src", "--resource-out", "out.txt", "--skip-unsupported",
                "--merge-outlines"});
        assertEquals(Path.of("in"), options.svgDir());
        assertEquals("mi", options.prefix());
        assertTrue(options.skipUnsupported());
        assertTrue(options.mergeOutlines());
        assertEquals(Path.of("src", "a", "b", "C.java"), options.javaFile());
        assertThrows(NullPointerException.class, () -> IconPackGenerator.Options.parse(new String[]{"--svg", "in"}));
        assertThrows(IllegalArgumentException.class, () -> IconPackGenerator.Options.parse(new String[]{"--svg"}));
    }

    private IconPackGenerator.Options options(Path svgs, boolean skip) {
        return new IconPackGenerator.Options(svgs, "tg", "Test", "test.icons", "TestIcons",
                folder.resolve("java"), folder.resolve("out").resolve("test.txt"), "Test <fixtures>", skip, false);
    }

    private static void svg(Path folder, String name, String body) throws IOException {
        Files.writeString(folder.resolve(name), "<svg " + NS + " width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">"
                + body + "</svg>", StandardCharsets.UTF_8);
    }
}
