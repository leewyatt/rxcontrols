package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
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
 * (helper / error) row, and a built-in clear affordance composed into the
 * trailing slot.
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

    /** Null / non-finite fallback for the label gap; must match the controls' default. */
    private static final double FALLBACK_LABEL_GAP = 4.0;
    /** Null / non-finite fallback for the supporting gap; must match the controls' default. */
    private static final double FALLBACK_SUPPORTING_GAP = 4.0;
    /** Collapsed horizontal scale of the accent line when unfocused. */
    private static final double ACCENT_REST_SCALE_X = 0.05;
    /** Fallback accent-line thickness before CSS resolves {@code -fx-pref-height}. */
    private static final double FALLBACK_ACCENT_THICKNESS = 2.0;
    /** Fallback activation-line thickness before CSS resolves {@code -fx-pref-height}. */
    private static final double FALLBACK_ACTIVATION_THICKNESS = 1.0;
    /** Null-value fallback for the animation duration; must match the controls' default. */
    private static final Duration FALLBACK_ANIMATION_DURATION = Duration.millis(180.0);
    /** Null / non-finite fallback for the label float scale; must match the controls' default. */
    private static final double FALLBACK_LABEL_FLOAT_SCALE = 0.85;
    /** House "fast-out slow-in" easing (Material standard easing). */
    private static final Interpolator MATERIAL_EASING = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    private static final PseudoClass FLOATED = PseudoClass.getPseudoClass("floated");

    private static final String ACTIVATION_LINE_CLASS = "activation-line";
    private static final String ACCENT_LINE_CLASS = "accent-line";
    private static final String SUPPORTING_CLASS = "supporting";
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
    private final ObservableValue<Number> labelGap;
    private final ObservableValue<Number> supportingGap;
    private final ObservableValue<Boolean> showClearButton;

    /**
     * Skin-controlled relay handed to the base as its effective-right node: set
     * to the internal trailing container when there is trailing content, else
     * {@code null}. Created before {@code super(...)}; populated after.
     */
    private final ObjectProperty<Node> effectiveRight;

    // ==================== Decoration nodes ====================

    private final Label labelNode = new Label();
    private final Scale labelScale = new Scale(1.0, 1.0, 0.0, 0.0);
    private final Region activationLine = new Region();
    private final Region accentLine = new Region();
    private final Scale accentScale = new Scale(ACCENT_REST_SCALE_X, 1.0, 0.0, 0.0);
    private final Label supporting = new Label();

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

    /** Pending hook that stamps labelFor once this skin actually attaches. */
    private ChangeListener<Skin<?>> pendingLabelForListener;

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
     * @param labelGap          floated-label-to-editor gap observable
     * @param supportingGap     line-to-supporting-text gap observable
     * @param showClearButton   built-in clear-button-enabled observable
     */
    protected RXMaterialFieldBaseSkin(TextField control,
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
                                      ObservableValue<Number> labelGap,
                                      ObservableValue<Number> supportingGap,
                                      ObservableValue<Boolean> showClearButton) {
        // The effective-right relay must exist before super(...) and cannot
        // reference this; a private constructor receives it and wires the
        // trailing composition afterward.
        this(control, userLeading, userTrailing, userTextPadding, labelText, helperText,
                errorText, invalid, floatingLabel, animated, animationDuration,
                labelFloatScale, labelGap, supportingGap, showClearButton,
                new SimpleObjectProperty<>());
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
                                    ObservableValue<Number> labelGap,
                                    ObservableValue<Number> supportingGap,
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
        this.labelGap = labelGap;
        this.supportingGap = supportingGap;

        // A Label already carries the built-in ".label" style class; the CSS
        // targets it through the direct-child path (AGENTS §2.4.4).
        labelNode.setManaged(false);
        labelNode.setMouseTransparent(true);
        labelNode.getTransforms().add(labelScale);
        // Label the control for assistive technology via the LABELED_BY channel
        // (labelFor stamps Node.labeledBy on the control). Deferred to the
        // actual skin attach: on a skin replacement this constructor runs
        // BEFORE the predecessor's dispose, whose setLabelFor(null) would wipe
        // a constructor-time stamp (Node.labeledBy is one last-writer-wins
        // field); change listeners fire after Control's invalidated(), i.e.
        // after that teardown. A same-class ghost skin (JFX17 setSkin
        // short-circuit) never attaches and so never stamps. The user-owned
        // accessibleText property is deliberately left untouched: writing it
        // would throw if the user bound it and clobber user values.
        pendingLabelForListener = (obs, oldSkin, newSkin) -> {
            if (newSkin == this) {
                control.skinProperty().removeListener(pendingLabelForListener);
                pendingLabelForListener = null;
                syncLabelFor();
            } else if (newSkin != null) {
                // Another skin won; this instance will never attach.
                control.skinProperty().removeListener(pendingLabelForListener);
                pendingLabelForListener = null;
            }
        };
        control.skinProperty().addListener(pendingLabelForListener);

        activationLine.getStyleClass().add(ACTIVATION_LINE_CLASS);
        activationLine.setManaged(false);
        activationLine.setMouseTransparent(true);

        accentLine.getStyleClass().add(ACCENT_LINE_CLASS);
        accentLine.setManaged(false);
        accentLine.setMouseTransparent(true);
        accentLine.getTransforms().add(accentScale);

        // Single supporting-text label showing helper or error text (mutually
        // exclusive, no cross-fade). setAll (not add) drops the Label's default
        // ".label" class so the floating-label "> .label" selectors (focused ->
        // primary, invalid -> danger) do not bleed onto it now that it is a direct
        // child of the control; its colours come from the ".supporting" rules.
        supporting.getStyleClass().setAll(SUPPORTING_CLASS);
        supporting.setManaged(false);
        supporting.setMouseTransparent(true);

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
        disposer.registerListener(labelGap, this::onLayoutChanged);
        disposer.registerListener(supportingGap, this::onLayoutChanged);
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
        // transforms, the LABELED_BY hook, and the trailing box's children —
        // including any user trailing node) on dispose so nothing lingers,
        // mirroring what RXFieldBaseSkin does for its wrappers. (builtinTrailing
        // itself rides inside the base's right-wrapper, which the base releases.)
        disposer.registerDisposeTask(() -> {
            if (pendingLabelForListener != null) {
                control.skinProperty().removeListener(pendingLabelForListener);
                pendingLabelForListener = null;
            }
            // Withdraw LABELED_BY only while the relation is still ours: an
            // external Label.setLabelFor(field) stamp must survive our teardown.
            if (control.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY) == labelNode) {
                labelNode.setLabelFor(null);
            }
            labelNode.getTransforms().remove(labelScale);
            accentLine.getTransforms().remove(accentScale);
            builtinTrailing.getChildren().clear();
            getChildren().removeAll(activationLine, accentLine, labelNode, supporting);
        });

        labelNode.setText(effectiveLabelText());
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
        syncLabelFor();
        getSkinnable().requestLayout();
    }

    /**
     * Keeps the LABELED_BY relation in step with the effective label source:
     * stamped only while a label source exists, withdrawn when it goes blank.
     * Gated on actual attach (stamping earlier races with a replaced
     * predecessor's teardown) and, on withdrawal, on still owning the relation
     * ({@code setLabelFor(null)} wipes {@code Node.labeledBy} unconditionally —
     * an external {@code Label.setLabelFor(field)} stamp must survive us).
     */
    private void syncLabelFor() {
        TextField control = getSkinnable();
        if (control == null || control.getSkin() != this) {
            return;
        }
        if (hasLabelSource()) {
            labelNode.setLabelFor(control);
        } else if (control.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY) == labelNode) {
            labelNode.setLabelFor(null);
        }
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
        // Turning animation off mid-transition snaps to the current targets —
        // including a clear-button fade in flight.
        retargetFloat();
        retargetAccent();
        if (shouldSnap()) {
            clearFade.stop();
            clearButton.setOpacity(clearTarget);
        }
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

    private void updateSupporting() {
        String err = str(errorText.getValue());
        String help = str(helperText.getValue());
        // Error text replaces helper text in the same slot when invalid; otherwise
        // the helper text shows. The :invalid pseudo-class recolours it (see CSS), so
        // an invalid field with no error message keeps the helper text but turns red.
        boolean showError = Boolean.TRUE.equals(invalid.getValue()) && !err.isEmpty();
        String text = showError ? err : help;
        supporting.setText(text);
        supporting.setVisible(!text.isEmpty());
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
        // INDEFINITE would build a tween that never completes, leaving the
        // float / accent / clear-fade stuck mid-animation.
        return d.isUnknown() || d.isIndefinite() || d.lessThanOrEqualTo(Duration.ZERO);
    }

    private Duration effectiveDuration() {
        Duration d = animationDuration.getValue();
        return d == null ? FALLBACK_ANIMATION_DURATION : d;
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
        // Blank prompt text is "no label source" too — otherwise a whitespace
        // prompt reserves a label band and becomes the accessible name.
        String pt = getSkinnable().getPromptText();
        return (pt == null || pt.isBlank()) ? "" : pt;
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
        double s = (n == null) ? FALLBACK_LABEL_FLOAT_SCALE : n.doubleValue();
        if (!Double.isFinite(s)) {
            // NaN/Infinity would flow through the band math into pref height
            // and the label transform; treat non-finite as "use the default".
            return FALLBACK_LABEL_FLOAT_SCALE;
        }
        return Math.max(0.0, s);
    }


    private double labelGap() {
        return gapOrDefault(labelGap, FALLBACK_LABEL_GAP);
    }

    private double supportingGap() {
        return gapOrDefault(supportingGap, FALLBACK_SUPPORTING_GAP);
    }

    private static double gapOrDefault(ObservableValue<Number> gap, double fallback) {
        Number n = gap.getValue();
        double v = (n == null) ? fallback : n.doubleValue();
        if (!Double.isFinite(v)) {
            return fallback;
        }
        return Math.max(0.0, v);
    }

    // ==================== Bands ====================

    private double labelBand() {
        if (!hasLabelSource()) {
            return 0.0;
        }
        // Floated label height plus a breathing gap above the editor text; the
        // gap lives inside the band so pref/hit-test/baseline stay consistent.
        return snapSizeY(unscaledLabelHeight() * floatScale()) + snapSizeY(labelGap());
    }

    private double unscaledLabelHeight() {
        return Math.max(0.0, labelNode.prefHeight(-1));
    }

    private double lineBand() {
        // Reserve the thicker of the two lines so a custom-styled activation
        // line cannot bleed past the band into the supporting row. The gap
        // between the editor text and the line is NOT part of this band: it is
        // the effective text padding's bottom inset (UA default 0.25em),
        // matching how a plain text field spaces text off its bottom border.
        return snapSizeY(Math.max(accentThickness(), activationThickness()));
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
        if (supporting.getText().isEmpty()) {
            return 0.0;
        }
        // Gap between the activation line and the supporting text, inside the
        // band; layoutSupporting positions the text below the gap.
        return snapSizeY(supportingGap()) + snapSizeY(supporting.prefHeight(-1));
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

        layoutFloatingLabel(x, y, w, labelBand, editorH);
        layoutActivationLines(x, y + labelBand + editorH, w);
        layoutSupporting(x, y + h - supportingBand, w, supportingBand);
    }

    private void layoutFloatingLabel(double x, double y, double w, double labelBand, double editorH) {
        if (!hasLabelSource()) {
            labelNode.setVisible(false);
            return;
        }
        labelNode.setVisible(true);
        // Clamp to the editor's inner width so a long label (or promptText
        // fallback) ellipsizes like the native prompt instead of painting past
        // the control onto its neighbours; the unmanaged label has no clip.
        final double innerWidth = Math.max(0.0, w - editorLeftOffset() - editorRightOffset());
        final double labelWidth = Math.min(snapSizeX(labelNode.prefWidth(-1)), snapSizeX(innerWidth));
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
        final double lineY = lineRegionTop;
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
        final double gap = snapSizeY(supportingGap());
        supporting.resizeRelocate(x, top + gap, w, Math.max(0.0, supportingBand - gap));
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

    // computeMinHeight is deliberately not overridden: TextFieldSkin maps min
    // height to computePrefHeight via a virtual call that already lands on the
    // band-inclusive override above — adding the bands here again would
    // double-count them (min > pref). Same constraint in RXFieldBaseSkin.

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
