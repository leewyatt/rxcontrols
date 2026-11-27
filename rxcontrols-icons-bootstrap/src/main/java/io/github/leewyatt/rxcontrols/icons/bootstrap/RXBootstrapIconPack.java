package io.github.leewyatt.rxcontrols.icons.bootstrap;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;

import java.util.List;

/**
 * The Bootstrap Icons pack, with the name prefix {@code bi}.
 *
 * <p>The pack is registered automatically when this library is on the class
 * path or module path; {@link RXBootstrapIcons} lists the names. The icons are
 * read the first time one is looked up.</p>
 *
 * <p>Bootstrap Icons are made by The Bootstrap Authors and licensed under the MIT
 * License; the license text is in {@code META-INF/LICENSE-bootstrap-icons.txt}.</p>
 */
public final class RXBootstrapIconPack implements RXIconPack {

    /**
     * The name prefix of this pack.
     */
    public static final String PREFIX = "bi";

    private final RXIconPack icons = RXIconPack.fromResource(PREFIX, "Bootstrap Icons",
            RXBootstrapIconPack.class.getResource("bootstrap-icons.txt"));

    /**
     * Creates the pack. It is usually created by {@link java.util.ServiceLoader}.
     */
    public RXBootstrapIconPack() {
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
