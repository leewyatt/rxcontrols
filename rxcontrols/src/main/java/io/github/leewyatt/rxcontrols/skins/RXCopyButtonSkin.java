package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Skin for {@link RXCopyButton}: a ripple layer below two faces. The normal
 * face shows a copy indicator next to the button's own text and graphic; the
 * feedback face shows a check indicator next to the copied text and graphic.
 * The button
 * is sized to the larger of the two faces, so swapping faces never changes
 * its size.
 *
 * <p>The skin owns the feedback state: it swaps to the feedback face and sets
 * the {@code copied} pseudo-class on the button after a successful copy, and
 * a single timer swaps back. Swaps play the button's animation when one is
 * set and cut directly otherwise.</p>
 *
 * <p>This skin does not extend {@code ButtonSkin} (the faces own the text and
 * graphic nodes), so the standard button behavior is wired here through the
 * public {@code arm()}/{@code disarm()}/{@code fire()} API: mouse arming,
 * SPACE (and ENTER on non-Mac platforms) activation, disarm on focus loss,
 * and the default/cancel button accelerators. Mnemonics are not supported.</p>
 */
public class RXCopyButtonSkin extends RXSkinBase<RXCopyButton> {

    private static final boolean MAC = RXOS.isMacOS();
    private static final KeyCombination DEFAULT_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCombination CANCEL_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ESCAPE);
    private static final PseudoClass COPIED = PseudoClass.getPseudoClass("copied");

    // ==================== Nodes ====================

    private final TransitionPages pages = new TransitionPages("page", true);
    private final Face normalFace;
    private final Face copiedFace;
    private final RippleDecoration ripple;
    private final ArmedRippleTrigger rippleTrigger;

    // ==================== Feedback state ====================

    private final PauseTransition feedbackTimer = new PauseTransition();

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
    public RXCopyButtonSkin(RXCopyButton button) {
        super(button);

        ripple = new RippleDecoration(button, button.rippleEnabledProperty(),
                button.stateOverlayEnabledProperty(), button.rippleFillProperty(),
                button::getRippleOpacity, null, button.rippleCornerRadiusProperty());
        rippleTrigger = new ArmedRippleTrigger(button, ripple,
                button::isRippleEnabled, button::isRippleCentered);
        normalFace = new Face(button, "copy-face");
        copiedFace = new Face(button, "copied-face");
        pages.getPageA().getChildren().add(normalFace);
        pages.getPageB().getChildren().add(copiedFace);
        getChildren().addAll(ripple.getLayer(), pages.getContentPane());

        // Registered first, so it runs last: by then the result listener and
        // the timer are gone and nothing can swap the face again.
        disposer.registerDisposeTask(() -> {
            // A direct cut: disposing must not start a transition.
            pages.interrupt();
            pages.directCutTo(pages.getPageA());
            button.pseudoClassStateChanged(COPIED, false);
            pages.dispose(button.getAnimation());
            // Only this skin's nodes: a replacement skin may already have
            // added its own children to the control.
            getChildren().removeAll(ripple.getLayer(), pages.getContentPane());
        });

        bindCaptions(button);
        wireBehavior(button);
        wireAccelerators(button);
        wireFeedback(button);
    }

    private void bindCaptions(RXCopyButton button) {
        Label normal = normalFace.caption;
        disposer.registerBinding(normal.textProperty(), button.textProperty());
        disposer.registerBinding(normal.graphicProperty(), button.graphicProperty());
        bindAppearance(normal, button);

        // The feedback face has its own graphic: the button graphic cannot be
        // parented by both faces.
        Label copied = copiedFace.caption;
        disposer.registerBinding(copied.graphicProperty(), button.copiedGraphicProperty());
        bindAppearance(copied, button);
        disposer.registerListener(button.textProperty(), this::updateCopiedCaption);
        disposer.registerListener(button.copiedTextProperty(), this::updateCopiedCaption);
        updateCopiedCaption();

        // The gap between indicator and caption is laid out by the faces.
        disposer.registerListener(button.graphicTextGapProperty(), () -> {
            normalFace.requestLayout();
            copiedFace.requestLayout();
        });
    }

    private void bindAppearance(Label caption, RXCopyButton button) {
        disposer.registerBinding(caption.fontProperty(), button.fontProperty());
        disposer.registerBinding(caption.textFillProperty(), button.textFillProperty());
        disposer.registerBinding(caption.ellipsisStringProperty(), button.ellipsisStringProperty());
        disposer.registerBinding(caption.contentDisplayProperty(), button.contentDisplayProperty());
        disposer.registerBinding(caption.graphicTextGapProperty(), button.graphicTextGapProperty());
        disposer.registerBinding(caption.alignmentProperty(), button.alignmentProperty());
        disposer.registerBinding(caption.textAlignmentProperty(), button.textAlignmentProperty());
        disposer.registerBinding(caption.textOverrunProperty(), button.textOverrunProperty());
        disposer.registerBinding(caption.wrapTextProperty(), button.wrapTextProperty());
        disposer.registerBinding(caption.underlineProperty(), button.underlineProperty());
        disposer.registerBinding(caption.lineSpacingProperty(), button.lineSpacingProperty());
    }

    private void wireBehavior(RXCopyButton button) {
        // Shared ripple pointer tracking (records the press location for the next arm).
        rippleTrigger.installPointerTracking(disposer);
        rippleTrigger.installPlayRipple(disposer);
        disposer.registerListener(button.armedProperty(), rippleTrigger::handleArmedChanged);
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

    private void wireAccelerators(RXCopyButton button) {
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

    private void wireFeedback(RXCopyButton button) {
        feedbackTimer.setOnFinished(event -> showFace(false));
        disposer.registerDisposeTask(feedbackTimer::stop);
        // A result posted before this skin existed is stale: start on the normal face.
        button.getProperties().remove(FeedbackSignals.COPY_RESULT);
        disposer.registerListener(button.getProperties(), this::consumeCopyResult);
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

    // ==================== Feedback ====================

    // Every posted result is the outcome of the latest activation, so it
    // replaces whatever feedback is showing: the timer always stops, and the
    // feedback face only comes back for a successful copy with a positive
    // duration.
    private void consumeCopyResult() {
        // Null for unrelated keys and for the notification of this removal.
        Object result = getSkinnable().getProperties().remove(FeedbackSignals.COPY_RESULT);
        if (result == null) {
            return;
        }
        feedbackTimer.stop();
        Duration duration = feedbackDurationOrDefault();
        boolean feedback = Boolean.TRUE.equals(result) && duration.greaterThan(Duration.ZERO);
        showFace(feedback);
        // An indefinite duration keeps the feedback until the next result.
        if (feedback && !duration.isIndefinite()) {
            feedbackTimer.setDuration(duration);
            feedbackTimer.playFromStart();
        }
    }

    private void showFace(boolean copied) {
        StackPane target = copied ? pages.getPageB() : pages.getPageA();
        if (pages.getCurrentPage() == target) {
            return;
        }
        RXCopyButton button = getSkinnable();
        // Latest wins: a running swap jumps to its end before the next one.
        pages.interrupt();
        PageAnimation animation = button.getAnimation();
        TransitionDirection direction =
                copied ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        Duration duration = animationDurationOrDefault();
        pages.clearEffectsIfChanged(animation, direction, duration);
        if (PageTransitionEngine.canAnimate(animation, true, 2, duration, false)) {
            pages.transitionTo(target, animation, direction, duration, null, null, null);
        } else {
            pages.directCutTo(target);
        }
        button.pseudoClassStateChanged(COPIED, copied);
        button.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    private Duration feedbackDurationOrDefault() {
        Duration duration = getSkinnable().getFeedbackDuration();
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return RXCopyButton.DEFAULT_FEEDBACK_DURATION;
        }
        return duration;
    }

    // Only Duration.ZERO turns the animation off (it fails the engine gate and
    // falls back to a direct cut); unusable values still animate.
    private Duration animationDurationOrDefault() {
        Duration duration = getSkinnable().getAnimationDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThan(Duration.ZERO)) {
            return RXCopyButton.DEFAULT_ANIMATION_DURATION;
        }
        return duration;
    }

    private void updateCopiedCaption() {
        copiedFace.caption.setText(copiedTextOrText());
    }

    private String copiedTextOrText() {
        RXCopyButton button = getSkinnable();
        String copiedText = button.getCopiedText();
        return copiedText == null ? button.getText() : copiedText;
    }

    // ==================== Accessibility ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.TEXT) {
            RXCopyButton button = getSkinnable();
            String accessibleText = button.getAccessibleText();
            if (accessibleText != null && !accessibleText.isEmpty()) {
                return accessibleText;
            }
            // An empty copied text is a visual choice (indicator-only
            // feedback); the button still needs a name.
            String copiedText = copiedTextOrText();
            boolean copied = pages.getCurrentPage() == pages.getPageB()
                    && copiedText != null && !copiedText.isEmpty();
            return copied ? copiedText : button.getText();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    // ==================== Button Behavior ====================

    // All handlers consume their event, matching the auto-consume default of
    // the standard ButtonBehavior input mappings. For activation keys this is
    // load-bearing: scene accelerators only run for unconsumed events, so the
    // focused button must consume ENTER or a default button would also fire.
    // Other keys are left alone, so the scene still handles focus traversal.

    private void mousePressed(MouseEvent event) {
        RXCopyButton button = getSkinnable();
        if (button.isFocusTraversable()) {
            button.requestFocus();
        }
        if (!button.isArmed() && RXMouse.isPlainPrimaryPress(event)) {
            button.arm();
        }
        event.consume();
    }

    private void mouseReleased(MouseEvent event) {
        RXCopyButton button = getSkinnable();
        if (!keyDown && button.isArmed()) {
            // A throwing content factory propagates out of fire(); the button
            // must not stay armed behind it.
            try {
                button.fire();
            } finally {
                button.disarm();
            }
        }
        event.consume();
    }

    private void mouseEntered(MouseEvent event) {
        RXCopyButton button = getSkinnable();
        if (!keyDown && button.isPressed()) {
            button.arm();
        }
        event.consume();
    }

    private void mouseExited(MouseEvent event) {
        RXCopyButton button = getSkinnable();
        if (!keyDown && button.isArmed()) {
            button.disarm();
        }
        event.consume();
    }

    private void keyPressed(KeyEvent event) {
        // A modified press (e.g. Ctrl+ENTER) is not an activation: it stays
        // unconsumed for application shortcuts.
        if (!isActivationKey(event) || hasModifier(event)) {
            return;
        }
        RXCopyButton button = getSkinnable();
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
            RXCopyButton button = getSkinnable();
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

    private static boolean hasModifier(KeyEvent event) {
        return event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    private void fireFromAccelerator() {
        RXCopyButton button = getSkinnable();
        if (button != null && button.getScene() != null
                && isTreeVisible(button) && !button.isDisabled()) {
            button.fire();
        }
    }

    // A button inside a hidden ancestor (an unselected tab, a collapsed pane)
    // is still in the scene but must not answer ENTER or ESCAPE.
    private static boolean isTreeVisible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
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
        RXCopyButton button = getSkinnable();
        if (button != null) {
            // The scene may have changed since construction: read the current one.
            Scene scene = button.getScene();
            setAccelerator(scene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable, false);
            setAccelerator(scene, CANCEL_BUTTON_COMBO, cancelButtonRunnable, false);
        }
        ripple.dispose();
    }

    // ==================== Face ====================

    /**
     * One face of the button: an indicator followed by a caption, placed as
     * one block by the button's alignment. The gap between the two only exists
     * while both have a width, so a zero-sized indicator or an empty caption
     * leaves no hole.
     */
    private static final class Face extends Region {

        private final RXCopyButton button;
        private final Region indicator = new Region();
        private final Label caption = new Label();

        Face(RXCopyButton button, String styleClass) {
            this.button = button;
            getStyleClass().add(styleClass);

            indicator.getStyleClass().add("indicator");
            indicator.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
            indicator.setMouseTransparent(true);
            // Neither shape has a direction: in a right-to-left button the
            // indicator moves to the other side but is not drawn mirrored.
            indicator.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

            caption.setMnemonicParsing(false);
            // setAll drops the built-in .label class. The caption's text fill
            // and font are bound to the button, so .label rules cannot style
            // it anyway, and .label:disabled would dim it a second time inside
            // the disabled button.
            caption.getStyleClass().setAll("caption");

            getChildren().addAll(indicator, caption);
        }

        @Override
        public Orientation getContentBias() {
            return caption.getContentBias();
        }

        private double indicatorWidth() {
            return snapSizeX(indicator.prefWidth(-1));
        }

        private double indicatorHeight() {
            return snapSizeY(indicator.prefHeight(-1));
        }

        private double gap(double indicatorWidth) {
            return indicatorWidth > 0.0 && caption.prefWidth(-1) > 0.0
                    ? snapSpaceX(button.getGraphicTextGap())
                    : 0.0;
        }

        private double captionWidthFor(double width, double indicatorWidth, double gap) {
            if (width < 0) {
                return -1;
            }
            return Math.max(0.0, width - snappedLeftInset() - snappedRightInset() - indicatorWidth - gap);
        }

        @Override
        protected double computePrefWidth(double height) {
            double indicatorWidth = indicatorWidth();
            return snappedLeftInset() + indicatorWidth + gap(indicatorWidth)
                    + snapSizeX(caption.prefWidth(-1)) + snappedRightInset();
        }

        @Override
        protected double computeMinWidth(double height) {
            double indicatorWidth = indicatorWidth();
            return snappedLeftInset() + indicatorWidth + gap(indicatorWidth)
                    + snapSizeX(caption.minWidth(-1)) + snappedRightInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            double indicatorWidth = indicatorWidth();
            double captionWidth = captionWidthFor(width, indicatorWidth, gap(indicatorWidth));
            return snappedTopInset()
                    + Math.max(indicatorHeight(), snapSizeY(caption.prefHeight(captionWidth)))
                    + snappedBottomInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            double indicatorWidth = indicatorWidth();
            double captionWidth = captionWidthFor(width, indicatorWidth, gap(indicatorWidth));
            return snappedTopInset()
                    + Math.max(indicatorHeight(), snapSizeY(caption.minHeight(captionWidth)))
                    + snappedBottomInset();
        }

        @Override
        protected void layoutChildren() {
            double x = snappedLeftInset();
            double y = snappedTopInset();
            double width = Math.max(0.0, getWidth() - x - snappedRightInset());
            double height = Math.max(0.0, getHeight() - y - snappedBottomInset());

            double indicatorWidth = indicatorWidth();
            double indicatorHeight = indicatorHeight();
            double gap = gap(indicatorWidth);
            double captionWidth = Math.min(snapSizeX(caption.prefWidth(-1)),
                    Math.max(0.0, width - indicatorWidth - gap));
            double captionHeight = Math.min(snapSizeY(caption.prefHeight(captionWidth)), height);

            double blockWidth = indicatorWidth + gap + captionWidth;
            double blockHeight = Math.max(indicatorHeight, captionHeight);
            Pos alignment = button.getAlignment() == null ? Pos.CENTER : button.getAlignment();
            double blockX = x;
            if (alignment.getHpos() == HPos.CENTER) {
                blockX += (width - blockWidth) / 2.0;
            } else if (alignment.getHpos() == HPos.RIGHT) {
                blockX += width - blockWidth;
            }
            double blockY = y;
            if (alignment.getVpos() == VPos.BOTTOM) {
                blockY += height - blockHeight;
            } else if (alignment.getVpos() != VPos.TOP) {
                blockY += (height - blockHeight) / 2.0;
            }

            indicator.resizeRelocate(snapPositionX(blockX),
                    snapPositionY(blockY + (blockHeight - indicatorHeight) / 2.0),
                    indicatorWidth, indicatorHeight);
            caption.resizeRelocate(snapPositionX(blockX + indicatorWidth + gap),
                    snapPositionY(blockY + (blockHeight - captionHeight) / 2.0),
                    captionWidth, captionHeight);
        }
    }
}
