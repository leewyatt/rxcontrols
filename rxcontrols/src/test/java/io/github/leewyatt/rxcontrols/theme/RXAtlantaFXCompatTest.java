package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXFillButton;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completeness of the AtlantaFX bridge. Statically: every default role that reads a
 * Modena token is re-declared in the AtlantaFX scope, and every Modena palette token a
 * component rule reads directly is supplied by the scope's compat block, since AtlantaFX
 * defines none of them. At runtime: every control root resolves without a lookup or
 * conversion warning under all seven AtlantaFX themes, and representative colors take
 * the theme's values.
 */
public class RXAtlantaFXCompatTest {

    private static final String CSS = "/io/github/leewyatt/rxcontrols/theme/rx-controls.css";
    private static final String SCOPE_CLASS = "rx-theme-atlantafx";

    /** {@code -fx-*} names in value position: not followed by another name character. */
    private static final Pattern FX_REFERENCE = Pattern.compile("(?<![\\w-])(-fx-[a-z][a-z-]*[a-z])(?![\\w-])");
    private static final Pattern DECLARATION = Pattern.compile("(-[a-z][a-z-]*)\\s*:([^;]*);");
    private static final Pattern DEFAULT_STYLE_CLASS =
            Pattern.compile("DEFAULT_STYLE_CLASS\\s*=\\s*\"(rx-[a-z0-9-]+)\"");

    /**
     * Component-rule references allowed without a compat entry, each with the reason
     * every reference point sees a local declaration. Empty: no component rule declares
     * a Modena palette token of its own.
     */
    private static final Map<String, String> EXCEPTIONS = Map.of();

    private static final List<Theme> THEMES = List.of(new PrimerLight(), new PrimerDark(), new NordLight(),
            new NordDark(), new CupertinoLight(), new CupertinoDark(), new Dracula());

    /**
     * Starts the toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void setup() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /** Restores the shared-JVM default. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Static ====================

    /**
     * Every default role that reads a Modena token is re-declared in the AtlantaFX scope,
     * so under AtlantaFX the Modena reference is never the winning value.
     */
    @Test
    public void everyModenaDerivedRoleIsRedeclaredInTheScope() {
        List<Rule> rules = rules();
        Map<String, String> defaults = declarations(tokenRule(rules, false).body);
        Map<String, String> scoped = declarations(tokenRule(rules, true).body);

        List<String> missing = new ArrayList<>();
        defaults.forEach((name, value) -> {
            if (name.startsWith("-rx-") && FX_REFERENCE.matcher(value).find() && !scoped.containsKey(name)) {
                missing.add(name + ": " + value.trim());
            }
        });
        assertTrue(missing.isEmpty(), "default roles reading Modena that the AtlantaFX scope does not "
                + "re-declare:\n" + String.join("\n", missing));
    }

    /**
     * Every Modena palette token a component rule reads directly is declared by the
     * AtlantaFX scope's compat block or listed as a reasoned exception.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void everyModenaTokenAComponentReadsIsSupplied() throws Exception {
        List<Rule> rules = rules();
        Rule defaults = tokenRule(rules, false);
        Rule scope = tokenRule(rules, true);
        Set<String> supplied = declarations(scope.body).keySet();

        Map<String, String> referencedAt = new TreeMap<>();
        for (Rule rule : rules) {
            if (rule == defaults || rule == scope) {
                continue;
            }
            Matcher declaration = DECLARATION.matcher(rule.body);
            while (declaration.find()) {
                Matcher reference = FX_REFERENCE.matcher(declaration.group(2));
                while (reference.find()) {
                    referencedAt.putIfAbsent(reference.group(1), rule.selector.trim().split("\\s*,\\s*")[0]);
                }
            }
        }
        Set<String> modenaTokens = modenaPaletteTokens(referencedAt.keySet());

        List<String> missing = new ArrayList<>();
        for (String token : modenaTokens) {
            if (!supplied.contains(token) && !EXCEPTIONS.containsKey(token)) {
                missing.add(token + " (first read by " + referencedAt.get(token) + ")");
            }
        }
        assertTrue(modenaTokens.size() >= 10,
                "found only " + modenaTokens.size() + " Modena tokens; the scan is probably broken: " + modenaTokens);
        assertTrue(missing.isEmpty(), "Modena tokens read by component rules but not supplied by the AtlantaFX "
                + "compat block:\n" + String.join("\n", missing));
    }

    // ==================== Runtime ====================

    /**
     * Every control root resolves without a lookup or conversion warning in a bridged
     * scene under each of the seven AtlantaFX themes, and the fill button takes the
     * theme's accent.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void everyControlRootResolvesUnderEveryAtlantaFxTheme() throws Exception {
        Map<String, String> roots = controlRoots();
        Map<String, List<String>> warnings = new LinkedHashMap<>();
        Map<String, Integer> styled = new LinkedHashMap<>();
        List<String> wrongAccent = new ArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();

        for (Theme theme : THEMES) {
            List<String> messages = captureCssWarnings(() -> {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                int count = 0;
                for (Map.Entry<String, String> root : roots.entrySet()) {
                    try {
                        Node control = (Node) Class.forName(root.getValue()).getDeclaredConstructor().newInstance();
                        StackPane host = new StackPane(control);
                        RXAtlantaFXThemeBridge.install(new Scene(host, 400, 400));
                        host.applyCss();
                        host.layout();
                        count++;
                    } catch (Throwable error) {
                        failures.add(theme.getName() + " / " + root.getKey() + ": " + error);
                    }
                }
                styled.put(theme.getName(), count);

                RXFillButton button = new RXFillButton("go");
                Region accent = new Region();
                accent.setStyle("-fx-background-color: -color-accent-emphasis;");
                StackPane host = new StackPane(button, accent);
                RXAtlantaFXThemeBridge.install(new Scene(host, 200, 100));
                host.applyCss();
                host.layout();
                Paint fill = background((Region) button.lookup(".fill-region"));
                if (fill == null || !fill.equals(background(accent))) {
                    wrongAccent.add(theme.getName() + ": " + fill + " vs " + background(accent));
                }
            });
            if (!messages.isEmpty()) {
                warnings.put(theme.getName(), messages);
            }
        }

        assertTrue(failures.isEmpty(), "control roots that could not be styled: " + failures);
        styled.forEach((theme, count) -> assertEquals(roots.size(), count,
                theme + ": styled " + count + " of " + roots.size() + " control roots"));
        assertTrue(warnings.isEmpty(), "CSS warnings under AtlantaFX: " + warnings);
        assertTrue(wrongAccent.isEmpty(), "RXFillButton fill must follow -color-accent-emphasis: " + wrongAccent);
    }

    // ==================== Helpers ====================

    /** A comment-free CSS rule. The stylesheet has no nested blocks. */
    private record Rule(String selector, String body) {
    }

