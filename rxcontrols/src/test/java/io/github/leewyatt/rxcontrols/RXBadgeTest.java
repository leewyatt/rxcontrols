package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXBadge.Overlap;
import io.github.leewyatt.rxcontrols.RXBadge.Position;
import io.github.leewyatt.rxcontrols.RXBadge.Variant;
import io.github.leewyatt.rxcontrols.skins.RXBadgeSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXBadge}: the rules deciding what the indicator shows, its
 * pseudo-classes and CSS metadata, the placement of the indicator on the
 * content, the pass-through sizing, the badge without content, content
 * replacement, mouse transparency and disposal.
 */
public class RXBadgeTest {

    private static final double EPSILON = 1.0e-6;
    private static final double CONTENT_WIDTH = 100.0;
    private static final double CONTENT_HEIGHT = 60.0;
    private static final double CIRCULAR_INSET = (1.0 - Math.cos(Math.PI / 4.0)) / 2.0;
    private static final PseudoClass DOT = PseudoClass.getPseudoClass("dot");
    private static final PseudoClass STANDALONE = PseudoClass.getPseudoClass("standalone");

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

    // ==================== Display rules ====================

    /** Verifies the defaults: no count, cap 99, standard pill at the top end corner, nothing shown. */
    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            assertNull(badge.getContent());
            assertEquals(0, badge.getCount());
            assertEquals(99, badge.getMaxCount());
            assertNull(badge.getText());
            assertFalse(badge.isShowZero());
            assertTrue(badge.isIndicatorVisible());
            assertEquals(Variant.STANDARD, badge.getVariant());
            assertEquals(Position.TOP_END, badge.getPosition());
            assertEquals(Overlap.RECTANGULAR, badge.getOverlap());
            assertEquals(0.0, badge.getOffsetX(), EPSILON);
            assertEquals(0.0, badge.getOffsetY(), EPSILON);
            assertTrue(badge.isAnimated());
            assertFalse(badge.isPulsing());
            assertFalse(badge.isFocusTraversable());
            assertFalse(badge.isPickOnBounds());
            assertHidden(badge);
        });
    }

    /** Verifies counts up to the cap show as numbers and larger ones as the cap plus a plus sign. */
    @Test
    public void countShowsUpToTheCap() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            int[] counts = {1, 9, 10, 99, 100, Integer.MAX_VALUE};
            String[] expected = {"1", "9", "10", "99", "99+", "99+"};
            for (int i = 0; i < counts.length; i++) {
                badge.setCount(counts[i]);
                assertShowing(badge, expected[i]);
            }
            badge.setMaxCount(9);
            badge.setCount(10);
            assertShowing(badge, "9+");
            badge.setMaxCount(Integer.MAX_VALUE);
            badge.setCount(123456);
            assertShowing(badge, "123456");
        });
    }

    /** Verifies zero is hidden unless showZero is on, and a negative count is always hidden. */
    @Test
    public void zeroAndNegativeCountsAreHidden() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            badge.setCount(5);
            badge.setCount(0);
            assertHidden(badge);
            badge.setShowZero(true);
            assertShowing(badge, "0");
            badge.setCount(-1);
            assertHidden(badge);
        });
    }

    /** Verifies a maximum below one falls back to 99 without changing the property. */
    @Test
    public void maxCountBelowOneUsesTheDefault() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            badge.setCount(500);
            for (int max : new int[]{0, -5}) {
                badge.setMaxCount(max);
                assertEquals(max, badge.getMaxCount());
                assertShowing(badge, "99+");
            }
        });
    }

    /** Verifies a non-empty text replaces the count, and an empty or null text gives it back. */
    @Test
    public void textTakesPrecedenceOverTheCount() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            badge.setText("NEW");
            assertShowing(badge, "NEW");
            badge.setCount(3);
            assertShowing(badge, "NEW");
            badge.setText("");
            assertShowing(badge, "3");
            badge.setText("HOT");
            badge.setText(null);
            assertShowing(badge, "3");
        });
    }

    /** Verifies the dot shows without text whatever the count and text are, and a null variant is standard. */
    @Test
    public void dotIgnoresCountAndText() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            badge.setVariant(Variant.DOT);
            assertShowing(badge, "");
            assertTrue(badge.getPseudoClassStates().contains(DOT));
            badge.setCount(7);
            badge.setText("NEW");
            assertShowing(badge, "");

            badge.setVariant(null);
            assertShowing(badge, "NEW");
            assertFalse(badge.getPseudoClassStates().contains(DOT));
        });
    }

    /** Verifies a bound count drives the indicator. */
    @Test
    public void boundCountDrivesTheIndicator() throws Exception {
        runOnFx(() -> {
            SimpleIntegerProperty unread = new SimpleIntegerProperty(3);
            RXBadge badge = new RXBadge();
            badge.countProperty().bind(unread);
            assertShowing(badge, "3");
            unread.set(0);
            assertHidden(badge);
            unread.set(250);
            assertShowing(badge, "99+");
        });
    }

    /** Verifies indicatorVisible=false hides a count, a text and a dot. */
    @Test
    public void indicatorVisibleFalseHidesEverything() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            badge.setCount(4);
            badge.setIndicatorVisible(false);
            assertHidden(badge);
            badge.setText("NEW");
            assertHidden(badge);
            badge.setVariant(Variant.DOT);
            assertHidden(badge);
            badge.setIndicatorVisible(true);
            assertShowing(badge, "");
        });
    }

    /** Verifies listeners of indicatorShowing never read an empty text for a shown count. */
    @Test
    public void textIsSetBeforeShowingAndClearedAfterHiding() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            AtomicReference<String> seenOnShow = new AtomicReference<>();
            AtomicReference<String> seenOnHide = new AtomicReference<>();
            badge.indicatorShowingProperty().addListener((obs, was, showing) ->
                    (showing ? seenOnShow : seenOnHide).set(badge.getIndicatorText()));
            badge.setCount(3);
            assertEquals("3", seenOnShow.get());
            badge.setCount(0);
            assertEquals("3", seenOnHide.get());
            assertEquals("", badge.getIndicatorText());
        });
    }

    /** Verifies :standalone follows the absence of content. */
    @Test
    public void standalonePseudoClassFollowsTheContent() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            assertTrue(badge.getPseudoClassStates().contains(STANDALONE));
            badge.setContent(new Region());
            assertFalse(badge.getPseudoClassStates().contains(STANDALONE));
            badge.setContent(null);
            assertTrue(badge.getPseudoClassStates().contains(STANDALONE));
            assertFalse(new RXBadge(new Region()).getPseudoClassStates().contains(STANDALONE));
        });
    }

    // ==================== CSS ====================

    /** Verifies the styleable properties are exposed under their CSS names. */
    @Test
    public void cssMetaDataListsTheStyleableProperties() throws Exception {
        runOnFx(() -> {
            Set<String> names = RXBadge.getClassCssMetaData().stream()
                    .map(CssMetaData::getProperty)
                    .collect(Collectors.toSet());
            assertTrue(names.containsAll(Set.of("-rx-variant", "-rx-position", "-rx-overlap",
                    "-rx-offset-x", "-rx-offset-y", "-rx-animated", "-rx-pulsing")), names.toString());
            RXBadge badge = new RXBadge();
            for (CssMetaData<? extends Styleable, ?> metaData : badge.getCssMetaData()) {
                if (metaData.getProperty().startsWith("-rx-")) {
                    assertTrue(settable(metaData, badge), metaData.getProperty());
                }
            }
        });
    }

    /** Verifies a stylesheet sets variant, position, overlap, offsets, animated and pulsing. */
    @Test
    public void stylesheetSetsTheStyleableProperties() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge(content());
            StackPane root = new StackPane(badge);
            root.getStylesheets().add("data:text/css,.rx-badge{-rx-variant:dot;-rx-position:bottom-start;"
                    + "-rx-overlap:circular;-rx-offset-x:3;-rx-offset-y:4;-rx-animated:false;-rx-pulsing:true;}");
            new Scene(root);
            root.applyCss();
            assertEquals(Variant.DOT, badge.getVariant());
            assertTrue(badge.getPseudoClassStates().contains(DOT));
            assertTrue(badge.lookup(".indicator").getPseudoClassStates().contains(DOT));
            assertEquals(Position.BOTTOM_START, badge.getPosition());
            assertEquals(Overlap.CIRCULAR, badge.getOverlap());
            assertEquals(3.0, badge.getOffsetX(), EPSILON);
            assertEquals(4.0, badge.getOffsetY(), EPSILON);
            assertFalse(badge.isAnimated());
            assertTrue(badge.isPulsing());
            assertTrue(badge.isIndicatorShowing());
        });
    }

    // ==================== Sizing ====================

    /** Verifies the badge reports the content's sizes and bias, and adds its own padding. */
    @Test
    public void sizeIsThatOfTheContentPlusPadding() throws Exception {
        runOnFx(() -> {
            Region content = new Region();
            content.setMinSize(20.0, 10.0);
            content.setPrefSize(CONTENT_WIDTH, CONTENT_HEIGHT);
            content.setMaxSize(300.0, 200.0);
            RXBadge badge = new RXBadge(content);
            badge.setCount(1000);
            inScene(badge);

            assertSize(badge, 20.0, 10.0, CONTENT_WIDTH, CONTENT_HEIGHT, 300.0, 200.0);
            badge.setPadding(new Insets(4.0, 6.0, 8.0, 10.0));
            assertSize(badge, 36.0, 22.0, CONTENT_WIDTH + 16.0, CONTENT_HEIGHT + 12.0, 316.0, 212.0);

            content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            assertEquals(Double.MAX_VALUE, badge.maxWidth(-1.0), 0.0);
            assertEquals(Double.MAX_VALUE, badge.maxHeight(-1.0), 0.0);

            Label wrapped = new Label("a text long enough to wrap over several lines when narrow");
            wrapped.setWrapText(true);
            badge.setContent(wrapped);
            assertEquals(Orientation.HORIZONTAL, badge.getContentBias());
            badge.setContent(null);
            assertNull(badge.getContentBias());
        });
    }

    /** Verifies the baseline is that of the content below the top padding, or of the indicator alone. */
    @Test
    public void baselineIsThatOfTheContent() throws Exception {
        runOnFx(() -> {
            Label label = new Label("Inbox");
            RXBadge badge = new RXBadge(label);
            badge.setCount(3);
            badge.setPadding(new Insets(10.0));
            laidOut(badge);
            assertEquals(10.0 + label.getBaselineOffset(), badge.getBaselineOffset(), EPSILON);

            badge.setContent(content());
            badge.layout();
            assertEquals(Node.BASELINE_OFFSET_SAME_AS_HEIGHT, badge.getBaselineOffset(), 0.0);

            badge.setContent(null);
            badge.layout();
            assertEquals(10.0 + badge.lookup(".indicator").getBaselineOffset(), badge.getBaselineOffset(), EPSILON);
        });
    }

    /** Verifies a badge without content is as large as its indicator and empty when nothing is shown. */
    @Test
    public void standaloneBadgeIsSizedToTheIndicator() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            inScene(badge);
            assertSize(badge, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

            badge.setCount(5);
            badge.applyCss();
            double height = badge.prefHeight(-1.0);
            assertTrue(height > 0.0);
            assertEquals(height, badge.prefWidth(-1.0), EPSILON, "one character gives a circle");
            assertEquals(height, badge.minHeight(-1.0), EPSILON);
            assertEquals(height, badge.maxHeight(-1.0), EPSILON);

            badge.setCount(1000);
            assertTrue(badge.prefWidth(-1.0) > height, "99+ is wider than tall");
            assertEquals(height, badge.prefHeight(-1.0), EPSILON);

            badge.autosize();
            badge.layout();
            Node indicator = badge.lookup(".indicator");
            assertEquals(0.0, indicator.getLayoutX(), EPSILON);
            assertEquals(0.0, indicator.getLayoutY(), EPSILON);
            assertEquals(badge.getWidth(), indicator.getLayoutBounds().getWidth(), EPSILON);

            badge.setCount(0);
            assertSize(badge, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        });
    }

    /** Verifies the dot is a small square region smaller than a count pill. */
    @Test
    public void dotIsSmallerThanACount() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge();
            inScene(badge);
            badge.setCount(5);
            badge.applyCss();
            double pill = badge.prefHeight(-1.0);
            badge.setVariant(Variant.DOT);
            badge.applyCss();
            double dot = badge.prefHeight(-1.0);
            assertTrue(dot > 0.0 && dot < pill, dot + " vs " + pill);
            assertEquals(dot, badge.prefWidth(-1.0), EPSILON);

            badge.setVariant(Variant.STANDARD);
            badge.applyCss();
            assertEquals(pill, badge.prefHeight(-1.0), EPSILON);
        });
    }

    /** Verifies a hidden dot keeps its dot shape, and takes the new shape when it shows again. */
    @Test
    public void hiddenIndicatorKeepsItsLastShape() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge(content());
            badge.setVariant(Variant.DOT);
            laidOut(badge);
            Node indicator = badge.lookup(".indicator");
            assertTrue(indicator.getPseudoClassStates().contains(DOT));

            badge.setVariant(Variant.STANDARD);
            assertFalse(badge.isIndicatorShowing());
            assertFalse(badge.getPseudoClassStates().contains(DOT));
            assertTrue(indicator.getPseudoClassStates().contains(DOT));

            badge.setCount(2);
            assertFalse(indicator.getPseudoClassStates().contains(DOT));
        });
    }

    // ==================== Indicator placement ====================

    /** Verifies the indicator is centered on each corner of the content. */
    @Test
    public void indicatorIsCenteredOnTheContentCorner() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            assertCenter(badge, CONTENT_WIDTH, 0.0);
            badge.setPosition(Position.TOP_START);
            assertCenter(badge, 0.0, 0.0);
            badge.setPosition(Position.BOTTOM_START);
            assertCenter(badge, 0.0, CONTENT_HEIGHT);
            badge.setPosition(Position.BOTTOM_END);
            assertCenter(badge, CONTENT_WIDTH, CONTENT_HEIGHT);
            badge.setPosition(null);
            assertCenter(badge, CONTENT_WIDTH, 0.0);
        });
    }

    /** Verifies circular overlap moves the indicator onto the inscribed ellipse at each corner. */
    @Test
    public void circularOverlapInsetsTheIndicator() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            badge.setOverlap(Overlap.CIRCULAR);
            double dx = CIRCULAR_INSET * CONTENT_WIDTH;
            double dy = CIRCULAR_INSET * CONTENT_HEIGHT;
            assertCenter(badge, CONTENT_WIDTH - dx, dy);
            badge.setPosition(Position.BOTTOM_START);
            assertCenter(badge, dx, CONTENT_HEIGHT - dy);
            badge.setOverlap(null);
            assertCenter(badge, 0.0, CONTENT_HEIGHT);
        });
    }

    /** Verifies positive offsets move the indicator toward the content center at every corner. */
    @Test
    public void offsetsMoveTheIndicatorInward() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            badge.setOffsetX(10.0);
            badge.setOffsetY(6.0);
            assertCenter(badge, CONTENT_WIDTH - 10.0, 6.0);
            badge.setPosition(Position.BOTTOM_START);
            assertCenter(badge, 10.0, CONTENT_HEIGHT - 6.0);
            badge.setOffsetX(-4.0);
            assertCenter(badge, -4.0, CONTENT_HEIGHT - 6.0);
            badge.setOffsetX(Double.NaN);
            badge.setOffsetY(Double.POSITIVE_INFINITY);
            assertCenter(badge, 0.0, CONTENT_HEIGHT);
        });
    }

    /** Verifies the indicator follows the content, not the outer edge, of a padded or stretched badge. */
    @Test
    public void indicatorFollowsTheContentInsideALargerBadge() throws Exception {
        runOnFx(() -> {
            RXBadge badge = countBadge();
            badge.setPadding(new Insets(10.0));
            laidOut(badge);
            assertCenter(badge, 10.0 + CONTENT_WIDTH, 10.0);

            // The fixed-size content is centered in the stretched badge; a single
            // layout pass must already place the indicator on its new corner.
            badge.resize(220.0, 160.0);
            badge.layout();
            assertCenter(badge, 110.0 + CONTENT_WIDTH / 2.0, 80.0 - CONTENT_HEIGHT / 2.0);
            badge.resize(320.0, 100.0);
            badge.layout();
            assertCenter(badge, 160.0 + CONTENT_WIDTH / 2.0, 50.0 - CONTENT_HEIGHT / 2.0);
        });
    }

    /** Verifies the indicator is placed on the visual bounds of a shape whose origin is its center. */
    @Test
    public void indicatorFollowsANonResizableShape() throws Exception {
        runOnFx(() -> {
            RXBadge badge = new RXBadge(new Circle(20.0));
            badge.setCount(1);
            laidOut(badge);
            assertEquals(40.0, badge.getWidth(), EPSILON);
            assertCenter(badge, 40.0, 0.0);
        });
    }

    /** Verifies a longer text keeps the indicator centered on the same corner. */
    @Test
    public void indicatorStaysCenteredWhenItsTextGrows() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            double narrow = badge.lookup(".indicator").getLayoutBounds().getWidth();
            badge.setCount(1000);
            badge.applyCss();
            badge.layout();
            assertTrue(badge.lookup(".indicator").getLayoutBounds().getWidth() > narrow);
            assertCenter(badge, CONTENT_WIDTH, 0.0);
        });
    }

    // ==================== Content ====================

    /** Verifies replacing, clearing and re-parenting the content keeps a single owner. */
    @Test
    public void contentIsReplacedClearedAndTakenFromAnotherParent() throws Exception {
        runOnFx(() -> {
            Region first = content();
            Region second = content();
            RXBadge badge = laidOut(new RXBadge(first));
            Pane slot = (Pane) badge.lookup(".content-slot");
            assertSame(slot, first.getParent());

            badge.setContent(second);
            assertNull(first.getParent());
            assertSame(slot, second.getParent());

            badge.setContent(null);
            assertNull(second.getParent());
            assertTrue(slot.getChildren().isEmpty());

            Pane other = new Pane(first);
            badge.setContent(first);
            assertSame(slot, first.getParent());
            assertTrue(other.getChildren().isEmpty());
        });
    }

    /** Verifies content the application moved elsewhere leaves the indicator on the badge's own corner. */
    @Test
    public void indicatorFallsBackToTheSlotWhenTheContentIsTakenAway() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            Node content = badge.getContent();
            Pane other = new Pane(content);
            content.relocate(500.0, 500.0);
            badge.resize(80.0, 40.0);
            badge.layout();
            assertSame(other, content.getParent());
            assertCenter(badge, 80.0, 0.0);
        });
    }

    /** Verifies removing the skin releases the content and the skin's nodes, and a new skin takes over. */
    @Test
    public void removingTheSkinReleasesTheContent() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            Node content = badge.getContent();
            badge.setSkin(null);
            assertNull(content.getParent());
            assertTrue(badge.getChildrenUnmodifiable().isEmpty());

            badge.setSkin(new RXBadgeSkin(badge));
            badge.applyCss();
            badge.layout();
            assertSame(badge.lookup(".content-slot"), content.getParent());
            assertCenter(badge, CONTENT_WIDTH, 0.0);
        });
    }

    /** Verifies a skin replacing another takes the content over and leaves no node of the old skin. */
    @Test
    public void replacementSkinTakesTheContentOver() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(countBadge());
            Node content = badge.getContent();
            Label oldIndicator = (Label) badge.lookup(".indicator");

            // A skin of the same class as the current one is ignored by the control.
            badge.setSkin(new RXBadgeSkin(badge) {
            });
            badge.applyCss();
            badge.layout();
            assertEquals(3, badge.getChildrenUnmodifiable().size());
            assertSame(badge.lookup(".content-slot"), content.getParent());
            assertCenter(badge, CONTENT_WIDTH, 0.0);

            badge.setCount(6);
            assertEquals("5", oldIndicator.getText());
            assertEquals("6", ((Label) badge.lookup(".indicator")).getText());
        });
    }

    // ==================== Indicator node ====================

    /** Verifies the indicator node shows and hides at once, with the current text, outside a showing window. */
    @Test
    public void indicatorNodeFollowsTheState() throws Exception {
        runOnFx(() -> {
            RXBadge badge = laidOut(new RXBadge(content()));
            Label indicator = (Label) badge.lookup(".indicator");
            assertFalse(indicator.isVisible());

            badge.setCount(3);
            assertTrue(indicator.isVisible());
            assertEquals("3", indicator.getText());
            assertEquals(1.0, indicator.getScaleX(), EPSILON);

            badge.setCount(4);
            assertEquals("4", indicator.getText());

            badge.setCount(0);
            assertFalse(indicator.isVisible());
            assertEquals("4", indicator.getText(), "the last text stays while hidden");

            badge.setText("NEW");
            assertTrue(indicator.isVisible());
            assertEquals("NEW", indicator.getText());
        });
    }

    /** Verifies the pulse ring stays hidden outside a showing window. */
    @Test
    public void pulseDoesNotRunOutsideAShowingWindow() throws Exception {
        runOnFx(() -> {
            RXBadge badge = countBadge();
            badge.setPulsing(true);
            laidOut(badge);
            Node pulse = badge.lookup(".pulse");
            assertFalse(pulse.isVisible());
            assertEquals(0.0, pulse.getOpacity(), EPSILON);
            Node indicator = badge.lookup(".indicator");
            assertEquals(indicator.getLayoutX(), pulse.getLayoutX(), EPSILON);
            assertEquals(indicator.getLayoutY(), pulse.getLayoutY(), EPSILON);
            assertEquals(indicator.getLayoutBounds(), pulse.getLayoutBounds());
        });
    }

    // ==================== Mouse ====================

    /** Verifies the indicator and pulse ignore the mouse and only the content is picked. */
    @Test
    public void onlyTheContentIsPicked() throws Exception {
        runOnFx(() -> {
            RXBadge badge = countBadge();
            badge.setPadding(new Insets(10.0));
            laidOut(badge);
            assertTrue(badge.lookup(".indicator").isMouseTransparent());
            assertTrue(badge.lookup(".pulse").isMouseTransparent());
            assertFalse(badge.lookup(".content-slot").isPickOnBounds());
            // In the padding, and in the area the indicator overflows into.
            assertFalse(badge.contains(2.0, 30.0));
            assertFalse(badge.contains(10.0 + CONTENT_WIDTH + 3.0, 8.0));
        });
    }

    /** Verifies a mouse event on the content reaches the ancestors of the badge. */
    @Test
    public void mouseEventsOnTheContentReachTheAncestors() throws Exception {
        runOnFx(() -> {
            Region target = content();
            RXBadge badge = new RXBadge(target);
            StackPane root = new StackPane(badge);
            new Scene(root);
            root.applyCss();
            root.layout();
            AtomicInteger received = new AtomicInteger();
            root.addEventHandler(MouseEvent.MOUSE_MOVED, event -> received.incrementAndGet());
            Event.fireEvent(target, new MouseEvent(MouseEvent.MOUSE_MOVED, 1.0, 1.0, 1.0, 1.0,
                    MouseButton.NONE, 0, false, false, false, false, false, false, false,
                    false, false, false, null));
            assertEquals(1, received.get());
        });
    }

    // ==================== Helpers ====================

    private static Region content() {
        Region region = new Region();
        region.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        region.setPrefSize(CONTENT_WIDTH, CONTENT_HEIGHT);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
    }

    private static RXBadge countBadge() {
        RXBadge badge = new RXBadge(content());
        badge.setCount(5);
        return badge;
    }

    private static void inScene(RXBadge badge) {
        new Scene(new Pane(badge));
        badge.applyCss();
    }

    private static RXBadge laidOut(RXBadge badge) {
        inScene(badge);
        badge.autosize();
        badge.layout();
        return badge;
    }

    private static void assertShowing(RXBadge badge, String text) {
        assertTrue(badge.isIndicatorShowing(), "showing " + text);
        assertEquals(text, badge.getIndicatorText());
    }

    private static void assertHidden(RXBadge badge) {
        assertFalse(badge.isIndicatorShowing());
        assertEquals("", badge.getIndicatorText());
    }

    private static void assertSize(RXBadge badge, double minW, double minH, double prefW, double prefH,
                                   double maxW, double maxH) {
        assertEquals(minW, badge.minWidth(-1.0), EPSILON, "min width");
        assertEquals(minH, badge.minHeight(-1.0), EPSILON, "min height");
        assertEquals(prefW, badge.prefWidth(-1.0), EPSILON, "pref width");
        assertEquals(prefH, badge.prefHeight(-1.0), EPSILON, "pref height");
        assertEquals(maxW, badge.maxWidth(-1.0), EPSILON, "max width");
        assertEquals(maxH, badge.maxHeight(-1.0), EPSILON, "max height");
    }

    // The indicator is snapped to whole pixels, so its center is within half a pixel.
    private static void assertCenter(RXBadge badge, double x, double y) {
        badge.layout();
        Bounds bounds = badge.lookup(".indicator").getBoundsInParent();
        assertEquals(x, bounds.getCenterX(), 0.5, "center x");
        assertEquals(y, bounds.getCenterY(), 0.5, "center y");
    }

    @SuppressWarnings("unchecked")
    private static boolean settable(CssMetaData<? extends Styleable, ?> metaData, RXBadge badge) {
        return ((CssMetaData<RXBadge, ?>) metaData).isSettable(badge);
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }
}
