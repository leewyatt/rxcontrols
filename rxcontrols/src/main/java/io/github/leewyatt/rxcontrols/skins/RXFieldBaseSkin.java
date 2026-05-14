package io.github.leewyatt.rxcontrols.skins;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.StackPane;
import javafx.scene.text.HitInfo;

/**
 * Skin extension point — shared base for skins that need to render user-supplied
 * left and right nodes inside a {@link TextField}. The base skin reads the
 * "effective" left/right node from the two {@link ObservableValue}s passed by
 * the subclass (so subclasses can layer default nodes on top of the user's
 * selection without writing back to the control's own properties).
 * <p>
 * The class is concrete so it can be used directly for ad-hoc validation
 * during development; production code should prefer subclassing or using
 * one of the rxcontrols {@code RX*FieldSkin} classes.
 * <p>
 * Contract for the supplied observables: {@code effectiveLeft} and
 * {@code effectiveRight} should not resolve to the same {@link Node} instance
 * at the same time. {@link io.github.leewyatt.rxcontrols.RXTextField}
 * enforces this for its own setters by clearing the opposite slot when the
 * same node is moved across — see its slot migration semantics. Subclasses
 * that bypass {@code RXTextField}'s setters (or feed in custom effective
 * observables) are responsible for the same uniqueness; if both observables
 * resolve to the same node, only the right wrapper will keep it (JavaFX
 * reparents the node) and the left wrapper will render empty.
 */
public class RXFieldBaseSkin extends TextFieldSkin {

    private static final PseudoClass SHOWING_LEFT_NODE = PseudoClass.getPseudoClass("showing-left-node");
    private static final PseudoClass SHOWING_RIGHT_NODE = PseudoClass.getPseudoClass("showing-right-node");
    private static final PseudoClass SHOWING_NO_SIDE_NODES = PseudoClass.getPseudoClass("showing-no-side-nodes");

    private static final String LEFT_WRAPPER_CLASS = "left-wrapper";
    private static final String RIGHT_WRAPPER_CLASS = "right-wrapper";

    private final TextField control;
    private final ObservableValue<Node> effectiveLeft;
    private final ObservableValue<Node> effectiveRight;

    private StackPane leftWrapper;
    private StackPane rightWrapper;

    private final ChangeListener<Node> leftListener = (obs, oldVal, newVal) -> updateChildren();
    private final ChangeListener<Node> rightListener = (obs, oldVal, newVal) -> updateChildren();
    private final ChangeListener<Boolean> wrapperNeedsLayoutListener = (obs, oldVal, newVal) -> {
        if (Boolean.TRUE.equals(newVal)) {
            getSkinnable().requestLayout();
        }
    };

    public RXFieldBaseSkin(TextField control,
                           ObservableValue<Node> effectiveLeft,
                           ObservableValue<Node> effectiveRight) {
        super(control);
        this.control = control;
        this.effectiveLeft = effectiveLeft;
        this.effectiveRight = effectiveRight;

        effectiveLeft.addListener(leftListener);
        effectiveRight.addListener(rightListener);

        updateChildren();
    }

    // ==================== Children / pseudo-class wiring ====================

    private void updateChildren() {
        Node newLeft = effectiveLeft.getValue();
        Node newRight = effectiveRight.getValue();

        // Release both wrappers before constructing any new wrapper, so that
        // user nodes are fully detached from old parents before being
        // re-parented into freshly created wrappers below.
        leftWrapper = releaseWrapper(leftWrapper);
        rightWrapper = releaseWrapper(rightWrapper);

        if (newLeft != null) {
            leftWrapper = createWrapper(newLeft, Pos.CENTER_LEFT, LEFT_WRAPPER_CLASS);
        }
        if (newRight != null) {
            rightWrapper = createWrapper(newRight, Pos.CENTER_RIGHT, RIGHT_WRAPPER_CLASS);
        }

        control.pseudoClassStateChanged(SHOWING_LEFT_NODE, newLeft != null);
        control.pseudoClassStateChanged(SHOWING_RIGHT_NODE, newRight != null);
        control.pseudoClassStateChanged(SHOWING_NO_SIDE_NODES, newLeft == null && newRight == null);
        // Removing an unmanaged child (releaseWrapper above) does not invalidate
        // the skin's layout on its own, so the text node would stay at its
        // previous left offset until something else triggered a layout pass.
        control.requestLayout();
    }

