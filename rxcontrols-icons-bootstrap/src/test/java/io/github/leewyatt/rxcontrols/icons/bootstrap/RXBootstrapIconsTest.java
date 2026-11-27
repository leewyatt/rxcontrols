package io.github.leewyatt.rxcontrols.icons.bootstrap;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Bootstrap Icons pack: it is found through ServiceLoader, every
 * constant names an icon, and the data matches the upstream SVG files.
 */
public class RXBootstrapIconsTest {

    private static final int ICON_COUNT = 2077;

    @Test
    public void packIsFoundThroughTheServiceLoader() {
        RXIconPack pack = RXIcons.getPacks().stream()
                .filter(candidate -> candidate.prefix().equals(RXBootstrapIconPack.PREFIX))
                .findFirst().orElseThrow();
        assertInstanceOf(RXBootstrapIconPack.class, pack);
        assertEquals("Bootstrap Icons", pack.title());
        assertEquals(ICON_COUNT, pack.names().size());
    }

    @Test
    public void everyConstantNamesAnIcon() throws IllegalAccessException {
        List<String> names = new ArrayList<>();
        for (Field field : RXBootstrapIcons.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                names.add((String) field.get(null));
            }
        }
        assertEquals(ICON_COUNT, names.size());
        List<String> missing = names.stream().filter(name -> RXIcons.find(name) == null).toList();
        assertTrue(missing.isEmpty(), "constants without an icon: " + missing);
    }

    @Test
    public void iconDataMatchesTheUpstreamFiles() {
        RXIconData alarm = RXIcons.find(RXBootstrapIcons.ALARM);
        assertNotNull(alarm);
        assertEquals(16.0, alarm.getWidth());
        assertEquals(16.0, alarm.getHeight());
        assertEquals(FillRule.NON_ZERO, alarm.getFillRule());
        assertTrue(alarm.getPath().startsWith("M8.5 5.5a.5.5 0 0 0-1 0v3.362l-1.429 2.38"), alarm.getPath());

        assertEquals(FillRule.EVEN_ODD, RXIcons.find(RXBootstrapIcons.ACTIVITY).getFillRule());
        assertEquals("bi-0-circle", RXBootstrapIcons._0_CIRCLE);
    }

    @Test
    public void iconsThatMixFillRulesAreMergedAndTwoToneIconsAreLeftOut() {
        RXIconData merged = RXIcons.find(RXBootstrapIcons.ARROW_CLOCKWISE);
        assertNotNull(merged);
        assertEquals(FillRule.NON_ZERO, merged.getFillRule());
        assertNull(RXIcons.find("bi-opencollective"), "a two-tone icon cannot be shown in one color");
    }
}
