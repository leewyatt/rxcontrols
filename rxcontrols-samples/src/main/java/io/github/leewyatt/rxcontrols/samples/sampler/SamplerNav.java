package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarItemBase;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The navigation sidebar: the brand, the collapse toggle and a search field in its header,
 * a group per category holding that category's pages, and the page count in its footer.
 * Collapsed, it is a rail of the category glyphs, each opening its pages. The
 * {@link #currentProperty() current} entry is the page on display; picking a page sets it,
 * and setting it from elsewhere selects and reveals the page.
 */
final class SamplerNav extends RXSidebar {

    private final List<SamplerEntry> entries;
    private final Map<SamplerEntry, RXSidebarItem> pages = new HashMap<>();
    private final RXTextField search = new RXTextField();
    private final Label countLabel = new Label();
    private final Button collapse = new Button();
    private final HBox brand = new HBox(10.0);
    private final VBox header = new VBox(14.0);
    private final HBox footer = buildFooter();
    private final ObjectProperty<SamplerEntry> current = new SimpleObjectProperty<>(this, "current");

    SamplerNav(List<SamplerEntry> entries) {
        this.entries = entries;
        getStyleClass().add("sampler-nav");
        setExpandedWidth(272.0);
        // Shorter than the default: the page beside the rail relays out on every frame.
        setAnimationDuration(Duration.millis(150.0));
        getItems().setAll(buildItems());
        buildHeader();
        setHeader(header);
        setFooter(footer);
        setPlaceholder(new Label("No component matches"));

        selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && item.getUserData() instanceof SamplerEntry entry) {
                current.set(entry);
            }
        });
        current.addListener((obs, old, entry) -> selectItem(pages.get(entry)));
        modeProperty().addListener((obs, old, mode) -> onModeChanged(mode == Mode.MINI));
        updateCount("");
    }

    ObjectProperty<SamplerEntry> currentProperty() {
        return current;
    }

    void focusSearch() {
        setMode(Mode.EXPANDED);
        search.requestFocus();
        search.selectAll();
    }

    // ==================== Parts ====================

    private List<RXSidebarItemBase> buildItems() {
        List<RXSidebarItemBase> roots = new ArrayList<>();
        Map<SamplerCategory, RXSidebarItem> groups = new EnumMap<>(SamplerCategory.class);
        for (SamplerEntry entry : entries) {
            RXSidebarItem page = RXSidebarItem.of(entry.name());
            page.setUserData(entry);
            pages.put(entry, page);
            if (entry.isHome()) {
                // The home page is all there is to its category, so it needs no group.
                page.setGraphic(glyph(entry.category()));
                roots.add(page);
                continue;
            }
            RXSidebarItem group = groups.get(entry.category());
            if (group == null) {
                group = RXSidebarItem.group(entry.category().title(), glyph(entry.category()));
                groups.put(entry.category(), group);
                roots.add(group);
            }
            group.getItems().add(page);
        }
        for (RXSidebarItem group : groups.values()) {
            group.setTrailing(new Label(String.valueOf(group.getItems().size())));
        }
        return roots;
    }

    private void buildHeader() {
        Label logo = new Label("RX");
        logo.getStyleClass().add("brand-logo");
        logo.setPrefSize(32.0, 32.0);
        logo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        hideInRail(logo);
        Label name = new Label("RXControls");
        name.getStyleClass().add("brand-name");
        Label caption = new Label("Component sampler");
        caption.getStyleClass().add("brand-caption");
        VBox brandText = new VBox(name, caption);
        hideInRail(brandText);
        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        hideInRail(brandSpacer);

        Region menu = new Region();
        menu.getStyleClass().addAll("glyph", "menu-glyph");
        RXSizing.lockToPrefSize(menu);
        menu.setMouseTransparent(true);
        collapse.getStyleClass().add("collapse-button");
        collapse.setGraphic(menu);
        collapse.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        collapse.setAccessibleText("Collapse or expand the sidebar");
        collapse.setOnAction(event -> setMode(getMode() == Mode.MINI ? Mode.EXPANDED : Mode.MINI));

        brand.getChildren().setAll(logo, brandText, brandSpacer, collapse);
        brand.getStyleClass().add("brand");
        brand.setAlignment(Pos.CENTER_LEFT);

        Region glyph = new Region();
        glyph.getStyleClass().add("search-glyph");
        RXSizing.lockToPrefSize(glyph);
        glyph.setMouseTransparent(true);

        Region clearGlyph = new Region();
        clearGlyph.getStyleClass().add("clear-glyph");
        RXSizing.lockToPrefSize(clearGlyph);
        clearGlyph.setMouseTransparent(true);
        Button clear = new Button();
        clear.getStyleClass().add("clear-button");
        clear.setGraphic(clearGlyph);
        clear.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        clear.setFocusTraversable(false);
        clear.visibleProperty().bind(search.textProperty().isNotEmpty());
        clear.setOnAction(event -> search.clear());

        search.setPromptText("Search components");
        search.setLeading(glyph);
        search.setTrailing(clear);
        search.textProperty().addListener((obs, old, text) -> applyFilter(text));
        search.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && !search.getText().isEmpty()) {
                search.clear();
                event.consume();
            }
        });
        hideInRail(search);
        header.getChildren().setAll(brand, search);
    }

    private HBox buildFooter() {
        Label platform = new Label("JavaFX 17+");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(countLabel, spacer, platform);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void hideInRail(Node node) {
        node.visibleProperty().bind(modeProperty().isNotEqualTo(Mode.MINI));
        node.managedProperty().bind(node.visibleProperty());
    }

    // ==================== Behavior ====================

    // The rail keeps only the toggle of the brand row, centered over the glyphs; it is
    // too narrow for the footer and for a field to show or clear a query.
    private void onModeChanged(boolean rail) {
        brand.setAlignment(rail ? Pos.CENTER : Pos.CENTER_LEFT);
        if (rail) {
            search.clear();
        }
        setFooter(rail ? null : footer);
    }

    // A group is not asked for a match of its own: a query naming a category matches
    // every page of it.
    private void applyFilter(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        setFilter(query.isEmpty() ? null
                : item -> item.getUserData() instanceof SamplerEntry entry && entry.matches(query));
        updateCount(query);
    }

    private void updateCount(String query) {
        int total = entries.size();
        long count = entries.stream().filter(entry -> entry.matches(query)).count();
        countLabel.setText(count == total ? total + " pages" : count + " of " + total + " pages");
    }

    // ==================== Glyphs ====================

    static SVGPath glyphShape(SamplerCategory category) {
        SVGPath path = new SVGPath();
        path.setContent(category.glyph());
        return path;
    }

    private static Region glyph(SamplerCategory category) {
        Region glyph = new Region();
        glyph.getStyleClass().add("graphic");
        RXSizing.lockToPrefSize(glyph);
        glyph.setShape(glyphShape(category));
        return glyph;
    }
}
