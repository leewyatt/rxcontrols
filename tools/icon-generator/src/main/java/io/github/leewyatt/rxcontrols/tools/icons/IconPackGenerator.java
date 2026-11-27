package io.github.leewyatt.rxcontrols.tools.icons;

import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXSvgIcons;
import javafx.scene.shape.FillRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Turns a folder of single-color SVG icons into the two files of an rxcontrols
 * icon pack: the text resource read by {@code RXIconPack.fromResource} and a
 * class of name constants.
 *
 * <p>File names become icon names: lowercase, with underscores turned into
 * hyphens ({@code account_circle.svg} becomes {@code <prefix>-account-circle}).
 * Constants are the upper-case names with hyphens turned into underscores, and
 * a leading underscore when the name starts with a digit.</p>
 *
 * <pre>
 * mvn -q install -pl rxcontrols -DskipTests
 * mvn -q -f tools/icon-generator/pom.xml compile exec:java -Dexec.args="
 *     --svg /path/to/svgs --prefix mi --title 'Material Icons'
 *     --package io.github.leewyatt.rxcontrols.icons.material --class RXMaterialIcons
 *     --java-out rxcontrols-icons-material/src/main/java
 *     --resource-out rxcontrols-icons-material/src/main/resources/.../material-icons.txt
 *     --source '@material-design-icons/svg 0.14.15 (filled), Apache License 2.0'"
 * </pre>
 *
 * <p>Paths are given relative to the working directory, which for
 * {@code exec:java} with {@code -f} is the folder Maven was started in.</p>
 */
public final class IconPackGenerator {

    private IconPackGenerator() {
    }

