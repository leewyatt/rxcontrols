package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXStatePane;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Compact demo for {@link RXStatePane}: a realistic async flow — the first
 * fetch covers the pane with the loading overlay, a refresh stacks the spinner
 * over the still-visible rows (the two axes are orthogonal), a failing fetch
 * replaces the content with the default error view whose retry button
 * reloads, and an empty fetch shows the default "No data" placeholder.
 *
 * <p>For the full property explorer see the RXStatePane showcase.</p>
 */
public class RXStatePaneDemo extends Application {

    private static final Duration FAKE_LATENCY = Duration.millis(1200.0);

    private RXStatePane statePane;
    private int fetchCount;

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        statePane = new RXStatePane();
        statePane.setContent(createRows());
        statePane.setLoadingText("Fetching articles...");
        statePane.setOnRetry(event -> fetch(false, false));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> fetch(false, false));
        Button failing = new Button("Failing fetch");
        failing.setOnAction(event -> fetch(true, false));
        Button empty = new Button("Empty fetch");
        empty.setOnAction(event -> fetch(false, true));
        HBox toolbar = new HBox(10.0, refresh, failing, empty);
        toolbar.getStyleClass().add("toolbar");

        BorderPane root = new BorderPane(statePane);
        root.setTop(toolbar);
        root.getStyleClass().add("rx-state-pane-demo");

        Scene scene = new Scene(root, 760.0, 520.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-state-pane-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXStatePane Demo");
        primaryStage.show();

        // First fetch: the overlay covers the initial content.
        fetch(false, false);
    }

    // Simulates an async fetch; both axes are driven independently.
    private void fetch(boolean fail, boolean empty) {
        statePane.showLoading();
        PauseTransition latency = new PauseTransition(FAKE_LATENCY);
        latency.setOnFinished(event -> {
            statePane.hideLoading();
            if (fail) {
                statePane.showError();
            } else if (empty) {
                statePane.showEmpty();
            } else {
                fetchCount++;
                statePane.setContent(createRows());
                statePane.showContent();
            }
        });
        latency.play();
    }

    private VBox createRows() {
        VBox rows = new VBox(8.0);
        rows.getStyleClass().add("rows");
        rows.setAlignment(Pos.TOP_LEFT);
        rows.setPadding(new Insets(16.0));
        for (int i = 1; i <= 6; i++) {
            Label title = new Label("Article " + i + " (batch " + (fetchCount + 1) + ")");
            title.getStyleClass().add("row-title");
            Label summary = new Label("A short teaser line for article " + i + ".");
            summary.getStyleClass().add("row-summary");
            VBox row = new VBox(2.0, title, summary);
            row.getStyleClass().add("row");
            row.setMaxWidth(Double.MAX_VALUE);
            rows.getChildren().add(row);
        }
        return rows;
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
