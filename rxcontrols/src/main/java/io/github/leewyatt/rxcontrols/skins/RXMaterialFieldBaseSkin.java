package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.enums.RXFieldVariant;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.HitInfo;
import javafx.scene.transform.Scale;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.List;

/**
 * Shared Material decoration skin for {@code RXMaterialTextField} /
 * {@code RXMaterialPasswordField}. Builds on {@link RXFieldBaseSkin} (slot
 * layout, effective text-padding, hit-test correction, {@link SkinDisposer})
 * and adds the floating label, the bottom activation lines, the supporting
 * (helper / error) row, a built-in clear affordance composed into the trailing
 * slot, and — for the FILLED variant — a surface-variant container box behind
 * the editor (rounded top, full width, top edge down to the activation line,
 * with the supporting row outside) plus a {@code -rx-state-overlay-color}
 * hover / focus layer; CSS paints the box only under {@code :filled}.
 * <p>
 * Layout uses a sub-rect strategy: this skin reserves a top label band, a
 * bottom line band, and a bottom supporting band, then hands the trimmed
 * editor sub-rect to {@link RXFieldBaseSkin#layoutChildren}. The hit-test and
 * baseline are shifted by the label band so clicks and baseline alignment stay
 * correct. Each band is added exactly once into the height computations.
 * <p>
 * The floating label and the accent (focus) line animate between their resting
 * and active states. Animation is expressed as a normalized progress
 * ({@code floatProgress} / {@code accentProgress}); layout writes the geometry
 * targets and the progress maps onto them, so a resize mid-transition stays
 * consistent. Transitions snap to their end value when {@code animated} is off,
 * when the duration is non-positive, or while the control is not showing (so
 * there is no opening animation and headless tests are deterministic). These
 * are short one-shot tweens, so they are not paused on tree-hide (AGENTS §3.1).
 */
public class RXMaterialFieldBaseSkin extends RXFieldBaseSkin {

    // ==================== Constants ====================

    /** Gap between the editor text and the activation line. */
    private static final double LINE_GAP = 3.0;
    /** Collapsed horizontal scale of the accent line when unfocused. */
    private static final double ACCENT_REST_SCALE_X = 0.05;
    /** Fallback accent-line thickness before CSS resolves {@code -fx-pref-height}. */
    private static final double FALLBACK_ACCENT_THICKNESS = 2.0;
    /** Fallback activation-line thickness before CSS resolves {@code -fx-pref-height}. */
    private static final double FALLBACK_ACTIVATION_THICKNESS = 1.0;
    /** House "fast-out slow-in" easing (Material standard easing). */
    private static final Interpolator MATERIAL_EASING = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    private static final PseudoClass FLOATED = PseudoClass.getPseudoClass("floated");

    private static final String ACTIVATION_LINE_CLASS = "activation-line";
    private static final String ACCENT_LINE_CLASS = "accent-line";
    private static final String CONTAINER_CLASS = "container";
    private static final String STATE_OVERLAY_CLASS = "state-overlay";
    private static final String SUPPORTING_CLASS = "supporting";
    private static final String HELPER_CLASS = "helper";
    private static final String ERROR_CLASS = "error";
    private static final String CLEAR_BUTTON_CLASS = "clear-button";
    private static final String GRAPHIC_CLASS = "graphic";

    // ==================== Observed control state ====================

    private final ObservableValue<Node> userTrailing;
    private final ObservableValue<String> labelText;
    private final ObservableValue<String> helperText;
    private final ObservableValue<String> errorText;
    private final ObservableValue<Boolean> invalid;
    private final ObservableValue<Boolean> floatingLabel;
    private final ObservableValue<Boolean> animated;
    private final ObservableValue<Duration> animationDuration;
    private final ObservableValue<Number> labelFloatScale;
    private final ObservableValue<RXFieldVariant> variant;
    private final ObservableValue<Boolean> showClearButton;

    /**
     * Skin-controlled relay handed to the base as its effective-right node: set
     * to the internal trailing container when there is trailing content, else
     * {@code null}. Created before {@code super(...)}; populated after.
     */
    private final ObjectProperty<Node> effectiveRight;

