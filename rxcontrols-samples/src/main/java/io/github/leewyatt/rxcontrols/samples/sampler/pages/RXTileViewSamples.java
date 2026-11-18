package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.PALETTE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXTileView page: a photo library grouped by album with multiple selection, an
 * app launcher, ten thousand color swatches, product cards that stretch to fill the
 * row, and files grouped by type with headers that scroll away.
 */
public final class RXTileViewSamples {

    private static final double VIEW_WIDTH = 420.0;
    private static final double VIEW_HEIGHT = 300.0;
    private static final double TILE_INSET = 4.0;

    private static final String[] ALBUMS = {"Mountain trip", "Lake weekend", "Road to the coast", "Autumn walks"};
    private static final String[] PLACES = {"Ridge", "Valley", "Shore", "Pines", "Summit", "Meadow", "Harbor"};

    private static final String[][] APPS = {
            {"Mail", "sky"}, {"Calendar", "rose"}, {"Photos", "amber"}, {"Music", "pink"}, {"Maps", "lime"},
            {"Notes", "amber"}, {"Files", "indigo"}, {"Clock", "orange"}, {"Weather", "sky"}, {"Chat", "teal"},
            {"Camera", "violet"}, {"Books", "orange"}, {"Wallet", "lime"}, {"Health", "rose"}, {"News", "indigo"},
            {"Podcasts", "violet"}, {"Store", "sky"}, {"Settings", "teal"}};

    private static final String[][] PRODUCTS = {
            {"Desk lamp", "$39", "amber"}, {"Wool throw", "$54", "rose"}, {"Plant pot", "$18", "lime"},
            {"Wall clock", "$27", "sky"}, {"Tea set", "$46", "teal"}, {"Cushion", "$22", "violet"},
            {"Vase", "$31", "pink"}, {"Rug", "$89", "orange"}, {"Mirror", "$64", "indigo"}};

    private static final String[][] FILES = {
            {"Documents", "Budget 2026", "Meeting notes", "Contract", "Roadmap", "Invoice 0412", "Resume"},
            {"Images", "Logo", "Banner", "Team photo", "Screenshot", "Poster"},
            {"Music", "Morning run", "Focus", "Road trip", "Rainy day"},
            {"Archives", "Backup", "Old site", "Assets"}};

    private record Photo(String album, String title, int image) {
    }

    private record Product(String name, String price, String color) {
    }

    private record FileEntry(String type, String name) {
    }

