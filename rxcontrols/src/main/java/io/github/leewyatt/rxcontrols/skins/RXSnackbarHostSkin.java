package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.DismissReason;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSnackbarRequest;
import io.github.leewyatt.rxcontrols.RXSnackbarSeverity;
import io.github.leewyatt.rxcontrols.utils.RXMath;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skin for {@link RXSnackbarHost}. The host fills its overlay layer; this skin
 * positions a single bar node inside it per the host's {@code position} and
 * {@code margin}, and plays the bar's enter / exit transitions.
 *
 * <p>A single scalar {@link #progress} in {@code [0, 1]} drives the transition
 * through {@link #applyPose(double)} — slide (from the nearest vertical edge) plus
 * fade. One superseded {@link Timeline} (stop-and-replace) runs at a time, like
 * {@code RXDialogSkin}. The skin observes
 * {@link RXSnackbarHost#currentRequestProperty() currentRequest}: {@code null → R}
 * plays the enter, {@code R → null} plays the exit, and a non-null {@code A → B}
 * switch (same-key in-place update) swaps content and restarts the auto-hide timer
 * without transitions. Transition ends report back through
 * {@link RXSnackbarHost#notifyShown()} / {@link RXSnackbarHost#notifyDismissed()}.</p>
 *
 * <p>The auto-hide timer lives here: it starts when a bar has fully entered and,
 * on expiry, routes through
 * {@link RXSnackbarHost#requestDismiss(DismissReason) requestDismiss(TIMEOUT)}.
 * It pauses while the bar is hovered or holds focus, while the control's tree is
 * not showing, and while the host window is unfocused or iconified; it resumes
 * with the remaining time — an interactive resume (hover / focus leaving) is
 * floored at half the bar's effective duration so a bar never vanishes right
 * after the pointer leaves.</p>
 */
public class RXSnackbarHostSkin extends RXSkinBase<RXSnackbarHost> {

    private static final PseudoClass INFO_PSEUDO_CLASS = PseudoClass.getPseudoClass("info");
    private static final PseudoClass SUCCESS_PSEUDO_CLASS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    private final HBox bar = new HBox();
    private final Label message = new Label();

    // Enter/exit transition, stop-and-replace; rebuilt per play, so it is stopped
    // in disposeSkin by reading the live field, never via registerDisposeTask.
    private Timeline animation;
    // Guard lifecycle reporting: notifyShown only when an enter is in flight,
    // notifyDismissed only when an exit is in flight.
    private boolean openInFlight;
    private boolean closeInFlight;

    // Auto-hide timer, rebuilt on every (re)schedule; stopped by reading the live
    // field. autoHideTotalMs is the displayed bar's effective duration, kept for
    // the interactive-resume floor (total / 2). interactivePauseSeen remembers
    // that hover / focus contributed to the current pause, so the floor still
    // applies when a non-interactive gate (window focus) ends up driving the
    // resume.
    private Timeline autoHide;
    private double autoHideTotalMs;
    private boolean interactivePauseSeen;

    // True while the scene's focus owner is inside the bar.
    private boolean focusWithin;

    // Manually paired listener chain following scene -> window -> focused/iconified;
    // re-hooked on each scene / window change and detached in disposeSkin.
    private Scene observedScene;
    private Window observedWindow;
    private final ChangeListener<Node> focusOwnerListener =
            (observable, oldOwner, newOwner) -> onFocusOwnerChanged(newOwner);
    private final ChangeListener<Window> windowListener =
            (observable, oldWindow, newWindow) -> observeWindow(newWindow);
    private final ChangeListener<Boolean> windowFocusedListener =
            (observable, was, is) -> onGateChanged(false);
    private final ChangeListener<Boolean> iconifiedListener =
            (observable, was, is) -> onGateChanged(false);

    // Single scalar [0,1]: 0 = fully hidden pose, 1 = fully shown pose. Its change
    // re-applies the pose, so the Timeline only has to tween this one value.
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0) {
        @Override
        protected void invalidated() {
            applyPose(get());
        }
    };

    /**
     * Creates the skin, assembles the bar, and registers all listeners with the
     * disposer.
     *
     * @param control the host this skin is attached to
     */
    public RXSnackbarHostSkin(RXSnackbarHost control) {
        super(control);

        bar.getStyleClass().add("snackbar");
        // The bar catches clicks across its whole bounds so a click on it never
        // falls through to the scene content beneath.
        bar.setPickOnBounds(true);
        message.getStyleClass().add("message");
        message.setWrapText(true);
        HBox.setHgrow(message, Priority.ALWAYS);
        bar.getChildren().setAll(message);
        getChildren().add(bar);

        disposer.registerListener(control.currentRequestProperty(),
                (observable, oldRequest, newRequest) -> onCurrentRequestChanged(oldRequest, newRequest));

        // ESC while focus is inside the bar dismisses it. A bubbling handler on the
        // bar (not the scene) so the non-modal host never intercepts a global ESC.
        disposer.registerEventHandler(bar, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && getSkinnable().getCurrentRequest() != null) {
                getSkinnable().requestDismiss(DismissReason.PROGRAMMATIC);
                event.consume();
            }
        });

        // Auto-hide gates: hover and focus-within are interactive pauses (their
        // resume is floored); tree-showing and window focus / iconified are not.
        disposer.registerListener(bar.hoverProperty(), (observable, was, is) -> onGateChanged(true));
        disposer.registerListener(controlTreeShowingProperty(), (observable, was, is) -> onGateChanged(false));
        disposer.registerListener(control.sceneProperty(),
                (observable, oldScene, newScene) -> onSceneChanged(newScene));

        observeScene(control.getScene());
        snapToCurrent();
        // Settle transitions orphaned by a previous skin disposed mid-flight; both
        // hooks are guarded no-ops otherwise.
        control.notifyShown();
        control.notifyDismissed();
    }

    // ==================== Content ====================

    // Rebuilds the bar for the request: [graphic?] message — or the request's
    // custom content in place of both — then [action?] [close?]. The action and
    // close are host-managed even around custom content, so every bar keeps a
    // consistent way to leave; both are fresh per request (skin-private nodes).
    private void updateBarContent(RXSnackbarRequest request) {
        List<Node> children = new ArrayList<>();
        Node custom = request.getContent();
        if (custom != null) {
            children.add(custom);
        } else {
            Node graphic = request.getGraphic();
            if (graphic != null) {
                children.add(graphic);
            }
            message.setText(request.getMessage());
            children.add(message);
        }
        if (request.hasAction()) {
            HBox actions = new HBox(createActionButton(request));
            actions.getStyleClass().add("actions");
            children.add(actions);
        }
        if (getSkinnable().effectiveShowCloseIcon(request)) {
            children.add(createCloseButton());
        }
        bar.getChildren().setAll(children);
        applySeverity(request.getSeverity());
    }

    // Severity is a pure style hook on the bar: a rx-snackbar-<severity> style
    // class plus matching pseudo-class for non-NONE values; NONE adds nothing.
    private void applySeverity(RXSnackbarSeverity severity) {
        RXSnackbarSeverity effective = severity == null ? RXSnackbarSeverity.NONE : severity;
        bar.getStyleClass().removeIf(styleClass -> styleClass.startsWith("rx-snackbar-"));
        if (effective != RXSnackbarSeverity.NONE) {
            bar.getStyleClass().add("rx-snackbar-" + effective.name().toLowerCase(Locale.ROOT));
        }
        bar.pseudoClassStateChanged(INFO_PSEUDO_CLASS, effective == RXSnackbarSeverity.INFO);
        bar.pseudoClassStateChanged(SUCCESS_PSEUDO_CLASS, effective == RXSnackbarSeverity.SUCCESS);
        bar.pseudoClassStateChanged(WARNING_PSEUDO_CLASS, effective == RXSnackbarSeverity.WARNING);
        bar.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, effective == RXSnackbarSeverity.ERROR);
    }

    // The action: a dogfooded RXButton (ripple + state overlay for free). Runs the
    // request handler, then always dismisses with ACTION — a throwing handler
    // still closes the bar, and its exception propagates uncaught.
    private RXButton createActionButton(RXSnackbarRequest request) {
        RXButton button = new RXButton(request.getActionLabel());
        button.setOnAction(event -> {
            try {
                Runnable handler = request.getActionHandler();
                if (handler != null) {
                    handler.run();
                }
            } finally {
                getSkinnable().requestDismiss(DismissReason.ACTION);
            }
        });
        return button;
    }

    // The close icon: a shape-backed Region in a transparent, pickable StackPane
    // wrapper, pinned to its preferred size. Unlike the dialog's close (X) it is a
    // focusable, button-like node — for a persistent bar with no action it is the
    // keyboard user's only way out.
    private StackPane createCloseButton() {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("close-button");
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        button.setFocusTraversable(true);
        button.setAccessibleRole(AccessibleRole.BUTTON);
        button.setAccessibleText("Close");
        button.setOnMouseClicked(event -> {
            getSkinnable().requestDismiss(DismissReason.CLOSE_ICON);
            event.consume();
        });
        button.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                getSkinnable().requestDismiss(DismissReason.CLOSE_ICON);
                event.consume();
            }
        });
        return button;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        Insets margin = sanitizedMargin();
        double availableWidth = Math.max(0.0, contentWidth - margin.getLeft() - margin.getRight());
        double availableHeight = Math.max(0.0, contentHeight - margin.getTop() - margin.getBottom());
        double widthCap = Math.min(availableWidth, effectiveMaxWidth());
        double barWidth = RXMath.clamp(bar.prefWidth(-1), bar.minWidth(-1), widthCap);
        double barHeight = Math.min(availableHeight, bar.prefHeight(barWidth));
        Pos pos = positionOrDefault();
        double barX = contentX + margin.getLeft() + alignedX(pos.getHpos(), availableWidth, barWidth);
        double barY = contentY + margin.getTop() + alignedY(pos.getVpos(), availableHeight, barHeight);
        layoutInArea(bar, barX, barY, barWidth, barHeight, 0, HPos.LEFT, VPos.TOP);
    }

    private static double alignedX(HPos hpos, double available, double width) {
        return switch (hpos) {
            case CENTER -> (available - width) / 2.0;
            case RIGHT -> available - width;
            default -> 0.0;
        };
    }

    private static double alignedY(VPos vpos, double available, double height) {
        return switch (vpos) {
            case TOP -> 0.0;
            case CENTER -> (available - height) / 2.0;
            default -> available - height;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + bar.prefWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + bar.prefHeight(-1) + bottomInset;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Unbounded so the host's overlay layer (a {@code StackPane}) stretches it
     * to fill the layer, letting the bar position itself against the scene.</p>
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

    // Lenient per-component margin: null falls back to the default; a negative,
    // NaN, or infinite component would push the bar off-screen, so it reads as 0.
    private Insets sanitizedMargin() {
        Insets margin = getSkinnable().getMargin();
        if (margin == null) {
            return RXSnackbarHost.DEFAULT_MARGIN;
        }
        return new Insets(sanitizedComponent(margin.getTop()), sanitizedComponent(margin.getRight()),
                sanitizedComponent(margin.getBottom()), sanitizedComponent(margin.getLeft()));
    }

    private static double sanitizedComponent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    // Lenient max width: non-positive / NaN falls back to the default; positive
    // infinity is the single "no cap" entry (still bounded by the available width).
    private double effectiveMaxWidth() {
        double value = getSkinnable().getSnackbarMaxWidth();
        if (Double.isNaN(value) || value <= 0.0) {
            return RXSnackbarHost.DEFAULT_SNACKBAR_MAX_WIDTH;
        }
        return value;
    }

    private Pos positionOrDefault() {
        Pos value = getSkinnable().getPosition();
        return value == null ? RXSnackbarHost.DEFAULT_POSITION : value;
    }

    // ==================== Transitions ====================

    private void onCurrentRequestChanged(RXSnackbarRequest oldRequest, RXSnackbarRequest newRequest) {
        if (oldRequest == null && newRequest != null) {
            updateBarContent(newRequest);
            stopAutoHide();
            openInFlight = true;
            closeInFlight = false;
            bar.setVisible(true);
            bar.setMouseTransparent(false);
            playOpen();
        } else if (oldRequest != null && newRequest == null) {
            stopAutoHide();
            closeInFlight = true;
            openInFlight = false;
            playClose();
        } else if (oldRequest != null && newRequest != null) {
            // Same-key in-place update: swap content and restart the timer without
            // transitions. The host fires SHOWING / SHOWN itself on this path.
            updateBarContent(newRequest);
            startAutoHide(newRequest);
        }
    }

    private void playOpen() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeOpen();
            return;
        }
        Timeline timeline = buildTimeline(1.0, this::finalizeOpen);
        animation = timeline;
        timeline.play();
    }

    private void playClose() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeClose();
            return;
        }
        Timeline timeline = buildTimeline(0.0, this::finalizeClose);
        animation = timeline;
        timeline.play();
    }

    private Timeline buildTimeline(double target, Runnable onFinish) {
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(progress, target, interpolatorOrDefault())));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            onFinish.run();
        });
        return timeline;
    }

    private void finalizeOpen() {
        progress.set(1.0);
        if (openInFlight) {
            openInFlight = false;
            RXSnackbarRequest opened = getSkinnable().getCurrentRequest();
            getSkinnable().notifyShown();
            // A SHOWN handler may have dismissed this bar (and, synchronously, a
            // successor may already be current with its own timer running): start
            // the timer only for the request this finalize opened.
            if (opened != null && getSkinnable().getCurrentRequest() == opened) {
                startAutoHide(opened);
            }
        }
    }

    private void finalizeClose() {
        progress.set(0.0);
        bar.setVisible(false);
        bar.setMouseTransparent(true);
        if (closeInFlight) {
            closeInFlight = false;
            // Last action: the host settles the request and promotes the next one.
            getSkinnable().notifyDismissed();
        }
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    private void snapToCurrent() {
        RXSnackbarRequest current = getSkinnable().getCurrentRequest();
        if (current != null) {
            updateBarContent(current);
            progress.set(1.0);
            bar.setVisible(true);
            bar.setMouseTransparent(false);
            startAutoHide(current);
        } else {
            applyPose(0.0);
            bar.setVisible(false);
            bar.setMouseTransparent(true);
        }
    }

    private void applyPose(double rawProgress) {
        double p = RXMath.clamp0To1(rawProgress);
        bar.setOpacity(p);
        double distance = bar.getHeight() > 0.0 ? bar.getHeight() : bar.prefHeight(-1);
        double direction = positionOrDefault().getVpos() == VPos.TOP ? -1.0 : 1.0;
        bar.setTranslateY((1.0 - p) * distance * direction);
    }

    private boolean animationsActive() {
        return getSkinnable().isAnimated()
                && getSkinnable().getScene() != null
                && isPositiveFinite(getSkinnable().getAnimationDuration());
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXSnackbarHost.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private static boolean isPositiveFinite(Duration duration) {
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    // ==================== Auto-hide timer ====================

    private void startAutoHide(RXSnackbarRequest request) {
        stopAutoHide();
        Duration total = getSkinnable().effectiveDuration(request);
        if (!isPositiveFinite(total)) {
            // Persistent bar: no timer (the host's close-icon guard keeps it closable).
            return;
        }
        autoHideTotalMs = total.toMillis();
        scheduleAutoHide(autoHideTotalMs);
    }

    // (Re)builds the timer for the given span and starts it when no gate blocks it;
    // otherwise it waits at 0 until a gate change resumes it.
    private void scheduleAutoHide(double millis) {
        stopAutoHideTimeline();
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(Math.max(1.0, millis))));
        timeline.setOnFinished(event -> {
            if (autoHide == timeline) {
                autoHide = null;
                getSkinnable().requestDismiss(DismissReason.TIMEOUT);
            }
        });
        autoHide = timeline;
        if (timerGateOpen()) {
            timeline.play();
        }
    }

    private void onGateChanged(boolean interactive) {
        if (autoHide == null) {
            return;
        }
        if (timerGateOpen()) {
            if (autoHide.getStatus() != Animation.Status.RUNNING) {
                double remaining = autoHide.getTotalDuration().subtract(autoHide.getCurrentTime()).toMillis();
                // A resume after an interactive pause (hover / focus) is floored at
                // half the effective duration, so a bar with 200ms left does not
                // vanish under the leaving pointer — even when a non-interactive
                // gate (window focus) is what finally reopens. Never playFromStart:
                // a stray hover must not reset a bar back to its full duration.
                boolean floored = interactive || interactivePauseSeen;
                double target = floored ? Math.max(remaining, autoHideTotalMs / 2.0) : remaining;
                interactivePauseSeen = false;
                scheduleAutoHide(target);
            }
        } else {
            if (interactive) {
                interactivePauseSeen = true;
            }
            if (autoHide.getStatus() == Animation.Status.RUNNING) {
                autoHide.pause();
            }
        }
    }

    // The timer runs only while the bar is unhovered and unfocused, the control's
    // tree is showing, and the host window is focused and not iconified — so a
    // message never silently expires in the background. `iconified` exists only on
    // Stage; a non-Stage window relies on the focus gate alone.
    private boolean timerGateOpen() {
        if (bar.isHover() || focusWithin) {
            return false;
        }
        if (!controlTreeShowingProperty().get()) {
            return false;
        }
        Scene scene = getSkinnable().getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window == null || !window.isFocused()) {
            return false;
        }
        return !(window instanceof Stage stage) || !stage.isIconified();
    }

    private void stopAutoHide() {
        stopAutoHideTimeline();
        autoHideTotalMs = 0.0;
        interactivePauseSeen = false;
    }

    private void stopAutoHideTimeline() {
        if (autoHide != null) {
            autoHide.stop();
            autoHide = null;
        }
    }

    // ==================== Scene / window gate chain ====================

    private void onSceneChanged(Scene newScene) {
        observeScene(newScene);
        if (newScene == null) {
            // The host's own scene listener (registered first) has already settled
            // the model; this is pure view cleanup.
            stopAnimation();
            stopAutoHide();
            openInFlight = false;
            closeInFlight = false;
            applyPose(0.0);
            bar.setVisible(false);
            bar.setMouseTransparent(true);
        }
    }

    private void observeScene(Scene newScene) {
        if (observedScene != null) {
            observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
            observedScene.windowProperty().removeListener(windowListener);
        }
        observedScene = newScene;
        if (newScene != null) {
            newScene.focusOwnerProperty().addListener(focusOwnerListener);
            newScene.windowProperty().addListener(windowListener);
            onFocusOwnerChanged(newScene.getFocusOwner());
            observeWindow(newScene.getWindow());
        } else {
            onFocusOwnerChanged(null);
            observeWindow(null);
        }
    }

    private void observeWindow(Window newWindow) {
        if (observedWindow != null) {
            observedWindow.focusedProperty().removeListener(windowFocusedListener);
            if (observedWindow instanceof Stage stage) {
                stage.iconifiedProperty().removeListener(iconifiedListener);
            }
        }
        observedWindow = newWindow;
        if (newWindow != null) {
            newWindow.focusedProperty().addListener(windowFocusedListener);
            if (newWindow instanceof Stage stage) {
                stage.iconifiedProperty().addListener(iconifiedListener);
            }
        }
        onGateChanged(false);
    }

    private void onFocusOwnerChanged(Node newOwner) {
        boolean inside = isInBar(newOwner);
        if (inside != focusWithin) {
            focusWithin = inside;
            onGateChanged(true);
        }
    }

    private boolean isInBar(Node node) {
        Node current = node;
        while (current != null) {
            if (current == bar) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        // Both timelines are rebuilt repeatedly; stop the live fields explicitly
        // rather than via the disposer, which would hold stale references.
        stopAnimation();
        stopAutoHide();
        // The scene/window listener chain is manually paired (its targets change);
        // detach from whatever is currently observed.
        observeScene(null);
    }
}
