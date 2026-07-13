package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBackdrop;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXStatePane;
import io.github.leewyatt.rxcontrols.RXStatePane.State;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Skin for {@link RXStatePane}. Stacks three layers over the content area: the
 * managed {@code baseLayer} holding the current base view (which drives the
 * pane's preferred size), the {@code RXBackdrop} dim scrim, and the
 * {@code overlayLayer} hosting the loading indicator box. The base view is
 * swapped atomically so exactly one of content / emptyContent / errorContent
 * (or its default placeholder) is in the scene graph at any instant.
 *
 * <p>Animation runs on three independent channels: the backdrop manages its own
 * fade, while this skin owns two separate {@link Timeline} fields —
 * {@link #overlayFade} for the overlay axis and {@link #stateFade} for the
 * replacement-axis sequential fade-through (which only tweens the skin-owned
 * {@code baseLayer} opacity, never a user node). The two axes never share a
 * timeline, so a transition on one can never cancel the other; each channel is
 * built end-KeyValue-only (resuming from the current value) and every
 * termination path converges on a settle method.</p>
 */
public class RXStatePaneSkin extends RXSkinBase<RXStatePane> {

    private static final PseudoClass LOADING_PSEUDO_CLASS = PseudoClass.getPseudoClass("loading");
    private static final PseudoClass BLOCKING_PSEUDO_CLASS = PseudoClass.getPseudoClass("blocking");

    private final StackPane baseLayer = new StackPane();
    private final RXBackdrop backdrop = new RXBackdrop();
    private final StackPane overlayLayer = new StackPane();
    private final VBox loadingBox = new VBox();
    private final Label loadingTextLabel = new Label();
    private final Rectangle clipRect = new Rectangle();

    // Overlay-axis fade; rebuilt per transition, stopped explicitly in
    // disposeSkin (the disposer would hold a stale reference).
    private Timeline overlayFade;
    // Replacement-axis sequential fade-through; same lifecycle as overlayFade.
    private Timeline stateFade;
    // The loadingDelay gate; also rebuilt per use.
    private PauseTransition pendingActivation;
    // The loadingMinDuration hold; rebuilt per activation.
    private PauseTransition minDurationHold;
    // True when a hide request arrived during the hold and the withdrawal is
    // deferred to the hold's expiry.
    private boolean pendingDeactivation;

    // Lazily created defaults for the null slots; cached so round-trips reuse
    // the same instances.
    private RXPlaceholder defaultEmptyPlaceholder;
    private RXPlaceholder defaultErrorPlaceholder;
    private RXCircularProgressIndicator defaultLoadingIndicator;
    private Button retryButton;

    // True while the loading presentation is showing (the delay has elapsed);
    // during the delay window the base view stays fully interactive.
    private boolean presentationActive;

    // The resolved state the base view is showing or animating toward; guards
    // against alias writes (null <-> CONTENT) replaying a no-op fade-through.
    private State presentedState;

    // The focus owner evacuated from the base layer when blocking activated,
    // restored conditionally on release (only when focus still sits on the
    // sink, and only within the same scene).
    private Node prevFocusOwner;

    /**
     * Creates the skin, assembles the three layers, installs the clip, and
     * snap-settles the presentation to the control's current property values.
     *
     * @param control the state pane this skin is attached to
     */
    public RXStatePaneSkin(RXStatePane control) {
        super(control);

        backdrop.getStyleClass().add("backdrop");
        backdrop.setManaged(false);

        overlayLayer.getStyleClass().add("overlay");
        overlayLayer.setManaged(false);
        overlayLayer.setFocusTraversable(false);

        loadingBox.getStyleClass().add("loading-box");
        loadingBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        loadingTextLabel.getStyleClass().add("loading-text");
        overlayLayer.getChildren().setAll(loadingBox);

        // z-order: base view (bottom) → dim scrim → loading overlay (top).
        getChildren().setAll(baseLayer, backdrop, overlayLayer);
        presentedState = stateOrDefault();
        updateBaseView();
        updateLoadingGraphic();
        updateLoadingText();
        settleOverlay(false);

        control.setClip(clipRect);
        disposer.registerDisposeTask(() -> control.setClip(null));

        disposer.registerListener(control.stateProperty(), this::onStateChanged);
        disposer.registerListener(control.contentProperty(), this::onSlotChanged);
        disposer.registerListener(control.emptyContentProperty(), this::onSlotChanged);
        disposer.registerListener(control.errorContentProperty(), this::onSlotChanged);
        disposer.registerListener(control.loadingProperty(), this::onLoadingChanged);
        disposer.registerListener(control.blockingProperty(), this::onBlockingChanged);
        disposer.registerListener(control.dimmedProperty(), this::onDimmedChanged);
        disposer.registerListener(control.loadingGraphicProperty(), this::updateLoadingGraphic);
        disposer.registerListener(control.loadingTextProperty(), this::updateLoadingText);
        disposer.registerListener(control.onRetryProperty(), this::updateDefaultRetryAction);
        disposer.registerListener(control.sceneProperty(),
                (observable, oldScene, newScene) -> onSceneChanged(newScene));

        // Attach with loading already true: the same delay-gated activation
        // path as the listener, with the delay counting from attach. The
        // attach contract snap-settles — construction never animates (the
        // control may already sit in a scene while its skin is being built).
        if (control.isLoading()) {
            scheduleLoadingActivation(false);
        }
    }

    // ==================== Base view (replacement axis) ====================

    private void updateBaseView() {
        Node target = effectiveBaseView();
        if (target == null) {
            baseLayer.getChildren().clear();
        } else if (baseLayer.getChildren().size() != 1 || baseLayer.getChildren().get(0) != target) {
            baseLayer.getChildren().setAll(target);
        }
    }

    private Node effectiveBaseView() {
        RXStatePane control = getSkinnable();
        return switch (stateOrDefault()) {
            case EMPTY -> control.getEmptyContent() != null
                    ? control.getEmptyContent() : defaultEmptyPlaceholder();
            case ERROR -> control.getErrorContent() != null
                    ? control.getErrorContent() : defaultErrorPlaceholder();
            default -> control.getContent();
        };
    }

    // A slot change never animates: it swaps in place. While a state
    // fade-through is in flight the swap is deferred to the fade's own
    // boundary — every termination path re-reads the newest slot value.
    private void onSlotChanged() {
        if (stateFade != null) {
            return;
        }
        updateBaseView();
    }

    private void onStateChanged() {
        State resolved = stateOrDefault();
        if (resolved == presentedState) {
            // An alias write (null <-> CONTENT): the presentation is unchanged.
            return;
        }
        presentedState = resolved;
        if (shouldAnimate()) {
            playStateFade();
        } else {
            settleStateFade();
        }
    }

    // Sequential fade-through: fade the skin-owned baseLayer to zero, swap the
    // base view atomically at the midpoint, fade back in. Only the baseLayer
    // opacity is tweened — never a user node's. A retarget mid-flight rebuilds
    // the timeline from the current opacity (latest-wins) and the midpoint
    // handler re-reads the newest target.
    private void playStateFade() {
        stopStateFade();
        Duration duration = getSkinnable().getAnimationDuration();
        Interpolator interpolator = interpolatorOrDefault();
        Timeline timeline = new Timeline(
                new KeyFrame(duration.divide(2.0), event -> updateBaseView(),
                        new KeyValue(baseLayer.opacityProperty(), 0.0, interpolator)),
                new KeyFrame(duration,
                        new KeyValue(baseLayer.opacityProperty(), 1.0, interpolator)));
        timeline.setOnFinished(event -> {
            if (stateFade == timeline) {
                stateFade = null;
            }
            settleStateFade();
        });
        stateFade = timeline;
        timeline.play();
    }

    private void settleStateFade() {
        stopStateFade();
        updateBaseView();
        baseLayer.setOpacity(1.0);
    }

    private void stopStateFade() {
        if (stateFade != null) {
            stateFade.stop();
            stateFade = null;
        }
    }

    private RXPlaceholder defaultEmptyPlaceholder() {
        if (defaultEmptyPlaceholder == null) {
            defaultEmptyPlaceholder = new RXPlaceholder(RXPlaceholder.Status.EMPTY, "No data");
        }
        return defaultEmptyPlaceholder;
    }

    private RXPlaceholder defaultErrorPlaceholder() {
        if (defaultErrorPlaceholder == null) {
            defaultErrorPlaceholder = new RXPlaceholder(RXPlaceholder.Status.ERROR, "Something went wrong");
            if (getSkinnable().getOnRetry() != null) {
                defaultErrorPlaceholder.getActions().add(defaultRetryButton());
            }
        }
        return defaultErrorPlaceholder;
    }

    // ==================== Loading presentation (overlay axis) ====================

    private void onLoadingChanged() {
        if (getSkinnable().isLoading()) {
            // Loading back on while a deferred hide is parked: the presentation
            // simply stays up.
            pendingDeactivation = false;
            scheduleLoadingActivation(true);
        } else {
            cancelPendingActivation();
            if (!presentationActive) {
                return;
            }
            if (minDurationHold != null) {
                pendingDeactivation = true;
            } else {
                deactivateLoadingPresentation();
            }
        }
    }

    // animateWhenImmediate distinguishes the listener path (may fade) from the
    // attach path (snap-settles by contract); a delayed activation always
    // re-evaluates the gate when the delay elapses.
    private void scheduleLoadingActivation(boolean animateWhenImmediate) {
        cancelPendingActivation();
        if (presentationActive) {
            return;
        }
        Duration delay = loadingDelayOrZero();
        if (delay.lessThanOrEqualTo(Duration.ZERO)) {
            activateLoadingPresentation(animateWhenImmediate && shouldAnimate());
        } else {
            PauseTransition pause = new PauseTransition(delay);
            pause.setOnFinished(event -> {
                pendingActivation = null;
                activateLoadingPresentation(shouldAnimate());
            });
            pendingActivation = pause;
            pause.play();
        }
    }

    private void cancelPendingActivation() {
        if (pendingActivation != null) {
            pendingActivation.stop();
            pendingActivation = null;
        }
    }

    // Anti-flicker on the withdrawal side: once shown, the presentation stays
    // up for at least loadingMinDuration; a hide inside the window is parked
    // and executed at expiry.
    private void startMinDurationHold() {
        stopMinDurationHold();
        Duration minDuration = loadingMinDurationOrZero();
        if (minDuration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        PauseTransition hold = new PauseTransition(minDuration);
        hold.setOnFinished(event -> {
            minDurationHold = null;
            if (pendingDeactivation) {
                pendingDeactivation = false;
                // The property is the truth: withdraw only if loading is still off.
                if (!getSkinnable().isLoading() && presentationActive) {
                    deactivateLoadingPresentation();
                }
            }
        });
        minDurationHold = hold;
        hold.play();
    }

    private void stopMinDurationHold() {
        if (minDurationHold != null) {
            minDurationHold.stop();
            minDurationHold = null;
        }
        pendingDeactivation = false;
    }

    // The whole presentation activates atomically once the delay elapses:
    // scrim, indicator box, input blocking, and the :loading pseudo-class.
    private void activateLoadingPresentation(boolean animate) {
        presentationActive = true;
        getSkinnable().pseudoClassStateChanged(LOADING_PSEUDO_CLASS, true);
        // Armed before the focus evacuation below: requestFocus notifies the
        // old owner's focus listeners synchronously, and a hide called from
        // one of them must find the hold in place (and be parked), not
        // deactivate in the middle of this activation.
        startMinDurationHold();
        syncBackdropAnimationConfig();
        if (dimmedEnabled()) {
            backdrop.show(animate);
        }
        // The overlay must be visible before the focus sink can accept focus,
        // so the fade (which flips visible on immediately) runs first.
        playOverlayFade(true, animate);
        applyBlocking(blockingEnabled());
        // The overlay's min only matters in the degenerate no-base-view case,
        // but the unmanaged layer never bubbles a layout request on its own.
        getSkinnable().requestLayout();
    }

    // Withdrawal releases input immediately; only the visuals fade out. A
    // visible scrim keeps catching mouse events until the backdrop settles —
    // the documented cost of reusing RXBackdrop unmodified.
    private void deactivateLoadingPresentation() {
        stopMinDurationHold();
        presentationActive = false;
        getSkinnable().pseudoClassStateChanged(LOADING_PSEUDO_CLASS, false);
        applyBlocking(false);
        boolean animate = shouldAnimate();
        syncBackdropAnimationConfig();
        backdrop.hide(animate);
        playOverlayFade(false, animate);
        getSkinnable().requestLayout();
    }

    private void onDimmedChanged() {
        // Live tracking while active; at rest the backdrop is already hidden.
        if (!presentationActive) {
            return;
        }
        syncBackdropAnimationConfig();
        if (dimmedEnabled()) {
            backdrop.show(shouldAnimate());
        } else {
            backdrop.hide(shouldAnimate());
        }
    }

    private void onBlockingChanged() {
        // Live tracking while active; at rest the block path is already off.
        if (!presentationActive) {
            return;
        }
        applyBlocking(blockingEnabled());
    }

    private void updateLoadingGraphic() {
        Node graphic = getSkinnable().getLoadingGraphic();
        Node effective = graphic != null ? graphic : defaultLoadingIndicator();
        if (loadingBox.getChildren().size() != 2 || loadingBox.getChildren().get(0) != effective) {
            loadingBox.getChildren().setAll(effective, loadingTextLabel);
        }
        if (presentationActive) {
            getSkinnable().requestLayout();
        }
    }

    // The label is a permanent, independent sibling of the indicator slot, so
    // it keeps working with any custom loadingGraphic.
    private void updateLoadingText() {
        String text = getSkinnable().getLoadingText();
        boolean present = text != null && !text.isEmpty();
        loadingTextLabel.setText(text);
        loadingTextLabel.setVisible(present);
        loadingTextLabel.setManaged(present);
        overlayLayer.setAccessibleText(present ? text : null);
        if (presentationActive) {
            getSkinnable().requestLayout();
        }
    }

    private RXCircularProgressIndicator defaultLoadingIndicator() {
        if (defaultLoadingIndicator == null) {
            defaultLoadingIndicator = new RXCircularProgressIndicator();
            // Slot-conditional progress drive, in its simplest form: the
            // built-in default is bound once for the skin's lifetime; a custom
            // loadingGraphic simply replaces it in the box, and clearing the
            // slot brings the still-bound default back.
            defaultLoadingIndicator.progressProperty().bind(getSkinnable().progressProperty());
        }
        return defaultLoadingIndicator;
    }

    // The retry button is a member of the DEFAULT error placeholder's actions
    // exactly while onRetry is non-null — added and removed as a list member,
    // never a hidden resident, so the :filled footer state stays honest.
    private void updateDefaultRetryAction() {
        if (defaultErrorPlaceholder == null) {
            return;
        }
        boolean wanted = getSkinnable().getOnRetry() != null;
        if (wanted && !defaultErrorPlaceholder.getActions().contains(defaultRetryButton())) {
            defaultErrorPlaceholder.getActions().add(defaultRetryButton());
        } else if (!wanted && retryButton != null) {
            defaultErrorPlaceholder.getActions().remove(retryButton);
        }
    }

    private Button defaultRetryButton() {
        if (retryButton == null) {
            retryButton = new Button("Retry");
            retryButton.addEventHandler(ActionEvent.ACTION,
                    event -> getSkinnable().fireEvent(new Event(RXStatePane.RETRY)));
        }
        return retryButton;
    }

    // ==================== Input blocking & focus ====================

    // Disabling the base layer (not the content property node — it may be off
    // the scene graph in EMPTY/ERROR) removes the whole subtree from picking,
    // focus eligibility, and Tab traversal in both directions; the overlay
    // layer handles the mouse per the truth table.
    private void applyBlocking(boolean on) {
        if (on) {
            Node owner = currentFocusOwner();
            boolean ownerWasEligible = owner != null && !owner.isDisabled();
            baseLayer.setDisable(true);
            // The owner is governed by the base layer's disable exactly when
            // this flip just made it ineligible — which also covers content
            // embedded in a SubScene, unreachable by a parent walk. Disable
            // propagation is synchronous while Scene.focusCleanup only runs at
            // the next pulse, so evacuating after the flip is race-free.
            // Without the evacuation, focusCleanup would traverse focus to the
            // next control OUTSIDE the pane — Enter could then fire an
            // unrelated button. The sink keeps focusTraversable=false:
            // requestFocus eligibility does not require it, and the normal Tab
            // flow never stops on the sink.
            if (ownerWasEligible && owner.isDisabled()) {
                prevFocusOwner = owner;
                overlayLayer.requestFocus();
            }
        } else {
            baseLayer.setDisable(false);
            restoreFocusConditionally();
        }
        getSkinnable().pseudoClassStateChanged(BLOCKING_PSEUDO_CLASS, on);
        updateOverlayMouseTransparent();
    }

    // Restore only when focus still sits inside the withdrawing presentation
    // (the sink or a focusable node in a custom loadingGraphic) or nowhere: if
    // the user moved focus elsewhere during the block, never steal it back.
    // Stricter than the drawer's some-scene check: the saved owner must still
    // be in THIS pane's scene, so a reparented node cannot pull focus across
    // windows.
    private void restoreFocusConditionally() {
        Node saved = prevFocusOwner;
        prevFocusOwner = null;
        if (saved == null) {
            return;
        }
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return;
        }
        Node owner = scene.getFocusOwner();
        if (owner != null && !isInSubtree(overlayLayer, owner)) {
            return;
        }
        if (saved.getScene() == scene) {
            saved.requestFocus();
        }
    }

    private Node currentFocusOwner() {
        Scene scene = getSkinnable().getScene();
        return scene == null ? null : scene.getFocusOwner();
    }

    private static boolean isInSubtree(Node root, Node node) {
        Node current = node;
        while (current != null) {
            if (current == root) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // ==================== Overlay pose & fade ====================

    private void playOverlayFade(boolean showing, boolean animate) {
        stopOverlayFade();
        if (!animate) {
            settleOverlay(showing);
            return;
        }
        if (showing) {
            overlayLayer.setVisible(true);
        }
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(overlayLayer.opacityProperty(), showing ? 1.0 : 0.0,
                        interpolatorOrDefault())));
        timeline.setOnFinished(event -> {
            if (overlayFade == timeline) {
                overlayFade = null;
            }
            settleOverlay(showing);
        });
        overlayFade = timeline;
        timeline.play();
    }

    // Picking ignores opacity, so the resting overlay must be both invisible
    // and mouse-transparent (the RXBackdrop.applyRest contract) or the
    // full-area layer would swallow every click.
    private void settleOverlay(boolean showing) {
        overlayLayer.setOpacity(showing ? 1.0 : 0.0);
        overlayLayer.setVisible(showing);
        updateOverlayMouseTransparent();
    }

    private void stopOverlayFade() {
        if (overlayFade != null) {
            overlayFade.stop();
            overlayFade = null;
        }
    }

    // Mouse interception needs the presentationActive qualifier: blocking
    // defaults to true, so a bare !blocking would make the resting or
    // delay-window overlay swallow every click.
    private void updateOverlayMouseTransparent() {
        overlayLayer.setMouseTransparent(!(presentationActive && blockingEnabled()));
    }

    /**
     * Whether the loading presentation is currently active (the loading delay
     * has elapsed and the overlay is being shown).
     *
     * @return {@code true} while the loading presentation is active
     */
    private boolean loadingPresentationActive() {
        return presentationActive;
    }

    private boolean blockingEnabled() {
        return getSkinnable().isBlocking();
    }

    private boolean dimmedEnabled() {
        return getSkinnable().isDimmed();
    }

    private Duration loadingDelayOrZero() {
        Duration delay = getSkinnable().getLoadingDelay();
        if (delay == null || delay.isUnknown() || delay.isIndefinite()) {
            return Duration.ZERO;
        }
        return delay;
    }

    private Duration loadingMinDurationOrZero() {
        Duration minDuration = getSkinnable().getLoadingMinDuration();
        if (minDuration == null || minDuration.isUnknown() || minDuration.isIndefinite()) {
            return Duration.ZERO;
        }
        return minDuration;
    }

    // ==================== Animation gating ====================

    private boolean shouldAnimate() {
        return getSkinnable().isAnimated()
                && getSkinnable().getScene() != null
                && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = getSkinnable().getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXStatePane.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    // Forwards only duration and interpolator; RXBackdrop has no animated
    // property — whether a command animates is expressed per call through the
    // show(boolean)/hide(boolean) overloads.
    private void syncBackdropAnimationConfig() {
        RXStatePane control = getSkinnable();
        Duration duration = control.getAnimationDuration();
        Interpolator interpolator = interpolatorOrDefault();
        backdrop.setFadeInDuration(duration);
        backdrop.setFadeOutDuration(duration);
        backdrop.setFadeInInterpolator(interpolator);
        backdrop.setFadeOutInterpolator(interpolator);
    }

    private void onSceneChanged(Scene newScene) {
        if (newScene != null) {
            return;
        }
        // Settle both skin channels to the current logical pose; the backdrop
        // settles itself on scene detach.
        settleStateFade();
        stopOverlayFade();
        settleOverlay(presentationActive);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        clipRect.setWidth(getSkinnable().getWidth());
        clipRect.setHeight(getSkinnable().getHeight());

        layoutInArea(baseLayer, contentX, contentY, contentWidth, contentHeight,
                -1, HPos.CENTER, VPos.CENTER);
        // One-shot positioning of the unmanaged layers; per-frame animation only
        // ever touches opacity (a relocate would dirty ancestors every frame).
        backdrop.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        overlayLayer.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        return leftInset + baseLayer.prefWidth(inner) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double inner = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        return topInset + baseLayer.prefHeight(inner) + bottomInset;
    }

    // Min follows the base view; the active overlay's min is folded in only in
    // the degenerate loading-only case (no base view at all) — anything wider
    // would pulse the pane's min on every refresh cycle.

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        double overlayMin = (loadingPresentationActive() && baseViewAbsent())
                ? overlayLayer.minWidth(inner) : 0;
        return leftInset + Math.max(baseLayer.minWidth(inner), overlayMin) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double inner = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        double overlayMin = (loadingPresentationActive() && baseViewAbsent())
                ? overlayLayer.minHeight(inner) : 0;
        return topInset + Math.max(baseLayer.minHeight(inner), overlayMin) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    private boolean baseViewAbsent() {
        return getSkinnable().getContent() == null && stateOrDefault() == State.CONTENT;
    }

    private State stateOrDefault() {
        State current = getSkinnable().getState();
        return current == null ? RXStatePane.DEFAULT_STATE : current;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        // The repeatedly-rebuilt animation fields are stopped here explicitly
        // rather than via the disposer, which would hold stale references.
        cancelPendingActivation();
        stopMinDurationHold();
        stopOverlayFade();
        stopStateFade();
        getSkinnable().pseudoClassStateChanged(LOADING_PSEUDO_CLASS, false);
        getSkinnable().pseudoClassStateChanged(BLOCKING_PSEUDO_CLASS, false);
        baseLayer.setDisable(false);
        // An evacuated owner is handed back (under the usual conditions), not
        // abandoned on the sink of a dead skin.
        restoreFocusConditionally();
        if (defaultLoadingIndicator != null) {
            // The bound default indicator would otherwise keep the control's
            // progress property referencing this skin's node.
            defaultLoadingIndicator.progressProperty().unbind();
        }
        backdrop.hide(false);
    }
}
