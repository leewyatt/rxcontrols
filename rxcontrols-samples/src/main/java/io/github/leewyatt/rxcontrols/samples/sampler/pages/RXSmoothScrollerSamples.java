package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.RXSmoothScrollSupport;
import io.github.leewyatt.rxcontrols.RXSmoothScroller;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.Choice;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.menu;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.text;

/**
 * The RXSmoothScroller page: a configurable vertical playlist and a horizontal
 * album shelf that demonstrate the two common installation patterns.
 */
public final class RXSmoothScrollerSamples {

    private static final double PLAYLIST_HEIGHT = 360.0;
    private static final String[] COLORS = {"rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet",
        "pink"};
    private static final String[][] SONGS = {
        {"Paper Boats", "The Quiet Hours"}, {"Glass Harbor", "Northbound"}, {"Late Bloom", "Ada Vale"},
        {"Copper Sky", "The Lanterns"}, {"Small Hours", "Mira Sol"}, {"Tidelines", "Northbound"},
        {"Open Window", "Ada Vale"}, {"Paper Moon Road", "The Quiet Hours"}, {"Slow Rivers", "Mira Sol"},
        {"Field Notes", "The Lanterns"}, {"After the Rain", "Ada Vale"}, {"Blue Hour", "Northbound"}};

    private RXSmoothScrollerSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A handle that adds smooth, animated mouse-wheel scrolling to an ordinary JavaFX ScrollPane through RXSmoothScrollSupport.install.",
                List.of("Long articles, feeds and lists in a ScrollPane where wheel steps should glide instead of jump.",
                        "RXListView, RXTileView and RXMasonryView scroll smoothly on their own and need no installer."),
                List.of(
                        SampleCard.canvas("Tune the glide",
                                "Toggle smooth scrolling, then tune its mode, wheel distance and animation duration.",
                                playlist()),
                        SampleCard.canvas("Horizontal collection",
                                "A horizontal installation maps Shift+wheel or a sideways trackpad gesture to the album shelf.",
                                shelf())),
                MotionKit.STYLESHEET);
    }

    // ==================== Configurable playlist ====================

    private static Node playlist() {
        VBox tracks = new VBox();
        tracks.getStyleClass().add("list");
        for (int round = 0; round < 8; round++) {
            for (int i = 0; i < SONGS.length; i++) {
                int number = round * SONGS.length + i + 1;
                tracks.getChildren().add(song(number, SONGS[i][0], SONGS[i][1],
                        COLORS[number % COLORS.length]));
            }
        }

        ScrollPane pane = verticalPane(tracks);
        RXSmoothScroller scroller = RXSmoothScrollSupport.install(pane);

        RXCheckBox smooth = new RXCheckBox("Smooth");
        smooth.setSelected(scroller.isEnabled());
        smooth.selectedProperty().bindBidirectional(scroller.enabledProperty());
        RXSegmentedControl<SmoothScrollMode> mode = picker(scroller::setMode,
                SmoothScrollMode.MOMENTUM, SmoothScrollMode.TARGET);
        RXMenuButton speed = menu("Speed", 1, scroller::setWheelMultiplier,
                new Choice<>("0.65x", 0.65),
                new Choice<>("1x", RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER),
                new Choice<>("1.75x", 1.75));
        RXMenuButton duration = menu("Duration", 1, scroller::setDuration,
                new Choice<>("120 ms", Duration.millis(120.0)),
                new Choice<>("200 ms", RXSmoothScrollOptions.DEFAULT_DURATION),
                new Choice<>("360 ms", Duration.millis(360.0)));
        for (Node option : List.of(mode, speed, duration)) {
            option.visibleProperty().bind(smooth.selectedProperty());
            option.managedProperty().bind(smooth.selectedProperty());
        }

        RXFlowPane options = new RXFlowPane(8.0, 8.0, smooth, mode, speed, duration);
        options.setAnimated(false);
        options.getStyleClass().add("smooth-toolbar");
        return new VBox(12.0, options, pane);
    }

    private static Node song(int number, String title, String artist, String color) {
        Region cover = new Region();
        cover.getStyleClass().addAll("cover", color);
        cover.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox details = new VBox(1.0, text(title, "copy"), text(artist, "muted"));
        HBox.setHgrow(details, Priority.ALWAYS);
        HBox row = new HBox(10.0, text(String.valueOf(number), "track-number"), cover, details,
                text("3:" + (10 + number % 50), "muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    // ==================== Horizontal shelf ====================

    private static Node shelf() {
        HBox albums = new HBox(12.0);
        albums.getStyleClass().add("shelf");
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < SONGS.length; i++) {
                Region cover = new Region();
                cover.getStyleClass().addAll("album", COLORS[i % COLORS.length]);
                VBox album = new VBox(5.0, cover, text(SONGS[i][0], "copy"), text(SONGS[i][1], "muted"));
                album.getStyleClass().add("album-card");
                albums.getChildren().add(album);
            }
        }
        ScrollPane pane = new ScrollPane(albums);
        pane.getStyleClass().add("smooth-view");
        pane.setFitToHeight(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setPrefHeight(178.0);
        RXSmoothScrollSupport.install(pane, RXSmoothScrollOptions.builder()
                .axis(ScrollAxis.HORIZONTAL)
                .wheelMultiplier(1.25)
                .build());

        HBox hint = new HBox(8.0, text("Shift + wheel", "gesture-chip"),
                text("or swipe sideways over the shelf", "muted"));
        hint.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10.0, hint, pane);
    }

    // ==================== Helpers ====================

    private static ScrollPane verticalPane(Region content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setPrefHeight(PLAYLIST_HEIGHT);
        pane.getStyleClass().add("smooth-view");
        return pane;
    }
}
