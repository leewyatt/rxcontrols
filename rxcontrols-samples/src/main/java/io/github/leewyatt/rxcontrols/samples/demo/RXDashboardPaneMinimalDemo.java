package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXDashboardPane}: eleven numbered cards on a six-column, three-row grid. Drag a card to move
 * it, or pull its right or bottom edge to resize it; the other cards make room.
 */
public class RXDashboardPaneMinimalDemo extends Application {

    // Column, row, column span and row span of each card, numbered from 1 in this order.
    private static final int[][] CARDS = {
            {0, 0, 2, 1}, {2, 0, 2, 2}, {4, 0, 1, 1}, {5, 0, 1, 1},
            {0, 1, 1, 1}, {1, 1, 1, 2}, {4, 1, 2, 1},
            {0, 2, 1, 1}, {2, 2, 2, 1}, {4, 2, 1, 1}, {5, 2, 1, 1}
    };

    @Override
    public void start(Stage stage) {
        RXDashboardPane dashboard = new RXDashboardPane();
        dashboard.setColumnCount(6);
        dashboard.setRowHeight(190);
        dashboard.setHgap(10);
        dashboard.setVgap(10);
        dashboard.setPadding(new Insets(10));
        dashboard.setStyle("-fx-background-color: #eceef1; -fx-background-radius: 8;");
        dashboard.setMaxHeight(Region.USE_PREF_SIZE);
        for (int i = 0; i < CARDS.length; i++) {
            int[] cell = CARDS[i];
            String number = String.valueOf(i + 1);
            dashboard.getCards().add(new RXDashboardCard(number, new Label(number), cell[0], cell[1], cell[2], cell[3]));
        }

        StackPane root = new StackPane(dashboard);
        root.setPadding(new Insets(20));
        StackPane.setAlignment(dashboard, Pos.TOP_CENTER);
        stage.setScene(new Scene(root, 1240, 650));
        stage.setTitle("RXDashboardPane Minimal Demo");
        stage.show();
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
