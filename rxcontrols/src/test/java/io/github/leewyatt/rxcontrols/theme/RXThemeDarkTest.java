package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless acceptance + drift-proofing for the built-in dark overlay
 * ({@code rx-controls-dark.css} + {@link RXTheme}). Runs on the default Modena
 * Application UA — the dark overlay is self-contained (literal values), so it does
 * not need any host theme. Asserts each {@code -rx-*} role token resolves to its
 * dark value, that the Modena {@code -fx-*} compat overrides keep raw-{@code -fx-*}
 * controls (cascader) dark, and that {@link RXTheme.Variant#LIGHT}/uninstall revert.
 */
public class RXThemeDarkTest {

    private static final String BASELINE_CSS =
            "/io/github/leewyatt/rxcontrols/theme/rx-controls.css";
    private static final String DARK_CSS =
            "/io/github/leewyatt/rxcontrols/theme/rx-controls-dark.css";

    private static final Pattern ROOT_SELECTOR_PATTERN = Pattern.compile("\\.(rx-[a-z0-9-]+)");

    /** WCAG 2.1 AA minimum contrast ratio for normal-size text. */
    private static final double WCAG_AA_CONTRAST = 4.5;

    private static final Color DARK_PRIMARY = Color.web("#7c86ff");
    private static final Color BASELINE_PRIMARY = Color.web("#616dff");

    /** Exact {@code -rx-*} -> resolved dark value (excludes the two derive tokens). */
    private static final Map<String, Color> EXPECTED = new LinkedHashMap<>();

    private static final List<String> DERIVED = List.of("primary-hover", "primary-active");

    /** Modena base palette the dark overlay overrides -> resolved dark value (exact ones). */
    private static final Map<String, Color> COMPAT = new LinkedHashMap<>();

    static {
        EXPECTED.put("primary", Color.web("#7c86ff"));
        EXPECTED.put("primary-bg", Color.rgb(124, 134, 255, 0.18));
        EXPECTED.put("on-primary", Color.web("#ffffff"));
        EXPECTED.put("success", Color.web("#6cc04a"));
        EXPECTED.put("warning", Color.web("#e6a23c"));
        EXPECTED.put("danger", Color.web("#f56c6c"));
        EXPECTED.put("info", Color.web("#a0a4ad"));
        EXPECTED.put("surface", Color.web("#1e1f2b"));
        EXPECTED.put("surface-variant", Color.web("#2a2c3a"));
        EXPECTED.put("on-surface", Color.web("#e6e7ee"));
        EXPECTED.put("on-surface-secondary", Color.web("#a6a8b5"));
        EXPECTED.put("on-surface-disabled", Color.rgb(255, 255, 255, 0.35));
        EXPECTED.put("outline", Color.web("#3a3d4d"));
        EXPECTED.put("outline-variant", Color.web("#2c2e3b"));
        EXPECTED.put("focus", Color.web("#7c86ff"));
        EXPECTED.put("selection", Color.rgb(124, 134, 255, 0.4));
        EXPECTED.put("state-overlay-color", Color.web("#ffffff"));

        COMPAT.put("-fx-base", Color.web("#1e1f2b"));
        COMPAT.put("-fx-color", Color.web("#1e1f2b"));
        COMPAT.put("-fx-control-inner-background", Color.web("#1e1f2b"));
        COMPAT.put("-fx-text-base-color", Color.web("#e6e7ee"));
        COMPAT.put("-fx-text-inner-color", Color.web("#e6e7ee"));
        COMPAT.put("-fx-mid-text-color", Color.web("#a6a8b5"));
        COMPAT.put("-fx-box-border", Color.web("#3a3d4d"));
        COMPAT.put("-fx-text-box-border", Color.web("#3a3d4d"));
        COMPAT.put("-fx-accent", Color.web("#7c86ff"));
        COMPAT.put("-fx-focus-color", Color.web("#7c86ff"));
        COMPAT.put("-fx-faint-focus-color", Color.rgb(124, 134, 255, 0.4));
    }

    /**
     * Starts the toolkit and pins Modena (the dark overlay rides on top of it).
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
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    /** Restores the shared-JVM default. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Drift guard ====================

    /**
     * The dark overlay's control-root selector list must equal the baseline's.
     */
    @Test
    public void darkOverlaySelectorListMatchesTheBaseline() {
        Set<String> baseline = controlRootSelectors(readResource(BASELINE_CSS));
        Set<String> dark = controlRootSelectors(readResource(DARK_CSS));
        assertTrue(dark.size() >= 40, "dark overlay selector scan looks broken: " + dark);
        assertEquals(baseline, dark,
                "rx-controls-dark.css control roots must match the rx-controls.css baseline");
    }

    // ==================== Token mapping ====================

    /**
     * Every {@code -rx-*} role token resolves to its dark value through the overlay;
     * the derive tokens resolve to a distinct color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void everyRoleTokenResolvesToItsDarkValue() throws Exception {
        Map<String, Region> probes = new LinkedHashMap<>();
        for (String token : EXPECTED.keySet()) {
            probes.put(token, probe("-rx-" + token));
        }
        for (String token : DERIVED) {
            probes.put(token, probe("-rx-" + token));
        }
        // Pin the derive tokens to the JavaFX-computed result of the exact
        // expressions the overlay uses (catches a wrong %/sign/base or a dropped derive).
        Region refHover = probe("derive(-rx-primary, 8%)");
        Region refActive = probe("derive(-rx-primary, -10%)");

        runOnFx(() -> {
            StackPane host = new StackPane();
            host.getChildren().addAll(probes.values());
            host.getChildren().addAll(refHover, refActive);
            Scene scene = new Scene(host, 200, 200);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
        });

        EXPECTED.forEach((token, expected) ->
                assertEquals(expected, background(probes.get(token)),
                        "-rx-" + token + " should resolve to its dark value"));

        Color refHoverColor = background(refHover);
        Color refActiveColor = background(refActive);
        assertNotNull(refHoverColor, "reference derive(-rx-primary, 8%) did not resolve");
        assertNotNull(refActiveColor, "reference derive(-rx-primary, -10%) did not resolve");
        Color hover = background(probes.get("primary-hover"));
        Color active = background(probes.get("primary-active"));
        assertEquals(refHoverColor, hover, "-rx-primary-hover must equal derive(-rx-primary, 8%)");
        assertEquals(refActiveColor, active, "-rx-primary-active must equal derive(-rx-primary, -10%)");
        assertTrue(!hover.equals(active), "hover (+8%) and active (-10%) must differ");
    }

    /**
     * The Modena {@code -fx-*} base palette is overridden to the dark role tokens.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void modenaCompatOverridesResolveToDark() throws Exception {
        Map<String, Region> probes = new LinkedHashMap<>();
        for (String var : COMPAT.keySet()) {
            probes.put(var, probe(var));
        }

        runOnFx(() -> {
            StackPane host = new StackPane();
            host.getChildren().addAll(probes.values());
            Scene scene = new Scene(host, 200, 200);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
        });

        COMPAT.forEach((var, expected) ->
                assertEquals(expected, background(probes.get(var)),
                        var + " should be overridden to its dark role token"));
    }

    // ==================== Real consumers ====================

    /**
     * Real controls turn dark: {@code RXFillButton} fill (dark primary),
     * {@code RXButton} ripple (white state overlay), timeline success dot.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void realControlsTurnDark() throws Exception {
        AtomicReference<Color> fill = new AtomicReference<>();
        AtomicReference<Paint> ripple = new AtomicReference<>();
        AtomicReference<Color> successDot = new AtomicReference<>();

        runOnFx(() -> {
            RXFillButton fillButton = new RXFillButton("Go");
            RXButton plain = new RXButton("Go");
            RXTimelineItem done = new RXTimelineItem("Done");
            done.setType(RXTimelineItem.Type.SUCCESS);
            RXTimelineView timeline = new RXTimelineView(done);

            StackPane host = new StackPane(fillButton, plain, timeline);
            Scene scene = new Scene(host, 360, 220);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();

            Region region = (Region) fillButton.lookup(".fill-region");
            assertNotNull(region, "fill-region not found");
            fill.set(background(region));
            ripple.set(plain.getRippleFill());
            Node dot = timeline.lookup(".dot");
            assertNotNull(dot, "timeline dot not found");
            successDot.set(background((Region) dot));
        });

        assertEquals(DARK_PRIMARY, fill.get(), "RXFillButton fill -> -rx-primary (dark)");
        assertEquals(Color.web("#ffffff"), ripple.get(),
                "RXButton ripple -> -rx-state-overlay-color -> white (dark)");
        assertEquals(Color.web("#6cc04a"), successDot.get(), "timeline success dot -> -rx-success (dark)");
    }

    /**
     * A selected sidebar item stays legible under the dark overlay. Guards the
     * whole family against re-introducing a light-theme-only colour expression
     * (e.g. {@code derive(-fx-accent, 80%)}): a lighten-by-N% of a token that the
     * overlay itself re-points drifts toward the text colour in dark, and the two
     * meet. Assert perceived contrast, not the literal fill, so any future
     * expression that reads as unreadable fails here.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void selectedSidebarItemStaysLegibleUnderTheDarkOverlay() throws Exception {
        AtomicReference<Color> rail = new AtomicReference<>();
        AtomicReference<Color> selectedFill = new AtomicReference<>();
        AtomicReference<Paint> textFill = new AtomicReference<>();

        runOnFx(() -> {
            RXSidebarNavItem selected = new RXSidebarNavItem("Inbox", new Region());
            RXSidebar sidebar = new RXSidebar();
            sidebar.getItems().addAll(selected, new RXSidebarNavItem("Files", new Region()));
            sidebar.selectItem(selected);

            StackPane host = new StackPane(sidebar);
            Scene scene = new Scene(host, 320, 240);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();

            rail.set(background(sidebar));
            selectedFill.set(background(selected));
            textFill.set(selected.getTextFill());
        });

        assertNotNull(selectedFill.get(), "selected sidebar item has no background fill");
        // -rx-primary-bg is translucent in dark, so composite it over the rail
        // before measuring — reading the raw fill would report a false contrast.
        Color effective = composite(selectedFill.get(), rail.get());
        double ratio = contrastRatio((Color) textFill.get(), effective);
        assertTrue(ratio >= WCAG_AA_CONTRAST,
                "selected sidebar item is unreadable in dark: text " + textFill.get()
                        + " on " + effective + " = " + String.format("%.2f", ratio)
                        + ":1, need >= " + WCAG_AA_CONTRAST + ":1");
    }

    /**
     * A filling fill button keeps on-primary (white) text under the dark overlay
     * even when armed (the same {@code .button:armed} vs {@code :filling} tie).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void fillButtonArmedTextStaysOnPrimaryUnderTheDarkOverlay() throws Exception {
        AtomicReference<Paint> textFill = new AtomicReference<>();
        runOnFx(() -> {
            Label probe = new Label("x");
            probe.getStyleClass().addAll("button", "rx-fill-button");
            probe.pseudoClassStateChanged(PseudoClass.getPseudoClass("filling"), true);
            probe.pseudoClassStateChanged(PseudoClass.getPseudoClass("armed"), true);
            StackPane host = new StackPane(probe);
            Scene scene = new Scene(host, 120, 60);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            textFill.set(probe.getTextFill());
        });
        assertEquals(Color.web("#ffffff"), textFill.get(),
                "filling fill button text must stay -rx-on-primary (white) under dark");
    }

    /**
     * Controls whose colors are baseline literals (not tokens / not {@code -fx-*})
     * are recolored dark by the overlay's per-control flip rules — guarded here via
     * {@code RXTextView}, whose literal near-black text would otherwise be unreadable.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkOverlayRecolorsLiteralColoredControls() throws Exception {
        AtomicReference<Paint> textFill = new AtomicReference<>();
        runOnFx(() -> {
            RXTextView textView = new RXTextView("hello");
            StackPane host = new StackPane(textView);
            Scene scene = new Scene(host, 200, 80);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            textFill.set(textView.getTextFill());
        });
        assertEquals(Color.web("#e6e7ee"), textFill.get(),
                "RXTextView text must follow -rx-on-surface under dark (literal #1b1f2a would be unreadable)");
    }

    /**
     * The cascader prompt uses a baseline {@code derive(-fx-control-inner-background,
     * -30%)} grey tuned for the light surface; on the dark surface it collapses into
     * the background. The dark overlay re-points it to {@code -rx-on-surface-secondary}
     * so the prompt stays legible.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkOverlayMakesCascaderPromptLegible() throws Exception {
        AtomicReference<Paint> promptFill = new AtomicReference<>();
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.setPromptText("Select");
            StackPane host = new StackPane(cascader);
            Scene scene = new Scene(host, 240, 80);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            Label prompt = (Label) cascader.lookup(".display .label");
            assertNotNull(prompt, "the display label should exist");
            promptFill.set(prompt.getTextFill());
        });
        assertEquals(Color.web("#a6a8b5"), promptFill.get(),
                "cascader prompt must follow -rx-on-surface-secondary under dark");
    }

    /**
     * The selected segment's label sits on the white indicator pill, so under dark
     * it must stay dark — the broad unselected-label re-point (to light on-surface)
     * must not also lighten the selected label (which would be light-on-white).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void selectedSegmentLabelStaysDarkOnTheWhitePillUnderDark() throws Exception {
        AtomicReference<Paint> selectedFill = new AtomicReference<>();
        AtomicReference<Paint> unselectedFill = new AtomicReference<>();

        runOnFx(() -> {
            // Reproduce the segmented label structure + the :selected state.
            Label selected = new Label("on");
            StackPane selectedSeg = new StackPane(selected);
            selectedSeg.getStyleClass().add("segment");
            selectedSeg.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
            Label unselected = new Label("off");
            StackPane unselectedSeg = new StackPane(unselected);
            unselectedSeg.getStyleClass().add("segment");
            StackPane control = new StackPane(selectedSeg, unselectedSeg);
            control.getStyleClass().add("rx-segmented-control");

            StackPane host = new StackPane(control);
            Scene scene = new Scene(host, 200, 80);
            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            selectedFill.set(selected.getTextFill());
            unselectedFill.set(unselected.getTextFill());
        });

        assertEquals(Color.rgb(0, 0, 0, 0.88), selectedFill.get(),
                "selected segment label must stay dark on the white pill (not light-on-white)");
        assertEquals(Color.web("#e6e7ee"), unselectedFill.get(),
                "unselected segment label must be light on the dark track");
    }

    // ==================== Revert ====================

    /**
     * {@link RXTheme.Variant#LIGHT} and {@link RXTheme#uninstall} remove the dark
     * overlay, reverting tokens to the built-in light baseline.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void lightVariantAndUninstallRevertToBaseline() throws Exception {
        AtomicReference<Color> afterDark = new AtomicReference<>();
        AtomicReference<Color> afterLight = new AtomicReference<>();
        AtomicReference<Color> afterUninstall = new AtomicReference<>();

        runOnFx(() -> {
            RXFillButton fillButton = new RXFillButton("Go");
            StackPane host = new StackPane(fillButton);
            Scene scene = new Scene(host, 160, 80);

            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            afterDark.set(fillRegionColor(fillButton));

            RXTheme.install(scene, RXTheme.Variant.LIGHT);
            host.applyCss();
            host.layout();
            afterLight.set(fillRegionColor(fillButton));

            RXTheme.install(scene, RXTheme.Variant.DARK);
            host.applyCss();
            host.layout();
            RXTheme.uninstall(scene);
            host.applyCss();
            host.layout();
            afterUninstall.set(fillRegionColor(fillButton));
        });

        assertEquals(DARK_PRIMARY, afterDark.get(),
                "precondition: install(DARK) should apply the dark primary");
        assertEquals(BASELINE_PRIMARY, afterLight.get(),
                "install(LIGHT) should revert -rx-primary to the baseline brand color");
        assertEquals(BASELINE_PRIMARY, afterUninstall.get(),
                "uninstall should revert -rx-primary to the baseline brand color");
    }

    // ==================== Helpers ====================

    private static Region probe(String paint) {
        Region region = new Region();
        region.getStyleClass().add("rx-button"); // a covered control root
        region.setStyle("-fx-background-color: " + paint + ";");
        return region;
    }

    private static Color fillRegionColor(RXFillButton button) {
        Node region = button.lookup(".fill-region");
        return region instanceof Region ? background((Region) region) : null;
    }

    private static Color background(Region region) {
        if (region.getBackground() == null || region.getBackground().getFills().isEmpty()) {
            return null;
        }
        Paint fill = region.getBackground().getFills().get(0).getFill();
        return fill instanceof Color ? (Color) fill : null;
    }

    /** Alpha-composites a (possibly translucent) fill over its backdrop. */
    private static Color composite(Color fill, Color backdrop) {
        double alpha = fill.getOpacity();
        if (alpha >= 1.0 || backdrop == null) {
            return fill;
        }
        return new Color(
                fill.getRed() * alpha + backdrop.getRed() * (1.0 - alpha),
                fill.getGreen() * alpha + backdrop.getGreen() * (1.0 - alpha),
                fill.getBlue() * alpha + backdrop.getBlue() * (1.0 - alpha),
                1.0);
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

    /** WCAG 2.1 contrast ratio; both colors must already be opaque. */
    private static double contrastRatio(Color foreground, Color background) {
        double lf = relativeLuminance(foreground);
        double lb = relativeLuminance(background);
        return (Math.max(lf, lb) + 0.05) / (Math.min(lf, lb) + 0.05);
    }

    private static Set<String> controlRootSelectors(String css) {
        String stripped = css.replaceAll("(?s)/\\*.*?\\*/", " ");
        String selectorText = null;
        int from = 0;
        while (true) {
            int open = stripped.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = stripped.indexOf('}', open);
            if (close < 0) {
                break;
            }
            if (stripped.substring(open + 1, close).contains("-rx-primary:")) {
                int prevClose = stripped.lastIndexOf('}', open);
                selectorText = stripped.substring(prevClose < 0 ? 0 : prevClose + 1, open);
                break;
            }
            from = close + 1;
        }
        assertNotNull(selectorText, "rule defining -rx-primary not found");
        Set<String> selectors = new TreeSet<>();
        Matcher matcher = ROOT_SELECTOR_PATTERN.matcher(selectorText);
        while (matcher.find()) {
            selectors.add(matcher.group(1));
        }
        return selectors;
    }

    private static String readResource(String path) {
        try (InputStream in = RXThemeDarkTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
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
        if (!latch.await(10, TimeUnit.SECONDS)) {
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
