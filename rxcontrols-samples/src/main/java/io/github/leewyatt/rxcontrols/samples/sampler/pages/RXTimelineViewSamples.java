package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineItem.Type;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXTimelineView.Position;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorValue;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXTimelineView page: project milestones laid out horizontally, order tracking
 * with done, current and upcoming steps, an activity feed with times on the opposite
 * side, a release history on alternating sides, and a comment thread with custom
 * content.
 */
public final class RXTimelineViewSamples {

    private static final double VIEW_WIDTH = 400.0;

    private RXTimelineViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A vertical or horizontal sequence of events joined by a line, each with a dot, title and details.",
                List.of("Showing what happened and in which order: order tracking, activity, release history.",
                        "For steps a user still has to go through, use RXSegmentedStepIndicator."),
                List.of(
                        SampleCard.wide("Project milestones",
                                "Orientation HORIZONTAL lays the events out in a row; dotFill and lineFill color single items.",
                                milestones()),
                        SampleCard.large("Order tracking",
                                "Types color the dots, a dotGraphic marks finished steps and hollow dots show steps still to come.",
                                order()),
                        SampleCard.large("Activity feed",
                                "showOppositeContent puts each time on the other side of the line.",
                                activity()),
                        SampleCard.large("Release history",
                                "Position ALTERNATE centers the line and switches sides; click a release to fire onItemClicked.",
                                releases()),
                        SampleCard.large("Comment thread",
                                "setContent replaces the title and details with any node, here an avatar and a message.",
                                comments())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Order tracking ====================

    private static Node order() {
        RXTimelineView timeline = new RXTimelineView(
                done("Order placed", "Mon, Jun 8 · 09:24", "Order #20480 with 3 items."),
                done("Payment confirmed", "Mon, Jun 8 · 09:31", "Paid by card ending in 4242."),
                done("Shipped", "Tue, Jun 9 · 14:05", "Handed to the courier, tracking number 7731 0042."),
                step("Out for delivery", "Today · 08:40", "The courier expects to arrive between 13:00 and 15:00.",
                        Type.PRIMARY, false),
                step("Delivered", "Expected today", null, null, true));
        timeline.setDotSize(18.0);
        timeline.setItemSpacing(14.0);
        return fixed(timeline);
    }

    private static RXTimelineItem done(String title, String time, String description) {
        RXTimelineItem item = step(title, time, description, Type.SUCCESS, false);
        Region check = icon("check-icon");
        item.setDotGraphic(check);
        return item;
    }

    private static RXTimelineItem step(String title, String time, String description, Type type, boolean hollow) {
        RXTimelineItem item = new RXTimelineItem(title, time);
        if (description != null) {
            item.setDescription(description);
        }
        item.setType(type);
        item.setHollow(hollow);
        return item;
    }

    // ==================== Activity feed ====================

    private static Node activity() {
        RXTimelineView timeline = new RXTimelineView(
                feed("09:12", "Lee Wyatt opened the pull request", "Add sticky headers to the list view", Type.PRIMARY),
                feed("09:40", "CI passed", "2,930 tests in 34 s", Type.SUCCESS),
                feed("10:05", "Mia Chen asked for changes", "Please keep the old method for one release", Type.WARNING),
                feed("11:30", "Lee Wyatt pushed 2 commits", null, Type.INFO),
                feed("12:02", "Mia Chen approved", null, Type.SUCCESS),
                feed("12:10", "Merged into develop", null, Type.PRIMARY));
        timeline.setShowOppositeContent(true);
        timeline.setPrefWidth(440.0);
        return timeline;
    }

    private static RXTimelineItem feed(String time, String title, String description, Type type) {
        RXTimelineItem item = new RXTimelineItem(title);
        if (description != null) {
            item.setDescription(description);
        }
        item.setType(type);
        item.setOppositeContent(new Label(time));
        return item;
    }

    // ==================== Release history ====================

    private static Node releases() {
        RXTimelineView timeline = new RXTimelineView(
                release("2.4", "Nov 2026", "Collapsible list groups"),
                release("2.3", "Sep 2026", "Kanban board and masonry view"),
                release("2.2", "Jun 2026", "Date and time pickers"),
                release("2.1", "Mar 2026", "Dark theme"),
                release("2.0", "Jan 2026", "New skins for every control"));
        timeline.setPosition(Position.ALTERNATE);
        timeline.getItems().get(0).setType(Type.SUCCESS);

        Label status = text("Click a release to read its notes", "hint");
        timeline.setOnItemClicked(event -> status.setText("Release notes for "
                + event.getItem().getTitle() + ": " + event.getItem().getDescription()));
        status.setWrapText(true);
        VBox box = new VBox(14.0, timeline, status);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefWidth(VIEW_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXTimelineItem release(String version, String date, String text) {
        RXTimelineItem item = new RXTimelineItem("Version " + version, date);
        item.setDescription(text);
        return item;
    }

    // ==================== Comment thread ====================

    private static Node comments() {
        RXTimelineView timeline = new RXTimelineView(
                comment("Lee Wyatt", "2 h", "The new list headers look great. Can we make them sticky?"),
                comment("Mia Chen", "1 h", "They are sticky by default, try scrolling the contacts list."),
                comment("Lee Wyatt", "45 min", "Oh nice, I missed that. Merging now."),
                comment("Sam Ortega", "5 min", "Released in 2.4"));
        timeline.setDotSize(10.0);
        timeline.setAxisSpacing(14.0);
        return fixed(timeline);
    }

    private static RXTimelineItem comment(String author, String ago, String message) {
        Label avatar = avatar(30.0);
        avatar.setText(initials(author));
        paint(avatar, colorOf(author));
        Label name = text(author, "name");
        Label time = text(ago + " ago", "note");
        HBox meta = new HBox(8.0, name, time);
        meta.setAlignment(Pos.BASELINE_LEFT);
        Label body = text(message, "body");
        body.setWrapText(true);
        VBox bubble = new VBox(3.0, meta, body);
        bubble.getStyleClass().add("bubble");
        HBox row = new HBox(10.0, avatar, bubble);
        row.setAlignment(Pos.TOP_LEFT);

        RXTimelineItem item = new RXTimelineItem();
        item.setContent(row);
        item.setDotFill(colorValue(colorOf(author)));
        return item;
    }

    // ==================== Project milestones ====================

    private static Node milestones() {
        RXTimelineItem kickoff = milestone("Kickoff", "Jan 12", Type.SUCCESS);
        RXTimelineItem research = milestone("Research", "Feb 3", Type.SUCCESS);
        RXTimelineItem design = milestone("Design", "Mar 16", Type.SUCCESS);
        RXTimelineItem beta = milestone("Beta", "May 4", Type.PRIMARY);
        beta.setDescription("In progress");
        RXTimelineItem launch = milestone("Launch", "Jun 22", null);
        launch.setHollow(true);
        launch.setDotFill(Color.web("#f97316"));
        launch.setLineFill(Color.web("#f97316"));
        RXTimelineItem review = milestone("Review", "Aug 1", null);
        review.setHollow(true);

        RXTimelineView timeline = new RXTimelineView(kickoff, research, design, beta, launch, review);
        timeline.setOrientation(Orientation.HORIZONTAL);
        timeline.setItemSpacing(36.0);
        ScrollPane scroll = new ScrollPane(timeline);
        scroll.getStyleClass().add("plain-scroll");
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(540.0);
        scroll.setMaxWidth(Region.USE_PREF_SIZE);
        return scroll;
    }

    private static RXTimelineItem milestone(String title, String date, Type type) {
        RXTimelineItem item = new RXTimelineItem(title, date);
        item.setType(type);
        return item;
    }

    // ==================== Helpers ====================

    private static Node fixed(RXTimelineView timeline) {
        timeline.setPrefWidth(VIEW_WIDTH);
        return timeline;
    }
}
