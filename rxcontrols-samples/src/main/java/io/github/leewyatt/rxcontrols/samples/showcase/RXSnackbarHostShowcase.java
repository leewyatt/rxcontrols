package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSnackbarRequest;
import io.github.leewyatt.rxcontrols.RXSnackbarSeverity;
import io.github.leewyatt.rxcontrols.RXSnackbarStrategy;
import io.github.leewyatt.rxcontrols.RXSnackbars;
import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Showcase application for {@code RXSnackbarHost}.
 *
 * <p>The preview hosts a bordered sub-pane with its own snackbar host (via
 * {@code RXSnackbars.installInto}), so every host property — position, margin,
 * max width, strategy, duplicate prevention, queue bound, auto-hide duration and
 * the animation pair — can be driven live from the control panel and verified
 * against boundary values. Request-side options (severity, action, close icon,
 * same-key in-place update) shape the next shown message. A separate button
 * exercises the scene-level facade path ({@code RXSnackbars.show}), and a live
 * read-out tracks {@code showing} and the last lifecycle event.</p>
 */
public class RXSnackbarHostShowcase extends RXShowcaseApplication {

    private RXSnackbarHost host;
    private final StringProperty lastEvent = new SimpleStringProperty("—");
    private final AtomicInteger counter = new AtomicInteger();

    // Request options driven by the control panel.
    private ComboBox<RXSnackbarSeverity> severityCombo;
    private CheckBox withAction;
    private CheckBox withCloseIcon;
    private CheckBox useKey;
    private CheckBox persistent;

    @Override
    protected String title() {
        return "RXSnackbarHost";
    }

    @Override
    protected String subtitle() {
        return "One-at-a-time, auto-hiding in-scene snackbars with a bounded queue.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-snackbar-host-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        StackPane snackbarArea = new StackPane();
        snackbarArea.getStyleClass().add("snackbar-area");
        snackbarArea.setMinSize(420.0, 260.0);
        snackbarArea.setMaxSize(420.0, 260.0);
        host = RXSnackbars.installInto(snackbarArea);
        host.addEventHandler(RXSnackbarEvent.ANY, event -> lastEvent.set(
                event.getEventType().getName().replace("RX_SNACKBAR_", "")
                        + (event.getReason() == null ? "" : " (" + event.getReason() + ")")));

        Label heading = new Label("Preview");
        heading.getStyleClass().add("preview-heading");
        Label hint = new Label("Snackbars show inside the bordered area below; "
                + "configure the host and the next request on the right.");
        hint.getStyleClass().add("preview-hint");
        hint.setWrapText(true);

        Button show = new Button("Show snackbar");
        show.getStyleClass().add("preview-show");
        show.setOnAction(event -> host.show(buildRequest()));

        Button showFacade = new Button("Show over the whole scene (facade)");
        showFacade.setOnAction(event -> RXSnackbars.show(showFacade, buildRequest()));

        VBox box = new VBox(14.0, heading, hint, snackbarArea, show, showFacade);
        box.getStyleClass().add("preview-content");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private RXSnackbarRequest buildRequest() {
        int n = counter.incrementAndGet();
        RXSnackbarRequest.Builder builder = RXSnackbarRequest
                .builder(useKey.isSelected() ? "Job update #" + n : "Message #" + n)
                .severity(severityCombo.getValue());
        if (withAction.isSelected()) {
            builder.action("Undo", () -> lastEvent.set("action #" + n + " ran"));
        }
        if (withCloseIcon.isSelected()) {
            builder.showCloseIcon(true);
        }
        if (useKey.isSelected()) {
            builder.key("job");
        }
        if (persistent.isSelected()) {
            builder.duration(Duration.INDEFINITE);
        }
        return builder.build();
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Next request", requestGrid()),
                section("Host scheduling", schedulingGrid()),
                section("Layout", layoutGrid()),
                section("Animation", animationGrid()),
                section("Commands", commandsBox()),
                section("State", stateBox()));
    }

    private Node requestGrid() {
        severityCombo = new ComboBox<>();
        severityCombo.getItems().setAll(RXSnackbarSeverity.values());
        severityCombo.setValue(RXSnackbarSeverity.NONE);
        severityCombo.setMaxWidth(Double.MAX_VALUE);

        withAction = new CheckBox("With \"Undo\" action");
        withCloseIcon = new CheckBox("With close icon");
        useKey = new CheckBox("Use key \"job\" (in-place update)");
        persistent = new CheckBox("Persistent (INDEFINITE duration)");

        return createGrid(
                row("Severity", severityCombo),
                row(withAction),
                row(withCloseIcon),
                row(useKey),
                row(persistent));
    }

