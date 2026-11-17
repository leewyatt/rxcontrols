package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.internal.BorderBeamGeometry;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Default skin for {@link RXBorderBeam}.
 *
 * <p>The content sits in a {@code content-slot} pane that fills the control.
 * Over it lies the {@code beams} layer, clipped to a ring on the border of the
 * content; each {@code beam} in it is a gradient square that one timeline
 * moves along a rounded track. The ring is rebuilt only when its geometry
 * changes; a frame of the animation moves and turns the squares and nothing
 * else.</p>
 */
public class RXBorderBeamSkin extends RXSkinBase<RXBorderBeam> {

    // The share of a beam, from its leading edge, that carries its colors; the
    // rest fades out.
    private static final double COLOR_SHARE = 0.7;
    // The point of a beam that rides on the track, as shares of its side: close
    // to the leading edge and centered across.
    private static final double ANCHOR_X = 0.9;
    private static final double ANCHOR_Y = 0.5;

    private final ContentSlot contentSlot;
    private final Pane beamLayer;
    private final Path ring;
    private final List<Beam> beams = new ArrayList<>();

    private final Timeline timeline;
    private final DoubleProperty phase = new SimpleDoubleProperty();
    private final ReadOnlyBooleanProperty treeShowing;

    private final InvalidationListener contentLookListener = observable -> getSkinnable().requestLayout();
    private Region observedContent;

