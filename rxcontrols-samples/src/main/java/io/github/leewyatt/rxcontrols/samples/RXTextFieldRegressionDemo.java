package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.skins.RXTextFieldSkin;
import javafx.application.Application;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Manual regression checks for RXTextField / RXFieldBaseSkin layout behavior.
 */
public class RXTextFieldRegressionDemo extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(14);
        root.setStyle("-fx-padding: 20; -fx-background-color: #f4f4f4;");

        root.getChildren().addAll(
                new Label("1. GridPane width constraint: actual width should stay <= 100"),
                createGridPaneCheck(),
                new Label("2. Long text clipping: text must stop before the red right wrapper"),
                createLongTextCheck(),
                new Label("3. RTL: confirm visual behavior with right-to-left orientation"),
                createRtlCheck(),
                new Label("4. Mouse hit: hover blue icon, then click text; caret should land near click"),
                createMouseHitCheck(),
                new Label("5. Prompt fill: prompt text should be green"),
                createPromptFillCheck(),
                new Label("6. Node detach: click buttons; neither should throw IllegalArgumentException"),
                createDetachCheck()
        );

        Scene scene = new Scene(root, 760, 680);
        scene.getStylesheets().add(getClass().getResource("rx-text-field-regression-demo.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("RXTextField regression checks");
        stage.show();
    }

    private static GridPane createGridPaneCheck() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);

        ColumnConstraints labelColumn = new ColumnConstraints(170);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setPrefWidth(100);
        fieldColumn.setMaxWidth(100);
        fieldColumn.setHgrow(Priority.NEVER);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        RXTextField field = baseField("GridPane 100px column");
        field.setLeft(iconNode());
        field.setRight(new Button("R"));
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);

        Label width = new Label();
        width.getStyleClass().add("status");
        field.widthProperty().addListener((obs, oldValue, newValue) ->
                width.setText("actual width = " + Math.round(newValue.doubleValue()) + " px"));
        width.setText("actual width = 0 px");

        grid.add(new Label("column pref/max = 100"), 0, 0);
        grid.add(field, 1, 0);
        grid.add(width, 2, 0);
        return grid;
    }

    private static RXTextField createLongTextCheck() {
        RXTextField field = baseField("a".repeat(220));
        field.setPrefWidth(520);
        field.setLeft(iconNode());
        Button right = new Button("END");
        right.setFocusTraversable(false);
        right.getStyleClass().add("right-marker");
        field.setRight(right);
        return field;
    }

    private static RXTextField createRtlCheck() {
        RXTextField field = baseField("RTL abc 123");
        field.setPrefWidth(520);
        field.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        field.setLeft(new Label("LEFT"));
        field.setRight(new Label("RIGHT"));
        return field;
    }

    private static VBox createMouseHitCheck() {
        RXTextField field = baseField("0123456789 abcdefghijklmnopqrstuvwxyz");
        field.setPrefWidth(520);
        field.setLeft(iconNode());
        field.getStyleClass().add("hit-check");

        Label status = new Label("click inside the text area");
        status.getStyleClass().add("status");
        field.addEventHandler(MouseEvent.MOUSE_CLICKED, event ->
                status.setText("caret position = " + field.getCaretPosition()));

        return new VBox(4, field, status);
    }

    private static RXTextField createPromptFillCheck() {
        RXTextField field = baseField("");
        field.setPrefWidth(520);
        field.setPromptText("This prompt should be green");
        field.setLeft(iconNode());
        field.setRight(new Button("R"));
        field.getStyleClass().add("prompt-check");
        return field;
    }

    private static HBox createDetachCheck() {
        RXTextField field = baseField("detach check");
        field.setPrefWidth(360);
        Button reusable = new Button("N");
        reusable.setFocusTraversable(false);
        field.setLeft(reusable);

        Button toggle = new Button("setLeft(null) -> setLeft(same node)");
        toggle.setOnAction(event -> {
            field.setLeft(null);
            field.setLeft(reusable);
        });

        Button clear = new Button("setLeft(null)");
        clear.setOnAction(event -> field.setLeft(null));

        Button restore = new Button("setLeft(restore)");
        restore.setOnAction(event -> field.setLeft(reusable));

        Button move = new Button("move left/right");
        move.setOnAction(event -> {
            if (field.getLeft() == reusable) {
                field.setRight(reusable);
            } else {
                field.setLeft(reusable);
            }
        });

        Button replaceSkin = new Button("setSkin(null) -> new skin");
        replaceSkin.setOnAction(event -> {
            field.setSkin(null);
            field.setSkin(new RXTextFieldSkin(field));
        });

        HBox row = new HBox(8, field, toggle, clear, restore, move, replaceSkin);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static RXTextField baseField(String text) {
        RXTextField field = new RXTextField(text);
        field.getStyleClass().add("regression-field");
        return field;
    }

    private static StackPane iconNode() {
        SVGPath path = new SVGPath();
        path.setContent("M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001c.03.04.062.078.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1.012 1.012 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z");
        path.setFill(Color.WHITE);
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(22);
        icon.getStyleClass().add("icon-node");
        return icon;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
