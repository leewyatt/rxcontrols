package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.RXSmoothScrollSupport;
import io.github.leewyatt.rxcontrols.RXSmoothScroller;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.iconBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.wrapped;

/**
 * The RXSmoothScroller page: the same article in a plain and a smooth scroll pane, the
 * two scrolling modes, chained and contained scrolling inside the page, a horizontal
 * shelf and an adjustable wheel speed.
 */
public final class RXSmoothScrollerSamples {

    private static final double VIEW_HEIGHT = 240.0;
    private static final String[] COLORS = {"rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet",
        "pink"};
    private static final String[] PARAGRAPHS = {
        "We left the city before sunrise, when the streets were still blue and the bakeries were only just lighting their ovens.",
        "The road climbed slowly. Pine trees closed in on both sides, and every bend opened a new view of the valley below.",
        "By nine we reached the lake. The water was so still that the mountains seemed to stand on their own reflection.",
        "We rented a small wooden boat and rowed to the far shore, where a family of ducks escorted us with loud opinions.",
        "Lunch was bread, cheese and peaches from a roadside stand, eaten on a warm rock with our feet in the cold water.",
        "In the afternoon the clouds rolled in. We watched the rain move across the lake like a grey curtain being drawn.",
        "The cabin had a stove, two chairs and a shelf of old paperbacks. We read until the fire burned down to embers.",
        "The next morning the air smelled of wet pine. A deer crossed the path, looked at us for a long second, and left.",
        "On the way home we stopped at every viewpoint, as if we could carry a little more of the place back with us.",
        "Back in the city, the noise felt louder than before. We already know where we are going next summer."};
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
                        SampleCard.canvas("Plain or smooth",
                                "The same article twice: wheel over the left pane and it jumps, over the right one and it glides.",
                                compare()),
                        SampleCard.canvas("Target or momentum",
                                "TARGET eases to where each wheel step points; MOMENTUM, the default, turns the steps into speed that coasts on after the wheel stops.",
                                modes()),
                        SampleCard.canvas("Inside a scrolling page",
                                "At the end of the list CHAIN, the default, hands the wheel on to the page; CONTAIN keeps it in the list.",
                                boundaries()),
                        SampleCard.medium("Horizontal shelf",
                                "axis HORIZONTAL drives only the sideways scroll; Shift with the wheel or a sideways trackpad swipe moves the shelf.",
                                shelf()),
                        SampleCard.medium("Wheel speed",
                                "wheelMultiplier scales how far each wheel step travels; drag the slider and scroll the list.",
                                speed())),
                MotionKit.STYLESHEET);
    }

    // ==================== Plain or smooth ====================

    private static Node compare() {
        ScrollPane plain = articlePane();
        ScrollPane smooth = articlePane();
        RXSmoothScrollSupport.install(smooth);
        return sideBySide(labeled("ScrollPane", plain), labeled("With RXSmoothScrollSupport.install", smooth));
    }

    private static ScrollPane articlePane() {
        VBox article = new VBox(12.0, text("Two days at the lake", "headline"));
        for (String paragraph : PARAGRAPHS) {
            article.getChildren().add(wrapped(paragraph, "copy"));
        }
        article.getStyleClass().add("article");
        return pane(article);
    }

    // ==================== Modes ====================

    private static Node modes() {
        ScrollPane target = songPane();
        RXSmoothScrollSupport.install(target, RXSmoothScrollOptions.builder().mode(SmoothScrollMode.TARGET).build());
        ScrollPane momentum = songPane();
        RXSmoothScrollSupport.install(momentum, RXSmoothScrollOptions.builder().mode(SmoothScrollMode.MOMENTUM).build());
        return sideBySide(labeled("SmoothScrollMode.TARGET", target), labeled("SmoothScrollMode.MOMENTUM", momentum));
    }

    private static ScrollPane songPane() {
        VBox list = new VBox();
        list.getStyleClass().add("list");
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < SONGS.length; i++) {
                int number = round * SONGS.length + i + 1;
                list.getChildren().add(song(number, SONGS[i][0], SONGS[i][1], COLORS[number % COLORS.length]));
            }
        }
        return pane(list);
    }

    private static Node song(int number, String title, String artist, String color) {
        Region cover = new Region();
        cover.getStyleClass().addAll("cover", color);
        cover.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        HBox row = new HBox(10.0, text(String.valueOf(number), "track-number"), cover,
                new VBox(1.0, text(title, "copy"), text(artist, "muted")), spacer(), text("3:" + (10 + number % 50), "muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    // ==================== Boundaries ====================

    private static Node boundaries() {
        ScrollPane chain = commentPane();
        RXSmoothScrollSupport.install(chain);
        ScrollPane contain = commentPane();
        RXSmoothScrollSupport.install(contain, RXSmoothScrollOptions.builder()
                .boundaryPolicy(ScrollBoundaryPolicy.CONTAIN).build());
        return sideBySide(labeled("ScrollBoundaryPolicy.CHAIN", chain),
                labeled("ScrollBoundaryPolicy.CONTAIN", contain));
    }

    private static ScrollPane commentPane() {
        String[] names = {"Mia Chen", "Sam Ortiz", "Lee Wyatt", "Noor Haddad", "Jonas Berg"};
        String[] comments = {"Love this view!", "Which trail is this?", "Adding it to my list.",
            "The light here is amazing.", "We went last spring, highly recommend.", "How long is the hike?",
            "Saving this for later.", "That boat looks perfect."};
        VBox list = new VBox(10.0);
        list.getStyleClass().add("comments");
        for (int i = 0; i < 12; i++) {
            String color = COLORS[i % COLORS.length];
            Region avatar = new Region();
            avatar.getStyleClass().addAll("cover", "round", color);
            avatar.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            HBox comment = new HBox(10.0, avatar, new VBox(1.0, text(names[i % names.length], "copy-strong"),
                    wrapped(comments[i % comments.length], "copy")));
            list.getChildren().add(comment);
        }
        ScrollPane pane = pane(list);
        pane.setPrefHeight(180.0);
        return pane;
    }

    // ==================== Horizontal shelf ====================

    private static Node shelf() {
        HBox albums = new HBox(10.0);
        albums.getStyleClass().add("shelf");
        for (int i = 0; i < SONGS.length; i++) {
            Region cover = new Region();
            cover.getStyleClass().addAll("album", COLORS[i % COLORS.length]);
            VBox album = new VBox(4.0, cover, text(SONGS[i][0], "copy"), text(SONGS[i][1], "muted"));
            albums.getChildren().add(album);
        }
        ScrollPane pane = new ScrollPane(albums);
        pane.getStyleClass().add("smooth-view");
        pane.setFitToHeight(true);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        RXSmoothScrollSupport.install(pane, RXSmoothScrollOptions.builder().axis(ScrollAxis.HORIZONTAL).build());
        return fixedWidth(pane, 250.0);
    }

    // ==================== Wheel speed ====================

    private static Node speed() {
        VBox list = new VBox();
        list.getStyleClass().add("list");
        String[] tasks = {"Pack the tent", "Buy trail snacks", "Charge the camera", "Print the map", "Book the cabin",
            "Check the weather", "Refill water bottles", "Pack rain jackets"};
        for (int i = 0; i < 32; i++) {
            HBox row = new HBox(10.0, iconBadge("check-icon", COLORS[i % COLORS.length], "small"),
                    text(tasks[i % tasks.length], "copy"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            list.getChildren().add(row);
        }
        ScrollPane pane = pane(list);
        pane.setPrefHeight(170.0);
        RXSmoothScroller scroller = RXSmoothScrollSupport.install(pane);

        Slider slider = new Slider(0.5, 3.0, RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER);
        HBox.setHgrow(slider, Priority.ALWAYS);
        Label value = text("", "value");
        value.setMinWidth(Region.USE_PREF_SIZE);
        slider.valueProperty().addListener((obs, old, number) -> {
            scroller.setWheelMultiplier(number.doubleValue());
            value.setText(String.format("%.1fx", number.doubleValue()));
        });
        value.setText(String.format("%.1fx", slider.getValue()));
        HBox control = new HBox(8.0, text("Speed", "muted"), slider, value);
        control.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10.0, control, pane);
        return fixedWidth(box, 250.0);
    }

    // ==================== Helpers ====================

    private static ScrollPane pane(Region content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setPrefHeight(VIEW_HEIGHT);
        pane.getStyleClass().add("smooth-view");
        return pane;
    }

    private static Node labeled(String name, ScrollPane pane) {
        VBox box = new VBox(8.0, text(name, "code"), pane);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setPrefWidth(0.0);
        return box;
    }

    private static Node sideBySide(Node left, Node right) {
        return new HBox(16.0, left, right);
    }
}
