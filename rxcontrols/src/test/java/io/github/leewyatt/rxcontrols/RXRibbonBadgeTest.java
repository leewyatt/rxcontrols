package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXRibbonBadge.Placement;
import io.github.leewyatt.rxcontrols.skins.RXRibbonBadgeSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
 * Tests {@link RXRibbonBadge}: defaults, the placement pseudo-classes and CSS
 * metadata, the geometry of the ribbon and its fold, the pass-through sizing,
 * content replacement, mouse transparency and disposal.
 */
public class RXRibbonBadgeTest {

    private static final double EPSILON = 1.0e-6;
    private static final double CONTENT_WIDTH = 200.0;
    private static final double CONTENT_HEIGHT = 120.0;
    private static final PseudoClass START = PseudoClass.getPseudoClass("start");
    private static final PseudoClass END = PseudoClass.getPseudoClass("end");

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

    // ==================== Control ====================

    /** Verifies the defaults: no content or text, end placement, 8px offset and overhang. */
    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = new RXRibbonBadge();
            assertNull(badge.getContent());
            assertNull(badge.getText());
            assertEquals(Placement.END, badge.getPlacement());
            assertEquals(8.0, badge.getRibbonOffset(), EPSILON);
            assertEquals(8.0, badge.getOverhang(), EPSILON);
            assertFalse(badge.isFocusTraversable());
            assertFalse(badge.isPickOnBounds());

