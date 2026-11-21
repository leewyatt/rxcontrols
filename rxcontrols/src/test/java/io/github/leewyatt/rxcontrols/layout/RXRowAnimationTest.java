package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the structural layout animation of {@link RXRow}: only a change of the
 * row's discrete structure starts a glide, geometry-only passes never do, responsive
 * hidden fades a column out in place, and every stop path leaves the columns settled.
 *
 * <p>The fixture has three columns. Below the {@code md} breakpoint each takes a full
 * line; from {@code md} on, the first two share a line and the third wraps.</p>
 */
public class RXRowAnimationTest {

    private static final double EPSILON = 1.0e-6;
    private static final double NARROW = 500.0;
    private static final double WIDE = 900.0;
    private static final double HEIGHT = 400.0;
    // Long enough that no tween advances while a single FX runnable executes.
    private static final Duration LONG = Duration.seconds(10.0);

    /**
     * Starts the JavaFX toolkit so timelines can run.
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

    // ==================== Defaults and CSS ====================

    @Test
    public void animationIsOffByDefaultAndStyleable() {
        RXRow row = new RXRow();
        assertFalse(row.isAnimated(), "animated defaults to false");
        assertEquals(Duration.millis(200.0), row.getAnimationDuration());
        assertEquals(Interpolator.EASE_BOTH, row.getAnimationInterpolator());

        List<CssMetaData<? extends Styleable, ?>> metadata = RXRow.getClassCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-animated"));
        assertTrue(hasProperty(metadata, "-rx-animation-duration"));
    }

    @Test
    public void invalidDurationAndInterpolatorAreAccepted() {
        RXRow row = new RXRow();
        row.setAnimationDuration(null);
        row.setAnimationDuration(Duration.ZERO);
        row.setAnimationDuration(Duration.millis(-20.0));
        row.setAnimationInterpolator(null);
        assertEquals(Duration.millis(-20.0), row.getAnimationDuration());
    }

    @Test
    public void cssEnablesAnimation() throws Exception {
        onFx(() -> {
            RXRow row = new RXRow();
            row.setStyle("-rx-animated: true; -rx-animation-duration: 350ms;");
            new Scene(row, NARROW, HEIGHT);
            row.applyCss();
            assertTrue(row.isAnimated());
            assertEquals(Duration.millis(350.0), row.getAnimationDuration());
        });
    }

    // ==================== Structural passes ====================

    @Test
    public void defaultRowSnapsOnStructuralChange() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            row.setAnimated(false);
            layout(row, NARROW);
            layout(row, WIDE);
            assertSettled(row);
        });
    }

    @Test
    public void firstLayoutDoesNotAnimate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            assertSettled(row);
        });
    }

    @Test
    public void breakpointChangeStartsFromThePreviousVisualPosition() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            double[][] before = visualPositions(row);

            layout(row, WIDE);

            double[][] after = visualPositions(row);
            for (int i = 0; i < before.length; i++) {
                assertEquals(before[i][0], after[i][0], EPSILON, "visual x of column " + i);
                assertEquals(before[i][1], after[i][1], EPSILON, "visual y of column " + i);
            }
            Region second = col(row, 1);
            assertNotEquals(0.0, second.getTranslateX(), "second column glides sideways");
            assertNotEquals(0.0, second.getTranslateY(), "second column glides up");
            assertEquals(WIDE / 2.0, second.getWidth(), 1.0, "width changes immediately");
        });
    }

    @Test
    public void orderChangeAnimates() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);

            ((RXCol) col(row, 2)).setOrder(-1);
            layout(row, WIDE);

            assertTrue(anyTranslated(row), "reordering glides");
        });
    }

    @Test
    public void justifyChangeAnimates() throws Exception {
        onFx(() -> {
            RXRow row = animatedRow();
            RXCol only = column(12, 12);
            row.getChildren().add(only);
            layout(row, WIDE);

            row.setJustify(RXRow.Justify.END);
            layout(row, WIDE);

            assertEquals(-WIDE / 2.0, only.getTranslateX(), 1.0, "glides from the start edge");
        });
    }

    @Test
    public void addedColumnFadesInPlace() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);

            RXCol added = column(24, 24);
            added.setLayoutX(300.0);
            row.getChildren().add(added);
            layout(row, WIDE);

            assertEquals(0.0, added.getOpacity(), EPSILON, "starts transparent");
            assertEquals(0.0, added.getTranslateX(), EPSILON, "no glide from a foreign position");
            assertEquals(0.0, added.getTranslateY(), EPSILON);
        });
    }

    // ==================== Geometry-only passes ====================

    @Test
    public void resizeWithinBreakpointDoesNotAnimate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            layout(row, WIDE + 60.0);
            assertSettled(row);
        });
    }

    @Test
    public void contentHeightChangeDoesNotAnimate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region third = col(row, 2);
            double oldY = third.getLayoutY();

            content(row, 0).setPrefHeight(60.0);
            layout(row, WIDE);

            assertNotEquals(oldY, third.getLayoutY(), "the wrapped line moves down");
            assertSettled(row);
        });
    }

    @Test
    public void gutterAndRowGapChangesDoNotAnimate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);

            row.setGutter(16.0);
            row.setRowGap(12.0);
            layout(row, WIDE);

            assertSettled(row);
        });
    }

    @Test
    public void geometryPassKeepsInFlightOffsetAndFollowsTarget() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            layout(row, WIDE);
            Region second = col(row, 1);
            double translateX = second.getTranslateX();
            double layoutX = second.getLayoutX();

            layout(row, WIDE + 60.0);

            assertEquals(translateX, second.getTranslateX(), EPSILON, "offset is not re-armed");
            assertNotEquals(layoutX, second.getLayoutX(), "layout target follows the resize");
        });
    }

    @Test
    public void geometryPassesDoNotResetTheTweenClock() throws Exception {
        Duration duration = Duration.millis(400.0);
        AtomicReference<RXRow> rowRef = new AtomicReference<>();
        AtomicReference<Timeline> pulses = new AtomicReference<>();
        long[] start = new long[1];

        onFx(() -> {
            RXRow row = fixture();
            rowRef.set(row);
            row.setAnimationDuration(duration);
            layout(row, NARROW);
            layout(row, WIDE);
            assertTrue(anyTranslated(row));
            start[0] = System.nanoTime();
            int[] count = {0};
            Timeline timeline = new Timeline(new KeyFrame(Duration.millis(16.0), e -> {
                count[0]++;
                layout(row, WIDE + (count[0] % 2) * 30.0);
            }));
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
            pulses.set(timeline);
        });

        // A clock reset on every pass would never settle within the timeout.
        waitUntil(() -> !anyTranslated(rowRef.get()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start[0]);
        onFx(() -> pulses.get().stop());
        assertTrue(elapsedMillis < duration.toMillis() + 1500.0,
                "settled near the original duration, took " + elapsedMillis + " ms");
    }

    // ==================== Stop paths ====================

    @Test
    public void disablingAnimationSnaps() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            RXCol added = column(24, 24);
            row.getChildren().add(added);
            layout(row, WIDE);
            assertTrue(anyTranslated(row));

            row.setAnimated(false);

            assertSettled(row);
        });
    }

    @Test
    public void zeroDurationSnaps() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            RXCol added = column(24, 24);
            row.getChildren().add(added);
            layout(row, WIDE);
            assertTrue(anyTranslated(row));

            row.setAnimationDuration(Duration.ZERO);

            assertSettled(row);
        });
    }

    @Test
    public void leavingTheSceneSnapsAndReattachingDoesNotAnimate() throws Exception {
        onFx(() -> {
            RXRow row = detachedFixture();
            StackPane holder = new StackPane(row);
            new Scene(holder, WIDE, HEIGHT);
            layout(row, NARROW);
            layout(row, WIDE);
            assertTrue(anyTranslated(row));

            holder.getChildren().remove(row);
            assertSettled(row);

            holder.getChildren().add(row);
            layout(row, NARROW);
            assertSettled(row);

            layout(row, WIDE);
            assertTrue(anyTranslated(row), "later structural changes animate again");
        });
    }

    @Test
    public void removingAnInFlightColumnRestoresIt() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            RXCol added = column(24, 24);
            added.setOpacity(0.8);
            row.getChildren().add(added);
            layout(row, WIDE);
            Region sliding = col(row, 1);
            assertNotEquals(0.0, sliding.getTranslateX());
            assertEquals(0.0, added.getOpacity(), EPSILON);

            row.getChildren().removeAll(sliding, added);

            assertEquals(0.0, sliding.getTranslateX(), EPSILON, "translate restored");
            assertEquals(0.0, sliding.getTranslateY(), EPSILON);
            assertEquals(0.8, added.getOpacity(), EPSILON, "borrowed opacity restored");
        });
    }

    // ==================== Bound channels ====================

    @Test
    public void boundTranslateColumnIsSkipped() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            Region second = col(row, 1);
            second.translateXProperty().bind(new SimpleDoubleProperty(3.0));

            layout(row, WIDE);

            assertEquals(3.0, second.getTranslateX(), EPSILON, "binding left untouched");
            assertNotEquals(0.0, col(row, 2).getTranslateY(), "other columns still glide");
        });
    }

    /**
     * Verifies a column a structural change moves by exactly its own offset glides from
     * where it showed and settles back at that offset: its glide starts at translate
     * zero, which must not read as no motion.
     */
    @Test
    public void columnMovedByItsOwnOffsetGlidesAndKeepsIt() throws Exception {
        onFx(() -> {
            // Measures how far the reorder moves the first column.
            RXRow twin = fixture();
            layout(twin, WIDE);
            double beforeX = col(twin, 0).getLayoutX();
            double beforeY = col(twin, 0).getLayoutY();
            ((RXCol) col(twin, 2)).setOrder(-1);
            layout(twin, WIDE);
            double dx = col(twin, 0).getLayoutX() - beforeX;
            double dy = col(twin, 0).getLayoutY() - beforeY;
            assertTrue(Math.abs(dx) + Math.abs(dy) > 1.0, "setup: the reorder moves the first column");

            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            first.setTranslateX(dx);
            first.setTranslateY(dy);
            double shownX = first.getLayoutX() + dx;
            double shownY = first.getLayoutY() + dy;
            ((RXCol) col(row, 2)).setOrder(-1);
            layout(row, WIDE);
            assertEquals(shownX, first.getLayoutX() + first.getTranslateX(), EPSILON, "starts where it showed (x)");
            assertEquals(shownY, first.getLayoutY() + first.getTranslateY(), EPSILON, "starts where it showed (y)");

            row.setAnimated(false);
            assertEquals(dx, first.getTranslateX(), EPSILON, "settles at its own translateX");
            assertEquals(dy, first.getTranslateY(), EPSILON, "settles at its own translateY");
        });
    }

