package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.PALETTE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXMasonryView page: a photo board with cards of mixed heights and wide featured
 * cards, sticky notes measured from their text, recipes in two fixed columns, and a
 * dashboard of widgets that span several columns.
 */
public final class RXMasonryViewSamples {

    private static final double VIEW_WIDTH = 420.0;
    private static final double VIEW_HEIGHT = 320.0;
    private static final double CARD_INSET = 4.0;
    private static final double CAPTION_HEIGHT = 50.0;

    private static final double[] RATIOS = {0.75, 1.2, 0.6, 1.0, 1.35, 0.8, 0.55, 1.1};
    private static final String[] PLACES = {
            "Quiet beach", "Old bridge", "River bend", "Blossom path", "Palm cove", "Red maples",
            "Green valley", "Pink canopy"};
    private static final String[] AUTHORS = {"Lee Wyatt", "Mia Chen", "Sam Ortega", "Ada Brooks"};

    private static final String[] NOTES = {
            "Buy milk",
            "Call the landlord about the heater before Friday.",
            "Ideas for the team offsite: a cooking class, a short hike by the lake, or a board game evening.",
            "Water the plants",
            "Book flights for the June conference and ask whether the hotel has a shuttle from the airport.",
            "Return the library books",
            "Birthday gift for Sam: the green notebook he liked, or tickets to the jazz night.",
            "Fix the squeaky door",
            "Draft the newsletter: three short stories, one photo, and the dates of the next two meetups.",
            "Renew passport",
            "Learn ten new words a day",
            "Plan the garden: tomatoes along the fence, herbs by the kitchen window, and a small bench under the tree."};

    private static final String[][] RECIPES = {
            {"Lemon pasta", "20 min", "Pasta, lemon, butter, parmesan and black pepper."},
            {"Tomato soup", "35 min", "Roasted tomatoes and garlic, blended smooth."},
            {"Green curry", "40 min", "Coconut milk, green paste, vegetables, basil and lime, served with rice."},
            {"Pancakes", "15 min", "Fluffy and quick."},
            {"Shakshuka", "30 min", "Eggs poached in a spiced pepper and tomato sauce."},
            {"Banana bread", "70 min", "Ripe bananas, brown sugar, walnuts and a pinch of cinnamon, baked until golden."},
            {"Caesar salad", "15 min", "Crisp lettuce, croutons and a garlic dressing."},
            {"Miso ramen", "45 min", "Rich broth with noodles, egg, corn and spring onion."}};

    private static final String[][] WIDGETS = {
            {"Revenue", "$48,210", "Up 12% from last month", "2", "teal"},
            {"Orders", "1,284", "38 waiting to ship", "1", "sky"},
            {"Refunds", "17", "Down 4", "1", "rose"},
            {"Visitors", "92,400", "Most come from search", "1", "violet"},
            {"New customers", "326", "Best week this year", "2", "amber"},
            {"Avg. order", "$37.50", "Steady", "1", "indigo"},
            {"Support tickets", "64 open", "Median reply in 2 h", "2", "orange"},
            {"Stock alerts", "9 items", "Reorder before Monday", "1", "pink"},
            {"Uptime", "99.98%", "Last incident 21 days ago", "1", "lime"}};

    private record Pin(String title, String author, int image, double ratio, boolean featured) {
    }

    private record Recipe(String name, String time, String text) {
    }

