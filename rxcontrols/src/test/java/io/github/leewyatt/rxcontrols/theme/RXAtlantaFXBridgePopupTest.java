package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.PrimerLight;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window-dependent acceptance for AtlantaFX-bridge propagation into the
 * {@link RXCascader} popup (proposal §7, states ① and ③). Two CSS paths reach a
 * popup: <strong>A</strong> — {@code PopupWindow.showImpl} snapshots the owner
 * scene's stylesheets at show time; <strong>B</strong> — the popup content's
 * {@code CSSBridge} collects stylesheets from the owner node's {@code Parent}
 * ancestor chain. The POC confirmed: a scene-level bridge reaches the popup (A);
 * a parent-level bridge reaches it only when that parent is on the cascader's
 * ancestor chain (B); a bridge on an unrelated parent does not.
 *
 * <p>The cascader view consumes no {@code -rx-*} role token directly, so a tiny
 * test-only probe stylesheet makes its border consume {@code -rx-primary}; the
 * border color then reports whether the bridge reached the popup. Both the bridge
 * and the probe are applied at the same level so they travel the same path.
 *
 * <p>Tagged {@code "ui"} (needs a shown {@link Stage}); a headless CI excludes it
 * with {@code -DexcludedGroups=ui}.
 */
@Tag("ui")
public class RXAtlantaFXBridgePopupTest {

    private static final Color ACCENT_EMPHASIS = Color.web("#0969da");

    /** Makes the cascader view border consume -rx-primary so the popup is measurable. */
    private static String probeUrl;

    private Stage stage;

    /**
     * Starts the toolkit, installs the AtlantaFX Application UA, and writes the
     * probe stylesheet.
     *
     * @throws Exception if startup or temp-file creation fails
     */
    @BeforeAll
    public static void setup() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        // Hiding the last real window must not shut the toolkit down for the rest
        // of the JVM fork (mirrors RXCascaderPopupTest).
        Platform.setImplicitExit(false);
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        Path sheet = Files.createTempFile("rx-cascader-view-probe", ".css");
        sheet.toFile().deleteOnExit();
        Files.writeString(sheet, ".rx-cascader-view { -fx-border-color: -rx-primary; }",
                StandardCharsets.UTF_8);
        probeUrl = sheet.toUri().toString();
    }

    /** Restores the shared-JVM default so later CSS tests that expect Modena pass. */
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

    // ==================== Path A — scene level ====================

    /**
     * A scene-level bridge installed before opening is snapshotted into the popup
     * scene, so the cascader view inside the popup follows AtlantaFX.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void popupFollowsSceneLevelBridge() throws Exception {
        AtomicReference<Boolean> popupSceneHasBridge = new AtomicReference<>();
        AtomicReference<Color> border = new AtomicReference<>();
        AtomicReference<Color> background = new AtomicReference<>();

        runOnFx(() -> {
            RXCascader<String> cascader = newCascader();
            StackPane root = new StackPane(cascader);
            stage = show(root);
            RXAtlantaFX.install(stage.getScene());
            stage.getScene().getStylesheets().add(probeUrl);
            cascader.applyCss();
            cascader.layout();
            cascader.show();

            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup should be present");
            popupSceneHasBridge.set(popup.getScene().getStylesheets()
                    .contains(RXAtlantaFX.getStylesheet()));
            border.set(cascaderViewBorder(popup));
            background.set(cascaderViewBackground(popup));
        });

        assertTrue(popupSceneHasBridge.get(),
                "path A: the popup scene should snapshot the owner scene's bridge stylesheet");
        assertEquals(ACCENT_EMPHASIS, border.get(),
                "path A: cascader view inside the popup follows AtlantaFX accent");
        // The view background uses raw -fx-control-inner-background; the bridge's
        // Modena compat layer must resolve it (it rendered transparent before).
        assertEquals(Color.web("#ffffff"), background.get(),
                "path A: cascader view background resolves via the bridge's Modena compat layer");
    }

    // ==================== Path B — parent level ====================

    /**
     * A parent-level bridge whose parent is on the cascader's ancestor chain
     * reaches the popup via the CSSBridge styleable-parent chain, even though the
     * popup scene did not snapshot it.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void popupFollowsAncestorParentBridge() throws Exception {
        AtomicReference<Boolean> popupSceneHasBridge = new AtomicReference<>();
        AtomicReference<Color> border = new AtomicReference<>();

        runOnFx(() -> {
            RXCascader<String> cascader = newCascader();
            StackPane ancestor = new StackPane(cascader);
            StackPane root = new StackPane(ancestor);
            stage = show(root);
            RXAtlantaFX.install(ancestor);
            ancestor.getStylesheets().add(probeUrl);
            cascader.applyCss();
            cascader.layout();
            cascader.show();

            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup should be present");
            popupSceneHasBridge.set(popup.getScene().getStylesheets()
                    .contains(RXAtlantaFX.getStylesheet()));
            border.set(cascaderViewBorder(popup));
        });

        assertFalse(popupSceneHasBridge.get(),
                "path B precondition: a parent-level bridge is not in the popup scene stylesheets");
        assertEquals(ACCENT_EMPHASIS, border.get(),
                "path B: an ancestor-parent bridge reaches the popup via the CSSBridge parent chain");
    }

    /**
     * A bridge on a parent that is NOT on the cascader's ancestor chain must not
     * reach the popup (neither path A nor B carries it).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void popupIgnoresNonAncestorParentBridge() throws Exception {
        AtomicReference<Color> border = new AtomicReference<>();

        runOnFx(() -> {
            RXCascader<String> cascader = newCascader();
            StackPane cascaderHolder = new StackPane(cascader);
            StackPane unrelated = new StackPane();
            StackPane root = new StackPane(cascaderHolder, unrelated);
            stage = show(root);
            RXAtlantaFX.install(unrelated);
            unrelated.getStylesheets().add(probeUrl);
            cascader.applyCss();
            cascader.layout();
            cascader.show();

            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup should be present");
            border.set(cascaderViewBorder(popup));
        });

        assertFalse(ACCENT_EMPHASIS.equals(border.get()),
                "a bridge on a non-ancestor parent must not theme the popup; got " + border.get());
    }

    // ==================== Helpers ====================

    private static RXCascader<String> newCascader() {
        RXCascader<String> cascader = new RXCascader<>();
        cascader.getRootItems().add(new RXCascaderItem<>("root"));
        return cascader;
    }

    private static Stage show(StackPane root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 320, 200));
        stage.show();
        return stage;
    }

    private static Color cascaderViewBorder(PopupControl popup) {
        Region view = cascaderView(popup);
        if (view == null || view.getBorder() == null || view.getBorder().getStrokes().isEmpty()) {
            return null;
        }
        Paint stroke = view.getBorder().getStrokes().get(0).getTopStroke();
        return stroke instanceof Color ? (Color) stroke : null;
    }

    private static Color cascaderViewBackground(PopupControl popup) {
        Region view = cascaderView(popup);
        if (view == null || view.getBackground() == null || view.getBackground().getFills().isEmpty()) {
            return null;
        }
        Paint fill = view.getBackground().getFills().get(0).getFill();
        return fill instanceof Color ? (Color) fill : null;
    }

    private static Region cascaderView(PopupControl popup) {
        if (popup.getScene() == null || popup.getScene().getRoot() == null) {
            return null;
        }
        popup.getScene().getRoot().applyCss();
        popup.getScene().getRoot().layout();
        Node node = popup.getScene().getRoot().lookup(".rx-cascader-view");
        return node instanceof Region ? (Region) node : null;
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
