package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAnimatedButton;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Skin for {@link RXTransitionButton}: a ripple layer below a double-page
 * face surface. The front face mirrors the button's own text and graphic in
 * an internal label; the alternate face hosts the
 * {@code alternateContent} node. Face changes are driven by the trigger
 * state and played through the shared {@link TransitionPages} surface.
 *
 * <p>This skin does not extend {@code ButtonSkin} (the faces need to own the
 * text and graphic nodes, which {@code LabeledSkinBase} would compete for),
 * so the standard button behavior is wired here through the public
 * {@code arm()}/{@code disarm()}/{@code fire()} API: mouse arming, SPACE
 * (and ENTER on non-Mac platforms) activation, disarm on focus loss, and
 * the default/cancel button accelerators. Mnemonics and arrow-key focus
 * traversal rely on JavaFX-internal APIs and are not supported.</p>
 */
public class RXTransitionButtonSkin extends RXSkinBase<RXTransitionButton> {

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    private static final KeyCombination DEFAULT_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCombination CANCEL_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ESCAPE);

    // ==================== Nodes ====================

    private final TransitionPages pages = new TransitionPages("page", true);
    private final Label frontLabel = new Label();
    private final RippleDecoration ripple;
    private final ArmedRippleTrigger rippleTrigger;

    // ==================== Behavior state ====================

    private boolean keyDown;

    private final Runnable defaultButtonRunnable = () -> fireFromAccelerator();
    private final Runnable cancelButtonRunnable = () -> fireFromAccelerator();

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given button.
     *
     * @param button the button this skin is attached to
     */
    public RXTransitionButtonSkin(RXTransitionButton button) {
        super(button);

        ripple = new RippleDecoration(button, button.rippleEnabledProperty(),
                button.stateOverlayEnabledProperty(), button.rippleFillProperty(),
                button::getRippleOpacity, null, button.rippleCornerRadiusProperty());
        rippleTrigger = new ArmedRippleTrigger(button, ripple,
                button::isRippleEnabled, button::isRippleCentered);
        getChildren().addAll(ripple.getLayer(), pages.getContentPane());

        // Front face: an internal label mirroring the button's Labeled API.
        // mnemonicParsing is deliberately not mirrored: the label would render
        // the mnemonic affordance without firing the button.
        frontLabel.setMnemonicParsing(false);
        frontLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        pages.getPageA().getChildren().add(frontLabel);
        bindFrontLabel(button);

        setPageContent(pages.getPageB(), button.getAlternateContent());

        wireFaces(button);
        wireBehavior(button);
        wireAccelerators(button);
    }

    private void bindFrontLabel(RXTransitionButton button) {
        disposer.registerBinding(frontLabel.textProperty(), button.textProperty());
        disposer.registerBinding(frontLabel.graphicProperty(), button.graphicProperty());
        disposer.registerBinding(frontLabel.fontProperty(), button.fontProperty());
        disposer.registerBinding(frontLabel.textFillProperty(), button.textFillProperty());
        disposer.registerBinding(frontLabel.ellipsisStringProperty(), button.ellipsisStringProperty());
        disposer.registerBinding(frontLabel.contentDisplayProperty(), button.contentDisplayProperty());
        disposer.registerBinding(frontLabel.graphicTextGapProperty(), button.graphicTextGapProperty());
        disposer.registerBinding(frontLabel.alignmentProperty(), button.alignmentProperty());
        disposer.registerBinding(frontLabel.textAlignmentProperty(), button.textAlignmentProperty());
        disposer.registerBinding(frontLabel.textOverrunProperty(), button.textOverrunProperty());
        disposer.registerBinding(frontLabel.wrapTextProperty(), button.wrapTextProperty());
        disposer.registerBinding(frontLabel.underlineProperty(), button.underlineProperty());
        disposer.registerBinding(frontLabel.lineSpacingProperty(), button.lineSpacingProperty());
    }

    private void wireFaces(RXTransitionButton button) {
        disposer.registerListener(button.hoverProperty(), this::updateFace);
        disposer.registerListener(button.armedProperty(), () -> {
            rippleTrigger.handleArmedChanged();
            updateFace();
        });
        disposer.registerListener(button.animationTriggerProperty(), this::updateFace);
        disposer.registerListener(button.alternateContentProperty(),
                () -> setPageContent(pages.getPageB(), button.getAlternateContent()));
        disposer.registerEventHandler(button, RXAnimationEvent.PLAY_ANIMATION, event -> {
            // Reject events bubbling up from a nested animation host.
            if (event.getTarget() != button) {
                return;
            }
            event.consume();
            playOnce();
        });
    }

    private void wireBehavior(RXTransitionButton button) {
        // Shared ripple pointer tracking (records the press location for the next arm).
        rippleTrigger.installPointerTracking(disposer);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_PRESSED, this::mousePressed);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_RELEASED, this::mouseReleased);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_ENTERED, this::mouseEntered);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_EXITED, this::mouseExited);
        disposer.registerEventHandler(button, KeyEvent.KEY_PRESSED, this::keyPressed);
        disposer.registerEventHandler(button, KeyEvent.KEY_RELEASED, this::keyReleased);
        // If the key went down but focus left, the button must disarm or it
        // would stay armed forever.
        disposer.registerListener(button.focusedProperty(), () -> {
            if (keyDown && !button.isFocused()) {
                keyDown = false;
                button.disarm();
            }
        });
    }

    private void wireAccelerators(RXTransitionButton button) {
        disposer.registerListener(button.defaultButtonProperty(),
                () -> setAccelerator(button.getScene(), DEFAULT_BUTTON_COMBO,
                        defaultButtonRunnable, button.isDefaultButton()));
        disposer.registerListener(button.cancelButtonProperty(),
                () -> setAccelerator(button.getScene(), CANCEL_BUTTON_COMBO,
                        cancelButtonRunnable, button.isCancelButton()));
        disposer.registerListener(button.sceneProperty(), (observable, oldScene, newScene) -> {
            if (oldScene != null) {
                setAccelerator(oldScene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable, false);
                setAccelerator(oldScene, CANCEL_BUTTON_COMBO, cancelButtonRunnable, false);
            }
            if (newScene != null) {
                setAccelerator(newScene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable,
                        button.isDefaultButton());
                setAccelerator(newScene, CANCEL_BUTTON_COMBO, cancelButtonRunnable,
                        button.isCancelButton());
            }
        });
        setAccelerator(button.getScene(), DEFAULT_BUTTON_COMBO, defaultButtonRunnable,
                button.isDefaultButton());
        setAccelerator(button.getScene(), CANCEL_BUTTON_COMBO, cancelButtonRunnable,
                button.isCancelButton());
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
        pages.layout(x, y, w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxFaceWidth(false, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxFaceHeight(false, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxFaceWidth(true, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxFaceHeight(true, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Buttons do not stretch beyond their preferred size, matching
        // LabeledSkinBase: layout panes clamp to this unless the user
        // raises maxWidth explicitly.
        return getSkinnable().prefWidth(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // The button keeps a stable size across the face swap: both faces count.
    // The hint flows to both faces so a content-bias face (e.g. wrapText) is
    // measured against the real cross-axis extent; a non-content-bias face
    // ignores the hint and returns its natural size, so forwarding is safe.
    private double maxFaceWidth(boolean min, double height) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minWidth(height), pageB.minWidth(height))
                : Math.max(pageA.prefWidth(height), pageB.prefWidth(height));
    }

    private double maxFaceHeight(boolean min, double width) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minHeight(width), pageB.minHeight(width))
                : Math.max(pageA.prefHeight(width), pageB.prefHeight(width));
    }

    // ==================== Faces ====================

    private void updateFace() {
        boolean targetAlternate = isTriggerActive();
        boolean showingAlternate = pages.getCurrentPage() == pages.getPageB();
        if (targetAlternate != showingAlternate) {
            showFace(targetAlternate, null);
        }
    }

    private boolean isTriggerActive() {
        RXTransitionButton button = getSkinnable();
        return switch (triggerOrDefault(button)) {
            case HOVER -> button.isHover();
            case PRESSED -> button.isArmed();
            case NONE -> false;
        };
    }

    private void showFace(boolean alternate, Runnable onSettledExtra) {
        pages.interrupt();

        RXTransitionButton button = getSkinnable();
        PageAnimation animation = button.getAnimation();
        TransitionDirection direction =
                alternate ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        Duration duration = durationOrDefault(button);

        pages.clearEffectsIfChanged(animation, direction, duration);

        StackPane target = alternate ? pages.getPageB() : pages.getPageA();
        boolean animate = button.getAlternateContent() != null
                && PageTransitionEngine.canAnimate(animation, true, 2, duration, false);
        if (!animate) {
            pages.directCutTo(target);
            button.setTransitioning(false);
            if (onSettledExtra != null) {
                onSettledExtra.run();
            }
            return;
        }

        pages.transitionTo(target, animation, direction, duration,
                () -> button.setTransitioning(true),
                () -> {
                    button.setTransitioning(false);
                    if (onSettledExtra != null) {
                        onSettledExtra.run();
                    }
                },
                () -> button.setTransitioning(false));
    }

    // Plays forward once, then converges back to the current trigger state.
    // Per the RXAnimatedButton contract there is no visible effect when the
    // alternate face is already showing, the button is disabled, or the
    // animation cannot play (null alternate content, zero duration).
    private void playOnce() {
        RXTransitionButton button = getSkinnable();
        if (button.isDisabled() || pages.getCurrentPage() == pages.getPageB()) {
            return;
        }
        boolean animate = button.getAlternateContent() != null
                && PageTransitionEngine.canAnimate(button.getAnimation(), true, 2,
                        durationOrDefault(button), false);
        if (!animate) {
            return;
        }
        showFace(true, this::updateFace);
    }

    private static void setPageContent(StackPane page, Node content) {
        if (content == null) {
            page.getChildren().clear();
        } else {
            page.getChildren().setAll(content);
        }
    }

    private static AnimationTrigger triggerOrDefault(RXTransitionButton button) {
        AnimationTrigger trigger = button.getAnimationTrigger();
        return trigger == null ? RXAnimatedButton.DEFAULT_ANIMATION_TRIGGER : trigger;
    }

    // Normalizes the duration per the RXAnimatedButton contract: null,
    // negative or otherwise unusable values fall back to the default and
    // still animate; only Duration.ZERO disables the animation (it fails the
    // engine gate and falls back to a direct cut).
    private static Duration durationOrDefault(RXTransitionButton button) {
        Duration duration = button.getAnimationDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThan(Duration.ZERO) || !Double.isFinite(duration.toMillis())) {
            return RXAnimatedButton.DEFAULT_ANIMATION_DURATION;
        }
        return duration;
    }

    // ==================== Button Behavior ====================

    // All handlers consume their event, matching the auto-consume default of
    // the standard ButtonBehavior input mappings. For activation keys this is
    // load-bearing: scene accelerators only run for unconsumed events, so the
    // focused button must consume ENTER or a default button would also fire.

    private void mousePressed(MouseEvent event) {
        RXTransitionButton button = getSkinnable();
        if (button.isFocusTraversable()) {
            button.requestFocus();
        }
        boolean valid = event.getButton() == MouseButton.PRIMARY
                && !(event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown());
        if (!button.isArmed() && valid) {
            button.arm();
        }
        event.consume();
    }

    private void mouseReleased(MouseEvent event) {
        RXTransitionButton button = getSkinnable();
        if (!keyDown && button.isArmed()) {
            button.fire();
            button.disarm();
        }
        event.consume();
    }

    private void mouseEntered(MouseEvent event) {
        RXTransitionButton button = getSkinnable();
        if (!keyDown && button.isPressed()) {
            button.arm();
        }
        event.consume();
    }

    private void mouseExited(MouseEvent event) {
        RXTransitionButton button = getSkinnable();
        if (!keyDown && button.isArmed()) {
            button.disarm();
        }
        event.consume();
    }

    private void keyPressed(KeyEvent event) {
        if (!isActivationKey(event)) {
            return;
        }
        RXTransitionButton button = getSkinnable();
        if (!button.isPressed() && !button.isArmed()) {
            keyDown = true;
            button.arm();
        }
        event.consume();
    }

    private void keyReleased(KeyEvent event) {
        if (!isActivationKey(event)) {
            return;
        }
        if (keyDown) {
            keyDown = false;
            RXTransitionButton button = getSkinnable();
            if (button.isArmed()) {
                button.disarm();
                button.fire();
            }
        }
        event.consume();
    }

    // SPACE always activates; ENTER activates on non-Mac platforms only,
    // matching the standard ButtonBehavior platform split.
    private static boolean isActivationKey(KeyEvent event) {
        return event.getCode() == KeyCode.SPACE
                || (event.getCode() == KeyCode.ENTER && !MAC);
    }

    private void fireFromAccelerator() {
        RXTransitionButton button = getSkinnable();
        if (button != null && button.getScene() != null
                && button.isVisible() && !button.isDisabled()) {
            button.fire();
        }
    }

    private void setAccelerator(Scene scene, KeyCombination combo, Runnable runnable, boolean set) {
        if (scene == null) {
            return;
        }
        if (set) {
            scene.getAccelerators().put(combo, runnable);
        } else if (scene.getAccelerators().get(combo) == runnable) {
            scene.getAccelerators().remove(combo);
        }
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        RXTransitionButton button = getSkinnable();
        if (button != null) {
            Scene scene = button.getScene();
            setAccelerator(scene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable, false);
            setAccelerator(scene, CANCEL_BUTTON_COMBO, cancelButtonRunnable, false);
            button.setTransitioning(false);
        }
        ripple.dispose();
        pages.dispose(button == null ? null : button.getAnimation());
        getChildren().clear();
    }
}