    // ==================== Decoration nodes ====================

    // FILLED-variant background box; transparent (no CSS fill) for other variants.
    private final Region filledContainer = new Region();
    // FILLED hover/focus state layer; tinted with -rx-state-overlay-color (flips
    // black on light / white on dark), opacity driven by CSS :hover / :focused.
    private final Region stateOverlay = new Region();
    private final Label labelNode = new Label();
    private final Scale labelScale = new Scale(1.0, 1.0, 0.0, 0.0);
    private final Region activationLine = new Region();
    private final Region accentLine = new Region();
    private final Scale accentScale = new Scale(ACCENT_REST_SCALE_X, 1.0, 0.0, 0.0);
    private final StackPane supporting = new StackPane();
    private final Label helperLabel = new Label();
    private final Label errorLabel = new Label();

    // Internal trailing container = [userTrailing] [clearButton]; fed to the base
    // as the effective-right node so user and built-in affordances coexist.
    private final HBox builtinTrailing = new HBox();
    private final Region clearGraphic = new Region();
    private final StackPane clearButton = new StackPane(clearGraphic);
    // Reused for the clear-button fade; its duration is set per play (effectiveDuration).
    private final FadeTransition clearFade = new FadeTransition(Duration.ONE, clearButton);
    /** Last applied clear-button opacity target; guards fade restarts to the boundary only. */
    private double clearTarget;

    // ==================== Animation state ====================

    /** 0 = resting (placeholder), 1 = floated. */
    private final DoubleProperty floatProgress = new SimpleDoubleProperty(this, "floatProgress", 0.0);
    /** 0 = collapsed/hidden accent line, 1 = expanded/visible (focused). */
    private final DoubleProperty accentProgress = new SimpleDoubleProperty(this, "accentProgress", 0.0);
    /** Layout-derived translateY that places the label in the floated position. */
    private double floatedTranslateY;
    private Timeline floatTimeline;
    private Timeline accentTimeline;
    private double floatTarget;
    private double accentTarget;

    /**
     * Creates the shared Material skin.
     *
     * @param control           the text field being skinned
     * @param userLeading       leading-node observable (effective left)
     * @param userTrailing      trailing-node observable (effective right)
     * @param userTextPadding   text-padding observable (effective text padding)
     * @param labelText         floating-label text observable
     * @param helperText        helper-text observable
     * @param errorText         error-text observable
     * @param invalid           invalid-state observable
     * @param floatingLabel     floating-label-enabled observable
     * @param animated          animation-enabled observable
     * @param animationDuration transition-duration observable
     * @param labelFloatScale   floated-label scale observable
     * @param variant           visual-variant observable
     * @param showClearButton   built-in clear-button-enabled observable
     */
    public RXMaterialFieldBaseSkin(TextField control,
                                   ObservableValue<Node> userLeading,
                                   ObservableValue<Node> userTrailing,
                                   ObservableValue<Insets> userTextPadding,
                                   ObservableValue<String> labelText,
                                   ObservableValue<String> helperText,
                                   ObservableValue<String> errorText,
                                   ObservableValue<Boolean> invalid,
                                   ObservableValue<Boolean> floatingLabel,
                                   ObservableValue<Boolean> animated,
                                   ObservableValue<Duration> animationDuration,
                                   ObservableValue<Number> labelFloatScale,
                                   ObservableValue<RXFieldVariant> variant,
                                   ObservableValue<Boolean> showClearButton) {
        // The effective-right relay must exist before super(...) and cannot
        // reference this; a private constructor receives it and wires the
        // trailing composition afterward.
        this(control, userLeading, userTrailing, userTextPadding, labelText, helperText,
                errorText, invalid, floatingLabel, animated, animationDuration,
                labelFloatScale, variant, showClearButton, new SimpleObjectProperty<>());
    }

