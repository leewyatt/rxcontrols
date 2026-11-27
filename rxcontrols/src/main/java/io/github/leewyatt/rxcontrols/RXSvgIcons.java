package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.icon.SvgIconReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates {@link RXIconData} from single-color SVG files, such as icons exported
 * from a design tool.
 *
 * <p>The view box comes from the {@code viewBox} attribute, or from
 * {@code width} and {@code height} when it is missing. The filled geometry of
 * {@code path}, {@code rect}, {@code circle}, {@code ellipse}, {@code polygon}
 * and {@code polyline} elements is joined into one path, also inside {@code g}
 * groups. Elements with {@code fill="none"} are skipped, so the transparent
 * bounding box many icon sets include is ignored. Colors are ignored: the icon
 * takes its color from {@link RXIcon}.</p>
 *
 * <p>SVG that one filled path cannot show is rejected with an
 * {@link IllegalArgumentException}: strokes (outline icons must be converted to
 * filled outlines first), transforms, clipping, masks, gradients, partial
 * opacity, embedded style sheets, references, text, and shapes that mix the
 * non-zero and even-odd fill rules. Document type declarations and external
 * entities are not processed.</p>
 *
 * <pre>{@code
 * RXIconData logo = RXSvgIcons.read("app-logo", Path.of("icons/logo.svg"));
 * RXIcons.register(logo);
 * }</pre>
 */
public final class RXSvgIcons {

    private RXSvgIcons() {
    }

    /**
     * Reads an icon from an SVG file.
     *
     * @param name the icon name
     * @param file the SVG file
     * @return the icon
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file is not valid SVG or cannot be shown as one
     *                                  filled path
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static RXIconData read(String name, Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (InputStream in = Files.newInputStream(file)) {
            return read(name, in);
        }
    }

    /**
     * Reads an icon from an SVG resource or file URL.
     *
     * @param name the icon name
     * @param url  the SVG location
     * @return the icon
     * @throws IOException              if the URL cannot be read
     * @throws IllegalArgumentException if the content is not valid SVG or cannot be shown as
     *                                  one filled path
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static RXIconData read(String name, URL url) throws IOException {
        Objects.requireNonNull(url, "url");
        try (InputStream in = url.openStream()) {
            return read(name, in);
        }
    }

    /**
     * Reads an icon from a stream of SVG markup. The stream is not closed.
     *
     * @param name the icon name
     * @param in   the SVG markup
     * @return the icon
     * @throws IOException              if the stream cannot be read
     * @throws IllegalArgumentException if the content is not valid SVG or cannot be shown as
     *                                  one filled path
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static RXIconData read(String name, InputStream in) throws IOException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(in, "in");
        try {
            return SvgIconReader.read(name, in);
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    /**
     * Creates an icon from SVG markup held in a string.
     *
     * @param name the icon name
     * @param svg  the SVG markup
     * @return the icon
     * @throws IllegalArgumentException if the markup is not valid SVG or cannot be shown as one
     *                                  filled path
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static RXIconData parse(String name, String svg) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(svg, "svg");
        return SvgIconReader.read(name, new StringReader(svg));
    }
}
