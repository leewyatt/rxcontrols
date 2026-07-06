package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Default skin for {@link RXCheckBox}.
 *
 * <p>Renders a Material {@code .box} holding a single {@code .mark} (a check or a
 * dash, expressed with {@code -fx-shape}; the {@code :selected} /
 * {@code :indeterminate} pseudo-classes swap the shape), plus a Material state
 * layer drawn <em>below</em> the box and statically centred on it: a
 * {@code .state-overlay} halo (steady hover / focus / pressed tint) and a
 * {@code .ripple-layer} (press ink). The mark appear / disappear is expressed by
 * {@code scaleX/scaleY} (no per-frame layout invalidation), driven by a single
 * reusable {@link Timeline} animating a {@code markScale} ratio in {@code [0, 1]}
 * whose target is {@code (selected || indeterminate) ? 1 : 0}. The label is laid
 * out beside the box through {@link LabeledSkinBase#layoutLabelInArea}.</p>
 *
 * <p>The state layer is <em>scoped to the box</em>: its hover / pressed feedback
 * follows the box's own pointer state, not the whole control, so hovering or
 * clicking the label toggles the control without lighting up a circle on the box.
 * Keyboard focus shows the focus tier (the focus indicator), while a pointer press
 * suppresses it (a JFX17 stand-in for {@code :focus-visible}).</p>
 *
 * <p>Mouse and keyboard interaction is installed directly (not via the internal
 * {@code com.sun} {@code ButtonBehavior}), matching the standard
 * {@code ButtonBase} semantics: a valid primary press arms, release fires; SPACE
 * (and ENTER off macOS) arms on press and fires on release. Activation always
 * routes through the inherited {@link RXCheckBox#fire()} tri-state cycle. A check
 * box is never dragged, so there is no scrub gesture.</p>
 *
 * <p>Right-to-left layout is left to JavaFX's automatic node-orientation
 * mirroring: positions are computed in left-to-right layout space and the
 * platform flips the rendered result, so no direction is handled here.</p>
 */
public class RXCheckBoxSkin extends LabeledSkinBase<RXCheckBox> {

    // ==================== Constants ====================

    /** ENTER activates the check box everywhere except macOS (matching ButtonBehavior). */
    private static final boolean MAC = RXOS.isMacOS();

    // ==================== Fields ====================

    private final SkinDisposer disposer = new SkinDisposer();

    private final StateLayer stateLayer = new StateLayer();
    private final RippleLayer rippleLayer = new RippleLayer();
    private final StackPane box = new StackPane();
    private final Region mark = new Region();

    /** Ripple ink colour; follows the halo's CSS-resolved {@code -rx-state-overlay-color}. */
    private final ObjectProperty<Paint> rippleFill = new SimpleObjectProperty<>(this, "rippleFill", null);
    private final RippleBehavior rippleBehavior =
            new RippleBehavior(rippleLayer, rippleFill::get, () -> RXRipplePane.DEFAULT_RIPPLE_OPACITY);

    /** Single driver in {@code [0, 1]}; a listener maps it to {@code scaleX/scaleY}. */
    private final DoubleProperty markScale = new SimpleDoubleProperty(this, "markScale", 0.0);

    /** Single reusable scale animation; rebuilt per toggle, so never registered with the disposer. */
    private Timeline timeline;

    /** True while the box itself is pointer-pressed; scopes the ripple + pressed halo to the box. */
    private boolean boxPressed;

    /** True while armed via the keyboard; gates the mouse fire path and arms focus-loss disarm. */
    private boolean keyDown;

    /**
     * True when the current focus arrived via a pointer press, so the focus halo is
     * suppressed — a JFX17 stand-in for {@code :focus-visible} (added in JFX19),
     * which shows the focus state only for keyboard focus.
     */
    private boolean mouseFocus;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given check box.
     *
     * @param control the check box this skin is attached to
     */
    public RXCheckBoxSkin(RXCheckBox control) {
        super(control);

        box.getStyleClass().add("box");
        mark.getStyleClass().add("mark");
        mark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        mark.setMouseTransparent(true);
        // Opt the check / dash mark out of the RTL auto-mirror (as CheckBoxSkin does
        // for its mark) so it stays upright in right-to-left layouts.
        mark.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        box.getChildren().setAll(mark);

        // Halo + ripple: an unbounded circle drawn below the opaque box and centred
        // on it. The default ClipMode.NONE never clips; the halo's round shape and
        // colour come from CSS (-fx-background-radius:50% + -rx-state-overlay-color).
        // Do NOT call setClipMode(CIRCLE, null) / setFill: with no Java fill it runs
        // setBackground(null), which pins the USER style origin and blocks the
        // user-agent -fx-background-color from ever applying, so the halo would
        // never paint.

        updateChildren();

        // Initial snap: the first frame is not animated (avoids a pop-in on show).
        markScale.set((control.isSelected() || control.isIndeterminate()) ? 1.0 : 0.0);
        applyMarkScale();

        disposer.registerListener(markScale, this::applyMarkScale);
        disposer.registerListener(control.selectedProperty(), this::handleMarkStateChanged);
        disposer.registerListener(control.indeterminateProperty(), this::handleMarkStateChanged);
        disposer.registerListener(control.boxSideProperty(), control::requestLayout);
        // Hover feedback follows the box, not the whole control (no circle on label hover).
        disposer.registerListener(box.hoverProperty(), this::updateHaloState);
        disposer.registerListener(control.focusedProperty(), this::handleFocusChanged);
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
        // skin's fields are initialized. Halo + ripple first (z-order below), box after.
        if (box != null) {
            getChildren().add(stateLayer);
            getChildren().add(rippleLayer);
            getChildren().add(box);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXCheckBox control = getSkinnable();

        double boxW = snapSizeX(box.prefWidth(-1));
        double boxH = snapSizeY(box.prefHeight(-1));

        double labelW = Math.max(0.0, w - boxW);
        boolean boxOnLeft = boxSideOrDefault() != HorizontalDirection.RIGHT;
        double boxX = snapPositionX(boxOnLeft ? x : x + labelW);
        double labelX = boxOnLeft ? x + boxW : x;

        layoutLabelInArea(labelX, y, labelW, h, control.getAlignment());

        // resizeRelocate (not positionInArea): the box must be resized to its pref
        // so the Material box and all its :state colours actually paint (Region
        // defaults to 0x0).
        double boxY = snapPositionY(y + (h - boxH) / 2.0);
        box.resizeRelocate(boxX, boxY, boxW, boxH);

        // Halo + ripple share one geometry: statically centred on the box (which
        // never moves), drawn below it. Both unmanaged + mouseTransparent, so they
        // never enter layout. They are larger than the box, so they read as a ring /
        // expanding ink around it — the Material check-box state layer.
        double haloW = snapSizeX(stateLayer.prefWidth(-1));
        double haloH = snapSizeY(stateLayer.prefHeight(-1));
        double haloX = snapPositionX(boxX + (boxW - haloW) / 2.0);
        double haloY = snapPositionY(boxY + (boxH - haloH) / 2.0);
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
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(box.minWidth(-1));
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Math.max(
                super.computeMinHeight(width - box.minWidth(-1), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(box.minHeight(-1)) + bottomInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(box.prefWidth(-1));
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return Math.max(
                super.computePrefHeight(width - box.prefWidth(-1), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(box.prefHeight(-1)) + bottomInset);
    }

    // ==================== Mark animation ====================

    private void handleMarkStateChanged() {
        RXCheckBox control = getSkinnable();
        // Both checked and indeterminate show the mark (scale 1); unchecked hides it
        // (scale 0). checked <-> indeterminate keeps scale at 1, only the CSS shape swaps.
        animateMarkTo((control.isSelected() || control.isIndeterminate()) ? 1.0 : 0.0);
    }

    private void animateMarkTo(double target) {
        Duration duration = animationDurationOrDefault();
        if (!isAnimatable(duration)) {
            // Non-positive / unusable duration: snap to the target rather than
            // feeding the Timeline a degenerate duration. A short one-shot tween
            // needs no tree-showing gate (AGENTS §3.1): an off-screen toggle just
            // completes invisibly, leaving the same end state.
            if (timeline != null) {
                timeline.stop();
            }
            markScale.set(target);
            return;
        }
        if (timeline == null) {
            timeline = new Timeline();
        } else {
            timeline.stop();
        }
        // KeyValue starts from the current markScale (no explicit fromValue), so a
        // mid-flight reversal continues smoothly instead of jumping.
        KeyValue keyValue = new KeyValue(markScale, target, interpolatorOrDefault());
        timeline.getKeyFrames().setAll(new KeyFrame(duration, keyValue));
        timeline.play();
    }

    private void applyMarkScale() {
        double s = markScale.get();
        mark.setScaleX(s);
        mark.setScaleY(s);
    }

    private Duration animationDurationOrDefault() {
        Duration value = getSkinnable().getAnimationDuration();
        return value == null ? RXCheckBox.DEFAULT_ANIMATION_DURATION : value;
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXCheckBox.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private HorizontalDirection boxSideOrDefault() {
        HorizontalDirection value = getSkinnable().getBoxSide();
        return value == null ? RXCheckBox.DEFAULT_BOX_SIDE : value;
    }

    private static boolean isAnimatable(Duration duration) {
        return !duration.isUnknown() && !duration.isIndefinite()
                && Double.isFinite(duration.toMillis()) && duration.toMillis() > 0.0;
    }

    private void stopMarkAnimation() {
        // Rebuilt across toggles; the disposer would hold a stale reference, so
        // stop the live field directly (AGENTS §2.8).
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    // ==================== State overlay (halo) + press ripple ====================

    private void updateHaloState() {
        // Hover / pressed feedback is scoped to the box, not the whole control, so
        // the label does not light up the box. Focus shows for keyboard focus only
        // (mouseFocus suppresses pointer focus). A check box is never dragged.
        boolean focusVisible = getSkinnable().isFocused() && !mouseFocus;
        stateLayer.setState(box.isHover(), focusVisible, boxPressed || keyDown, false);
    }

    private void syncRippleFill() {
        Background background = stateLayer.getBackground();
        if (background != null && !background.getFills().isEmpty()) {
            rippleFill.set(background.getFills().get(0).getFill());
        }
    }

    private void handleFocusChanged() {
        RXCheckBox control = getSkinnable();
        if (!control.isFocused()) {
            // Reset pointer-focus tracking so the next keyboard Tab is focus-visible.
            mouseFocus = false;
            // Losing focus while a key is held would otherwise strand the control armed
            // (the KEY_RELEASED goes to the new focus owner); matches ButtonBehavior.
            if (keyDown) {
                keyDown = false;
                control.disarm();
                rippleBehavior.release();
            }
        }
        updateHaloState();
    }

    // ==================== Interaction ====================

    private void installInteraction() {
        RXCheckBox control = getSkinnable();
        // Toggle: the whole control (box, gap and label) activates.
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, this::onMouseExited);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::onMouseEntered);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, KeyEvent.KEY_RELEASED, this::onKeyReleased);
        // Press ink + pressed halo: scoped to the box (a press on the label does not
        // reach these box handlers, so it toggles without lighting up the box).
        disposer.registerEventHandler(box, MouseEvent.MOUSE_PRESSED, this::onBoxPressed);
        disposer.registerEventHandler(box, MouseEvent.MOUSE_RELEASED, this::onBoxReleased);
        disposer.registerEventHandler(box, MouseEvent.MOUSE_EXITED, this::onBoxExited);
    }

    private void onMousePressed(MouseEvent event) {
        RXCheckBox control = getSkinnable();
        // Record that focus (if gained or already held) is now pointer-driven, so the
        // focus halo stays suppressed; cleared on focus loss so a later Tab is visible.
        mouseFocus = true;
        if (control.isFocusTraversable() && !control.isFocused()) {
            control.requestFocus();
        }
        if (RXMouse.isPlainPrimaryPress(event) && !control.isArmed()) {
            control.arm();
        }
    }

    private void onMouseReleased(MouseEvent event) {
        RXCheckBox control = getSkinnable();
        // Plain click: fire then disarm (mouse path order). Gated on !keyDown so a
        // stray mouse release does not fire a keyboard-armed check box.
        if (control.isArmed() && !keyDown) {
            control.fire();
            control.disarm();
        }
    }

    private void onMouseExited(MouseEvent event) {
        RXCheckBox control = getSkinnable();
        if (control.isArmed() && !keyDown) {
            control.disarm();
        }
    }

    private void onMouseEntered(MouseEvent event) {
        RXCheckBox control = getSkinnable();
        if (control.isPressed() && !keyDown) {
            // Pressed, dragged out and back in: re-arm so a release still fires.
            control.arm();
        }
    }

    private void onBoxPressed(MouseEvent event) {
        if (RXMouse.isPlainPrimaryPress(event) && !boxPressed) {
            boxPressed = true;
            // Centered press ink in the circular state-layer region, behind the box.
            rippleBehavior.press(0.0, 0.0, true);
            updateHaloState();
        }
    }

    private void onBoxReleased(MouseEvent event) {
        if (boxPressed) {
            boxPressed = false;
            rippleBehavior.release();
            updateHaloState();
        }
    }

    private void onBoxExited(MouseEvent event) {
        if (boxPressed) {
            boxPressed = false;
            rippleBehavior.release();
            updateHaloState();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        RXCheckBox control = getSkinnable();
        if (isActivationKey(event.getCode())) {
            if (!control.isPressed() && !control.isArmed()) {
                keyDown = true;
                control.arm();
                // Keyboard activation starts the ink from the box centre too.
                rippleBehavior.press(0.0, 0.0, true);
                updateHaloState();
            }
            // Consume every activation-key press (including OS auto-repeat while held)
            // so a held SPACE / ENTER cannot leak to a scene-level default-button
            // accelerator, matching ButtonBehavior's unconditional auto-consume.
            event.consume();
        }
    }

    private void onKeyReleased(KeyEvent event) {
        RXCheckBox control = getSkinnable();
        if (isActivationKey(event.getCode()) && keyDown) {
            // Keyboard path: disarm then fire (the opposite order from the mouse path).
            keyDown = false;
            control.disarm();
            control.fire();
            rippleBehavior.release();
            updateHaloState();
            event.consume();
        }
    }

    private static boolean isActivationKey(KeyCode code) {
        return code == KeyCode.SPACE || (!MAC && code == KeyCode.ENTER);
    }

    // ==================== Dispose ====================

    /**
     * Stops the rebuilt mark timeline and the press ripple, disposes the halo
     * overlay, runs the disposer (listeners, handlers) and then the standard
     * {@link LabeledSkinBase} cleanup, in that order. Safe to call more than once.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::stopMarkAnimation, rippleBehavior::clear,
                stateLayer::dispose, disposer::dispose, super::dispose);
    }
}
