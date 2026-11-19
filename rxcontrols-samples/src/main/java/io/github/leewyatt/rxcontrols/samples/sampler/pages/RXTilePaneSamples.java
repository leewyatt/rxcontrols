package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.Choice;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.menu;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;

/**
 * The RXTilePane page: an app launcher in a resizable frame with menus for the tile size,
 * a column cap and the gap, product tiles with each itemsJustify mode and a filtered
 * project grid whose tiles glide.
 */
public final class RXTilePaneSamples {

    private static final String[][] APPS = {
        {"Mail", "mail-icon", "sky"}, {"Calendar", "calendar-icon", "rose"}, {"Photos", "image-icon", "amber"},
        {"Music", "music-icon", "pink"}, {"Maps", "map-icon", "lime"}, {"Notes", "note-icon", "orange"},
        {"Files", "folder-icon", "indigo"}, {"Weather", "sun-icon", "teal"}, {"Settings", "settings-icon", "violet"},
        {"Camera", "camera-icon", "rose"}, {"Clock", "clock-icon", "slate"}, {"Chat", "chat-icon", "sky"}};

    private static final double TILE_SIZE = 92.0;

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
                                "Drag the frame edge: the column count follows the width and the tiles glide to their new "
                                        + "places. Tile size Computed sizes every tile by the largest app; Max columns caps the "
                                        + "count however wide the frame is.",
                                launcher()),
                        SampleCard.canvas("Spare width",
                                "itemsJustify decides where a row's spare width goes; STRETCH grows the tiles up to maxTileWidth.",
                                products()),
                        SampleCard.canvas("Filtered projects",
                                "Pick a category: hidden tiles leave the layout and the rest glide into the free slots.",
                                projects())),
                LayoutKit.STYLESHEET);
    }

    // ==================== App launcher ====================

    private static Node launcher() {
        RXTilePane tiles = new RXTilePane();
        tiles.setPrefTileWidth(TILE_SIZE);
        tiles.setPrefTileHeight(TILE_SIZE);
        tiles.setHgap(8.0);
        tiles.setVgap(8.0);
        int[] next = {0};
        for (; next[0] < APPS.length; next[0]++) {
            tiles.getChildren().add(app(APPS[next[0]]));
        }
        Label columns = text("", "frame-size");
        columns.textProperty().bind(Bindings.createStringBinding(
                () -> tiles.getActualColumnCount() + " columns", tiles.actualColumnCountProperty()));
        columns.setMinWidth(Region.USE_PREF_SIZE);

        // Computed sizes every tile by the largest app instead of a fixed square.
        RXMenuButton size = menu("Tile size", 1, value -> {
                    tiles.setPrefTileWidth(value);
                    tiles.setPrefTileHeight(value);
                },
                new Choice<>("72", 72.0), new Choice<>("92", TILE_SIZE), new Choice<>("120", 120.0),
                new Choice<>("Computed", Region.USE_COMPUTED_SIZE));
        RXMenuButton cap = menu("Max columns", 0, tiles::setMaxColumns,
                new Choice<>("Any", 0), new Choice<>("3", 3), new Choice<>("4", 4), new Choice<>("6", 6));
        RXMenuButton gap = menu("Gap", 1, value -> {
                    tiles.setHgap(value);
                    tiles.setVgap(value);
                },
                new Choice<>("0", 0.0), new Choice<>("8", 8.0), new Choice<>("16", 16.0));
        RXCheckBox animate = new RXCheckBox("Animate");
        animate.selectedProperty().bindBidirectional(tiles.animatedProperty());

        RXButton add = plain("Add app");
        add.setOnAction(event -> tiles.getChildren().add(app(APPS[next[0]++ % APPS.length])));
        RXButton remove = plain("Remove last");
        remove.setOnAction(event -> tiles.getChildren().remove(tiles.getChildren().size() - 1));
        remove.disableProperty().bind(Bindings.isEmpty(tiles.getChildren()));
        FocusHandOff.onDisable(remove, add);
        for (Region region : List.of(add, remove)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }

        // The layout choices wrap on a narrow card, being a flow pane themselves.
        RXFlowPane choices = new RXFlowPane(8.0, 8.0, size, cap, gap, animate);
        choices.setAnimated(false);
        HBox.setHgrow(choices, Priority.ALWAYS);
        HBox options = new HBox(8.0, choices, add, remove);
        options.setAlignment(Pos.TOP_LEFT);

        VBox frame = widthFrame(tiles, 220.0, columns);
        frame.getChildren().add(1, options);
        return frame;
    }

    private static Node app(String[] app) {
        return app(app[0], app[1], app[2]);
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
}
