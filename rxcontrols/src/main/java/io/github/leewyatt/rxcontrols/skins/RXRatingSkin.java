package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.internal.RatingAccess;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default skin for {@link RXRating}.
 *
 * <p>Each icon is an {@code .item} with an {@code .empty} shape and, above it, a
 * {@code .filled} shape clipped to the part of the icon the shown value covers. The
 * shown value is the preview while the pointer is over the rating, the committed value
 * otherwise.</p>
 *
 * <p>The pointer picks the icon it is nearest to, so the gaps between icons respond
 * too. A press shows the value it would commit, releasing inside the rating commits
 * it, and dragging out before releasing commits nothing. After a click that cleared
 * the rating, the preview stays off until the pointer reaches another value or leaves,
 * so the cleared icons do not light up again under the pointer. Touch input commits
 * the same way and never leaves a preview behind.</p>
 *
 * <p>Focus shows only once the keyboard is in use: the skin sets the
 * {@code :focus-shown} pseudo-class on the rating.</p>
 */
public class RXRatingSkin extends RXSkinBase<RXRating> {

    // ==================== Constants ====================

    private static final PseudoClass TARGET = PseudoClass.getPseudoClass("target");
    private static final double WHOLE_STEP = 1.0;
    private static final double HALF_STEP = 0.5;
    /** How far the clip reaches past the icon on its uncut sides, so no edge pixel is lost. */
    private static final double CLIP_MARGIN = 2.0;

    // ==================== Nodes ====================

    private final List<Item> items = new ArrayList<>();

    // ==================== State ====================

    private boolean activated;
    private boolean disposed;
    /** True from a primary press on the rating until its release. */
    private boolean pressed;
    /** The value a click just cleared; no preview is shown while the pointer stays on it. */
    private Double clearedValue;
    private final InvalidationListener skinListener = observable -> activate();

    // ==================== Constructor ====================

