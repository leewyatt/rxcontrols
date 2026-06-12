package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimNewspaper;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimWhipPan;
import io.github.leewyatt.rxcontrols.animation.page.AnimWind;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXTransitionLabel}.
 *
 * <p>Exercises the two flagship use cases: a message banner fed manually or
 * by a periodic auto-push, and a live numeric display — plus the transition
 * presets shared with the other page-animation hosts.</p>
 */
public class RXTransitionLabelShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_ANIMATION = "Slide (vertical)";
    private static final List<String> MESSAGES = List.of(
            "Build finished in 42 s",
            "3 new comments on your post",
            "Deploy to production succeeded",
            "Battery low: 15% remaining",
            "Meeting starts in 10 minutes",
            "Backup completed without errors");

    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();

    private RXTransitionLabel messageLabel;
    private RXTransitionLabel counterLabel;
    private Timeline autoPush;
    private Timeline counterTimeline;
    private int messageIndex;
    private int counter;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTransitionLabel";
    }

    @Override
    protected String subtitle() {
        return "Animated text switcher for banners and live values";
    }

    @Override
    protected String windowTitle() {
        return "RXTransitionLabel Showcase";
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
        return getClass().getResource("rx-transition-label-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        messageLabel = new RXTransitionLabel(MESSAGES.get(0));
        messageLabel.getStyleClass().add("showcase-message-label");
        messageLabel.setAnimation(new AnimSlide(Orientation.VERTICAL));
        messageLabel.setPrefSize(460.0, 56.0);
        messageLabel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        counterLabel = new RXTransitionLabel("0");
        counterLabel.getStyleClass().add("showcase-counter-label");
        counterLabel.setAnimation(new AnimSlide(Orientation.VERTICAL));
        counterLabel.setAnimationDuration(Duration.millis(260.0));
        counterLabel.setPrefSize(180.0, 72.0);
        counterLabel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label messageCaption = new Label("Message banner");
        messageCaption.getStyleClass().add("preview-caption");
        Label counterCaption = new Label("Live value (independent instance)");
        counterCaption.getStyleClass().add("preview-caption");

        VBox preview = new VBox(10.0,
                messageCaption, messageLabel,
                counterCaption, counterLabel);
        preview.getStyleClass().add("transition-label-preview");
        preview.setAlignment(Pos.CENTER);

        counterTimeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> {
            counter++;
            counterLabel.setText(Integer.toString(counter));
        }));
        counterTimeline.setCycleCount(Timeline.INDEFINITE);
        counterTimeline.play();

        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Messages", buildMessageGrid()),
                section("Animation", buildAnimationGrid()));
    }

    // ==================== Sections ====================

    private Node buildMessageGrid() {
        Button sendButton = new Button("Send next message");
        sendButton.setOnAction(event -> pushNextMessage());

        CheckBox autoBox = new CheckBox("Auto-push every 2 s");
        autoBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                autoPush = new Timeline(new KeyFrame(Duration.seconds(2.0),
                        event -> pushNextMessage()));
                autoPush.setCycleCount(Timeline.INDEFINITE);
                autoPush.play();
            } else if (autoPush != null) {
                autoPush.stop();
                autoPush = null;
            }
        });

        Label hint = new Label("Rapid sends jump to the latest message; no queue.");
        hint.getStyleClass().add("hint-label");

        HBox controls = new HBox(12.0, sendButton, autoBox);
        controls.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row(controls),
                row(hint));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox("Animate text changes");
        animatedBox.setSelected(messageLabel.isAnimated());
        messageLabel.animatedProperty().bind(animatedBox.selectedProperty());

        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(animationPresets.keySet());
        animationBox.setValue(DEFAULT_ANIMATION);
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Supplier<PageAnimation> preset = animationPresets.get(newValue);
            if (preset != null) {
                messageLabel.setAnimation(preset.get());
            }
        });

        ComboBox<TransitionDirection> directionBox = new ComboBox<>();
        directionBox.getItems().setAll(TransitionDirection.values());
        directionBox.setValue(messageLabel.getDirection());
        directionBox.setMaxWidth(Double.MAX_VALUE);
        messageLabel.directionProperty().bind(directionBox.valueProperty());

        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT);
        alignmentBox.setValue(messageLabel.getAlignment());
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        messageLabel.alignmentProperty().bind(alignmentBox.valueProperty());

        Slider durationSlider = createSlider(100.0, 1500.0,
                RXTransitionLabel.DEFAULT_ANIMATION_DURATION.toMillis());
        messageLabel.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty()));

        return createGrid(
                row(animatedBox),
                row("Animation", animationBox),
                row("Direction", directionBox),
                row("Alignment", alignmentBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    // ==================== Helpers ====================

    private void pushNextMessage() {
        messageIndex = (messageIndex + 1) % MESSAGES.size();
        messageLabel.setText(MESSAGES.get(messageIndex));
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
        presets.put("Gaussian Blur", AnimGaussianBlur::new);
        presets.put("Newspaper", AnimNewspaper::new);
        presets.put("Wind", AnimWind::new);
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