    private static List<Rule> rules() {
        String css = readCss().replaceAll("(?s)/\\*.*?\\*/", " ");
        List<Rule> rules = new ArrayList<>();
        int from = 0;
        while (true) {
            int open = css.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = css.indexOf('}', open);
            rules.add(new Rule(css.substring(from, open), css.substring(open + 1, close)));
            from = close + 1;
        }
        return rules;
    }

    /** The default token rule or the AtlantaFX scope rule: the rules that declare {@code -rx-primary}. */
    private static Rule tokenRule(List<Rule> rules, boolean scoped) {
        for (Rule rule : rules) {
            if (declarations(rule.body).containsKey("-rx-primary")
                    && rule.selector.contains("." + SCOPE_CLASS) == scoped) {
                return rule;
            }
        }
        throw new AssertionError((scoped ? "AtlantaFX scope" : "default token") + " rule not found");
    }

    private static Map<String, String> declarations(String body) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher matcher = DECLARATION.matcher(body);
        while (matcher.find()) {
            found.put(matcher.group(1), matcher.group(2));
        }
        return found;
    }

    /**
     * The candidates that are Modena palette tokens: the ones a plain region resolves
     * under Modena alone, outside any RxControls root.
     */
    private static Set<String> modenaPaletteTokens(Set<String> candidates) throws Exception {
        Set<String> tokens = new TreeSet<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            Map<String, Region> probes = new LinkedHashMap<>();
            StackPane root = new StackPane();
            for (String candidate : candidates) {
                Region probe = new Region();
                probe.setStyle("-fx-background-color: " + candidate + ";");
                probes.put(candidate, probe);
                root.getChildren().add(probe);
            }
            new Scene(root, 100, 100);
            Logger css = Logger.getLogger("javafx.css");
            Level previous = css.getLevel();
            css.setLevel(Level.OFF);
            try {
                root.applyCss();
            } finally {
                css.setLevel(previous);
            }
            probes.forEach((candidate, probe) -> {
                if (background(probe) != null) {
                    tokens.add(candidate);
                }
            });
        });
        return tokens;
    }

    private static Paint background(Region region) {
        if (region == null || region.getBackground() == null || region.getBackground().getFills().isEmpty()) {
            return null;
        }
        Paint fill = region.getBackground().getFills().get(0).getFill();
        return fill instanceof Color ? fill : null;
    }

    /** Every concrete control root: style class to class name, scanned from the sources. */
    private static Map<String, String> controlRoots() {
        Path src = moduleSrcMainJava();
        Map<String, String> roots = new TreeMap<>();
        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = DEFAULT_STYLE_CLASS.matcher(Files.readString(file, StandardCharsets.UTF_8));
                if (matcher.find()) {
                    String relative = src.relativize(file).toString();
                    String className = relative.substring(0, relative.length() - ".java".length())
                            .replace('/', '.').replace('\\', '.');
                    if (!isAbstract(className)) {
                        roots.put(matcher.group(1), className);
                    }
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        assertTrue(roots.size() >= 40, "found only " + roots.size() + " control roots: " + roots);
        return roots;
    }

    private static boolean isAbstract(String className) {
        try {
            return Modifier.isAbstract(Class.forName(className, false,
                    RXAtlantaFXCompatTest.class.getClassLoader()).getModifiers());
        } catch (Throwable notLoadable) {
            return false;
        }
    }

    private static Path moduleSrcMainJava() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("src", "main", "java"));
        try {
            Path testClasses = Paths.get(RXAtlantaFXCompatTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            candidates.add(testClasses.getParent().getParent().resolve(Paths.get("src", "main", "java")));
        } catch (URISyntaxException | NullPointerException ignored) {
            // Fall back to the working-directory candidate.
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate src/main/java; tried " + candidates);
    }

    private static String readCss() {
        try (InputStream in = RXAtlantaFXCompatTest.class.getResourceAsStream(CSS)) {
            assertNotNull(in, CSS + " not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Runs the body on the FX thread and returns the CSS warnings it logged. */
    private static List<String> captureCssWarnings(Runnable fxBody) throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger css = Logger.getLogger("javafx.css");
        css.addHandler(handler);
        try {
            runOnFx(fxBody);
        } finally {
            css.removeHandler(handler);
        }
        return messages;
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof AssertionError) {
            throw (AssertionError) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
