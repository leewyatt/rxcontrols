package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXColorView;
import io.github.leewyatt.rxcontrols.internal.color.Checkerboard;
import io.github.leewyatt.rxcontrols.internal.color.ColorArea;
import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.internal.color.ColorSlider;
import io.github.leewyatt.rxcontrols.internal.color.ColorSwatch;
import io.github.leewyatt.rxcontrols.internal.color.ColorViewAccess;
import io.github.leewyatt.rxcontrols.internal.color.HsbaCursor;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Window;

import java.util.Objects;

/**
 * Default skin for {@link RXColorView}.
 *
 * <p>The skin keeps an {@link HsbaCursor} next to the view's value: the cursor
 * is where the panel stands, the value is the color it reports. A value that
 * equals the cursor's color leaves the cursor in place, so hue and saturation
 * survive grays and black; any other value moves the cursor.
 *
 * <p>All edits share one session: the first user write records the value it
 * started from, and the session ends at a commit point (pointer released, the
 * key that started a keyboard change released, a part losing focus, the view
 * hidden, disabled or bound). The view's action fires when a session ends on a
 * different value. A session that ends while the view's window is closing
 * fires after the closing call has returned, so a handler may reopen a popup
 * that hosts the view. Disposing the skin drops a pending session without an
 * action, so no application code runs during the dispose.
 */
public class RXColorViewSkin extends RXSkinBase<RXColorView> {

    // ==================== Constants ====================

    private static final double PREVIEW_SIZE = 28.0;
    private static final double CLEAR_SIZE = 24.0;
    private static final double AREA_STEP = 0.01;
    private static final double AREA_PAGE = 0.10;
    private static final double HUE_STEP = 1.0;
    private static final double HUE_PAGE = 15.0;
    private static final int ALPHA_STEP = 1;
    private static final int ALPHA_PAGE = 10;
    private static final double PERCENT = 100.0;

    // ==================== Nodes ====================

    private final VBox content = new VBox();
    private final ColorArea area = new ColorArea();
    private final ColorSlider hueSlider = new ColorSlider("hue-slider");
    private final ColorSlider alphaSlider = new ColorSlider("alpha-slider");
    private final VBox sliders = new VBox();
    private final ColorSwatch preview = new ColorSwatch("preview");
    private final HBox sliderRow = new HBox();
    private final ColorInputRow inputRow = new ColorInputRow(new InputHost());
    private final HBox header = new HBox();
    private final ColorSwatch clearButton = new ColorSwatch("clear-button");
    private final ColorPresetSection presetSection = new ColorPresetSection(new PresetHost());

    // ==================== State ====================

    private final HsbaCursor cursor = new HsbaCursor();
    private boolean activated;
    private boolean disposed;

    /** Whether a user edit has written the value since the last commit point. */
    private boolean pending;
    /** The value the pending session started from. */
    private Color baseline;
    /** The part a primary press is dragging on, or {@code null}. */
    private Node pointerTarget;
    /** The part whose key started the current keyboard change, or {@code null}. */
    private Node keyTarget;
    private KeyCode keyCode;

    private final InvalidationListener skinListener = observable -> activate();
    private final InvalidationListener focusOwnerListener = observable -> forwardFocus();
    private Scene observedScene;

    // ==================== Constructor ====================

    /**
     * Creates the default skin for the given color view. The skin builds its
     * parts and starts taking input once it is installed as the view's skin.
     *
     * @param control the color view
     */
    public RXColorViewSkin(RXColorView control) {
        super(control);
        content.getStyleClass().add("content");

        sliders.getStyleClass().add("sliders");
        sliders.setAlignment(Pos.CENTER_LEFT);
        sliders.getChildren().addAll(hueSlider, alphaSlider);
        HBox.setHgrow(sliders, Priority.ALWAYS);

        preview.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);
        preview.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        preview.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        sliderRow.getStyleClass().add("slider-row");
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        sliderRow.getChildren().addAll(sliders, preview);