            Region content = content();
            RXRibbonBadge full = new RXRibbonBadge(content, "NEW");
            assertSame(content, full.getContent());
            assertEquals("NEW", full.getText());
        });
    }

    /** Verifies exactly one of :start and :end is set, with null placement counting as end. */
    @Test
    public void placementPseudoClassesFollowThePlacement() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = new RXRibbonBadge();
            assertPlacementState(badge, false);
            badge.setPlacement(Placement.START);
            assertPlacementState(badge, true);
            badge.setPlacement(null);
            assertPlacementState(badge, false);

            SimpleObjectProperty<Placement> source = new SimpleObjectProperty<>(Placement.START);
            badge.placementProperty().bind(source);
            assertPlacementState(badge, true);
            source.set(Placement.END);
            assertPlacementState(badge, false);
        });
    }

    /** Verifies the square corner of the ribbon moves to the start side with the placement. */
    @Test
    public void squareCornerFollowsThePlacement() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(content(), "NEW"));
            CornerRadii end = ribbon(badge).getBackground().getFills().get(0).getRadii();
            assertEquals(0.0, end.getBottomRightHorizontalRadius(), EPSILON);
            assertTrue(end.getBottomLeftHorizontalRadius() > 0.0);
            Object endFold = fold(badge).getShape();

            badge.setPlacement(Placement.START);
            badge.applyCss();
            CornerRadii start = ribbon(badge).getBackground().getFills().get(0).getRadii();
            assertEquals(0.0, start.getBottomLeftHorizontalRadius(), EPSILON);
            assertTrue(start.getBottomRightHorizontalRadius() > 0.0);
            assertTrue(fold(badge).getShape() != endFold, "the fold takes the mirrored shape");
        });
    }

    /** Verifies a stylesheet sets placement, ribbon offset and overhang. */
    @Test
    public void stylesheetSetsTheStyleableProperties() throws Exception {
        runOnFx(() -> {
            Set<String> names = RXRibbonBadge.getClassCssMetaData().stream()
                    .map(CssMetaData::getProperty)
                    .collect(Collectors.toSet());
            assertTrue(names.containsAll(Set.of("-rx-placement", "-rx-ribbon-offset", "-rx-overhang")),
                    names.toString());

            RXRibbonBadge badge = new RXRibbonBadge(content(), "NEW");
            StackPane root = new StackPane(badge);
            root.getStylesheets().add("data:text/css,.rx-ribbon-badge{-rx-placement:start;"
                    + "-rx-ribbon-offset:20;-rx-overhang:4;}");
            new Scene(root);
            root.applyCss();
            assertEquals(Placement.START, badge.getPlacement());
            assertPlacementState(badge, true);
            assertEquals(20.0, badge.getRibbonOffset(), EPSILON);
            assertEquals(4.0, badge.getOverhang(), EPSILON);
        });
    }

    // ==================== Geometry ====================

    /** Verifies the end ribbon reaches past the end side, below the top by the offset, with the fold under the reach. */
    @Test
    public void endRibbonReachesPastTheEndSide() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(content(), "NEW"));
            Region ribbon = ribbon(badge);
            Region fold = fold(badge);
            assertTrue(ribbon.isVisible());
            assertEquals(CONTENT_WIDTH + 8.0, ribbon.getLayoutX() + ribbon.getWidth(), EPSILON);
            assertEquals(8.0, ribbon.getLayoutY(), EPSILON);
            assertTrue(ribbon.getWidth() < CONTENT_WIDTH);

            assertTrue(fold.isVisible());
            assertEquals(CONTENT_WIDTH, fold.getLayoutX(), EPSILON);
            assertEquals(ribbon.getLayoutY() + ribbon.getHeight(), fold.getLayoutY(), EPSILON);
            assertEquals(8.0, fold.getWidth(), EPSILON);
            assertEquals(6.0, fold.getHeight(), EPSILON);
        });
    }

    /** Verifies the start ribbon mirrors the end one. */
    @Test
    public void startRibbonReachesPastTheStartSide() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = new RXRibbonBadge(content(), "NEW");
            badge.setPlacement(Placement.START);
            laidOut(badge);
            Region ribbon = ribbon(badge);
            Region fold = fold(badge);
            assertEquals(-8.0, ribbon.getLayoutX(), EPSILON);
            assertEquals(-8.0, fold.getLayoutX(), EPSILON);
            assertEquals(ribbon.getLayoutY() + ribbon.getHeight(), fold.getLayoutY(), EPSILON);
        });
    }

    /** Verifies offset and overhang move the ribbon, and zero, negative or non-finite overhang drops the fold. */
    @Test
    public void offsetAndOverhangShapeTheRibbon() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(content(), "NEW"));
            Region ribbon = ribbon(badge);
            Region fold = fold(badge);
            badge.setRibbonOffset(30.0);
            badge.setOverhang(12.0);
            badge.layout();
            assertEquals(30.0, ribbon.getLayoutY(), EPSILON);
            assertEquals(CONTENT_WIDTH + 12.0, ribbon.getLayoutX() + ribbon.getWidth(), EPSILON);
            assertEquals(12.0, fold.getWidth(), EPSILON);
            assertEquals(9.0, fold.getHeight(), EPSILON);

            for (double overhang : new double[]{0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY}) {
                badge.setOverhang(overhang);
                badge.layout();
                assertFalse(fold.isVisible(), "fold with overhang " + overhang);
                assertEquals(CONTENT_WIDTH, ribbon.getLayoutX() + ribbon.getWidth(), EPSILON);
            }
            badge.setRibbonOffset(Double.NaN);
            badge.layout();
            assertEquals(0.0, ribbon.getLayoutY(), EPSILON);
        });
    }

    /** Verifies a ribbon longer than the content stops at the far side of the content. */
    @Test
    public void longRibbonIsLimitedToTheContent() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(content(),
                    "a ribbon text that is far too long to fit on a card of this width, by a wide margin"));
            Region ribbon = ribbon(badge);
            assertEquals(CONTENT_WIDTH + 8.0, ribbon.getWidth(), EPSILON);
            assertEquals(0.0, ribbon.getLayoutX(), EPSILON);
        });
    }

    /** Verifies a fractional overhang is rounded to whole pixels, so the fold ends on the content's side. */
    @Test
    public void fractionalOverhangIsSnapped() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = new RXRibbonBadge(content(), "NEW");
            badge.setPlacement(Placement.START);
            badge.setOverhang(7.5);
            laidOut(badge);
            Region fold = fold(badge);
            assertEquals(-8.0, ribbon(badge).getLayoutX(), EPSILON);
            assertEquals(0.0, fold.getLayoutX() + fold.getWidth(), EPSILON);
        });
    }

    /** Verifies a text without content shows no ribbon. */
    @Test
    public void ribbonNeedsContent() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(null, "NEW"));
            assertFalse(ribbon(badge).isVisible());
            assertFalse(fold(badge).isVisible());
            badge.setContent(content());
            badge.layout();
            assertTrue(ribbon(badge).isVisible());
            assertTrue(fold(badge).isVisible());
        });
    }

    /** Verifies an empty or null text hides the ribbon and its fold, and a text brings them back. */
    @Test
    public void emptyTextHidesTheRibbon() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(content()));
            Region ribbon = ribbon(badge);
            Region fold = fold(badge);
            assertFalse(ribbon.isVisible());
            assertFalse(fold.isVisible());

            badge.setText("NEW");
            badge.layout();
            assertTrue(ribbon.isVisible());
            assertTrue(fold.isVisible());
            assertEquals("NEW", ((Label) ribbon).getText());

            badge.setText("");
            badge.layout();
            assertFalse(ribbon.isVisible());
            assertFalse(fold.isVisible());
        });
    }

    /** Verifies the ribbon follows the content, not the outer edge, of a padded or stretched control. */
    @Test
    public void ribbonFollowsTheContentInsideALargerControl() throws Exception {
        runOnFx(() -> {
            RXRibbonBadge badge = new RXRibbonBadge(content(), "NEW");
            badge.setPadding(new Insets(10.0, 20.0, 10.0, 20.0));
            laidOut(badge);
            Region ribbon = ribbon(badge);
            assertEquals(20.0 + CONTENT_WIDTH + 8.0, ribbon.getLayoutX() + ribbon.getWidth(), EPSILON);
            assertEquals(10.0 + 8.0, ribbon.getLayoutY(), EPSILON);

            // The fixed-size content is centered in the stretched control; a
            // single layout pass must already move the ribbon with it.
            badge.resize(400.0, 300.0);
            badge.layout();
            assertEquals(200.0 + CONTENT_WIDTH / 2.0 + 8.0, ribbon.getLayoutX() + ribbon.getWidth(), EPSILON);
            assertEquals(150.0 - CONTENT_HEIGHT / 2.0 + 8.0, ribbon.getLayoutY(), EPSILON);
        });
    }

    // ==================== Sizing ====================

    /** Verifies the control reports the content's sizes and bias plus its padding, ribbon excluded. */
    @Test
    public void sizeIsThatOfTheContentPlusPadding() throws Exception {
        runOnFx(() -> {
            Region content = new Region();
            content.setMinSize(20.0, 10.0);
            content.setPrefSize(CONTENT_WIDTH, CONTENT_HEIGHT);
            content.setMaxSize(300.0, 250.0);
            RXRibbonBadge badge = new RXRibbonBadge(content, "NEW");
            inScene(badge);
            assertSize(badge, 20.0, 10.0, CONTENT_WIDTH, CONTENT_HEIGHT, 300.0, 250.0);
            badge.setPadding(new Insets(4.0, 6.0, 8.0, 10.0));
            assertSize(badge, 36.0, 22.0, CONTENT_WIDTH + 16.0, CONTENT_HEIGHT + 12.0, 316.0, 262.0);

            Label wrapped = new Label("a text long enough to wrap over several lines when narrow");
            wrapped.setWrapText(true);
            badge.setContent(wrapped);
            assertEquals(Orientation.HORIZONTAL, badge.getContentBias());
            badge.setContent(null);
            assertNull(badge.getContentBias());
            badge.setPadding(Insets.EMPTY);
            assertEquals(0.0, badge.prefWidth(-1.0), EPSILON);
            assertEquals(0.0, badge.prefHeight(-1.0), EPSILON);
        });
    }

    // ==================== Content and lifecycle ====================

    /** Verifies replacing and clearing the content keeps a single owner, and removing the skin releases it. */
    @Test
    public void contentIsReplacedClearedAndReleased() throws Exception {
        runOnFx(() -> {
            Region first = content();
            Region second = content();
            RXRibbonBadge badge = laidOut(new RXRibbonBadge(first, "NEW"));
            Pane slot = (Pane) badge.lookup(".content-slot");
            assertSame(slot, first.getParent());

            badge.setContent(second);
            assertNull(first.getParent());
            assertSame(slot, second.getParent());

            badge.setSkin(null);
            assertNull(second.getParent());
            assertTrue(badge.getChildrenUnmodifiable().isEmpty());

            badge.setSkin(new RXRibbonBadgeSkin(badge));
            badge.applyCss();
            badge.layout();
            assertSame(badge.lookup(".content-slot"), second.getParent());
            assertEquals(CONTENT_WIDTH + 8.0, ribbon(badge).getLayoutX() + ribbon(badge).getWidth(), EPSILON);

            badge.setContent(null);
            assertNull(second.getParent());
        });
    }

    // ==================== Mouse ====================

    /** Verifies the ribbon and fold ignore the mouse, only the content is picked, and its events bubble up. */
    @Test
    public void ribbonIsTransparentToTheMouse() throws Exception {
        runOnFx(() -> {
            Region target = content();
            RXRibbonBadge badge = new RXRibbonBadge(target, "NEW");
            badge.setPadding(new Insets(10.0));
            StackPane root = new StackPane(badge);
            new Scene(root);
            root.applyCss();
            root.layout();
            assertTrue(ribbon(badge).isMouseTransparent());
            assertTrue(fold(badge).isMouseTransparent());
            assertFalse(badge.lookup(".content-slot").isPickOnBounds());
            // In the padding, and on the part of the ribbon past the content.
            assertFalse(badge.contains(2.0, 60.0));
            assertFalse(badge.contains(10.0 + CONTENT_WIDTH + 4.0, 10.0 + 12.0));

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

    private static Region ribbon(RXRibbonBadge badge) {
        return (Region) badge.lookup(".ribbon");
    }

    private static Region fold(RXRibbonBadge badge) {
        return (Region) badge.lookup(".fold");
    }

    private static void inScene(RXRibbonBadge badge) {
        new Scene(new Pane(badge));
        badge.applyCss();
    }

    private static RXRibbonBadge laidOut(RXRibbonBadge badge) {
        inScene(badge);
        badge.autosize();
        badge.layout();
        return badge;
    }

    private static void assertPlacementState(RXRibbonBadge badge, boolean start) {
        assertEquals(start, badge.getPseudoClassStates().contains(START));
        assertEquals(!start, badge.getPseudoClassStates().contains(END));
    }

    private static void assertSize(RXRibbonBadge badge, double minW, double minH, double prefW, double prefH,
                                   double maxW, double maxH) {
        assertEquals(minW, badge.minWidth(-1.0), EPSILON, "min width");
        assertEquals(minH, badge.minHeight(-1.0), EPSILON, "min height");
        assertEquals(prefW, badge.prefWidth(-1.0), EPSILON, "pref width");
        assertEquals(prefH, badge.prefHeight(-1.0), EPSILON, "pref height");
        assertEquals(maxW, badge.maxWidth(-1.0), EPSILON, "max width");
        assertEquals(maxH, badge.maxHeight(-1.0), EPSILON, "max height");
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
