package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorDisplay;
import io.github.leewyatt.rxcontrols.internal.slider.IndicatorAnimator;
import io.github.leewyatt.rxcontrols.internal.slider.SliderAxis;
import io.github.leewyatt.rxcontrols.internal.slider.SliderValueIndicator;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.Function;

/**
 * Default skin for {@link RXSeekBar}.
 *
 * <p>With an indicator text factory set, a preview bubble above the thumb shows
 * while the pointer seeks and while the bar has keyboard focus that came from the
 * keyboard (a key pressed since the last mouse press), not after a click.</p>
 */
public class RXSeekBarSkin extends RXSkinBase<RXSeekBar> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = 160.0;
    private static final double DEFAULT_KEYBOARD_STEP = 0.05;
    private static final double HALF = 0.5;

    // ==================== Nodes ====================

    private final Region hitArea = new Region();
    private final Region track = new Region();
    private final Region secondaryBar = new Region();
    private final Region bar = new Region();
    private final StackPane thumb = new StackPane();
    private final SliderValueIndicator indicator = new SliderValueIndicator();

    // ==================== Pointer state ====================

    private Point2D dragStart;
    private double preDragProgress;
    private boolean pointerSeeking;

    // ==================== Indicator state ====================

    private final IndicatorAnimator indicatorAnimator;
    private boolean keyboardFocus;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given seek bar.
     *
     * @param control the skinnable control
     */
    public RXSeekBarSkin(RXSeekBar control) {
        super(control);
        // No factory means no bubble; animator transitions always run.
        indicatorAnimator = new IndicatorAnimator(indicator,
                () -> control.getIndicatorTextFactory() == null
                        ? RXSliderIndicatorDisplay.NEVER
                        : RXSliderIndicatorDisplay.DRAGGING,
                () -> true);
        initializeNodes();
        registerListeners(control);
        registerPointerHandlers();
        registerKeyboardHandler(control);
        registerIndicatorListeners(control);
        updateIndicatorText();
    }

    private void initializeNodes() {
        hitArea.setManaged(false);
        hitArea.setPickOnBounds(true);

        track.getStyleClass().add("track");
        secondaryBar.getStyleClass().add("secondary-bar");
        bar.getStyleClass().add("bar");
        thumb.getStyleClass().add("thumb");
        thumb.setAccessibleRole(AccessibleRole.THUMB);

        // The hit area under the bars takes every press on the content area.
        track.setMouseTransparent(true);
        secondaryBar.setMouseTransparent(true);
        bar.setMouseTransparent(true);

        getChildren().setAll(hitArea, track, secondaryBar, bar, thumb, indicator);
    }

    private void registerListeners(RXSeekBar control) {
        disposer.registerListener(control.progressProperty(), control::requestLayout);
        disposer.registerListener(control.secondaryProgressProperty(), control::requestLayout);
    }

    private void registerPointerHandlers() {
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_PRESSED, this::onHitAreaPressed);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_DRAGGED, this::onHitAreaDragged);
        disposer.registerEventHandler(hitArea, MouseEvent.MOUSE_RELEASED, this::onPointerReleased);

        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_PRESSED, this::onThumbPressed);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_DRAGGED, this::onThumbDragged);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_RELEASED, this::onPointerReleased);
    }

    private void registerKeyboardHandler(RXSeekBar control) {
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    private void registerIndicatorListeners(RXSeekBar control) {
        disposer.registerListener(control.progressProperty(), this::updateIndicatorText);
        disposer.registerListener(control.indicatorTextFactoryProperty(), () -> {
            updateIndicatorText();
            updateIndicator();
            control.requestLayout();
        });
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, event -> setKeyboardFocus(true));
        disposer.registerEventFilter(control, KeyEvent.KEY_RELEASED, event -> setKeyboardFocus(true));
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, event -> setKeyboardFocus(false));
        disposer.registerListener(control.focusedProperty(), () -> {
            // A focus leaving the window keeps the owner, so its bubble returns with it.
            if (!control.isFocused() && control.getScene() != null
                    && control.getScene().getFocusOwner() != control) {
                keyboardFocus = false;
            }
            updateIndicator();
        });
    }

    // ==================== Pointer interaction ====================

    private void onHitAreaPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        beginPointerSeek();
        setProgressFromNode(hitArea, event);
        event.consume();
    }

    private void onHitAreaDragged(MouseEvent event) {
        if (pointerSeeking) {
            setProgressFromNode(hitArea, event);
            event.consume();
        }
    }

    private void onThumbPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        beginPointerSeek();
        dragStart = thumb.localToParent(event.getX(), event.getY());
        preDragProgress = RXMath.clamp0To1(getSkinnable().getProgress());
        event.consume();
    }

    private void onThumbDragged(MouseEvent event) {
        if (!pointerSeeking || dragStart == null) {
            return;
        }
        double trackLength = track.getWidth();
        if (trackLength <= 0.0) {
            return;
        }
        Point2D current = thumb.localToParent(event.getX(), event.getY());
        double dragProgress = preDragProgress + (current.getX() - dragStart.getX()) / trackLength;
        getSkinnable().setProgress(RXMath.clamp0To1(dragProgress));
        event.consume();
    }

    private void onPointerReleased(MouseEvent event) {
        if (pointerSeeking && event.getButton() == MouseButton.PRIMARY) {
            pointerSeeking = false;
            dragStart = null;
            getSkinnable().setSeeking(false);
            updateIndicator();
            event.consume();
        }
    }

    private void beginPointerSeek() {
        RXSeekBar control = getSkinnable();
        control.requestFocus();
        pointerSeeking = true;
        control.setSeeking(true);
        updateIndicator();
    }

    private void setProgressFromNode(Node source, MouseEvent event) {
        Point2D point = source.localToParent(event.getX(), event.getY());
        setProgressFromSkinX(point.getX());
    }

    private void setProgressFromSkinX(double x) {
        double trackLength = track.getWidth();
        if (trackLength <= 0.0) {
            return;
        }
        getSkinnable().setProgress(RXMath.clamp0To1((x - track.getLayoutX()) / trackLength));
    }

    // ==================== Discrete interaction ====================

    private void onKeyPressed(KeyEvent event) {
        if (pointerSeeking) {
            // A key commit would end the pointer seek before the button is released.
            return;
        }
        RXSeekBar control = getSkinnable();
        double current = RXMath.clamp0To1(control.getProgress());
        KeyCode code = event.getCode();
        // Mirrored for right-to-left: the arrow moves the thumb in its own direction.
        double step = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT
                ? -DEFAULT_KEYBOARD_STEP
                : DEFAULT_KEYBOARD_STEP;
        if (code == KeyCode.LEFT || code == KeyCode.KP_LEFT) {
            commitUserValue(current - step);
            event.consume();
        } else if (code == KeyCode.RIGHT || code == KeyCode.KP_RIGHT) {
            commitUserValue(current + step);
            event.consume();
        } else if (code == KeyCode.HOME) {
            commitUserValue(0.0);
            event.consume();
        } else if (code == KeyCode.END) {
            commitUserValue(1.0);
            event.consume();
        }
    }

    private void commitUserValue(double target) {
        RXSeekBar control = getSkinnable();
        control.setSeeking(true);
        control.setProgress(RXMath.clamp0To1(target));
        control.setSeeking(false);
    }

    // ==================== Indicator ====================

    private void setKeyboardFocus(boolean value) {
        if (keyboardFocus != value) {
            keyboardFocus = value;
            updateIndicator();
        }
    }

    private void updateIndicator() {
        indicatorAnimator.update(pointerSeeking || (keyboardFocus && getSkinnable().isFocused()));
    }

    private void updateIndicatorText() {
        Function<Double, String> factory = getSkinnable().getIndicatorTextFactory();
        if (factory != null) {
            indicator.setText(factory.apply(RXMath.clamp0To1(getSkinnable().getProgress())));
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double thumbW = snapSizeX(thumb.prefWidth(-1));
        double thumbH = snapSizeY(thumb.prefHeight(-1));
        double trackH = snapSizeY(track.prefHeight(-1));
        double barAreaH = Math.max(trackH, thumbH);
        hitArea.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));

        double centerY = y + (h - barAreaH) * HALF;
        double trackTop = snapPositionY(centerY + (barAreaH - trackH) * HALF);
        double thumbTop = snapPositionY(centerY + (barAreaH - thumbH) * HALF);
        double trackStart = snapPositionX(x + thumbW * HALF);
        double trackLength = snapSizeX(w - thumbW);

        SliderAxis axis = new SliderAxis(false, x, y, Math.max(0.0, w), Math.max(0.0, h));
        double barCrossCenter = centerY - y + barAreaH * HALF;
        if (w <= 0.0 || trackLength <= 0.0) {
            resetLayout(trackStart, trackTop, thumbW, thumbH, thumbTop);
            indicator.layoutBeside(axis, trackStart - x, barCrossCenter, thumbH, true);
            return;
        }

        double progress = RXMath.clamp0To1(getSkinnable().getProgress());
        double secondaryProgress = RXMath.clamp0To1(getSkinnable().getSecondaryProgress());
        double progressW = snapSizeX(trackLength * progress);
        double secondaryW = snapSizeX(trackLength * secondaryProgress);

        track.resizeRelocate(trackStart, trackTop, trackLength, trackH);
        secondaryBar.resizeRelocate(trackStart, trackTop, secondaryW, trackH);
        bar.resizeRelocate(trackStart, trackTop, progressW, trackH);

        thumb.resize(thumbW, thumbH);
        thumb.relocate(snapPositionX(trackStart + progressW - thumbW * HALF), thumbTop);
        indicator.layoutBeside(axis, trackStart + progressW - x, barCrossCenter, thumbH, true);
    }

    private void resetLayout(double trackStart, double trackTop,
                             double thumbW, double thumbH, double thumbTop) {
        track.resizeRelocate(trackStart, trackTop, 0.0, 0.0);
        secondaryBar.resizeRelocate(trackStart, trackTop, 0.0, 0.0);
        bar.resizeRelocate(trackStart, trackTop, 0.0, 0.0);
        thumb.resize(thumbW, thumbH);
        thumb.relocate(snapPositionX(trackStart - thumbW * HALF), thumbTop);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // A track two thumbs long, plus half a thumb past each end.
        double thumbW = thumb.prefWidth(-1);
        return leftInset + 3.0 * thumbW + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + Math.max(track.prefHeight(-1), thumb.prefHeight(-1)) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + Math.max(track.prefHeight(-1), thumb.prefHeight(-1)) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
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
        if (pointerSeeking) {
            pointerSeeking = false;
            dragStart = null;
            getSkinnable().setSeeking(false);
        }
    }

    // ==================== Accessibility ====================

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case VALUE:
                return RXMath.clamp0To1(getSkinnable().getProgress());
            case MIN_VALUE:
                return 0.0;
            case MAX_VALUE:
                return 1.0;
            case ORIENTATION:
                return Orientation.HORIZONTAL;
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    @Override
    protected void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        switch (action) {
            case INCREMENT:
                commitUserValue(RXMath.clamp0To1(getSkinnable().getProgress()) + DEFAULT_KEYBOARD_STEP);
                break;
            case DECREMENT:
                commitUserValue(RXMath.clamp0To1(getSkinnable().getProgress()) - DEFAULT_KEYBOARD_STEP);
                break;
            case SET_VALUE:
                if (parameters != null && parameters.length > 0 && parameters[0] instanceof Number) {
                    Number value = (Number) parameters[0];
                    commitUserValue(value.doubleValue());
                }
                break;
            default:
                super.executeAccessibleAction(action, parameters);
        }
    }
}