    private RXMaterialFieldBaseSkin(TextField control,
                                    ObservableValue<Node> userLeading,
                                    ObservableValue<Node> userTrailing,
                                    ObservableValue<Insets> userTextPadding,
                                    ObservableValue<String> labelText,
                                    ObservableValue<String> helperText,
                                    ObservableValue<String> errorText,
                                    ObservableValue<Boolean> invalid,
                                    ObservableValue<Boolean> floatingLabel,
                                    ObservableValue<Boolean> animated,
                                    ObservableValue<Duration> animationDuration,
                                    ObservableValue<Number> labelFloatScale,
                                    ObservableValue<RXFieldVariant> variant,
                                    ObservableValue<Boolean> showClearButton,
                                    ObjectProperty<Node> effectiveRight) {
        super(control, userLeading, effectiveRight, userTextPadding);
        this.effectiveRight = effectiveRight;
        this.userTrailing = userTrailing;
        this.showClearButton = showClearButton;
        this.labelText = labelText;
        this.helperText = helperText;
        this.errorText = errorText;
        this.invalid = invalid;
        this.floatingLabel = floatingLabel;
        this.animated = animated;
        this.animationDuration = animationDuration;
        this.labelFloatScale = labelFloatScale;
        this.variant = variant;

        filledContainer.getStyleClass().add(CONTAINER_CLASS);
        filledContainer.setManaged(false);
        filledContainer.setMouseTransparent(true);

        stateOverlay.getStyleClass().add(STATE_OVERLAY_CLASS);
        stateOverlay.setManaged(false);
        stateOverlay.setMouseTransparent(true);

        // A Label already carries the built-in ".label" style class; the CSS
        // targets it through the direct-child path (AGENTS §2.4.4).
        labelNode.setManaged(false);
        labelNode.setMouseTransparent(true);
        labelNode.getTransforms().add(labelScale);

        activationLine.getStyleClass().add(ACTIVATION_LINE_CLASS);
        activationLine.setManaged(false);
        activationLine.setMouseTransparent(true);

        accentLine.getStyleClass().add(ACCENT_LINE_CLASS);
        accentLine.setManaged(false);
        accentLine.setMouseTransparent(true);
        accentLine.getTransforms().add(accentScale);

        supporting.getStyleClass().add(SUPPORTING_CLASS);
        supporting.setManaged(false);
        supporting.setMouseTransparent(true);
        supporting.setAlignment(Pos.CENTER_LEFT);
        helperLabel.getStyleClass().add(HELPER_CLASS);
        errorLabel.getStyleClass().add(ERROR_CLASS);
        supporting.getChildren().addAll(helperLabel, errorLabel);

        // Built-in clear affordance: a shape-backed Region inside a transparent
        // StackPane wrapper (AGENTS §2.9). The wrapper is the click target; the
        // graphic is mouse-transparent and size-locked to its pref. The trailing
        // container is laid out by the base (via the effectiveRight relay), not
        // added to getChildren() here.
        clearGraphic.getStyleClass().add(GRAPHIC_CLASS);
        clearGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        clearGraphic.setMouseTransparent(true);
        clearButton.getStyleClass().add(CLEAR_BUTTON_CLASS);
        clearButton.setOpacity(0.0);
        builtinTrailing.setAlignment(Pos.CENTER);

        // Filled box (z-index 0) sits behind the editor; the state overlay (z-index
        // 1) tints the box on hover/focus, still behind the editor text. CSS paints
        // both only for the :filled variant.
        getChildren().add(0, filledContainer);
        getChildren().add(1, stateOverlay);
        getChildren().addAll(activationLine, accentLine, labelNode, supporting);

        // Map normalized progress onto the decoration transforms. Registered on
        // the shared disposer for a single cleanup channel (AGENTS §2.8),
        // matching the RXCircularProgressIndicatorSkin reference baseline.
        disposer.registerListener(floatProgress, this::applyFloatVisual);
        disposer.registerListener(accentProgress, this::applyAccentVisual);

        disposer.registerBinding(labelNode.fontProperty(), control.fontProperty());

        disposer.registerListener(labelText, this::onLabelSourceChanged);
        disposer.registerListener(control.promptTextProperty(), this::onLabelSourceChanged);
        disposer.registerListener(helperText, this::onSupportingChanged);
        disposer.registerListener(errorText, this::onSupportingChanged);
        disposer.registerListener(invalid, this::onSupportingChanged);
        disposer.registerListener(floatingLabel, this::retargetFloat);
        disposer.registerListener(control.textProperty(), this::onTextChanged);
        disposer.registerListener(control.focusedProperty(), this::onFocusChanged);
        disposer.registerListener(animated, this::onAnimatedChanged);
        disposer.registerListener(labelFloatScale, this::onLayoutChanged);
        disposer.registerListener(variant, this::onLayoutChanged);
        disposer.registerListener(userTrailing, this::updateTrailing);
        disposer.registerListener(showClearButton, this::updateTrailing);
        disposer.registerListener(control.editableProperty(), this::updateTrailing);
        disposer.registerEventHandler(clearButton, MouseEvent.MOUSE_CLICKED, this::onClearClicked);

        // Timelines are rebuilt over the skin's lifetime; stop the live one at
        // dispose by reading the field, not a captured reference.
        disposer.registerDisposeTask(this::stopFloatTimeline);
        disposer.registerDisposeTask(this::stopAccentTimeline);
        disposer.registerDisposeTask(clearFade::stop);
        // Decoration nodes are added to the control's (shared) children, and
        // SkinBase.dispose() does not clear them. Remove them (and detach their
        // transforms) on dispose so they don't linger — mirroring what
        // RXFieldBaseSkin does for its wrappers. (builtinTrailing rides inside the
        // base's right-wrapper, which the base releases.)
        disposer.registerDisposeTask(() -> {
            labelNode.getTransforms().remove(labelScale);
            accentLine.getTransforms().remove(accentScale);
            getChildren().removeAll(filledContainer, stateOverlay, activationLine,
                    accentLine, labelNode, supporting);
        });

        labelNode.setText(effectiveLabelText());
        updateAccessibleName();
        updateSupporting();
        updateTrailing();
        // Initial state snaps (no opening animation). Apply the pose explicitly
        // so the decoration is established by code, not by relying on set()
        // firing the listener (a no-op when the target equals the default 0).
        floatTarget = isFloated() ? 1.0 : 0.0;
        accentTarget = control.isFocused() ? 1.0 : 0.0;
        floatProgress.set(floatTarget);
        accentProgress.set(accentTarget);
        applyFloatVisual();
        applyAccentVisual();
        getSkinnable().pseudoClassStateChanged(FLOATED, isFloated());
    }

