package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes the coverage blind spot in {@code RxControlsThemeBaselineTest}: that guard
 * instantiates every control root via its no-arg constructor, so an empty
 * {@link RXTabPane} never creates {@code .tab} cells and the per-tab / close-button /
 * scroll-button CSS rules are never resolved at runtime. This test builds a fully
 * populated pane (text + icon + disabled tabs, closable, SCROLLABLE with visible
 * scroll buttons) and asserts no {@code -rx-*} lookup fails to resolve.
 */
public class RXTabPaneCssSmokeTest {

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
        // Pin modena so the -fx-* aliases the role tokens reference resolve.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void populatedTabPaneResolvesAllTokens() throws Exception {
        List<String> messages = captureCssMessages(() -> {
            RXTab icon = RXTab.of("Icon", new StackPane(), new Label("page"));
            RXTab disabled = new RXTab("Disabled");
            disabled.setDisable(true);
            RXTabPane pane = new RXTabPane(
                    RXTab.of("Overview", new Label("overview")),
                    icon,
                    disabled,
                    RXTab.of("Reports", new Label("reports")),
                    RXTab.of("Settings", new Label("settings")));
            // ALL_TABS renders every close button; SCROLLABLE + a narrow width forces
            // the scroll buttons (and their .arrow chevrons) to render too.
            pane.setTabClosingPolicy(RXTabPane.TabClosingPolicy.ALL_TABS);
            pane.setVariant(RXTabPane.Variant.SCROLLABLE);
            pane.setPrefWidth(200.0);
            pane.setMaxWidth(200.0);
            pane.getSelectionModel().select(0);

            StackPane root = new StackPane(pane);
            new Scene(root, 640, 400);
            root.applyCss();
            root.layout();
        });

        List<String> unresolved = messages.stream()
                .filter(RXTabPaneCssSmokeTest::isRxResolutionFailure)
                .collect(Collectors.toList());
        assertTrue(unresolved.isEmpty(),
                "Unresolved -rx-* lookups while applying CSS to a populated RXTabPane "
                        + "(a .tab / .close-button / .scroll-button rule references a token "
                        + "its node does not receive):\n" + String.join("\n", unresolved));
    }

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