    private RXTileViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A virtualized grid of equal tiles, with optional section headers and multiple selection.",
                List.of("Large collections shown as pictures or cards: photos, apps, products, swatches.",
                        "For items of different heights use RXMasonryView; for a single column use RXListView."),
                List.of(
                        SampleCard.canvas("Photo library",
                                "Photos grouped by album under sticky headers; MULTIPLE selection adds Shift/Ctrl clicks and drag-to-select.",
                                photos()),
                        SampleCard.large("App launcher",
                                "itemsJustify SPACE_EVENLY shares the spare row width; a double-click or Enter fires onAction.",
                                apps()),
                        SampleCard.large("Ten thousand swatches",
                                "Only the visible rows hold cells, so a palette of 10,000 colors scrolls smoothly.",
                                swatches()),
                        SampleCard.large("Stretch to fill",
                                "STRETCH widens every tile to fill the row, up to maxTileWidth.",
                                products()),
                        SampleCard.large("Files by type",
                                "With stickySectionHeader off, each header scrolls away with its tiles.",
                                files())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Photo library ====================

    private static Node photos() {
        List<Image> images = scenery();
        ObservableList<Photo> photos = FXCollections.observableArrayList();
        for (int album = 0; album < ALBUMS.length; album++) {
            int count = 9 + album * 3;
            for (int i = 0; i < count; i++) {
                photos.add(new Photo(ALBUMS[album], PLACES[(i + album) % PLACES.length] + " " + (i + 1),
                        (i + album) % images.size()));
            }
        }
        RXTileView<Photo> tiles = new RXTileView<>(photos);
        tiles.setPrefSize(VIEW_WIDTH, 340.0);
        tiles.setMinHeight(340.0);
        tiles.setPrefTileWidth(150.0);
        tiles.setPrefTileHeight(124.0);
        tiles.setHgap(8.0);
        tiles.setVgap(8.0);
        tiles.getStyleClass().add("photos");
        tiles.setSectionKeyFactory(Photo::album);
        tiles.setSectionHeaderFactory(view -> new AlbumHeader());
        tiles.setCellFactory(view -> new PhotoCell(images));
        tiles.setSelectionMode(SelectionMode.MULTIPLE);
        tiles.getSelectionModel().selectIndices(1, 2);

        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int selected = tiles.getSelectionModel().getSelectedItems().size();
            return selected == 0 ? photos.size() + " photos" : selected + " of " + photos.size() + " selected";
        }, tiles.getSelectionModel().getSelectedItems()));
        Button clear = plain("Clear selection");
        clear.setOnAction(event -> tiles.getSelectionModel().clearSelection());
        HBox header = new HBox(8.0, text("Library", "heading"), spacer(), count, clear);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(tiles, Priority.ALWAYS);
        return new VBox(12.0, header, tiles);
    }

    private static final class PhotoCell extends RXTileCell<Photo> {

        private final List<Image> images;
        private final RXImageView image = new RXImageView();
        private final Label title = text("", "caption");
        private final StackPane frame = new StackPane(image, title);

        private PhotoCell(List<Image> images) {
            this.images = images;
            getStyleClass().add("photo-tile");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            image.setImageRadius(6.0);
            image.setMinSize(0.0, 0.0);
            StackPane.setAlignment(title, Pos.BOTTOM_LEFT);
            frame.prefWidthProperty().bind(widthProperty().subtract(TILE_INSET * 2));
            frame.prefHeightProperty().bind(heightProperty().subtract(TILE_INSET * 2));
        }

        @Override
        protected void updateItem(Photo photo, boolean empty) {
            super.updateItem(photo, empty);
            if (empty || photo == null) {
                setGraphic(null);
            } else {
                image.setImage(images.get(photo.image()));
                title.setText(photo.title());
                setGraphic(frame);
            }
        }
    }

    private static final class AlbumHeader extends RXTileSectionCell {

        @Override
        protected void updateItem(RXTileSection section, boolean empty) {
            super.updateItem(section, empty);
            setText(empty || section == null ? null : section.key() + "  ·  " + section.itemCount() + " photos");
        }
    }

    // ==================== App launcher ====================

    private static Node apps() {
        ObservableList<String[]> apps = FXCollections.observableArrayList(APPS);
        RXTileView<String[]> tiles = sized(new RXTileView<>(apps), VIEW_WIDTH, VIEW_HEIGHT);
        tiles.setPrefTileWidth(84.0);
        tiles.setPrefTileHeight(92.0);
        tiles.setItemsJustify(ItemsJustify.SPACE_EVENLY);
        tiles.setCellFactory(view -> new AppCell());

        Label status = text("Double-click an app to open it", "hint");
        tiles.setOnAction(event -> status.setText("Opening " + event.getItem()[0] + "…"));
        return column(tiles, status);
    }

    private static final class AppCell extends RXTileCell<String[]> {

        private final Label glyph = new Label();
        private final Label name = text("", "app-name");
        private final VBox box = new VBox(6.0, glyph, name);

        private AppCell() {
            getStyleClass().add("app-tile");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            glyph.getStyleClass().add("app-icon");
            glyph.setAlignment(Pos.CENTER);
            glyph.setMinSize(48.0, 48.0);
            glyph.setPrefSize(48.0, 48.0);
            box.setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(String[] app, boolean empty) {
            super.updateItem(app, empty);
            if (empty || app == null) {
                setGraphic(null);
            } else {
                glyph.setText(app[0].substring(0, 1));
                glyph.getStyleClass().removeAll(PALETTE);
                glyph.getStyleClass().add(app[1]);
                name.setText(app[0]);
                setGraphic(box);
            }
        }
    }

    // ==================== Ten thousand swatches ====================

    private static Node swatches() {
        final int count = 10_000;
        ObservableList<Color> colors = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            double hue = (i * 7.0) % 360.0;
            double saturation = 0.35 + (i / 360 % 6) * 0.12;
            double brightness = 0.95 - (i / 2160 % 5) * 0.12;
            colors.add(Color.hsb(hue, Math.min(saturation, 1.0), brightness));
        }
        RXTileView<Color> tiles = sized(new RXTileView<>(colors), VIEW_WIDTH, VIEW_HEIGHT);
        tiles.setPrefTileWidth(44.0);
        tiles.setPrefTileHeight(44.0);
        tiles.setHgap(6.0);
        tiles.setVgap(6.0);
        tiles.setCellFactory(view -> new SwatchCell());
        tiles.getSelectionModel().select(19);

        Region chip = new Region();
        chip.getStyleClass().add("chip");
        chip.setMinSize(18.0, 18.0);
        chip.setPrefSize(18.0, 18.0);
        Label hex = text("", "value");
        tiles.getSelectionModel().selectedItemProperty().addListener((obs, old, color) -> showColor(chip, hex, color));
        showColor(chip, hex, tiles.getSelectionModel().getSelectedItem());
        HBox summary = new HBox(8.0, chip, hex);
        summary.setAlignment(Pos.CENTER_LEFT);
        return column(tiles, summary);
    }

    private static void showColor(Region chip, Label hex, Color color) {
        if (color == null) {
            chip.setStyle(null);
            hex.setText("No color");
            return;
        }
        String web = String.format("#%02x%02x%02x", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
        chip.setStyle("-fx-background-color: " + web + ";");
        hex.setText(web.toUpperCase(Locale.ROOT));
    }

    private static final class SwatchCell extends RXTileCell<Color> {

        private final Region swatch = new Region();

        private SwatchCell() {
            getStyleClass().add("swatch-tile");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            swatch.getStyleClass().add("swatch");
            swatch.prefWidthProperty().bind(widthProperty().subtract(TILE_INSET * 2));
            swatch.prefHeightProperty().bind(heightProperty().subtract(TILE_INSET * 2));
        }

        @Override
        protected void updateItem(Color color, boolean empty) {
            super.updateItem(color, empty);
            if (empty || color == null) {
                setGraphic(null);
            } else {
                swatch.setBackground(new Background(new BackgroundFill(color, new CornerRadii(6.0), null)));
                setGraphic(swatch);
            }
        }
    }

    // ==================== Stretch to fill ====================

    private static Node products() {
        ObservableList<Product> products = FXCollections.observableArrayList();
        for (String[] product : PRODUCTS) {
            products.add(new Product(product[0], product[1], product[2]));
        }
        RXTileView<Product> tiles = sized(new RXTileView<>(products), VIEW_WIDTH, VIEW_HEIGHT);
        tiles.setPrefTileWidth(110.0);
        tiles.setPrefTileHeight(130.0);
        tiles.setItemsJustify(ItemsJustify.STRETCH);
        tiles.setMaxTileWidth(160.0);
        tiles.setCellFactory(view -> new ProductCell());
        return tiles;
    }

    private static final class ProductCell extends RXTileCell<Product> {

        private final Region picture = new Region();
        private final Label name = text("", "name");
        private final Label price = text("", "note");
        private final VBox card = new VBox(4.0, picture, name, price);

        private ProductCell() {
            getStyleClass().add("product-tile");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            picture.getStyleClass().add("picture");
            VBox.setVgrow(picture, Priority.ALWAYS);
            card.getStyleClass().add("product");
            card.prefWidthProperty().bind(widthProperty().subtract(TILE_INSET * 2));
            card.prefHeightProperty().bind(heightProperty().subtract(TILE_INSET * 2));
        }

        @Override
        protected void updateItem(Product product, boolean empty) {
            super.updateItem(product, empty);
            if (empty || product == null) {
                setGraphic(null);
            } else {
                picture.getStyleClass().removeAll(PALETTE);
                picture.getStyleClass().add(product.color());
                name.setText(product.name());
                price.setText(product.price());
                setGraphic(card);
            }
        }
    }

    // ==================== Files by type ====================

    private static Node files() {
        ObservableList<FileEntry> files = FXCollections.observableArrayList();
        for (String[] group : FILES) {
            for (int i = 1; i < group.length; i++) {
                files.add(new FileEntry(group[0], group[i]));
            }
        }
        RXTileView<FileEntry> tiles = sized(new RXTileView<>(files), VIEW_WIDTH, VIEW_HEIGHT);
        tiles.setPrefTileWidth(92.0);
        tiles.setPrefTileHeight(84.0);
        tiles.setSectionKeyFactory(FileEntry::type);
        tiles.setStickySectionHeader(false);
        tiles.setCellFactory(view -> new FileCell());
        return tiles;
    }

    private static final class FileCell extends RXTileCell<FileEntry> {

        private final Region doc = icon("doc-icon");
        private final Region picture = icon("image-icon");
        private final Region note = icon("music-icon");
        private final Region box = icon("archive-icon");
        private final StackPane glyph = new StackPane();
        private final Label name = text("", "app-name");
        private final VBox content = new VBox(6.0, glyph, name);

        private FileCell() {
            getStyleClass().add("file-tile");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            content.setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(FileEntry file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setGraphic(null);
            } else {
                glyph.getChildren().setAll(switch (file.type()) {
                    case "Images" -> picture;
                    case "Music" -> note;
                    case "Archives" -> box;
                    default -> doc;
                });
                name.setText(file.name());
                setGraphic(content);
            }
        }
    }

    // ==================== Helpers ====================

    private static Node column(Node... nodes) {
        VBox column = new VBox(12.0, nodes);
        column.setPrefWidth(VIEW_WIDTH);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