    /**
     * Creates the default skin for the given rating. The skin builds the icons and
     * starts taking input once it is installed as the rating's skin.
     *
     * @param control the rating
     */
    public RXRatingSkin(RXRating control) {
        super(control);
        // Weak: setSkin drops a skin of the installed class without disposing it, and
        // nothing else may keep such a skin reachable from the rating.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        control.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> control.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a second
    // skin of the installed class. Taking the rating over only once installed keeps a
    // skin that never gets installed from adding nodes or answering input twice.
    private void activate() {
        RXRating control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;

        syncItems();

        disposer.registerListener(control.valueProperty(), live(this::onValueChanged));
        disposer.registerListener(control.hoverValueProperty(), live(this::onHoverValueChanged));
        disposer.registerListener(control.maxProperty(), live(this::onMaxChanged));
        disposer.registerListener(control.stepProperty(), live(this::clearPreview));
        disposer.registerListener(control.readOnlyProperty(), live(this::cancelPointer));
        disposer.registerListener(control.disabledProperty(), live(this::cancelPointer));

        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::onPointerMoved);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_MOVED, this::onPointerMoved);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, event -> clearPreview());
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_DRAGGED, this::onDragged);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, this::onReleased);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        // After the press handler, so the tracker sees the focus that press requested.
        FocusShownTracker.install(control, disposer);

        control.requestLayout();
    }

    // A listener removed while its property is notifying still gets that notification,
    // so an earlier listener that replaced the skin would reach this one after dispose.
    private Runnable live(Runnable action) {
        return () -> {
            if (!disposed) {
                action.run();
            }
        };
    }

    // ==================== Items ====================

    private void onMaxChanged() {
        cancelPointer();
        // A hover value listener may have replaced this skin.
        if (disposed) {
            return;
        }
        syncItems();
        getSkinnable().requestLayout();
    }

    private void syncItems() {
        int count = Math.max(0, getSkinnable().getMax());
        while (items.size() > count) {
            Item removed = items.remove(items.size() - 1);
            getChildren().remove(removed.node);
        }
        while (items.size() < count) {
            Item added = new Item();
            items.add(added);
            getChildren().add(added.node);
        }
        updateTarget();
        updateFill();
    }

    // Binding the value invalidates it: the bound source takes the icons from a preview.
    private void onValueChanged() {
        if (getSkinnable().valueProperty().isBound()) {
            cancelPointer();
            if (disposed) {
                return;
            }
        }
        updateFill();
    }

    private void onHoverValueChanged() {
        updateTarget();
        updateFill();
    }

    private void updateTarget() {
        Double hover = getSkinnable().getHoverValue();
        int target = hover == null ? -1 : (int) Math.ceil(hover) - 1;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).node.pseudoClassStateChanged(TARGET, i == target);
        }
    }

    private void updateFill() {
        RXRating control = getSkinnable();
        Double hover = control.getHoverValue();
        // Read even under a preview: a value left invalid reports no later change.
        Double value = control.getValue();
        double shown = shownValue(hover != null ? hover : value);
        if (Double.isNaN(shown)) {
            shown = 0.0;
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).fill(RXMath.clamp0To1(shown - i));
        }
    }

    /** The value as drawn: clamped to the icons, NaN for a missing or non-finite one. */
    private double shownValue(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return Double.NaN;
        }
        return RXMath.clamp(value, 0.0, items.size());
    }

    private double stepSize() {
        return getSkinnable().getStep() == RXRating.Step.HALF ? HALF_STEP : WHOLE_STEP;
    }

    // ==================== Pointer ====================

    private boolean interactive() {
        RXRating control = getSkinnable();
        return !control.isReadOnly() && !control.isDisabled()
                && !control.valueProperty().isBound() && !items.isEmpty();
    }

    // The same area that picks the rating, so a click lands wherever a preview shows.
    private boolean inside(MouseEvent event) {
        return getSkinnable().contains(event.getX(), event.getY());
    }

    // A touch never hovers: a lifted finger may never send an exit.
    private void onPointerMoved(MouseEvent event) {
        if (event.isSynthesized()) {
            return;
        }
        if (pressed) {
            // An enter may come just ahead of the release that ends this press; a move
            // is only sent with every button up, so the release never arrived.
            if (event.getEventType() != MouseEvent.MOUSE_MOVED) {
                return;
            }
            pressed = false;
        }
        if (interactive()) {
            previewAt(event.getX());
        } else {
            clearPreview();
        }
    }

    private void onPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !interactive()) {
            return;
        }
        RXRating control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
            // A focus listener may have replaced this skin.
            if (disposed) {
                return;
            }
        }
        pressed = true;
        previewAt(event.getX());
    }

    private void onDragged(MouseEvent event) {
        if (!pressed) {
            return;
        }
        if (inside(event) && interactive()) {
            previewAt(event.getX());
        } else {
            clearPreview();
        }
    }

    private void onReleased(MouseEvent event) {
        if (!pressed || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        pressed = false;
        boolean inside = inside(event);
        if (inside && interactive()) {
            commitFromPointer(valueAt(event.getX()));
            if (disposed) {
                return;
            }
        }
        if (inside && !event.isSynthesized() && interactive()) {
            previewAt(event.getX());
        } else {
            clearPreview();
        }
    }

    private void commitFromPointer(double picked) {
        RXRating control = getSkinnable();
        Double current = control.getValue();
        if (current != null && current == picked) {
            if (control.isClearable()) {
                clearedValue = picked;
                commit(null);
            }
            return;
        }
        commit(picked);
    }

    private void previewAt(double x) {
        double picked = valueAt(x);
        if (clearedValue != null) {
            if (clearedValue == picked) {
                setHoverValue(null);
                return;
            }
            clearedValue = null;
        }
        setHoverValue(picked);
    }

    private void clearPreview() {
        clearedValue = null;
        setHoverValue(null);
    }

    private void cancelPointer() {
        pressed = false;
        clearPreview();
    }

    private void setHoverValue(Double value) {
        RXRating control = getSkinnable();
        if (!Objects.equals(control.getHoverValue(), value)) {
            RatingAccess.setHoverValue(control, value);
        }
    }

    // The gap between two icons is split at its middle, which goes to the earlier icon.
    private double valueAt(double x) {
        int index = items.size() - 1;
        for (int i = 0; i < items.size() - 1; i++) {
            Node current = items.get(i).node;
            Node next = items.get(i + 1).node;
            double boundary = (current.getLayoutX() + current.getLayoutBounds().getWidth() + next.getLayoutX()) / 2.0;
            if (x <= boundary) {
                index = i;
                break;
            }
        }
        if (stepSize() == WHOLE_STEP) {
            return index + WHOLE_STEP;
        }
        Item item = items.get(index);
        double iconWidth = item.iconWidth();
        double iconLeft = item.node.getLayoutX() + (item.node.getLayoutBounds().getWidth() - iconWidth) / 2.0;
        return x <= iconLeft + iconWidth * HALF_STEP ? index + HALF_STEP : index + WHOLE_STEP;
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        // A key commit would overtake the click the pressed button is about to make.
        if (pressed || !interactive()) {
            return;
        }
        // Chords belong to accelerators and to the platform.
        if (event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        RXRating control = getSkinnable();
        boolean mirrored = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        KeyCode code = event.getCode();
        if (code == KeyCode.UP || code == KeyCode.KP_UP) {
            keyCommit(this::increase);
        } else if (code == KeyCode.DOWN || code == KeyCode.KP_DOWN) {
            keyCommit(this::decrease);
        } else if (code == KeyCode.RIGHT || code == KeyCode.KP_RIGHT) {
            keyCommit(mirrored ? this::decrease : this::increase);
        } else if (code == KeyCode.LEFT || code == KeyCode.KP_LEFT) {
            keyCommit(mirrored ? this::increase : this::decrease);
        } else if (code == KeyCode.HOME) {
            keyCommit(() -> commit(stepSize()));
        } else if (code == KeyCode.END) {
            keyCommit(() -> commit((double) items.size()));
        } else if ((code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) && control.isClearable()) {
            keyCommit(() -> commit(null));
        } else {
            return;
        }
        event.consume();
    }

    // The keyboard takes the icons back from a hover preview until the pointer moves again.
    private void keyCommit(Runnable change) {
        clearPreview();
        // A hover value listener may have replaced this skin.
        if (disposed) {
            return;
        }
        change.run();
    }

    /** Raises the value to the next step above it; a missing value becomes the smallest step. */
    private void increase() {
        double step = stepSize();
        double current = shownValue(getSkinnable().getValue());
        if (Double.isNaN(current)) {
            commit(step);
        } else if (current < items.size()) {
            commit(Math.min(items.size(), (Math.floor(current / step) + 1.0) * step));
        }
    }

    /** Lowers the value to the next step below it; below the smallest step it clears, if allowed. */
    private void decrease() {
        double step = stepSize();
        double current = shownValue(getSkinnable().getValue());
        if (Double.isNaN(current)) {
            return;
        }
        double target = (Math.ceil(current / step) - 1.0) * step;
        if (target >= step) {
            commit(target);
        } else if (getSkinnable().isClearable()) {
            commit(null);
        }
    }

    /** Sets a value the user chose and reports it, unless it is the value already set. */
    private void commit(Double value) {
        RXRating control = getSkinnable();
        if (Objects.equals(control.getValue(), value)) {
            return;
        }
        control.setValue(value);
        // A value listener may have replaced this skin or put the old value back.
        if (disposed || !Objects.equals(control.getValue(), value)) {
            return;
        }
        control.fireEvent(new ActionEvent());
    }

    // ==================== Layout ====================

    private double gap() {
        return snapSpaceX(RXMath.sanitizeFiniteNonNegative(getSkinnable().getGap()));
    }

    private double itemWidth() {
        double width = 0.0;
        for (Item item : items) {
            width = Math.max(width, snapSizeX(item.node.prefWidth(-1)));
        }
        return width;
    }

    private double itemHeight() {
        double height = 0.0;
        for (Item item : items) {
            height = Math.max(height, snapSizeY(item.node.prefHeight(-1)));
        }
        return height;
    }

    private double contentWidth() {
        int count = items.size();
        return count == 0 ? 0.0 : count * itemWidth() + (count - 1) * gap();
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double itemWidth = itemWidth();
        double itemHeight = itemHeight();
        double pitch = itemWidth + gap();
        double top = snapPositionY(y + (h - itemHeight) / 2.0);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).node.resizeRelocate(snapPositionX(x + i * pitch), top, itemWidth, itemHeight);
        }
        // The clips follow the icon size, which CSS may have changed.
        updateFill();
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + contentWidth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + itemHeight() + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + contentWidth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + itemHeight() + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    /** {@inheritDoc} */
    @Override
    public double computeBaselineOffset(double topInset, double rightInset,
                                        double bottomInset, double leftInset) {
        return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        disposed = true;
        if (!activated) {
            return;
        }
        pressed = false;
        clearedValue = null;
        // SkinBase leaves its nodes in the control; the next skin must not find them.
        for (Item item : items) {
            getChildren().remove(item.node);
        }
        setHoverValue(null);
    }

    // ==================== Accessibility ====================

    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case VALUE:
                double shown = shownValue(getSkinnable().getValue());
                return Double.isNaN(shown) ? 0.0 : shown;
            case MIN_VALUE:
                return 0.0;
            case MAX_VALUE:
                return (double) Math.max(0, getSkinnable().getMax());
            case ORIENTATION:
                return Orientation.HORIZONTAL;
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    @Override
    protected void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        switch (action) {
            case INCREMENT:
                if (interactive()) {
                    keyCommit(this::increase);
                }
                break;
            case DECREMENT:
                if (interactive()) {
                    keyCommit(this::decrease);
                }
                break;
            case SET_VALUE:
                if (interactive() && parameters != null && parameters.length > 0
                        && parameters[0] instanceof Number number) {
                    keyCommit(() -> commitNearest(number.doubleValue()));
                }
                break;
            default:
                super.executeAccessibleAction(action, parameters);
        }
    }

    /** Commits the step nearest to the value, the higher one from the middle. */
    private void commitNearest(double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        double step = stepSize();
        double target = Math.round(RXMath.clamp(value, 0.0, items.size()) / step) * step;
        if (target >= step) {
            commit(target);
        } else if (getSkinnable().isClearable()) {
            commit(null);
        } else {
            commit(step);
        }
    }

    // ==================== Item ====================

    /** One icon: the empty shape with the clipped filled shape above it. */
    private static final class Item {

        private final Region empty = new Region();
        private final Region filled = new Region();
        private final Rectangle clip = new Rectangle();
        private final StackPane node = new StackPane(empty, filled);

        private Item() {
            node.getStyleClass().add("item");
            // The rating itself takes every pointer event, in the gaps as on the icons.
            node.setMouseTransparent(true);
            empty.getStyleClass().add("empty");
            empty.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            filled.getStyleClass().add("filled");
            filled.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            filled.setClip(clip);
        }

        private double iconWidth() {
            return node.snapSizeX(filled.prefWidth(-1));
        }

        /** Shows the given part of the filled shape, measured from the icon's start. */
        private void fill(double fraction) {
            filled.setVisible(fraction > 0.0);
            double width = iconWidth();
            double height = node.snapSizeY(filled.prefHeight(-1));
            clip.setX(-CLIP_MARGIN);
            clip.setY(-CLIP_MARGIN);
            clip.setHeight(height + 2.0 * CLIP_MARGIN);
            clip.setWidth(fraction >= 1.0 ? width + 2.0 * CLIP_MARGIN : CLIP_MARGIN + width * fraction);
        }
    }
}
