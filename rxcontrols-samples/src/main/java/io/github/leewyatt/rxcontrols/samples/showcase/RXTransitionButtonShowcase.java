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
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXTransitionButton}.
 *
 * <p>Exercises the two flagship use cases — a social-style button whose icon
 * face swaps to a detail face, and a call-to-action button whose caption
 * swaps to a confirmation — plus the trigger modes (hover, pressed, none with
 * programmatic playback) and the transition presets shared with the other
 * page-animation hosts.</p>
 */
public class RXTransitionButtonShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_ANIMATION = "Slide (vertical)";

    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();

    private RXTransitionButton emailButton;
    private RXTransitionButton downloadButton;

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
        emailButton = new RXTransitionButton("@  Email");
        emailButton.getStyleClass().add("showcase-email-button");
        emailButton.setPrefSize(240.0, 64.0);
        Label address = new Label("hello@example.com");
        emailButton.setAlternateContent(address);

        downloadButton = new RXTransitionButton("Download");
        downloadButton.getStyleClass().add("showcase-download-button");
        downloadButton.setPrefSize(240.0, 64.0);
        Label confirm = new Label("3.2 MB — click to start");
        downloadButton.setAlternateContent(confirm);

        Label hint = new Label("Hover swaps the faces; the action fires on click as usual.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(18.0, emailButton, downloadButton, hint);
        preview.getStyleClass().add("transition-button-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
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
        emailButton.animationTriggerProperty().bind(triggerBox.valueProperty());
        downloadButton.animationTriggerProperty().bind(triggerBox.valueProperty());

        Button playButton = new Button("playAnimation()");
        playButton.setOnAction(event -> {
            emailButton.playAnimation();
            downloadButton.playAnimation();
        });

        Label hint = new Label("NONE disables automatic triggering; playAnimation() round-trips once.");
        hint.getStyleClass().add("hint-label");

        HBox controls = new HBox(12.0, playButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Trigger", triggerBox),
                row(controls),
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
                emailButton.setAnimation(preset.get());
                downloadButton.setAnimation(preset.get());
            }
        });

        Slider durationSlider = createSlider(80.0, 800.0,
                RXAnimatedButton.DEFAULT_ANIMATION_DURATION.toMillis());
        var durationBinding = Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty());
        emailButton.animationDurationProperty().bind(durationBinding);
        downloadButton.animationDurationProperty().bind(durationBinding);

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
