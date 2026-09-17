package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Switches RxControls between its built-in light and dark looks. RxControls ships
 * a per-control user-agent stylesheet whose {@code -rx-*} color role tokens default
 * to a light palette; {@link Variant#DARK} puts the {@code rx-theme-dark} scope style
 * class on the themed root — the dark token values live in the same user-agent
 * stylesheet under that scope — and adds a small author-origin overlay for the
 * per-control re-points that must sit above the platform theme.
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
 * <p>The {@code -rx-*} tokens resolve on RxControls controls and their descendants
 * (that is where the library's user-agent stylesheet applies), exactly as they do on
 * the light baseline. Application nodes outside any RxControls control should use
 * their own colors rather than {@code -rx-*}.
 *
 * <p>Apply at scene level (recommended) or to a single {@link Parent} subtree.
 * Popups (such as {@code RXCascader}'s) follow a scene-level theme through their
 * owner's style parent chain. A scene-level install also mirrors the scope class onto
 * the root of every {@link javafx.scene.SubScene} present at that moment; for a
 * sub-scene created later, call {@link #install(Parent, Variant)} on its root.
 *
 * <p>A subtree may carry a different theme than its scene: the nearest scope wins for
 * the color tokens, and a theme installed on a node wins over a scene theme mirrored
 * onto that node, whichever was installed first. One level of mixing is supported.
 * Per-control rules follow the nearer install only where both themes carry a rule for
 * the control; a rule only the outer theme defines still reaches the inner subtree.
 * Uninstalling undoes only what that install marked, so a theme installed on a subtree
 * of its own survives the scene's uninstall, and a scope class the application added
 * itself is left in place.
 *
 * <p>For matching an external <a href="https://github.com/mkpaz/atlantafx">AtlantaFX</a>
 * theme instead of the built-in palette, use {@link AtlantaFXThemeBridge}.
 */
public final class RXTheme {

    /** Scope style class carried by the themed root while the dark variant is installed. */
    private static final String DARK_SCOPE_CLASS = "rx-theme-dark";

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
     * Returns the style class the dark variant puts on the themed root. {@link #install}
     * adds it; callers that manage the stylesheet list themselves must add it too,
     * otherwise the color role tokens stay at their light baseline.
     *
     * @return the dark scope style class
     */
    public static String getDarkScopeClass() {
        return DARK_SCOPE_CLASS;
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
            ThemeScope.install(scene, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
        } else {
            ThemeScope.uninstall(scene, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
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
            ThemeScope.install(parent, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
        } else {
            ThemeScope.uninstall(parent, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
        }
    }

    /**
     * Removes any RxControls theme overlay from the scene, reverting to the
     * built-in light baseline.
     *
     * @param scene the scene to revert; must not be null
     */
    public static void uninstall(Scene scene) {
        ThemeScope.uninstall(scene, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
    }

    /**
     * Removes any RxControls theme overlay from the parent, reverting to the
     * built-in light baseline.
     *
     * @param parent the parent to revert; must not be null
     */
    public static void uninstall(Parent parent) {
        ThemeScope.uninstall(parent, DARK_SCOPE_CLASS, RXResources.DARK_OVERLAY_STYLESHEET);
    }
}