    /**
     * Runs the generator from the command line.
     *
     * @param args {@code --name value} pairs; see {@link Options#parse(String[])}
     * @throws IOException if a file cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        Result result = generate(options);
        System.out.println("Generated " + result.icons().size() + " icons into " + options.resourceOut()
                + " and " + options.javaFile());
        if (!result.merged().isEmpty()) {
            System.out.println("Merged " + result.merged().size() + " icons into one outline: " + result.merged());
        }
        if (!result.skipped().isEmpty()) {
            System.out.println("Skipped " + result.skipped().size() + " unsupported icons:");
            result.skipped().forEach((file, reason) -> System.out.println("  " + file + ": " + reason));
        }
    }

    /**
     * Reads every SVG file of the input folder and writes the pack files.
     *
     * @param options the generator options
     * @return the icons written and the files skipped
     * @throws IOException              if a file cannot be read or written
     * @throws IllegalArgumentException if an icon cannot be converted and skipping is off, or
     *                                  two files map to the same name
     */
    public static Result generate(Options options) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(options.svgDir())) {
            files = listing.filter(file -> file.getFileName().toString().endsWith(".svg")).sorted().toList();
        }
        Map<String, RXIconData> icons = new LinkedHashMap<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        List<String> merged = new ArrayList<>();
        Map<String, String> constants = new HashMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String localName = localName(fileName.substring(0, fileName.length() - ".svg".length()));
            String constant = constantName(localName);
            String previous = constants.put(constant, fileName);
            if (previous != null) {
                throw new IllegalArgumentException(previous + " and " + fileName + " both map to " + constant);
            }
            String fullName = options.prefix() + "-" + localName;
            try {
                icons.put(localName, RXSvgIcons.read(fullName, file));
                continue;
            } catch (IllegalArgumentException unsupported) {
                String reason = unsupported.getMessage();
                if (options.mergeOutlines()) {
                    try {
                        icons.put(localName, SvgOutlineMerger.merge(fullName, file));
                        merged.add(localName);
                        continue;
                    } catch (IllegalArgumentException notMerged) {
                        reason = reason + "; not merged: " + notMerged.getMessage();
                    }
                }
                if (!options.skipUnsupported()) {
                    throw new IllegalArgumentException(fileName + ": " + reason, unsupported);
                }
                skipped.put(fileName, reason);
                constants.remove(constant);
            }
        }
        writeResource(options, icons, merged);
        writeConstants(options, icons);
        return new Result(new ArrayList<>(icons.keySet()), merged, skipped);
    }

    // ==================== Names ====================

    /**
     * Turns a file name without its extension into an icon name without the prefix.
     *
     * @param baseName the file name without {@code .svg}
     * @return the lowercase name with hyphens
     * @throws IllegalArgumentException if the name has characters other than letters, digits,
     *                                  hyphens and underscores
     */
    public static String localName(String baseName) {
        String name = baseName.toLowerCase(Locale.ROOT).replace('_', '-');
        if (name.isEmpty() || !name.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
            throw new IllegalArgumentException("Unsupported icon file name: " + baseName);
        }
        return name;
    }

    /**
     * Turns an icon name without the prefix into a Java constant name.
     *
     * @param localName the icon name without the prefix
     * @return the constant name
     */
    public static String constantName(String localName) {
        String constant = localName.toUpperCase(Locale.ROOT).replace('-', '_');
        return Character.isDigit(constant.charAt(0)) ? "_" + constant : constant;
    }

    // ==================== Output ====================

    private static void writeResource(Options options, Map<String, RXIconData> icons, List<String> merged)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("# ").append(options.title()).append(": ").append(icons.size()).append(" icons, prefix \"")
                .append(options.prefix()).append("\".\n");
        if (options.source() != null) {
            text.append("# Source: ").append(options.source()).append('\n');
        }
        if (!merged.isEmpty()) {
            text.append("# Icons merged into one outline because they mix fill rules or use transforms: ")
                    .append(merged.size()).append('\n');
        }
        text.append("# Generated by the rxcontrols icon generator (tools/icon-generator); do not edit.\n");
        text.append("# <name> <minX> <minY> <width> <height> <n|e> <path>\n");
        icons.forEach((name, data) -> text.append(name).append(' ')
                .append(number(data.getMinX())).append(' ').append(number(data.getMinY())).append(' ')
                .append(number(data.getWidth())).append(' ').append(number(data.getHeight())).append(' ')
                .append(data.getFillRule() == FillRule.EVEN_ODD ? 'e' : 'n').append(' ')
                .append(data.getPath().replaceAll("\\s+", " ").strip()).append('\n'));
        Path out = options.resourceOut();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
    }

    private static void writeConstants(Options options, Map<String, RXIconData> icons) throws IOException {
        String example = icons.containsKey("search") ? "search"
                : icons.isEmpty() ? "name" : icons.keySet().iterator().next();
        StringBuilder java = new StringBuilder();
        java.append("package ").append(options.javaPackage()).append(";\n\n");
        java.append("/**\n");
        java.append(" * Names of the icons in the ").append(options.title()).append(" pack, for\n");
        java.append(" * {@link io.github.leewyatt.rxcontrols.RXIcon}.\n");
        java.append(" *\n");
        java.append(" * <p>Each constant is a full icon name, such as {@code ").append(options.prefix()).append('-')
                .append(example).append("}. Use it in code,\n");
        java.append(" * as in {@code new RXIcon(").append(options.className()).append('.')
                .append(constantName(example)).append(")}, or write the name in CSS,\n");
        java.append(" * as in {@code -rx-icon: \"").append(options.prefix()).append('-')
                .append(example).append("\"}.</p>\n");
        if (options.source() != null) {
            java.append(" *\n * <p>Source: ").append(escapeJavadoc(options.source())).append(".</p>\n");
        }
        java.append(" *\n * <p>Generated by the rxcontrols icon generator; do not edit.</p>\n");
        java.append(" */\n");
        java.append("public final class ").append(options.className()).append(" {\n\n");
        for (String name : icons.keySet()) {
            java.append("    public static final String ").append(constantName(name)).append(" = \"")
                    .append(options.prefix()).append('-').append(name).append("\";\n");
        }
        java.append("\n    private ").append(options.className()).append("() {\n    }\n}\n");
        Path out = options.javaFile();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, java, StandardCharsets.UTF_8);
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String escapeJavadoc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("@", "&#64;")
                .replace("*/", "*&#47;");
    }

    // ==================== Options and result ====================

    /**
     * Generator options.
     *
     * @param svgDir          the folder of SVG files
     * @param prefix          the pack prefix
     * @param title           the pack title
     * @param javaPackage     the package of the constants class
     * @param className       the simple name of the constants class
     * @param javaOut         the source root the constants class is written into
     * @param resourceOut     the text resource file to write
     * @param source          where the icons come from and their license, or {@code null}
     * @param skipUnsupported whether to skip icons that cannot be converted instead of failing
     * @param mergeOutlines   whether to merge the elements of an icon that mixes fill rules or
     *                        uses transforms into one outline, instead of rejecting it
     */
    public record Options(Path svgDir, String prefix, String title, String javaPackage, String className,
                          Path javaOut, Path resourceOut, String source, boolean skipUnsupported,
                          boolean mergeOutlines) {

        /**
         * Checks the required options.
         *
         * @throws NullPointerException if a required option is missing
         */
        public Options {
            Objects.requireNonNull(svgDir, "--svg");
            Objects.requireNonNull(prefix, "--prefix");
            Objects.requireNonNull(title, "--title");
            Objects.requireNonNull(javaPackage, "--package");
            Objects.requireNonNull(className, "--class");
            Objects.requireNonNull(javaOut, "--java-out");
            Objects.requireNonNull(resourceOut, "--resource-out");
        }

        /**
         * Parses {@code --svg}, {@code --prefix}, {@code --title}, {@code --package},
         * {@code --class}, {@code --java-out}, {@code --resource-out}, the optional
         * {@code --source} and the flags {@code --skip-unsupported} and {@code --merge-outlines}.
         *
         * @param args the command-line arguments
         * @return the options
         * @throws IllegalArgumentException if an option is unknown or has no value
         * @throws NullPointerException     if a required option is missing
         */
        public static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            boolean skip = false;
            boolean merge = false;
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (key.equals("--skip-unsupported")) {
                    skip = true;
                    continue;
                }
                if (key.equals("--merge-outlines")) {
                    merge = true;
                    continue;
                }
                if (!key.startsWith("--") || i + 1 >= args.length) {
                    throw new IllegalArgumentException("Expected --name value, found " + key);
                }
                values.put(key, args[++i]);
            }
            return new Options(path(values.get("--svg")), values.get("--prefix"), values.get("--title"),
                    values.get("--package"), values.get("--class"), path(values.get("--java-out")),
                    path(values.get("--resource-out")), values.get("--source"), skip, merge);
        }

        /**
         * Returns the file the constants class is written to.
         *
         * @return the Java source file
         */
        public Path javaFile() {
            return javaOut.resolve(javaPackage.replace('.', '/')).resolve(className + ".java");
        }

        private static Path path(String value) {
            return value == null ? null : Path.of(value);
        }
    }

    /**
     * What a run produced.
     *
     * @param icons   the icon names written, without the prefix
     * @param merged  the icon names whose elements were merged into one outline
     * @param skipped the skipped file names and why they were skipped
     */
    public record Result(List<String> icons, List<String> merged, Map<String, String> skipped) {
    }
}
