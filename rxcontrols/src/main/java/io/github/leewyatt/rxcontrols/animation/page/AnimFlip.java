package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.effect.Effect;
import javafx.scene.effect.PerspectiveTransform;
import javafx.util.Duration;

/**
 * 3D flip transition drawn with a {@link PerspectiveTransform}, so the page turns
 * with perspective foreshortening without a {@code PerspectiveCamera} in the scene.
 * The current page turns 90 degrees about its centre line until it is seen edge on,
 * then the next page turns in from the other side.
 *
 * <p>{@link Orientation#HORIZONTAL} (the default) turns the pages about the
 * horizontal centre line, top over bottom; {@link Orientation#VERTICAL} turns them
 * about the vertical centre line, side over side, like a playing card.</p>
 *
 * <p>Each page turns about the centre of its own layout bounds, read on every frame,
 * so the animation is resize-safe and a page that does not fill the content pane
 * turns in place. Content drawn past those bounds, such as a shadow, turns with the
 * page, and when the near edge would leave the page's content the whole page is
 * scaled down just enough to stay inside it. A page's own effect turns with it and
 * is put back when the flip ends.</p>
 *
 * <p>An effect is drawn before the node's clip is applied, so the transform sees a
 * page's content unclipped. A page that clips content reaching past its bounds should
 * carry that clip on an inner node, or the first and last frames squeeze the whole
 * content into the page.</p>
 */
public class AnimFlip extends PageAnimationBase {

    private static final double DEFAULT_FOCAL_LENGTH_FACTOR = 2.0;
    // Below this the camera would sit inside the turning content.
    private static final double MIN_FOCAL_LENGTH_FACTOR = 0.5;
    // The share of the flip at which the pages are seen edge on and swap.
    private static final double SWAP_POINT = 0.5;

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double focalLengthFactor = DEFAULT_FOCAL_LENGTH_FACTOR;

    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();
    private final double[] corners = new double[8];
    // How far each page's content (a shadow, say) reaches past its layout bounds:
    // top, right, bottom, left. The transform maps the whole content, so the turn
    // uses that wider rectangle and the first and last frames match the page exactly.
    private final double[] currentMargins = new double[4];
    private final double[] nextMargins = new double[4];

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    // ==================== Constructors ====================

    /**
     * Creates a flip animation about the horizontal centre line.
     */
    public AnimFlip() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a flip animation with the given orientation.
     *
     * @param orientation HORIZONTAL turns the pages about the horizontal centre
     *                    line (top over bottom), VERTICAL about the vertical one
     *                    (side over side)
     */
    public AnimFlip(Orientation orientation) {
        this.orientation = orientation;
    }

    // ==================== Settings ====================

    /**
     * Returns the interpolator used for the flip animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the flip animation. It runs over the whole
     * flip, so the default {@link Interpolator#EASE_BOTH} turns fastest when the
     * pages are edge on; {@code null} falls back to that default.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the focal length factor: the distance of the eye from the page, in
     * page widths (VERTICAL) or heights (HORIZONTAL), measured over the page's content
     * when it draws past its bounds. Higher values give a flatter turn. Default is 2.0.
     *
     * @return the focal length factor
     */
    public double getFocalLengthFactor() {
        return focalLengthFactor;
    }

    /**
     * Sets the focal length factor. A value that is not finite or not above 0.5
     * falls back to the default 2.0.
     *
     * @param focalLengthFactor typically 1.5 to 4.0
     */
    public void setFocalLengthFactor(double focalLengthFactor) {
        this.focalLengthFactor = focalLengthFactor;
    }

    // ==================== Transition ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        double sign = context.getDirection() == TransitionDirection.FORWARD ? 1.0 : -1.0;

        // A flip this instance has not ended yet ends first, so two never drive the same
        // transforms and no page is left wearing one.
        jumpToEnd();

        // A page's own effect turns with it, as the transform's input, and comes back
        // when the flip ends.
        Effect currentEffect = ownEffectOf(currentPage);
        Effect nextEffect = ownEffectOf(nextPage);
        // The current page is showing, so it has been laid out. The next one may have
        // joined the pane just now; it is measured at the swap, once it has a size.
        measureMargins(currentPage, currentEffect, currentMargins);
        ptCurrent.setInput(currentEffect);
        ptNext.setInput(nextEffect);

