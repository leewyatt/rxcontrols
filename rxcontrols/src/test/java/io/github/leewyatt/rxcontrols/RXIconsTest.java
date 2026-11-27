package io.github.leewyatt.rxcontrols;

import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXIcons} and the text-resource packs of
 * {@link RXIconPack#fromResource(String, String, URL)}.
 */
public class RXIconsTest {

    @TempDir
    Path folder;

    // ==================== Text packs ====================

    @Test
    public void textPackReadsIconsOnFirstLookup() throws IOException {
        RXIconPack pack = RXIconPack.fromResource("tpa", "Test Pack A", resource("""
                # a comment line

                square 0 0 24 24 n M8 8h8v8H8z
                ring -1 2 16 12.5 e M1 1h10v10H1z M3 3h6v6H3z
                """));
        assertEquals("tpa", pack.prefix());
        assertEquals("Test Pack A", pack.title());
        assertEquals(List.of("tpa-square", "tpa-ring"), pack.names());

        RXIconData ring = pack.find("tpa-ring");
        assertNotNull(ring);
        assertEquals("tpa-ring", ring.getName());
        assertEquals(-1.0, ring.getMinX());
        assertEquals(2.0, ring.getMinY());
        assertEquals(16.0, ring.getWidth());
        assertEquals(12.5, ring.getHeight());
        assertEquals("M1 1h10v10H1z M3 3h6v6H3z", ring.getPath());
        assertEquals(FillRule.EVEN_ODD, ring.getFillRule());
        assertSame(ring, pack.find("tpa-ring"), "loaded once");
        assertNull(pack.find("tpa-missing"));
        assertNull(pack.find(null));
    }

    @Test
    public void malformedLinesAreSkippedWithAWarning() throws IOException {
        RXIconPack pack = RXIconPack.fromResource("tpb", "Test Pack B", resource("""
                good 0 0 24 24 n M8 8h8v8H8z
                too-short 0 0 24 n
                bad-rule 0 0 24 24 x M0 0h1v1z
                bad-size 0 0 0 24 n M0 0h1v1z
                bad-number 0 0 a 24 n M0 0h1v1z
                """));
        List<String> warnings = captureWarnings(Logger.getLogger(
                "io.github.leewyatt.rxcontrols.internal.icon.TextIconPack"), pack::names);
        assertEquals(List.of("tpb-good"), pack.names());
        assertEquals(1, warnings.size(), "one warning lists every skipped line: " + warnings);
        assertTrue(warnings.get(0).contains("[2, 3, 4, 5]"), warnings.get(0));
    }

    @Test
    public void prefixesAreChecked() throws IOException {
        URL url = resource("a 0 0 1 1 n M0 0h1v1z");
        assertThrows(IllegalArgumentException.class, () -> RXIconPack.fromResource("", "t", url));
        assertThrows(IllegalArgumentException.class, () -> RXIconPack.fromResource("Mi", "t", url));
        assertThrows(IllegalArgumentException.class, () -> RXIconPack.fromResource("m-i", "t", url));
        assertThrows(NullPointerException.class, () -> RXIconPack.fromResource("mi", null, url));
        assertThrows(NullPointerException.class, () -> RXIconPack.fromResource("mi", "t", null));
    }

    // ==================== Registry ====================

    @Test
    public void namesAreFoundInThePackOfTheirPrefix() throws IOException {
        RXIconPack pack = RXIconPack.fromResource("tpc", "Test Pack C", resource("dot 0 0 24 24 n M8 8h8v8H8z"));
        RXIcons.register(pack);
        assertSame(pack.find("tpc-dot"), RXIcons.find("tpc-dot"));
        assertNull(RXIcons.find("tpc-missing"));
        assertNull(RXIcons.find("nopack-dot"));
        assertNull(RXIcons.find("noprefix"));
        assertNull(RXIcons.find(null));
        assertTrue(RXIcons.getPacks().contains(pack));
    }

    @Test
    public void aPackReplacesOneWithTheSamePrefix() throws IOException {
        RXIcons.register(RXIconPack.fromResource("tpd", "Old", resource("dot 0 0 24 24 n M0 0h1v1z")));
        RXIconPack replacement = RXIconPack.fromResource("tpd", "New", resource("dot 0 0 16 16 n M0 0h2v2z"));
        RXIcons.register(replacement);
        assertEquals(16.0, RXIcons.find("tpd-dot").getWidth());
        assertEquals(1, RXIcons.getPacks().stream().filter(pack -> pack.prefix().equals("tpd")).count());
    }

    @Test
    public void singleIconsTakePrecedenceOverPackIcons() throws IOException {
        RXIcons.register(RXIconPack.fromResource("tpe", "Test Pack E", resource("dot 0 0 24 24 n M0 0h1v1z")));
        RXIconData custom = new RXIconData("tpe-dot", 10, 10, "M0 0h10v10H0z");
        RXIcons.register(custom);
        assertSame(custom, RXIcons.find("tpe-dot"));

        RXIconData unprefixed = new RXIconData("logo", 10, 10, "M0 0h10v10H0z");
        RXIcons.register(unprefixed);
        assertSame(unprefixed, RXIcons.find("logo"));
    }

    @Test
    public void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> RXIcons.register((RXIconPack) null));
        assertThrows(NullPointerException.class, () -> RXIcons.register((RXIconData) null));
    }

    // ==================== Helpers ====================

    private URL resource(String text) throws IOException {
        Path file = Files.createTempFile(folder, "pack", ".icons");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toUri().toURL();
    }

    private static List<String> captureWarnings(Logger logger, Runnable action) {
        List<String> warnings = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return warnings;
    }
}
