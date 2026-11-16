package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventTarget;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin of {@link RXTitledPane}.
 *
 * <p>Structure: {@code .header} is a single {@link RXRipplePane} spanning the whole
 * header row; it is the hover / ripple surface, the keyboard focus stop and the
 * accessible {@link AccessibleRole#TITLED_PANE} node. Its {@code .row} lays out the
 * {@code .indicator-button > .indicator}, the {@code .slot} holding the default
 * {@code .label} or the custom header node, and the {@code .trailing} slot holding the
 * header-trailing node; with an end indicator the order is slot, trailing,
 * indicator. The trailing slot is inert: a press inside it never toggles the pane
 * and never starts a ripple. {@code .body} is a skin-owned clipping viewport around
 * the content.</p>
 *
 * <p>The body is revealed, not squeezed: the content is laid out at its natural
 * height for the current width and the viewport height follows the transition
 * progress, so the content is not resized on every animation frame. Because the
 * progress is normalized, the target height is recomputed on each layout and follows
 * width or content changes during a transition.</p>
 */
public class RXTitledPaneSkin extends RXSkinBase<RXTitledPane> {

    /**
     * Key under which the header focus node stores its pane in
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
    private final HeaderRow row = new HeaderRow();
    private final StackPane titleSlot = new StackPane();
    private final StackPane trailingSlot = new StackPane();
    private final StackPane indicatorButton = new StackPane();
    private final Region indicator = new Region();
    private final Label label = new Label();
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

    private final InvalidationListener focusOwnerListener = obs -> forwardFocus();
    private Scene trackedScene;

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
        indicatorButton.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        indicatorButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        titleSlot.getStyleClass().add("slot");
        titleSlot.setAlignment(Pos.CENTER_LEFT);

        trailingSlot.getStyleClass().add("trailing");
        trailingSlot.setAlignment(Pos.CENTER_RIGHT);

        row.getStyleClass().add("row");
        header.setContent(row);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getProperties().put(TRIGGER_KEY, control);

        body.setClip(bodyClip);

        progress.set(control.isExpanded() ? 1.0 : 0.0);

        getChildren().setAll(header, body);

        disposer.registerBinding(label.textProperty(), control.textProperty());

        onContentChanged(null, control.getContent());
        updateTitle();
        updateTrailing();
        updateRow();
        updateBodyVisibility();

        disposer.registerListener(control.contentProperty(), contentListener);
        disposer.registerListener(control.headerProperty(), this::updateTitle);
        disposer.registerListener(control.graphicProperty(), this::updateTitle);
        disposer.registerListener(control.headerTrailingProperty(), this::updateTrailing);
        disposer.registerListener(control.indicatorPositionProperty(), this::updateRow);
        disposer.registerListener(control.expandedProperty(), this::onExpandedChanged);
        disposer.registerListener(control.animatedProperty(), this::onAnimatedChanged);
        disposer.registerListener(progress, this::onProgressChanged);
        disposer.registerListener(control.sceneProperty(), this::trackSceneFocusOwner);
        trackSceneFocusOwner();
        disposer.registerDisposeTask(() -> {
            if (trackedScene != null) {
                trackedScene.focusOwnerProperty().removeListener(focusOwnerListener);
                trackedScene = null;
            }
        });
        disposer.registerListener(header.focusedProperty(), this::onHeaderFocusChanged);

        // The trailing slot is inert: consuming the press keeps it from reaching the
        // header, which would otherwise start a ripple and arm a toggle.
        disposer.registerEventHandler(trailingSlot, MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
        disposer.registerEventHandler(header, MouseEvent.MOUSE_PRESSED, this::onHeaderPressed);
        disposer.registerEventHandler(header, MouseEvent.MOUSE_RELEASED, this::onHeaderReleased);
        disposer.registerEventHandler(header, KeyEvent.KEY_PRESSED, this::onHeaderKeyPressed);
        disposer.registerEventHandler(header, KeyEvent.KEY_RELEASED, this::onHeaderKeyReleased);
    }

    // ==================== Package API ====================

    /**
     * Moves keyboard focus to the header.
     */
    void focusTrigger() {
        header.requestFocus();
    }

    // ==================== Header ====================

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

    private void updateTrailing() {
        Node trailing = getSkinnable().getHeaderTrailing();
        if (trailing == null) {
            trailingSlot.getChildren().clear();
        } else {
            trailingSlot.getChildren().setAll(trailing);
        }
        updateRow();
    }

    private void updateRow() {
        IndicatorPosition position = indicatorPositionOrDefault();
        List<Node> parts = new ArrayList<>();
        if (position == IndicatorPosition.START) {
            parts.add(indicatorButton);
        }
        parts.add(titleSlot);
        if (!trailingSlot.getChildren().isEmpty()) {
            parts.add(trailingSlot);
        }
        if (position == IndicatorPosition.END) {
            parts.add(indicatorButton);
        }
        row.setParts(parts);
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

    private void onHeaderPressed(MouseEvent event) {
        armed = false;
        armedArea = null;
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        mouseFocus = true;
        if (header.isFocused()) {
            header.setFocusShown(false);
        } else {
            header.requestFocus();
        }
        if (indicatorOnlyTrigger()) {
            armedArea = isWithin(event.getTarget(), indicatorButton) ? indicatorButton : null;
        } else {
            armedArea = header;
        }
        armed = armedArea != null;
    }

    private void onHeaderReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !armed) {
            return;
        }
        Node area = armedArea;
        armed = false;
        armedArea = null;
        if (contains(area, event) && !(trailingSlot.getParent() != null && contains(trailingSlot, event))) {
            toggle();
        }
    }

    private static boolean contains(Node node, MouseEvent event) {
        Point2D local = node.sceneToLocal(event.getSceneX(), event.getSceneY());
        return local != null && node.contains(local);
    }

    private void onHeaderKeyPressed(KeyEvent event) {
        if (event.getTarget() != header) {
            return;
        }
        mouseFocus = false;
        header.setFocusShown(true);
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
            if (!keyDown) {
                keyDown = true;
                toggle();
            }
            event.consume();
        }
    }

    private void onHeaderKeyReleased(KeyEvent event) {
        if (event.getTarget() != header) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
            keyDown = false;
            event.consume();
        }
    }

    private void onHeaderFocusChanged() {
        if (header.isFocused()) {
            header.setFocusShown(!mouseFocus);
        } else {
            header.setFocusShown(false);
            mouseFocus = false;
            keyDown = false;
            armed = false;
        }
    }

    // Follows the scene focus owner rather than the pane's focused property: the latter
    // only turns true while the window itself has focus.
    private void trackSceneFocusOwner() {
        Scene scene = getSkinnable().getScene();
        if (scene == trackedScene) {
            return;
        }
        if (trackedScene != null) {
            trackedScene.focusOwnerProperty().removeListener(focusOwnerListener);
        }
        trackedScene = scene;
        if (scene != null) {
            scene.focusOwnerProperty().addListener(focusOwnerListener);
            forwardFocus();
        }
    }

    private void forwardFocus() {
        Scene scene = trackedScene;
        if (scene != null && scene.getFocusOwner() == getSkinnable()) {
            header.requestFocus();
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
        header.notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
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
        header.getProperties().remove(TRIGGER_KEY);
        label.setGraphic(null);
        titleSlot.getChildren().clear();
        trailingSlot.getChildren().clear();
        body.setContentNode(null);
        shownContent = null;
    }

    // ==================== Header region ====================

    /**
     * The whole header row: hover / ripple surface, keyboard focus stop and the node
     * that carries the {@link AccessibleRole#TITLED_PANE} semantics.
     */
    private final class HeaderRegion extends RXRipplePane {

        HeaderRegion() {
            getStyleClass().add("header");
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

    // ==================== Header row ====================

    /**
     * Lays out indicator, title slot and trailing slot with the row's spacing. The
     * indicator keeps its preferred size; the trailing slot keeps its preferred width
     * and shrinks toward its minimum only after the title slot has reached its
     * minimum; below that the title gets whatever is left, down to zero.
     */
    private final class HeaderRow extends HBox {

        void setParts(List<Node> parts) {
            getChildren().setAll(parts);
        }

        private double gaps() {
            int count = 0;
            for (Node child : getChildren()) {
                if (child.isManaged()) {
                    count++;
                }
            }
            return snapSpaceX(getSpacing()) * Math.max(0, count - 1);
        }

        private double indicatorWidth() {
            return indicatorButton.getParent() == this ? snapSizeX(indicatorButton.prefWidth(-1)) : 0.0;
        }

        private double trailingWidth(double available) {
            if (trailingSlot.getParent() != this) {
                return 0.0;
            }
            double pref = snapSizeX(trailingSlot.prefWidth(-1));
            double min = snapSizeX(trailingSlot.minWidth(-1));
            double titleMin = snapSizeX(titleSlot.minWidth(-1));
            return Math.max(min, Math.min(pref, available - titleMin));
        }

        private double flexibleWidth(double width) {
            return Math.max(0.0, width - snappedLeftInset() - snappedRightInset() - gaps() - indicatorWidth());
        }

        @Override
        protected double computeMinWidth(double height) {
            double trailing = trailingSlot.getParent() == this ? snapSizeX(trailingSlot.minWidth(-1)) : 0.0;
            return snappedLeftInset() + indicatorWidth() + snapSizeX(titleSlot.minWidth(-1)) + trailing
                    + gaps() + snappedRightInset();
        }

        @Override
        protected double computePrefHeight(double width) {
            if (width < 0) {
                return super.computePrefHeight(width);
            }
            double flexible = flexibleWidth(width);
            double trailing = trailingWidth(flexible);
            double height = titleSlot.prefHeight(Math.max(0.0, flexible - trailing));
            if (trailingSlot.getParent() == this) {
                height = Math.max(height, trailingSlot.prefHeight(trailing));
            }
            if (indicatorButton.getParent() == this) {
                height = Math.max(height, indicatorButton.prefHeight(-1));
            }
            return snappedTopInset() + snapSizeY(height) + snappedBottomInset();
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected void layoutChildren() {
            double top = snappedTopInset();
            double height = Math.max(0.0, getHeight() - top - snappedBottomInset());
            double flexible = flexibleWidth(getWidth());
            double trailing = trailingWidth(flexible);
            double title = Math.max(0.0, flexible - trailing);
            double spacing = snapSpaceX(getSpacing());
            double x = snappedLeftInset();
            for (Node child : getChildren()) {
                if (!child.isManaged()) {
                    continue;
                }
                double width;
                boolean fill;
                HPos align;
                if (child == indicatorButton) {
                    width = indicatorWidth();
                    fill = false;
                    align = HPos.CENTER;
                } else if (child == trailingSlot) {
                    width = trailing;
                    fill = true;
                    align = HPos.RIGHT;
                } else {
                    width = title;
                    fill = true;
                    align = HPos.LEFT;
                }
                layoutInArea(child, x, top, width, height, 0, Insets.EMPTY, fill, false, align, VPos.CENTER);
                x += width + spacing;
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
