package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Comparator;

/**
 * Compact demo for {@link RXTileView}: a photo wall grouped into year sections.
 * The items are wrapped in a {@link SortedList} so that adjacent equal keys (the
 * year) cluster into sections — the minimum code to use grouping — and reorder
 * animation is on, so resizing the window reflows the columns with a glide.
 *
 * <p>There is no control panel by design; only the visible rows ever hold live
 * cells, so the same code scales from dozens to millions of tiles.</p>
 */
public class RXTileViewDemo extends Application {

    private static final int PHOTO_COUNT = 120;
    private static final int FIRST_YEAR = 2018;
    private static final int YEAR_SPAN = 6;

    private record Photo(String title, int year) {
    }

    @Override
    public void start(Stage primaryStage) {
        ObservableList<Photo> raw = FXCollections.observableArrayList();
        for (int i = 0; i < PHOTO_COUNT; i++) {
            // Years are interleaved so the SortedList genuinely has to cluster them.
            raw.add(new Photo("Photo " + (i + 1), FIRST_YEAR + (i % YEAR_SPAN)));
        }
        SortedList<Photo> sorted = new SortedList<>(raw, Comparator.comparingInt(Photo::year));

        RXTileView<Photo> tiles = new RXTileView<>(sorted);
        tiles.setPrefTileWidth(120);
        tiles.setPrefTileHeight(120);
        tiles.setHgap(14);
        tiles.setVgap(14);
        tiles.setPadding(new Insets(14));
        tiles.setSectionKeyFactory(Photo::year);
        tiles.setCellFactory(view -> new PhotoCell());
        tiles.setSectionHeaderFactory(view -> new YearHeader());
        tiles.setStickySectionHeader(true);
        tiles.setAnimated(true);

        BorderPane root = new BorderPane(tiles);
        root.setTop(createHeader());

        Scene scene = new Scene(root, 900.0, 640.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXTileView Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Photos by year");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Resize the window — columns reflow and the tiles glide to their new slots");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
    }

    /**
     * A tile that paints a rounded swatch tinted by the photo's year.
     */
    private static final class PhotoCell extends RXTileCell<Photo> {

        private static final String BASE_STYLE =
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;";

        private PhotoCell() {
            setAlignment(Pos.CENTER);
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
                double hue = ((item.year() - FIRST_YEAR) * 60.0) % 360.0;
                setStyle(BASE_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 78%);"
                        + " -fx-background-radius: 12;");
            }
        }
    }

    /**
     * A section header showing the year and its photo count.
     */
    private static final class YearHeader extends RXTileSectionCell {

        @Override
        protected void updateItem(RXTileSection section, boolean empty) {
            super.updateItem(section, empty);
            setText(empty || section == null
                    ? null
                    : section.key() + "  ·  " + section.itemCount() + " photos");
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
