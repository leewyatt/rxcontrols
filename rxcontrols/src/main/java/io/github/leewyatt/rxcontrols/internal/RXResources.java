package io.github.leewyatt.rxcontrols.internal;

public final class RXResources {

    public static final String USER_AGENT_STYLESHEET =
            RXResources.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls.css").toExternalForm();

    /**
     * Author-origin stylesheet that re-points the {@code -rx-*} color role tokens
     * at AtlantaFX {@code -color-*} functional tokens. Applied (not as a UA sheet)
     * to a Scene or Parent via {@code AtlantaFXThemeBridge}.
     */
    public static final String ATLANTAFX_BRIDGE_STYLESHEET =
            RXResources.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls-atlantafx.css").toExternalForm();

    /**
     * Author-origin overlay that re-defines the {@code -rx-*} color role tokens
     * with a self-contained dark palette. Applied (not as a UA sheet) to a Scene
     * or Parent via {@code RXTheme}.
     */
    public static final String DARK_OVERLAY_STYLESHEET =
            RXResources.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls-dark.css").toExternalForm();

    private RXResources() {
    }

}
