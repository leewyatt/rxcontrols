package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXTaskButton;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXTaskButton}.
 *
 * <p>Clicking the button starts a run. The run ends on its own after the work
 * time, or waits for the control pane: Succeed and Fail report through the
 * handle of the newest run, Cancel and Reset call the button. The preview shows
 * {@code running} and {@code state} as they change, and how often
 * {@code onAction} fired. The control pane covers the four durations including zero and indefinite, the
 * progress, {@code cancellable}, the text of every face, the pending indicator,
 * the swap animation, the disabled state and right-to-left orientation.</p>
 */
public class RXTaskButtonShowcase extends RXShowcaseApplication {

    private static final String MANUAL = "Waits for the buttons below";
    private static final String SUCCEEDS = "Succeeds after the work time";
    private static final String FAILS = "Fails after the work time";
    private static final String BUILT_IN_RING = "Ring (built in)";
    private static final String START_ANIMATION = "Slide (vertical)";

    private RXTaskButton button;
    private RXTaskButton.Run run;
    private final ComboBox<String> outcomeBox = new ComboBox<>();
    private Slider workSlider;
    private final Label firedLabel = new Label("onAction fired 0x");
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTaskButton";
    }

    @Override
    protected String subtitle() {
        return "Follows one piece of work: pending, then succeeded or failed";
    }

    @Override
    protected String windowTitle() {
        return "RXTaskButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-task-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXTaskButton("Save");
        button.setPendingText("Saving…");
        button.setSucceededText("Saved");
        button.setFailedText("Not saved");
        // An activation the button swallows or turns into a cancel never gets here.
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("onAction fired " + firedCount + "x");
            startRun();
        });

        Label runningLabel = new Label();
        runningLabel.textProperty().bind(Bindings.concat("running: ", button.runningProperty()));
        Label stateLabel = new Label();
        stateLabel.textProperty().bind(Bindings.concat("state: ", button.stateProperty()));
        VBox status = new VBox(4.0, runningLabel, stateLabel, firedLabel);
        status.getStyleClass().add("status");
        status.setAlignment(Pos.CENTER);

        Label hint = new Label("state is the face on show, not the outcome: it lags behind running by the"
                + " pending delay and the pending minimum.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setMaxWidth(340.0);

        VBox preview = new VBox(18.0, button, status, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Run", buildRunGrid()),
                section("Progress", buildProgressGrid()),
                section("Timing", buildTimingGrid()),
                section("Faces", buildFacesGrid()),
                section("Swap animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Run ====================

    private void startRun() {
        RXTaskButton.Run started = button.start();
        run = started;
        String outcome = outcomeBox.getValue();
        if (MANUAL.equals(outcome)) {
            return;
        }
        // Each run keeps its own handle: a timer that outlives its run reports
        // into a handle that no longer does anything.
        PauseTransition work = new PauseTransition(Duration.millis(workSlider.getValue()));
        work.setOnFinished(event -> {
            if (FAILS.equals(outcome)) {
                started.fail();
            } else {
                started.succeed();
            }
        });
        work.play();
    }

    private Node buildRunGrid() {
        outcomeBox.getItems().setAll(SUCCEEDS, FAILS, MANUAL);
        outcomeBox.setValue(SUCCEEDS);
        outcomeBox.setMaxWidth(Double.MAX_VALUE);

        workSlider = createSlider(0.0, 5000.0, 1500.0);
        Label workValue = createValueLabel(workSlider, "%.0f ms");
        workSlider.disableProperty().bind(outcomeBox.valueProperty().isEqualTo(MANUAL));

        Button succeedButton = new Button("Succeed");
        succeedButton.setOnAction(event -> {
            if (run != null) {
                run.succeed();
            }
        });
        Button failButton = new Button("Fail");
        failButton.setOnAction(event -> {
            if (run != null) {
                run.fail();
            }
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> button.cancel());
        Button resetButton = new Button("Reset");
        resetButton.setOnAction(event -> button.resetFeedback());

        Button fireButton = new Button("fire()");
        fireButton.setMaxWidth(Double.MAX_VALUE);
        fireButton.setOnAction(event -> button.fire());

        return createGrid(
                row("A run", outcomeBox),
                row("Work time", workSlider, workValue),
                row("Newest run", sideBySide(succeedButton, failButton)),
                row("Button", sideBySide(cancelButton, resetButton)),
                row("Program", fireButton));
    }

    // Two to a row: four in one row do not fit the control pane under every theme.
    private static Node sideBySide(Button first, Button second) {
        first.setMaxWidth(Double.MAX_VALUE);
        second.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(first, Priority.ALWAYS);
        HBox.setHgrow(second, Priority.ALWAYS);
        return new HBox(8.0, first, second);
    }

    // ==================== Progress ====================

    private Node buildProgressGrid() {
        Slider progressSlider = createSlider(0.0, 1.0, 0.0);
        progressSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setProgress(newV.doubleValue()));
        // Shows the property, not the slider: starting a run makes it indeterminate again.
        Label progressValue = new Label();
        progressValue.getStyleClass().add("value-label");
        progressValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        progressValue.setAlignment(Pos.CENTER_RIGHT);
        progressValue.textProperty().bind(Bindings.createStringBinding(() -> {
            double progress = button.getProgress();
            return progress >= 0.0 ? String.format("%.0f%%", Math.min(progress, 1.0) * 100.0) : "none";
        }, button.progressProperty()));

        Button indeterminateButton = new Button("Indeterminate");
        indeterminateButton.setMaxWidth(Double.MAX_VALUE);
        indeterminateButton.setOnAction(event -> button.setProgress(-1.0));

        return createGrid(
                row("Progress", progressSlider, progressValue),
                row("Unknown", indeterminateButton));
    }

    // ==================== Timing ====================

    private Node buildTimingGrid() {
        Slider delaySlider = durationSlider(1000.0, RXTaskButton.DEFAULT_PENDING_DELAY,
                button::setPendingDelay);
        Slider minSlider = durationSlider(2000.0, RXTaskButton.DEFAULT_PENDING_MIN_DURATION,
                button::setPendingMinDuration);
        Slider succeededSlider = durationSlider(5000.0, RXTaskButton.DEFAULT_SUCCEEDED_DURATION,
                button::setSucceededDuration);
        Slider failedSlider = durationSlider(5000.0, RXTaskButton.DEFAULT_FAILED_DURATION,
                button::setFailedDuration);

        return createGrid(
                row("Pending delay", delaySlider, createValueLabel(delaySlider, "%.0f ms")),
                row("Pending minimum", minSlider, createValueLabel(minSlider, "%.0f ms")),
                row("Succeeded for", succeededSlider, createValueLabel(succeededSlider, "%.0f ms")),
                row("Succeeded stays", indefiniteBox(succeededSlider, button::setSucceededDuration,
                        RXTaskButton.State.SUCCEEDED)),
                row("Failed for", failedSlider, createValueLabel(failedSlider, "%.0f ms")),
                row("Failed stays", indefiniteBox(failedSlider, button::setFailedDuration,
                        RXTaskButton.State.FAILED)));
    }

    private Slider durationSlider(double max, Duration initial, Consumer<Duration> setter) {
        Slider slider = createSlider(0.0, max, initial.toMillis());
        slider.valueProperty().addListener((obs, oldV, newV) ->
                setter.accept(Duration.millis(newV.doubleValue())));
        return slider;
    }

    private CheckBox indefiniteBox(Slider slider, Consumer<Duration> setter, RXTaskButton.State face) {
        CheckBox box = new CheckBox();
        slider.disableProperty().bind(box.selectedProperty());
        // The button reads the duration when the face comes up: only a face
        // that came up while the box was selected is waiting for its reset.
        BooleanProperty waiting = new SimpleBooleanProperty();
        button.stateProperty().addListener((obs, oldV, newV) ->
                waiting.set(newV == face && box.isSelected()));
        box.selectedProperty().addListener((obs, oldV, newV) -> {
            setter.accept(newV ? Duration.INDEFINITE : Duration.millis(slider.getValue()));
            if (!newV && waiting.get()) {
                button.resetFeedback();
            }
        });
        return box;
    }

    // ==================== Faces ====================

    private Node buildFacesGrid() {
        TextField textField = new TextField(button.getText());
        textField.setPromptText("empty = graphic only");
        button.textProperty().bind(textField.textProperty());

        // An empty field shows the indicator alone, the same as null.
        TextField pendingField = new TextField(button.getPendingText());
        pendingField.setPromptText("empty = ring only");
        button.pendingTextProperty().bind(pendingField.textProperty());
        TextField succeededField = new TextField(button.getSucceededText());
        succeededField.setPromptText("empty = check only");
        button.succeededTextProperty().bind(succeededField.textProperty());
        TextField failedField = new TextField(button.getFailedText());
        failedField.setPromptText("empty = cross only");
        button.failedTextProperty().bind(failedField.textProperty());

        Region star = createStar();
        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setGraphic(newV ? star : null));

        Map<String, Supplier<Node>> indicators = new LinkedHashMap<>();
        indicators.put(BUILT_IN_RING, () -> null);
        indicators.put("RXDotPulse", RXTaskButtonShowcase::createDots);
        indicators.put("RXBarSpinner", RXTaskButtonShowcase::createBars);
        ComboBox<String> indicatorBox = new ComboBox<>();
        indicatorBox.getItems().setAll(indicators.keySet());
        indicatorBox.setMaxWidth(Double.MAX_VALUE);
        indicatorBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setPendingIndicator(indicators.get(newV).get()));
        indicatorBox.setValue(BUILT_IN_RING);

        return createGrid(
                row("Text", textField),
                row("Pending text", pendingField),
                row("Succeeded text", succeededField),
                row("Failed text", failedField),
                row("Graphic", graphicBox),
                row("Pending indicator", indicatorBox));
    }

    private static Node createDots() {
        RXDotPulse dots = new RXDotPulse();
        dots.setDotSize(4.0);
        dots.setDotGap(3.0);
        return decorative(dots);
    }

    private static Node createBars() {
        RXBarSpinner bars = new RXBarSpinner();
        bars.setBarWidth(3.0);
        bars.setBarHeight(14.0);
        bars.setBarGap(2.0);
        return decorative(bars);
    }

    // A control inside the button would otherwise take the Tab key and the
    // clicks that land on it.
    private static Node decorative(Node indicator) {
        indicator.setFocusTraversable(false);
        indicator.setMouseTransparent(true);
        return indicator;
    }

    private static Region createStar() {
        Region star = new Region();
        star.getStyleClass().add("star-icon");
        star.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return star;
    }

    // ==================== Swap animation ====================

    private Node buildAnimationGrid() {
        Map<String, Supplier<PageAnimation>> presets = animationPresets();
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(presets.keySet());
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimation(presets.get(newV).get()));
        // The control default is a direct cut; the showcase starts on a visible one.
        animationBox.setValue(START_ANIMATION);

        Slider durationSlider = durationSlider(800.0, RXTaskButton.DEFAULT_ANIMATION_DURATION,
                button::setAnimationDuration);

        return createGrid(
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("None (direct cut)", () -> null);
        presets.put("Fade", AnimFade::new);
        presets.put(START_ANIMATION, () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Gaussian Blur", AnimGaussianBlur::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Flip", AnimFlip::new);
        presets.put("Cube (vertical)", () -> new AnimCube(Orientation.VERTICAL));
        presets.put("Blinds", AnimBlinds::new);
        return presets;
    }

    // ==================== Button ====================

    private Node buildButtonGrid() {
        CheckBox cancellableBox = new CheckBox();
        button.cancellableProperty().bind(cancellableBox.selectedProperty());

        CheckBox defaultBox = new CheckBox();
        button.defaultButtonProperty().bind(defaultBox.selectedProperty());

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        CheckBox rtlBox = new CheckBox();
        rtlBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setNodeOrientation(newV
                        ? NodeOrientation.RIGHT_TO_LEFT
                        : NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Cancellable", cancellableBox),
                row("Default button", defaultBox),
                row("Disabled", disableBox),
                row("Right to left", rtlBox));
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
