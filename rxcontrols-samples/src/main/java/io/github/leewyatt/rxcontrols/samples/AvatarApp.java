package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
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

    private RXAvatar avatar;

    @Override
    public void start(Stage primaryStage) {
        Image image = new Image(AvatarApp.class.getResource("/scenery/2.png").toExternalForm());
        avatar = new RXAvatar(image);

        BorderPane root = new BorderPane();
        root.setCenter(createContentPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 700, 380);
        scene.getStylesheets().add(getClass().getResource("avatar-app.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXAvatar Demo");
        primaryStage.show();
    }

    private Node createContentPane() {
        StackPane pane = new StackPane(avatar);
        pane.getStyleClass().add("content-pane");
        return pane;
    }

    private Node createControlPane() {
        Label title = new Label("RXAvatar");
        title.getStyleClass().add("title-label");

        // ==================== Image ====================
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

        // ==================== Text ====================
        TextField textField = new TextField();
        textField.setPromptText("e.g. LW");
        textField.setMaxWidth(Double.MAX_VALUE);
        avatar.textProperty().bind(textField.textProperty());

        // ==================== Shape Type ====================
        ComboBox<ShapeType> shapeTypeBox = new ComboBox<>();
        shapeTypeBox.getItems().addAll(ShapeType.values());
        shapeTypeBox.valueProperty().bindBidirectional(avatar.shapeTypeProperty());
        shapeTypeBox.setMaxWidth(Double.MAX_VALUE);

        // ==================== Size ====================
        Slider sizeSlider = new Slider(20, 200, 100);
        sizeSlider.setMaxWidth(Double.MAX_VALUE);
        avatar.prefWidthProperty().bind(sizeSlider.valueProperty());
        avatar.prefHeightProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = createValueLabel(sizeSlider);

        // ==================== Arc Width / Height ====================
        Label arcWidthLabel = new Label("Arc Width");
        Slider arcWidthSlider = new Slider(0, 100, 10);
        arcWidthSlider.setMaxWidth(Double.MAX_VALUE);
        arcWidthSlider.valueProperty().bindBidirectional(avatar.arcWidthProperty());
        Label arcWidthValue = createValueLabel(arcWidthSlider);

        Label arcHeightLabel = new Label("Arc Height");
        Slider arcHeightSlider = new Slider(0, 100, 10);
        arcHeightSlider.setMaxWidth(Double.MAX_VALUE);
        arcHeightSlider.valueProperty().bindBidirectional(avatar.arcHeightProperty());
        Label arcHeightValue = createValueLabel(arcHeightSlider);

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

        // ==================== Grid ====================
        GridPane grid = new GridPane();
        grid.getStyleClass().add("control-grid");

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
        grid.addRow(row, new Label("Width / Height"), sizeSlider, sizeValue);

        row++;
        grid.addRow(row, arcWidthLabel, arcWidthSlider, arcWidthValue);

        row++;
        grid.addRow(row, arcHeightLabel, arcHeightSlider, arcHeightValue);

        // ==================== Tips & Layout ====================
        Label tips = new Label("Display priority: Image > Text > Default Icon");
        tips.setWrapText(true);
        tips.getStyleClass().add("tips-label");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox panel = new VBox(title, new Separator(), grid, spacer, new Separator(), tips);
        panel.getStyleClass().add("control-pane");
        return panel;
    }

    private Label createValueLabel(Slider slider) {
        Label label = new Label();
        label.textProperty().bind(Bindings.format("%.0f", slider.valueProperty()));
        label.getStyleClass().add("value-label");
        return label;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
