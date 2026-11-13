package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Skin for {@link RXHoldButton}: a progress fill layer below three captions,
 * one per face. The idle and holding captions share the first page and swap
 * by visibility; the completed caption sits on the second page. All three
 * stay in the scene, so the button is sized to the largest face and swapping
 * faces never changes its size.
 *
 * <p>This skin does not extend {@code ButtonSkin}: the standard button
 * behavior fires on release, while a hold button fires when the hold
 * completes. Input is handled here through the public
 * {@code arm()}/{@code disarm()}/{@code fire()} API. A hold has one owner,
 * the primary mouse button or the activation key that started it, and only
 * the owner's release ends it.</p>
 *
 * <p>The skin owns the completed feedback: it sets the {@code completed}
 * pseudo-class, swaps to the completed face unless the feedback duration is
 * zero, and a single timer swaps back. While the feedback lasts, a press
 * starts no hold.</p>
 */
public class RXHoldButtonSkin extends RXSkinBase<RXHoldButton> {

    private static final PseudoClass HOLDING = PseudoClass.getPseudoClass("holding");
    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");
    private static final boolean MAC = RXOS.isMacOS();

    // Long enough to read as "cancelled", short enough not to hold up the
    // next press.
    private static final Duration CANCEL_REWIND_DURATION = Duration.millis(180.0);

    private enum State {
        IDLE,
        HOLDING,
        // The hold was cancelled while its owner is still down; nothing
        // restarts until the owner is released.
        CANCELLED_AWAIT_RELEASE,
        // The hold fired while its owner is still down; the release is
        // swallowed.
        COMPLETED_AWAIT_RELEASE
    }

    // ==================== Nodes ====================

    private final Pane fillLayer = new Pane();
    private final Region fillRegion = new Region();
    private final TransitionPages pages = new TransitionPages("page", true);
    private final Label idleCaption = new Label();
    private final Label holdingCaption = new Label();
    private final Label completedCaption = new Label();

    // ==================== Hold state ====================

    private State state = State.IDLE;
    // MouseButton.PRIMARY or the KeyCode that started the hold.
    private Object owner;
    // What the fill shows. The hold completes when its timeline finishes, not
    // when this value reaches 1, so the cancel rewind never completes a hold.
    private final DoubleProperty displayProgress = new SimpleDoubleProperty(this, "displayProgress");
    private Timeline holdTimeline;
    private Timeline rewindTimeline;

    // ==================== Feedback state ====================

    // True from a completed hold until the feedback ends; presses are locked
    // out meanwhile.
    private boolean inFeedback;
    private final PauseTransition feedbackTimer = new PauseTransition();

    // ==================== Window focus ====================

    private Scene hookedScene;
    private Window hookedWindow;
    private final InvalidationListener windowListener = observable -> hookWindow();
    private final InvalidationListener windowFocusListener = observable -> {
        if (hookedWindow != null && !hookedWindow.isFocused()) {
            resetToIdle();
        }
    };

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given button.
     *
     * @param button the button this skin is attached to
     */
    public RXHoldButtonSkin(RXHoldButton button) {
        super(button);

        fillLayer.getStyleClass().add("fill-layer");
        fillLayer.setManaged(false);
        fillLayer.setMouseTransparent(true);
        fillLayer.setVisible(false);
        fillRegion.getStyleClass().add("fill-region");
        fillRegion.setManaged(false);
        fillLayer.getChildren().add(fillRegion);

        initCaption(idleCaption, "caption");
        initCaption(holdingCaption, "holding-caption");
        initCaption(completedCaption, "completed-caption");
        // Both captions stay managed, so the page is sized to the larger one.
        holdingCaption.setVisible(false);
        pages.getPageA().getChildren().addAll(idleCaption, holdingCaption);
        pages.getPageB().getChildren().add(completedCaption);
        getChildren().addAll(fillLayer, pages.getContentPane());

        // Registered first, so it runs last: by then every listener is gone
        // and nothing can swap the face again.
        disposer.registerDisposeTask(() -> {
            // A direct cut: disposing must not start a transition.
            pages.interrupt();
            pages.directCutTo(pages.getPageA());
            showHoldingCaption(false);
            button.pseudoClassStateChanged(HOLDING, false);
            button.pseudoClassStateChanged(COMPLETED, false);
            pages.dispose(button.getAnimation());
            // Only this skin's nodes: a replacement skin may already have
            // added its own children to the control.
            getChildren().removeAll(fillLayer, pages.getContentPane());
        });

        bindCaptions(button);
        wireFeedback(button);
        wireBehavior(button);
        wireInterruptions(button);
    }

