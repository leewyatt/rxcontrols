package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXStatePane;
import io.github.leewyatt.rxcontrols.RXStatePane.State;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXStatePane}.
 *
 * <p>Drives both axes of the state pane — the CONTENT/EMPTY/ERROR replacement
 * axis and the orthogonal loading overlay — plus every configurable property:
 * dimmed, blocking, loading delay, loading text, determinate progress, the
 * loadingGraphic escape hatch, custom empty/error slots, the retry hook, the
 * animation group, and a compact-size mode for checking placeholder
 * truncation.</p>
 */
public class RXStatePaneShowcase extends RXShowcaseApplication {

    private RXStatePane pane;
    private final StringProperty lastEvent = new SimpleStringProperty("—");

    @Override
    protected String title() {
        return "RXStatePane";
    }

    @Override
    protected double sceneWidth() {
        return 1080;
    }

    @Override
    protected String subtitle() {
        return "A region-level state container: CONTENT / EMPTY / ERROR replacement plus an orthogonal loading overlay.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-state-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        pane = new RXStatePane();
        pane.setContent(createContent());
        pane.addEventHandler(RXStatePane.RETRY, e -> lastEvent.set("RETRY"));
        StackPane host = new StackPane(pane);
        host.getStyleClass().add("preview-host");
        return host;
    }

