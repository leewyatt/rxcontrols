package io.github.leewyatt.rxcontrols.internal.line;

import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.DecorationProgress;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The line decoration shared by line hosts ({@code RXLineButton},
 * {@code RXLineLabel}): an unmanaged, unclipped layer carrying {@code .line}
 * bars positioned by a {@link LineAnimation} around a reference box.
 *
 * <p>The trigger state and reversible timing live in the shared
 * {@link DecorationProgress}; this class renders the bars as a pure function
 * of its progress. Bars may extend beyond the host bounds — the layer is a
 * pure visual overlay that never participates in layout or picking. While any
 * line is visible the {@code :line-showing} pseudo-class is active on the
 * host.</p>
 *
 * <p>The owning skin inserts {@link #getLayer()} into its children (below
 * label content), calls {@link #layout(double, double, Bounds)} from
 * {@code layoutChildren} with the content reference box and
 * {@link #dispose()} from its dispose chain (children removal stays with the
 * skin).</p>
 */
public final class LineDecoration {

    private static final PseudoClass LINE_SHOWING_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("line-showing");

    private static final LineAnimation DEFAULT_ANIMATION = LineAnimation.UNDERLINE_CENTER_OUT;

    private final Control host;
    private final ObjectProperty<LineAnimation> animation;
    private final DoubleProperty thickness;
    private final DoubleProperty gap;

    private final SkinDisposer disposer = new SkinDisposer();
    private final DecorationProgress progress;
    private final Pane lineLayer = new Pane();
    private final List<Region> bars = new ArrayList<>();

    private LineAnimation appliedAnimation;
    private Bounds reference;

    /**
     * Creates the decoration and wires its triggers on the host.
     *
     * @param host      the control carrying the lines
     * @param animation the line animation property
     * @param trigger   the animation trigger property
     * @param duration  the animation duration property
     * @param thickness the line thickness property
     * @param gap       the line gap property
     */
    public LineDecoration(Control host,
                          ObjectProperty<LineAnimation> animation,
                          ObjectProperty<AnimationTrigger> trigger,
                          ObjectProperty<Duration> duration,
                          DoubleProperty thickness,
                          DoubleProperty gap) {
        this.host = host;
        this.animation = animation;
        this.thickness = thickness;
        this.gap = gap;

        lineLayer.getStyleClass().add("line-layer");
        lineLayer.setManaged(false);
        lineLayer.setMouseTransparent(true);

        progress = new DecorationProgress(host, trigger, duration);

        disposer.registerListener(progress.progressProperty(), this::updateLineGeometry);
        disposer.registerListener(animation, this::updateLineGeometry);
        disposer.registerListener(thickness, this::updateLineGeometry);
        disposer.registerListener(gap, this::updateLineGeometry);
    }

    /**
     * Returns the line layer for the owning skin to insert into its children.
     *
     * @return the line layer
     */
    public Pane getLayer() {
        return lineLayer;
    }

    /**
     * Lays out the line layer over the host bounds and repositions the bars
     * around the given reference box.
     *
     * @param width     the host width
     * @param height    the host height
     * @param reference the content reference box in host-local coordinates
     */
    public void layout(double width, double height, Bounds reference) {
        lineLayer.resizeRelocate(0.0, 0.0, width, height);
        this.reference = snapReference(reference);
        updateLineGeometry();
    }

    /**
     * Computes the content reference box of a labeled host: the union of the
     * bounds of the labeled text node (style class {@code "text"}; the
     * package-private field of {@code LabeledSkinBase} is inaccessible) and
     * the graphic, both placed by the same layout pass. The mnemonic
     * underline and decoration layers are not content. With no visible
     * content the padded content area is returned instead.
     *
     * @param host     the labeled control
     * @param children the skin children
     * @param x        the content area x
     * @param y        the content area y
     * @param w        the content area width
     * @param h        the content area height
     * @return the reference box in host-local coordinates
     */
    public static Bounds contentReferenceOf(Labeled host, List<Node> children,
                                            double x, double y, double w, double h) {
        Bounds union = null;
        Node graphic = host.getGraphic();
        for (Node child : children) {
            boolean isText = child instanceof Text && child.getStyleClass().contains("text");
            if ((isText || child == graphic) && child.isVisible()) {
                union = union(union, child.getBoundsInParent());
            }
        }
        return union != null ? union : new BoundingBox(x, y, w, h);
    }

    private static Bounds union(Bounds a, Bounds b) {
        if (a == null) {
            return b;
        }
        double minX = Math.min(a.getMinX(), b.getMinX());
        double minY = Math.min(a.getMinY(), b.getMinY());
        double maxX = Math.max(a.getMaxX(), b.getMaxX());
        double maxY = Math.max(a.getMaxY(), b.getMaxY());
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Stops the line animation and unregisters all listeners; the owning skin
     * removes {@link #getLayer()} from its children itself.
     */
    public void dispose() {
        progress.dispose();
        host.pseudoClassStateChanged(LINE_SHOWING_PSEUDO_CLASS, false);
        disposer.dispose();
    }

    // ==================== Line Geometry ====================

    private void updateLineGeometry() {
        Bounds box = reference;
        if (box == null || box.getWidth() <= 0.0 || box.getHeight() <= 0.0
                || !Double.isFinite(box.getWidth()) || !Double.isFinite(box.getHeight())) {
            appliedAnimation = null;
            bars.clear();
            lineLayer.getChildren().clear();
            setLineShowing(false);
            return;
        }
        LineAnimation value = animationOrDefault();
        if (value != appliedAnimation || bars.size() != value.barCount()) {
            appliedAnimation = value;
            rebuildBars(value.barCount());
        }
        double current = progress.getProgress();
        // Hide the layer at rest so zero-size or zero-opacity bars can never
        // leak a hairline through sub-pixel rasterization.
        setLineShowing(current > 0.0);
        value.update(bars, current, box,
                host.snapSizeY(RXMath.sanitizeFiniteNonNegative(thickness.get())),
                host.snapSizeY(RXMath.sanitizeFiniteNonNegative(gap.get())));
    }

    private void rebuildBars(int count) {
        bars.clear();
        lineLayer.getChildren().clear();
        for (int i = 0; i < count; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("line");
            bar.setManaged(false);
            bar.setMouseTransparent(true);
            bars.add(bar);
        }
        lineLayer.getChildren().addAll(bars);
    }

    private void setLineShowing(boolean showing) {
        lineLayer.setVisible(showing);
        host.pseudoClassStateChanged(LINE_SHOWING_PSEUDO_CLASS, showing);
    }

    private Bounds snapReference(Bounds value) {
        if (value == null) {
            return null;
        }
        double minX = host.snapPositionX(value.getMinX());
        double minY = host.snapPositionY(value.getMinY());
        double maxX = host.snapPositionX(value.getMaxX());
        double maxY = host.snapPositionY(value.getMaxY());
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private LineAnimation animationOrDefault() {
        LineAnimation value = animation.get();
        return value == null ? DEFAULT_ANIMATION : value;
    }
}
