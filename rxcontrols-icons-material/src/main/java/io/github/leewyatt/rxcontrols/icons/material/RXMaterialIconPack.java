package io.github.leewyatt.rxcontrols.icons.material;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;

import java.util.List;

/**
 * The Material Icons pack, filled style, with the name prefix {@code mi}.
 *
 * <p>The pack is registered automatically when this library is on the class
 * path or module path; {@link RXMaterialIcons} lists the names. The icons are
 * read the first time one is looked up.</p>
 *
 * <p>Material Icons are made by Google and licensed under the Apache License
 * 2.0; the license text is in {@code META-INF/LICENSE-material-icons.txt}.</p>
 */
public final class RXMaterialIconPack implements RXIconPack {

    /**
     * The name prefix of this pack.
     */
    public static final String PREFIX = "mi";

    private final RXIconPack icons = RXIconPack.fromResource(PREFIX, "Material Icons",
            RXMaterialIconPack.class.getResource("material-icons.txt"));

    /**
     * Creates the pack. It is usually created by {@link java.util.ServiceLoader}.
     */
    public RXMaterialIconPack() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String prefix() {
        return icons.prefix();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String title() {
        return icons.title();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RXIconData find(String name) {
        return icons.find(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> names() {
        return icons.names();
    }
}
