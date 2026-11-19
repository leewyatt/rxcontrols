package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.Choice;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.chip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.menu;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;

/**
 * The RXFlowPane page: one board of numbered blocks of mixed sizes under a toolbar that
 * sets its width and adds or removes blocks, with a menu button for each layout choice;
 * then one card each for row, item and block alignment, switched from a picker.
 */
public final class RXFlowPaneSamples {

    private static final double FULL_WIDTH = 1440.0;
    private static final double MIN_WIDTH = 220.0;
    private static final double MIN_HEIGHT = 140.0;
    private static final double BOARD_HEIGHT = 420.0;
    private static final double START_GAP = 10.0;
    private static final int START_COUNT = 20;
    private static final double ROW_FRAME_WIDTH = 560.0;
    private static final String[] COLORS = {
        "rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet", "pink"};

    // A run or item alignment, read as hpos or vpos by the current direction.
    private record Align(HPos hpos, VPos vpos) {
    }

    private RXFlowPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A flow layout that wraps its children into rows or columns, aligns the whole block once and keeps a short last row in line with the rows above it.",
                List.of("Tags, chips, filters and button groups whose items keep their own size and wrap when space runs out.",
                        "For same-size tiles use RXTilePane; for a responsive column grid use RXRow and RXCol."),
                List.of(
                        SampleCard.wide("Flow board",
                                "The block is centered while every row starts at its left edge, so the last row stays "
                                        + "in line; Runs: Center is what FlowPane does. Drag the width to watch the blocks reflow.",
                                board()),
                        SampleCard.canvas("Row alignment",
                                "rowHalignment places each row inside the block; a short last row shows it best.",
                                rowAlignment()),
                        SampleCard.canvas("Item alignment",
                                "rowValignment places the items of a row against each other; BASELINE lines up "
                                        + "text of different sizes.",
                                itemAlignment()),
                        SampleCard.canvas("Block alignment",
                                "alignment places the whole block in a pane larger than its content.",
                                blockAlignment())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Flow board ====================

    private static Node board() {
        RXFlowPane flow = new RXFlowPane(START_GAP, START_GAP);
        flow.setAlignment(Pos.TOP_CENTER);
        // Matches the Items: Center choice; columns otherwise default to the left.
        flow.setColumnHalignment(HPos.CENTER);
        int[] next = {0};
        for (; next[0] < START_COUNT; next[0]++) {
            flow.getChildren().add(block(next[0]));
        }

        ScrollPane scroll = new ScrollPane(flow);
        scroll.getStyleClass().add("flow-board");
        scroll.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // The stage takes the card's whole width and keeps it, so dragging the slider
        // moves only the board.
        StackPane stage = new StackPane(scroll);
        stage.setAlignment(Pos.TOP_LEFT);
        stage.setPrefHeight(BOARD_HEIGHT);
        stage.setMinWidth(0.0);

        RXButton add = plain("Add");
        add.setOnAction(event -> flow.getChildren().add(block(next[0]++)));
        RXButton remove = plain("Remove last");
        remove.setOnAction(event -> flow.getChildren().remove(flow.getChildren().size() - 1));
        remove.disableProperty().bind(Bindings.isEmpty(flow.getChildren()));
        FocusHandOff.onDisable(remove, add);

        BooleanProperty rows = new SimpleBooleanProperty(true);
        HBox sizeBar = sizeBar(flow, scroll, stage, rows, add, remove);
        Label count = text("", "muted");
        count.textProperty().bind(Bindings.createStringBinding(
                () -> flow.getChildren().size() + " blocks", flow.getChildren()));

        VBox box = new VBox(12.0, sizeBar, layoutBar(flow, rows), stage, count);
        // As wide as the page's widest content, so the board fills whatever width the card gives.
        box.setPrefWidth(FULL_WIDTH);
        return box;
    }

    // The width (or height, for columns) and the content buttons.
    private static HBox sizeBar(RXFlowPane flow, ScrollPane scroll, StackPane stage, BooleanProperty rows,
                                RXButton add, RXButton remove) {
        RXSlider size = new RXSlider(MIN_WIDTH, MIN_WIDTH, MIN_WIDTH);
        size.setPrefWidth(220.0);
        size.setMinWidth(80.0);
        Label caption = text("", "copy");
        caption.textProperty().bind(Bindings.when(rows).then("Width").otherwise("Height"));
        Label value = text("", "muted");
        value.textProperty().bind(Bindings.createStringBinding(
                () -> Math.round(size.getValue()) + " px", size.valueProperty()));

        // Rows wrap at the board's width and columns at its height, so the slider sizes
        // whichever side the flow wraps at. The pane is fitted to that side only: on the
        // other side its minimum follows the view, so it fills the view (and the block's
        // alignment shows) until the blocks outgrow it and the scroll bar comes in. Fitting
        // both sides would hide that scroll bar, as a flow's minimum there is tiny.
        DoubleBinding viewWidth = Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getWidth(), scroll.viewportBoundsProperty());
        DoubleBinding viewHeight = Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty());
        Runnable direction = () -> {
            scroll.prefWidthProperty().unbind();
            scroll.prefHeightProperty().unbind();
            flow.minWidthProperty().unbind();
            flow.minHeightProperty().unbind();
            if (rows.get()) {
                flow.setOrientation(Orientation.HORIZONTAL);
                scroll.setFitToHeight(false);
                scroll.setFitToWidth(true);
                flow.setMinWidth(Region.USE_COMPUTED_SIZE);
                flow.minHeightProperty().bind(viewHeight);
                size.setMin(MIN_WIDTH);
                size.setMax(Math.max(MIN_WIDTH, stage.getWidth()));
                size.setValue(size.getMax());
                scroll.setPrefHeight(BOARD_HEIGHT);
                scroll.prefWidthProperty().bind(size.valueProperty());
            } else {
                flow.setOrientation(Orientation.VERTICAL);
                scroll.setFitToWidth(false);
                scroll.setFitToHeight(true);
                flow.setMinHeight(Region.USE_COMPUTED_SIZE);
                flow.minWidthProperty().bind(viewWidth);
                size.setMin(MIN_HEIGHT);
                size.setMax(BOARD_HEIGHT);
                size.setValue(BOARD_HEIGHT);
                scroll.prefWidthProperty().bind(stage.widthProperty());
                scroll.prefHeightProperty().bind(size.valueProperty());
            }
        };
        rows.addListener(obs -> direction.run());
        direction.run();
        // The widest board is as wide as the card; a slider left at its end keeps
        // following the card as the window grows or shrinks.
        stage.widthProperty().addListener((obs, oldWidth, width) -> {
            if (rows.get()) {
                boolean atEnd = size.getValue() >= size.getMax() - 0.5;
                size.setMax(Math.max(MIN_WIDTH, width.doubleValue()));
                if (atEnd) {
                    size.setValue(size.getMax());
                }
            }
        });