    private StackPane createWrapper(Node content, Pos alignment, String styleClass) {
        StackPane wrapper = new StackPane(content);
        // Unmanaged: we size/position it manually in layoutChildren. The
        // downside is that the wrapper's requestLayout() does not walk up to
        // the skin — see needsLayoutProperty listener below for the bridge.
        wrapper.setManaged(false);
        wrapper.setAlignment(alignment);
        wrapper.getStyleClass().add(styleClass);
        // CSS pseudo-class changes on the wrapper (e.g. :hover bumping padding)
        // flip its needsLayout flag. Without this bridge the skin would not
        // re-run layoutChildren and the wrapper's outer bounds would stay
        // stale until something else invalidated the control.
        wrapper.needsLayoutProperty().addListener(wrapperNeedsLayoutListener);
        getChildren().add(wrapper);
        return wrapper;
    }

    private StackPane releaseWrapper(StackPane wrapper) {
        if (wrapper != null) {
            wrapper.needsLayoutProperty().removeListener(wrapperNeedsLayoutListener);
            wrapper.getChildren().clear();
            getChildren().remove(wrapper);
        }
        return null;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        final double leftWidth = leftWrapper == null
                ? 0.0
                : snapSizeX(leftWrapper.prefWidth(h));
        final double rightWidth = rightWrapper == null
                ? 0.0
                : snapSizeX(rightWrapper.prefWidth(h));
        final double innerWidth = Math.max(0.0, w - leftWidth - rightWidth);

        super.layoutChildren(x + leftWidth, y, innerWidth, h);

        if (leftWrapper != null) {
            leftWrapper.resizeRelocate(x, y, leftWidth, h);
        }

        if (rightWrapper != null) {
            rightWrapper.resizeRelocate(x + w - rightWidth, y, rightWidth, h);
        }
    }

    @Override
    public HitInfo getIndex(double x, double y) {
        // Use the last-laid-out width rather than prefWidth: a CSS state change
        // (e.g. :hover bumping padding) can shift prefWidth before relayout,
        // which makes hit-testing drift by a few pixels.
        final double leftWidth = leftWrapper == null ? 0.0 : leftWrapper.getWidth();
        return super.getIndex(x - leftWidth, y);
    }

    @Override
    protected double computePrefWidth(double h, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double pw = super.computePrefWidth(h, topInset, rightInset, bottomInset, leftInset);
        final double leftWidth = leftWrapper == null ? 0.0 : snapSizeX(leftWrapper.prefWidth(h));
        final double rightWidth = rightWrapper == null ? 0.0 : snapSizeX(rightWrapper.prefWidth(h));
        return pw + leftWidth + rightWidth;
    }

    @Override
    protected double computePrefHeight(double w, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double ph = super.computePrefHeight(w, topInset, rightInset, bottomInset, leftInset);
        final double leftHeight = leftWrapper == null ? 0.0 : snapSizeY(leftWrapper.prefHeight(-1));
        final double rightHeight = rightWrapper == null ? 0.0 : snapSizeY(rightWrapper.prefHeight(-1));
        // super.computePrefHeight already includes vertical insets; bare wrapper
        // heights need them added before being compared, otherwise a tall side
        // node would force the control to shrink its own padding away.
        final double sidesH = Math.max(leftHeight, rightHeight) + topInset + bottomInset;
        return Math.max(ph, sidesH);
    }

    @Override
    protected double computeMinWidth(double h, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double mw = super.computeMinWidth(h, topInset, rightInset, bottomInset, leftInset);
        final double leftWidth = leftWrapper == null ? 0.0 : snapSizeX(leftWrapper.minWidth(h));
        final double rightWidth = rightWrapper == null ? 0.0 : snapSizeX(rightWrapper.minWidth(h));
        return mw + leftWidth + rightWidth;
    }

    @Override
    protected double computeMinHeight(double w, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double mh = super.computeMinHeight(w, topInset, rightInset, bottomInset, leftInset);
        final double leftHeight = leftWrapper == null ? 0.0 : snapSizeY(leftWrapper.minHeight(-1));
        final double rightHeight = rightWrapper == null ? 0.0 : snapSizeY(rightWrapper.minHeight(-1));
        final double sidesH = Math.max(leftHeight, rightHeight) + topInset + bottomInset;
        return Math.max(mh, sidesH);
    }

    // ==================== Lifecycle ====================

    @Override
    public void dispose() {
        effectiveLeft.removeListener(leftListener);
        effectiveRight.removeListener(rightListener);
        // TextFieldSkin.dispose() removes its own children explicitly, so we do
        // the same for ours — otherwise on skin replacement the old wrappers
        // stay attached to the control and the user's nodes still parent to
        // them, causing the next setLeft/setRight to fail re-parenting.
        leftWrapper = releaseWrapper(leftWrapper);
        rightWrapper = releaseWrapper(rightWrapper);
        super.dispose();
    }
}
