package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXDrawerPane}: a right-sliding detail panel over a main
 * page. A button toggles the drawer; a side picker shows the slide working from
 * all four edges; a live label echoes the {@code showing} state and the last
 * {@link RXDrawerEvent}; an "unsaved changes" check box vetoes the close.
 *
 * <p>Everything here uses only the public API — {@code open}/{@code close}/
 * {@code toggle}, the bindable {@code showing} property, and the
 * {@code CLOSE_REQUEST} veto — to keep the demo an honest illustration of the
 * control's surface.</p>
 */
public class RXDrawerPaneDemo extends Application {

    private final StringProperty lastEvent = new SimpleStringProperty("—");

    @Override
    public void start(Stage primaryStage) {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.RIGHT);
        drawer.setContent(createMainPage(drawer));
        drawer.setDrawerContent(createDrawerPanel(drawer));
        drawer.addEventHandler(RXDrawerEvent.ANY, e -> lastEvent.set(
                e.getEventType().getName() + (e.getReason() == null ? "" : " (" + e.getReason() + ")")));

        Scene scene = new Scene(drawer, 900.0, 600.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-drawer-pane-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXDrawerPane Demo");
        primaryStage.show();
    }

    private Region createMainPage(RXDrawerPane drawer) {
        Label title = new Label("Project dashboard");
        title.getStyleClass().add("page-title");

        Label hint = new Label("Open the side panel to review the details.");
        hint.getStyleClass().add("page-hint");

        Button toggle = new Button("Toggle panel");
        toggle.getStyleClass().add("primary-button");
        toggle.setOnAction(e -> drawer.toggle());

        ComboBox<Side> sidePicker = new ComboBox<>();
        sidePicker.getItems().addAll(Side.TOP, Side.RIGHT, Side.BOTTOM, Side.LEFT);
        sidePicker.getSelectionModel().select(drawer.getSide());
        sidePicker.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                drawer.setSide(value);
            }
        });

        Label showing = new Label();
        showing.getStyleClass().add("state-label");
        showing.textProperty().bind(Bindings.createStringBinding(
                () -> "showing: " + drawer.isShowing(), drawer.showingProperty()));

        Label event = new Label();
        event.getStyleClass().add("state-label");
        event.textProperty().bind(Bindings.createStringBinding(
                () -> "event: " + lastEvent.get(), lastEvent));

        HBox controls = new HBox(12.0, toggle, new Label("Side:"), sidePicker, showing);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(16.0, title, hint, controls, event);
        page.getStyleClass().add("main-page");
        page.setPadding(new Insets(32.0));
        return page;
    }

    private Region createDrawerPanel(RXDrawerPane drawer) {
        Label heading = new Label("Task details");
        heading.getStyleClass().add("drawer-title");

        Label body = new Label(
                "This panel slides over the page as a pure translate animation. "
                        + "Layout always parks it at the open position, so it reopens "
                        + "from exactly the right place — even mid-slide.");
        body.getStyleClass().add("drawer-body");
        body.setWrapText(true);

        CheckBox dirty = new CheckBox("Unsaved changes (veto close)");
        // Any close path — Close button, toggle, ESC later — fires CLOSE_REQUEST first;
        // consuming it keeps the drawer open, the library's key improvement over the web.
        drawer.setOnCloseRequest(e -> {
            if (dirty.isSelected()) {
                e.consume();
            }
        });

        Button close = new Button("Close");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> drawer.close());

        VBox panel = new VBox(16.0, heading, body, dirty, close);
        panel.setFillWidth(true);
        return panel;
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
