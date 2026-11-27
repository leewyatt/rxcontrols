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
import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.RXColorView;
import io.github.leewyatt.rxcontrols.RXDatePicker;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXMenuList;
import io.github.leewyatt.rxcontrols.RXOverlayPane;
import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSelectionBox;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        DEFAULT_MODENA.put("segmented.selected", "#ffffffff");
        DEFAULT_MODENA.put("segmented.unselected", "#000000a6");
        DEFAULT_MODENA.put("segmented.indicator", "#0096c9ff");
        DEFAULT_MODENA.put("backdrop", "#00000052");
        DEFAULT_MODENA.put("dialog.scrim", "#00000052");
        DEFAULT_MODENA.put("drawer.scrim", "#00000052");
        DEFAULT_MODENA.put("state-pane.scrim", "#00000052");
        DEFAULT_MODENA.put("seek.track", "#e0e0e0ff");
        DEFAULT_MODENA.put("seek.secondary", "#00000040");
        DEFAULT_MODENA.put("seek.hover", "#868686ff");
        DEFAULT_MODENA.put("seek.bar", "#0096c9ff");
        DEFAULT_MODENA.put("seek.thumb", "#0096c9ff");
        DEFAULT_MODENA.put("slider.track", "#e0e0e0ff");
        DEFAULT_MODENA.put("slider.indicator", "#424242ff");
        DEFAULT_MODENA.put("slider.indicator.text", "#ffffffff");
        DEFAULT_MODENA.put("slider.indicator.caret", "#424242ff");
        DEFAULT_MODENA.put("range-slider.indicator", "#424242ff");
        DEFAULT_MODENA.put("range-slider.indicator.text", "#ffffffff");
        DEFAULT_MODENA.put("slider.tick-label", "#333333ff");
        DEFAULT_MODENA.put("range-slider.tick-label", "#333333ff");
        DEFAULT_MODENA.put("wave.container", "#e8f4ffff");
        DEFAULT_MODENA.put("wave.front", "#0096c9ff");
        DEFAULT_MODENA.put("wave.ring", "#0096c9ff");
        DEFAULT_MODENA.put("wave.label", "#0096c9ff");
        DEFAULT_MODENA.put("wave.label-below", "#ffffffff");
        DEFAULT_MODENA.put("circular.track", "#81cee7ff");
        DEFAULT_MODENA.put("circular.progress", "#0096c9ff");
        DEFAULT_MODENA.put("circular.completed", "#67c23aff");
        DEFAULT_MODENA.put("avatar.icon-disc", "#757575ff");
        DEFAULT_MODENA.put("avatar.icon", "#ffffffff");
        DEFAULT_MODENA.put("avatar.text-disc", "#757575ff");
        DEFAULT_MODENA.put("avatar.text", "#ffffffff");
        DEFAULT_MODENA.put("avatar.ring", "#0000001a");
        DEFAULT_MODENA.put("snackbar.bar", "#323232ff");
        DEFAULT_MODENA.put("snackbar.message", "#ffffffde");
        DEFAULT_MODENA.put("snackbar.action", "#83d0e7ff");
        DEFAULT_MODENA.put("snackbar.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("snackbar.info.bar", "#909399ff");
        DEFAULT_MODENA.put("snackbar.info.message", "#ffffffff");
        DEFAULT_MODENA.put("snackbar.info.close-icon", "#ffffffb2");
        DEFAULT_MODENA.put("progress-bar.track", "#cfcfcfff");
        DEFAULT_MODENA.put("progress-bar.completed", "#67c23aff");
        DEFAULT_MODENA.put("step-indicator.track", "#cfcfcfff");
        DEFAULT_MODENA.put("carousel-navigator.track", "#ffffff59");
        DEFAULT_MODENA.put("digit.lit", "#333333ff");
        DEFAULT_MODENA.put("digit.unlit", "null");
        DEFAULT_MODENA.put("fill-button.filling-armed.text", "#ffffffff");
        DEFAULT_MODENA.put("sidebar.background", "#ecececff");
        DEFAULT_MODENA.put("sidebar.selected", "#d7f0f8ff");
        DEFAULT_MODENA.put("sidebar.selected.text", "#333333ff");
        DEFAULT_MODENA.put("timeline.dot", "#0096c9ff");
        DEFAULT_MODENA.put("timeline.primary-dot", "#0096c9ff");
        DEFAULT_MODENA.put("timeline.hollow-dot.border", "#0096c9ff");
        DEFAULT_MODENA.put("list-view.background", "#c9c9c9ff #f4f4f4ff");
        DEFAULT_MODENA.put("tile-view.background", "#c9c9c9ff #f4f4f4ff");
        DEFAULT_MODENA.put("masonry-view.background", "#c9c9c9ff #f4f4f4ff");
    }

    static {
        PRIMER_LIGHT.put("cascader.prompt", "#57606aff");
        PRIMER_LIGHT.put("cascader.column.border", "#d0d7deff");
        PRIMER_LIGHT.put("cascader.cell.hover", "#f6f8faff");
        PRIMER_LIGHT.put("segmented.selected", "#ffffffff");
        PRIMER_LIGHT.put("segmented.unselected", "#57606aff");
        PRIMER_LIGHT.put("segmented.indicator", "#0969daff");
        PRIMER_LIGHT.put("backdrop", "#00000052");
        PRIMER_LIGHT.put("dialog.scrim", "#00000052");
        PRIMER_LIGHT.put("drawer.scrim", "#00000052");
        PRIMER_LIGHT.put("state-pane.scrim", "#00000052");
        PRIMER_LIGHT.put("seek.track", "#b5bbc3ff");
        PRIMER_LIGHT.put("seek.secondary", "#6e7781ff");
        PRIMER_LIGHT.put("seek.hover", "#5e656eff");
        PRIMER_LIGHT.put("seek.bar", "#0969daff");
        PRIMER_LIGHT.put("seek.thumb", "#0969daff");
        PRIMER_LIGHT.put("slider.track", "#d0d7deff");
        PRIMER_LIGHT.put("slider.indicator", "#424242ff");
        PRIMER_LIGHT.put("slider.indicator.text", "#ffffffff");
        PRIMER_LIGHT.put("slider.indicator.caret", "#424242ff");
        PRIMER_LIGHT.put("range-slider.indicator", "#424242ff");
        PRIMER_LIGHT.put("range-slider.indicator.text", "#ffffffff");
        PRIMER_LIGHT.put("slider.tick-label", "#57606aff");
        PRIMER_LIGHT.put("range-slider.tick-label", "#57606aff");
        PRIMER_LIGHT.put("wave.container", "#f6f8faff");
        PRIMER_LIGHT.put("wave.front", "#0969daff");
        PRIMER_LIGHT.put("wave.ring", "#0969daff");
        PRIMER_LIGHT.put("wave.label", "#0969daff");
        PRIMER_LIGHT.put("wave.label-below", "#ffffffff");
        PRIMER_LIGHT.put("circular.track", "#54aeff66");
        PRIMER_LIGHT.put("circular.progress", "#0969daff");
        PRIMER_LIGHT.put("circular.completed", "#2da44eff");
        PRIMER_LIGHT.put("avatar.icon-disc", "#6e7781ff");
        PRIMER_LIGHT.put("avatar.icon", "#ffffffff");
        PRIMER_LIGHT.put("avatar.text-disc", "#6e7781ff");
        PRIMER_LIGHT.put("avatar.text", "#ffffffff");
        PRIMER_LIGHT.put("avatar.ring", "#0000001a");
        PRIMER_LIGHT.put("snackbar.bar", "#323232ff");
        PRIMER_LIGHT.put("snackbar.message", "#ffffffde");
        PRIMER_LIGHT.put("snackbar.action", "#85b8eeff");
        PRIMER_LIGHT.put("snackbar.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("snackbar.info.bar", "#6e7781ff");
        PRIMER_LIGHT.put("snackbar.info.message", "#ffffffff");
        PRIMER_LIGHT.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_LIGHT.put("progress-bar.track", "#d0d7deff");
        PRIMER_LIGHT.put("progress-bar.completed", "#2da44eff");
        PRIMER_LIGHT.put("step-indicator.track", "#d0d7deff");
        PRIMER_LIGHT.put("carousel-navigator.track", "#ffffff59");
        PRIMER_LIGHT.put("digit.lit", "#24292fff");
        PRIMER_LIGHT.put("digit.unlit", "null");
        PRIMER_LIGHT.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_LIGHT.put("sidebar.background", "#f6f8faff");
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
        PRIMER_DARK.put("segmented.selected", "#ffffffff");
        PRIMER_DARK.put("segmented.unselected", "#8b949eff");
        PRIMER_DARK.put("segmented.indicator", "#1f6febff");
        PRIMER_DARK.put("backdrop", "#ffffff33");
        PRIMER_DARK.put("dialog.scrim", "#ffffff33");
        PRIMER_DARK.put("drawer.scrim", "#ffffff33");
        PRIMER_DARK.put("state-pane.scrim", "#ffffff33");
        PRIMER_DARK.put("seek.track", "#535961ff");
        PRIMER_DARK.put("seek.secondary", "#6e7681ff");
        PRIMER_DARK.put("seek.hover", "#8d959fff");
        PRIMER_DARK.put("seek.bar", "#1f6febff");
        PRIMER_DARK.put("seek.thumb", "#1f6febff");
        PRIMER_DARK.put("slider.track", "#30363dff");
        PRIMER_DARK.put("slider.indicator", "#e0e0e0ff");
        PRIMER_DARK.put("slider.indicator.text", "#000000de");
        PRIMER_DARK.put("slider.indicator.caret", "#e0e0e0ff");
        PRIMER_DARK.put("range-slider.indicator", "#e0e0e0ff");
        PRIMER_DARK.put("range-slider.indicator.text", "#000000de");
        PRIMER_DARK.put("slider.tick-label", "#8b949eff");
        PRIMER_DARK.put("range-slider.tick-label", "#8b949eff");
        PRIMER_DARK.put("wave.container", "#161b22ff");
        PRIMER_DARK.put("wave.front", "#1f6febff");
        PRIMER_DARK.put("wave.ring", "#1f6febff");
        PRIMER_DARK.put("wave.label", "#1f6febff");
        PRIMER_DARK.put("wave.label-below", "#ffffffff");
        PRIMER_DARK.put("circular.track", "#388bfd66");
        PRIMER_DARK.put("circular.progress", "#1f6febff");
        PRIMER_DARK.put("circular.completed", "#238636ff");
        PRIMER_DARK.put("avatar.icon-disc", "#6e7681ff");
        PRIMER_DARK.put("avatar.icon", "#ffffffff");
        PRIMER_DARK.put("avatar.text-disc", "#6e7681ff");
        PRIMER_DARK.put("avatar.text", "#ffffffff");
        PRIMER_DARK.put("avatar.ring", "#ffffff26");
        // The snackbar is an inverse surface, so it turns light on a dark AtlantaFX theme.
        PRIMER_DARK.put("snackbar.bar", "#e0e0e0ff");
        PRIMER_DARK.put("snackbar.message", "#000000de");
        PRIMER_DARK.put("snackbar.action", "#13448dff");
        PRIMER_DARK.put("snackbar.close-icon", "#00000099");
        PRIMER_DARK.put("snackbar.info.bar", "#6e7681ff");
        PRIMER_DARK.put("snackbar.info.message", "#ffffffff");
        PRIMER_DARK.put("snackbar.info.close-icon", "#ffffffb2");
        PRIMER_DARK.put("progress-bar.track", "#30363dff");
        PRIMER_DARK.put("progress-bar.completed", "#238636ff");
        PRIMER_DARK.put("step-indicator.track", "#30363dff");
        PRIMER_DARK.put("carousel-navigator.track", "#ffffff59");
        PRIMER_DARK.put("digit.lit", "#c9d1d9ff");
        PRIMER_DARK.put("digit.unlit", "null");
        PRIMER_DARK.put("fill-button.filling-armed.text", "#ffffffff");
        PRIMER_DARK.put("sidebar.background", "#161b22ff");
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
    // Nord Dark's muted accent on its subtle surface measures 2.5.
    private static final double PILL_EDGE_CONTRAST = 2.0;
    // AtlantaFX muted text on its subtle surface; Nord Dark's own pair measures 3.78.
    private static final double MUTED_LABEL_CONTRAST = 3.0;
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
        assertEquals(roles.get("primary"), samples.get("segmented.indicator"), "segment selection pill");
        assertEquals(roles.get("on-primary"), samples.get("segmented.selected"), "selected segment label on the pill");
        assertEquals(roles.get("outline"), samples.get("seek.track"), "seek bar track");
        assertEquals(roles.get("on-surface-disabled"), samples.get("seek.secondary"), "seek bar buffer");
        assertEquals(roles.get("primary"), samples.get("seek.bar"), "seek bar progress");
        assertEquals(roles.get("primary"), samples.get("seek.thumb"), "seek bar thumb");
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
        assertEquals("null", samples.get("digit.unlit"), "unlit digit segment is not drawn");
        assertEquals(roles.get("on-primary"), samples.get("fill-button.filling-armed.text"), "filling fill button text");
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
     * The segmented selection pill takes the primary color with on-primary text and stands out
     * from its track, on default Modena, a dark application palette and every AtlantaFX theme.
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
        Map<String, String> roles = roles(userAgentStylesheet, themer);
        assertEquals(roles.get("primary"), samples.get("segmented.indicator"), "segment selection pill under " + host);
        assertEquals(roles.get("on-primary"), samples.get("segmented.selected"), "selected segment label under " + host);
        Color track = Color.web(roles.get("surface-variant"));
        Color pill = composite(Color.web(samples.get("segmented.indicator")), track);
        double edge = contrastRatio(pill, track);
        assertTrue(edge >= PILL_EDGE_CONTRAST, "segmented pill vs track " + edge + " under " + host);
    }

    /**
     * Under every AtlantaFX theme the unselected segment labels recede from the selected one
     * and stay readable on the track.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void segmentedUnselectedLabelsRecedeUnderAtlantaFX() throws Exception {
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            String stylesheet = theme.getUserAgentStylesheet();
            Map<String, String> samples = sampleUnder(stylesheet, RXAtlantaFXThemeBridge::install);
            Color track = Color.web(roles(stylesheet, RXAtlantaFXThemeBridge::install).get("surface-variant"));
            assertNotEquals(samples.get("segmented.selected"), samples.get("segmented.unselected"),
                    "segment labels under " + theme.getName());
            double ratio = contrastRatio(composite(Color.web(samples.get("segmented.unselected")), track), track);
            assertTrue(ratio >= MUTED_LABEL_CONTRAST, "unselected segment label " + ratio + " under " + theme.getName());
        }
    }

    // ==================== Drawer ====================

    /**
     * The drawer panel is a raised surface (the content surface on a light host, lighter than
     * the window on a dark one) with a hairline on the edge that faces the content, and only an
     * overlaying drawer casts a shadow, on default Modena, a dark application palette and under
     * the bridge.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void drawerIsARaisedSurfaceWithAContentEdge() throws Exception {
        assertDrawer("default Modena", Application.STYLESHEET_MODENA, scene -> { }, "-fx-control-inner-background");
        assertDrawer("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette,
                "-rx-surface-variant");
        assertDrawer("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                "-fx-control-inner-background");
        assertDrawer("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                "-rx-surface-variant");
    }

    private static void assertDrawer(String host, String userAgentStylesheet, Consumer<Scene> themer,
            String surfaceExpression) throws Exception {
        Map<String, String> expected = resolve(userAgentStylesheet, themer,
                List.of(surfaceExpression, "-rx-surface", "-rx-outline-variant"));
        Map<String, String> overlay = new LinkedHashMap<>();
        Map<String, String> push = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXDrawerPane drawer = new RXDrawerPane();
            drawer.setSide(Side.LEFT);
            drawer.setDrawerContent(new StackPane());
            StackPane root = new StackPane(drawer);
            themer.accept(new Scene(root, 400, 300));
            root.applyCss();
            sampleDrawer((Region) drawer.lookup(".drawer-wrapper"), overlay);
            drawer.setDrawerMode(RXDrawerPane.DrawerMode.PUSH);
            root.applyCss();
            sampleDrawer((Region) drawer.lookup(".drawer-wrapper"), push);
        });
        String surface = expected.get(surfaceExpression);
        String edge = expected.get("-rx-outline-variant") + " 0/1/0/0";
        assertEquals(surface, overlay.get("fill"), "drawer surface under " + host);
        assertEquals(surface, push.get("fill"), "pushing drawer surface under " + host);
        if (surfaceExpression.equals("-rx-surface-variant")) {
            double drawerLuminance = relativeLuminance(Color.web(surface));
            double windowLuminance = relativeLuminance(Color.web(expected.get("-rx-surface")));
            assertTrue(drawerLuminance > windowLuminance, "drawer is lighter than the window under " + host);
        }
        assertEquals(edge, overlay.get("border"), "overlay drawer edge faces the content under " + host);
        assertEquals(edge, push.get("border"), "pushing drawer edge faces the content under " + host);
        assertEquals("true", overlay.get("shadow"), "overlay drawer shadow under " + host);
        assertEquals("false", push.get("shadow"), "pushing drawer shadow under " + host);
        // A border stroke would count toward the insets and thicken the drawer.
        assertEquals("false", overlay.get("stroke"), "overlay drawer has no border under " + host);
        assertEquals("false", push.get("stroke"), "pushing drawer has no border under " + host);
    }

    // The surface is the top background layer; the hairline is the layer beneath it,
    // left showing on the side where the surface is inset.
    private static void sampleDrawer(Region wrapper, Map<String, String> into) {
        List<BackgroundFill> fills = wrapper.getBackground().getFills();
        BackgroundFill surface = fills.get(fills.size() - 1);
        into.put("fill", ThemeColorSamples.hex(surface.getFill()));
        into.put("shadow", String.valueOf(wrapper.getEffect() != null));
        Insets insets = surface.getInsets();
        into.put("border", fills.size() < 2 ? "none" : ThemeColorSamples.hex(fills.get(0).getFill()) + " "
                + (int) insets.getTop() + "/" + (int) insets.getRight() + "/"
                + (int) insets.getBottom() + "/" + (int) insets.getLeft());
        into.put("stroke", String.valueOf(wrapper.getBorder() != null && !wrapper.getBorder().getStrokes().isEmpty()));
    }

    // ==================== Dialog card ====================

    /**
     * The dialog card is a raised surface (the content surface on a light host, lighter than
     * the window on a dark one), outlined only on a dark host, and casts a translucent black
     * shadow, on default Modena, a dark application palette and under the bridge.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void kanbanCardIsARaisedSurface() throws Exception {
        assertKanbanCard("default Modena", Application.STYLESHEET_MODENA, scene -> { }, "-fx-control-inner-background");
        assertKanbanCard("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette,
                "derive(-rx-surface-variant, 8%)");
        assertKanbanCard("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                "-fx-control-inner-background");
        assertKanbanCard("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                "derive(-rx-surface-variant, 8%)");
    }

    private static void assertKanbanCard(String host, String userAgentStylesheet, Consumer<Scene> themer,
            String surfaceExpression) throws Exception {
        Map<String, String> expected = resolve(userAgentStylesheet, themer,
                List.of(surfaceExpression, "-rx-surface-variant"));
        Map<String, String> card = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXKanbanColumn<String> column = new RXKanbanColumn<>("TODO");
            column.getCards().add("card");
            RXKanbanView<String> board = new RXKanbanView<>();
            board.getColumns().add(column);
            StackPane root = new StackPane(board);
            themer.accept(new Scene(root, 400, 300));
            for (int i = 0; i < 4; i++) {
                root.applyCss();
                root.layout();
            }
            Region cell = (Region) board.lookup(".rx-kanban-card-cell");
            card.put("fill", ThemeColorSamples.hex(cell.getBackground().getFills().get(0).getFill()));
        });
        String surface = expected.get(surfaceExpression);
        String columnSurface = expected.get("-rx-surface-variant");
        assertEquals(surface, card.get("fill"), "kanban card surface under " + host);
        assertTrue(relativeLuminance(Color.web(surface)) > relativeLuminance(Color.web(columnSurface)),
                "kanban card is lighter than its column under " + host);
    }

    @Test
    public void kanbanBoardColumnsAndHeadersAreDistinctLayers() throws Exception {
        assertKanbanLayers("default Modena", Application.STYLESHEET_MODENA, scene -> { }, false);
        assertKanbanLayers("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertKanbanLayers("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                false);
        assertKanbanLayers("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                true);
    }

    private static void assertKanbanLayers(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean dark) throws Exception {
        String boardExpression = dark ? "-rx-surface" : "-fx-control-inner-background";
        String headerExpression = dark ? "derive(-rx-surface-variant, 4%)" : "derive(-rx-surface-variant, -4%)";
        Map<String, String> expected = resolve(userAgentStylesheet, themer, List.of("-fx-box-border",
                boardExpression, "-rx-surface-variant", "-rx-outline-variant", headerExpression,
                "-rx-primary", "-rx-primary-bg", "-rx-focus"));
        Map<String, String> actual = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXKanbanColumn<String> column = new RXKanbanColumn<>("TODO");
            column.getCards().add("card");
            FocusableBoard board = new FocusableBoard();
            board.getColumns().add(column);
            StackPane root = new StackPane(board);
            themer.accept(new Scene(root, 400, 300));
            for (int i = 0; i < 4; i++) {
                root.applyCss();
                root.layout();
            }
            sampleLayers("board", board, actual);
            sampleLayers("column", (Region) board.lookup(".column"), actual);
            sampleLayers("header", (Region) board.lookup(".column > .header"), actual);

            Region columnNode = (Region) board.lookup(".column");
            Insets columnInsets = columnNode.getInsets();
            columnNode.pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-target"), true);
            root.applyCss();
            sampleLayers("dropTarget", columnNode, actual);
            actual.put("dropTargetShift", String.valueOf(!columnNode.getInsets().equals(columnInsets)));
            columnNode.pseudoClassStateChanged(PseudoClass.getPseudoClass("drop-target"), false);
            root.applyCss();

            Region cell = (Region) board.lookup(".rx-kanban-card-cell");
            Insets restingInsets = cell.getInsets();
            String restingFill = ThemeColorSamples.hex(cell.getBackground().getFills().get(0).getFill());
            cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), true);
            root.applyCss();
            List<BackgroundFill> selectedFills = cell.getBackground().getFills();
            actual.put("selectedFill", ThemeColorSamples.hex(selectedFills.get(selectedFills.size() - 1).getFill()));
            actual.put("selectedBase", String.valueOf(
                    ThemeColorSamples.hex(selectedFills.get(0).getFill()).equals(restingFill)));
            // The card ring only shows while the board itself holds keyboard focus.
            board.showFocus();
            cell.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            root.applyCss();
            sampleLayers("selectedFocused", cell, actual);
            BorderStroke ring = cell.getBorder().getStrokes().get(0);
            actual.put("ringStrokes", String.valueOf(cell.getBorder().getStrokes().size()));
            actual.put("ring", ThemeColorSamples.hex(ring.getTopStroke()) + " width " + ring.getWidths().getTop()
                    + " insets " + ring.getInsets().getLeft());
            actual.put("contentShift", String.valueOf(!cell.getInsets().equals(restingInsets)));
        });
        String line = expected.get("-rx-outline-variant");
        assertEquals(expected.get("-fx-box-border") + " | " + expected.get(boardExpression) + " " + new Insets(1),
                actual.get("board"), "board frame and surface under " + host);
        assertEquals(line + " | " + expected.get("-rx-surface-variant") + " " + new Insets(1),
                actual.get("column"), "column hairline and surface under " + host);
        // The header band is inset on three sides so the column hairline stays visible around it.
        assertEquals(line + " | " + expected.get(headerExpression) + " " + new Insets(1),
                actual.get("header"), "header divider and band under " + host);
        // The drop-target ring is the column's own outer layer: an outset border is cut by the columns clip.
        assertEquals(expected.get("-rx-primary") + " | " + expected.get("-rx-primary-bg") + " " + new Insets(1),
                actual.get("dropTarget"), "drop-target ring and tint under " + host);
        assertEquals("false", actual.get("dropTargetStroke"), "drop-target column has no border under " + host);
        assertEquals("false", actual.get("dropTargetShift"), "drop-target keeps the column insets under " + host);
        assertNotEquals(expected.get(boardExpression), expected.get("-rx-surface-variant"),
                "columns differ from the board under " + host);
        assertNotEquals(expected.get("-rx-surface-variant"), expected.get(headerExpression),
                "headers differ from the column body under " + host);
        // Hairlines are background layers: a border stroke would count toward the insets.
        assertEquals("false", actual.get("columnStroke"), "column has no border under " + host);
        assertEquals("false", actual.get("headerStroke"), "header has no border under " + host);
        assertEquals(expected.get("-rx-primary-bg"), actual.get("selectedFill"), "selected card tint under " + host);
        // The tint lies on the card's own surface, so a translucent theme tint shows the card, not the column.
        assertEquals("true", actual.get("selectedBase"), "selected card keeps its surface under the tint under " + host);
        // The focus ring lies inside the card: the column content clip cuts anything outside.
        assertEquals("1", actual.get("ringStrokes"), "selected focused card has one border under " + host);
        assertEquals(expected.get("-rx-focus") + " width 1.0 insets 0.0", actual.get("ring"),
                "focus ring border under " + host);
        assertEquals(expected.get("-rx-focus") + " | " + expected.get("-rx-primary-bg") + " " + new Insets(2),
                actual.get("selectedFocused"), "focus ring inner pixel and tint under " + host);
        assertEquals("false", actual.get("contentShift"), "focus does not move the card content under " + host);
    }

    // "<lower layer> | <top layer> <top layer insets>"
    private static void sampleLayers(String key, Region region, Map<String, String> into) {
        List<BackgroundFill> fills = region.getBackground().getFills();
        BackgroundFill top = fills.get(fills.size() - 1);
        into.put(key, ThemeColorSamples.hex(fills.get(0).getFill()) + " | " + ThemeColorSamples.hex(top.getFill())
                + " " + top.getInsets());
        into.put(key + "Stroke", String.valueOf(region.getBorder() != null && !region.getBorder().getStrokes().isEmpty()));
    }

    @Test
    public void dialogCardIsARaisedSurface() throws Exception {
        assertDialogCard("default Modena", Application.STYLESHEET_MODENA, scene -> { }, false);
        assertDialogCard("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertDialogCard("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                false);
        assertDialogCard("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                true);
    }

    @Test
    public void speedDialActionsStandOutFromTheSurface() throws Exception {
        assertSpeedDialAction("default Modena", Application.STYLESHEET_MODENA, scene -> { }, false);
        assertSpeedDialAction("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertSpeedDialAction("Primer Light", new PrimerLight().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, false);
        assertSpeedDialAction("Primer Dark", new PrimerDark().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, true);
    }

    private static void assertSpeedDialAction(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean dark) throws Exception {
        String raisedExpression = dark ? "-rx-surface-variant" : "-fx-control-inner-background";
        String ringTopExpression = dark ? "derive(-rx-outline, 45%)" : "-rx-outline";
        String raiseExpression = dark ? "rgba(255, 255, 255, 0.1)" : "transparent";
        Map<String, String> expected = resolve(userAgentStylesheet, themer,
                List.of(raisedExpression, "-rx-surface", "-rx-outline", ringTopExpression, raiseExpression));
        Map<String, String> action = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            StackPane root = new StackPane(dial);
            Scene scene = new Scene(root, 300, 300);
            themer.accept(scene);
            root.applyCss();
            Region button = (Region) dial.lookup(".actions > .action > .rx-fab");
            List<BackgroundFill> fills = button.getBackground().getFills();
            action.put("layers", String.valueOf(fills.size()));
            if (fills.get(0).getFill() instanceof LinearGradient ring) {
                action.put("ring top", ThemeColorSamples.hex(ring.getStops().get(0).getColor()));
                action.put("ring bottom", ThemeColorSamples.hex(ring.getStops().get(ring.getStops().size() - 1).getColor()));
            }
            if (fills.size() == 3) {
                action.put("fill", ThemeColorSamples.hex(fills.get(1).getFill()));
                action.put("inset", String.valueOf(fills.get(1).getInsets()));
                action.put("raise", ThemeColorSamples.hex(fills.get(2).getFill()));
            }
        });
        // An action button is outlined over a raised fill, so it never blends into the
        // surface it floats over. A shadow barely shows on a dark host, so there the
        // button is also lifted by a white overlay and an outline lit from the top.
        assertEquals("3", action.get("layers"), "action layers under " + host);
        assertEquals(expected.get(ringTopExpression), action.get("ring top"), "action ring top under " + host);
        assertEquals(expected.get("-rx-outline"), action.get("ring bottom"), "action ring bottom under " + host);
        assertEquals(expected.get(raisedExpression), action.get("fill"), "action fill under " + host);
        assertEquals(String.valueOf(new Insets(1)), action.get("inset"), "action fill inset under " + host);
        assertEquals(expected.get(raiseExpression), action.get("raise"), "action overlay under " + host);
        if (dark) {
            assertNotEquals(expected.get("-rx-surface"), action.get("fill"), "action fill differs from the surface under " + host);
            assertNotEquals(expected.get("-rx-outline"), action.get("ring top"), "action ring is lit from the top under " + host);
        }
    }

    @Test
    public void everyPopupDrawsTheSameCardOnItsShell() throws Exception {
        assertPopupCards("default Modena", Application.STYLESHEET_MODENA, scene -> { }, false);
        assertPopupCards("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertPopupCards("Primer Light", new PrimerLight().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, false);
        assertPopupCards("Primer Dark", new PrimerDark().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, true);
    }

    /** What a popup hosts: the content node, the owner's style class and the popup's. */
    private record PopupContent(String name, Supplier<Region> content, String ownerClass, String popupClass) {
    }

    private static void assertPopupCards(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean dark) throws Exception {
        String edgeExpression = dark ? "-rx-outline" : "-rx-outline-variant";
        String fillExpression = dark ? "-rx-surface-variant" : "-fx-control-inner-background";
        Map<String, String> expected = resolve(userAgentStylesheet, themer,
                List.of(edgeExpression, fillExpression, "-rx-surface", "-rx-elevation-umbra-color"));
        List<PopupContent> contents = List.of(
                new PopupContent("color picker", RXColorView::new, "rx-color-picker", "rx-color-picker-popup"),
                new PopupContent("picker", () -> styled(new StackPane(), "container"), "rx-date-picker",
                        "rx-picker-popup"),
                new PopupContent("cascader", RXCascaderView::new, "rx-cascader", "rx-cascader-popup"),
                new PopupContent("selection box", () -> styled(new VBox(), "popup-content"), "rx-selection-box",
                        "rx-selection-box-popup"),
                new PopupContent("suggestions", RXListView::new, "rx-auto-complete-field", "rx-suggestion-popup"),
                new PopupContent("menu", RXMenuList::new, "rx-menu-button", "rx-menu-popup"),
                new PopupContent("sidebar flyout", () -> styled(new StackPane(), "rx-sidebar-flyout"), "rx-sidebar",
                        null));
        for (PopupContent popup : contents) {
            Map<String, String> card = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(userAgentStylesheet);
                Region content = popup.content().get();
                // The popup shell's place: under the popup window (its style class) and,
                // through the window, under the owner.
                StackPane shell = styled(new StackPane(content), "rx-popup");
                StackPane window = new StackPane(shell);
                if (popup.popupClass() != null) {
                    window.getStyleClass().add(popup.popupClass());
                }
                StackPane owner = styled(new StackPane(window), popup.ownerClass());
                Scene scene = new Scene(new StackPane(owner), 400, 400);
                scene.getStylesheets().add(new RXColorView().getUserAgentStylesheet());
                themer.accept(scene);
                scene.getRoot().applyCss();
                List<BackgroundFill> fills = shell.getBackground().getFills();
                card.put("layers", String.valueOf(fills.size()));
                card.put("edge", ThemeColorSamples.hex(fills.get(0).getFill()));
                card.put("fill", ThemeColorSamples.hex(fills.get(1).getFill()));
                card.put("insets", fills.get(0).getInsets() + " " + fills.get(1).getInsets());
                card.put("radii", fills.get(0).getRadii().getTopLeftHorizontalRadius() + " "
                        + fills.get(1).getRadii().getTopLeftHorizontalRadius());
                card.put("padding", String.valueOf(shell.getPadding()));
                DropShadow shadow = (DropShadow) shell.getEffect();
                card.put("shadow", shadow.getRadius() + " " + shadow.getOffsetY() + " "
                        + ThemeColorSamples.hex(shadow.getColor()));
                card.put("content", content.getBackground() + " " + content.getBorder() + " " + content.getEffect());
            });
            String name = popup.name() + " popup under " + host;
            assertEquals("2", card.get("layers"), name + " card layers");
            assertEquals(expected.get(edgeExpression), card.get("edge"), name + " card edge");
            assertEquals(expected.get(fillExpression), card.get("fill"), name + " card fill");
            assertEquals(new Insets(0) + " " + new Insets(1), card.get("insets"), name + " card insets");
            assertEquals("4.0 3.0", card.get("radii"), name + " card radius");
            assertEquals(String.valueOf(new Insets(1)), card.get("padding"), name + " card padding");
            assertEquals("12.0 4.0 " + expected.get("-rx-elevation-umbra-color"), card.get("shadow"),
                    name + " card shadow");
            assertEquals("null null null", card.get("content"), name + " content draws no frame of its own");
            if (dark) {
                // A shadow barely shows on a dark host: the card is lighter than the window.
                assertTrue(relativeLuminance(Color.web(card.get("fill")))
                        > relativeLuminance(Color.web(expected.get("-rx-surface"))), name + " is lighter than the window");
            }
        }
    }

    @Test
    public void hoverInsideAPopupStepsOffTheCard() throws Exception {
        assertPopupHover("default Modena", Application.STYLESHEET_MODENA, scene -> { }, false);
        assertPopupHover("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertPopupHover("Primer Light", new PrimerLight().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, false);
        assertPopupHover("Primer Dark", new PrimerDark().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install, true);
    }

    private static void assertPopupHover(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean dark) throws Exception {
        Map<String, String> expected = resolve(userAgentStylesheet, themer, List.of("-rx-surface-variant"));
        for (String view : List.of("rx-calendar-view", "rx-time-view", "rx-cascader-view")) {
            Map<String, String> fills = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(userAgentStylesheet);
                // Stand-ins for the hosted view: the style class makes them its token root.
                Region inlineProbe = probe("-rx-surface-step");
                Region popupProbe = probe("-rx-surface-step");
                StackPane shell = styled(new StackPane(styled(new StackPane(popupProbe), view)), "rx-popup");
                StackPane inline = styled(new StackPane(inlineProbe), view);
                Scene scene = new Scene(new StackPane(inline, shell), 400, 400);
                scene.getStylesheets().add(new RXColorView().getUserAgentStylesheet());
                themer.accept(scene);
                scene.getRoot().applyCss();
                fills.put("inline", ThemeColorSamples.hex(inlineProbe.getBackground().getFills().get(0).getFill()));
                fills.put("card", ThemeColorSamples.hex(shell.getBackground().getFills().get(1).getFill()));
                fills.put("popup", ThemeColorSamples.hex(popupProbe.getBackground().getFills().get(0).getFill()));
            });
            String name = view + " under " + host;
            assertEquals(expected.get("-rx-surface-variant"), fills.get("inline"), name + ": inline hover is unchanged");
            if (dark) {
                // The card itself takes the surface variant on a dark host.
                assertTrue(relativeLuminance(Color.web(fills.get("popup")))
                        > relativeLuminance(Color.web(fills.get("card"))), name + ": popup hover is lighter than the card");
            } else {
                assertEquals(expected.get("-rx-surface-variant"), fills.get("popup"), name + ": popup hover is unchanged");
                assertNotEquals(fills.get("card"), fills.get("popup"), name + ": popup hover differs from the card");
            }
        }
    }

    private static Region probe(String color) {
        Region probe = new Region();
        probe.setStyle("-fx-background-color: " + color + ";");
        return probe;
    }

    @Test
    @Tag("ui")
    public void popupCardResolvesItsColorsOffAPlainAnchor() throws Exception {
        // Hiding the test's window must not shut the toolkit down for later tests.
        Platform.setImplicitExit(false);
        assertPlainAnchorPopup("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette);
        assertPlainAnchorPopup("Primer Dark", new PrimerDark().getUserAgentStylesheet(),
                RXAtlantaFXThemeBridge::install);
    }

    private static void assertPlainAnchorPopup(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, String> expected = resolve(userAgentStylesheet, themer, List.of("-rx-surface-variant", "-rx-outline"));
        Map<String, String> card = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            // A plain button: no RX control around the anchor defines the card's tokens.
            Button anchor = new Button("Open");
            Scene scene = new Scene(new StackPane(anchor), 300, 300);
            themer.accept(scene);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            RXPopupSupport support = new RXPopupSupport(new Region());
            try {
                support.show(anchor);
                Region shell = null;
                for (Window window : Window.getWindows()) {
                    if (window instanceof PopupWindow && window.isShowing()) {
                        shell = (Region) window.getScene().getRoot().lookup(".rx-popup");
                    }
                }
                assertNotNull(shell, "the popup shell is showing");
                shell.applyCss();
                List<BackgroundFill> fills = shell.getBackground().getFills();
                card.put("edge", ThemeColorSamples.hex(fills.get(0).getFill()));
                card.put("fill", ThemeColorSamples.hex(fills.get(1).getFill()));
            } finally {
                support.dispose();
                stage.hide();
            }
        });
        assertEquals(expected.get("-rx-outline"), card.get("edge"), "popup card edge off a plain anchor under " + host);
        assertEquals(expected.get("-rx-surface-variant"), card.get("fill"),
                "popup card fill off a plain anchor under " + host);
    }

    private static <T extends Region> T styled(T region, String styleClass) {
        region.getStyleClass().add(styleClass);
        return region;
    }

    // ==================== Field heights ====================

    @Test
    public void colorPickerIsAsTallAsATextField() throws Exception {
        assertPickerHeight("default Modena", Application.STYLESHEET_MODENA, scene -> { });
        assertPickerHeight("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
        assertPickerHeight("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
    }

    @Test
    public void fieldControlsAreAsTallAsTheirNativeCounterparts() throws Exception {
        assertFieldHeights("default Modena", Application.STYLESHEET_MODENA, scene -> { });
        assertFieldHeights("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
        assertFieldHeights("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
    }

    private static void assertFieldHeights(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, Double> heights = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            Map<String, Region> controls = new LinkedHashMap<>();
            controls.put("text field", new TextField("Text"));
            controls.put("native menu button", new MenuButton("Menu"));
            RXSelectionBox<String> selectionBox = new RXSelectionBox<>(FXCollections.observableArrayList("Apple"));
            selectionBox.getSelectionModel().select(0);
            controls.put("selection box", selectionBox);
            controls.put("cascader", new RXCascader());
            controls.put("date picker", new RXDatePicker());
            controls.put("date range picker", new RXDateRangePicker());
            controls.put("menu button", new RXMenuButton("Menu"));
            controls.put("chip input", new RXChipInput<String>());
            RXChipInput<String> chipInput = new RXChipInput<>();
            chipInput.getChips().addAll("Apple", "Pear");
            controls.put("chip input with chips", chipInput);
            VBox root = new VBox();
            root.getChildren().addAll(controls.values());
            Scene scene = new Scene(root, 600, 400);
            themer.accept(scene);
            root.applyCss();
            controls.forEach((name, control) -> heights.put(name, control.prefHeight(-1)));
        });
        for (String field : List.of("selection box", "cascader", "date picker", "date range picker")) {
            assertEquals(heights.get("text field"), heights.get(field), 0.0, field + " height under " + host);
        }
        assertEquals(heights.get("native menu button"), heights.get("menu button"), 0.0,
                "menu button height under " + host);
        // The chip input may stand taller than a text field, but a chip must not change it.
        assertEquals(heights.get("chip input"), heights.get("chip input with chips"), 0.0,
                "chip input height with chips under " + host);
        assertTrue(heights.get("chip input") >= heights.get("text field"), "chip input height under " + host);
    }

    private static void assertPickerHeight(String host, String userAgentStylesheet, Consumer<Scene> themer)
            throws Exception {
        Map<String, Double> heights = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            TextField field = new TextField("Text");
            RXColorPicker plain = new RXColorPicker();
            RXColorPicker full = new RXColorPicker();
            full.setShowText(true);
            full.setCopyButtonVisible(true);
            VBox root = new VBox(field, plain, full);
            Scene scene = new Scene(root, 400, 300);
            themer.accept(scene);
            root.applyCss();
            heights.put("field", field.prefHeight(-1));
            heights.put("plain", plain.prefHeight(-1));
            heights.put("full", full.prefHeight(-1));
        });
        assertEquals(heights.get("field"), heights.get("plain"), 0.0, "swatch-only picker height under " + host);
        assertEquals(heights.get("field"), heights.get("full"), 0.0, "picker with text and copy button under " + host);
    }

    @Test
    public void colorPickerFrameMatchesModenasColorPicker() throws Exception {
        assertModenaPickerFrame("default Modena", scene -> { });
        assertModenaPickerFrame("dark palette", RXControlColorsTest::darkPalette);
    }

    private static void assertModenaPickerFrame(String host, Consumer<Scene> themer) throws Exception {
        Map<String, String> ours = new LinkedHashMap<>();
        Map<String, String> theirs = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            ours.putAll(frameStates(new RXColorPicker(), null, PICKER_STATES, themer));
            theirs.putAll(frameStates(new ColorPicker(), null, PICKER_STATES, themer));
        });
        theirs.forEach((state, frame) -> assertEquals(frame, ours.get(state), state + " frame under " + host));
    }

    private static final List<String> PICKER_STATES = List.of("idle", "hover", "showing", "focused");

    @Test
    public void selectionBoxFrameMatchesModenasComboBox() throws Exception {
        for (Consumer<Scene> themer : List.<Consumer<Scene>>of(scene -> { }, RXControlColorsTest::darkPalette)) {
            Map<String, String> ours = new LinkedHashMap<>();
            Map<String, String> theirs = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
                ours.putAll(frameStates(new RXSelectionBox<String>(), ".display", PICKER_STATES, themer));
                theirs.putAll(frameStates(new ComboBox<String>(), null, PICKER_STATES, themer));
            });
            theirs.forEach((state, frame) -> assertEquals(frame, ours.get(state), state + " selection box frame"));
        }
    }

    @Test
    public void menuButtonFrameMatchesModenasMenuButton() throws Exception {
        List<String> states = List.of("idle", "hover", "armed", "focused");
        for (Consumer<Scene> themer : List.<Consumer<Scene>>of(scene -> { }, RXControlColorsTest::darkPalette)) {
            Map<String, String> ours = new LinkedHashMap<>();
            Map<String, String> theirs = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
                ours.putAll(frameStates(new RXMenuButton("Menu"), null, states, themer));
                theirs.putAll(frameStates(new MenuButton("Menu"), null, states, themer));
            });
            theirs.forEach((state, frame) -> assertEquals(frame, ours.get(state), state + " menu button frame"));
        }
    }

    @Test
    public void menuButtonFrameMatchesAtlantaFxsMenuButton() throws Exception {
        List<String> states = List.of("idle", "hover", "armed", "focused");
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark())) {
            Map<String, String> ours = new LinkedHashMap<>();
            Map<String, String> theirs = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                ours.putAll(frameStates(new RXMenuButton("Menu"), null, states, RXAtlantaFXThemeBridge::install));
                theirs.putAll(frameStates(new MenuButton("Menu"), null, states, RXAtlantaFXThemeBridge::install));
            });
            theirs.forEach((state, frame) -> assertEquals(frame, ours.get(state),
                    state + " menu button frame under " + theme.getName()));
        }
    }

    // Each state is forced alone; the frame is the background of the painted node.
    private static Map<String, String> frameStates(Region control, String part, List<String> stateNames,
                                                   Consumer<Scene> themer) {
        Scene scene = new Scene(new StackPane(control), 300, 200);
        themer.accept(scene);
        scene.getRoot().applyCss();
        Region painted = part == null ? control : (Region) control.lookup(part);
        Map<String, String> states = new LinkedHashMap<>();
        for (String state : stateNames) {
            for (String other : stateNames) {
                control.pseudoClassStateChanged(PseudoClass.getPseudoClass(other), other.equals(state));
            }
            scene.getRoot().applyCss();
            StringBuilder frame = new StringBuilder();
            for (BackgroundFill fill : painted.getBackground().getFills()) {
                frame.append(fill.getFill()).append(" @").append(fill.getInsets()).append(" r")
                        .append(fill.getRadii().getTopLeftHorizontalRadius()).append("; ");
            }
            states.put(state, frame.toString());
        }
        return states;
    }

    // ==================== Popup menus ====================

    @Test
    public void popupMenusFollowTheThemesContextMenu() throws Exception {
        assertPopupMenu("default Modena", Application.STYLESHEET_MODENA, scene -> { }, true);
        assertPopupMenu("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        assertPopupMenu("Primer Light", new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                false);
        assertPopupMenu("Primer Dark", new PrimerDark().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install,
                false);
    }

    private static void assertPopupMenu(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean modena) throws Exception {
        // AtlantaFX's own menu item highlight: base-1 on a light theme, base-7 on a dark one.
        String atlantaBar = "ladder(-color-bg-default, -color-base-7 49%, -color-base-1 50%)";
        Map<String, String> expected = resolve(userAgentStylesheet, themer, modena
                ? List.of("-fx-selection-bar", "-fx-focused-text-base-color")
                : List.of(atlantaBar, "-color-fg-default"));
        Map<String, String> menu = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("HEX"), RXMenuItem.of("RGB"));
            StackPane popup = styled(new StackPane(list), "rx-menu-popup");
            Scene scene = new Scene(new StackPane(popup), 300, 300);
            themer.accept(scene);
            scene.getRoot().applyCss();
            Region item = (Region) list.lookup(".rx-menu-item");
            item.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            scene.getRoot().applyCss();
            BackgroundFill bar = item.getBackground().getFills().get(0);
            menu.put("bar", ThemeColorSamples.hex(bar.getFill()));
            menu.put("radius", String.valueOf(bar.getRadii().getTopLeftHorizontalRadius()));
            menu.put("text", ThemeColorSamples.hex(((Labeled) item.lookup(".label")).getTextFill()));
            menu.put("minWidth", String.valueOf(list.getMinWidth()));
            menu.put("ripple", String.valueOf(list.isRippleEnabled()));
            menu.put("overlay", String.valueOf(list.isStateOverlayEnabled()));
        });
        String name = "popup menu under " + host;
        if (modena) {
            // Modena's context menu: a square selection bar, focused text, no minimum.
            assertEquals(expected.get("-fx-selection-bar"), menu.get("bar"), name + " selection bar");
            assertEquals(expected.get("-fx-focused-text-base-color"), menu.get("text"), name + " focused text");
            assertEquals("0.0", menu.get("radius"), name + " square bar");
            assertEquals(String.valueOf(Region.USE_COMPUTED_SIZE), menu.get("minWidth"), name + " minimum width");
        } else {
            // AtlantaFX's context menu: a rounded neutral highlight, unchanged text, no
            // minimum.
            assertEquals(expected.get(atlantaBar), menu.get("bar"), name + " neutral highlight");
            assertEquals(expected.get("-color-fg-default"), menu.get("text"), name + " text");
            assertEquals("5.0", menu.get("radius"), name + " rounded highlight");
            assertEquals(String.valueOf(Region.USE_COMPUTED_SIZE), menu.get("minWidth"), name + " minimum width");
        }
        // The ripple stays; the highlight already marks the row under the pointer, so the
        // hover / focus overlay would only darken it.
        assertEquals("true", menu.get("ripple"), name + " ripple");
        assertEquals("false", menu.get("overlay"), name + " hover / focus overlay");
    }

    // ==================== Focus rings ====================

    /** A focus ring member: how to build it, which node draws the ring, and its recipe. */
    private record FocusMember(String name, Supplier<Region> control, String part, String recipe) {
    }

    private static List<FocusMember> focusMembers() {
        return List.of(
                new FocusMember("date picker", RXDatePicker::new, null, "field"),
                new FocusMember("cascader", RXCascader::new, ".display", "field"),
                new FocusMember("selection box", RXSelectionBox::new, ".display", "button"),
                new FocusMember("chip input", RXChipInput::new, null, "field"),
                new FocusMember("menu button", () -> new RXMenuButton("Menu"), null, "button"),
                new FocusMember("color picker", RXColorPicker::new, null, "button"),
                new FocusMember("hold button", () -> new RXHoldButton("Hold"), null, "button"),
                new FocusMember("chip", () -> new RXChip("Chip"), null, "button"),
                new FocusMember("selected chip", () -> {
                    RXChip chip = new RXChip("Chip");
                    chip.setSelectable(true);
                    chip.setSelected(true);
                    return chip;
                }, null, "button"),
                new FocusMember("fill button", () -> new RXFillButton("Fill"), null, "stroke"),
                new FocusMember("line button", () -> new RXLineButton("Line"), null, "stroke"),
                new FocusMember("list view", RXListView::new, null, "container"),
                new FocusMember("tile view", RXTileView::new, null, "container"),
                new FocusMember("masonry view", RXMasonryView::new, null, "container"),
                new FocusMember("cascader view", RXCascaderView::new, null, "container"),
                new FocusMember("calendar view", RXCalendarView::new, null, "container"),
                new FocusMember("time view", RXTimeView::new, null, "container"),
                new FocusMember("year range view", RXYearRangeView::new, null, "container"));
    }

    @Test
    public void focusRingsFollowModenasRecipes() throws Exception {
        assertFocusRecipes("default Modena", scene -> { });
        assertFocusRecipes("dark palette", RXControlColorsTest::darkPalette);
    }

    private static void assertFocusRecipes(String host, Consumer<Scene> themer) throws Exception {
        Map<String, String> expected = resolve(Application.STYLESHEET_MODENA, themer,
                List.of("-fx-focus-color", "-fx-faint-focus-color"));
        String focus = expected.get("-fx-focus-color");
        String faint = expected.get("-fx-faint-focus-color");
        Map<String, String> field = new LinkedHashMap<>();
        Map<String, Map<String, String>> rings = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            // The reference field: Modena's own text field ring, layer for layer.
            field.putAll(focusedLayers(new RXTextField(), null, themer));
            for (FocusMember member : focusMembers()) {
                rings.put(member.name(), focusedLayers(member.control().get(), member.part(), themer));
            }
        });
        for (FocusMember member : focusMembers()) {
            Map<String, String> ring = rings.get(member.name());
            String name = member.name() + " under " + host;
            switch (member.recipe()) {
                case "field":
                    assertEquals(field.get("fills"), ring.get("fills"), name + " draws the text field ring");
                    break;
                case "button":
                    assertEquals(focus + "@-0.2 _ " + faint + "@-1.4 _", ring.get("ring"), name + " draws the button ring");
                    break;
                case "stroke":
                    assertTrue(ring.get("strokes").startsWith(faint + " " + focus), name + " draws the ring as strokes");
                    break;
                default:
                    assertEquals(faint + "@-1.4 " + focus + "@-0.3 _", ring.get("ring"), name + " draws the container ring");
                    break;
            }
        }
    }

    @Test
    public void chipInputFocusMatchesTheAtlantaFxTextField() throws Exception {
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark())) {
            Map<String, String> field = new LinkedHashMap<>();
            Map<String, String> chipInput = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                Consumer<Scene> bridge = RXAtlantaFXThemeBridge::install;
                Region textField = new RXTextField();
                focusedLayers(textField, null, bridge);
                field.put("edge", ThemeColorSamples.hex(textField.getBackground().getFills().get(0).getFill()));
                Region input = new RXChipInput<String>();
                chipInput.putAll(focusedLayers(input, null, bridge));
                chipInput.put("edge", ThemeColorSamples.hex(input.getBackground().getFills().get(0).getFill()));
            });
            // An AtlantaFX text field shows focus as an accent outline, with no halo.
            assertEquals(field.get("edge"), chipInput.get("edge"), "chip input focus outline under " + theme.getName());
            assertEquals("_", chipInput.get("ring"), "chip input draws no halo under " + theme.getName());
        }
    }

    // The focused pseudo-class is set directly: a test window does not get the
    // system focus, and :focus-within / :focus-shown follow the same state.
    private static Map<String, String> focusedLayers(Region control, String part, Consumer<Scene> themer) {
        Scene scene = new Scene(new StackPane(control), 400, 400);
        themer.accept(scene);
        // Skins first: a skin sets its own focus state from the real focus when it starts.
        scene.getRoot().applyCss();
        for (String pseudoClass : List.of("focused", "focus-within", "focus-shown")) {
            control.pseudoClassStateChanged(PseudoClass.getPseudoClass(pseudoClass), true);
        }
        scene.getRoot().applyCss();
        Region node = part == null ? control : (Region) control.lookup(part);
        node.applyCss();
        Map<String, String> layers = new LinkedHashMap<>();
        StringBuilder fills = new StringBuilder();
        StringBuilder ring = new StringBuilder();
        List<BackgroundFill> backgroundFills = node.getBackground().getFills();
        for (int i = 0; i < backgroundFills.size(); i++) {
            BackgroundFill fill = backgroundFills.get(i);
            String color = fill.getFill() instanceof Color ? ThemeColorSamples.hex(fill.getFill()) : "_";
            double inset = fill.getInsets().getTop();
            fills.append(fill.getFill()).append('@').append(inset).append(" r").append(fill.getRadii()
                    .getTopLeftHorizontalRadius()).append(' ');
            // The ring layers are the ones outside the body; the rest is the member's own fill.
            ring.append(inset < 0 ? color + "@" + inset : "_").append(i < backgroundFills.size() - 1 ? " " : "");
        }
        layers.put("fills", fills.toString());
        layers.put("ring", ring.toString().replaceAll("(_ )+_", "_").replaceAll("_ _", "_"));
        StringBuilder strokes = new StringBuilder();
        if (node.getBorder() != null) {
            for (BorderStroke stroke : node.getBorder().getStrokes()) {
                strokes.append(ThemeColorSamples.hex(stroke.getTopStroke())).append(' ');
            }
        }
        layers.put("strokes", strokes.toString().trim());
        return layers;
    }

    private static void assertDialogCard(String host, String userAgentStylesheet, Consumer<Scene> themer,
            boolean dark) throws Exception {
        String surfaceExpression = dark ? "-rx-surface-variant" : "-fx-control-inner-background";
        Map<String, String> expected = resolve(userAgentStylesheet, themer,
                List.of(surfaceExpression, "-rx-surface", "-rx-outline"));
        Map<String, String> card = new LinkedHashMap<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            Region owner = new Region();
            Scene scene = new Scene(new RXOverlayPane(new StackPane(owner)), 400, 300);
            themer.accept(scene);
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setAnimated(false);
            dialog.show(owner);
            scene.getRoot().applyCss();
            Region dialogCard = (Region) dialog.lookup(".dialog-card");
            List<BackgroundFill> fills = dialogCard.getBackground().getFills();
            BackgroundFill surface = fills.get(fills.size() - 1);
            card.put("surface", ThemeColorSamples.hex(surface.getFill()));
            card.put("edge", ThemeColorSamples.hex(fills.get(0).getFill()));
            card.put("inset", String.valueOf(surface.getInsets()));
            card.put("border", String.valueOf(dialogCard.getBorder() != null && !dialogCard.getBorder().getStrokes().isEmpty()));
            card.put("shadow", dialogCard.getEffect() instanceof DropShadow
                    ? ThemeColorSamples.hex(((DropShadow) dialogCard.getEffect()).getColor()) : "none");
        });
        String surface = expected.get(surfaceExpression);
        assertEquals(surface, card.get("surface"), "dialog card surface under " + host);
        if (dark) {
            assertTrue(relativeLuminance(Color.web(surface)) > relativeLuminance(Color.web(expected.get("-rx-surface"))),
                    "dialog card is lighter than the window under " + host);
        }
        // The edge is the lower layer showing around a 1px-inset surface: outlined on a dark
        // host, invisible (the surface color) on a light one, and never a border that would
        // change the card's size.
        assertEquals(dark ? expected.get("-rx-outline") : surface, card.get("edge"), "dialog card edge under " + host);
        assertEquals(String.valueOf(new Insets(1)), card.get("inset"), "dialog card surface inset under " + host);
        assertEquals("false", card.get("border"), "dialog card has no border under " + host);
        // An opaque light-gray theme shadow would read as a glow on the scrim.
        assertEquals("#0000004d", card.get("shadow"), "dialog card shadow under " + host);
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

    // ==================== List cells ====================

    /**
     * A selected list row stays distinguishable from the list surface under every
     * AtlantaFX theme.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void listSelectionIsVisibleOnEveryAtlantaFxTheme() throws Exception {
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            Map<String, String> sample = new LinkedHashMap<>();
            runOnFx(() -> {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                RXListView<String> list = new RXListView<>(FXCollections.observableArrayList("a", "b"));
                StackPane root = new StackPane(list);
                RXAtlantaFXThemeBridge.install(new Scene(root, 300, 200));
                list.getSelectionModel().select(0);
                root.applyCss();
                root.layout();
                Region viewport = (Region) list.lookup(".viewport");
                sample.put("surface", ThemeColorSamples.hex(viewport.getBackground().getFills().get(0).getFill()));
                for (Node node : list.lookupAll(".rx-list-cell")) {
                    if (node.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected"))) {
                        Region cell = (Region) node;
                        sample.put("selected", ThemeColorSamples.hex(cell.getBackground().getFills().get(0).getFill()));
                    }
                }
            });
            assertNotNull(sample.get("selected"), "selected row rendered under " + theme.getName());
            assertNotEquals(sample.get("surface"), sample.get("selected"),
                    "selected row differs from the list surface under " + theme.getName());
        }
    }

    // ==================== Tile / masonry cells ====================

    /**
     * Under default Modena a selected tile or masonry cell takes the light accent tint
     * with the default text, and an unselected cell keeps the default text.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void defaultModenaKeepsTheCellColors() throws Exception {
        List<String> samples = cellSamples(Application.STYLESHEET_MODENA, scene -> { });
        assertEquals(List.of(
                "RXTileView unselected text #000000ff", "RXTileView selected #d7f0f8ff text #000000ff",
                "RXMasonryView unselected text #000000ff", "RXMasonryView selected #d7f0f8ff text #000000ff"),
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

    /**
     * The text avatar shares the placeholder disc, and its initials and the icon are
     * white, on default Modena, a dark application palette and every AtlantaFX theme;
     * under Modena the disc keeps normal-text contrast (4.5:1) for them.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void avatarInitialsAndIconAreWhiteOnEveryHost() throws Exception {
        assertAvatarWhite("default Modena", Application.STYLESHEET_MODENA, scene -> { }, true);
        assertAvatarWhite("dark palette", Application.STYLESHEET_MODENA, RXControlColorsTest::darkPalette, true);
        for (Theme theme : List.of(new PrimerLight(), new PrimerDark(), new NordLight(), new NordDark(),
                new CupertinoLight(), new CupertinoDark(), new Dracula())) {
            assertAvatarWhite(theme.getName(), theme.getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install, false);
        }
    }

    private static void assertAvatarWhite(String host, String userAgentStylesheet, Consumer<Scene> themer,
                                          boolean checkContrast) throws Exception {
        Map<String, String> samples = sampleUnder(userAgentStylesheet, themer);
        String disc = samples.get("avatar.text-disc");
        assertEquals(samples.get("avatar.icon-disc"), disc, "text avatar disc under " + host);
        assertEquals("#ffffffff", samples.get("avatar.text"), "white initials under " + host);
        assertEquals("#ffffffff", samples.get("avatar.icon"), "white icon under " + host);
        if (checkContrast) {
            double discLuminance = relativeLuminance(Color.web(disc));
            double contrast = 1.05 / (discLuminance + 0.05);
            assertTrue(contrast >= 4.5, "white initials contrast " + contrast + " under " + host);
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
     * A disabled segmented control fades its selection pill, on default Modena and under the bridge.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disabledSegmentedControlFadesThePill() throws Exception {
        assertPillOpacity(Application.STYLESHEET_MODENA, scene -> { });
        assertPillOpacity(new PrimerLight().getUserAgentStylesheet(), RXAtlantaFXThemeBridge::install);
    }

    private static void assertPillOpacity(String userAgentStylesheet, Consumer<Scene> themer) throws Exception {
        List<Double> opacities = new ArrayList<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                    new RXSegmentedItem<>("on", "On"), new RXSegmentedItem<>("off", "Off"));
            segmented.select("on");
            StackPane root = new StackPane(segmented);
            themer.accept(new Scene(root, 300, 100));
            root.applyCss();
            opacities.add(segmented.lookup(".indicator").getOpacity());
            segmented.setDisable(true);
            root.applyCss();
            opacities.add(segmented.lookup(".indicator").getOpacity());
        });
        assertEquals(List.of(1.0, 0.4), opacities, "pill opacity enabled, then disabled");
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
            sampleBothWays("rating empty icon", RXRating::new,
                    control -> ThemeColorSamples.hex(fill(control.lookup(".empty"))), asRoot, nested);
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

    /** A board that can show the focused state without a focused window. */
    private static final class FocusableBoard extends RXKanbanView<String> {
        void showFocus() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
        }
    }

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
        List<String> tokens = List.of("primary", "on-primary", "success", "info", "surface", "surface-variant", "on-surface",
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
