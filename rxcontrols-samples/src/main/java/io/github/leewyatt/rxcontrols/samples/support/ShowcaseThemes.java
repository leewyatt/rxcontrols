package io.github.leewyatt.rxcontrols.samples.support;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.theme.AtlantaFXThemeBridge;
import io.github.leewyatt.rxcontrols.theme.RXTheme;
import javafx.application.Application;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared theme choices for the samples — the built-in RxControls light/dark looks
 * ({@link RXTheme}) and the AtlantaFX themes ({@link AtlantaFXThemeBridge}). Used by the
 * theme gallery and by every {@code RXShowcaseApplication} that enables theming.
 */
public final class ShowcaseThemes {

    private ShowcaseThemes() {
    }

    /**
     * A selectable theme: a display label and an action that applies it to a scene.
     *
     * @param label the display name
     * @param apply applies the theme to a scene
     */
    public record ThemeChoice(String label, Consumer<Scene> apply) {

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
                new ThemeChoice("RxControls — Light", scene -> rxControls(scene, RXTheme.Variant.LIGHT)),
                new ThemeChoice("RxControls — Dark", scene -> rxControls(scene, RXTheme.Variant.DARK)),
                new ThemeChoice("AtlantaFX — Primer Light", scene -> atlanta(scene, new PrimerLight())),
                new ThemeChoice("AtlantaFX — Primer Dark", scene -> atlanta(scene, new PrimerDark())),
                new ThemeChoice("AtlantaFX — Nord Light", scene -> atlanta(scene, new NordLight())),
                new ThemeChoice("AtlantaFX — Nord Dark", scene -> atlanta(scene, new NordDark())),
                new ThemeChoice("AtlantaFX — Cupertino Light", scene -> atlanta(scene, new CupertinoLight())),
                new ThemeChoice("AtlantaFX — Cupertino Dark", scene -> atlanta(scene, new CupertinoDark())),
                new ThemeChoice("AtlantaFX — Dracula", scene -> atlanta(scene, new Dracula())));
    }

    /**
     * Makes selecting a choice in the picker apply that theme to the scene. A choice picked
     * while the popup is still open is applied only after the popup has hidden, because
     * switching themes under an open popup logs a burst of CSS lookup warnings.
     *
     * @param picker        the theme picker
     * @param sceneSupplier supplies the scene to theme; nothing is applied while it returns
     *                      {@code null}
     */
    public static void bindPicker(ComboBox<ThemeChoice> picker, Supplier<Scene> sceneSupplier) {
        EventHandler<Event> applyWhenHidden = new EventHandler<>() {
            @Override
            public void handle(Event event) {
                // Detach first so a theme that fails to apply cannot leave the handler behind.
                picker.removeEventHandler(ComboBoxBase.ON_HIDDEN, this);
                applyChoice(picker.getValue(), sceneSupplier.get());
            }
        };
        picker.valueProperty().addListener((obs, old, choice) -> {
            if (picker.isShowing()) {
                // Several changes while the popup is open still leave a single pending apply.
                picker.removeEventHandler(ComboBoxBase.ON_HIDDEN, applyWhenHidden);
                picker.addEventHandler(ComboBoxBase.ON_HIDDEN, applyWhenHidden);
            } else {
                applyChoice(choice, sceneSupplier.get());
            }
        });
    }

    private static void applyChoice(ThemeChoice choice, Scene scene) {
        if (choice != null && scene != null) {
            choice.apply().accept(scene);
        }
    }

    // Samples-owned application-level chrome (window background, standard controls,
    // showcase chrome). RXTheme / AtlantaFXThemeBridge only theme the RxControls components;
    // these supply the rest for the demos.
    private static final String DARK_CHROME =
            ShowcaseThemes.class.getResource("rx-showcase-dark.css").toExternalForm();
    private static final String ATLANTAFX_CHROME =
            ShowcaseThemes.class.getResource("rx-showcase-atlantafx.css").toExternalForm();

    private static void rxControls(Scene scene, RXTheme.Variant variant) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        AtlantaFXThemeBridge.uninstall(scene);
        RXTheme.install(scene, variant);
        setChrome(scene, variant == RXTheme.Variant.DARK ? DARK_CHROME : null);
    }

    private static void atlanta(Scene scene, Theme theme) {
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        RXTheme.install(scene, RXTheme.Variant.LIGHT); // ensure the dark overlay is off
        AtlantaFXThemeBridge.install(scene);
        setChrome(scene, ATLANTAFX_CHROME);
    }

    private static void setChrome(Scene scene, String sheet) {
        scene.getStylesheets().removeAll(DARK_CHROME, ATLANTAFX_CHROME);
        if (sheet != null) {
            scene.getStylesheets().add(sheet);
        }
    }
}
