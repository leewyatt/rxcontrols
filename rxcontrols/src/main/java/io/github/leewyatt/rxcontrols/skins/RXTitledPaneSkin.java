package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventTarget;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Default skin of {@link RXTitledPane}.
 *
 * <p>Structure: {@code .header} holds the {@code .title} toggle area (an
 * {@link RXRipplePane} that is also the keyboard focus stop) and the {@code .actions}
 * box as siblings, so actions are structurally outside the toggle area. The title
 * holds a {@code .row} with the {@code .indicator-button > .indicator} and either the
 * default {@code .label} or the custom header node. {@code .body} is a skin-owned
 * clipping viewport around the content.</p>
 *
 * <p>The body is revealed, not squeezed: the content is laid out at its natural
 * height for the current width and the viewport height follows the transition
 * progress, so the content is not resized on every animation frame. Because the
 * progress is normalized, the target height is recomputed on each layout and follows
 * width or content changes during a transition.</p>
 */
public class RXTitledPaneSkin extends RXSkinBase<RXTitledPane> {

    /**
     * Key under which the title toggle node stores its pane in
     * {@link Node#getProperties()}; read by the accordion skin to recognise header
     * keyboard events.
     */
    static final String TRIGGER_KEY = "rx-titled-pane-trigger";

    private static final PseudoClass FOCUS_SHOWN = PseudoClass.getPseudoClass("focus-shown");

    private static final double INDICATOR_START_ANGLE = 0.0;
    private static final double INDICATOR_END_BASE_ANGLE = 90.0;
    private static final double INDICATOR_START_SWEEP = 90.0;
    private static final double INDICATOR_END_SWEEP = 180.0;

    // ==================== Nodes ====================

    private final HeaderRegion header = new HeaderRegion();
    private final TitleRegion title = new TitleRegion();
    private final HBox row = new HBox();
    private final StackPane titleSlot = new StackPane();
    private final StackPane indicatorButton = new StackPane();
    private final Region indicator = new Region();
    private final Label label = new Label();
    private final HBox actionsBox = new HBox();
    private final BodyRegion body = new BodyRegion();
    private final Rectangle bodyClip = new Rectangle();

    // ==================== State ====================

    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress");

    private Timeline timeline;
    private Node shownContent;
    private double contentLayoutHeight;

    private boolean armed;
    private Node armedArea;
    private boolean keyDown;
    private boolean mouseFocus;

    private final ChangeListener<Node> contentListener = (obs, oldValue, newValue) -> onContentChanged(oldValue, newValue);

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given pane.
     *
     * @param control the pane
     */
    public RXTitledPaneSkin(RXTitledPane control) {
        super(control);

        indicator.getStyleClass().add("indicator");
        indicator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        indicator.setMouseTransparent(true);
        indicatorButton.getStyleClass().add("indicator-button");
        indicatorButton.getChildren().add(indicator);
        indicatorButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        titleSlot.getStyleClass().add("slot");
        titleSlot.setAlignment(Pos.CENTER_LEFT);
        titleSlot.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleSlot, Priority.ALWAYS);

        row.getStyleClass().add("row");
        row.setAlignment(Pos.CENTER_LEFT);
        title.setContent(row);
        title.setAlignment(Pos.CENTER_LEFT);
        title.getProperties().put(TRIGGER_KEY, control);

        actionsBox.getStyleClass().add("actions");
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.setFillHeight(false);

        header.setParts(title, actionsBox);
        body.setClip(bodyClip);

        progress.set(control.isExpanded() ? 1.0 : 0.0);

        getChildren().setAll(body, header);

        disposer.registerBinding(label.textProperty(), control.textProperty());
        Bindings.bindContent(actionsBox.getChildren(), control.getActions());
        disposer.registerDisposeTask(() -> {
            Bindings.unbindContent(actionsBox.getChildren(), control.getActions());
            actionsBox.getChildren().clear();
        });

        onContentChanged(null, control.getContent());
        updateTitle();
        updateIndicator();
        updateActionsVisibility();
        updateBodyVisibility();

