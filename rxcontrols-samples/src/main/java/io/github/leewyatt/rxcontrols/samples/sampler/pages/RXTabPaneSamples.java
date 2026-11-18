package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTab;
import io.github.leewyatt.rxcontrols.RXTabPane;
import io.github.leewyatt.rxcontrols.RXTabPane.TabAlignment;
import io.github.leewyatt.rxcontrols.RXTabPane.TabClosingPolicy;
import io.github.leewyatt.rxcontrols.RXTabPane.Variant;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.pill;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wrapped;

/**
 * The RXTabPane page: closable editor tabs that scroll, icon tabs that share the
 * width, settings tabs on the left, a phone tab bar at the bottom, pill-shaped tabs
 * restyled from CSS, and a report whose pages slide as the tab changes.
 */
public final class RXTabPaneSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 300.0;

    private static final String[][] FILES = {
            {"App.java", "orange"}, {"Main.css", "sky"}, {"index.html", "rose"}, {"README.md", "teal"},
            {"build.gradle", "lime"}, {"Config.java", "orange"}, {"theme.css", "sky"}};

    private RXTabPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A row of tabs that each own a page, with an underline that slides to the selected tab.",
                List.of("Switch between a few related views of the same thing: files, settings sections, reports.",
                        "For an app's main navigation with many pages use RXSidebar; for two or three options use RXSegmentedControl."),
                List.of(
                        SampleCard.large("Editor tabs",
                                "Variant SCROLLABLE scrolls the tabs with arrows and the wheel; tabClosingPolicy "
                                        + "ALL_TABS puts a close button on every tab.",
                                editor()),
                        SampleCard.large("Icons above text",
                                "contentDisplay TOP stacks icon and text; variant FULL_WIDTH shares the width "
                                        + "equally between the tabs.",
                                media()),
                        SampleCard.large("Tabs on the left",
                                "side LEFT turns the header into a column, a good fit for settings sections.",
                                settings()),
                        SampleCard.large("Bottom tab bar",
                                "side BOTTOM with icon tabs makes a phone tab bar; contentAnimation AnimFade "
                                        + "cross-fades the pages.",
                                phone()),
                        SampleCard.large("Pill tabs",
                                "The same control restyled from CSS: rounded tabs and a filled selected tab, "
                                        + "centered with tabAlignment CENTER.",
                                pills()),
                        SampleCard.large("Sliding report",
                                "contentAnimation AnimSlide moves the pages left or right, following the "
                                        + "direction of the tab change.",
                                report())),
                NavKit.STYLESHEET);
    }

    // ==================== Editor tabs ====================

    private static Node editor() {
        RXTabPane tabs = new RXTabPane();
        tabs.setVariant(Variant.SCROLLABLE);
        tabs.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
        for (String[] file : FILES) {
            tabs.getTabs().add(fileTab(file[0], file[1]));
        }
        Label status = text("", "muted");
        status.textProperty().bind(Bindings.size(tabs.getTabs()).asString("%d files open"));
        RXButton add = plain("New file");
        add.setGraphic(icon("add-icon"));
        int[] next = {1};
        add.setOnAction(event -> {
            RXTab tab = fileTab("Untitled-" + next[0]++ + ".txt", "violet");
            tabs.getTabs().add(tab);
            tabs.getSelectionModel().select(tab);
        });
        HBox bar = new HBox(10.0, status, spacer(), add);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("frame-bar", "bottom");
        BorderPane window = new BorderPane(tabs);
        window.setBottom(bar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, window);
    }

    private static RXTab fileTab(String name, String color) {
        VBox code = new VBox(4.0,
                text("// " + name, "code", "comment"),
                text("public final class Greeter {", "code"),
                text("    String greet(String who) {", "code"),
                text("        return \"Hello, \" + who;", "code"),
                text("    }", "code"),
                text("}", "code"));
        code.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        return RXTab.of(name, icon("file-icon", color), code);
    }

    // ==================== Icons above text ====================

    private static Node media() {
        RXTabPane tabs = new RXTabPane(
                iconTab("Photos", "photo-icon", page("photo-icon", "amber", "1,284 photos", "Last added today")),
                iconTab("Music", "music-icon", page("music-icon", "pink", "312 songs", "18 hours of music")),
                iconTab("Videos", "play-icon", page("play-icon", "violet", "46 videos", "Longest: 12 min")),
                iconTab("Files", "folder-icon", page("folder-icon", "sky", "2.4 GB used", "Of 15 GB")));
        tabs.setVariant(Variant.FULL_WIDTH);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, tabs);
    }

    private static RXTab iconTab(String name, String iconClass, Node content) {
        RXTab tab = RXTab.of(name, icon(iconClass, "nav-icon"), content);
        tab.setContentDisplay(ContentDisplay.TOP);
        return tab;
    }

    private static Node page(String iconClass, String color, String headline, String note) {
        StackPane badge = new StackPane(icon(iconClass, "on-color"));
        badge.getStyleClass().addAll("tile", color);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(10.0, badge, text(headline, "headline"), text(note, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Tabs on the left ====================

    private static Node settings() {
        RXTextField name = new RXTextField("Lee Wyatt");
        RXTextField handle = new RXTextField("@LeeWyatt_7788");
        VBox profile = section("Profile", new VBox(6.0, text("Name", "muted"), name,
                text("Handle", "muted"), handle));

        VBox alerts = section("Notifications",
                switchRow("Email me about replies", true),
                switchRow("Weekly summary", false),
                switchRow("Sounds", true));

        VBox privacy = section("Privacy",
                switchRow("Public profile", true),
                switchRow("Show when I'm online", false));

        Label plan = text("Free plan", "headline");
        VBox billing = section("Billing", new HBox(10.0, plan, pill("Active", "lime")),
                wrapped("Upgrade to get more storage and shared folders.", "copy"), solid("Upgrade", "indigo"));

        RXTabPane tabs = new RXTabPane(
                RXTab.of("Profile", icon("person-icon", "nav-icon"), profile),
                RXTab.of("Alerts", icon("bell-icon", "nav-icon"), alerts),
                RXTab.of("Privacy", icon("lock-icon", "nav-icon"), privacy),
                RXTab.of("Billing", icon("card-icon", "nav-icon"), billing));
        tabs.setSide(Side.LEFT);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, tabs);
    }

    private static VBox section(String title, Node... rows) {
        VBox box = new VBox(12.0);
        box.getChildren().add(text(title, "headline"));
        box.getChildren().addAll(rows);
        box.setPadding(new Insets(16.0, 18.0, 16.0, 18.0));
        return box;
    }

    private static Node switchRow(String label, boolean on) {
        RXSwitchButton toggle = new RXSwitchButton();
        toggle.setSelected(on);
        HBox row = new HBox(10.0, text(label, "copy"), spacer(), toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Bottom tab bar ====================

    private static Node phone() {
        RXTabPane tabs = new RXTabPane(
                iconTab("Home", "home-icon", page("home-icon", "teal", "Good morning", "3 things on today")),
                iconTab("Search", "search-icon", page("search-icon", "sky", "Find anything", "Places, people, notes")),
                iconTab("Inbox", "inbox-icon", page("inbox-icon", "rose", "2 new messages", "From the design team")),
                iconTab("Me", "person-icon", page("person-icon", "violet", "Lee Wyatt", "@LeeWyatt_7788")));
        tabs.setSide(Side.BOTTOM);
        tabs.setVariant(Variant.FULL_WIDTH);
        tabs.setContentAnimation(new AnimFade());
        tabs.getStyleClass().add("phone-tabs");
        StackPane phone = frame(260.0, FRAME_HEIGHT, tabs);
        phone.getStyleClass().add("phone");
        return phone;
    }

    // ==================== Pill tabs ====================

    private static Node pills() {
        RXTabPane tabs = new RXTabPane(
                RXTab.of("Monthly", plan("$12", "per month", "Cancel any time")),
                RXTab.of("Yearly", plan("$120", "per year", "Two months free")),
                RXTab.of("Lifetime", plan("$290", "once", "Every future update")));
        tabs.setTabAlignment(TabAlignment.CENTER);
        tabs.getStyleClass().add("pills");
        tabs.getSelectionModel().select(1);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, tabs);
    }

    private static Node plan(String price, String period, String perk) {
        Label amount = text(price, "price");
        HBox line = new HBox(6.0, amount, text(period, "muted"));
        line.setAlignment(Pos.BASELINE_CENTER);
        HBox perkRow = new HBox(6.0, icon("check-icon", "lime"), text(perk, "copy"));
        perkRow.setAlignment(Pos.CENTER);
        RXButton choose = solid("Choose plan", "indigo");
        VBox box = new VBox(12.0, line, perkRow, choose);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Sliding report ====================

    private static Node report() {
        RXTabPane tabs = new RXTabPane(
                RXTab.of("Day", chart("Visitors today", "orange", 30, 45, 38, 60, 52, 75, 68)),
                RXTab.of("Week", chart("Visitors this week", "teal", 55, 62, 48, 80, 90, 72, 40)),
                RXTab.of("Month", chart("Visitors this month", "indigo", 70, 64, 82, 76, 88, 95, 84)),
                RXTab.of("Year", chart("Visitors this year", "pink", 40, 52, 61, 58, 73, 85, 92)));
        tabs.setContentAnimation(new AnimSlide());
        return frame(FRAME_WIDTH, FRAME_HEIGHT, tabs);
    }

    private static Node chart(String title, String color, int... values) {
        HBox bars = new HBox(10.0);
        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.setPrefHeight(150.0);
        for (int value : values) {
            Region bar = new Region();
            bar.getStyleClass().addAll("bar", color);
            bar.setPrefSize(30.0, value * 1.5);
            bar.setMaxHeight(Region.USE_PREF_SIZE);
            bars.getChildren().add(bar);
        }
        int total = 0;
        for (int value : values) {
            total += value;
        }
        HBox head = new HBox(text(title, "copy"), spacer(), text(String.format("%,d", total * 37), "headline"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(12.0, head, bars);
        VBox.setVgrow(bars, Priority.ALWAYS);
        box.setPadding(new Insets(16.0, 18.0, 16.0, 18.0));
        return box;
    }
}
