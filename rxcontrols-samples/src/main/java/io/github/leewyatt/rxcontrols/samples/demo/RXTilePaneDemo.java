package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXTilePane}: a wall of colored node cards laid out in a
 * responsive tile grid. Reorder animation is on, so resizing the window reflows
 * the columns and the cards glide to their new positions. The pane is installed
 * directly as the scene root so its vertical alignment can be inspected without
 * an outer scroll container.
 *
 * <p>There is no control panel by design.</p>
 */
public class RXTilePaneDemo extends Application {

    private static final int CARD_COUNT = 17;

    @Override
    public void start(Stage primaryStage) {
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(140);
        tiles.setPrefTileHeight(100);
        tiles.setHgap(14);
        tiles.setVgap(14);
        tiles.setStyle("-fx-padding: 10px; -fx-background-color: #c2cce0ab;");
        tiles.setAnimated(true);
        tiles.setItemsJustify(ItemsJustify.CENTER);
        tiles.setContentVAlignment(VPos.CENTER);
        for (int i = 0; i < CARD_COUNT; i++) {
            tiles.getChildren().add(card(i));
        }
        Scene scene = new Scene(tiles, 900.0, 640.0);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXTilePane Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Card wall");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label("Resize the window — the cards reflow and glide to their new slots");
        subtitle.setStyle("-fx-text-fill: #6b7280;");
        VBox header = new VBox(2.0, title, subtitle);
        header.setPadding(new Insets(16, 16, 8, 16));
        return header;
    }

    private Region card(int index) {
        Label label = new Label("Card " + (index + 1));
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        StackPane card = new StackPane(label);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        double hue = (index * 29) % 360;
        card.setStyle("-fx-background-color: hsb(" + hue + ", 55%, 80%); -fx-background-radius: 12;");
        return card;
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
