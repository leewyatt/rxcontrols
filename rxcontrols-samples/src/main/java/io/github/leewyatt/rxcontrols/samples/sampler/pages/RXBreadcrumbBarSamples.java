package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wideFrame;

/**
 * The RXBreadcrumbBar page: a file browser whose long path folds into an overflow
 * menu in a narrow window, shop categories with menus of the next level, a path that
 * refuses to change while there are unsaved edits, a custom separator with a home
 * icon, and a path capped at three visible items.
 */
public final class RXBreadcrumbBarSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 210.0;

    private static final List<String> DRIVES = List.of("Macintosh HD", "Backup drive");
    private static final Map<String, List<String>> FOLDERS = new LinkedHashMap<>();

    static {
        FOLDERS.put("Macintosh HD", List.of("Applications", "Library", "Users"));
        FOLDERS.put("Backup drive", List.of("2025", "2026"));
        FOLDERS.put("Users", List.of("lee", "Shared"));
        FOLDERS.put("lee", List.of("Documents", "Pictures", "Projects"));
        FOLDERS.put("Projects", List.of("notes-app", "rxcontrols", "website"));
        FOLDERS.put("rxcontrols", List.of("build", "docs", "src"));
        FOLDERS.put("src", List.of("main", "test"));
        FOLDERS.put("main", List.of("java", "resources"));
        FOLDERS.put("java", List.of("Catalog.java", "Sampler.java", "Theme.java"));
        FOLDERS.put("website", List.of("assets", "index.html", "styles.css"));
        FOLDERS.put("Documents", List.of("budget.xlsx", "letter.docx"));
        FOLDERS.put("Pictures", List.of("beach.png", "city.png"));
    }

    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Shop", List.of("Electronics", "Home & garden", "Sports"));
        CATEGORIES.put("Electronics", List.of("Laptops", "Phones", "Cameras", "Audio"));
        CATEGORIES.put("Laptops", List.of("Ultrabooks", "Gaming", "Workstations"));
        CATEGORIES.put("Phones", List.of("Android", "iPhone"));
        CATEGORIES.put("Audio", List.of("Headphones", "Speakers"));
        CATEGORIES.put("Home & garden", List.of("Kitchen", "Lighting", "Plants"));
        CATEGORIES.put("Sports", List.of("Running", "Cycling", "Camping"));
    }

    private RXBreadcrumbBarSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A trail of links from the root to the current location, each one a step back up the hierarchy.",
                List.of("Show where a page or file sits in a deep hierarchy and let the user jump back up.",
                        "For moving between pages at the same level use RXTabPane or RXSidebar."),
                List.of(
                        SampleCard.canvas("File browser",
                                "Both bars show one path list; a bar that runs out of room folds middle folders into an "
                                        + "overflow menu. The arrows (childrenFactory) list the folders next to each one.",
                                files()),
                        SampleCard.large("Shop categories",
                                "branchMode CHILDREN: each arrow lists the level below, so a shopper can go "
                                        + "deeper without leaving the bar.",
                                shop()),
                        SampleCard.large("Unsaved changes",
                                "autoNavigate is off by default, so onNavigate decides: here it refuses to leave "
                                        + "while there are unsaved edits.",
                                guarded()),
                        SampleCard.large("Separator and icons",
                                "separatorFactory puts a chevron between the items; graphicFactory gives the first "
                                        + "one a home icon.",
                                settings()),
                        SampleCard.large("At most three",
                                "maxVisibleItems 3 keeps the root and the current item and folds the rest, "
                                        + "even when there is room.",
                                capped())),
                NavKit.STYLESHEET);
    }

    // ==================== File browser ====================

    private static Node files() {
        ObservableList<String> path = FXCollections.observableArrayList(
                "Macintosh HD", "Users", "lee", "Projects", "rxcontrols", "src", "main", "java");
        RXBreadcrumbBar<String> wide = fileBar(path);
        RXBreadcrumbBar<String> narrow = fileBar(path);
        narrow.setMaxWidth(300.0);

        VBox list = new VBox(2.0);
        list.setPadding(new Insets(8.0, 12.0, 12.0, 12.0));
        Runnable fill = () -> {
            list.getChildren().clear();
            String current = path.get(path.size() - 1);
            List<String> children = FOLDERS.getOrDefault(current, List.of());
            if (children.isEmpty()) {
                list.getChildren().add(text("This folder is empty", "muted"));
            }
            for (String child : children) {
                list.getChildren().add(fileRow(child, path));
            }
        };
        path.addListener((ListChangeListener<String>) change -> fill.run());
        fill.run();

        VBox bars = new VBox(6.0, text("Full width", "muted"), wide, text("In a 300 px panel", "muted"), narrow);
        VBox.setMargin(narrow, new Insets(0.0, 0.0, 2.0, 0.0));
        bars.getStyleClass().add("frame-bar");
        BorderPane window = new BorderPane(list);
        window.setTop(bars);
        return wideFrame(300.0, window);
    }

    private static RXBreadcrumbBar<String> fileBar(ObservableList<String> path) {
        RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>();
        bar.setItems(path);
        bar.setAutoNavigate(true);
        bar.setGraphicFactory(name -> DRIVES.contains(name) ? icon("drive-icon") : icon("folder-icon", "amber"));
        bar.setChildrenFactory(p -> p.isEmpty() ? DRIVES : folderChildren(p.get(p.size() - 1)));
        return bar;
    }

    private static List<String> folderChildren(String folder) {
        List<String> result = new ArrayList<>();
        for (String child : FOLDERS.getOrDefault(folder, List.of())) {
            if (!child.contains(".")) {
                result.add(child);
            }
        }
        return result;
    }

    private static Node fileRow(String name, ObservableList<String> path) {
        boolean file = name.contains(".");
        HBox row = new HBox(10.0, file ? icon("file-icon", "sky") : icon("folder-icon", "amber"),
                text(name, "copy"), spacer(), text(file ? "4 KB" : "Folder", "muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        if (!file) {
            row.getStyleClass().add("clickable");
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> path.add(name));
        }
        return row;
    }

    // ==================== Shop categories ====================

    private static Node shop() {
        RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>("Shop", "Electronics", "Laptops");
        bar.setBranchMode(RXBreadcrumbBar.BranchMode.CHILDREN);
        bar.setAutoNavigate(true);
        bar.setChildrenFactory(p -> p.isEmpty() ? List.of() : CATEGORIES.getOrDefault(p.get(p.size() - 1), List.of()));

        Label title = text("", "page-title");
        Label count = text("", "muted");
        Runnable update = () -> {
            String current = bar.getItems().get(bar.getItems().size() - 1);
            title.setText(current);
            count.setText((current.length() * 37 % 300 + 24) + " products");
        };
        bar.getItems().addListener((ListChangeListener<String>) change -> update.run());
        update.run();
        HBox products = new HBox(10.0, product("Best seller", "amber"), product("New in", "sky"),
                product("On sale", "pink"));
        VBox body = new VBox(6.0, title, count, products);
        VBox.setMargin(products, new Insets(8.0, 0.0, 0.0, 0.0));
        body.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        return window(bar, body);
    }

    private static Node product(String name, String color) {
        VBox card = new VBox(6.0, icon("photo-icon", color, "large"), text(name, "copy"));
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("product");
        return card;
    }

    // ==================== Unsaved changes ====================

    private static Node guarded() {
        RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>("Projects", "Website", "Pages", "Pricing");
        RXCheckBox unsaved = new RXCheckBox("Unsaved changes on this page");
        unsaved.setSelected(true);
        Label message = text("Click Website or Projects in the bar", "muted");
        bar.setOnNavigate((RXBreadcrumbEvent<String> event) -> {
            if (unsaved.isSelected()) {
                message.setText("Save or discard your changes before leaving “" + bar.getItems()
                        .get(bar.getItems().size() - 1) + "”.");
                message.getStyleClass().setAll("label", "error-text");
            } else {
                bar.getItems().setAll(event.getPath());
                message.setText("Opened “" + event.getItem() + "”");
                message.getStyleClass().setAll("label", "muted");
            }
        });
        message.setWrapText(true);
        VBox body = new VBox(12.0, text("Editing the pricing page", "headline"), unsaved, message);
        body.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        return window(bar, body);
    }

    // ==================== Separator and icons ====================

    private static Node settings() {
        RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>("Home", "Settings", "Account", "Security");
        bar.setAutoNavigate(true);
        bar.setSeparatorFactory(() -> icon("chevron-icon", "separator-glyph"));
        bar.setGraphicFactory(name -> "Home".equals(name) ? icon("home-icon") : null);
        bar.getStyleClass().add("chevrons");

        Label title = text("", "page-title");
        title.textProperty().bind(lastItem(bar));
        VBox body = new VBox(8.0, title, text("Click an earlier item to go back up.", "muted"));
        body.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        return window(bar, body);
    }

    private static StringBinding lastItem(RXBreadcrumbBar<String> bar) {
        return Bindings.createStringBinding(
                () -> bar.getItems().isEmpty() ? "" : bar.getItems().get(bar.getItems().size() - 1), bar.getItems());
    }

    // ==================== At most three ====================

    private static Node capped() {
        RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>(
                "Company", "Engineering", "Platform", "Payments", "API team");
        bar.setMaxVisibleItems(3);
        bar.setAutoNavigate(true);
        HBox people = new HBox(6.0);
        String[] colors = {"teal", "orange", "violet", "sky"};
        String[] initials = {"LW", "AK", "MR", "JS"};
        for (int i = 0; i < colors.length; i++) {
            Label avatar = text(initials[i], "avatar", colors[i]);
            avatar.setAlignment(Pos.CENTER);
            avatar.setMinSize(32.0, 32.0);
            avatar.setMaxSize(32.0, 32.0);
            people.getChildren().add(avatar);
        }
        Label title = text("", "page-title");
        title.textProperty().bind(lastItem(bar));
        VBox body = new VBox(10.0, title, text("4 members", "muted"), people);
        body.setPadding(new Insets(14.0, 16.0, 14.0, 16.0));
        return window(bar, body);
    }

    // ==================== Shared ====================

    private static Node window(RXBreadcrumbBar<String> bar, Node body) {
        HBox top = new HBox(bar);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("frame-bar");
        BorderPane window = new BorderPane(body);
        window.setTop(top);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, window);
    }
}
