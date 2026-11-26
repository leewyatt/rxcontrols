package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the library's nodes from FXML the way an application writes them:
 * several style classes in one attribute, a label's text inside the tag, a
 * list of items as child elements. A class that FXML has to build through one
 * of its constructors, rather than through the no-argument constructor and
 * setters, gets these wrong without an error.
 *
 * <p>Nodes are found by scanning the compiled library classes outside the
 * internal package, so a new one is covered without editing this test.
 */
public class FxmlLoadingTest {

    private static final String LIBRARY_PACKAGE = "io.github.leewyatt.rxcontrols";
    private static final String INTERNAL_PACKAGE = LIBRARY_PACKAGE + ".internal.";

    // FXML has to build these two from a constructor. A card's position in the grid can
    // only be requested there. A range slider's two values clamp each other as they are
    // set, so only the constructor gives the same range whatever order the attributes
    // are written in.
    private static final Set<Class<?>> BUILT_FROM_A_CONSTRUCTOR =
            Set.of(RXDashboardCard.class, RXRangeSlider.class);

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
        Platform.setImplicitExit(false);
    }

    // ==================== Every node ====================

    /** Verifies a style class attribute with two names gives every node two style classes. */
    @TestFactory
    public Stream<DynamicTest> styleClassAttributeIsSplitAtCommas() throws IOException, URISyntaxException {
        List<Class<? extends Node>> nodes = findNodes();
        // Guards the scan itself: an empty result would pass vacuously.
        assertTrue(nodes.contains(RXButton.class), "node scan found " + nodes);
        return nodes.stream()
                .filter(type -> !BUILT_FROM_A_CONSTRUCTOR.contains(type))
                .map(type -> DynamicTest.dynamicTest(type.getSimpleName(), () -> runOnFx(() -> {
                    Node node = load("<%s styleClass=\"one, two\"/>".formatted(type.getSimpleName()), type);
                    assertTrue(node.getStyleClass().containsAll(List.of("one", "two")),
                            "style classes " + node.getStyleClass());
                })));
    }

    /** Verifies text written inside the tag becomes the text of every labeled node. */
    @TestFactory
    public Stream<DynamicTest> labeledTextCanBeWrittenInsideTheTag() throws IOException, URISyntaxException {
        List<Class<? extends Node>> labeled = findNodes().stream()
                .filter(Labeled.class::isAssignableFrom)
                .toList();
        assertTrue(labeled.contains(RXButton.class), "labeled scan found " + labeled);
        return labeled.stream()
                .map(type -> DynamicTest.dynamicTest(type.getSimpleName(), () -> runOnFx(() -> {
                    Labeled node = load("<%1$s>Save</%1$s>".formatted(type.getSimpleName()), type);
                    assertEquals("Save", node.getText());
                })));
    }

    // ==================== Lists ====================

    /** Verifies a list written as child elements keeps every element. */
    @Test
    public void listElementKeepsEveryItem() throws InterruptedException {
        runOnFx(() -> {
            RXMenuButton menu = load("""
                    <RXMenuButton>
                        <items>
                            <RXMenuItem text="Open"/>
                            <RXMenuItem text="Close"/>
                        </items>
                    </RXMenuButton>
                    """, RXMenuButton.class, RXMenuItem.class);
            assertEquals(List.of("Open", "Close"),
                    menu.getItems().stream().map(RXMenuItem::getText).toList());

            RXCascaderItem<?> item = load("""
                    <RXCascaderItem value="Asia">
                        <children>
                            <RXCascaderItem value="China"/>
                            <RXCascaderItem value="Japan"/>
                        </children>
                    </RXCascaderItem>
                    """, RXCascaderItem.class);
            assertEquals(List.of("China", "Japan"),
                    item.getChildren().stream().map(child -> String.valueOf(child.getValue())).toList());
        });
    }

    // ==================== Helpers ====================

    private static <T> T load(String body, Class<?>... imports) {
        StringBuilder fxml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        for (Class<?> type : imports) {
            fxml.append("<?import ").append(type.getName()).append("?>\n");
        }
        fxml.append(body);
        try {
            return new FXMLLoader().load(
                    new ByteArrayInputStream(fxml.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Every public, concrete node an application can write as an FXML tag.
    private static List<Class<? extends Node>> findNodes() throws IOException, URISyntaxException {
        Path classesRoot = Paths.get(RXButton.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packageRoot = classesRoot.resolve(LIBRARY_PACKAGE.replace('.', File.separatorChar));
        List<String> classNames = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageRoot)) {
            files.map(path -> classesRoot.relativize(path).toString())
                    .filter(name -> name.endsWith(".class") && !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length())
                            .replace(File.separatorChar, '.'))
                    .filter(name -> !name.startsWith(INTERNAL_PACKAGE))
                    .forEach(classNames::add);
        }

        List<Class<? extends Node>> nodes = new ArrayList<>();
        ClassLoader loader = FxmlLoadingTest.class.getClassLoader();
        for (String className : classNames) {
            Class<?> type;
            try {
                type = Class.forName(className, false, loader);
            } catch (ClassNotFoundException ex) {
                throw new AssertionError("could not load " + className, ex);
            }
            int modifiers = type.getModifiers();
            if (Node.class.isAssignableFrom(type) && Modifier.isPublic(modifiers)
                    && !Modifier.isAbstract(modifiers) && hasNoArgConstructor(type)) {
                nodes.add(type.asSubclass(Node.class));
            }
        }
        nodes.sort(Comparator.comparing(Class::getSimpleName));
        return nodes;
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    private static void runOnFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        Throwable t = failure.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }
}