    private Node schedulingGrid() {
        ComboBox<RXSnackbarStrategy> strategy = new ComboBox<>();
        strategy.getItems().setAll(RXSnackbarStrategy.values());
        strategy.setValue(host.getStrategy());
        strategy.setMaxWidth(Double.MAX_VALUE);
        strategy.valueProperty().addListener((observable, was, is) -> host.setStrategy(is));

        CheckBox preventDuplicate = new CheckBox("Prevent duplicate (key > message)");
        preventDuplicate.setSelected(host.isPreventDuplicate());
        preventDuplicate.selectedProperty().addListener((observable, was, is) ->
                host.setPreventDuplicate(is));

        Slider maxQueue = createSlider(0.0, 8.0, host.getMaxQueueSize());
        maxQueue.setMajorTickUnit(1.0);
        maxQueue.setSnapToTicks(true);
        maxQueue.valueProperty().addListener((observable, was, is) ->
                host.setMaxQueueSize(is.intValue()));
        Label maxQueueValue = createValueLabel(maxQueue, "%.0f");

        Slider duration = createSlider(0.5, 10.0, host.getDefaultDuration().toSeconds());
        duration.valueProperty().addListener((observable, was, is) ->
                host.setDefaultDuration(Duration.seconds(is.doubleValue())));
        Label durationValue = createValueLabel(duration, "%.1f s");

        return createGrid(
                row("Strategy", strategy),
                row(preventDuplicate),
                row("Max queue (0 ⇒ default 5)", maxQueue, maxQueueValue),
                row("Default duration", duration, durationValue));
    }

    private Node layoutGrid() {
        ComboBox<Pos> position = new ComboBox<>();
        position.getItems().setAll(Pos.values());
        position.setValue(host.getPosition());
        position.setMaxWidth(Double.MAX_VALUE);
        position.valueProperty().addListener((observable, was, is) -> host.setPosition(is));

        Slider margin = createSlider(0.0, 64.0, host.getMargin().getTop());
        margin.valueProperty().addListener((observable, was, is) ->
                host.setMargin(new Insets(is.doubleValue())));
        Label marginValue = createValueLabel(margin, "%.0f px");

        Slider maxWidth = createSlider(120.0, 900.0, host.getSnackbarMaxWidth());
        maxWidth.valueProperty().addListener((observable, was, is) ->
                host.setSnackbarMaxWidth(is.doubleValue()));
        Label maxWidthValue = createValueLabel(maxWidth, "%.0f px");

        return createGrid(
                row("Position", position),
                row("Margin", margin, marginValue),
                row("Max bar width", maxWidth, maxWidthValue));
    }

    private Node animationGrid() {
        CheckBox animated = new CheckBox("Animated");
        animated.setSelected(host.isAnimated());
        animated.selectedProperty().addListener((observable, was, is) -> host.setAnimated(is));

        Slider duration = createSlider(0.0, 600.0, host.getAnimationDuration().toMillis());
        duration.valueProperty().addListener((observable, was, is) ->
                host.setAnimationDuration(Duration.millis(is.doubleValue())));
        Label durationValue = createValueLabel(duration, "%.0f ms");

        return createGrid(
                row(animated),
                row("Duration (0 ⇒ snap)", duration, durationValue));
    }

    private Node commandsBox() {
        Button dismiss = new Button("dismiss()");
        dismiss.setOnAction(event -> host.dismiss());
        Button dismissKey = new Button("dismiss(\"job\")");
        dismissKey.setOnAction(event -> host.dismiss("job"));
        Button clear = new Button("clear()");
        clear.setOnAction(event -> host.clear());
        Button burst = new Button("Show 4 at once (queue)");
        burst.setOnAction(event -> {
            for (int i = 0; i < 4; i++) {
                host.show(buildRequest());
            }
        });
        VBox box = new VBox(8.0, burst, dismiss, dismissKey, clear);
        return box;
    }

    private Node stateBox() {
        Label showing = new Label();
        showing.textProperty().bind(Bindings.createStringBinding(
                () -> "showing: " + host.isShowing(), host.showingProperty()));
        Label event = new Label();
        event.textProperty().bind(Bindings.concat("last event: ", lastEvent));
        event.setWrapText(true);
        return new VBox(6.0, showing, event);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
