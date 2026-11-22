package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout and behavior tests for {@link RXMasonryPane}, exercised entirely through
 * its public API plus the laid-out children's geometry.
 */
public class RXMasonryPaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so the animated-removal path can play a timeline.
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

    /**
     * Verifies default property values, the style class, and child constraints.
     */
    @Test
    public void defaultStateAndConstraints() {
        Region card = card(80.0, 50.0);
        RXMasonryPane pane = new RXMasonryPane(card);

        assertTrue(pane.getStyleClass().contains("rx-masonry-pane"));
        assertClose(260.0, pane.getColumnWidth(), "columnWidth");
        assertClose(8.0, pane.getHgap(), "hgap");
        assertClose(8.0, pane.getVgap(), "vgap");
        assertEquals(0, pane.getColumnCount());
        assertEquals(3, pane.getPrefColumns());
        assertEquals(0, pane.getMaxColumns());
        assertTrue(pane.isFillWidth());
        assertSame(Pos.TOP_LEFT, pane.getAlignment());
        assertTrue(pane.isAnimated());
        assertEquals(Duration.millis(200.0), pane.getAnimationDuration());
        assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
        assertSame(RXBreakpointProfile.ANT_DESIGN, pane.getBreakpointProfile());
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());

        assertNull(RXMasonryPane.getMargin(card));
        assertNull(RXMasonryPane.getColumnSpan(card));
        RXMasonryPane.setMargin(card, new Insets(2.0));
        RXMasonryPane.setColumnSpan(card, 2);
        assertEquals(new Insets(2.0), RXMasonryPane.getMargin(card));
        assertEquals(2, RXMasonryPane.getColumnSpan(card));
        RXMasonryPane.clearConstraints(card);
        assertNull(RXMasonryPane.getMargin(card));
        assertNull(RXMasonryPane.getColumnSpan(card));
    }

    /**
     * Verifies the responsive column count and stretched track widths.
     */
    @Test
    public void columnCountFromColumnWidthAndFillWidthTracks() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        Region c = card(80.0, 70.0);
        Region d = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b, c, d);

        layout(pane, 350.0, 1000.0);

        // floor((350 + 10) / (100 + 10)) = 3 columns; track = (350 - 2*10) / 3 = 110.
        assertEquals(3, pane.getActualColumnCount());
        assertBox(a, 0.0, 0.0, 110.0, 50.0, "a");
        assertBox(b, 120.0, 0.0, 110.0, 60.0, "b");
        assertBox(c, 240.0, 0.0, 110.0, 70.0, "c");
        // d goes to the currently shortest column (a at height 50).
        assertBox(d, 0.0, 50.0, 110.0, 40.0, "d");
    }

    /**
     * Verifies a forced column count overrides the width-derived count.
     */
    @Test
    public void forcedColumnCountOverridesAuto() {
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, card(80.0, 50.0), card(80.0, 60.0));
        pane.setColumnCount(2);

        layout(pane, 350.0, 1000.0);

        assertEquals(2, pane.getActualColumnCount());
    }

    /**
     * Verifies the resolved column count is capped by maxColumns.
     */
    @Test
    public void maxColumnsCapsResolvedColumns() {
        RXMasonryPane pane = pane(50.0, 0.0, 0.0, card(40.0, 50.0));
        pane.setMaxColumns(3);

        layout(pane, 400.0, 1000.0);

        // floor(400 / 50) = 8 would be auto; capped to 3.
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies non-fill mode keeps fixed track widths and left-aligns the block.
     */
    @Test
    public void fillWidthFalseKeepsFixedTrackWidth() {
        Region a = card(60.0, 50.0);
        Region b = card(60.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, a, b);
        pane.setFillWidth(false);

        layout(pane, 350.0, 1000.0);

        // 3 columns of fixed width 100; TOP_LEFT keeps the block at x = 0.
        assertEquals(3, pane.getActualColumnCount());
        assertClose(0.0, a.getLayoutX(), "a x");
        assertClose(100.0, b.getLayoutX(), "b x");
        // fillWidth=false: child sits at its own pref width inside the 100 track.
        assertClose(60.0, a.getWidth(), "a width");
    }

    /**
     * Verifies a column span widens the child and is clamped to the column count.
     */
    @Test
    public void columnSpanWidensAndClampsToColumns() {
        Region wide = card(80.0, 50.0);
        Region narrow = card(80.0, 30.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, wide, narrow);
        RXMasonryPane.setColumnSpan(wide, 5);

        layout(pane, 350.0, 1000.0);

        // 3 columns, track 110; span clamps to 3 -> width 3*110 + 2*10 = 350.
        assertEquals(3, pane.getActualColumnCount());
        assertClose(350.0, wide.getWidth(), "wide width");
        assertClose(0.0, wide.getLayoutX(), "wide x");
        // The span fills the whole first row, so the next card drops below it.
        assertClose(0.0, narrow.getLayoutX(), "narrow x");
        assertClose(50.0, narrow.getLayoutY(), "narrow y");
    }

    /**
     * Verifies content-bias preferred height equals the packed column height.
     */
    @Test
    public void prefHeightReportsPackedColumnHeight() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setColumnCount(1);

        // One column: 100 + vgap 10 + 50 = 160.
        assertClose(160.0, pane.prefHeight(300.0), "pref height");
    }

    /**
     * Verifies an explicit breakpoint column count overrides the width auto-count.
     */
    @Test
    public void breakpointColumnsOverrideAutoCount() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 800.0, 1000.0);

        // ANT_DESIGN resolves 800 to "md"; the md override forces 3 columns
        // instead of the auto floor(800 / 100) = 8.
        assertEquals(RXBreakpoint.MD, pane.getActiveBreakpoint());
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies that without a matching breakpoint override the auto count is used.
     */
    @Test
    public void breakpointWithoutOverrideFallsBackToColumnWidth() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 600.0, 1000.0);

        // 600 resolves to "sm"; no sm override -> auto floor(600 / 100) = 6.
        assertEquals(RXBreakpoint.SM, pane.getActiveBreakpoint());
        assertEquals(6, pane.getActualColumnCount());
    }

    /**
     * Verifies the explicit column count beats breakpoint overrides.
     */
    @Test
    public void forcedColumnCountBeatsBreakpoint() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(5);
        pane.setColumnCount(2);

        layout(pane, 1000.0, 1000.0);

        assertEquals(2, pane.getActualColumnCount());
    }

    /**
     * Verifies a breakpoint column override cascades to wider breakpoints
     * (mobile-first) rather than only applying in its own band.
     */
    @Test
    public void breakpointColumnsCascadeToWiderBreakpoint() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 1500.0, 1000.0);

        // 1500 resolves to "xl"; with no xl override the md=3 override cascades
        // up instead of falling back to auto floor(1500 / 100) = 15.
        assertEquals(RXBreakpoint.XL, pane.getActiveBreakpoint());
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies an xxl override keeps applying at xxxl widths under the default
     * ANT_DESIGN profile when no xxxl override is set.
     */
    @Test
    public void xxlColumnsCascadeIntoXxxlWidth() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setXxl(4);

        layout(pane, 2000.0, 1000.0);

        assertEquals(RXBreakpoint.XXXL, pane.getActiveBreakpoint());
        assertEquals(4, pane.getActualColumnCount());
    }

    /**
     * Verifies an AUTO_COLUMNS override breaks the mobile-first cascade and
     * restores columnWidth auto-calculation from that breakpoint up.
     */
    @Test
    public void autoColumnsSentinelRestoresAutoColumnCount() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);
        pane.setXl(RXMasonryPane.AUTO_COLUMNS);

        layout(pane, 800.0, 1000.0);   // md -> fixed 3
        assertEquals(3, pane.getActualColumnCount());

        layout(pane, 1100.0, 1000.0);  // lg inherits md=3
        assertEquals(3, pane.getActualColumnCount());

        layout(pane, 1500.0, 1000.0);  // xl is explicit AUTO -> floor(1500 / 100) = 15
        assertEquals(RXBreakpoint.XL, pane.getActiveBreakpoint());
        assertEquals(15, pane.getActualColumnCount());
    }

    /**
     * Verifies a positive override at a breakpoint wider than an AUTO_COLUMNS
     * reset takes effect again.
     */
    @Test
    public void positiveOverrideAfterAutoSentinelTakesEffect() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);
        pane.setXl(RXMasonryPane.AUTO_COLUMNS);
        pane.setXxl(5);

        layout(pane, 1500.0, 1000.0);  // xl AUTO -> floor = 15
        assertEquals(15, pane.getActualColumnCount());

        layout(pane, 1700.0, 1000.0);  // xxl -> fixed 5
        assertEquals(RXBreakpoint.XXL, pane.getActiveBreakpoint());
        assertEquals(5, pane.getActualColumnCount());
    }

    /**
     * Verifies AUTO_COLUMNS (0) is accepted while negative counts are rejected.
     */
    @Test
    public void autoColumnsAcceptedNegativeRejected() {
        RXMasonryPane pane = new RXMasonryPane();
        pane.setXs(RXMasonryPane.AUTO_COLUMNS);
        assertEquals(Integer.valueOf(RXMasonryPane.AUTO_COLUMNS), pane.getXs());
        assertThrows(IllegalArgumentException.class,
                () -> pane.setBreakpointColumns(RXBreakpoint.MD, -1));
    }

    /**
     * Verifies a child pref-height change at constant width re-packs the layout,
     * guarding against a layout cache that is invalidated only when the column
     * count changes.
     */
    @Test
    public void childPrefHeightChangeInvalidatesPack() {
        Region a = card(80.0, 100.0);
        Region b = card(80.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, a, b);
        pane.setColumnCount(1);

        layout(pane, 120.0, 1000.0);
        assertClose(110.0, b.getLayoutY(), "b y before");

        // Same width, but a grows: the cache must not keep b at its stale position.
        a.setPrefHeight(200.0);
        pane.layout();

        assertClose(210.0, b.getLayoutY(), "b y after");
    }

    /**
     * Verifies a height-for-width child whose width is clamped by maxWidth is
     * measured at its real (clamped) width, so the next card never overlaps it.
     */
    @Test
    public void boundedWidthChildIsMeasuredAtItsActualWidth() {
        // Capped at maxWidth=100 inside a 250px column; height = 20000 / width,
        // so the measured height must use width 100 (height 200), not 250 (height 80).
        BoundedWidthBiasedRegion biased = new BoundedWidthBiasedRegion();
        Region next = card(80.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, biased, next);
        pane.setColumnCount(1);

        layout(pane, 250.0, 1000.0);

        assertClose(100.0, biased.getWidth(), "biased width clamped");
        assertClose(200.0, biased.getHeight(), "biased height at clamped width");
        assertClose(200.0, next.getLayoutY(), "next card sits below, no overlap");
    }

    /**
     * Verifies the alignment's horizontal component positions the whole content
     * block (fillWidth off), and that an overflowing block clamps the offset at
     * zero instead of going negative.
     */
    @Test
    public void alignmentPositionsContentBlock() {
        Region a = card(100.0, 50.0);
        Region b = card(100.0, 70.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);
        pane.setColumnCount(2);
        pane.setFillWidth(false);

        layout(pane, 400.0, 300.0); // used width = 2 * 100 + 10 = 210
        assertClose(0.0, a.getLayoutX(), "TOP_LEFT keeps the block at x = 0");

        pane.setAlignment(Pos.TOP_CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(95.0, a.getLayoutX(), "CENTER offsets the block by (400 - 210) / 2");
        assertClose(205.0, b.getLayoutX(), "the second column carries the same offset");

        pane.setAlignment(Pos.TOP_RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(190.0, a.getLayoutX(), "RIGHT flushes the block to the right edge");

        layout(pane, 150.0, 300.0); // block (210) wider than the pane (150)
        assertClose(0.0, a.getLayoutX(), "an overflowing block clamps the offset at zero");
    }

    /**
     * Verifies removeAnimated's happy path (exit armed, then the child leaves on
     * its own once the fade completes) and clearAnimated over several children.
     */
    @Test
    public void removeAnimatedFadesOutThenRemoves() throws Exception {
        RXMasonryPane pane = new RXMasonryPane();
        Region card = card(80.0, 50.0);
        CountDownLatch gone = new CountDownLatch(1);
        runOnFx(() -> {
            pane.setAnimationDuration(Duration.millis(40.0));
            pane.getChildren().add(card);
            new Scene(pane);
            layout(pane, 300.0, 300.0);
            pane.getChildren().addListener((ListChangeListener<Node>) change -> {
                if (!pane.getChildren().contains(card)) {
                    gone.countDown();
                }
            });

            pane.removeAnimated(card);
            assertTrue(pane.getChildren().contains(card), "still a child while the exit plays");
            assertFalse(card.isManaged(), "unmanaged during the exit so survivors reflow");
        });
        assertTrue(gone.await(5, TimeUnit.SECONDS), "the exit removes the child when the fade completes");
        runOnFx(() -> assertTrue(card.isManaged(), "managed state restored after the exit"));
    }

    /**
     * Verifies clearAnimated arms an exit for every child (all stay children,
     * unmanaged) and clears immediately when animation is disabled.
     */
    @Test
    public void clearAnimatedArmsExitsOrClearsImmediately() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane(card(80.0, 50.0), card(80.0, 60.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            pane.clearAnimated();
            assertEquals(2, pane.getChildren().size(), "children stay while their exits play");
            assertTrue(pane.getChildren().stream().noneMatch(Node::isManaged),
                    "every child is unmanaged during its exit");

            RXMasonryPane plain = new RXMasonryPane(card(80.0, 50.0));
            plain.setAnimated(false);
            new Scene(plain);
            layout(plain, 300.0, 300.0);
            plain.clearAnimated();
            assertTrue(plain.getChildren().isEmpty(), "animation off clears immediately");
        });
    }

    /**
     * Verifies the enter fade's pane-level contract: an added child starts its
     * first laid-out frame fully transparent and settles back at the user's own
     * opacity (not a hard-coded 1.0) once the fade completes.
     */
    /**
     * Verifies a child the layout moves by exactly its own offset glides from where it
     * showed and settles back at that offset.
     */
    @Test
    public void childMovedByItsOwnOffsetGlidesAndKeepsIt() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            Region card = card(80.0, 50.0);
            pane.getChildren().add(card);
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            card.setTranslateX(20.0);
            pane.setPadding(new Insets(0.0, 0.0, 0.0, 20.0));
            layout(pane, 300.0, 300.0);
            assertClose(20.0, card.getLayoutX() + card.getTranslateX(), "starts where it showed");

            pane.setAnimated(false);
            assertClose(20.0, card.getTranslateX(), "settles at its own translate");
        });
    }

    /**
     * Verifies an entering child starts its entrance offset from a translate of its own
     * and ends there, on both axes.
     */
    @Test
    public void enteringChildEntersFromItsOwnTranslate() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            pane.getChildren().add(card(80.0, 50.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            Region entering = card(80.0, 50.0);
            entering.setTranslateX(20.0);
            entering.setTranslateY(6.0);
            pane.getChildren().add(entering);
            layout(pane, 300.0, 300.0);
            // The default 8 px vgap sets the entrance offset.
            assertClose(20.0, entering.getTranslateX(), "first frame x");
            assertClose(14.0, entering.getTranslateY(), "first frame y, offset from its own 6");
            assertClose(0.0, entering.getOpacity(), "first frame transparent");

            pane.setAnimated(false);
            assertClose(20.0, entering.getTranslateX(), "last frame x");
            assertClose(6.0, entering.getTranslateY(), "last frame y");
            assertClose(1.0, entering.getOpacity(), "last frame opaque");
        });
    }

    /**
     * Verifies removeAnimated gives the child back its own translate by the time it is
     * removed.
     */
    @Test
    public void removeAnimatedRestoresTheChildsOwnTranslate() throws Exception {
        RXMasonryPane pane = new RXMasonryPane();
        Region card = card(80.0, 50.0);
        CountDownLatch gone = new CountDownLatch(1);
        runOnFx(() -> {
            pane.setAnimationDuration(Duration.millis(40.0));
            pane.getChildren().add(card);
            new Scene(pane);
            layout(pane, 300.0, 300.0);
            card.setTranslateX(20.0);
            card.setTranslateY(6.0);
            pane.getChildren().addListener((ListChangeListener<Node>) change -> {
                if (!pane.getChildren().contains(card)) {
                    gone.countDown();
                }
            });

            pane.removeAnimated(card);
        });
        assertTrue(gone.await(5, TimeUnit.SECONDS), "the exit removes the child");
        runOnFx(() -> {
            assertClose(20.0, card.getTranslateX(), "translateX back");
            assertClose(6.0, card.getTranslateY(), "translateY back");
            assertClose(1.0, card.getOpacity(), "opacity back");
        });
    }

    @Test
    public void enterFadeStartsAtZeroAndRestoresUserOpacity() throws Exception {
        RXMasonryPane pane = new RXMasonryPane();
        Region entering = card(80.0, 50.0);
        CountDownLatch settled = new CountDownLatch(1);
        runOnFx(() -> {
            pane.setAnimationDuration(Duration.millis(40.0));
            pane.getChildren().add(card(80.0, 50.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            entering.setOpacity(0.7);
            entering.opacityProperty().addListener((obs, old, value) -> {
                if (Math.abs(value.doubleValue() - 0.7) < EPSILON) {
                    settled.countDown();
                }
            });
            pane.getChildren().add(entering);
            layout(pane, 300.0, 300.0);
            assertClose(0.0, entering.getOpacity(), "the first laid-out frame is fully transparent");
        });
        assertTrue(settled.await(5, TimeUnit.SECONDS), "the fade settles at the user opacity");
        runOnFx(() -> assertClose(0.7, entering.getOpacity(), "the user's own opacity is restored, not 1.0"));
    }

    /**
     * Verifies bound channels never throw: a bound-translate child snaps through a
     * reflow, a bound-opacity child enters without fading, and removeAnimated removes
     * a bound child at once instead of fading it.
     */
    @Test
    public void boundChannelsSkipTheirAnimation() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = pane(80.0, 10.0, 10.0,
                    card(80.0, 50.0), card(80.0, 60.0), card(80.0, 40.0), card(80.0, 70.0));
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);
            Region translateBound = (Region) pane.getChildren().get(3);
            translateBound.translateXProperty().bind(new SimpleDoubleProperty(3.0));

            layout(pane, 500.0, 400.0); // reflow: must not throw "A bound value cannot be set"
            assertClose(3.0, translateBound.getTranslateX(), "translate binding left untouched");

            Region opacityBound = card(80.0, 50.0);
            opacityBound.opacityProperty().bind(new SimpleDoubleProperty(0.6));
            pane.getChildren().add(opacityBound);
            layout(pane, 500.0, 400.0);
            assertClose(0.6, opacityBound.getOpacity(), "enters without fading");
            assertTrue(Math.abs(opacityBound.getTranslateY()) > EPSILON, "still slides in");

            pane.removeAnimated(opacityBound);
            pane.removeAnimated(translateBound);
            assertFalse(pane.getChildren().contains(opacityBound), "removed at once");
            assertFalse(pane.getChildren().contains(translateBound), "removed at once");
            assertTrue(opacityBound.isManaged() && translateBound.isManaged(), "managed restored");
        });
    }

    /**
     * Verifies a resize that keeps every card in its column starts no glide (filled
     * columns shift on every pixel of width), while a card height change at the same
     * width still glides the cards below it.
     */
    @Test
    public void resizeOnlyPassDoesNotGlideButHeightChangeDoes() throws Exception {
        runOnFx(() -> {
            Region tall = card(80.0, 50.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0,
                    tall, card(80.0, 60.0), card(80.0, 40.0), card(80.0, 70.0));
            pane.setColumnCount(2);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            layout(pane, 340.0, 400.0);
            assertFalse(anyTranslated(pane), "a pure resize applies immediately");

            tall.setPrefHeight(90.0);
            layout(pane, 340.0, 400.0);
            assertTrue(anyTranslated(pane), "a height change still glides");
        });
    }

    /**
     * Verifies a width change that follows the pane's own preferred width (here a gap
     * change in a parent that sizes the pane to its pref) still glides: it is not a
     * resize from outside.
     */
    @Test
    public void prefDrivenWidthChangeStillGlides() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = pane(80.0, 10.0, 10.0,
                    card(80.0, 50.0), card(80.0, 60.0), card(80.0, 40.0));
            pane.setColumnCount(2);
            pane.setAnimationDuration(Duration.seconds(10.0));
            HBox parent = new HBox(pane);
            new Scene(parent);
            layout(parent, 600.0, 400.0);

            pane.setHgap(40.0);
            layout(parent, 600.0, 400.0);

            assertTrue(anyTranslated(pane), "the gap change glides");
        });
    }

    /**
     * Verifies a child fading out stays on its way out when setAll keeps it in the list,
     * and really leaves the list once, when its exit ends. Before, the reorder handed it
     * back as if it had been removed: it was managed again and stayed in the pane.
     */
    @Test
    public void leavingChildKeptBySetAllLeavesOnce() throws Exception {
        runOnFx(() -> {
            Region a = card(80.0, 50.0);
            Region b = card(80.0, 60.0);
            Region c = card(80.0, 40.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, a, b, c);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);
            int[] departures = {0};
            pane.getChildren().addListener((ListChangeListener<Node>) change -> {
                boolean removed = false;
                boolean added = false;
                while (change.next()) {
                    removed |= change.wasRemoved() && change.getRemoved().contains(b);
                    added |= change.wasAdded() && change.getAddedSubList().contains(b);
                }
                if (removed && !added) {
                    departures[0]++;
                }
            });

            pane.removeAnimated(b);
            pane.getChildren().setAll(c, b, a);
            assertFalse(b.isManaged(), "kept by setAll, it is still leaving");
            layout(pane, 300.0, 300.0);
            pane.setAnimated(false);
            assertFalse(pane.getChildren().contains(b), "it leaves when its exit ends");
            assertTrue(b.isManaged(), "with its managed state back");
            assertEquals(1, departures[0], "it leaves the list once");
        });
    }

    /**
     * Verifies a child that setAll keeps in the list goes on with what it was doing: a
     * gliding child keeps its glide and an entering child its fade through the change.
     */
    @Test
    public void childKeptBySetAllGoesOn() throws Exception {
        runOnFx(() -> {
            Region a = card(80.0, 50.0);
            Region b = card(80.0, 60.0);
            Region c = card(80.0, 40.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, a, b, c);
            pane.setColumnCount(3);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 600.0, 400.0);
            pane.setHgap(40.0);
            layout(pane, 600.0, 400.0);
            double gliding = b.getTranslateX();
            assertTrue(Math.abs(gliding) > 0.5, "setup: B glides");

            Region entering = card(80.0, 30.0);
            pane.getChildren().add(entering);
            layout(pane, 600.0, 400.0);
            assertEquals(0.0, entering.getOpacity(), 1.0e-9, "setup: the new child fades in from 0");

            pane.getChildren().setAll(entering, c, b, a);
            assertEquals(gliding, b.getTranslateX(), 1.0e-9, "B keeps its glide through the change");
            assertEquals(0.0, entering.getOpacity(), 1.0e-9, "the new child keeps its fade through the change");
        });
    }

    /**
     * Verifies removeAnimated on a child mid-glide starts the fade where the child shows,
     * glide offset included, both at once and after the next layout, and the child has
     * its own translate and opacity back when it leaves.
     */
    @Test
    public void childRemovedMidGlideFadesOutWhereItShows() throws Exception {
        Runnable body = () -> {
            Region a = card(80.0, 50.0);
            Region b = card(80.0, 60.0);
            Region c = card(80.0, 40.0);
            b.setTranslateY(-4.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, a, b, c);
            pane.setColumnCount(3);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 600.0, 400.0);
            pane.setHgap(40.0);
            layout(pane, 600.0, 400.0);
            double shownX = b.getLayoutX() + b.getTranslateX();
            double shownY = b.getLayoutY() + b.getTranslateY();
            assertTrue(Math.abs(b.getTranslateX()) > 0.5, "setup: B glides");

            pane.removeAnimated(b);
            assertMasonryClose(shownX, b.getLayoutX() + b.getTranslateX(), "the fade starts where it shows (x)");
            assertMasonryClose(shownY, b.getLayoutY() + b.getTranslateY(), "the fade starts where it shows (y)");
            layout(pane, 600.0, 400.0);
            assertMasonryClose(shownX, b.getLayoutX() + b.getTranslateX(), "the next layout leaves it there (x)");
            assertMasonryClose(shownY, b.getLayoutY() + b.getTranslateY(), "the next layout leaves it there (y)");

            pane.setAnimated(false);
            assertFalse(pane.getChildren().contains(b), "it leaves when the exit ends");
            assertMasonryClose(0.0, b.getTranslateX(), "its own translateX is back");
            assertMasonryClose(-4.0, b.getTranslateY(), "its own translateY is back");
            assertMasonryClose(1.0, b.getOpacity(), "its own opacity is back");
        };
        runOnFx(body);
    }

    private static void assertMasonryClose(double expected, double actual, String label) {
        assertEquals(expected, actual, 1.0e-9, label);
    }

    /**
     * Verifies a child with a bound managed is removed at once by removeAnimated and
     * clearAnimated instead of throwing, and does not stop the other children's exits.
     */
    @Test
    public void boundManagedChildIsRemovedAtOnce() throws Exception {
        runOnFx(() -> {
            Region plain = card(80.0, 50.0);
            Region bound = card(80.0, 60.0);
            bound.managedProperty().bind(new SimpleBooleanProperty(true));
            bound.opacityProperty().bind(new SimpleDoubleProperty(0.6));
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, plain, bound);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            pane.clearAnimated();

            assertFalse(pane.getChildren().contains(bound), "bound managed: removed at once");
            assertTrue(bound.isManaged(), "binding left untouched");
            assertTrue(pane.getChildren().contains(plain), "the other child still fades out");
            assertFalse(plain.isManaged(), "the other child left the layout for its exit");

            Region single = card(80.0, 40.0);
            single.managedProperty().bind(new SimpleBooleanProperty(true));
            pane.getChildren().add(single);
            pane.removeAnimated(single);
            assertFalse(pane.getChildren().contains(single), "removeAnimated removes it at once");
        });
    }

    /**
     * Verifies clearAnimated checks each child's managed binding when it reaches the
     * child, so a listener binding one while an earlier child leaves does not throw.
     */
    @Test
    public void clearAnimatedRechecksBindingsAsItGoes() throws Exception {
        runOnFx(() -> {
            Region first = card(80.0, 50.0);
            Region second = card(80.0, 60.0);
            first.managedProperty().addListener((obs, oldValue, managed) -> {
                if (!managed) {
                    second.managedProperty().bind(new SimpleBooleanProperty(true));
                }
            });
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, first, second);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            assertNoListenerException(pane::clearAnimated);

            assertFalse(pane.getChildren().contains(second), "bound on the way: removed at once");
            assertTrue(pane.getChildren().contains(first), "the first child still fades out");
        });
    }

    /**
     * Verifies that when removing the bound-managed children turns animation off (here
     * through a binding on the child count), the exits already started finish at once
     * instead of leaving children waiting on an animation that is now disabled.
     */
    @Test
    public void clearAnimatedThatDisablesAnimationFinishesEveryExit() throws Exception {
        runOnFx(() -> {
            Region plain = card(80.0, 50.0);
            Region bound = card(80.0, 60.0);
            bound.managedProperty().bind(new SimpleBooleanProperty(true));
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, plain, bound);
            pane.setAnimationDuration(Duration.seconds(10.0));
            pane.animatedProperty().bind(Bindings.size(pane.getChildren()).greaterThan(1));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            assertNoListenerException(pane::clearAnimated);

            assertFalse(pane.isAnimated(), "precondition: removal disabled animation");
            assertTrue(pane.getChildren().isEmpty(), "every child is gone");
            assertTrue(plain.isManaged(), "managed restored");
        });
    }

    /**
     * Same as above with a child whose only binding is opacity: it too is removed
     * before any exit starts, and the child after it is not left fading out once the
     * removal has turned animation off.
     */
    @Test
    public void clearAnimatedWithBoundOpacityThatDisablesAnimation() throws Exception {
        runOnFx(() -> {
            Region bound = card(80.0, 60.0);
            bound.opacityProperty().bind(new SimpleDoubleProperty(0.6));
            Region plain = card(80.0, 50.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, bound, plain);
            pane.setAnimationDuration(Duration.seconds(10.0));
            pane.animatedProperty().bind(Bindings.size(pane.getChildren()).greaterThan(1));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            assertNoListenerException(pane::clearAnimated);

            assertTrue(pane.getChildren().isEmpty(), "every child is gone");
            assertTrue(plain.isManaged(), "managed restored");
        });
    }

    /**
     * Verifies clearAnimated leaves alone a child that a listener moved to another
     * parent while earlier children were processed.
     */
    @Test
    public void clearAnimatedSkipsChildMovedAway() throws Exception {
        runOnFx(() -> {
            Region first = card(80.0, 50.0);
            Region second = card(80.0, 60.0);
            Pane elsewhere = new Pane();
            first.managedProperty().addListener((obs, oldValue, managed) -> {
                if (!managed) {
                    elsewhere.getChildren().add(second);
                }
            });
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, first, second);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            assertNoListenerException(pane::clearAnimated);

            assertSame(elsewhere, second.getParent(), "moved away");
            assertTrue(second.isManaged(), "not unmanaged by the old pane");
            assertClose(1.0, second.getOpacity(), "no exit started on it");
            assertTrue(pane.getChildren().contains(first), "the first child still fades out");
        });
    }

    /**
     * Verifies a leaving child that its own managed listener moves to another parent is
     * left alone: no exit fades it inside its new parent, through either entry point.
     */
    @Test
    public void childMovedAwayByItsOwnManagedListenerIsLeftAlone() throws Exception {
        Region removed = card(80.0, 50.0);
        Region cleared = card(80.0, 60.0);
        Pane elsewhere = new Pane();
        runOnFx(() -> {
            for (Region card : List.of(removed, cleared)) {
                card.managedProperty().addListener((obs, oldValue, managed) -> {
                    if (!managed) {
                        elsewhere.getChildren().add(card);
                    }
                });
            }
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, removed, cleared);
            pane.setAnimationDuration(Duration.seconds(1.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            assertNoListenerException(() -> pane.removeAnimated(removed));
            assertNoListenerException(pane::clearAnimated);
        });
        Thread.sleep(200); // an exit started by mistake would have faded them by now

        runOnFx(() -> {
            for (Region card : List.of(removed, cleared)) {
                assertSame(elsewhere, card.getParent(), "moved away");
                assertTrue(card.isManaged(), "managed restored");
                assertClose(1.0, card.getOpacity(), "not faded in its new parent");
                assertClose(0.0, card.getTranslateY(), "not drifted in its new parent");
            }
        });
    }

    /**
     * Verifies a child whose managed listener turns animation off finishes leaving at
     * once instead of waiting on an exit the disabled pane should not play.
     */
    @Test
    public void animationTurnedOffByManagedListenerFinishesAtOnce() throws Exception {
        runOnFx(() -> {
            Region removed = card(80.0, 50.0);
            Region cleared = card(80.0, 60.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, removed, cleared);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);

            pane.animatedProperty().bind(removed.managedProperty());
            assertNoListenerException(() -> pane.removeAnimated(removed));
            assertFalse(pane.getChildren().contains(removed), "removed at once");
            assertTrue(removed.isManaged(), "managed restored");

            pane.animatedProperty().bind(cleared.managedProperty());
            assertNoListenerException(pane::clearAnimated);
            assertTrue(pane.getChildren().isEmpty(), "cleared at once");
            assertTrue(cleared.isManaged(), "managed restored");
        });
    }

    /**
     * Verifies a prefWidth bound to the parent's width still counts as a resize from
     * outside: the pref follows the resize, it is not the pane changing itself.
     */
    @Test
    public void prefWidthBoundToParentResizesWithoutGliding() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = pane(80.0, 10.0, 10.0,
                    card(80.0, 50.0), card(80.0, 60.0), card(80.0, 40.0));
            pane.setColumnCount(2);
            pane.setAnimationDuration(Duration.seconds(10.0));
            StackPane parent = new StackPane(pane);
            pane.prefWidthProperty().bind(parent.widthProperty());
            new Scene(parent);
            layout(parent, 300.0, 400.0);

            layout(parent, 340.0, 400.0);

            assertFalse(anyTranslated(pane), "the resize applies immediately");
        });
    }

    /**
     * Verifies a resize-only pass keeps an in-flight glide's offset (no re-arm) while
     * its layout target follows the resize.
     */
    @Test
    public void resizeOnlyPassKeepsInFlightGlide() throws Exception {
        runOnFx(() -> {
            Region tall = card(80.0, 50.0);
            Region below = card(80.0, 40.0);
            RXMasonryPane pane = pane(80.0, 10.0, 10.0, tall, card(80.0, 60.0), below);
            pane.setColumnCount(2);
            pane.setAnimationDuration(Duration.seconds(10.0));
            new Scene(pane);
            layout(pane, 300.0, 400.0);
            tall.setPrefHeight(90.0);
            layout(pane, 300.0, 400.0);
            // The taller card pushes "below" into the second column, whose x follows the width.
            double translateX = below.getTranslateX();
            double translateY = below.getTranslateY();
            double layoutX = below.getLayoutX();
            assertTrue(Math.abs(translateX) > EPSILON, "precondition: the card below glides sideways");

            layout(pane, 340.0, 400.0);

            assertClose(translateX, below.getTranslateX(), "x offset is not re-armed");
            assertClose(translateY, below.getTranslateY(), "y offset is not re-armed");
            assertTrue(Math.abs(below.getLayoutX() - layoutX) > EPSILON, "the target follows the resize");
        });
    }

    /**
     * Verifies removing a leaving child externally (mid-exit) restores its
     * original managed state instead of leaving it stuck unmanaged.
     */
    @Test
    public void externalRemoveDuringExitRestoresManaged() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            Region card = new Region();
            pane.getChildren().add(card);
            new Scene(pane);

            assertTrue(card.isManaged(), "managed before");
            pane.removeAnimated(card);
            assertFalse(card.isManaged(), "unmanaged during exit");

            pane.getChildren().remove(card);
            assertTrue(card.isManaged(), "managed restored after external remove");
        });
    }

    /**
     * Verifies a child mid-fade keeps its in-flight fade across a static relayout
     * pass: the pre-filter must keep submitting nodes the animator still tracks,
     * otherwise the drop detection would finalize the fade to its target.
     */
    @Test
    public void midFadeChildSurvivesStaticRelayout() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            pane.setAnimationDuration(Duration.millis(400.0));
            Region first = card(80.0, 50.0);
            pane.getChildren().add(first);
            new Scene(pane);
            layout(pane, 300.0, 600.0);

            Region entering = card(80.0, 50.0);
            pane.getChildren().add(entering);
            layout(pane, 300.0, 600.0);
            assertTrue(entering.getOpacity() < 1.0 - EPSILON, "entering child fade armed");

            // A static relayout (no geometry change) must not finalize the fade.
            pane.requestLayout();
            pane.layout();
            assertTrue(entering.getOpacity() < 1.0 - EPSILON,
                    "in-flight fade survives a static relayout");

            // Settling via animated=false restores the fade target deterministically.
            pane.setAnimated(false);
            assertClose(1.0, entering.getOpacity(), "fade target restored on settle");
        });
    }

    /**
     * Verifies a vertical content-bias child is measured at its intrinsic height
     * (not at a width-dependent height), matching layoutInArea, so the next card
     * does not leave a phantom gap.
     */
    @Test
    public void verticalBiasChildMeasuredAtIntrinsicHeight() {
        VerticalBiasedRegion biased = new VerticalBiasedRegion();
        Region next = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, biased, next);
        pane.setColumnCount(1);

        layout(pane, 250.0, 1000.0);

        assertClose(50.0, biased.getHeight(), "biased laid out at intrinsic height");
        assertClose(50.0, next.getLayoutY(), "next card directly below, no phantom gap");
    }

    /**
     * Verifies a negative margin (valid in JavaFX) clamps the block instead of
     * driving a negative block height that crashes the layout pass.
     */
    @Test
    public void negativeMarginDoesNotCrashLayout() {
        Region card = card(80.0, 50.0);
        Region next = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card, next);
        pane.setColumnCount(1);
        RXMasonryPane.setMargin(card, new Insets(-50.0));

        layout(pane, 250.0, 1000.0);

        double prefHeight = pane.prefHeight(250.0);
        assertTrue(Double.isFinite(prefHeight) && prefHeight >= 0.0, "finite non-negative pref height");
    }

    /**
     * Verifies an empty pane reports zero content height.
     */
    @Test
    public void emptyPaneReportsZeroContentHeight() {
        RXMasonryPane pane = new RXMasonryPane();
        assertClose(0.0, pane.prefHeight(300.0), "empty pref height");
    }

    /**
     * Verifies a width narrower than one column still resolves to a single column.
     */
    @Test
    public void veryNarrowWidthResolvesToOneColumn() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);

        layout(pane, 30.0, 1000.0);

        assertEquals(1, pane.getActualColumnCount());
        // Both cards stack in the single column.
        assertClose(0.0, a.getLayoutY(), "a y");
        assertClose(50.0, b.getLayoutY(), "b y");
    }

    /**
     * Verifies unmanaged children are excluded from layout and content height.
     */
    @Test
    public void unmanagedChildrenAreExcluded() {
        Region managed = card(80.0, 50.0);
        Region unmanaged = card(80.0, 200.0);
        unmanaged.setManaged(false);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, managed, unmanaged);
        pane.setColumnCount(1);

        layout(pane, 100.0, 1000.0);

        assertClose(50.0, pane.prefHeight(100.0), "pref height ignores unmanaged");
        assertClose(0.0, managed.getLayoutY(), "managed y");
    }

    /**
     * Verifies layout never animates without a scene, leaving transforms neutral.
     */
    @Test
    public void layoutWithoutSceneLeavesTransformsNeutral() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);

        layout(pane, 350.0, 1000.0);
        // a relayout that would otherwise animate still snaps without a scene.
        pane.getChildren().add(card(80.0, 40.0));
        layout(pane, 360.0, 1000.0);

        for (Region child : new Region[]{a, b}) {
            assertClose(0.0, child.getTranslateX(), "translateX");
            assertClose(0.0, child.getTranslateY(), "translateY");
            assertClose(1.0, child.getOpacity(), "opacity");
        }
    }

    /**
     * Verifies size properties accept invalid values and leave coercion to layout.
     */
    @Test
    public void sizePropertiesHandleInvalidValues() {
        RXMasonryPane pane = new RXMasonryPane();

        pane.setColumnWidth(120.0);
        pane.setColumnWidth(0.0);
        assertClose(0.0, pane.getColumnWidth(), "columnWidth zero accepted");
        pane.setColumnWidth(-1.0);
        assertClose(-1.0, pane.getColumnWidth(), "columnWidth negative accepted");
        pane.setColumnWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getColumnWidth()), "columnWidth NaN accepted");

        pane.setHgap(-1.0);
        assertClose(-1.0, pane.getHgap(), "hgap accepted");

        pane.setColumnCount(-1);
        assertEquals(-1, pane.getColumnCount(), "columnCount accepted");

        pane.setPrefColumns(0);
        assertEquals(0, pane.getPrefColumns(), "prefColumns accepted");

        pane.setMaxColumns(-2);
        assertEquals(-2, pane.getMaxColumns(), "maxColumns accepted");
    }

    @Test
    public void invalidGapsAndPrefColumnsSurviveLayout() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));

        // Invalid size hints must not crash the masonry engine or the pref math.
        pane.setColumnWidth(Double.NaN);
        pane.setVgap(-5.0);
        pane.setHgap(Double.NaN);
        layout(pane, 300.0, 200.0);
        double ph = pane.prefHeight(300.0);
        assertTrue(Double.isFinite(ph) && ph >= 0.0, "finite non-negative pref height despite invalid gaps");

        // prefColumns <= 0 still yields a sane preferred width.
        pane.setPrefColumns(0);
        double pw = pane.prefWidth(-1.0);
        assertTrue(Double.isFinite(pw) && pw >= 0.0, "finite non-negative pref width for prefColumns <= 0");
    }

    @Test
    public void invalidColumnWidthUsesDefaultDuringLayout() {
        Region a = card(60.0, 50.0);
        Region b = card(60.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, a, b);
        pane.setColumnWidth(Double.NaN);
        pane.setColumnCount(2);
        pane.setFillWidth(false);

        layout(pane, 600.0, 200.0);

        assertTrue(Double.isNaN(pane.getColumnWidth()), "invalid value remains visible on the property");
        assertClose(260.0, b.getLayoutX(), "second column uses the default track step");
        assertClose(260.0, pane.minWidth(-1.0), "default column width used for min width");
        // The forced count (2) drives the pref width, not prefColumns (3).
        assertClose(520.0, pane.prefWidth(-1.0), "forced count with default column width");
    }

    /**
     * Verifies a forced column count drives the preferred width, mirroring the
     * layout-time priority over prefColumns.
     */
    @Test
    public void forcedColumnCountDrivesPrefWidth() {
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, card(80.0, 50.0));

        assertClose(3 * 100.0 + 2 * 10.0, pane.prefWidth(-1.0), "prefColumns drives pref width");

        pane.setColumnCount(6);
        assertClose(6 * 100.0 + 5 * 10.0, pane.prefWidth(-1.0), "forced count drives pref width");

        // maxColumns still caps the forced count in the pref math.
        pane.setMaxColumns(4);
        assertClose(4 * 100.0 + 3 * 10.0, pane.prefWidth(-1.0), "maxColumns caps the forced count");

        // A pathological forced count is capped like the layout side (4096), so
        // measure and layout never split on the column count.
        pane.setMaxColumns(0);
        pane.setColumnCount(100000);
        assertClose(4096 * 100.0 + 4095 * 10.0, pane.prefWidth(-1.0), "pref capped at 4096 columns");
    }

    /**
     * Verifies negative gaps overlap columns and stacked items in both fill
     * modes: the count formula's step floor bounds the column count only, while
     * the track geometry keeps the caller's raw gaps.
     */
    @Test
    public void negativeGapsOverlapGeometry() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        Region c = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, -20.0, -10.0, a, b, c);
        pane.setFillWidth(false);

        layout(pane, 190.0, 1000.0);

        // step = columnWidth + hgap = 80 -> floor((190 - 20) / 80) = 2 columns.
        assertEquals(2, pane.getActualColumnCount());
        assertClose(80.0, b.getLayoutX(), "columns overlap by the negative hgap");
        // Column bottoms carry the negative vgap: a(50) - 10 = 40 is the shortest.
        assertClose(40.0, c.getLayoutY(), "stacked items overlap by the negative vgap");

        pane.setFillWidth(true);
        layout(pane, 190.0, 1000.0);

        // track = (190 - 1 * (-20)) / 2 = 105; step = 105 - 20 = 85.
        assertClose(85.0, b.getLayoutX(), "stretched track keeps the raw negative hgap step");
    }

    @Test
    public void nullObjectPropertiesSurviveLayout() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setAlignment(null);
        pane.setBreakpointProfile(null);
        pane.setAnimationInterpolator(null);
        pane.setAnimationDuration(null);

        // Each null resolves to its default at the use site, so layout and measurement
        // must not throw (guards against a raw-deref regression like the RXSkeleton one).
        layout(pane, 300.0, 200.0);
        assertTrue(Double.isFinite(pane.prefHeight(300.0)), "null object properties lay out without throwing");
    }

    @Test
    public void negativeVgapOverlapsItems() {
        RXMasonryPane pane = pane(100.0, 0.0, -20.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setColumnCount(1);

        // A finite negative vgap overlaps items (like HBox/VBox negative spacing),
        // it is not clamped to 0: 100 + (-20) + 50 = 130 (vs 150 at a 0 gap).
        assertClose(130.0, pane.prefHeight(300.0), "negative vgap overlaps, not clamped");
    }

    /**
     * Verifies tolerant object and animation properties accept null and otherwise
     * invalid durations, while the per-child span and breakpoint counts still reject
     * non-positive values.
     */
    @Test
    public void objectPropertiesHandleNullAndInvalidValues() {
        RXMasonryPane pane = new RXMasonryPane();

        pane.setAlignment(null);
        assertNull(pane.getAlignment(), "alignment accepts null");

        pane.setBreakpointProfile(null);
        assertNull(pane.getBreakpointProfile(), "breakpointProfile accepts null");

        pane.setAnimationDuration(null);
        assertNull(pane.getAnimationDuration(), "animationDuration accepts null");
        Duration negative = Duration.millis(-1.0);
        pane.setAnimationDuration(negative);
        assertSame(negative, pane.getAnimationDuration(), "animationDuration accepts negative");
        pane.setAnimationDuration(Duration.INDEFINITE);
        assertSame(Duration.INDEFINITE, pane.getAnimationDuration(), "animationDuration accepts indefinite");

        pane.setAnimationInterpolator(null);
        assertNull(pane.getAnimationInterpolator(), "animationInterpolator accepts null");

        assertThrows(IllegalArgumentException.class,
                () -> RXMasonryPane.setColumnSpan(card(10.0, 10.0), 0));
        assertThrows(IllegalArgumentException.class, () -> pane.setBreakpointColumns(RXBreakpoint.MD, -1));
    }

    /**
     * Verifies CSS metadata exposes the styleable RXMasonryPane properties.
     */
    @Test
    public void cssMetadataContainsRxProperties() {
        assertTrue(hasCssProperty("-rx-column-width"));
        assertTrue(hasCssProperty("-rx-hgap"));
        assertTrue(hasCssProperty("-rx-vgap"));
        assertTrue(hasCssProperty("-rx-column-count"));
        assertTrue(hasCssProperty("-rx-pref-columns"));
        assertTrue(hasCssProperty("-rx-max-columns"));
        assertTrue(hasCssProperty("-rx-fill-width"));
        assertTrue(hasCssProperty("-rx-alignment"));
        assertTrue(hasCssProperty("-rx-animated"));
        assertTrue(hasCssProperty("-rx-animation-duration"));
    }

    /**
     * Verifies filled columns stay inside the padded content area at every width and
     * render scale, keep at least the hgap between them, share one snapped width and
     * reach both content edges.
     */
    @Test
    public void filledColumnsStayInsideTheContentArea() throws Exception {
        runOnFx(() -> {
            Region[] cards = new Region[18];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = card(40.0, 30.0);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 520.0; width += 0.25) {
                    layout(pane, width, 600.0);
                    String at = "scale " + scale + ", width " + width;
                    List<Region> row = new ArrayList<>();
                    for (Region card : cards) {
                        if (card.getLayoutY() == pane.snappedTopInset()) {
                            row.add(card);
                        }
                    }
                    row.sort(Comparator.comparingDouble(Region::getLayoutX));
                    assertEquals(pane.getActualColumnCount(), row.size(), at + ": first row is full");
                    assertClose(pane.snappedLeftInset(), row.get(0).getLayoutX(), at + ": left edge");
                    double contentRight = pane.getWidth() - pane.snappedRightInset();
                    Region last = row.get(row.size() - 1);
                    double edge = last.getLayoutX() + last.getWidth();
                    assertTrue(edge <= contentRight + 1e-9, at + ": edge " + edge + " > " + contentRight);
                    assertTrue(edge > contentRight - 1.0 / scale - 1e-9, at + ": edge " + edge + " stops short");
                    for (int i = 0; i < row.size(); i++) {
                        Region card = row.get(i);
                        assertOnGrid(card.getLayoutX(), scale, at + ": x of column " + i);
                        assertClose(row.get(0).getWidth(), card.getWidth(), at + ": width of column " + i);
                        if (i > 0) {
                            Region previous = row.get(i - 1);
                            double gap = card.getLayoutX() - (previous.getLayoutX() + previous.getWidth());
                            assertTrue(gap >= 10.0 - 1e-9, at + ": gap " + gap + " before column " + i);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a pane at its own preferred width shows its preferred column count at
     * every render scale; at 1.25 the 10 px gap snaps to 10.4 and the column division
     * used to come out just below four.
     */
    @Test
    public void preferredWidthShowsThePreferredColumnsAtEveryScale() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            pane.setColumnWidth(60);
            pane.setHgap(10);
            pane.setPrefColumns(4);
            pane.setAnimated(false);
            for (int i = 0; i < 8; i++) {
                pane.getChildren().add(card(60.0, 40.0));
            }
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                double width = pane.prefWidth(-1);
                layout(pane, width, 400.0);
                assertEquals(4, pane.getActualColumnCount(), "scale " + scale + ", width " + width);
            }
        });
    }

    /**
     * Verifies the preferred width is exactly the grid value of the padding plus the
     * snapped columns and gaps at every render scale. Their sum is on the pixel grid
     * already; snapped as a size once more it grew a device pixel at fractional scales.
     * Left a hair above its grid line, it would grow one in a parent that snaps it.
     */
    @Test
    public void preferredWidthIsTheSumOfTheSnappedColumns() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                RXMasonryPane pane = new RXMasonryPane();
                pane.setAnimated(false);
                pane.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
                pane.getChildren().add(card(60.0, 40.0));
                stageFor(pane).setRenderScaleX(scale);
                for (double columnWidth : new double[] {60.0, 61.0, 100.0, 137.3}) {
                    for (double hgap : new double[] {0.0, 9.0, 10.0, 12.5}) {
                        for (int columns = 1; columns <= 12; columns++) {
                            pane.setColumnWidth(columnWidth);
                            pane.setHgap(hgap);
                            pane.setPrefColumns(columns);
                            long expected = Math.round(pane.snappedLeftInset() * scale)
                                    + columns * Math.round(pane.snapSizeX(columnWidth) * scale)
                                    + (columns - 1) * Math.round(pane.snapSpaceX(hgap) * scale)
                                    + Math.round(pane.snappedRightInset() * scale);
                            assertEquals(expected / scale, pane.prefWidth(-1), 0.0, expected + " device pixels at scale "
                                    + scale + ": " + columns + " columns of " + columnWidth + ", gap " + hgap);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies the preferred height is exactly the grid value of the padding plus the
     * snapped cards and gaps of the tallest column at every vertical render scale, with
     * a horizontal scale that differs from it.
     */
    @Test
    public void preferredHeightIsTheSumOfTheSnappedBlocks() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                for (double height : new double[] {40.0, 41.0, 55.5}) {
                    for (double vgap : new double[] {0.0, 9.0, 10.0, 12.5}) {
                        RXMasonryPane pane = new RXMasonryPane();
                        pane.setAnimated(false);
                        pane.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
                        pane.setColumnWidth(60.0);
                        pane.setColumnCount(1);
                        pane.setVgap(vgap);
                        Stage stage = stageFor(pane);
                        stage.setRenderScaleX(scale == 1.5 ? 1.25 : 1.5);
                        stage.setRenderScaleY(scale);
                        for (int count = 1; count <= 12; count++) {
                            pane.getChildren().add(card(60.0, height));
                            long expected = Math.round(pane.snappedTopInset() * scale)
                                    + count * Math.round(pane.snapSizeY(height) * scale)
                                    + (count - 1) * Math.round(pane.snapSpaceY(vgap) * scale)
                                    + Math.round(pane.snappedBottomInset() * scale);
                            assertEquals(expected / scale, pane.prefHeight(-1), 0.0, expected + " device pixels at scale "
                                    + scale + ": " + count + " cards of " + height + ", gap " + vgap);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies the minimum width, one column and the padding, is on the pixel grid as
     * the preferred width is: a pane preferring one column never reports a minimum above
     * its preferred width, which a parent would take in its place.
     */
    @Test
    public void minimumWidthNeverExceedsThePreferredWidth() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                RXMasonryPane pane = new RXMasonryPane();
                pane.setAnimated(false);
                pane.setPrefColumns(1);
                pane.getChildren().add(card(60.0, 40.0));
                stageFor(pane).setRenderScaleX(scale);
                for (double columnWidth : new double[] {100.0, 143.0, 150.0, 160.0, 170.0, 260.0}) {
                    for (double padding : new double[] {0.0, 4.25, 7.5, 9.0, 10.0, 13.0}) {
                        pane.setColumnWidth(columnWidth);
                        pane.setPadding(new Insets(3.0, padding, 3.0, padding));
                        String at = "scale " + scale + ", column " + columnWidth + ", padding " + padding;
                        long expected = Math.round(pane.snappedLeftInset() * scale)
                                + Math.round(pane.snapSizeX(columnWidth) * scale)
                                + Math.round(pane.snappedRightInset() * scale);
                        assertEquals(expected / scale, pane.minWidth(-1), 0.0, at + ": minimum width");
                        assertTrue(pane.minWidth(-1) <= pane.prefWidth(-1),
                                at + ": minimum " + pane.minWidth(-1) + " above preferred " + pane.prefWidth(-1));
                    }
                }
            }
        });
    }

    /**
     * Verifies a parent that sizes the pane to its preferred width gives it the snapped
     * columns, gaps and padding and no device pixel more, at the scales where the
     * parent's own snapping of a grid value adds none.
     */
    @Test
    public void parentGivesThePaneItsSnappedColumns() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.25, 1.5, 2.0}) {
                RXMasonryPane pane = new RXMasonryPane();
                pane.setAnimated(false);
                pane.getChildren().add(card(60.0, 40.0));
                HBox box = new HBox(pane);
                stageFor(box).setRenderScaleX(scale);
                for (double columnWidth : new double[] {60.0, 61.0, 143.0, 160.0}) {
                    for (double hgap : new double[] {0.0, 9.0, 10.0, 12.5}) {
                        for (double padding : new double[] {0.0, 4.25, 9.0}) {
                            for (int columns = 1; columns <= 8; columns++) {
                                pane.setColumnWidth(columnWidth);
                                pane.setHgap(hgap);
                                pane.setPadding(new Insets(3.0, padding, 3.0, padding));
                                pane.setPrefColumns(columns);
                                layout(box, 4000.0, 400.0);
                                long expected = Math.round(pane.snappedLeftInset() * scale)
                                        + columns * Math.round(pane.snapSizeX(columnWidth) * scale)
                                        + (columns - 1) * Math.round(pane.snapSpaceX(hgap) * scale)
                                        + Math.round(pane.snappedRightInset() * scale);
                                String at = "scale " + scale + ": " + columns + " columns of " + columnWidth
                                        + ", gap " + hgap + ", padding " + padding;
                                assertEquals(expected, pane.getWidth() * scale, 1e-6, at + ": device pixels");
                                assertEquals(columns, pane.getActualColumnCount(), at + ": columns");
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a child that wants more than its columns is as wide as its slot and sits
     * on it, in fixed-width and in filled columns, under every horizontal alignment and
     * with margins: one reaching the last column ends at the content edge of a pane at
     * its preferred width.
     */
    @Test
    public void childrenWantingMoreThanTheirColumnsTakeTheirSlot() throws Exception {
        runOnFx(() -> {
            double[][] geometries = {{60.0, 10.0, 4.0}, {61.0, 0.0, 11.0}, {49.0, 22.0, 8.0}, {40.0, 10.0, 4.0},
                    {100.0, 10.0, 3.0}};
            for (boolean fillWidth : new boolean[] {false, true}) {
                for (Pos alignment : new Pos[] {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT}) {
                    for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                        for (double[] geometry : geometries) {
                            int columns = (int) geometry[2];
                            // One card a column, then one spanning every column and one half of them:
                            // equal heights put card i in column i and the spanning ones in column 0.
                            int[] spans = new int[columns + 2];
                            Region[] cards = new Region[spans.length];
                            for (int i = 0; i < cards.length; i++) {
                                spans[i] = i < columns ? 1 : i == columns ? columns : Math.max(1, columns / 2);
                                cards[i] = card(5000.0, 30.0);
                                RXMasonryPane.setColumnSpan(cards[i], spans[i]);
                                if (i % 2 == 1) {
                                    RXMasonryPane.setMargin(cards[i], new Insets(0.0, 3.0, 0.0, 5.0));
                                }
                            }
                            RXMasonryPane pane = pane(geometry[0], geometry[1], 6.0, cards);
                            pane.setAnimated(false);
                            pane.setFillWidth(fillWidth);
                            pane.setAlignment(alignment);
                            pane.setPrefColumns(columns);
                            pane.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
                            stageFor(pane).setRenderScaleX(scale);
                            layout(pane, pane.prefWidth(-1), 600.0);

                            String at = (fillWidth ? "filled, " : "fixed, ") + alignment + ", scale " + scale + ", "
                                    + columns + " columns of " + geometry[0] + ", gap " + geometry[1];
                            assertEquals(columns, pane.getActualColumnCount(), at + ": columns");
                            double track = pane.snapSizeX(geometry[0]);
                            double gap = pane.snapSpaceX(geometry[1]);
                            double contentRight = pane.getWidth() - pane.snappedRightInset();
                            for (int i = 0; i < cards.length; i++) {
                                Insets margin = RXMasonryPane.getMargin(cards[i]);
                                double left = margin == null ? 0.0 : pane.snapSpaceX(margin.getLeft());
                                double right = margin == null ? 0.0 : pane.snapSpaceX(margin.getRight());
                                int column = i < columns ? i : 0;
                                double slotX = pane.snappedLeftInset() + column * (track + gap) + left;
                                double slotWidth = spans[i] * track + (spans[i] - 1) * gap - left - right;
                                assertClose(slotX, cards[i].getLayoutX(), at + ": x of card " + i);
                                assertEquals(slotWidth, cards[i].getWidth(), 1e-9, at + ": width of card " + i);
                                double edge = cards[i].getLayoutX() + cards[i].getWidth() + right;
                                assertTrue(edge <= contentRight + 1e-9,
                                        at + ": card " + i + " ends at " + edge + ", content at " + contentRight);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a child narrower than its slot, by its preferred width in fixed-width
     * columns or by its maximum width in filled ones, keeps that width and is placed in
     * the slot by the pane's horizontal alignment.
     */
    @Test
    public void childNarrowerThanItsSlotFollowsTheAlignment() throws Exception {
        runOnFx(() -> {
            for (boolean fillWidth : new boolean[] {false, true}) {
                for (double scale : new double[] {1.0, 1.5}) {
                    Region[] cards = new Region[3];
                    for (int i = 0; i < cards.length; i++) {
                        cards[i] = card(40.0, 30.0);
                        cards[i].setMaxWidth(40.0);
                    }
                    RXMasonryPane pane = pane(100.0, 10.0, 6.0, cards);
                    pane.setAnimated(false);
                    pane.setFillWidth(fillWidth);
                    pane.setPrefColumns(3);
                    stageFor(pane).setRenderScaleX(scale);
                    double[] offsets = {0.0, 30.0, 60.0};
                    Pos[] alignments = {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT};
                    for (int a = 0; a < alignments.length; a++) {
                        pane.setAlignment(alignments[a]);
                        layout(pane, pane.prefWidth(-1), 400.0);
                        for (int i = 0; i < cards.length; i++) {
                            String at = (fillWidth ? "filled, " : "fixed, ") + alignments[a] + ", scale " + scale
                                    + ", card " + i;
                            assertClose(40.0, cards[i].getWidth(), at + ": width");
                            assertClose(i * 110.0 + offsets[a], cards[i].getLayoutX(), at + ": x");
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a child that cannot be resized, as a shape or an image view, keeps its
     * size and is placed in its slot by the pane's horizontal alignment and its margin.
     */
    @Test
    public void nonResizableChildrenArePlacedInTheirSlots() throws Exception {
        runOnFx(() -> {
            for (boolean fillWidth : new boolean[] {false, true}) {
                for (double scale : new double[] {1.0, 1.5}) {
                    Rectangle[] shapes = new Rectangle[6];
                    for (int i = 0; i < shapes.length; i++) {
                        shapes[i] = new Rectangle(40.0, 20.0);
                    }
                    RXMasonryPane.setMargin(shapes[4], new Insets(4.0, 6.0, 0.0, 10.0));
                    RXMasonryPane pane = new RXMasonryPane(shapes);
                    pane.setColumnWidth(100.0);
                    pane.setHgap(10.0);
                    pane.setVgap(6.0);
                    pane.setAnimated(false);
                    pane.setFillWidth(fillWidth);
                    pane.setPrefColumns(3);
                    stageFor(pane).setRenderScaleX(scale);
                    double[] offsets = {0.0, 30.0, 60.0};
                    double[] marginedOffsets = {10.0, 32.0, 54.0};
                    Pos[] alignments = {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT};
                    for (int a = 0; a < alignments.length; a++) {
                        pane.setAlignment(alignments[a]);
                        layout(pane, pane.prefWidth(-1), 400.0);
                        for (int i = 0; i < shapes.length; i++) {
                            String at = (fillWidth ? "filled, " : "fixed, ") + alignments[a] + ", scale " + scale
                                    + ", shape " + i;
                            double offset = i == 4 ? marginedOffsets[a] : offsets[a];
                            assertClose((i % 3) * 110.0 + offset, shapes[i].getLayoutX(), at + ": x");
                            assertClose(i < 3 ? 0.0 : i == 4 ? 30.0 : 26.0, shapes[i].getLayoutY(), at + ": y");
                            assertClose(40.0, shapes[i].getLayoutBounds().getWidth(), at + ": width");
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a minimum width of the child's own that is a rounding error above a grid
     * line counts as on it. At 1.5 a column of 217 snaps to 217.33; that plus 40 comes to
     * 386.00000000000006 device pixels, which ceiled as Region does would be 387.
     */
    @Test
    public void ownMinimumARoundingErrorAboveTheGridStaysOnIt() throws Exception {
        runOnFx(() -> {
            for (boolean fillWidth : new boolean[] {false, true}) {
                Region card = card(60.0, 30.0);
                RXMasonryPane pane = pane(217.0, 10.0, 6.0, card);
                pane.setAnimated(false);
                pane.setFillWidth(fillWidth);
                stageFor(pane).setRenderScaleX(1.5);
                double minimum = pane.snapSizeX(217.0) + 40.0;
                assertTrue(minimum * 1.5 > 386.0, "setup: the minimum is above its grid line");
                card.setMinWidth(minimum);
                layout(pane, pane.snapSizeX(217.0), 400.0);
                assertEquals(386.0 / 1.5, card.getWidth(), 1.0e-9, fillWidth ? "filled" : "fixed");
            }
        });
    }

    /**
     * Verifies a child's height is snapped on the vertical grid, as its block is, where
     * the two render scales differ: at 1.25 by 1.5 a child of 30.3 is 46 device pixels
     * high and the next block starts a gap below it. A negative top margin moves a child
     * up without taking from its height.
     */
    @Test
    public void childHeightIsSnappedOnTheVerticalGrid() throws Exception {
        runOnFx(() -> {
            for (boolean fillWidth : new boolean[] {false, true}) {
                String at = fillWidth ? "filled" : "fixed";
                Region first = card(60.0, 30.3);
                Region second = card(60.0, 20.0);
                Region third = card(60.0, 20.0);
                RXMasonryPane.setMargin(second, new Insets(-4.0, 0.0, 0.0, 0.0));
                RXMasonryPane pane = pane(60.0, 10.0, 6.0, first, second, third);
                pane.setAnimated(false);
                pane.setFillWidth(fillWidth);
                Stage stage = stageFor(pane);
                stage.setRenderScaleX(1.25);
                stage.setRenderScaleY(1.5);
                layout(pane, 60.0, 400.0);
                assertEquals(46.0 / 1.5, first.getHeight(), 1.0e-9, at + ": height of the first child");
                assertEquals(first.getHeight() + 6.0 - 4.0, second.getLayoutY(), 1.0e-9,
                        at + ": y of the second child");
                assertEquals(20.0, second.getHeight(), 1.0e-9, at + ": height of the second child");
                assertEquals(second.getLayoutY() + 20.0 + 6.0, third.getLayoutY(), 1.0e-9,
                        at + ": y of the third child");
            }
        });
    }

    /**
     * Verifies a child whose height depends on its width is given the height its block was
     * measured with when its width is off the pixel grid: at 1.5 a preferred width of 40.3
     * is laid out 40.67 wide, where the child would ask for a pixel less than its block.
     */
    @Test
    public void biasedChildOffTheGridIsAsHighAsItsBlock() throws Exception {
        runOnFx(() -> {
            WidthRecordingRegion biased = new WidthRecordingRegion();
            biased.setPrefWidth(40.3);
            Region next = card(40.0, 20.0);
            RXMasonryPane pane = pane(100.0, 10.0, 6.0, biased, next);
            pane.setAnimated(false);
            pane.setFillWidth(false);
            stageFor(pane).setRenderScaleX(1.5);
            layout(pane, 100.0, 400.0);
            assertEquals(61.0 / 1.5, biased.getWidth(), 1.0e-9, "setup: the width is off the preferred width");
            assertEquals(Math.ceil(6000.0 / 40.3), biased.getHeight(), 1.0e-9, "the height of the block");
            assertEquals(biased.getHeight() + 6.0, next.getLayoutY(), 1.0e-9, "the next block starts a gap below");
        });
    }

    /**
     * Verifies a child whose width depends on its height is asked for its widths at the
     * height it is given: its minimum width at that height takes it past a narrower slot.
     */
    @Test
    public void verticallyBiasedChildIsMeasuredAtItsHeight() throws Exception {
        runOnFx(() -> {
            for (boolean fillWidth : new boolean[] {false, true}) {
                Region biased = new Region() {
                    @Override
                    public Orientation getContentBias() {
                        return Orientation.VERTICAL;
                    }

                    @Override
                    protected double computePrefHeight(double width) {
                        return 40.0;
                    }

                    @Override
                    protected double computeMinWidth(double height) {
                        return height < 0.0 ? 10.0 : 80.0;
                    }

                    @Override
                    protected double computePrefWidth(double height) {
                        return 80.0;
                    }
                };
                RXMasonryPane pane = pane(60.0, 10.0, 6.0, biased);
                pane.setAnimated(false);
                pane.setFillWidth(fillWidth);
                layout(pane, 60.0, 400.0);
                assertEquals(80.0, biased.getWidth(), 1.0e-9, fillWidth ? "filled" : "fixed");
                assertEquals(40.0, biased.getHeight(), 1.0e-9, fillWidth ? "filled" : "fixed");
            }
        });
    }

    /**
     * Verifies a layout pass sets the width of a child once, and a pass that changes
     * nothing sets none: a child ceiled past its slot and pulled back was resized twice
     * on every pass, and laid out again each time. Columns of 60 with a gap of 10 meet
     * that when they are filled, columns of 63 with no gap when their width is fixed.
     */
    @Test
    public void passResizesAChildAtMostOnce() throws Exception {
        runOnFx(() -> {
            double[][] geometries = {{60.0, 10.0}, {63.0, 0.0}};
            for (double[] geometry : geometries) {
                for (boolean fillWidth : new boolean[] {false, true}) {
                    for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                        CountingRegion[] cards = new CountingRegion[24];
                        for (int i = 0; i < cards.length; i++) {
                            cards[i] = new CountingRegion();
                            cards[i].setPrefSize(5000.0, 20.0 + (i * 13) % 50);
                            RXMasonryPane.setColumnSpan(cards[i], i % 5 == 2 ? 2 : i % 11 == 6 ? 3 : 1);
                        }
                        RXMasonryPane pane = paddedFilledPane(cards);
                        pane.setColumnWidth(geometry[0]);
                        pane.setHgap(geometry[1]);
                        pane.setFillWidth(fillWidth);
                        stageFor(pane).setRenderScaleX(scale);
                        for (double width = 300.0; width <= 520.0; width += 0.25) {
                            String at = (fillWidth ? "filled" : "fixed") + ", scale " + scale + ", columns of "
                                    + geometry[0] + ", gap " + geometry[1] + ", width " + width;
                            for (CountingRegion card : cards) {
                                card.widthChanges = 0;
                            }
                            layout(pane, width, 800.0);
                            for (int i = 0; i < cards.length; i++) {
                                assertTrue(cards[i].widthChanges <= 1,
                                        at + ": card " + i + " resized " + cards[i].widthChanges + " times");
                            }
                            for (CountingRegion card : cards) {
                                card.widthChanges = 0;
                                card.layouts = 0;
                            }
                            pane.requestLayout();
                            pane.layout();
                            for (int i = 0; i < cards.length; i++) {
                                assertEquals(0, cards[i].widthChanges,
                                        at + ": card " + i + " resized on a repeated pass");
                                assertEquals(0, cards[i].layouts,
                                        at + ": card " + i + " laid out on a repeated pass");
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a pane whose children follow their own width at once, as a wrapping label
     * does, comes to rest. Such a child, resized past its slot and back in one pass,
     * changed its content both times and asked for another layout from inside the pass:
     * the pane laid out on every pulse.
     */
    @Test
    public void layoutComesToRestWithWrappingLabels() throws Exception {
        runOnFx(() -> {
            double[][] geometries = {{63.0, 0.0, 3.0}, {61.0, 8.0, 2.0}, {261.0, 10.0, 3.0}, {60.0, 10.0, 4.0}};
            for (boolean fillWidth : new boolean[] {false, true}) {
                for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                    for (double[] geometry : geometries) {
                        int columns = (int) geometry[2];
                        RXMasonryPane pane = new RXMasonryPane();
                        pane.setFillWidth(fillWidth);
                        pane.setColumnWidth(geometry[0]);
                        pane.setHgap(geometry[1]);
                        pane.setPrefColumns(columns);
                        Label banner = wrappingLabel();
                        RXMasonryPane.setColumnSpan(banner, columns);
                        pane.getChildren().add(banner);
                        for (int i = 0; i < columns * 2; i++) {
                            pane.getChildren().add(wrappingLabel());
                        }
                        stageFor(pane).setRenderScaleX(scale);
                        pane.applyCss();
                        double preferred = pane.prefWidth(-1);
                        // Fixed columns do not depend on the width; filled ones are tried at many.
                        double widest = fillWidth ? preferred + 40.0 : preferred;
                        for (double width = preferred; width <= widest; width += 0.25) {
                            pane.resize(width, 2000.0);
                            for (int pass = 0; pass < 6; pass++) {
                                pane.applyCss();
                                pane.layout();
                            }
                            assertFalse(pane.isNeedsLayout(), (fillWidth ? "filled" : "fixed") + ", scale " + scale
                                    + ", " + columns + " columns of " + geometry[0] + ", gap " + geometry[1]
                                    + ", width " + width + ": still asking for a layout");
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies filled spanning children stay inside the content area and keep the hgap
     * from every child beside them, wherever they land.
     */
    @Test
    public void filledSpanningChildrenStayInsideTheContentArea() throws Exception {
        runOnFx(() -> {
            Region[] cards = new Region[24];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = card(40.0, 20.0 + (i * 13) % 50);
                RXMasonryPane.setColumnSpan(cards[i], i % 5 == 2 ? 2 : i % 11 == 6 ? 3 : 1);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 520.0; width += 0.25) {
                    layout(pane, width, 800.0);
                    String at = "scale " + scale + ", width " + width;
                    double contentRight = pane.getWidth() - pane.snappedRightInset();
                    for (Region a : cards) {
                        double right = a.getLayoutX() + a.getWidth();
                        assertTrue(right <= contentRight + 1e-9, at + ": edge " + right + " > " + contentRight);
                        assertOnGrid(a.getLayoutX(), scale, at + ": x");
                        assertOnGrid(right, scale, at + ": right edge");
                        for (Region b : cards) {
                            boolean beside = a != b && a.getLayoutX() < b.getLayoutX()
                                    && a.getLayoutY() < b.getLayoutY() + b.getHeight()
                                    && b.getLayoutY() < a.getLayoutY() + a.getHeight();
                            if (beside) {
                                double gap = b.getLayoutX() - right;
                                assertTrue(gap >= 10.0 - 1e-9, at + ": gap " + gap);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a width-dependent child is measured at the width it is laid out at, for
     * single and spanning children at fractional render scales.
     */
    @Test
    public void childHeightIsMeasuredAtTheLaidOutWidth() throws Exception {
        runOnFx(() -> {
            WidthRecordingRegion[] cards = new WidthRecordingRegion[16];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = new WidthRecordingRegion();
                RXMasonryPane.setColumnSpan(cards[i], i % 3 == 1 ? 2 : 1);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 420.0; width += 0.25) {
                    for (WidthRecordingRegion card : cards) {
                        card.askedWidths.clear();
                    }
                    layout(pane, width, 2000.0);
                    for (int i = 0; i < cards.length; i++) {
                        for (double asked : cards[i].askedWidths) {
                            assertEquals(cards[i].getWidth(), asked, 1e-9,
                                    "card " + i + " at scale " + scale + ", width " + width);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies filled columns move to the new grid on a snapToPixel change with no other
     * layout request, and on a render scale change at the next layout.
     */
    @Test
    public void filledColumnsFollowASnapOrRenderScaleChange() throws Exception {
        runOnFx(() -> {
            Region[] cards = {card(40.0, 30.0), card(40.0, 30.0), card(40.0, 30.0)};
            RXMasonryPane pane = pane(60.0, 10.0, 6.0, cards);
            pane.setAnimated(false);
            pane.setColumnCount(3);
            Region host = new StackPane(pane);
            Stage stage = stageFor(host);
            layout(host, 301.0, 400.0);
            assertClose(93.0, cards[0].getWidth(), "snapped at 1x");

            pane.setSnapToPixel(false);
            host.layout();
            assertClose(281.0 / 3.0, cards[0].getWidth(), "unsnapped");
            pane.setSnapToPixel(true);
            host.layout();
            assertClose(93.0, cards[0].getWidth(), "snapped again");

            // A render scale change requests no layout (JavaFX Region behaves the same),
            // so the new grid applies at the pane's next layout.
            stage.setRenderScaleX(2.0);
            pane.requestLayout();
            host.layout();
            assertClose(93.5, cards[0].getWidth(), "snapped at 2x");
        });
    }

    /**
     * Verifies a filled child whose minimum width exceeds its column keeps that minimum
     * width, as layoutInArea gives it, instead of being squeezed into the slot.
     */
    @Test
    public void filledChildKeepsAMinimumWidthWiderThanItsColumn() throws Exception {
        runOnFx(() -> {
            Region wide = card(40.0, 30.0);
            wide.setMinWidth(120.0);
            RXMasonryPane pane = pane(60.0, 10.0, 6.0, wide, card(40.0, 30.0), card(40.0, 30.0));
            pane.setAnimated(false);
            Stage stage = stageFor(pane);
            stage.setRenderScaleX(1.75);
            layout(pane, 301.0, 400.0);
            assertClose(120.0, wide.getWidth(), "min width");
        });
    }

    /**
     * Verifies fixed-width columns are unaffected by the render scale snapping of filled
     * columns: they keep the configured hgap.
     */
    @Test
    public void fixedColumnsKeepTheConfiguredGapAtAnyScale() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                Region first = card(40.0, 30.0);
                Region second = card(40.0, 30.0);
                RXMasonryPane pane = pane(60.0, 10.0, 6.0, first, second);
                pane.setFillWidth(false);
                Stage stage = stageFor(pane);
                stage.setRenderScaleX(scale);
                layout(pane, 301.0, 400.0);
                double snappedGap = Math.round(10.0 * scale) / scale;
                double secondX = Math.round((60.0 + snappedGap) * scale) / scale;
                assertClose(0.0, first.getLayoutX(), "first x @ " + scale);
                assertClose(40.0, first.getWidth(), "first width @ " + scale);
                assertClose(secondX, second.getLayoutX(), "second x @ " + scale);
                assertClose(40.0, second.getWidth(), "second width @ " + scale);
            }
        });
    }

    /**
     * Verifies fixed columns stay inside the content area and on the pixel grid for every
     * alignment at any render scale: a right-aligned block ends within a device pixel of
     * the content edge.
     */
    @Test
    public void fixedColumnsStayInsideTheContentAreaForEveryAlignment() throws Exception {
        runOnFx(() -> {
            for (Pos pos : new Pos[] {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT}) {
                // Ten full-width cards fill the first rows; a margined card and a card
                // narrower than its column follow below them.
                Region[] cards = new Region[12];
                cards[0] = card(130.0, 30.0);
                for (int i = 1; i < cards.length; i++) {
                    cards[i] = card(60.0, 30.0);
                }
                cards[11].setPrefWidth(40.0);
                RXMasonryPane pane = paddedFilledPane(cards);
                RXMasonryPane.setColumnSpan(cards[0], 2);
                RXMasonryPane.setMargin(cards[10], new Insets(1.5, 2.0, 0.0, 3.0));
                pane.setFillWidth(false);
                pane.setAlignment(pos);
                Stage stage = stageFor(pane);
                for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                    stage.setRenderScaleX(scale);
                    for (int base = 300; base < 420; base++) {
                        for (double fraction : new double[] {0.0, 0.25, 0.5, 0.75, 1.0 / 3.0, 2.0 / 3.0}) {
                            double width = base + fraction;
                            layout(pane, width, 600.0);
                            String at = pos + ", scale " + scale + ", width " + width;
                            double left = pane.snappedLeftInset();
                            double right = pane.getWidth() - pane.snappedRightInset();
                            double first = Double.MAX_VALUE;
                            double last = -Double.MAX_VALUE;
                            List<Region> firstRow = new ArrayList<>();
                            for (Region card : cards) {
                                assertOnGrid(card.getLayoutX(), scale, at + ": x");
                                assertOnGrid(card.getLayoutX() + card.getWidth(), scale, at + ": end");
                                first = Math.min(first, card.getLayoutX());
                                last = Math.max(last, card.getLayoutX() + card.getWidth());
                                if (card.getLayoutY() == pane.snappedTopInset()) {
                                    firstRow.add(card);
                                }
                            }
                            assertTrue(firstRow.size() >= 2, at + ": first row holds " + firstRow.size() + " cards");
                            firstRow.sort(Comparator.comparingDouble(Region::getLayoutX));
                            for (int i = 1; i < firstRow.size(); i++) {
                                Region previous = firstRow.get(i - 1);
                                assertTrue(firstRow.get(i).getLayoutX()
                                        >= previous.getLayoutX() + previous.getWidth() - 1e-9,
                                        at + ": card " + i + " overlaps the one before");
                            }
                            assertTrue(first >= left - 1e-9, at + ": first " + first + " < " + left);
                            assertTrue(last <= right + 1e-9, at + ": last " + last + " > " + right);
                            double columnX = cards[10].getLayoutX() - Math.round(3.0 * scale) / scale;
                            boolean onColumn = false;
                            for (int i = 0; i < 10; i++) {
                                onColumn |= Math.abs(cards[i].getLayoutX() - columnX) < 1e-9;
                            }
                            assertTrue(onColumn, at + ": the margined card is not inset from a column");
                            double unit = 1.0 / scale;
                            switch (pos.getHpos()) {
                                case LEFT -> assertClose(left, first, at + ": leading edge");
                                case RIGHT -> assertClose(left + Math.floor((right - left) * scale + 1.0e-9) / scale,
                                        last, at + ": trailing edge");
                                default -> assertTrue(Math.abs((first - left) - (right - last)) <= unit + 1e-9,
                                        at + ": centred within a pixel");
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a right-aligned fixed block on a grid width with fractional padding ends
     * exactly on the floored content edge, where the subtracted insets leave rounding noise.
     */
    @Test
    public void gridWidthWithFractionalPaddingEndsOnTheContentEdge() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.25, 1.5, 1.75}) {
                Region[] cards = new Region[8];
                for (int i = 0; i < cards.length; i++) {
                    cards[i] = card(60.0, 30.0);
                }
                RXMasonryPane pane = paddedFilledPane(cards);
                pane.setPadding(new Insets(0.0, 0.6, 0.0, 0.6));
                pane.setFillWidth(false);
                pane.setAlignment(Pos.TOP_RIGHT);
                stageFor(pane).setRenderScaleX(scale);
                for (int k = 375; k < 600; k++) {
                    double width = k / scale;
                    layout(pane, width, 600.0);
                    double left = pane.snappedLeftInset();
                    double right = width - pane.snappedRightInset();
                    double last = -Double.MAX_VALUE;
                    for (Region card : cards) {
                        last = Math.max(last, card.getLayoutX() + card.getWidth());
                    }
                    assertClose(left + Math.floor((right - left) * scale + 1.0e-9) / scale, last,
                            "scale " + scale + ", width " + width);
                }
            }
        });
    }

    /**
     * Verifies a centred fixed block keeps its offset from the unfloored content width.
     */
    @Test
    public void centredFixedColumnsKeepTheUnflooredOffset() throws Exception {
        runOnFx(() -> {
            Region[] cards = {card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0)};
            RXMasonryPane pane = paddedFilledPane(cards);
            pane.setFillWidth(false);
            pane.setAlignment(Pos.TOP_CENTER);
            stageFor(pane).setRenderScaleX(1.5);
            // The content width 301 - 4 - 22/3 holds four 60 px columns (270 px). Half the
            // slack is 14.75 device pixels and rounds to 15. Flooring the width would leave
            // exactly 14.5, which still rounds to 15 in exact arithmetic; in doubles it lands
            // just below and rounds to 14. The sample tells the two apart only through that
            // tie, which a 1x or 2x scale would not produce.
            layout(pane, 301.0, 600.0);
            assertEquals(4, pane.getActualColumnCount());
            assertClose(4.0 + 15.0 / 1.5, cards[0].getLayoutX(), "first column x");
        });
    }

    /**
     * Verifies a right-aligned fixed block keeps the continuous geometry, flush with the
     * content edge, when the pane does not snap to pixels.
     */
    @Test
    public void unsnappedRightAlignedColumnsEndAtTheContentEdge() throws Exception {
        runOnFx(() -> {
            // Four 60 px columns with a 10 px gap fit the 285.3 px content width.
            Region[] cards = {card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0)};
            RXMasonryPane pane = paddedFilledPane(cards);
            pane.setFillWidth(false);
            pane.setAlignment(Pos.TOP_RIGHT);
            pane.setSnapToPixel(false);
            stageFor(pane).setRenderScaleX(1.5);
            layout(pane, 297.05, 600.0);
            assertEquals(4, pane.getActualColumnCount());
            Region last = cards[3];
            assertClose(297.05 - 7.5, last.getLayoutX() + last.getWidth(), "trailing edge");
        });
    }

    // ==================== Assertions ====================

    private static void assertBox(Region region, double x, double y, double width, double height,
                                  String label) {
        assertClose(x, region.getLayoutX(), label + " x");
        assertClose(y, region.getLayoutY(), label + " y");
        assertClose(width, region.getWidth(), label + " width");
        assertClose(height, region.getHeight(), label + " height");
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    private static void assertOnGrid(double value, double scale, String label) {
        assertEquals(Math.round(value * scale), value * scale, 1e-6, label + " " + value + " off the grid");
    }

    // ==================== Helpers ====================

    // JavaFX hands an exception thrown inside a listener to the thread's uncaught
    // exception handler rather than to the caller, so checking only the final state
    // would miss it.
    private static void assertNoListenerException(Runnable action) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((t, error) -> reported.compareAndSet(null, error));
        try {
            action.run();
        } finally {
            thread.setUncaughtExceptionHandler(previous);
        }
        assertNull(reported.get(), () -> "a listener threw: " + reported.get());
    }

    // ==================== Entrance membership ====================

    /**
     * Verifies only a child added while the pane shows enters: one added while animation
     * is turned off around the addition, one added while the pane is out of its scene, one
     * managed for the first time and one back after missing a layout all snap in, and a
     * pane put back into its scene glides nothing on its first layout there.
     */
    @Test
    public void onlyAChildAddedWhileShownEnters() throws Exception {
        runOnFx(() -> {
            RXMasonryPane quiet = shownMasonry(2);
            Region whileOff = card(80.0, 50.0);
            quiet.setAnimated(false);
            quiet.getChildren().add(whileOff);
            quiet.setAnimated(true);
            layout(quiet, 300.0, 600.0);
            assertFalse(entering(whileOff), "added with animation turned off around it");

            RXMasonryPane away = pane(80.0, 10.0, 10.0, card(80.0, 50.0), card(80.0, 50.0));
            StackPane root = new StackPane(away);
            new Scene(root, 300.0, 600.0);
            layout(root, 300.0, 600.0);
            root.getChildren().clear();
            Region whileAway = card(80.0, 50.0);
            away.getChildren().add(whileAway);
            away.setHgap(40.0);
            root.getChildren().add(away);
            layout(root, 300.0, 600.0);
            assertFalse(entering(whileAway), "added while the pane was out of its scene");
            assertFalse(anyTranslated(away), "back in its scene, nothing glides");

            Region late = card(80.0, 50.0);
            late.setManaged(false);
            RXMasonryPane managedLate = pane(80.0, 10.0, 10.0, card(80.0, 50.0), late);
            new Scene(managedLate, 300.0, 600.0);
            layout(managedLate, 300.0, 600.0);
            late.setManaged(true);
            layout(managedLate, 300.0, 600.0);
            assertFalse(entering(late), "managed for the first time");

            RXMasonryPane missing = shownMasonry(3);
            Node second = missing.getChildren().get(1);
            second.setManaged(false);
            layout(missing, 300.0, 600.0);
            missing.setHgap(40.0);
            second.setManaged(true);
            layout(missing, 300.0, 600.0);
            assertFalse(translatedNode(second), "back after missing a layout: it snaps");
        });
    }

    /**
     * Verifies a child keeps what it was when the children list reorders it, whether
     * setAll keeps it or it is removed and added back before the next layout: one that was
     * in the list but unmanaged on the last layout snaps once managed, and one added since
     * still enters, even with animation turned off around the reorder.
     */
    @Test
    public void reorderedChildrenKeepWhetherTheyAreNew() throws Exception {
        runOnFx(() -> {
            for (boolean viaSetAll : new boolean[] {true, false}) {
                String how = viaSetAll ? "setAll" : "remove and add";
                Region unmanaged = card(80.0, 50.0);
                unmanaged.setManaged(false);
                RXMasonryPane pane = pane(80.0, 10.0, 10.0, card(80.0, 50.0), unmanaged);
                new Scene(pane, 300.0, 600.0);
                layout(pane, 300.0, 600.0);
                moveToFront(pane, unmanaged, viaSetAll);
                unmanaged.setManaged(true);
                layout(pane, 300.0, 600.0);
                assertFalse(entering(unmanaged), how + ": a child that was in the list snaps once managed");

                Region added = card(80.0, 50.0);
                pane.getChildren().add(added);
                pane.setAnimated(false);
                moveToFront(pane, added, viaSetAll);
                pane.setAnimated(true);
                layout(pane, 300.0, 600.0);
                assertNear(0.0, added.getOpacity(), how + ": a child added since still enters");
            }
        });
    }

    /**
     * Verifies a child removed while it fades in and put back before the next layout fades
     * on from the opacity it showed, and a settled child whose opacity is changed while it
     * is out keeps that opacity when put back.
     */
    @Test
    public void childPutBackGoesOnAsItStood() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = shownMasonry(3);
            Region entering = card(80.0, 50.0);
            entering.setOpacity(0.7);
            pane.getChildren().add(entering);
            layout(pane, 300.0, 600.0);
            assertNear(0.0, entering.getOpacity(), "setup: it fades in from 0");
            pane.getChildren().remove(entering);
            assertNear(0.7, entering.getOpacity(), "removed, its own opacity is back");
            pane.getChildren().add(0, entering);
            layout(pane, 300.0, 600.0);
            assertNear(0.0, entering.getOpacity(), "put back, it fades on from where it showed");

            RXMasonryPane other = shownMasonry(3);
            Node settled = other.getChildren().get(2);
            other.getChildren().remove(settled);
            settled.setOpacity(0.3);
            other.getChildren().add(2, settled);
            layout(other, 300.0, 600.0);
            assertNear(0.3, settled.getOpacity(), "a settled child keeps the opacity set while it was out");
        });
    }

    /**
     * Verifies a listener reached while a layout places the children can call off what
     * that layout collected: a child it moves to another parent in the same scene after its
     * slot was set keeps its own opacity and translate, animation turned off lets the new
     * children land at once, and a child moved away before its turn keeps the geometry it
     * was given and, put back later, enters as a new child.
     */
    @Test
    public void listenerReachedFromALayoutCanCallOffItsWork() throws Exception {
        runOnFx(() -> {
            for (boolean moveAway : new boolean[] {true, false}) {
                String what = moveAway ? "moved away" : "animation off";
                RXMasonryPane pane = pane(80.0, 10.0, 10.0, card(80.0, 50.0), card(80.0, 50.0));
                StackPane elsewhere = new StackPane();
                VBox root = new VBox(pane, elsewhere);
                new Scene(root, 300.0, 600.0);
                layout(pane, 300.0, 600.0);
                Region a = card(80.0, 50.0);
                a.setOpacity(0.8);
                a.setTranslateY(-3.0);
                Region b = card(80.0, 50.0);
                boolean[] called = {false};
                b.layoutYProperty().addListener((obs, oldValue, newValue) -> {
                    if (!called[0] && newValue.doubleValue() != 0.0) {
                        called[0] = true;
                        if (moveAway) {
                            elsewhere.getChildren().add(a);
                        } else {
                            pane.setAnimated(false);
                        }
                    }
                });
                pane.getChildren().addAll(a, b);
                layout(pane, 300.0, 600.0);
                assertTrue(called[0], what + ": setup, the listener ran during the layout");
                assertNear(0.8, a.getOpacity(), what + ": A keeps its opacity");
                assertNear(-3.0, a.getTranslateY(), what + ": A keeps its translate");
                assertNear(moveAway ? 0.0 : 1.0, b.getOpacity(), what + (moveAway ? ": B still enters" : ": B lands at once"));
            }

            RXMasonryPane pane = pane(80.0, 10.0, 10.0, card(80.0, 50.0));
            StackPane elsewhere = new StackPane();
            VBox root = new VBox(pane, elsewhere);
            new Scene(root, 300.0, 600.0);
            layout(pane, 300.0, 600.0);
            Region a = card(80.0, 50.0);
            Region b = card(80.0, 50.0);
            boolean[] called = {false};
            a.layoutXProperty().addListener((obs, oldValue, newValue) -> {
                if (!called[0] && newValue.doubleValue() != 0.0) {
                    called[0] = true;
                    elsewhere.getChildren().add(b);
                    b.resizeRelocate(321.0, 123.0, 50.0, 55.0);
                }
            });
            pane.getChildren().addAll(a, b);
            layout(pane, 300.0, 600.0);
            assertTrue(called[0], "setup: the listener ran during the layout");
            assertNear(321.0, b.getLayoutX(), "moved away before its turn: its x stands");
            assertNear(50.0, b.getWidth(), "moved away before its turn: its width stands");
            pane.getChildren().add(b);
            layout(pane, 300.0, 600.0);
            assertNear(0.0, b.getOpacity(), "put back later, it enters as a new child");
        });
    }

    /**
     * Verifies a child added while a layout runs, which that layout does not reach,
     * enters on the layout that first places it, and a pane a column count listener takes
     * out of its scene mid-layout snaps when put back.
     */
    @Test
    public void layoutListenersChangingThePaneAreHandled() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = shownMasonry(1);
            Region a = card(80.0, 50.0);
            Region late = card(80.0, 50.0);
            late.setOpacity(0.7);
            boolean[] called = {false};
            a.layoutXProperty().addListener((obs, oldValue, newValue) -> {
                if (!called[0] && newValue.doubleValue() != 0.0) {
                    called[0] = true;
                    pane.getChildren().add(late);
                }
            });
            pane.getChildren().add(a);
            layout(pane, 300.0, 600.0);
            assertTrue(called[0], "setup: the listener ran during the layout");
            layout(pane, 300.0, 600.0);
            assertNear(0.0, late.getOpacity(), "added during a layout: it enters on its first layout");

            RXMasonryPane detached = pane(80.0, 10.0, 10.0, card(80.0, 50.0), card(80.0, 50.0), card(80.0, 50.0));
            StackPane root = new StackPane(detached);
            new Scene(root, 300.0, 600.0);
            layout(root, 300.0, 600.0);
            detached.actualColumnCountProperty().addListener((obs, oldValue, newValue) -> root.getChildren().remove(detached));
            layout(detached, 200.0, 600.0);
            assertNull(detached.getScene(), "setup: the column count listener took the pane out");
            assertFalse(anyTranslated(detached), "the abandoned layout leaves no glide behind");
        });
    }

    /**
     * Verifies a child that a column count listener removes while the layout runs is not
     * kept by the pane afterwards, with no further layout. Taken out of its scene by the
     * same listener, the pane holds nothing that keeps the child from being collected.
     * Kept in its scene, the child cannot be collected yet (JavaFX keeps a parent's
     * removed children until its next render sync, which a scene that is not shown never
     * runs), so the resize record the pane keeps is checked for the child instead.
     */
    @Test
    public void childRemovedByAColumnCountListenerIsNotKept() throws Exception {
        List<WeakReference<Node>> removed = new ArrayList<>();
        List<RXMasonryPane> kept = new ArrayList<>();
        runOnFx(() -> {
            RXMasonryPane pane = columnCountRemovalPane(true);
            removed.add(new WeakReference<>(pane.getChildren().get(2)));
            layout(pane, 100.0, 600.0);
            assertEquals(2, pane.getChildren().size(), "setup: the listener removed a card");
            assertNull(pane.getScene(), "setup: and took the pane out of its scene");
            kept.add(pane);
        });
        for (int i = 0; i < 50 && removed.get(0).get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(removed.get(0).get(), "taken out of its scene: the removed card is not kept");
        assertEquals(1, kept.size(), "the pane itself is still reachable");

        runOnFx(() -> {
            RXMasonryPane pane = columnCountRemovalPane(false);
            Node card = pane.getChildren().get(2);
            layout(pane, 100.0, 600.0);
            assertEquals(2, pane.getChildren().size(), "setup: the listener removed a card");
            assertFalse(recordedStructure(pane).contains(card), "kept in its scene: the resize record does not hold it");
        });
    }

    // Three cards laid out in two columns; dropping to one column, a listener removes the
    // last card and, if asked, takes the pane out of its scene.
    private static RXMasonryPane columnCountRemovalPane(boolean detach) {
        RXMasonryPane pane = pane(80.0, 10.0, 10.0, card(80.0, 50.0), card(80.0, 50.0), card(80.0, 50.0));
        StackPane root = new StackPane(pane);
        new Scene(root, 300.0, 600.0);
        layout(pane, 200.0, 600.0);
        assertEquals(2, pane.getActualColumnCount(), "setup: two columns");
        pane.actualColumnCountProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue.intValue() == 1 && pane.getChildren().size() == 3) {
                pane.getChildren().remove(2);
                if (detach) {
                    root.getChildren().clear();
                }
            }
        });
        return pane;
    }

    // The structure the pane's resize gate last recorded, read reflectively: the test only
    // needs to see whether a node is still referenced there.
    private static List<?> recordedStructure(RXMasonryPane pane) {
        try {
            Field gateField = RXMasonryPane.class.getDeclaredField("resizeGate");
            gateField.setAccessible(true);
            Object gate = gateField.get(pane);
            Field structureField = gate.getClass().getDeclaredField("lastStructure");
            structureField.setAccessible(true);
            Object structure = structureField.get(gate);
            return structure == null ? List.of() : (List<?>) structure;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the resize record could not be read", e);
        }
    }

    /**
     * Verifies clearing many children that are still entering costs about as much as
     * clearing as many settled ones, through clearAnimated and through the children list.
     */
    @Test
    public void clearingEnteringChildrenCostsAboutAsMuchAsSettledOnes() throws Exception {
        runOnFx(() -> {
            for (boolean viaClearAnimated : new boolean[] {true, false}) {
                String how = viaClearAnimated ? "clearAnimated" : "getChildren().clear()";
                clearNanos(false, viaClearAnimated);
                clearNanos(true, viaClearAnimated);
                long settled = Math.min(clearNanos(false, viaClearAnimated), clearNanos(false, viaClearAnimated));
                long entering = Math.min(clearNanos(true, viaClearAnimated), clearNanos(true, viaClearAnimated));
                assertTrue(entering < 5 * settled + 100_000_000L,
                        how + ": entering " + entering / 1_000_000 + " ms, settled " + settled / 1_000_000 + " ms");
            }
        });
    }

    private static long clearNanos(boolean entering, boolean viaClearAnimated) {
        RXMasonryPane pane = shownMasonry(1);
        pane.setAnimated(entering);
        for (int i = 0; i < 800; i++) {
            pane.getChildren().add(card(80.0, 50.0));
        }
        layout(pane, 300.0, 600.0);
        pane.setAnimated(true);
        long start = System.nanoTime();
        if (viaClearAnimated) {
            pane.clearAnimated();
        } else {
            pane.getChildren().clear();
        }
        long elapsed = System.nanoTime() - start;
        pane.setAnimated(false);
        return elapsed;
    }

    // 80 x 50 cards in 80 px columns with 10 px gaps, three columns at 300 px, laid out once
    // in a scene.
    private static RXMasonryPane shownMasonry(int count) {
        RXMasonryPane pane = pane(80.0, 10.0, 10.0);
        for (int i = 0; i < count; i++) {
            pane.getChildren().add(card(80.0, 50.0));
        }
        new Scene(pane, 300.0, 600.0);
        layout(pane, 300.0, 600.0);
        return pane;
    }

    private static boolean translatedNode(Node node) {
        return Math.abs(node.getTranslateX()) > EPSILON || Math.abs(node.getTranslateY()) > EPSILON;
    }

    private static boolean entering(Node node) {
        return node.getOpacity() < 1.0 - 1.0e-9 || translatedNode(node);
    }

    private static void assertNear(double expected, double actual, String label) {
        assertEquals(expected, actual, 1.0e-9, label);
    }

    private static void moveToFront(Pane pane, Node node, boolean viaSetAll) {
        if (viaSetAll) {
            List<Node> order = new ArrayList<>(pane.getChildren());
            order.remove(node);
            order.add(0, node);
            pane.getChildren().setAll(order);
        } else {
            pane.getChildren().remove(node);
            pane.getChildren().add(0, node);
        }
    }

    private static boolean anyTranslated(RXMasonryPane pane) {
        return pane.getChildren().stream()
                .anyMatch(n -> Math.abs(n.getTranslateX()) > EPSILON || Math.abs(n.getTranslateY()) > EPSILON);
    }

    private static RXMasonryPane pane(double columnWidth, double hgap, double vgap, Region... cards) {
        RXMasonryPane pane = new RXMasonryPane(cards);
        pane.setColumnWidth(columnWidth);
        pane.setHgap(hgap);
        pane.setVgap(vgap);
        return pane;
    }

    private static RXMasonryPane paddedFilledPane(Region... cards) {
        RXMasonryPane pane = pane(60.0, 10.0, 6.0, cards);
        pane.setAnimated(false);
        pane.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
        return pane;
    }

    // The stage is never shown: it only supplies the render scale the pane snaps to.
    private static Stage stageFor(Region root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        return stage;
    }

    private static Region card(double prefWidth, double prefHeight) {
        FixedRegion region = new FixedRegion();
        region.setPrefSize(prefWidth, prefHeight);
        return region;
    }

    private static Label wrappingLabel() {
        Label label = new Label("A text long enough to wrap onto several lines in a narrow column of the pane");
        label.setWrapText(true);
        return label;
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }

    private static boolean hasCssProperty(String property) {
        return RXMasonryPane.getClassCssMetaData().stream()
                .anyMatch(cssMetaData -> property.equals(cssMetaData.getProperty()));
    }

    private static final class FixedRegion extends Region {
    }

    /**
     * A region that counts its width changes and its layout passes.
     */
    private static final class CountingRegion extends Region {

        private int widthChanges;
        private int layouts;

        private CountingRegion() {
            widthProperty().addListener((observable, oldWidth, newWidth) -> widthChanges++);
        }

        @Override
        protected void layoutChildren() {
            layouts++;
            super.layoutChildren();
        }
    }

    /**
     * A horizontal content-bias region that records every width its height is asked at.
     */
    private static final class WidthRecordingRegion extends Region {

        private final List<Double> askedWidths = new ArrayList<>();

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            if (width >= 0.0) {
                askedWidths.add(width);
            }
            return width <= 0.0 ? 40.0 : 6000.0 / width;
        }

        @Override
        protected double computePrefWidth(double height) {
            return 40.0;
        }
    }

    /**
     * A horizontal content-bias region whose height grows as its width shrinks,
     * capped at {@code maxWidth=100} below its preferred width.
     */
    private static final class BoundedWidthBiasedRegion extends Region {

        private BoundedWidthBiasedRegion() {
            setMinWidth(0.0);
            setPrefWidth(300.0);
            setMaxWidth(100.0);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            double resolved = width <= 0.0 ? prefWidth(-1.0) : width;
            return 20000.0 / resolved;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }
    }

    /**
     * A vertical content-bias region whose intrinsic height (width {@code -1}) is
     * 50 but reports 200 when queried with a width, exposing a width-vs-bias
     * measurement mismatch.
     */
    private static final class VerticalBiasedRegion extends Region {

        @Override
        public Orientation getContentBias() {
            return Orientation.VERTICAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            return width < 0.0 ? 50.0 : 200.0;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computePrefWidth(double height) {
            return 80.0;
        }
    }
}