    // ==================== State reactions ====================

    private void onLabelSourceChanged() {
        labelNode.setText(effectiveLabelText());
        updateAccessibleName();
        getSkinnable().requestLayout();
    }

    private void onSupportingChanged() {
        updateSupporting();
        getSkinnable().requestLayout();
    }

    private void onLayoutChanged() {
        getSkinnable().requestLayout();
    }

    private void onFocusChanged() {
        retargetFloat();
        retargetAccent();
    }

    private void onAnimatedChanged() {
        // Turning animation off mid-transition snaps to the current targets.
        retargetFloat();
        retargetAccent();
    }

    private void onTextChanged() {
        retargetFloat();
        updateClearVisual();
    }

    // ==================== Trailing / clear affordance ====================

    /**
     * Built-in trailing affordances placed between the user trailing node and the
     * clear button (e.g. a password reveal button). The default is none;
     * subclasses override and manage their own visible / managed state. Must
     * tolerate being called during {@code super(...)} before subclass fields are
     * initialized (return an empty list then).
     *
     * @return the affordance nodes, in display order
     */
    protected List<Node> builtinTrailingAffordances() {
        return List.of();
    }

    /** Rebuilds the trailing composition; subclasses call this when an affordance's state changes. */
    protected final void refreshTrailing() {
        updateTrailing();
    }

