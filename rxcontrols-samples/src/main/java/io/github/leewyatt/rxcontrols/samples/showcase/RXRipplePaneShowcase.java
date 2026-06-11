package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXRipplePane}.
 *
 * <p>Exercises the public V1 ripple properties and bounded rounded clipping on
 * a single-content card.</p>
 */
public class RXRipplePaneShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 360.0;
    private static final double PREVIEW_HEIGHT = 220.0;

    private RXRipplePane ripplePane;
    private StackPane contentCard;
    private ColorPicker rippleFillPicker;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXRipplePane";
    }

    @Override
    protected String subtitle() {
        return "Bounded pressed feedback for a single content node";
    }

    @Override
    protected String windowTitle() {
        return "RXRipplePane Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-ripple-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        contentCard = new StackPane(createCardContent());
        contentCard.getStyleClass().add("content-card");

        ripplePane = new RXRipplePane(contentCard);
        ripplePane.getStyleClass().add("showcase-ripple-pane");
        ripplePane.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        ripplePane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        ripplePane.setRippleFill(Color.web("#0f766e"));
        ripplePane.setRippleOpacity(0.18);
        updateRadius(18.0);

        Label sizeLabel = new Label();
        sizeLabel.getStyleClass().add("size-label");
        sizeLabel.textProperty().bind(ripplePane.widthProperty().asString("pane %.0f")
                .concat(" x ")
                .concat(ripplePane.heightProperty().asString("%.0f")));

        VBox preview = new VBox(14.0, ripplePane, sizeLabel);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Ripple", buildRippleGrid()),
                section("Shape", buildShapeGrid()),
                section("Geometry", buildGeometryGrid()));
    }

    // ==================== Sections ====================

    private Node buildRippleGrid() {
        rippleFillPicker = new ColorPicker(Color.web("#0f766e"));
        rippleFillPicker.setMaxWidth(Double.MAX_VALUE);
        rippleFillPicker.setOnAction(event -> ripplePane.setRippleFill(rippleFillPicker.getValue()));

        Slider opacitySlider = createSlider(0.0, 0.4, ripplePane.getRippleOpacity());
        ripplePane.rippleOpacityProperty().bind(opacitySlider.valueProperty());

        CheckBox enabledBox = new CheckBox();
        enabledBox.setSelected(ripplePane.isRippleEnabled());
        ripplePane.rippleEnabledProperty().bind(enabledBox.selectedProperty());

        CheckBox centeredBox = new CheckBox();
        centeredBox.setSelected(ripplePane.isRippleCentered());
        ripplePane.rippleCenteredProperty().bind(centeredBox.selectedProperty());

        Slider insetSlider = createSlider(-12, 40.0, 0.0);
        insetSlider.valueProperty().addListener((obs, oldV, newV) ->
                ripplePane.setRippleInsets(new Insets(newV.doubleValue())));

        return createGrid(
                row("Fill", rippleFillPicker),
                row("Opacity", opacitySlider, createValueLabel(opacitySlider, "%.2f")),
                row("Enabled", enabledBox),
                row("Centered", centeredBox),
                row("Inset", insetSlider, createValueLabel(insetSlider, "%.0f px")));
    }

    private Node buildShapeGrid() {
        Slider radiusSlider = createSlider(0.0, 80.0, 18.0);
        radiusSlider.valueProperty().addListener((obs, oldV, newV) ->
                updateRadius(newV.doubleValue()));

        return createGrid(
                row("Radius", radiusSlider, createValueLabel(radiusSlider, "%.0f px")));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(220.0, 520.0, PREVIEW_WIDTH);
        Slider heightSlider = createSlider(130.0, 320.0, PREVIEW_HEIGHT);
        ripplePane.prefWidthProperty().bind(widthSlider.valueProperty());
        ripplePane.prefHeightProperty().bind(heightSlider.valueProperty());

        return createGrid(
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")),
                row("Height", heightSlider, createValueLabel(heightSlider, "%.0f px")));
    }

    // ==================== Preview assembly ====================

    private Node createCardContent() {
        Label eyebrow = new Label("NOVA TEAM");
        eyebrow.getStyleClass().add("card-eyebrow");

        Label title = new Label("Incident response queue");
        title.getStyleClass().add("card-title");

        Label copy = new Label("Triage, owner assignment, and recovery status in one compact surface.");
        copy.getStyleClass().add("card-copy");
        copy.setWrapText(true);

        Label count = new Label("18");
        count.getStyleClass().add("metric-number");
        Label countText = new Label("open");
        countText.getStyleClass().add("metric-label");
        VBox countBox = new VBox(1.0, count, countText);
        countBox.getStyleClass().add("metric-box");

        Label sla = new Label("92%");
        sla.getStyleClass().add("metric-number");
        Label slaText = new Label("SLA");
        slaText.getStyleClass().add("metric-label");
        VBox slaBox = new VBox(1.0, sla, slaText);
        slaBox.getStyleClass().add("metric-box");

        HBox metrics = new HBox(10.0, countBox, slaBox);
        metrics.getStyleClass().add("metric-row");

        Label owner = new Label("ON CALL");
        owner.getStyleClass().addAll("status-chip", "status-on-call");
        Label region = new Label("APAC");
        region.getStyleClass().addAll("status-chip", "status-region");
        HBox chips = new HBox(8.0, owner, region);
        chips.getStyleClass().add("chip-row");

        VBox content = new VBox(14.0, eyebrow, title, copy, metrics, chips);
        content.getStyleClass().add("card-body");
        return content;
    }

    private void updateRadius(double radius) {
        String style = String.format(Locale.ROOT,
                "-fx-background-radius: %.0fpx; -fx-border-radius: %.0fpx;",
                radius, radius);
        ripplePane.setStyle(style);
        contentCard.setStyle(String.format(Locale.ROOT,
                "-fx-background-radius: %.0fpx;", radius));
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
