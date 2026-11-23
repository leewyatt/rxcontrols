package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.Choice;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.DoubleToIntFunction;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.menu;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;

/**
 * The RXDashboardPane page: a clean board of numbered cards in a resizable frame whose column count follows its
 * width, with packing, the collision policy, adding and removing cards, and save and restore; and a live board of
 * stock, weather, room, storage, release, team and revenue cards that are pinned in place with a lock, where the
 * stock, release, weather and room cards fit what they show to their size.
 */
public final class RXDashboardPaneSamples {

    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");
    private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final PseudoClass NARROW = PseudoClass.getPseudoClass("narrow");
    private static final PseudoClass OFF = PseudoClass.getPseudoClass("off");

    private static final int NUMBERED_CARDS = 11;
    private static final RXDashboardLayout NUMBERED = RXDashboardLayout.builder(6)
            .card("1", 0, 0, 2, 1).card("2", 2, 0, 2, 2).card("3", 4, 0, 1, 1).card("4", 5, 0, 1, 1)
            .card("5", 0, 1, 1, 1).card("6", 1, 1, 1, 2).card("7", 4, 1, 2, 1)
            .card("8", 0, 2, 1, 1).card("9", 2, 2, 2, 1).card("10", 4, 2, 1, 1).card("11", 5, 2, 1, 1)
            .build();

    private static final RXDashboardLayout LIVE = RXDashboardLayout.builder(4)
            .card("stock", 0, 0, 2, 2).card("weather", 2, 0, 1, 2).card("home", 3, 0, 1, 1)
            .card("storage", 3, 1, 1, 1).card("release", 0, 2, 2, 1).card("team", 2, 2, 1, 1)
            .card("revenue", 3, 2, 1, 1)
            .build();

    // A card narrower than this shows a compact form of its content.
    private static final double NARROW_CARD = 300.0;
    // A card shorter than this, about one row, leaves out what only fits two.
    private static final double TALL_CARD = 240.0;

    private static final double OPEN_PRICE = 181.90;
    private static final int PRICE_POINTS = 48;
    private static final String[] RELEASE_STEPS = {"Design", "Build", "Test", "Ship"};
    private static final String[] RELEASE_STAGES = {"Designing", "Building", "Testing", "Shipping"};
    private static final double[] WEEK_REVENUE = {0.46, 0.62, 0.38, 0.71, 0.55, 0.83, 1.0};

    private RXDashboardPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @param life the life of the page, which runs the live cards while the page is shown
     * @return the content
     */
    public static SampleContent create(SampleLifecycle life) {
        return new SampleContent(
                "An editable grid of cards: users drag and resize them, the others make room and pack upward, and the layout saves and restores as a snapshot.",
                List.of("Dashboards and home screens where users arrange and resize widgets on a grid, and the arrangement is kept.",
                        "For a fixed responsive grid use RXRow / RXCol; for cards of free heights use RXMasonryPane."),
                List.of(
                        SampleCard.canvas("Live board",
                                "Drag a card anywhere on it; the grab cursor shows only on cards that can move, "
                                        + "the pin locks a card in place and Edit layout makes the board read-only. "
                                        + "The quote, release, weather and room cards fit their size: on one column "
                                        + "the quote shows just the price, the change and a small trend and the "
                                        + "release drops its step names; on one row the weather drops its hourly "
                                        + "forecast and the room keeps two switches of three.",
                                live(life)),
                        SampleCard.canvas("Numbered cards",
                                "Drag a card, or pull its right or bottom edge: the others make room and pack upward. "
                                        + "Narrow the frame and the columns go from 6 to 4 to 2; widen it and the "
                                        + "layout comes back as it was. Save takes a snapshot that Restore applies.",
                                numbered())),
                LayoutKit.STYLESHEET);
    }

    // Runs the action with the card's width and height each time its size changes: on a resize, live while the card is
    // pulled, and when the board's columns change. Content goes by pixels, not spans: one column of a two-column board
    // is as wide as two of a four-column one.
    private static void followSize(Region card, BiConsumer<Double, Double> action) {
        InvalidationListener fit = obs -> action.accept(card.getWidth(), card.getHeight());
        card.widthProperty().addListener(fit);
        card.heightProperty().addListener(fit);
    }