    private void updateTrailing() {
        Node user = userTrailing.getValue();
        builtinTrailing.getChildren().clear();
        if (user != null) {
            builtinTrailing.getChildren().add(user);
        }
        builtinTrailing.getChildren().addAll(builtinTrailingAffordances());
        builtinTrailing.getChildren().add(clearButton);
        updateClearVisual(); // sets the clear button's managed state
        // Reserve the right wrapper only when the box holds space-reserving
        // (managed) content; an empty box would otherwise reserve width and flip
        // :has-right-node needlessly.
        boolean hasContent = builtinTrailing.getChildren().stream().anyMatch(Node::isManaged);
        effectiveRight.set(hasContent ? builtinTrailing : null);
    }

    private void updateClearVisual() {
        boolean active = isClearActive();
        boolean wasManaged = clearButton.isManaged();
        clearButton.setManaged(active);
        clearButton.setVisible(active);
        if (wasManaged != active) {
            // The relay may keep pointing at the same container (a user trailing
            // node is present), so the base's effectiveRight listener does not
            // fire; invalidate the control size to reclaim / grant the clear
            // button's reserved width.
            getSkinnable().requestLayout();
        }
        if (!active) {
            clearFade.stop();
            clearButton.setOpacity(0.0);
            clearButton.setMouseTransparent(true);
            clearTarget = 0.0;
            return;
        }
        // A faded-out (empty) clear button stays managed for a stable width, but
        // opacity does not affect picking — make it non-interactive so it does
        // not swallow clicks meant for the editor; only a visible one is pickable.
        double target = isTextEmpty() ? 0.0 : 1.0;
        clearButton.setMouseTransparent(target == 0.0);
        if (target == clearTarget) {
            return; // empty<->non-empty boundary not crossed
        }
        clearTarget = target;
        if (shouldSnap()) {
            clearFade.stop();
            clearButton.setOpacity(target);
            return;
        }
        clearFade.stop();
        clearFade.setDuration(effectiveDuration());
        clearFade.setFromValue(clearButton.getOpacity());
        clearFade.setToValue(target);
        clearFade.playFromStart();
    }

    private void onClearClicked(MouseEvent event) {
        getSkinnable().clear();
        event.consume();
    }

    private boolean isClearActive() {
        return getSkinnable().isEditable() && showClearButtonEnabled();
    }

    private boolean showClearButtonEnabled() {
        Boolean v = showClearButton.getValue();
        return v == null || v;
    }

    private boolean isTextEmpty() {
        return str(getSkinnable().getText()).isEmpty();
    }

    private void updateAccessibleName() {
        String name = effectiveLabelText();
        getSkinnable().setAccessibleText(name.isEmpty() ? null : name);
    }

    private void updateSupporting() {
        String err = str(errorText.getValue());
        String help = str(helperText.getValue());
        boolean showError = Boolean.TRUE.equals(invalid.getValue()) && !err.isEmpty();
        errorLabel.setText(err);
        helperLabel.setText(help);
        errorLabel.setVisible(showError);
        errorLabel.setManaged(showError);
        boolean showHelper = !showError && !help.isEmpty();
        helperLabel.setVisible(showHelper);
        helperLabel.setManaged(showHelper);
    }

    // ==================== Animation ====================

    private void retargetFloat() {
        getSkinnable().pseudoClassStateChanged(FLOATED, isFloated());
        double target = isFloated() ? 1.0 : 0.0;
        if (shouldSnap()) {
            stopFloatTimeline();
            floatTarget = target;
            floatProgress.set(target);
            return;
        }
        if (floatTimeline != null && floatTarget == target) {
            return;
        }
        if (floatTimeline == null && floatProgress.get() == target) {
            return;
        }
        stopFloatTimeline();
        floatTarget = target;
        floatTimeline = new Timeline(new KeyFrame(effectiveDuration(),
                new KeyValue(floatProgress, target, MATERIAL_EASING)));
        floatTimeline.setOnFinished(e -> floatTimeline = null);
        floatTimeline.play();
    }

