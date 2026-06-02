package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Default skin for {@link RXSeekBar}.
 */
public class RXSeekBarSkin extends RXSkinBase<RXSeekBar> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = 160.0;
    private static final double DEFAULT_KEYBOARD_STEP = 0.05;
    private static final double HALF = 0.5;

    // ==================== Nodes ====================

    private final Region track = new Region();
    private final Region secondaryBar = new Region();
    private final Region bar = new Region();
    private final StackPane thumb = new StackPane();

    // ==================== Pointer state ====================

    private Point2D dragStart;
    private double preDragProgress;
    private boolean pointerSeeking;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given seek bar.
     *
     * @param control the skinnable control
     */
    public RXSeekBarSkin(RXSeekBar control) {
        super(control);
        initializeNodes();
        registerListeners(control);
        registerPointerHandlers();
        registerKeyboardHandler(control);
    }

    private void initializeNodes() {
        track.getStyleClass().add("track");
        secondaryBar.getStyleClass().add("secondary-bar");
        bar.getStyleClass().add("bar");
        thumb.getStyleClass().add("thumb");
        thumb.setAccessibleRole(AccessibleRole.THUMB);

        secondaryBar.setMouseTransparent(true);
        bar.setMouseTransparent(true);

        getChildren().setAll(track, secondaryBar, bar, thumb);
    }

    private void registerListeners(RXSeekBar control) {
        disposer.registerListener(control.widthProperty(), control::requestLayout);
        disposer.registerListener(control.progressProperty(), control::requestLayout);
        disposer.registerListener(control.secondaryProgressProperty(), control::requestLayout);
    }

    private void registerPointerHandlers() {
        disposer.registerEventHandler(track, MouseEvent.MOUSE_PRESSED, this::onTrackPressed);
        disposer.registerEventHandler(track, MouseEvent.MOUSE_DRAGGED, this::onTrackDragged);
        disposer.registerEventHandler(track, MouseEvent.MOUSE_RELEASED, this::onPointerReleased);

        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_PRESSED, this::onThumbPressed);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_DRAGGED, this::onThumbDragged);
        disposer.registerEventHandler(thumb, MouseEvent.MOUSE_RELEASED, this::onPointerReleased);
    }

    private void registerKeyboardHandler(RXSeekBar control) {
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Pointer interaction ====================

    private void onTrackPressed(MouseEvent event) {
        beginPointerSeek();
        setProgressFromTrack(event.getX());
        event.consume();
    }

    private void onTrackDragged(MouseEvent event) {
        if (pointerSeeking) {
            setProgressFromTrack(event.getX());
            event.consume();
        }
    }

    private void onThumbPressed(MouseEvent event) {
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
        if (pointerSeeking) {
            pointerSeeking = false;
            dragStart = null;
            getSkinnable().setSeeking(false);
            event.consume();
        }
    }

    private void beginPointerSeek() {
        RXSeekBar control = getSkinnable();
        control.requestFocus();
        pointerSeeking = true;
        control.setSeeking(true);
    }

    private void setProgressFromTrack(double localX) {
        double trackLength = track.getWidth();
        if (trackLength <= 0.0) {
            return;
        }
        getSkinnable().setProgress(RXMath.clamp0To1(localX / trackLength));
    }

    // ==================== Discrete interaction ====================

    private void onKeyPressed(KeyEvent event) {
        RXSeekBar control = getSkinnable();
        double current = RXMath.clamp0To1(control.getProgress());
        KeyCode code = event.getCode();
        if (code == KeyCode.LEFT) {
            commitUserValue(current - DEFAULT_KEYBOARD_STEP);
            event.consume();
        } else if (code == KeyCode.RIGHT) {
            commitUserValue(current + DEFAULT_KEYBOARD_STEP);
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

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double thumbW = snapSizeX(thumb.prefWidth(-1));
        double thumbH = snapSizeY(thumb.prefHeight(-1));
        double trackH = snapSizeY(track.prefHeight(-1));
        double barAreaH = Math.max(trackH, thumbH);
        double centerY = y + (h - barAreaH) * HALF;
        double trackTop = snapPositionY(centerY + (barAreaH - trackH) * HALF);
        double thumbTop = snapPositionY(centerY + (barAreaH - thumbH) * HALF);
        double trackStart = snapPositionX(x + thumbW * HALF);
        double trackLength = snapSizeX(w - thumbW);

        if (w <= 0.0 || trackLength <= 0.0) {
            resetLayout(trackStart, trackTop, thumbW, thumbH, thumbTop);
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
        double thumbW = thumb.prefWidth(-1);
        double minTrackLength = 2.0 * thumbW;
        return leftInset + minTrackLength + thumb.minWidth(-1) + rightInset;
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
