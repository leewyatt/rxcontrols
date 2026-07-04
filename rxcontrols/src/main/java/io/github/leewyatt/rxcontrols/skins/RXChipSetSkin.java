package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

import java.util.List;

/**
 * Default skin for {@link RXChipSet}: composes an {@link RXFlowPane} for the wrap
 * layout, mirrors the set's chips into it, and installs arrow-key roving focus
 * between chips.
 *
 * <p>Selection is coordinated by the control itself (from each chip's
 * {@code selected} state); this skin owns only the layout and keyboard navigation.
 * Left / Right move focus to the previous / next chip (mirrored under
 * right-to-left orientation), Home / End jump to the first / last chip.</p>
 */
public class RXChipSetSkin extends RXSkinBase<RXChipSet> {

    private final RXFlowPane flowPane = new RXFlowPane();

    /**
     * Creates a skin for the given chip set.
     *
     * @param control the chip set this skin is attached to
     */
    public RXChipSetSkin(RXChipSet control) {
        super(control);

        disposer.registerBinding(flowPane.hgapProperty(), control.hgapProperty());
        disposer.registerBinding(flowPane.vgapProperty(), control.vgapProperty());
        disposer.registerBinding(flowPane.alignmentProperty(), control.alignmentProperty());

        syncChips();
        disposer.registerListener(control.getChips(), this::syncChips);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);

        getChildren().setAll(flowPane);
    }

    private void syncChips() {
        flowPane.getChildren().setAll(getSkinnable().getChips());
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        flowPane.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + flowPane.minWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + flowPane.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        // Horizontal content bias: the wrapped height depends on the available width.
        return topInset + flowPane.minHeight(innerWidth(width, leftInset, rightInset)) + bottomInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + flowPane.prefHeight(innerWidth(width, leftInset, rightInset)) + bottomInset;
    }

    private static double innerWidth(double width, double leftInset, double rightInset) {
        // A negative width means "unconstrained"; keep it as the -1 sentinel rather
        // than turning it into a spurious negative wrap width.
        return width < 0 ? -1 : width - leftInset - rightInset;
    }

    // ==================== Keyboard roving ====================

    private void onKeyPressed(KeyEvent event) {
        RXChipSet control = getSkinnable();
        List<RXChip> chips = control.getChips();
        if (chips.isEmpty()) {
            return;
        }
        Scene scene = control.getScene();
        if (scene == null) {
            return;
        }
        Node focusOwner = scene.getFocusOwner();
        int current = chips.indexOf(focusOwner);
        if (current < 0) {
            // Focus is not on a chip of this set; leave traversal to the platform.
            return;
        }
        boolean rtl = control.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        int target;
        switch (event.getCode()) {
            case LEFT -> target = nextFocusable(chips, current, rtl ? 1 : -1);
            case RIGHT -> target = nextFocusable(chips, current, rtl ? -1 : 1);
            case HOME -> target = nextFocusable(chips, -1, 1);
            case END -> target = nextFocusable(chips, chips.size(), -1);
            default -> {
                return;
            }
        }
        // Only consume when focus actually moves, so at a boundary (or when the
        // whole direction is disabled) the platform can still traverse out of the set.
        if (target >= 0 && target != current) {
            chips.get(target).requestFocus();
            event.consume();
        }
    }

    /**
     * First chip strictly past {@code from} in the {@code step} direction that can
     * take focus (enabled and visible); {@code -1} if none — a disabled node refuses
     * {@code requestFocus()}, so disabled/invisible chips must be stepped over.
     */
    private static int nextFocusable(List<RXChip> chips, int from, int step) {
        for (int i = from + step; i >= 0 && i < chips.size(); i += step) {
            RXChip chip = chips.get(i);
            if (!chip.isDisabled() && chip.isVisible()) {
                return i;
            }
        }
        return -1;
    }
}
