package io.github.leewyatt.rxcontrols.utils;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RXSizing#lockToPrefSize(Region)}: the locked region keeps its
 * CSS preferred size when a parent squeezes or stretches it, and a min size from
 * a user agent style sheet still replaces the lock.
 */
public class RXSizingTest {

    private static final double EPSILON = 1.0e-6;
    private static final String PREF_CSS = "-fx-pref-width: 12px; -fx-pref-height: 8px;";

    /**
     * Starts the JavaFX toolkit so CSS and layout can run.
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

    // ==================== Lock ====================

    @Test
    public void locksMinAndMaxToPrefAndReturnsTheRegion() {
        Region region = new Region();
        assertSame(region, RXSizing.lockToPrefSize(region));
        assertEquals(Region.USE_PREF_SIZE, region.getMinWidth());
        assertEquals(Region.USE_PREF_SIZE, region.getMinHeight());
        assertEquals(Region.USE_PREF_SIZE, region.getMaxWidth());
        assertEquals(Region.USE_PREF_SIZE, region.getMaxHeight());
    }

    @Test
    public void rejectsNull() {
        assertThrows(NullPointerException.class, () -> RXSizing.lockToPrefSize(null));
    }

    @Test
    public void computedSizesFollowTheCssPrefSize() throws Exception {
        runOnFx(() -> {
            Region region = RXSizing.lockToPrefSize(new Region());
            region.setStyle(PREF_CSS);
            Parent root = new StackPane(region);
            new Scene(root, 100, 100);
            root.applyCss();
            assertEquals(12.0, region.minWidth(-1), EPSILON);
            assertEquals(12.0, region.prefWidth(-1), EPSILON);
            assertEquals(12.0, region.maxWidth(-1), EPSILON);
            assertEquals(8.0, region.minHeight(-1), EPSILON);
            assertEquals(8.0, region.maxHeight(-1), EPSILON);
        });
    }

    // ==================== Parents ====================

    @Test
    public void aCrowdedRowDoesNotSqueezeIt() throws Exception {
        runOnFx(() -> {
            Region region = RXSizing.lockToPrefSize(new Region());
            region.setStyle(PREF_CSS);
            Region wide = new Region();
            wide.setPrefWidth(200.0);
            HBox row = new HBox(wide, region);
            row.setMaxWidth(Region.USE_PREF_SIZE);
            row.setPrefWidth(30.0);
            Parent root = new StackPane(row);
            new Scene(root, 300, 100);
            root.applyCss();
            root.layout();
            assertEquals(12.0, region.getWidth(), EPSILON);
        });
    }

    @Test
    public void aStackPaneDoesNotStretchIt() throws Exception {
        runOnFx(() -> {
            Region region = RXSizing.lockToPrefSize(new Region());
            region.setStyle(PREF_CSS);
            Parent root = new StackPane(region);
            new Scene(root, 100, 100);
            root.applyCss();
            root.layout();
            assertEquals(12.0, region.getWidth(), EPSILON);
            assertEquals(8.0, region.getHeight(), EPSILON);
        });
    }

    // ==================== Style Sheets ====================

    @Test
    public void aUserAgentStyleSheetMinSizeReplacesTheLock() throws Exception {
        Path css = Files.createTempFile("rx-sizing", ".css");
        try {
            Files.writeString(css, ".locked { -fx-pref-width: 12px; -fx-min-width: 20px; }");
            String url = css.toUri().toString();
            runOnFx(() -> {
                // The size properties are created lazily; reading them here would
                // change what this test checks, so only getters are used.
                Region region = RXSizing.lockToPrefSize(new Region());
                region.getStyleClass().add("locked");
                StackPane host = new StackPane(region) {
                    @Override
                    public String getUserAgentStylesheet() {
                        return url;
                    }
                };
                new Scene(host, 100, 100);
                host.applyCss();
                assertEquals(20.0, region.getMinWidth(), EPSILON);
                assertEquals(Region.USE_PREF_SIZE, region.getMaxWidth());
            });
        } finally {
            Files.deleteIfExists(css);
        }
    }

    // ==================== Helpers ====================

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable throwable = error.get();
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable instanceof Error failure) {
            throw failure;
        }
    }
}