    private static void setShown(Node part, boolean shown) {
        part.setVisible(shown);
        part.setManaged(shown);
    }

    // The column count follows the board's width, as a responsive dashboard's would.
    private static void followWidth(RXDashboardPane board, DoubleToIntFunction columns) {
        board.widthProperty().addListener(
                (obs, old, width) -> board.setColumnCount(columns.applyAsInt(width.doubleValue())));
    }

    // ==================== Numbered cards ====================

    private static Node numbered() {
        RXDashboardPane board = new RXDashboardPane();
        board.getStyleClass().add("numbered-board");
        board.setColumnCount(NUMBERED.getColumnCount());
        board.setRowHeight(84.0);
        board.setHgap(10.0);
        board.setVgap(10.0);
        int[] next = {1};
        for (; next[0] <= NUMBERED_CARDS; next[0]++) {
            board.getCards().add(numberCard(next[0]));
        }
        board.applyLayout(NUMBERED);
        followWidth(board, width -> width >= 640.0 ? 6 : width >= 400.0 ? 4 : 2);

        Label columns = text("", "frame-size");
        columns.textProperty().bind(Bindings.createStringBinding(
                () -> board.getColumnCount() + " columns", board.columnCountProperty()));
        columns.setMinWidth(Region.USE_PREF_SIZE);

        RXCheckBox packed = new RXCheckBox("Keep packed");
        packed.selectedProperty().bindBidirectional(board.autoCompactProperty());
        RXMenuButton collision = menu("Collision", 0, board::setCollisionPolicy,
                new Choice<>("Push", CollisionPolicy.PUSH), new Choice<>("Block", CollisionPolicy.BLOCK));

        RXButton add = plain("Add card");
        add.setOnAction(event -> board.getCards().add(numberCard(next[0]++)));
        RXButton remove = plain("Remove last");
        remove.setOnAction(event -> {
            board.getCards().remove(board.getCards().size() - 1);
            next[0]--;
        });
        remove.disableProperty().bind(Bindings.isEmpty(board.getCards()));
        FocusHandOff.onDisable(remove, add);
        RXDashboardLayout[] saved = {NUMBERED};
        RXButton save = plain("Save");
        save.setOnAction(event -> saved[0] = board.getLayout());
        RXButton restore = plain("Restore");
        restore.setOnAction(event -> board.applyLayout(saved[0]));
        for (Region region : List.of(add, remove, save, restore)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }

        // The options wrap on a narrow card, being a flow pane themselves.
        RXFlowPane options = new RXFlowPane(8.0, 8.0, packed, collision, add, remove, save, restore);
        options.setAnimated(false);
        VBox frame = widthFrame(board, 260.0, columns);
        frame.getChildren().add(1, options);
        return frame;
    }

    private static RXDashboardCard numberCard(int number) {
        String id = String.valueOf(number);
        return new RXDashboardCard(id, text(id, "card-number"));
    }

    // ==================== Live board ====================

    private static Node live(SampleLifecycle life) {
        RXDashboardPane board = new RXDashboardPane();
        board.getStyleClass().add("live-board");
        board.setColumnCount(LIVE.getColumnCount());
        board.setRowHeight(150.0);
        board.setHgap(12.0);
        board.setVgap(12.0);

        StockQuote quote = new StockQuote(life);
        RXDashboardCard stock = card("stock", "RXC", "chart-icon", "success", quote.body(), liveBadge());
        quote.adaptTo(stock);
        VBox hourly = hourly();
        RXDashboardCard weather = card("weather", "Lisbon", "sun-icon", "warning", weather(hourly));
        followSize(weather, (width, height) -> setShown(hourly, height >= TALL_CARD));
        Label stage = text("", "stage-chip");
        stage.setMinWidth(Region.USE_PREF_SIZE);
        ReleaseTrack track = new ReleaseTrack(life, stage);
        RXDashboardCard release = card("release", "Release 3.2", "flag-icon", "primary", track.body(), stage);
        track.adaptTo(release);
        HBox quiet = setting("moon-icon", "danger", "Do not disturb", "Until 07:00", false, true);
        VBox switches = new VBox(setting("bulb-icon", "warning", "Lights", "4 lamps on", true, false),
                setting("snow-icon", "primary", "Climate", "Cooling to 22°", true, true), quiet);
        RXDashboardCard home = card("home", "Living room", "home-icon", "primary", switches);
        followSize(home, (width, height) -> setShown(quiet, height >= TALL_CARD));
        board.getCards().addAll(stock, weather, home,
                card("storage", "Storage", "storage-icon", "muted", storage()),
                release,
                card("team", "On call", "group-icon", "danger", team()),
                card("revenue", "Revenue", "money-icon", "success", revenue()));
        board.applyLayout(LIVE);
        followWidth(board, width -> width >= 760.0 ? 4 : 2);

        RXSwitchButton edit = new RXSwitchButton("Edit layout");
        edit.selectedProperty().bindBidirectional(board.editableProperty());
        RXButton reset = plain("Reset layout");
        reset.setMinWidth(Region.USE_PREF_SIZE);
        reset.setOnAction(event -> {
            for (RXDashboardCard card : board.getCards()) {
                card.setLocked(false);
            }
            board.applyLayout(LIVE);
        });
        HBox options = new HBox(12.0, edit, spacer(), reset);
        options.setAlignment(Pos.CENTER_LEFT);
        return new VBox(14.0, options, board);
    }

