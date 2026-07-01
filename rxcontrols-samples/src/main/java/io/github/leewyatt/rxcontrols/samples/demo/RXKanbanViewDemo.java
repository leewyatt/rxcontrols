package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXKanbanView}: a three-column board of string cards. Cards
 * reorder within a column and move across columns by pointer drag; the board keeps
 * only the visible cards of each column realized, so it scales to long columns.
 *
 * <p>There is no control panel by design — this is the least code needed to stand up
 * a working board. See {@code RXKanbanViewShowcase} for the property playground.</p>
 */
public class RXKanbanViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXKanbanView<String> board = new RXKanbanView<>();
        board.setColumns(FXCollections.observableArrayList(
                column("TODO", "Wire up API", "Draft docs", "Design icons", "Review PR #42"),
                column("DOING", "Kanban DnD", "Settle animation"),
                column("DONE", "Column model", "Virtualized viewport", "Board scroll")));

        BorderPane root = new BorderPane(board);
        root.setTop(createHeader());

        Scene scene = new Scene(root, 860.0, 600.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXKanbanView Demo");
        primaryStage.show();
    }

    private RXKanbanColumn<String> column(String title, String... cards) {
        RXKanbanColumn<String> column = new RXKanbanColumn<>(title);
        column.getCards().addAll(cards);
        return column;
    }

    private Region createHeader() {
        Label title = new Label("Sprint board");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Drag a card within a column to reorder, or across columns to move it");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
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
