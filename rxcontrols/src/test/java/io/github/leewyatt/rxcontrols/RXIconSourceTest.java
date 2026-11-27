package io.github.leewyatt.rxcontrols;

import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFXThemeBridge;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
 * Tests for {@link RXIcon} with icon data: the synchronous shape, the box from
 * the icon size and view box, CSS names, which source owns the shape, the default
 * color, and FXML.
 */
public class RXIconSourceTest {

    private static final double EPSILON = 1.0e-6;
    private static final RXIconData WIDE = new RXIconData("srctest-wide", 0, 0, 24, 12, "M2 2h20v8H2z",
            FillRule.NON_ZERO);
    private static final RXIconData SQUARE = new RXIconData("srctest-square", 24, 24, "M4 4h16v16H4z");
    private static final String CSS_SHAPE = "M0 0 L10 0 L5 8 Z";

    @TempDir
    static Path folder;

    /**
     * Starts the toolkit and registers the test icons.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void setup() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        // Modena is installed when the first control class loads; these tests may
        // build only regions, so pin it for the -fx-* colors the icon reads.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        RXIcons.register(WIDE);
        RXIcons.register(SQUARE);
    }

    /** Restores the shared-JVM default. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Shape ====================

    @Test
    public void iconDataSetsTheShapeAtOnce() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            assertSame(SQUARE.getShape(), icon.getShape(), "no CSS pass needed");
            icon.setIcon(WIDE);
            assertSame(WIDE.getShape(), icon.getShape());
            assertSame(SQUARE, new RXIcon("srctest-square").getIcon());
        });
    }

    @Test
    public void iconsShareOneShape() throws Exception {
        runOnFx(() -> assertSame(new RXIcon(SQUARE).getShape(), new RXIcon(SQUARE).getShape()));
    }

    @Test
    public void setShapeFailsWhileIconDataOwnsTheShape() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            assertThrows(RuntimeException.class, () -> icon.setShape(new SVGPath()));
            icon.setIcon(null);
            assertNull(icon.getShape());
            SVGPath own = new SVGPath();
            icon.setShape(own);
            assertSame(own, icon.getShape(), "released after clearing");
        });
    }

    @Test
    public void authorShapeRulesDoNotReplaceIconData() throws Exception {
        String sheet = stylesheet(".rx-icon { -fx-shape: \"" + CSS_SHAPE + "\"; }");
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            StackPane root = host(icon, sheet);
            root.applyCss();
            assertSame(SQUARE.getShape(), icon.getShape());
        });
    }

    @Test
    public void clearingIconDataInCodeHandsTheShapeBackToCss() throws Exception {
        String sheet = stylesheet(".css-shaped { -fx-shape: \"" + CSS_SHAPE + "\"; }");
        runOnFx(() -> {
            RXIcon icon = new RXIcon();
            icon.getStyleClass().add("css-shaped");
            StackPane root = host(icon, sheet);
            root.applyCss();
            assertEquals(CSS_SHAPE, ((SVGPath) icon.getShape()).getContent());

            icon.setIcon(SQUARE);
            assertSame(SQUARE.getShape(), icon.getShape());

            icon.setIcon(null);
            Shape restored = icon.getShape();
            assertNotNull(restored, "the CSS shape applies again without waiting for another pass");
            assertEquals(CSS_SHAPE, ((SVGPath) restored).getContent());
        });
    }

    @Test
    public void clearingIconDataInCssHandsTheShapeBackInTheSamePass() throws Exception {
        String sheet = stylesheet(".css-shaped { -fx-shape: \"" + CSS_SHAPE + "\"; }"
                + " .css-shaped:with-data { -rx-icon: \"srctest-square\"; }"
                + " .css-shaped.with-data { -rx-icon: \"srctest-square\"; }");
        PseudoClass withData = PseudoClass.getPseudoClass("with-data");
        runOnFx(() -> {
            RXIcon icon = new RXIcon();
            icon.getStyleClass().add("css-shaped");
            StackPane root = host(icon, sheet);

            // A state change: -rx-icon is dropped inside the CSS pass itself.
            icon.pseudoClassStateChanged(withData, true);
            root.applyCss();
            assertSame(SQUARE.getShape(), icon.getShape());
            icon.pseudoClassStateChanged(withData, false);
            root.applyCss();
            assertNull(icon.getIcon());
            assertEquals(CSS_SHAPE, ((SVGPath) icon.getShape()).getContent());

            // A style class change: -rx-icon is reset before the pass.
            icon.getStyleClass().add("with-data");
            root.applyCss();
            assertSame(SQUARE.getShape(), icon.getShape());
            icon.getStyleClass().remove("with-data");
            root.applyCss();
            assertNull(icon.getIcon());
            assertEquals(CSS_SHAPE, ((SVGPath) icon.getShape()).getContent());
        });
    }

    // ==================== CSS names ====================

    @Test
    public void cssNameIsLookedUpInTheRegistry() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon();
            icon.setStyle("-rx-icon: \"srctest-wide\";");
            StackPane root = host(icon, null);
            root.applyCss();
            assertSame(WIDE, icon.getIcon());
            assertSame(WIDE.getShape(), icon.getShape());
        });
    }

    @Test
    public void unknownNamesLeaveTheIconEmptyAndWarnOnce() throws Exception {
        List<String> warnings = captureWarnings(() -> runOnFxUnchecked(() -> {
            RXIcon first = new RXIcon("srctest-unknown");
            RXIcon second = new RXIcon("srctest-unknown");
            assertNull(first.getIcon());
            assertNull(second.getShape());
            RXIcon styled = new RXIcon();
            styled.setStyle("-rx-icon: \"srctest-unknown-css\";");
            host(styled, null).applyCss();
            assertNull(styled.getIcon());
        }));
        assertEquals(2, warnings.size(), "one warning per unknown name: " + warnings);
        assertTrue(warnings.get(0).contains("srctest-unknown"), warnings.get(0));
    }

    // ==================== Box ====================

    @Test
    public void theBoxFollowsTheIconSizeAndViewBox() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(WIDE);
            host(icon, null).applyCss();
            assertBox(icon, RXIcon.DEFAULT_ICON_SIZE, RXIcon.DEFAULT_ICON_SIZE / 2.0);

            icon.setIconSize(20.0);
            assertBox(icon, 20.0, 10.0);

            icon.setStyle("-fx-padding: 2px;");
            icon.applyCss();
            assertBox(icon, 24.0, 14.0);
        });
    }

    @Test
    public void theBoxIsFixedInsideParents() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            icon.setIconSize(20.0);
            StackPane root = host(icon, null);
            root.applyCss();
            root.layout();
            assertEquals(20.0, icon.getWidth(), EPSILON);
            assertEquals(20.0, icon.getHeight(), EPSILON);
        });
    }

    @Test
    public void invalidIconSizesUseTheDefault() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            icon.setIconSize(-4.0);
            assertBox(icon, RXIcon.DEFAULT_ICON_SIZE, RXIcon.DEFAULT_ICON_SIZE);
            icon.setIconSize(Double.NaN);
            assertBox(icon, RXIcon.DEFAULT_ICON_SIZE, RXIcon.DEFAULT_ICON_SIZE);
            icon.setIconSize(0.0);
            assertBox(icon, 0.0, 0.0);
        });
    }

    @Test
    public void cssIconSizeAcceptsEm() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            icon.setStyle("-rx-icon-size: 2em;");
            StackPane root = host(icon, null);
            root.setStyle("-fx-font-size: 10px;");
            root.applyCss();
            assertBox(icon, 20.0, 20.0);
        });
    }

    @Test
    public void anExplicitPrefSizeWins() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(WIDE);
            icon.setPrefSize(30.0, 30.0);
            assertBox(icon, 30.0, 30.0);
        });
    }

    @Test
    public void withoutIconDataTheBoxComesFromCss() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon();
            icon.setIconSize(40.0);
            icon.setStyle("-fx-pref-width: 12px; -fx-pref-height: 8px;");
            host(icon, null).applyCss();
            assertBox(icon, 12.0, 8.0);
        });
    }

    // ==================== Color ====================

    @Test
    public void theDefaultColorFollowsModenaText() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon(SQUARE);
            Region probe = new Region();
            probe.setStyle("-fx-background-color: -fx-text-base-color;");
            StackPane root = host(icon, null);
            root.getChildren().add(probe);
            root.applyCss();
            assertEquals(fill(probe), fill(icon));
        });
    }

    @Test
    public void theDefaultColorFollowsAtlantaFxUnderTheBridge() throws Exception {
        runOnFx(() -> {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            try {
                RXIcon icon = new RXIcon(SQUARE);
                Region probe = new Region();
                probe.setStyle("-fx-background-color: -color-fg-default;");
                StackPane root = new StackPane(icon, probe);
                RXAtlantaFXThemeBridge.install(new Scene(root, 100, 100));
                root.applyCss();
                assertNotNull(fill(icon));
                assertEquals(fill(probe), fill(icon));
            } finally {
                Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            }
        });
    }

    // ==================== FXML ====================

    @Test
    public void fxmlTakesAnIconName() throws Exception {
        runOnFx(() -> {
            String fxml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<?import " + RXIcon.class.getName() + "?>\n"
                    + "<RXIcon icon=\"srctest-wide\" iconSize=\"20\"/>";
            try {
                RXIcon icon = new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
                assertSame(WIDE, icon.getIcon());
                assertEquals(20.0, icon.getIconSize(), EPSILON);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
    }

    // ==================== Helpers ====================

    private static void assertBox(RXIcon icon, double width, double height) {
        assertEquals(width, icon.prefWidth(-1), EPSILON, "pref width");
        assertEquals(height, icon.prefHeight(-1), EPSILON, "pref height");
        assertEquals(width, icon.minWidth(-1), EPSILON, "min width");
        assertEquals(height, icon.minHeight(-1), EPSILON, "min height");
        assertEquals(width, icon.maxWidth(-1), EPSILON, "max width");
        assertEquals(height, icon.maxHeight(-1), EPSILON, "max height");
    }

    private static Paint fill(Region region) {
        assertNotNull(region.getBackground(), region + " has no background");
        return region.getBackground().getFills().get(0).getFill();
    }

    private static StackPane host(RXIcon icon, String stylesheet) {
        StackPane root = new StackPane(icon);
        Scene scene = new Scene(root, 200, 200);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
        }
        return root;
    }

    private static String stylesheet(String css) throws IOException {
        Path file = Files.createTempFile(folder, "icon", ".css");
        Files.writeString(file, css, StandardCharsets.UTF_8);
        return file.toUri().toString();
    }

    private static List<String> captureWarnings(Runnable action) {
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
        Logger logger = Logger.getLogger(RXIcons.class.getName());
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return warnings;
    }

    private static void runOnFxUnchecked(Runnable action) {
        try {
            runOnFx(action);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
