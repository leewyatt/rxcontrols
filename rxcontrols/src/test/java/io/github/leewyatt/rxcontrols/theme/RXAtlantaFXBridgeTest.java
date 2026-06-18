package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless acceptance + drift-proofing for the AtlantaFX author bridge
 * ({@code rx-controls-atlantafx.css} + {@link RXAtlantaFX}). Installs a real
 * {@link PrimerLight} Application UA so {@code -color-*} resolves, applies the
 * bridge, and asserts each {@code -rx-*} role token resolves to its mapped
 * AtlantaFX functional token (proposal §2). Popup propagation is covered by the
 * window-dependent {@code RXAtlantaFXBridgePopupTest}.
 *
 * <p>Assertions are value-based, not "zero unresolved lookups": AtlantaFX does
 * not define the Modena {@code -fx-*} palette, so RxControls rules still using raw
 * {@code -fx-*} colors are expected to be unresolved under AtlantaFX — that is the
 * untokenized-recolor backlog, not a bridge defect.
 */
public class RXAtlantaFXBridgeTest {

    private static final String BASELINE_CSS =
            "/io/github/leewyatt/rxcontrols/theme/rx-controls.css";
    private static final String BRIDGE_CSS =
            "/io/github/leewyatt/rxcontrols/theme/rx-controls-atlantafx.css";

    private static final Pattern ROOT_SELECTOR_PATTERN = Pattern.compile("\\.(rx-[a-z0-9-]+)");

    private static final Color ACCENT_EMPHASIS = Color.web("#0969da");

    /** Exact {@code -rx-*} -> resolved Primer-light value (excludes the two derive tokens). */
    private static final Map<String, Color> EXPECTED = new LinkedHashMap<>();

    /** Derive-based tokens: asserted to resolve to a color distinct from the base accent. */
    private static final List<String> DERIVED = List.of("primary-hover", "primary-active");

    static {
        EXPECTED.put("primary", Color.web("#0969da"));          // -color-accent-emphasis
        EXPECTED.put("primary-bg", Color.web("#ddf4ff"));       // -color-accent-subtle
        EXPECTED.put("on-primary", Color.web("#ffffff"));       // -color-fg-emphasis
        EXPECTED.put("success", Color.web("#2da44e"));          // -color-success-emphasis
        EXPECTED.put("warning", Color.web("#bf8700"));          // -color-warning-emphasis
        EXPECTED.put("danger", Color.web("#cf222e"));           // -color-danger-emphasis
        EXPECTED.put("info", Color.web("#6e7781"));             // -color-neutral-emphasis
        EXPECTED.put("surface", Color.web("#ffffff"));          // -color-bg-default
        EXPECTED.put("surface-variant", Color.web("#f6f8fa"));  // -color-bg-subtle
        EXPECTED.put("on-surface", Color.web("#24292f"));       // -color-fg-default
        EXPECTED.put("on-surface-secondary", Color.web("#57606a")); // -color-fg-muted
        EXPECTED.put("on-surface-disabled", Color.web("#6e7781"));  // -color-fg-subtle
        EXPECTED.put("outline", Color.web("#d0d7de"));          // -color-border-default
        EXPECTED.put("outline-variant", Color.web("#d0d7de"));  // -color-border-muted
        EXPECTED.put("focus", Color.web("#0969da"));            // -color-accent-emphasis
        EXPECTED.put("selection", Color.rgb(84, 174, 255, 0.4)); // -color-accent-muted
        EXPECTED.put("state-overlay-color", Color.web("#24292f")); // -color-fg-default
    }

    /**
     * Modena base palette the bridge supplies for RxControls rules still using raw
     * {@code -fx-*} (AtlantaFX defines none of these) -> resolved Primer-light value.
     */
    private static final Map<String, Color> COMPAT = new LinkedHashMap<>();

    static {
        COMPAT.put("-fx-base", Color.web("#ffffff"));                  // -color-bg-default
        COMPAT.put("-fx-color", Color.web("#ffffff"));                 // -color-bg-default
        COMPAT.put("-fx-control-inner-background", Color.web("#ffffff")); // -color-bg-default
        COMPAT.put("-fx-hover-base", Color.web("#f6f8fa"));            // -color-bg-subtle
        COMPAT.put("-fx-pressed-base", Color.web("#f6f8fa"));          // -color-bg-inset
        COMPAT.put("-fx-text-base-color", Color.web("#24292f"));       // -color-fg-default
        COMPAT.put("-fx-text-inner-color", Color.web("#24292f"));      // -color-fg-default
        COMPAT.put("-fx-mid-text-color", Color.web("#57606a"));        // -color-fg-muted
        COMPAT.put("-fx-box-border", Color.web("#d0d7de"));            // -color-border-default
        COMPAT.put("-fx-text-box-border", Color.web("#d0d7de"));       // -color-border-default
        COMPAT.put("-fx-accent", Color.web("#0969da"));                // -color-accent-emphasis
        COMPAT.put("-fx-focus-color", Color.web("#0969da"));           // -color-accent-emphasis
        COMPAT.put("-fx-faint-focus-color", Color.rgb(84, 174, 255, 0.4)); // -color-accent-muted
    }

