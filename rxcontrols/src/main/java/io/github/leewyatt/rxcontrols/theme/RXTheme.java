package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Switches RxControls between its built-in light and dark looks. RxControls ships
 * a per-control user-agent stylesheet whose {@code -rx-*} color role tokens default
 * to a light palette; {@link Variant#DARK} layers an <em>author-origin</em> overlay
 * that re-defines those tokens with a self-contained dark palette (no host theme
 * required), so the whole library turns dark without replacing its structure.
 *
 * <pre>{@code
 * RXTheme.install(scene, RXTheme.Variant.DARK); // go dark
 * RXTheme.install(scene, RXTheme.Variant.LIGHT); // back to the built-in light baseline
 * RXTheme.uninstall(scene);                      // also back to the baseline
 * }</pre>
 *
 * <p><b>Scope = RxControls components only.</b> This themes the RxControls controls
 * (their role tokens, a Modena {@code -fx-*} compat layer on the control roots so
 * controls still using raw {@code -fx-*} like the cascader turn dark too, and
 * per-control re-points for the few whose baseline colors are hardcoded literals).
 * It does <em>not</em> theme the window background, standard JavaFX controls, or app
 * chrome — that is the application's responsibility. For a fully dark UI, give your
 * scene a dark background (e.g. flip Modena's base palette on {@code .root}); the
 * samples do this in {@code rx-showcase-dark.css}. In particular, transparent-
 * background controls (text-view, timeline) only read well on a dark surface the app
 * provides.
 *
 * <p>Apply at scene level (recommended) or to a single {@link Parent} subtree.
 * Popups (such as {@code RXCascader}'s) follow a scene-level overlay installed
 * before they open; switch the theme before opening popups, or reopen them
 * afterwards.
 *
 * <p>For matching an external <a href="https://github.com/mkpaz/atlantafx">AtlantaFX</a>
 * theme instead of the built-in palette, use {@link RXAtlantaFXThemeBridge}.
 */
public final class RXTheme {

    private RXTheme() {
    }

    /**
     * Built-in look. {@code LIGHT} is the default baseline (no overlay);
     * {@code DARK} adds the dark overlay.
     */
    public enum Variant {
        /** The built-in light baseline (the default; selecting it removes the dark overlay). */
        LIGHT,
        /** The built-in dark overlay. */
        DARK
    }

    /**
     * Sets the variant on the scene: installs the dark overlay for {@link Variant#DARK},
     * or removes it (reverting to the light baseline) for {@link Variant#LIGHT}.
     *
     * @param scene   the scene to theme; must not be null
     * @param variant the variant to apply; must not be null
     */
    public static void install(Scene scene, Variant variant) {
        if (Objects.requireNonNull(variant, "variant") == Variant.DARK) {
            RXStyles.addSheets(scene, RXResources.DARK_OVERLAY_STYLESHEET);
        } else {
            RXStyles.removeSheets(scene, RXResources.DARK_OVERLAY_STYLESHEET);
        }
    }

    /**
     * Sets the variant on the parent's subtree: installs the dark overlay for
     * {@link Variant#DARK}, or removes it for {@link Variant#LIGHT}.
     *
     * @param parent  the parent to theme; must not be null
     * @param variant the variant to apply; must not be null
     */
    public static void install(Parent parent, Variant variant) {
        if (Objects.requireNonNull(variant, "variant") == Variant.DARK) {
            RXStyles.addSheets(parent, RXResources.DARK_OVERLAY_STYLESHEET);
        } else {
            RXStyles.removeSheets(parent, RXResources.DARK_OVERLAY_STYLESHEET);
        }
    }

    /**
     * Removes any RxControls theme overlay from the scene, reverting to the
     * built-in light baseline.
     *
     * @param scene the scene to revert; must not be null
     */
    public static void uninstall(Scene scene) {
        RXStyles.removeSheets(scene, RXResources.DARK_OVERLAY_STYLESHEET);
    }

    /**
     * Removes any RxControls theme overlay from the parent, reverting to the
     * built-in light baseline.
     *
     * @param parent the parent to revert; must not be null
     */
    public static void uninstall(Parent parent) {
        RXStyles.removeSheets(parent, RXResources.DARK_OVERLAY_STYLESHEET);
    }
}
