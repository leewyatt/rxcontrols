package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCopyButton;
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
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXCopyButton}.
 *
 * <p>Exercises the content factory (nothing to copy, plain text, text plus
 * HTML, an image), the feedback duration including zero and indefinite,
 * {@code resetFeedback()}, the swap animation, the copied text, the
 * icon-only / icon-and-text / text-only forms, a graphic on either face,
 * alignment in a stretched button, the disabled state and right-to-left
 * orientation. The text area takes a paste to verify what reached the
 * clipboard.</p>
 */
public class RXCopyButtonShowcase extends RXShowcaseApplication {

    private static final String COPIED_TEXT_NULL = "null (reuse text)";
    private static final String COPIED_TEXT_EMPTY = "\"\" (no text)";

    private RXCopyButton button;
    private TextField sourceField;
    private Label firedLabel;
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCopyButton";
    }

    @Override
    protected String subtitle() {
        return "Copies to the clipboard and confirms with a feedback face";
    }

    @Override
    protected String windowTitle() {
        return "RXCopyButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-copy-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXCopyButton("Copy");
        button.setCopiedText("Copied");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("action fired " + firedCount + "x");
        });

        sourceField = new TextField("Text read at the moment of the click");
        sourceField.setMaxWidth(320.0);
        // Changing the data makes a showing "Copied" stale.
        sourceField.textProperty().addListener(observable -> button.resetFeedback());
        button.setContentFactory(() -> RXCopyButton.textContent(sourceField.getText()));

        firedLabel = new Label("action fired 0x");
        firedLabel.getStyleClass().add("fired-label");

        TextArea pasteArea = new TextArea();
        pasteArea.setPromptText("Paste here to check the clipboard");
        pasteArea.setPrefRowCount(3);
        pasteArea.setMaxWidth(320.0);

        Label hint = new Label("Clicking again while the feedback shows copies again and restarts it.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, sourceField, button, firedLabel, pasteArea, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Swap animation", buildAnimationGrid()),
                section("Content", buildContentGrid()),
                section("Feedback", buildFeedbackGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Sections ====================

    private Node buildAnimationGrid() {
        Map<String, Supplier<PageAnimation>> presets = animationPresets();
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(presets.keySet());
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimation(presets.get(newV).get()));
        // The control default is a direct cut; the showcase starts on a visible one.
        animationBox.setValue("Slide (vertical)");

        Slider durationSlider = createSlider(0.0, 800.0,
                RXCopyButton.DEFAULT_ANIMATION_DURATION.toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimationDuration(Duration.millis(newV.doubleValue())));

        return createGrid(
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("None (direct cut)", () -> null);
        presets.put("Fade", AnimFade::new);
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Gaussian Blur", AnimGaussianBlur::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Flip", AnimFlip::new);
        presets.put("Flip (vertical)", () -> new AnimFlip(Orientation.VERTICAL));
        presets.put("Blinds", AnimBlinds::new);
        presets.put("Whip Pan", AnimWhipPan::new);
        presets.put("Glitch", AnimGlitch::new);
        presets.put("Shatter", AnimShatter::new);
        presets.put("Newspaper", AnimNewspaper::new);
        return presets;
    }

    private Node buildContentGrid() {
        Map<String, Supplier<ClipboardContent>> factories = new LinkedHashMap<>();
        factories.put("text field", () -> RXCopyButton.textContent(sourceField.getText()));
        factories.put("text + HTML", () -> {
            ClipboardContent content = RXCopyButton.textContent(sourceField.getText());
            content.putHtml("<b>" + escapeHtml(sourceField.getText()) + "</b>");
            return content;
        });
        factories.put("image (button snapshot)", () -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(button.snapshot(null, null));
            return content;
        });
        factories.put("textContent(\"\")", () -> RXCopyButton.textContent(""));
        factories.put("factory returns null", () -> null);
        factories.put("null factory", null);

        ComboBox<String> sourceBox = new ComboBox<>();
        sourceBox.getItems().setAll(factories.keySet());
        sourceBox.setValue("text field");
        sourceBox.setMaxWidth(Double.MAX_VALUE);
        sourceBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setContentFactory(factories.get(newV)));

        Button fireButton = new Button("fire()");
        fireButton.setMaxWidth(Double.MAX_VALUE);
        fireButton.setOnAction(event -> button.fire());

        return createGrid(
                row("Source", sourceBox),
                row("Program", fireButton));
    }

    private Node buildFeedbackGrid() {
        Slider durationSlider = createSlider(0.0, 5000.0,
                RXCopyButton.DEFAULT_FEEDBACK_DURATION.toMillis());
        CheckBox indefiniteBox = new CheckBox();
        Runnable applyDuration = () -> button.setFeedbackDuration(indefiniteBox.isSelected()
                ? Duration.INDEFINITE
                : Duration.millis(durationSlider.getValue()));
        durationSlider.valueProperty().addListener((obs, oldV, newV) -> applyDuration.run());
        indefiniteBox.selectedProperty().addListener((obs, oldV, newV) -> applyDuration.run());

        ComboBox<String> copiedTextBox = new ComboBox<>();
        copiedTextBox.getItems().setAll("Copied", "Copied to clipboard", COPIED_TEXT_NULL, COPIED_TEXT_EMPTY);
        copiedTextBox.setValue("Copied");
        copiedTextBox.setMaxWidth(Double.MAX_VALUE);
        copiedTextBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (COPIED_TEXT_NULL.equals(newV)) {
                button.setCopiedText(null);
            } else if (COPIED_TEXT_EMPTY.equals(newV)) {
                button.setCopiedText("");
            } else {
                button.setCopiedText(newV);
            }
        });

        Button resetButton = new Button("resetFeedback()");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(event -> button.resetFeedback());

        return createGrid(
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Indefinite", indefiniteBox),
                row("Copied text", copiedTextBox),
                row("Reset", resetButton));
    }

    private Node buildButtonGrid() {
        TextField textField = new TextField(button.getText());
        textField.setPromptText("empty = icon only");
        button.textProperty().bind(textField.textProperty());

        CheckBox hideIndicatorBox = new CheckBox();
        hideIndicatorBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                button.getStyleClass().add("no-indicator");
            } else {
                button.getStyleClass().remove("no-indicator");
            }
        });

        // Each face needs its own node: one node cannot be shown on both.
        Region star = createStar();
        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setGraphic(newV ? star : null));

        Region copiedStar = createStar();
        CheckBox copiedGraphicBox = new CheckBox();
        copiedGraphicBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setCopiedGraphic(newV ? copiedStar : null));

        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT);
        alignmentBox.setValue(Pos.CENTER);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        button.alignmentProperty().bind(alignmentBox.valueProperty());

        CheckBox stretchBox = new CheckBox();
        stretchBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setPrefWidth(newV ? 260.0 : Region.USE_COMPUTED_SIZE));

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        CheckBox rtlBox = new CheckBox();
        rtlBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setNodeOrientation(newV
                        ? NodeOrientation.RIGHT_TO_LEFT
                        : NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Text", textField),
                row("Hide indicators", hideIndicatorBox),
                row("User graphic", graphicBox),
                row("Copied graphic", copiedGraphicBox),
                row("Alignment", alignmentBox),
                row("Stretch to 260 px", stretchBox),
                row("Disabled", disableBox),
                row("Right to left", rtlBox));
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Region createStar() {
        Region star = new Region();
        star.getStyleClass().add("star-icon");
        star.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return star;
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
