package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator.TitleSide;
import io.github.leewyatt.rxcontrols.event.RXSegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.skins.RXSegmentedStepIndicatorSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Callback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the segment titles of RXSegmentedStepIndicator: when the title factory runs, where the
 * titles sit and how wide they get, their states and events, and what happens to the title nodes
 * when the skin changes.
 */
public class RXSegmentedStepIndicatorTitleTest {

    private static final List<String> NAMES = List.of("Cart", "Shipping", "Payment", "Review");
    private static final double SEGMENT_HEIGHT = 8.0;
    private static final double TITLE_GAP = 6.0;
    private static final double SEGMENT_GAP = 4.0;

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Factory calls ====================

    @Test
    public void noFactoryShowsNoTitlesAndKeepsTheHeight() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            host(indicator, 412.0);
            assertTrue(parts(indicator, "title").isEmpty());
            assertEquals(SEGMENT_HEIGHT, indicator.prefHeight(-1));
            assertEquals(SEGMENT_HEIGHT, indicator.getHeight());
            return null;
        });
    }

    @Test
    public void factoryRunsOncePerSegmentAndOnlyWhenTheSegmentsChange() throws Exception {
        onFx(() -> {
            List<Integer> calls = new ArrayList<>();
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(i -> {
                calls.add(i);
                return new Label(NAMES.get(i));
            });
            StackPane root = host(indicator, 412.0);
            assertEquals(List.of(0, 1, 2, 3), calls);

            indicator.setSelectedIndex(2);
            indicator.setSegmentProgress(0.5);
            root.resize(300.0, 120.0);
            layout(root);
            assertEquals(List.of(0, 1, 2, 3), calls);

            calls.clear();
            indicator.setStepCount(2);
            assertEquals(List.of(0, 1), calls);

            List<Integer> otherCalls = new ArrayList<>();
            indicator.setTitleFactory(i -> {
                otherCalls.add(i);
                return new Label(NAMES.get(i));
            });
            assertEquals(List.of(0, 1), otherCalls);
            assertEquals(List.of(0, 1), calls);
            return null;
        });
    }

    @Test
    public void nullTitleLeavesItsSlotEmpty() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(i -> i == 1 ? null : new Label(NAMES.get(i)));
            host(indicator, 412.0);
            List<Region> titles = parts(indicator, "title");
            assertEquals(NAMES.size(), titles.size());
            assertTrue(titles.get(1).getChildrenUnmodifiable().isEmpty());
            assertEquals("Payment", ((Label) titles.get(2).getChildrenUnmodifiable().get(0)).getText());
            return null;
        });
    }

    @Test
    public void factoryWithoutAnyTitleAddsNoSpace() throws Exception {
        onFx(() -> {
            // Gaps this wide (3 x 60) pass the 150px default, so a preferred width
            // that still counted the empty titles would differ.
            RXSegmentedStepIndicator plain = new RXSegmentedStepIndicator(NAMES.size());
            plain.setSegmentGap(60.0);
            host(plain, 412.0);

            for (TitleSide side : TitleSide.values()) {
                RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
                indicator.setSegmentGap(60.0);
                indicator.setTitleSide(side);
                indicator.setTitleFactory(i -> null);
                host(indicator, 412.0);

                assertEquals(plain.prefHeight(-1), indicator.prefHeight(-1), side + " pref height");
                assertEquals(plain.prefWidth(-1), indicator.prefWidth(-1), side + " pref width");
                assertEquals(0.0, parts(indicator, "track").get(0).getLayoutY(), side + " segment y");
                for (Region title : parts(indicator, "title")) {
                    assertEquals(0.0, title.getHeight(), side + " title box height");
                }
            }
            return null;
        });
    }

    @Test
    public void titleRowFollowsATitleNodeMovedAwayLater() throws Exception {
        onFx(() -> {
            Label only = new Label("Shipping");
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleSide(TitleSide.TOP);
            indicator.setTitleFactory(i -> i == 1 ? only : null);
            StackPane root = host(indicator, 412.0);
            assertTrue(parts(indicator, "track").get(0).getLayoutY() > 0.0);

            // The application takes its node back.
            new StackPane(only);
            layout(root);
            assertEquals(SEGMENT_HEIGHT, indicator.prefHeight(-1));
            assertEquals(SEGMENT_HEIGHT, indicator.getHeight());
            assertEquals(0.0, parts(indicator, "track").get(0).getLayoutY());

            indicator.setTitleFactory(i -> new Label(NAMES.get(i)));
            layout(root);
            assertTrue(parts(indicator, "track").get(0).getLayoutY() > 0.0);
            assertTrue(indicator.getHeight() > SEGMENT_HEIGHT + TITLE_GAP);
            return null;
        });
    }

    @Test
    public void titleNodeWithoutHeightKeepsTheGap() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(i -> i == 2 ? new Region() : null);
            host(indicator, 412.0);
            assertEquals(SEGMENT_HEIGHT + TITLE_GAP, indicator.prefHeight(-1));
            return null;
        });
    }

    @Test
    public void throwingFactoryIsReportedAndLeavesThatTitleEmpty() throws Exception {
        onFx(() -> {
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            List<Throwable> reported = new ArrayList<>();
            thread.setUncaughtExceptionHandler((t, e) -> reported.add(e));
            try {
                RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
                IllegalStateException failure = new IllegalStateException("no title");
                indicator.setTitleFactory(i -> {
                    if (i == 2) {
                        throw failure;
                    }
                    return new Label(NAMES.get(i));
                });
                host(indicator, 412.0);
                assertEquals(List.of(failure), reported);
                List<Region> titles = parts(indicator, "title");
                assertTrue(titles.get(2).getChildrenUnmodifiable().isEmpty());
                assertEquals(1, titles.get(3).getChildrenUnmodifiable().size());
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }
            return null;
        });
    }

    @Test
    public void stepCountChangedByTheFactoryIsPickedUp() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            boolean[] changed = {false};
            indicator.setTitleFactory(i -> {
                if (!changed[0]) {
                    changed[0] = true;
                    indicator.setStepCount(2);
                }
                return new Label(NAMES.get(i));
            });
            host(indicator, 412.0);
            assertEquals(2, parts(indicator, "track").size());
            assertEquals(2, parts(indicator, "title").size());
            assertEquals(6, indicator.getChildrenUnmodifiable().size());
            return null;
        });
    }

    // ==================== Placement and size ====================

    @Test
    public void titleBoxesSpanTheirSegments() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            host(indicator, 412.0);
            List<Region> tracks = parts(indicator, "track");
            List<Region> titles = parts(indicator, "title");
            for (int i = 0; i < NAMES.size(); i++) {
                Region track = tracks.get(i);
                Region title = titles.get(i);
                assertEquals(100.0, track.getWidth());
                assertEquals(track.getLayoutX(), title.getLayoutX());
                assertEquals(track.getWidth(), title.getWidth());
                assertTrue(label(title).getWidth() <= title.getWidth());
            }
            return null;
        });
    }

    @Test
    public void longTitlesAreTruncatedWithinTheirSegment() throws Exception {
        onFx(() -> {
            List<String> names = List.of("Choose a delivery address", "Pick a payment method",
                    "Review the whole order", "Wait for the confirmation");
            RXSegmentedStepIndicator indicator = titled(names);
            host(indicator, 200.0);
            for (Region title : parts(indicator, "title")) {
                Label label = label(title);
                assertTrue(label.getWidth() <= title.getWidth(), "a title is wider than its segment");
                assertNotEquals(label.getText(), shownText(label), "a long title was not truncated");
                Rectangle clip = (Rectangle) title.getClip();
                assertEquals(title.getWidth(), clip.getWidth());
                assertEquals(title.getHeight(), clip.getHeight());
            }
            return null;
        });
    }

    @Test
    public void titlesSitBelowByDefaultAndAboveOnRequest() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            StackPane root = host(indicator, 412.0);
            Region track = parts(indicator, "track").get(0);
            Region title = parts(indicator, "title").get(0);
            assertEquals(track.getLayoutY() + SEGMENT_HEIGHT + TITLE_GAP, title.getLayoutY());

            indicator.setTitleSide(TitleSide.TOP);
            layout(root);
            assertEquals(title.getLayoutY() + title.getHeight() + TITLE_GAP, track.getLayoutY());

            indicator.setTitleSide(null);
            layout(root);
            assertEquals(track.getLayoutY() + SEGMENT_HEIGHT + TITLE_GAP, title.getLayoutY());
            return null;
        });
    }

    @Test
    public void preferredSizeShowsEveryTitleInFull() throws Exception {
        onFx(() -> {
            List<String> names = List.of("Choose an address", "Payment", "Review", "Done");
            RXSegmentedStepIndicator indicator = titled(names);
            indicator.setMaxWidth(Region.USE_PREF_SIZE);
            host(indicator, 900.0);
            List<Region> titles = parts(indicator, "title");
            double widest = 0.0;
            for (Region title : titles) {
                widest = Math.max(widest, title.prefWidth(-1));
                assertEquals(label(title).getText(), shownText(label(title)), "a title is truncated");
            }
            assertEquals(Math.ceil(widest) * names.size() + SEGMENT_GAP * (names.size() - 1),
                    indicator.getWidth());
            assertEquals(SEGMENT_HEIGHT + TITLE_GAP + titles.get(0).getHeight(), indicator.getHeight());

            RXSegmentedStepIndicator shortTitles = titled(List.of("A", "B"));
            shortTitles.setMaxWidth(Region.USE_PREF_SIZE);
            host(shortTitles, 900.0);
            assertEquals(150.0, shortTitles.getWidth());
            return null;
        });
    }

    // ==================== States and events ====================

    @Test
    public void titlesCarryTheSegmentStates() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            indicator.setSelectedIndex(1);
            host(indicator, 412.0);
            List<Region> titles = parts(indicator, "title");
            assertStates(titles.get(0), "first", "completed");
            assertStates(titles.get(1), "selected");
            assertStates(titles.get(2), "upcoming");
            assertStates(titles.get(3), "upcoming", "last");

            indicator.setSelectedIndex(3);
            assertStates(titles.get(1), "completed");
            assertStates(titles.get(3), "selected", "last");
            return null;
        });
    }

    @Test
    public void clickingOrEnteringATitleReportsItsSegment() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            host(indicator, 412.0);
            List<Integer> clicked = new ArrayList<>();
            List<Integer> entered = new ArrayList<>();
            indicator.setOnSegmentClicked(e -> clicked.add(e.getSegmentIndex()));
            indicator.addEventHandler(RXSegmentInteractionEvent.ENTERED, e -> entered.add(e.getSegmentIndex()));
            List<Region> titles = parts(indicator, "title");

            Event.fireEvent(label(titles.get(2)), mouse(MouseEvent.MOUSE_CLICKED));
            Event.fireEvent(titles.get(1), mouse(MouseEvent.MOUSE_ENTERED));

            assertEquals(List.of(2), clicked);
            assertEquals(List.of(1), entered);
            return null;
        });
    }

    // ==================== CSS ====================

    @Test
    public void titleSideAndGapAreStyleable() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            indicator.setStyle("-rx-title-side: top; -rx-title-gap: 10px;");
            host(indicator, 412.0);
            assertEquals(TitleSide.TOP, indicator.getTitleSide());
            assertEquals(10.0, indicator.getTitleGap());
            Region track = parts(indicator, "track").get(0);
            Region title = parts(indicator, "title").get(0);
            assertEquals(title.getLayoutY() + title.getHeight() + 10.0, track.getLayoutY());
            return null;
        });
    }

    @Test
    public void upcomingTitlesAreClearlyLighterThanCompletedOnesUnderModena() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            indicator.setSelectedIndex(1);
            host(indicator, 412.0);
            List<Region> titles = parts(indicator, "title");
            Color completed = (Color) label(titles.get(0)).getTextFill();
            Color upcoming = (Color) label(titles.get(2)).getTextFill();
            // On a light Modena host the default and the secondary text colors are both #333.
            assertTrue(upcoming.getBrightness() - completed.getBrightness() >= 0.2,
                    "upcoming " + upcoming + " is too close to completed " + completed);
            assertTrue(upcoming.getBrightness() <= 0.5, "upcoming " + upcoming + " is too faint to read");
            return null;
        });
    }

    @Test
    public void upcomingTitlesKeepModenasSecondaryTextOnAMidGrayHost() throws Exception {
        onFx(() -> {
            RXSegmentedStepIndicator indicator = titled(NAMES);
            indicator.setSelectedIndex(1);
            StackPane root = new StackPane(indicator);
            // Background brightness between 46% and 59%: Modena draws black text and #333 secondary text.
            root.setStyle("-fx-base: #6b6b6b;");
            new Scene(root, 412.0, 120.0);
            layout(root);
            List<Region> titles = parts(indicator, "title");
            assertEquals(Color.BLACK, label(titles.get(0)).getTextFill());
            assertEquals(Color.web("#333333"), label(titles.get(2)).getTextFill());
            return null;
        });
    }

    // ==================== Skin lifecycle ====================

    @Test
    public void disposingTheSkinReleasesTheTitleNodes() throws Exception {
        onFx(() -> {
            List<Label> labels = labels(NAMES);
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(labels::get);
            host(indicator, 412.0);
            indicator.setSkin(null);
            for (Label label : labels) {
                assertNull(label.getParent());
            }
            assertTrue(indicator.getChildrenUnmodifiable().isEmpty());
            return null;
        });
    }

    @Test
    public void replacementSkinTakesOverCachedTitles() throws Exception {
        onFx(() -> {
            List<Label> labels = labels(NAMES);
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(labels::get);
            StackPane root = host(indicator, 412.0);

            // A skin of the installed class is ignored by setSkin, hence the subclass.
            indicator.setSkin(new RXSegmentedStepIndicatorSkin(indicator) {
            });
            layout(root);

            List<Region> titles = parts(indicator, "title");
            assertEquals(NAMES.size(), titles.size());
            assertEquals(NAMES.size() * 3, indicator.getChildrenUnmodifiable().size());
            for (int i = 0; i < labels.size(); i++) {
                assertSame(titles.get(i), labels.get(i).getParent());
            }
            return null;
        });
    }

    @Test
    public void ignoredSkinOfTheInstalledClassLeavesTheTitlesAlone() throws Exception {
        onFx(() -> {
            List<Label> labels = labels(NAMES);
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            indicator.setTitleFactory(labels::get);
            host(indicator, 412.0);
            List<Parent> parents = new ArrayList<>();
            for (Label label : labels) {
                parents.add(label.getParent());
            }

            indicator.setSkin(new RXSegmentedStepIndicatorSkin(indicator));

            for (int i = 0; i < labels.size(); i++) {
                assertSame(parents.get(i), labels.get(i).getParent());
            }
            return null;
        });
    }

    @Test
    public void skinDisposedByTheFactoryKeepsNoTitles() throws Exception {
        onFx(() -> {
            List<Label> labels = labels(NAMES);
            RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(NAMES.size());
            StackPane root = host(indicator, 412.0);
            indicator.setTitleFactory(i -> {
                if (i == 1) {
                    indicator.setSkin(null);
                }
                return labels.get(i);
            });
            layout(root);
            assertTrue(indicator.getChildrenUnmodifiable().isEmpty());
            for (Label label : labels) {
                assertNull(label.getParent());
            }
            return null;
        });
    }

    // ==================== Helpers ====================

    private static RXSegmentedStepIndicator titled(List<String> names) {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(names.size());
        Callback<Integer, Node> factory = i -> new Label(names.get(i));
        indicator.setTitleFactory(factory);
        return indicator;
    }

    private static List<Label> labels(List<String> names) {
        List<Label> labels = new ArrayList<>();
        for (String name : names) {
            labels.add(new Label(name));
        }
        return labels;
    }

    private static StackPane host(RXSegmentedStepIndicator indicator, double width) {
        StackPane root = new StackPane(indicator);
        new Scene(root, width, 120.0);
        layout(root);
        return root;
    }

    private static void layout(Parent root) {
        root.applyCss();
        root.layout();
        root.applyCss();
        root.layout();
    }

    private static List<Region> parts(RXSegmentedStepIndicator indicator, String styleClass) {
        List<Region> parts = new ArrayList<>();
        for (Node child : indicator.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(styleClass)) {
                parts.add((Region) child);
            }
        }
        return parts;
    }

    private static Label label(Region title) {
        return (Label) title.getChildrenUnmodifiable().get(0);
    }

    private static String shownText(Label label) {
        return ((Text) label.lookup(".text")).getText();
    }

    private static void assertStates(Region title, String... states) {
        List<String> expected = List.of(states);
        for (String state : List.of("first", "last", "completed", "selected", "upcoming")) {
            boolean on = title.getPseudoClassStates().contains(PseudoClass.getPseudoClass(state));
            if (expected.contains(state)) {
                assertTrue(on, ":" + state + " is missing");
            } else {
                assertFalse(on, ":" + state + " is unexpected");
            }
        }
    }

    private static MouseEvent mouse(EventType<MouseEvent> type) {
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw (Error) cause;
        }
    }
}
