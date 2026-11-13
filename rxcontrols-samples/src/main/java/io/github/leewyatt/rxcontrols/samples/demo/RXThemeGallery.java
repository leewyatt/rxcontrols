package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import io.github.leewyatt.rxcontrols.samples.demo.ThemeGalleryCards.NamedControl;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Theme gallery: a scrollable, alphabetised grid of RxControls, each in a card
 * with a one-line description, with a name filter and a theme switcher to
 * compare the default style under Modena against the AtlantaFX themes (see
 * {@link ShowcaseThemes}). Wide controls follow in their own two-column grid.
 * Pure layout containers (RXBox / RXRow / RXCol / RXMasonryPane / RXFlowPane /
 * RXTilePane) and cells used only inside their views are omitted; the
 * displayed controls are built by {@link ThemeGalleryCards}.
 */
public class RXThemeGallery extends Application {

    private static final double CARD_WIDTH = 300.0;
    private static final double CARD_GAP = 24.0;

    private final List<Card> cards = new ArrayList<>();
    private final Label countLabel = new Label();
    private RXMasonryPane regularGrid;
    private Label wideHeading;

    @Override
    public void start(Stage primaryStage) {
        ScrollPane scroll = new ScrollPane(buildGallery());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("gallery-scroll");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("gallery-root");
        root.setCenter(scroll);

        Scene scene = new Scene(root, 1280, 860);
        scene.getStylesheets().add(getClass().getResource("rx-theme-gallery.css").toExternalForm());
        root.setTop(buildToolbar(scene)); // applies the initial theme

        primaryStage.setScene(scene);
        primaryStage.setTitle("RxControls Theme Gallery");
        primaryStage.show();
    }

    // ==================== Toolbar ====================

    private HBox buildToolbar(Scene scene) {
        Label title = new Label("RxControls Theme Gallery");
        title.getStyleClass().add("gallery-title");
        countLabel.getStyleClass().add("gallery-count");

        RXTextField filter = new RXTextField();
        filter.setPromptText("Filter by name");
        filter.setPrefColumnCount(16);
        filter.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));
        filter.setTrailing(clearButton(filter));
        applyFilter("");

        List<ThemeChoice> choices = ShowcaseThemes.all();
        ComboBox<ThemeChoice> picker = new ComboBox<>();
        picker.getItems().setAll(choices);
        ShowcaseThemes.bindPicker(picker, () -> scene);
        picker.setValue(choices.get(0)); // start on plain Modena

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(12, title, countLabel, spacer, filter, new Label("Theme"), picker);
        toolbar.getStyleClass().add("gallery-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    // Shown only while there is text to clear; it stays laid out, so the
    // field's text area does not shift as it appears.
    private static Button clearButton(RXTextField field) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("glyph", "clear-glyph");
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMouseTransparent(true);

        Button clear = new Button();
        clear.getStyleClass().add("slot-button");
        clear.setGraphic(glyph);
        clear.setFocusTraversable(false);
        clear.visibleProperty().bind(field.textProperty().isNotEmpty());
        clear.setOnAction(event -> field.clear());
        return clear;
    }

    private void applyFilter(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        boolean regularShown = false;
        boolean wideShown = false;
        for (Card card : cards) {
            boolean match = card.name().toLowerCase(Locale.ROOT).contains(query);
            card.node().setVisible(match);
            card.node().setManaged(match);
            if (match) {
                shown++;
                regularShown |= !card.wide();
                wideShown |= card.wide();
            }
        }
        // An empty section would still take its share of the content spacing.
        regularGrid.setVisible(regularShown);
        regularGrid.setManaged(regularShown);
        wideHeading.setVisible(wideShown);
        wideHeading.setManaged(wideShown);
        countLabel.setText(shown == cards.size()
                ? cards.size() + " controls"
                : shown + " of " + cards.size() + " controls");
    }

    // ==================== Gallery ====================

    // Wide cards get their own grid after the regular ones: mixed into one
    // masonry, a two-column card leaves holes in the shorter column.
    private VBox buildGallery() {
        regularGrid = grid(CARD_WIDTH);
        RXMasonryPane wide = grid(2 * CARD_WIDTH + CARD_GAP);
        for (NamedControl control : ThemeGalleryCards.cards()) {
            Node card = card(control);
            cards.add(new Card(control.name(), card, control.wide()));
            (control.wide() ? wide : regularGrid).getChildren().add(card);
        }
        wideHeading = new Label("Larger components");
        wideHeading.getStyleClass().add("gallery-heading");

        VBox content = new VBox(CARD_GAP, regularGrid, wideHeading, wide);
        content.getStyleClass().add("gallery-content");
        return content;
    }

    private static RXMasonryPane grid(double columnWidth) {
        RXMasonryPane grid = new RXMasonryPane();
        grid.setColumnWidth(columnWidth);
        grid.setHgap(CARD_GAP);
        grid.setVgap(CARD_GAP);
        return grid;
    }

    private static Node card(NamedControl control) {
        Label name = new Label(control.name());
        name.getStyleClass().add("card-title");
        Label description = new Label(control.description());
        description.getStyleClass().add("card-description");
        description.setWrapText(true);

        StackPane stage = new StackPane(control.node());
        stage.getStyleClass().add("card-stage");
        stage.setAlignment(Pos.CENTER);
        stage.setMinHeight(100);
        VBox.setVgrow(stage, Priority.ALWAYS);

        VBox card = new VBox(4, name, description, stage);
        VBox.setMargin(stage, new Insets(12, 0, 0, 0));
        card.getStyleClass().add("gallery-card");
        card.setMinHeight(Region.USE_PREF_SIZE);
        return card;
    }

    private record Card(String name, Node node, boolean wide) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
