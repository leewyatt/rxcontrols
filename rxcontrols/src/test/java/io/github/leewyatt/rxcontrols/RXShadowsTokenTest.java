package io.github.leewyatt.rxcontrols;

import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the RXShadows elevation token added to the {@code rx-controls.css}
 * baseline and its dark / AtlantaFX re-points. {@code -rx-elevation-umbra-color}
 * must resolve to a {@link Color} under each theme — in JFX17 an unresolved
 * looked-up color falls through to a {@code String}, which surfaces as a
 * {@code ClassCastException String -> Paint} the moment it is used as a dropshadow
 * color (the failure mode design doc §A.8 warns about). The light and dark literals
 * are asserted exactly; the AtlantaFX mapping ({@code -color-shadow-default}) is
 * asserted to resolve to a non-null color (its exact value is AtlantaFX's to own).
 *
 * <p>Each test sets the Application user-agent stylesheet it needs, because that
 * setting is global: light / dark resolve under Modena (the dark overlay is
 * self-contained literals), the AtlantaFX mapping needs a real AtlantaFX UA so
 * {@code -color-shadow-default} resolves.</p>
 */
public class RXShadowsTokenTest {

    /**
     * Starts the JavaFX toolkit. The user-agent stylesheet is set per test, not
     * here, because the three themes need different ones.
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
    }

    /**
     * Restores the global user-agent stylesheet to Modena so this test's AtlantaFX
     * UA switch does not leak into other test classes (a lingering AtlantaFX UA would
     * make any {@code -rx-*}-themed node fail to resolve {@code -fx-*} aliases).
     */
    @AfterAll
    public static void restoreUserAgentStylesheet() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    /**
     * Light baseline: the umbra resolves to the documented translucent black.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void umbraColorResolvesUnderLight() throws Exception {
        Color umbra = resolveUmbra(Application.STYLESHEET_MODENA, RXResources.USER_AGENT_STYLESHEET);
        assertEquals(Color.rgb(0, 0, 0, 0.2), umbra,
                "-rx-elevation-umbra-color should resolve to rgba(0, 0, 0, 0.2) under the light baseline");
    }

    /**
     * Dark overlay: the umbra stays a (slightly deeper) translucent black, not a
     * glow. The dark sheet is self-contained, so Modena as the UA is fine.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void umbraColorResolvesUnderDark() throws Exception {
        Color umbra = resolveUmbra(Application.STYLESHEET_MODENA, RXResources.DARK_OVERLAY_STYLESHEET);
        assertEquals(Color.rgb(0, 0, 0, 0.4), umbra,
                "-rx-elevation-umbra-color should resolve to rgba(0, 0, 0, 0.4) under the dark overlay");
    }

    /**
     * AtlantaFX bridge: the umbra maps to {@code -color-shadow-default}, which must
     * resolve to a real color against an AtlantaFX UA (catches a wrong token name /
     * String fall-through in the mapping).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void umbraColorResolvesUnderAtlantaFX() throws Exception {
        Color umbra = resolveUmbra(new PrimerLight().getUserAgentStylesheet(),
                RXResources.ATLANTAFX_BRIDGE_STYLESHEET);
        assertNotNull(umbra,
                "-rx-elevation-umbra-color should resolve to a color via -color-shadow-default under AtlantaFX");
        assertTrue(umbra.getOpacity() > 0.0,
                "the AtlantaFX shadow color should be at least partly opaque");
    }

    /**
     * Resolves {@code -rx-elevation-umbra-color} into a real background fill on a
     * control-root-classed node under the given UA + token stylesheet, forcing the
     * looked-up resolution.
     */
    private static Color resolveUmbra(String userAgentStylesheet, String tokenStylesheet) throws Exception {
        AtomicReference<Color> umbraRef = new AtomicReference<>();
        runOnFx(() -> {
            Application.setUserAgentStylesheet(userAgentStylesheet);
            Region probe = new Region();
            probe.getStyleClass().add("rx-button");
            probe.setStyle("-fx-background-color: -rx-elevation-umbra-color;");
            StackPane root = new StackPane(probe);
            Scene scene = new Scene(root, 50, 50);
            scene.getStylesheets().add(tokenStylesheet);
            root.applyCss();
            root.layout();

            assertNotNull(probe.getBackground(), "umbra token did not resolve into a background");
            umbraRef.set((Color) probe.getBackground().getFills().get(0).getFill());
        });
        return umbraRef.get();
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
}
