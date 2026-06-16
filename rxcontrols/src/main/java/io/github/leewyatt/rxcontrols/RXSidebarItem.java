package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.SidebarMode;
import javafx.scene.Node;

/**
 * Common type for the items hosted by {@link RXSidebar}. Every implementor is a
 * JavaFX {@link Node}: navigation items are toggles, action items are buttons.
 * Navigation and action items live on different JavaFX control branches (toggle
 * vs button), so there is no shared concrete base class — hence a sealed
 * interface. Cross-cutting concerns (accessible text, mini-mode tooltip, the
 * shared selection group, and mode delivery) are applied uniformly by
 * {@code RXSidebarSkin}.
 */
public sealed interface RXSidebarItem
        permits RXSidebarNavItem, RXSidebarActionItem {

    /**
     * Style class shared by every sidebar item, scoped under {@code .rx-sidebar}
     * in CSS. Both permitted item types carry it so the sidebar's item styling
     * (alignment, content display, text overrun) applies uniformly across types.
     */
    String STYLE_CLASS = "item";

    /**
     * Returns this item as a scene-graph node. The {@code (Node) this} cast is a
     * hand-maintained invariant: a sealed interface does not force its permitted
     * types to extend {@link Node}, so every permitted implementor must be a
     * {@link Node} — adding a non-{@code Node} permit would break this.
     *
     * @return this item as a {@link Node}
     */
    default Node asNode() {
        return (Node) this;
    }

    /**
     * Called by the skin when the sidebar's committed mode changes. The default
     * does nothing; custom items (V2) override it to swap mini/expanded content.
     *
     * @param mode the new committed sidebar mode
     */
    default void onSidebarModeChanged(SidebarMode mode) {
    }
}