    private Region createContent() {
        Label heading = new Label("Loaded content");
        heading.getStyleClass().add("preview-heading");
        Label hint = new Label("This is the CONTENT base view. Turn on loading to stack the "
                + "overlay on top of it, or switch the state to replace it.");
        hint.getStyleClass().add("preview-hint");
        hint.setWrapText(true);

        Button action = new Button("A focusable button");
        action.getStyleClass().add("preview-action");
        action.setOnAction(e -> lastEvent.set("content button"));

        VBox box = new VBox(14.0, heading, hint, action);
        box.getStyleClass().add("preview-content");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Two axes", axesGrid()),
                section("Loading overlay", overlayGrid()),
                section("Slots & retry", slotsGrid()),
                section("Animation", animationGrid()),
                section("Sizing", sizingGrid()));
    }

    private Node axesGrid() {
        ComboBox<State> state = new ComboBox<>(FXCollections.observableArrayList(State.values()));
        state.setValue(pane.getState());
        state.valueProperty().addListener((obs, old, value) -> pane.setState(value));
        state.setMaxWidth(Double.MAX_VALUE);

        CheckBox loading = checkBox("Loading (orthogonal)", pane.isLoading(), pane::setLoading);

        Label event = new Label();
        event.getStyleClass().add("value-label");
        event.textProperty().bind(Bindings.createStringBinding(
                () -> "last event: " + lastEvent.get(), lastEvent));

        return createGrid(
                row("State", state),
                row(loading),
                row(event));
    }

    private Node overlayGrid() {
        CheckBox dimmed = checkBox("Dimmed (scrim)", pane.isDimmed(), pane::setDimmed);
        CheckBox blocking = checkBox("Blocking (mouse + keyboard)", pane.isBlocking(), pane::setBlocking);

        Slider delay = createSlider(0.0, 1500.0, 0.0);
        delay.valueProperty().addListener(
                (obs, old, value) -> pane.setLoadingDelay(Duration.millis(value.doubleValue())));

        TextField text = new TextField();
        text.setPromptText("loading text");
        text.textProperty().addListener((obs, old, value) -> pane.setLoadingText(value));

        CheckBox determinate = new CheckBox("Determinate");
        Slider progress = createSlider(0.0, 1.0, 0.4);
        progress.disableProperty().bind(determinate.selectedProperty().not());
        Runnable pushProgress = () -> pane.setProgress(
                determinate.isSelected() ? progress.getValue() : -1.0);
        determinate.selectedProperty().addListener((obs, old, value) -> pushProgress.run());
        progress.valueProperty().addListener((obs, old, value) -> pushProgress.run());

        ComboBox<String> graphic = new ComboBox<>(FXCollections.observableArrayList(
                "Default", "RXBarSpinner", "RXDotPulse", "Custom node"));
        graphic.setValue("Default");
        graphic.valueProperty().addListener((obs, old, value) -> pane.setLoadingGraphic(
                switch (value) {
                    case "RXBarSpinner" -> new RXBarSpinner();
                    case "RXDotPulse" -> new RXDotPulse();
                    case "Custom node" -> customIndicator();
                    default -> null;
                }));
        graphic.setMaxWidth(Double.MAX_VALUE);

        return createGrid(
                row(dimmed),
                row(blocking),
                row("Delay", delay, createValueLabel(delay, "%.0f ms")),
                row("Text", text),
                row(determinate),
                row("Progress", progress, createValueLabel(progress, "%.2f")),
                row("Indicator", graphic));
    }

    private Node customIndicator() {
        Label note = new Label("Custom indicator node");
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> pane.hideLoading());
        VBox box = new VBox(8.0, note, cancel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node slotsGrid() {
        CheckBox customEmpty = checkBox("Custom emptyContent", false, selected ->
                pane.setEmptyContent(selected ? customPlaceholder() : null));
        CheckBox customError = checkBox("Custom errorContent", false, selected ->
                pane.setErrorContent(selected ? customErrorView() : null));
        CheckBox retry = checkBox("onRetry (default retry button)", false, selected ->
                pane.setOnRetry(selected ? e -> lastEvent.set("onRetry handled") : null));

        return createGrid(
                row(customEmpty),
                row(customError),
                row(retry));
    }

    private Node customPlaceholder() {
        RXPlaceholder placeholder = new RXPlaceholder(RXPlaceholder.Status.FORBIDDEN, "No access");
        placeholder.setDescription("A user-supplied placeholder replacing the default empty view.");
        Button signIn = new Button("Sign in");
        signIn.setOnAction(e -> lastEvent.set("sign-in"));
        placeholder.getActions().add(signIn);
        return placeholder;
    }

    private Node customErrorView() {
        Label heading = new Label("Custom error view");
        heading.getStyleClass().add("preview-heading");
        Label hint = new Label("Any node works here; the default retry button never touches "
                + "a user-supplied error view.");
        hint.getStyleClass().add("preview-hint");
        hint.setWrapText(true);
        VBox box = new VBox(10.0, heading, hint);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", pane.isAnimated(), pane::setAnimated);

        Slider duration = createSlider(0.0, 800.0, pane.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> pane.setAnimationDuration(Duration.millis(value.doubleValue())));

        ComboBox<String> interpolator = new ComboBox<>(FXCollections.observableArrayList(
                "EASE_BOTH", "LINEAR", "EASE_IN", "EASE_OUT"));
        interpolator.setValue("EASE_BOTH");
        interpolator.valueProperty().addListener(
                (obs, old, value) -> pane.setAnimationInterpolator(interpolatorFor(value)));
        interpolator.setMaxWidth(Double.MAX_VALUE);

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")),
                row("Easing", interpolator));
    }

    private Node sizingGrid() {
        // A pane smaller than the placeholder's min height clips the bottom of
        // the default placeholder (including retry) — this toggle makes that
        // truncation observable.
        CheckBox compact = checkBox("Compact pane (260 x 180)", false, selected -> {
            if (selected) {
                pane.setMaxSize(260.0, 180.0);
            } else {
                pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }
        });
        return createGrid(row(compact));
    }

    private static Interpolator interpolatorFor(String name) {
        return switch (name) {
            case "LINEAR" -> Interpolator.LINEAR;
            case "EASE_IN" -> Interpolator.EASE_IN;
            case "EASE_OUT" -> Interpolator.EASE_OUT;
            default -> Interpolator.EASE_BOTH;
        };
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
