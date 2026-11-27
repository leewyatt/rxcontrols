package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXMenuList;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Supplier;

/**
 * Manual check of label alignment in menus that mix item kinds. Each row builds the same
 * menu three times: an inline {@link RXMenuList} (always visible), an {@link RXMenuButton}
 * (the popup), and a native JavaFX {@link MenuButton} for comparison. Like the native menu,
 * the RX menus put the selection marks and the icons in two list-wide columns, so every label
 * in a menu starts at the same x when plain commands sit next to radio / checkbox items or
 * items with icons next to items without.
 */
public class RXMenuMixedItemsVerification extends Application {

    private static final String COPY_ICON = "M4 0h10v12H4zM0 4h2v10h10v2H0z";
    private static final String PASTE_ICON = "M3 2h3V0h4v2h3v14H3z";
    private static final String TRASH_ICON = "M2 3h12v2H2zM5 0h6v2H5zM3 6h10l-1 10H4z";

    @Override
    public void start(Stage primaryStage) {
        Label steps = new Label("""
                In every menu below, all labels should start at the same x, in the inline list, in the \
                RXMenuButton popup and in the native JavaFX menu (right column) alike.
                1. Row 1, plain commands + radio items: "Refresh" / "Select all" line up with "Newest" / \
                "Oldest"; the plain rows leave the radio column empty.
                2. Row 2, items with and without icons: "Paste as plain text" lines up with "Copy" / "Paste" / \
                "Delete"; it leaves the icon column empty.
                3. Row 3, icon commands + checkbox items: as in the native menu, checks and icons get a column \
                each, so all four labels start after both columns.
                4. Row 4, radio items only (the RXMenuButton page's case): only the radio column is reserved. \
                The "Sort by" header lines up with the radio column's left edge, not the labels (unchanged).
                5. Pick an AtlantaFX theme and look again; the labels stay in line.""");
        steps.setWrapText(true);

        ComboBox<ShowcaseThemes.ThemeChoice> themes = new ComboBox<>();
        ShowcaseThemes.bindPicker(themes);

        GridPane grid = new GridPane();
        grid.setHgap(32.0);
        grid.setVgap(24.0);
        grid.addRow(0, header("Case"), header("RXMenuList (inline)"), header("RXMenuButton"),
                header("JavaFX MenuButton"));
        addCase(grid, 1, "1. Plain + radio", this::plainAndRadio, this::nativePlainAndRadio);
        addCase(grid, 2, "2. Icon + no icon", this::iconAndPlain, this::nativeIconAndPlain);
        addCase(grid, 3, "3. Icon + checkbox", this::iconAndCheckbox, this::nativeIconAndCheckbox);
        addCase(grid, 4, "4. Radio only", this::radioOnly, this::nativeRadioOnly);

        VBox content = new VBox(16.0, steps, new HBox(10.0, new Label("Theme"), themes), grid);
        content.setPadding(new Insets(16.0));

        ScrollPane scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        // A plain root carries the theme's background; a ScrollPane root would stay transparent.
        Scene scene = new Scene(new BorderPane(scroller), 900.0, 920.0);
        scene.getStylesheets().add(RXShowcaseApplication.shellStylesheet());
        ShowcaseThemes.register(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMenuList: label alignment with mixed items");
        primaryStage.show();
    }

    // ==================== Cases ====================

    private List<RXMenuItem> plainAndRadio() {
        ToggleGroup group = new ToggleGroup();
        RXMenuItem newest = RXMenuItem.radio("Newest", group);
        newest.setSelected(true);
        return List.of(RXMenuItem.of("Refresh"), RXMenuItem.of("Select all"), RXMenuItem.separator(),
                newest, RXMenuItem.radio("Oldest", group));
    }

    private List<MenuItem> nativePlainAndRadio() {
        ToggleGroup group = new ToggleGroup();
        RadioMenuItem newest = new RadioMenuItem("Newest");
        newest.setToggleGroup(group);
        newest.setSelected(true);
        RadioMenuItem oldest = new RadioMenuItem("Oldest");
        oldest.setToggleGroup(group);
        return List.of(new MenuItem("Refresh"), new MenuItem("Select all"), new SeparatorMenuItem(),
                newest, oldest);
    }

    private List<RXMenuItem> iconAndPlain() {
        return List.of(RXMenuItem.of("Copy", icon(COPY_ICON)), RXMenuItem.of("Paste", icon(PASTE_ICON)),
                RXMenuItem.of("Paste as plain text"), RXMenuItem.separator(),
                RXMenuItem.of("Delete", icon(TRASH_ICON)));
    }

    private List<MenuItem> nativeIconAndPlain() {
        return List.of(new MenuItem("Copy", icon(COPY_ICON)), new MenuItem("Paste", icon(PASTE_ICON)),
                new MenuItem("Paste as plain text"), new SeparatorMenuItem(),
                new MenuItem("Delete", icon(TRASH_ICON)));
    }

    private List<RXMenuItem> iconAndCheckbox() {
        return List.of(RXMenuItem.of("Copy", icon(COPY_ICON)), RXMenuItem.of("Paste", icon(PASTE_ICON)),
                RXMenuItem.separator(), RXMenuItem.checkbox("Word wrap", new SimpleBooleanProperty(true)),
                RXMenuItem.checkbox("Line numbers", new SimpleBooleanProperty(false)));
    }

    private List<MenuItem> nativeIconAndCheckbox() {
        CheckMenuItem wrap = new CheckMenuItem("Word wrap");
        wrap.setSelected(true);
        return List.of(new MenuItem("Copy", icon(COPY_ICON)), new MenuItem("Paste", icon(PASTE_ICON)),
                new SeparatorMenuItem(), wrap, new CheckMenuItem("Line numbers"));
    }

    private List<RXMenuItem> radioOnly() {
        ToggleGroup group = new ToggleGroup();
        RXMenuItem newest = RXMenuItem.radio("Newest", group);
        newest.setSelected(true);
        return List.of(RXMenuItem.header("Sort by"), newest, RXMenuItem.radio("Oldest", group),
                RXMenuItem.radio("Name", group));
    }

    private List<MenuItem> nativeRadioOnly() {
        ToggleGroup group = new ToggleGroup();
        RadioMenuItem newest = new RadioMenuItem("Newest");
        newest.setSelected(true);
        RadioMenuItem oldest = new RadioMenuItem("Oldest");
        RadioMenuItem name = new RadioMenuItem("Name");
        for (RadioMenuItem item : List.of(newest, oldest, name)) {
            item.setToggleGroup(group);
        }
        // JavaFX menus have no header item.
        return List.of(newest, oldest, name);
    }

    // ==================== Helpers ====================

    // Each column gets its own items: an item, and its graphic node, can sit in one menu only.
    private void addCase(GridPane grid, int row, String title, Supplier<List<RXMenuItem>> rxItems,
                         Supplier<List<MenuItem>> nativeItems) {
        RXMenuList inline = new RXMenuList();
        inline.getItems().setAll(rxItems.get());

        RXMenuButton rxButton = new RXMenuButton("Open");
        rxButton.getItems().setAll(rxItems.get());

        MenuButton nativeButton = new MenuButton("Open");
        nativeButton.getItems().setAll(nativeItems.get());

        Label label = new Label(title);
        GridPane.setValignment(label, VPos.TOP);
        grid.addRow(row, label, inline, top(rxButton), top(nativeButton));
    }

    private static Node top(Node node) {
        VBox box = new VBox(node);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static RXIcon icon(String shape) {
        RXIcon icon = new RXIcon();
        icon.setPrefSize(16.0, 16.0);
        // A fixed gray: native popups under AtlantaFX cannot resolve Modena's text color lookups.
        icon.setStyle("-fx-shape: \"" + shape + "\"; -fx-background-color: gray;");
        return icon;
    }

    /**
     * Launches the check.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
