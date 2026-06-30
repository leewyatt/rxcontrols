package io.github.leewyatt.rxcontrols.internal.fill;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import io.github.leewyatt.rxcontrols.internal.DecorationProgress;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.beans.property.ObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * The fill sweep decoration shared by fill hosts ({@code RXFillButton},
 * {@code RXFillLabel}): an unmanaged layer revealing a fill color through a
 * progress clip, bounded to the host's painted geometry.
 *
 * <p>The trigger state and reversible timing live in the shared
 * {@link DecorationProgress}; this class renders the clip geometry as a pure
 * function of its progress. While any fill is visible the {@code :filling}
 * pseudo-class is active on the host.</p>
 *
 * <p>The owning skin inserts {@link #getLayer()} into its children (below
 * label content), calls {@link #layout(double, double)} from
 * {@code layoutChildren} and {@link #dispose()} from its dispose chain
 * (children removal stays with the skin).</p>
 */
public final class FillDecoration {

    private static final PseudoClass FILLING_PSEUDO_CLASS = PseudoClass.getPseudoClass("filling");

    private static final FillAnimation DEFAULT_ANIMATION = FillAnimation.LEFT_TO_RIGHT;

    private final Control host;
    private final ObjectProperty<FillAnimation> animation;
    private final ObjectProperty<Insets> insets;
    private final ObjectProperty<CornerRadii> radius;

    private final SkinDisposer disposer = new SkinDisposer();
    private final DecorationProgress progress;
    private final Pane fillLayer = new Pane();
    private final Pane fillContent = new Pane();
    private final Region fillRegion = new Region();
    private final BoundedClipSupport boundedClip = new BoundedClipSupport(fillLayer);

    private FillAnimation appliedAnimation;
    private Node fillClip;

    /**
     * Creates the decoration and wires its triggers on the host.
     *
     * @param host      the control carrying the fill
     * @param animation the fill animation property
     * @param trigger   the animation trigger property
     * @param duration  the animation duration property
     * @param insets    the fill insets property
     * @param radius    the fill radius property
     */
    public FillDecoration(Control host,
                          ObjectProperty<FillAnimation> animation,
                          ObjectProperty<AnimationTrigger> trigger,
                          ObjectProperty<Duration> duration,
                          ObjectProperty<Insets> insets,
                          ObjectProperty<CornerRadii> radius) {
        this.host = host;
        this.animation = animation;
        this.insets = insets;
        this.radius = radius;

        fillLayer.getStyleClass().add("fill-layer");
        fillLayer.setManaged(false);
        fillLayer.setMouseTransparent(true);
        fillContent.getStyleClass().add("fill-content");
        fillContent.setManaged(false);
        fillRegion.getStyleClass().add("fill-region");
        fillRegion.setManaged(false);
        fillContent.getChildren().add(fillRegion);
        fillLayer.getChildren().add(fillContent);

        progress = new DecorationProgress(host, trigger, duration);

        disposer.registerListener(progress.progressProperty(), this::updateFillGeometry);
        disposer.registerListener(animation, this::updateFillGeometry);
        // Border width changes relayout via the insets chain on their own.
        disposer.registerListener(insets, host::requestLayout);
        disposer.registerListener(radius, host::requestLayout);
    }

    /**
     * Returns the fill layer for the owning skin to insert into its children.
     *
     * @return the fill layer
     */
    public Pane getLayer() {
        return fillLayer;
    }

    /**
     * Lays out the fill layer over the host bounds and refreshes both clips.
     *
     * @param width  the host width
     * @param height the host height
     */
    public void layout(double width, double height) {
        Insets fillInsets = insets.get();
        Insets effective = fillInsets != null
                ? fillInsets
                : BoundedClipSupport.borderInsetsOf(host);
        double areaW = Math.max(0.0, width - effective.getLeft() - effective.getRight());
        double areaH = Math.max(0.0, height - effective.getTop() - effective.getBottom());
        fillLayer.resizeRelocate(0.0, 0.0, width, height);
        boundedClip.updateClipFor(host, width, height, fillInsets, radius.get());
        fillContent.resizeRelocate(effective.getLeft(), effective.getTop(), areaW, areaH);
        fillRegion.resizeRelocate(0.0, 0.0, areaW, areaH);
        updateFillGeometry();
    }

    /**
     * Stops the fill animation and unregisters all listeners; the owning skin
     * removes {@link #getLayer()} from its children itself.
     */
    public void dispose() {
        progress.dispose();
        boundedClip.clearClip();
        fillContent.setClip(null);
        appliedAnimation = null;
        fillClip = null;
        host.pseudoClassStateChanged(FILLING_PSEUDO_CLASS, false);
        disposer.dispose();
    }

    // ==================== Fill Geometry ====================

    private void updateFillGeometry() {
        // The fill area is the (possibly inset) fillContent box, sized by
        // layout(...) from the insets property or the host border.
        double areaW = fillContent.getWidth();
        double areaH = fillContent.getHeight();
        if (areaW <= 0.0 || areaH <= 0.0
                || !Double.isFinite(areaW) || !Double.isFinite(areaH)) {
            appliedAnimation = null;
            fillClip = null;
            fillContent.setClip(null);
            setFilling(false);
            return;
        }
        FillAnimation value = animationOrDefault();
        if (value != appliedAnimation || fillClip == null) {
            appliedAnimation = value;
            fillClip = value.createClip();
            fillContent.setClip(fillClip);
        }
        double current = progress.getProgress();
        // Hide the layer at rest: a zero-progress clip should paint nothing,
        // but sub-pixel clip rasterization can leak a hairline of the fill.
        setFilling(current > 0.0);
        value.update(fillClip, current, areaW, areaH);
    }

    private void setFilling(boolean filling) {
        fillLayer.setVisible(filling);
        host.pseudoClassStateChanged(FILLING_PSEUDO_CLASS, filling);
    }

    private FillAnimation animationOrDefault() {
        FillAnimation value = animation.get();
        return value == null ? DEFAULT_ANIMATION : value;
    }
}