    private double boxWidth;
    private double boxHeight;
    private double trackRadius;
    private final double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
    private final double[] ringRadii = new double[BorderBeamGeometry.RADII_LENGTH];
    private double ringWidth = -1.0;
    private double ringHeight = -1.0;
    private double ringLineWidth = -1.0;
    private final double[] point = new double[3];

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXBorderBeamSkin(RXBorderBeam control) {
        super(control);
        // A wrapper: mouse events on the content still reach the control's ancestors.
        consumeMouseEvents(false);

        contentSlot = new ContentSlot();

        ring = new Path();
        ring.setFillRule(FillRule.EVEN_ODD);
        ring.setFill(Color.BLACK);
        ring.setStroke(null);

        beamLayer = new Pane();
        beamLayer.getStyleClass().add("beams");
        beamLayer.setManaged(false);
        beamLayer.setMouseTransparent(true);
        beamLayer.setVisible(false);
        beamLayer.setClip(ring);

        getChildren().addAll(contentSlot, beamLayer);

        // One second per lap; the rate stretches it to the cycle duration, so a
        // new duration takes effect without moving the beams.
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(phase, 0.0)),
                new KeyFrame(Duration.seconds(1.0), new KeyValue(phase, 1.0, Interpolator.LINEAR)));
        timeline.setCycleCount(Animation.INDEFINITE);
        disposer.registerDisposeTask(timeline::stop);

        treeShowing = controlTreeShowingProperty();

        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.activeProperty(), this::updateRunning);
        disposer.registerListener(control.beamFillProperty(), this::updateBeamFill);
        disposer.registerListener(control.beamSizeProperty(), this::updateBeamSize);
        disposer.registerListener(control.beamCountProperty(), this::updateBeams);
        disposer.registerListener(control.cycleDurationProperty(), this::updateRate);
        disposer.registerListener(treeShowing, this::updateRunning);
        disposer.registerListener(phase, this::placeBeams);

        updateRate();
        updateBeams();
        updateContent();
    }

    // ==================== State Changes ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        contentSlot.setContent(content);
        observeContentLook(content instanceof Region ? (Region) content : null);
        getSkinnable().requestLayout();
    }

    // The corners follow the content's border and background.
    private void observeContentLook(Region region) {
        if (observedContent == region) {
            return;
        }
        if (observedContent != null) {
            observedContent.backgroundProperty().removeListener(contentLookListener);
            observedContent.borderProperty().removeListener(contentLookListener);
        }
        observedContent = region;
        if (region != null) {
            region.backgroundProperty().addListener(contentLookListener);
            region.borderProperty().addListener(contentLookListener);
        }
    }

    private void updateBeams() {
        int count = Math.max(0, getSkinnable().getBeamCount());
        if (count != beams.size()) {
            beams.clear();
            List<Node> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Beam beam = new Beam();
                beams.add(beam);
                nodes.add(beam.node);
            }
            beamLayer.getChildren().setAll(nodes);
            updateBeamFill();
            updateBeamSize();
        }
        updateRunning();
    }

    private void updateBeamFill() {
        Paint fill = beamPaint(getSkinnable().getBeamFill());
        for (Beam beam : beams) {
            beam.node.setFill(fill);
        }
    }

    private void updateBeamSize() {
        double size = beamSizeOrDefault();
        for (Beam beam : beams) {
            beam.node.setWidth(size);
            beam.node.setHeight(size);
            beam.rotate.setPivotX(ANCHOR_X * size);
            beam.rotate.setPivotY(ANCHOR_Y * size);
        }
        trackRadius = BorderBeamGeometry.trackRadius(boxWidth, boxHeight, size);
        placeBeams();
    }

    private void updateRate() {
        timeline.setRate(1.0 / cycleDurationOrDefault().toSeconds());
    }

    private void updateRunning() {
        // Read first: the property reports its next change only once it has been read.
        boolean inShowingTree = treeShowing.get();
        boolean showable = getSkinnable().isActive() && boxWidth > 0.0 && boxHeight > 0.0 && !beams.isEmpty();
        beamLayer.setVisible(showable);
        if (showable && inShowingTree) {
            timeline.play();
        } else {
            // Paused, not stopped: the beams continue from where they are.
            timeline.pause();
        }
    }

    // ==================== Beam ====================

    private void placeBeams() {
        if (boxWidth <= 0.0 || boxHeight <= 0.0) {
            return;
        }
        double size = beamSizeOrDefault();
        double anchorX = ANCHOR_X * size;
        double anchorY = ANCHOR_Y * size;
        double start = phase.get();
        int count = beams.size();
        for (int i = 0; i < count; i++) {
            Beam beam = beams.get(i);
            BorderBeamGeometry.pointOnTrack(boxWidth, boxHeight, trackRadius, start + (double) i / count, point);
            beam.node.setTranslateX(point[0] - anchorX);
            beam.node.setTranslateY(point[1] - anchorY);
            beam.rotate.setAngle(point[2]);
        }
    }

    // Offset 0 is the leading edge, which is the right side of the square.
    private static Paint beamPaint(Paint fill) {
        List<Stop> colors;
        if (fill instanceof LinearGradient) {
            colors = ((LinearGradient) fill).getStops();
        } else if (fill instanceof RadialGradient) {
            colors = ((RadialGradient) fill).getStops();
        } else {
            Color color = fill instanceof Color ? (Color) fill : RXBorderBeam.DEFAULT_BEAM_COLOR;
            colors = List.of(new Stop(0.0, color), new Stop(1.0, color));
        }
        List<Stop> stops = new ArrayList<>(colors.size() + 1);
        for (Stop stop : colors) {
            stops.add(new Stop(stop.getOffset() * COLOR_SHARE, stop.getColor()));
        }
        // Fading to the last color at zero opacity, not to transparent black,
        // keeps the hue of the tail.
        Color last = colors.get(colors.size() - 1).getColor();
        stops.add(new Stop(1.0, new Color(last.getRed(), last.getGreen(), last.getBlue(), 0.0)));
        return new LinearGradient(1.0, 0.0, 0.0, 0.0, true, CycleMethod.NO_CYCLE, stops);
    }

    private double beamSizeOrDefault() {
        double size = getSkinnable().getBeamSize();
        return Double.isFinite(size) && size > 0.0 ? size : RXBorderBeam.DEFAULT_BEAM_SIZE;
    }

    private double lineWidthOrDefault() {
        double width = getSkinnable().getLineWidth();
        return Double.isFinite(width) && width > 0.0 ? width : RXBorderBeam.DEFAULT_LINE_WIDTH;
    }

    private Duration cycleDurationOrDefault() {
        Duration duration = getSkinnable().getCycleDuration();
        boolean usable = duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
        return usable ? duration : RXBorderBeam.DEFAULT_CYCLE_DURATION;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        contentSlot.layoutContent(x, y, w, h);
        updateGeometry();
    }

    private void updateGeometry() {
        RXBorderBeam control = getSkinnable();
        Node content = control.getContent();
        if (content == null) {
            boxWidth = 0.0;
            boxHeight = 0.0;
            updateRunning();
            return;
        }

        double boxX = contentSlot.contentX();
        double boxY = contentSlot.contentY();
        double width = contentSlot.contentWidth();
        double height = contentSlot.contentHeight();

        CornerRadii corners = control.getCornerRadius();
        if (corners == null && content instanceof Region) {
            Region region = (Region) content;
            Insets insets = Insets.EMPTY;
            BorderStroke stroke = firstStroke(region.getBorder());
            BackgroundFill fill = stroke == null ? outlineFill(region.getBackground()) : null;
            if (stroke != null) {
                corners = stroke.getRadii();
                insets = stroke.getInsets();
            } else if (fill != null) {
                corners = fill.getRadii();
                insets = fill.getInsets();
            }
            if (insets != null) {
                boxX += insets.getLeft();
                boxY += insets.getTop();
                width -= insets.getLeft() + insets.getRight();
                height -= insets.getTop() + insets.getBottom();
            }
        }
        BorderBeamGeometry.resolveRadii(corners, Math.max(0.0, width), Math.max(0.0, height), radii);

        double outset = control.getOutset();
        if (Double.isFinite(outset) && outset != 0.0) {
            boxX -= outset;
            boxY -= outset;
            width += 2.0 * outset;
            height += 2.0 * outset;
            BorderBeamGeometry.offsetRadii(radii, outset, Math.max(0.0, width), Math.max(0.0, height));
        }

        boxWidth = Math.max(0.0, width);
        boxHeight = Math.max(0.0, height);
        if (boxWidth > 0.0 && boxHeight > 0.0) {
            double lineWidth = lineWidthOrDefault();
            if (boxWidth != ringWidth || boxHeight != ringHeight || lineWidth != ringLineWidth
                    || !Arrays.equals(radii, ringRadii)) {
                BorderBeamGeometry.buildRing(ring.getElements(), boxWidth, boxHeight, radii, lineWidth);
                ringWidth = boxWidth;
                ringHeight = boxHeight;
                ringLineWidth = lineWidth;
                System.arraycopy(radii, 0, ringRadii, 0, radii.length);
            }
            beamLayer.setLayoutX(boxX);
            beamLayer.setLayoutY(boxY);
            trackRadius = BorderBeamGeometry.trackRadius(boxWidth, boxHeight, beamSizeOrDefault());
            placeBeams();
        }
        updateRunning();
    }

    private static BorderStroke firstStroke(Border border) {
        return border == null || border.getStrokes().isEmpty() ? null : border.getStrokes().get(0);
    }

    // A fill with a negative inset is a decoration round the outline, such as
    // a focus ring, not the outline itself.
    private static BackgroundFill outlineFill(Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        for (BackgroundFill fill : background.getFills()) {
            Insets insets = fill.getInsets();
            if (insets == null || (insets.getTop() >= 0.0 && insets.getRight() >= 0.0
                    && insets.getBottom() >= 0.0 && insets.getLeft() >= 0.0)) {
                return fill;
            }
        }
        return background.getFills().get(0);
    }

    private static double innerSize(double size, double firstInset, double secondInset) {
        return size < 0.0 ? -1.0 : Math.max(0.0, size - firstInset - secondInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + contentSlot.minWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + contentSlot.minHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + contentSlot.prefWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + contentSlot.prefHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + contentSlot.contentMaxWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + contentSlot.contentMaxHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeBaselineOffset(double topInset, double rightInset,
                                           double bottomInset, double leftInset) {
        double offset = contentSlot.getBaselineOffset();
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? offset : topInset + offset;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        observeContentLook(null);
        contentSlot.setContent(null);
        // A replacement skin is constructed before this one is disposed and has
        // already added its nodes: remove only our own.
        getChildren().removeAll(contentSlot, beamLayer);
    }

    // ==================== Beam Node ====================

    private static final class Beam {

        private final Rectangle node = new Rectangle();
        private final Rotate rotate = new Rotate();

        private Beam() {
            node.getStyleClass().add("beam");
            node.setManaged(false);
            node.getTransforms().add(rotate);
        }
    }
}
