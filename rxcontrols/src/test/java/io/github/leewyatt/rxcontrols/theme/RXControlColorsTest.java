package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-control colors that used literals or light-only formulas: they keep their exact
 * values under default Modena and under AtlantaFX (light and dark), and become legible
 * on a dark application Modena palette. Values are compared at 8 bits per channel.
 */
public class RXControlColorsTest {

    private static final String PALETTES =
            RXControlColorsTest.class.getResource("app-palettes.css").toExternalForm();

    private static final Map<String, String> DEFAULT_MODENA = new LinkedHashMap<>();
    private static final Map<String, String> PRIMER_LIGHT = new LinkedHashMap<>();
    private static final Map<String, String> PRIMER_DARK = new LinkedHashMap<>();

    static {
        DEFAULT_MODENA.put("cascader.prompt", "#b2b2b2ff");
        DEFAULT_MODENA.put("cascader.column.border", "#c4c4c4ff");
        DEFAULT_MODENA.put("cascader.cell.hover", "#ebebebff");
        DEFAULT_MODENA.put("segmented.selected", "#000000e0");
        DEFAULT_MODENA.put("segmented.unselected", "#000000a6");
        DEFAULT_MODENA.put("segmented.indicator", "#ffffffff");
        DEFAULT_MODENA.put("seek.track", "#eaeaeaff");
        DEFAULT_MODENA.put("seek.secondary", "#dadadaff");
        DEFAULT_MODENA.put("slider.track", "#e0e0e0ff");
        DEFAULT_MODENA.put("wave.container", "#e8f4ffff");
        DEFAULT_MODENA.put("avatar.icon-disc", "#e0e0e0ff");
        DEFAULT_MODENA.put("snackbar.bar", "#323232ff");
        DEFAULT_MODENA.put("snackbar.message", "#ffffffde");
        DEFAULT_MODENA.put("snackbar.action", "#8791ffff");
        DEFAULT_MODENA.put("snackbar.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("snackbar.info.bar", "#909399ff");
        DEFAULT_MODENA.put("snackbar.info.message", "#ffffffff");
        DEFAULT_MODENA.put("snackbar.info.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("progress-bar.track", "#cfcfcfff");
        DEFAULT_MODENA.put("step-indicator.track", "#cfcfcfff");
        DEFAULT_MODENA.put("carousel-navigator.track", "#ffffff59");
        DEFAULT_MODENA.put("digit.lit", "#333333ff");
        DEFAULT_MODENA.put("digit.unlit", "#cfcfcfff");
        DEFAULT_MODENA.put("fill-button.filling-armed.text", "#ffffffff");
        DEFAULT_MODENA.put("list-view.background", "#c9c9c9ff #f4f4f4ff");
        DEFAULT_MODENA.put("tile-view.background", "#c9c9c9ff #f4f4f4ff");
        DEFAULT_MODENA.put("masonry-view.background", "#c9c9c9ff #f4f4f4ff");
    }

    static {
        PRIMER_LIGHT.put("cascader.prompt", "#57606aff");
        PRIMER_LIGHT.put("cascader.column.border", "#d0d7deff");
        PRIMER_LIGHT.put("cascader.cell.hover", "#f6f8faff");
        PRIMER_LIGHT.put("segmented.selected", "#000000e0");
        PRIMER_LIGHT.put("segmented.unselected", "#24292fff");
        PRIMER_LIGHT.put("segmented.indicator", "#ffffffff");
        PRIMER_LIGHT.put("seek.track", "#f6f8faff");
        PRIMER_LIGHT.put("seek.secondary", "#d0d7deff");
        PRIMER_LIGHT.put("slider.track", "#d0d7deff");
        PRIMER_LIGHT.put("wave.container", "#f6f8faff");
        PRIMER_LIGHT.put("avatar.icon-disc", "#f6f8faff");
        PRIMER_LIGHT.put("snackbar.bar", "#323232ff");
        PRIMER_LIGHT.put("snackbar.message", "#ffffffde");
        PRIMER_LIGHT.put("snackbar.action", "#3787e2ff");
        PRIMER_LIGHT.put("snackbar.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("snackbar.info.bar", "#6e7781ff");
        PRIMER_LIGHT.put("snackbar.info.message", "#ffffffff");
        PRIMER_LIGHT.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("progress-bar.track", "#d0d7deff");
        PRIMER_LIGHT.put("step-indicator.track", "#d0d7deff");
        PRIMER_LIGHT.put("carousel-navigator.track", "#ffffff59");
        PRIMER_LIGHT.put("digit.lit", "#24292fff");
        PRIMER_LIGHT.put("digit.unlit", "#d0d7deff");
        PRIMER_LIGHT.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_LIGHT.put("list-view.background", "#d0d7deff #ffffffff");
        // Was unresolved before the views read -rx-surface instead of -fx-background.
        PRIMER_LIGHT.put("tile-view.background", "#d0d7deff #ffffffff");
        PRIMER_LIGHT.put("masonry-view.background", "#d0d7deff #ffffffff");
    }

    static {
        PRIMER_DARK.put("cascader.prompt", "#8b949eff");
        PRIMER_DARK.put("cascader.column.border", "#30363dff");
        PRIMER_DARK.put("cascader.cell.hover", "#161b22ff");
        PRIMER_DARK.put("segmented.selected", "#000000e0");
        PRIMER_DARK.put("segmented.unselected", "#c9d1d9ff");
        PRIMER_DARK.put("segmented.indicator", "#ffffffff");
        PRIMER_DARK.put("seek.track", "#161b22ff");
        PRIMER_DARK.put("seek.secondary", "#30363dff");
        PRIMER_DARK.put("slider.track", "#30363dff");
        PRIMER_DARK.put("wave.container", "#161b22ff");
        PRIMER_DARK.put("avatar.icon-disc", "#161b22ff");
        // The snackbar is an inverse surface, so it turns light on a dark AtlantaFX theme.
        PRIMER_DARK.put("snackbar.bar", "#e0e0e0ff");
        PRIMER_DARK.put("snackbar.message", "#000000de");
        PRIMER_DARK.put("snackbar.action", "#1f6febff");
        PRIMER_DARK.put("snackbar.close-icon", "#00000099");
        PRIMER_DARK.put("snackbar.info.bar", "#6e7681ff");
        PRIMER_DARK.put("snackbar.info.message", "#ffffffff");
        PRIMER_DARK.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_DARK.put("progress-bar.track", "#30363dff");
        PRIMER_DARK.put("step-indicator.track", "#30363dff");
        PRIMER_DARK.put("carousel-navigator.track", "#ffffff59");
        PRIMER_DARK.put("digit.lit", "#c9d1d9ff");
        PRIMER_DARK.put("digit.unlit", "#30363dff");
        PRIMER_DARK.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_DARK.put("list-view.background", "#30363dff #0d1117ff");
        // Was unresolved before the views read -rx-surface instead of -fx-background.
        PRIMER_DARK.put("tile-view.background", "#30363dff #0d1117ff");
        PRIMER_DARK.put("masonry-view.background", "#30363dff #0d1117ff");
    }

    private static final double WCAG_AA_CONTRAST = 4.5;
    private static final double PLACEHOLDER_CONTRAST = 3.0;

    /**
     * Starts the toolkit.
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
    }

    /** Restores the shared-JVM default. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Unchanged hosts ====================

    /**
     * Default Modena keeps every sampled control color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void defaultModenaKeepsEveryControlColor() throws Exception {
        assertSamples(DEFAULT_MODENA, sampleUnder(Application.STYLESHEET_MODENA, scene -> { }), "default Modena");
    }

    /**
     * A light AtlantaFX theme keeps every sampled control color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void lightAtlantaFxThemeKeepsEveryControlColor() throws Exception {
        assertSamples(PRIMER_LIGHT, sampleUnder(new PrimerLight().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install), "Primer Light");
    }

    /**
     * A dark AtlantaFX theme keeps every sampled control color except the inverse snackbar.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkAtlantaFxThemeKeepsEveryControlColor() throws Exception {
        assertSamples(PRIMER_DARK, sampleUnder(new PrimerDark().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install), "Primer Dark");
    }

    // ==================== Dark application palette ====================

    /**
     * On a dark application palette each sampled color takes its dark role or value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkPaletteRecolorsEveryControl() throws Exception {
        Map<String, String> samples = sampleUnder(Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        Map<String, String> roles = darkRoles();

        assertEquals(roles.get("on-surface-secondary"), samples.get("cascader.prompt"), "cascader prompt");
        assertEquals(roles.get("outline"), samples.get("cascader.column.border"), "cascader column separator");
        assertEquals(roles.get("on-surface"), samples.get("segmented.unselected"), "unselected segment label");
        assertEquals("#000000e0", samples.get("segmented.selected"), "selected segment label on the white pill");
        assertEquals(roles.get("surface-variant"), samples.get("seek.track"), "seek bar track");
        assertEquals(roles.get("outline"), samples.get("seek.secondary"), "seek bar buffer");
        assertEquals(roles.get("outline"), samples.get("slider.track"), "slider track");
        assertEquals(roles.get("surface-variant"), samples.get("wave.container"), "wave container");
        assertEquals(roles.get("surface-variant"), samples.get("avatar.icon-disc"), "avatar placeholder disc");
        assertEquals("#e0e0e0ff", samples.get("snackbar.bar"), "snackbar turns into a light bar");
        assertEquals("#000000de", samples.get("snackbar.message"), "snackbar message on the light bar");
        assertEquals(roles.get("primary"), samples.get("snackbar.action"), "snackbar action on the light bar");
        assertEquals("#00000099", samples.get("snackbar.close-icon"), "snackbar close icon on the light bar");
        assertEquals(roles.get("info"), samples.get("snackbar.info.bar"), "severity bar keeps its tint");
        assertEquals("#ffffffff", samples.get("snackbar.info.message"), "severity bar keeps white text");
        assertEquals("#ffffffb2", samples.get("snackbar.info.close-icon"), "severity bar keeps a light close icon");
        assertEquals(roles.get("outline"), samples.get("progress-bar.track"), "segmented progress track");
        assertEquals(roles.get("outline"), samples.get("step-indicator.track"), "step indicator track");
        assertEquals("#ffffff59", samples.get("carousel-navigator.track"), "carousel navigator stays translucent white");
        assertEquals(roles.get("on-surface"), samples.get("digit.lit"), "lit digit segment");
        assertEquals(roles.get("outline"), samples.get("digit.unlit"), "unlit digit segment");
        assertEquals("#ffffffff", samples.get("fill-button.filling-armed.text"), "filling fill button text");
        for (String view : List.of("tile-view", "list-view", "masonry-view")) {
            assertTrue(samples.get(view + ".background").endsWith(roles.get("surface")),
                    view + " background ends on -rx-surface: " + samples.get(view + ".background"));
        }
    }

    /**
     * On a dark application palette the cascader prompt and hover stay distinguishable
     * from the field and column background.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkPaletteKeepsTheCascaderLegible() throws Exception {
        Map<String, String> samples = sampleUnder(Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        Color field = Color.web("#2a2c3a");

        double promptRatio = contrastRatio(Color.web(samples.get("cascader.prompt")), field);
        assertTrue(promptRatio >= PLACEHOLDER_CONTRAST, "cascader prompt contrast " + promptRatio);
        assertNotEquals("#2a2c3aff", samples.get("cascader.cell.hover"), "cascader hover must differ from the column");
        double segmentRatio = contrastRatio(Color.web(samples.get("segmented.unselected")), Color.web("#262735"));
        assertTrue(segmentRatio >= WCAG_AA_CONTRAST, "unselected segment label contrast " + segmentRatio);
    }

    // ==================== Helpers ====================

    private static void darkPalette(Scene scene) {
        scene.getStylesheets().add(PALETTES);
        scene.getRoot().getStyleClass().add("dark-palette");
    }

    private static Map<String, String> sampleUnder(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        AtomicReference<Map<String, String>> samples = new AtomicReference<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            samples.set(ThemeColorSamples.sample(themer));
        });
        return samples.get();
    }

    /** The role tokens resolved on the dark application palette, as 8-bit hex. */
    private static Map<String, String> darkRoles() throws Exception {
        List<String> tokens = List.of("primary", "info", "surface", "surface-variant", "on-surface",
                "on-surface-secondary", "outline");
        Map<String, String> roles = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            Map<String, Region> probes = new LinkedHashMap<>();
            StackPane root = new StackPane();
            for (String token : tokens) {
                RXBox probe = new RXBox();
                probe.setStyle("-fx-background-color: -rx-" + token + ";");
                probes.put(token, probe);
                root.getChildren().add(probe);
            }
            Scene scene = new Scene(root, 100, 100);
            darkPalette(scene);
            root.applyCss();
            probes.forEach((token, probe) ->
                    roles.put(token, ThemeColorSamples.hex(probe.getBackground().getFills().get(0).getFill())));
        });
        return roles;
    }

    private static void assertSamples(Map<String, String> expected, Map<String, String> actual, String host) {
        assertEquals(expected.keySet(), actual.keySet(), "sampled keys under " + host);
        expected.forEach((key, value) -> assertEquals(value, actual.get(key), key + " under " + host));
    }

    /** WCAG 2.1 relative luminance. */
    private static double relativeLuminance(Color color) {
        double[] channels = {color.getRed(), color.getGreen(), color.getBlue()};
        for (int i = 0; i < channels.length; i++) {
            double c = channels[i];
            channels[i] = c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /** WCAG 2.1 contrast ratio of two opaque colors. */
    private static double contrastRatio(Color foreground, Color background) {
        double lf = relativeLuminance(foreground);
        double lb = relativeLuminance(background);
        return (Math.max(lf, lb) + 0.05) / (Math.min(lf, lb) + 0.05);
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof AssertionError) {
            throw (AssertionError) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