    private void retargetAccent() {
        double target = getSkinnable().isFocused() ? 1.0 : 0.0;
        if (shouldSnap()) {
            stopAccentTimeline();
            accentTarget = target;
            accentProgress.set(target);
            return;
        }
        if (accentTimeline != null && accentTarget == target) {
            return;
        }
        if (accentTimeline == null && accentProgress.get() == target) {
            return;
        }
        stopAccentTimeline();
        accentTarget = target;
        accentTimeline = new Timeline(new KeyFrame(effectiveDuration(),
                new KeyValue(accentProgress, target, MATERIAL_EASING)));
        accentTimeline.setOnFinished(e -> accentTimeline = null);
        accentTimeline.play();
    }

    private void stopFloatTimeline() {
        if (floatTimeline != null) {
            floatTimeline.stop();
            floatTimeline = null;
        }
    }

    private void stopAccentTimeline() {
        if (accentTimeline != null) {
            accentTimeline.stop();
            accentTimeline = null;
        }
    }

    private void applyFloatVisual() {
        double p = floatProgress.get();
        labelNode.setTranslateY(p * floatedTranslateY);
        double s = 1.0 + p * (floatScale() - 1.0);
        labelScale.setX(s);
        labelScale.setY(s);
    }

    private void applyAccentVisual() {
        double p = accentProgress.get();
        accentScale.setX(ACCENT_REST_SCALE_X + p * (1.0 - ACCENT_REST_SCALE_X));
        accentLine.setOpacity(p);
    }

    private boolean shouldSnap() {
        if (!animatedEnabled() || !isShowing()) {
            return true;
        }
        Duration d = effectiveDuration();
        return d.isUnknown() || d.lessThanOrEqualTo(Duration.ZERO);
    }

    private Duration effectiveDuration() {
        Duration d = animationDuration.getValue();
        return d == null ? RXMaterialTextField.DEFAULT_ANIMATION_DURATION : d;
    }

