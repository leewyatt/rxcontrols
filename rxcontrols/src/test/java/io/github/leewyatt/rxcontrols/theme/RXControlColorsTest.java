package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private static final String OVERRIDES =
            RXControlColorsTest.class.getResource("app-overrides.css").toExternalForm();

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
        DEFAULT_MODENA.put("slider.indicator", "#424242ff");
        DEFAULT_MODENA.put("slider.indicator.text", "#ffffffff");
        DEFAULT_MODENA.put("slider.indicator.caret", "#424242ff");
        DEFAULT_MODENA.put("range-slider.indicator", "#424242ff");
        DEFAULT_MODENA.put("range-slider.indicator.text", "#ffffffff");
        DEFAULT_MODENA.put("slider.tick-label", "#333333ff");
        DEFAULT_MODENA.put("range-slider.tick-label", "#333333ff");
        DEFAULT_MODENA.put("wave.container", "#e8f4ffff");
        DEFAULT_MODENA.put("circular.track", "#81cee7ff");
        DEFAULT_MODENA.put("circular.progress", "#0096c9ff");
        DEFAULT_MODENA.put("circular.completed", "#67c23aff");
        DEFAULT_MODENA.put("avatar.icon-disc", "#bdbdbdff");
        DEFAULT_MODENA.put("avatar.icon", "#ffffffff");
        DEFAULT_MODENA.put("snackbar.bar", "#323232ff");
        DEFAULT_MODENA.put("snackbar.message", "#ffffffde");
        DEFAULT_MODENA.put("snackbar.action", "#8791ffff");
        DEFAULT_MODENA.put("snackbar.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("snackbar.info.bar", "#909399ff");
        DEFAULT_MODENA.put("snackbar.info.message", "#ffffffff");
        DEFAULT_MODENA.put("snackbar.info.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("progress-bar.track", "#cfcfcfff");
        DEFAULT_MODENA.put("progress-bar.completed", "#67c23aff");
        DEFAULT_MODENA.put("step-indicator.track", "#cfcfcfff");
        DEFAULT_MODENA.put("carousel-navigator.track", "#ffffff59");
        DEFAULT_MODENA.put("digit.lit", "#333333ff");
        DEFAULT_MODENA.put("digit.unlit", "#33333326");
        DEFAULT_MODENA.put("fill-button.filling-armed.text", "#ffffffff");
        DEFAULT_MODENA.put("sidebar.background", "#ecececff");
        DEFAULT_MODENA.put("sidebar.selected", "#ccd1ffff");
        DEFAULT_MODENA.put("sidebar.selected.text", "#333333ff");
        DEFAULT_MODENA.put("timeline.dot", "#616dffff");
        DEFAULT_MODENA.put("timeline.primary-dot", "#616dffff");
        DEFAULT_MODENA.put("timeline.hollow-dot.border", "#616dffff");
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
        PRIMER_LIGHT.put("slider.indicator", "#424242ff");
        PRIMER_LIGHT.put("slider.indicator.text", "#ffffffff");
        PRIMER_LIGHT.put("slider.indicator.caret", "#424242ff");
        PRIMER_LIGHT.put("range-slider.indicator", "#424242ff");
        PRIMER_LIGHT.put("range-slider.indicator.text", "#ffffffff");
        PRIMER_LIGHT.put("slider.tick-label", "#57606aff");
        PRIMER_LIGHT.put("range-slider.tick-label", "#57606aff");
        PRIMER_LIGHT.put("wave.container", "#f6f8faff");
        PRIMER_LIGHT.put("circular.track", "#54aeff66");
        PRIMER_LIGHT.put("circular.progress", "#0969daff");
        PRIMER_LIGHT.put("circular.completed", "#2da44eff");
        PRIMER_LIGHT.put("avatar.icon-disc", "#6e7781ff");
        PRIMER_LIGHT.put("avatar.icon", "#ffffffff");
        PRIMER_LIGHT.put("snackbar.bar", "#323232ff");
        PRIMER_LIGHT.put("snackbar.message", "#ffffffde");
        PRIMER_LIGHT.put("snackbar.action", "#3787e2ff");
        PRIMER_LIGHT.put("snackbar.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("snackbar.info.bar", "#6e7781ff");
        PRIMER_LIGHT.put("snackbar.info.message", "#ffffffff");
        PRIMER_LIGHT.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("progress-bar.track", "#d0d7deff");
        PRIMER_LIGHT.put("progress-bar.completed", "#2da44eff");
        PRIMER_LIGHT.put("step-indicator.track", "#d0d7deff");
        PRIMER_LIGHT.put("carousel-navigator.track", "#ffffff59");
        PRIMER_LIGHT.put("digit.lit", "#24292fff");
        PRIMER_LIGHT.put("digit.unlit", "#24292f26");
        PRIMER_LIGHT.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_LIGHT.put("sidebar.background", "#ffffffff");
        PRIMER_LIGHT.put("sidebar.selected", "#ddf4ffff");
        PRIMER_LIGHT.put("sidebar.selected.text", "#24292fff");
        PRIMER_LIGHT.put("timeline.dot", "#0969daff");
        PRIMER_LIGHT.put("timeline.primary-dot", "#0969daff");
        PRIMER_LIGHT.put("timeline.hollow-dot.border", "#0969daff");
        PRIMER_LIGHT.put("list-view.background", "#d0d7deff #ffffffff");
        // The views read -rx-surface; AtlantaFX does not define -fx-background.
        PRIMER_LIGHT.put("tile-view.background", "#d0d7deff #ffffffff");
        PRIMER_LIGHT.put("masonry-view.background", "#d0d7deff #ffffffff");
    }

    static {
        PRIMER_DARK.put("cascader.prompt", "#8b949eff");
        PRIMER_DARK.put("cascader.column.border", "#30363dff");
        PRIMER_DARK.put("cascader.cell.hover", "#161b22ff");
        PRIMER_DARK.put("segmented.selected", "#c9d1d9ff");
        PRIMER_DARK.put("segmented.unselected", "#c9d1d9ff");
        PRIMER_DARK.put("segmented.indicator", "#343d4aff");
        PRIMER_DARK.put("seek.track", "#161b22ff");
        PRIMER_DARK.put("seek.secondary", "#30363dff");
        PRIMER_DARK.put("slider.track", "#30363dff");
        PRIMER_DARK.put("slider.indicator", "#e0e0e0ff");
        PRIMER_DARK.put("slider.indicator.text", "#000000de");
        PRIMER_DARK.put("slider.indicator.caret", "#e0e0e0ff");
        PRIMER_DARK.put("range-slider.indicator", "#e0e0e0ff");
        PRIMER_DARK.put("range-slider.indicator.text", "#000000de");
        PRIMER_DARK.put("slider.tick-label", "#8b949eff");
        PRIMER_DARK.put("range-slider.tick-label", "#8b949eff");
        PRIMER_DARK.put("wave.container", "#161b22ff");
        PRIMER_DARK.put("circular.track", "#388bfd66");
        PRIMER_DARK.put("circular.progress", "#1f6febff");
        PRIMER_DARK.put("circular.completed", "#238636ff");
        PRIMER_DARK.put("avatar.icon-disc", "#6e7681ff");
        PRIMER_DARK.put("avatar.icon", "#ffffffff");
        // The snackbar is an inverse surface, so it turns light on a dark AtlantaFX theme.
        PRIMER_DARK.put("snackbar.bar", "#e0e0e0ff");
        PRIMER_DARK.put("snackbar.message", "#000000de");
        PRIMER_DARK.put("snackbar.action", "#164fa4ff");
        PRIMER_DARK.put("snackbar.close-icon", "#00000099");
        PRIMER_DARK.put("snackbar.info.bar", "#6e7681ff");
        PRIMER_DARK.put("snackbar.info.message", "#ffffffff");
        PRIMER_DARK.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_DARK.put("progress-bar.track", "#30363dff");
        PRIMER_DARK.put("progress-bar.completed", "#238636ff");
        PRIMER_DARK.put("step-indicator.track", "#30363dff");
        PRIMER_DARK.put("carousel-navigator.track", "#ffffff59");
        PRIMER_DARK.put("digit.lit", "#c9d1d9ff");
        PRIMER_DARK.put("digit.unlit", "#c9d1d926");
        PRIMER_DARK.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_DARK.put("sidebar.background", "#0d1117ff");
        PRIMER_DARK.put("sidebar.selected", "#388bfd26");
        PRIMER_DARK.put("sidebar.selected.text", "#c9d1d9ff");
        PRIMER_DARK.put("timeline.dot", "#1f6febff");
        PRIMER_DARK.put("timeline.primary-dot", "#1f6febff");
        PRIMER_DARK.put("timeline.hollow-dot.border", "#1f6febff");
        PRIMER_DARK.put("list-view.background", "#30363dff #0d1117ff");
        // The views read -rx-surface; AtlantaFX does not define -fx-background.
        PRIMER_DARK.put("tile-view.background", "#30363dff #0d1117ff");
        PRIMER_DARK.put("masonry-view.background", "#30363dff #0d1117ff");
    }

    private static final double WCAG_AA_CONTRAST = 4.5;
    private static final double PLACEHOLDER_CONTRAST = 3.0;
    private static final double TRACK_CONTRAST = 1.3;
    private static final double NON_TEXT_CONTRAST = 3.0;
    private static final double PILL_MIN_CONTRAST = 1.04;
    private static final double PILL_MAX_CONTRAST = 3.0;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");

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
        Map<String, String> roles = roles(Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);

        assertEquals(roles.get("on-surface-secondary"), samples.get("cascader.prompt"), "cascader prompt");
        assertEquals(roles.get("outline"), samples.get("cascader.column.border"), "cascader column separator");
        assertEquals(roles.get("on-surface"), samples.get("segmented.unselected"), "unselected segment label");
        assertEquals(roles.get("on-surface"), samples.get("segmented.selected"), "selected segment label on the dark pill");
        assertEquals(roles.get("surface-variant"), samples.get("seek.track"), "seek bar track");
        assertEquals(roles.get("outline"), samples.get("seek.secondary"), "seek bar buffer");
        assertEquals(roles.get("outline"), samples.get("slider.track"), "slider track");
        assertEquals(roles.get("surface-variant"), samples.get("wave.container"), "wave container");
        assertEquals("#757575ff", samples.get("avatar.icon-disc"), "avatar placeholder disc");
        assertEquals("#ffffffff", samples.get("avatar.icon"), "avatar placeholder icon");
        assertEquals("#e0e0e0ff", samples.get("snackbar.bar"), "snackbar turns into a light bar");
        assertEquals("#000000de", samples.get("snackbar.message"), "snackbar message on the light bar");
        double actionRatio = contrastRatio(Color.web(samples.get("snackbar.action")), Color.web(samples.get("snackbar.bar")));
        assertTrue(actionRatio >= WCAG_AA_CONTRAST, "snackbar action on the light bar: contrast " + actionRatio);
        assertEquals("#00000099", samples.get("snackbar.close-icon"), "snackbar close icon on the light bar");
        assertEquals(roles.get("info"), samples.get("snackbar.info.bar"), "severity bar keeps its tint");
        assertEquals("#ffffffff", samples.get("snackbar.info.message"), "severity bar keeps white text");
        assertEquals("#ffffffb2", samples.get("snackbar.info.close-icon"), "severity bar keeps a light close icon");
        assertEquals(roles.get("outline"), samples.get("progress-bar.track"), "segmented progress track");
        assertEquals(roles.get("success"), samples.get("progress-bar.completed"), "completed segmented progress fill");
        assertEquals(roles.get("success"), samples.get("circular.completed"), "completed circular progress arc");
        assertEquals(roles.get("outline"), samples.get("step-indicator.track"), "step indicator track");
        assertEquals("#ffffff59", samples.get("carousel-navigator.track"), "carousel navigator stays translucent white");
        assertEquals(roles.get("on-surface"), samples.get("digit.lit"), "lit digit segment");
        assertEquals(samples.get("digit.lit").substring(0, 7) + "26", samples.get("digit.unlit"),
                "unlit digit segment is the lit color at 15% opacity");
        assertEquals("#ffffffff", samples.get("fill-button.filling-armed.text"), "filling fill button text");
        assertEquals(roles.get("primary"), samples.get("timeline.dot"), "default timeline dot");
        assertEquals(roles.get("primary"), samples.get("timeline.primary-dot"), "primary timeline dot");
        assertEquals(roles.get("primary"), samples.get("timeline.hollow-dot.border"), "hollow timeline dot outline");
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

    // ==================== Circular progress ====================

    /**
     * The circular progress track stays visible on both surface roles yet weaker than the
     * progress arc, on default Modena, a dark application palette and every AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void circularTrackIsVisibleButBelowTheProgressOnEveryHost() throws Exception {
        // Modena panels and fields paint -fx-control-inner-background; AtlantaFX panels are -rx-surface-variant.
        List<String> modenaBackdrops = List.of("-rx-surface", "-rx-surface-variant", "-fx-control-inner-background");
        List<String> atlantaFxBackdrops = List.of("-rx-surface", "-rx-surface-variant");
        assertCircularTrack("default Modena", Application.STYLESHEET_MODENA, scene -> { }, modenaBackdrops);
        assertCircularTrack("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette,
                modenaBackdrops);
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            assertCircularTrack(theme.getName(), theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                    atlantaFxBackdrops);
        }
    }

    private static void assertCircularTrack(String host, String userAgentStylesheet, Consumer<Scene> themer,
            List<String> backdrops) throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        Map<String, String> resolved = resolve(userAgentStylesheet, themer, backdrops);
        Color trackPaint = Color.web(samples.get("circular.track"));
        for (String backdropName : backdrops) {
            Color backdrop = Color.web(resolved.get(backdropName));
            double trackRatio = contrastRatio(composite(trackPaint, backdrop), backdrop);
            assertTrue(trackRatio >= TRACK_CONTRAST,
                    "circular track vs " + backdropName + " " + trackRatio + " under " + host);
        }
        Color surface = Color.web(resolved.get("-rx-surface"));
        double trackRatio = contrastRatio(composite(trackPaint, surface), surface);
        double progressRatio = contrastRatio(composite(Color.web(samples.get("circular.progress")), surface), surface);
        assertTrue(trackRatio < progressRatio,
                "circular track " + trackRatio + " outweighs the progress " + progressRatio + " under " + host);
    }

    // ==================== Segmented pill ====================

    /**
     * The segmented selection pill stands apart from its track without glaring over it, and
     * keeps the selected label legible, on default Modena, a dark application palette and every
     * AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void segmentedPillStandsOutOnEveryHost() throws Exception {
        assertSegmentedPill("default Modena", Application.STYLESHEET_MODENA, scene -> { });
        assertSegmentedPill("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            assertSegmentedPill(theme.getName(), theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
        }
    }

    private static void assertSegmentedPill(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        Color track = Color.web(roles(userAgentStylesheet, themer).get("surface-variant"));
        Color pill = composite(Color.web(samples.get("segmented.indicator")), track);
        Color label = composite(Color.web(samples.get("segmented.selected")), pill);
        double edge = contrastRatio(pill, track);
        double text = contrastRatio(label, pill);
        assertTrue(edge >= PILL_MIN_CONTRAST && edge <= PILL_MAX_CONTRAST,
                "segmented pill vs track " + edge + " under " + host);
        assertTrue(text >= WCAG_AA_CONTRAST, "selected segment label " + text + " under " + host);
    }

    // ==================== Slider value bubble ====================

    /**
     * The slider and range slider value bubble stands out from the surface and keeps its
     * text legible, and the tick labels stay legible on the surface, on default Modena, a
     * dark application palette and every AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void sliderBubbleAndTickLabelsStayLegibleOnEveryHost() throws Exception {
        assertSliderBubble("default Modena", Application.STYLESHEET_MODENA, scene -> { });
        assertSliderBubble("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            assertSliderBubble(theme.getName(), theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
        }
    }

    private static void assertSliderBubble(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        Color surface = Color.web(roles(userAgentStylesheet, themer).get("surface"));
        for (String slider : List.of("slider", "range-slider")) {
            Color bubble = Color.web(samples.get(slider + ".indicator"));
            Color text = composite(Color.web(samples.get(slider + ".indicator.text")), bubble);
            double edge = contrastRatio(bubble, surface);
            double label = contrastRatio(text, bubble);
            assertTrue(edge >= NON_TEXT_CONTRAST, slider + " bubble vs surface " + edge + " under " + host);
            assertTrue(label >= WCAG_AA_CONTRAST, slider + " bubble text " + label + " under " + host);
        }
        assertEquals(samples.get("slider.indicator"), samples.get("slider.indicator.caret"),
                "caret matches the bubble under " + host);
        for (String slider : List.of("slider", "range-slider")) {
            double tick = contrastRatio(Color.web(samples.get(slider + ".tick-label")), surface);
            assertTrue(tick >= WCAG_AA_CONTRAST, slider + " tick label " + tick + " under " + host);
        }
    }

    // ==================== Sidebar ====================

    /**
     * The selected sidebar item keeps its label legible on its tint, on default Modena, a
     * dark application palette and every AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void sidebarSelectedItemStaysLegibleOnEveryHost() throws Exception {
        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("default Modena", sidebarSelectedContrast(Application.STYLESHEET_MODENA, scene -> { }));
        ratios.put("dark palette", sidebarSelectedContrast(Application.STYLESHEET_MODENA,
                RXControlColorsTest::darkPalette));
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            ratios.put(theme.getName(), sidebarSelectedContrast(theme.getUserAgentStylesheet(),
                    RXAtlantaFXThemeBridge::install));
        }
        ratios.forEach((host, ratio) -> assertTrue(ratio >= WCAG_AA_CONTRAST,
                "selected sidebar item text " + ratio + " under " + host));
    }

    private static double sidebarSelectedContrast(String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        Color tint = composite(Color.web(samples.get("sidebar.selected")), Color.web(samples.get("sidebar.background")));
        Color text = composite(Color.web(samples.get("sidebar.selected.text")), tint);
        return contrastRatio(text, tint);
    }

    // ==================== Tile / masonry cells ====================

    /**
     * Under default Modena a selected tile or masonry cell keeps its selection-bar
     * background with black text, and an unselected cell keeps black text.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void defaultModenaKeepsTheCellColors() throws Exception {
        List<String> samples = cellSamples(Application.STYLESHEET_MODENA, scene -> { });
        assertEquals(List.of(
                "RXTileView unselected text #000000ff", "RXTileView selected #0096c9ff text #000000ff",
                "RXMasonryView unselected text #000000ff", "RXMasonryView selected #0096c9ff text #000000ff"),
                samples);
    }

    /**
     * Cell text stays legible, selected or not, on a dark application palette and under
     * every AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void cellTextStaysLegibleOnEveryHost() throws Exception {
        Map<String, List<Double>> ratios = new LinkedHashMap<>();
        ratios.put("dark palette", cellContrasts(Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette));
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            ratios.put(theme.getName(), cellContrasts(theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install));
        }
        ratios.forEach((host, values) -> values.forEach(ratio ->
                assertTrue(ratio >= WCAG_AA_CONTRAST, "cell text contrast under " + host + ": " + values)));
    }

    /**
     * The avatar placeholder disc differs from both surface roles, and its icon from the
     * disc, on default Modena, a dark application palette and every AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void avatarPlaceholderDiscDiffersFromSurfacesOnEveryHost() throws Exception {
        assertAvatarDiscDiffers("default Modena", Application.STYLESHEET_MODENA, scene -> { });
        assertAvatarDiscDiffers("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            assertAvatarDiscDiffers(theme.getName(), theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
        }
    }

    private static void assertAvatarDiscDiffers(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        Map<String, String> roles = roles(userAgentStylesheet, themer);
        String disc = samples.get("avatar.icon-disc");
        assertNotEquals(roles.get("surface"), disc, "avatar disc is the surface color under " + host);
        assertNotEquals(roles.get("surface-variant"), disc, "avatar disc is the panel color under " + host);
        assertNotEquals(disc, samples.get("avatar.icon"), "avatar icon is the disc color under " + host);
    }

    /**
     * Under the bridge, an application stylesheet added before {@code install} still
     * colors the avatar placeholder, the selected tile / masonry cell and the seek bar
     * track token.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void applicationStylesheetOverridesThemedPartsUnderTheBridge() throws Exception {
        Map<String, String> colors = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            RXAvatar avatar = new RXAvatar();
            RXSeekBar seekBar = new RXSeekBar();
            RXTileView<String> tile = new RXTileView<>();
            tile.getItems().add("alpha");
            RXMasonryView<String> masonry = new RXMasonryView<>();
            masonry.getItems().add("alpha");
            StackPane root = new StackPane(avatar, seekBar, tile, masonry);
            Scene scene = new Scene(root, 600, 400);
            scene.getStylesheets().add(OVERRIDES);
            RXAtlantaFXThemeBridge.install(scene);
            tile.getSelectionModel().select(0);
            masonry.getSelectionModel().select(0);
            root.applyCss();
            root.layout();
            colors.put("avatar disc", ThemeColorSamples.hex(fill(avatar.lookup(".default-icon-wrapper"))));
            colors.put("avatar icon", ThemeColorSamples.hex(fill(avatar.lookup(".default-icon"))));
            colors.put("seek track", ThemeColorSamples.hex(fill(seekBar.lookup(".track"))));
            for (Node cell : List.of(tile.lookup(".rx-tile-cell:selected"), masonry.lookup(".rx-masonry-cell:selected"))) {
                String name = cell.getStyleClass().contains("rx-tile-cell") ? "tile" : "masonry";
                colors.put(name + " background", ThemeColorSamples.hex(fill(cell)));
                colors.put(name + " text", ThemeColorSamples.hex(((Labeled) cell).getTextFill()));
            }
        });
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("avatar disc", "#4f46e5ff");
        expected.put("avatar icon", "#ff0000ff");
        expected.put("seek track", "#0000ffff");
        expected.put("tile background", "#00ff00ff");
        expected.put("tile text", "#ff0000ff");
        expected.put("masonry background", "#00ff00ff");
        expected.put("masonry text", "#ff0000ff");
        assertEquals(expected, colors);
    }

    /**
     * Under the bridge a disabled segment label takes the disabled text role, not the
     * scoped default label color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disabledSegmentLabelUnderTheBridge() throws Exception {
        String stylesheet = new PrimerLight().getUserAgentStylesheet();
        AtomicReference<String> label = new AtomicReference<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(stylesheet);
            RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                    new RXSegmentedItem<>("on", "On"), new RXSegmentedItem<>("off", "Off"));
            segmented.setDisable(true);
            StackPane root = new StackPane(segmented);
            RXAtlantaFXThemeBridge.install(new Scene(root, 300, 100));
            root.applyCss();
            label.set(ThemeColorSamples.hex(((Labeled) segmented.lookup(".segment .label")).getTextFill()));
        });
        assertEquals(roles(stylesheet, RXAtlantaFXThemeBridge::install).get("on-surface-disabled"), label.get());
    }

    /**
     * A control that is itself the bridged scene root takes the same scoped colors as
     * one nested under the root.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void controlAsSceneRootTakesTheBridgedColors() throws Exception {
        Map<String, String> asRoot = new LinkedHashMap<>();
        Map<String, String> nested = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            Function<Region, String> track = control -> ThemeColorSamples.hex(fill(control.lookup(".track")));
            sampleBothWays("seek track", RXSeekBar::new, track, asRoot, nested);
            sampleBothWays("slider track", RXSlider::new, track, asRoot, nested);
            sampleBothWays("range slider track", RXRangeSlider::new, track, asRoot, nested);
            sampleBothWays("wave container", RXWaveProgressIndicator::new,
                    control -> ThemeColorSamples.hex(fill(control.lookup(".wave-container"))), asRoot, nested);
            sampleBothWays("circular track", RXCircularProgressIndicator::new,
                    control -> ThemeColorSamples.hex(((RXCircularProgressIndicator) control).getTrackStroke()),
                    asRoot, nested);
            sampleBothWays("segment labels", () -> {
                RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                        new RXSegmentedItem<>("on", "On"), new RXSegmentedItem<>("off", "Off"));
                segmented.select("on");
                return segmented;
            }, control -> {
                String selected = "missing";
                String unselected = "missing";
                for (Node segment : control.lookupAll(".segment")) {
                    String text = ThemeColorSamples.hex(((Labeled) ((Parent) segment).lookup(".label")).getTextFill());
                    if (segment.getPseudoClassStates().contains(SELECTED)) {
                        selected = text;
                    } else {
                        unselected = text;
                    }
                }
                return "selected " + selected + " unselected " + unselected;
            }, asRoot, nested);
            sampleBothWays("cascader prompt", () -> {
                RXCascader<String> cascader = new RXCascader<>();
                cascader.setPromptText("Select");
                return cascader;
            }, control -> ThemeColorSamples.hex(((Labeled) control.lookup(".display .label")).getTextFill()),
                    asRoot, nested);
            sampleBothWays("cascader view column and hover cell", () -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                RXCascaderItem<String> parent = new RXCascaderItem<>("parent");
                parent.getChildren().add(new RXCascaderItem<>("child"));
                view.getRootItems().add(parent);
                return view;
            }, control -> {
                Region column = (Region) control.lookup(".column");
                Node cell = control.lookup(".list-cell");
                cell.pseudoClassStateChanged(HOVER, true);
                control.applyCss();
                return ThemeColorSamples.hex(column.getBorder().getStrokes().get(0).getRightStroke())
                        + " " + ThemeColorSamples.hex(fill(cell));
            }, asRoot, nested);
        });
        assertEquals(nested, asRoot);
    }

    private static void sampleBothWays(String name, Supplier<Region> factory, Function<Region, String> reader,
            Map<String, String> asRoot, Map<String, String> nested) {
        Region rootControl = factory.get();
        RXAtlantaFXThemeBridge.install(new Scene(rootControl, 300, 200));
        rootControl.applyCss();
        rootControl.layout();
        asRoot.put(name, reader.apply(rootControl));

        Region nestedControl = factory.get();
        StackPane root = new StackPane(nestedControl);
        RXAtlantaFXThemeBridge.install(new Scene(root, 300, 200));
        root.applyCss();
        root.layout();
        nested.put(name, reader.apply(nestedControl));
    }

    private static Paint fill(Node node) {
        return ((Region) node).getBackground().getFills().get(0).getFill();
    }

    /** "View unselected text #…" and "View selected #background text #…" for a tile and a masonry view. */
    private static List<String> cellSamples(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        List<String> samples = new ArrayList<>();
        for (CellColors colors : cellColors(userAgentStylesheet, themer)) {
            samples.add(colors.view + " unselected text " + ThemeColorSamples.hex(colors.unselectedText));
            samples.add(colors.view + " selected " + ThemeColorSamples.hex(colors.selectedBackground)
                    + " text " + ThemeColorSamples.hex(colors.selectedText));
        }
        return samples;
    }

    /** Unselected text on the view surface, then selected text on the selected cell, per view. */
    private static List<Double> cellContrasts(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        List<Double> ratios = new ArrayList<>();
        for (CellColors colors : cellColors(userAgentStylesheet, themer)) {
            ratios.add(contrastRatio(colors.unselectedText, colors.surface));
            ratios.add(contrastRatio(colors.selectedText, composite(colors.selectedBackground, colors.surface)));
        }
        return ratios;
    }

    /** The resolved cell colors of one view. */
    private record CellColors(String view, Color surface, Color unselectedText, Color selectedBackground,
                              Color selectedText) {
    }

    private static List<CellColors> cellColors(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        List<CellColors> found = new ArrayList<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXTileView<String> tile = new RXTileView<>();
            tile.getItems().addAll("alpha", "beta");
            RXMasonryView<String> masonry = new RXMasonryView<>();
            masonry.getItems().addAll("alpha", "beta");
            StackPane root = new StackPane(tile, masonry);
            Scene scene = new Scene(root, 600, 400);
            themer.accept(scene);
            root.applyCss();
            root.layout();
            tile.getSelectionModel().select(0);
            masonry.getSelectionModel().select(0);
            root.applyCss();
            root.layout();
            found.add(cellColors("RXTileView", tile, ".rx-tile-cell"));
            found.add(cellColors("RXMasonryView", masonry, ".rx-masonry-cell"));
        });
        return found;
    }

    private static CellColors cellColors(String name, Region view, String cellSelector) {
        Labeled selected = null;
        Labeled unselected = null;
        for (Node node : view.lookupAll(cellSelector)) {
            if (!(node instanceof Labeled) || ((Labeled) node).getText() == null) {
                continue;
            }
            if (node.getPseudoClassStates().contains(SELECTED)) {
                selected = (Labeled) node;
            } else {
                unselected = (Labeled) node;
            }
        }
        assertTrue(selected != null && unselected != null, name + ": a selected and an unselected cell");
        List<?> layers = view.getBackground().getFills();
        Color surface = (Color) view.getBackground().getFills().get(layers.size() - 1).getFill();
        Color selectedBackground = (Color) selected.getBackground().getFills().get(0).getFill();
        return new CellColors(name, surface, (Color) unselected.getTextFill(), selectedBackground,
                (Color) selected.getTextFill());
    }

    /** Alpha-composites a (possibly translucent) fill over its backdrop. */
    private static Color composite(Color fill, Color backdrop) {
        double alpha = fill.getOpacity();
        return new Color(
                fill.getRed() * alpha + backdrop.getRed() * (1.0 - alpha),
                fill.getGreen() * alpha + backdrop.getGreen() * (1.0 - alpha),
                fill.getBlue() * alpha + backdrop.getBlue() * (1.0 - alpha),
                1.0);
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

    /** The role tokens resolved under a host, as 8-bit hex. */
    private static Map<String, String> roles(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        List<String> tokens = List.of("primary", "success", "info", "surface", "surface-variant", "on-surface",
                "on-surface-secondary", "on-surface-disabled", "outline");
        Map<String, String> roles = new LinkedHashMap<>();
        List<String> expressions = new ArrayList<>();
        tokens.forEach(token -> expressions.add("-rx-" + token));
        Map<String, String> resolved = resolve(userAgentStylesheet, themer, expressions);
        tokens.forEach(token -> roles.put(token, resolved.get("-rx-" + token)));
        return roles;
    }

    /** Background color expressions resolved under a host, as 8-bit hex. */
    private static Map<String, String> resolve(String userAgentStylesheet, Consumer<Scene> themer,
            List<String> expressions) throws Exception {
        Map<String, String> resolved = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            Map<String, Region> probes = new LinkedHashMap<>();
            StackPane root = new StackPane();
            for (String expression : expressions) {
                RXBox probe = new RXBox();
                probe.setStyle("-fx-background-color: " + expression + ";");
                probes.put(expression, probe);
                root.getChildren().add(probe);
            }
            Scene scene = new Scene(root, 100, 100);
            themer.accept(scene);
            root.applyCss();
            probes.forEach((expression, probe) ->
                    resolved.put(expression, ThemeColorSamples.hex(probe.getBackground().getFills().get(0).getFill())));
        });
        return resolved;
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
