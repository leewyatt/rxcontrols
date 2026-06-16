package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineItem.Type;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTimelineViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXTimelineView}.
 *
 * <p>Shows a realistic order-tracking activity stream: title plus timestamp on
 * every row, a description on a few, and one {@code :success} row — the default
 * appearance with no styling code. It is wrapped in a {@code ScrollPane} with
 * {@code setFitToWidth(true)} to demonstrate the recommended scrolling setup.
 * For a full property explorer see {@link RXTimelineViewShowcase}.
 */
public class RXTimelineViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXTimelineView timeline = new RXTimelineView(
                event("Order placed", "2026-06-12 09:24",
                        "Order #20260612-0098 created, awaiting payment.", Type.PRIMARY),
                event("Payment confirmed", "2026-06-12 09:31",
                        "Paid via credit card.", Type.SUCCESS),
                event("Packed", "2026-06-12 14:05", null, null),
                event("Shipped", "2026-06-13 08:40",
                        "Handed to courier, tracking SF1234567890.", null),
                event("Out for delivery", "2026-06-14 07:12", null, null),
                event("Delivered", "2026-06-14 11:58",
                        "Signed for at the front desk.", Type.SUCCESS));

        StackPane content = new StackPane(timeline);
        content.setPadding(new Insets(28.0));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        primaryStage.setScene(new Scene(scroll, 460, 470));
        primaryStage.setTitle("RXTimelineView Demo");
        primaryStage.show();
    }

    private static RXTimelineItem event(String title, String timestamp, String description,
                                        Type type) {
        RXTimelineItem item = new RXTimelineItem(title, timestamp);
        if (description != null) {
            item.setDescription(description);
        }
        if (type != null) {
            item.setType(type);
        }
        return item;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
