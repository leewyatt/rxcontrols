package io.github.leewyatt.rxcontrols.utils;

import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convenience helpers for manipulating CSS style classes on any {@link Styleable}
 * and stylesheets on a {@link Parent} or {@link Scene}.
 *
 * <p>All methods are static and stateless. Style-class operations accept a
 * {@link Styleable}, so they cover not only {@code Node} but also
 * {@code MenuItem}, {@code Tab}, {@code TableColumn} and other non-node styleables.
 * Stylesheet operations are limited to {@link Parent} and {@link Scene}, the only
 * two carriers of {@code getStylesheets()}.</p>
 *
 * <p>These helpers target application code. Internal control state is expressed
 * with {@code PseudoClass}, not by imperatively juggling style classes. Arguments
 * are never null-tolerant: a null target throws {@link NullPointerException} so
 * caller bugs surface immediately.</p>
 */
public final class RXStyles {

    private RXStyles() {
    }

    // ==================== Style Class (Styleable) ====================

    /**
     * Adds style classes that are not already present, preserving order.
     *
     * @param target  the styleable to modify
     * @param classes the style classes to add
     * @throws NullPointerException if {@code target} is null
     */
    public static void addClass(Styleable target, String... classes) {
        add(target.getStyleClass(), classes);
    }

    /**
     * Removes the given style classes, including every duplicate occurrence.
     *
     * @param target  the styleable to modify
     * @param classes the style classes to remove
     * @throws NullPointerException if {@code target} is null
     */
    public static void removeClass(Styleable target, String... classes) {
        remove(target.getStyleClass(), classes);
    }

    /**
     * Toggles each style class: removed if present, added if absent.
     *
     * @param target  the styleable to modify
     * @param classes the style classes to toggle
     * @throws NullPointerException if {@code target} is null
     */
    public static void toggleClass(Styleable target, String... classes) {
        toggle(target.getStyleClass(), classes);
    }

    /**
     * Adds the given style classes when {@code present} is {@code true}, removes
     * them otherwise. Mirrors jQuery's {@code toggleClass(class, state)}.
     *
     * @param target  the styleable to modify
     * @param present whether the classes should end up present
     * @param classes the style classes to add or remove
     * @throws NullPointerException if {@code target} is null
     */
    public static void toggleClass(Styleable target, boolean present, String... classes) {
        ObservableList<String> list = target.getStyleClass();
        if (present) {
            add(list, classes);
        } else {
            remove(list, classes);
        }
    }

    /**
     * Replaces {@code oldClass} with {@code newClass}, keeping {@code newClass}
     * at the position {@code oldClass} held when possible. If {@code oldClass} is
     * absent, {@code newClass} is appended (if not already present); if
     * {@code newClass} is already present, {@code oldClass} is simply removed.
     *
     * @param target   the styleable to modify
     * @param oldClass the style class to remove
     * @param newClass the style class to ensure present
     * @throws NullPointerException if {@code target} is null
     */
    public static void replaceClass(Styleable target, String oldClass, String newClass) {
        ObservableList<String> list = target.getStyleClass();
        int index = list.indexOf(oldClass);
        if (index < 0) {
            if (!list.contains(newClass)) {
                list.add(newClass);
            }
            return;
        }
        if (list.contains(newClass)) {
            list.removeAll(oldClass);
        } else {
            list.set(index, newClass);
        }
    }

    /**
     * Returns whether the target carries the given style class.
     *
     * @param target     the styleable to query
     * @param styleClass the style class to test for
     * @return {@code true} if present
     * @throws NullPointerException if {@code target} is null
     */
    public static boolean hasClass(Styleable target, String styleClass) {
        return target.getStyleClass().contains(styleClass);
    }

    /**
     * Removes duplicate style classes, keeping first occurrences in order.
     *
     * @param target the styleable to modify
     * @throws NullPointerException if {@code target} is null
     */
    public static void distinctClass(Styleable target) {
        distinct(target.getStyleClass());
    }

    /**
     * Removes all style classes from the target.
     *
     * @param target the styleable to clear
     * @throws NullPointerException if {@code target} is null
     */
    public static void clearClass(Styleable target) {
        target.getStyleClass().clear();
    }

    // ==================== Stylesheet (Parent) ====================

    /**
     * Adds stylesheets that are not already present, preserving order.
     *
     * @param parent the parent to modify
     * @param sheets the stylesheet URLs to add
     * @throws NullPointerException if {@code parent} is null
     */
    public static void addSheets(Parent parent, String... sheets) {
        add(parent.getStylesheets(), sheets);
    }

    /**
     * Removes the given stylesheets, including every duplicate occurrence.
     *
     * @param parent the parent to modify
     * @param sheets the stylesheet URLs to remove
     * @throws NullPointerException if {@code parent} is null
     */
    public static void removeSheets(Parent parent, String... sheets) {
        remove(parent.getStylesheets(), sheets);
    }

    /**
     * Toggles each stylesheet: removed if present, added if absent.
     *
     * @param parent the parent to modify
     * @param sheets the stylesheet URLs to toggle
     * @throws NullPointerException if {@code parent} is null
     */
    public static void toggleSheets(Parent parent, String... sheets) {
        toggle(parent.getStylesheets(), sheets);
    }

