package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleCard;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleContent;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleGallery;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One component page: a header with the name, a one-line description and the launchers,
 * then the body.
 *
 * <p>A page with sample content shows example cards and a "when to use" note; its demo
 * and showcase open in their own windows. A page without it still hosts each variant's
 * showcase, with a switch between the variants. A variant's playground is built the first
 * time it is shown and then kept for the life of the page.
 */
final class SamplerPage extends BorderPane {

    private static final Logger LOG = Logger.getLogger(SamplerPage.class.getName());

    private final Map<SamplerVariant, Node> playgrounds = new HashMap<>();
    private final Map<SamplerVariant, String> descriptions = new HashMap<>();

    private final SamplerEntry entry;
    private final SampleContent samples;
    private final Label title = new Label();
    private final Label chip = new Label();
    private final Label description = new Label();
    private final Button demoButton = launcherButton("Open demo");
    private final Button showcaseButton = launcherButton("Open showcase");
    private final StackPane body = new StackPane();

    private Node examples;
    private SamplerVariant variant;

    SamplerPage(SamplerEntry entry) {
        this.entry = entry;
        this.samples = loadSamples(entry);
        getStyleClass().add("sampler-page");
        title.getStyleClass().add("page-title");
        chip.getStyleClass().add("chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        description.getStyleClass().add("page-description");
        description.setWrapText(true);
        body.getStyleClass().add("page-body");

        demoButton.setOnAction(event -> launch(variant.demo() == null ? null : () -> newInstance(variant.demo()),
                variant.typeName() + " demo"));
        showcaseButton.setOnAction(event -> launch(variant.showcase(), variant.typeName() + " showcase"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10.0, title, chip, spacer, showcaseButton, demoButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(6.0, titleRow, description);
        if (samples != null) {
            if (samples.stylesheet() != null) {
                getStylesheets().add(samples.stylesheet());
            }
            title.setText(entry.name());
            description.setText(samples.description());
            if (!samples.whenToUse().isEmpty()) {
                Node when = whenToUse(samples.whenToUse());
                header.getChildren().add(when);
                VBox.setMargin(when, new Insets(8.0, 0.0, 0.0, 0.0));
            }
            examples = buildExamples();
        }
        // The examples show every variant at once; only a showcase page switches between them.
        if (samples == null && entry.variants().size() > 1) {
            Node variantSwitch = buildVariantSwitch(entry);
            header.getChildren().add(variantSwitch);
            VBox.setMargin(variantSwitch, new Insets(10.0, 0.0, 0.0, 0.0));
        }
        header.getStyleClass().add("page-header");

        setTop(header);
        setCenter(body);
        showVariant(entry.variants().get(0));
    }

    private Node buildVariantSwitch(SamplerEntry entry) {
        RXSegmentedControl<SamplerVariant> variants = new RXSegmentedControl<>();
        variants.getStyleClass().add("variant-switch");
        for (SamplerVariant v : entry.variants()) {
            variants.getItems().add(new RXSegmentedItem<>(v, v.label()));
        }
        variants.setValue(entry.variants().get(0));
        variants.valueProperty().addListener((obs, old, picked) -> {
            if (picked != null) {
                showVariant(picked);
            }
        });
        return variants;
    }

    // ==================== Examples ====================

    private static SampleContent loadSamples(SamplerEntry entry) {
        if (entry.samples() == null) {
            return null;
        }
        try {
            return entry.samples().get();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not build the examples of " + entry.name(), e);
            return null;
        }
    }

    private Node buildExamples() {
        boolean single = entry.variants().size() == 1;
        for (SampleCard card : samples.cards()) {
            if (!single && card.getType() != null) {
                card.setLinks(cardLinks(card.getType()));
            }
        }
        VBox content = new VBox(SampleGallery.create(samples.cards()));
        content.getStyleClass().add("examples");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("examples-scroll");
        return scroll;
    }

    private Node[] cardLinks(Class<?> type) {
        for (SamplerVariant v : entry.variants()) {
            if (v.type() != type) {
                continue;
            }
            Hyperlink demo = new Hyperlink("Demo");
            demo.setOnAction(event -> launch(() -> newInstance(v.demo()), v.typeName() + " demo"));
            demo.setVisible(v.demo() != null);
            demo.setManaged(v.demo() != null);
            Hyperlink showcase = new Hyperlink("Showcase");
            showcase.setOnAction(event -> launch(v.showcase(), v.typeName() + " showcase"));
            showcase.setVisible(v.showcase() != null);
            showcase.setManaged(v.showcase() != null);
            return new Node[]{demo, showcase};
        }
        return new Node[0];
    }

    private static Node whenToUse(List<String> lines) {
        Label heading = new Label("When to use");
        heading.getStyleClass().add("heading");
        VBox box = new VBox(4.0, heading);
        for (String line : lines) {
            Region bullet = new Region();
            bullet.getStyleClass().add("bullet");
            bullet.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            Label text = new Label(line);
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            HBox row = new HBox(8.0, bullet, text);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
        box.getStyleClass().add("when-to-use");
        return box;
    }

    // ==================== Content ====================

    private void showVariant(SamplerVariant picked) {
        variant = picked;
        boolean launchers = samples == null || entry.variants().size() == 1;
        if (samples == null) {
            playgrounds.computeIfAbsent(picked, this::buildPlayground);
            title.setText(picked.typeName());
            description.setText(descriptions.get(picked));
        }
        chip.setText(entry.variants().size() == 1 || samples == null ? lineage(picked.type()) : "");
        chip.setVisible(!chip.getText().isEmpty());
        chip.setManaged(chip.isVisible());
        show(demoButton, launchers && picked.demo() != null);
        // A page without examples already hosts the showcase.
        show(showcaseButton, samples != null && launchers && picked.showcase() != null);
        body.getChildren().setAll(samples != null ? examples : playgrounds.get(variant));
    }

    private Node buildPlayground(SamplerVariant v) {
        if (v.showcase() == null) {
            descriptions.put(v, "Shown with its default settings; the demo has a working example.");
            return defaultPreview(v);
        }
        try {
            RXShowcaseApplication showcase = v.showcase().get();
            Node content = showcase.createEmbeddedContent();
            descriptions.put(v, showcase.getShowcaseSubtitle());
            return content;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not build the showcase of " + v.typeName(), e);
            descriptions.put(v, "");
            return message("The showcase of " + v.typeName() + " failed to start: " + e);
        }
    }

    private static Node defaultPreview(SamplerVariant v) {
        Node instance = SamplerInstances.create(v.type());
        if (instance == null) {
            return message(v.typeName() + " has no default instance to show.");
        }
        if (instance instanceof Region region) {
            region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        }
        StackPane preview = new StackPane(instance);
        preview.getStyleClass().add("preview-pane");
        return preview;
    }

    private static Node message(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("page-message");
        StackPane box = new StackPane(label);
        box.getStyleClass().add("preview-pane");
        return box;
    }

    private static String lineage(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        return parent == null || parent == Object.class ? "utility" : "extends " + parent.getSimpleName();
    }

    // ==================== Launchers ====================

    private static Button launcherButton(String text) {
        Region glyph = new Region();
        glyph.getStyleClass().add("open-glyph");
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Button button = new Button(text, glyph);
        button.getStyleClass().add("demo-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private static void show(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    private static Application newInstance(Class<? extends Application> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void launch(Supplier<? extends Application> factory, String name) {
        if (factory == null) {
            return;
        }
        try {
            Application app = factory.get();
            Stage stage = new Stage();
            // The app builds its own scene; it joins the theme the sampler windows share.
            stage.sceneProperty().addListener((obs, old, scene) -> ShowcaseThemes.register(scene));
            app.start(stage);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not start the " + name, e);
            new Alert(Alert.AlertType.ERROR, "Could not start the " + name + ":\n" + e).show();
        }
    }
}
