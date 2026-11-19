package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXTileView page: one tall photo library grouped by album, under a toolbar that
 * sets its width and the selection mode, with an Options menu for the tile size, how a
 * row shares its spare width, the album headers and animated re-layout.
 */
public final class RXTileViewSamples {

    private static final double MIN_VIEW_WIDTH = 280.0;
    private static final double MAX_VIEW_WIDTH = 940.0;
    private static final double VIEW_HEIGHT = 560.0;
    private static final double TILE_INSET = 4.0;

    private static final String[] ALBUMS = {"Mountain trip", "Lake weekend", "Road to the coast", "Autumn walks"};
    private static final String[] PLACES = {"Ridge", "Valley", "Shore", "Pines", "Summit", "Meadow", "Harbor"};

    private record Photo(String album, String title, int image) {
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
                        SampleCard.wide("Photo library",
                                "Photos grouped by album; drag the width to watch the columns change, and set the "
                                        + "tile size, the row spacing and the headers from the Options menu.",
                                photos())),
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
        tiles.getStyleClass().add("photos");
        tiles.setPrefHeight(VIEW_HEIGHT);
        tiles.setMaxWidth(Region.USE_PREF_SIZE);
        tiles.setPrefTileWidth(150.0);
        tiles.setPrefTileHeight(124.0);
        tiles.setHgap(8.0);
        tiles.setVgap(8.0);
        tiles.setSectionKeyFactory(Photo::album);
        tiles.setSectionHeaderFactory(view -> new AlbumHeader());
        tiles.setCellFactory(view -> new PhotoCell(images));
        tiles.setSelectionMode(SelectionMode.MULTIPLE);
        tiles.getSelectionModel().selectIndices(1, 2);

        // The stage keeps the widest view's room, so dragging the width moves only the view.
        StackPane stage = new StackPane(tiles);
        stage.setAlignment(Pos.TOP_LEFT);
        stage.setPrefSize(MAX_VIEW_WIDTH, VIEW_HEIGHT);
        stage.setMinWidth(0.0);

        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int selected = tiles.getSelectionModel().getSelectedItems().size();
            return selected == 0 ? photos.size() + " photos" : selected + " of " + photos.size() + " selected";
        }, tiles.getSelectionModel().getSelectedItems()));
        Button clear = plain("Clear selection");
        clear.setOnAction(event -> tiles.getSelectionModel().clearSelection());
        clear.disableProperty().bind(Bindings.isEmpty(tiles.getSelectionModel().getSelectedItems()));
        FocusHandOff.onDisable(clear, tiles);
        HBox footer = new HBox(8.0, count, spacer(), clear);
        footer.setAlignment(Pos.CENTER_LEFT);
        // As wide as the view, so the button stays under the photos it clears.
        footer.maxWidthProperty().bind(tiles.widthProperty());

        return new VBox(12.0, toolbar(tiles), stage, footer);
    }

    // The width and the selection mode stay in sight; the rest waits in the Options menu.
    private static Node toolbar(RXTileView<Photo> tiles) {
        RXSlider width = new RXSlider(MIN_VIEW_WIDTH, MAX_VIEW_WIDTH, 700.0);
        width.setPrefWidth(220.0);
        width.setMinWidth(80.0);
        tiles.prefWidthProperty().bind(width.valueProperty());
        Label size = text("", "hint");
        size.setMinWidth(Region.USE_PREF_SIZE);
        size.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = tiles.getActualColumnCount();
            // The view's own width: on a narrow page it is smaller than the slider asks for.
            return Math.round(tiles.getWidth()) + " px · " + count + (count == 1 ? " column" : " columns");
        }, tiles.widthProperty(), tiles.actualColumnCountProperty()));

        RXSegmentedControl<SelectionMode> mode = new RXSegmentedControl<>(
                RXSegmentedItem.of(SelectionMode.SINGLE, "Single"),
                RXSegmentedItem.of(SelectionMode.MULTIPLE, "Multiple"));
        mode.setValue(tiles.getSelectionModel().getSelectionMode());
        mode.valueProperty().addListener((obs, old, picked) -> tiles.getSelectionModel().setSelectionMode(picked));

        Label caption = text("Width", "body");
        RXMenuButton options = options(tiles);
        // On a narrow card only the slider gives up room; the labels and buttons keep theirs.
        for (Region region : List.of(caption, mode, options)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(10.0, caption, width, size, spacer(), mode, options);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static RXMenuButton options(RXTileView<Photo> tiles) {
        RXMenuButton options = new RXMenuButton("Options");

        options.getItems().add(RXMenuItem.header("Tile size"));
        ToggleGroup sizes = new ToggleGroup();
        options.getItems().addAll(
                tileSize("Small", 116.0, 96.0, tiles, sizes),
                tileSize("Medium", 150.0, 124.0, tiles, sizes),
                tileSize("Large", 200.0, 160.0, tiles, sizes));

        options.getItems().add(RXMenuItem.header("Spare row width"));
        ToggleGroup justify = new ToggleGroup();
        options.getItems().addAll(
                justify("Leave at the end", ItemsJustify.START, tiles, justify),
                justify("Center the row", ItemsJustify.CENTER, tiles, justify),
                justify("Space evenly", ItemsJustify.SPACE_EVENLY, tiles, justify),
                justify("Stretch the tiles", ItemsJustify.STRETCH, tiles, justify));

        BooleanProperty grouped = new SimpleBooleanProperty(true);
        grouped.addListener((obs, was, on) -> tiles.setSectionKeyFactory(on ? Photo::album : null));
        RXMenuItem sticky = RXMenuItem.checkbox("Sticky album headers", tiles.stickySectionHeaderProperty());
        sticky.disableProperty().bind(grouped.not());
        options.getItems().addAll(RXMenuItem.separator(), RXMenuItem.header("Albums"),
                RXMenuItem.checkbox("Group by album", grouped), sticky,
                RXMenuItem.checkbox("Animate re-layout", tiles.animatedProperty()));
        return options;
    }

    private static RXMenuItem tileSize(String text, double width, double height, RXTileView<Photo> tiles,
                                       ToggleGroup group) {
        RXMenuItem item = RXMenuItem.radio(text, group);
        item.setSelected(width == tiles.getPrefTileWidth());
        item.setOnAction(event -> {
            tiles.setPrefTileWidth(width);
            tiles.setPrefTileHeight(height);
        });
        return item;
    }

    private static RXMenuItem justify(String text, ItemsJustify value, RXTileView<Photo> tiles, ToggleGroup group) {
        RXMenuItem item = RXMenuItem.radio(text, group);
        item.setSelected(value == tiles.getItemsJustify());
        item.setOnAction(event -> tiles.setItemsJustify(value));
        return item;
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
}
