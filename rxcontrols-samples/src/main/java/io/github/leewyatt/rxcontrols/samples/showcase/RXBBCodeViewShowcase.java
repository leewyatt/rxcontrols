package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeImagePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeUrlPolicy;
import io.github.leewyatt.rxcontrols.samples.demo.RXBBCodeViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Showcase application for {@link RXBBCodeView}.
 *
 * <p>Exposes every configurable input: the {@code content} markup (a live two-way
 * {@link TextArea}), the {@code lenient} / {@code showMalformedTagsAsText} parsing flags,
 * {@code maxNestingDepth} (lower it to trip {@code MAX_DEPTH_EXCEEDED}, {@code -1} to
 * disable the guard), the immutable security {@code policy} broken into its parts (URL /
 * image scheme allow-lists, {@code loadImages} — each change rebuilds the whole
 * {@link RXBBCodePolicy}), {@code paragraphSpacing}, {@code maxFontSize}, and
 * the {@code imageMaxWidth} / {@code imageMaxHeight} ceilings ({@code -1} = unset). Read-only
 * warning count and the last-activated link are shown live. For a minimal example see
 * {@link RXBBCodeViewDemo}.</p>
 */
public class RXBBCodeViewShowcase extends RXShowcaseApplication {

    private static final String SAMPLE = """
            [h1]RXBBCodeView[/h1]
            A [b]safe[/b], [i]bindable[/i] BBCode renderer — no HTML, no scripting. Edit this \
            markup on the right and watch it re-render live.

            [h2]Inline styles[/h2]
            [b]bold[/b] · [i]italic[/i] · [u]underline[/u] · [s]strike[/s] · \
            [color=#e53935]hex red[/color] · [color=teal]named teal[/color] · \
            [size=20]size 20[/size] · [font=Monospaced]monospaced[/font]

            Untrusted [size=240]size 240[/size] is capped at maxFontSize, so it cannot explode layout.

            [h2]Headings[/h2]
            [h3]h3 heading[/h3]
            [h4]h4 heading[/h4]

            [h2]Links[/h2]
            Visit [url=https://github.com/leewyatt/rxcontrols]the project[/url] or write to \
            [email]hello@example.com[/email].

            [h2]Background block[/h2]
            [bgcolor=#fff3cd]A whole block tinted with [b]bgcolor[/b] — applied via a typed \
            setBackground, never inline CSS.[/bgcolor]

            [h2]Quote & spoiler[/h2]
            [quote=Docs]Lower Max depth, or toggle Load images, to see the effect.[/quote]
            [spoiler=Reveal]Hidden until you click the header.[/spoiler]

            [h2]Lists[/h2]
            [list]
            [*]Unordered item one
            [*]Unordered item two
            [/list]
            [ol]
            [*]Ordered item one
            [*]Ordered item two
            [/ol]

            [h2]Table[/h2]
            [table]
            [tr][th]Tag[/th][th]Purpose[/th][/tr]
            [tr][td]b / i / u / s[/td][td]text style[/td][/tr]
            [tr][td]color / size / font[/td][td]fill · size · family[/td][/tr]
            [/table]

            [h2]Code block & rule[/h2]
            [code]var view = new RXBBCodeView(markup);[/code]
            [hr]

            [h2]Image[/h2]
            [img width=180 height=60 alt=picsum]https://picsum.photos/seed/rx/420/140[/img]
            """;

    private RXBBCodeView view;
    private Label lastLink;

