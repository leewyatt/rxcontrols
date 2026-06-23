package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDialogContent;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXDialog}.
 *
 * <p>The preview hosts a "Show dialog" button (the dialog's owner); the control
 * panel drives every configurable property — transition, animation, modality and
 * dismissal behaviour, and the close (X) button — and shows a live read-out of the
 * {@code showing} state, the last {@link RXDialogEvent}, and the last result.</p>
 */
public class RXDialogShowcase extends RXShowcaseApplication {

    private RXDialog<ButtonType> dialog;
    private RXDialogContent layout;
    private final StringProperty lastEvent = new SimpleStringProperty("—");
    private final StringProperty lastResult = new SimpleStringProperty("—");

    @Override
    protected String title() {
        return "RXDialog";
    }

    @Override
    protected String subtitle() {
        return "In-scene modal overlay dialog with an asynchronous result.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-dialog-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        dialog = new RXDialog<>();
        layout = new RXDialogContent("Save changes?",
                "Your changes will be lost if you don't save them.");
        dialog.setContent(layout);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(buttonType -> buttonType);
        dialog.setOnResult(result -> lastResult.set(result == null ? "—" : result.getText()));
        dialog.addEventHandler(RXDialogEvent.ANY, event -> lastEvent.set(event.getEventType().getName()));

        Label heading = new Label("Preview");
        heading.getStyleClass().add("preview-heading");
        Label hint = new Label("Configure the dialog on the right, then show it.");
        hint.getStyleClass().add("preview-hint");
        hint.setWrapText(true);

        Button show = new Button("Show dialog");
        show.getStyleClass().add("preview-show");
        show.setOnAction(event -> dialog.show(show));

        VBox box = new VBox(14.0, heading, hint, show);
        box.getStyleClass().add("preview-content");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Transition", transitionGrid()),
                section("Animation", animationGrid()),
                section("Behaviour", behaviourBox()),
                section("State", stateBox()));
    }

    private Node transitionGrid() {
        ComboBox<RXDialogTransition> transition = new ComboBox<>(
                FXCollections.observableArrayList(RXDialogTransition.values()));
        transition.setValue(dialog.getTransition());
        transition.valueProperty().addListener((obs, old, value) -> dialog.setTransition(value));
        transition.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Style", transition));
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", dialog.isAnimated(), dialog::setAnimated);

        Slider duration = createSlider(0.0, 600.0, dialog.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> dialog.setAnimationDuration(Duration.millis(value.doubleValue())));

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node behaviourBox() {
        return new VBox(10.0,
                checkBox("Modal (scrim + focus trap)", dialog.isModal(), dialog::setModal),
                checkBox("Close on ESC", dialog.isCloseOnEsc(), dialog::setCloseOnEsc),
                checkBox("Close on scrim click", dialog.isCloseOnScrimClick(), dialog::setCloseOnScrimClick),
                checkBox("Show close (X) button", layout.isShowClose(), layout::setShowClose));
    }

    private Node stateBox() {
        Label showing = new Label();
        showing.textProperty().bind(Bindings.format("showing: %s", dialog.showingProperty()));
        Label event = new Label();
        event.textProperty().bind(Bindings.concat("last event: ", lastEvent));
        Label result = new Label();
        result.textProperty().bind(Bindings.concat("last result: ", lastResult));

        Button close = new Button("Close programmatically");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(e -> dialog.close());

        return new VBox(10.0, showing, event, result, close);
    }

    private CheckBox checkBox(String text, boolean initial, Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(text);
        box.setSelected(initial);
        box.selectedProperty().addListener((obs, old, value) -> setter.accept(value));
        return box;
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
