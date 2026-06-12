package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimCheckerboard;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimNewspaper;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatterRadial;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimWhipPan;
import io.github.leewyatt.rxcontrols.animation.page.AnimWind;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
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
 * Showcase application for {@link RXTransitionPane}.
 *
 * <p>Exercises the two flagship use cases: a direction-aware wizard (Back /
 * Next over a fixed list of step cards) and a state machine (loading,
 * content, error, empty) — plus the transition presets shared with
 * RXCarousel and RXLrcLineView (multi-page display animations are excluded;
 * the pane falls back to a direct cut for those).</p>
 */
public class RXTransitionPaneShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_ANIMATION = "Fade";
    private static final int STEP_COUNT = 4;

    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();
    private final List<Node> stepCards = createStepCards();

    private RXTransitionPane transitionPane;
    private int stepIndex;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTransitionPane";
    }

    @Override
    protected String subtitle() {
        return "Animated single-content switcher";
    }

    @Override
    protected String windowTitle() {
        return "RXTransitionPane Showcase";
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
        return getClass().getResource("rx-transition-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        transitionPane = new RXTransitionPane(stepCards.get(0));
        transitionPane.getStyleClass().add("showcase-transition-pane");
        transitionPane.setPrefSize(460.0, 250.0);
        transitionPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Button backButton = new Button("< Back");
        backButton.setOnAction(event -> showStep(stepIndex - 1, TransitionDirection.BACKWARD));
        Button nextButton = new Button("Next >");
        nextButton.setOnAction(event -> showStep(stepIndex + 1, TransitionDirection.FORWARD));
        HBox wizardBar = new HBox(12.0, backButton, nextButton);
        wizardBar.setAlignment(Pos.CENTER);

        Label hint = new Label("Wizard navigation plays the configured transition in both directions.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, transitionPane, wizardBar, hint);
        preview.getStyleClass().add("transition-pane-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Animation", buildAnimationGrid()),
                section("View States", buildStateGrid()));
    }

    // ==================== Sections ====================

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox("Animate content changes");
        animatedBox.setSelected(transitionPane.isAnimated());
        transitionPane.animatedProperty().bind(animatedBox.selectedProperty());

        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(animationPresets.keySet());
        animationBox.setValue(DEFAULT_ANIMATION);
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Supplier<PageAnimation> preset = animationPresets.get(newValue);
            if (preset != null) {
                transitionPane.setAnimation(preset.get());
            }
        });

        Slider durationSlider = createSlider(100.0, 1500.0,
                RXTransitionPane.DEFAULT_ANIMATION_DURATION.toMillis());
        transitionPane.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty()));

        return createGrid(
                row(animatedBox),
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildStateGrid() {
        Button loadingButton = new Button("Loading");
        loadingButton.setOnAction(event -> transitionPane.setContent(createLoadingCard()));
        Button contentButton = new Button("Content");
        contentButton.setOnAction(event -> showStep(stepIndex, TransitionDirection.FORWARD));
        Button errorButton = new Button("Error");
        errorButton.setOnAction(event -> transitionPane.setContent(createErrorCard()));
        Button emptyButton = new Button("Empty (null)");
        emptyButton.setOnAction(event -> transitionPane.setContent(null));

        HBox states = new HBox(8.0, loadingButton, contentButton, errorButton, emptyButton);
        states.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Switching to and from null uses a direct cut.");
        hint.getStyleClass().add("hint-label");

        return createGrid(
                row(states),
                row(hint));
    }

    // ==================== Content nodes ====================

    private void showStep(int index, TransitionDirection direction) {
        int clamped = Math.floorMod(index, STEP_COUNT);
        stepIndex = clamped;
        transitionPane.transitionTo(stepCards.get(clamped), direction);
    }

    private static List<Node> createStepCards() {
        return List.of(
                createStepCard(1, "Welcome", "Wizard pages switch with any preset."),
                createStepCard(2, "Profile", "Back plays the transition in reverse."),
                createStepCard(3, "Preferences", "Rapid clicks jump to the latest state."),
                createStepCard(4, "Summary", "Latest wins; no transition queue."));
    }

    private static Node createStepCard(int number, String title, String description) {
        Label step = new Label("Step " + number);
        step.getStyleClass().add("step-number");
        Label heading = new Label(title);
        heading.getStyleClass().add("step-title");
        Label body = new Label(description);
        body.getStyleClass().add("step-description");
        body.setWrapText(true);

        VBox card = new VBox(8.0, step, heading, body);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("state-card", "step-card", "step-card-" + number);
        return card;
    }

    private static Node createLoadingCard() {
        RXBarSpinner spinner = new RXBarSpinner();
        Label label = new Label("Loading…");
        label.getStyleClass().add("step-title");
        VBox card = new VBox(12.0, spinner, label);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("state-card", "loading-card");
        return card;
    }

    private static Node createErrorCard() {
        Label icon = new Label("!");
        icon.getStyleClass().add("error-icon");
        Label label = new Label("Something went wrong");
        label.getStyleClass().add("step-title");
        VBox card = new VBox(8.0, icon, label);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("state-card", "error-card");
        return card;
    }

    // ==================== Animation presets ====================

    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("Fade", AnimFade::new);
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Shatter", AnimShatter::new);
        presets.put("Shatter (radial)", AnimShatterRadial::new);
        presets.put("Glitch", AnimGlitch::new);
        presets.put("Blinds", AnimBlinds::new);
        presets.put("Checkerboard", AnimCheckerboard::new);
        presets.put("Whip Pan", AnimWhipPan::new);
        presets.put("Cube", AnimCube::new);
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
