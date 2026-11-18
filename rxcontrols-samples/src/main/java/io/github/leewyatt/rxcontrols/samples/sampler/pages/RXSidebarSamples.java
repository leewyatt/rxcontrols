package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.Mode;
import io.github.leewyatt.rxcontrols.RXSidebarCaption;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wideFrame;

/**
 * The RXSidebar page: an app sidebar with groups, counts, a profile footer and a
 * collapse-to-rail button, a sidebar that rests as an icon rail, a searchable docs
 * menu, actions next to pages, and a three-level tree with a smaller indent.
 */
public final class RXSidebarSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 330.0;
    private static final double NARROW_SIDEBAR = 210.0;

    private RXSidebarSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A vertical navigation menu of pages and groups that can shrink to an icon rail.",
                List.of("The main navigation of an app with many pages, kept on the side of every screen.",
                        "For three to five top-level views use RXTabPane; for a menu shown only on demand use RXDrawerPane."),
                List.of(
                        SampleCard.canvas("App sidebar",
                                "Groups, captions, counts in the trailing slot, pages pinned to the bottom and a header "
                                        + "button that switches mode between EXPANDED and MINI.",
                                appSidebar()),
                        SampleCard.large("Icon rail",
                                "Mode MINI shows icons only; a page's name is its tooltip and resting on a group opens "
                                        + "a flyout with its pages.",
                                rail()),
                        SampleCard.large("Search the menu",
                                "textFilter matches item text and keywords (try \"invoice\"); the placeholder shows "
                                        + "when nothing matches.",
                                search()),
                        SampleCard.large("Pages and actions",
                                "RXSidebarItem.action runs a command without changing the selected page; a disabled "
                                        + "item stays visible.",
                                actions()),
                        SampleCard.large("Nested folders",
                                "Groups inside groups, with indent set to 14 so deep levels keep their text.",
                                nested())),
                NavKit.STYLESHEET);
    }

    // ==================== App sidebar ====================

    private static Node appSidebar() {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setExpandedWidth(232.0);
        sidebar.getStyleClass().add("app");

        StackPane logo = new StackPane(icon("bolt-icon"));
        logo.getStyleClass().add("logo");
        logo.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label name = text("Northwind", "app-name");
        BooleanBinding expanded = sidebar.modeProperty().isNotEqualTo(Mode.MINI);
        name.visibleProperty().bind(expanded);
        name.managedProperty().bind(expanded);
        logo.visibleProperty().bind(expanded);
        logo.managedProperty().bind(expanded);
        StackPane collapse = iconButton("menu-icon");
        collapse.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> sidebar.setMode(
                sidebar.getMode() == Mode.MINI ? Mode.EXPANDED : Mode.MINI));
        HBox header = new HBox(10.0, logo, name, spacer(), collapse);
        header.setAlignment(Pos.CENTER);
        header.getStyleClass().add("sidebar-header");
        sidebar.setHeader(header);

        RXSidebarItem dashboard = page("Dashboard", "home-icon");
        RXSidebarItem inbox = page("Inbox", "inbox-icon");
        inbox.setTrailing(pill("12", "rose"));
        RXSidebarItem mobile = RXSidebarItem.of("Mobile app");
        mobile.setTrailing(pill("3", "amber"));
        RXSidebarItem projects = RXSidebarItem.group("Projects", icon("folder-icon"),
                RXSidebarItem.of("Website redesign"), mobile);
        projects.setExpanded(true);
        RXSidebarItem reports = RXSidebarItem.group("Reports", icon("chart-icon"),
                RXSidebarItem.of("Sales"), RXSidebarItem.of("Traffic"), RXSidebarItem.of("Customers"));
        RXSidebarItem calendar = page("Calendar", "calendar-icon");
        calendar.setTrailing(pill("New", "teal"));
        sidebar.getItems().addAll(dashboard, inbox, new RXSidebarCaption("Workspace"), projects, reports, calendar);
        sidebar.getBottomItems().addAll(page("Settings", "settings-icon"), page("Help", "help-icon"));
        sidebar.setFooter(profile(expanded));
        sidebar.selectItem(mobile);

        BorderPane app = new BorderPane(content(sidebar));
        app.setLeft(sidebar);
        return wideFrame(560.0, app);
    }

    private static Node profile(BooleanBinding expanded) {
        Label avatar = text("LW", "avatar");
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(32.0, 32.0);
        avatar.setMaxSize(32.0, 32.0);
        VBox who = new VBox(1.0, text("Lee Wyatt", "copy-strong"), text("@LeeWyatt_7788", "muted"));
        who.visibleProperty().bind(expanded);
        who.managedProperty().bind(expanded);
        HBox profile = new HBox(10.0, avatar, who);
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("sidebar-footer");
        return profile;
    }

    // ==================== Icon rail ====================

    private static Node rail() {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setMode(Mode.MINI);
        RXSidebarItem music = page("Listen now", "music-icon");
        sidebar.getItems().addAll(music, page("Browse", "search-icon"),
                RXSidebarItem.group("Library", icon("folder-icon"),
                        RXSidebarItem.of("Albums"), RXSidebarItem.of("Artists"), RXSidebarItem.of("Songs")),
                RXSidebarItem.group("Playlists", icon("note-icon"),
                        RXSidebarItem.of("Morning run"), RXSidebarItem.of("Focus"), RXSidebarItem.of("Road trip")));
        sidebar.getBottomItems().add(page("Settings", "settings-icon"));
        sidebar.selectItem(music);
        BorderPane app = new BorderPane(content(sidebar));
        app.setLeft(sidebar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, app);
    }

    // ==================== Search ====================

    private static Node search() {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setExpandedWidth(NARROW_SIDEBAR);
        RXSidebarItem invoices = RXSidebarItem.of("Billing");
        invoices.getKeywords().addAll("Invoice", "Payment");
        RXSidebarItem account = RXSidebarItem.group("Account", icon("person-icon"),
                RXSidebarItem.of("Profile"), invoices, RXSidebarItem.of("Security"));
        RXSidebarItem start = RXSidebarItem.of("Install");
        RXSidebarItem guides = RXSidebarItem.group("Guides", icon("note-icon"),
                start, RXSidebarItem.of("First project"), RXSidebarItem.of("Deploy"));
        guides.setExpanded(true);
        RXSidebarItem api = RXSidebarItem.group("API", icon("code-icon"),
                RXSidebarItem.of("Requests"), RXSidebarItem.of("Webhooks"), RXSidebarItem.of("Rate limits"));
        sidebar.getItems().addAll(guides, api, account);
        sidebar.selectItem(start);

        RXTextField field = new RXTextField();
        field.setPromptText("Search docs");
        field.setLeading(icon("search-icon", "field-icon"));
        field.textProperty().addListener((obs, old, query) -> sidebar.setFilter(RXSidebar.textFilter(query)));
        StackPane header = new StackPane(field);
        header.setPadding(new Insets(10.0));
        sidebar.setHeader(header);
        sidebar.setPlaceholder(text("No pages match", "muted"));

        BorderPane app = new BorderPane(content(sidebar));
        app.setLeft(sidebar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, app);
    }

    // ==================== Actions ====================

    private static Node actions() {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setExpandedWidth(NARROW_SIDEBAR);
        Label status = text("Click New note or Import", "muted");
        RXSidebarItem notes = page("All notes", "note-icon");
        RXSidebarItem billing = page("Billing", "card-icon");
        billing.setDisable(true);
        sidebar.getTopItems().addAll(
                RXSidebarItem.action("New note", icon("add-icon", "teal"),
                        event -> status.setText("Created “Untitled note”")),
                RXSidebarItem.action("Import", icon("upload-icon", "sky"),
                        event -> status.setText("Import started")),
                new RXSidebarSeparator());
        sidebar.getItems().addAll(notes, page("Starred", "star-icon"), page("Shared", "people-icon"),
                new RXSidebarSeparator(), page("Trash", "delete-icon"), billing);
        sidebar.selectItem(notes);

        VBox content = content(sidebar);
        content.getChildren().add(status);
        BorderPane app = new BorderPane(content);
        app.setLeft(sidebar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, app);
    }

    // ==================== Nested ====================

    private static Node nested() {
        RXSidebar sidebar = new RXSidebar();
        sidebar.setExpandedWidth(NARROW_SIDEBAR);
        sidebar.setIndent(14.0);
        RXSidebarItem logo = RXSidebarItem.of("logo.svg");
        RXSidebarItem assets = RXSidebarItem.group("assets", null, logo, RXSidebarItem.of("hero.png"));
        assets.setExpanded(true);
        RXSidebarItem site = RXSidebarItem.group("website", icon("folder-icon", "amber"),
                assets, RXSidebarItem.of("index.html"), RXSidebarItem.of("styles.css"));
        site.setExpanded(true);
        RXSidebarItem docs = RXSidebarItem.group("documents", icon("folder-icon", "sky"),
                RXSidebarItem.of("budget.xlsx"), RXSidebarItem.of("notes.md"));
        RXSidebarItem photos = RXSidebarItem.group("photos", icon("folder-icon", "pink"),
                RXSidebarItem.group("2026", null, RXSidebarItem.of("summer"), RXSidebarItem.of("winter")));
        sidebar.getItems().addAll(site, docs, photos);
        sidebar.selectItem(logo);

        BorderPane app = new BorderPane(content(sidebar));
        app.setLeft(sidebar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, app);
    }

    // ==================== Shared ====================

    private static RXSidebarItem page(String name, String iconClass) {
        return RXSidebarItem.of(name, icon(iconClass, "nav-icon"));
    }

    /** The page area next to a sidebar, showing the selected page. */
    private static VBox content(RXSidebar sidebar) {
        Label title = text("", "page-title");
        title.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarItem selected = sidebar.getSelectedItem();
            return selected == null ? "Nothing selected" : selected.getText();
        }, sidebar.selectedItemProperty()));
        Label path = text("", "muted");
        path.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarItem selected = sidebar.getSelectedItem();
            if (selected == null || selected.getParent() == null) {
                return "Home";
            }
            return "Home / " + selected.getParent().getText();
        }, sidebar.selectedItemProperty()));
        Region line1 = placeholderLine(0.9);
        Region line2 = placeholderLine(0.7);
        Region line3 = placeholderLine(0.8);
        VBox content = new VBox(8.0, path, title, line1, line2, line3);
        content.getStyleClass().add("page-area");
        return content;
    }

    private static Region placeholderLine(double share) {
        Region line = new Region();
        line.getStyleClass().add("placeholder-line");
        line.setMaxWidth(Region.USE_PREF_SIZE);
        line.setPrefWidth(180.0 * share);
        return line;
    }
}
