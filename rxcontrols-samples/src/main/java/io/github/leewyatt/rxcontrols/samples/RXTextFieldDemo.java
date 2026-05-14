package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Sample application demonstrating {@link RXTextField} — a text field with
 * optional left and right slots for user-supplied nodes.
 */
public class RXTextFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        // 1) Plain — no left/right.
        RXTextField plain = new RXTextField();
        plain.setPromptText("Plain RXTextField (no slots)");
        plain.setPrefWidth(360);

        // 2) Search field — left icon only.
        RXTextField search = new RXTextField();
        search.setPromptText("Search...");
        search.setPrefWidth(360);
        search.setLeft(magnifierIcon());

        // 3) Right slot — a single clear button.
        RXTextField clearable = new RXTextField("clear me");
        clearable.setPrefWidth(360);
        Button clear = new Button("✕");
        clear.setFocusTraversable(false);
        clear.setOnAction(e -> clearable.clear());
        clearable.setRight(clear);

        // 4) Both slots — left icon + right HBox with two buttons.
        RXTextField both = new RXTextField();
        both.setPromptText("Both slots");
        both.setPrefWidth(360);
        both.setLeft(magnifierIcon());
        Button clearBtn = new Button("✕");
        Button submitBtn = new Button("↵");
        clearBtn.setFocusTraversable(false);
        submitBtn.setFocusTraversable(false);
        clearBtn.setOnAction(e -> both.clear());
        submitBtn.setOnAction(e -> System.out.println("Submitted: " + both.getText()));
        HBox actions = new HBox(4, clearBtn, submitBtn);
        both.setRight(actions);

        // 5) Reference — JavaFX plain TextField to compare layout.
        TextField reference = new TextField();
        reference.setPromptText("Plain javafx.scene.control.TextField");
        reference.setPrefWidth(360);

        root.getChildren().addAll(
                new Label("Plain"), plain,
                new Label("Left slot only"), search,
                new Label("Right slot only"), clearable,
                new Label("Left + right slot"), both,
                new Label("Reference — JavaFX TextField"), reference
        );

        Scene scene = new Scene(root, 500, 540);
        scene.getStylesheets().add(getClass().getResource("rx-text-field-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXTextField Demo");
        primaryStage.show();
    }

    private static StackPane magnifierIcon() {
        SVGPath path = new SVGPath();
        path.setContent("M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001c.03.04.062.078.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1.012 1.012 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z");
        path.setFill(Color.web("#6c757d"));
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(20);
        return icon;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