    /**
     * Removes the stylesheets in {@code removeSheets} that are not also in
     * {@code addSheets}, then adds {@code addSheets}. Useful for swapping one
     * theme for another in a single call.
     *
     * @param parent       the parent to modify
     * @param removeSheets  the stylesheet URLs to remove unless re-added
     * @param addSheets     the stylesheet URLs to add
     * @throws NullPointerException if {@code parent} is null
     */
    public static void toggleSheets(Parent parent, String[] removeSheets, String... addSheets) {
        diffReplace(parent.getStylesheets(), removeSheets, addSheets);
    }

    /**
     * Returns whether the parent carries the given stylesheet.
     *
     * @param parent     the parent to query
     * @param stylesheet the stylesheet URL to test for
     * @return {@code true} if present
     * @throws NullPointerException if {@code parent} is null
     */
    public static boolean hasSheet(Parent parent, String stylesheet) {
        return parent.getStylesheets().contains(stylesheet);
    }

    /**
     * Removes duplicate stylesheets, keeping first occurrences in order.
     *
     * @param parent the parent to modify
     * @throws NullPointerException if {@code parent} is null
     */
    public static void distinctSheets(Parent parent) {
        distinct(parent.getStylesheets());
    }

    /**
     * Removes all stylesheets from the parent.
     *
     * @param parent the parent to clear
     * @throws NullPointerException if {@code parent} is null
     */
    public static void clearSheets(Parent parent) {
        parent.getStylesheets().clear();
    }

    // ==================== Stylesheet (Scene) ====================

    /**
     * Adds stylesheets that are not already present, preserving order.
     *
     * @param scene  the scene to modify
     * @param sheets the stylesheet URLs to add
     * @throws NullPointerException if {@code scene} is null
     */
    public static void addSheets(Scene scene, String... sheets) {
        add(scene.getStylesheets(), sheets);
    }

    /**
     * Removes the given stylesheets, including every duplicate occurrence.
     *
     * @param scene  the scene to modify
     * @param sheets the stylesheet URLs to remove
     * @throws NullPointerException if {@code scene} is null
     */
    public static void removeSheets(Scene scene, String... sheets) {
        remove(scene.getStylesheets(), sheets);
    }

    /**
     * Toggles each stylesheet: removed if present, added if absent.
     *
     * @param scene  the scene to modify
     * @param sheets the stylesheet URLs to toggle
     * @throws NullPointerException if {@code scene} is null
     */
    public static void toggleSheets(Scene scene, String... sheets) {
        toggle(scene.getStylesheets(), sheets);
    }

    /**
     * Removes the stylesheets in {@code removeSheets} that are not also in
     * {@code addSheets}, then adds {@code addSheets}.
     *
     * @param scene        the scene to modify
     * @param removeSheets the stylesheet URLs to remove unless re-added
     * @param addSheets    the stylesheet URLs to add
     * @throws NullPointerException if {@code scene} is null
     */
    public static void toggleSheets(Scene scene, String[] removeSheets, String... addSheets) {
        diffReplace(scene.getStylesheets(), removeSheets, addSheets);
    }

    /**
     * Returns whether the scene carries the given stylesheet.
     *
     * @param scene      the scene to query
     * @param stylesheet the stylesheet URL to test for
     * @return {@code true} if present
     * @throws NullPointerException if {@code scene} is null
     */
    public static boolean hasSheet(Scene scene, String stylesheet) {
        return scene.getStylesheets().contains(stylesheet);
    }

    /**
     * Removes duplicate stylesheets, keeping first occurrences in order.
     *
     * @param scene the scene to modify
     * @throws NullPointerException if {@code scene} is null
     */
    public static void distinctSheets(Scene scene) {
        distinct(scene.getStylesheets());
    }

    /**
     * Removes all stylesheets from the scene.
     *
     * @param scene the scene to clear
     * @throws NullPointerException if {@code scene} is null
     */
    public static void clearSheets(Scene scene) {
        scene.getStylesheets().clear();
    }

    // ==================== Internal list operations ====================

    private static void add(ObservableList<String> list, String[] entries) {
        for (String entry : entries) {
            if (!list.contains(entry)) {
                list.add(entry);
            }
        }
    }

    private static void remove(ObservableList<String> list, String[] entries) {
        list.removeAll(entries);
    }

    private static void toggle(ObservableList<String> list, String[] entries) {
        for (String entry : entries) {
            if (list.contains(entry)) {
                list.removeAll(entry);
            } else {
                list.add(entry);
            }
        }
    }

    private static void diffReplace(ObservableList<String> list, String[] removes, String[] adds) {
        if (removes.length == 0) {
            add(list, adds);
            return;
        }
        if (adds.length == 0) {
            remove(list, removes);
            return;
        }
        Set<String> keep = new HashSet<>(Arrays.asList(adds));
        for (String entry : removes) {
            if (!keep.contains(entry)) {
                list.removeAll(entry);
            }
        }
        add(list, adds);
    }

    private static void distinct(ObservableList<String> list) {
        List<String> deduped = list.stream().distinct().collect(Collectors.toList());
        if (deduped.size() != list.size()) {
            list.setAll(deduped);
        }
    }
}