    private boolean isShowing() {
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return false;
        }
        Window window = scene.getWindow();
        return window != null && window.isShowing();
    }

    // ==================== Derived state ====================

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private String effectiveLabelText() {
        String lt = labelText.getValue();
        if (lt != null && !lt.isBlank()) {
            return lt;
        }
        return str(getSkinnable().getPromptText());
    }

    private boolean hasLabelSource() {
        return !effectiveLabelText().isEmpty();
    }

    private boolean floatingLabelEnabled() {
        Boolean v = floatingLabel.getValue();
        return v == null || v;
    }

    private boolean animatedEnabled() {
        Boolean v = animated.getValue();
        return v == null || v;
    }

    private boolean isFloated() {
        if (!floatingLabelEnabled()) {
            return true;
        }
        TextField c = getSkinnable();
        return c.isFocused() || !str(c.getText()).isEmpty();
    }

    private double floatScale() {
        Number n = labelFloatScale.getValue();
        double s = (n == null) ? RXMaterialTextField.DEFAULT_LABEL_FLOAT_SCALE : n.doubleValue();
        return Math.max(0.0, s);
    }

    private boolean hasSupportingContent() {
        return !str(helperText.getValue()).isEmpty() || !str(errorText.getValue()).isEmpty();
    }

    // ==================== Bands ====================

    private double labelBand() {
        if (!hasLabelSource()) {
            return 0.0;
        }
        return snapSizeY(unscaledLabelHeight() * floatScale());
    }

    private double unscaledLabelHeight() {
        return Math.max(0.0, labelNode.prefHeight(-1));
    }

    private double lineBand() {
        // Reserve the thicker of the two lines so a custom-styled activation
        // line cannot bleed past the band into the supporting row.
        return snapSizeY(Math.max(accentThickness(), activationThickness())) + snapSizeY(LINE_GAP);
    }

    private double accentThickness() {
        double t = accentLine.prefHeight(-1);
        return t <= 0.0 ? FALLBACK_ACCENT_THICKNESS : t;
    }

    private double activationThickness() {
        double t = activationLine.prefHeight(-1);
        return t <= 0.0 ? FALLBACK_ACTIVATION_THICKNESS : t;
    }

    private double supportingBand() {
        if (!hasSupportingContent()) {
            return 0.0;
        }
        return snapSizeY(Math.max(helperLabel.prefHeight(-1), errorLabel.prefHeight(-1)));
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        final double labelBand = labelBand();
        final double lineBand = lineBand();
        final double supportingBand = supportingBand();
        final double editorH = Math.max(0.0, h - labelBand - lineBand - supportingBand);

        // Hand the band-trimmed editor sub-rect to the base skin.
        super.layoutChildren(x, y + labelBand, w, editorH);

        layoutFilledContainer(x, y, w, labelBand + editorH + lineBand);
        layoutFloatingLabel(x, y, labelBand, editorH);
        layoutActivationLines(x, y + labelBand + editorH, w);
        layoutSupporting(x, y + h - supportingBand, w, supportingBand);
    }

    private void layoutFilledContainer(double x, double y, double w, double bandHeight) {
        // Span the full control width and from the control top edge down through
        // the activation-line band (the supporting row sits outside the box).
        double leftInset = snappedLeftInset();
        double rightInset = snappedRightInset();
        double topInset = snappedTopInset();
        double boxX = x - leftInset;
        double boxY = y - topInset;
        double boxW = w + leftInset + rightInset;
        double boxH = bandHeight + topInset;
        filledContainer.resizeRelocate(boxX, boxY, boxW, boxH);
        stateOverlay.resizeRelocate(boxX, boxY, boxW, boxH);
    }

    private void layoutFloatingLabel(double x, double y, double labelBand, double editorH) {
        if (!hasLabelSource()) {
            labelNode.setVisible(false);
            return;
        }
        labelNode.setVisible(true);
        final double labelWidth = snapSizeX(labelNode.prefWidth(-1));
        final double labelHeight = snapSizeY(labelNode.prefHeight(-1));
        // Align with the editor text: reuse the base skin's exact left offset
        // (past the left wrapper + left text padding) rather than re-deriving it.
        final double textStartX = x + editorLeftOffset();
        final double restingY = y + labelBand + Math.max(0.0, (editorH - labelHeight) / 2.0);
        labelNode.resizeRelocate(textStartX, restingY, labelWidth, labelHeight);
        // Floated position sits at the top of the content area; progress maps
        // the label between resting (0) and floated (1).
        floatedTranslateY = y - restingY;
        applyFloatVisual();
    }

    private void layoutActivationLines(double x, double lineRegionTop, double w) {
        final double lineY = lineRegionTop + snapSizeY(LINE_GAP);
        activationLine.resizeRelocate(x, lineY, w, snapSizeY(activationThickness()));
        accentLine.resizeRelocate(x, lineY, w, snapSizeY(accentThickness()));

        accentScale.setPivotX(w / 2.0);
        accentScale.setPivotY(0.0);
        applyAccentVisual();
    }

    private void layoutSupporting(double x, double top, double w, double supportingBand) {
        if (supportingBand <= 0.0) {
            supporting.setVisible(false);
            return;
        }
        supporting.setVisible(true);
        supporting.resizeRelocate(x, top, w, supportingBand);
    }

    /** {@inheritDoc} */
    @Override
    public HitInfo getIndex(double x, double y) {
        // Cancel the editor's downward labelBand shift before delegating to the
        // base (which further removes its own leftAdjust / tpTop).
        return super.getIndex(x, y - labelBand());
    }

    @Override
    protected double computePrefHeight(double w, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return super.computePrefHeight(w, topInset, rightInset, bottomInset, leftInset)
                + labelBand() + lineBand() + supportingBand();
    }

    @Override
    protected double computeMinHeight(double w, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computeMinHeight(w, topInset, rightInset, bottomInset, leftInset)
                + labelBand() + lineBand() + supportingBand();
    }

    @Override
    protected double computeMaxHeight(double w, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // TextFieldSkin already maps max height to prefHeight; restate it here so
        // the band-inclusive prefHeight is unambiguously the max for this skin.
        return getSkinnable().prefHeight(w);
    }

    /** {@inheritDoc} */
    @Override
    public double computeBaselineOffset(double topInset, double rightInset,
                                        double bottomInset, double leftInset) {
        return super.computeBaselineOffset(topInset, rightInset, bottomInset, leftInset) + labelBand();
    }
}
