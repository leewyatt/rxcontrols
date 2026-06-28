package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorPosition;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.internal.slider.IndicatorAnimator;
import io.github.leewyatt.rxcontrols.internal.slider.SliderAxis;
import io.github.leewyatt.rxcontrols.internal.slider.SliderGeometry;
import io.github.leewyatt.rxcontrols.internal.slider.SliderMetrics;
import io.github.leewyatt.rxcontrols.internal.slider.SliderSnapper;
import io.github.leewyatt.rxcontrols.internal.slider.SliderThumb;
import io.github.leewyatt.rxcontrols.internal.slider.SliderTickLayout;
import io.github.leewyatt.rxcontrols.internal.slider.SliderValueIndicator;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
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
 * Default skin for {@link RXRangeSlider}: two thumbs (lower / upper) over a
 * shared track with the active fill drawn between them, each thumb carrying the
 * same Material feedback as {@link RXSliderSkin} (an unbounded {@link StateLayer}
 * halo, an optional press ink, and an in-skin value indicator). It reuses the
 * single-value support — {@link SliderGeometry}, {@link SliderValueIndicator},
 * {@link IndicatorAnimator}, {@link SliderThumb} — and the self-rendered tick
 * scale.
 *
 * <p>The two thumbs are real focus stops: Tab moves between them, and the arrow
 * keys / HOME / END move the focused thumb (fixing the ControlsFX bugs where
 * HOME/END always moved the high thumb, focus was faked, runtime tick toggles
 * dropped the high thumb / range bar, every key was consumed, and the skin was
 * never disposed). The {@code *Changing} flags flip only during a pointer drag.</p>
 */
