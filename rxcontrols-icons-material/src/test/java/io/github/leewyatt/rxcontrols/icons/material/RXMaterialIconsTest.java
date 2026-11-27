package io.github.leewyatt.rxcontrols.icons.material;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;
import io.github.leewyatt.rxcontrols.RXIcons;
import javafx.scene.shape.FillRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Material Icons pack: it is found through ServiceLoader, every
 * constant names an icon, and the data matches the upstream SVG files.
 */
public class RXMaterialIconsTest {

    private static final int ICON_COUNT = 2122;

    @Test
    public void packIsFoundThroughTheServiceLoader() {
        RXIconPack pack = RXIcons.getPacks().stream()
                .filter(candidate -> candidate.prefix().equals(RXMaterialIconPack.PREFIX))
                .findFirst().orElseThrow();
        assertInstanceOf(RXMaterialIconPack.class, pack);
        assertEquals("Material Icons", pack.title());
        assertEquals(ICON_COUNT, pack.names().size());
    }

    @Test
    public void everyConstantNamesAnIcon() throws IllegalAccessException {
        List<String> names = new ArrayList<>();
        for (Field field : RXMaterialIcons.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                names.add((String) field.get(null));
            }
        }
        assertEquals(ICON_COUNT, names.size());
        List<String> missing = names.stream().filter(name -> RXIcons.find(name) == null).toList();
        assertTrue(missing.isEmpty(), "constants without an icon: " + missing);
        assertEquals(new RXMaterialIconPack().names().stream().sorted().toList(), names.stream().sorted().toList());
    }

    @Test
    public void iconDataMatchesTheUpstreamFiles() {
        RXIconData search = RXIcons.find(RXMaterialIcons.SEARCH);
        assertNotNull(search);
        assertEquals("mi-search", search.getName());
        assertEquals(0.0, search.getMinX());
        assertEquals(24.0, search.getWidth());
        assertEquals(24.0, search.getHeight());
        assertEquals(FillRule.NON_ZERO, search.getFillRule());
        assertEquals("M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 "
                + "4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 "
                + "14 9.5 11.99 14 9.5 14z", search.getPath());

        assertEquals(FillRule.EVEN_ODD, RXIcons.find(RXMaterialIcons.GRID_VIEW).getFillRule());
        assertEquals("mi-10k", RXMaterialIcons._10K);
        assertEquals("mi-account-circle", RXMaterialIcons.ACCOUNT_CIRCLE);
    }
}
