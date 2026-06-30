package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import io.github.leewyatt.rxcontrols.samples.demo.ThemeGalleryCards.NamedControl;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Theme gallery: a scrollable, alphabetised grid of every color-relevant RxControl
 * (each in a labelled card) with a theme switcher to compare the built-in light/dark
 * looks against the AtlantaFX themes (see {@link ShowcaseThemes}). Pure layout
 * containers (RXBox / RXRow / RXCol / RXMasonryPane / RXFlowPane) are omitted; every other control
 * is built by {@link ThemeGalleryCards}.
 */
public class RXThemeGallery extends Application {

    private static final double CARD_WIDTH = 360.0;

    @Override
    public void start(Stage primaryStage) {
        ScrollPane scroll = new ScrollPane(buildGallery());
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setCenter(scroll);

        Scene scene = new Scene(root, 1100, 820);
        root.setTop(buildToolbar(scene)); // applies the initial theme

        primaryStage.setScene(scene);
        primaryStage.setTitle("RxControls Theme Gallery");
        primaryStage.show();
    }

    private HBox buildToolbar(Scene scene) {
        List<ThemeChoice> choices = ShowcaseThemes.all();
        ComboBox<ThemeChoice> picker = new ComboBox<>();
        picker.getItems().setAll(choices);
        picker.valueProperty().addListener((obs, old, choice) -> {
            if (choice != null) {
                choice.apply().accept(scene);
            }
        });
        picker.setValue(choices.get(0)); // start on RxControls light

        HBox toolbar = new HBox(12, new Label("Theme:"), picker);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        return toolbar;
    }

    private RXMasonryPane buildGallery() {
        final double gap = 32.0;
        RXMasonryPane grid = new RXMasonryPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(gap);
        grid.setVgap(gap);
        grid.setColumnWidth(CARD_WIDTH);
        grid.setFillWidth(false);
        System.out.println(grid.getAnimationDuration().toMillis());
        grid.animationDurationProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(newValue.toMillis());
        });
        for (NamedControl control : ThemeGalleryCards.cards()) {
            grid.getChildren().add(card(control));
        }
        return grid;
    }

    private static Node card(NamedControl control) {
        Label name = new Label(control.name());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        StackPane stage = new StackPane(control.node());
        stage.setAlignment(Pos.CENTER);
        stage.setMinHeight(130);
        VBox.setVgrow(stage, Priority.ALWAYS);

        VBox card = new VBox(10, name, new Separator(), stage);
        card.setPadding(new Insets(16));
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMinHeight(Region.USE_PREF_SIZE);
        // Theme-neutral chrome: a translucent grey border reads on light and dark
        // surfaces alike, with a transparent fill so the themed background shows.
        card.setStyle("-fx-border-color: rgba(128, 128, 128, 0.35);"
                + " -fx-border-radius: 10; -fx-border-width: 1; -fx-background-radius: 10;");
        return card;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