    /**
     * Verifies an added column with a translate of its own fades in where it lands, at
     * that translate, first frame and last, instead of sliding toward it.
     */
    @Test
    public void addedColumnFadesInAtItsOwnTranslate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);

            RXCol added = column(24, 24);
            added.setTranslateX(20.0);
            added.setTranslateY(6.0);
            row.getChildren().add(added);
            layout(row, WIDE);
            assertEquals(0.0, added.getOpacity(), EPSILON, "first frame transparent");
            assertEquals(20.0, added.getTranslateX(), EPSILON, "first frame x");
            assertEquals(6.0, added.getTranslateY(), EPSILON, "first frame y");

            row.setAnimated(false);
            assertEquals(1.0, added.getOpacity(), EPSILON, "last frame opaque");
            assertEquals(20.0, added.getTranslateX(), EPSILON, "last frame x");
            assertEquals(6.0, added.getTranslateY(), EPSILON, "last frame y");
        });
    }

    /**
     * Verifies an added column whose opacity is bound, so it cannot fade, is not set
     * sliding by its own translate either.
     */
    @Test
    public void addedColumnWithBoundOpacityKeepsItsTranslate() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            RXCol added = column(24, 24);
            added.opacityProperty().bind(new SimpleDoubleProperty(0.7));
            added.setTranslateX(20.0);
            row.getChildren().add(added);

            layout(row, WIDE);

            assertEquals(20.0, added.getTranslateX(), EPSILON, "translate not written");
            assertEquals(0.7, added.getOpacity(), EPSILON, "binding left untouched");
        });
    }

    /**
     * Verifies a column with a translate of its own fades out from it, and when shown
     * again mid fade-out continues from where it shows and settles back at it.
     */
    @Test
    public void reclaimedColumnSettlesAtItsOwnTranslate() throws Exception {
        AtomicReference<RXRow> rowRef = new AtomicReference<>();
        double[] shown = new double[2];

        onFx(() -> {
            RXRow row = fixture();
            rowRef.set(row);
            row.setAnimationDuration(Duration.millis(1000.0));
            layout(row, WIDE);
            Region first = col(row, 0);
            first.setTranslateX(-4.0);
            first.setTranslateY(6.0);
            ((RXCol) first).setHidden(true);
            layout(row, WIDE);
            assertEquals(-4.0, first.getTranslateX(), EPSILON, "fades out from its own translateX");
            assertEquals(6.0, first.getTranslateY(), EPSILON, "fades out from its own translateY");
        });
        Thread.sleep(200);
        onFx(() -> {
            RXRow row = rowRef.get();
            Region first = col(row, 0);
            shown[0] = first.getLayoutX() + first.getTranslateX();
            shown[1] = first.getLayoutY() + first.getTranslateY();

            ((RXCol) first).setHidden(false);
            layout(row, WIDE);

            assertEquals(shown[0], first.getLayoutX() + first.getTranslateX(), EPSILON, "no x jump");
            assertEquals(shown[1], first.getLayoutY() + first.getTranslateY(), EPSILON, "no y jump");
        });
        waitUntil(() -> {
            Region first = col(rowRef.get(), 0);
            return Math.abs(first.getOpacity() - 1.0) < EPSILON
                    && Math.abs(first.getTranslateX() + 4.0) < EPSILON
                    && Math.abs(first.getTranslateY() - 6.0) < EPSILON;
        });
    }

    @Test
    public void boundOpacityColumnDoesNotFade() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            RXCol added = column(24, 24);
            added.opacityProperty().bind(new SimpleDoubleProperty(0.7));
            row.getChildren().add(added);

            layout(row, WIDE);

            assertEquals(0.7, added.getOpacity(), EPSILON, "binding left untouched");
        });
    }

    // ==================== Responsive hidden ====================

    @Test
    public void hiddenColumnFadesOutInPlace() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            double x = first.getLayoutX();
            double width = first.getWidth();

            ((RXCol) first).setHidden(true);
            layout(row, WIDE);

            assertFading(first);
            assertEquals(x, first.getLayoutX(), EPSILON, "stays in place");
            assertEquals(width, first.getWidth(), EPSILON, "keeps its size");
            assertNotEquals(0.0, col(row, 1).getTranslateX(), "the sibling glides into the gap");

            layout(row, WIDE + 60.0);
            assertFading(first);
            assertEquals(width, first.getWidth(), EPSILON, "a later pass does not restart or snap the fade");

            row.setAnimated(false);
            assertFullyHidden(first);
            assertFalse(first.isMouseTransparent(), "mouseTransparent restored");
            assertEquals(1.0, first.getOpacity(), EPSILON, "opacity restored");
        });
    }

    @Test
    public void fadeOutCompletionRestoresOriginalMouseTransparent() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            first.setMouseTransparent(true);

            ((RXCol) first).setHidden(true);
            layout(row, WIDE);
            row.setAnimated(false);

            assertFullyHidden(first);
            assertTrue(first.isMouseTransparent());
        });
    }

    @Test
    public void showingMidFadeOutReclaimsTheColumn() throws Exception {
        Duration duration = Duration.millis(1000.0);
        AtomicReference<RXRow> rowRef = new AtomicReference<>();
        double[] visual = new double[3];

        onFx(() -> {
            RXRow row = fixture();
            rowRef.set(row);
            row.setAnimationDuration(duration);
            layout(row, WIDE);
            ((RXCol) col(row, 0)).setHidden(true);
            layout(row, WIDE);
        });
        Thread.sleep(200);
        onFx(() -> {
            RXRow row = rowRef.get();
            Region first = col(row, 0);
            visual[0] = first.getOpacity();
            visual[1] = first.getLayoutX() + first.getTranslateX();
            visual[2] = first.getLayoutY() + first.getTranslateY();
            assertTrue(visual[0] < 1.0 && visual[0] > 0.0, "caught mid fade-out: " + visual[0]);

            ((RXCol) first).setHidden(false);
            layout(row, WIDE);

            assertTrue(first.isVisible() && first.isManaged(), "shown again");
            assertFalse(first.isMouseTransparent(), "mouseTransparent restored");
            assertEquals(visual[0], first.getOpacity(), EPSILON, "opacity continues from its live value");
            assertEquals(visual[1], first.getLayoutX() + first.getTranslateX(), EPSILON, "no x jump");
            assertEquals(visual[2], first.getLayoutY() + first.getTranslateY(), EPSILON, "no y jump");
        });

        // Well past the original exit: a late completion would hide the column again.
        waitUntil(() -> !anyTranslated(rowRef.get()));
        Thread.sleep(200);
        onFx(() -> {
            Region first = col(rowRef.get(), 0);
            assertTrue(first.isVisible() && first.isManaged(), "still shown");
            assertEquals(1.0, first.getOpacity(), EPSILON);
            assertTrue(first.getWidth() > 0.0, "laid out in its slot");
        });
    }

    @Test
    public void reshownColumnFadesInPlace() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            RXCol first = (RXCol) col(row, 0);
            first.setHidden(true);
            layout(row, WIDE);
            row.setAnimated(false);
            row.setAnimated(true);

            first.setHidden(false);
            layout(row, WIDE);

            assertTrue(first.isVisible() && first.isManaged());
            assertEquals(0.0, first.getOpacity(), EPSILON, "fades in from transparent");
            assertEquals(0.0, first.getTranslateX(), EPSILON, "no glide from the hidden 0x0 spot");
            assertEquals(0.0, first.getTranslateY(), EPSILON);
        });
    }

    @Test
    public void removingAFadingColumnRestoresIt() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            Region second = col(row, 1);
            second.setMouseTransparent(true);
            ((RXCol) first).setHidden(true);
            ((RXCol) second).setHidden(true);
            layout(row, WIDE);
            assertFading(first);
            assertFading(second);

            row.getChildren().removeAll(first, second);

            for (Region removed : List.of(first, second)) {
                assertTrue(removed.isVisible() && removed.isManaged(), "visible and managed restored");
                assertEquals(1.0, removed.getOpacity(), EPSILON, "opacity restored");
                assertEquals(0.0, removed.getTranslateX(), EPSILON);
                assertEquals(0.0, removed.getTranslateY(), EPSILON);
            }
            assertFalse(first.isMouseTransparent(), "mouseTransparent restored");
            assertTrue(second.isMouseTransparent(), "an original true is kept");
        });
    }

    @Test
    public void hidingMidGlideKeepsTheVisualPosition() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, NARROW);
            layout(row, WIDE);
            Region second = col(row, 1);
            assertNotEquals(0.0, second.getTranslateX());
            double visualX = second.getLayoutX() + second.getTranslateX();
            double visualY = second.getLayoutY() + second.getTranslateY();

            ((RXCol) second).setHidden(true);
            layout(row, WIDE);

            assertFading(second);
            assertEquals(0.0, second.getTranslateX(), EPSILON, "offset folded into layoutX");
            assertEquals(0.0, second.getTranslateY(), EPSILON);
            assertEquals(visualX, second.getLayoutX(), EPSILON, "no x jump");
            assertEquals(visualY, second.getLayoutY(), EPSILON, "no y jump");
        });
    }

    @Test
    public void hidingMidFadeInKeepsTheOpacity() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            RXCol added = column(24, 24);
            added.setOpacity(0.9);
            row.getChildren().add(added);
            layout(row, WIDE);
            assertEquals(0.0, added.getOpacity(), EPSILON);

            added.setHidden(true);
            layout(row, WIDE);

            assertFading(added);
            assertEquals(0.0, added.getOpacity(), EPSILON, "opacity does not jump");

            row.setAnimated(false);
            assertFullyHidden(added);
            assertEquals(0.9, added.getOpacity(), EPSILON, "the fade-in target is the base");
        });
    }

    @Test
    public void mouseTransparentBoundTrueStillFades() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            first.mouseTransparentProperty().bind(new SimpleBooleanProperty(true));

            ((RXCol) first).setHidden(true);
            layout(row, WIDE);
            assertFading(first);

            row.setAnimated(false);
            assertFullyHidden(first);
        });
    }

    @Test
    public void mouseTransparentBoundFalseHidesImmediately() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            first.mouseTransparentProperty().bind(new SimpleBooleanProperty(false));

            ((RXCol) first).setHidden(true);
            layout(row, WIDE);

            assertFullyHidden(first);
        });
    }

    @Test
    public void focusInsideHidesImmediately() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region focused = content(row, 0);
            focused.requestFocus();
            assertEquals(focused, row.getScene().getFocusOwner(), "precondition: focus inside");

            ((RXCol) col(row, 0)).setHidden(true);
            layout(row, WIDE);

            assertFullyHidden(col(row, 0));
        });
    }

    @Test
    public void boundChannelsHideImmediately() throws Exception {
        onFx(() -> {
            RXRow row = fixture();
            layout(row, WIDE);
            Region first = col(row, 0);
            Region second = col(row, 1);
            Region third = col(row, 2);
            first.opacityProperty().bind(new SimpleDoubleProperty(1.0));
            second.translateXProperty().bind(new SimpleDoubleProperty(0.0));
            third.visibleProperty().bind(new SimpleBooleanProperty(true));

            for (Region region : List.of(first, second, third)) {
                ((RXCol) region).setHidden(true);
            }
            layout(row, WIDE);

            assertFullyHidden(first);
            assertFullyHidden(second);
            assertFalse(third.isManaged(), "bound visible keeps the existing snap path");
            assertEquals(0.0, third.getWidth(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static void assertFading(Region col) {
        assertTrue(col.isVisible(), "visible while fading out");
        assertFalse(col.isManaged(), "out of the layout while fading out");
        assertTrue(col.isMouseTransparent(), "mouse transparent while fading out");
        assertTrue(col.getWidth() > 0.0, "keeps its size while fading out");
    }

    private static void assertFullyHidden(Region col) {
        assertFalse(col.isVisible(), "hidden");
        assertFalse(col.isManaged(), "unmanaged");
        assertEquals(0.0, col.getWidth(), EPSILON, "collapsed");
    }


    private static RXRow animatedRow() {
        RXRow row = new RXRow();
        row.setAnimated(true);
        row.setAnimationDuration(LONG);
        new Scene(row, NARROW, HEIGHT);
        return row;
    }

    private static RXRow fixture() {
        RXRow row = detachedFixture();
        new Scene(row, NARROW, HEIGHT);
        return row;
    }

    private static RXRow detachedFixture() {
        RXRow row = new RXRow();
        row.setAnimated(true);
        row.setAnimationDuration(LONG);
        row.getChildren().addAll(column(24, 12), column(24, 12), column(24, 24));
        return row;
    }

    private static RXCol column(int xsSpan, int mdSpan) {
        Region content = new Region();
        content.setPrefSize(10.0, 20.0);
        RXCol col = new RXCol(content);
        col.setXs(RXColSpec.of(xsSpan));
        col.setMd(RXColSpec.of(mdSpan));
        return col;
    }

    private static Region col(RXRow row, int index) {
        return (Region) row.getChildren().get(index);
    }

    private static Region content(RXRow row, int index) {
        return (Region) ((RXCol) row.getChildren().get(index)).getChildren().get(0);
    }

    private static double[][] visualPositions(RXRow row) {
        double[][] positions = new double[row.getChildren().size()][];
        for (int i = 0; i < positions.length; i++) {
            Node child = row.getChildren().get(i);
            positions[i] = new double[]{
                    child.getLayoutX() + child.getTranslateX(),
                    child.getLayoutY() + child.getTranslateY()};
        }
        return positions;
    }

    private static boolean anyTranslated(RXRow row) {
        for (Node child : row.getChildren()) {
            if (child.getTranslateX() != 0.0 || child.getTranslateY() != 0.0 || child.getOpacity() != 1.0) {
                return true;
            }
        }
        return false;
    }

    private static void assertSettled(RXRow row) {
        for (Node child : row.getChildren()) {
            assertEquals(0.0, child.getTranslateX(), EPSILON, "translateX of " + child);
            assertEquals(0.0, child.getTranslateY(), EPSILON, "translateY of " + child);
            assertEquals(1.0, child.getOpacity(), EPSILON, "opacity of " + child);
        }
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metadata, String property) {
        return metadata.stream().anyMatch(data -> property.equals(data.getProperty()));
    }

    private static void layout(RXRow row, double width) {
        row.resize(width, HEIGHT);
        row.layout();
    }

    private static void onFx(Runnable action) throws Exception {
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
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error.get() instanceof Exception exception) {
            throw exception;
        }
        if (error.get() instanceof Error failure) {
            throw failure;
        }
    }

    private static void waitUntil(Callable<Boolean> conditionOnFx) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            onFx(() -> {
                try {
                    result.set(conditionOnFx.call());
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            if (Boolean.TRUE.equals(result.get())) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within timeout");
    }
}
