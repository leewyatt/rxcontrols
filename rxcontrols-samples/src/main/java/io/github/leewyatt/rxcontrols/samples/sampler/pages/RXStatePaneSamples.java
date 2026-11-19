package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXStatePane;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXStatePane page: fast and slow requests with an anti-flicker delay, the default
 * empty and error views with retry, an import with determinate progress, a background
 * save that keeps the editor usable and a custom empty view.
 */
public final class RXStatePaneSamples {

    private static final double PANE_WIDTH = 360.0;
    private static final double PANE_HEIGHT = 220.0;
    private static final int CONTACT_COUNT = 40;

    private RXStatePaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A region that shows its content, an empty view or an error view, with a loading overlay on top.",
                List.of("Any region filled by a request: a list, a table, a dashboard tile that can be loading, empty or broken.",
                        "For a placeholder alone use RXPlaceholder; for a loading shape of the content itself use RXSkeleton."),
                List.of(
                        SampleCard.large("Fast and slow requests",
                                "loadingDelay 300 ms keeps the overlay away for the fast request; loadingMinDuration keeps it up long enough to read.",
                                requests()),
                        SampleCard.large("Error and retry",
                                "The default error view shows a Retry button as soon as onRetry is set.",
                                retry()),
                        SampleCard.large("Empty result",
                                "showEmpty swaps the content for the default \"No data\" view.",
                                empty()),
                        SampleCard.large("Import with progress",
                                "A progress between 0 and 1 makes the overlay ring determinate; the loading text counts contacts.",
                                importing()),
                        SampleCard.large("Saving in the background",
                                "With dimmed and blocking off, the overlay shows but the editor stays usable.",
                                saving()),
                        SampleCard.large("Custom empty view",
                                "emptyContent takes your own RXPlaceholder, here with an action that creates the first project.",
                                customEmpty())),
                FeedbackKit.STYLESHEET);
    }

    private static RXStatePane pane(Node content) {
        RXStatePane pane = new RXStatePane();
        pane.setContent(content);
        pane.getStyleClass().add("panel");
        pane.setPrefSize(PANE_WIDTH, PANE_HEIGHT);
        pane.setMinHeight(PANE_HEIGHT);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pane;
    }

    private static Node rows(String... lines) {
        VBox list = new VBox(0.0);
        for (int i = 0; i + 1 < lines.length; i += 2) {
            VBox texts = new VBox(2.0, text(lines[i], "copy-strong"), text(lines[i + 1], "muted"));
            HBox row = new HBox(12.0, icon("file-icon"), texts);
            row.getStyleClass().add("panel-row");
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        return list;
    }

    private static Node articles() {
        return rows("Designing calm interfaces", "6 min read",
                "A field guide to empty states", "4 min read",
                "Loading without the flicker", "8 min read");
    }

    private static Node withActions(Node pane, Node... actions) {
        HBox bar = new HBox(8.0, actions);
        bar.setAlignment(Pos.CENTER);
        VBox box = new VBox(14.0, pane, bar);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Fast and slow requests ====================

    private static Node requests() {
        RXStatePane pane = pane(articles());
        pane.setLoadingText("Fetching articles");
        pane.setLoadingDelay(Duration.millis(300.0));
        pane.setLoadingMinDuration(Duration.millis(600.0));
        RXButton fast = plain("Fast request (0.1 s)");
        fast.setOnAction(event -> fetch(pane, Duration.millis(100.0)));
        RXButton slow = solid("Slow request (1.5 s)", "indigo");
        slow.setOnAction(event -> fetch(pane, Duration.seconds(1.5)));
        return withActions(pane, fast, slow);
    }

    private static void fetch(RXStatePane pane, Duration latency) {
        pane.showLoading();
        PauseTransition wait = new PauseTransition(latency);
        wait.setOnFinished(event -> {
            pane.hideLoading();
            pane.showContent();
        });
        wait.play();
    }

    // ==================== Error and retry ====================

    private static Node retry() {
        RXStatePane pane = pane(rows("Order #2041", "Shipped today", "Order #2038", "Delivered",
                "Order #2032", "Delivered"));
        pane.setLoadingText("Trying again");
        pane.setOnRetry(event -> fetch(pane, Duration.seconds(1.2)));
        pane.showError();
        RXButton fail = plain("Fail again");
        fail.setOnAction(event -> pane.showError());
        fail.disableProperty().bind(pane.stateProperty().isEqualTo(RXStatePane.State.ERROR));
        FocusHandOff.onDisable(fail, pane);
        return withActions(pane, fail);
    }

    // ==================== Empty result ====================

    private static Node empty() {
        RXStatePane pane = pane(rows("invoice-1042.pdf", "Paid", "invoice-1043.pdf", "Due in 5 days",
                "invoice-1044.pdf", "Draft"));
        pane.showEmpty();
        RXButton show = solid("Show invoices", "teal");
        show.setOnAction(event -> pane.showContent());
        RXButton clear = plain("Delete all");
        clear.setOnAction(event -> pane.showEmpty());
        show.disableProperty().bind(pane.stateProperty().isEqualTo(RXStatePane.State.CONTENT));
        clear.disableProperty().bind(pane.stateProperty().isEqualTo(RXStatePane.State.EMPTY));
        FocusHandOff.onDisable(show, clear);
        FocusHandOff.onDisable(clear, show);
        return withActions(pane, show, clear);
    }

    // ==================== Import with progress ====================

    private static Node importing() {
        RXStatePane pane = pane(rows("Lee Wyatt", "@LeeWyatt_7788", "Design team", "12 members",
                "Support desk", "support@example.com"));
        RXButton start = solid("Import contacts", "orange");
        start.setGraphic(icon("upload-icon"));
        FocusHandOff.onDisable(start, pane);
        start.setOnAction(event -> {
            start.setDisable(true);
            pane.setProgress(0.0);
            pane.showLoading();
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3.0),
                    new KeyValue(pane.progressProperty(), 1.0)));
            timeline.setOnFinished(done -> {
                pane.hideLoading();
                start.setDisable(false);
            });
            timeline.play();
        });
        pane.progressProperty().addListener((obs, oldValue, newValue) -> pane.setLoadingText(
                "Importing " + Math.round(newValue.doubleValue() * CONTACT_COUNT) + " of " + CONTACT_COUNT));
        return withActions(pane, start);
    }

    // ==================== Saving in the background ====================

    private static Node saving() {
        TextArea editor = new TextArea("Meeting notes\n\n- Ship the beta on Friday\n- Lee writes the release notes");
        editor.setWrapText(true);
        RXStatePane pane = pane(editor);
        pane.setDimmed(false);
        pane.setBlocking(false);
        pane.setLoadingText("Saving");
        RXButton save = solid("Save", "violet");
        save.setOnAction(event -> fetch(pane, Duration.seconds(2.0)));
        return withActions(pane, text("Keep typing while it saves", "muted"), spacer(), save);
    }

    // ==================== Custom empty view ====================

    private static Node customEmpty() {
        RXStatePane pane = pane(rows("Website redesign", "Due next week", "Mobile app", "In review",
                "Brand refresh", "Planning"));
        Region graphic = icon("folder-icon");
        graphic.getStyleClass().addAll("hero", "amber");
        RXPlaceholder placeholder = new RXPlaceholder("No projects yet", graphic);
        placeholder.setDescription("Projects group your boards and files.");
        RXButton create = solid("Create project", "amber");
        placeholder.getActions().add(create);
        pane.setEmptyContent(placeholder);
        pane.showEmpty();
        RXButton reset = plain("Archive all");
        reset.disableProperty().bind(pane.stateProperty().isEqualTo(RXStatePane.State.EMPTY));
        FocusHandOff.onDisable(reset, pane);
        // The empty view, Create project with it, goes away with the projects back, so
        // the focus moves on to Archive all, which comes back with them.
        create.setOnAction(event -> {
            pane.showContent();
            reset.requestFocus();
        });
        reset.setOnAction(event -> pane.showEmpty());
        return withActions(pane, reset);
    }
}
