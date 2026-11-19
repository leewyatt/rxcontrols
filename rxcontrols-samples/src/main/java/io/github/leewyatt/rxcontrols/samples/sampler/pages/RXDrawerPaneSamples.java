package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDrawerPane.DrawerMode;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.pill;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wideFrame;

/**
 * The RXDrawerPane page: order details sliding in from the right over a dimmed list,
 * a navigation menu from the left, and the same filter panel twice, once pushing the
 * product grid aside and once sliding over it, and a cross of buttons that opens the
 * drawer from each of the four sides. The first drawers start open; while one is
 * closed, a border beam runs round the button that opens it again.
 */
public final class RXDrawerPaneSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 300.0;
    private static final double SIDE_BUTTON_WIDTH = 90.0;
    private static final Color BEAM_COLOR = Color.web("#8b5cf6");

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
                                "side RIGHT (the default) with a backdrop, open on the first order: a click on the "
                                        + "dimmed list, Close or Esc closes the drawer.",
                                orders()),
                        SampleCard.large("Navigation menu",
                                "side LEFT; picking a page sets the title and calls close(). While the drawer is "
                                        + "closed an RXBorderBeam runs round the menu button.",
                                menu()),
                        SampleCard.large("Filters that push",
                                "drawerMode PUSH moves the grid aside instead of covering it, without a backdrop, "
                                        + "so the grid stays usable.",
                                filters(DrawerMode.PUSH)).ribbon("Push"),
                        SampleCard.large("Filters that overlay",
                                "The same panel with drawerMode OVERLAY slides over the grid and leaves it in place, "
                                        + "with a backdrop: a click on the dimmed grid closes the drawer.",
                                filters(DrawerMode.OVERLAY)).ribbon("Overlay"),
                        SampleCard.canvas("Four sides",
                                "Each button sets side and opens the drawer from that edge; LEFT and RIGHT take "
                                        + "prefDrawerWidth, TOP and BOTTOM prefDrawerHeight.",
                                sides())),
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
        RXButton done = solid("Close", "sky");
        done.setMaxWidth(Double.MAX_VALUE);
        done.setOnAction(event -> drawer.close());
        VBox details = new VBox(10.0, head, status, text("Item", "muted"), product,
                text("Total", "muted"), total, spacer(), done);
        details.setPadding(new Insets(14.0, 16.0, 16.0, 16.0));
        drawer.setDrawerContent(details);

        Consumer<String[]> show = order -> {
            number.setText("Order " + order[0]);
            product.setText(order[1]);
            total.setText(order[2]);
            status.getChildren().setAll(pill(order[3], order[4]));
        };
        VBox list = new VBox(2.0);
        list.setPadding(new Insets(10.0, 12.0, 10.0, 12.0));
        list.getChildren().add(text("Recent orders", "headline", "list-title"));
        for (String[] order : ORDERS) {
            HBox row = new HBox(10.0, text(order[0], "muted"), text(order[1], "copy"), spacer(),
                    pill(order[3], order[4]), text(order[2], "copy"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("list-row", "clickable");
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                show.accept(order);
                drawer.open();
            });
            list.getChildren().add(row);
        }
        drawer.setContent(list);
        show.accept(ORDERS[0]);
        drawer.open();
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
        HBox bar = new HBox(10.0, beckon(burger, drawer), title);
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
        drawer.open();
        return frame(FRAME_WIDTH, FRAME_HEIGHT, drawer);
    }

    // ==================== Filters ====================

    // One builder for both filter cards, so they differ in the drawer mode and backdrop
    // alone: a pushed grid stays usable, an overlaid one is dimmed and closes on a click.
    private static Node filters(DrawerMode mode) {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.LEFT);
        drawer.setDrawerMode(mode);
        drawer.setBackdropVisible(mode == DrawerMode.OVERLAY);
        drawer.setPrefDrawerWidth(150.0);

        VBox panel = new VBox(10.0, text("Filters", "headline"),
                new RXCheckBox("In stock"), new RXCheckBox("On sale"), new RXCheckBox("Free shipping"));
        panel.setPadding(new Insets(14.0));
        drawer.setDrawerContent(panel);

        RXButton toggle = plain("Filters");
        toggle.setGraphic(icon("tune-icon"));
        toggle.setOnAction(event -> drawer.toggle());
        HBox bar = new HBox(10.0, text("24 products", "headline"), spacer(), beckon(toggle, drawer));
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

    // ==================== Four sides ====================

    private static Node sides() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setPrefDrawerWidth(220.0);
        drawer.setPrefDrawerHeight(120.0);

        Label from = text("", "headline");
        Label property = text("", "muted");
        RXButton close = solid("Close", "indigo");
        close.setOnAction(event -> drawer.close());
        VBox panel = new VBox(8.0, from, property, close);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(14.0));
        drawer.setDrawerContent(panel);

        // The backdrop covers the buttons while the drawer is open, so the side only
        // changes once it has closed.
        GridPane cross = new GridPane();
        cross.setHgap(10.0);
        cross.setVgap(10.0);
        cross.setAlignment(Pos.CENTER);
        cross.add(sideButton("Top", Side.TOP, drawer, from, property), 1, 0);
        cross.add(sideButton("Left", Side.LEFT, drawer, from, property), 0, 1);
        cross.add(text("Pick a side", "muted"), 1, 1);
        cross.add(sideButton("Right", Side.RIGHT, drawer, from, property), 2, 1);
        cross.add(sideButton("Bottom", Side.BOTTOM, drawer, from, property), 1, 2);
        for (Node node : cross.getChildren()) {
            GridPane.setHalignment(node, HPos.CENTER);
        }
        drawer.setContent(cross);
        return wideFrame(FRAME_HEIGHT, drawer);
    }

    private static Node sideButton(String name, Side side, RXDrawerPane drawer, Label from, Label property) {
        RXButton button = plain(name);
        button.setPrefWidth(SIDE_BUTTON_WIDTH);
        button.setOnAction(event -> {
            from.setText("From the " + name.toLowerCase(Locale.ROOT));
            property.setText("side " + side.name());
            drawer.setSide(side);
            drawer.open();
        });
        return button;
    }

    // ==================== Shared ====================

    // A beam runs round the button that brings the drawer back, only while it is closed.
    private static Node beckon(Node button, RXDrawerPane drawer) {
        RXBorderBeam beam = new RXBorderBeam(button);
        beam.setBeamFill(BEAM_COLOR);
        beam.setBeamSize(30.0);
        beam.setLineWidth(1.5);
        beam.setCycleDuration(Duration.seconds(2.5));
        beam.activeProperty().bind(drawer.showingProperty().not());
        beam.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return beam;
    }

    private static Region placeholder(double width) {
        Region line = new Region();
        line.getStyleClass().add("placeholder-line");
        line.setPrefWidth(width);
        line.setMaxWidth(Region.USE_PREF_SIZE);
        return line;
    }
}
