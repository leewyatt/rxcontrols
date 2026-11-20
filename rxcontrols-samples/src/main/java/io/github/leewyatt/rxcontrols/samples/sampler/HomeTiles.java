package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleLifecycle;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.col;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.glyph;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.wrapped;

/**
 * The "See them working" tiles of the home page: live, working components in a bento
 * grid, each with a link to its page.
 */
final class HomeTiles {

    private static final List<String> PALETTE = List.of("rose", "orange", "amber", "teal", "sky", "indigo", "violet");
    private static final double WALL_MIN_HEIGHT = 320.0;
    private static final double DIGIT_WIDTH = 24.0;

    private record Task(String title, String tag, String owner, int points) {
    }

    private record Photo(int image, double ratio) {
    }

    private HomeTiles() {
    }

    /**
     * Builds the tile grid.
     *
     * @param life     the life of the home page, for the parts that keep running
     * @param openType opens the page of a component, by its simple type name
     * @return the grid
     */
    static RXRow create(SampleLifecycle life, Consumer<String> openType) {
        // The kanban, calendar, tank and clock share the left two thirds, so the photo
        // wall beside them can run the full height of both lines; the tank and the clock
        // stack beside the calendar, which needs the width.
        RXRow left = HomeKit.row(16.0, 16.0);
        left.setAlign(RXRow.Align.STRETCH);
        VBox small = new VBox(16.0,
                tile("RXWaveProgressIndicator", "Wave progress", "Fill as a wave.", wave(), openType),
                tile("RXDigit", "RXDigit", "Seven-segment glyphs.", clock(life), openType));
        small.getChildren().forEach(child -> VBox.setVgrow(child, Priority.ALWAYS));
        left.getChildren().addAll(
                col(tile("RXKanbanView", "Drag cards within and between columns.", kanban(), openType), 24, 24, 24),
                col(tile("RXDateRangeView", "A start and an end date on one calendar.", dateRange(), openType),
                        24, 14, 14),
                col(small, 24, 10, 10));

        RXCol wall = col(tile("RXMasonryView", "Virtualized wall of cards in columns.", wall(), openType), 24, 24, 8);
        wall.setMinHeight(WALL_MIN_HEIGHT);

        RXRow grid = HomeKit.row(16.0, 16.0);
        grid.getStyleClass().add("home-tiles");
        grid.setAlign(RXRow.Align.STRETCH);
        grid.getChildren().addAll(
                col(HomeKit.bleed(left), 24, 24, 16),
                wall,
                col(tile("RXTimelineView", "Events on a vertical or horizontal line.", timeline(), openType),
                        24, 12, 8),
                col(tile("RXSegmentedControl", "One of many, with a sliding indicator.", segmented(), openType),
                        24, 12, 8),
                col(tile("RXRibbonBadge", "RXRibbonBadge and RXBadge", "Mark one card among many.", badges(),
                        openType), 24, 24, 8));
        return HomeKit.bleed(grid);
    }

    // ==================== Tile ====================

    private static VBox tile(String type, String caption, Node content, Consumer<String> openType) {
        return tile(type, type, caption, content, openType);
    }

