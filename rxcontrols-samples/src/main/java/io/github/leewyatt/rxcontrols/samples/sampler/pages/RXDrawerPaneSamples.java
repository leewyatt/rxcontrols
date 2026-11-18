package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDrawerPane.DrawerMode;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.pill;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;

/**
 * The RXDrawerPane page: order details sliding in from the right over a dimmed list,
 * a navigation menu from the left, filters that push the product grid aside, and a
 * share sheet rising from the bottom.
 */
public final class RXDrawerPaneSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 300.0;

    private static final String[][] ORDERS = {
            {"#1042", "Desk lamp", "$39.00", "Shipped", "sky"},
            {"#1041", "Wool throw", "$54.00", "Delivered", "lime"},
            {"#1040", "Tea set", "$46.00", "Processing", "amber"},
            {"#1039", "Wall clock", "$27.00", "Delivered", "lime"},
            {"#1038", "Plant pot", "$18.00", "Refunded", "rose"}};

    private RXDrawerPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A panel that slides in from an edge of an area, over its content or pushing it aside.",
                List.of("Details, menus and filters that are needed now and then, without leaving the current screen.",
                        "For a menu that is always visible use RXSidebar; for a question that needs an answer use RXDialog."),
                List.of(
                        SampleCard.large("Order details",
                                "side RIGHT (the default) with a backdrop: a click on the dimmed list or Esc "
                                        + "closes the drawer.",
                                orders()),
                        SampleCard.large("Navigation menu",
                                "side LEFT; picking a page sets the title and calls close().",
                                menu()),
                        SampleCard.large("Filters that push",
                                "drawerMode PUSH moves the grid aside instead of covering it, without a backdrop, "
                                        + "so the grid stays usable.",
                                filters()),
                        SampleCard.large("Share sheet",
                                "side BOTTOM with prefDrawerHeight makes a sheet that rises from the bottom edge.",
                                share())),
                NavKit.STYLESHEET);
    }

    // ==================== Order details ====================

    private static Node orders() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setPrefDrawerWidth(230.0);

        Label number = text("", "page-title");
        Label product = text("", "copy");
        Label total = text("", "headline");
        StackPane status = new StackPane();
        status.setAlignment(Pos.CENTER_LEFT);
        StackPane close = iconButton("close-icon");
        close.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> drawer.close());
        HBox head = new HBox(number, spacer(), close);
        head.setAlignment(Pos.CENTER_LEFT);
        RXButton track = solid("Track parcel", "sky");
        track.setMaxWidth(Double.MAX_VALUE);
        VBox details = new VBox(10.0, head, status, text("Item", "muted"), product,
                text("Total", "muted"), total, spacer(), track);
        details.setPadding(new Insets(14.0, 16.0, 16.0, 16.0));
        drawer.setDrawerContent(details);

        VBox list = new VBox(2.0);
        list.setPadding(new Insets(10.0, 12.0, 10.0, 12.0));
        list.getChildren().add(text("Recent orders", "headline", "list-title"));
        for (String[] order : ORDERS) {
            HBox row = new HBox(10.0, text(order[0], "muted"), text(order[1], "copy"), spacer(),
                    pill(order[3], order[4]), text(order[2], "copy"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("list-row", "clickable");
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                number.setText("Order " + order[0]);
                product.setText(order[1]);
                total.setText(order[2]);
                status.getChildren().setAll(pill(order[3], order[4]));
                drawer.open();
            });
            list.getChildren().add(row);
        }
        drawer.setContent(list);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, drawer);
    }

    // ==================== Navigation menu ====================

    private static Node menu() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.LEFT);
        drawer.setPrefDrawerWidth(200.0);

        Label title = text("Home", "page-title");
        StackPane burger = iconButton("menu-icon");
        burger.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> drawer.open());
        HBox bar = new HBox(10.0, burger, title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("frame-bar");
        VBox body = new VBox(8.0, text("Tap the menu button to switch pages.", "muted"),
                placeholder(260.0), placeholder(200.0), placeholder(230.0));
        body.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        BorderPane page = new BorderPane(body);
        page.setTop(bar);
        drawer.setContent(page);

        VBox items = new VBox(2.0);
        items.setPadding(new Insets(12.0, 8.0, 12.0, 8.0));
        Label brand = text("Northwind", "headline", "list-title");
        items.getChildren().add(brand);
        String[][] pages = {{"Home", "home-icon"}, {"Inbox", "inbox-icon"}, {"Calendar", "calendar-icon"},
                {"Files", "folder-icon"}, {"Settings", "settings-icon"}};
        for (String[] entry : pages) {
            HBox row = new HBox(12.0, icon(entry[1]), text(entry[0], "copy"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("menu-row", "clickable");
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                title.setText(entry[0]);
                drawer.close();
            });
            items.getChildren().add(row);
        }
        drawer.setDrawerContent(items);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, drawer);
    }

    // ==================== Filters that push ====================

    private static Node filters() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.LEFT);
        drawer.setDrawerMode(DrawerMode.PUSH);
        drawer.setBackdropVisible(false);
        drawer.setPrefDrawerWidth(150.0);

        VBox panel = new VBox(10.0, text("Filters", "headline"),
                new RXCheckBox("In stock"), new RXCheckBox("On sale"), new RXCheckBox("Free shipping"));
        panel.setPadding(new Insets(14.0));
        drawer.setDrawerContent(panel);

        RXButton toggle = plain("Filters");
        toggle.setGraphic(icon("tune-icon"));
        toggle.setOnAction(event -> drawer.toggle());
        HBox bar = new HBox(10.0, text("24 products", "headline"), spacer(), toggle);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("frame-bar");

        RXFlowPane grid = new RXFlowPane(10.0, 10.0);
        grid.setPadding(new Insets(12.0));
        String[] colors = {"amber", "sky", "pink", "teal", "violet", "orange", "lime", "indigo"};
        for (String color : colors) {
            StackPane tile = new StackPane(icon("photo-icon", "on-color"));
            tile.getStyleClass().addAll("tile", color);
            grid.getChildren().add(tile);
        }
        BorderPane page = new BorderPane(grid);
        page.setTop(bar);
        drawer.setContent(page);
        drawer.open();
        return frame(FRAME_WIDTH, FRAME_HEIGHT, drawer);
    }

    // ==================== Share sheet ====================

    private static Node share() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.BOTTOM);
        drawer.setPrefDrawerHeight(140.0);

        Label shared = text("", "muted");
        HBox targets = new HBox(18.0);
        targets.setAlignment(Pos.CENTER);
        String[][] apps = {{"Mail", "mail-icon", "sky"}, {"Chat", "chat-icon", "teal"},
                {"Copy link", "link-icon", "violet"}, {"Download", "download-icon", "orange"}};
        for (String[] app : apps) {
            StackPane tile = new StackPane(icon(app[1], "on-color"));
            tile.getStyleClass().addAll("round-tile", app[2]);
            tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            VBox target = new VBox(6.0, tile, text(app[0], "copy"));
            target.setAlignment(Pos.CENTER);
            target.getStyleClass().add("clickable");
            target.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                shared.setText("Shared with " + app[0]);
                drawer.close();
            });
            targets.getChildren().add(target);
        }
        VBox sheet = new VBox(12.0, text("Share “Summer trip.png”", "headline"), targets);
        sheet.setAlignment(Pos.CENTER);
        sheet.setPadding(new Insets(14.0));
        drawer.setDrawerContent(sheet);

        StackPane photo = new StackPane(icon("photo-icon", "on-color", "huge"));
        photo.getStyleClass().addAll("tile", "sky", "hero-tile");
        photo.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXButton open = solid("Share", "teal");
        open.setGraphic(icon("share-icon"));
        open.setOnAction(event -> drawer.open());
        VBox page = new VBox(12.0, photo, open, shared);
        page.setAlignment(Pos.CENTER);
        drawer.setContent(page);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, drawer);
    }

    // ==================== Shared ====================

    private static Region placeholder(double width) {
        Region line = new Region();
        line.getStyleClass().add("placeholder-line");
        line.setPrefWidth(width);
        line.setMaxWidth(Region.USE_PREF_SIZE);
        return line;
    }
}
