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
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXCopyButton}.
 *
 * <p>The preview shows what the selected content source copies (plain text
 * from a text field, text plus HTML, an image, or nothing to copy) and, after
 * every activation, what the system clipboard holds in each format. The
 * control pane covers the feedback duration including zero and indefinite,
 * {@code resetFeedback()}, the copied text and graphic, the swap animation,
 * the icon-only / icon-and-text / text-only forms, the button graphic,
 * alignment, the disabled state and right-to-left orientation.</p>
 */
public class RXCopyButtonShowcase extends RXShowcaseApplication {

    private static final String RICH_PLAIN_TEXT = "RXControls: JavaFX controls";
    private static final String RICH_HTML = "<b>RXControls</b>: <i>JavaFX</i> controls";
    private static final String NONE = "—";
    private static final int PREVIEW_LENGTH = 160;
    private static final double CARD_WIDTH = 340.0;

    private RXCopyButton button;
    private final StackPane sourceCard = new StackPane();
    private final TextField sourceField = new TextField("Text read at the moment of the click");
    private final Label firedLabel = new Label("onAction fired 0x");
    private int firedCount;

    private final Label clipboardText = new Label(NONE);
    private final Label clipboardHtml = new Label(NONE);
    private final Label clipboardImageInfo = new Label(NONE);
    private final ImageView clipboardImage = new ImageView();