    /**
     * A tile: the live component on a stage, and its name and purpose below. Only the
     * Open button opens the component's page.
     */
    private static VBox tile(String type, String title, String caption, Node content, Consumer<String> openType) {
        StackPane stage = new StackPane(content);
        stage.getStyleClass().add("stage");
        VBox.setVgrow(stage, Priority.ALWAYS);

        VBox words = new VBox(2.0, text(title, "title"), wrapped(caption, "caption"));
        words.setMinWidth(0.0);
        HBox.setHgrow(words, Priority.ALWAYS);
        RXButton open = new RXButton("Open", glyph("arrow-right-glyph"));
        open.getStyleClass().add("link-button");
        open.setContentDisplay(ContentDisplay.RIGHT);
        open.setMinWidth(Region.USE_PREF_SIZE);
        open.setTooltip(new Tooltip("Open the " + type + " page"));
        open.setAccessibleText("Open the " + type + " page");
        open.setOnAction(event -> openType.accept(type));
        HBox footer = new HBox(10.0, words, open);
        footer.getStyleClass().add("footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox tile = new VBox(stage, footer);
        tile.getStyleClass().add("home-tile");
        return tile;
    }

    // ==================== Kanban ====================

    private static Node kanban() {
        RXKanbanView<Task> board = new RXKanbanView<>();
        board.getColumns().addAll(
                column("To do",
                        new Task("Write release notes", "Docs", "Lee Wyatt", 2),
                        new Task("Empty state for search", "Design", "Hana Sato", 3),
                        new Task("Export to CSV", "Feature", "Mia Chen", 3)),
                column("In progress",
                        new Task("Dark theme colors", "Design", "Ada Brooks", 5),
                        new Task("Login rate limit", "Security", "Omar Haddad", 3)),
                column("Done",
                        new Task("Fix date parsing", "Bug", "Lee Wyatt", 1),
                        new Task("Onboarding tour", "Feature", "Mia Chen", 8)));
        board.setPrefColumnWidth(150.0);
        board.setColumnsJustify(ItemsJustify.STRETCH);
        board.setPrefCardHeight(76.0);
        board.setCardCellFactory(view -> new TaskCell());
        board.setPrefHeight(270.0);
        board.setMinHeight(270.0);
        return board;
    }

    @SafeVarargs
    private static <T> RXKanbanColumn<T> column(String title, T... cards) {
        RXKanbanColumn<T> column = new RXKanbanColumn<>(title);
        column.getCards().addAll(cards);
        return column;
    }

    private static final class TaskCell extends RXKanbanCardCell<Task> {

        private final Label tag = text("", "tag");
        private final Label title = text("", "task-title");
        private final Label owner = text("", "initials");
        private final Label points = text("", "points");
        private final VBox card;

        private TaskCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            owner.setAlignment(Pos.CENTER);
            owner.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            owner.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            HBox footer = new HBox(6.0, owner, spacer(), points);
            footer.setAlignment(Pos.CENTER_LEFT);
            Region gap = new Region();
            VBox.setVgrow(gap, Priority.ALWAYS);
            card = new VBox(5.0, tag, title, gap, footer);
        }

        @Override
        protected void updateItem(Task task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setText(null);
                setGraphic(null);
            } else {
                tag.setText(task.tag());
                paint(tag, task.tag());
                title.setText(task.title());
                owner.setText(initials(task.owner()));
                paint(owner, task.owner());
                points.setText(task.points() + " pt");
                setGraphic(card);
            }
        }
    }

    private static void paint(Label label, String key) {
        label.getStyleClass().removeAll(PALETTE);
        label.getStyleClass().add(PALETTE.get(Math.floorMod(key.hashCode(), PALETTE.size())));
    }

    private static String initials(String name) {
        String[] parts = name.split(" ");
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    // ==================== Masonry ====================

    private static Node wall() {
        double[] ratios = {1.3, 0.75, 1.0, 1.45, 0.8, 1.15, 0.7, 1.25, 0.95, 1.4, 0.85, 1.1};
        List<Image> images = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            images.add(HomeKit.image("/scenery/" + i + ".png"));
        }
        ObservableList<Photo> photos = FXCollections.observableArrayList();
        for (int i = 0; i < 24; i++) {
            photos.add(new Photo(i % images.size(), ratios[i % ratios.length]));
        }
        RXMasonryView<Photo> wall = new RXMasonryView<>(photos);
        wall.getStyleClass().add("photo-wall");
        wall.setColumnWidth(110.0);
        wall.setFillWidth(true);
        wall.setHgap(8.0);
        wall.setVgap(8.0);
        wall.setCellHeightProvider(context -> context.cellWidth() * context.item().ratio());
        wall.setCellFactory(view -> new PhotoCell(images));
        // The tile, not the wall, decides the height: the wall scrolls in whatever it gets.
        wall.setMinHeight(0.0);
        wall.setPrefHeight(0.0);
        wall.setMaxHeight(Double.MAX_VALUE);
        return wall;
    }

    private static final class PhotoCell extends RXMasonryCell<Photo> {

        private final List<Image> images;
        private final RXImageView image = new RXImageView();

        private PhotoCell(List<Image> images) {
            this.images = images;
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            image.setImageRadius(8.0);
            image.setMinSize(0.0, 0.0);
            image.prefWidthProperty().bind(widthProperty());
            image.prefHeightProperty().bind(heightProperty());
        }

        @Override
        protected void updateItem(Photo photo, boolean empty) {
            super.updateItem(photo, empty);
            if (empty || photo == null) {
                setGraphic(null);
            } else {
                image.setImage(images.get(photo.image()));
                setGraphic(image);
            }
        }
    }

    // ==================== Date range ====================

    private static Node dateRange() {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        RXDateRangeView view = new RXDateRangeView(RXDateRange.of(first.plusDays(6), first.plusDays(17)));
        view.getStyleClass().add("violet");
        view.setMonthCount(1);
        view.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return view;
    }

    // ==================== Wave ====================

    private static Node wave() {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator(0.65);
        tank.setPrefSize(110.0, 110.0);
        tank.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // A click pours in another tenth, and an empty tank follows a full one.
        tank.setOnMouseClicked(event -> tank.setProgress(tank.getProgress() >= 0.99 ? 0.0
                : Math.min(1.0, tank.getProgress() + 0.1)));
        return tank;
    }

    // ==================== Clock ====================

    private static Node clock(SampleLifecycle life) {
        List<RXDigit> digits = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            RXDigit digit = new RXDigit();
            digit.setPrefSize(DIGIT_WIDTH, DIGIT_WIDTH * 2.0);
            digits.add(digit);
        }
        Region top = glyph("colon-dot");
        Region bottom = glyph("colon-dot");
        VBox colon = new VBox(DIGIT_WIDTH * 0.5, top, bottom);
        colon.setAlignment(Pos.CENTER);
        HBox face = new HBox(5.0, digits.get(0), digits.get(1), colon, digits.get(2), digits.get(3));
        face.setAlignment(Pos.CENTER);
        face.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Runnable update = () -> {
            LocalTime now = LocalTime.now();
            digits.get(0).setDigit(now.getHour() / 10);
            digits.get(1).setDigit(now.getHour() % 10);
            digits.get(2).setDigit(now.getMinute() / 10);
            digits.get(3).setDigit(now.getMinute() % 10);
            colon.setOpacity(now.getSecond() % 2 == 0 ? 1.0 : 0.25);
        };
        update.run();
        Timeline ticks = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> update.run()));
        ticks.setCycleCount(Animation.INDEFINITE);
        life.playWhileShown(ticks);
        return face;
    }

    // ==================== Timeline ====================

    private static Node timeline() {
        RXTimelineView timeline = new RXTimelineView(
                event("09:24", "Placed", "Order created", RXTimelineItem.Type.PRIMARY),
                event("09:31", "Paid", "Payment confirmed", RXTimelineItem.Type.SUCCESS),
                event("13:40", "Delayed", "Hub closed", RXTimelineItem.Type.WARNING),
                event("19:15", "Notified", "Email sent", RXTimelineItem.Type.INFO));
        timeline.setShowOppositeContent(true);
        timeline.setItemSpacing(6.0);
        return timeline;
    }

    private static RXTimelineItem event(String time, String title, String description, RXTimelineItem.Type type) {
        RXTimelineItem item = new RXTimelineItem(title);
        item.setDescription(description);
        item.setType(type);
        item.setOppositeContent(text(time, "time"));
        return item;
    }

    // ==================== Segmented ====================

    private static Node segmented() {
        RXSegmentedControl<String> period = new RXSegmentedControl<>(
                RXSegmentedItem.of("daily", "Day"),
                RXSegmentedItem.of("weekly", "Week"),
                RXSegmentedItem.of("monthly", "Month"),
                RXSegmentedItem.of("yearly", "Year"));
        period.select("weekly");

        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(5);
        steps.setSelectedIndex(2);
        steps.setSegmentProgress(1.0);
        steps.setSegmentHeight(6.0);
        steps.setMaxWidth(Double.MAX_VALUE);
        Label step = text("", "caption");
        steps.selectedIndexProperty().addListener((obs, old, index) ->
                step.setText("Step " + (index.intValue() + 1) + " of 5"));
        step.setText("Step 3 of 5");
        // The indicator only reports the click; the page decides which step is current.
        steps.setOnSegmentClicked(event -> steps.setSelectedIndex(event.getSegmentIndex()));

        VBox box = new VBox(16.0, period, new VBox(8.0, steps, step));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ==================== Badges ====================

    private static Node badges() {
        StackPane icon = new StackPane(glyph("gift-glyph"));
        icon.getStyleClass().add("app-icon");
        icon.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXBadge count = new RXBadge(icon);
        count.setCount(3);
        count.getStyleClass().add("danger");

        VBox words = new VBox(2.0, text("Spring sale", "strong"), text("Ends on Sunday", "caption"));
        words.setAlignment(Pos.CENTER_LEFT);
        HBox card = new HBox(12.0, count, words);
        card.getStyleClass().add("promo-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        // A click on the card clears the unread count, as opening the offer would.
        card.setOnMouseClicked(event -> count.setCount(count.getCount() == 0 ? 3 : 0));

        RXRibbonBadge ribbon = new RXRibbonBadge(card, "New");
        ribbon.getStyleClass().add("success");
        ribbon.setMaxSize(Double.MAX_VALUE, Region.USE_PREF_SIZE);
        return ribbon;
    }
}
