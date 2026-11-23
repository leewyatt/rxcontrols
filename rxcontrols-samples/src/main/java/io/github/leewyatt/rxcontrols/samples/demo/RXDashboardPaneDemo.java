package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

/**
 * Demo for {@link RXDashboardPane}: eleven numbered cards on an eight-column grid with 150 px rows. Drag a card to
 * move it, or pull its right or bottom edge to resize it; the other cards make room and pack upward. The layout is
 * saved to the user's preferences whenever it changes and restored at the next start, so the board reopens as it was
 * left.
 */
public class RXDashboardPaneDemo extends Application {

    private static final String LAYOUT_KEY = "dashboard.demo.layout";

    // Column, row, column span and row span of each card, numbered from 1 in this order.
    private static final int[][] CARDS = {
            {0, 0, 2, 1}, {2, 0, 3, 2}, {5, 0, 1, 1}, {6, 0, 2, 1},
            {0, 1, 1, 1}, {1, 1, 1, 2}, {5, 1, 2, 1}, {7, 1, 1, 2},
            {0, 2, 1, 1}, {2, 2, 2, 1}, {4, 2, 3, 1}
    };

    @Override
    public void start(Stage stage) {
        RXDashboardPane dashboard = new RXDashboardPane();
        dashboard.setColumnCount(8);
        dashboard.setRowHeight(150);
        dashboard.setHgap(12);
        dashboard.setVgap(12);
        dashboard.setPadding(new Insets(16));
        for (int i = 0; i < CARDS.length; i++) {
            int[] cell = CARDS[i];
            int number = i + 1;
            dashboard.getCards().add(new RXDashboardCard(String.valueOf(number), content(number),
                    cell[0], cell[1], cell[2], cell[3]));
        }

        // Restore the layout the demo was left with, if it can be read, and save every change from then on.
        Preferences preferences = Preferences.userNodeForPackage(RXDashboardPaneDemo.class);
        String saved = preferences.get(LAYOUT_KEY, null);
        if (saved != null) {
            try {
                dashboard.applyLayout(RXDashboardLayout.decode(saved));
            } catch (IllegalArgumentException unreadable) {
                preferences.remove(LAYOUT_KEY);
            }
        }
        dashboard.layoutProperty().addListener((obs, old, layout) -> preferences.put(LAYOUT_KEY, layout.encode()));

        // Pulling a card taller pushes the others down, so the board may outgrow the window.
        ScrollPane scroller = new ScrollPane(dashboard);
        scroller.setFitToWidth(true);
        Label note = new Label("Your layout is saved automatically and restored the next time the demo starts.");
        note.getStyleClass().add("layout-note");
        BorderPane root = new BorderPane(scroller);
        root.setBottom(note);
        Scene scene = new Scene(root, 1280, 650);
        scene.getStylesheets().add(getClass().getResource("rx-dashboard-pane-demo.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("RXDashboardPane Demo");
        stage.show();
    }

    private static Node content(int number) {
        Label title = new Label("#" + number);
        title.getStyleClass().add("card-number");
        Label hint = new Label("Drag me · Resize me");
        hint.getStyleClass().add("card-hint");
        VBox box = new VBox(4.0, title, hint);
        box.setAlignment(Pos.CENTER);
        return box;
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
