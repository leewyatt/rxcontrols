package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;

/**
 * The RXTilePane page: an app launcher in a resizable frame, product tiles with each
 * itemsJustify mode, a filtered project grid whose tiles glide, badges sized by the
 * largest child and a color palette capped at a column count.
 */
public final class RXTilePaneSamples {

    private static final String[][] APPS = {
        {"Mail", "mail-icon", "sky"}, {"Calendar", "calendar-icon", "rose"}, {"Photos", "image-icon", "amber"},
        {"Music", "music-icon", "pink"}, {"Maps", "map-icon", "lime"}, {"Notes", "note-icon", "orange"},
        {"Files", "folder-icon", "indigo"}, {"Weather", "sun-icon", "teal"}, {"Settings", "settings-icon", "violet"},
        {"Camera", "camera-icon", "rose"}, {"Clock", "clock-icon", "slate"}, {"Chat", "chat-icon", "sky"}};

    private RXTilePaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A tile layout that puts its children in same-size tiles and fits as many columns as the width allows.",
                List.of("Launchers, galleries, swatches and card grids where every item gets the same slot.",
                        "For many data items use the virtualized RXTileView; for items of their own size use RXFlowPane."),
                List.of(
                        SampleCard.canvas("App launcher",
                                "Drag the frame edge: the column count follows the width and the tiles glide to their new places.",
                                launcher()),
                        SampleCard.canvas("Spare width",
                                "itemsJustify decides where a row's spare width goes; STRETCH grows the tiles up to maxTileWidth.",
                                products()),
                        SampleCard.canvas("Filtered projects",
                                "Pick a category: hidden tiles leave the layout and the rest glide into the free slots.",
                                projects()),
                        SampleCard.large("Tiles sized by content",
                                "With prefTileWidth left computed, every tile is as wide as the longest badge.",
                                badges()),
                        SampleCard.large("Column cap",
                                "maxColumns 5 keeps the swatches in rows of five even when there is room for more.",
                                swatches())),
                LayoutKit.STYLESHEET);
    }

    // ==================== App launcher ====================

    private static Node launcher() {
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(92.0);
        tiles.setPrefTileHeight(92.0);
        tiles.setHgap(8.0);
        tiles.setVgap(8.0);
        for (String[] app : APPS) {
            tiles.getChildren().add(app(app[0], app[1], app[2]));
        }
        Label columns = text("", "frame-size");
        tiles.actualColumnCountProperty().addListener((obs, old, count) ->
                columns.setText(count + " columns"));
        columns.setMinWidth(Region.USE_PREF_SIZE);
        return widthFrame(tiles, 220.0, columns);
    }

    private static Node app(String name, String iconClass, String color) {
        StackPane badge = new StackPane(icon(iconClass, "on-color"));
        badge.getStyleClass().addAll("app-icon", color);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox app = new VBox(6.0, badge, text(name, "copy"));
        app.setAlignment(Pos.CENTER);
        app.getStyleClass().add("app-tile");
        return app;
    }

    // ==================== Spare width ====================

    private static Node products() {
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(150.0);
        tiles.setPrefTileHeight(120.0);
        tiles.setMaxTileWidth(210.0);
        String[][] items = {
            {"Desk lamp", "$39", "amber"}, {"Wool throw", "$64", "rose"}, {"Plant pot", "$18", "lime"},
            {"Wall clock", "$45", "sky"}, {"Tea set", "$52", "teal"}};
        for (String[] item : items) {
            tiles.getChildren().add(product(item[0], item[1], item[2]));
        }
        RXSegmentedControl<ItemsJustify> picker = picker(tiles::setItemsJustify,
                ItemsJustify.START, ItemsJustify.CENTER, ItemsJustify.SPACE_EVENLY, ItemsJustify.STRETCH);
        VBox box = new VBox(14.0, picker, tiles);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private static Node product(String name, String price, String color) {
        Region swatch = new Region();
        swatch.getStyleClass().addAll("product-photo", color);
        swatch.setPrefHeight(60.0);
        HBox meta = new HBox(text(name, "copy"), LayoutKit.spacer(), text(price, "copy-strong"));
        VBox card = new VBox(8.0, swatch, meta);
        card.getStyleClass().add("product");
        return card;
    }

    // ==================== Filtered projects ====================

    private static Node projects() {
        String[][] items = {
            {"Brand refresh", "Design", "pink"}, {"Payments API", "Code", "indigo"},
            {"Spring album", "Music", "amber"}, {"Icon set", "Design", "rose"},
            {"Mobile app", "Code", "sky"}, {"Podcast intro", "Music", "orange"},
            {"Landing page", "Design", "violet"}, {"Data import", "Code", "teal"},
            {"Live session", "Music", "lime"}};
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(150.0);
        tiles.setPrefTileHeight(70.0);
        tiles.setMaxTileWidth(220.0);
        tiles.setItemsJustify(ItemsJustify.STRETCH);
        for (String[] item : items) {
            Region stripe = new Region();
            stripe.getStyleClass().addAll("project-stripe", item[2]);
            stripe.setPrefWidth(4.0);
            VBox copy = new VBox(2.0, text(item[0], "copy-strong"), text(item[1], "muted"));
            copy.setAlignment(Pos.CENTER_LEFT);
            HBox card = new HBox(10.0, stripe, copy);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("project");
            card.setUserData(item[1]);
            tiles.getChildren().add(card);
        }
        RXSegmentedControl<String> picker = picker(category -> {
            for (Node card : tiles.getChildren()) {
                boolean shown = "All".equals(category) || category.equals(card.getUserData());
                card.setVisible(shown);
                card.setManaged(shown);
            }
        }, "All", "Design", "Code", "Music");
        VBox box = new VBox(14.0, picker, tiles);
        box.setAlignment(Pos.TOP_CENTER);
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Sized by content ====================

    private static Node badges() {
        RXTilePane tiles = new RXTilePane();
        tiles.setHgap(6.0);
        tiles.setVgap(6.0);
        String[][] skills = {{"Java", "orange"}, {"CSS", "sky"}, {"Scene Builder", "violet"},
            {"Gradle", "teal"}, {"SQL", "amber"}, {"Git", "rose"}};
        for (String[] skill : skills) {
            Label badge = text(skill[0], "chip", "big", skill[1]);
            badge.setMaxWidth(Double.MAX_VALUE);
            badge.setAlignment(Pos.CENTER);
            tiles.getChildren().add(badge);
        }
        tiles.setPrefWidth(340.0);
        tiles.setMaxWidth(Region.USE_PREF_SIZE);
        return tiles;
    }

    // ==================== Column cap ====================

    private static Node swatches() {
        String[] colors = {"rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet", "pink", "slate"};
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(48.0);
        tiles.setPrefTileHeight(48.0);
        tiles.setMaxColumns(5);
        for (String color : colors) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("swatch", color);
            tiles.getChildren().add(swatch);
        }
        tiles.setPrefWidth(380.0);
        tiles.setMaxWidth(Region.USE_PREF_SIZE);
        tiles.getStyleClass().add("grid-area");
        return tiles;
    }
}
