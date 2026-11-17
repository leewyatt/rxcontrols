package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.samples.demo.RXBorderBeamDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXBorderBeam}.
 *
 * <p>Exercises every public knob on one live beam round a card: on and off,
 * showing only under the pointer, the number of beams, their size, the line
 * width, the outset, the lap time and the fill (set from a style sheet), then
 * the corners, which either follow the card's own radius or are set
 * explicitly, the card's size, padding on the wrapper and a right-to-left
 * switch that reverses the direction. A button and a text field below the card
 * carry beams of their own and stay fully usable.
 *
 * <p>For a minimal "few lines of code" example see {@link RXBorderBeamDemo}.
 */
public class RXBorderBeamShowcase extends RXShowcaseApplication {

    private static final List<String> FILL_CLASSES = List.of("theme", "solid", "sunset", "aurora", "nebula");
    private static final double CARD_WIDTH = 320.0;
    private static final double CARD_HEIGHT = 150.0;
    private static final double CARD_RADIUS = 10.0;

    private RXBorderBeam beam;
    private Region card;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBorderBeam";
    }

    @Override
    protected String subtitle() {
        return "Fading highlight that runs round the border of its content";
    }

    @Override
    protected String windowTitle() {
        return "RXBorderBeam Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 720.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 440.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-border-beam-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        Label heading = new Label("Workspace overview");
        heading.getStyleClass().add("card-title");
        Label body = new Label("Review task status, deployment health, and recent automation activity in one panel.");
        body.setWrapText(true);
        VBox content = new VBox(10.0, heading, body);
        content.getStyleClass().add("sample-card");
        content.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        content.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        content.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card = content;
        beam = new RXBorderBeam(card);

        RXBorderBeam onButton = new RXBorderBeam(new Button("Upgrade"));
        onButton.setBeamSize(40.0);
        onButton.setCycleDuration(Duration.seconds(3.0));
        RXBorderBeam onField = new RXBorderBeam(new TextField("Still editable"));
        onField.setBeamSize(40.0);
        onField.setCycleDuration(Duration.seconds(4.0));
        HBox controls = new HBox(24.0, onButton, onField);
        controls.setAlignment(Pos.CENTER);

        VBox preview = new VBox(48.0, beam, controls);
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Beam", buildBeamGrid()),
                section("Geometry", buildGeometryGrid()),
                section("Layout", buildLayoutGrid()));
    }

    // ==================== Sections ====================

    private Node buildBeamGrid() {
        CheckBox active = new CheckBox("Active");
        active.setSelected(true);
        CheckBox onHover = new CheckBox("Only while the pointer is over the card");
        beam.activeProperty().bind(active.selectedProperty()
                .and(onHover.selectedProperty().not().or(beam.hoverProperty())));

        ComboBox<String> fillBox = new ComboBox<>();
        fillBox.getItems().setAll(FILL_CLASSES);
        fillBox.setValue("theme");
        fillBox.setMaxWidth(Double.MAX_VALUE);
        fillBox.valueProperty().addListener((obs, oldV, newV) -> {
            beam.getStyleClass().removeAll(FILL_CLASSES);
            beam.getStyleClass().add(newV);
        });

        Slider count = createSlider(0.0, 6.0, beam.getBeamCount());
        count.setMajorTickUnit(1.0);
        count.setMinorTickCount(0);
        count.setSnapToTicks(true);
        beam.beamCountProperty().bind(Bindings.createIntegerBinding(
                () -> (int) Math.round(count.getValue()), count.valueProperty()));
        Label countValue = createValueLabel(count, "%.0f");

        Slider size = createSlider(10.0, 320.0, beam.getBeamSize());
        beam.beamSizeProperty().bind(size.valueProperty());
        Label sizeValue = createValueLabel(size, "%.0f px");

        Slider lineWidth = createSlider(0.5, 8.0, beam.getLineWidth());
        beam.lineWidthProperty().bind(lineWidth.valueProperty());
        Label lineWidthValue = createValueLabel(lineWidth, "%.1f px");

        Slider outset = createSlider(-10.0, 20.0, beam.getOutset());
        beam.outsetProperty().bind(Bindings.createDoubleBinding(
                () -> (double) Math.round(outset.getValue()), outset.valueProperty()));
        Label outsetValue = createValueLabel(outset, "%.0f px");

        Slider duration = createSlider(0.5, 20.0, beam.getCycleDuration().toSeconds());
        beam.cycleDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.seconds(duration.getValue()), duration.valueProperty()));
        Label durationValue = createValueLabel(duration, "%.1f s");

        return createGrid(
                row(active),
                row(onHover),
                row("Fill class", fillBox),
                row("Beam count", count, countValue),
                row("Beam size", size, sizeValue),
                row("Line width", lineWidth, lineWidthValue),
                row("Outset", outset, outsetValue),
                row("Cycle duration", duration, durationValue));
    }

    private Node buildGeometryGrid() {
        Slider cardRadius = createSlider(0.0, 80.0, CARD_RADIUS);
        card.styleProperty().bind(Bindings.createStringBinding(() -> {
            long radius = Math.round(cardRadius.getValue());
            return "-fx-background-radius: " + radius + "px; -fx-border-radius: " + radius + "px;";
        }, cardRadius.valueProperty()));
        Label cardRadiusValue = createValueLabel(cardRadius, "%.0f px");

        CheckBox explicit = new CheckBox("Set the beam's corner radius explicitly");
        Slider beamRadius = createSlider(0.0, 80.0, CARD_RADIUS);
        beamRadius.disableProperty().bind(explicit.selectedProperty().not());
        beam.cornerRadiusProperty().bind(Bindings.createObjectBinding(
                () -> explicit.isSelected() ? new CornerRadii(Math.round(beamRadius.getValue())) : null,
                explicit.selectedProperty(), beamRadius.valueProperty()));
        Label beamRadiusValue = createValueLabel(beamRadius, "%.0f px");

        return createGrid(
                row("Card radius", cardRadius, cardRadiusValue),
                row(explicit),
                row("Beam radius", beamRadius, beamRadiusValue));
    }

    private Node buildLayoutGrid() {
        Slider width = createSlider(40.0, 420.0, CARD_WIDTH);
        card.prefWidthProperty().bind(width.valueProperty());
        Label widthValue = createValueLabel(width, "%.0f px");

        Slider height = createSlider(20.0, 320.0, CARD_HEIGHT);
        card.prefHeightProperty().bind(height.valueProperty());
        Label heightValue = createValueLabel(height, "%.0f px");

        Slider padding = createSlider(0.0, 24.0, 0.0);
        beam.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(Math.round(padding.getValue())), padding.valueProperty()));
        Label paddingValue = createValueLabel(padding, "%.0f px");

        CheckBox rightToLeft = new CheckBox("Right to left");
        beam.nodeOrientationProperty().bind(Bindings.when(rightToLeft.selectedProperty())
                .then(NodeOrientation.RIGHT_TO_LEFT)
                .otherwise(NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Card width", width, widthValue),
                row("Card height", height, heightValue),
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