        control.contentProperty().addListener(contentListener);
        disposer.registerDisposeTask(() -> control.contentProperty().removeListener(contentListener));
        disposer.registerListener(control.headerProperty(), this::updateTitle);
        disposer.registerListener(control.graphicProperty(), this::updateTitle);
        disposer.registerListener(control.indicatorPositionProperty(), this::updateIndicator);
        disposer.registerListener(control.getActions(), this::updateActionsVisibility);
        disposer.registerListener(control.expandedProperty(), this::onExpandedChanged);
        disposer.registerListener(control.animatedProperty(), this::onAnimatedChanged);
        disposer.registerListener(progress, this::onProgressChanged);
        disposer.registerListener(control.focusedProperty(), this::forwardFocus);
        disposer.registerListener(title.focusedProperty(), this::onTitleFocusChanged);

        disposer.registerEventHandler(title, MouseEvent.MOUSE_PRESSED, this::onTitlePressed);
        disposer.registerEventHandler(title, MouseEvent.MOUSE_RELEASED, this::onTitleReleased);
        disposer.registerEventHandler(title, KeyEvent.KEY_PRESSED, this::onTitleKeyPressed);
        disposer.registerEventHandler(title, KeyEvent.KEY_RELEASED, this::onTitleKeyReleased);
    }

    // ==================== Package API ====================

    /**
     * Moves keyboard focus to the title toggle area.
     */
    void focusTrigger() {
        title.requestFocus();
    }

    // ==================== Title ====================

    private void updateTitle() {
        RXTitledPane control = getSkinnable();
        Node custom = control.getHeader();
        if (custom != null) {
            label.setGraphic(null);
            titleSlot.getChildren().setAll(custom);
        } else {
            label.setGraphic(control.getGraphic());
            titleSlot.getChildren().setAll(label);
        }
    }

    private void updateIndicator() {
        IndicatorPosition position = indicatorPositionOrDefault();
        if (position == IndicatorPosition.NONE) {
            row.getChildren().setAll(titleSlot);
        } else if (position == IndicatorPosition.END) {
            row.getChildren().setAll(titleSlot, indicatorButton);
        } else {
            row.getChildren().setAll(indicatorButton, titleSlot);
        }
        updateIndicatorRotation();
    }

    private void updateIndicatorRotation() {
        double p = progress.get();
        if (indicatorPositionOrDefault() == IndicatorPosition.END) {
            indicator.setRotate(INDICATOR_END_BASE_ANGLE + INDICATOR_END_SWEEP * p);
        } else {
            indicator.setRotate(INDICATOR_START_ANGLE + INDICATOR_START_SWEEP * p);
        }
    }

    private void updateActionsVisibility() {
        boolean shown = !getSkinnable().getActions().isEmpty();
        actionsBox.setVisible(shown);
        actionsBox.setManaged(shown);
    }

    private IndicatorPosition indicatorPositionOrDefault() {
        IndicatorPosition position = getSkinnable().getIndicatorPosition();
        return position == null ? RXTitledPane.DEFAULT_INDICATOR_POSITION : position;
    }

    private boolean indicatorOnlyTrigger() {
        return getSkinnable().getToggleTrigger() == ToggleTrigger.INDICATOR
                && indicatorPositionOrDefault() != IndicatorPosition.NONE;
    }

    // ==================== Input ====================

    private void toggle() {
        RXTitledPane control = getSkinnable();
        if (!control.expandedProperty().isBound()) {
            control.setExpanded(!control.isExpanded());
        }
    }

    private void onTitlePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        mouseFocus = true;
        if (title.isFocused()) {
            title.setFocusShown(false);
        } else {
            title.requestFocus();
        }
        if (indicatorOnlyTrigger()) {
            armedArea = isWithin(event.getTarget(), indicatorButton) ? indicatorButton : null;
        } else {
            armedArea = title;
        }
        armed = armedArea != null;
    }

    private void onTitleReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !armed) {
            return;
        }
        Node area = armedArea;
        armed = false;
        armedArea = null;
        Point2D local = area.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (local != null && area.contains(local)) {
            toggle();
        }
    }

    private void onTitleKeyPressed(KeyEvent event) {
        if (event.getTarget() != title) {
            return;
        }
        mouseFocus = false;
        title.setFocusShown(true);
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
            if (!keyDown) {
                keyDown = true;
                toggle();
            }
            event.consume();
        }
    }

    private void onTitleKeyReleased(KeyEvent event) {
        if (event.getTarget() != title) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
            keyDown = false;
            event.consume();
        }
    }

    private void onTitleFocusChanged() {
        if (title.isFocused()) {
            title.setFocusShown(!mouseFocus);
        } else {
            title.setFocusShown(false);
            mouseFocus = false;
            keyDown = false;
            armed = false;
        }
    }

    private void forwardFocus() {
        if (getSkinnable().isFocused()) {
            title.requestFocus();
        }
    }

    // ==================== Content ====================

    private void onContentChanged(Node oldValue, Node newValue) {
        boolean hadFocus = oldValue != null && containsFocusOwner(oldValue);
        shownContent = newValue;
        body.setContentNode(newValue);
        if (hadFocus) {
            focusTrigger();
        }
        getSkinnable().requestLayout();
    }

    private boolean containsFocusOwner(Node root) {
        Scene scene = getSkinnable().getScene();
        Node owner = scene == null ? null : scene.getFocusOwner();
        return owner != null && isWithin(owner, root);
    }

    private static boolean isWithin(EventTarget target, Node ancestor) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            if (node == ancestor) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ==================== Transition ====================

    private void onExpandedChanged() {
        RXTitledPane control = getSkinnable();
        boolean expanded = control.isExpanded();
        title.notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
        if (!expanded && containsFocusOwner(body)) {
            focusTrigger();
        }
        updateBodyVisibility();
        transitionTo(expanded ? 1.0 : 0.0);
    }

    private void onAnimatedChanged() {
        if (!getSkinnable().isAnimated() && timeline != null) {
            transitionTo(getSkinnable().isExpanded() ? 1.0 : 0.0);
        }
    }

    private void transitionTo(double target) {
        stopTimeline();
        double from = progress.get();
        Duration full = getSkinnable().getAnimationDuration();
        if (from == target || !canAnimate(full)) {
            progress.set(target);
            onSettled();
            return;
        }
        Duration duration = full.multiply(Math.abs(target - from));
        Timeline next = new Timeline(new KeyFrame(duration,
                new KeyValue(progress, target, interpolatorOrDefault())));
        next.setOnFinished(event -> {
            if (timeline == next) {
                timeline = null;
                onSettled();
            }
        });
        timeline = next;
        next.play();
    }

    private boolean canAnimate(Duration duration) {
        return getSkinnable().isAnimated()
                && duration != null
                && !duration.isIndefinite()
                && !duration.isUnknown()
                && duration.greaterThan(Duration.ZERO)
                && controlTreeShowingProperty().get();
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator interpolator = getSkinnable().getAnimationInterpolator();
        return interpolator == null ? RXTitledPane.DEFAULT_ANIMATION_INTERPOLATOR : interpolator;
    }

    private void stopTimeline() {
        if (timeline != null) {
            Timeline running = timeline;
            timeline = null;
            running.stop();
        }
    }

    private void onProgressChanged() {
        updateIndicatorRotation();
        updateBodyVisibility();
        getSkinnable().requestLayout();
    }

    private void onSettled() {
        updateBodyVisibility();
        if (progress.get() == 0.0 && containsFocusOwner(body)) {
            focusTrigger();
        }
        getSkinnable().requestLayout();
    }

    private void updateBodyVisibility() {
        body.setVisible(getSkinnable().isExpanded() || progress.get() > 0.0);
    }

    private boolean isSettledExpanded() {
        return timeline == null && progress.get() >= 1.0;
    }

    // ==================== Measurement ====================

    private double contentNaturalHeight(double contentWidth) {
        Node content = shownContent;
        if (content == null || !content.isManaged()) {
            return 0.0;
        }
        double width = content.getContentBias() == Orientation.HORIZONTAL ? contentWidth : -1;
        double min = content.minHeight(width);
        double pref = content.prefHeight(width);
        double max = content.maxHeight(width);
        return Math.max(min, Math.min(pref, max));
    }

    private double contentMinHeight(double contentWidth) {
        Node content = shownContent;
        if (content == null || !content.isManaged()) {
            return 0.0;
        }
        double width = content.getContentBias() == Orientation.HORIZONTAL ? contentWidth : -1;
        return content.minHeight(width);
    }

    private double bodyVerticalInsets() {
        return body.snappedTopInset() + body.snappedBottomInset();
    }

    private double bodyHorizontalInsets() {
        return body.snappedLeftInset() + body.snappedRightInset();
    }

    private double contentWidthFor(double paneContentWidth) {
        if (paneContentWidth < 0) {
            Node content = shownContent;
            return content == null ? -1 : content.prefWidth(-1);
        }
        return Math.max(0.0, paneContentWidth - bodyHorizontalInsets());
    }

    private double bodyNaturalHeight(double paneContentWidth) {
        if (shownContent == null || !shownContent.isManaged()) {
            return 0.0;
        }
        return bodyVerticalInsets() + contentNaturalHeight(contentWidthFor(paneContentWidth));
    }

    private double revealedBodyHeight(double paneContentWidth) {
        return snapSizeY(bodyNaturalHeight(paneContentWidth) * progress.get());
    }

    private double contentAreaWidth(double width, double leftInset, double rightInset) {
        return width < 0 ? -1 : Math.max(0.0, width - leftInset - rightInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = contentAreaWidth(width, leftInset, rightInset);
        return topInset + snapSizeY(header.prefHeight(w)) + revealedBodyHeight(w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = contentAreaWidth(width, leftInset, rightInset);
        double bodyMin = shownContent == null || !shownContent.isManaged()
                ? 0.0
                : bodyVerticalInsets() + contentMinHeight(contentWidthFor(w));
        return topInset + snapSizeY(header.prefHeight(w)) + snapSizeY(bodyMin * progress.get()) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double bodyWidth = shownContent == null || !shownContent.isManaged()
                ? 0.0
                : bodyHorizontalInsets() + shownContent.prefWidth(-1);
        return leftInset + Math.max(snapSizeX(header.prefWidth(-1)), snapSizeX(bodyWidth)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double bodyWidth = shownContent == null || !shownContent.isManaged()
                ? 0.0
                : bodyHorizontalInsets() + shownContent.minWidth(-1);
        return leftInset + Math.max(snapSizeX(header.minWidth(-1)), snapSizeX(bodyWidth)) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double headerHeight = Math.min(snapSizeY(header.prefHeight(w)), Math.max(0.0, h));
        header.resizeRelocate(x, y, w, headerHeight);

        double contentWidth = contentWidthFor(w);
        double natural = contentNaturalHeight(contentWidth);
        double available = Math.max(0.0, h - headerHeight);
        double revealed = revealedBodyHeight(w);
        double bodyHeight = Math.min(revealed, available);

        double contentHeight = natural;
        if (isSettledExpanded() && available < bodyVerticalInsets() + natural) {
            contentHeight = Math.max(contentMinHeight(contentWidth), available - bodyVerticalInsets());
        }
        contentLayoutHeight = snapSizeY(contentHeight);

        body.resizeRelocate(x, y + headerHeight, w, bodyHeight);
        body.layoutContent();

        boolean overflows = bodyVerticalInsets() + contentLayoutHeight > bodyHeight + 0.5;
        if (!isSettledExpanded() || overflows) {
            bodyClip.setWidth(w);
            bodyClip.setHeight(bodyHeight);
            body.setClip(bodyClip);
        } else {
            body.setClip(null);
        }
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        stopTimeline();
        title.getProperties().remove(TRIGGER_KEY);
        label.setGraphic(null);
        titleSlot.getChildren().clear();
        body.setContentNode(null);
        shownContent = null;
    }

    // ==================== Header region ====================

    /**
     * Lays out the title area and the action box. Actions keep their preferred width
     * and only shrink toward their minimum after the title has reached its minimum;
     * below that the title gets whatever is left, down to zero.
     */
    private final class HeaderRegion extends Region {

        HeaderRegion() {
            getStyleClass().add("header");
        }

        void setParts(Node titlePart, Node actionsPart) {
            getChildren().setAll(titlePart, actionsPart);
        }

        private double actionsWidth(double available) {
            if (!actionsBox.isManaged()) {
                return 0.0;
            }
            double pref = snapSizeX(actionsBox.prefWidth(-1));
            double min = snapSizeX(actionsBox.minWidth(-1));
            double titleMin = snapSizeX(title.minWidth(-1));
            return Math.max(min, Math.min(pref, available - titleMin));
        }

        @Override
        protected double computePrefWidth(double height) {
            double actions = actionsBox.isManaged() ? snapSizeX(actionsBox.prefWidth(-1)) : 0.0;
            return snappedLeftInset() + snapSizeX(title.prefWidth(-1)) + actions + snappedRightInset();
        }

        @Override
        protected double computeMinWidth(double height) {
            double actions = actionsBox.isManaged() ? snapSizeX(actionsBox.minWidth(-1)) : 0.0;
            return snappedLeftInset() + snapSizeX(title.minWidth(-1)) + actions + snappedRightInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            double titleHeight;
            double actionsHeight = 0.0;
            if (width < 0) {
                titleHeight = title.prefHeight(-1);
                if (actionsBox.isManaged()) {
                    actionsHeight = actionsBox.prefHeight(-1);
                }
            } else {
                double available = Math.max(0.0, width - snappedLeftInset() - snappedRightInset());
                double actions = actionsWidth(available);
                titleHeight = title.prefHeight(Math.max(0.0, available - actions));
                if (actionsBox.isManaged()) {
                    actionsHeight = actionsBox.prefHeight(actions);
                }
            }
            return snappedTopInset() + snapSizeY(Math.max(titleHeight, actionsHeight)) + snappedBottomInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected void layoutChildren() {
            double left = snappedLeftInset();
            double top = snappedTopInset();
            double width = Math.max(0.0, getWidth() - left - snappedRightInset());
            double height = Math.max(0.0, getHeight() - top - snappedBottomInset());
            double actions = actionsWidth(width);
            double titleWidth = Math.max(0.0, width - actions);
            title.resizeRelocate(left, top, titleWidth, height);
            if (actionsBox.isManaged()) {
                layoutInArea(actionsBox, left + titleWidth, top, actions, height, 0,
                        HPos.RIGHT, VPos.CENTER);
            }
        }
    }

    // ==================== Title region ====================

    /**
     * The toggle area and keyboard focus stop of the pane; it carries the
     * {@link AccessibleRole#TITLED_PANE} semantics.
     */
    private final class TitleRegion extends RXRipplePane {

        TitleRegion() {
            getStyleClass().add("title");
            setFocusTraversable(true);
            setAccessibleRole(AccessibleRole.TITLED_PANE);
        }

        void setFocusShown(boolean value) {
            pseudoClassStateChanged(FOCUS_SHOWN, value);
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            RXTitledPane control = getSkinnable();
            if (control == null) {
                return super.queryAccessibleAttribute(attribute, parameters);
            }
            switch (attribute) {
                case TEXT:
                    String accessible = control.getAccessibleText();
                    return accessible != null ? accessible : control.getText();
                case EXPANDED:
                    return control.isExpanded();
                default:
                    return super.queryAccessibleAttribute(attribute, parameters);
            }
        }

        @Override
        public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
            RXTitledPane control = getSkinnable();
            if (control == null || control.expandedProperty().isBound()) {
                super.executeAccessibleAction(action, parameters);
                return;
            }
            switch (action) {
                case EXPAND:
                    control.setExpanded(true);
                    break;
                case COLLAPSE:
                    control.setExpanded(false);
                    break;
                default:
                    super.executeAccessibleAction(action, parameters);
            }
        }
    }

    // ==================== Body region ====================

    /**
     * Clipping viewport around the content. The content keeps its natural height
     * while the viewport height follows the transition progress.
     */
    private final class BodyRegion extends Region {

        BodyRegion() {
            getStyleClass().add("body");
        }

        void setContentNode(Node content) {
            if (content == null) {
                getChildren().clear();
            } else {
                getChildren().setAll(content);
            }
        }

        void layoutContent() {
            Node content = shownContent;
            if (content == null || content.getParent() != this || !content.isManaged()) {
                return;
            }
            double width = Math.max(0.0, getWidth() - snappedLeftInset() - snappedRightInset());
            content.resizeRelocate(snappedLeftInset(), snappedTopInset(), width, contentLayoutHeight);
        }

        @Override
        protected void layoutChildren() {
            layoutContent();
        }
    }
}