    // A card under a header: an icon, the title, any extras and a pin that locks the card.
    private static RXDashboardCard card(String id, String title, String glyph, String tone, Node body,
                                        Node... extras) {
        Label name = text(title, "card-title");
        name.setMinWidth(0.0);
        StackPane pin = iconButton("pin-icon");
        pin.getStyleClass().add("pin-button");
        HBox header = new HBox(8.0, icon(glyph, "card-icon", tone), name, spacer());
        header.getChildren().addAll(extras);
        header.getChildren().add(pin);
        header.getStyleClass().add("card-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10.0, header, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        RXDashboardCard card = new RXDashboardCard(id, content);
        // The pin takes its own press, so pressing it never starts a drag.
        pin.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);
        pin.setOnMouseClicked(event -> {
            if (event.isStillSincePress()) {
                card.setLocked(!card.isLocked());
            }
        });
        return card;
    }

    private static Node liveBadge() {
        HBox badge = new HBox(6.0, statusDot("danger"), text("LIVE", "live-text"));
        badge.getStyleClass().add("live-badge");
        badge.setAlignment(Pos.CENTER);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    // A pulsing status dot in a severity color.
    private static RXBadge statusDot(String severity) {
        RXBadge dot = new RXBadge();
        dot.setVariant(RXBadge.Variant.DOT);
        dot.setPulsing(true);
        dot.getStyleClass().add(severity);
        return dot;
    }

    // ==================== Stock ====================

    /**
     * A live quote that fits the card's size: a large card shows the full quote with the day's figures, a short one
     * the price over the trend, a narrow one only the price, the change and a small trend.
     */
    private static final class StockQuote {

        private final Random random = new Random(7L);
        private final List<Double> prices = new ArrayList<>();
        private final RXTransitionLabel quote = new RXTransitionLabel();
        private final Label delta = text("", "delta-chip");
        private final Label company = text("Rx Controls Inc.  ·  NASDAQ", "card-muted");
        private final Sparkline chart = new Sparkline();
        private final Label high = text("", "stat-value");
        private final Label low = text("", "stat-value");
        private final Label traded = text("", "stat-value");
        private final HBox stats = new HBox(8.0, stat("Open", text(money(OPEN_PRICE), "stat-value")),
                stat("High", high), stat("Low", low), stat("Volume", traded));
        private final VBox body;
        private double lowest = Double.MAX_VALUE;
        private double highest = -Double.MAX_VALUE;
        private double volume = 12.4;
        private boolean narrow;

        StockQuote(SampleLifecycle life) {
            double price = OPEN_PRICE;
            for (int i = 0; i < PRICE_POINTS; i++) {
                price += (random.nextDouble() - 0.45) * 1.2;
                record(price);
            }
            quote.setAnimation(new AnimSlide(Orientation.VERTICAL));
            quote.setAnimationDuration(Duration.millis(320.0));
            quote.setAlignment(Pos.CENTER_LEFT);
            quote.setPrefSize(150.0, 40.0);
            quote.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            quote.getStyleClass().add("stock-price");
            delta.setMinWidth(Region.USE_PREF_SIZE);
            HBox top = new HBox(12.0, quote, delta);
            top.setAlignment(Pos.CENTER_LEFT);
            VBox.setVgrow(chart, Priority.ALWAYS);

            body = new VBox(8.0, company, top, chart, stats);
            body.getStyleClass().add("stock");
            show();

            Timeline ticks = new Timeline(new KeyFrame(Duration.seconds(1.4), event -> {
                double last = latest();
                double next = last + (random.nextDouble() - 0.48) * 1.6;
                prices.remove(0);
                record(next);
                volume += random.nextDouble() * 0.3;
                quote.setDirection(next >= last ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD);
                show();
            }));
            ticks.setCycleCount(Animation.INDEFINITE);
            life.playWhileShown(ticks);
        }

        Node body() {
            return body;
        }

        void adaptTo(Region card) {
            followSize(card, this::fit);
        }

        private void fit(double width, double height) {
            narrow = width < NARROW_CARD;
            boolean roomy = !narrow && height >= TALL_CARD;
            setShown(company, roomy);
            setShown(stats, roomy);
            quote.setPrefSize(narrow ? 100.0 : 150.0, narrow ? 30.0 : 40.0);
            body.pseudoClassStateChanged(NARROW, narrow);
            show();
        }

        private void record(double price) {
            prices.add(price);
            lowest = Math.min(lowest, price);
            highest = Math.max(highest, price);
        }

        private double latest() {
            return prices.get(prices.size() - 1);
        }

        private void show() {
            double last = latest();
            double change = last - OPEN_PRICE;
            double percent = change / OPEN_PRICE * 100.0;
            quote.setText(money(last));
            delta.setText(narrow ? String.format(Locale.ROOT, "%+.2f%%", percent)
                    : String.format(Locale.ROOT, "%+.2f  (%+.2f%%)", change, percent));
            body.pseudoClassStateChanged(DOWN, change < 0.0);
            high.setText(money(highest));
            low.setText(money(lowest));
            traded.setText(String.format(Locale.ROOT, "%.1fM", volume));
            chart.setValues(prices);
        }
    }

    private static Node stat(String caption, Label value) {
        VBox stat = new VBox(2.0, text(caption, "stat-caption"), value);
        stat.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(stat, Priority.ALWAYS);
        return stat;
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "$%.2f", value);
    }

