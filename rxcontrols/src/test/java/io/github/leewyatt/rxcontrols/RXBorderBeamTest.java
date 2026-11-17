package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXBorderBeamSkin;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXBorderBeam}: defaults and CSS metadata, the pass-through
 * sizing, the placement of the band and its corners, the beams and their fill,
 * the conditions under which the beams show and move, content replacement and
 * disposal.
 */
public class RXBorderBeamTest {

    private static final double EPSILON = 1.0e-6;
    private static final double CONTENT_WIDTH = 200.0;
    private static final double CONTENT_HEIGHT = 100.0;

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
        // A test hides its only window.
        Platform.setImplicitExit(false);
    }

    // ==================== Properties and CSS ====================

    /** Verifies the initial property values. */
    @Test
    public void defaults() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam();
            assertNull(beam.getContent());
            assertTrue(beam.isActive());
            assertEquals(RXBorderBeam.DEFAULT_BEAM_COLOR, beam.getBeamFill());
            assertEquals(RXBorderBeam.DEFAULT_BEAM_SIZE, beam.getBeamSize(), EPSILON);
            assertEquals(1, beam.getBeamCount());
            assertEquals(RXBorderBeam.DEFAULT_LINE_WIDTH, beam.getLineWidth(), EPSILON);
            assertEquals(0.0, beam.getOutset(), EPSILON);
            assertEquals(RXBorderBeam.DEFAULT_CYCLE_DURATION, beam.getCycleDuration());
            assertNull(beam.getCornerRadius());
            assertFalse(beam.isFocusTraversable());
            assertTrue(beam.getStyleClass().contains("rx-border-beam"));
        });
    }

    /** Verifies the styleable properties are published. */
    @Test
    public void cssMetaDataListsTheProperties() {
        Set<String> names = RXBorderBeam.getClassCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("-rx-beam-fill", "-rx-beam-size", "-rx-beam-count",
                "-rx-line-width", "-rx-outset", "-rx-cycle-duration", "-rx-corner-radius")));
    }

    /** Verifies a style sheet sets every styleable property. */
    @Test
    public void styleSheetSetsTheProperties() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            Pane root = new Pane(beam);
            root.getStylesheets().add("data:text/css,.rx-border-beam{-rx-beam-fill:red;-rx-beam-size:40;"
                    + "-rx-beam-count:3;-rx-line-width:2;-rx-outset:5;-rx-cycle-duration:2s;"
                    + "-rx-corner-radius:4 8 12 16;}");
            new Scene(root);
            root.applyCss();

            assertEquals(Color.RED, beam.getBeamFill());
            assertEquals(40.0, beam.getBeamSize(), EPSILON);
            assertEquals(3, beam.getBeamCount());
            assertEquals(2.0, beam.getLineWidth(), EPSILON);
            assertEquals(5.0, beam.getOutset(), EPSILON);
            assertEquals(Duration.seconds(2.0), beam.getCycleDuration());
            assertEquals(new CornerRadii(4.0, 8.0, 12.0, 16.0, false), beam.getCornerRadius());
        });
    }

    /** Verifies the default style sheet gives a gradient fill and a fill set in code survives it. */
    @Test
    public void themeFillYieldsToAFillSetInCode() throws Exception {
        runOnFx(() -> {
            RXBorderBeam themed = laidOut(new RXBorderBeam(content()));
            assertTrue(themed.getBeamFill() instanceof LinearGradient);

            RXBorderBeam custom = new RXBorderBeam(content());
            custom.setBeamFill(Color.RED);
            laidOut(custom);
            assertEquals(Color.RED, custom.getBeamFill());
        });
    }

    // ==================== Sizing ====================

    /** Verifies the control reports the content's sizes plus its own padding. */
    @Test
    public void sizesPassThroughFromTheContent() throws Exception {
        runOnFx(() -> {
            Region content = content();
            content.setMinSize(20.0, 10.0);
            content.setMaxSize(400.0, 300.0);
            RXBorderBeam beam = new RXBorderBeam(content);
            beam.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));
            inScene(beam);

            assertEquals(26.0, beam.minWidth(-1.0), EPSILON);
            assertEquals(14.0, beam.minHeight(-1.0), EPSILON);
            assertEquals(CONTENT_WIDTH + 6.0, beam.prefWidth(-1.0), EPSILON);
            assertEquals(CONTENT_HEIGHT + 4.0, beam.prefHeight(-1.0), EPSILON);
            assertEquals(406.0, beam.maxWidth(-1.0), EPSILON);
            assertEquals(304.0, beam.maxHeight(-1.0), EPSILON);
        });
    }

    /** Verifies a control without content is empty and shows no beam. */
    @Test
    public void withoutContentTheControlIsEmpty() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = laidOut(new RXBorderBeam());
            assertEquals(0.0, beam.prefWidth(-1.0), EPSILON);
            assertEquals(0.0, beam.prefHeight(-1.0), EPSILON);
            assertFalse(layer(beam).isVisible());
        });
    }

    /** Verifies the beam layer takes no part in layout or picking. */
    @Test
    public void beamLayerIsOutOfLayoutAndPicking() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = laidOut(new RXBorderBeam(content()));
            beam.setOutset(20.0);
            beam.layout();
            assertFalse(layer(beam).isManaged());
            assertTrue(layer(beam).isMouseTransparent());
            assertFalse(beam.isPickOnBounds());
            assertEquals(CONTENT_WIDTH, beam.prefWidth(-1.0), EPSILON);
            assertEquals(CONTENT_WIDTH, beam.getLayoutBounds().getWidth(), EPSILON);
        });
    }

    // ==================== Band Geometry ====================

    /** Verifies the band lies on the content's bounds with square corners by default. */
    @Test
    public void bandLiesOnTheContentBounds() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setPadding(new Insets(10.0));
            laidOut(beam);

            Pane layer = layer(beam);
            assertEquals(10.0, layer.getLayoutX(), EPSILON);
            assertEquals(10.0, layer.getLayoutY(), EPSILON);
            Path ring = ring(beam);
            assertEquals(FillRule.EVEN_ODD, ring.getFillRule());
            assertTrue(arcs(ring).isEmpty());
            assertEquals(CONTENT_WIDTH, ring.getLayoutBounds().getWidth(), EPSILON);
            assertEquals(CONTENT_HEIGHT, ring.getLayoutBounds().getHeight(), EPSILON);
            MoveTo inner = moves(ring).get(1);
            assertEquals(RXBorderBeam.DEFAULT_LINE_WIDTH, inner.getX(), EPSILON);
            assertEquals(RXBorderBeam.DEFAULT_LINE_WIDTH, inner.getY(), EPSILON);
        });
    }

    /** Verifies the outset moves the band outward and grows round corners with it. */
    @Test
    public void outsetMovesTheBandAndGrowsTheCorners() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setCornerRadius(new CornerRadii(8.0));
            beam.setOutset(4.0);
            beam.setLineWidth(2.0);
            laidOut(beam);

            assertEquals(-4.0, layer(beam).getLayoutX(), EPSILON);
            assertEquals(-4.0, layer(beam).getLayoutY(), EPSILON);
            Path ring = ring(beam);
            assertEquals(CONTENT_WIDTH + 8.0, ring.getLayoutBounds().getWidth(), EPSILON);
            assertEquals(12.0, arcs(ring).get(0).getRadiusX(), EPSILON);
            assertEquals(10.0, arcs(ring).get(4).getRadiusX(), EPSILON);
        });
    }

    /** Verifies the corners follow the first stroke of the content's border. */
    @Test
    public void cornersFollowTheContentBorder() throws Exception {
        runOnFx(() -> {
            Region content = content();
            content.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(3.0), null)));
            content.setBorder(new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID,
                    new CornerRadii(6.0), new BorderWidths(1.0))));
            RXBorderBeam beam = laidOut(new RXBorderBeam(content));
            assertEquals(6.0, arcs(ring(beam)).get(0).getRadiusX(), EPSILON);
        });
    }

    /** Verifies the band keeps its place and causes no layout when a control swaps its fills for a focus ring. */
    @Test
    public void bandIgnoresFillInsetsAndStateChanges() throws Exception {
        runOnFx(() -> {
            Region content = content();
            content.setBackground(new Background(
                    new BackgroundFill(Color.GRAY, new CornerRadii(3.0), Insets.EMPTY),
                    new BackgroundFill(Color.WHITE, new CornerRadii(2.0), new Insets(1.0))));
            RXBorderBeam beam = laidOut(new RXBorderBeam(content));
            assertEquals(3.0, arcs(ring(beam)).get(0).getRadiusX(), EPSILON);

            // A restyle that leaves the corners alone, as on hover, asks for no layout.
            content.setBackground(new Background(
                    new BackgroundFill(Color.DARKGRAY, new CornerRadii(3.0), Insets.EMPTY),
                    new BackgroundFill(Color.WHITESMOKE, new CornerRadii(2.0), new Insets(1.0))));
            assertFalse(beam.isNeedsLayout());

            content.setBackground(new Background(
                    new BackgroundFill(Color.BLUE, new CornerRadii(3.0), new Insets(-0.2)),
                    new BackgroundFill(Color.WHITE, new CornerRadii(2.0), new Insets(1.0)),
                    new BackgroundFill(Color.LIGHTBLUE, new CornerRadii(4.0), new Insets(-1.4))));
            beam.requestLayout();
            beam.layout();

            Path ring = ring(beam);
            assertEquals(3.0, arcs(ring).get(0).getRadiusX(), EPSILON);
            assertEquals(0.0, layer(beam).getLayoutX(), EPSILON);
            assertEquals(CONTENT_WIDTH, ring.getLayoutBounds().getWidth(), EPSILON);
        });
    }

    /** Verifies the band round a button stays put when the button shows its focus ring. */
    @Test
    public void bandRoundAButtonIgnoresItsFocusRing() throws Exception {
        runOnFx(() -> {
            Button button = new Button("Upgrade");
            RXBorderBeam beam = laidOut(new RXBorderBeam(button));
            double width = ring(beam).getLayoutBounds().getWidth();
            double radius = arcs(ring(beam)).get(0).getRadiusX();
            assertEquals(button.getWidth(), width, EPSILON);

            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            beam.applyCss();
            beam.requestLayout();
            beam.layout();

            assertEquals(0.0, layer(beam).getLayoutX(), EPSILON);
            assertEquals(width, ring(beam).getLayoutBounds().getWidth(), EPSILON);
            assertEquals(radius, arcs(ring(beam)).get(0).getRadiusX(), EPSILON);
        });
    }

    /** Verifies elliptical and percentage radii reach the ring, each axis reduced by the line width. */
    @Test
    public void ellipticalRadiiReachTheRing() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            // Horizontal radii of 20%, vertical ones of 10%, on every corner.
            beam.setCornerRadius(new CornerRadii(0.2, 0.1, 0.1, 0.2, 0.2, 0.1, 0.1, 0.2,
                    true, true, true, true, true, true, true, true));
            beam.setLineWidth(15.0);
            laidOut(beam);

            List<ArcTo> arcs = arcs(ring(beam));
            assertEquals(0.2 * CONTENT_WIDTH, arcs.get(0).getRadiusX(), EPSILON);
            assertEquals(0.1 * CONTENT_HEIGHT, arcs.get(0).getRadiusY(), EPSILON);
            // The vertical radius of 10 is used up by the line: the inner corners are square.
            assertEquals(4, arcs.size());
        });
    }

    /** Verifies content that is not a region gets a square band on its layout bounds. */
    @Test
    public void nonRegionContentGetsASquareBand() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = laidOut(new RXBorderBeam(new Rectangle(80.0, 40.0)));
            Path ring = ring(beam);
            assertTrue(arcs(ring).isEmpty());
            assertEquals(80.0, ring.getLayoutBounds().getWidth(), EPSILON);
            assertEquals(40.0, ring.getLayoutBounds().getHeight(), EPSILON);
            assertEquals(80.0, beam.prefWidth(-1.0), EPSILON);
            assertTrue(layer(beam).isVisible());
        });
    }

    /** Verifies the baseline is the content's, shifted by the top padding. */
    @Test
    public void baselinePassesThroughFromTheContent() throws Exception {
        runOnFx(() -> {
            Label content = new Label("Baseline");
            RXBorderBeam beam = new RXBorderBeam(content);
            beam.setPadding(new Insets(7.0));
            laidOut(beam);
            assertEquals(content.getBaselineOffset() + 7.0, beam.getBaselineOffset(), EPSILON);
        });
    }

    /** Verifies a change of the content's background reaches the band. */
    @Test
    public void bandFollowsAChangeOfTheContentBackground() throws Exception {
        runOnFx(() -> {
            Region content = content();
            RXBorderBeam beam = laidOut(new RXBorderBeam(content));
            assertTrue(arcs(ring(beam)).isEmpty());

            content.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(10.0), null)));
            assertTrue(beam.isNeedsLayout());
            beam.layout();
            assertEquals(10.0, arcs(ring(beam)).get(0).getRadiusX(), EPSILON);
        });
    }

    /** Verifies explicit radii replace the content's and keep the band on its bounds. */
    @Test
    public void explicitRadiiReplaceTheContentRadii() throws Exception {
        runOnFx(() -> {
            Region content = content();
            content.setBackground(new Background(
                    new BackgroundFill(Color.WHITE, new CornerRadii(7.0), new Insets(3.0))));
            RXBorderBeam beam = new RXBorderBeam(content);
            beam.setCornerRadius(new CornerRadii(999.0));
            laidOut(beam);

            assertEquals(0.0, layer(beam).getLayoutX(), EPSILON);
            assertEquals(CONTENT_HEIGHT / 2.0, arcs(ring(beam)).get(0).getRadiusX(), EPSILON);
        });
    }

    /** Verifies invalid sizes fall back to the defaults instead of breaking the geometry. */
    @Test
    public void invalidSizesFallBackToTheDefaults() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setLineWidth(Double.NaN);
            beam.setBeamSize(-5.0);
            beam.setOutset(Double.POSITIVE_INFINITY);
            beam.setCycleDuration(Duration.millis(Double.MIN_VALUE));
            laidOut(beam);

            assertEquals(RXBorderBeam.DEFAULT_LINE_WIDTH, moves(ring(beam)).get(1).getX(), EPSILON);
            assertEquals(RXBorderBeam.DEFAULT_BEAM_SIZE, beams(beam).get(0).getWidth(), EPSILON);
            assertEquals(0.0, layer(beam).getLayoutX(), EPSILON);
            assertTrue(Double.isFinite(beams(beam).get(0).getTranslateX()));
        });
    }

    /** Verifies an outset that swallows the box hides the beams. */
    @Test
    public void bandWithoutAreaHidesTheBeams() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setOutset(-CONTENT_HEIGHT);
            laidOut(beam);
            assertFalse(layer(beam).isVisible());

            beam.setOutset(0.0);
            beam.layout();
            assertTrue(layer(beam).isVisible());
        });
    }

    // ==================== Beams ====================

    /** Verifies a beam starts at the head of the track, pointing along the top edge. */
    @Test
    public void beamStartsOnTheTopEdge() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setBeamSize(40.0);
            laidOut(beam);

            Rectangle node = beams(beam).get(0);
            assertEquals(40.0, node.getWidth(), EPSILON);
            assertEquals(40.0, node.getHeight(), EPSILON);
            Rotate rotate = (Rotate) node.getTransforms().get(0);
            assertEquals(0.0, rotate.getAngle(), EPSILON);
            // The point 90% along and halfway across the square sits on the track.
            assertEquals(40.0, node.getTranslateX() + rotate.getPivotX(), EPSILON);
            assertEquals(0.0, node.getTranslateY() + rotate.getPivotY(), EPSILON);
            assertEquals(36.0, rotate.getPivotX(), EPSILON);
            assertEquals(20.0, rotate.getPivotY(), EPSILON);
        });
    }

    /** Verifies several beams are spread evenly from the first frame. */
    @Test
    public void beamsAreSpreadEvenly() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setBeamSize(20.0);
            beam.setBeamCount(2);
            laidOut(beam);

            List<Rectangle> nodes = beams(beam);
            assertEquals(2, nodes.size());
            Rotate first = (Rotate) nodes.get(0).getTransforms().get(0);
            Rotate second = (Rotate) nodes.get(1).getTransforms().get(0);
            assertEquals(0.0, first.getAngle(), EPSILON);
            assertEquals(180.0, second.getAngle(), EPSILON);
            // Half a lap away: the mirrored point on the bottom edge.
            assertEquals(CONTENT_WIDTH - 20.0, nodes.get(1).getTranslateX() + second.getPivotX(), EPSILON);
            assertEquals(CONTENT_HEIGHT, nodes.get(1).getTranslateY() + second.getPivotY(), EPSILON);

            beam.setBeamCount(0);
            assertTrue(beams(beam).isEmpty());
            assertFalse(layer(beam).isVisible());
            beam.setBeamCount(-3);
            assertTrue(beams(beam).isEmpty());
        });
    }

    /** Verifies a color fills the leading 70% and fades out over the rest. */
    @Test
    public void colorFillFadesOutAtTheTail() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setBeamFill(Color.RED);
            laidOut(beam);

            LinearGradient paint = (LinearGradient) beams(beam).get(0).getFill();
            // The gradient runs from the right, leading, edge to the left.
            assertEquals(1.0, paint.getStartX(), EPSILON);
            assertEquals(0.0, paint.getEndX(), EPSILON);
            List<Stop> stops = paint.getStops();
            assertEquals(Color.RED, stops.get(0).getColor());
            Stop last = stops.get(stops.size() - 1);
            assertEquals(1.0, last.getOffset(), EPSILON);
            assertEquals(new Color(1.0, 0.0, 0.0, 0.0), last.getColor());
            Stop beforeLast = stops.get(stops.size() - 2);
            assertEquals(0.7, beforeLast.getOffset(), EPSILON);
            assertEquals(Color.RED, beforeLast.getColor());
        });
    }

    /** Verifies gradient stops are fitted into the leading 70%, and other paints use the default color. */
    @Test
    public void gradientStopsAreFittedBeforeTheTail() throws Exception {
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setBeamFill(new LinearGradient(0.0, 0.0, 0.0, 1.0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.RED), new Stop(0.5, Color.LIME), new Stop(1.0, Color.BLUE)));
            laidOut(beam);

            List<Stop> stops = ((LinearGradient) beams(beam).get(0).getFill()).getStops();
            assertEquals(4, stops.size());
            assertEquals(0.35, stops.get(1).getOffset(), EPSILON);
            assertEquals(Color.LIME, stops.get(1).getColor());
            assertEquals(0.7, stops.get(2).getOffset(), EPSILON);
            assertEquals(new Color(0.0, 0.0, 1.0, 0.0), stops.get(3).getColor());

            beam.setBeamFill(null);
            stops = ((LinearGradient) beams(beam).get(0).getFill()).getStops();
            assertEquals(RXBorderBeam.DEFAULT_BEAM_COLOR, stops.get(0).getColor());
        });
    }

    // ==================== Running ====================

    /** Verifies the beams stay at the start outside a showing window. */
    @Test
    public void beamsDoNotMoveOutsideAShowingWindow() throws Exception {
        AtomicReference<RXBorderBeam> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setCycleDuration(Duration.millis(400.0));
            ref.set(laidOut(beam));
            assertTrue(layer(beam).isVisible());
        });
        Thread.sleep(300);
        runOnFx(() -> assertEquals(0.0, angle(ref.get()), EPSILON));
    }

    /** Verifies the beams move in a showing window, and pause in place when inactive or hidden. */
    @Test
    public void beamsMoveWhileActiveAndShowing() throws Exception {
        AtomicReference<RXBorderBeam> ref = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        double[] seen = new double[4];
        runOnFx(() -> {
            RXBorderBeam beam = new RXBorderBeam(content());
            beam.setBeamSize(30.0);
            // Long enough that the beam cannot come back to where it was.
            beam.setCycleDuration(Duration.seconds(20.0));
            ref.set(beam);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(beam), 300.0, 200.0));
            stage.show();
            stageRef.set(stage);
        });
        try {
            Thread.sleep(500);
            runOnFx(() -> {
                seen[0] = position(ref.get());
                // At rest the beam sits at the track's start: its radius of 30, less the anchor at 27.
                assertTrue(seen[0] > 3.0 + EPSILON);
                ref.get().setActive(false);
                assertFalse(layer(ref.get()).isVisible());
                seen[1] = position(ref.get());
            });
            Thread.sleep(300);
            runOnFx(() -> {
                assertEquals(seen[1], position(ref.get()), EPSILON);
                ref.get().setActive(true);
                assertTrue(layer(ref.get()).isVisible());
            });
            Thread.sleep(300);
            runOnFx(() -> {
                assertTrue(position(ref.get()) > seen[1]);
                ref.get().setVisible(false);
                seen[2] = position(ref.get());
            });
            Thread.sleep(300);
            runOnFx(() -> {
                assertEquals(seen[2], position(ref.get()), EPSILON);
                ref.get().setVisible(true);
            });
            Thread.sleep(300);
            runOnFx(() -> {
                assertTrue(position(ref.get()) > seen[2]);
                // A new duration keeps the beams where they are.
                double before = position(ref.get());
                ref.get().setCycleDuration(Duration.seconds(40.0));
                assertEquals(before, position(ref.get()), EPSILON);
                stageRef.get().hide();
                seen[3] = position(ref.get());
            });
            Thread.sleep(300);
            runOnFx(() -> assertEquals(seen[3], position(ref.get()), EPSILON));
        } finally {
            runOnFx(() -> stageRef.get().hide());
        }
    }

    // ==================== Content and Skin ====================

    /** Verifies replacing the content moves the band to the new content and drops the old one. */
    @Test
    public void contentCanBeReplaced() throws Exception {
        runOnFx(() -> {
            Region first = content();
            RXBorderBeam beam = laidOut(new RXBorderBeam(first));
            Region second = new Region();
            second.setPrefSize(50.0, 40.0);
            beam.setContent(second);
            beam.autosize();
            beam.layout();

            assertNull(first.getParent());
            assertEquals(50.0, ring(beam).getLayoutBounds().getWidth(), EPSILON);

            first.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(10.0), null)));
            assertFalse(beam.isNeedsLayout());

            beam.setContent(null);
            beam.layout();
            assertFalse(layer(beam).isVisible());
            assertNull(second.getParent());
        });
    }

    /** Verifies the content keeps its own clip, effect, transforms and mouse handling. */
    @Test
    public void contentIsLeftUntouched() throws Exception {
        runOnFx(() -> {
            Label content = new Label("Content");
            Rectangle clip = new Rectangle(10.0, 10.0);
            content.setClip(clip);
            content.setRotate(15.0);
            RXBorderBeam beam = laidOut(new RXBorderBeam(content));

            assertSame(clip, content.getClip());
            assertEquals(15.0, content.getRotate(), EPSILON);
            assertEquals(0.0, content.getTranslateX(), EPSILON);
            assertNull(content.getEffect());
            assertEquals(1.0, content.getOpacity(), EPSILON);
            assertFalse(content.isMouseTransparent());
            assertNull(ring(beam).getParent());
        });
    }

    /** Verifies a disposed skin removes its nodes and stops following the content. */
    @Test
    public void disposeReleasesTheContent() throws Exception {
        runOnFx(() -> {
            Region content = content();
            RXBorderBeam beam = laidOut(new RXBorderBeam(content));
            RXBorderBeamSkin old = (RXBorderBeamSkin) beam.getSkin();
            // A subclass: a skin of the same class as the current one is ignored.
            beam.setSkin(new RXBorderBeamSkin(beam) {
            });
            beam.layout();

            assertEquals(1, beam.lookupAll(".beams").size());
            assertEquals(1, beam.lookupAll(".content-slot").size());
            assertSame(beam.lookup(".content-slot"), content.getParent());
            assertNull(old.getSkinnable());

            beam.setSkin(null);
            assertNull(content.getParent());
            beam.layout();
            content.setBackground(new Background(new BackgroundFill(Color.WHITE, new CornerRadii(10.0), null)));
            assertFalse(beam.isNeedsLayout());
        });
    }

    // ==================== Helpers ====================

    private static Region content() {
        Region region = new Region();
        region.setPrefSize(CONTENT_WIDTH, CONTENT_HEIGHT);
        return region;
    }

    private static void inScene(RXBorderBeam beam) {
        new Scene(new Pane(beam));
        beam.applyCss();
    }

    private static RXBorderBeam laidOut(RXBorderBeam beam) {
        inScene(beam);
        beam.autosize();
        beam.layout();
        return beam;
    }

    private static Pane layer(RXBorderBeam beam) {
        return (Pane) beam.lookup(".beams");
    }

    private static Path ring(RXBorderBeam beam) {
        return (Path) layer(beam).getClip();
    }

    private static List<Rectangle> beams(RXBorderBeam beam) {
        List<Rectangle> nodes = new ArrayList<>();
        for (Node node : layer(beam).getChildren()) {
            nodes.add((Rectangle) node);
        }
        return nodes;
    }

    private static double angle(RXBorderBeam beam) {
        return ((Rotate) beams(beam).get(0).getTransforms().get(0)).getAngle();
    }

    // Distance of the first beam along the top edge; valid while it is still there.
    private static double position(RXBorderBeam beam) {
        return beams(beam).get(0).getTranslateX();
    }

    private static List<ArcTo> arcs(Path path) {
        List<ArcTo> arcs = new ArrayList<>();
        for (PathElement element : path.getElements()) {
            if (element instanceof ArcTo) {
                arcs.add((ArcTo) element);
            }
        }
        return arcs;
    }

    private static List<MoveTo> moves(Path path) {
        List<MoveTo> moves = new ArrayList<>();
        for (PathElement element : path.getElements()) {
            if (element instanceof MoveTo) {
                moves.add((MoveTo) element);
            }
        }
        return moves;
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
