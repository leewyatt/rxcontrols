package io.github.leewyatt.rxcontrols.internal.icon;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;
import javafx.scene.shape.FillRule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An icon pack read from a text resource with one icon per line, loaded the first
 * time an icon is looked up.
 */
public final class TextIconPack implements RXIconPack {

    private static final Logger LOGGER = Logger.getLogger(TextIconPack.class.getName());

    private static final int FIELD_COUNT = 7;

    private final String prefix;
    private final String title;
    private final URL resource;

    private volatile Map<String, RXIconData> icons;

    /**
     * Creates a pack backed by the given resource.
     *
     * @param prefix   the name prefix, lowercase letters and digits only
     * @param title    the pack title
     * @param resource the text resource
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if the prefix is empty or has other characters
     */
    public TextIconPack(String prefix, String title, URL resource) {
        this.prefix = IconNames.checkPrefix(prefix);
        this.title = Objects.requireNonNull(title, "title");
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String prefix() {
        return prefix;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String title() {
        return title;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RXIconData find(String name) {
        return name == null ? null : icons().get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> names() {
        return List.copyOf(icons().keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "TextIconPack[" + prefix + ", " + resource + "]";
    }

    private Map<String, RXIconData> icons() {
        Map<String, RXIconData> result = icons;
        if (result == null) {
            synchronized (this) {
                result = icons;
                if (result == null) {
                    result = load();
                    icons = result;
                }
            }
        }
        return result;
    }

    private Map<String, RXIconData> load() {
        Map<String, RXIconData> loaded = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        try (InputStream in = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                RXIconData data = parse(trimmed);
                if (data == null) {
                    problems.add(Integer.toString(number));
                } else {
                    loaded.put(data.getName(), data);
                }
            }
        } catch (IOException error) {
            LOGGER.log(Level.WARNING, "Could not read the icon pack " + resource, error);
        }
        if (!problems.isEmpty()) {
            LOGGER.warning(() -> "Skipped malformed lines in the icon pack " + resource + ": " + problems);
        }
        return Collections.unmodifiableMap(loaded);
    }

    private RXIconData parse(String line) {
        String[] fields = line.split("\\s+", FIELD_COUNT);
        if (fields.length < FIELD_COUNT) {
            return null;
        }
        FillRule rule;
        if (fields[5].equals("n")) {
            rule = FillRule.NON_ZERO;
        } else if (fields[5].equals("e")) {
            rule = FillRule.EVEN_ODD;
        } else {
            return null;
        }
        try {
            return new RXIconData(prefix + IconNames.SEPARATOR + fields[0],
                    Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                    Double.parseDouble(fields[3]), Double.parseDouble(fields[4]), fields[6], rule);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
