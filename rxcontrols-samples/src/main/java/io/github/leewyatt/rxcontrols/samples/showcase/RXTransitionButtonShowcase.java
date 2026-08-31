package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAnimatedButton;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimNewspaper;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimWhipPan;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXTransitionButton}.
 *
 * <p>Exercises the two flagship face pairings — an icon-only navigation
 * button revealing its caption, and a copy action whose caption swaps to an
 * icon + confirmation face — plus the trigger modes and the transition
 * presets shared with the other page-animation hosts.</p>
 */
public class RXTransitionButtonShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_ANIMATION = "Slide (vertical)";

    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();

    private RXTransitionButton navButton;
    private RXTransitionButton copyButton;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTransitionButton";
    }

    @Override
    protected String subtitle() {
        return "Button with two content faces swapped by trigger state";
    }

    @Override
    protected String windowTitle() {
        return "RXTransitionButton Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 620.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-transition-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        navButton = new RXTransitionButton();
        navButton.getStyleClass().addAll("showcase-button", "nav-button");
        navButton.setGraphic(icon("home-icon"));
        navButton.setAlternateContent(altContent(new Label("Home")));

        copyButton = new RXTransitionButton("Copy text", icon("copy-icon"));
        copyButton.getStyleClass().addAll("showcase-button", "copy-button");
        copyButton.setAlternateContent(altContent(new Label("Copied!", icon("check-icon"))));
        copyButton.setOnAction(event -> copyToClipboard());

        Label hint = new Label("Icon face in, caption face out — clicking still fires the action.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(18.0, navButton, copyButton, hint);
        preview.getStyleClass().add("transition-button-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    // The skin mirrors the button's own text fill onto the front face; the
    // alternate node is a plain user node and is styled through this class.
    private static Label altContent(Label label) {
        label.getStyleClass().add("alt-content");
        return label;
    }

    private static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().add(styleClass);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    private static void copyToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString("RXTransitionButton");
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Trigger", buildTriggerGrid()),
                section("Animation", buildAnimationGrid()));
    }

    // ==================== Sections ====================

    private Node buildTriggerGrid() {
        ComboBox<AnimationTrigger> triggerBox = new ComboBox<>();
        triggerBox.getItems().setAll(AnimationTrigger.values());
        triggerBox.setValue(RXAnimatedButton.DEFAULT_ANIMATION_TRIGGER);
        triggerBox.setMaxWidth(Double.MAX_VALUE);
        navButton.animationTriggerProperty().bind(triggerBox.valueProperty());
        copyButton.animationTriggerProperty().bind(triggerBox.valueProperty());

        Label hint = new Label("PRESSED fits confirmation pairs; NONE disables automatic "
                + "triggering, leaving playAnimation() as the only way in.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        return createGrid(
                row("Trigger", triggerBox),
                row(hint));
    }

    private Node buildAnimationGrid() {
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(animationPresets.keySet());
        animationBox.setValue(DEFAULT_ANIMATION);
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Supplier<PageAnimation> preset = animationPresets.get(newValue);
            if (preset != null) {
                navButton.setAnimation(preset.get());
                copyButton.setAnimation(preset.get());
            }
        });

        Slider durationSlider = createSlider(80.0, 800.0,
                RXAnimatedButton.DEFAULT_ANIMATION_DURATION.toMillis());
        var durationBinding = Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty());
        navButton.animationDurationProperty().bind(durationBinding);
        copyButton.animationDurationProperty().bind(durationBinding);

        return createGrid(
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    // ==================== Animation presets ====================

    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Fade", AnimFade::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Shatter", AnimShatter::new);
        presets.put("Glitch", AnimGlitch::new);
        presets.put("Blinds", AnimBlinds::new);
        presets.put("Whip Pan", AnimWhipPan::new);
        presets.put("Flip", AnimFlip::new);
        presets.put("Newspaper", AnimNewspaper::new);
        presets.put("Random (selector)", () -> AnimSelector.random(
                new AnimFade(), new AnimSlide(), new AnimZoom(), new AnimShatter(), new AnimFlip()));
        presets.put("Sequence (selector)", () -> AnimSelector.sequence(
                new AnimFade(), new AnimSlide(), new AnimZoom(), new AnimShatter(), new AnimFlip()));
        return presets;
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
