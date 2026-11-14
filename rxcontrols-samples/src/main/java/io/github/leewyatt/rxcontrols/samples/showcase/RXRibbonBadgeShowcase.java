package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXRibbonBadge.Position;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXRibbonBadge}.
 *
 * <p>Exercises every public knob on one live ribbon over a card: text (empty
 * hides the ribbon, a long one is cut at the far side of the card), position,
 * offset from the top, overhang with its folded corner, the color classes,
 * padding that reserves room for the overhang, the card width and a
 * right-to-left switch that mirrors start and end.
 */
public class RXRibbonBadgeShowcase extends RXShowcaseApplication {

    private static final List<String> COLOR_CLASSES = List.of("primary", "success", "warning", "danger", "info");
    private static final double CARD_WIDTH = 260.0;

    private RXRibbonBadge ribbon;
    private Region card;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXRibbonBadge";
    }

    @Override
    protected String subtitle() {
        return "Text ribbon folded over the edge of a card";
    }

    @Override
    protected String windowTitle() {
        return "RXRibbonBadge Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 980.0;
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
        return getClass().getResource("rx-ribbon-badge-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        Label heading = new Label("Pro plan");
        heading.getStyleClass().add("card-title");
        Label body = new Label("Unlimited projects, priority support and early access to new features.");
        body.setWrapText(true);
        VBox content = new VBox(8.0, heading, body);
        content.getStyleClass().add("sample-card");
        content.setPrefWidth(CARD_WIDTH);
        content.setMaxWidth(Region.USE_PREF_SIZE);
        card = content;

        ribbon = new RXRibbonBadge(card, "Recommended");
        return ribbon;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Ribbon", buildRibbonGrid()),
                section("Layout", buildLayoutGrid()));
    }

    // ==================== Sections ====================

    private Node buildRibbonGrid() {
        TextField textField = new TextField(ribbon.getText());
        textField.setPromptText("Empty hides the ribbon");
        ribbon.textProperty().bind(textField.textProperty());

        ComboBox<Position> positionBox = new ComboBox<>();
        positionBox.getItems().setAll(Position.values());
        positionBox.setValue(ribbon.getPosition());
        positionBox.setMaxWidth(Double.MAX_VALUE);
        ribbon.positionProperty().bind(positionBox.valueProperty());

        ComboBox<String> colorBox = new ComboBox<>();
        colorBox.getItems().setAll(COLOR_CLASSES);
        colorBox.setValue("primary");
        colorBox.setMaxWidth(Double.MAX_VALUE);
        colorBox.valueProperty().addListener((obs, oldV, newV) -> {
            ribbon.getStyleClass().removeAll(COLOR_CLASSES);
            ribbon.getStyleClass().add(newV);
        });

        Slider offset = createSlider(0.0, 60.0, ribbon.getRibbonOffset());
        ribbon.ribbonOffsetProperty().bind(offset.valueProperty());
        Label offsetValue = createValueLabel(offset, "%.0f px");

        Slider overhang = createSlider(0.0, 20.0, ribbon.getOverhang());
        ribbon.overhangProperty().bind(overhang.valueProperty());
        Label overhangValue = createValueLabel(overhang, "%.0f px");

        return createGrid(
                row("Text", textField),
                row("Position", positionBox),
                row("Color class", colorBox),
                row("Ribbon offset", offset, offsetValue),
                row("Overhang", overhang, overhangValue));
    }

    private Node buildLayoutGrid() {
        Slider width = createSlider(80.0, 360.0, CARD_WIDTH);
        card.prefWidthProperty().bind(width.valueProperty());
        Label widthValue = createValueLabel(width, "%.0f px");

        Slider padding = createSlider(0.0, 24.0, 0.0);
        ribbon.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(Math.round(padding.getValue())), padding.valueProperty()));
        Label paddingValue = createValueLabel(padding, "%.0f px");

        CheckBox rightToLeft = new CheckBox("Right to left");
        ribbon.nodeOrientationProperty().bind(Bindings.when(rightToLeft.selectedProperty())
                .then(NodeOrientation.RIGHT_TO_LEFT)
                .otherwise(NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Card width", width, widthValue),
                row("Padding", padding, paddingValue),
                row(rightToLeft));
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
