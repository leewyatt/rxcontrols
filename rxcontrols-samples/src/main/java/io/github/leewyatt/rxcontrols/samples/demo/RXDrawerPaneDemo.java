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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
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
 * {@code toggle}, the read-only {@code showing} state, and the
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
                e.getEventType().getName()));

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
        Label title = new Label("Edit task");
        title.getStyleClass().add("drawer-title");

        Button close = new Button("Close");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> drawer.close());

        BorderPane header = new BorderPane();
        header.getStyleClass().add("drawer-header");
        header.setLeft(title);
        header.setRight(close);

        Label intro = new Label(
                "The drawerContent is a regular BorderPane. Header, footer, scrolling, "
                        + "and close actions are owned by the application layout.");
        intro.getStyleClass().add("drawer-body");
        intro.setWrapText(true);

        TextField name = new TextField();
        name.setPromptText("Task name");
        TextArea notes = new TextArea();
        notes.setPromptText("Notes");
        notes.setPrefRowCount(4);

        CheckBox dirty = new CheckBox("Unsaved changes (veto close)");
        // Every close path - custom close button, Cancel, toggle, ESC later - fires
        // CLOSE_REQUEST first; consuming it keeps the drawer open.
        drawer.setOnCloseRequest(e -> {
            if (dirty.isSelected()) {
                e.consume();
            }
        });

        VBox form = new VBox(12.0, intro, name, notes, dirty);
        form.setFillWidth(true);
        form.setPadding(new Insets(20.0));

        ScrollPane scroll = new ScrollPane(form);
        scroll.getStyleClass().add("drawer-scroll");
        scroll.setFitToWidth(true);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> drawer.close());
        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> dirty.setSelected(false));
        HBox footer = new HBox(8.0, cancel, save);
        footer.getStyleClass().add("drawer-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("drawer-panel");
        panel.setTop(header);
        panel.setCenter(scroll);
        panel.setBottom(footer);
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
