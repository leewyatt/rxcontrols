package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSlider;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.DoubleConsumer;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXMasonryView page: one tall photo board under a toolbar that sets its width and
 * the selection mode, with an Options menu for the column width, the gaps, wide featured
 * cards, filling the width and animated re-packing.
 */
public final class RXMasonryViewSamples {

    private static final double MIN_BOARD_WIDTH = 280.0;
    private static final double MAX_BOARD_WIDTH = 940.0;
    private static final double BOARD_HEIGHT = 560.0;
    private static final double CARD_INSET = 4.0;
    private static final double CAPTION_HEIGHT = 50.0;

    private static final double[] RATIOS = {0.75, 1.2, 0.6, 1.0, 1.35, 0.8, 0.55, 1.1};
    private static final String[] PLACES = {
            "Quiet beach", "Old bridge", "River bend", "Blossom path", "Palm cove", "Red maples",
            "Green valley", "Pink canopy"};
    private static final String[] AUTHORS = {"Lee Wyatt", "Mia Chen", "Sam Ortega", "Ada Brooks"};

    private record Pin(String title, String author, int image, double ratio, boolean featured) {
        int span() {
            return featured ? 2 : 1;
        }
    }

    private RXMasonryViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A virtualized wall of cards in columns, each card as tall as its content.",
                List.of("Cards of different heights: photos, notes, pins, dashboard widgets.",
                        "For tiles that all share one size, RXTileView is simpler and supports section headers."),
                List.of(
                        SampleCard.wide("Photo board",
                                "cellHeightProvider sizes each card from its photo; drag the width to watch the columns "
                                        + "re-pack, and change the layout from the Options menu.",
                                board())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Photo board ====================

    private static Node board() {
        List<Image> images = scenery();
        ObservableList<Pin> pins = FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++) {
            pins.add(new Pin(PLACES[i % PLACES.length], AUTHORS[i % AUTHORS.length], i % images.size(),
                    RATIOS[i % RATIOS.length], i % 7 == 0));
        }
        RXMasonryView<Pin> board = new RXMasonryView<>(pins);
        board.getStyleClass().add("pins");
        board.setPrefHeight(BOARD_HEIGHT);
        board.setMaxWidth(Region.USE_PREF_SIZE);
        board.setColumnWidth(150.0);
        board.setHgap(10.0);
        board.setVgap(10.0);
        board.setSelectionMode(SelectionMode.MULTIPLE);
        board.setColumnSpanFactory(Pin::span);
        // A featured photo is cropped wide only while it spans two columns.
        board.setCellHeightProvider(context -> {
            boolean wide = context.item().featured() && board.getColumnSpanFactory() != null;
            return (context.cellWidth() - CARD_INSET * 2) * (wide ? 0.5 : context.item().ratio())
                    + CAPTION_HEIGHT + CARD_INSET * 2;
        });
        board.setCellFactory(view -> new PinCell(images));

        // The stage keeps the widest board's room, so dragging the width moves only the board.
        StackPane stage = new StackPane(board);
        stage.setAlignment(Pos.TOP_LEFT);
        stage.setPrefSize(MAX_BOARD_WIDTH, BOARD_HEIGHT);
        stage.setMinWidth(0.0);

        Label saved = text("", "hint");
        saved.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = board.getSelectionModel().getSelectedItems().size();
            return count == 0 ? "Click a photo to save it" : count + " saved to your board";
        }, board.getSelectionModel().getSelectedItems()));

        return new VBox(12.0, toolbar(board), stage, saved);
    }

    // The width and the selection mode stay in sight; the rest waits in the Options menu.
    private static Node toolbar(RXMasonryView<Pin> board) {
        RXSlider width = new RXSlider(MIN_BOARD_WIDTH, MAX_BOARD_WIDTH, 700.0);
        width.setPrefWidth(220.0);
        board.prefWidthProperty().bind(width.valueProperty());
        Label size = text("", "hint");
        size.setMinWidth(Region.USE_PREF_SIZE);
        size.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = board.getActualColumnCount();
            // The board's own width: on a narrow page it is smaller than the slider asks for.
            return Math.round(board.getWidth()) + " px · " + count + (count == 1 ? " column" : " columns");
        }, board.widthProperty(), board.actualColumnCountProperty()));

        RXSegmentedControl<SelectionMode> mode = new RXSegmentedControl<>(
                RXSegmentedItem.of(SelectionMode.SINGLE, "Single"),
                RXSegmentedItem.of(SelectionMode.MULTIPLE, "Multiple"));
        mode.setValue(board.getSelectionModel().getSelectionMode());
        mode.valueProperty().addListener((obs, old, picked) -> board.getSelectionModel().setSelectionMode(picked));

        Label caption = text("Width", "body");
        RXMenuButton options = options(board);
        // On a narrow card only the slider gives up room; the labels and buttons keep theirs.
        width.setMinWidth(80.0);
        for (Region region : List.of(caption, mode, options)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(10.0, caption, width, size, spacer(), mode, options);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static RXMenuButton options(RXMasonryView<Pin> board) {
        RXMenuButton options = new RXMenuButton("Options");
        options.getItems().add(RXMenuItem.header("Column width"));
        ToggleGroup columnWidths = new ToggleGroup();
        options.getItems().addAll(
                choice("Narrow", 120.0, board.getColumnWidth(), columnWidths, board::setColumnWidth),
                choice("Normal", 150.0, board.getColumnWidth(), columnWidths, board::setColumnWidth),
                choice("Wide", 200.0, board.getColumnWidth(), columnWidths, board::setColumnWidth));
        options.getItems().add(RXMenuItem.header("Gap"));
        ToggleGroup gaps = new ToggleGroup();
        DoubleConsumer setGap = gap -> {
            board.setHgap(gap);
            board.setVgap(gap);
        };
        options.getItems().addAll(
                choice("None", 0.0, board.getHgap(), gaps, setGap),
                choice("Small", 10.0, board.getHgap(), gaps, setGap),
                choice("Large", 20.0, board.getHgap(), gaps, setGap));

        BooleanProperty featured = new SimpleBooleanProperty(true);
        featured.addListener((obs, was, on) -> board.setColumnSpanFactory(on ? Pin::span : null));
        options.getItems().addAll(RXMenuItem.separator(), RXMenuItem.header("Cards"),
                RXMenuItem.checkbox("Featured photos span two columns", featured),
                RXMenuItem.checkbox("Columns fill the width", board.fillWidthProperty()),
                RXMenuItem.checkbox("Animate re-packing", board.animatedProperty()));
        return options;
    }

    private static RXMenuItem choice(String text, double value, double current, ToggleGroup group,
                                     DoubleConsumer apply) {
        RXMenuItem item = RXMenuItem.radio(text, group);
        item.setSelected(value == current);
        item.setOnAction(event -> apply.accept(value));
        return item;
    }

    private static final class PinCell extends RXMasonryCell<Pin> {

        private final List<Image> images;
        private final RXImageView image = new RXImageView();
        private final Label title = text("", "name");
        private final Label author = text("", "note");
        private final VBox card = new VBox(4.0, image, title, author);

        private PinCell(List<Image> images) {
            this.images = images;
            getStyleClass().add("pin-card");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            image.setImageRadius(8.0);
            image.setMinSize(0.0, 0.0);
            image.setPrefSize(0.0, 0.0);
            image.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(image, Priority.ALWAYS);
            card.prefWidthProperty().bind(widthProperty().subtract(CARD_INSET * 2));
            card.prefHeightProperty().bind(heightProperty().subtract(CARD_INSET * 2));
        }

        @Override
        protected void updateItem(Pin pin, boolean empty) {
            super.updateItem(pin, empty);
            if (empty || pin == null) {
                setGraphic(null);
            } else {
                image.setImage(images.get(pin.image()));
                title.setText(pin.featured() ? pin.title() + "  ·  Featured" : pin.title());
                author.setText("by " + pin.author());
                setGraphic(card);
            }
        }
    }
}
