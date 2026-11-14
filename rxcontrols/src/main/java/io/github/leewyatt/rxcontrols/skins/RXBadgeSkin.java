package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXBadge.Overlap;
import io.github.leewyatt.rxcontrols.RXBadge.Position;
import io.github.leewyatt.rxcontrols.RXBadge.Variant;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Default skin for {@link RXBadge}.
 *
 * <p>The content sits in a {@code content-slot} pane that fills the badge; the
 * {@code indicator} label and the {@code pulse} ring behind it are placed over a
 * corner of the content and take no part in the size of the badge. Without
 * content the badge is as large as the indicator.</p>
 */
public class RXBadgeSkin extends RXSkinBase<RXBadge> {

    // Half the gap between a square's corner and its inscribed circle, as a
    // share of the side: the point of the circle nearest to the corner.
    private static final double CIRCULAR_INSET = (1.0 - Math.cos(Math.PI / 4.0)) / 2.0;

    private static final Duration SHOW_HIDE_DURATION = Duration.millis(200.0);
    private static final Duration PULSE_DURATION = Duration.millis(1200.0);
    private static final double PULSE_SCALE = 2.4;
    private static final double PULSE_OPACITY = 0.5;

    private static final PseudoClass DOT_PSEUDO_CLASS = PseudoClass.getPseudoClass("dot");

    private final StackPane contentSlot;
    private final Region pulse;
    private final Label indicator;

