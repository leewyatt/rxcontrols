package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBackdrop;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXBackdrop}: a simple preview surface with a modal
 * card shown above a dimming layer.
 *
 * <p>For the full property explorer see the RXBackdrop showcase.</p>
 */
public class RXBackdropDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        RXBackdrop backdrop = new RXBackdrop();
        backdrop.getStyleClass().add("app-backdrop");

        Region modal = createModal(backdrop);
        modal.visibleProperty().bind(backdrop.showingProperty());
        modal.managedProperty().bind(backdrop.showingProperty());

        StackPane root = new StackPane(createWorkspace(backdrop), backdrop, modal);
        root.getStyleClass().add("rx-backdrop-demo");
        StackPane.setMargin(modal, new Insets(24.0));

        backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> backdrop.hide());

        Scene scene = new Scene(root, 900.0, 560.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-backdrop-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXBackdrop Demo");
        primaryStage.show();
    }

    private Region createWorkspace(RXBackdrop backdrop) {
        Label title = label("Backdrop demo", "dashboard-title");
        Label subtitle = label("Show the backdrop with a fade or snap it on instantly.",
                "dashboard-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(360.0);

        Button show = new Button("Show");
        show.getStyleClass().add("primary-button");
        show.setOnAction(event -> backdrop.show());

        Button showWithoutFade = new Button("Show without fade");
        showWithoutFade.getStyleClass().add("secondary-button");
        showWithoutFade.setOnAction(event -> backdrop.show(false));

        HBox actions = new HBox(10.0, show, showWithoutFade);
        actions.setAlignment(Pos.CENTER);

        VBox dashboard = new VBox(16.0, title, subtitle, actions);
        dashboard.getStyleClass().add("dashboard");
        dashboard.setAlignment(Pos.CENTER);
        return dashboard;
    }

    private Region createModal(RXBackdrop backdrop) {
        Label title = label("Backdrop active", "modal-title");
        Label copy = label("This card stays above the backdrop until one of the hide commands runs.",
                "modal-copy");
        copy.setWrapText(true);

        Button hide = new Button("Hide");
        hide.getStyleClass().add("primary-button");
        hide.setOnAction(event -> backdrop.hide());

        Button hideWithoutFade = new Button("Hide without fade");
        hideWithoutFade.getStyleClass().add("secondary-button");
        hideWithoutFade.setOnAction(event -> backdrop.hide(false));

        HBox actions = new HBox(10.0, hide, hideWithoutFade);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(12.0, title, copy, actions);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(340.0);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        return card;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
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
