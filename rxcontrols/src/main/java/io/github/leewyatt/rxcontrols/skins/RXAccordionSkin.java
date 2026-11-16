package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.beans.binding.Bindings;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin of {@link RXAccordion}: stacks the panes top to bottom at their
 * preferred heights, separated by the accordion's spacing, and moves focus between
 * pane titles with Up / Down / Home / End.
 *
 * <p>Navigation only reacts to key events whose target is the title of one of this
 * accordion's own panes (as rendered by {@link RXTitledPaneSkin}), so keys typed in a
 * pane body or in a nested accordion are left alone.</p>
 */
public class RXAccordionSkin extends RXSkinBase<RXAccordion> {

    /**
     * Creates the skin for the given accordion.
     *
     * @param control the accordion
     */
    public RXAccordionSkin(RXAccordion control) {
        super(control);
        Bindings.bindContent(getChildren(), control.getPanes());
        disposer.registerDisposeTask(() -> Bindings.unbindContent(getChildren(), control.getPanes()));
        disposer.registerListener(control.getPanes(), control::requestLayout);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code != KeyCode.UP && code != KeyCode.DOWN && code != KeyCode.HOME && code != KeyCode.END) {
            return;
        }
        if (event.isShortcutDown() || event.isAltDown() || event.isShiftDown()) {
            return;
        }
        RXTitledPane source = triggerPane(event.getTarget());
        if (source == null) {
            return;
        }
        List<RXTitledPane> candidates = navigablePanes(source);
        int index = candidates.indexOf(source);
        RXTitledPane next = null;
        if (code == KeyCode.HOME) {
            next = candidates.isEmpty() ? null : candidates.get(0);
        } else if (code == KeyCode.END) {
            next = candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
        } else if (code == KeyCode.UP && index > 0) {
            next = candidates.get(index - 1);
        } else if (code == KeyCode.DOWN && index >= 0 && index < candidates.size() - 1) {
            next = candidates.get(index + 1);
        }
        if (next != null && next != source) {
            focusTitle(next);
        }
        // Consumed even at an edge so an enclosing ScrollPane does not scroll instead.
        event.consume();
    }

    private RXTitledPane triggerPane(EventTarget target) {
        if (!(target instanceof Node)) {
            return null;
        }
        Node node = (Node) target;
        if (!node.hasProperties()) {
            return null;
        }
        Object pane = node.getProperties().get(RXTitledPaneSkin.TRIGGER_KEY);
        if (pane instanceof RXTitledPane && getSkinnable().getPanes().contains(pane)) {
            return (RXTitledPane) pane;
        }
        return null;
    }

    private List<RXTitledPane> navigablePanes(RXTitledPane source) {
        List<RXTitledPane> result = new ArrayList<>();
        for (RXTitledPane pane : getSkinnable().getPanes()) {
            boolean usable = pane.isVisible() && !pane.isDisabled()
                    && pane.getSkin() instanceof RXTitledPaneSkin;
            if (usable || pane == source) {
                result.add(pane);
            }
        }
        return result;
    }

    private static void focusTitle(RXTitledPane pane) {
        Skin<?> skin = pane.getSkin();
        if (skin instanceof RXTitledPaneSkin) {
            ((RXTitledPaneSkin) skin).focusTrigger();
        }
    }

    // ==================== Layout ====================

    private double spacingOrZero() {
        double spacing = getSkinnable().getSpacing();
        return Double.isFinite(spacing) && spacing > 0 ? snapSpaceY(spacing) : 0.0;
    }

    private List<Node> managedPanes() {
        List<Node> result = new ArrayList<>();
        for (Node child : getChildren()) {
            if (child.isManaged()) {
                result.add(child);
            }
        }
        return result;
    }

    private static double paneHeight(Node pane, double width) {
        double min = pane.minHeight(width);
        double pref = pane.prefHeight(width);
        double max = pane.maxHeight(width);
        return Math.max(min, Math.min(pref, max));
    }

    private double contentWidth(double width, double leftInset, double rightInset) {
        return width < 0 ? -1 : Math.max(0.0, width - leftInset - rightInset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = contentWidth(width, leftInset, rightInset);
        List<Node> managed = managedPanes();
        double total = 0.0;
        for (Node pane : managed) {
            total += snapSizeY(paneHeight(pane, w));
        }
        total += spacingOrZero() * Math.max(0, managed.size() - 1);
        return topInset + total + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = contentWidth(width, leftInset, rightInset);
        List<Node> managed = managedPanes();
        double total = 0.0;
        for (Node pane : managed) {
            total += snapSizeY(pane.minHeight(w));
        }
        total += spacingOrZero() * Math.max(0, managed.size() - 1);
        return topInset + total + bottomInset;
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
        double max = 0.0;
        for (Node pane : managedPanes()) {
            max = Math.max(max, snapSizeX(pane.prefWidth(-1)));
        }
        return leftInset + max + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double max = 0.0;
        for (Node pane : managedPanes()) {
            max = Math.max(max, snapSizeX(pane.minWidth(-1)));
        }
        return leftInset + max + rightInset;
    }

    /**
     * {@inheritDoc}
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
    protected void layoutChildren(double x, double y, double w, double h) {
        double spacing = spacingOrZero();
        double cursor = y;
        for (Node pane : managedPanes()) {
            double height = snapSizeY(paneHeight(pane, w));
            pane.resizeRelocate(x, cursor, w, height);
            cursor += height + spacing;
        }
    }
}
