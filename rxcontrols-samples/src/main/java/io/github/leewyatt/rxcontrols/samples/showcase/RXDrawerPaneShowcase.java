package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDrawerPane.DrawerMode;
import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXDrawerPane}.
 *
 * <p>Hosts a drawer with placeholder content and a composed drawer content tree,
 * and a control panel that drives every configurable property: side, mode,
 * backdrop group, animation, and drawer thickness, plus open/close/toggle
 * buttons and a live read-out of {@code showing} and the last
 * {@link RXDrawerEvent}.</p>
 */
public class RXDrawerPaneShowcase extends RXShowcaseApplication {

    private static final double DEFAULT_THICKNESS = 320.0;

    private RXDrawerPane drawer;
    private final StringProperty lastEvent = new SimpleStringProperty("—");

    @Override
    protected String title() {
        return "RXDrawerPane";
    }

    @Override
    protected double sceneWidth() {
        return 1060;
    }

    @Override
    protected String subtitle() {
        return "An overlay / push content drawer with a backdrop, veto, custom content and a11y.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-drawer-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        drawer = new RXDrawerPane();
        drawer.setContent(createMainContent());
        drawer.setDrawerContent(createDrawerContent());
        drawer.addEventHandler(RXDrawerEvent.ANY, e -> lastEvent.set(
                e.getEventType().getName()));
        return drawer;
    }

    private Region createMainContent() {
        Label heading = new Label("Main content");
        heading.getStyleClass().add("preview-heading");
        Label hint = new Label("Use the panel on the right to drive every property, "
                + "then open the drawer.");
        hint.getStyleClass().add("preview-hint");
        hint.setWrapText(true);

        Button open = new Button("Open drawer");
        open.getStyleClass().add("preview-action");
        open.setOnAction(e -> drawer.open());

        VBox box = new VBox(14.0, heading, hint, open);
        box.getStyleClass().add("preview-content");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Region createDrawerContent() {
        Label title = new Label("Release notes");
        title.getStyleClass().add("drawer-title");

        Label description = new Label("A compact user-supplied node tree. It keeps the "
                + "drawer focused without turning the showcase into a form demo.");
        description.getStyleClass().add("drawer-description");
        description.setWrapText(true);

        Button close = new Button("Done");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(e -> drawer.close());

        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);

        VBox panel = new VBox(16.0, title, description, region, close);
        panel.getStyleClass().add("drawer-panel");
        return panel;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("State & actions", actionsBox()),
                section("Layout", layoutGrid()),
                section("Backdrop & dismissal", backdropGrid()),
                section("Animation", animationGrid()));
    }

    private Node layoutGrid() {
        ComboBox<Side> side = new ComboBox<>(FXCollections.observableArrayList(
                Side.TOP, Side.RIGHT, Side.BOTTOM, Side.LEFT));
        side.setValue(drawer.getSide());
        side.valueProperty().addListener((obs, old, value) -> drawer.setSide(value));
        side.setMaxWidth(Double.MAX_VALUE);

        ComboBox<DrawerMode> mode = new ComboBox<>(FXCollections.observableArrayList(
                DrawerMode.OVERLAY, DrawerMode.PUSH));
        mode.setValue(drawer.getDrawerMode());
        mode.valueProperty().addListener((obs, old, value) -> drawer.setDrawerMode(value));
        mode.setMaxWidth(Double.MAX_VALUE);

        Slider width = createSlider(120.0, 460.0, DEFAULT_THICKNESS);
        width.valueProperty().addListener((obs, old, value) -> drawer.setPrefDrawerWidth(value.doubleValue()));
        drawer.setPrefDrawerWidth(DEFAULT_THICKNESS);

        Slider height = createSlider(120.0, 460.0, DEFAULT_THICKNESS);
        height.valueProperty().addListener((obs, old, value) -> drawer.setPrefDrawerHeight(value.doubleValue()));
        drawer.setPrefDrawerHeight(DEFAULT_THICKNESS);

        return createGrid(
                row("Side", side),
                row("Mode", mode),
                row("Pref width", width, createValueLabel(width, "%.0f")),
                row("Pref height", height, createValueLabel(height, "%.0f")));
    }

    private Node backdropGrid() {
        // The dim level itself is CSS (the .backdrop background), so there is no
        // opacity control here.
        CheckBox visible = checkBox("Backdrop (modal)", drawer.isBackdropVisible(),
                drawer::setBackdropVisible);
        CheckBox click = checkBox("Close on backdrop click", drawer.isCloseOnBackdropClick(),
                drawer::setCloseOnBackdropClick);
        CheckBox esc = checkBox("Close on ESC", drawer.isCloseOnEsc(), drawer::setCloseOnEsc);

        return createGrid(
                row(visible),
                row(click),
                row(esc));
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", drawer.isAnimated(), drawer::setAnimated);

        Slider duration = createSlider(0.0, 600.0, drawer.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> drawer.setAnimationDuration(Duration.millis(value.doubleValue())));

        ComboBox<String> interpolator = new ComboBox<>(FXCollections.observableArrayList(
                "EASE_BOTH", "LINEAR", "EASE_IN", "EASE_OUT"));
        interpolator.setValue("EASE_BOTH");
        interpolator.valueProperty().addListener(
                (obs, old, value) -> drawer.setAnimationInterpolator(interpolatorFor(value)));
        interpolator.setMaxWidth(Double.MAX_VALUE);

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")),
                row("Easing", interpolator));
    }

    private static Interpolator interpolatorFor(String name) {
        return switch (name) {
            case "LINEAR" -> Interpolator.LINEAR;
            case "EASE_IN" -> Interpolator.EASE_IN;
            case "EASE_OUT" -> Interpolator.EASE_OUT;
            default -> Interpolator.EASE_BOTH;
        };
    }

    private Node actionsBox() {
        Button open = new Button("open()");
        open.setOnAction(e -> drawer.open());
        Button close = new Button("close()");
        close.setOnAction(e -> drawer.close());
        Button toggle = new Button("toggle()");
        toggle.setOnAction(e -> drawer.toggle());
        HBox buttons = new HBox(8.0, toggle, open, close);

        Label showing = new Label();
        showing.getStyleClass().add("value-label");
        showing.textProperty().bind(Bindings.createStringBinding(
                () -> "showing: " + drawer.isShowing(), drawer.showingProperty()));

        Label event = new Label();
        event.getStyleClass().add("value-label");
        event.textProperty().bind(Bindings.createStringBinding(
                () -> "last event: " + lastEvent.get(), lastEvent));

        return new VBox(10.0, buttons, showing, event);
    }

    private static CheckBox checkBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.selectedProperty().addListener((obs, old, value) -> onChange.accept(value));
        return box;
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
