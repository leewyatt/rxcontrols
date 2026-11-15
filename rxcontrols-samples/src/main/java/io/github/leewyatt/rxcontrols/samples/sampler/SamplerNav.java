package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The navigation column: the brand, a search field and the grouped entry list. The
 * {@link #currentProperty() current} entry is the page on display; picking a row sets
 * it, and setting it from elsewhere selects and reveals the row.
 */
final class SamplerNav extends VBox {

    private static final String RX_PREFIX = "RX";

    private final FilteredList<SamplerEntry> shown;
    private final RXListView<SamplerEntry> list;
    private final RXTextField search = new RXTextField();
    private final Label countLabel = new Label();
    private final ObjectProperty<SamplerEntry> current = new SimpleObjectProperty<>(this, "current");
    private final int total;
    private boolean syncing;

    SamplerNav(List<SamplerEntry> entries) {
        getStyleClass().add("sampler-nav");
        setPrefWidth(272.0);
        setMinWidth(Region.USE_PREF_SIZE);
        total = entries.size();
        shown = new FilteredList<>(FXCollections.observableArrayList(entries));

        list = new RXListView<>(shown);
        list.setFixedCellSize(30.0);
        list.setSectionHeaderHeight(34.0);
        list.setSectionKeyFactory(SamplerEntry::category);
        list.setSectionHeaderFactory(view -> new SectionCell());
        list.setStickySectionHeader(true);
        list.setCellFactory(view -> new EntryCell());
        list.setPlaceholder(new Label("No component matches"));
        VBox.setVgrow(list, Priority.ALWAYS);

        list.getSelectionModel().selectedItemProperty().addListener((obs, old, picked) -> {
            if (!syncing && picked != null) {
                current.set(picked);
            }
        });
        current.addListener((obs, old, entry) -> reveal(entry));
        // A filter that hides the current row drops its selection; clearing the filter
        // brings the row back, and with it the selection.
        shown.addListener((ListChangeListener<SamplerEntry>) change -> reveal(current.get()));

        getChildren().setAll(buildBrand(), buildSearch(), list, buildFooter());
        updateCount();
    }

    ObjectProperty<SamplerEntry> currentProperty() {
        return current;
    }

    void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    // ==================== Parts ====================

    private HBox buildBrand() {
        Label logo = new Label(RX_PREFIX);
        logo.getStyleClass().add("brand-logo");
        logo.setPrefSize(32.0, 32.0);
        logo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label name = new Label("RxControls");
        name.getStyleClass().add("brand-name");
        Label caption = new Label("Component sampler");
        caption.getStyleClass().add("brand-caption");
        HBox brand = new HBox(10.0, logo, new VBox(name, caption));
        brand.getStyleClass().add("brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    private HBox buildSearch() {
        Region glyph = new Region();
        glyph.getStyleClass().add("search-glyph");
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMouseTransparent(true);

        Region clearGlyph = new Region();
        clearGlyph.getStyleClass().add("clear-glyph");
        clearGlyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
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
        HBox.setHgrow(search, Priority.ALWAYS);

        HBox box = new HBox(search);
        box.getStyleClass().add("search-box");
        return box;
    }

    private HBox buildFooter() {
        Label platform = new Label("JavaFX 17+");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(countLabel, spacer, platform);
        footer.getStyleClass().add("nav-footer");
        return footer;
    }

    // ==================== Behavior ====================

    private void applyFilter(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        shown.setPredicate(entry -> entry.matches(query));
        updateCount();
    }

    private void updateCount() {
        int count = shown.size();
        countLabel.setText(count == total ? total + " pages" : count + " of " + total + " pages");
    }

    private void reveal(SamplerEntry entry) {
        if (entry == null || list.getSelectionModel().getSelectedItem() == entry || !shown.contains(entry)) {
            return;
        }
        syncing = true;
        try {
            list.getSelectionModel().select(entry);
        } finally {
            syncing = false;
        }
        list.scrollTo(entry, ScrollAlignment.NEAREST);
    }

    // ==================== Cells ====================

    static SVGPath glyphShape(SamplerCategory category) {
        SVGPath path = new SVGPath();
        path.setContent(category.glyph());
        return path;
    }

    // Sub-nodes are fields: updateItem runs on every re-bind, not once per row.
    private static final class EntryCell extends RXListCell<SamplerEntry> {

        private final Label prefix = new Label();
        private final Label rest = new Label();
        private final Label badge = new Label("New");
        private final HBox content = new HBox(prefix, rest);

        EntryCell() {
            getStyleClass().add("nav-cell");
            prefix.getStyleClass().add("prefix");
            rest.getStyleClass().add("name");
            badge.getStyleClass().add("new-badge");
            badge.setMinWidth(Region.USE_PREF_SIZE);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content.getChildren().addAll(spacer, badge);
            content.setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(SamplerEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String name = item.name();
            boolean rx = name.startsWith(RX_PREFIX);
            prefix.setText(rx ? RX_PREFIX : "");
            rest.setText(rx ? name.substring(RX_PREFIX.length()) : name);
            badge.setVisible(item.isNew());
            setGraphic(content);
        }
    }

    private static final class SectionCell extends RXListSectionCell {

        // A Region does not watch the content of its shape, so each glyph gets its own
        // path, created the first time this cell shows that category.
        private final Map<SamplerCategory, SVGPath> shapes = new EnumMap<>(SamplerCategory.class);
        private final Region glyph = new Region();
        private final Label title = new Label();
        private final Label count = new Label();
        private final HBox content;

        SectionCell() {
            getStyleClass().add("nav-section");
            glyph.getStyleClass().add("glyph");
            glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            title.getStyleClass().add("title");
            count.getStyleClass().add("count");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            content = new HBox(8.0, glyph, title, spacer, count);
            content.setAlignment(Pos.CENTER_LEFT);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(RXListSection item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || !(item.key() instanceof SamplerCategory category)) {
                setGraphic(null);
                return;
            }
            glyph.setShape(shapes.computeIfAbsent(category, SamplerNav::glyphShape));
            title.setText(category.title().toUpperCase(Locale.ROOT));
            count.setText(String.valueOf(item.itemCount()));
            setGraphic(content);
        }
    }
}
