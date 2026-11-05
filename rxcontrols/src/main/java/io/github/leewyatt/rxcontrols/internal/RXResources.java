package io.github.leewyatt.rxcontrols.internal;

public final class RXResources {

    public static final String USER_AGENT_STYLESHEET =
            RXResources.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls.css").toExternalForm();

    /**
     * Author-origin stylesheet with the per-control overrides of the AtlantaFX bridge.
     * Added to a scene (not as a UA sheet) by {@code RXAtlantaFXThemeBridge}.
     */
    public static final String ATLANTAFX_BRIDGE_STYLESHEET =
            RXResources.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls-atlantafx.css").toExternalForm();

    private RXResources() {
    }

}
