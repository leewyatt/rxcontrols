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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The theme of the sample windows — plain Modena, where RxControls uses its default style,
 * or one of the AtlantaFX themes ({@link RXAtlantaFXThemeBridge}).
 *
 * <p>A theme is one setting for the whole application: the user-agent stylesheet is
 * global, while the AtlantaFX bridge is installed per scene. So the current theme lives
 * here, every sample window registers its scene, and applying a theme sets the
 * stylesheet once and installs or removes the bridge on every registered scene. A window
 * opened later starts in the current theme, and every theme picker shows it.</p>
 */
public final class ShowcaseThemes {

    private static final String PICKER_LISTENER_KEY = "showcase-themes-picker-listener";

    private static final List<ThemeChoice> CHOICES = List.of(
            new ThemeChoice("Modena (default)", Application.STYLESHEET_MODENA, false),
            atlanta("AtlantaFX — Primer Light", new PrimerLight()),
            atlanta("AtlantaFX — Primer Dark", new PrimerDark()),
            atlanta("AtlantaFX — Nord Light", new NordLight()),
            atlanta("AtlantaFX — Nord Dark", new NordDark()),
            atlanta("AtlantaFX — Cupertino Light", new CupertinoLight()),
            atlanta("AtlantaFX — Cupertino Dark", new CupertinoDark()),
            atlanta("AtlantaFX — Dracula", new Dracula()));

    // Modena is the JavaFX default, so nothing needs applying before the first change.
    private static final ReadOnlyObjectWrapper<ThemeChoice> CURRENT =
            new ReadOnlyObjectWrapper<>(ShowcaseThemes.class, "current", CHOICES.get(0));

    private static final List<WeakReference<Scene>> SCENES = new ArrayList<>();

    private ShowcaseThemes() {
    }

    /**
     * A selectable theme.
     *
     * @param label                 the display name
     * @param userAgentStylesheet   the user-agent stylesheet the theme installs
     * @param atlantaFX             whether the theme is an AtlantaFX theme, which needs the
     *                              bridge on every scene
     */
    public record ThemeChoice(String label, String userAgentStylesheet, boolean atlantaFX) {

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Returns the theme choices offered by the samples.
     *
     * @return the ordered list of theme choices (Modena first, then the AtlantaFX themes)
     */
    public static List<ThemeChoice> all() {
        return CHOICES;
    }

    /**
     * The theme of all sample windows.
     *
     * @return the current-theme property
     */
    public static ReadOnlyObjectProperty<ThemeChoice> currentProperty() {
        return CURRENT.getReadOnlyProperty();
    }

    /**
     * Returns the theme of all sample windows.
     *
     * @return the current theme
     */
    public static ThemeChoice getCurrent() {
        return CURRENT.get();
    }

    /**
     * Makes a theme the theme of all sample windows: sets the user-agent stylesheet and
     * installs or removes the AtlantaFX bridge on every registered scene.
     *
     * @param choice the theme to apply; {@code null} is ignored
     */
    public static void apply(ThemeChoice choice) {
        if (choice == null) {
            return;
        }
        Application.setUserAgentStylesheet(choice.userAgentStylesheet());
        CURRENT.set(choice);
        for (Iterator<WeakReference<Scene>> it = SCENES.iterator(); it.hasNext(); ) {
            Scene scene = it.next().get();
            if (scene == null) {
                it.remove();
            } else {
                bridge(scene, choice);
            }
        }
    }

    /**
     * Registers the scene of a sample window and brings it into the current theme. A scene
     * is held weakly, so a closed window does not stay registered.
     *
     * @param scene the scene to keep in the current theme; {@code null} is ignored
     */
    public static void register(Scene scene) {
        if (scene == null) {
            return;
        }
        boolean known = false;
        for (Iterator<WeakReference<Scene>> it = SCENES.iterator(); it.hasNext(); ) {
            Scene registered = it.next().get();
            if (registered == null) {
                it.remove();
            } else if (registered == scene) {
                known = true;
            }
        }
        if (!known) {
            SCENES.add(new WeakReference<>(scene));
        }
        bridge(scene, CURRENT.get());
    }

    /**
     * Turns a combo box into a theme picker: it lists the choices, shows the current theme,
     * applies the theme picked in it, and registers the scene it is shown in. A choice
     * picked while the popup is still open is applied only after the popup has hidden,
     * because switching themes under an open popup logs a burst of CSS lookup warnings.
     *
     * @param picker the theme picker
     */
    public static void bindPicker(ComboBox<ThemeChoice> picker) {
        picker.getItems().setAll(CHOICES);
        picker.setValue(CURRENT.get());

        // Held by the picker, so the global property does not keep a closed window alive.
        ChangeListener<ThemeChoice> follow = (obs, old, choice) -> picker.setValue(choice);
        picker.getProperties().put(PICKER_LISTENER_KEY, follow);
        CURRENT.addListener(new WeakChangeListener<>(follow));

        picker.sceneProperty().addListener((obs, old, scene) -> register(scene));
        register(picker.getScene());

        EventHandler<Event> applyWhenHidden = new EventHandler<>() {
            @Override
            public void handle(Event event) {
                // Detach first so a theme that fails to apply cannot leave the handler behind.
                picker.removeEventHandler(ComboBoxBase.ON_HIDDEN, this);
                applyPicked(picker.getValue());
            }
        };
        picker.valueProperty().addListener((obs, old, choice) -> {
            if (picker.isShowing()) {
                // Several changes while the popup is open still leave a single pending apply.
                picker.removeEventHandler(ComboBoxBase.ON_HIDDEN, applyWhenHidden);
                picker.addEventHandler(ComboBoxBase.ON_HIDDEN, applyWhenHidden);
            } else {
                applyPicked(choice);
            }
        });
    }

    // A picker that only follows the current theme must not apply it again.
    private static void applyPicked(ThemeChoice choice) {
        if (choice != null && choice != CURRENT.get()) {
            apply(choice);
        }
    }

    private static void bridge(Scene scene, ThemeChoice choice) {
        if (choice.atlantaFX()) {
            RXAtlantaFXThemeBridge.install(scene);
        } else {
            RXAtlantaFXThemeBridge.uninstall(scene);
        }
    }

    private static ThemeChoice atlanta(String label, Theme theme) {
        return new ThemeChoice(label, theme.getUserAgentStylesheet(), true);
    }
}
