package io.github.leewyatt.rxcontrols.samples.support;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFX;
import io.github.leewyatt.rxcontrols.theme.RXTheme;
import javafx.application.Application;
import javafx.scene.Scene;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared theme choices for the samples — the built-in RxControls light/dark looks
 * ({@link RXTheme}) and the AtlantaFX themes ({@link RXAtlantaFX}). Used by the
 * theme gallery and by every {@code RXShowcaseApplication} that enables theming.
 */
public final class ShowcaseThemes {

    private ShowcaseThemes() {
    }

    /**
     * A selectable theme: a display label, an action that applies it to a scene,
     * and the CSS color a showcase should paint its preview surface with (null
     * keeps the showcase's default light preview).
     *
     * @param label             the display name
     * @param apply             applies the theme to a scene
     * @param previewBackground a CSS color for the preview surface, or null
     */
    public record ThemeChoice(String label, Consumer<Scene> apply, String previewBackground) {

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @return the ordered list of theme choices (RxControls light/dark first, then
     *         the AtlantaFX themes)
     */
    public static List<ThemeChoice> all() {
        return List.of(
                new ThemeChoice("RxControls — Light", scene -> rxControls(scene, RXTheme.Variant.LIGHT), null),
                new ThemeChoice("RxControls — Dark", scene -> rxControls(scene, RXTheme.Variant.DARK), "#1e1f2b"),
                new ThemeChoice("AtlantaFX — Primer Light", scene -> atlanta(scene, new PrimerLight()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Primer Dark", scene -> atlanta(scene, new PrimerDark()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Nord Light", scene -> atlanta(scene, new NordLight()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Nord Dark", scene -> atlanta(scene, new NordDark()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Cupertino Light", scene -> atlanta(scene, new CupertinoLight()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Cupertino Dark", scene -> atlanta(scene, new CupertinoDark()), "-color-bg-default"),
                new ThemeChoice("AtlantaFX — Dracula", scene -> atlanta(scene, new Dracula()), "-color-bg-default"));
    }

    private static void rxControls(Scene scene, RXTheme.Variant variant) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        RXAtlantaFX.uninstall(scene);
        RXTheme.install(scene, variant);
    }

    private static void atlanta(Scene scene, Theme theme) {
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        RXTheme.install(scene, RXTheme.Variant.LIGHT); // ensure the dark overlay is off
        RXAtlantaFX.install(scene);
    }
}
