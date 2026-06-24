package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.CellHeightProvider;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXMasonryView}: a virtualized text wall where each tile holds
 * a title plus a paragraph of its own length, so the tiles genuinely stagger into the
 * shortest column and only the cells intersecting the viewport ever hold live cells —
 * the same code scales from dozens to thousands of tiles.
 *
 * <p>The "Measured heights" toggle switches between the two height paths: unchecked uses
 * a {@code cellHeightProvider} (the precise, never-jumping path); checked clears it, so
 * the skin estimates, measures each realized cell's real height and re-packs to converge
 * (the estimated path). Resize the window to see the column count and the reflow.</p>
 */
public class RXMasonryViewDemo extends Application {

    private static final int TILE_COUNT = 10000;
    private static final double APPROX_LINE_HEIGHT = 21.0;
    private static final double TILE_CHROME_HEIGHT = 58.0;

    private record Note(String title, int paragraphs) {
    }

    @Override
    public void start(Stage primaryStage) {
        ObservableList<Note> notes = FXCollections.observableArrayList();
        for (int i = 0; i < TILE_COUNT; i++) {
            notes.add(new Note("Note " + (i + 1), 1 + (i % 5)));
        }

        RXMasonryView<Note> masonry = new RXMasonryView<>(notes);
        masonry.setColumnWidth(220);
        masonry.setHgap(12);
        masonry.setVgap(12);
        masonry.setPadding(new Insets(12));
        masonry.setCellFactory(view -> new NoteCell());
        // Glide the tiles to their new slots when a resize changes the column count.
        masonry.setAnimated(true);

        // The precise path: a per-item height approximating the rendered paragraph.
        CellHeightProvider<Note> provider = context ->
                TILE_CHROME_HEIGHT + context.item().paragraphs() * APPROX_LINE_HEIGHT * 2.0;
        masonry.setCellHeightProvider(provider);

        CheckBox measuredToggle = new CheckBox("Measured heights (estimated path)");
        measuredToggle.selectedProperty().addListener((obs, was, measured) ->
                masonry.setCellHeightProvider(measured ? null : provider));

        BorderPane root = new BorderPane(masonry);
        root.setTop(createHeader(measuredToggle));

        Scene scene = new Scene(root, 920.0, 680.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMasonryView Demo");
        primaryStage.show();
    }

    private Region createHeader(CheckBox measuredToggle) {
        Label title = new Label("Masonry note wall");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Tiles of different heights tile into the shortest column — resize to reflow");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox text = new VBox(2.0, title, subtitle);
        HBox header = new HBox(24.0, text, measuredToggle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
    }

    /**
     * A tile that paints a rounded card tinted by the note's index, with the title and a
     * paragraph of its own length wrapped inside.
     */
    private static final class NoteCell extends RXMasonryCell<Note> {

        private static final String BASE_STYLE =
                "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 12;";

        private NoteCell() {
            setAlignment(Pos.TOP_LEFT);
            setWrapText(true);
            setStyle(BASE_STYLE);
        }

        @Override
        protected void updateItem(Note item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(BASE_STYLE);
            } else {
                setText(blurb(item));
                double hue = (getIndex() * 37.0) % 360.0;
                setStyle(BASE_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 70%);"
                        + " -fx-background-radius: 12;");
            }
        }

        private static String blurb(Note note) {
            StringBuilder builder = new StringBuilder(note.title());
            for (int i = 0; i < note.paragraphs(); i++) {
                builder.append("\n\nLorem ipsum dolor sit amet, consectetur adipiscing elit.");
            }
            return builder.toString();
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
