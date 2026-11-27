package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.icon.IconNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The registry that turns icon names into {@link RXIconData}.
 *
 * <p>A name is looked up first among single icons registered with
 * {@link #register(RXIconData)}, then in the pack whose prefix matches the part
 * of the name before the first hyphen. Packs provided through
 * {@link java.util.ServiceLoader} are registered the first time the registry is
 * used.</p>
 *
 * <pre>{@code
 * RXIcons.register(RXSvgIcons.read("app-logo", Path.of("logo.svg")));
 * RXIcon logo = new RXIcon("app-logo");
 * }</pre>
 *
 * <p>Register icons and packs before the icons that use them are created or
 * styled: an icon whose name could not be found stays empty until its name is
 * set again.</p>
 */
public final class RXIcons {

    private static final Logger LOGGER = Logger.getLogger(RXIcons.class.getName());

    private static final Map<String, RXIconPack> PACKS = new ConcurrentHashMap<>();
    private static final Map<String, RXIconData> SINGLE_ICONS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED_NAMES = ConcurrentHashMap.newKeySet();

    private static volatile boolean discovered;

    private RXIcons() {
    }

    // ==================== Registration ====================

    /**
     * Registers a pack. It replaces a registered pack with the same prefix.
     *
     * @param pack the pack
     * @throws NullPointerException     if the pack is {@code null}
     * @throws IllegalArgumentException if the pack's prefix is empty or has characters other
     *                                  than lowercase letters and digits
     */
    public static void register(RXIconPack pack) {
        Objects.requireNonNull(pack, "pack");
        discover();
        PACKS.put(IconNames.checkPrefix(pack.prefix()), pack);
    }

    /**
     * Registers a single icon under its own name. It takes precedence over a pack
     * icon with the same name and replaces a single icon registered earlier.
     *
     * @param icon the icon
     * @throws NullPointerException if the icon is {@code null}
     */
    public static void register(RXIconData icon) {
        Objects.requireNonNull(icon, "icon");
        SINGLE_ICONS.put(icon.getName(), icon);
    }

    // ==================== Lookup ====================

    /**
     * Returns the icon with the given name.
     *
     * @param name the full icon name, such as {@code mi-search}
     * @return the icon, or {@code null} if no registered icon or pack has it
     */
    public static RXIconData find(String name) {
        if (name == null) {
            return null;
        }
        RXIconData single = SINGLE_ICONS.get(name);
        if (single != null) {
            return single;
        }
        String prefix = IconNames.prefixOf(name);
        if (prefix == null) {
            return null;
        }
        discover();
        RXIconPack pack = PACKS.get(prefix);
        return pack == null ? null : pack.find(name);
    }

    /**
     * Returns the registered packs, sorted by prefix.
     *
     * @return a new list of the packs
     */
    public static List<RXIconPack> getPacks() {
        discover();
        List<RXIconPack> packs = new ArrayList<>(PACKS.values());
        packs.sort(Comparator.comparing(RXIconPack::prefix));
        return packs;
    }

    /**
     * Looks up a name for a node that shows it: an unknown name is logged once.
     */
    static RXIconData resolve(String name) {
        if (name == null) {
            return null;
        }
        RXIconData data = find(name);
        if (data == null && REPORTED_NAMES.add(name)) {
            LOGGER.warning(() -> "No icon is registered under the name \"" + name
                    + "\"; check the name and that its icon pack is on the class path or module path.");
        }
        return data;
    }

    // ==================== Discovery ====================

    private static void discover() {
        if (discovered) {
            return;
        }
        synchronized (RXIcons.class) {
            if (discovered) {
                return;
            }
            discovered = true;
            Iterator<RXIconPack> providers = ServiceLoader.load(RXIconPack.class).iterator();
            while (hasNext(providers)) {
                try {
                    RXIconPack pack = providers.next();
                    PACKS.putIfAbsent(IconNames.checkPrefix(pack.prefix()), pack);
                } catch (ServiceConfigurationError | RuntimeException error) {
                    LOGGER.log(Level.WARNING, "Could not load an icon pack", error);
                }
            }
        }
    }

    // A broken service configuration file can make hasNext() itself fail; stop there
    // rather than risk failing on the same entry forever.
    private static boolean hasNext(Iterator<RXIconPack> providers) {
        try {
            return providers.hasNext();
        } catch (ServiceConfigurationError error) {
            LOGGER.log(Level.WARNING, "Could not look up icon packs", error);
            return false;
        }
    }
}
