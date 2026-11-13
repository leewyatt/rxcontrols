package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
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
 * Showcase application for {@link RXHoldButton}.
 *
 * <p>The preview shows the button with its current state (idle, holding,
 * completed), an ActionEvent counter and an icon-only variant. The control
 * pane covers the hold duration, the text and graphic of each of the three
 * faces, the feedback duration (indefinite, finite,
 * zero) with {@code resetFeedback()}, the swap animation, the fill animation,
 * a solid and an outlined look, a stretched width, the disabled state,
 * right-to-left orientation and the untimed {@code fire()}.</p>
 */
public class RXHoldButtonShowcase extends RXShowcaseApplication {

    private static final PseudoClass HOLDING = PseudoClass.getPseudoClass("holding");
    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");
    private static final String SOLID = "Solid (theme default)";
    private static final String OUTLINED = "Outlined";
    private static final Duration ICON_FEEDBACK = Duration.seconds(1.5);

    private RXHoldButton button;
    private RXHoldButton iconButton;
    private final Label stateLabel = new Label();
    private final Label firedLabel = new Label("onAction fired 0x");
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXHoldButton";
    }

    @Override
    protected String subtitle() {
        return "Fires only after being held down, then confirms with a completed face";
    }

    @Override
    protected String windowTitle() {
        return "RXHoldButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-hold-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXHoldButton("Hold to delete");
        button.setCompletedText("Deleted");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("onAction fired " + firedCount + "x");
        });
        button.getPseudoClassStates().addListener(
                (SetChangeListener<PseudoClass>) change -> updateStateLabel());
        updateStateLabel();
        stateLabel.getStyleClass().add("state-label");
        firedLabel.getStyleClass().add("muted-label");

        // Icon-only: no text on any face, so the button stays icon-sized.
        // Each face gets its own icon node: nothing is borrowed from the idle face.
        iconButton = new RXHoldButton(null, icon("trash-icon"));
        iconButton.setHoldingText(null);
        iconButton.setHoldingGraphic(icon("hourglass-icon"));
        iconButton.setCompletedText(null);
        iconButton.setCompletedGraphic(icon("check-icon"));
        iconButton.setAccessibleText("Delete");
        iconButton.setFeedbackDuration(ICON_FEEDBACK);
        Label iconCaption = new Label(String.format("Icon only (resets after %.1f s)",
                ICON_FEEDBACK.toSeconds()));
        iconCaption.getStyleClass().add("muted-label");
        HBox iconRow = new HBox(10.0, iconButton, iconCaption);
        iconRow.setAlignment(Pos.CENTER);

        Label hint = new Label("Release or move out to cancel. While the completed face shows,"
                + " a press starts no hold until the feedback ends.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setMaxWidth(360.0);

        // A row neighbor, so "Stretch" shows the button sharing an HBox's width.
        Label neighbor = new Label("Row neighbor");
        neighbor.getStyleClass().add("muted-label");
        neighbor.setMinWidth(Region.USE_PREF_SIZE);
        HBox buttonRow = new HBox(12.0, button, neighbor);
        buttonRow.setAlignment(Pos.CENTER);

        VBox preview = new VBox(16.0, buttonRow, stateLabel, firedLabel, iconRow, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Hold", buildHoldGrid()),
                section("Faces", buildFacesGrid()),
                section("Feedback", buildFeedbackGrid()),
                section("Animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    private void updateStateLabel() {
        String state;
        if (button.getPseudoClassStates().contains(COMPLETED)) {
            state = "completed";
        } else if (button.getPseudoClassStates().contains(HOLDING)) {
            state = "holding";
        } else {
            state = "idle";
        }
        stateLabel.setText("State: " + state);
    }

    // ==================== Sections ====================

    private Node buildHoldGrid() {
        Slider durationSlider = createSlider(0.0, 4000.0,
                RXHoldButton.DEFAULT_HOLD_DURATION.toMillis());
        // Whole milliseconds, so the label shows exactly what the button gets:
        // 0 falls back to the default, while 0.4 would fire almost at once.
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setHoldDuration(Duration.millis(Math.round(newV.doubleValue()))));

        Button fireButton = new Button("fire()");
        fireButton.setMaxWidth(Double.MAX_VALUE);
        fireButton.setOnAction(event -> button.fire());

        return createGrid(
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Untimed", fireButton));
    }

    private Node buildFacesGrid() {
        // Each face needs its own node: the three faces are in the scene at once.
        Region idleIcon = icon("trash-icon");
        Region holdingIcon = icon("hourglass-icon");
        Region completedIcon = icon("check-icon");

        return createGrid(
                row("Text", textField(button.getText(), button::setText)),
                row("Holding text", textField(button.getHoldingText(), button::setHoldingText)),
                row("Completed text", textField(button.getCompletedText(), button::setCompletedText)),
                row("Graphic", toggle(true, selected -> button.setGraphic(selected ? idleIcon : null))),
                row("Holding graphic",
                        toggle(true, selected -> button.setHoldingGraphic(selected ? holdingIcon : null))),
                row("Completed graphic",
                        toggle(true, selected -> button.setCompletedGraphic(selected ? completedIcon : null))));
    }

    private Node buildFeedbackGrid() {
        Slider durationSlider = createSlider(0.0, 5000.0, 2000.0);
        Label durationValue = createValueLabel(durationSlider, "%.0f ms");
        CheckBox indefiniteBox = new CheckBox();
        durationSlider.disableProperty().bind(indefiniteBox.selectedProperty());
        durationValue.disableProperty().bind(indefiniteBox.selectedProperty());
        Runnable applyDuration = () -> button.setFeedbackDuration(indefiniteBox.isSelected()
                ? Duration.INDEFINITE
                : Duration.millis(Math.round(durationSlider.getValue())));
        durationSlider.valueProperty().addListener((obs, oldV, newV) -> applyDuration.run());
        // Leaving indefinite also ends a completed face still waiting for its
        // reset, so the button is usable again without a press.
        indefiniteBox.selectedProperty().addListener((obs, oldV, newV) -> {
            applyDuration.run();
            if (!newV) {
                button.resetFeedback();
            }
        });
        applyDuration.run();

        Button resetButton = new Button("resetFeedback()");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(event -> button.resetFeedback());

        Label zeroHint = new Label("0 ms skips the completed face.");
        zeroHint.getStyleClass().add("hint-label");

        return createGrid(
                row("Indefinite", indefiniteBox),
                row("Duration", durationSlider, durationValue),
                row(zeroHint),
                row("Reset", resetButton));
    }

    private Node buildAnimationGrid() {
        final String noAnimation = "None (direct cut)";
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put(noAnimation, () -> null);
        presets.put("Fade", AnimFade::new);
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide", AnimSlide::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Flip (vertical)", () -> new AnimFlip(Orientation.VERTICAL));
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(presets.keySet());
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimation(presets.get(newV).get()));
        animationBox.setValue(noAnimation);

        Slider durationSlider = createSlider(0.0, 800.0,
                RXHoldButton.DEFAULT_ANIMATION_DURATION.toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimationDuration(Duration.millis(Math.round(newV.doubleValue()))));

        final String defaultFill = "Left to right";
        Map<String, FillAnimation> fills = new LinkedHashMap<>();
        fills.put(defaultFill, FillAnimation.LEFT_TO_RIGHT);
        fills.put("Right to left", FillAnimation.RIGHT_TO_LEFT);
        fills.put("Top to bottom", FillAnimation.TOP_TO_BOTTOM);
        fills.put("Bottom to top", FillAnimation.BOTTOM_TO_TOP);
        fills.put("Center out", FillAnimation.CENTER_OUT);
        fills.put("Center out (vertical)", FillAnimation.CENTER_OUT_VERTICAL);
        fills.put("Edges in", FillAnimation.EDGES_IN);
        fills.put("Edges in (vertical)", FillAnimation.EDGES_IN_VERTICAL);
        fills.put("Circle", FillAnimation.CIRCLE);
        fills.put("Corners in", FillAnimation.CORNERS_IN);
        fills.put("Zigzag", FillAnimation.ZIGZAG);
        fills.put("Zigzag (vertical)", FillAnimation.ZIGZAG_VERTICAL);
        ComboBox<String> fillBox = new ComboBox<>();
        fillBox.getItems().setAll(fills.keySet());
        fillBox.setMaxWidth(Double.MAX_VALUE);
        fillBox.valueProperty().addListener((obs, oldV, newV) -> button.setFillAnimation(fills.get(newV)));
        fillBox.setValue(defaultFill);

        return createGrid(
                row("Swap", animationBox),
                row("Swap duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Fill", fillBox));
    }

    private Node buildButtonGrid() {
        ComboBox<String> lookBox = new ComboBox<>();
        lookBox.getItems().setAll(SOLID, OUTLINED);
        lookBox.setMaxWidth(Double.MAX_VALUE);
        lookBox.valueProperty().addListener((obs, oldV, newV) -> {
            for (RXHoldButton target : List.of(button, iconButton)) {
                if (OUTLINED.equals(newV)) {
                    target.getStyleClass().add("outlined");
                } else {
                    target.getStyleClass().remove("outlined");
                }
            }
        });
        lookBox.setValue(SOLID);

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        // A button does not grow past its preferred width unless its max
        // width is raised; with hgrow it then takes the row's spare width.
        CheckBox stretchBox = new CheckBox();
        stretchBox.selectedProperty().addListener((obs, oldV, newV) -> {
            button.setMaxWidth(newV ? Double.MAX_VALUE : Region.USE_COMPUTED_SIZE);
            HBox.setHgrow(button, newV ? Priority.ALWAYS : null);
        });

        CheckBox rtlBox = new CheckBox();
        rtlBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setNodeOrientation(newV
                        ? NodeOrientation.RIGHT_TO_LEFT
                        : NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Look", lookBox),
                row("Stretch", stretchBox),
                row("Disabled", disableBox),
                row("Right to left", rtlBox));
    }

    // ==================== Helpers ====================

    // An empty field shows no text, the same as null.
    private static TextField textField(String text, Consumer<String> setter) {
        TextField field = new TextField(text);
        field.textProperty().addListener((obs, oldV, newV) -> setter.accept(newV));
        return field;
    }

    private static CheckBox toggle(boolean selected, Consumer<Boolean> action) {
        CheckBox box = new CheckBox();
        box.selectedProperty().addListener((obs, oldV, newV) -> action.accept(newV));
        box.setSelected(selected);
        return box;
    }

    private static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("showcase-icon", styleClass);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
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
