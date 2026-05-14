package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXImageView;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sample application demonstrating {@link RXImageView}.
 */
public class RXImageViewDemo extends Application {

    private static final String OPTION_NONE = "None";
    private static final String IMAGE_LABEL_PREFIX = "Image ";

    private static final String[] IMAGE_PATHS = {
            "/scenery/1.png", "/scenery/2.png", "/scenery/3.png", "/scenery/4.png"
    };

    private static final String[] SHAPE_NAMES = {
            OPTION_NONE, "Circle", "Hexagon", "Diamond", "Star", "Rounded Rect",
            "Heart", "Cross", "Octagon", "Shield", "Drop"
    };

    private RXImageView imageView;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setCenter(createContentPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 700, 380);
        scene.getStylesheets().add(getClass().getResource("rx-image-view-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXImageView Demo");
        primaryStage.show();
    }

    private Node createContentPane() {
        Image image = new Image(RXImageViewDemo.class.getResource("/scenery/2.png").toExternalForm());
        imageView = new RXImageView(image);
        StackPane pane = new StackPane(imageView);
        pane.getStyleClass().add("content-pane");
        return pane;
    }

    private Node createControlPane() {
        Label title = new Label("RXImageView");
        title.getStyleClass().add("title-label");

        // ==================== Image ====================
        ComboBox<String> imageBox = new ComboBox<>();
        imageBox.getItems().add(OPTION_NONE);
        for (int i = 0; i < IMAGE_PATHS.length; i++) {
            imageBox.getItems().add(IMAGE_LABEL_PREFIX + (i + 1));
        }
        imageBox.setValue(IMAGE_LABEL_PREFIX + "2");
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int index = newIdx.intValue() - 1;
            if (index < 0 || index >= IMAGE_PATHS.length) {
                imageView.setImage(null);
            } else {
                imageView.setImage(new Image(
                        RXImageViewDemo.class.getResource(IMAGE_PATHS[index]).toExternalForm()));
            }
        });

        // ==================== Shape ====================
        ComboBox<String> shapeBox = new ComboBox<>();
        shapeBox.getItems().addAll(SHAPE_NAMES);
        shapeBox.setMaxWidth(Double.MAX_VALUE);
        shapeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            imageView.setClipSvgPath(shapeNameToSvg(newVal));
        });
        shapeBox.getSelectionModel().select("Shield");

        // ==================== Grid ====================
        GridPane grid = new GridPane();
        grid.getStyleClass().add("control-grid");

        ColumnConstraints labelCol = new ColumnConstraints();
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, controlCol);

        int row = 0;
        grid.add(new Label("Image"), 0, row);
        grid.add(imageBox, 1, row);

        row++;
        grid.add(new Label("Shape"), 0, row);
        grid.add(shapeBox, 1, row);

        // ==================== Tips & Layout ====================
        Label tips = new Label("Cover-fit: image fills the available area, preserving aspect ratio and cropping overflow.");
        tips.setWrapText(true);
        tips.getStyleClass().add("tips-label");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox panel = new VBox(title, new Separator(), grid, spacer, new Separator(), tips);
        panel.getStyleClass().add("control-pane");
        return panel;
    }

    private String shapeNameToSvg(String name) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "Circle":
                return RXImageView.SHAPE_CIRCLE;
            case "Hexagon":
                return RXImageView.SHAPE_HEXAGON;
            case "Diamond":
                return RXImageView.SHAPE_DIAMOND;
            case "Star":
                return RXImageView.SHAPE_STAR;
            case "Rounded Rect":
                return RXImageView.SHAPE_ROUNDED_RECT;
            case "Heart":
                return RXImageView.SHAPE_HEART;
            case "Cross":
                return RXImageView.SHAPE_CROSS;
            case "Octagon":
                return RXImageView.SHAPE_OCTAGON;
            case "Shield":
                return RXImageView.SHAPE_SHIELD;
            case "Drop":
                return RXImageView.SHAPE_DROP;
            default:
                return null;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
