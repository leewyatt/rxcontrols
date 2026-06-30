package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift-proofing for the color-token baseline added to {@code rx-controls.css}.
 *
 * <p>Two guards enforce the Model-3 baseline (see the theme migration plan):
 * a static guard asserting every control root's {@code DEFAULT_STYLE_CLASS} is
 * listed in the baseline selector block, and a runtime guard asserting every
 * control resolves its {@code -rx-*} tokens with no unresolved lookups. The
 * runtime guard is the authoritative backstop and is independent of the static
 * scan convention; {@link #cssErrorCaptureDetectsUnresolvedLookup()} proves the
 * capture mechanism actually fires.
 */
public class RxControlsThemeBaselineTest {

    private static final String CSS_RESOURCE =
            "/io/github/leewyatt/rxcontrols/theme/rx-controls.css";

    /** Matches {@code DEFAULT_STYLE_CLASS = "rx-..."} constants in control sources. */
    private static final Pattern DEFAULT_STYLE_CLASS_PATTERN =
            Pattern.compile("DEFAULT_STYLE_CLASS\\s*=\\s*\"(rx-[a-z0-9-]+)\"");

    /** Matches a class selector {@code .rx-...} in the baseline selector list. */
    private static final Pattern ROOT_SELECTOR_PATTERN =
            Pattern.compile("\\.(rx-[a-z0-9-]+)");

    /**
     * Starts the JavaFX toolkit so scenes can be built and CSS applied.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        // Pin modena so the -fx-* aliases the role tokens reference (e.g.
        // -rx-on-surface -> -fx-text-base-color) resolve deterministically; an
        // absent platform stylesheet would otherwise surface as a -fx-* failure.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Static drift guard ====================

    /**
     * Asserts the source control roots and the baseline selector list are the
     * same set: a new control root missing from the baseline (its tokens would
     * not resolve) or a stale/misspelled baseline selector both fail here.
     */
    @Test
    public void everyControlRootIsCoveredByTheTokenBaseline() {
        Set<String> scanned = new TreeSet<>(scanControlRoots().keySet());

        // Guard against a broken scan silently satisfying a subset check.
        assertTrue(scanned.size() >= 40,
                "Source scan found only " + scanned.size() + " control roots; "
                        + "the DEFAULT_STYLE_CLASS scan is probably broken: " + scanned);
        assertTrue(scanned.contains("rx-button") && scanned.contains("rx-cascader")
                        && scanned.contains("rx-timeline-view"),
                "Source scan missing well-known control roots: " + scanned);

        Set<String> baseline = parseBaselineSelectors();

        Set<String> missingFromBaseline = new TreeSet<>(scanned);
        missingFromBaseline.removeAll(baseline);
        Set<String> staleInBaseline = new TreeSet<>(baseline);
        staleInBaseline.removeAll(scanned);

        assertTrue(missingFromBaseline.isEmpty(),
                "Control roots declared in source but absent from the rx-controls.css "
                        + "token baseline (add them to the baseline selector list): "
                        + missingFromBaseline);
        assertTrue(staleInBaseline.isEmpty(),
                "Selectors in the token baseline that are not real control roots "
                        + "(remove them or fix the typo): " + staleInBaseline);
    }

    // ==================== Runtime backstop ====================

    /**
     * Proves the CSS-error capture used by the runtime guard actually fires:
     * a rule referencing a non-existent {@code -rx-*} token must produce a
     * captured "Could not resolve" warning. If this fails, the smoke test below
     * cannot be trusted.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void cssErrorCaptureDetectsUnresolvedLookup() throws Exception {
        Path probeSheet = Files.createTempFile("rx-probe", ".css");
        probeSheet.toFile().deleteOnExit();
        Files.writeString(probeSheet,
                ".rx-probe-missing-token { -fx-background-color: -rx-this-token-does-not-exist; }",
                StandardCharsets.UTF_8);
        String sheetUrl = probeSheet.toUri().toString();

        List<String> messages = captureCssMessages(() -> {
            Region probe = new Region();
            probe.getStyleClass().add("rx-probe-missing-token");
            StackPane root = new StackPane(probe);
            Scene scene = new Scene(root, 100, 100);
            scene.getStylesheets().add(sheetUrl);
            root.applyCss();
            root.layout();
        });

        assertFalse(unresolvedRxLookups(messages).isEmpty(),
                "Expected an unresolved '-rx-*' lookup warning to be captured, but none "
                        + "was. The CSS-error capture mechanism is not working, so the "
                        + "runtime smoke test cannot be trusted. Captured: " + messages);
    }

    /**
     * Instantiates every control root (the same set the static guard scans),
     * applies CSS, and asserts no unresolved {@code -rx-*} lookups were logged.
     * Covers the popup content root ({@code rx-cascader-view}) because
     * {@code RXCascaderView} is instantiated directly, with its own UA sheet.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void allControlRootsResolveTokensWithoutUnresolvedLookups() throws Exception {
        Map<String, String> roots = scanControlRoots();
        List<String> failures = new CopyOnWriteArrayList<>();

        List<String> messages = captureCssMessages(() -> {
            StackPane host = new StackPane();
            new Scene(host, 400, 400);
            for (Map.Entry<String, String> entry : roots.entrySet()) {
                try {
                    Class<?> type = Class.forName(entry.getValue());
                    Node control = (Node) type.getDeclaredConstructor().newInstance();
                    host.getChildren().setAll(control);
                    host.applyCss();
                    host.layout();
                } catch (Throwable error) {
                    failures.add(entry.getKey() + " -> " + entry.getValue() + ": " + error);
                } finally {
                    host.getChildren().clear();
                }
            }
        });

        assertTrue(failures.isEmpty(),
                "Some control roots could not be instantiated/laid out: " + failures);

        List<String> unresolved = unresolvedRxLookups(messages);
        assertTrue(unresolved.isEmpty(),
                "Unresolved -rx-* lookups during CSS application — a control rule "
                        + "references a token its root does not receive (check the baseline "
                        + "selector list and the token name spelling):\n"
                        + String.join("\n", unresolved));
    }

    /**
     * Samples the headline brand path: an {@code RXFillButton} resolves
     * {@code -rx-fill -> -rx-primary -> #616dff}, and an {@code RXButton}
     * resolves its ripple fill through {@code -rx-state-overlay-color -> black}.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void brandColorResolvesThroughThePrimaryToken() throws Exception {
        AtomicReference<Color> fillRef = new AtomicReference<>();
        AtomicReference<Paint> rippleRef = new AtomicReference<>();

        runOnFx(() -> {
            RXFillButton fillButton = new RXFillButton("Go");
            RXButton plain = new RXButton("Go");
            StackPane host = new StackPane(fillButton, plain);
            new Scene(host, 240, 120);
            host.applyCss();
            host.layout();

            Node fillRegion = fillButton.lookup(".fill-region");
            assertNotNull(fillRegion, "fill-region not found after CSS/layout");
            Region region = (Region) fillRegion;
            assertNotNull(region.getBackground(), "fill-region background not applied by CSS");
            fillRef.set((Color) region.getBackground().getFills().get(0).getFill());
            rippleRef.set(plain.getRippleFill());
        });

        assertEquals(Color.web("#616dff"), fillRef.get(),
                "RXFillButton fill should resolve -rx-fill -> -rx-primary -> #616dff");
        assertEquals(Color.BLACK, rippleRef.get(),
                "RXButton ripple fill should resolve -rx-ripple-fill -> "
                        + "-rx-state-overlay-color -> black");
    }

    // ==================== Helpers ====================

    /**
     * Scans {@code src/main/java} for {@code DEFAULT_STYLE_CLASS = "rx-..."}
     * constants.
     *
     * @return style-class -&gt; fully-qualified class name, sorted by style class
     */
    private static Map<String, String> scanControlRoots() {
        Path src = moduleSrcMainJava();
        Map<String, String> roots = new TreeMap<>();
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> collectRoots(src, path, roots));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return roots;
    }

    private static void collectRoots(Path srcRoot, Path javaFile, Map<String, String> roots) {
        String content;
        try {
            content = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        Matcher matcher = DEFAULT_STYLE_CLASS_PATTERN.matcher(content);
        while (matcher.find()) {
            String className = toClassName(srcRoot, javaFile);
            if (isAbstractControl(className)) {
                // Abstract base controls (e.g. RXAnimatedButton / RXAnimatedLabel)
                // add a shared style class — like JavaFX's `.cell` on the abstract
                // Cell — that always co-occurs with a concrete subclass root already
                // in the baseline. They cannot be instantiated alone and are never
                // the sole token receiver, so they are not independent control roots.
                continue;
            }
            roots.put(matcher.group(1), className);
        }
    }

    /**
     * Reports whether the named control class is {@code abstract}. Loaded without
     * initialization (only the modifiers are needed); a class that cannot be
     * loaded is treated as concrete so the instantiation guard surfaces the real
     * error rather than silently dropping a root.
     */
    private static boolean isAbstractControl(String className) {
        try {
            Class<?> type = Class.forName(className, false,
                    RxControlsThemeBaselineTest.class.getClassLoader());
            return Modifier.isAbstract(type.getModifiers());
        } catch (Throwable notLoadable) {
            return false;
        }
    }

    private static String toClassName(Path srcRoot, Path javaFile) {
        String relative = srcRoot.relativize(javaFile).toString();
        relative = relative.substring(0, relative.length() - ".java".length());
        return relative.replace('/', '.').replace('\\', '.');
    }

    private static Path moduleSrcMainJava() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("src", "main", "java"));
        try {
            Path codeSource = Paths.get(RxControlsThemeBaselineTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            // .../rxcontrols/target/test-classes -> module dir is two levels up.
            Path moduleDir = codeSource.getParent().getParent();
            candidates.add(moduleDir.resolve(Paths.get("src", "main", "java")));
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

    private static Set<String> parseBaselineSelectors() {
        // Strip comments first; CSS rules have no nested braces, so each rule is
        // "selectors { body }". Locate the baseline rule by the token it uniquely
        // DEFINES (-rx-primary:) rather than by file position, so prepending another
        // rule later cannot silently mis-parse the selector list.
        String css = readCss().replaceAll("(?s)/\\*.*?\\*/", " ");
        String selectorText = null;
        int from = 0;
        while (true) {
            int open = css.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = css.indexOf('}', open);
            if (close < 0) {
                break;
            }
            if (css.substring(open + 1, close).contains("-rx-primary:")) {
                int prevClose = css.lastIndexOf('}', open);
                selectorText = css.substring(prevClose < 0 ? 0 : prevClose + 1, open);
                break;
            }
            from = close + 1;
        }
        assertNotNull(selectorText,
                "baseline token rule (the one defining -rx-primary) not found in rx-controls.css");
        Set<String> selectors = new TreeSet<>();
        Matcher matcher = ROOT_SELECTOR_PATTERN.matcher(selectorText);
        while (matcher.find()) {
            selectors.add(matcher.group(1));
        }
        return selectors;
    }

    private static String readCss() {
        try (InputStream in = RXResources.class.getResourceAsStream(CSS_RESOURCE)) {
            assertNotNull(in, "rx-controls.css not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static List<String> unresolvedRxLookups(List<String> messages) {
        return messages.stream()
                .filter(RxControlsThemeBaselineTest::isRxResolutionFailure)
                .collect(Collectors.toList());
    }

    /**
     * Recognizes a CSS value resolution/conversion failure originating in the
     * RxControls stylesheet. In JFX17 an unresolved looked-up color falls
     * through to a String and the converter logs a {@code ClassCastException}
     * ("...while converting value for '...' from rule '...'"); older/non-color
     * paths log "...while resolving lookups for...". Scoped to RxControls so
     * unrelated platform CSS warnings are ignored.
     */
    private static boolean isRxResolutionFailure(String message) {
        boolean resolutionFailure = message.contains("while converting value for")
                || message.contains("while resolving lookups for")
                || message.contains("Could not resolve");
        if (!resolutionFailure) {
            return false;
        }
        return message.contains("rx-controls.css")
                || message.contains(".rx-")
                || message.contains("-rx-");
    }

    /**
     * Runs the body on the FX thread with a temporary handler on the root logger
     * capturing JavaFX CSS warnings (the CSS logger propagates to root).
     */
    private static List<String> captureCssMessages(Runnable fxBody) throws Exception {
        CollectingHandler handler = new CollectingHandler();
        handler.setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        root.addHandler(handler);
        try {
            runOnFx(fxBody);
        } finally {
            root.removeHandler(handler);
        }
        return handler.messages;
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
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    private static final class CollectingHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null && record.getMessage() != null) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