    // Policy inputs — every change rebuilds the immutable RXBBCodePolicy from scratch.
    private CheckBox urlHttp;
    private CheckBox urlHttps;
    private CheckBox urlMailto;
    private CheckBox urlJavascript;
    private CheckBox imageHttp;
    private CheckBox imageHttps;
    private CheckBox imageData;
    private CheckBox loadImages;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBBCodeView";
    }

    @Override
    protected String subtitle() {
        return "Sandboxed BBCode renderer";
    }

    @Override
    protected String windowTitle() {
        return "RXBBCodeView Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-bbcode-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        view = new RXBBCodeView(SAMPLE);

        // Created here so the link handler is wired with the preview, but shown in the
        // read-only section (a Node has a single parent, so it lives in one place only).
        lastLink = new Label("(none)");
        lastLink.getStyleClass().add("value-label");
        view.setOnLinkActivated(event ->
                lastLink.setText(event.getHref() + "  (" + event.getLinkKind() + ")"));

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("bbcode-preview-scroll");

        VBox box = new VBox(scroll);
        box.getStyleClass().add("bbcode-preview");
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Content", buildContentGrid()),
                section("Parsing", buildParsingGrid()),
                section("Policy", buildPolicyGrid()),
                section("Layout", buildLayoutGrid()),
                section("Read-only", buildReadonlyGrid()));
    }

    // ==================== Sections ====================

    private Node buildContentGrid() {
        TextArea content = new TextArea();
        content.getStyleClass().add("content-area");
        content.setPrefRowCount(10);
        content.setWrapText(true);
        content.textProperty().bindBidirectional(view.contentProperty());
        return createGrid(row(content));
    }

    private Node buildParsingGrid() {
        CheckBox lenient = new CheckBox("lenient");
        lenient.selectedProperty().bindBidirectional(view.lenientProperty());

        CheckBox showMalformed = new CheckBox("showMalformedTagsAsText");
        showMalformed.selectedProperty().bindBidirectional(view.showMalformedTagsAsTextProperty());

        SpinnerValueFactory.IntegerSpinnerValueFactory depthFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-1, 512, view.getMaxNestingDepth());
        // A cleared / non-numeric editor commit falls back to the current value instead of
        // parsing to null (which would NPE the default IntegerSpinnerValueFactory clamp).
        depthFactory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    return Integer.valueOf(text.trim());
                } catch (NumberFormatException invalid) {
                    return depthFactory.getValue();
                }
            }
        });
        Spinner<Integer> depth = new Spinner<>(depthFactory);
        depth.setEditable(true);
        depth.setMaxWidth(Double.MAX_VALUE);
        depth.valueProperty().addListener((observable, old, value) -> view.setMaxNestingDepth(value));

        return createGrid(
                row(lenient),
                row(showMalformed),
                row("Max depth", depth));
    }

    private Node buildPolicyGrid() {
        RXBBCodePolicy current = view.getPolicy();
        Set<String> url = current.urlPolicy().allowedSchemes();
        Set<String> image = current.imagePolicy().allowedSchemes();

        urlHttp = schemeBox("http", url.contains("http"));
        urlHttps = schemeBox("https", url.contains("https"));
        urlMailto = schemeBox("mailto", url.contains("mailto"));
        urlJavascript = schemeBox("javascript", url.contains("javascript"));

        imageHttp = schemeBox("http", image.contains("http"));
        imageHttps = schemeBox("https", image.contains("https"));
        imageData = schemeBox("data", image.contains("data"));

        loadImages = new CheckBox("loadImages");
        loadImages.setSelected(current.imagePolicy().loadImages());
        loadImages.selectedProperty().addListener((observable, old, value) -> rebuildPolicy());

        return createGrid(
                row(new Label("URL schemes")),
                row(schemeRow(urlHttp, urlHttps, urlMailto, urlJavascript)),
                row(new Label("Image schemes")),
                row(schemeRow(imageHttp, imageHttps, imageData)),
                row(loadImages));
    }

    private Node buildLayoutGrid() {
        Slider paragraphSpacing = createSlider(0.0, 24.0, view.getParagraphSpacing());
        view.paragraphSpacingProperty().bind(paragraphSpacing.valueProperty());
        Label spacingValue = createValueLabel(paragraphSpacing, "%.0f px");

        Slider imageMaxWidth = createSlider(-1.0, 800.0, view.getImageMaxWidth());
        view.imageMaxWidthProperty().bind(imageMaxWidth.valueProperty());
        Label widthValue = ceilingValueLabel(imageMaxWidth);

        Slider imageMaxHeight = createSlider(-1.0, 800.0, view.getImageMaxHeight());
        view.imageMaxHeightProperty().bind(imageMaxHeight.valueProperty());
        Label heightValue = ceilingValueLabel(imageMaxHeight);

        return createGrid(
                row("Paragraph gap", paragraphSpacing, spacingValue),
                row("Image max W", imageMaxWidth, widthValue),
                row("Image max H", imageMaxHeight, heightValue));
    }

    private Node buildReadonlyGrid() {
        Label warnings = new Label();
        warnings.getStyleClass().add("value-label");
        warnings.textProperty().bind(Bindings.createStringBinding(
                () -> view.getWarnings().size() + " warning(s)", view.warningsProperty()));

        lastLink.setWrapText(true);
        return createGrid(
                row("Warnings", warnings),
                row("Last link", lastLink));
    }

    // ==================== Helpers ====================

    private CheckBox schemeBox(String scheme, boolean selected) {
        CheckBox box = new CheckBox(scheme);
        box.setSelected(selected);
        box.selectedProperty().addListener((observable, old, value) -> rebuildPolicy());
        return box;
    }

    private static FlowPane schemeRow(CheckBox... boxes) {
        FlowPane pane = new FlowPane(12.0, 6.0, boxes);
        return pane;
    }

    private void rebuildPolicy() {
        Set<String> urlSchemes = collectSchemes(urlHttp, urlHttps, urlMailto, urlJavascript);
        Set<String> imageSchemes = collectSchemes(imageHttp, imageHttps, imageData);
        RXBBCodePolicy policy = new RXBBCodePolicy(
                new RXBBCodeUrlPolicy(urlSchemes),
                new RXBBCodeImagePolicy(imageSchemes, loadImages.isSelected()));
        view.setPolicy(policy);
    }

    private static Set<String> collectSchemes(CheckBox... boxes) {
        Set<String> schemes = new LinkedHashSet<>();
        for (CheckBox box : boxes) {
            if (box.isSelected()) {
                schemes.add(box.getText());
            }
        }
        return schemes;
    }

    private Label ceilingValueLabel(Slider slider) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        // A negative ceiling (USE_COMPUTED_SIZE) means "no upper bound".
        label.textProperty().bind(Bindings.createStringBinding(() -> {
            double value = slider.getValue();
            return value < 0 ? "unset" : String.format("%.0f px", value);
        }, slider.valueProperty()));
        return label;
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
