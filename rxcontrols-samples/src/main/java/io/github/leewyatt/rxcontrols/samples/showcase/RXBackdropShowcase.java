package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBackdrop;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXBackdrop}.
 *
 * <p>Exercises the standalone overlay layer, command-driven animation, writable
 * {@code showing} state, CSS duration properties, interpolators, and author
 * styling of the dim fill.</p>
 */
public class RXBackdropShowcase extends RXShowcaseApplication {

    private RXBackdrop backdrop;
    private Region dialogCard;
    private ColorPicker colorPicker;
    private Slider alphaSlider;

    /**
     * {@inheritDoc}
     */
    @Override
    protected String title() {
        return "RXBackdrop";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String subtitle() {
        return "Standalone dimming layer for modal and transient overlays";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String windowTitle() {
        return "RXBackdrop Showcase";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-backdrop-showcase.css").toExternalForm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Node createPreview() {
        backdrop = new RXBackdrop();
        backdrop.getStyleClass().add("showcase-backdrop");

        dialogCard = createDialogCard();
        dialogCard.visibleProperty().bind(backdrop.showingProperty());
        dialogCard.managedProperty().bind(backdrop.showingProperty());

        StackPane preview = new StackPane(createDashboard(), backdrop, dialogCard);
        preview.getStyleClass().add("backdrop-preview");
        StackPane.setMargin(dialogCard, new Insets(20.0));

        backdrop.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> backdrop.hide());
        applyBackdropPaint(Color.web("#0f172a"), 0.46);
        return preview;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Section> createSections() {
        return List.of(
                section("State", buildStateGrid()),
                section("Fade", buildFadeGrid()),
                section("Fill", buildFillGrid()));
    }

    private Node buildStateGrid() {
        Button show = new Button("show()");
        show.setMaxWidth(Double.MAX_VALUE);
        show.setOnAction(event -> backdrop.show());

        Button hide = new Button("hide()");
        hide.setMaxWidth(Double.MAX_VALUE);
        hide.setOnAction(event -> backdrop.hide());

        Button showSnap = new Button("show(false)");
        showSnap.setMaxWidth(Double.MAX_VALUE);
        showSnap.setOnAction(event -> backdrop.show(false));

        Button hideSnap = new Button("hide(false)");
        hideSnap.setMaxWidth(Double.MAX_VALUE);
        hideSnap.setOnAction(event -> backdrop.hide(false));

        HBox animated = new HBox(8.0, show, hide);
        HBox snapped = new HBox(8.0, showSnap, hideSnap);

        Label showing = new Label();
        showing.getStyleClass().add("value-label");
        showing.textProperty().bind(Bindings.createStringBinding(
                () -> "showing: " + backdrop.isShowing(), backdrop.showingProperty()));

        return createGrid(
                row(animated),
                row(snapped),
                row(showing));
    }

    private Node buildFadeGrid() {
        Slider fadeIn = createSlider(0.0, 900.0, backdrop.getFadeInDuration().toMillis());
        fadeIn.valueProperty().addListener((obs, old, value) ->
                backdrop.setFadeInDuration(Duration.millis(value.doubleValue())));

        Slider fadeOut = createSlider(0.0, 900.0, backdrop.getFadeOutDuration().toMillis());
        fadeOut.valueProperty().addListener((obs, old, value) ->
                backdrop.setFadeOutDuration(Duration.millis(value.doubleValue())));

        ComboBox<String> fadeInInterpolator = interpolatorBox("EASE_BOTH");
        fadeInInterpolator.valueProperty().addListener((obs, old, value) ->
                backdrop.setFadeInInterpolator(interpolatorFor(value)));

        ComboBox<String> fadeOutInterpolator = interpolatorBox("EASE_BOTH");
        fadeOutInterpolator.valueProperty().addListener((obs, old, value) ->
                backdrop.setFadeOutInterpolator(interpolatorFor(value)));

        return createGrid(
                row("Fade in", fadeIn, createValueLabel(fadeIn, "%.0f ms")),
                row("Fade out", fadeOut, createValueLabel(fadeOut, "%.0f ms")),
                row("In easing", fadeInInterpolator),
                row("Out easing", fadeOutInterpolator));
    }

    private Node buildFillGrid() {
        colorPicker = new ColorPicker(Color.web("#0f172a"));
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        colorPicker.setOnAction(event -> updateBackdropPaint());

        alphaSlider = createSlider(0.0, 0.75, 0.46);
        alphaSlider.valueProperty().addListener((obs, old, value) -> updateBackdropPaint());

        return createGrid(
                row("Color", colorPicker),
                row("Alpha", alphaSlider, createValueLabel(alphaSlider, "%.2f")));
    }

    private ComboBox<String> interpolatorBox(String value) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(
                "EASE_BOTH", "LINEAR", "EASE_IN", "EASE_OUT"));
        box.setValue(value);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Interpolator interpolatorFor(String name) {
        return switch (name) {
            case "LINEAR" -> Interpolator.LINEAR;
            case "EASE_IN" -> Interpolator.EASE_IN;
            case "EASE_OUT" -> Interpolator.EASE_OUT;
            default -> Interpolator.EASE_BOTH;
        };
    }

    private Node createDashboard() {
        Label title = label("Backdrop preview", "dashboard-title");
        Label subtitle = label("Compare the animated show() call with the instant show(false) call.",
                "dashboard-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(360.0);

        Button show = new Button("show() animated");
        show.getStyleClass().add("preview-primary-button");
        show.setOnAction(event -> backdrop.show());

        Button showSnap = new Button("show(false) instant");
        showSnap.getStyleClass().add("preview-secondary-button");
        showSnap.setOnAction(event -> backdrop.show(false));

        HBox actions = new HBox(10.0, show, showSnap);
        actions.setAlignment(Pos.CENTER);

        VBox dashboard = new VBox(16.0, title, subtitle, actions);
        dashboard.getStyleClass().add("dashboard");
        dashboard.setAlignment(Pos.CENTER);
        return dashboard;
    }

    private Region createDialogCard() {
        Label title = label("Backdrop active", "dialog-title");
        Label copy = label("The card stays above the backdrop until it is closed.",
                "dialog-copy");
        copy.setWrapText(true);

        Button close = new Button("Close");
        close.getStyleClass().add("dialog-close");
        close.setOnAction(event -> backdrop.hide());
        HBox actions = new HBox(10.0, close);
        actions.setAlignment(Pos.CENTER_RIGHT);


        VBox card = new VBox(12.0, title, copy, actions);
        card.getStyleClass().add("dialog-card");
        card.setMaxWidth(340.0);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        card.setPickOnBounds(false);
        return card;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void updateBackdropPaint() {
        applyBackdropPaint(colorPicker.getValue(), alphaSlider.getValue());
    }

    private void applyBackdropPaint(Color color, double alpha) {
        int red = colorComponent(color.getRed());
        int green = colorComponent(color.getGreen());
        int blue = colorComponent(color.getBlue());
        backdrop.setStyle(String.format(Locale.ROOT,
                "-fx-background-color: rgba(%d, %d, %d, %.3f);",
                red, green, blue, alpha));
    }

    private static int colorComponent(double value) {
        return (int) Math.round(value * 255.0);
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