public class RXRangeSliderSkin extends RXSkinBase<RXRangeSlider> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = SliderMetrics.DEFAULT_PREF_LENGTH;
    private static final double HALF = 0.5;
    private static final double INDICATOR_GAP = SliderMetrics.INDICATOR_GAP;
    private static final double TICK_LABEL_GAP = SliderMetrics.TICK_LABEL_GAP;
    private static final double MERGE_GAP = 4.0;
    private static final String EN_DASH = " – ";

    private static final PseudoClass LOWER_PSEUDO = PseudoClass.getPseudoClass("lower");
    private static final PseudoClass UPPER_PSEUDO = PseudoClass.getPseudoClass("upper");
    private static final PseudoClass MERGED_PSEUDO = PseudoClass.getPseudoClass("merged");

    // ==================== Nodes ====================

    private final Region hitArea = new Region();
    private final Region track = new Region();
    private final Region fill = new Region();
    private final SliderThumb low;
    private final SliderThumb high;
    private final SliderValueIndicator lowIndicator = new SliderValueIndicator();
    private final SliderValueIndicator highIndicator = new SliderValueIndicator();
    private final IndicatorAnimator lowAnimator;
    private final IndicatorAnimator highAnimator;
    private final List<Region> tickMarks = new ArrayList<>();
    private final List<Label> tickLabels = new ArrayList<>();
    private double[] tickMarkValues = new double[0];
    private double[] tickLabelValues = new double[0];
    private final SliderSnapper snapper = new SliderSnapper(
            this::snapPositionX, this::snapPositionY, this::snapSizeX, this::snapSizeY);

    // ==================== Pointer / focus state ====================

    private Point2D dragStart;
    private double preDragValue;
    private boolean dragging;
    private boolean draggingLow;
    private boolean pointerInLow;
    private boolean pointerInHigh;
    private boolean activeThumbIsLow = true;

    private Point2D bandDragStart;
    private double bandPreLow;
    private double bandPreHigh;
    private boolean bandDragging;
    private boolean merged;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given range slider.
     *
     * @param control the skinnable control
     */
    public RXRangeSliderSkin(RXRangeSlider control) {
        super(control);
        low = new SliderThumb(control::getLowValue, control::getRippleFill,
                () -> RXRipplePane.DEFAULT_RIPPLE_OPACITY, LOWER_PSEUDO);
        high = new SliderThumb(control::getHighValue, control::getRippleFill,
                () -> RXRipplePane.DEFAULT_RIPPLE_OPACITY, UPPER_PSEUDO);
        lowAnimator = new IndicatorAnimator(lowIndicator, control::getIndicatorDisplay, control::isAnimated);
        highAnimator = new IndicatorAnimator(highIndicator, control::getIndicatorDisplay, control::isAnimated);

        initializeNodes(control);
        registerListeners(control);
        registerFeedbackListeners(control);
        registerPointerHandlers(control);
        registerKeyboardHandler(control);

        rebuildTicks();
        updateIndicatorTexts();
        updateLowFeedback();
        updateHighFeedback();
    }

    private void initializeNodes(RXRangeSlider control) {
        hitArea.setManaged(false);
        hitArea.setPickOnBounds(true);

        track.getStyleClass().add("track");
        fill.getStyleClass().add("fill");
        // The fill is pickable only when the band is draggable; otherwise clicks
        // on the filled region fall through to the track (nearest-thumb).
        fill.setMouseTransparent(!control.isRangeDraggable());

        low.setFill(control.getRippleFill());
        high.setFill(control.getRippleFill());
        // The indicators start hidden (set by the IndicatorAnimator); the child
        // list is established by rebuildTicks() in the constructor.
    }

    private void registerListeners(RXRangeSlider control) {
        disposer.registerListener(control.lowValueProperty(), () -> {
            low.getThumb().notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
            onValueChanged();
        });
        disposer.registerListener(control.highValueProperty(), () -> {
            high.getThumb().notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
            onValueChanged();
        });
        disposer.registerListener(control.minProperty(), this::onTickModelChanged);
        disposer.registerListener(control.maxProperty(), this::onTickModelChanged);
        disposer.registerListener(control.majorTickUnitProperty(), this::onTickModelChanged);
        disposer.registerListener(control.minorTickCountProperty(), this::onTickModelChanged);
        disposer.registerListener(control.orientationProperty(), control::requestLayout);
        disposer.registerListener(control.showTickMarksProperty(), this::onTickVisibilityChanged);
        disposer.registerListener(control.showTickLabelsProperty(), this::onTickVisibilityChanged);
        disposer.registerListener(control.labelFormatterProperty(), () -> {
            updateIndicatorTexts();
            rebuildTicks();
        });
    }

    private void registerFeedbackListeners(RXRangeSlider control) {
        disposer.registerEventHandler(low.getThumb(), MouseEvent.MOUSE_ENTERED, event -> {
            pointerInLow = true;
            updateLowFeedback();
        });
        disposer.registerEventHandler(low.getThumb(), MouseEvent.MOUSE_EXITED, event -> {
            pointerInLow = false;
            updateLowFeedback();
        });
        disposer.registerEventHandler(high.getThumb(), MouseEvent.MOUSE_ENTERED, event -> {
            pointerInHigh = true;
            updateHighFeedback();
        });
        disposer.registerEventHandler(high.getThumb(), MouseEvent.MOUSE_EXITED, event -> {
            pointerInHigh = false;
            updateHighFeedback();
        });
        disposer.registerListener(low.getThumb().pressedProperty(), this::updateLowFeedback);
        disposer.registerListener(high.getThumb().pressedProperty(), this::updateHighFeedback);
        disposer.registerListener(low.getThumb().focusedProperty(), () -> {
            if (low.getThumb().isFocused()) {
                activeThumbIsLow = true;
            }
            updateLowFeedback();
        });
        disposer.registerListener(high.getThumb().focusedProperty(), () -> {
            if (high.getThumb().isFocused()) {
                activeThumbIsLow = false;
            }
            updateHighFeedback();
        });
        disposer.registerListener(control.lowValueChangingProperty(), this::updateLowFeedback);
        disposer.registerListener(control.highValueChangingProperty(), this::updateHighFeedback);
        disposer.registerListener(control.stateOverlayEnabledProperty(), this::updateBothFeedback);
        disposer.registerListener(control.disabledProperty(), () -> {
            updateBothFeedback();
            if (control.isDisabled()) {
                // A disabled node may never see the release, so end any in-flight
                // gesture rather than strand an unreleased ripple or stuck flags.
                low.clearInk();
                high.clearInk();
                cancelGestures();
            }
        });
        disposer.registerListener(control.rippleFillProperty(), () -> {
            low.setFill(control.getRippleFill());
            high.setFill(control.getRippleFill());
        });
        disposer.registerListener(control.rippleEnabledProperty(), () -> {
            if (!control.isRippleEnabled()) {
                low.clearInk();
                high.clearInk();
            }
        });
        disposer.registerListener(control.indicatorDisplayProperty(), this::updateBothFeedback);
        disposer.registerListener(control.rangeDraggableProperty(), () -> {
            fill.setMouseTransparent(!control.isRangeDraggable());
            if (!control.isRangeDraggable() && bandDragging) {
                endBandDrag();
            }
        });
    }

    private void registerPointerHandlers(RXRangeSlider control) {
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);
        disposer.registerEventHandler(track, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(track, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);

        disposer.registerEventHandler(fill, MouseEvent.MOUSE_PRESSED, this::onBandPressed);
        disposer.registerEventHandler(fill, MouseEvent.MOUSE_DRAGGED, this::onBandDragged);
        disposer.registerEventHandler(fill, MouseEvent.MOUSE_RELEASED, this::onBandReleased);

        disposer.registerEventHandler(low.getThumb(), MouseEvent.MOUSE_PRESSED, event -> onThumbPressed(true, event));
        disposer.registerEventHandler(low.getThumb(), MouseEvent.MOUSE_DRAGGED, event -> onThumbDragged(true, event));
        disposer.registerEventHandler(low.getThumb(), MouseEvent.MOUSE_RELEASED, event -> onThumbReleased(true, event));
        disposer.registerEventHandler(high.getThumb(), MouseEvent.MOUSE_PRESSED, event -> onThumbPressed(false, event));
        disposer.registerEventHandler(high.getThumb(), MouseEvent.MOUSE_DRAGGED, event -> onThumbDragged(false, event));
        disposer.registerEventHandler(high.getThumb(), MouseEvent.MOUSE_RELEASED, event -> onThumbReleased(false, event));
    }

    private void registerKeyboardHandler(RXRangeSlider control) {
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Pointer interaction ====================

    private void onThumbPressed(boolean isLow, MouseEvent event) {
        RXRangeSlider control = getSkinnable();
        SliderThumb unit = isLow ? low : high;
        StackPane thumbNode = unit.getThumb();
        thumbNode.requestFocus();
        activeThumbIsLow = isLow;
        if (isLow) {
            control.setLowValueChanging(true);
        } else {
            control.setHighValueChanging(true);
        }
        dragging = true;
        draggingLow = isLow;
        dragStart = thumbNode.localToParent(event.getX(), event.getY());
        preDragValue = isLow ? control.getLowValue() : control.getHighValue();
        if (control.isRippleEnabled() && !control.isDisabled()) {
            unit.pressInk(thumbNode.getWidth() * HALF, thumbNode.getHeight() * HALF);
        }
        event.consume();
    }

    private void onThumbDragged(boolean isLow, MouseEvent event) {
        if (!dragging || draggingLow != isLow || dragStart == null) {
            return;
        }
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        StackPane thumbNode = (isLow ? low : high).getThumb();
        Point2D current = thumbNode.localToParent(event.getX(), event.getY());
        double mainDelta = vertical()
                ? -(current.getY() - dragStart.getY())
                : (current.getX() - dragStart.getX());
        double deltaFraction = mainDelta / trackLength;
        double newValue = preDragValue + deltaFraction * (control.getMax() - control.getMin());
        // No snap during the drag; the property cross-clamps so the values never
        // cross over.
        if (isLow) {
            control.setLowValue(newValue);
        } else {
            control.setHighValue(newValue);
        }
        event.consume();
    }

    private void onThumbReleased(boolean isLow, MouseEvent event) {
        (isLow ? low : high).releaseInk();
        if (!dragging || draggingLow != isLow) {
            return;
        }
        dragging = false;
        dragStart = null;
        RXRangeSlider control = getSkinnable();
        if (isLow) {
            control.setLowValueChanging(false);
            control.adjustLowValue(control.getLowValue());
        } else {
            control.setHighValueChanging(false);
            control.adjustHighValue(control.getHighValue());
        }
        event.consume();
    }

    private void onTrackPressed(MouseEvent event) {
        if (low.getThumb().isPressed() || high.getThumb().isPressed()) {
            return;
        }
        double value = valueFromPointer(event);
        if (Double.isNaN(value)) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        boolean nearerLow;
        if (control.getLowValue() == control.getHighValue()) {
            // Coincident thumbs: split by click direction so the pair can be
            // pulled apart (the nearest-thumb tie would always pick low, which
            // then caps at high and absorbs the click).
            nearerLow = value < control.getLowValue();
        } else {
            nearerLow = Math.abs(value - control.getLowValue()) <= Math.abs(value - control.getHighValue());
        }
        activeThumbIsLow = nearerLow;
        (nearerLow ? low : high).getThumb().requestFocus();
        moveActiveThumb(value);
        event.consume();
    }

    private void onTrackDragged(MouseEvent event) {
        if (low.getThumb().isPressed() || high.getThumb().isPressed()) {
            return;
        }
        double value = valueFromPointer(event);
        if (Double.isNaN(value)) {
            return;
        }
        moveActiveThumb(value);
        event.consume();
    }

    private double valueFromPointer(MouseEvent event) {
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return Double.NaN;
        }
        RXRangeSlider control = getSkinnable();
        Point2D point = ((Node) event.getSource()).localToParent(event.getX(), event.getY());
        double fraction = vertical()
                ? 1.0 - (point.getY() - track.getLayoutY()) / trackLength
                : (point.getX() - track.getLayoutX()) / trackLength;
        return SliderGeometry.fractionToValue(fraction, control.getMin(), control.getMax());
    }

    private void moveActiveThumb(double value) {
        RXRangeSlider control = getSkinnable();
        if (activeThumbIsLow) {
            control.adjustLowValue(value);
        } else {
            control.adjustHighValue(value);
        }
    }

    private boolean vertical() {
        return getSkinnable().getOrientation() == Orientation.VERTICAL;
    }

    private double trackMainLength() {
        return vertical() ? track.getHeight() : track.getWidth();
    }

    private void cancelGestures() {
        RXRangeSlider control = getSkinnable();
        dragging = false;
        dragStart = null;
        bandDragging = false;
        bandDragStart = null;
        control.setLowValueChanging(false);
        control.setHighValueChanging(false);
    }

    // ==================== Band drag ====================

    private void onBandPressed(MouseEvent event) {
        RXRangeSlider control = getSkinnable();
        if (!control.isRangeDraggable() || control.isDisabled()) {
            return;
        }
        bandDragging = true;
        bandDragStart = fill.localToParent(event.getX(), event.getY());
        bandPreLow = control.getLowValue();
        bandPreHigh = control.getHighValue();
        control.setLowValueChanging(true);
        control.setHighValueChanging(true);
        event.consume();
    }

    private void onBandDragged(MouseEvent event) {
        if (!bandDragging || bandDragStart == null) {
            return;
        }
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        double min = control.getMin();
        double max = control.getMax();
        Point2D current = fill.localToParent(event.getX(), event.getY());
        double mainDelta = vertical()
                ? -(current.getY() - bandDragStart.getY())
                : (current.getX() - bandDragStart.getX());
        double deltaValue = mainDelta / trackLength * (max - min);
        double span = bandPreHigh - bandPreLow;
        double newLow = bandPreLow + deltaValue;
        double newHigh = bandPreHigh + deltaValue;
        // Keep the whole band inside [min, max], preserving its span.
        if (newLow < min) {
            newLow = min;
            newHigh = min + span;
        }
        if (newHigh > max) {
            newHigh = max;
            newLow = max - span;
        }
        // Set in the direction of travel so neither value cross-clamps against
        // the other's stale position mid-update.
        if (deltaValue >= 0.0) {
            control.setHighValue(newHigh);
            control.setLowValue(newLow);
        } else {
            control.setLowValue(newLow);
            control.setHighValue(newHigh);
        }
        event.consume();
    }

    private void onBandReleased(MouseEvent event) {
        if (!bandDragging) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        endBandDrag();
        control.adjustLowValue(control.getLowValue());
        control.adjustHighValue(control.getHighValue());
        event.consume();
    }

    private void endBandDrag() {
        bandDragging = false;
        bandDragStart = null;
        RXRangeSlider control = getSkinnable();
        control.setLowValueChanging(false);
        control.setHighValueChanging(false);
    }

    // ==================== Keyboard interaction ====================

    private void onKeyPressed(KeyEvent event) {
        RXRangeSlider control = getSkinnable();
        // Route to the active thumb, kept in sync with which thumb was last
        // pressed / focused / track-targeted. The handler only fires when the
        // slider has focus (a thumb is the focus owner), so the active thumb is
        // the focused one.
        boolean isLow = activeThumbIsLow;
        KeyCode code = event.getCode();
        boolean horizontal = control.getOrientation() != Orientation.VERTICAL;
        boolean rtl = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;

        if (code == KeyCode.HOME) {
            adjustThumb(isLow, control.getMin());
            event.consume();
        } else if (code == KeyCode.END) {
            adjustThumb(isLow, control.getMax());
            event.consume();
        } else if (horizontal && (code == KeyCode.LEFT || code == KeyCode.KP_LEFT)) {
            stepThumb(isLow, rtl);
            event.consume();
        } else if (horizontal && (code == KeyCode.RIGHT || code == KeyCode.KP_RIGHT)) {
            stepThumb(isLow, !rtl);
            event.consume();
        } else if (!horizontal && (code == KeyCode.UP || code == KeyCode.KP_UP)) {
            stepThumb(isLow, true);
            event.consume();
        } else if (!horizontal && (code == KeyCode.DOWN || code == KeyCode.KP_DOWN)) {
            stepThumb(isLow, false);
            event.consume();
        }
    }

    private void stepThumb(boolean isLow, boolean increment) {
        RXRangeSlider control = getSkinnable();
        double step = control.isSnapToTicks() ? snapAwareStep() : control.getBlockIncrement();
        double current = isLow ? control.getLowValue() : control.getHighValue();
        adjustThumb(isLow, current + (increment ? step : -step));
    }

    private void adjustThumb(boolean isLow, double value) {
        RXRangeSlider control = getSkinnable();
        if (isLow) {
            control.adjustLowValue(value);
        } else {
            control.adjustHighValue(value);
        }
    }

    private double snapAwareStep() {
        RXRangeSlider control = getSkinnable();
        double tickSpacing = SliderGeometry.minorSpacing(control.getMajorTickUnit(),
                control.getMinorTickCount());
        double block = control.getBlockIncrement();
        return block > 0.0 && block < tickSpacing ? tickSpacing : block;
    }

    // ==================== Feedback (halo + indicator) ====================

    private void onValueChanged() {
        updateIndicatorTexts();
        updateLowFeedback();
        updateHighFeedback();
        getSkinnable().requestLayout();
    }

    private void updateBothFeedback() {
        updateLowFeedback();
        updateHighFeedback();
    }

    private void updateLowFeedback() {
        RXRangeSlider control = getSkinnable();
        boolean enabled = control.isStateOverlayEnabled() && !control.isDisabled();
        StackPane thumbNode = low.getThumb();
        low.setState(enabled && pointerInLow, enabled && thumbNode.isFocused(),
                enabled && thumbNode.isPressed(), enabled && control.isLowValueChanging());
        updateIndicatorVisibility();
    }

    private void updateHighFeedback() {
        RXRangeSlider control = getSkinnable();
        boolean enabled = control.isStateOverlayEnabled() && !control.isDisabled();
        StackPane thumbNode = high.getThumb();
        high.setState(enabled && pointerInHigh, enabled && thumbNode.isFocused(),
                enabled && thumbNode.isPressed(), enabled && control.isHighValueChanging());
        updateIndicatorVisibility();
    }

    private void updateIndicatorVisibility() {
        // The merged bubble couples both thumbs, so both thumbs' feedback updates
        // route here. While merged the low indicator is the shared bubble (shown
        // when either thumb is active) and the high indicator is force-hidden,
        // overriding even the ALWAYS policy.
        if (merged) {
            lowAnimator.update(lowActive() || highActive());
            highAnimator.update(false, true);
        } else {
            lowAnimator.update(lowActive());
            highAnimator.update(highActive());
        }
    }

    private boolean lowActive() {
        StackPane thumbNode = low.getThumb();
        return getSkinnable().isLowValueChanging() || thumbNode.isPressed() || thumbNode.isFocused();
    }

    private boolean highActive() {
        StackPane thumbNode = high.getThumb();
        return getSkinnable().isHighValueChanging() || thumbNode.isPressed() || thumbNode.isFocused();
    }

    private void updateIndicatorTexts() {
        lowIndicator.setText(formatValue(getSkinnable().getLowValue()));
        highIndicator.setText(formatValue(getSkinnable().getHighValue()));
    }

    private String formatValue(double value) {
        StringConverter<Number> formatter = getSkinnable().getLabelFormatter();
        return formatter != null ? formatter.toString(value) : Long.toString(Math.round(value));
    }

    private RXSliderIndicatorPosition positionOrDefault() {
        RXSliderIndicatorPosition position = getSkinnable().getIndicatorPosition();
        return position == null ? RXSlider.DEFAULT_INDICATOR_POSITION : position;
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
        RXRangeSlider control = getSkinnable();
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
        getChildren().setAll(assembleChildren());
        updateTickVisibility();
    }

    private void updateTickVisibility() {
        RXRangeSlider control = getSkinnable();
        boolean showMarks = control.isShowTickMarks();
        boolean showLabels = control.isShowTickLabels();
        for (Region mark : tickMarks) {
            mark.setVisible(showMarks);
        }
        for (Label label : tickLabels) {
            label.setVisible(showLabels);
        }
    }

    private List<Node> assembleChildren() {
        List<Node> children = new ArrayList<>();
        children.add(hitArea);
        children.add(track);
        children.add(fill);
        children.addAll(tickMarks);
        children.addAll(tickLabels);
        children.add(low.getHalo());
        children.add(high.getHalo());
        children.add(low.getThumb());
        children.add(high.getThumb());
        children.add(lowIndicator);
        children.add(highIndicator);
        return children;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double thumbSize = snapSizeX(low.getThumb().prefWidth(-1));
        double thickness = snapSizeY(track.prefHeight(-1));
        hitArea.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));

        boolean vertical = vertical();
        SliderAxis axis = new SliderAxis(vertical, x, y, Math.max(0.0, w), Math.max(0.0, h));
        double mainLength = axis.mainLength();
        double crossExtent = vertical ? Math.max(0.0, w) : Math.max(0.0, h);
        double crossBar = Math.max(0.0, crossExtent - labelBandExtent());
        double barCrossCenter = crossBar * HALF;
        double trackMLo = thumbSize * HALF;
        double trackLengthM = mainLength - thumbSize;

        if (mainLength <= 0.0 || trackLengthM <= 0.0) {
            resetLayout(axis, thumbSize, barCrossCenter);
            return;
        }

        RXRangeSlider control = getSkinnable();
        double lowF = SliderGeometry.valueToFraction(control.getLowValue(), control.getMin(), control.getMax());
        double highF = SliderGeometry.valueToFraction(control.getHighValue(), control.getMin(), control.getMax());
        double lowM = trackMLo + trackLengthM * lowF;
        double highM = trackMLo + trackLengthM * highF;
        double trackCLo = barCrossCenter - thickness * HALF;
        double trackCHi = barCrossCenter + thickness * HALF;

        place(axis, track, trackMLo, trackMLo + trackLengthM, trackCLo, trackCHi);
        place(axis, fill, lowM, Math.max(lowM, highM), trackCLo, trackCHi);
        placeThumb(axis, low.getThumb(), lowM, barCrossCenter, thumbSize);
        placeThumb(axis, high.getThumb(), highM, barCrossCenter, thumbSize);

        layoutThumbFeedback(axis, low, lowM, barCrossCenter, thumbSize);
        layoutThumbFeedback(axis, high, highM, barCrossCenter, thumbSize);
        layoutIndicators(axis, lowM, highM, barCrossCenter, thumbSize);
        layoutTicks(axis, trackMLo, trackLengthM, barCrossCenter, crossBar);
    }

    private void place(SliderAxis axis, Region node, double mLo, double mHi, double cLo, double cHi) {
        node.resizeRelocate(
                snapPositionX(axis.rectX(mLo, mHi, cLo, cHi)),
                snapPositionY(axis.rectY(mLo, mHi, cLo, cHi)),
                snapSizeX(axis.rectW(mHi - mLo, cHi - cLo)),
                snapSizeY(axis.rectH(mHi - mLo, cHi - cLo)));
    }

    private void placeThumb(SliderAxis axis, Region thumbNode, double thumbM,
                            double barCrossCenter, double thumbSize) {
        place(axis, thumbNode, thumbM - thumbSize * HALF, thumbM + thumbSize * HALF,
                barCrossCenter - thumbSize * HALF, barCrossCenter + thumbSize * HALF);
    }

    private void layoutThumbFeedback(SliderAxis axis, SliderThumb unit, double thumbM,
                                     double barCrossCenter, double thumbSize) {
        StateLayer halo = unit.getHalo();
        double diameter = snapSizeX(halo.prefWidth(-1));
        halo.resize(diameter, diameter);
        double centerX = axis.pointX(thumbM, barCrossCenter);
        double centerY = axis.pointY(thumbM, barCrossCenter);
        halo.relocate(snapPositionX(centerX - diameter * HALF), snapPositionY(centerY - diameter * HALF));

        RippleLayer ink = unit.getInk();
        ink.resizeRelocate(0.0, 0.0, thumbSize, thumbSize);
        ink.updateClipFor(unit.getThumb(), thumbSize, thumbSize);
    }

    private void layoutIndicators(SliderAxis axis, double lowM, double highM,
                                  double barCrossCenter, double thumbSize) {
        boolean negativeSide = positionOrDefault() != RXSliderIndicatorPosition.BELOW;
        boolean vertical = axis.isVertical();
        RXRangeSlider control = getSkinnable();
        // Measure each bubble at its own value, then decide whether they overlap.
        lowIndicator.setText(formatValue(control.getLowValue()));
        highIndicator.setText(formatValue(control.getHighValue()));
        double lowMain = vertical ? snapSizeY(lowIndicator.prefHeight(-1)) : snapSizeX(lowIndicator.prefWidth(-1));
        double highMain = vertical ? snapSizeY(highIndicator.prefHeight(-1)) : snapSizeX(highIndicator.prefWidth(-1));
        boolean overlap = Math.abs(highM - lowM) < (lowMain + highMain) * HALF + MERGE_GAP;
        setMerged(overlap);
        if (overlap) {
            // The low indicator becomes the shared "low – high" bubble, centered
            // between the thumbs; the high indicator is hidden by its animator.
            lowIndicator.setText(formatValue(control.getLowValue()) + EN_DASH
                    + formatValue(control.getHighValue()));
            positionIndicator(axis, lowIndicator, (lowM + highM) * HALF, barCrossCenter, thumbSize, negativeSide);
        } else {
            positionIndicator(axis, lowIndicator, lowM, barCrossCenter, thumbSize, negativeSide);
            positionIndicator(axis, highIndicator, highM, barCrossCenter, thumbSize, negativeSide);
        }
    }

    private void positionIndicator(SliderAxis axis, SliderValueIndicator indicator, double centerM,
                                   double barCrossCenter, double thumbSize, boolean negativeSide) {
        boolean vertical = axis.isVertical();
        indicator.setCaretVisible(!vertical);
        if (!vertical) {
            indicator.setCaretBelow(negativeSide);
        }
        double bubbleW = snapSizeX(indicator.prefWidth(-1));
        double bubbleH = snapSizeY(indicator.prefHeight(-1));
        indicator.resize(bubbleW, bubbleH);
        double bubbleMain = vertical ? bubbleH : bubbleW;
        double bubbleCross = vertical ? bubbleW : bubbleH;
        double mainLo = RXMath.clamp(centerM - bubbleMain * HALF, 0.0, Math.max(0.0, axis.mainLength() - bubbleMain));
        double thumbCrossLo = barCrossCenter - thumbSize * HALF;
        double thumbCrossHi = barCrossCenter + thumbSize * HALF;
        double crossLo = negativeSide
                ? (thumbCrossLo - INDICATOR_GAP - bubbleCross)
                : (thumbCrossHi + INDICATOR_GAP);
        indicator.relocate(
                snapPositionX(axis.rectX(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)),
                snapPositionY(axis.rectY(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)));
    }

    private void setMerged(boolean value) {
        if (merged != value) {
            merged = value;
            lowIndicator.pseudoClassStateChanged(MERGED_PSEUDO, value);
            // Re-apply the indicator visibility for the new merge state.
            updateIndicatorVisibility();
        }
    }

    private void layoutTicks(SliderAxis axis, double trackMLo, double trackLengthM,
                             double barCrossCenter, double crossBar) {
        RXRangeSlider control = getSkinnable();
        SliderTickLayout.layoutTicks(axis, trackMLo, trackLengthM, barCrossCenter, crossBar,
                TICK_LABEL_GAP, control.getMin(), control.getMax(),
                control.isShowTickMarks(), tickMarks, tickMarkValues,
                control.isShowTickLabels(), tickLabels, tickLabelValues, snapper);
    }

    private double labelBandExtent() {
        return SliderTickLayout.labelBandExtent(getSkinnable().isShowTickLabels(),
                vertical(), tickLabels, TICK_LABEL_GAP);
    }

    private void resetLayout(SliderAxis axis, double thumbSize, double barCrossCenter) {
        double trackMLo = thumbSize * HALF;
        place(axis, track, trackMLo, trackMLo, barCrossCenter, barCrossCenter);
        place(axis, fill, trackMLo, trackMLo, barCrossCenter, barCrossCenter);
        placeThumb(axis, low.getThumb(), trackMLo, barCrossCenter, thumbSize);
        placeThumb(axis, high.getThumb(), trackMLo, barCrossCenter, thumbSize);
        layoutThumbFeedback(axis, low, trackMLo, barCrossCenter, thumbSize);
        layoutThumbFeedback(axis, high, trackMLo, barCrossCenter, thumbSize);
        layoutIndicators(axis, trackMLo, trackMLo, barCrossCenter, thumbSize);
        layoutTicks(axis, trackMLo, 0.0, barCrossCenter, 0.0);
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
        return 2.0 * low.getThumb().prefWidth(-1) + low.getThumb().minWidth(-1);
    }

    private double crossThickness() {
        double bar = Math.max(Math.max(track.prefHeight(-1), low.getThumb().prefHeight(-1)),
                low.getHalo().prefHeight(-1));
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
        lowAnimator.dispose();
        highAnimator.dispose();
        low.dispose();
        high.dispose();
    }
}