    /**
     * A line over a soft area that spans the values from lowest to highest, ending in a dot on the latest value.
     */
    private static final class Sparkline extends Region {

        private final Path area = new Path();
        private final Path line = new Path();
        private final Circle dot = new Circle(3.5);
        private final List<Double> values = new ArrayList<>();

        Sparkline() {
            getStyleClass().add("sparkline");
            area.getStyleClass().add("area");
            area.setStroke(null);
            line.getStyleClass().add("line");
            dot.getStyleClass().add("dot");
            getChildren().addAll(area, line, dot);
            setMinSize(0.0, 0.0);
            setPrefSize(200.0, 80.0);
        }

        void setValues(List<Double> latest) {
            values.clear();
            values.addAll(latest);
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            line.getElements().clear();
            area.getElements().clear();
            if (values.size() < 2) {
                dot.setVisible(false);
                return;
            }
            double left = snappedLeftInset();
            double top = snappedTopInset();
            double width = Math.max(0.0, getWidth() - left - snappedRightInset());
            double height = Math.max(0.0, getHeight() - top - snappedBottomInset());
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (double value : values) {
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            double span = Math.max(high - low, 1e-9);
            double step = width / (values.size() - 1);
            double x = left;
            double y = top;
            for (int i = 0; i < values.size(); i++) {
                x = left + i * step;
                y = top + (high - values.get(i)) / span * height;
                line.getElements().add(i == 0 ? new MoveTo(x, y) : new LineTo(x, y));
                area.getElements().add(i == 0 ? new MoveTo(x, y) : new LineTo(x, y));
            }
            area.getElements().addAll(new LineTo(x, top + height), new LineTo(left, top + height), new ClosePath());
            dot.setCenterX(x);
            dot.setCenterY(y);
            dot.setVisible(true);
        }
    }