    private record Widget(String title, String value, String note, int span, String color) {
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
                        SampleCard.canvas("Photo board",
                                "cellHeightProvider gives each card its height from the photo's shape; featured photos span two columns.",
                                board()),
                        SampleCard.large("Sticky notes",
                                "Without a height provider each note is measured once it is shown, and the columns re-pack.",
                                notes()),
                        SampleCard.large("Two fixed columns",
                                "columnCount 2 keeps two columns at any width; a double-click or Enter fires onAction.",
                                recipes()),
                        SampleCard.canvas("Dashboard",
                                "columnSpanFactory lets wide widgets take two of the four columns.",
                                dashboard())),
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
        board.setPrefSize(VIEW_WIDTH, 360.0);
        board.setMinHeight(360.0);
        board.getStyleClass().add("pins");
        board.setColumnWidth(160.0);
        board.setHgap(10.0);
        board.setVgap(10.0);
        board.setSelectionMode(SelectionMode.MULTIPLE);
        board.setColumnSpanFactory(pin -> pin.featured() ? 2 : 1);
        board.setCellHeightProvider(context ->
                (context.cellWidth() - CARD_INSET * 2) * (context.item().featured() ? 0.5 : context.item().ratio())
                        + CAPTION_HEIGHT + CARD_INSET * 2);
        board.setCellFactory(view -> new PinCell(images));

        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int selected = board.getSelectionModel().getSelectedItems().size();
            return selected == 0 ? "Click to pick, Ctrl or Shift for more" : selected + " saved to your board";
        }, board.getSelectionModel().getSelectedItems()));
        HBox header = new HBox(8.0, text("Ideas for the weekend", "heading"), spacer(), count);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(board, Priority.ALWAYS);
        return new VBox(12.0, header, board);
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

    // ==================== Sticky notes ====================

    private static Node notes() {
        RXMasonryView<String> wall = sized(new RXMasonryView<>(FXCollections.observableArrayList(NOTES)),
                VIEW_WIDTH, VIEW_HEIGHT);
        wall.setColumnWidth(120.0);
        wall.setEstimatedCellHeight(90.0);
        wall.setCellFactory(view -> new NoteCell());
        return wall;
    }

    private static final class NoteCell extends RXMasonryCell<String> {

        private NoteCell() {
            getStyleClass().add("note-card");
            setWrapText(true);
            setAlignment(Pos.TOP_LEFT);
        }

        @Override
        protected void updateItem(String note, boolean empty) {
            super.updateItem(note, empty);
            getStyleClass().removeAll(PALETTE);
            if (empty || note == null) {
                setText(null);
            } else {
                setText(note);
                getStyleClass().add(new String[]{"amber", "lime", "sky", "pink", "violet"}[getIndex() % 5]);
            }
        }
    }

    // ==================== Two fixed columns ====================

    private static Node recipes() {
        ObservableList<Recipe> recipes = FXCollections.observableArrayList();
        for (String[] recipe : RECIPES) {
            recipes.add(new Recipe(recipe[0], recipe[1], recipe[2]));
        }
        RXMasonryView<Recipe> view = sized(new RXMasonryView<>(recipes), VIEW_WIDTH, VIEW_HEIGHT);
        view.setColumnCount(2);
        // About 6.3 px per character at 12 px, 16 px per line, plus the name row and padding.
        view.setCellHeightProvider(context -> {
            double charsPerLine = Math.max(1.0, (context.cellWidth() - 40.0) / 6.3);
            return 58.0 + Math.ceil(context.item().text().length() / charsPerLine) * 16.0;
        });
        view.setCellFactory(list -> new RecipeCell());

        Label status = text("Double-click a recipe to cook it", "hint");
        view.setOnAction(event -> status.setText("Cooking " + event.getItem().name() + " · "
                + event.getItem().time()));
        VBox column = new VBox(12.0, view, status);
        column.setPrefWidth(VIEW_WIDTH);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }

    private static final class RecipeCell extends RXMasonryCell<Recipe> {

        private final Label name = text("", "name");
        private final Label time = text("", "time");
        private final Label body = text("", "note");
        private final VBox card = new VBox(4.0, new HBox(6.0, name, spacer(), time), body);

        private RecipeCell() {
            getStyleClass().add("recipe-card");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            body.setWrapText(true);
            card.getStyleClass().add("recipe");
            card.prefWidthProperty().bind(widthProperty().subtract(CARD_INSET * 2));
            card.prefHeightProperty().bind(heightProperty().subtract(CARD_INSET * 2));
        }

        @Override
        protected void updateItem(Recipe recipe, boolean empty) {
            super.updateItem(recipe, empty);
            if (empty || recipe == null) {
                setGraphic(null);
            } else {
                name.setText(recipe.name());
                time.setText(recipe.time());
                body.setText(recipe.text());
                setGraphic(card);
            }
        }
    }

    // ==================== Dashboard ====================

    private static Node dashboard() {
        ObservableList<Widget> widgets = FXCollections.observableArrayList();
        for (String[] widget : WIDGETS) {
            widgets.add(new Widget(widget[0], widget[1], widget[2], Integer.parseInt(widget[3]), widget[4]));
        }
        RXMasonryView<Widget> view = new RXMasonryView<>(widgets);
        view.setPrefSize(VIEW_WIDTH, 340.0);
        view.setMinHeight(340.0);
        view.setColumnCount(4);
        view.setHgap(10.0);
        view.setVgap(10.0);
        view.setColumnSpanFactory(Widget::span);
        view.setCellHeightProvider(context -> context.index() % 3 == 0 ? 150.0 : 110.0);
        view.setCellFactory(list -> new WidgetCell());
        return view;
    }

    private static final class WidgetCell extends RXMasonryCell<Widget> {

        private final Region bar = new Region();
        private final Label title = text("", "note");
        private final Label value = text("", "figure");
        private final Label note = text("", "note");
        private final VBox card = new VBox(4.0, bar, title, value, note);

        private WidgetCell() {
            getStyleClass().add("widget-card");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            bar.getStyleClass().add("bar");
            bar.setMinHeight(4.0);
            bar.setPrefSize(36.0, 4.0);
            bar.setMaxSize(36.0, 4.0);
            note.setWrapText(true);
            card.getStyleClass().add("widget");
            card.prefWidthProperty().bind(widthProperty());
            card.prefHeightProperty().bind(heightProperty());
        }

        @Override
        protected void updateItem(Widget widget, boolean empty) {
            super.updateItem(widget, empty);
            if (empty || widget == null) {
                setGraphic(null);
            } else {
                bar.getStyleClass().removeAll(PALETTE);
                bar.getStyleClass().add(widget.color());
                title.setText(widget.title());
                value.setText(widget.value());
                note.setText(widget.note());
                setGraphic(card);
            }
        }
    }
}
