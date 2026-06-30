package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDialogContent;
import io.github.leewyatt.rxcontrols.RXDialogs;
import io.github.leewyatt.rxcontrols.enums.DialogActionsLayout;
import io.github.leewyatt.rxcontrols.enums.DialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
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
        // Seed the card bounds so the "Card bounds" sliders start consistent with the card:
        // it opens at 380 wide and (when resizable) can grow to 600 x 420.
        dialog.setCardPrefWidth(380);
        dialog.setCardMaxWidth(600);
        dialog.setCardMaxHeight(420);
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

        Button stack = new Button("Show 4 stacked dialogs");
        stack.getStyleClass().add("preview-show");
        stack.setOnAction(event -> showStackedDialogs(stack));

        VBox box = new VBox(14.0, heading, hint, show, stack);
        box.getStyleClass().add("preview-content");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // Pops four independent dialogs over the same scene, ~450ms apart, to show stacking: they
    // share the per-scene overlay (one merged scrim, focus trapped in the top one), the cards
    // nest (decreasing width), and closing / dragging the top reveals the one below.
    private void showStackedDialogs(Node owner) {
        int count = 4;
        showOneStacked(owner, 1, count, 460.0);
        Timeline timeline = new Timeline();
        for (int i = 2; i <= count; i++) {
            int index = i;
            double prefWidth = 460.0 - (i - 1) * 50.0;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(650.0 * (i - 1)),
                    event -> showOneStacked(owner, index, count, prefWidth)));
        }
        timeline.play();
    }

    private void showOneStacked(Node owner, int index, int count, double prefWidth) {
        RXDialog<ButtonType> stacked = new RXDialog<>();
        stacked.setContent(new RXDialogContent("Dialog " + index + " of " + count,
                "Stacked above the previous ones — drag me aside, or close me (OK / ESC / scrim) "
                        + "to reveal the dialog below."));
        stacked.getButtonTypes().setAll(ButtonType.OK);
        stacked.setCardPrefWidth(prefWidth);
        stacked.setEnableDraggable(true);
        stacked.setEnableResizable(true);
        stacked.setCloseOnScrimClick(false);
        stacked.show(owner);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Transition", transitionGrid()),
                section("Actions", actionsGrid()),
                section("Animation", animationGrid()),
                section("Behaviour", behaviourBox()),
                section("Card bounds (px)", cardBoundsGrid()),
                section("Quick dialogs (RXDialogs)", quickDialogsBox()),
                section("Custom dialogs (builder)", builderDialogsBox()),
                section("State", stateBox()));
    }

    // One-click triggers for the RXDialogs convenience facade: message / confirm / input / busy,
    // each owned by its own button and wiring its async result into the State read-out.
    private Node quickDialogsBox() {
        Button info = facadeButton("information(…)",
                b -> RXDialogs.information(b, "Saved", "Your changes have been saved."));
        Button warn = facadeButton("warning(…)",
                b -> RXDialogs.warning(b, "Low disk space", "Less than 1 GB remaining."));
        Button error = facadeButton("error(…) + custom Report button", b -> {
            ButtonType report = new ButtonType("Report");
            RXDialogs.error(b, "Upload failed", "The server could not be reached.", ButtonType.OK, report)
                    .thenAccept(result -> lastResult.set(
                            "error → " + (result == null ? "—" : result.getText())));
        });
        Button confirm = facadeButton("confirm(…)",
                b -> RXDialogs.confirm(b, "Delete file?", "This cannot be undone.")
                        .thenAccept(result -> lastResult.set(
                                "confirm → " + (result == null ? "—" : result.getText()))));
        Button input = facadeButton("input(…)",
                b -> RXDialogs.input(b, "Rename", "New name:", "untitled")
                        .thenAccept(text -> lastResult.set(
                                "input → " + (text == null ? "(cancelled)" : text))));
        Button choice = facadeButton("choice(…)",
                b -> RXDialogs.choice(b, "Pick a size", "Size:", "Medium",
                                "Small", "Medium", "Large", "Extra Large")
                        .thenAccept(size -> lastResult.set(
                                "choice → " + (size == null ? "(cancelled)" : size))));
        Button busy = facadeButton("busy(…) — auto-closes in 1.5s", b -> {
            RXDialogs.Busy handle = RXDialogs.busy(b, "Working…");
            new Timeline(new KeyFrame(Duration.seconds(1.5), event -> handle.close())).play();
        });
        return new VBox(8.0, info, warn, error, confirm, input, choice, busy);
    }

    private Button facadeButton(String text, Consumer<Button> action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.accept(button));
        return button;
    }

    // Builder-built dialogs: one configured purely by chaining (custom buttons + PLATFORM
    // action order + close X + draggable — no factory overload), one validity-gated confirmation
    // (OK vetoed until the checkbox is ticked), and one with a bespoke header close button placed
    // in RXDialogContent's trailing slot and wired through getDialog().
    private Node builderDialogsBox() {
        Button custom = facadeButton("create()… 3 actions + PLATFORM + close X + drag", b -> {
            ButtonType save = new ButtonType("Save", ButtonData.OK_DONE);
            ButtonType discard = new ButtonType("Discard", ButtonData.OTHER);
            RXDialogs.create(b).type(RXDialogs.Type.WARNING)
                    .title("Unsaved changes").message("Save your changes before closing?")
                    .buttons(ButtonType.CANCEL, discard, save)
                    .actionsLayout(DialogActionsLayout.PLATFORM)
                    .closeButton(true).draggable(true)
                    .show()
                    .thenAccept(result -> lastResult.set(
                            "builder → " + (result == null ? "—" : result.getText())));
        });
        Button gated = facadeButton("create()… validWhen (OK gated on a checkbox)", b -> {

            CheckBox agree = new CheckBox("I accept the terms");
            VBox content = new VBox(10.0, new Label("Please accept the terms to continue."), agree);
            RXDialogs.create(b).type(RXDialogs.Type.CONFIRMATION)
                    .title("Terms").content(content)
                    .buttons(ButtonType.CANCEL, ButtonType.OK)
                    .validWhen(agree.selectedProperty())
                    .closeButton(true)
                    .draggable(true)
                    .show()
                    .thenAccept(result -> lastResult.set(
                            "terms → " + (result == null ? "—" : result.getText())));
        });
        Button customHeader = facadeButton("create()… custom header close (headerTrailing + getDialog())", b -> {
            RXDialogContent content = new RXDialogContent("Custom chrome",
                    "The default X is off here; this dialog puts its own button in the header's trailing slot.");
            Button close = new Button("✕");
            close.setOnAction(ev -> {
                RXDialog<?> host = content.getDialog();   // RXDialogContent reverse-references its dialog
                if (host != null) {
                    host.close();
                }
            });
            content.setHeaderTrailing(close);
            RXDialogs.create(b).content(content).buttons(ButtonType.OK).show();
        });
        return new VBox(8.0, custom, gated, customHeader);
    }

    private Node cardBoundsGrid() {
        Slider prefWidth = createSlider(280.0, 760.0, dialog.getCardPrefWidth());
        prefWidth.valueProperty().addListener((obs, old, value) -> dialog.setCardPrefWidth(value.doubleValue()));

        Slider maxWidth = createSlider(280.0, 900.0, dialog.getCardMaxWidth());
        maxWidth.valueProperty().addListener((obs, old, value) -> dialog.setCardMaxWidth(value.doubleValue()));

        Slider maxHeight = createSlider(160.0, 640.0, dialog.getCardMaxHeight());
        maxHeight.valueProperty().addListener((obs, old, value) -> dialog.setCardMaxHeight(value.doubleValue()));

        // Enable "User-resizable" in Behaviour, then drag the card's edges to see these bounds.
        return createGrid(
                row("Pref width", prefWidth, createValueLabel(prefWidth, "%.0f")),
                row("Max width", maxWidth, createValueLabel(maxWidth, "%.0f")),
                row("Max height", maxHeight, createValueLabel(maxHeight, "%.0f")));
    }

    private Node transitionGrid() {
        ComboBox<DialogTransition> transition = new ComboBox<>(
                FXCollections.observableArrayList(DialogTransition.values()));
        transition.setValue(dialog.getTransition());
        transition.valueProperty().addListener((obs, old, value) -> dialog.setTransition(value));
        transition.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Style", transition));
    }

    private Node actionsGrid() {
        ComboBox<DialogActionsLayout> layout = new ComboBox<>(
                FXCollections.observableArrayList(DialogActionsLayout.values()));
        layout.setValue(dialog.getActionsLayout());
        layout.valueProperty().addListener((obs, old, value) -> dialog.setActionsLayout(value));
        layout.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Layout", layout));
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
        Region graphic = demoGraphic();
        return new VBox(10.0,
                checkBox("Modal (scrim + focus trap)", dialog.isModal(), dialog::setModal),
                checkBox("Close on ESC", dialog.isCloseOnEsc(), dialog::setCloseOnEsc),
                checkBox("Close on scrim click", dialog.isCloseOnScrimClick(), dialog::setCloseOnScrimClick),
                checkBox("Show graphic", layout.getGraphic() != null,
                        show -> layout.setGraphic(show ? graphic : null)),
                checkBox("Show close (X) button", dialog.isShowCloseButton(), dialog::setShowCloseButton),
                checkBox("Enable resizable (drag edges)", dialog.isEnableResizable(), dialog::setEnableResizable),
                checkBox("Enable draggable (drag title)", dialog.isEnableDraggable(), dialog::setEnableDraggable));
    }

    // A 24px info glyph for the "Show graphic" toggle, so the heading's leading graphic slot
    // is visible. Built as a shape-filled Region (the project's icon idiom), tinted via a token.
    private Region demoGraphic() {
        Region icon = new Region();
        int size = 12;
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        icon.setStyle("-fx-background-color: -rx-primary;"
                + " -fx-shape: \"M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1"
                + " 15h-2v-6h2v6zm0-8h-2V7h2v2z\";");
        return icon;
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
