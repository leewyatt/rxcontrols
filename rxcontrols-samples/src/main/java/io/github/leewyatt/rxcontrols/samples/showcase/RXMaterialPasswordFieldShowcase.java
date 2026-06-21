package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase for {@link RXMaterialPasswordField}.
 *
 * <p>Exercises the shared Material properties (floating label + scale,
 * animation, invalid / helper / error, leading node, clear button) plus the
 * password-specific reveal-button switch and echo character; reveal itself is
 * toggled via the in-pane eye button. The theme bar (Modena / dark / AtlantaFX)
 * shows the role-token theming; the text sibling has its own
 * {@link RXMaterialTextFieldShowcase}.</p>
 */
public class RXMaterialPasswordFieldShowcase extends RXShowcaseApplication {

    private RXMaterialPasswordField field;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMaterialPasswordField";
    }

    @Override
    protected String subtitle() {
        return "Floating label, mask / reveal, supporting text, clear button";
    }

    @Override
    protected String windowTitle() {
        return "RXMaterialPasswordField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-material-password-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXMaterialPasswordField("s3cr3t-pw");
        field.setLabelText("Password");
        field.setHelperText("At least 8 characters");
        field.setLeadingNode(icon("lock-icon"));
        field.setPrefColumnCount(18);

        VBox preview = new VBox(22.0, field);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER_LEFT);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Content", buildContentGrid()),
                section("Variant & state", buildStateGrid()),
                section("Animation", buildAnimationGrid()),
                section("Password", buildPasswordGrid()));
    }

    // ==================== Sections ====================

    private Node buildContentGrid() {
        TextField labelBox = new TextField(field.getLabelText());
        labelBox.setMaxWidth(Double.MAX_VALUE);
        field.labelTextProperty().bind(labelBox.textProperty());

        TextField helperBox = new TextField(field.getHelperText());
        helperBox.setMaxWidth(Double.MAX_VALUE);
        field.helperTextProperty().bind(helperBox.textProperty());

        TextField errorBox = new TextField();
        errorBox.setMaxWidth(Double.MAX_VALUE);
        errorBox.setPromptText("shown when invalid");
        field.errorTextProperty().bind(errorBox.textProperty());

        return createGrid(
                row("Label", labelBox),
                row("Helper", helperBox),
                row("Error", errorBox));
    }

    private Node buildStateGrid() {
        CheckBox floatingBox = new CheckBox();
        floatingBox.setSelected(field.isFloatingLabel());
        field.floatingLabelProperty().bind(floatingBox.selectedProperty());

        CheckBox invalidBox = new CheckBox();
        field.invalidProperty().bind(invalidBox.selectedProperty());

        CheckBox clearBox = new CheckBox();
        clearBox.setSelected(field.isShowClearButton());
        field.showClearButtonProperty().bind(clearBox.selectedProperty());

        CheckBox editableBox = new CheckBox();
        editableBox.setSelected(field.isEditable());
        field.editableProperty().bind(editableBox.selectedProperty());

        CheckBox disableBox = new CheckBox();
        field.disableProperty().bind(disableBox.selectedProperty());

        return createGrid(
                row("Floating label", floatingBox),
                row("Invalid", invalidBox),
                row("Clear button", clearBox),
                row("Editable", editableBox),
                row("Disabled", disableBox));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox();
        animatedBox.setSelected(field.isAnimated());
        field.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider = createSlider(0.0, 600.0, field.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                field.setAnimationDuration(Duration.millis(newV.doubleValue())));

        Slider scaleSlider = createSlider(0.5, 1.0, field.getLabelFloatScale());
        scaleSlider.valueProperty().addListener((obs, oldV, newV) ->
                field.setLabelFloatScale(newV.doubleValue()));

        return createGrid(
                row("Animated", animatedBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Label scale", scaleSlider, createValueLabel(scaleSlider, "%.2f")));
    }

    private Node buildPasswordGrid() {

        CheckBox revealButtonBox = new CheckBox();
        revealButtonBox.setSelected(field.isShowRevealButton());
        field.showRevealButtonProperty().bind(revealButtonBox.selectedProperty());

        ComboBox<Character> echoBox = new ComboBox<>();
        echoBox.getItems().setAll('●', '•', '*', '✱');
        echoBox.setValue(field.getEchoChar());
        echoBox.setMaxWidth(Double.MAX_VALUE);
        field.echoCharProperty().bind(echoBox.valueProperty());

        return createGrid(
                row("Reveal button", revealButtonBox),
                row("Echo char", echoBox));
    }

    // ==================== Helpers ====================

    private static Region icon(String styleClass) {
        Region region = new Region();
        region.getStyleClass().add(styleClass);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
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
