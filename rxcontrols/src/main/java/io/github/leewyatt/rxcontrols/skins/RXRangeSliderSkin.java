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
import io.github.leewyatt.rxcontrols.internal.slider.SliderValueText;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
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
 * keys / HOME / END move the focused thumb; keys the slider does not handle are
 * left unconsumed. A runtime tick toggle keeps both thumbs and the range bar. The
 * {@code *Changing} flags flip during a pointer drag and around a key press that
 * crosses the other value.</p>
 *
 * <p>Focus shows (the focused thumb's halo and indicator) only once the keyboard is
 * in use: after a key reaches a focused thumb, until a pointer press or the focus
 * leaving both thumbs. A pointer click therefore leaves no lingering focus cue.</p>
 *
 * <p>With no gap a drag or key may carry a value past the other one; the values
 * stay sorted and the thumb that now holds the dragged value takes focus, the
 * active halo and the top of the stack. The last used thumb stays on top, so a
 * press on coincident thumbs picks it.</p>
 */
public class RXRangeSliderSkin extends RXSkinBase<RXRangeSlider> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = SliderMetrics.DEFAULT_PREF_LENGTH;
    private static final double HALF = 0.5;
    private static final double INDICATOR_GAP = SliderMetrics.INDICATOR_GAP;
    private static final double TICK_LABEL_GAP = SliderMetrics.TICK_LABEL_GAP;
    private static final double MERGE_GAP = 4.0;
    private static final double GAP_TOLERANCE = 1e-9;
    private static final String EN_DASH = " – ";

    private static final PseudoClass LOWER_PSEUDO = PseudoClass.getPseudoClass("lower");
    private static final PseudoClass UPPER_PSEUDO = PseudoClass.getPseudoClass("upper");
    private static final PseudoClass MERGED_PSEUDO = PseudoClass.getPseudoClass("merged");
    private static final PseudoClass RANGE_DRAGGING_PSEUDO = PseudoClass.getPseudoClass("range-dragging");

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

    // A thumb or track drag keeps three things apart: the node that took the press
    // (it keeps receiving the drag and release), the value being dragged, and the role
    // that value holds once sorted against the other value, which may change as the
    // drag crosses it.
    private boolean thumbGesture;
    private boolean gestureOwnerIsLow;
    private boolean trackGesture;
    private boolean trackDragging;
    private boolean handOffDecided;
    private Point2D dragStart;
    private double draggedStart;
    private double otherToken;
    private boolean pointerInLow;
    private boolean pointerInHigh;
    private boolean keyboardFocus;
    // The role of the active thumb: the one being dragged, else the last used.
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
        applyLayering();
        updateLowFeedback();
        updateHighFeedback();
    }

    private void initializeNodes(RXRangeSlider control) {
        hitArea.setManaged(false);
        hitArea.setPickOnBounds(true);

        track.getStyleClass().add("track");
        fill.getStyleClass().add("fill");
        // The hit area under the bars takes every press on the content area.
        track.setMouseTransparent(true);
        // The fill is pickable only when the band is draggable; otherwise clicks
        // on the filled region fall through to the hit area (nearest thumb).
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
            if (low.getThumb().isFocused() && !activeThumbIsLow) {
                activeThumbIsLow = true;
                applyLayering();
            }
            onThumbFocusChanged();
        });
        disposer.registerListener(high.getThumb().focusedProperty(), () -> {
            if (high.getThumb().isFocused() && activeThumbIsLow) {
                activeThumbIsLow = false;
                applyLayering();
            }
            onThumbFocusChanged();
        });
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, event -> setKeyboardFocus(true));
        disposer.registerEventFilter(control, KeyEvent.KEY_RELEASED, event -> setKeyboardFocus(true));
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, event -> setKeyboardFocus(false));
        disposer.registerListener(control.lowValueChangingProperty(), this::updateLowFeedback);
        disposer.registerListener(control.highValueChangingProperty(), this::updateHighFeedback);
        disposer.registerListener(control.stateOverlayEnabledProperty(), this::updateBothFeedback);
        disposer.registerListener(control.disabledProperty(), () -> {
            updateBothFeedback();
            if (control.isDisabled()) {
                // A disabled node may never see the release, so end any in-flight
                // gesture rather than strand an unreleased ripple or stuck flags.
                endAllGestures();
                low.clearInk();
                high.clearInk();
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
            if (!control.isRangeDraggable()) {
                endBandDrag();
            }
        });
    }

    private void registerPointerHandlers(RXRangeSlider control) {
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_RELEASED, this::onTrackReleased);

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
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        StackPane thumbNode = unit(isLow).getThumb();
        thumbGesture = true;
        gestureOwnerIsLow = isLow;
        handOffDecided = false;
        dragStart = thumbNode.localToParent(event.getX(), event.getY());
        draggedStart = valueOf(isLow);
        otherToken = valueOf(!isLow);
        setChanging(isLow);
        setActiveRole(isLow);
        if (control.isRippleEnabled() && !control.isDisabled()) {
            unit(isLow).pressInk(thumbNode.getWidth() * HALF, thumbNode.getHeight() * HALF);
        }
        updateBothFeedback();
        event.consume();
    }

    private void onThumbDragged(boolean isLow, MouseEvent event) {
        if (!thumbGesture || gestureOwnerIsLow != isLow || dragStart == null) {
            return;
        }
        double trackLength = trackMainLength();
        if (trackLength <= 0.0) {
            return;
        }
        RXRangeSlider control = getSkinnable();
        // The press target keeps the gesture, wherever its role has moved.
        Point2D current = unit(isLow).getThumb().localToParent(event.getX(), event.getY());
        double mainDelta = vertical()
                ? -(current.getY() - dragStart.getY())
                : (current.getX() - dragStart.getX());
        double delta = mainDelta / trackLength * (control.getMax() - control.getMin());
        if (!handOffDecided && delta != 0.0) {
            handOffDecided = true;
            handOffIfBlocked(delta);
        }
        // No snap during the drag; the release snaps.
        dragTo(draggedStart + delta);
        event.consume();
    }

    private void onThumbReleased(boolean isLow, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!thumbGesture || gestureOwnerIsLow != isLow) {
            unit(isLow).releaseInk();
            return;
        }
        endPointerGesture();
        event.consume();
    }

    private void onTrackPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY
                || low.getThumb().isPressed() || high.getThumb().isPressed()) {
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
        setActiveRole(nearerLow);
        // A press alone is a discrete commit; only a following drag flips a flag.
        adjustThumb(nearerLow, value);
        trackGesture = true;
        trackDragging = false;
        event.consume();
    }

    private void onTrackDragged(MouseEvent event) {
        if (!trackGesture || !event.isPrimaryButtonDown()) {
            return;
        }
        double value = valueFromPointer(event);
        if (Double.isNaN(value)) {
            return;
        }
        if (!trackDragging) {
            trackDragging = true;
            otherToken = valueOf(!activeThumbIsLow);
            setChanging(activeThumbIsLow);
            updateBothFeedback();
        }
        dragTo(value);
        event.consume();
    }

    private void onTrackReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !trackGesture) {
            return;
        }
        endPointerGesture();
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

    private boolean vertical() {
        return getSkinnable().getOrientation() == Orientation.VERTICAL;
    }

    private double trackMainLength() {
        return vertical() ? track.getHeight() : track.getWidth();
    }

    // ==================== Crossing ====================

    // Moves the dragged value to the candidate. With no gap the pair is re-sorted, so
    // the dragged value passes the other one and its role flips; a candidate equal to
    // the other value keeps the role. With a gap the active role stays and the
    // property holds the gap.
    private void dragTo(double candidate) {
        RXRangeSlider control = getSkinnable();
        double value = RXMath.clamp(candidate, control.getMin(), control.getMax());
        if (gap() > 0.0) {
            if (activeThumbIsLow) {
                control.setLowValue(value);
            } else {
                control.setHighValue(value);
            }
            return;
        }
        boolean lowRole = value < otherToken || (value == otherToken && activeThumbIsLow);
        if (lowRole != activeThumbIsLow) {
            // Raise the second flag before the values move.
            setChanging(lowRole);
            moveInk(lowRole);
            setActiveRole(lowRole);
            updateBothFeedback();
        }
        writePair(lowRole ? value : otherToken, lowRole ? otherToken : value);
    }

    // With a gap the thumbs never cross. When they are drawn over each other, already
    // held at the gap, and the first move of the pressed thumb heads toward the other
    // one, the press moves to the other thumb, which can travel that way; it continues
    // from its own value. A thumb that still has room moves itself and stops at the gap.
    private void handOffIfBlocked(double delta) {
        if (gap() <= 0.0 || !thumbsOverlap() || !heldAtGap()) {
            return;
        }
        boolean towardOther = activeThumbIsLow ? delta > 0.0 : delta < 0.0;
        if (!towardOther) {
            return;
        }
        boolean lowRole = !activeThumbIsLow;
        double pressedValue = draggedStart;
        draggedStart = otherToken;
        otherToken = pressedValue;
        setChanging(lowRole);
        moveInk(lowRole);
        setActiveRole(lowRole);
        updateBothFeedback();
    }

    private boolean heldAtGap() {
        RXRangeSlider control = getSkinnable();
        double room = control.getHighValue() - control.getLowValue() - gap();
        return room <= (control.getMax() - control.getMin()) * GAP_TOLERANCE;
    }

    private boolean thumbsOverlap() {
        RXRangeSlider control = getSkinnable();
        double span = control.getMax() - control.getMin();
        if (!(span > 0.0)) {
            return false;
        }
        double distance = Math.abs(control.getHighValue() - control.getLowValue()) / span * trackMainLength();
        return distance < low.getThumb().getWidth();
    }

    // Writes an ordered pair: moving up, the high value goes first, moving down the low
    // value does, so every intermediate state keeps its order and gap and nothing is
    // clamped on the way.
    private void writePair(double lowValue, double highValue) {
        RXRangeSlider control = getSkinnable();
        if (lowValue > control.getLowValue()) {
            control.setHighValue(highValue);
            control.setLowValue(lowValue);
        } else {
            control.setLowValue(lowValue);
            control.setHighValue(highValue);
        }
    }

    // Ends a thumb or track gesture: snaps the dragged value, sorts it against the other
    // value and writes the final pair, and only then clears the flags, so a commit on
    // their falling edge reads the final values.
    private void endPointerGesture() {
        boolean dragged = thumbGesture || trackDragging;
        boolean wasTrack = trackGesture;
        thumbGesture = false;
        trackGesture = false;
        trackDragging = false;
        dragStart = null;
        if (!dragged) {
            if (wasTrack) {
                updateBothFeedback();
            }
            return;
        }
        RXRangeSlider control = getSkinnable();
        double draggedValue = valueOf(activeThumbIsLow);
        if (gap() > 0.0 || !(control.getMax() > control.getMin())) {
            adjustThumb(activeThumbIsLow, draggedValue);
        } else {
            double snapped = snap(draggedValue);
            boolean lowRole = snapped < otherToken || (snapped == otherToken && activeThumbIsLow);
            if (lowRole != activeThumbIsLow) {
                setChanging(lowRole);
                setActiveRole(lowRole);
            }
            writePair(lowRole ? snapped : otherToken, lowRole ? otherToken : snapped);
        }
        low.releaseInk();
        high.releaseInk();
        control.setLowValueChanging(false);
        control.setHighValueChanging(false);
        updateBothFeedback();
    }

    private void endAllGestures() {
        endBandDrag();
        endPointerGesture();
    }

    private void setActiveRole(boolean isLow) {
        if (activeThumbIsLow != isLow) {
            activeThumbIsLow = isLow;
            applyLayering();
        }
        unit(isLow).getThumb().requestFocus();
    }

    private void setChanging(boolean isLow) {
        RXRangeSlider control = getSkinnable();
        if (isLow) {
            control.setLowValueChanging(true);
        } else {
            control.setHighValueChanging(true);
        }
    }

    // Moves the press ink to the thumb that now holds the dragged value.
    private void moveInk(boolean toLow) {
        RXRangeSlider control = getSkinnable();
        unit(!toLow).releaseInk();
        if (control.isRippleEnabled() && !control.isDisabled()) {
            StackPane thumbNode = unit(toLow).getThumb();
            unit(toLow).pressInk(thumbNode.getWidth() * HALF, thumbNode.getHeight() * HALF);
        }
    }

    // Both halos lie below both thumbs, so a halo never covers the other thumb; the
    // active thumb and halo stack above the other ones, and the value bubbles above
    // every thumb. Ticks and bars keep the default order 0.
    private void applyLayering() {
        SliderThumb top = activeThumbIsLow ? low : high;
        SliderThumb bottom = activeThumbIsLow ? high : low;
        bottom.getHalo().setViewOrder(-1.0);
        top.getHalo().setViewOrder(-2.0);
        bottom.getThumb().setViewOrder(-3.0);
        top.getThumb().setViewOrder(-4.0);
        lowIndicator.setViewOrder(-5.0);
        highIndicator.setViewOrder(-5.0);
    }

    private SliderThumb unit(boolean isLow) {
        return isLow ? low : high;
    }

    private double valueOf(boolean isLow) {
        RXRangeSlider control = getSkinnable();
        return isLow ? control.getLowValue() : control.getHighValue();
    }

    private double gap() {
        return RXMath.sanitizeFiniteNonNegative(getSkinnable().getMinGap());
    }

    private double snap(double value) {
        RXRangeSlider control = getSkinnable();
        return SliderGeometry.snap(value, control.getMin(), control.getMax(),
                control.getMajorTickUnit(), control.getMinorTickCount(), control.isSnapToTicks());
    }

    // ==================== Band drag ====================

    private void onBandPressed(MouseEvent event) {
        RXRangeSlider control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY || !control.isRangeDraggable() || control.isDisabled()) {
            return;
        }
        bandDragging = true;
        bandDragStart = fill.localToParent(event.getX(), event.getY());
        bandPreLow = control.getLowValue();
        bandPreHigh = control.getHighValue();
        // On the control, so the drag cursor holds when the pointer runs off the fill.
        control.pseudoClassStateChanged(RANGE_DRAGGING_PSEUDO, true);
        control.setLowValueChanging(true);
        control.setHighValueChanging(true);
        updateBothFeedback();
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
        writePair(newLow, newHigh);
        event.consume();
    }

    private void onBandReleased(MouseEvent event) {
        if (!bandDragging || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        endBandDrag();
        event.consume();
    }

    // Ends a band drag: snaps both values first, then clears the flags. The low value
    // snaps again once the high value has settled, since the gap may have held it back
    // against the unsnapped high value. A snap that would break the gap is not taken.
    private void endBandDrag() {
        if (!bandDragging) {
            return;
        }
        bandDragging = false;
        bandDragStart = null;
        RXRangeSlider control = getSkinnable();
        control.pseudoClassStateChanged(RANGE_DRAGGING_PSEUDO, false);
        control.adjustLowValue(control.getLowValue());
        control.adjustHighValue(control.getHighValue());
        control.adjustLowValue(control.getLowValue());
        control.setLowValueChanging(false);
        control.setHighValueChanging(false);
        updateBothFeedback();
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
            commitKey(isLow, control.getMin());
            event.consume();
        } else if (code == KeyCode.END) {
            commitKey(isLow, control.getMax());
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
        commitKey(isLow, current + (increment ? step : -step));
    }

    // A key commit: with no gap a value past the other one takes the other role and
    // focus follows it; a value equal to the other keeps its role. A crossing key
    // changes both values, so both flags bracket the two writes and their falling edge
    // sees only the final pair.
    private void commitKey(boolean isLow, double value) {
        RXRangeSlider control = getSkinnable();
        if (gap() > 0.0 || !(control.getMax() > control.getMin())) {
            adjustThumb(isLow, value);
            return;
        }
        double snapped = snap(value);
        double other = valueOf(!isLow);
        boolean lowRole = snapped < other || (snapped == other && isLow);
        if (lowRole == isLow) {
            writePair(lowRole ? snapped : other, lowRole ? other : snapped);
            return;
        }
        control.setLowValueChanging(true);
        control.setHighValueChanging(true);
        setActiveRole(lowRole);
        writePair(lowRole ? snapped : other, lowRole ? other : snapped);
        control.setLowValueChanging(false);
        control.setHighValueChanging(false);
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
        updateFilledTicks();
        getSkinnable().requestLayout();
    }

    private void updateFilledTicks() {
        RXRangeSlider control = getSkinnable();
        SliderTickLayout.markFilled(tickMarks, tickMarkValues, control.getLowValue(), control.getHighValue());
    }

    private void updateBothFeedback() {
        updateLowFeedback();
        updateHighFeedback();
    }

    // Focus passing between the thumbs (Tab, a crossing) keeps the keyboard mode; a
    // focus leaving the window keeps the owner, so its cue returns with it.
    private void onThumbFocusChanged() {
        Scene scene = getSkinnable().getScene();
        Node owner = scene == null ? null : scene.getFocusOwner();
        if (owner != low.getThumb() && owner != high.getThumb()) {
            keyboardFocus = false;
        }
        updateBothFeedback();
    }

    private void setKeyboardFocus(boolean value) {
        if (keyboardFocus != value) {
            keyboardFocus = value;
            updateBothFeedback();
        }
    }

    private boolean focusVisible(SliderThumb unit) {
        return keyboardFocus && unit.getThumb().isFocused();
    }

    private void updateLowFeedback() {
        RXRangeSlider control = getSkinnable();
        boolean enabled = control.isStateOverlayEnabled() && !control.isDisabled();
        low.setState(enabled && pointerInLow, enabled && focusVisible(low),
                enabled && thumbGesture && activeThumbIsLow, enabled && dragsRole(true));
        updateIndicatorVisibility();
    }

    private void updateHighFeedback() {
        RXRangeSlider control = getSkinnable();
        boolean enabled = control.isStateOverlayEnabled() && !control.isDisabled();
        high.setState(enabled && pointerInHigh, enabled && focusVisible(high),
                enabled && thumbGesture && !activeThumbIsLow, enabled && dragsRole(false));
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

    // The halo and bubble follow the role the dragged value holds, not the press
    // target or the changing flags, which both stay up across a crossing.
    private boolean dragsRole(boolean isLow) {
        return bandDragging || ((thumbGesture || trackDragging) && activeThumbIsLow == isLow);
    }

    private boolean lowActive() {
        return dragsRole(true) || focusVisible(low);
    }

    private boolean highActive() {
        return dragsRole(false) || focusVisible(high);
    }

    private void updateIndicatorTexts() {
        lowIndicator.setText(formatValue(getSkinnable().getLowValue()));
        highIndicator.setText(formatValue(getSkinnable().getHighValue()));
    }

    private String formatValue(double value) {
        RXRangeSlider control = getSkinnable();
        StringConverter<Number> formatter = control.getLabelFormatter();
        return formatter != null
                ? formatter.toString(value)
                : SliderValueText.format(value, control.getMin(), control.getMax(), control.getMajorTickUnit());
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
        updateFilledTicks();
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
        double fillThickness = fillThickness(thickness);
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
        place(axis, fill, lowM, Math.max(lowM, highM),
                barCrossCenter - fillThickness * HALF, barCrossCenter + fillThickness * HALF);
        placeThumb(axis, low.getThumb(), lowM, barCrossCenter, thumbSize);
        placeThumb(axis, high.getThumb(), highM, barCrossCenter, thumbSize);

        layoutThumbFeedback(axis, low, lowM, barCrossCenter, thumbSize);
        layoutThumbFeedback(axis, high, highM, barCrossCenter, thumbSize);
        layoutIndicators(axis, lowM, highM, barCrossCenter, thumbSize);
        layoutTicks(axis, trackMLo, trackLengthM, barCrossCenter, crossBar);
    }

    // The fill may be thicker than the track; with no size of its own it matches it.
    private double fillThickness(double trackThickness) {
        double own = snapSizeY(fill.prefHeight(-1));
        return own > 0.0 ? own : trackThickness;
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
        boolean startSide = positionOrDefault() != RXSliderIndicatorPosition.END;
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
            positionIndicator(axis, lowIndicator, (lowM + highM) * HALF, barCrossCenter, thumbSize, startSide);
        } else {
            positionIndicator(axis, lowIndicator, lowM, barCrossCenter, thumbSize, startSide);
            positionIndicator(axis, highIndicator, highM, barCrossCenter, thumbSize, startSide);
        }
    }

    private void positionIndicator(SliderAxis axis, SliderValueIndicator indicator, double centerM,
                                   double barCrossCenter, double thumbSize, boolean startSide) {
        boolean vertical = axis.isVertical();
        double bubbleW = snapSizeX(indicator.prefWidth(-1));
        double bubbleH = snapSizeY(indicator.prefHeight(-1));
        indicator.resize(bubbleW, bubbleH);
        double bubbleMain = vertical ? bubbleH : bubbleW;
        double bubbleCross = vertical ? bubbleW : bubbleH;
        // The bubble stays inside the slider, except near an end, where it overflows
        // just enough for its caret, kept clear of the rounded corners, to reach the target.
        double reach = indicator.caretReach(vertical, startSide);
        double mainMin = Math.min(0.0, centerM - reach);
        double mainMax = Math.max(axis.mainLength() - bubbleMain, centerM + reach - bubbleMain);
        double mainLo = RXMath.clamp(centerM - bubbleMain * HALF, mainMin, Math.max(mainMin, mainMax));
        double thumbCrossLo = barCrossCenter - thumbSize * HALF;
        double thumbCrossHi = barCrossCenter + thumbSize * HALF;
        double crossLo = startSide
                ? (thumbCrossLo - INDICATOR_GAP - bubbleCross)
                : (thumbCrossHi + INDICATOR_GAP);
        indicator.relocate(
                snapPositionX(axis.rectX(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)),
                snapPositionY(axis.rectY(mainLo, mainLo + bubbleMain, crossLo, crossLo + bubbleCross)));
        // Near the ends the bubble is held inside the slider; the caret still points at
        // its thumb, or between the thumbs for the merged bubble.
        indicator.pointCaretAt(axis.pointX(centerM, barCrossCenter), axis.pointY(centerM, barCrossCenter),
                vertical, startSide);
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
        // A track two thumb diameters long, plus half a thumb past each end.
        return 3.0 * low.getThumb().prefWidth(-1);
    }

    private double crossThickness() {
        double bar = Math.max(Math.max(Math.max(track.prefHeight(-1), fill.prefHeight(-1)),
                low.getThumb().prefHeight(-1)), low.getHalo().prefHeight(-1));
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
        // End a drag in flight so the changing flags do not stay true without a release.
        endAllGestures();
        lowAnimator.dispose();
        highAnimator.dispose();
        low.dispose();
        high.dispose();
    }
}
