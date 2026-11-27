package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTaskButton;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.TaskButtonAccess;
import io.github.leewyatt.rxcontrols.internal.feedback.AccessibleNames;
import io.github.leewyatt.rxcontrols.internal.feedback.CaptionBinding;
import io.github.leewyatt.rxcontrols.internal.feedback.ClickBehavior;
import io.github.leewyatt.rxcontrols.internal.feedback.FeedbackDurations;
import io.github.leewyatt.rxcontrols.internal.feedback.FeedbackFaces;
import io.github.leewyatt.rxcontrols.internal.feedback.IndicatorFace;
import io.github.leewyatt.rxcontrols.internal.feedback.RingIndicator;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;

/**
 * Skin for {@link RXTaskButton}: a ripple layer below four faces, one per
 * {@link RXTaskButton.State}. The idle face shows the button's own text and
 * graphic; the pending, succeeded and failed faces each show an indicator
 * next to their own text and graphic. The button is sized to the largest
 * face, so swapping faces never changes its size.
 *
 * <p>The pending indicator is a ring that draws the button's progress, or
 * travels round while the progress is indeterminate; the button's
 * {@code pendingIndicator}, when set, takes its place. The succeeded and
 * failed indicators are regions shaped from CSS. The ring and the two shapes
 * are painted in the button's text fill, so they follow whatever colors the
 * text: a theme, a state rule or the application.</p>
 *
 * <p>The button owns the state; the skin only follows it. A face swap plays
 * the button's animation when one is set, forward to the pending and result
 * faces and backward to the idle face, and cuts directly otherwise. A skin
 * installed while a face other than the idle one is due shows it at once,
 * without an animation.</p>
 *
 * <p>This skin does not extend {@code ButtonSkin} (the faces own the text and
 * graphic nodes), so the standard button behavior is wired here through the
 * public {@code arm()}/{@code disarm()}/{@code fire()} API: mouse arming,
 * SPACE (and ENTER on non-Mac platforms) activation, disarm on focus loss,
 * and the default/cancel button accelerators. A press only arms the button
 * while an activation would do something, so a press the button is going to
 * ignore shows no ripple. Mnemonics are not supported.</p>
 */
public class RXTaskButtonSkin extends RXSkinBase<RXTaskButton> {

    // ==================== Nodes ====================

    private final IndicatorFace idleFace;
    private final IndicatorFace pendingFace;
    private final IndicatorFace succeededFace;
    private final IndicatorFace failedFace;
    private final FeedbackFaces faces;
    private final RingIndicator ring = new RingIndicator();
    private final ObjectBinding<Background> shapeBackground;
    private final RippleDecoration ripple;
    private final ArmedRippleTrigger rippleTrigger;

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given button.
     *
     * @param button the button this skin is attached to
     */
    public RXTaskButtonSkin(RXTaskButton button) {
        super(button);

        ripple = new RippleDecoration(button, button.rippleEnabledProperty(),
                button.stateOverlayEnabledProperty(), button.rippleFillProperty(),
                button::getRippleOpacity, null, button.rippleCornerRadiusProperty());
        rippleTrigger = new ArmedRippleTrigger(button, ripple,
                button::isRippleEnabled, button::isRippleCentered);
        idleFace = new IndicatorFace(button, "idle-face");
        pendingFace = new IndicatorFace(button, "pending-face");
        succeededFace = new IndicatorFace(button, "succeeded-face");
        failedFace = new IndicatorFace(button, "failed-face");
        faces = new FeedbackFaces(idleFace, pendingFace, succeededFace, failedFace);
        getChildren().addAll(ripple.getLayer(), faces);

        // Registered first, so it runs last: by then the state listener is
        // gone and nothing can swap the face again.
        disposer.registerDisposeTask(() -> {
            faces.dispose(button.getAnimation());
            // Only this skin's nodes: a replacement skin may already have
            // added its own children to the control.
            getChildren().removeAll(ripple.getLayer(), faces);
        });

        // The idle face shows the button's own text and graphic. Each other
        // face has its own graphic: a node cannot be parented by two faces.
        bindFace(idleFace, button.textProperty(), button.graphicProperty());
        bindFace(pendingFace, button.pendingTextProperty(), button.pendingGraphicProperty());
        bindFace(succeededFace, button.succeededTextProperty(), button.succeededGraphicProperty());
        bindFace(failedFace, button.failedTextProperty(), button.failedGraphicProperty());
        // The gap between indicator and caption is laid out by the faces.
        disposer.registerListener(button.graphicTextGapProperty(), () -> {
            idleFace.requestLayout();
            pendingFace.requestLayout();
            succeededFace.requestLayout();
            failedFace.requestLayout();
        });

        // Bound, not styled: a style sheet cannot color the ring, the check
        // or the cross, they always match the text. Only the stop mark of the
        // ring takes its color from the style sheet.
        disposer.registerBinding(ring.paintProperty(), button.textFillProperty());
        shapeBackground = Bindings.createObjectBinding(() -> {
            Paint fill = button.getTextFill();
            return fill == null ? null : new Background(new BackgroundFill(fill, null, null));
        }, button.textFillProperty());
        disposer.registerDisposeTask(shapeBackground::dispose);
        disposer.registerBinding(shapeOf(succeededFace).backgroundProperty(), shapeBackground);
        disposer.registerBinding(shapeOf(failedFace).backgroundProperty(), shapeBackground);

        disposer.registerDisposeTask(ring::dispose);
        // The stop mark shows exactly while a click would cancel, not while
        // the button is merely cancellable. It belongs to the ring: a custom
        // indicator gets none.
        disposer.registerBinding(ring.stopMarkVisibleProperty(), TaskButtonAccess.cancelReadyOf(button));
        ring.setProgress(button.getProgress());
        disposer.registerListener(button.progressProperty(), () -> ring.setProgress(button.getProgress()));
        updatePendingIndicator();
        disposer.registerListener(button.pendingIndicatorProperty(), this::updatePendingIndicator);

        rippleTrigger.installPointerTracking(disposer);
        rippleTrigger.installPlayRipple(disposer);
        disposer.registerListener(button.armedProperty(), rippleTrigger::handleArmedChanged);
        new ClickBehavior(button, () -> TaskButtonAccess.activationOf(button))
                .install(disposer);

        // A button that is already past idle starts on the face that is due.
        showFace(false);
        disposer.registerListener(button.stateProperty(), () -> showFace(true));
    }

