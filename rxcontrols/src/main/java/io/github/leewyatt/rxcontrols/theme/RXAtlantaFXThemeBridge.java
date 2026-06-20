package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Makes RxControls follow an <a href="https://github.com/mkpaz/atlantafx">AtlantaFX</a>
 * theme. RxControls ships its own per-control user-agent stylesheet whose
 * {@code -rx-*} color role tokens default to a built-in palette; this helper layers
 * an <em>author-origin</em> bridge that re-points those tokens at AtlantaFX
 * {@code -color-*} functional tokens, so RxControls picks up the host's AtlantaFX
 * colors without replacing its own structural styling.
 *
 * <h2>Prerequisite — install the AtlantaFX theme first</h2>
 * The bridge only references {@code -color-*}; those resolve from the AtlantaFX
 * theme installed as the <em>Application</em> user-agent stylesheet. The app must
 * install it <strong>before</strong> creating or showing any Scene that contains
 * RxControls, otherwise the first CSS pass cannot resolve {@code -color-*} and logs
 * conversion warnings (and may briefly mis-render until CSS is re-applied):
 * <pre>{@code
 * Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
 * Scene scene = new Scene(root);
 * RXAtlantaFXThemeBridge.install(scene);
 * stage.setScene(scene);
 * stage.show();
 * }</pre>
 *
 * <h2>Scene vs Parent</h2>
 * Prefer {@link #install(Scene)}. A scene-level bridge is the most predictable and
 * also reaches popups (such as {@code RXCascader}'s): a popup opened afterwards
 * snapshots its owner scene's stylesheets, so it follows the bridge too. Install it
 * before opening popups; popups already open when the bridge is added do not pick
 * it up until reopened.
 *
 * <p>{@link #install(Parent)} scopes the bridge to one subtree (useful for mixing
 * themes within a scene). A popup follows a parent-level bridge only when that
 * parent is on the ancestor chain of the control that owns the popup; a bridge on
 * an unrelated parent does not reach the popup.
 *
 * <h2>Scope</h2>
 * The bridge re-points the {@code -rx-*} color role tokens, and additionally
 * supplies the handful of Modena base colors ({@code -fx-control-inner-background},
 * {@code -fx-accent}, {@code -fx-text-base-color}, …) that AtlantaFX does not
 * define — so RxControls rules still referencing raw {@code -fx-*} directly (the
 * not-yet role-tokenized parts) follow the theme too instead of rendering
 * transparent/black. This compat layer is a bridge until those colors migrate to
 * role tokens. Non-color values (radius, spacing, sizes) are not themed by the
 * bridge.
 *
 * <p>This class adds no compile-time dependency on AtlantaFX; installing the
 * AtlantaFX Application UA is the caller's responsibility.
 */
public final class RXAtlantaFXThemeBridge {

    private RXAtlantaFXThemeBridge() {
    }

    /**
     * Returns the external-form URL of the AtlantaFX bridge stylesheet (author
     * origin), for callers that manage stylesheet lists themselves.
     *
     * @return the bridge stylesheet URL
     */
    public static String getStylesheet() {
        return RXResources.ATLANTAFX_BRIDGE_STYLESHEET;
    }

    /**
     * Installs the AtlantaFX bridge on the scene (adds it to the scene's
     * stylesheets if not already present). Install before showing the scene and
     * before opening popups (see class doc).
     *
     * @param scene the scene to bridge; must not be null
     */
    public static void install(Scene scene) {
        RXStyles.addSheets(scene, getStylesheet());
    }

    /**
     * Installs the AtlantaFX bridge on the parent's subtree (adds it to the
     * parent's stylesheets if not already present).
     *
     * @param parent the parent to bridge; must not be null
     */
    public static void install(Parent parent) {
        RXStyles.addSheets(parent, getStylesheet());
    }

    /**
     * Uninstalls the AtlantaFX bridge from the scene if present.
     *
     * @param scene the scene to unbridge; must not be null
     */
    public static void uninstall(Scene scene) {
        RXStyles.removeSheets(scene, getStylesheet());
    }

    /**
     * Uninstalls the AtlantaFX bridge from the parent if present.
     *
     * @param parent the parent to unbridge; must not be null
     */
    public static void uninstall(Parent parent) {
        RXStyles.removeSheets(parent, getStylesheet());
    }
}
