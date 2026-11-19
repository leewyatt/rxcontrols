package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.Choice;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.COMPACT_PROFILE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.breakpointBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.menu;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.widthFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXMasonryPane page: a photo wall in a resizable frame with menus for the column
 * width, a column cap and the gap, a featured photo spanning two columns and photos
 * that fade in and out; fixed-width columns aligned in a wider pane; and a notes board
 * with a column count per breakpoint.
 */
public final class RXMasonryPaneSamples {

    private static final double COLUMN_WIDTH = 150.0;
    private static final double FIXED_FRAME_WIDTH = 560.0;
    private static final double FIXED_COLUMN_WIDTH = 100.0;
    private static final String FILL = "Fill width";
    private static final String[] CAPTIONS = {"Palm beach", "Maple bridge", "River walk", "Cherry lane",
        "Turquoise bay", "Old bridge", "Riverside town", "Blossom path"};
    private static final double[] PHOTO_HEIGHTS = {120.0, 170.0, 100.0, 150.0, 110.0, 160.0, 130.0, 100.0};
    private static final String[] NOTE_COLORS = {"amber", "lime", "sky", "pink", "violet", "orange", "teal"};
    private static final String[] NOTES = {
        "Buy oat milk",
        "Call the landlord about the leaking tap in the kitchen before Friday",
        "Book flights to Porto",
        "Ideas for the talk: start with the demo, then explain the layout model, end with questions",
        "Water the plants",
        "Return library books",
        "Draft the newsletter and pick three photos from the hike",
        "Dentist, Tuesday 9:30"};

