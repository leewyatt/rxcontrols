package io.github.leewyatt.rxcontrols.samples.support;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFXThemeBridge;
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
 * Shared theme choices for the samples — plain Modena, where RxControls uses its
 * default style, an application-owned dark Modena palette that RxControls follows
 * without any theme API, and the AtlantaFX themes ({@link RXAtlantaFXThemeBridge}). Used by the
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
     * @return the ordered list of theme choices (Modena first, then the app dark palette,
     *         then the AtlantaFX themes)
     */
    public static List<ThemeChoice> all() {
        return List.of(
                new ThemeChoice("Modena (default)", scene -> modena(scene, null)),
                new ThemeChoice("Modena — app dark palette", scene -> modena(scene, APP_DARK_PALETTE)),
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

    // Samples-owned stylesheets. The dark palette flips Modena for the whole window, and
    // RxControls follows it on its own. The AtlantaFX chrome maps the showcase chrome onto
    // AtlantaFX colors; the bridge only themes the RxControls components.
    private static final String APP_DARK_PALETTE =
            ShowcaseThemes.class.getResource("rx-showcase-dark.css").toExternalForm();
    private static final String ATLANTAFX_CHROME =
            ShowcaseThemes.class.getResource("rx-showcase-atlantafx.css").toExternalForm();

    private static void modena(Scene scene, String palette) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        RXAtlantaFXThemeBridge.uninstall(scene);
        setChrome(scene, palette);
    }

    private static void atlanta(Scene scene, Theme theme) {
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        RXAtlantaFXThemeBridge.install(scene);
        setChrome(scene, ATLANTAFX_CHROME);
    }

    private static void setChrome(Scene scene, String sheet) {
        scene.getStylesheets().removeAll(APP_DARK_PALETTE, ATLANTAFX_CHROME);
        if (sheet != null) {
            scene.getStylesheets().add(sheet);
        }
    }
}
