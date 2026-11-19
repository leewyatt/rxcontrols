package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSnackbarRequest;
import io.github.leewyatt.rxcontrols.RXSnackbarSeverity;
import io.github.leewyatt.rxcontrols.RXSnackbarStrategy;
import io.github.leewyatt.rxcontrols.RXSnackbars;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXSnackbarHost page: every card is a small screen with its own host installed
 * through RXSnackbars.installInto, showing undo after a delete, the four severities,
 * host positions, queue and replace, a message that stays until closed and a bar with
 * custom content.
 */
public final class RXSnackbarHostSamples {

    private static final double SCREEN_WIDTH = 420.0;
    private static final double SCREEN_HEIGHT = 240.0;
    private static final String OFFLINE_KEY = "offline";
    private static final String UPLOAD_KEY = "upload";

    private RXSnackbarHostSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "Short messages at the edge of the screen that hide on their own, one at a time.",
                List.of("Confirm an action or offer a quick undo without interrupting: saved, deleted, sent, offline.",
                        "When the user must answer before going on, use RXDialog."),
                List.of(
                        SampleCard.large("Undo a delete",
                                "Delete a conversation; the bar's Undo action puts it back where it was.",
                                undo()),
                        SampleCard.large("Severities",
                                "RXSnackbars.success, info, warning and error tint the bar.",
                                severities()),
                        SampleCard.large("Position",
                                "The host's position moves the bar; bottom left is the default.",
                                positions()),
                        SampleCard.large("Queue or replace",
                                "Send three messages: QUEUE (the default) shows them in turn, REPLACE shows only the newest.",
                                strategies()),
                        SampleCard.large("Stays until closed",
                                "An indefinite duration keeps the bar up with a close icon until the connection is back.",
                                offline()),
                        SampleCard.large("Custom content",
                                "A request can carry any node; this one holds a spinner while files upload.",
                                upload())),
                FeedbackKit.STYLESHEET);
    }

    private static StackPane screen(Node content) {
        StackPane screen = FeedbackKit.screen(SCREEN_WIDTH, SCREEN_HEIGHT, content);
        RXSnackbarHost host = RXSnackbars.installInto(screen);
        host.setMargin(new Insets(12.0));
        host.setSnackbarMaxWidth(SCREEN_WIDTH - 24.0);
        return screen;
    }

    private static Node centered(Node... nodes) {
        FlowPane flow = new FlowPane(8.0, 8.0, nodes);
        flow.setAlignment(Pos.CENTER);
        flow.setPadding(new Insets(16.0, 16.0, 60.0, 16.0));
        return flow;
    }

    // ==================== Undo a delete ====================

    private static Node undo() {
        VBox list = new VBox(0.0);
        String[][] mails = {
                {"Design review", "Notes from today's session are attached."},
                {"Invoice #1042", "Your invoice is ready."},
                {"Weekend trip", "Train tickets are booked for Saturday."}};
        for (String[] mail : mails) {
            list.getChildren().add(mailRow(list, mail[0], mail[1]));
        }
        VBox content = new VBox(list);
        content.setPadding(new Insets(8.0));
        StackPane.setAlignment(content, Pos.TOP_CENTER);
        return screen(content);
    }

    private static Node mailRow(VBox list, String title, String summary) {
        RXButton delete = new RXButton(null, icon("delete-icon"));
        delete.getStyleClass().add("flat-icon");
        VBox texts = new VBox(2.0, text(title, "copy-strong"), text(summary, "muted"));
        HBox row = new HBox(12.0, icon("mail-icon"), texts, spacer(), delete);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        delete.setOnAction(event -> {
            int index = list.getChildren().indexOf(row);
            list.getChildren().remove(row);
            // The focused button leaves with its row; the focus moves to the row that
            // takes its place instead of wandering off with the page's scroll.
            List<Node> rows = list.getChildren();
            (rows.isEmpty() ? list : rows.get(Math.min(index, rows.size() - 1)).lookup(".flat-icon")).requestFocus();
            RXSnackbars.show(list, RXSnackbarRequest.builder("Conversation deleted")
                    .action("Undo", () -> list.getChildren().add(Math.min(index, list.getChildren().size()), row))
                    .build());
        });
        return row;
    }

    // ==================== Severities ====================

    private static Node severities() {
        RXButton success = plain("Save");
        RXButton info = plain("Check for updates");
        RXButton warning = plain("Copy a big file");
        RXButton error = plain("Upload");
        Node content = centered(success, info, warning, error);
        success.setOnAction(event -> RXSnackbars.success(content, "Changes saved"));
        info.setOnAction(event -> RXSnackbars.info(content, "Version 2.4 is available"));
        warning.setOnAction(event -> RXSnackbars.warning(content, "Only 2 GB of storage left"));
        error.setOnAction(event -> RXSnackbars.error(content, "Upload failed: the file is too large"));
        return screen(content);
    }

    // ==================== Position ====================

    private static Node positions() {
        RXButton topCenter = plain("Top center");
        RXButton topRight = plain("Top right");
        RXButton bottomLeft = plain("Bottom left");
        RXButton bottomCenter = plain("Bottom center");
        Node content = centered(topCenter, topRight, bottomLeft, bottomCenter);
        StackPane screen = screen(content);
        RXSnackbarHost host = RXSnackbars.installInto(screen);
        place(topCenter, host, Pos.TOP_CENTER);
        place(topRight, host, Pos.TOP_RIGHT);
        place(bottomLeft, host, Pos.BOTTOM_LEFT);
        place(bottomCenter, host, Pos.BOTTOM_CENTER);
        return screen;
    }

    private static void place(RXButton button, RXSnackbarHost host, Pos position) {
        button.setOnAction(event -> {
            host.dismiss();
            host.setPosition(position);
            host.show("Shown at the " + button.getText().toLowerCase(Locale.ROOT));
        });
    }

    // ==================== Queue or replace ====================

    private static Node strategies() {
        RXButton queue = plain("Send 3, queued");
        RXButton replace = plain("Send 3, replace");
        Node content = centered(queue, replace);
        StackPane screen = screen(content);
        RXSnackbarHost host = RXSnackbars.installInto(screen);
        host.setDefaultDuration(Duration.seconds(1.6));
        queue.setOnAction(event -> sendThree(host, RXSnackbarStrategy.QUEUE));
        replace.setOnAction(event -> sendThree(host, RXSnackbarStrategy.REPLACE));
        return screen;
    }

    private static void sendThree(RXSnackbarHost host, RXSnackbarStrategy strategy) {
        host.clear();
        host.setStrategy(strategy);
        for (int i = 1; i <= 3; i++) {
            host.show("Message " + i + " of 3");
        }
    }

    // ==================== Stays until closed ====================

    private static Node offline() {
        RXButton lose = solid("Lose connection", "rose");
        RXButton back = plain("Connection back");
        Node content = centered(lose, back);
        lose.setOnAction(event -> RXSnackbars.show(content, RXSnackbarRequest.builder("You are offline")
                .severity(RXSnackbarSeverity.WARNING)
                .duration(Duration.INDEFINITE)
                .showCloseIcon(true)
                .key(OFFLINE_KEY)
                .strategy(RXSnackbarStrategy.REPLACE)
                .build()));
        back.setOnAction(event -> {
            if (RXSnackbars.dismiss(content, OFFLINE_KEY)) {
                RXSnackbars.success(content, "Back online");
            }
        });
        return screen(content);
    }

    // ==================== Custom content ====================

    private static Node upload() {
        RXButton start = solid("Upload 3 files", "sky");
        start.setGraphic(icon("upload-icon"));
        Node content = centered(start);
        FocusHandOff.onDisable(start, content);
        start.setOnAction(event -> {
            start.setDisable(true);
            RXCircularProgressIndicator spinner = new RXCircularProgressIndicator();
            spinner.getStyleClass().add("on-snackbar");
            spinner.setMinSize(18.0, 18.0);
            spinner.setPrefSize(18.0, 18.0);
            spinner.setTrackStrokeWidth(2.0);
            spinner.setProgressStrokeWidth(2.0);
            Label message = text("Uploading 3 files", "snackbar-text");
            HBox body = new HBox(12.0, spinner, message);
            body.setAlignment(Pos.CENTER_LEFT);
            RXSnackbars.show(content, RXSnackbarRequest.builder("Uploading 3 files")
                    .content(body)
                    .duration(Duration.INDEFINITE)
                    .key(UPLOAD_KEY)
                    .build());
            PauseTransition wait = new PauseTransition(Duration.seconds(2.5));
            wait.setOnFinished(done -> {
                RXSnackbars.dismiss(content, UPLOAD_KEY);
                RXSnackbars.success(content, "3 files uploaded");
                start.setDisable(false);
            });
            wait.play();
        });
        return screen(content);
    }
}
