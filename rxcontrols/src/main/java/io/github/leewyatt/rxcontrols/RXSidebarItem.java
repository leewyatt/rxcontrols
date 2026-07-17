package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;

/**
 * Common type for the items hosted by {@link RXSidebar}. Every implementor is a
 * JavaFX {@link Node}: navigation items are toggles, action items are buttons.
 * Navigation and action items live on different JavaFX control branches (toggle
 * vs button), so there is no shared concrete base class — hence a sealed
 * interface. The mutually-exclusive selection is owned by {@link RXSidebar}
 * itself (so it survives a skin change); presentation concerns such as the
 * mini-mode tooltip and the icon column are applied by the skin.
 */
public sealed interface RXSidebarItem permits RXSidebarNavItem, RXSidebarActionItem {

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
}