    private final ScaleTransition showHide;
    private final Timeline pulseTimeline;
    private final ReadOnlyBooleanProperty treeShowing;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXBadgeSkin(RXBadge control) {
        super(control);
        // A wrapper: mouse events on the content still reach the badge's ancestors.
        consumeMouseEvents(false);

        contentSlot = new StackPane();
        contentSlot.getStyleClass().add("content-slot");
        contentSlot.setPickOnBounds(false);

        pulse = new Region();
        pulse.getStyleClass().add("pulse");
        pulse.setManaged(false);
        pulse.setMouseTransparent(true);
        pulse.setVisible(false);
        pulse.setOpacity(0.0);

        indicator = new Label();
        indicator.getStyleClass().add("indicator");
        indicator.setMouseTransparent(true);
        indicator.setVisible(control.isIndicatorShowing());
        syncIndicatorLook();

        getChildren().addAll(contentSlot, pulse, indicator);

        showHide = new ScaleTransition(SHOW_HIDE_DURATION, indicator);
        showHide.setInterpolator(Interpolator.EASE_BOTH);
        showHide.setOnFinished(event -> onShowHideFinished());
        disposer.registerDisposeTask(showHide::stop);

        pulseTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(pulse.scaleXProperty(), 1.0),
                        new KeyValue(pulse.scaleYProperty(), 1.0),
                        new KeyValue(pulse.opacityProperty(), PULSE_OPACITY)),
                new KeyFrame(PULSE_DURATION,
                        new KeyValue(pulse.scaleXProperty(), PULSE_SCALE, Interpolator.EASE_OUT),
                        new KeyValue(pulse.scaleYProperty(), PULSE_SCALE, Interpolator.EASE_OUT),
                        new KeyValue(pulse.opacityProperty(), 0.0, Interpolator.EASE_OUT)));
        pulseTimeline.setCycleCount(Animation.INDEFINITE);
        disposer.registerDisposeTask(pulseTimeline::stop);

        treeShowing = controlTreeShowingProperty();

        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.indicatorTextProperty(), this::onIndicatorLookChanged);
        disposer.registerListener(control.variantProperty(), this::onIndicatorLookChanged);
        disposer.registerListener(control.indicatorShowingProperty(), this::onIndicatorShowingChanged);
        disposer.registerListener(control.pulsingProperty(), this::updatePulse);
        disposer.registerListener(treeShowing, this::updatePulse);

        updateContent();
        updatePulse();
    }

    // ==================== State Changes ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        if (content == null) {
            contentSlot.getChildren().clear();
        } else {
            contentSlot.getChildren().setAll(content);
        }
        contentSlot.setManaged(content != null);
        getSkinnable().requestLayout();
    }

    // The indicator keeps its last text and shape while it scales out.
    private void onIndicatorLookChanged() {
        if (getSkinnable().isIndicatorShowing()) {
            syncIndicatorLook();
        }
    }

    private void syncIndicatorLook() {
        RXBadge control = getSkinnable();
        indicator.setText(control.getIndicatorText());
        indicator.pseudoClassStateChanged(DOT_PSEUDO_CLASS, control.getVariant() == Variant.DOT);
    }

    private void onIndicatorShowingChanged() {
        RXBadge control = getSkinnable();
        boolean showing = control.isIndicatorShowing();
        showHide.stop();
        if (showing) {
            syncIndicatorLook();
        }
        boolean inShowingTree = treeShowing.get();
        if (control.isAnimated() && inShowingTree) {
            if (showing && !indicator.isVisible()) {
                indicator.setScaleX(0.0);
                indicator.setScaleY(0.0);
                indicator.setVisible(true);
            }
            double target = showing ? 1.0 : 0.0;
            showHide.setFromX(indicator.getScaleX());
            showHide.setFromY(indicator.getScaleY());
            showHide.setToX(target);
            showHide.setToY(target);
            showHide.playFromStart();
        } else {
            settleIndicator(showing);
        }
        updatePulse();
        control.requestLayout();
    }

    private void onShowHideFinished() {
        settleIndicator(getSkinnable().isIndicatorShowing());
        updatePulse();
        getSkinnable().requestLayout();
    }

    private void settleIndicator(boolean showing) {
        indicator.setScaleX(1.0);
        indicator.setScaleY(1.0);
        indicator.setVisible(showing);
    }

    private void updatePulse() {
        RXBadge control = getSkinnable();
        // Read first: the property reports its next change only once it has been read.
        boolean inShowingTree = treeShowing.get();
        // The ring waits for an indicator that is still scaling in.
        boolean settled = showHide.getStatus() != Animation.Status.RUNNING;
        if (control.isPulsing() && control.isIndicatorShowing() && inShowingTree && settled) {
            pulse.setVisible(true);
            pulseTimeline.play();
        } else {
            pulseTimeline.stop();
            pulse.setVisible(false);
            pulse.setScaleX(1.0);
            pulse.setScaleY(1.0);
            pulse.setOpacity(0.0);
        }
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXBadge control = getSkinnable();
        Node content = control.getContent();
        double indicatorHeight = indicatorHeight();
        double indicatorWidth = indicatorWidth(indicatorHeight);

        if (content == null) {
            placeIndicator(x + w / 2.0, y + h / 2.0, indicatorWidth, indicatorHeight);
            return;
        }

        contentSlot.resizeRelocate(x, y, w, h);
        // Positions the content now, so the indicator can follow where it landed.
        contentSlot.layout();
        double contentX = x;
        double contentY = y;
        double contentW = w;
        double contentH = h;
        // Content moved to another parent by the application has no place here;
        // the indicator then follows the slot.
        if (content.getParent() == contentSlot) {
            Bounds bounds = content.getLayoutBounds();
            contentX = x + content.getLayoutX() + bounds.getMinX();
            contentY = y + content.getLayoutY() + bounds.getMinY();
            contentW = bounds.getWidth();
            contentH = bounds.getHeight();
        }

        Position position = control.getPosition() == null ? RXBadge.DEFAULT_POSITION : control.getPosition();
        double inset = control.getOverlap() == Overlap.CIRCULAR ? CIRCULAR_INSET : 0.0;
        double shiftX = inset * contentW + finiteOrZero(control.getOffsetX());
        double shiftY = inset * contentH + finiteOrZero(control.getOffsetY());
        double centerX = position.isEnd() ? contentX + contentW - shiftX : contentX + shiftX;
        double centerY = position.isTop() ? contentY + shiftY : contentY + contentH - shiftY;
        placeIndicator(centerX, centerY, indicatorWidth, indicatorHeight);
    }

    private void placeIndicator(double centerX, double centerY, double width, double height) {
        double x = snapPositionX(centerX - width / 2.0);
        double y = snapPositionY(centerY - height / 2.0);
        indicator.resizeRelocate(x, y, width, height);
        pulse.resizeRelocate(x, y, width, height);
    }

    private double indicatorHeight() {
        return snapSizeY(indicator.prefHeight(-1.0));
    }

    // Never narrower than tall: a single character gives a circle.
    private double indicatorWidth(double height) {
        return Math.max(snapSizeX(indicator.prefWidth(-1.0)), height);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private boolean isStandalone() {
        return getSkinnable().getContent() == null;
    }

    // The size of a badge without content; it stays until a hiding indicator
    // has finished scaling out.
    private double standaloneHeight() {
        return indicator.isVisible() ? indicatorHeight() : 0.0;
    }

    private double standaloneWidth() {
        return indicator.isVisible() ? indicatorWidth(indicatorHeight()) : 0.0;
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
        double inner = isStandalone()
                ? standaloneWidth()
                : contentSlot.minWidth(innerSize(height, topInset, bottomInset));
        return leftInset + inner + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double inner = isStandalone()
                ? standaloneHeight()
                : contentSlot.minHeight(innerSize(width, leftInset, rightInset));
        return topInset + inner + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double inner = isStandalone()
                ? standaloneWidth()
                : contentSlot.prefWidth(innerSize(height, topInset, bottomInset));
        return leftInset + inner + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double inner = isStandalone()
                ? standaloneHeight()
                : contentSlot.prefHeight(innerSize(width, leftInset, rightInset));
        return topInset + inner + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        Node content = getSkinnable().getContent();
        if (content == null) {
            return leftInset + standaloneWidth() + rightInset;
        }
        if (!content.isManaged()) {
            return Double.MAX_VALUE;
        }
        return leftInset + content.maxWidth(innerSize(height, topInset, bottomInset)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        Node content = getSkinnable().getContent();
        if (content == null) {
            return topInset + standaloneHeight() + bottomInset;
        }
        if (!content.isManaged()) {
            return Double.MAX_VALUE;
        }
        return topInset + content.maxHeight(innerSize(width, leftInset, rightInset)) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeBaselineOffset(double topInset, double rightInset,
                                           double bottomInset, double leftInset) {
        Node source = isStandalone() ? indicator : contentSlot;
        double offset = source.getBaselineOffset();
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? offset : topInset + offset;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        contentSlot.getChildren().clear();
        // A replacement skin is constructed before this one is disposed and has
        // already added its nodes: remove only our own.
        getChildren().removeAll(contentSlot, pulse, indicator);
    }
}
