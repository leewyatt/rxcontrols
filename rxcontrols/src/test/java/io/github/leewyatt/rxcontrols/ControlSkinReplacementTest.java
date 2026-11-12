package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replaces the default skin of every control in the library with a skin of
 * another class and checks that the new skin's nodes survive. A new skin is
 * constructed, and adds its nodes to the control, before the old skin is
 * disposed; an old skin that clears all of the control's children on dispose
 * removes the new skin's nodes too.
 *
 * <p>Controls are found by scanning the compiled library classes outside the
 * internal package, so a new control is covered without editing this
 * test.</p>
 */
public class ControlSkinReplacementTest {

    private static final String CONTROL_PACKAGE = "io.github.leewyatt.rxcontrols";
    // Not exported: its controls are skin parts that users never reskin.
    private static final String INTERNAL_PACKAGE = CONTROL_PACKAGE + ".internal.";

    // Controls without a public no-argument constructor. A new one fails the
    // test until it is added here.
    private static final Map<Class<?>, Supplier<Control>> FACTORIES = Map.of(
            RXCascaderCell.class, () -> new RXCascaderCell<>(new RXCascaderView<>()),
            RXSidebarActionItem.class, () -> new RXSidebarActionItem("Item"),
            RXSidebarNavItem.class, () -> new RXSidebarNavItem("Item"));

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    @TestFactory
    public Stream<DynamicTest> replacementSkinKeepsItsNodes() throws IOException, URISyntaxException {
        List<Class<? extends Control>> controls = findControls();
        // Guards the scan itself: an empty result would pass vacuously.
        assertTrue(controls.contains(RXButton.class), "control scan found " + controls);
        return controls.stream().map(type -> DynamicTest.dynamicTest(type.getSimpleName(),
                () -> runOnFx(() -> assertReplacementSkinKeepsItsNodes(type))));
    }

    private static void assertReplacementSkinKeepsItsNodes(Class<? extends Control> type) {
        Control control = create(type);
        StackPane root = new StackPane(control);
        new Scene(root, 320.0, 200.0);
        root.applyCss();
        root.layout();
        String name = type.getSimpleName();
        assertNotNull(control.getSkin(), name + ": no default skin");

        Rectangle marker = new Rectangle(4.0, 4.0);
        control.setSkin(new SkinBase<>(control) {
            {
                getChildren().add(marker);
            }
        });

        assertTrue(control.getChildrenUnmodifiable().contains(marker),
                name + ": disposing the default skin removed the replacement skin's node");
        control.getSkin().dispose();
    }

    private static Control create(Class<? extends Control> type) {
        Supplier<Control> factory = FACTORIES.get(type);
        if (factory != null) {
            return factory.get();
        }
        try {
            Constructor<? extends Control> constructor = type.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(type.getSimpleName()
                    + " has no public no-argument constructor: add a factory to FACTORIES", ex);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not create " + type.getSimpleName(), ex);
        }
    }

    // Every public, concrete Control subclass that users can reach.
    private static List<Class<? extends Control>> findControls() throws IOException, URISyntaxException {
        Path classesRoot = Paths.get(RXButton.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packageRoot = classesRoot.resolve(CONTROL_PACKAGE.replace('.', File.separatorChar));
        List<String> classNames = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageRoot)) {
            files.map(path -> classesRoot.relativize(path).toString())
                    .filter(name -> name.endsWith(".class") && !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length())
                            .replace(File.separatorChar, '.'))
                    .filter(name -> !name.startsWith(INTERNAL_PACKAGE))
                    .forEach(classNames::add);
        }

        List<Class<? extends Control>> controls = new ArrayList<>();
        ClassLoader loader = ControlSkinReplacementTest.class.getClassLoader();
        for (String className : classNames) {
            Class<?> type;
            try {
                type = Class.forName(className, false, loader);
            } catch (ClassNotFoundException ex) {
                throw new AssertionError("could not load " + className, ex);
            }
            int modifiers = type.getModifiers();
            if (Control.class.isAssignableFrom(type)
                    && Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers)) {
                controls.add(type.asSubclass(Control.class));
            }
        }
        controls.sort(Comparator.comparing(Class::getSimpleName));
        return controls;
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
