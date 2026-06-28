package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorPosition;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.internal.slider.IndicatorAnimator;
import io.github.leewyatt.rxcontrols.internal.slider.SliderAxis;
import io.github.leewyatt.rxcontrols.internal.slider.SliderGeometry;
import io.github.leewyatt.rxcontrols.internal.slider.SliderMetrics;
import io.github.leewyatt.rxcontrols.internal.slider.SliderSnapper;
import io.github.leewyatt.rxcontrols.internal.slider.SliderTickLayout;
import io.github.leewyatt.rxcontrols.internal.slider.SliderValueIndicator;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXSlider}. Generalizes the {@code RXSeekBar} skeleton
 * from {@code [0, 1]} to the slider's {@code [min, max]} model, rendering an
 * inactive track, an active fill, a draggable thumb with Material feedback (an
 * unbounded {@link StateLayer} halo plus an optional bounded press ink), and an
 * in-skin value indicator bubble.
 *
 * <p>{@code valueChanging} is set {@code true} only while the thumb is dragged
 * with the pointer and {@code false} on release (with a snap), matching
 * {@code SliderBehavior}; track clicks and the keyboard commit discretely and do
 * not flip it. The indicator visibility is driven independently of
 * {@code valueChanging} (by interaction and focus), so the keyboard pops it too.</p>
 */