        // On a narrow card only the slider gives up room; the labels and buttons keep theirs.
        for (Region region : List.of(caption, value, add, remove)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(10.0, caption, size, value, spacer(), add, remove);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // One menu button per layout choice, each showing what is picked; the row wraps on a
    // narrow card, being a flow pane itself.
    private static Node layoutBar(RXFlowPane flow, BooleanProperty rows) {
        RXMenuButton direction = menu("Direction", 0, rows::set,
                new Choice<>("Rows", true), new Choice<>("Columns", false));
        RXMenuButton block = menu("Block", 1, flow::setAlignment,
                new Choice<>("Top left", Pos.TOP_LEFT), new Choice<>("Top center", Pos.TOP_CENTER),
                new Choice<>("Center", Pos.CENTER), new Choice<>("Bottom right", Pos.BOTTOM_RIGHT));
        RXMenuButton runs = menu("Runs", 0, run -> {
                    flow.setRowHalignment(run.hpos());
                    flow.setColumnValignment(run.vpos());
                },
                new Choice<>("Start", new Align(HPos.LEFT, VPos.TOP)),
                new Choice<>("Center", new Align(HPos.CENTER, VPos.CENTER)),
                new Choice<>("End", new Align(HPos.RIGHT, VPos.BOTTOM)));
        RXMenuButton items = menu("Items", 1, item -> {
                    flow.setRowValignment(item.vpos());
                    flow.setColumnHalignment(item.hpos());
                },
                new Choice<>("Start", new Align(HPos.LEFT, VPos.TOP)),
                new Choice<>("Center", new Align(HPos.CENTER, VPos.CENTER)),
                new Choice<>("End", new Align(HPos.RIGHT, VPos.BOTTOM)));
        RXMenuButton gap = menu("Gap", 1, value -> {
                    flow.setHgap(value);
                    flow.setVgap(value);
                },
                new Choice<>("0", 0.0), new Choice<>("10", START_GAP), new Choice<>("20", 20.0));
        RXCheckBox animate = new RXCheckBox("Animate");
        animate.selectedProperty().bindBidirectional(flow.animatedProperty());

        RXFlowPane bar = new RXFlowPane(8.0, 8.0, direction, block, runs, items, gap, animate);
        // A button's text grows with a new choice; its neighbours make room at once
        // instead of gliding there.
        bar.setAnimated(false);
        return bar;
    }

    // ==================== Row alignment ====================

    private static Node rowAlignment() {
        RXFlowPane flow = new RXFlowPane(10.0, 10.0);
        // The block sits centered in the frame, so a row's place inside the block shows.
        flow.setAlignment(Pos.TOP_CENTER);
        String[][] items = {
            {"Desk lamp", "$39", "amber", "150"}, {"Plant pot", "$18", "lime", "120"},
            {"Wool throw", "$64", "rose", "180"}, {"Mug", "$9", "sky", "110"},
            {"Wall clock", "$45", "teal", "160"}, {"Tea set", "$52", "violet", "130"},
            {"Candle", "$14", "orange", "140"}};
        for (String[] item : items) {
            flow.getChildren().add(product(item[0], item[1], item[2], Double.parseDouble(item[3])));
        }
        flow.getStyleClass().add("flow-frame");
        flow.setPrefWidth(ROW_FRAME_WIDTH);
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        RXSegmentedControl<HPos> picker = picker(flow::setRowHalignment, HPos.LEFT, HPos.CENTER, HPos.RIGHT);
        return column(picker, flow);
    }

    private static Node product(String name, String price, String color, double width) {
        Region photo = new Region();
        photo.getStyleClass().addAll("product-photo", color);
        photo.setPrefHeight(44.0);
        HBox meta = new HBox(text(name, "copy"), spacer(), text(price, "copy-strong"));
        VBox card = new VBox(8.0, photo, meta);
        card.getStyleClass().add("product");
        card.setPrefWidth(width);
        card.setMinWidth(width);
        return card;
    }

    // ==================== Item alignment ====================

    private static Node itemAlignment() {
        RXFlowPane flow = new RXFlowPane(10.0, 10.0);
        flow.setAlignment(Pos.TOP_CENTER);
        String[][] words = {
            {"JavaFX", "size-l", "indigo"}, {"css", "size-s", "teal"}, {"Layout", "size-m", "amber"},
            {"Flow", "size-xl", "rose"}, {"pane", "size-s", "lime"}, {"Baseline", "size-l", "sky"},
            {"rows", "size-m", "violet"}};
        for (String[] word : words) {
            Label chip = chip(word[0], word[2]);
            chip.getStyleClass().add(word[1]);
            flow.getChildren().add(chip);
        }
        flow.getStyleClass().add("flow-frame");
        flow.setPrefWidth(ROW_FRAME_WIDTH);
        flow.setMaxWidth(Region.USE_PREF_SIZE);
        RXSegmentedControl<VPos> picker = picker(flow::setRowValignment,
                VPos.BASELINE, VPos.TOP, VPos.CENTER, VPos.BOTTOM);
        return column(picker, flow);
    }

    // ==================== Block alignment ====================

    private static Node blockAlignment() {
        RXFlowPane flow = new RXFlowPane(10.0, 10.0);
        for (int i = 0; i < 5; i++) {
            flow.getChildren().add(block(i));
        }
        flow.getStyleClass().add("flow-frame");
        flow.setPrefSize(ROW_FRAME_WIDTH, 220.0);
        flow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXSegmentedControl<Pos> picker = picker(flow::setAlignment,
                Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.CENTER, Pos.BOTTOM_RIGHT);
        return column(picker, flow);
    }

    private static Node column(Node picker, Node flow) {
        VBox box = new VBox(14.0, picker, flow);
        box.setAlignment(Pos.TOP_CENTER);
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }

    // Widths and heights step with the number, so rows of mixed blocks show how runs and
    // the items in them align.
    private static Label block(int index) {
        Label block = text("#" + (index + 1), "cell", COLORS[index % COLORS.length]);
        double width = 56.0 + (index % 5) * 20.0;
        double height = 40.0 + (index % 3) * 20.0;
        block.setPrefSize(width, height);
        block.setMinSize(width, height);
        block.setMaxSize(width, height);
        return block;
    }
}
