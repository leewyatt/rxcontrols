package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.HorizontalDirection;
import javafx.scene.control.skin.RadioButtonSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Default skin for {@link RXRadioButton}.
 *
 * <p>Extends the public {@link RadioButtonSkin} so the inherited
 * {@code ToggleButtonBehavior} (mouse arming, SPACE, ENTER off macOS and the
 * arrow-key group traversal) is installed for free; no key / mouse activation is
 * wired here. The native {@code .radio} node is removed in {@link #updateChildren()}
 * and replaced by a self-drawn {@code .radio} {@link StackPane} (the outer ring)
 * holding a {@code .state-overlay} halo (drawn below) and a {@code .dot} (the inner
 * point). The dot appear / disappear is expressed by {@code scaleX/scaleY} and
 * {@code opacity} (no per-frame layout invalidation), driven by a {@code dotScale}
 * ratio in {@code [0, 1]} whose target is {@code selected ? 1 : 0}. The label is
 * laid out beside the ring through {@link #layoutLabelInArea}.</p>
 *
 * <p>The halo is <em>scoped to the indicator</em>: its hover / pressed feedback
 * follows the indicator's own pointer state, not the whole control, so hovering or
 * clicking the label selects the button without lighting up the ring. Keyboard focus
 * shows the focus tier, but a pointer click does not (a {@code :focus-visible}
 * stand-in), even when the click lands on the label. A primary press on the ring also plays a centred M2
 * ripple ink (the {@code .ripple-layer}, drawn above the halo and below the dot,
 * clipped to the touch circle); a press on the label does not. The halo's round
 * shape and colour come entirely from CSS ({@code -fx-background-radius:50%} +
 * {@code -rx-state-overlay-color}); the {@link StateLayer} stays in its default
 * {@code ClipMode.NONE} and {@code setFill} is never called — doing so would run
 * {@code setBackground(null)}, pinning the USER style origin and blocking the
 * user-agent background so the halo would never paint (matching
 * {@link RXCheckBoxSkin} / {@link RXSwitchButtonSkin}). The ink colour follows that
 * CSS-resolved halo colour, with no Java property.</p>
 *
 * <p>Right-to-left layout is left to JavaFX's automatic node-orientation mirroring:
 * positions are computed in left-to-right layout space and the platform flips the
 * rendered result, so no direction is handled here.</p>
 */
public class RXRadioButtonSkin extends RadioButtonSkin {

    // ==================== Constants ====================

    /**
     * Style class of the ring sub-structure. The self-drawn indicator deliberately
     * reuses the native {@code .radio} class (so author CSS targets one name), which
     * is exactly why {@link #updateChildren()} must match it by identity as well as
     * by class to tell the two apart.
     */
    private static final String RADIO_STYLE_CLASS = "radio";

    // ==================== Fields ====================

    private final SkinDisposer disposer = new SkinDisposer();

    private final StackPane indicator = new StackPane();    // self-drawn ring, replaces the native .radio
    private final StateLayer stateLayer = new StateLayer();  // circular halo (below the dot)
    private final RippleLayer rippleLayer = new RippleLayer(); // M2 press ink (above the halo, below the dot)
    private final StackPane dot = new StackPane();           // inner point (scale animation)

    /** Ink colour; follows the halo's CSS-resolved {@code -rx-state-overlay-color} (no Control property). */
    private final ObjectProperty<Paint> rippleFill = new SimpleObjectProperty<>(this, "rippleFill", null);
    private final RippleBehavior rippleBehavior =
            new RippleBehavior(rippleLayer, rippleFill::get, () -> RXRipplePane.DEFAULT_RIPPLE_OPACITY);

    /** Single driver in {@code [0, 1]}; a listener maps it to {@code scaleX/scaleY} + opacity. */
    private final DoubleProperty dotScale = new SimpleDoubleProperty(this, "dotScale", 0.0);

    /** Single reusable scale animation; rebuilt per toggle, so never registered with the disposer. */
    private Timeline timeline;

    /**
     * True while a primary button is held on the indicator; scopes the pressed tier and
     * the press ink to the ring and keeps them in lockstep (both drop on release or on a
     * drag off the ring, so the halo and the ink never disagree).
     */
    private boolean pressedOnIndicator;

    /**
     * True when the current focus arrived via a pointer press, so the focus tier is
     * suppressed — a JFX17 stand-in for {@code :focus-visible} (added in JFX19), which
     * shows the focus state only for keyboard focus. Matches {@link RXSwitchButtonSkin} /
     * {@link RXCheckBoxSkin}; set by a capture-phase press filter (the inherited behavior
     * owns {@code requestFocus}, so the filter must run before it) and reset on focus loss.
     */
    private boolean mouseFocus;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given radio button.
     *
     * @param control the radio button this skin is attached to
     */
    public RXRadioButtonSkin(RXRadioButton control) {
        super(control);   // installs ToggleButtonBehavior + the native .radio

        indicator.getStyleClass().add(RADIO_STYLE_CLASS);
        dot.getStyleClass().add("dot");
        dot.setManaged(false);
        dot.setMouseTransparent(true);
        // Halo + ink + dot are unmanaged children of the indicator: the halo overflows
        // the ring (a StackPane does not clip), and the skin positions them by hand.
        // z-order: halo (steady tint) and ink (press ripple) below the dot. The halo
        // keeps the default ClipMode.NONE; its round shape + colour are CSS driven.
        indicator.getChildren().setAll(stateLayer, rippleLayer, dot);

        updateChildren();   // remove the native .radio, add the self-drawn indicator

        // Initial snap: the first frame is not animated (avoids a pop-in on show).
        dotScale.set(control.isSelected() ? 1.0 : 0.0);
        applyDotScale();

        disposer.registerListener(dotScale, this::applyDotScale);
        disposer.registerListener(control.selectedProperty(), this::handleSelectedChanged);
        // Halo + ink event source scoped to the indicator: hovering / pressing the label
        // does not light up the ring (selection itself stays whole-control clickable via
        // the inherited ToggleButtonBehavior). hover = indicator.hoverProperty()
        // (framework-maintained, like the sibling box / track); pressed = a primary press
        // on the ring (tracked so the pressed tier and the ink drop together); focus =
        // keyboard focus; dragged is never active (a radio cannot be dragged).
        disposer.registerListener(indicator.hoverProperty(), this::updateHalo);
        disposer.registerEventHandler(indicator, MouseEvent.MOUSE_PRESSED, this::onIndicatorPressed);
        disposer.registerEventHandler(indicator, MouseEvent.MOUSE_RELEASED, event -> endPress());
        // Dragging off the ring while held ends the press feedback (the pressed tier + ink).
        disposer.registerEventHandler(indicator, MouseEvent.MOUSE_EXITED, event -> endPress());
        // Any pointer press (ring OR label) marks the resulting focus as pointer-driven so
        // the focus tier stays suppressed (:focus-visible stand-in). A capture-phase filter
        // runs before the inherited behavior's requestFocus, so the focus listener sees it.
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, event -> mouseFocus = true);
        disposer.registerListener(control.focusedProperty(), this::handleFocusChanged);
        disposer.registerListener(control.disabledProperty(), this::handleDisabledChanged);
        disposer.registerListener(control.radioPositionProperty(), control::requestLayout);
        // The press ink shares the halo's CSS-resolved colour (Pattern B: no Control property).
        disposer.registerListener(stateLayer.backgroundProperty(), this::syncRippleFill);
        syncRippleFill();

        updateHalo();
    }

    /**
     * {@link RadioButtonSkin} is parameterized on {@code RadioButton}, so cast the
     * skinnable back to the concrete control type. The skin is only ever created for
     * an {@link RXRadioButton}, so the cast is safe.
     *
     * @return the skinned control
     */
    private RXRadioButton getControl() {
        return (RXRadioButton) getSkinnable();
    }

    // ==================== Children ====================

    @Override
    protected void updateChildren() {
        super.updateChildren();   // installs text / graphic + the native .radio
        if (indicator == null) {  // first call comes from the RadioButtonSkin constructor
            return;
        }
        // Remove the native .radio (a StackPane with style class "radio" injected by
        // the super skin); the identity check guards against deleting our own indicator,
        // which carries the same style class.
        getChildren().removeIf(node ->
                node != indicator && node.getStyleClass().contains(RADIO_STYLE_CLASS));
        if (!getChildren().contains(indicator)) {
            getChildren().add(indicator);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXRadioButton control = getControl();

        double indW = snapSizeX(indicator.prefWidth(-1));
        double indH = snapSizeY(indicator.prefHeight(-1));

        double labelW = Math.max(0.0, w - indW);
        // The ring side is the radioPosition; right-to-left is handled by JavaFX's
        // automatic node-orientation mirroring (the ring and the :left/:right
        // label-padding flip together), so no direction is handled here — matching
        // RXCheckBoxSkin / RXSwitchButtonSkin.
        boolean ringOnLeft = radioPositionOrDefault() != HorizontalDirection.RIGHT;
        double indX = snapPositionX(ringOnLeft ? x : x + labelW);
        double labelX = ringOnLeft ? x + indW : x;

        layoutLabelInArea(labelX, y, labelW, h, control.getAlignment());

        // resizeRelocate (not positionInArea): the ring must be resized to its pref so
        // the border and all its :state colours actually paint (Region defaults to 0x0).
        double indY = snapPositionY(y + (h - indH) / 2.0);
        indicator.resizeRelocate(indX, indY, indW, indH);

        // Dot centred in the indicator (indicator-local coordinates); unmanaged, so the
        // StackPane does not lay it out.
        double dotW = snapSizeX(dot.prefWidth(-1));
        double dotH = snapSizeY(dot.prefHeight(-1));
        dot.resize(dotW, dotH);
        dot.relocate(snapPositionX((indW - dotW) / 2.0), snapPositionY((indH - dotH) / 2.0));

        // Halo centred on the indicator and resized square (a 50% background radius
        // rounds it); larger than the ring, so it reads as a circle around it. resize is
        // required — relocate alone leaves it 0x0 and invisible.
        double haloD = snapSizeX(stateLayer.prefWidth(-1));
        double haloX = snapPositionX((indW - haloD) / 2.0);
        double haloY = snapPositionY((indH - haloD) / 2.0);
        stateLayer.resize(haloD, haloD);
        stateLayer.relocate(haloX, haloY);

        // Press ink shares the halo geometry (centred on the ring), clipped to a circle
        // so the M2 ripple stays round within the touch region.
        rippleLayer.resizeRelocate(haloX, haloY, haloD, haloD);
        Circle clip = (Circle) rippleLayer.getClip();
        if (clip == null) {
            clip = new Circle();
            rippleLayer.setClip(clip);
        }
        clip.setCenterX(haloD / 2.0);
        clip.setCenterY(haloD / 2.0);
        clip.setRadius(haloD / 2.0);
    }

    // The native .radio is removed from the children before any CSS pass, so its
    // prefWidth / prefHeight are 0; the inherited RadioButtonSkin.compute* therefore
    // reduce to the LabeledSkinBase label metrics, to which we add the self-drawn
    // indicator's size (matching the RXCheckBoxSkin structure).

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(indicator.minWidth(-1));
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Math.max(
                super.computeMinHeight(width - indicator.minWidth(-1), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(indicator.minHeight(-1)) + bottomInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(indicator.prefWidth(-1));
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return Math.max(
                super.computePrefHeight(width - indicator.prefWidth(-1), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(indicator.prefHeight(-1)) + bottomInset);
    }

    // ==================== Dot animation ====================

    private void handleSelectedChanged() {
        animateDotTo(getControl().isSelected() ? 1.0 : 0.0);
    }

    private void animateDotTo(double target) {
        Duration duration = animationDurationOrDefault();
        if (!isAnimatable(duration)) {
            // Non-positive / unusable duration: snap to the target rather than feeding
            // the Timeline a degenerate duration. A short one-shot tween needs no
            // tree-showing gate (AGENTS §3.1): an off-screen toggle just completes
            // invisibly, leaving the same end state.
            if (timeline != null) {
                timeline.stop();
            }
            dotScale.set(target);
            return;
        }
        if (timeline == null) {
            timeline = new Timeline();
        } else {
            timeline.stop();
        }
        // KeyValue starts from the current dotScale (no explicit fromValue), so a
        // mid-flight reversal continues smoothly instead of jumping.
        KeyValue keyValue = new KeyValue(dotScale, target, interpolatorOrDefault());
        timeline.getKeyFrames().setAll(new KeyFrame(duration, keyValue));
        timeline.play();
    }

    private void applyDotScale() {
        double s = dotScale.get();
        dot.setScaleX(s);
        dot.setScaleY(s);
        dot.setOpacity(s);   // fade in / out in parallel with the scale (Material)
    }

    private Duration animationDurationOrDefault() {
        Duration value = getControl().getAnimationDuration();
        return value == null ? RXRadioButton.DEFAULT_ANIMATION_DURATION : value;
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getControl().getAnimationInterpolator();
        return value == null ? RXRadioButton.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private HorizontalDirection radioPositionOrDefault() {
        HorizontalDirection value = getControl().getRadioPosition();
        return value == null ? RXRadioButton.DEFAULT_RADIO_POSITION : value;
    }

    private static boolean isAnimatable(Duration duration) {
        return !duration.isUnknown() && !duration.isIndefinite()
                && Double.isFinite(duration.toMillis()) && duration.toMillis() > 0.0;
    }

    private void stopDotAnimation() {
        // Rebuilt across toggles; the disposer would hold a stale reference, so stop the
        // live field directly (AGENTS §2.8).
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    // ==================== State overlay (halo) + press ink ====================

    private void updateHalo() {
        RXRadioButton control = getControl();
        // No stateOverlayEnabled property (Pattern B): to turn the halo off, set the
        // tier opacities to 0 via CSS. A disabled control shows no halo.
        boolean enabled = !control.isDisabled();
        // Focus-visible stand-in: show the focus tier only for keyboard focus, not after a
        // pointer click (mouseFocus), even when the click landed on the label.
        boolean focusVisible = control.isFocused() && !mouseFocus;
        stateLayer.setState(
                enabled && indicator.isHover(),     // hover: pointer over the ring (framework-maintained)
                enabled && focusVisible,            // focus: keyboard focus only
                enabled && pressedOnIndicator,      // pressed: a primary press on the ring
                false);                             // dragged: a radio cannot be dragged
    }

    private void handleFocusChanged() {
        if (!getControl().isFocused()) {
            // Reset so the next keyboard Tab focus is focus-visible (shows the focus tier).
            mouseFocus = false;
        }
        updateHalo();
    }

    private void onIndicatorPressed(MouseEvent event) {
        if (validPrimaryPress(event) && !getControl().isDisabled() && !pressedOnIndicator) {
            // A press on the ring lights the pressed tier and a centred ink together; a
            // press on the label does not reach this handler, so it selects without either.
            pressedOnIndicator = true;
            rippleBehavior.press(0.0, 0.0, true);
            updateHalo();
        }
    }

    private void endPress() {
        // Drop the pressed tier and the ink together — on button-up or when the pointer
        // drags off the ring while held — so the halo and the ink never disagree.
        pressedOnIndicator = false;
        rippleBehavior.release();
        updateHalo();
    }

    private void handleDisabledChanged() {
        if (getControl().isDisabled()) {
            // A disabled node may never see the release, so end the press feedback rather
            // than strand it (matching the RXSlider lifecycle). Hover self-heals via
            // indicator.hoverProperty(), so no manual hover reset is needed.
            pressedOnIndicator = false;
            rippleBehavior.clear();
        }
        updateHalo();
    }

    private void syncRippleFill() {
        Background background = stateLayer.getBackground();
        if (background != null && !background.getFills().isEmpty()) {
            rippleFill.set(background.getFills().get(0).getFill());
        }
    }

    private static boolean validPrimaryPress(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && !(event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown());
    }

    // ==================== Dispose ====================

    /**
     * Stops the rebuilt dot timeline and the press ink, disposes the halo overlay, runs
     * the disposer (listeners, handlers) and then the standard {@link RadioButtonSkin}
     * cleanup (which disposes the inherited behavior), in that order. Safe to call more
     * than once.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::stopDotAnimation, rippleBehavior::clear,
                stateLayer::dispose, disposer::dispose, super::dispose);
    }
}
