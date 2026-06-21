package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.enums.RXFieldVariant;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase for {@link RXMaterialTextField} / {@link RXMaterialPasswordField}.
 *
 * <p>Exercises every styleable and runtime property: variant (UNDERLINE /
 * FILLED), floating label + scale, animation toggle + duration, invalid /
 * helper / error supporting text, leading / trailing nodes, the built-in clear
 * button, and the password reveal toggle. A variant-comparison strip and the
 * theme bar (Modena / dark / AtlantaFX) show the role-token theming.</p>
 */
public class RXMaterialTextFieldShowcase extends RXShowcaseApplication {

    private RXMaterialTextField field;
    private RXMaterialPasswordField password;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMaterialTextField";
    }

    @Override
    protected String subtitle() {
        return "Floating label, activation line, supporting text, clear / reveal";
    }

    @Override
    protected String windowTitle() {
        return "RXMaterialTextField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-material-text-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXMaterialTextField();
        field.setLabelText("Full name");
        field.setHelperText("As it appears on your ID");
        field.setPrefColumnCount(18);

        password = new RXMaterialPasswordField();
        password.setLabelText("Password");
        password.setHelperText("At least 8 characters");
        password.setPrefColumnCount(18);

        Label variantsHint = new Label("Variant comparison");
        variantsHint.getStyleClass().add("hint-label");
        RXMaterialTextField underline = compareField("Underline", RXFieldVariant.UNDERLINE);
        RXMaterialTextField filled = compareField("Filled", RXFieldVariant.FILLED);
        VBox comparison = new VBox(10.0, variantsHint, underline, filled);
        comparison.getStyleClass().add("comparison");

        VBox preview = new VBox(22.0, field, password, comparison);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER_LEFT);
        return preview;
    }

    private static RXMaterialTextField compareField(String label, RXFieldVariant variant) {
        RXMaterialTextField sample = new RXMaterialTextField();
        sample.setLabelText(label);
        sample.setText(label);
        sample.setVariant(variant);
        sample.setPrefColumnCount(14);
        return sample;
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
        ComboBox<RXFieldVariant> variantBox = new ComboBox<>();
        variantBox.getItems().setAll(RXFieldVariant.UNDERLINE, RXFieldVariant.FILLED);
        variantBox.setValue(field.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);
        field.variantProperty().bind(variantBox.valueProperty());

        CheckBox floatingBox = new CheckBox();
        floatingBox.setSelected(field.isFloatingLabel());
        field.floatingLabelProperty().bind(floatingBox.selectedProperty());

        CheckBox invalidBox = new CheckBox();
        field.invalidProperty().bind(invalidBox.selectedProperty());

        CheckBox clearBox = new CheckBox();
        clearBox.setSelected(field.isShowClearButton());
        field.showClearButtonProperty().bind(clearBox.selectedProperty());

        CheckBox leadingBox = new CheckBox();
        leadingBox.selectedProperty().addListener((obs, oldV, newV) ->
                field.setLeadingNode(newV ? icon("person-icon") : null));

        CheckBox editableBox = new CheckBox();
        editableBox.setSelected(field.isEditable());
        field.editableProperty().bind(editableBox.selectedProperty());

        CheckBox disableBox = new CheckBox();
        field.disableProperty().bind(disableBox.selectedProperty());

        return createGrid(
                row("Variant", variantBox),
                row("Floating label", floatingBox),
                row("Invalid", invalidBox),
                row("Clear button", clearBox),
                row("Leading icon", leadingBox),
                row("Editable", editableBox),
                row("Disabled", disableBox));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox();
        animatedBox.setSelected(field.isAnimated());
        field.animatedProperty().bind(animatedBox.selectedProperty());
        password.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider = createSlider(0.0, 600.0, field.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) -> {
            field.setAnimationDuration(Duration.millis(newV.doubleValue()));
            password.setAnimationDuration(Duration.millis(newV.doubleValue()));
        });

        Slider scaleSlider = createSlider(0.5, 1.0, field.getLabelFloatScale());
        scaleSlider.valueProperty().addListener((obs, oldV, newV) -> {
            field.setLabelFloatScale(newV.doubleValue());
            password.setLabelFloatScale(newV.doubleValue());
        });

        return createGrid(
                row("Animated", animatedBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Label scale", scaleSlider, createValueLabel(scaleSlider, "%.2f")));
    }

    private Node buildPasswordGrid() {
        CheckBox revealBox = new CheckBox();
        password.revealPasswordProperty().bind(revealBox.selectedProperty());

        CheckBox revealButtonBox = new CheckBox();
        revealButtonBox.setSelected(password.isShowRevealButton());
        password.showRevealButtonProperty().bind(revealButtonBox.selectedProperty());

        ComboBox<Character> echoBox = new ComboBox<>();
        echoBox.getItems().setAll('●', '•', '*', '✱');
        echoBox.setValue(password.getEchoChar());
        echoBox.setMaxWidth(Double.MAX_VALUE);
        password.echoCharProperty().bind(echoBox.valueProperty());

        return createGrid(
                row("Reveal", revealBox),
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