    private void bindFace(IndicatorFace face, StringProperty text, ObjectProperty<Node> graphic) {
        RXTaskButton button = getSkinnable();
        Label caption = face.getCaption();
        disposer.registerBinding(caption.textProperty(), text);
        disposer.registerBinding(caption.graphicProperty(), graphic);
        CaptionBinding.bindAppearance(disposer, caption, button);
        // labelPadding pads the whole indicator-and-caption block, as it pads
        // the text and graphic of a standard button.
        disposer.registerBinding(face.paddingProperty(), button.labelPaddingProperty());
    }

    // ==================== Indicators ====================

    private static Region shapeOf(IndicatorFace face) {
        return (Region) face.getIndicator();
    }

    // The button does not drive an indicator of the application: it neither
    // sets its progress nor paints it.
    private void updatePendingIndicator() {
        RXTaskButton button = getSkinnable();
        if (button == null) {
            // Told once more after dispose; the indicator belongs to the
            // skin that replaced this one.
            return;
        }
        Node custom = button.getPendingIndicator();
        pendingFace.setIndicator(custom == null ? ring : custom);
    }

    // ==================== Faces ====================

    // Reads the state instead of taking it from the notification: a listener
    // that ran earlier may have moved the button on, and the face follows
    // where the button is now.
    private void showFace(boolean animate) {
        RXTaskButton button = getSkinnable();
        if (button == null) {
            // A listener removed while the state is being reported is still
            // told once; this skin is disposed and has nothing left to show.
            return;
        }
        IndicatorFace face = faceOf(button.getState());
        if (face == faces.getCurrentFace()) {
            return;
        }
        TransitionDirection direction = face == idleFace
                ? TransitionDirection.BACKWARD
                : TransitionDirection.FORWARD;
        // Only Duration.ZERO turns the animation off; unusable values still
        // animate, for the default duration.
        faces.show(face, direction, button.getAnimation(),
                FeedbackDurations.finiteOrDefault(button.getAnimationDuration(),
                        RXTaskButton.DEFAULT_ANIMATION_DURATION),
                animate);
        button.notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    private IndicatorFace faceOf(RXTaskButton.State state) {
        if (state == RXTaskButton.State.PENDING) {
            return pendingFace;
        }
        if (state == RXTaskButton.State.SUCCEEDED) {
            return succeededFace;
        }
        if (state == RXTaskButton.State.FAILED) {
            return failedFace;
        }
        return idleFace;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
        faces.resizeRelocate(x, y, w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + faces.prefWidth(h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + faces.prefHeight(w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + faces.minWidth(h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + faces.minHeight(w) + bottomInset;
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

    // ==================== Accessibility ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.TEXT) {
            IndicatorFace face = (IndicatorFace) faces.getCurrentFace();
            return AccessibleNames.resolve(getSkinnable(), face.getCaption().getText());
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        ripple.dispose();
    }
}
