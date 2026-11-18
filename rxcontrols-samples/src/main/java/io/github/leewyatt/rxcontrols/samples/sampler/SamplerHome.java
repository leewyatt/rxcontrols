package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The first page: what the library is, and a card per category that opens its first page.
 */
final class SamplerHome extends ScrollPane {

    private static final int SAMPLE_NAMES = 3;

    SamplerHome(List<SamplerEntry> entries, Consumer<SamplerEntry> open, Runnable openGallery) {
        getStyleClass().add("sampler-home");
        setFitToWidth(true);

        Map<SamplerCategory, List<SamplerEntry>> byCategory = new EnumMap<>(SamplerCategory.class);
        int controls = 0;
        for (SamplerEntry entry : entries) {
            if (entry.isHome()) {
                continue;
            }
            byCategory.computeIfAbsent(entry.category(), c -> new ArrayList<>()).add(entry);
            controls += entry.variants().size();
        }

        VBox column = new VBox(32.0,
                buildHero(byCategory, controls, open, openGallery),
                section("Browse by category", buildCategories(byCategory, open)));
        column.getStyleClass().add("home-column");
        column.setMaxWidth(1080.0);

        StackPane center = new StackPane(column);
        center.setAlignment(Pos.TOP_CENTER);
        center.getStyleClass().add("home-content");
        setContent(center);
    }

    private static VBox buildHero(Map<SamplerCategory, List<SamplerEntry>> byCategory, int controls,
                                  Consumer<SamplerEntry> open, Runnable openGallery) {
        Label title = new Label("RXControls");
        title.getStyleClass().add("home-title");
        Label lead = new Label("JavaFX controls, views, pickers, layouts and page animations, "
                + "styled for Modena and the AtlantaFX themes.");
        lead.getStyleClass().add("home-lead");
        lead.setWrapText(true);

        int pages = byCategory.values().stream().mapToInt(List::size).sum();
        Label stats = new Label(controls + " controls on " + pages + " pages in "
                + byCategory.size() + " categories");
        stats.getStyleClass().add("home-stats");

        Button browse = new Button("Browse components");
        browse.getStyleClass().add("primary-button");
        SamplerEntry first = byCategory.values().iterator().next().get(0);
        browse.setOnAction(event -> open.accept(first));
        Button gallery = new Button("Theme gallery");
        gallery.getStyleClass().add("secondary-button");
        gallery.setOnAction(event -> openGallery.run());
        HBox actions = new HBox(10.0, browse, gallery);

        VBox hero = new VBox(12.0, title, lead, stats, actions);
        VBox.setMargin(actions, new Insets(8.0, 0.0, 0.0, 0.0));
        hero.getStyleClass().add("home-hero");
        return hero;
    }

    private static VBox section(String title, Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("home-heading");
        return new VBox(12.0, heading, content);
    }

    private static RXTilePane buildCategories(Map<SamplerCategory, List<SamplerEntry>> byCategory,
                                              Consumer<SamplerEntry> open) {
        RXTilePane tiles = new RXTilePane();
        tiles.getStyleClass().add("category-tiles");
        tiles.setPrefTileWidth(300.0);
        // Two columns at most: ten categories fill five rows, with no one left alone.
        tiles.setMaxColumns(2);
        tiles.setHgap(12.0);
        tiles.setVgap(12.0);
        tiles.setItemsJustify(ItemsJustify.STRETCH);
        byCategory.forEach((category, list) -> tiles.getChildren().add(categoryCard(category, list, open)));
        return tiles;
    }

    private static Button categoryCard(SamplerCategory category, List<SamplerEntry> list,
                                       Consumer<SamplerEntry> open) {
        Region glyph = new Region();
        glyph.getStyleClass().add("glyph");
        glyph.setShape(SamplerNav.glyphShape(category));
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane badge = new StackPane(glyph);
        badge.getStyleClass().add("glyph-badge");
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label name = new Label(category.title());
        name.getStyleClass().add("card-title");
        // The card is the button; the chip only displays the count.
        RXChip count = new RXChip(list.size() + (list.size() == 1 ? " page" : " pages"));
        count.setMouseTransparent(true);
        count.setFocusTraversable(false);
        count.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8.0, name, spacer, count);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label sample = new Label(list.stream().limit(SAMPLE_NAMES).map(SamplerEntry::name)
                .collect(Collectors.joining(" · ")));
        sample.getStyleClass().add("card-sample");
        VBox text = new VBox(4.0, heading, sample);
        text.setMinWidth(0.0);

        HBox.setHgrow(text, Priority.ALWAYS);
        HBox graphic = new HBox(14.0, badge, text);
        graphic.setAlignment(Pos.TOP_LEFT);
        Button card = new Button();
        // A button lays its graphic out at the preferred width; the tile pane decides
        // the card's width, so following it lets the count sit at the trailing edge.
        graphic.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> card.getWidth() - card.getInsets().getLeft() - card.getInsets().getRight(),
                card.widthProperty(), card.insetsProperty()));
        card.getStyleClass().add("category-card");
        card.setGraphic(graphic);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setOnAction(event -> open.accept(list.get(0)));
        return card;
    }
}