        clearButton.setPrefSize(CLEAR_SIZE, CLEAR_SIZE);
        clearButton.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        clearButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        clearButton.setFocusTraversable(true);
        clearButton.setAccessibleRole(AccessibleRole.BUTTON);
        clearButton.setAccessibleText("Clear color");
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().add(clearButton);

        VBox.setVgrow(area, Priority.ALWAYS);
        content.getChildren().addAll(header, area, sliderRow, inputRow, presetSection);

        // Weak: setSkin drops a skin of the installed class without disposing it,
        // and nothing else may keep such a skin reachable from the view.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        control.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> control.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a second
    // skin of the installed class. Taking the view over only once installed keeps a
    // skin that never gets installed from adding nodes or answering input twice.
    private void activate() {
        RXColorView control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        cursor.startAt(control.getValue());
        getChildren().setAll(content);

        disposer.registerListener(control.valueProperty(), live(this::onValueChanged));
        disposer.registerListener(control.alphaEnabledProperty(), live(() -> {
            onAlphaEnabledChanged();
            refresh();
        }));
        disposer.registerListener(control.formatProperty(), live(inputRow::cancelEdits));
        disposer.registerListener(control.clearableProperty(), live(this::updateHeader));
        // The view is not a Tab stop; focus requested on it (a picker opening its
        // popup) goes to the area, where the arrow keys work at once. The scene's
        // focus owner is watched, not focused: a popup of an inactive window gets a
        // focus owner without ever being focused.
        disposer.registerListener(control.sceneProperty(), live(this::observeScene));
        disposer.registerDisposeTask(() -> {
            if (observedScene != null) {
                observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
                observedScene = null;
            }
        });
        observeScene();
        // The fields' own actions (Enter) and other inner actions bubble here; only
        // the view's completed-edit action may reach its handlers.
        disposer.registerEventFilter(control, ActionEvent.ACTION, event -> {
            if (event.getTarget() != control) {
                event.consume();
            }
        });
        disposer.registerListener(control.disabledProperty(), live(() -> {
            if (control.isDisabled()) {
                endInteraction();
            }
        }));
        disposer.registerListener(controlTreeShowingProperty(), live(() -> {
            if (!controlTreeShowingProperty().get()) {
                endInteraction();
                if (!disposed) {
                    inputRow.cancelEdits();
                }
            }
        }));

        installPointer(area, this::editAreaAt);
        installPointer(hueSlider, (x, y) -> editHue(hueSlider.fractionAt(x) * ColorMath.MAX_HUE));
        installPointer(alphaSlider, (x, y) -> editOpacity(alphaSlider.fractionAt(x)));
        installKeys(area, this::onAreaKey);
        installKeys(hueSlider, this::onHueKey);
        installKeys(alphaSlider, this::onAlphaKey);
        area.setAccessibleStep(direction -> discreteEdit(
                () -> editArea(cursor.getSaturation(), cursor.getBrightness() + direction * AREA_STEP)));
        hueSlider.setAccessibleStep(direction -> discreteEdit(
                () -> editHue(cursor.getHue() + direction * HUE_STEP)));
        alphaSlider.setAccessibleStep(direction -> discreteEdit(() -> stepOpacity(direction * ALPHA_STEP)));
        FocusShownTracker.install(area, disposer);
        FocusShownTracker.install(hueSlider, disposer);
        FocusShownTracker.install(alphaSlider, disposer);
        inputRow.install(disposer);
        ColorViewAccess.setEditCanceller(control, () -> {
            if (!disposed) {
                inputRow.cancelEdits();
            }
        });
        disposer.registerDisposeTask(() -> ColorViewAccess.setEditCanceller(control, null));
        presetSection.install(disposer, control.getPresets());
        disposer.registerEventHandler(clearButton, MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                clearButton.requestFocus();
            }
        });
        disposer.registerEventHandler(clearButton, MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                event.consume();
                discreteEdit(() -> write(null));
            }
        });
        disposer.registerEventHandler(clearButton, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                event.consume();
                discreteEdit(() -> write(null));
            }
        });
        FocusShownTracker.install(clearButton, disposer);
        disposer.registerDisposeTask(() -> {
            area.setAccessibleStep(null);
            hueSlider.setAccessibleStep(null);
            alphaSlider.setAccessibleStep(null);
        });

        onAlphaEnabledChanged();
        updateHeader();
        refresh();
    }

    private void observeScene() {
        Scene scene = getSkinnable().getScene();
        if (scene == observedScene) {
            return;
        }
        if (observedScene != null) {
            observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
        }
        observedScene = scene;
        if (scene != null) {
            scene.focusOwnerProperty().addListener(focusOwnerListener);
            forwardFocus();
        }
    }

    private void forwardFocus() {
        if (!disposed && observedScene != null && observedScene.getFocusOwner() == getSkinnable()) {
            area.requestFocus();
        }
    }

    private void updateHeader() {
        boolean clearable = getSkinnable().isClearable();
        header.setVisible(clearable);
        header.setManaged(clearable);
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

    // ==================== Value ====================

    private void onValueChanged() {
        RXColorView control = getSkinnable();
        if (control.valueProperty().isBound()) {
            // A value that just got bound takes the panel out of a gesture.
            endInteraction();
            if (disposed) {
                return;
            }
        }
        Color value = control.getValue();
        if (value != null && !cursor.matches(value)) {
            cursor.absorb(value);
        }
        refresh();
    }

    private void onAlphaEnabledChanged() {
        boolean enabled = getSkinnable().isAlphaEnabled();
        alphaSlider.setVisible(enabled);
        alphaSlider.setManaged(enabled);
    }

    // ==================== Edits ====================

    private boolean editable() {
        RXColorView control = getSkinnable();
        return !disposed && !control.isDisabled() && !control.valueProperty().isBound()
                && !ColorViewAccess.isInputLocked(control);
    }

    private void editAreaAt(double x, double y) {
        editArea(area.saturationAt(x), area.brightnessAt(y));
    }

    private void editArea(double saturation, double brightness) {
        cursor.changeSaturation(saturation);
        cursor.changeBrightness(brightness);
        writeCursor();
    }

    private void editHue(double hue) {
        cursor.changeHue(hue);
        writeCursor();
    }

    private void writeCursor() {
        if (!getSkinnable().isAlphaEnabled()) {
            cursor.changeOpacity(1.0);
        }
        write(cursor.toColor());
    }

    private void editOpacity(double opacity) {
        Color current = getSkinnable().getValue();
        Color base = current != null ? current : cursor.toExactColor();
        Color color = ColorMath.quantize(
                Color.color(base.getRed(), base.getGreen(), base.getBlue(), ColorMath.clean01(opacity)));
        cursor.changeOpacity(color.getOpacity());
        write(color);
    }

    /** Writes a whole color (a preset, a typed hex or RGB value) and moves the cursor to it. */
    private void editTarget(Color target) {
        Color color = target;
        if (!getSkinnable().isAlphaEnabled()) {
            color = Color.color(color.getRed(), color.getGreen(), color.getBlue());
        }
        color = ColorMath.quantize(color);
        cursor.absorb(color);
        write(color);
    }

    // Opacity steps from the shown whole percentage, so a step never skips one.
    private void stepOpacity(int percentDelta) {
        int shown = ColorMath.toPercent(cursor.getOpacity());
        editOpacity((shown + percentDelta) / PERCENT);
    }

    /** Writes a user edit, opening a session if none is pending; {@code null} clears. */
    private void write(Color color) {
        RXColorView control = getSkinnable();
        if (!pending) {
            baseline = control.getValue();
            pending = true;
        }
        if (Objects.equals(color, control.getValue())) {
            // The value stays, but the cursor may have moved (the hue on a gray).
            refresh();
        } else {
            // The value listener refreshes the panel.
            control.setValue(color);
        }
    }

    private void discreteEdit(Runnable edit) {
        if (!editable()) {
            return;
        }
        edit.run();
        commit();
    }

    /** A commit point: ends the session and fires the action if the value changed. */
    private void commit() {
        if (!pending) {
            return;
        }
        Color start = baseline;
        pending = false;
        baseline = null;
        RXColorView control = getSkinnable();
        if (disposed || Objects.equals(start, control.getValue())) {
            return;
        }
        Scene scene = control.getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window == null || !window.isShowing()) {
            // The window is still inside its hide call (a popup closing): firing now
            // would let a handler reopen it before the close has finished.
            Platform.runLater(() -> {
                if (!disposed) {
                    control.fireEvent(new ActionEvent());
                }
            });
            return;
        }
        control.fireEvent(new ActionEvent());
    }

    // ==================== Pointer ====================

    private interface PointerEdit {
        void apply(double x, double y);
    }

    private void installPointer(Node part, PointerEdit edit) {
        disposer.registerEventHandler(part, MouseEvent.MOUSE_PRESSED, event -> {
            if (!RXMouse.isPlainPrimaryPress(event) || !editable()) {
                return;
            }
            event.consume();
            part.requestFocus();
            // A focus listener (a field committing its text) may have replaced this
            // skin or locked the view.
            if (!editable()) {
                return;
            }
            pointerTarget = part;
            ColorViewAccess.setValueChanging(getSkinnable(), true);
            if (disposed || pointerTarget != part) {
                return;
            }
            edit.apply(event.getX(), event.getY());
        });
        disposer.registerEventHandler(part, MouseEvent.MOUSE_DRAGGED, event -> {
            if (pointerTarget == part) {
                event.consume();
                // A picker's lock is no binding on this view, so nothing ends the drag
                // when it engages: each step checks.
                if (!editable()) {
                    endPointer();
                    return;
                }
                edit.apply(event.getX(), event.getY());
            }
        });
        disposer.registerEventHandler(part, MouseEvent.MOUSE_RELEASED, event -> {
            if (pointerTarget == part && event.getButton() == MouseButton.PRIMARY) {
                event.consume();
                endPointer();
            }
        });
    }

    private void endPointer() {
        if (pointerTarget == null) {
            return;
        }
        pointerTarget = null;
        // The final color is already written; clear the flag after it.
        ColorViewAccess.setValueChanging(getSkinnable(), false);
        if (disposed) {
            return;
        }
        commit();
    }

    // ==================== Keyboard ====================

    private interface KeyEdit {
        boolean apply(KeyEvent event);
    }

    private void installKeys(Node part, KeyEdit edit) {
        disposer.registerEventHandler(part, KeyEvent.KEY_PRESSED, event -> {
            if (!editable()) {
                return;
            }
            if (edit.apply(event)) {
                event.consume();
                if (keyCode == null && !disposed) {
                    keyTarget = part;
                    keyCode = event.getCode();
                }
            }
        });
        disposer.registerEventHandler(part, KeyEvent.KEY_RELEASED, event -> {
            if (keyTarget == part && event.getCode() == keyCode) {
                endKeys();
            }
        });
        disposer.registerListener(part.focusedProperty(), live(() -> {
            if (!part.isFocused()) {
                if (keyTarget == part) {
                    endKeys();
                } else if (pointerTarget == null) {
                    // A drag that loses focus (the window deactivated) ends on release.
                    commit();
                }
            }
        }));
    }

    private void endKeys() {
        keyTarget = null;
        keyCode = null;
        commit();
    }

    /** Ends any gesture or key change in progress: the view was hidden, disabled or bound. */
    private void endInteraction() {
        if (pointerTarget != null) {
            endPointer();
        } else if (keyTarget != null) {
            endKeys();
        } else {
            commit();
        }
    }

    private boolean onAreaKey(KeyEvent event) {
        double step = event.isShiftDown() ? AREA_PAGE : AREA_STEP;
        double s = cursor.getSaturation();
        double b = cursor.getBrightness();
        switch (event.getCode()) {
            case LEFT:
                editArea(s - step, b);
                return true;
            case RIGHT:
                editArea(s + step, b);
                return true;
            case UP:
                editArea(s, b + step);
                return true;
            case DOWN:
                editArea(s, b - step);
                return true;
            case PAGE_UP:
                editArea(s, b + AREA_PAGE);
                return true;
            case PAGE_DOWN:
                editArea(s, b - AREA_PAGE);
                return true;
            default:
                return false;
        }
    }

    private boolean onHueKey(KeyEvent event) {
        double step = event.isShiftDown() ? HUE_PAGE : HUE_STEP;
        double hue = cursor.getHue();
        switch (event.getCode()) {
            case LEFT:
            case DOWN:
                editHue(hue - step);
                return true;
            case RIGHT:
            case UP:
                editHue(hue + step);
                return true;
            case PAGE_DOWN:
                editHue(hue - HUE_PAGE);
                return true;
            case PAGE_UP:
                editHue(hue + HUE_PAGE);
                return true;
            case HOME:
                editHue(0.0);
                return true;
            case END:
                editHue(ColorMath.MAX_HUE);
                return true;
            default:
                return false;
        }
    }

    private boolean onAlphaKey(KeyEvent event) {
        int step = event.isShiftDown() ? ALPHA_PAGE : ALPHA_STEP;
        switch (event.getCode()) {
            case LEFT:
            case DOWN:
                stepOpacity(-step);
                return true;
            case RIGHT:
            case UP:
                stepOpacity(step);
                return true;
            case PAGE_DOWN:
                stepOpacity(-ALPHA_PAGE);
                return true;
            case PAGE_UP:
                stepOpacity(ALPHA_PAGE);
                return true;
            case HOME:
                editOpacity(0.0);
                return true;
            case END:
                editOpacity(1.0);
                return true;
            default:
                return false;
        }
    }

    // ==================== Visuals ====================

    private void refresh() {
        Color value = getSkinnable().getValue();
        Color shown = value != null ? value : cursor.toColor();
        Color opaque = Color.color(shown.getRed(), shown.getGreen(), shown.getBlue());

        area.setHue(cursor.getHue());
        area.setPoint(cursor.getSaturation(), cursor.getBrightness(), opaque);

        double hue = cursor.getHue();
        hueSlider.setFraction(hue / ColorMath.MAX_HUE);
        hueSlider.setThumbFill(ColorMath.hsb(hue, 1.0, 1.0, 1.0));
        int degrees = ColorMath.toDegrees(hue);
        hueSlider.setAccessibleState(degrees, ColorMath.MAX_HUE, "Hue " + degrees + "°");

        double opacity = cursor.getOpacity();
        alphaSlider.setFraction(opacity);
        alphaSlider.setThumbFill(shown);
        alphaSlider.setOverlayFills(Checkerboard.pattern(), new LinearGradient(0.0, 0.0, 1.0, 0.0, true,
                CycleMethod.NO_CYCLE, new Stop(0.0, Color.color(opaque.getRed(), opaque.getGreen(),
                opaque.getBlue(), 0.0)), new Stop(1.0, opaque)));
        int percent = ColorMath.toPercent(opacity);
        alphaSlider.setAccessibleState(percent, PERCENT, "Alpha " + percent + "%");

        preview.showColor(value);
        inputRow.refresh();
        presetSection.refresh();
    }

    // ==================== Preset host ====================

    private final class PresetHost implements ColorPresetSection.Host {

        @Override
        public boolean disposed() {
            return disposed;
        }

        @Override
        public Color value() {
            return getSkinnable().getValue();
        }

        @Override
        public boolean alphaEnabled() {
            return getSkinnable().isAlphaEnabled();
        }

        @Override
        public void pick(Color color) {
            discreteEdit(() -> editTarget(color));
        }
    }

    // ==================== Input row host ====================

    private final class InputHost implements ColorInputRow.Host {

        @Override
        public boolean disposed() {
            return disposed;
        }

        @Override
        public boolean editable() {
            return RXColorViewSkin.this.editable();
        }

        @Override
        public Color shownColor() {
            Color value = getSkinnable().getValue();
            return value != null ? value : cursor.toColor();
        }

        @Override
        public HsbaCursor cursor() {
            return cursor;
        }

        @Override
        public RXColorFormat format() {
            RXColorFormat format = getSkinnable().getFormat();
            return format == null ? RXColorFormat.HEX : format;
        }

        @Override
        public void pickFormat(RXColorFormat format) {
            RXColorView control = getSkinnable();
            if (!disposed && !control.formatProperty().isBound()) {
                control.setFormat(format);
            }
        }

        @Override
        public boolean alphaEnabled() {
            return getSkinnable().isAlphaEnabled();
        }

        @Override
        public void editCursor(Runnable change) {
            if (editable()) {
                change.run();
                writeCursor();
            }
        }

        @Override
        public void editColor(Color color) {
            if (editable()) {
                editTarget(color);
            }
        }

        @Override
        public void editOpacity(double opacity) {
            if (editable()) {
                RXColorViewSkin.this.editOpacity(opacity);
            }
        }

        @Override
        public void finishEdit() {
            if (!disposed) {
                commit();
            }
        }
    }

    // ==================== Layout ====================

    // The handle and thumbs reach past their parts by half their size; the content
    // is inset far enough that they never leave the view, whatever the padding:
    // a popup tracks its content's bounds and would resize on every drag otherwise.
    private double overhang() {
        return Math.max(area.overhang(), Math.max(hueSlider.overhang(), alphaSlider.overhang()));
    }

    private double extra(double inset) {
        return Math.max(0.0, overhang() - inset);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXColorView control = getSkinnable();
        double left = extra(control.snappedLeftInset());
        double right = extra(control.snappedRightInset());
        double top = extra(control.snappedTopInset());
        double bottom = extra(control.snappedBottomInset());
        content.resizeRelocate(x + left, y + top, Math.max(0.0, w - left - right), Math.max(0.0, h - top - bottom));
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return Math.max(leftInset, overhang()) + content.prefWidth(-1) + Math.max(rightInset, overhang());
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
                                       double leftInset) {
        // The preset swatches wrap, so their height depends on the width they get.
        double contentWidth = width < 0.0 ? content.prefWidth(-1)
                : Math.max(0.0, width - Math.max(leftInset, overhang()) - Math.max(rightInset, overhang()));
        return Math.max(topInset, overhang()) + content.prefHeight(contentWidth) + Math.max(bottomInset, overhang());
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {
        return Math.max(leftInset, overhang()) + content.minWidth(-1) + Math.max(rightInset, overhang());
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return Math.max(topInset, overhang()) + content.minHeight(-1) + Math.max(bottomInset, overhang());
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        // Only the skin holding the gesture set the flag; one never installed must not clear it.
        boolean ownsGesture = pointerTarget != null;
        disposed = true;
        pending = false;
        pointerTarget = null;
        keyTarget = null;
        keyCode = null;
        // JavaFX keeps the children when the next skin is a SkinBase, which has already
        // added its own: take out only the node this skin added.
        getChildren().remove(content);
        RXColorView control = getSkinnable();
        if (ownsGesture && control.isValueChanging()) {
            // Last: it runs application listeners, and nothing touches the view after.
            ColorViewAccess.setValueChanging(control, false);
        }
    }
}