    private RXMasonryPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A masonry layout that stacks children of different heights into equal-width columns, always filling the shortest column next.",
                List.of("Photo walls, pinboards and feeds of cards whose heights differ.",
                        "For thousands of items use the virtualized RXMasonryView; for same-size tiles use RXTilePane."),
                List.of(
                        SampleCard.canvas("Photo wall",
                                "Drag the frame edge: columnWidth sets how many columns fit and the photos glide to their "
                                        + "new columns. Featured spans the first photo over two columns; new photos fade in "
                                        + "and removeAnimated fades one out.",
                                photoWall()),
                        SampleCard.canvas("Fixed-width columns",
                                "With fillWidth off the columns keep columnWidth, and alignment places them in the wider pane.",
                                fixedColumns()),
                        SampleCard.canvas("Columns per breakpoint",
                                "setXs(1), setSm(2), setMd(3) and setLg(4) fix the column count for each breakpoint of the pane's width.",
                                breakpoints())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Photo wall ====================

    private static Node photoWall() {
        RXMasonryPane wall = new RXMasonryPane();
        wall.setColumnWidth(COLUMN_WIDTH);
        wall.setHgap(10.0);
        wall.setVgap(10.0);
        // The photos still on the wall; one fading out has left it already, so Remove last
        // moves on to the next one instead of picking the leaving photo again.
        ObservableList<Node> photos = FXCollections.observableArrayList();
        int[] next = {0};
        for (; next[0] < CAPTIONS.length; next[0]++) {
            photos.add(photo(next[0]));
        }
        wall.getChildren().setAll(photos);
        Label columns = text("", "frame-size");
        columns.textProperty().bind(Bindings.createStringBinding(
                () -> wall.getActualColumnCount() + " columns", wall.actualColumnCountProperty()));
        columns.setMinWidth(Region.USE_PREF_SIZE);

        RXMenuButton width = menu("Column width", 1, wall::setColumnWidth,
                new Choice<>("120", 120.0), new Choice<>("150", COLUMN_WIDTH), new Choice<>("200", 200.0));
        RXMenuButton cap = menu("Max columns", 0, wall::setMaxColumns,
                new Choice<>("Any", 0), new Choice<>("2", 2), new Choice<>("3", 3), new Choice<>("4", 4));
        RXMenuButton gap = menu("Gap", 1, value -> {
                    wall.setHgap(value);
                    wall.setVgap(value);
                },
                new Choice<>("0", 0.0), new Choice<>("10", 10.0), new Choice<>("20", 20.0));
        RXCheckBox featured = new RXCheckBox("Featured spans 2");
        featured.selectedProperty().addListener((obs, was, on) -> {
            if (!photos.isEmpty()) {
                RXMasonryPane.setColumnSpan(photos.get(0), on ? 2 : null);
            }
        });
        RXCheckBox animate = new RXCheckBox("Animate");
        animate.selectedProperty().bindBidirectional(wall.animatedProperty());

        RXButton add = plain("Add photo");
        add.setOnAction(event -> {
            Node photo = photo(next[0]++);
            photos.add(photo);
            wall.getChildren().add(photo);
        });
        RXButton remove = plain("Remove last");
        remove.setOnAction(event -> wall.removeAnimated(photos.remove(photos.size() - 1)));
        remove.disableProperty().bind(Bindings.isEmpty(photos));
        FocusHandOff.onDisable(remove, add);
        for (Region region : List.of(add, remove)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }

        // The layout choices wrap on a narrow card, being a flow pane themselves.
        RXFlowPane choices = new RXFlowPane(8.0, 8.0, width, cap, gap, featured, animate);
        choices.setAnimated(false);
        HBox.setHgrow(choices, Priority.ALWAYS);
        HBox options = new HBox(8.0, choices, add, remove);
        options.setAlignment(Pos.TOP_LEFT);

        VBox frame = widthFrame(wall, 220.0, columns);
        frame.getChildren().add(1, options);
        return frame;
    }

    private static Node photo(int index) {
        int i = index % CAPTIONS.length;
        RXImageView photo = new RXImageView(scenery(i % 4 + 1));
        photo.setPrefHeight(PHOTO_HEIGHTS[i]);
        photo.setMinHeight(PHOTO_HEIGHTS[i]);
        photo.setImageRadius(8.0);
        return new VBox(6.0, photo, text(CAPTIONS[i], "copy"));
    }

    // ==================== Breakpoints ====================

    private static Node breakpoints() {
        RXMasonryPane board = new RXMasonryPane();
        board.setBreakpointProfile(COMPACT_PROFILE);
        board.setXs(1);
        board.setSm(2);
        board.setMd(3);
        board.setLg(4);
        for (int i = 0; i < NOTES.length; i++) {
            Label note = wrapped(NOTES[i], "note-text");
            VBox card = new VBox(note);
            card.getStyleClass().addAll("note", NOTE_COLORS[i % NOTE_COLORS.length]);
            board.getChildren().add(card);
        }
        return widthFrame(board, 260.0, breakpointBadge(board.activeBreakpointProperty()));
    }

    // ==================== Fixed-width columns ====================

    private static Node fixedColumns() {
        RXMasonryPane pane = new RXMasonryPane();
        pane.setColumnWidth(FIXED_COLUMN_WIDTH);
        pane.setHgap(10.0);
        pane.setVgap(10.0);
        String[] colors = {"rose", "amber", "teal", "violet", "sky", "lime", "orange", "indigo", "pink"};
        double[] heights = {60.0, 90.0, 40.0, 70.0, 50.0, 80.0, 45.0, 65.0, 55.0};
        for (int i = 0; i < colors.length; i++) {
            Label block = text("#" + (i + 1), "cell", colors[i]);
            block.setPrefHeight(heights[i]);
            block.setMinHeight(heights[i]);
            // With fillWidth off a child keeps its preferred width, so the block asks for
            // the column width; it still stretches while the columns fill the pane.
            block.setPrefWidth(FIXED_COLUMN_WIDTH);
            block.setMaxWidth(Double.MAX_VALUE);
            pane.getChildren().add(block);
        }
        pane.getStyleClass().add("flow-frame");
        pane.setPrefWidth(FIXED_FRAME_WIDTH);
        pane.setMaxWidth(Region.USE_PREF_SIZE);

        // Fill width stretches the columns over the pane; the others keep columnWidth and
        // place the columns by alignment.
        RXSegmentedControl<String> picker = picker(choice -> {
            pane.setFillWidth(FILL.equals(choice));
            pane.setAlignment(switch (choice) {
                case "Center" -> Pos.TOP_CENTER;
                case "Right" -> Pos.TOP_RIGHT;
                default -> Pos.TOP_LEFT;
            });
        }, FILL, "Left", "Center", "Right");
        VBox box = new VBox(14.0, picker, pane);
        box.setAlignment(Pos.TOP_CENTER);
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }
}
