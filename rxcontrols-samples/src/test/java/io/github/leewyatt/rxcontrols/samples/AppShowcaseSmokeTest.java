package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.samples.demo.RXSpeedDialDemo;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSpeedDialShowcase;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke tests for AppShowcase wiring.
 */
public class AppShowcaseSmokeTest {

    /**
     * Starts the JavaFX toolkit so FXML can instantiate controls.
     *
     * @throws InterruptedException if startup is interrupted
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
     * Verifies the FXML sampler can build the embedded Speed Dial page.
     *
     * @throws Exception if loading or FX-thread execution fails
     */
    @Test
    public void mainFxmlCanInstantiateSpeedDialPage() throws Exception {
        runOnFx(() -> {
            Parent root = loadMainFxml();
            new Scene(root);
            root.applyCss();
            root.layout();

            RXCarousel carousel = findRequired(root, RXCarousel.class);
            assertEquals(13, carousel.getPageCount());
            carousel.goToPage(4, false);
            root.applyCss();
            root.layout();

            assertNotNull(findRequired(root, RXSpeedDial.class));
        });
    }

    /**
     * Verifies the standalone Speed Dial sample resources are packaged.
     */
    @Test
    public void speedDialSampleStylesheetsArePackaged() {
        assertNotNull(RXSpeedDialDemo.class.getResource("rx-speed-dial-demo.css"));
        assertNotNull(RXSpeedDialShowcase.class.getResource("rx-speed-dial-showcase.css"));
    }

    private static Parent loadMainFxml() {
        try {
            return new FXMLLoader(AppShowcase.class.getResource("/fxml/main.fxml")).load();
        } catch (Exception e) {
            throw new AssertionError("Unable to load main.fxml", e);
        }
    }

    private static <T> T findRequired(Node root, Class<T> type) {
        T found = find(root, type);
        if (found == null) {
            throw new AssertionError("Unable to find " + type.getSimpleName());
        }
        return found;
    }

    private static <T> T find(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
