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
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window-dependent acceptance for the dark overlay reaching the {@link RXCascader}
 * popup. A scene-level overlay installed before opening is snapshotted into the
 * popup scene, so the cascader view's background (raw {@code -fx-control-inner-background},
 * overridden to {@code -rx-surface} by the dark overlay's compat layer) renders dark.
 *
 * <p>Tagged {@code "ui"} (needs a shown {@link Stage}); excluded headless with
 * {@code -DexcludedGroups=ui}.
 */
@Tag("ui")
public class RXThemeDarkPopupTest {

    private static final Color DARK_SURFACE = Color.web("#1e1f2b");

    private Stage stage;

    /**
     * Starts the toolkit, keeps it alive across window hides, and pins Modena.
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
     * A scene-level dark overlay installed before opening reaches the popup: its
     * cascader view renders the dark surface instead of Modena's light one.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void darkOverlayReachesCascaderPopup() throws Exception {
        AtomicReference<Boolean> popupSceneHasOverlay = new AtomicReference<>();
        AtomicReference<Color> background = new AtomicReference<>();

        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("root"));
            StackPane root = new StackPane(cascader);
            stage = new Stage();
            stage.setScene(new Scene(root, 320, 200));
            stage.show();
            RXTheme.install(stage.getScene(), RXTheme.Variant.DARK);
            cascader.applyCss();
            cascader.layout();
            cascader.show();

            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup should be present");
            popupSceneHasOverlay.set(popup.getScene().getStylesheets()
                    .contains(RXTheme.class.getResource("/io/github/leewyatt/rxcontrols/theme/rx-controls-dark.css")
                            .toExternalForm()));
            background.set(cascaderViewBackground(popup));
        });

        assertTrue(popupSceneHasOverlay.get(),
                "the popup scene should snapshot the owner scene's dark overlay");
        assertEquals(DARK_SURFACE, background.get(),
                "cascader view inside the popup renders the dark surface via the compat override");
    }

    // ==================== Helpers ====================

    private static Color cascaderViewBackground(PopupControl popup) {
        if (popup.getScene() == null || popup.getScene().getRoot() == null) {
            return null;
        }
        popup.getScene().getRoot().applyCss();
        popup.getScene().getRoot().layout();
        Node node = popup.getScene().getRoot().lookup(".rx-cascader-view");
        if (!(node instanceof Region)) {
            return null;
        }
        Region view = (Region) node;
        if (view.getBackground() == null || view.getBackground().getFills().isEmpty()) {
            return null;
        }
        Paint fill = view.getBackground().getFills().get(0).getFill();
        return fill instanceof Color ? (Color) fill : null;
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
