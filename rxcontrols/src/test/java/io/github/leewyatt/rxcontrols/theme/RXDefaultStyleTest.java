package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default style under Modena: every role token keeps its original value on the
 * default palette, and the roles follow an application that recolors the Modena
 * palette, either for the whole scene or for one container. The palettes are fixed
 * in {@code app-palettes.css} so the expectations do not depend on Modena's own
 * derivations.
 */
public class RXDefaultStyleTest {

    private static final String PALETTES =
            RXDefaultStyleTest.class.getResource("app-palettes.css").toExternalForm();

    private static final String DARK_ROOT =
            RXDefaultStyleTest.class.getResource("app-dark-root.css").toExternalForm();

    private static final String DARK_PALETTE = "dark-palette";
    private static final String DARK_REGION = "dark-region";
    private static final String LIGHT_REGION = "light-region";

    private static final double WCAG_AA_CONTRAST = 4.5;

    /** Every role token resolved under default Modena; the default look must not change. */
    private static final Map<String, Color> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("primary", Color.web("#616dff"));
        DEFAULTS.put("primary-hover", Color.web("#6b77ff"));
        DEFAULTS.put("primary-active", Color.web("#5763e5"));
        DEFAULTS.put("primary-bg", Color.web("#ccd1ff"));
        DEFAULTS.put("on-primary", Color.web("#ffffff"));
        DEFAULTS.put("success", Color.web("#67c23a"));
        DEFAULTS.put("warning", Color.web("#e6a23c"));
        DEFAULTS.put("danger", Color.web("#f56c6c"));
        DEFAULTS.put("info", Color.web("#909399"));
        DEFAULTS.put("surface", Color.web("#f4f4f4"));
        DEFAULTS.put("surface-variant", Color.web("#f5f5f5"));
        DEFAULTS.put("on-surface", Color.web("#333333"));
        DEFAULTS.put("on-surface-secondary", Color.web("#333333"));
        DEFAULTS.put("on-surface-disabled", Color.rgb(0, 0, 0, 0.25));
        DEFAULTS.put("outline", Color.web("#cfcfcf"));
        DEFAULTS.put("outline-variant", Color.web("#c0c4cc"));
        DEFAULTS.put("focus", Color.web("#039ed3"));
        DEFAULTS.put("selection", Color.rgb(0, 120, 215, 0.3));
        DEFAULTS.put("highlight", Color.web("#ffff00"));
        DEFAULTS.put("state-overlay-color", Color.web("#000000"));
        DEFAULTS.put("elevation-umbra-color", Color.rgb(0, 0, 0, 0.2));
    }

    /**
     * Roles that follow the palette, each with the Modena expression (or literal) it
     * must resolve to on a dark palette. Resolved on a plain region in the same scope,
     * so the expectation is JavaFX's own result for that expression.
     */
    private static final Map<String, String> DARK_REFERENCES = new LinkedHashMap<>();

    static {
        DARK_REFERENCES.put("primary-bg", "derive(#616dff, -60%)");
        DARK_REFERENCES.put("surface", "-fx-background");
        DARK_REFERENCES.put("surface-variant", "derive(-fx-background, 8%)");
        DARK_REFERENCES.put("on-surface", "-fx-text-background-color");
        DARK_REFERENCES.put("on-surface-secondary", "derive(-fx-light-text-color, -32%)");
        DARK_REFERENCES.put("on-surface-disabled", "rgba(255, 255, 255, 0.35)");
        DARK_REFERENCES.put("outline", "-fx-text-box-border");
        DARK_REFERENCES.put("outline-variant", "derive(-fx-background, 25%)");
        DARK_REFERENCES.put("focus", "-fx-focus-color");
        DARK_REFERENCES.put("highlight", "rgba(230, 162, 60, 0.4)");
        DARK_REFERENCES.put("state-overlay-color", "white");
    }

    /** Held strongly: the log manager keeps loggers weakly, and a fresh one would drop the handler. */
    private static final Logger CSS_LOGGER = Logger.getLogger("javafx.css");

    private final List<LogRecord> cssWarnings = new CopyOnWriteArrayList<>();
    private final Handler cssWarningHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                cssWarnings.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    /**
     * Starts the toolkit and pins Modena. The probes are panes, not controls, so
     * nothing else would load the platform stylesheet.
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

    /** Collects CSS warnings logged while a test runs. */
    @BeforeEach
    public void captureCssWarnings() {
        cssWarnings.clear();
        CSS_LOGGER.addHandler(cssWarningHandler);
    }

    /** Stops collecting CSS warnings. */
    @AfterEach
    public void releaseCssWarnings() {
        CSS_LOGGER.removeHandler(cssWarningHandler);
    }

    // ==================== Default palette ====================

    /**
     * Under default Modena every role token keeps its original value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void defaultPaletteKeepsTheOriginalValues() throws Exception {
        Map<String, Region> probes = roleProbes(DEFAULTS.keySet());
        runOnFx(() -> applyCss(scene(new StackPane(probes.values().toArray(new Node[0])), false)));

        DEFAULTS.forEach((token, expected) ->
                assertSameColor(expected, background(probes.get(token)), "-rx-" + token + " under default Modena"));
        assertNoCssWarnings();
    }

    // ==================== Whole-scene palette ====================

    /**
     * With a dark Modena palette on the scene root every palette-derived role takes
     * its dark value, and nothing fails to resolve.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void everyRoleFollowsADarkScenePalette() throws Exception {
        Map<String, Region> probes = roleProbes(DARK_REFERENCES.keySet());
        Map<String, Region> references = referenceProbes(DARK_REFERENCES);
        runOnFx(() -> {
            StackPane root = new StackPane(probes.values().toArray(new Node[0]));
            root.getChildren().addAll(references.values());
            applyCss(scene(root, true));
        });

        assertRolesMatchReferences(probes, references);
        DARK_REFERENCES.keySet().forEach(token ->
                assertNotEquals(hex(DEFAULTS.get(token)), hex(background(probes.get(token))),
                        "-rx-" + token + " must change on a dark palette"));
        assertLighter(background(probes.get("on-surface")), background(probes.get("surface")),
                "-rx-on-surface on -rx-surface");
        assertNoCssWarnings();
    }

    /**
     * The brand and semantic colors do not follow the palette.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void brandAndSemanticColorsStayOnADarkPalette() throws Exception {
        List<String> fixed = List.of("primary", "on-primary", "success", "warning", "danger", "info",
                "selection", "elevation-umbra-color");
        Map<String, Region> probes = roleProbes(fixed);
        runOnFx(() -> applyCss(scene(new StackPane(probes.values().toArray(new Node[0])), true)));

        fixed.forEach(token ->
                assertSameColor(DEFAULTS.get(token), background(probes.get(token)), "-rx-" + token + " is fixed"));
    }

    // ==================== Container palette ====================

    /**
     * A dark palette set on one container recolors the roles and a standard control
     * inside it, and leaves the controls outside unchanged.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkContainerInALightSceneRecolorsOnlyItsContent() throws Exception {
        Map<String, Region> inside = roleProbes(DARK_REFERENCES.keySet());
        Map<String, Region> references = referenceProbes(DARK_REFERENCES);
        Map<String, Region> outside = roleProbes(DARK_REFERENCES.keySet());
        CheckBox standardInside = new CheckBox("inside");
        CheckBox standardOutside = new CheckBox("outside");
        RXCheckBox rxInside = new RXCheckBox("inside");
        runOnFx(() -> {
            StackPane region = new StackPane(inside.values().toArray(new Node[0]));
            region.getChildren().addAll(references.values());
            region.getChildren().addAll(standardInside, rxInside);
            region.getStyleClass().add(DARK_REGION);
            StackPane root = new StackPane(region, standardOutside);
            root.getChildren().addAll(outside.values());
            applyCss(scene(root, false));
        });

        assertRolesMatchReferences(inside, references);
        DARK_REFERENCES.keySet().forEach(token ->
                assertSameColor(DEFAULTS.get(token), background(outside.get(token)),
                        "-rx-" + token + " outside the dark container keeps its default"));
        assertEquals(background(inside.get("on-surface")), standardInside.getTextFill(),
                "a standard control inside the container agrees with -rx-on-surface");
        assertEquals(standardInside.getTextFill(), rxInside.getTextFill(),
                "RXCheckBox inside the container agrees with the standard check box");
        assertEquals(DEFAULTS.get("on-surface"), standardOutside.getTextFill(),
                "a standard control outside the container keeps the default text");
        assertNoCssWarnings();
    }

    /**
     * A light palette set on one container inside a dark scene brings back the
     * default values there.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void lightContainerInADarkSceneRestoresTheDefaults() throws Exception {
        Map<String, Region> inside = roleProbes(DARK_REFERENCES.keySet());
        CheckBox standardInside = new CheckBox("inside");
        runOnFx(() -> {
            StackPane region = new StackPane(inside.values().toArray(new Node[0]));
            region.getChildren().add(standardInside);
            region.getStyleClass().add(LIGHT_REGION);
            applyCss(scene(new StackPane(region), true));
        });

        DARK_REFERENCES.keySet().forEach(token ->
                assertSameColor(DEFAULTS.get(token), background(inside.get(token)),
                        "-rx-" + token + " inside the light container"));
        assertEquals(DEFAULTS.get("on-surface"), standardInside.getTextFill(),
                "a standard control inside the light container");
        assertNoCssWarnings();
    }

    /**
     * Where Modena itself sets a local {@code -fx-background} (a focused selected cell
     * uses the selection bar), RxControls agree with a standard label in the same
     * place, and keep the defaults next to it.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void controlsInALocalModenaBackgroundAgreeWithAStandardLabel() throws Exception {
        Label standardInside = new Label("inside");
        RXTextView rxInside = new RXTextView("inside");
        Label standardOutside = new Label("outside");
        RXTextView rxOutside = new RXTextView("outside");
        runOnFx(() -> {
            StackPane selected = new StackPane(standardInside, rxInside);
            selected.setStyle("-fx-background: -fx-selection-bar;");
            applyCss(scene(new StackPane(selected, standardOutside, rxOutside), false));
        });

        assertEquals(standardInside.getTextFill(), rxInside.getTextFill(),
                "RXTextView on the selection bar agrees with a standard label");
        assertEquals(standardOutside.getTextFill(), rxOutside.getTextFill(),
                "RXTextView outside agrees with a standard label");
        assertNotEquals(rxOutside.getTextFill(), rxInside.getTextFill(), "the selection bar flips the text");
        assertNoCssWarnings();
    }

    // ==================== Real controls on a dark palette ====================

    /**
     * Real controls on a dark palette: the fill button keeps the brand fill, the
     * ripple turns white, and the timeline success dot keeps the semantic color.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void realControlsFollowADarkPalette() throws Exception {
        AtomicReference<Color> fill = new AtomicReference<>();
        AtomicReference<Paint> ripple = new AtomicReference<>();
        AtomicReference<Color> successDot = new AtomicReference<>();
        runOnFx(() -> {
            RXFillButton fillButton = new RXFillButton("Go");
            RXButton plain = new RXButton("Go");
            RXTimelineItem done = new RXTimelineItem("Done");
            done.setType(RXTimelineItem.Type.SUCCESS);
            RXTimelineView timeline = new RXTimelineView(done);
            applyCss(scene(new StackPane(fillButton, plain, timeline), true));

            fill.set(background(lookupRegion(fillButton, ".fill-region")));
            ripple.set(plain.getRippleFill());
            successDot.set(background(lookupRegion(timeline, ".dot")));
        });

        assertEquals(DEFAULTS.get("primary"), fill.get(), "RXFillButton fill keeps -rx-primary");
        assertEquals(Color.WHITE, ripple.get(), "RXButton ripple follows -rx-state-overlay-color");
        assertEquals(DEFAULTS.get("success"), successDot.get(), "timeline success dot keeps -rx-success");
        assertNoCssWarnings();
    }

    /**
     * Text views and toggle labels turn light on a dark palette.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void textAndToggleLabelsTurnLightOnADarkPalette() throws Exception {
        Map<String, Paint> textFills = new LinkedHashMap<>();
        runOnFx(() -> {
            List<Labeled> toggles = List.of(
                    new RXCheckBox("check"), new RXRadioButton("radio"), new RXSwitchButton("switch"));
            RXTextView textView = new RXTextView("hello");
            StackPane root = new StackPane(toggles.toArray(new Node[0]));
            root.getChildren().add(textView);
            applyCss(scene(root, true));
            for (Labeled toggle : toggles) {
                textFills.put(toggle.getClass().getSimpleName(), toggle.getTextFill());
            }
            textFills.put("RXTextView", textView.getTextFill());
        });

        assertEquals(4, textFills.size());
        textFills.forEach((name, textFill) -> assertEquals(Color.WHITE, textFill, name + " text on a dark palette"));
        assertNoCssWarnings();
    }

    /**
     * The highlight, skeleton and timeline connector follow their roles on a dark palette.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void highlightSkeletonAndConnectorFollowADarkPalette() throws Exception {
        Map<String, Paint> fills = new LinkedHashMap<>();
        Region surfaceVariant = referenceProbe("derive(-fx-background, 8%)");
        Region outline = referenceProbe("-fx-text-box-border");
        runOnFx(() -> {
            RXHighlightTextView highlight = new RXHighlightTextView("hello");
            RXSkeleton skeleton = new RXSkeleton();
            RXTimelineView timeline = new RXTimelineView(new RXTimelineItem("First"), new RXTimelineItem("Second"));
            applyCss(scene(new StackPane(highlight, skeleton, timeline, surfaceVariant, outline), true));
            fills.put("highlight", highlight.getHighlightFill());
            fills.put("skeleton", skeleton.getBaseColor());
            fills.put("connector", background(lookupRegion(timeline, ".connector")));
        });

        assertEquals(Color.rgb(230, 162, 60, 0.4), fills.get("highlight"), "highlight -> -rx-highlight");
        assertEquals(background(surfaceVariant), fills.get("skeleton"), "skeleton base -> -rx-surface-variant");
        assertEquals(background(outline), fills.get("connector"), "timeline connector -> -rx-outline");
        assertNoCssWarnings();
    }

    /**
     * A selected sidebar item stays legible on a dark palette. Measures perceived
     * contrast rather than a literal fill, so any expression that makes it unreadable fails.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void selectedSidebarItemStaysLegibleOnADarkPalette() throws Exception {
        AtomicReference<Color> rail = new AtomicReference<>();
        AtomicReference<Color> selectedFill = new AtomicReference<>();
        AtomicReference<Paint> textFill = new AtomicReference<>();
        runOnFx(() -> {
            RXSidebarNavItem selected = new RXSidebarNavItem("Inbox", new Region());
            RXSidebar sidebar = new RXSidebar();
            sidebar.getItems().addAll(selected, new RXSidebarNavItem("Files", new Region()));
            sidebar.selectItem(selected);
            applyCss(scene(new StackPane(sidebar), true));
            rail.set(background(sidebar));
            selectedFill.set(background(selected));
            textFill.set(selected.getTextFill());
        });

        assertNotNull(selectedFill.get(), "selected sidebar item has no background fill");
        Color effective = composite(selectedFill.get(), rail.get());
        double ratio = contrastRatio((Color) textFill.get(), effective);
        assertTrue(ratio >= WCAG_AA_CONTRAST, "selected sidebar item text " + textFill.get() + " on " + effective
                + " = " + String.format("%.2f", ratio) + ":1, need >= " + WCAG_AA_CONTRAST + ":1");
    }

    /**
     * A palette on the unqualified {@code .root} also reaches a SubScene: the sub-scene
     * root carries the {@code root} style class too.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void controlsInsideASubSceneFollowAPaletteOnTheUnqualifiedRoot() throws Exception {
        AtomicReference<Paint> textFill = new AtomicReference<>();
        runOnFx(() -> {
            RXTextView inside = new RXTextView("inside");
            StackPane subRoot = new StackPane(inside);
            StackPane root = new StackPane(new SubScene(subRoot, 200, 80));
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(DARK_ROOT);
            applyCss(scene);
            subRoot.applyCss();
            textFill.set(inside.getTextFill());
        });

        assertEquals(Color.WHITE, textFill.get(), "a control inside a SubScene follows a .root palette");
    }

    /**
     * A palette keyed on a style class reaches a SubScene only once the sub-scene root
     * carries that class: a sub-scene root does not inherit looked-up colors from the
     * scene root.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void controlsInsideASubSceneFollowAPaletteOnTheSubSceneRoot() throws Exception {
        AtomicReference<Paint> textFill = new AtomicReference<>();
        runOnFx(() -> {
            RXTextView inside = new RXTextView("inside");
            StackPane subRoot = new StackPane(inside);
            subRoot.getStyleClass().add(DARK_REGION);
            SubScene subScene = new SubScene(subRoot, 200, 80);
            StackPane root = new StackPane(subScene);
            applyCss(scene(root, true));
            subRoot.applyCss();
            textFill.set(inside.getTextFill());
        });

        assertEquals(Color.WHITE, textFill.get(), "a control inside a SubScene follows the palette");
    }

    // ==================== Colors set in code ====================

    /**
     * Colors set in code on controls that are alone in their scene survive a dark palette.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void loneControlsKeepColorsSetInCodeOnADarkPalette() throws Exception {
        Map<String, Paint> fills = new LinkedHashMap<>();
        runOnFx(() -> {
            RXTextView textView = new RXTextView("hello");
            textView.setTextFill(Color.RED);
            textView.setSelectedTextFill(Color.BLUE);
            RXSkeleton skeleton = new RXSkeleton();
            skeleton.setBaseColor(Color.RED);
            for (Parent content : List.of(new StackPane(textView), new StackPane(skeleton))) {
                applyCss(scene(content, true));
            }
            fills.put("text", textView.getTextFill());
            fills.put("selected text", textView.getSelectedTextFill());
            fills.put("skeleton base", skeleton.getBaseColor());
        });

        assertEquals(Color.RED, fills.get("text"), "text fill set in code");
        assertEquals(Color.BLUE, fills.get("selected text"), "selected text fill set in code");
        assertEquals(Color.RED, fills.get("skeleton base"), "skeleton base color set in code");
    }

    /**
     * A text color set in code next to a default sibling of the same kind behaves
     * exactly like it does on a standard check box. With the palette in an author
     * stylesheet, JavaFX 17-23 may reset such a color (JDK-8336097, fixed in 24);
     * RxControls must not be worse than the platform either way.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void codeSetTextFillNextToADefaultSiblingMatchesAStandardCheckBox() throws Exception {
        AtomicReference<Paint> rxFill = new AtomicReference<>();
        AtomicReference<Paint> standardFill = new AtomicReference<>();
        runOnFx(() -> {
            RXCheckBox rxPlain = new RXCheckBox("plain");
            RXCheckBox rxCoded = new RXCheckBox("coded");
            rxCoded.setTextFill(Color.RED);
            applyCss(scene(new StackPane(rxPlain, rxCoded), true));
            CheckBox plain = new CheckBox("plain");
            CheckBox coded = new CheckBox("coded");
            coded.setTextFill(Color.RED);
            applyCss(scene(new StackPane(plain, coded), true));
            rxFill.set(rxCoded.getTextFill());
            standardFill.set(coded.getTextFill());
        });

        assertEquals(standardFill.get(), rxFill.get(),
                "RXCheckBox keeps or loses a code-set text fill exactly like CheckBox");
    }

    // ==================== Helpers ====================

    private static Scene scene(Parent root, boolean darkPalette) {
        if (darkPalette) {
            root.getStyleClass().add(DARK_PALETTE);
        }
        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(PALETTES);
        return scene;
    }

    private static void applyCss(Scene scene) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    /** A real control root: the role tokens live in the control user-agent stylesheet. */
    private static Map<String, Region> roleProbes(Iterable<String> tokens) {
        Map<String, Region> probes = new LinkedHashMap<>();
        for (String token : tokens) {
            RXBox box = new RXBox();
            box.setStyle("-fx-background-color: -rx-" + token + ";");
            probes.put(token, box);
        }
        return probes;
    }

    private static Map<String, Region> referenceProbes(Map<String, String> expressions) {
        Map<String, Region> probes = new LinkedHashMap<>();
        expressions.forEach((token, expression) -> probes.put(token, referenceProbe(expression)));
        return probes;
    }

    /** A plain region: resolves the expression against Modena and the palette only. */
    private static Region referenceProbe(String expression) {
        Region region = new Region();
        region.setStyle("-fx-background-color: " + expression + ";");
        return region;
    }

    private void assertRolesMatchReferences(Map<String, Region> probes, Map<String, Region> references) {
        references.forEach((token, reference) -> {
            Color expected = background(reference);
            assertNotNull(expected, "reference for -rx-" + token + " did not resolve");
            assertSameColor(expected, background(probes.get(token)), "-rx-" + token + " on a dark palette");
        });
    }

    /** Compares at 8 bits per channel: derive() results may differ from a literal below 1/255. */
    private static void assertSameColor(Color expected, Color actual, String what) {
        assertNotNull(actual, what + " did not resolve");
        assertEquals(hex(expected), hex(actual), what);
    }

    private static String hex(Color color) {
        if (color == null) {
            return "null";
        }
        return String.format("#%02x%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255), Math.round(color.getOpacity() * 255));
    }

    private void assertNoCssWarnings() {
        assertTrue(cssWarnings.isEmpty(), "CSS warnings: " + cssWarnings.stream().map(LogRecord::getMessage).toList());
    }

    private static void assertLighter(Color foreground, Color background, String what) {
        assertTrue(foreground.getBrightness() > background.getBrightness(),
                what + ": " + foreground + " must be lighter than " + background);
    }

    private static Region lookupRegion(Node control, String selector) {
        Node node = control.lookup(selector);
        assertNotNull(node, selector + " not found");
        return (Region) node;
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
