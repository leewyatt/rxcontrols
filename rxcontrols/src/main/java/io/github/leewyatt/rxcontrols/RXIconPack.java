package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.icon.TextIconPack;

import java.net.URL;
import java.util.List;

/**
 * A named set of icons whose names share a prefix, such as {@code mi} for
 * {@code mi-search}.
 *
 * <p>Packs found on the class path or module path through
 * {@link java.util.ServiceLoader} are registered automatically; others can be
 * registered with {@link RXIcons#register(RXIconPack)}. A pack in a named module
 * declares {@code provides io.github.leewyatt.rxcontrols.RXIconPack with ...};
 * on the class path it lists its class in
 * {@code META-INF/services/io.github.leewyatt.rxcontrols.RXIconPack}. A provider
 * class needs a public no-argument constructor and should load its icons only
 * when they are first looked up.</p>
 *
 * <p>{@link #fromResource(String, String, URL)} creates a pack from a text
 * resource with one icon per line:</p>
 * <pre>
 * # comment
 * &lt;name&gt; &lt;minX&gt; &lt;minY&gt; &lt;width&gt; &lt;height&gt; &lt;n|e&gt; &lt;path&gt;
 * search 0 0 24 24 n M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 ...
 * </pre>
 * <p>The name is given without the prefix; {@code n} fills with the non-zero
 * rule and {@code e} with the even-odd rule; the path runs to the end of the
 * line.</p>
 */
public interface RXIconPack {

    /**
     * Returns the prefix of every icon name in this pack, without the hyphen that
     * follows it. A prefix has only lowercase letters and digits.
     *
     * @return the prefix, such as {@code mi}
     */
    String prefix();

    /**
     * Returns a short human-readable title of this pack.
     *
     * @return the title, such as {@code Material Icons}
     */
    String title();

    /**
     * Returns the icon with the given full name.
     *
     * @param name the full icon name, including the prefix
     * @return the icon, or {@code null} if this pack has no icon with that name
     */
    RXIconData find(String name);

    /**
     * Returns the full names of all icons in this pack.
     *
     * @return an unmodifiable list of names
     */
    List<String> names();

    /**
     * Creates a pack that reads its icons from a text resource the first time an
     * icon is looked up. The format is described in the class documentation.
     *
     * @param prefix   the name prefix, lowercase letters and digits only
     * @param title    the pack title
     * @param resource the text resource, encoded in UTF-8
     * @return the pack
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the prefix is empty or has other characters
     */
    static RXIconPack fromResource(String prefix, String title, URL resource) {
        return new TextIconPack(prefix, title, resource);
    }
}