    private static void initCaption(Label caption, String styleClass) {
        caption.setMnemonicParsing(false);
        // Fill the page, so the button alignment places the content and a
        // narrowed button truncates the text.
        caption.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // setAll drops the built-in .label class: the caption's text fill and
        // font are bound to the button, so .label rules cannot style it
        // anyway, and .label:disabled would dim it a second time inside the
        // disabled button.
        caption.getStyleClass().setAll(styleClass);
    }

    private void bindCaptions(RXHoldButton button) {
        disposer.registerBinding(idleCaption.textProperty(), button.textProperty());
        disposer.registerBinding(idleCaption.graphicProperty(), button.graphicProperty());
        disposer.registerBinding(holdingCaption.textProperty(), button.holdingTextProperty());
        disposer.registerBinding(holdingCaption.graphicProperty(), button.holdingGraphicProperty());
        disposer.registerBinding(completedCaption.textProperty(), button.completedTextProperty());
        disposer.registerBinding(completedCaption.graphicProperty(), button.completedGraphicProperty());
        bindAppearance(idleCaption, button);
        bindAppearance(holdingCaption, button);
        bindAppearance(completedCaption, button);
    }

    private void bindAppearance(Label caption, RXHoldButton button) {
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
        // labelPadding pads the caption, as it pads the text and graphic of a
        // standard button.
        disposer.registerBinding(caption.paddingProperty(), button.labelPaddingProperty());
    }

    private void wireFeedback(RXHoldButton button) {
        feedbackTimer.setOnFinished(event -> leaveFeedback(true));
        // A reset posted before this skin existed is stale.
        button.getProperties().remove(FeedbackSignals.HOLD_RESET);
        disposer.registerListener(button.getProperties(), this::consumeResetSignal);
    }