    /**
     * Starts the toolkit and installs an AtlantaFX Application UA so the bridge's
     * {@code -color-*} references resolve.
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
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }

    /** Restores the shared-JVM default so later CSS tests that expect Modena pass. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Drift guard ====================

    /**
     * The bridge's control-root selector list must be the same set as the
     * baseline's: a new control root added to one but not the other (its tokens
     * would not follow / would be stale) fails here.
     */
    @Test
    public void bridgeSelectorListMatchesTheBaseline() {
        Set<String> baseline = controlRootSelectors(readResource(BASELINE_CSS));
        Set<String> bridge = controlRootSelectors(readResource(BRIDGE_CSS));

        assertTrue(bridge.size() >= 40,
                "bridge selector scan found only " + bridge.size() + " roots; parse is probably broken: " + bridge);
        assertEquals(baseline, bridge,
                "rx-controls-atlantafx.css control roots must match the rx-controls.css baseline");
    }

    // ==================== Full mapping ====================

    /**
     * Every {@code -rx-*} role token resolves through the scene-level bridge to its
     * mapped AtlantaFX functional-token value; the two derive tokens resolve to a
     * distinct color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void everyRoleTokenResolvesToItsAtlantaFxFunctionalToken() throws Exception {
        Map<String, Region> probes = new LinkedHashMap<>();
        for (String token : EXPECTED.keySet()) {
            probes.put(token, probe(token));
        }
        for (String token : DERIVED) {
            probes.put(token, probe(token));
        }
        // Reference probes carry the exact derive() expressions the bridge must
        // apply, so the two derive tokens are pinned to the JavaFX-computed result
        // version-independently (catches a wrong percentage/sign/base or a dropped
        // derive — not just "some color other than the base accent").
        Region refHover = referenceProbe("derive(-color-accent-emphasis, 8%)");
        Region refActive = referenceProbe("derive(-color-accent-emphasis, -10%)");

        runOnFx(() -> {
            StackPane host = new StackPane();
            host.getChildren().addAll(probes.values());
            host.getChildren().addAll(refHover, refActive);
            Scene scene = new Scene(host, 200, 200);
            RXAtlantaFX.applyTo(scene);
            host.applyCss();
            host.layout();
        });

        EXPECTED.forEach((token, expected) ->
                assertEquals(expected, background(probes.get(token)),
                        "-rx-" + token + " should resolve to its AtlantaFX functional token"));

        Color refHoverColor = background(refHover);
        Color refActiveColor = background(refActive);
        assertNotNull(refHoverColor, "reference derive(-color-accent-emphasis, 8%) did not resolve");
        assertNotNull(refActiveColor, "reference derive(-color-accent-emphasis, -10%) did not resolve");

        Color hover = background(probes.get("primary-hover"));
        Color active = background(probes.get("primary-active"));
        assertEquals(refHoverColor, hover,
                "-rx-primary-hover must equal derive(-color-accent-emphasis, 8%)");
        assertEquals(refActiveColor, active,
                "-rx-primary-active must equal derive(-color-accent-emphasis, -10%)");
        assertTrue(!hover.equals(active),
                "hover (+8%) and active (-10%) must derive to different colors");
    }

    // ==================== Real consumers ====================

    /**
     * Real controls follow the bridge through their actual styled properties:
     * {@code RXFillButton} fill (accent), {@code RXButton} ripple (state overlay),
     * and an {@code RXTimelineView} success dot (success).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void realControlsFollowTheBridge() throws Exception {
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
            RXAtlantaFX.applyTo(scene);
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

        assertEquals(ACCENT_EMPHASIS, fill.get(),
                "RXFillButton fill -> -rx-primary -> AtlantaFX accent");
        assertEquals(Color.web("#24292f"), ripple.get(),
                "RXButton ripple -> -rx-state-overlay-color -> -color-fg-default");
        assertEquals(Color.web("#2da44e"), successDot.get(),
                "RXTimelineView success dot -> -rx-success -> -color-success-emphasis");
    }

    // ==================== Subtree scoping ====================

    /**
     * A parent-level bridge themes only its own subtree: an {@code RXFillButton}
     * inside follows AtlantaFX while a sibling outside keeps the built-in brand
     * color. Both are real controls (each carries the rx-controls.css UA baseline);
     * the bridge is an author stylesheet on the inner subtree only.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void parentLevelBridgeScopesToItsSubtree() throws Exception {
        AtomicReference<Color> inside = new AtomicReference<>();
        AtomicReference<Color> outside = new AtomicReference<>();

        runOnFx(() -> {
            RXFillButton insideButton = new RXFillButton("in");
            RXFillButton outsideButton = new RXFillButton("out");
            StackPane subtree = new StackPane(insideButton);
            StackPane root = new StackPane(subtree, outsideButton);
            Scene scene = new Scene(root, 320, 200);
            RXAtlantaFX.applyTo(subtree);
            root.applyCss();
            root.layout();
            inside.set(fillRegionColor(insideButton));
            outside.set(fillRegionColor(outsideButton));
        });

        assertEquals(ACCENT_EMPHASIS, inside.get(),
                "RXFillButton inside the bridged subtree follows AtlantaFX");
        assertEquals(Color.web("#616dff"), outside.get(),
                "RXFillButton outside the bridged subtree keeps the built-in brand color");
    }

    // ==================== Modena compat layer ====================

    /**
     * The bridge supplies the Modena base palette AtlantaFX omits, so each raw
     * {@code -fx-*} that RxControls component rules still reference resolves to its
     * AtlantaFX counterpart instead of failing.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void modenaCompatLayerResolvesRawFxColors() throws Exception {
        Map<String, Region> probes = new LinkedHashMap<>();
        for (String var : COMPAT.keySet()) {
            probes.put(var, probeFx(var));
        }

        runOnFx(() -> {
            StackPane host = new StackPane();
            host.getChildren().addAll(probes.values());
            Scene scene = new Scene(host, 200, 200);
            RXAtlantaFX.applyTo(scene);
            host.applyCss();
            host.layout();
        });

        COMPAT.forEach((var, expected) ->
                assertEquals(expected, background(probes.get(var)),
                        var + " should resolve to its AtlantaFX counterpart under the bridge"));
    }

    /**
     * The cascader — whose display rules still use raw {@code -fx-*} — logs CSS
     * resolution failures under a bare AtlantaFX UA; with the bridge applied the
     * compat layer resolves them, so no rx-controls.css conversion failure is
     * logged (the demo's invisible-cascader regression).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void cascaderDisplayResolvesUnderTheBridge() throws Exception {
        List<String> messages = captureCss(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("a"));
            StackPane host = new StackPane(cascader);
            Scene scene = new Scene(host, 320, 120);
            RXAtlantaFX.applyTo(scene);
            host.applyCss();
            host.layout();
        });

        List<String> failures = rxControlsResolutionFailures(messages);
        assertTrue(failures.isEmpty(),
                "rx-controls.css still has unresolved colors under the bridge (compat layer "
                        + "is missing a -fx-* var):\n" + String.join("\n", failures));
    }

    // ==================== Helpers ====================

    private static Region probe(String token) {
        Region region = new Region();
        region.getStyleClass().add("rx-button"); // a covered control root
        region.setStyle("-fx-background-color: -rx-" + token + ";");
        return region;
    }

    private static Region referenceProbe(String paint) {
        Region region = new Region();
        region.setStyle("-fx-background-color: " + paint + ";");
        return region;
    }

    private static Region probeFx(String var) {
        Region region = new Region();
        region.getStyleClass().add("rx-button"); // a covered control root
        region.setStyle("-fx-background-color: " + var + ";");
        return region;
    }

    private static List<String> rxControlsResolutionFailures(List<String> messages) {
        List<String> out = new ArrayList<>();
        for (String message : messages) {
            boolean failure = message.contains("while converting value for")
                    || message.contains("while resolving lookups for")
                    || message.contains("Could not resolve");
            if (failure && message.contains("rx-controls.css")) {
                out.add(message);
            }
        }
        return out;
    }

    private static List<String> captureCss(Runnable fxBody) throws Exception {
        CollectingHandler handler = new CollectingHandler();
        handler.setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        root.addHandler(handler);
        try {
            runOnFx(fxBody);
        } finally {
            root.removeHandler(handler);
        }
        return handler.messages;
    }

    private static final class CollectingHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null && record.getMessage() != null) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
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

    private static Set<String> controlRootSelectors(String css) {
        // Strip comments, then locate the rule the token layer DEFINES (the one
        // whose body assigns -rx-primary) and harvest its .rx-* selectors. Matches
        // the position-independent approach used by RxControlsThemeBaselineTest.
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
        try (InputStream in = RXAtlantaFXBridgeTest.class.getResourceAsStream(path)) {
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
