package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.DialogTransition;
import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDialogContent;
import io.github.leewyatt.rxcontrols.RXOverlayPane;
import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXDialog page: a project settings workspace demonstrates decisions, validated
 * input and converted results, while a configurable preview exposes the dialog chrome,
 * transitions and movable-card options in one place.
 */
public final class RXDialogSamples {

    private RXDialogSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        BooleanProperty dialogOpen = new SimpleBooleanProperty();
        return new SampleContent(
                "A modal card over a dimmed scrim, shown inside the scene, with buttons that close it and deliver a result.",
                List.of("Ask for a decision or a short input before going on: confirm a delete, rename, pick an option.",
                        "For a message that needs no answer, use RXSnackbarHost."),
                List.of(
                        SampleCard.canvas("Project settings",
                                "Rename the workspace, change its plan or request a destructive action from one realistic settings screen.",
                                projectSettings(dialogOpen)),
                        SampleCard.canvas("Dialog playground",
                                "Choose the transition and chrome options, then open one dialog to feel the complete combination.",
                                playground(dialogOpen))),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Project settings ====================

    private static Node projectSettings(BooleanProperty dialogOpen) {
        Label workspaceName = text("Weekly planning", "copy-strong");
        Label currentPlan = text("Pro", "copy-strong");
        Label status = text("Workspace settings are up to date", "workspace-status");

        RXButton rename = trigger(plain("Rename"), dialogOpen);
        RXButton changePlan = trigger(plain("Change plan"), dialogOpen);
        RXButton delete = trigger(solid("Delete workspace", "danger"), dialogOpen);

        VBox rows = new VBox(
                settingRow("note-icon", "Workspace name", workspaceName, highlighted(rename, "rename-beam")),
                settingRow("star-icon", "Current plan", currentPlan, highlighted(changePlan, "plan-beam")),
                settingRow("delete-icon", "Danger zone",
                        text("Permanently remove this workspace and its files", "muted"),
                        highlighted(delete, "delete-beam")));
        rows.getStyleClass().add("workspace-rows");

        StackPane brand = new StackPane(icon("folder-icon"));
        brand.getStyleClass().add("workspace-brand");
        brand.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox heading = new VBox(2.0, text("Northwind Studio", "headline"),
                text("Project settings", "muted"));
        HBox header = new HBox(12.0, brand, heading);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("workspace-header");
        HBox footer = new HBox(status);
        footer.getStyleClass().add("workspace-footer");

        VBox workspace = new VBox(header, rows, footer);
        workspace.getStyleClass().add("dialog-workspace");
        StackPane screen = screen(workspace, 380.0);

        rename.setOnAction(event -> showRenameDialog(workspace, dialogOpen, workspaceName, status));
        changePlan.setOnAction(event -> showPlanDialog(workspace, dialogOpen, currentPlan, status));
        delete.setOnAction(event -> showDeleteDialog(workspace, dialogOpen, status));
        return screen;
    }

    private static HBox settingRow(String iconClass, String title, Node value, Node action) {
        StackPane graphic = new StackPane(icon(iconClass));
        graphic.getStyleClass().add("settings-icon");
        graphic.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox copy = new VBox(3.0, text(title, "copy"), value);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(12.0, graphic, copy, action);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("workspace-row");
        return row;
    }

    private static RXBorderBeam highlighted(Node content, String styleClass) {
        RXBorderBeam beam = new RXBorderBeam(content);
        beam.getStyleClass().addAll("dialog-action-beam", styleClass);
        beam.setMaxWidth(Region.USE_PREF_SIZE);
        return beam;
    }

    private static void showRenameDialog(Node owner, BooleanProperty dialogOpen,
                                         Label workspaceName, Label status) {
        TextField field = new TextField(workspaceName.getText());
        Label error = text("", "error-text");
        RXDialogContent content = new RXDialogContent();
        content.setHeaderText("Rename workspace");
        content.setContent(new VBox(6.0, field, error));

        ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, saveType);
        dialog.setResultConverter(type -> type);
        dialog.setOnCloseRequest(request -> {
            if (request.getButtonType() == saveType && field.getText().isBlank()) {
                error.setText("The name cannot be empty.");
                request.consume();
            }
        });
        dialog.setOnResult(type -> {
            if (type == saveType) {
                workspaceName.setText(field.getText().trim());
                status.setText("Workspace renamed to “" + workspaceName.getText() + "”");
            }
        });
        open(dialog, owner, dialogOpen);
        field.requestFocus();
    }