    // ==================== Weather ====================

    // The weather now; a card about one row tall leaves out the hourly forecast.
    private static Node weather(Node hourly) {
        Label temperature = text("24°", "weather-temperature");
        HBox now = new HBox(12.0, sky("partly", "large"), temperature);
        now.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6.0, now, text("Partly cloudy", "card-strong"), text("H 27°  ·  L 18°", "card-muted"),
                hourly);
    }

    private static VBox hourly() {
        Region rule = new Region();
        rule.getStyleClass().add("card-rule");
        VBox hourly = new VBox(9.0, rule,
                hour("15:00", "sun", "25°"), hour("16:00", "partly", "24°"), hour("17:00", "cloud", "22°"),
                hour("18:00", "rain", "20°"), hour("19:00", "rain", "19°"));
        hourly.setPadding(new Insets(4.0, 0.0, 0.0, 0.0));
        return hourly;
    }

    private static Node hour(String time, String sky, String temperature) {
        HBox row = new HBox(8.0, text(time, "card-muted"), spacer(), sky(sky, "small"),
                text(temperature, "hour-temperature"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // A weather symbol: the sun, a cloud, rain, or the sun behind a cloud.
    private static Node sky(String kind, String size) {
        StackPane symbol = new StackPane();
        symbol.getStyleClass().addAll("sky", size);
        symbol.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        symbol.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        switch (kind) {
            case "sun" -> symbol.getChildren().add(icon("sun-icon"));
            case "cloud" -> symbol.getChildren().add(icon("cloud-icon"));
            case "rain" -> symbol.getChildren().add(icon("drop-icon"));
            default -> {
                Region sun = icon("sun-icon", "behind");
                Region cloud = icon("cloud-icon", "front");
                StackPane.setAlignment(sun, Pos.TOP_RIGHT);
                StackPane.setAlignment(cloud, Pos.BOTTOM_LEFT);
                symbol.getChildren().addAll(sun, cloud);
            }
        }
        return symbol;
    }

    // ==================== Room ====================

    // A setting row: a badge in the setting's color, the setting over its state, and a switch. Switched off, the badge
    // turns gray and the state reads Off. A card about one row tall leaves out the third row.
    private static HBox setting(String glyph, String tone, String title, String state, boolean on, boolean ruled) {
        StackPane badge = new StackPane(icon(glyph));
        badge.getStyleClass().addAll("setting-badge", tone);
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label name = text(title, "card-strong");
        Label hint = text("", "card-muted");
        VBox texts = new VBox(1.0, name, hint);
        texts.setMinWidth(0.0);
        HBox.setHgrow(texts, Priority.ALWAYS);
        RXSwitchButton toggle = new RXSwitchButton();
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        // The switch takes its own press, so pulling its thumb never drags the card.
        toggle.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);

        HBox row = new HBox(10.0, badge, texts, toggle);
        row.getStyleClass().add("setting-row");
        if (ruled) {
            row.getStyleClass().add("ruled");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        Runnable show = () -> {
            hint.setText(toggle.isSelected() ? state : "Off");
            row.pseudoClassStateChanged(OFF, !toggle.isSelected());
        };
        toggle.setSelected(on);
        toggle.selectedProperty().addListener(obs -> show.run());
        show.run();
        return row;
    }

    // ==================== Storage ====================

    private static Node storage() {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator(0.64);
        tank.setPrefSize(78.0, 78.0);
        tank.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        tank.setTextFactory(value -> Math.round(value * 100.0) + "%");
        VBox texts = new VBox(4.0, text("640 GB of 1 TB", "card-strong"), text("Photos take 312 GB", "card-muted"));
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox body = new HBox(14.0, tank, texts);
        body.setAlignment(Pos.CENTER_LEFT);
        return body;
    }

    // ==================== Release ====================

    /**
     * A release moving through its steps, then starting over. On a narrow card the step names give way, and the bar,
     * the stage in the header and a shorter status remain.
     */
    private static final class ReleaseTrack {

        private final RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(RELEASE_STEPS.length);
        private final List<Label> names = new ArrayList<>();
        private final HBox nameRow = new HBox(6.0);
        private final Label status = text("", "card-muted");
        private final Label stage;
        private final VBox body;
        private int index = 2;
        private double progress = 0.4;
        private int held;
        private boolean narrow;

        ReleaseTrack(SampleLifecycle life, Label stage) {
            this.stage = stage;
            steps.setSegmentHeight(8.0);
            steps.setSegmentGap(6.0);
            steps.setMaxWidth(Double.MAX_VALUE);
            for (String step : RELEASE_STEPS) {
                Label name = text(step, "step-name");
                // Equal widths, so each name sits under its segment.
                name.setMinWidth(0.0);
                name.setPrefWidth(0.0);
                name.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(name, Priority.ALWAYS);
                names.add(name);
                nameRow.getChildren().add(name);
            }
            body = new VBox(8.0, steps, nameRow, status);
            body.setAlignment(Pos.CENTER_LEFT);
            show();

            Timeline run = new Timeline(new KeyFrame(Duration.millis(450.0), event -> {
                advance();
                show();
            }));
            run.setCycleCount(Animation.INDEFINITE);
            life.playWhileShown(run);
        }

        Node body() {
            return body;
        }

        void adaptTo(Region card) {
            followSize(card, (width, height) -> {
                narrow = width < NARROW_CARD;
                setShown(nameRow, !narrow);
                show();
            });
        }

        // Fills the current step, moves to the next, and after a pause on the finished release starts over.
        private void advance() {
            if (progress < 1.0) {
                progress = Math.min(1.0, progress + 0.08);
            } else if (index < RELEASE_STEPS.length - 1) {
                index++;
                progress = 0.0;
            } else if (++held > 8) {
                held = 0;
                index = 0;
                progress = 0.0;
            }
        }

        private void show() {
            boolean released = index == RELEASE_STEPS.length - 1 && progress >= 1.0;
            steps.setSelectedIndex(index);
            steps.setSegmentProgress(progress);
            for (int i = 0; i < names.size(); i++) {
                Label name = names.get(i);
                name.pseudoClassStateChanged(DONE, i < index || released);
                name.pseudoClassStateChanged(CURRENT, i == index && !released);
            }
            stage.setText(released ? "Released" : RELEASE_STAGES[index]);
            stage.pseudoClassStateChanged(DONE, released);
            long percent = Math.round((index + Math.min(progress, 1.0)) / RELEASE_STEPS.length * 100.0);
            if (released) {
                status.setText(narrow ? "All checks passed" : "All checks passed  ·  live for everyone");
            } else {
                status.setText(String.format(Locale.ROOT, "Step %d of %d  ·  %d%%", index + 1,
                        RELEASE_STEPS.length, percent));
            }
        }
    }

    // ==================== Team ====================

    private static Node team() {
        HBox faces = new HBox(-8.0);
        String[][] people = {{"AK", "primary"}, {"MJ", "success"}, {"SL", "warning"}, {"RT", "danger"}};
        for (String[] person : people) {
            RXAvatar avatar = new RXAvatar();
            avatar.setText(person[0]);
            avatar.setPrefSize(32.0, 32.0);
            avatar.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            avatar.getStyleClass().add(person[1]);
            faces.getChildren().add(avatar);
        }
        Label more = text("+2", "avatar-more");
        more.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        faces.getChildren().add(more);
        faces.setAlignment(Pos.CENTER_LEFT);

        HBox status = new HBox(8.0, statusDot("success"), text("3 online  ·  1 away", "card-strong"));
        status.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10.0, faces, status, text("Handover at 18:00", "card-muted"));
    }

    // ==================== Revenue ====================

    private static Node revenue() {
        Label change = text("+12.4%", "delta-chip");
        change.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(8.0, text("$48.2k", "metric"), change);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox bars = new HBox(5.0);
        bars.setAlignment(Pos.BOTTOM_LEFT);
        for (int i = 0; i < WEEK_REVENUE.length; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("bar");
            if (i == WEEK_REVENUE.length - 1) {
                bar.getStyleClass().add("today");
            }
            bar.setPrefSize(0.0, 40.0 * WEEK_REVENUE[i]);
            bar.setMaxSize(Double.MAX_VALUE, Region.USE_PREF_SIZE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            bars.getChildren().add(bar);
        }
        return new VBox(10.0, top, bars, text("This week", "card-muted"));
    }
}