public class RXSliderSkin extends RXSkinBase<RXSlider> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = SliderMetrics.DEFAULT_PREF_LENGTH;
    private static final double HALF = 0.5;
    private static final double INDICATOR_GAP = SliderMetrics.INDICATOR_GAP;
    private static final double TICK_LABEL_GAP = SliderMetrics.TICK_LABEL_GAP;

    // ==================== Nodes ====================

    private final Region hitArea = new Region();
    private final Region track = new Region();
    private final Region fill = new Region();
    private final StateLayer halo = new StateLayer();
    private final StackPane thumb = new StackPane() {
        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            if (attribute == AccessibleAttribute.VALUE) {
                return getSkinnable().getValue();
            }
            return super.queryAccessibleAttribute(attribute, parameters);
        }
    };
    private final RippleLayer ink = new RippleLayer();
    private final SliderValueIndicator indicator = new SliderValueIndicator();
    private final List<Region> tickMarks = new ArrayList<>();
    private final List<Label> tickLabels = new ArrayList<>();
    private double[] tickMarkValues = new double[0];
    private double[] tickLabelValues = new double[0];
    private final SliderSnapper snapper = new SliderSnapper(
            this::snapPositionX, this::snapPositionY, this::snapSizeX, this::snapSizeY);

    private final RippleBehavior inkBehavior;
    private final IndicatorAnimator indicatorAnimator;

    // ==================== Pointer state ====================

    private Point2D dragStart;
    private double preDragValue;
    private boolean thumbDragging;
    private boolean pointerInThumb;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given slider.
     *
     * @param control the skinnable control
     */
    public RXSliderSkin(RXSlider control) {
        super(control);
        inkBehavior = new RippleBehavior(ink, control::getRippleFill,
                () -> RXRipplePane.DEFAULT_RIPPLE_OPACITY);
        indicatorAnimator = new IndicatorAnimator(indicator,
                control::getIndicatorDisplay, control::isAnimated);
        initializeNodes(control);
        registerListeners(control);
        registerFeedbackListeners(control);
        registerPointerHandlers();
        registerKeyboardHandler(control);

        rebuildTicks();
        updateIndicatorText();
        updateHalo();
        updateIndicator();
    }

    private void initializeNodes(RXSlider control) {
        hitArea.setManaged(false);
        hitArea.setPickOnBounds(true);

        track.getStyleClass().add("track");
        fill.getStyleClass().add("fill");
        thumb.getStyleClass().add("thumb");
        thumb.setAccessibleRole(AccessibleRole.THUMB);
        fill.setMouseTransparent(true);

        // Unbounded thumb halo: a square overlay shaped round by setFill, painted
        // below the opaque thumb so only the ring shows. Never clipped (CIRCLE).
        halo.setClipMode(StateLayer.ClipMode.CIRCLE, null);
        halo.setFill(control.getRippleFill());

        // Optional bounded press ink, living on the thumb face and clipped to the
        // thumb circle by updateClipFor in layoutChildren.
        thumb.getChildren().add(ink);
        // The indicator starts hidden (set by the IndicatorAnimator); the child
        // list is established by rebuildTicks() in the constructor.
    }

    private void registerListeners(RXSlider control) {
        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        disposer.registerListener(control.minProperty(), this::onTickModelChanged);
        disposer.registerListener(control.maxProperty(), this::onTickModelChanged);
        disposer.registerListener(control.majorTickUnitProperty(), this::onTickModelChanged);
        disposer.registerListener(control.minorTickCountProperty(), this::onTickModelChanged);
        disposer.registerListener(control.orientationProperty(), control::requestLayout);
        disposer.registerListener(control.showTickMarksProperty(), this::onTickVisibilityChanged);
        disposer.registerListener(control.showTickLabelsProperty(), this::onTickVisibilityChanged);
        disposer.registerListener(control.labelFormatterProperty(), () -> {
            updateIndicatorText();
            rebuildTicks();
        });
    }

    private void registerFeedbackListeners(RXSlider control) {
        // Hover is driven from the thumb's enter/exit events (not its
        // hoverProperty) so it reflects the pointer being over the thumb itself,
        // never the far track.
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_ENTERED, event -> {
            pointerInThumb = true;
            updateHalo();
        });
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_EXITED, event -> {
            pointerInThumb = false;
            updateHalo();
        });
        disposer.registerListener(thumb.pressedProperty(), this::updateFeedback);
        disposer.registerListener(control.focusedProperty(), this::updateFeedback);
        disposer.registerListener(control.valueChangingProperty(), this::updateFeedback);
        disposer.registerListener(control.disabledProperty(), () -> {
            updateHalo();
            if (control.isDisabled()) {
                // A disabled node may never see the thumb release, so end any
                // in-flight gesture: clear the ink and reset the drag /
                // valueChanging state rather than strand an unreleased ripple or
                // a stuck flag (matching the RippleDecoration facade lifecycle).
                inkBehavior.clear();
                if (thumbDragging) {
                    thumbDragging = false;
                    dragStart = null;
                    control.setValueChanging(false);
                }
            }
        });
        disposer.registerListener(control.stateOverlayEnabledProperty(), this::updateHalo);
        disposer.registerListener(control.rippleFillProperty(), () -> halo.setFill(control.getRippleFill()));
        disposer.registerListener(control.rippleEnabledProperty(), () -> {
            if (!control.isRippleEnabled()) {
                inkBehavior.clear();
            }
        });
        disposer.registerListener(control.indicatorDisplayProperty(), this::updateIndicator);
    }

    private void registerPointerHandlers() {
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);

        disposer.registerEventHandler(track, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(track, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);

        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_PRESSED, this::onThumbPressed);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_DRAGGED, this::onThumbDragged);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_RELEASED, this::onThumbReleased);
    }

    private void registerKeyboardHandler(RXSlider control) {
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Pointer interaction ====================

    private void onTrackPressed(MouseEvent event) {
        if (thumb.isPressed()) {
            return;
        }
        getSkinnable().requestFocus();
        setValueFromPointer(event);
        event.consume();
    }

    private void onTrackDragged(MouseEvent event) {
        if (thumb.isPressed()) {
            return;
        }
        setValueFromPointer(event);
        event.consume();
    }

    private void onThumbPressed(MouseEvent event) {
        RXSlider control = getSkinnable();
        control.requestFocus();
        control.setValueChanging(true);
        thumbDragging = true;
        dragStart = thumb.localToParent(event.getX(), event.getY());
        preDragValue = control.getValue();
        if (control.isRippleEnabled() && !control.isDisabled()) {
            inkBehavior.press(thumb.getWidth() * HALF, thumb.getHeight() * HALF, true);
        }
        event.consume();
    }

    private void onThumbDragged(MouseEvent event) {
        if (!thumbDragging || dragStart == null) {
            return;
        }
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return;
        }
        RXSlider control = getSkinnable();
        Point2D current = thumb.localToParent(event.getX(), event.getY());
        // Vertical grows upward (min at the bottom), so a smaller y is a larger value.
        double mainDelta = vertical()
                ? -(current.getY() - dragStart.getY())
                : (current.getX() - dragStart.getX());
        double deltaFraction = mainDelta / trackLength;
        // No snap during the drag (RT-15207): snapping happens only on release.
        control.setValue(preDragValue + deltaFraction * (control.getMax() - control.getMin()));
        event.consume();
    }

    private void onThumbReleased(MouseEvent event) {
        inkBehavior.release();
        if (!thumbDragging) {
            return;
        }
        thumbDragging = false;
        dragStart = null;
        RXSlider control = getSkinnable();
        control.setValueChanging(false);
        // Snap to the nearest tick once the drag ends (RT-15207).
        control.adjustValue(control.getValue());
        event.consume();
    }

    private void setValueFromPointer(MouseEvent event) {
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return;
        }
        Point2D point = ((Node) event.getSource()).localToParent(event.getX(), event.getY());
        double fraction = vertical()
                ? 1.0 - (point.getY() - track.getLayoutY()) / trackLength
                : (point.getX() - track.getLayoutX()) / trackLength;
        RXSlider control = getSkinnable();
        control.adjustValue(SliderGeometry.fractionToValue(fraction, control.getMin(), control.getMax()));
    }

    private boolean vertical() {
        return getSkinnable().getOrientation() == Orientation.VERTICAL;
    }

    private double trackMainLength() {
        return vertical() ? track.getHeight() : track.getWidth();
    }

    // ==================== Keyboard interaction ====================

    private void onKeyPressed(KeyEvent event) {
        RXSlider control = getSkinnable();
        KeyCode code = event.getCode();
        boolean horizontal = control.getOrientation() != Orientation.VERTICAL;
        boolean rtl = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;

        if (code == KeyCode.HOME) {
            control.adjustValue(control.getMin());
            event.consume();
        } else if (code == KeyCode.END) {
            control.adjustValue(control.getMax());
            event.consume();
        } else if (horizontal && (code == KeyCode.LEFT || code == KeyCode.KP_LEFT)) {
            decrementOrIncrement(rtl);
            event.consume();
        } else if (horizontal && (code == KeyCode.RIGHT || code == KeyCode.KP_RIGHT)) {
            decrementOrIncrement(!rtl);
            event.consume();
        } else if (!horizontal && (code == KeyCode.UP || code == KeyCode.KP_UP)) {
            decrementOrIncrement(true);
            event.consume();
        } else if (!horizontal && (code == KeyCode.DOWN || code == KeyCode.KP_DOWN)) {
            decrementOrIncrement(false);
            event.consume();
        }
    }

    private void decrementOrIncrement(boolean increment) {
        RXSlider control = getSkinnable();
        if (control.isSnapToTicks()) {
            // RT-8634: under snapToTicks the step is at least one tick, so an
            // arrow press always advances even when blockIncrement is smaller.
            double step = snapAwareStep();
            control.adjustValue(control.getValue() + (increment ? step : -step));
        } else if (increment) {
            control.increment();
        } else {
            control.decrement();
        }
    }

    private double snapAwareStep() {
        RXSlider control = getSkinnable();
        double tickSpacing = SliderGeometry.minorSpacing(control.getMajorTickUnit(),
                control.getMinorTickCount());
        double block = control.getBlockIncrement();
        return block > 0.0 && block < tickSpacing ? tickSpacing : block;
    }

    // ==================== Feedback (halo + indicator) ====================

    private void onValueChanged() {
        updateIndicatorText();
        updateIndicator();
        getSkinnable().requestLayout();
    }

    private void updateFeedback() {
        updateHalo();
        updateIndicator();
    }

    private void updateHalo() {
        RXSlider control = getSkinnable();
        boolean enabled = control.isStateOverlayEnabled() && !control.isDisabled();
        halo.setState(enabled && pointerInThumb, enabled && control.isFocused(),
                enabled && thumb.isPressed(), enabled && control.isValueChanging());
    }

    private void updateIndicator() {
        RXSlider control = getSkinnable();
        indicatorAnimator.update(control.isValueChanging() || thumb.isPressed() || control.isFocused());
    }

    private void updateIndicatorText() {
        indicator.setText(formatValue(getSkinnable().getValue()));
    }

    private String formatValue(double value) {
        StringConverter<Double> formatter = getSkinnable().getLabelFormatter();
        return formatter != null ? formatter.toString(value) : Long.toString(Math.round(value));
    }

    private RXSliderIndicatorPosition positionOrDefault() {
        RXSliderIndicatorPosition position = getSkinnable().getIndicatorPosition();
        return position == null ? RXSlider.DEFAULT_INDICATOR_POSITION : position;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double thumbSize = snapSizeX(thumb.prefWidth(-1));
        double thickness = snapSizeY(track.prefHeight(-1));
        hitArea.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));

        boolean vertical = vertical();
        SliderAxis axis = new SliderAxis(vertical, x, y, Math.max(0.0, w), Math.max(0.0, h));
        double mainLength = axis.mainLength();
        double crossExtent = vertical ? Math.max(0.0, w) : Math.max(0.0, h);
        // Reserve a cross-axis band for the tick labels (the bottom for a
        // horizontal slider, the trailing side for a vertical one).
        double crossBar = Math.max(0.0, crossExtent - labelBandExtent());
        double barCrossCenter = crossBar * HALF;
        double trackMLo = thumbSize * HALF;
        double trackLengthM = mainLength - thumbSize;

        if (mainLength <= 0.0 || trackLengthM <= 0.0) {
            resetLayout(axis, thumbSize, barCrossCenter);
            return;
        }

        RXSlider control = getSkinnable();
        double f = SliderGeometry.valueToFraction(control.getValue(), control.getMin(), control.getMax());
        double thumbM = trackMLo + trackLengthM * f;
        double trackCLo = barCrossCenter - thickness * HALF;
        double trackCHi = barCrossCenter + thickness * HALF;

        place(axis, track, trackMLo, trackMLo + trackLengthM, trackCLo, trackCHi);
        place(axis, fill, trackMLo, thumbM, trackCLo, trackCHi);
        place(axis, thumb, thumbM - thumbSize * HALF, thumbM + thumbSize * HALF,
                barCrossCenter - thumbSize * HALF, barCrossCenter + thumbSize * HALF);

        layoutHalo(axis.pointX(thumbM, barCrossCenter), axis.pointY(thumbM, barCrossCenter));
        layoutInk(thumbSize, thumbSize);
        layoutIndicator(axis, thumbM, barCrossCenter, thumbSize);
        layoutTicks(axis, trackMLo, trackLengthM, barCrossCenter, crossBar);
    }

    private void place(SliderAxis axis, Region node, double mLo, double mHi, double cLo, double cHi) {
        node.resizeRelocate(
                snapPositionX(axis.rectX(mLo, mHi, cLo, cHi)),
                snapPositionY(axis.rectY(mLo, mHi, cLo, cHi)),
                snapSizeX(axis.rectW(mHi - mLo, cHi - cLo)),
                snapSizeY(axis.rectH(mHi - mLo, cHi - cLo)));
    }

    private void layoutHalo(double centerX, double centerY) {
        double diameter = snapSizeX(halo.prefWidth(-1));
        halo.resize(diameter, diameter);
        halo.relocate(snapPositionX(centerX - diameter * HALF), snapPositionY(centerY - diameter * HALF));
    }

    private void layoutInk(double thumbW, double thumbH) {
        // The ink lives in the thumb's local coordinate space and is clipped to
        // the thumb's painted circle.
        ink.resizeRelocate(0.0, 0.0, thumbW, thumbH);
        ink.updateClipFor(thumb, thumbW, thumbH);
    }

    private void layoutIndicator(SliderAxis axis, double thumbM, double barCrossCenter, double thumbSize) {
        boolean negativeSide = positionOrDefault() != RXSliderIndicatorPosition.BELOW;
        boolean vertical = axis.isVertical();
        // The horizontal bubble points at the thumb with its caret; the vertical
        // bubble sits beside the thumb without a caret.
        indicator.setCaretVisible(!vertical);
        if (!vertical) {
            indicator.setCaretBelow(negativeSide);
        }
        double bubbleW = snapSizeX(indicator.prefWidth(-1));
        double bubbleH = snapSizeY(indicator.prefHeight(-1));
        indicator.resize(bubbleW, bubbleH);
        double bubbleMain = vertical ? bubbleH : bubbleW;
        double bubbleCross = vertical ? bubbleW : bubbleH;
        double mainLo = RXMath.clamp(thumbM - bubbleMain * HALF, 0.0, Math.max(0.0, axis.mainLength() - bubbleMain));
        double thumbCrossLo = barCrossCenter - thumbSize * HALF;
        double thumbCrossHi = barCrossCenter + thumbSize * HALF;
        double crossLo = negativeSide
                ? (thumbCrossLo - INDICATOR_GAP - bubbleCross)
                : (thumbCrossHi + INDICATOR_GAP);
        indicator.relocate(
                snapPositionX(axis.rectX(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)),
                snapPositionY(axis.rectY(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)));
    }

    private void resetLayout(SliderAxis axis, double thumbSize, double barCrossCenter) {
        double trackMLo = thumbSize * HALF;
        place(axis, track, trackMLo, trackMLo, barCrossCenter, barCrossCenter);
        place(axis, fill, trackMLo, trackMLo, barCrossCenter, barCrossCenter);
        place(axis, thumb, trackMLo - thumbSize * HALF, trackMLo + thumbSize * HALF,
                barCrossCenter - thumbSize * HALF, barCrossCenter + thumbSize * HALF);
        // Keep the overlays glued to the collapsed thumb rather than stranded at
        // stale coordinates (AGENTS §1.8).
        layoutHalo(axis.pointX(trackMLo, barCrossCenter), axis.pointY(trackMLo, barCrossCenter));
        layoutInk(thumbSize, thumbSize);
        layoutIndicator(axis, trackMLo, barCrossCenter, thumbSize);
        layoutTicks(axis, trackMLo, 0.0, barCrossCenter, 0.0);
    }

    // ==================== Ticks ====================

    private void onTickModelChanged() {
        rebuildTicks();
        getSkinnable().requestLayout();
    }

    private void onTickVisibilityChanged() {
        updateTickVisibility();
        getSkinnable().requestLayout();
    }

    private void rebuildTicks() {
        RXSlider control = getSkinnable();
        tickMarkValues = SliderGeometry.tickValues(control.getMin(), control.getMax(),
                control.getMajorTickUnit(), control.getMinorTickCount());
        tickLabelValues = SliderGeometry.majorTickValues(control.getMin(), control.getMax(),
                control.getMajorTickUnit());
        tickMarks.clear();
        tickLabels.clear();
        for (int i = 0; i < tickMarkValues.length; i++) {
            Region mark = new Region();
            mark.getStyleClass().add("tick-mark");
            mark.setManaged(false);
            mark.setMouseTransparent(true);
            mark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            tickMarks.add(mark);
        }
        for (double value : tickLabelValues) {
            Label label = new Label(formatValue(value));
            label.getStyleClass().add("tick-label");
            label.setManaged(false);
            label.setMouseTransparent(true);
            tickLabels.add(label);
        }
        // Rebuild the whole children list deterministically (never a bare clear:
        // the base nodes — thumb, halo, indicator — are always re-added, fixing
        // the ControlsFX runtime-toggle bug that dropped the thumb / range bar).
        getChildren().setAll(assembleChildren());
        updateTickVisibility();
    }

    private void updateTickVisibility() {
        RXSlider control = getSkinnable();
        boolean showMarks = control.isShowTickMarks();
        boolean showLabels = control.isShowTickLabels();
        for (Region mark : tickMarks) {
            mark.setVisible(showMarks);
        }
        for (Label label : tickLabels) {
            label.setVisible(showLabels);
        }
    }

    private void layoutTicks(SliderAxis axis, double trackMLo, double trackLengthM,
                             double barCrossCenter, double crossBar) {
        RXSlider control = getSkinnable();
        SliderTickLayout.layoutTicks(axis, trackMLo, trackLengthM, barCrossCenter, crossBar,
                TICK_LABEL_GAP, control.getMin(), control.getMax(),
                control.isShowTickMarks(), tickMarks, tickMarkValues,
                control.isShowTickLabels(), tickLabels, tickLabelValues, snapper);
    }

    private double labelBandExtent() {
        return SliderTickLayout.labelBandExtent(getSkinnable().isShowTickLabels(),
                vertical(), tickLabels, TICK_LABEL_GAP);
    }

    private List<Node> assembleChildren() {
        List<Node> children = new ArrayList<>();
        children.add(hitArea);
        children.add(track);
        children.add(fill);
        children.addAll(tickMarks);
        children.addAll(tickLabels);
        children.add(halo);
        children.add(thumb);
        children.add(indicator);
        return children;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double main = vertical() ? crossThickness() : mainMin();
        return leftInset + main + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double main = vertical() ? mainMin() : crossThickness();
        return topInset + main + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double main = vertical() ? crossThickness() : DEFAULT_PREF_WIDTH;
        return leftInset + main + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double main = vertical() ? DEFAULT_PREF_WIDTH : crossThickness();
        return topInset + main + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return vertical() ? getSkinnable().prefWidth(height) : Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return vertical() ? Double.MAX_VALUE : getSkinnable().prefHeight(width);
    }

    private double mainMin() {
        // The shortest usable track: at least two thumb diameters.
        return 2.0 * thumb.prefWidth(-1) + thumb.minWidth(-1);
    }

    private double crossThickness() {
        // The cross-axis thickness: the thumb / halo bar plus the tick-label band.
        // The transient indicator is excluded — it may overflow.
        double bar = Math.max(Math.max(track.prefHeight(-1), thumb.prefHeight(-1)), halo.prefHeight(-1));
        return bar + labelBandExtent();
    }

    /** {@inheritDoc} */
    @Override
    public double computeBaselineOffset(double topInset, double rightInset,
                                        double bottomInset, double leftInset) {
        return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        indicatorAnimator.dispose();
        inkBehavior.clear();
        ink.clearClip();
        halo.reset();
    }
}
