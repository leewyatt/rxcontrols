package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.DialogTransition;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDialogContent;
import io.github.leewyatt.rxcontrols.RXRadioButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXDialog page: every card is a small screen the dialog opens in through showIn. It
 * shows a delete confirmation, a rename form that refuses an empty name, the five
 * transitions, terms that only the X button closes, a plan picker with a result
 * converter and a dialog the user can drag and resize.
 */
public final class RXDialogSamples {

    private static final double SCREEN_WIDTH = 440.0;
    private static final double SCREEN_HEIGHT = 260.0;

    private RXDialogSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        // One dialog overlay per scene: while a dialog is open, the triggers of the other
        // cards stay disabled so no second dialog opens in a different card.
        BooleanProperty open = new SimpleBooleanProperty();
        return new SampleContent(
                "A modal card over a dimmed scrim, shown inside the scene, with buttons that close it and deliver a result.",
                List.of("Ask for a decision or a short input before going on: confirm a delete, rename, pick an option.",
                        "For a message that needs no answer, use RXSnackbarHost."),
                List.of(
                        SampleCard.large("Delete confirmation",
                                "Cancel and Delete buttons; onResult gets the chosen ButtonType once the dialog has closed.",
                                confirm(open)),
                        SampleCard.large("Rename",
                                "A text field in the body; consuming the CLOSE_REQUEST event keeps the dialog open while the name is empty.",
                                rename(open)),
                        SampleCard.large("Transitions",
                                "CENTER (the default) scales in; the SLIDE_* transitions come in from an edge.",
                                transitions(open)),
                        SampleCard.large("Terms",
                                "showCloseButton adds an X; closeOnScrimClick false keeps a click outside from closing it.",
                                terms(open)),
                        SampleCard.large("Choose a plan",
                                "A resultConverter turns the clicked button into the selected plan's name.",
                                plan(open)),
                        SampleCard.large("Drag and resize",
                                "enableDraggable moves the card by its title; enableResizable lets its edges be dragged.",
                                movable(open))),
                FeedbackKit.STYLESHEET);
    }

    private static StackPane screen(Node content) {
        StackPane screen = FeedbackKit.screen(SCREEN_WIDTH, SCREEN_HEIGHT, content);
        screen.setMaxWidth(Region.USE_PREF_SIZE);
        return screen;
    }

    private static <R> void open(RXDialog<R> dialog, Pane screen, BooleanProperty open) {
        open.set(true);
        dialog.setOnHidden(event -> open.set(false));
        dialog.showIn(screen);
    }

    private static RXButton trigger(RXButton button, BooleanProperty open) {
        button.disableProperty().bind(open);
        return button;
    }

    private static Region headerIcon(String iconClass, String color) {
        Region icon = icon(iconClass);
        icon.getStyleClass().add(color);
        return icon;
    }

    // ==================== Delete confirmation ====================

    private static Node confirm(BooleanProperty open) {
        Label result = text("report-q3.pdf, 2.4 MB", "muted");
        RXButton delete = trigger(solid("Delete", "rose"), open);
        delete.setGraphic(icon("delete-icon"));
        VBox file = new VBox(2.0, text("Quarterly report", "copy-strong"), result);
        HBox row = new HBox(12.0, icon("file-icon"), file, spacer(), delete);
        row.getStyleClass().addAll("panel", "panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setMargin(row, new Insets(20.0));
        StackPane screen = screen(row);

        ButtonType deleteType = new ButtonType("Delete", ButtonData.OK_DONE);
        delete.setOnAction(event -> {
            RXDialogContent content = new RXDialogContent("Delete “report-q3.pdf”?",
                    "It will be removed for everyone on the team. This cannot be undone.");
            content.setGraphic(headerIcon("warning-icon", "rose"));
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setContent(content);
            dialog.getButtonTypes().setAll(ButtonType.CANCEL, deleteType);
            dialog.setResultConverter(type -> type);
            dialog.setOnResult(type -> result.setText(type == deleteType ? "Deleted" : "Kept, nothing changed"));
            open(dialog, screen, open);
        });
        return screen;
    }

    // ==================== Rename ====================

    private static Node rename(BooleanProperty open) {
        Label name = text("Weekly planning", "headline");
        RXButton edit = trigger(plain("Rename"), open);
        edit.setGraphic(icon("note-icon"));
        VBox board = new VBox(10.0, text("Board", "muted"), name, edit);
        board.setAlignment(Pos.CENTER);
        StackPane screen = screen(board);

        edit.setOnAction(event -> {
            TextField field = new TextField(name.getText());
            Label error = text("", "error-text");
            RXDialogContent content = new RXDialogContent();
            content.setHeaderText("Rename board");
            content.setContent(new VBox(6.0, field, error));
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setContent(content);
            dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
            dialog.setResultConverter(type -> type);
            dialog.setOnCloseRequest(request -> {
                if (request.getButtonType() == ButtonType.OK && field.getText().isBlank()) {
                    error.setText("The name cannot be empty.");
                    request.consume();
                }
            });
            dialog.setOnResult(type -> {
                if (type == ButtonType.OK) {
                    name.setText(field.getText().trim());
                }
            });
            open(dialog, screen, open);
            field.requestFocus();
        });
        return screen;
    }

    // ==================== Transitions ====================

    private static Node transitions(BooleanProperty open) {
        FlowPane buttons = new FlowPane(8.0, 8.0);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(16.0));
        StackPane screen = screen(buttons);
        for (DialogTransition transition : DialogTransition.values()) {
            RXButton button = trigger(plain(label(transition)), open);
            button.setOnAction(event -> {
                RXDialog<ButtonType> dialog = new RXDialog<>();
                dialog.setContent(new RXDialogContent("Update installed",
                        "Version 2.4 is ready. This card used " + label(transition).toLowerCase(Locale.ROOT) + "."));
                dialog.getButtonTypes().setAll(ButtonType.OK);
                dialog.setTransition(transition);
                open(dialog, screen, open);
            });
            buttons.getChildren().add(button);
        }
        return screen;
    }

    // ==================== Terms ====================

    private static Node terms(BooleanProperty open) {
        Label state = text("Not accepted yet", "muted");
        RXButton read = trigger(solid("Read the terms", "indigo"), open);
        VBox box = new VBox(10.0, icon("task-icon"), state, read);
        box.setAlignment(Pos.CENTER);
        StackPane screen = screen(box);

        ButtonType accept = new ButtonType("Accept", ButtonData.OK_DONE);
        read.setOnAction(event -> {
            Label body = text("By using the service you agree to keep your password private, to share only "
                    + "content you own and to let us store your files on servers in the EU. You can delete "
                    + "your account at any time; your files are removed within 30 days. We email you before "
                    + "these terms change.", "copy");
            body.setWrapText(true);
            body.setPrefWidth(300.0);
            ScrollPane scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(80.0);
            RXDialogContent content = new RXDialogContent();
            content.setHeaderText("Terms of service");
            content.setContent(scroll);
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setContent(content);
            dialog.setShowCloseButton(true);
            dialog.setCloseOnScrimClick(false);
            dialog.getButtonTypes().setAll(accept);
            dialog.setResultConverter(type -> type);
            dialog.setOnResult(type -> state.setText(type == accept ? "Accepted" : "Closed without accepting"));
            open(dialog, screen, open);
        });
        return screen;
    }

    // ==================== Choose a plan ====================

    private static Node plan(BooleanProperty open) {
        Label current = text("Plan: Basic", "headline");
        RXButton change = trigger(plain("Change plan"), open);
        VBox box = new VBox(10.0, current, change);
        box.setAlignment(Pos.CENTER);
        StackPane screen = screen(box);

        change.setOnAction(event -> {
            ToggleGroup group = new ToggleGroup();
            VBox options = new VBox(8.0);
            for (String name : List.of("Basic", "Pro", "Team")) {
                RXRadioButton radio = new RXRadioButton(name);
                radio.setToggleGroup(group);
                radio.setSelected(current.getText().endsWith(name));
                options.getChildren().add(radio);
            }
            RXDialogContent content = new RXDialogContent();
            content.setHeaderText("Choose a plan");
            content.setContent(options);
            RXDialog<String> dialog = new RXDialog<>();
            dialog.setContent(content);
            dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.APPLY);
            dialog.setResultConverter(type -> type == ButtonType.APPLY && group.getSelectedToggle() != null
                    ? ((RXRadioButton) group.getSelectedToggle()).getText() : null);
            dialog.setOnResult(name -> {
                if (name != null) {
                    current.setText("Plan: " + name);
                }
            });
            open(dialog, screen, open);
        });
        return screen;
    }

    // ==================== Drag and resize ====================

    private static Node movable(BooleanProperty open) {
        RXButton show = trigger(solid("Open notes", "teal"), open);
        StackPane screen = screen(show);
        show.setOnAction(event -> {
            Label body = text("Drag this card by its title, or pull an edge to make it bigger.", "copy");
            body.setWrapText(true);
            RXDialogContent content = new RXDialogContent();
            content.setHeaderText("Sticky note");
            content.setContent(body);
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setContent(content);
            dialog.setEnableDraggable(true);
            dialog.setEnableResizable(true);
            dialog.getButtonTypes().setAll(ButtonType.CLOSE);
            open(dialog, screen, open);
        });
        return screen;
    }

    /** "SLIDE_LEFT" becomes "Slide left". */
    private static String label(DialogTransition transition) {
        String words = transition.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
