package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Sample application demonstrating {@link RXTreeShowingProperty}.
 *
 * <p>A {@link RotateTransition} runs on a node hosted inside a {@link TabPane}.
 * When the user switches to another tab, the node leaves the visible chain,
 * {@code RXTreeShowingProperty} flips to {@code false}, and the animation
 * auto-pauses; switching back resumes it. This is the classic use case: avoid
 * spending CPU/render work on visuals that are not currently on screen.</p>
 */
public class RXTreeShowingPropertyDemo extends Application {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void start(Stage primaryStage) {
        Rectangle shape = new Rectangle(120, 120, Color.web("#4a90e2"));
        shape.setArcWidth(24);
        shape.setArcHeight(24);

        RotateTransition rotate = new RotateTransition(Duration.seconds(2), shape);
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);

        Label activeHint = new Label("This square rotates only while this tab is visible.");
        activeHint.setStyle("-fx-text-fill: #555;");

        VBox activePane = new VBox(20, shape, activeHint);
        activePane.setAlignment(Pos.CENTER);
        activePane.setPadding(new Insets(30));

        Label idleHint = new Label(
                "The rotation animation is paused while this tab is active.\n"
                        + "No CPU or render work is wasted on the off-screen node.\n"
                        + "Switch back to \"Animated\" to see it resume.");
        idleHint.setStyle("-fx-text-fill: #555; -fx-font-size: 13;");
        idleHint.setWrapText(true);
        StackPane idlePane = new StackPane(idleHint);
        idlePane.setPadding(new Insets(30));

        Tab animatedTab = new Tab("Animated", activePane);
        animatedTab.setClosable(false);
        Tab idleTab = new Tab("Idle", idlePane);
        idleTab.setClosable(false);

        TabPane tabPane = new TabPane(animatedTab, idleTab);

        ReadOnlyBooleanProperty shapeShowing = RXTreeShowingProperty.of(shape);
        shapeShowing.addListener((obs, was, now) -> {
            if (now) {
                rotate.play();
            } else {
                rotate.pause();
            }
            System.out.printf("[%s] treeShowing %s -> %s   animation.status = %s%n",
                    LocalTime.now().format(TIME_FORMATTER), was, now, rotate.getStatus());
        });

        Label status = new Label();
        status.textProperty().bind(Bindings.format(
                "shape.treeShowing = %s   →   animation %s",
                shapeShowing,
                Bindings.when(shapeShowing).then("playing").otherwise("paused")));
        status.setStyle("-fx-font-family: 'Menlo'; -fx-font-size: 12; -fx-text-fill: #333;");

        VBox root = new VBox(10, tabPane, status);
        root.setPadding(new Insets(12));
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        primaryStage.setScene(new Scene(root, 440, 340));
        primaryStage.setTitle("RXTreeShowingProperty Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
