package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXGridCell;
import io.github.leewyatt.rxcontrols.RXGridView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXGridView}: a virtualized wall of colored swatch
 * tiles built from a flat item list plus a cell factory — the minimum code to
 * use the control.
 *
 * <p>There is no control panel by design. Resize the window and watch the
 * columns reflow to fit the available width; only the visible rows ever hold
 * live cells, so the same code scales from dozens to millions of tiles.</p>
 */
public class RXGridViewDemo extends Application {

    private static final int TILE_COUNT = 240;

    @Override
    public void start(Stage primaryStage) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < TILE_COUNT; i++) {
            items.add(i);
        }

        RXGridView<Integer> grid = new RXGridView<>(items);
        grid.setCellWidth(120);
        grid.setCellHeight(120);
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(14));
        grid.setCellFactory(view -> new SwatchCell());

        BorderPane root = new BorderPane(grid);
        root.setTop(createHeader());

        Scene scene = new Scene(root, 900.0, 640.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXGridView Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Swatch wall");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Resize the window — the columns reflow to the available width");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
    }

    /**
     * A grid cell that paints a rounded color swatch and shows its index. The
     * color is derived from the item so scrolling shows a smooth hue sweep.
     */
    private static final class SwatchCell extends RXGridCell<Integer> {

        private static final String BASE_STYLE =
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;";

        private SwatchCell() {
            setAlignment(Pos.CENTER);
            setStyle(BASE_STYLE);
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(BASE_STYLE);
            } else {
                setText(String.valueOf(item));
                double hue = (item * 23) % 360;
                setStyle(BASE_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 80%);"
                        + " -fx-background-radius: 12;");
            }
        }
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
