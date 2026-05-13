package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
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
 * Sample application demonstrating {@link RXAvatar}.
 */
public class AvatarApp extends Application {

    private static final String[] IMAGE_PATHS = {
            "/scenery/1.png", "/scenery/2.png", "/scenery/3.png", "/scenery/4.png"
    };

    @Override
    public void start(Stage primaryStage) {
        Image image = new Image(AvatarApp.class.getResource("/scenery/2.png").toExternalForm());
        RXAvatar avatar = new RXAvatar(image);

        StackPane displayPane = new StackPane(avatar);
        displayPane.setPadding(new Insets(20));
        displayPane.setStyle("-fx-background-color: white;");

        Label title = new Label("RXAvatar");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        ComboBox<ShapeType> shapeTypeBox = new ComboBox<>();
        shapeTypeBox.getItems().addAll(ShapeType.values());
        shapeTypeBox.valueProperty().bindBidirectional(avatar.shapeTypeProperty());
        shapeTypeBox.setMaxWidth(Double.MAX_VALUE);

        Slider sizeSlider = new Slider(20, 200, 100);
        sizeSlider.setMaxWidth(Double.MAX_VALUE);
        avatar.prefWidthProperty().bind(sizeSlider.valueProperty());
        avatar.prefHeightProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = new Label();
        sizeValue.textProperty().bind(Bindings.format("%.0f", sizeSlider.valueProperty()));
        sizeValue.setMinWidth(30);

        Label arcWidthLabel = new Label("Arc Width");
        Slider arcWidthSlider = new Slider(0, 100, 10);
        arcWidthSlider.setMaxWidth(Double.MAX_VALUE);
        arcWidthSlider.valueProperty().bindBidirectional(avatar.arcWidthProperty());
        Label arcWidthValue = new Label();
        arcWidthValue.textProperty().bind(Bindings.format("%.0f", arcWidthSlider.valueProperty()));
        arcWidthValue.setMinWidth(30);

        Label arcHeightLabel = new Label("Arc Height");
        Slider arcHeightSlider = new Slider(0, 100, 10);
        arcHeightSlider.setMaxWidth(Double.MAX_VALUE);
        arcHeightSlider.valueProperty().bindBidirectional(avatar.arcHeightProperty());
        Label arcHeightValue = new Label();
        arcHeightValue.textProperty().bind(Bindings.format("%.0f", arcHeightSlider.valueProperty()));
        arcHeightValue.setMinWidth(30);

        arcWidthLabel.visibleProperty().bind(avatar.shapeTypeProperty().isEqualTo(ShapeType.SQUARE));
        arcWidthSlider.visibleProperty().bind(arcWidthLabel.visibleProperty());
        arcWidthValue.visibleProperty().bind(arcWidthLabel.visibleProperty());
        arcHeightLabel.visibleProperty().bind(arcWidthLabel.visibleProperty());
        arcHeightSlider.visibleProperty().bind(arcWidthLabel.visibleProperty());
        arcHeightValue.visibleProperty().bind(arcWidthLabel.visibleProperty());

        arcWidthLabel.managedProperty().bind(arcWidthLabel.visibleProperty());
        arcWidthSlider.managedProperty().bind(arcWidthSlider.visibleProperty());
        arcWidthValue.managedProperty().bind(arcWidthValue.visibleProperty());
        arcHeightLabel.managedProperty().bind(arcHeightLabel.visibleProperty());
        arcHeightSlider.managedProperty().bind(arcHeightSlider.visibleProperty());
        arcHeightValue.managedProperty().bind(arcHeightValue.visibleProperty());

        ComboBox<String> imageBox = new ComboBox<>();
        imageBox.getItems().add("None");
        for (int i = 0; i < IMAGE_PATHS.length; i++) {
            imageBox.getItems().add("Image " + (i + 1));
        }
        imageBox.setValue("Image 2");
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("None".equals(newVal)) {
                avatar.setImage(null);
            } else {
                int index = Integer.parseInt(newVal.replace("Image ", "")) - 1;
                avatar.setImage(new Image(AvatarApp.class.getResource(IMAGE_PATHS[index]).toExternalForm()));
            }
        });

        TextField textField = new TextField();
        textField.setPromptText("e.g. LW");
        textField.setMaxWidth(Double.MAX_VALUE);
        avatar.textProperty().bind(textField.textProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);

        ColumnConstraints labelCol = new ColumnConstraints();
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        ColumnConstraints valueCol = new ColumnConstraints();
        grid.getColumnConstraints().addAll(labelCol, controlCol, valueCol);

        int row = 0;
        grid.add(new Label("Image"), 0, row);
        grid.add(imageBox, 1, row, 2, 1);

        row++;
        grid.add(new Label("Text"), 0, row);
        grid.add(textField, 1, row, 2, 1);

        row++;
        grid.add(new Separator(), 0, row, 3, 1);

        row++;
        grid.add(new Label("Shape Type"), 0, row);
        grid.add(shapeTypeBox, 1, row, 2, 1);

        row++;
        grid.add(new Label("Width / Height"), 0, row);
        grid.add(sizeSlider, 1, row);
        grid.add(sizeValue, 2, row);

        row++;
        grid.add(arcWidthLabel, 0, row);
        grid.add(arcWidthSlider, 1, row);
        grid.add(arcWidthValue, 2, row);

        row++;
        grid.add(arcHeightLabel, 0, row);
        grid.add(arcHeightSlider, 1, row);
        grid.add(arcHeightValue, 2, row);

        Label tips = new Label("Display priority: Image > Text > Default Icon");
        tips.setWrapText(true);
        tips.setStyle("-fx-text-fill: #888; -fx-font-size: 12;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox controlPanel = new VBox(15, title, new Separator(), grid, spacer, new Separator(), tips);
        controlPanel.setAlignment(Pos.TOP_LEFT);
        controlPanel.setPadding(new Insets(15));
        controlPanel.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");
        controlPanel.setPrefWidth(300);

        BorderPane root = new BorderPane();
        root.setCenter(displayPane);
        root.setRight(controlPanel);

        primaryStage.setScene(new Scene(root, 700, 380));
        primaryStage.setTitle("RXAvatar Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
