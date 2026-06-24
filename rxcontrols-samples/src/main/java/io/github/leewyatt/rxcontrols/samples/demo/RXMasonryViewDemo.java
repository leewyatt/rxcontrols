package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
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
 * Compact demo for {@link RXMasonryView}: a virtualized photo wall where each cell's
 * height comes from its aspect ratio via a {@code cellHeightProvider} — the precise,
 * never-jumping masonry path. Items of different heights tile into the shortest
 * column, and only the cells intersecting the viewport ever hold live cells, so the
 * same code scales from dozens to thousands of photos.
 *
 * <p>There is no control panel by design; resize the window to see the column count
 * and the waterfall reflow.</p>
 */
public class RXMasonryViewDemo extends Application {

    private static final int PHOTO_COUNT = 10000;
    // A spread of portrait / square / landscape ratios (width : height) so the
    // columns genuinely stagger.
    private static final double[] RATIOS = {0.72, 1.0, 1.5, 0.85, 1.2, 0.66, 1.78};

    private record Photo(String title, double aspectRatio) {
    }

    @Override
    public void start(Stage primaryStage) {
        ObservableList<Photo> photos = FXCollections.observableArrayList();
        for (int i = 0; i < PHOTO_COUNT; i++) {
            photos.add(new Photo("Photo " + (i + 1), RATIOS[i % RATIOS.length]));
        }

        RXMasonryView<Photo> masonry = new RXMasonryView<>(photos);
        masonry.setColumnWidth(200);
        masonry.setHgap(12);
        masonry.setVgap(12);
        masonry.setPadding(new Insets(12));
        // Exact height from the slot width and the photo's aspect ratio.
        masonry.setCellHeightProvider(context -> context.cellWidth() / context.item().aspectRatio());
        masonry.setCellFactory(view -> new PhotoCell());
        // Glide the tiles to their new slots when a resize changes the column count.
        masonry.setAnimated(true);

        BorderPane root = new BorderPane(masonry);
        root.setTop(createHeader());

        Scene scene = new Scene(root, 920.0, 680.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMasonryView Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Masonry photo wall");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label(
                "Each tile's height is its width over its aspect ratio — resize to reflow the columns");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
    }

    /**
     * A tile that paints a rounded swatch tinted by the photo's index, with the title
     * pinned to the bottom-left.
     */
    private static final class PhotoCell extends RXMasonryCell<Photo> {

        private static final String BASE_STYLE =
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10;";

        private PhotoCell() {
            setAlignment(Pos.BOTTOM_LEFT);
            setWrapText(true);
            setStyle(BASE_STYLE);
        }

        @Override
        protected void updateItem(Photo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(BASE_STYLE);
            } else {
                setText(item.title());
                double hue = (getIndex() * 37.0) % 360.0;
                setStyle(BASE_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 72%);"
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
