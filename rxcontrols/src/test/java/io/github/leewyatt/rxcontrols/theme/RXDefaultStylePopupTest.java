package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A dark application Modena palette on the unqualified {@code .root} reaches the
 * {@link RXCascader} popup: the popup snapshots the owner scene's stylesheets when it
 * shows, and its own root matches {@code .root}. Tagged {@code "ui"} (needs a shown
 * {@link Stage}).
 */
@Tag("ui")
public class RXDefaultStylePopupTest {

    private static final String DARK_ROOT =
            RXDefaultStylePopupTest.class.getResource("app-dark-root.css").toExternalForm();

    private Stage stage;

    /**
     * Starts the toolkit and pins Modena.
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
        Platform.setImplicitExit(false);
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    /** Restores the shared-JVM default. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    /**
     * Hides any popup and the test stage so windows do not leak across tests.
     *
     * @throws Exception if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws Exception {
        runOnFx(() -> {
            PopupControl popup = findCascaderPopup();
            if (popup != null) {
                popup.hide();
            }
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * The cascader popup content follows a dark application palette set on the owner scene.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void cascaderPopupFollowsADarkApplicationPalette() throws Exception {
        AtomicReference<String> background = new AtomicReference<>();
        AtomicReference<String> separator = new AtomicReference<>();
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> parent = new RXCascaderItem<>("parent");
            parent.getChildren().add(new RXCascaderItem<>("child"));
            cascader.getRootItems().add(parent);
            Scene scene = new Scene(new StackPane(cascader), 320, 200);
            scene.getStylesheets().add(DARK_ROOT);
            stage = new Stage();
            stage.setScene(scene);
            stage.show();
            cascader.applyCss();
            cascader.layout();
            cascader.show();

            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup should be present");
            popup.getScene().getRoot().applyCss();
            popup.getScene().getRoot().layout();
            Region view = (Region) popup.getScene().getRoot().lookup(".rx-cascader-view");
            assertNotNull(view, "cascader view inside the popup");
            background.set(ThemeColorSamples.hex(view.getBackground().getFills().get(0).getFill()));
            Node column = view.lookup(".column");
            assertNotNull(column, "cascader column inside the popup");
            separator.set(ThemeColorSamples.hex(((Region) column).getBorder().getStrokes().get(0).getRightStroke()));
        });

        assertEquals("#2a2c3aff", background.get(), "popup view takes the palette's inner background");
        assertEquals("#4d5166ff", separator.get(), "popup column separator takes -rx-outline on the dark palette");
    }

    private static PopupControl findCascaderPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl) {
                PopupControl popup = (PopupControl) window;
                if (popup.getStyleClass().contains("rx-cascader-popup")) {
                    return popup;
                }
            }
        }
        return null;
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
        if (error instanceof AssertionError) {
            throw (AssertionError) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