    /**
     * One entry of the content source picker: what the factory copies and the
     * preview card that shows it.
     */
    private record Source(Supplier<ClipboardContent> factory, Node card) {
    }

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
        // onAction runs after the copy, so the clipboard already holds the result.
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("onAction fired " + firedCount + "x");
            readClipboard();
        });

        // Changing the data makes a showing "Copied" stale.
        sourceField.textProperty().addListener(observable -> button.resetFeedback());
        sourceField.setPromptText("empty = nothing to copy");
        firedLabel.getStyleClass().add("muted-label");

        sourceCard.getStyleClass().add("preview-card");
        sourceCard.setMaxWidth(CARD_WIDTH);

        Label hint = new Label("Clicking again while the feedback shows copies again and restarts it.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, sourceCard, button, firedLabel, buildClipboardCard(), hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Content", buildContentGrid()),
                section("Feedback", buildFeedbackGrid()),
                section("Swap animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Preview cards ====================

    private Map<String, Source> createSources() {
        Map<String, Source> sources = new LinkedHashMap<>();
        sources.put("Plain text", new Source(
                () -> RXCopyButton.textContent(sourceField.getText()),
                card("Copies the text of this field as plain text", sourceField)));
        sources.put("Text + HTML", new Source(
                () -> {
                    ClipboardContent content = RXCopyButton.textContent(RICH_PLAIN_TEXT);
                    content.putHtml(RICH_HTML);
                    return content;
                },
                buildRichCard()));

        Image image = new Image(getClass().getResource("/scenery/1.png").toExternalForm());
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(300.0);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        sources.put("Image", new Source(
                () -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putImage(image);
                    return content;
                },
                card("Copies this image (" + (int) image.getWidth() + " × "
                        + (int) image.getHeight() + ")", imageView)));

        sources.put("Empty text", new Source(
                () -> RXCopyButton.textContent(""),
                nothingCard("textContent(\"\") returns empty content.")));
        sources.put("Factory returns null", new Source(
                () -> null,
                nothingCard("The factory returns null.")));
        sources.put("No factory", new Source(
                null,
                nothingCard("No factory is set (the default).")));
        return sources;
    }

    private Node buildRichCard() {
        Label bold = new Label("RXControls");
        bold.getStyleClass().add("bold-text");
        Label italic = new Label("JavaFX");
        italic.getStyleClass().add("italic-text");
        HBox rendered = new HBox(bold, new Label(": "), italic, new Label(" controls"));

        GridPane formats = formatGrid();
        formats.addRow(0, mutedLabel("Rich editors"), rendered);
        formats.addRow(1, mutedLabel("Plain editors"), new Label(RICH_PLAIN_TEXT));
        formats.addRow(2, mutedLabel("HTML source"), monoLabel(RICH_HTML));
        return card("Copies one item in two formats", formats);
    }

    private Node buildClipboardCard() {
        clipboardImage.setFitWidth(120.0);
        clipboardImage.setPreserveRatio(true);
        clipboardHtml.getStyleClass().add("mono-label");
        clipboardHtml.setWrapText(true);
        clipboardText.setWrapText(true);

        GridPane formats = formatGrid();
        formats.addRow(0, mutedLabel("Text"), clipboardText);
        formats.addRow(1, mutedLabel("HTML"), clipboardHtml);
        formats.addRow(2, mutedLabel("Image"), new VBox(4.0, clipboardImageInfo, clipboardImage));

        Button readButton = new Button("Read");
        readButton.setOnAction(event -> readClipboard());
        HBox header = new HBox(10.0, cardTitle("System clipboard now"), readButton);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10.0, header, formats);
        card.getStyleClass().add("preview-card");
        card.setMaxWidth(CARD_WIDTH);
        return card;
    }

    // Read on demand only, never at startup: the showcase does not display
    // whatever the user copied before opening it.
    private void readClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        clipboardText.setText(clipboard.hasString() ? shorten(clipboard.getString()) : NONE);
        clipboardHtml.setText(clipboard.hasHtml() ? shorten(clipboard.getHtml()) : NONE);
        Image image = clipboard.hasImage() ? clipboard.getImage() : null;
        clipboardImage.setImage(image);
        clipboardImageInfo.setText(image == null
                ? NONE
                : (int) image.getWidth() + " × " + (int) image.getHeight());
    }

    private static String shorten(String text) {
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private Node nothingCard(String reason) {
        Label explanation = new Label(reason + " Nothing is copied: the clipboard keeps its"
                + " previous content, a showing feedback is cleared, and onAction still runs.");
        explanation.setWrapText(true);
        return card("Nothing to copy", explanation);
    }

    private static Node card(String title, Node body) {
        VBox card = new VBox(10.0, cardTitle(title), body);
        card.setFillWidth(true);
        return card;
    }

    private static Label cardTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-title");
        return label;
    }

    private static Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label monoLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("mono-label");
        label.setWrapText(true);
        return label;
    }

    // The format tables of both cards line up the same way.
    private static GridPane formatGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(6.0);
        return grid;
    }

    // ==================== Sections ====================

    private Node buildContentGrid() {
        Map<String, Source> sources = createSources();
        ComboBox<String> sourceBox = new ComboBox<>();
        sourceBox.getItems().setAll(sources.keySet());
        sourceBox.setMaxWidth(Double.MAX_VALUE);
        sourceBox.valueProperty().addListener((obs, oldV, newV) -> {
            Source source = sources.get(newV);
            button.setContentFactory(source.factory());
            sourceCard.getChildren().setAll(source.card());
            // The feedback belongs to the previous source.
            button.resetFeedback();
        });
        sourceBox.setValue("Plain text");

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
        Label durationValue = createValueLabel(durationSlider, "%.0f ms");
        CheckBox indefiniteBox = new CheckBox();
        durationSlider.disableProperty().bind(indefiniteBox.selectedProperty());
        durationValue.disableProperty().bind(indefiniteBox.selectedProperty());
        Runnable applyDuration = () -> button.setFeedbackDuration(indefiniteBox.isSelected()
                ? Duration.INDEFINITE
                : Duration.millis(durationSlider.getValue()));
        durationSlider.valueProperty().addListener((obs, oldV, newV) -> applyDuration.run());
        indefiniteBox.selectedProperty().addListener((obs, oldV, newV) -> applyDuration.run());

        // Null and an empty field both show the check mark only.
        TextField copiedTextField = new TextField(button.getCopiedText());
        copiedTextField.setPromptText("empty = check mark only");
        CheckBox nullTextBox = new CheckBox();
        copiedTextField.disableProperty().bind(nullTextBox.selectedProperty());
        Runnable applyCopiedText = () -> button.setCopiedText(nullTextBox.isSelected()
                ? null
                : copiedTextField.getText());
        copiedTextField.textProperty().addListener((obs, oldV, newV) -> applyCopiedText.run());
        nullTextBox.selectedProperty().addListener((obs, oldV, newV) -> applyCopiedText.run());

        // Each face needs its own node: one node cannot be shown on both.
        Region copiedStar = createStar();
        CheckBox copiedGraphicBox = new CheckBox();
        copiedGraphicBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setCopiedGraphic(newV ? copiedStar : null));

        Button resetButton = new Button("resetFeedback()");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(event -> button.resetFeedback());

        return createGrid(
                row("Duration", durationSlider, durationValue),
                row("Indefinite", indefiniteBox),
                row("Copied text", copiedTextField),
                row("Null text", nullTextBox),
                row("Copied graphic", copiedGraphicBox),
                row("Reset", resetButton));
    }

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

    private Node buildButtonGrid() {
        TextField textField = new TextField(button.getText());
        textField.setPromptText("empty = icon only (Null text keeps it narrow)");
        button.textProperty().bind(textField.textProperty());

        CheckBox hideIndicatorBox = new CheckBox();
        hideIndicatorBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                button.getStyleClass().add("no-indicator");
            } else {
                button.getStyleClass().remove("no-indicator");
            }
        });

        Region star = createStar();
        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setGraphic(newV ? star : null));

        // The button is as wide as its wider face, so the narrower face shows
        // the alignment (e.g. "Copy" against "Copied to clipboard").
        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT);
        alignmentBox.setValue(Pos.CENTER);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        button.alignmentProperty().bind(alignmentBox.valueProperty());

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
                row("Graphic", graphicBox),
                row("Alignment", alignmentBox),
                row("Disabled", disableBox),
                row("Right to left", rtlBox));
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