    private static void showPlanDialog(Node owner, BooleanProperty dialogOpen,
                                       Label currentPlan, Label status) {
        ToggleGroup group = new ToggleGroup();
        VBox options = new VBox(10.0);
        for (String name : List.of("Basic", "Pro", "Team")) {
            RXRadioButton radio = new RXRadioButton(name);
            radio.setToggleGroup(group);
            radio.setSelected(currentPlan.getText().equals(name));
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
                currentPlan.setText(name);
                status.setText("Plan changed to " + name);
            }
        });
        open(dialog, owner, dialogOpen);
    }

    private static void showDeleteDialog(Node owner, BooleanProperty dialogOpen, Label status) {
        ButtonType deleteType = new ButtonType("Delete", ButtonData.OK_DONE);
        RXDialogContent content = new RXDialogContent("Delete this workspace?",
                "Northwind Studio and all of its files will be permanently removed. This cannot be undone.");
        Region warning = icon("warning-icon");
        warning.getStyleClass().add("danger");
        content.setGraphic(warning);

        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, deleteType);
        dialog.setResultConverter(type -> type);
        dialog.setOnResult(type -> status.setText(type == deleteType
                ? "Delete requested for Northwind Studio" : "Workspace kept"));
        open(dialog, owner, dialogOpen);
    }

    // ==================== Playground ====================

    private static Node playground(BooleanProperty dialogOpen) {
        RXCheckBox closeButton = option("Close button", false);
        RXCheckBox scrimClose = option("Scrim closes", true);
        RXCheckBox draggable = option("Draggable", false);
        RXCheckBox resizable = option("Resizable", false);

        RXFlowPane controls = new RXFlowPane(8.0, 8.0,
                closeButton, scrimClose, draggable, resizable);
        controls.setAnimated(false);
        controls.getStyleClass().add("dialog-toolbar");

        Label result = text("No decision yet", "muted");
        RXButton top = directionButton("Top", false, dialogOpen);
        RXButton right = directionButton("Right", false, dialogOpen);
        RXButton bottom = directionButton("Bottom", false, dialogOpen);
        RXButton left = directionButton("Left", false, dialogOpen);
        RXButton center = directionButton("Center", true, dialogOpen);
        GridPane directions = new GridPane();
        directions.setHgap(10.0);
        directions.setVgap(10.0);
        directions.setAlignment(Pos.CENTER);
        directions.getStyleClass().add("dialog-directions");
        directions.add(top, 1, 0);
        directions.add(left, 0, 1);
        directions.add(center, 1, 1);
        directions.add(right, 2, 1);
        directions.add(bottom, 1, 2);

        VBox canvas = new VBox(14.0,
                text("Choose an entry direction", "playground-title"), directions, result);
        canvas.setAlignment(Pos.CENTER);
        canvas.getStyleClass().add("dialog-playground");
        StackPane screen = screen(canvas, 340.0);

        setPreviewAction(top, DialogTransition.SLIDE_TOP, canvas, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable);
        setPreviewAction(right, DialogTransition.SLIDE_RIGHT, canvas, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable);
        setPreviewAction(bottom, DialogTransition.SLIDE_BOTTOM, canvas, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable);
        setPreviewAction(left, DialogTransition.SLIDE_LEFT, canvas, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable);
        setPreviewAction(center, DialogTransition.CENTER, canvas, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable);

        return new VBox(12.0, controls, screen);
    }

    private static RXButton directionButton(String text, boolean primary, BooleanProperty dialogOpen) {
        RXButton button = trigger(primary ? solid(text) : plain(text), dialogOpen);
        button.setPrefWidth(88.0);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private static void setPreviewAction(RXButton button, DialogTransition transition, Node owner,
                                         BooleanProperty dialogOpen, Label result,
                                         RXCheckBox closeButton, RXCheckBox scrimClose,
                                         RXCheckBox draggable, RXCheckBox resizable) {
        button.setOnAction(event -> showPreviewDialog(transition, owner, dialogOpen, result,
                closeButton, scrimClose, draggable, resizable));
    }

    private static void showPreviewDialog(DialogTransition transition, Node owner,
                                          BooleanProperty dialogOpen, Label result,
                                          RXCheckBox closeButton, RXCheckBox scrimClose,
                                          RXCheckBox draggable, RXCheckBox resizable) {
        RXDialogContent content = new RXDialogContent("Publish these changes?",
                "Members will see the new workspace settings immediately after you publish.");
        content.setGraphic(icon("spark-icon"));
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setTransition(transition);
        dialog.setShowCloseButton(closeButton.isSelected());
        dialog.setCloseOnScrimClick(scrimClose.isSelected());
        dialog.setEnableDraggable(draggable.isSelected());
        dialog.setEnableResizable(resizable.isSelected());
        dialog.setResultConverter(type -> type);
        dialog.setOnResult(type -> result.setText(type == ButtonType.OK
                ? "Changes published" : "No changes published"));
        open(dialog, owner, dialogOpen);
    }

    private static RXCheckBox option(String text, boolean selected) {
        RXCheckBox option = new RXCheckBox(text);
        option.setSelected(selected);
        return option;
    }

    // ==================== Helpers ====================

    // The overlay pane keeps the dialogs of this content inside its screen.
    private static StackPane screen(Node content, double height) {
        return FeedbackKit.screen(720.0, height, new RXOverlayPane(content));
    }

    private static <R> void open(RXDialog<R> dialog, Node owner, BooleanProperty dialogOpen) {
        dialogOpen.set(true);
        dialog.setOnHidden(event -> dialogOpen.set(false));
        dialog.show(owner);
    }

    private static RXButton trigger(RXButton button, BooleanProperty dialogOpen) {
        button.disableProperty().bind(dialogOpen);
        return button;
    }
}
