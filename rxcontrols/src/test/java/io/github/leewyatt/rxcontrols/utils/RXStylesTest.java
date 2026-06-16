package io.github.leewyatt.rxcontrols.utils;

import javafx.scene.Group;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXStyles}: style-class operations on any {@code Styleable}
 * (exercised through a non-node {@link MenuItem}) and stylesheet operations on a
 * {@link Parent}.
 */
public class RXStylesTest {

    // ==================== Style Class ====================

    @Test
    public void addClassIsAddIfAbsent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().clear();
        RXStyles.addClass(item, "a", "b");
        RXStyles.addClass(item, "a", "c");
        assertEquals(List.of("a", "b", "c"), item.getStyleClass());
    }

    @Test
    public void removeClassRemovesAllOccurrences() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "a", "c");
        RXStyles.removeClass(item, "a");
        assertEquals(List.of("b", "c"), item.getStyleClass());
    }

    @Test
    public void toggleClassFlipsEach() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a");
        RXStyles.toggleClass(item, "a", "b");
        assertEquals(List.of("b"), item.getStyleClass());
    }

    @Test
    public void conditionalToggleAddsWhenPresentTrue() {
        MenuItem item = new MenuItem();
        item.getStyleClass().clear();
        RXStyles.toggleClass(item, true, "on");
        assertTrue(RXStyles.hasClass(item, "on"));
        RXStyles.toggleClass(item, true, "on");
        assertEquals(List.of("on"), item.getStyleClass());
    }

    @Test
    public void conditionalToggleRemovesWhenPresentFalse() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("on");
        RXStyles.toggleClass(item, false, "on");
        assertFalse(RXStyles.hasClass(item, "on"));
    }

    @Test
    public void replaceClassSwapsInPlace() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("x", "old", "y");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("x", "new", "y"), item.getStyleClass());
    }

    @Test
    public void replaceClassAppendsWhenOldAbsent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("x");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("x", "new"), item.getStyleClass());
    }

    @Test
    public void replaceClassDropsOldWhenNewAlreadyPresent() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("old", "new");
        RXStyles.replaceClass(item, "old", "new");
        assertEquals(List.of("new"), item.getStyleClass());
    }

    @Test
    public void distinctClassRemovesDuplicates() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b", "a", "b", "c");
        RXStyles.distinctClass(item);
        assertEquals(List.of("a", "b", "c"), item.getStyleClass());
    }

    @Test
    public void clearClassEmptiesList() {
        MenuItem item = new MenuItem();
        item.getStyleClass().setAll("a", "b");
        RXStyles.clearClass(item);
        assertTrue(item.getStyleClass().isEmpty());
    }

    @Test
    public void nullTargetThrows() {
        assertThrows(NullPointerException.class, () -> RXStyles.addClass(null, "a"));
    }

    // ==================== Stylesheet ====================

    @Test
    public void addSheetsIsAddIfAbsent() {
        Group root = new Group();
        RXStyles.addSheets(root, "one.css", "two.css");
        RXStyles.addSheets(root, "one.css", "three.css");
        assertEquals(List.of("one.css", "two.css", "three.css"), root.getStylesheets());
    }

    @Test
    public void toggleSheetsFlipsEach() {
        Group root = new Group();
        root.getStylesheets().setAll("a.css");
        RXStyles.toggleSheets(root, "a.css", "b.css");
        assertEquals(List.of("b.css"), root.getStylesheets());
    }

    @Test
    public void diffToggleSheetsSwapsTheme() {
        Group root = new Group();
        String[] all = {"sunset.css", "ocean.css"};
        root.getStylesheets().setAll(all);
        RXStyles.toggleSheets(root, all, "ocean.css");
        assertEquals(List.of("ocean.css"), root.getStylesheets());
        assertTrue(RXStyles.hasSheet(root, "ocean.css"));
        assertFalse(RXStyles.hasSheet(root, "sunset.css"));
    }
}
