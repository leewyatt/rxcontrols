package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Default skin for {@link RXSwitchButton}.
 *
 * <p>Renders a capsule {@code .track} holding a round {@code .thumb} (with an
 * on/off {@code .icon}) sliding inside it, plus a Material {@code .state-overlay}
 * halo that follows the thumb. The thumb's layout position is pinned at the off
 * end; the on/off transition is expressed by {@code translateX} (no per-frame
 * layout invalidation), driven by a single reusable {@link Timeline} animating a
 * {@code thumbPosition} ratio in {@code [0, 1]}. The label is laid out beside the
 * switch block through {@link LabeledSkinBase#layoutLabelInArea}.</p>
 *
 * <p>Mouse and keyboard interaction is installed directly (not via the internal
 * {@code com.sun} {@code ButtonBehavior}), matching the standard
 * {@code ButtonBase} semantics: a valid primary press arms, release fires; SPACE
 * (and ENTER off macOS) arms on press and fires on release. A press that turns
 * into a drag scrubs the thumb and commits to the nearest end on release. The
 * whole control toggles, but the halo + ripple feedback is scoped to the switch
 * block (track / thumb): hovering or clicking the label toggles without lighting
 * up the thumb.</p>
 *
 * <p>Right-to-left layout is left to JavaFX's automatic node-orientation
 * mirroring: positions are computed in left-to-right layout space and the
 * platform flips the rendered result, so no direction is handled here.</p>
 */
public class RXSwitchButtonSkin extends LabeledSkinBase<RXSwitchButton> {

    // ==================== Constants ====================

    /** Pointer travel before a press is treated as a drag rather than a click. */
    private static final double DRAG_THRESHOLD = 4.0;

    /** ENTER activates the switch everywhere except macOS (matching ButtonBehavior). */
    private static final boolean MAC = RXOS.isMacOS();

    /** Peak opacity of the press ripple ink. */
    private static final double RIPPLE_PEAK_OPACITY = 0.18;

    // ==================== Fields ====================

    private final SkinDisposer disposer = new SkinDisposer();

    private final StackPane track = new StackPane();
    private final StateLayer stateLayer = new StateLayer();
    private final RippleLayer rippleLayer = new RippleLayer();
    private final StackPane thumb = new StackPane();
    private final Region thumbIcon = new Region();

    /** Ripple ink colour; follows the halo's CSS-resolved {@code -rx-state-overlay-color}. */
    private final ObjectProperty<Paint> rippleFill = new SimpleObjectProperty<>(this, "rippleFill", null);
    private final RippleBehavior rippleBehavior =
            new RippleBehavior(rippleLayer, rippleFill::get, () -> RIPPLE_PEAK_OPACITY);

    /** Single driver in {@code [0, 1]}; a listener maps it to {@code translateX}. */
    private final DoubleProperty thumbPosition = new SimpleDoubleProperty(this, "thumbPosition", 0.0);

    /** Distance the thumb travels from off to on; recomputed each layout pass. */
    private double thumbTravel;

    /** Single reusable slide animation; rebuilt per toggle, so never registered with the disposer. */
    private Timeline timeline;

    // Drag-to-toggle state.
    private double dragStartX;
    private double preDragRatio;
    private boolean dragging;

    /** True while armed via the keyboard; gates the mouse path and arms focus-loss disarm. */
    private boolean keyDown;

    /**
     * True while a pointer press landed on the switch block (track / thumb), so the
     * halo + ripple stay scoped to the switch — a press on the label only toggles and
     * leaves the thumb dark.
     */
    private boolean pointerOnSwitch;

    /**
     * True when the current focus arrived via a pointer press, so the focus halo
     * is suppressed — a JFX17 stand-in for {@code :focus-visible} (added in JFX19),
     * which shows the focus state only for keyboard focus.
     */
    private boolean mouseFocus;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given switch.
     *
     * @param control the switch this skin is attached to
     */
    public RXSwitchButtonSkin(RXSwitchButton control) {
        super(control);

        track.getStyleClass().add("track");
        // Halo: an unbounded circle drawn below the opaque thumb and synced to its
        // translateX. The default ClipMode.NONE never clips; its round shape and
        // colour come from CSS (-fx-background-radius:50% + -rx-state-overlay-color).
        // Do NOT call setClipMode(CIRCLE, null): with no Java fill it runs
        // setBackground(null), which pins the USER style origin and blocks the
        // user-agent -fx-background-color from ever applying, so the halo would
        // never paint.
        thumb.getStyleClass().add("thumb");
        thumb.setManaged(false);
        thumbIcon.getStyleClass().add("icon");
        thumbIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        thumbIcon.setMouseTransparent(true);
        // Opt the directional check glyph out of the RTL auto-mirror (as CheckBoxSkin
        // does for its mark) so the check stays upright in right-to-left layouts.
        thumbIcon.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        thumb.getChildren().setAll(thumbIcon);
        // z-order: halo (steady tint) and ripple (press ink) below the opaque thumb,
        // both circular and synced to the thumb's translateX.
        track.getChildren().setAll(stateLayer, rippleLayer, thumb);

        updateChildren();

        // Initial snap: the first frame is not animated (avoids a slide on show).
        thumbPosition.set(control.isSelected() ? 1.0 : 0.0);

        disposer.registerListener(thumbPosition, this::applyThumbTranslate);
        disposer.registerListener(control.selectedProperty(), this::handleSelectedChanged);
        disposer.registerListener(control.switchPositionProperty(), control::requestLayout);
        disposer.registerListener(track.hoverProperty(), this::updateHaloState);
        disposer.registerListener(control.focusedProperty(), this::handleFocusChanged);
        disposer.registerListener(control.armedProperty(), this::handleArmedChanged);
        // The ripple ink shares the halo's CSS-resolved colour.
        disposer.registerListener(stateLayer.backgroundProperty(), this::syncRippleFill);
        syncRippleFill();

        installInteraction();
        updateHaloState();
    }

    // ==================== Children ====================

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // skin's fields are initialized.
        if (track != null) {
            getChildren().add(track);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXSwitchButton control = getSkinnable();

        double trackW = snapSizeX(track.prefWidth(-1));
        double trackH = snapSizeY(track.prefHeight(-1));
        double thumbW = snapSizeX(thumb.prefWidth(-1));
        double thumbH = snapSizeY(thumb.prefHeight(-1));
        double switchW = Math.max(trackW, thumbW);

        double labelW = Math.max(0.0, w - switchW);
        boolean switchOnRight = switchPositionOrDefault() != HorizontalDirection.LEFT;
        double switchX = switchOnRight ? x + labelW : x;
        double labelX = switchOnRight ? x : x + switchW;

        layoutLabelInArea(labelX, y, labelW, h, control.getAlignment());
        // positionInArea only relocates; the track must be resized to its pref so
        // the capsule and all its :state colours actually paint (Region defaults to 0x0).
        double trackX = snapPositionX(switchX + (switchW - trackW) / 2.0);
        double trackY = snapPositionY(y + (h - trackH) / 2.0);
        track.resizeRelocate(trackX, trackY, trackW, trackH);

        layoutThumb(trackW, trackH, thumbW, thumbH);
    }

    private void layoutThumb(double trackW, double trackH, double thumbW, double thumbH) {
        double leftPad = snapSpaceX(track.getInsets().getLeft());
        double rightPad = snapSpaceX(track.getInsets().getRight());
        double innerW = trackW - leftPad - rightPad;

        double thumbLocalX;
        if (thumbW <= innerW) {
            thumbLocalX = leftPad;
            thumbTravel = innerW - thumbW;
        } else {
            // Degenerate: thumb wider than the track inner area — center it, no travel.
            thumbLocalX = (trackW - thumbW) / 2.0;
            thumbTravel = 0.0;
        }

        thumb.resize(thumbW, thumbH);
        thumb.relocate(snapPositionX(thumbLocalX), snapPositionY((trackH - thumbH) / 2.0));

        // Halo + ripple share one geometry: centered on the thumb at rest
        // (track-local), moving with the thumb via translateX. Both are unmanaged +
        // mouseTransparent, so they never enter layout.
        double haloW = snapSizeX(stateLayer.prefWidth(-1));
        double haloH = snapSizeY(stateLayer.prefHeight(-1));
        double haloX = snapPositionX(thumbLocalX + (thumbW - haloW) / 2.0);
        double haloY = snapPositionY((trackH - haloH) / 2.0);
        stateLayer.resizeRelocate(haloX, haloY, haloW, haloH);

        rippleLayer.resizeRelocate(haloX, haloY, haloW, haloH);
        // Circular clip matching the layer so the press ink stays round.
        Circle clip = (Circle) rippleLayer.getClip();
        if (clip == null) {
            clip = new Circle();
            rippleLayer.setClip(clip);
        }
        clip.setCenterX(haloW / 2.0);
        clip.setCenterY(haloH / 2.0);
        clip.setRadius(Math.min(haloW, haloH) / 2.0);

        applyThumbTranslate();
    }

    private void applyThumbTranslate() {
        double tx = thumbTravel * thumbPosition.get();
        thumb.setTranslateX(tx);
        stateLayer.setTranslateX(tx);
        rippleLayer.setTranslateX(tx);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(computeSwitchWidth());
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Math.max(
                super.computeMinHeight(width - computeSwitchWidth(), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(computeSwitchHeight()) + bottomInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(computeSwitchWidth());
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return Math.max(
                super.computePrefHeight(width - computeSwitchWidth(), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(computeSwitchHeight()) + bottomInset);
    }

    private double computeSwitchWidth() {
        return Math.max(track.prefWidth(-1), thumb.prefWidth(-1));
    }

    private double computeSwitchHeight() {
        return Math.max(track.prefHeight(-1), thumb.prefHeight(-1));
    }

    // ==================== Animation ====================

    private void handleSelectedChanged() {
        animateThumbTo(getSkinnable().isSelected() ? 1.0 : 0.0);
    }

    private void animateThumbTo(double target) {
        Duration duration = animationDurationOrDefault();
        if (!isAnimatable(duration)) {
            // Non-positive / unusable duration: snap to the target rather than
            // feeding the Timeline a degenerate duration. A short one-shot tween
            // needs no tree-showing gate (AGENTS §3.1): an off-screen toggle just
            // completes invisibly, leaving the same end state.
            if (timeline != null) {
                timeline.stop();
            }
            thumbPosition.set(target);
            return;
        }
        if (timeline == null) {
            timeline = new Timeline();
        } else {
            timeline.stop();
        }
        // KeyValue starts from the current thumbPosition (no explicit fromValue),
        // so a mid-flight reversal continues smoothly instead of jumping.
        KeyValue keyValue = new KeyValue(thumbPosition, target, interpolatorOrDefault());
        timeline.getKeyFrames().setAll(new KeyFrame(duration, keyValue));
        timeline.play();
    }

    private Duration animationDurationOrDefault() {
        Duration value = getSkinnable().getAnimationDuration();
        return value == null ? RXSwitchButton.DEFAULT_ANIMATION_DURATION : value;
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXSwitchButton.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private HorizontalDirection switchPositionOrDefault() {
        HorizontalDirection value = getSkinnable().getSwitchPosition();
        return value == null ? RXSwitchButton.DEFAULT_SWITCH_POSITION : value;
    }

    private static boolean isAnimatable(Duration duration) {
        return !duration.isUnknown() && !duration.isIndefinite()
                && Double.isFinite(duration.toMillis()) && duration.toMillis() > 0.0;
    }

    private void stopThumbAnimation() {
        // Rebuilt across toggles; the disposer would hold a stale reference, so
        // stop the live field directly (AGENTS §2.8).
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    // ==================== State overlay (halo) + press ripple ====================

    private void updateHaloState() {
        RXSwitchButton control = getSkinnable();
        // Focus-visible stand-in: show the focus tier only for keyboard focus, not
        // pointer focus (JFX17 lacks the :focus-visible pseudo-class added in JFX19).
        boolean focusVisible = control.isFocused() && !mouseFocus;
        // Hover / pressed / dragged feedback is scoped to the switch block (track +
        // thumb), so hovering or clicking the label does not light up the thumb.
        boolean pressed = (control.isArmed() && pointerOnSwitch) || keyDown;
        stateLayer.setState(track.isHover(), focusVisible, pressed, dragging && pointerOnSwitch);
    }

    private void handleArmedChanged() {
        RXSwitchButton control = getSkinnable();
        if (control.isArmed() && (pointerOnSwitch || keyDown)) {
            // Centered press ink in the circular touch area, on top of the halo.
            rippleBehavior.press(0.0, 0.0, true);
        } else {
            rippleBehavior.release();
            if (!control.isArmed()) {
                // The press ended; the next press recomputes whether it is on the switch.
                pointerOnSwitch = false;
            }
        }
        updateHaloState();
    }

    private void syncRippleFill() {
        Background background = stateLayer.getBackground();
        if (background != null && !background.getFills().isEmpty()) {
            rippleFill.set(background.getFills().get(0).getFill());
        }
    }

    private void handleFocusChanged() {
        RXSwitchButton control = getSkinnable();
        if (!control.isFocused()) {
            // Reset pointer-focus tracking so the next keyboard Tab is focus-visible.
            mouseFocus = false;
            // Losing focus while a key is held would otherwise strand the control armed
            // (the KEY_RELEASED goes to the new focus owner); matches ButtonBehavior.
            if (keyDown) {
                keyDown = false;
                control.disarm();
            }
        }
        updateHaloState();
    }

    // ==================== Interaction ====================

    private void installInteraction() {
        RXSwitchButton control = getSkinnable();
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, this::onMouseExited);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::onMouseEntered);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, KeyEvent.KEY_RELEASED, this::onKeyReleased);
    }

    private void onMousePressed(MouseEvent event) {
        RXSwitchButton control = getSkinnable();
        // Record that focus (if gained or already held) is now pointer-driven, so the
        // focus halo stays suppressed; set before requestFocus so the focus listener
        // sees it. Cleared on focus loss, so a later keyboard Tab is focus-visible.
        mouseFocus = true;
        if (control.isFocusTraversable() && !control.isFocused()) {
            control.requestFocus();
        }
        if (validPrimaryPress(event) && !control.isArmed()) {
            // Scope the halo + ripple to the switch block: a press on the track / thumb
            // lights them up, a press on the label only toggles. Set before arm() so
            // the armed listener sees it.
            pointerOnSwitch = isOnSwitchBlock(event);
            control.arm();
            dragStartX = event.getX();
            preDragRatio = thumbPosition.get();
            dragging = false;
        }
    }

    private boolean isOnSwitchBlock(MouseEvent event) {
        PickResult pick = event.getPickResult();
        Node node = pick == null ? null : pick.getIntersectedNode();
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == track) {
                return true;
            }
        }
        return false;
    }

    private void onMouseDragged(MouseEvent event) {
        RXSwitchButton control = getSkinnable();
        if (!control.isArmed() || thumbTravel <= 0.0 || keyDown) {
            return;
        }
        double dx = event.getX() - dragStartX;
        if (!dragging) {
            if (Math.abs(dx) <= DRAG_THRESHOLD) {
                return;
            }
            dragging = true;
            updateHaloState();
        }
        if (timeline != null) {
            timeline.stop();
        }
        thumbPosition.set(RXMath.clamp0To1(preDragRatio + dx / thumbTravel));
        event.consume();
    }

    private void onMouseReleased(MouseEvent event) {
        RXSwitchButton control = getSkinnable();
        if (dragging) {
            dragging = false;
            double target = thumbPosition.get() >= 0.5 ? 1.0 : 0.0;
            boolean newSelected = target == 1.0;
            if (newSelected != control.isSelected()) {
                // Drag commit: the selected change animates the thumb to the end;
                // a user gesture must fire onAction (matching click / keyboard).
                control.setSelected(newSelected);
                control.fireEvent(new ActionEvent());
            } else {
                // Landed back on the same end: settle the scrubbed thumb, no fire.
                animateThumbTo(target);
            }
            control.disarm();
            updateHaloState();
            event.consume();
        } else if (control.isArmed() && !keyDown) {
            // Plain click: fire then disarm (mouse path order). Gated on !keyDown so
            // a stray mouse release does not fire a keyboard-armed switch.
            control.fire();
            control.disarm();
        }
    }

    private void onMouseExited(MouseEvent event) {
        RXSwitchButton control = getSkinnable();
        // Keep arming through a drag (the gesture stays captured) and through a
        // keyboard arm (the keyboard owns the arm state).
        if (control.isArmed() && !dragging && !keyDown) {
            control.disarm();
        }
    }

    private void onMouseEntered(MouseEvent event) {
        RXSwitchButton control = getSkinnable();
        if (control.isPressed() && !keyDown) {
            // Pressed, dragged out and back in: re-arm so a release still fires.
            control.arm();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        RXSwitchButton control = getSkinnable();
        if (isActivationKey(event.getCode())) {
            if (!control.isPressed() && !control.isArmed()) {
                keyDown = true;
                control.arm();
            }
            // Consume every activation-key press (including OS auto-repeat while held)
            // so a held SPACE / ENTER cannot leak to a scene-level default-button
            // accelerator, matching ButtonBehavior's unconditional auto-consume.
            event.consume();
        }
    }

    private void onKeyReleased(KeyEvent event) {
        RXSwitchButton control = getSkinnable();
        if (isActivationKey(event.getCode()) && keyDown) {
            // Keyboard path: disarm then fire (the opposite order from the mouse path).
            keyDown = false;
            control.disarm();
            control.fire();
            event.consume();
        }
    }

    private static boolean isActivationKey(KeyCode code) {
        return code == KeyCode.SPACE || (!MAC && code == KeyCode.ENTER);
    }

    private static boolean validPrimaryPress(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && !(event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown());
    }

    // ==================== Dispose ====================

    /**
     * Stops the rebuilt slide timeline and the press ripple, disposes the halo
     * overlay, runs the disposer (listeners, handlers) and then the standard
     * {@link LabeledSkinBase} cleanup, in that order. Safe to call more than once.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::stopThumbAnimation, rippleBehavior::clear,
                stateLayer::dispose, disposer::dispose, super::dispose);
    }
}
