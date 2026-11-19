package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.chip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;

/**
 * The RXBox page: a product card that flips between a row and a column, a
 * baseline-aligned form row, a sidebar with a pinned footer, chat bubbles aligned per
 * child, equal-height plan columns and a toolbar with a growing search field.
 */
public final class RXBoxSamples {

    private RXBoxSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A one-dimensional box that lays its children out in a row or a column and can switch between the two at runtime.",
                List.of("Toolbars, form rows, stacks and any single run of nodes, where you would reach for HBox or VBox.",
                        "For a grid that reflows by width use RXRow and RXCol; for wrapping runs use RXFlowPane."),
                List.of(
                        SampleCard.canvas("Row or column",
                                "orientation flips the same card between a row and a column at runtime; grow and margin constraints follow the new axis.",
                                productCard()),
                        SampleCard.medium("Form row on a baseline",
                                "alignment BASELINE_LEFT lines up the label, the field and the button text, even at different font sizes.",
                                baselineRows()),
                        SampleCard.medium("Sidebar with a pinned footer",
                                "setGrow on the menu pushes the profile row to the bottom of a fixed-height sidebar.",
                                sidebar()),
                        SampleCard.large("Chat bubbles",
                                "With fillCrossAxis off, RXBox.setAlignment puts your messages on the right and the replies on the left.",
                                chat()),
                        SampleCard.large("Equal-height columns",
                                "fillCrossAxis is on by default, so plan cards with more text do not leave their neighbours short; untick it to compare.",
                                plans()),
                        SampleCard.canvas("Toolbar",
                                "RXBox.setGrow gives the spare width to the search field, and setMargin sets the compose button apart from the icons.",
                                toolbar())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Toolbar ====================

    private static Node toolbar() {
        StackPane menu = iconButton("menu-icon");
        Label title = text("Inbox", "headline");
        title.setMinWidth(Region.USE_PREF_SIZE);

        TextField search = new TextField();
        search.setPromptText("Search mail");
        search.setMaxWidth(Double.MAX_VALUE);
        RXBox.setGrow(search, Priority.ALWAYS);

        StackPane filter = iconButton("filter-icon");
        StackPane archive = iconButton("archive-icon");
        RXButton compose = solid("Compose", "indigo");
        compose.setMinWidth(Region.USE_PREF_SIZE);
        RXBox.setMargin(compose, new Insets(0, 0, 0, 12.0));

        RXBox bar = new RXBox(8.0, menu, title, search, filter, archive, compose);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    // ==================== Row or column ====================

    private static Node productCard() {
        RXImageView photo = new RXImageView(scenery(3));
        photo.setPrefSize(220.0, 150.0);
        photo.setImageRadius(10.0);

        Label name = text("Lakeside cabin, 2 nights", "headline");
        Label body = wrapped("A quiet wooden cabin by the water, with a fire pit, two kayaks and a view of the hills.", "muted");
        RXButton book = solid("Book for $240", "teal");
        VBox copy = new VBox(8.0, chip("Free cancellation", "lime"), name, body, book);

        RXBox card = new RXBox(Orientation.HORIZONTAL, 16.0, photo, copy);
        card.getStyleClass().add("panel");
        RXBox.setGrow(copy, Priority.ALWAYS);
        card.setMaxWidth(560.0);

        RXSegmentedControl<Orientation> mode = picker(card::setOrientation,
                Orientation.HORIZONTAL, Orientation.VERTICAL);
        VBox box = new VBox(14.0, mode, card);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    // ==================== Baseline ====================

    private static Node baselineRows() {
        TextField code = new TextField();
        code.setPromptText("SPRING25");
        code.setPrefColumnCount(6);
        RXBox coupon = new RXBox(10.0, text("Coupon", "copy"), code, plain("Apply"));
        coupon.setAlignment(Pos.BASELINE_LEFT);

        RXBox total = new RXBox(8.0, text("Total", "copy"), text("$128.00", "price"),
                text("incl. tax", "muted"));
        total.setAlignment(Pos.BASELINE_LEFT);

        VBox box = new VBox(18.0, coupon, total);
        box.getStyleClass().add("panel");
        return box;
    }

    // ==================== Chat ====================

    private static Node chat() {
        RXBox thread = new RXBox(Orientation.VERTICAL, 8.0,
                bubble("Are we still on for the hike on Saturday?", false),
                bubble("Yes! Meet at the trailhead at 8?", true),
                bubble("Perfect. I'll bring coffee for both of us.", false),
                bubble("You're the best", true));
        thread.setFillCrossAxis(false);
        thread.getStyleClass().add("panel");
        thread.setPrefWidth(320.0);
        thread.setMaxWidth(Region.USE_PREF_SIZE);
        return thread;
    }

    private static Node bubble(String message, boolean mine) {
        Label bubble = wrapped(message, "bubble", mine ? "mine" : "theirs");
        bubble.setMaxWidth(210.0);
        RXBox.setAlignment(bubble, mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return bubble;
    }

    // ==================== Sidebar ====================

    private static Node sidebar() {
        VBox menu = new VBox(2.0,
                navItem("home-icon", "Home", true),
                navItem("folder-icon", "Projects", false),
                navItem("calendar-icon", "Calendar", false),
                navItem("settings-icon", "Settings", false));

        Region avatar = new Region();
        avatar.getStyleClass().add("avatar-dot");
        avatar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXBox profile = new RXBox(10.0, avatar, new VBox(text("Lee Wyatt", "copy-strong"),
                text("@LeeWyatt_7788", "muted")));
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("profile-row");

        RXBox side = new RXBox(Orientation.VERTICAL, 12.0, text("Workspace", "eyebrow"), menu, profile);
        RXBox.setGrow(menu, Priority.ALWAYS);
        side.getStyleClass().add("sidebar");
        side.setPrefSize(220.0, 280.0);
        side.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return side;
    }

    private static Node navItem(String iconClass, String name, boolean selected) {
        RXBox item = new RXBox(10.0, icon(iconClass), text(name, "copy"));
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("nav-item");
        if (selected) {
            item.getStyleClass().add("selected");
        }
        return item;
    }

    // ==================== Equal heights ====================

    private static Node plans() {
        RXBox row = new RXBox(10.0,
                plan("Free", "$0", "Personal notes", "sky"),
                plan("Team", "$8", "Shared spaces, comments and a full history of every change", "violet"),
                plan("Plus", "$4", "Sync on all devices", "orange"));
        row.setPrefWidth(380.0);
        row.setMaxWidth(Region.USE_PREF_SIZE);

        CheckBox fill = new CheckBox("fillCrossAxis");
        fill.setSelected(true);
        row.fillCrossAxisProperty().bind(fill.selectedProperty());

        VBox box = new VBox(14.0, row, fill);
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private static Node plan(String name, String price, String detail, String color) {
        VBox plan = new VBox(4.0, text(name, "plan-name", color), text(price, "price"),
                wrapped(detail, "muted"));
        plan.getStyleClass().add("plan");
        plan.setPrefWidth(120.0);
        RXBox.setGrow(plan, Priority.ALWAYS);
        return plan;
    }
}
