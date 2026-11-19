package io.github.leewyatt.rxcontrols.samples.support;

import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFXThemeBridge;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that one theme holds across all sample windows: a window opened later, or a theme
 * picked in another window, never leaves a window with a stylesheet its bridge state does
 * not match.
 */
public class ShowcaseThemesTest {

    private static final String SAMPLER_CSS = "/io/github/leewyatt/rxcontrols/samples/sampler/rx-sampler.css";
    private static final ThemeChoice MODENA = ShowcaseThemes.all().get(0);
    private static final ThemeChoice PRIMER_DARK = ShowcaseThemes.all().get(2);
    private static final ThemeChoice NORD_LIGHT = ShowcaseThemes.all().get(3);

    private final List<String> cssWarnings = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                cssWarnings.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

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
    }

    @AfterEach
    public void resetTheme() throws Exception {
        runOnFx(() -> ShowcaseThemes.apply(MODENA));
        Logger.getLogger("javafx.css").removeHandler(handler);
    }

    /** A window opened after an AtlantaFX theme was picked joins it and leaves the first window intact. */
    @Test
    public void windowOpenedLaterKeepsTheCurrentTheme() throws Exception {
        runOnFx(() -> {
            Window first = new Window();
            first.picker.setValue(PRIMER_DARK);
            Window second = new Window();

            assertSame(PRIMER_DARK, second.picker.getValue());
            assertEquals(PRIMER_DARK.userAgentStylesheet(), Application.getUserAgentStylesheet());
            assertTrue(first.bridged());
            assertTrue(second.bridged());
            assertNoCssWarnings(first, second);
        });
    }

    /** A theme picked in a later window is applied to the earlier window and shown in its picker. */
    @Test
    public void themePickedInAnotherWindowReachesEveryWindow() throws Exception {
        runOnFx(() -> {
            Window first = new Window();
            Window second = new Window();
            second.picker.setValue(NORD_LIGHT);

            assertSame(NORD_LIGHT, first.picker.getValue());
            assertTrue(first.bridged());
            assertNoCssWarnings(first, second);

            first.picker.setValue(MODENA);
            assertSame(MODENA, second.picker.getValue());
            assertFalse(first.bridged());
            assertFalse(second.bridged());
            assertNoCssWarnings(first, second);
        });
    }

    /** A window with the bridge but a Modena stylesheet logs warnings, so the checks above can fail. */
    @Test
    public void mismatchedBridgeIsDetected() throws Exception {
        runOnFx(() -> {
            Window window = new Window();
            RXAtlantaFXThemeBridge.install(window.scene);
            startCapture();
            window.scene.getRoot().applyCss();
            assertFalse(cssWarnings.isEmpty());
        });
    }

    private void assertNoCssWarnings(Window... windows) {
        startCapture();
        for (Window window : windows) {
            window.scene.getRoot().applyCss();
        }
        assertEquals(List.of(), cssWarnings);
    }

    private void startCapture() {
        cssWarnings.clear();
        Logger logger = Logger.getLogger("javafx.css");
        logger.removeHandler(handler);
        logger.addHandler(handler);
    }

    // A sample window: the sampler stylesheets, a list using the RX tokens, and a picker.
    private static final class Window {

        final ComboBox<ThemeChoice> picker = new ComboBox<>();
        final Scene scene;

        Window() {
            ShowcaseThemes.bindPicker(picker);
            Label name = new Label("RXSlider");
            name.getStyleClass().add("brand-name");
            RXListView<String> list = new RXListView<>(FXCollections.observableArrayList("one", "two"));
            VBox root = new VBox(picker, name, list);
            root.getStyleClass().add("sampler");
            scene = new Scene(root, 400, 300);
            scene.getStylesheets().addAll(RXShowcaseApplication.shellStylesheet(),
                    ShowcaseThemesTest.class.getResource(SAMPLER_CSS).toExternalForm());
            root.applyCss();
            root.layout();
        }

        boolean bridged() {
            return scene.getRoot().getStyleClass().contains(RXAtlantaFXThemeBridge.getScopeClass());
        }
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