        Runnable finish = () -> {
            removeProgressListener();
            ptCurrent.setInput(null);
            ptNext.setInput(null);
            if (currentPage != null) {
                restoreEffect(currentPage, currentEffect);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                restoreEffect(nextPage, nextEffect);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        // The current page starts flat: a PerspectiveTransform left at its all-zero
        // default would draw it as a single point on the first frame. The next page is
        // hidden until the swap, which projects it before it shows.
        project(ptCurrent, 0.0, currentPage, currentMargins);
        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
            currentPage.setVisible(true);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
            nextPage.setVisible(false);
        }

        progress.set(0);
        boolean[] swapped = {false};
        progressListener = (obs, oldValue, newValue) -> {
            double p = newValue.doubleValue();
            if (!swapped[0] && p < SWAP_POINT) {
                // The current page turns away until it is edge on.
                project(ptCurrent, sign * p * Math.PI, currentPage, currentMargins);
                return;
            }
            if (!swapped[0]) {
                swapped[0] = true;
                if (currentPage != null) {
                    currentPage.setVisible(false);
                }
                if (nextPage != null) {
                    // An effect given to the page since the start, by an opening handler
                    // say, stays; the page then shows without turning.
                    if (nextPage.getEffect() == ptNext) {
                        measureMargins(nextPage, nextEffect, nextMargins);
                        nextPage.setEffect(ptNext);
                    }
                    nextPage.setVisible(true);
                }
            }
            // The next page turns in from the other side. Once it shows, an
            // interpolator that dips back below the middle cannot turn it away again.
            project(ptNext, -sign * (1.0 - Math.max(p, SWAP_POINT)) * Math.PI, nextPage, nextMargins);
        };
        progress.addListener(progressListener);

        // The same order as the other transitions: closing and opening now, closed and
        // opened when it ends. A host that interrupts the flip makes up the last two.
        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolatorOrDefault())));
        timeline.setOnFinished(e -> {
            finish.run();
            // Let go of this transition's pages, panes and host callbacks.
            setFinishAction(null);
            setCurrentAnimation(null);
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private Interpolator interpolatorOrDefault() {
        return interpolator == null ? Interpolator.EASE_BOTH : interpolator;
    }

    // ==================== Projection ====================

    /**
     * Turns a page by theta about its own centre line and projects its corners for an
     * eye in front of it, then shrinks the result about the centre if the near edge
     * would leave the page's content rectangle. The turn is about the centre of the
     * page's layout bounds, so a page that does not fill the content pane turns in
     * place; the projected rectangle is the layout bounds widened by the margins, as
     * the transform maps the page's whole content.
     */
    private void project(PerspectiveTransform pt, double theta, Node page, double[] margins) {
        if (page == null) {
            return;
        }
        Bounds bounds = page.getLayoutBounds();
        double halfW = bounds.getWidth() / 2.0;
        double halfH = bounds.getHeight() / 2.0;
        double centerX = bounds.getMinX() + halfW;
        double centerY = bounds.getMinY() + halfH;
        if (halfW <= 0.0 || halfH <= 0.0) {
            setPT(pt, centerX, centerY, centerX, centerY, centerX, centerY, centerX, centerY);
            return;
        }
        double top = halfH + margins[0];
        double right = halfW + margins[1];
        double bottom = halfH + margins[2];
        double left = halfW + margins[3];
        boolean aboutVertical = orientation == Orientation.VERTICAL;
        // The focal length grows with the content's reach along the turn, so the
        // camera stays in front of a shadow that reaches far past a narrow page.
        double size = 2.0 * (aboutVertical ? Math.max(left, right) : Math.max(top, bottom));
        double f = size * focalLengthOrDefault();
        if (!Double.isFinite(f)) {
            f = size * DEFAULT_FOCAL_LENGTH_FACTOR;
        }
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double fit = 1.0;
        // Upper left, upper right, lower right, lower left.
        for (int i = 0; i < 4; i++) {
            double x = i == 1 || i == 2 ? right : -left;
            double y = i >= 2 ? bottom : -top;
            double z;
            if (aboutVertical) {
                z = x * sin;
                x = x * cos;
            } else {
                z = y * sin;
                y = y * cos;
            }
            double scale = f / (f + z);
            corners[2 * i] = x * scale;
            corners[2 * i + 1] = y * scale;
            fit = Math.min(fit, fitWithin(corners[2 * i], left, right));
            fit = Math.min(fit, fitWithin(corners[2 * i + 1], top, bottom));
        }
        setPT(pt,
                corners[0] * fit + centerX, corners[1] * fit + centerY,
                corners[2] * fit + centerX, corners[3] * fit + centerY,
                corners[4] * fit + centerX, corners[5] * fit + centerY,
                corners[6] * fit + centerX, corners[7] * fit + centerY);
    }

    // The scale that brings an offset from the centre back within the extent on its side.
    private static double fitWithin(double offset, double negativeExtent, double positiveExtent) {
        double extent = offset < 0.0 ? negativeExtent : positiveExtent;
        double reach = Math.abs(offset);
        return reach > extent ? extent / reach : 1.0;
    }

    private double focalLengthOrDefault() {
        // NaN fails the comparison; an infinite factor is caught where the focal length
        // is worked out.
        return focalLengthFactor > MIN_FOCAL_LENGTH_FACTOR
                ? focalLengthFactor : DEFAULT_FOCAL_LENGTH_FACTOR;
    }

    private void setPT(PerspectiveTransform pt,
                       double ulx, double uly, double urx, double ury,
                       double lrx, double lry, double llx, double lly) {
        pt.setUlx(ulx);
        pt.setUly(uly);
        pt.setUrx(urx);
        pt.setUry(ury);
        pt.setLrx(lrx);
        pt.setLry(lry);
        pt.setLlx(llx);
        pt.setLly(lly);
    }

    // ==================== Page effect ====================

    // The page's own effect; one of this instance's transforms left on it by a flip
    // that never ended stands for the effect it wraps.
    private Effect ownEffectOf(Node page) {
        if (page == null) {
            return null;
        }
        Effect effect = page.getEffect();
        if (effect == ptCurrent || effect == ptNext) {
            return ((PerspectiveTransform) effect).getInput();
        }
        return effect;
    }

    // A page wearing something other than this instance's transform was given that
    // effect during the flip, and keeps it.
    private void restoreEffect(Node page, Effect ownEffect) {
        Effect effect = page.getEffect();
        if (effect == ptCurrent || effect == ptNext) {
            page.setEffect(ownEffect);
        }
    }

    // Leaves the page wearing its own effect. A page not laid out yet has no size to
    // measure against, so its content would read as all margin.
    private static void measureMargins(Node page, Effect ownEffect, double[] margins) {
        margins[0] = margins[1] = margins[2] = margins[3] = 0.0;
        if (page == null) {
            return;
        }
        page.setEffect(ownEffect);
        Bounds layout = page.getLayoutBounds();
        if (layout.getWidth() <= 0.0 || layout.getHeight() <= 0.0) {
            return;
        }
        Bounds content = page.getBoundsInLocal();
        margins[0] = Math.max(0.0, layout.getMinY() - content.getMinY());
        margins[1] = Math.max(0.0, content.getMaxX() - layout.getMaxX());
        margins[2] = Math.max(0.0, content.getMaxY() - layout.getMaxY());
        margins[3] = Math.max(0.0, layout.getMinX() - content.getMinX());
    }

    // ==================== Cleanup ====================

    private void removeProgressListener() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        removeProgressListener();
        // Only this instance's transforms come off; a page's own effect stays.
        for (Node child : context.getContentPane().getChildren()) {
            Effect effect = child.getEffect();
            if (effect == ptCurrent || effect == ptNext) {
                child.setEffect(((PerspectiveTransform) effect).getInput());
            }
        }
        ptCurrent.setInput(null);
        ptNext.setInput(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        removeProgressListener();
        super.dispose();
    }
}