    private void wireBehavior(RXHoldButton button) {
        disposer.registerEventHandler(button, MouseEvent.MOUSE_PRESSED, this::mousePressed);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_RELEASED, this::mouseReleased);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_EXITED, this::mouseExited);
        disposer.registerEventHandler(button, KeyEvent.KEY_PRESSED, this::keyPressed);
        disposer.registerEventHandler(button, KeyEvent.KEY_RELEASED, this::keyReleased);
        // Any ActionEvent that reaches the button during a hold is an untimed
        // activation: it fires on its own, so the hold must not fire again.
        // Not consumed, so onAction still runs.
        disposer.registerEventHandler(button, ActionEvent.ACTION, event -> cancelToAwaitRelease());
        // Someone else disarmed the button mid-hold.
        disposer.registerListener(button.armedProperty(), () -> {
            if (!button.isArmed()) {
                cancelToAwaitRelease();
            }
        });
    }

    // ==================== Interruptions ====================

    private void wireInterruptions(RXHoldButton button) {
        disposer.registerListener(button.focusedProperty(), () -> {
            // A key that lost focus never sees its release.
            if (!button.isFocused() && owner instanceof KeyCode) {
                resetToIdle();
            }
        });
        disposer.registerListener(button.disabledProperty(), () -> {
            if (button.isDisabled()) {
                resetToIdle();
            }
        });
        disposer.registerListener(button.sceneProperty(), () -> {
            hookScene();
            if (button.getScene() == null) {
                resetToIdle();
            }
        });
        // A change listener, not an invalidation listener: the property is
        // lazy and only notifies once read, which adding a change listener does.
        disposer.registerListener(controlTreeShowingProperty(), (observable, was, showing) -> {
            if (!showing) {
                resetToIdle();
            }
        });
        hookScene();
        disposer.registerDisposeTask(this::unhookScene);
    }

    // The window can change under the same scene, and the scene under the
    // same button, so the focus listener follows both.
    private void hookScene() {
        Scene scene = getSkinnable() == null ? null : getSkinnable().getScene();
        if (scene != hookedScene) {
            if (hookedScene != null) {
                hookedScene.windowProperty().removeListener(windowListener);
            }
            hookedScene = scene;
            if (scene != null) {
                scene.windowProperty().addListener(windowListener);
            }
        }
        hookWindow();
    }

    private void hookWindow() {
        Window window = hookedScene == null ? null : hookedScene.getWindow();
        if (window != hookedWindow) {
            if (hookedWindow != null) {
                hookedWindow.focusedProperty().removeListener(windowFocusListener);
            }
            hookedWindow = window;
            if (window != null) {
                window.focusedProperty().addListener(windowFocusListener);
            }
        }
    }

    private void unhookScene() {
        if (hookedScene != null) {
            hookedScene.windowProperty().removeListener(windowListener);
            hookedScene = null;
        }
        hookWindow();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXHoldButton button = getSkinnable();
        fillLayer.resizeRelocate(0.0, 0.0, button.getWidth(), button.getHeight());
        pages.layout(x, y, w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxPageWidth(false, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxPageHeight(false, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxPageWidth(true, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxPageHeight(true, w) + bottomInset;
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

    // Every face counts, visible or not, so the size never changes on a swap.
    private double maxPageWidth(boolean min, double height) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minWidth(height), pageB.minWidth(height))
                : Math.max(pageA.prefWidth(height), pageB.prefWidth(height));
    }

    private double maxPageHeight(boolean min, double width) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minHeight(width), pageB.minHeight(width))
                : Math.max(pageA.prefHeight(width), pageB.prefHeight(width));
    }

    // ==================== Faces ====================

    private void showHoldingCaption(boolean holding) {
        idleCaption.setVisible(!holding);
        holdingCaption.setVisible(holding);
        getSkinnable().notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    // ==================== Hold ====================

    private void press(Object newOwner) {
        if (inFeedback) {
            // Locked: the press is owned, so its release is swallowed, but
            // nothing arms and the completed face stays.
            state = State.CANCELLED_AWAIT_RELEASE;
            owner = newOwner;
        } else {
            startHold(newOwner);
        }
    }

    private void startHold(Object newOwner) {
        RXHoldButton button = getSkinnable();
        // A swap back from the completed face may still be playing: end it,
        // so the press shows the holding face at once.
        pages.interrupt();
        stopRewind();
        state = State.HOLDING;
        owner = newOwner;
        displayProgress.set(0.0);
        button.pseudoClassStateChanged(HOLDING, true);
        showHoldingCaption(true);
        button.arm();
        // arm() notifies listeners, which may already have ended the hold.
        if (state != State.HOLDING) {
            return;
        }
        // Snapshot: a duration change during the hold applies to the next one.
        Timeline timeline = new Timeline(new KeyFrame(holdDurationOrDefault(),
                new KeyValue(displayProgress, 1.0, Interpolator.LINEAR)));
        // A listener may have ended this hold and started another inside
        // arm(); only the live hold's timeline may complete.
        timeline.setOnFinished(event -> {
            if (timeline == holdTimeline) {
                complete();
            }
        });
        stopHoldTimeline();
        holdTimeline = timeline;
        timeline.play();
    }

    // The only path to the hold's ActionEvent. The state is written before
    // disarm() and fire() call out to application code, and nothing is
    // written after fire(), so a listener that disables, removes or reskins
    // the button cannot fire it twice or leave it half-reset.
    private void complete() {
        if (state != State.HOLDING) {
            return;
        }
        RXHoldButton button = getSkinnable();
        holdTimeline = null;
        state = State.COMPLETED_AWAIT_RELEASE;
        button.pseudoClassStateChanged(HOLDING, false);
        displayProgress.set(1.0);
        enterFeedback();
        button.disarm();
        if (state == State.COMPLETED_AWAIT_RELEASE) {
            button.fire();
        } else {
            // A disarm listener vetoed the action: no completed face may
            // claim it happened, not even while a swap back plays.
            leaveFeedback(false);
        }
    }

    // Pointer out, untimed activation or an outside disarm: the owner is
    // still down, so nothing restarts until it is released.
    private void cancelToAwaitRelease() {
        if (state == State.HOLDING) {
            endHold(State.CANCELLED_AWAIT_RELEASE, true);
        }
    }

    // A structural interruption: the owner's release may never arrive, so
    // every state goes straight back to idle, without the cancel rewind.
    private void resetToIdle() {
        toIdle(false);
    }

    private void release() {
        toIdle(true);
    }

    private void toIdle(boolean rewind) {
        switch (state) {
            case HOLDING:
                endHold(State.IDLE, rewind);
                break;
            case COMPLETED_AWAIT_RELEASE:
                state = State.IDLE;
                owner = null;
                // A zero duration shows no completed face: the feedback lasts
                // only while the owner is down. Otherwise it runs its course.
                if (pages.getCurrentPage() == pages.getPageA()) {
                    leaveFeedback(true);
                }
                break;
            case CANCELLED_AWAIT_RELEASE:
                state = State.IDLE;
                owner = null;
                break;
            default:
                break;
        }
    }

    private void endHold(State next, boolean rewind) {
        RXHoldButton button = getSkinnable();
        state = next;
        if (next == State.IDLE) {
            owner = null;
        }
        stopHoldTimeline();
        button.pseudoClassStateChanged(HOLDING, false);
        showHoldingCaption(false);
        if (rewind) {
            rewind();
        } else {
            stopRewind();
            displayProgress.set(0.0);
        }
        button.disarm();
    }

    private void rewind() {
        stopRewind();
        if (displayProgress.get() <= 0.0) {
            return;
        }
        rewindTimeline = new Timeline(new KeyFrame(CANCEL_REWIND_DURATION,
                new KeyValue(displayProgress, 0.0, Interpolator.LINEAR)));
        rewindTimeline.play();
    }

    private void stopHoldTimeline() {
        if (holdTimeline != null) {
            holdTimeline.stop();
            holdTimeline = null;
        }
    }

    private void stopRewind() {
        if (rewindTimeline != null) {
            rewindTimeline.stop();
            rewindTimeline = null;
        }
    }

    private Duration holdDurationOrDefault() {
        Duration duration = getSkinnable().getHoldDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return RXHoldButton.DEFAULT_HOLD_DURATION;
        }
        return duration;
    }

    // ==================== Feedback ====================

    // The holding caption stays on the first page: with an animation, that
    // page is the outgoing one, and reverting it to the idle caption first
    // would flash the idle face between the holding and completed faces.
    private void enterFeedback() {
        inFeedback = true;
        getSkinnable().pseudoClassStateChanged(COMPLETED, true);
        Duration duration = feedbackDurationOrDefault();
        if (duration.lessThanOrEqualTo(Duration.ZERO)) {
            showHoldingCaption(false);
            return;
        }
        showFace(true, true);
        if (!duration.isIndefinite()) {
            feedbackTimer.setDuration(duration);
            feedbackTimer.playFromStart();
        }
    }

    // The one way out of the feedback, whatever ends it: the timer, a reset,
    // or the owner's release after a zero-duration feedback.
    private void leaveFeedback(boolean animate) {
        if (!inFeedback) {
            return;
        }
        inFeedback = false;
        feedbackTimer.stop();
        // Latest wins: a swap to the completed face still playing ends first,
        // then the hidden first page is reverted before it comes back.
        pages.interrupt();
        showHoldingCaption(false);
        showFace(false, animate);
        getSkinnable().pseudoClassStateChanged(COMPLETED, false);
        displayProgress.set(0.0);
    }

    private void consumeResetSignal() {
        // Null for unrelated keys and for the notification of this removal.
        Object request = getSkinnable().getProperties().remove(FeedbackSignals.HOLD_RESET);
        if (request != null) {
            leaveFeedback(true);
        }
    }

    private void showFace(boolean completed, boolean animate) {
        StackPane target = completed ? pages.getPageB() : pages.getPageA();
        if (pages.getCurrentPage() == target) {
            return;
        }
        RXHoldButton button = getSkinnable();
        pages.interrupt();
        PageAnimation animation = button.getAnimation();
        TransitionDirection direction =
                completed ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        Duration duration = animationDurationOrDefault();
        pages.clearEffectsIfChanged(animation, direction, duration);
        if (animate && PageTransitionEngine.canAnimate(animation, true, 2, duration, false)) {
            pages.transitionTo(target, animation, direction, duration, null, null, null);
        } else {
            pages.directCutTo(target);
        }
        button.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    private Duration feedbackDurationOrDefault() {
        Duration duration = getSkinnable().getFeedbackDuration();
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return RXHoldButton.DEFAULT_FEEDBACK_DURATION;
        }
        return duration;
    }

    // Only Duration.ZERO turns the animation off (it fails the engine gate and
    // falls back to a direct cut); unusable values still animate.
    private Duration animationDurationOrDefault() {
        Duration duration = getSkinnable().getAnimationDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThan(Duration.ZERO)) {
            return RXHoldButton.DEFAULT_ANIMATION_DURATION;
        }
        return duration;
    }

    // ==================== Input ====================

    // Handlers consume what they handle, matching the standard button
    // behavior; other keys stay unconsumed for focus traversal and
    // application shortcuts.

    private void mousePressed(MouseEvent event) {
        RXHoldButton button = getSkinnable();
        if (button.isFocusTraversable()) {
            button.requestFocus();
        }
        if (state == State.IDLE && !button.isDisabled() && RXMouse.isPlainPrimaryPress(event)) {
            press(MouseButton.PRIMARY);
        }
        event.consume();
    }

    private void mouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && owner == MouseButton.PRIMARY) {
            release();
        }
        event.consume();
    }

    private void mouseExited(MouseEvent event) {
        // Moving back in does not resume: the hold restarts from a new press.
        if (owner == MouseButton.PRIMARY) {
            cancelToAwaitRelease();
        }
        event.consume();
    }

    private void keyPressed(KeyEvent event) {
        // A modified press (e.g. Ctrl+SPACE) is not an activation: it stays
        // unconsumed for application shortcuts.
        if (!isActivationKey(event) || hasModifier(event)) {
            return;
        }
        if (state == State.IDLE && !getSkinnable().isDisabled()) {
            press(event.getCode());
        }
        // Auto-repeat and presses during a hold or an await state are
        // swallowed.
        event.consume();
    }

    private void keyReleased(KeyEvent event) {
        if (!isActivationKey(event)) {
            return;
        }
        if (owner == event.getCode()) {
            release();
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

    // ==================== Accessibility ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.TEXT) {
            RXHoldButton button = getSkinnable();
            String accessibleText = button.getAccessibleText();
            if (accessibleText != null && !accessibleText.isEmpty()) {
                return accessibleText;
            }
            // A face without text is a visual choice; the button still needs
            // a name, so it falls back to the idle text.
            String faceText = currentCaption().getText();
            if (faceText != null && !faceText.isEmpty()) {
                return faceText;
            }
            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                return text;
            }
            // Like a standard button, an icon-only button falls back to the
            // accessible text of its graphic.
            Node graphic = button.getGraphic();
            return graphic == null ? text : graphic.queryAccessibleAttribute(AccessibleAttribute.TEXT);
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    private Label currentCaption() {
        if (pages.getCurrentPage() == pages.getPageB()) {
            return completedCaption;
        }
        return holdingCaption.isVisible() ? holdingCaption : idleCaption;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        // Before the listeners are gone: the reset disarms the button.
        resetToIdle();
        stopHoldTimeline();
        stopRewind();
        // The completed face itself is cut back by the last dispose task,
        // without an animation.
        inFeedback = false;
        feedbackTimer.stop();
        displayProgress.set(0.0);
    }
}
