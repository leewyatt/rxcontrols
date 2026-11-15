package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One component page: a header with the name, the base class, a one-line description and
 * the demo launcher, a Playground / CSS switch, a variant switch when the page groups
 * several controls, and the body. Each variant's playground and CSS reference are built
 * the first time they are shown and then kept for the life of the page.
 */
final class SamplerPage extends BorderPane {

    private static final Logger LOG = Logger.getLogger(SamplerPage.class.getName());

    private enum Tab {
        PLAYGROUND("Playground"),
        CSS("CSS");

        private final String text;

        Tab(String text) {
            this.text = text;
        }
    }

    private final Map<SamplerVariant, Node> playgrounds = new HashMap<>();
    private final Map<SamplerVariant, Node> cssViews = new HashMap<>();
    private final Map<SamplerVariant, String> descriptions = new HashMap<>();

    private final Label title = new Label();
    private final Label chip = new Label();
    private final Label description = new Label();
    private final Button demoButton = new Button("Open demo");
    private final RXSegmentedControl<Tab> tabs = new RXSegmentedControl<>();
    private final StackPane body = new StackPane();

    private SamplerVariant variant;

    SamplerPage(SamplerEntry entry) {
        getStyleClass().add("sampler-page");
        title.getStyleClass().add("page-title");
        chip.getStyleClass().add("chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        description.getStyleClass().add("page-description");
        description.setWrapText(true);
        body.getStyleClass().add("page-body");

        Region demoGlyph = new Region();
        demoGlyph.getStyleClass().add("open-glyph");
        demoGlyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        demoButton.getStyleClass().add("demo-button");
        demoButton.setGraphic(demoGlyph);
        demoButton.setMinWidth(Region.USE_PREF_SIZE);
        demoButton.setOnAction(event -> openDemo(variant));

        for (Tab tab : Tab.values()) {
            tabs.getItems().add(new RXSegmentedItem<>(tab, tab.text));
        }
        tabs.setValue(Tab.PLAYGROUND);
        tabs.valueProperty().addListener((obs, old, tab) -> showBody());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10.0, title, chip, spacer, demoButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox switches = new HBox(16.0, tabs);
        switches.setAlignment(Pos.CENTER_LEFT);
        if (entry.variants().size() > 1) {
            switches.getChildren().add(buildVariantSwitch(entry));
        }

        VBox header = new VBox(6.0, titleRow, description, switches);
        VBox.setMargin(switches, new Insets(10.0, 0.0, 0.0, 0.0));
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

    // ==================== Content ====================

    private void showVariant(SamplerVariant picked) {
        variant = picked;
        playgrounds.computeIfAbsent(picked, this::buildPlayground);
        title.setText(picked.typeName());
        chip.setText(lineage(picked.type()));
        description.setText(descriptions.get(picked));
        demoButton.setVisible(picked.demo() != null);
        demoButton.setManaged(picked.demo() != null);
        showBody();
    }

    private void showBody() {
        if (tabs.getValue() == Tab.CSS) {
            Node playground = playgrounds.get(variant);
            body.getChildren().setAll(cssViews.computeIfAbsent(variant, v -> CssReference.create(v, playground)));
        } else {
            body.getChildren().setAll(playgrounds.get(variant));
        }
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

    // ==================== Demo ====================

    private static void openDemo(SamplerVariant v) {
        if (v == null || v.demo() == null) {
            return;
        }
        Class<? extends Application> type = v.demo();
        try {
            Application demo = type.getDeclaredConstructor().newInstance();
            Stage stage = new Stage();
            // The demo builds its own scene; it joins the theme the sampler windows share.
            stage.sceneProperty().addListener((obs, old, scene) -> ShowcaseThemes.register(scene));
            demo.start(stage);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not start " + type.getName(), e);
            new Alert(Alert.AlertType.ERROR, "Could not start " + type.getSimpleName() + ":\n" + e).show();
        }
    }
}
