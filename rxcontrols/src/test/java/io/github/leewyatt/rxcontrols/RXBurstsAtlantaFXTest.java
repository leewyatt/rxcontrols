package io.github.leewyatt.rxcontrols;

import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFXThemeBridge;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The burst overlay under the AtlantaFX bridge takes AtlantaFX's accent, in
 * the main window and in a popup, whose overlay has no scoped ancestor.
 * Tagged {@code "ui"}: it shows a stage and a popup.
 */
@Tag("ui")
public class RXBurstsAtlantaFXTest {

    // Primer Light's -color-accent-emphasis.
    private static final Color ACCENT = Color.web("#0969da");
    private static final RXBurstOptions LONG = RXBurstOptions.builder()
            .duration(Duration.seconds(5.0))
            .build();

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
        onFx(() -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet()));
    }

    /** Restores the shared-JVM default so later CSS tests that expect Modena pass. */
    @AfterAll
    public static void restore() throws InterruptedException {
        onFx(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void theOverlayTakesTheAtlantaFXAccentInTheWindowAndInAPopup() throws Exception {
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Popup> popup = new AtomicReference<>();
        try {
            onFx(() -> {
                Region box = new Region();
                box.setPrefSize(40.0, 40.0);
                stage.set(new Stage());
                stage.get().setScene(new Scene(new Pane(box), 300.0, 200.0));
                RXAtlantaFXThemeBridge.install(stage.get().getScene());
                stage.get().show();
                RXBursts.play(box, LONG);
                assertEquals(ACCENT, burstColor(stage.get().getScene()), "in the window");

                Region inPopup = new Region();
                inPopup.setPrefSize(30.0, 30.0);
                popup.set(new Popup());
                popup.get().getContent().add(inPopup);
                popup.get().show(stage.get());
                RXBursts.play(inPopup, LONG);
                assertEquals(ACCENT, burstColor(popup.get().getScene()), "in a popup");
            });
        } finally {
            onFx(() -> {
                if (popup.get() != null) {
                    popup.get().hide();
                }
                if (stage.get() != null) {
                    stage.get().hide();
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static Paint burstColor(Scene scene) {
        Node layer = scene.getRoot().lookup(".rx-burst-layer");
        assertNotNull(layer, "burst overlay");
        CssMetaData<Styleable, Paint> metaData = (CssMetaData<Styleable, Paint>) ((Parent) layer).getCssMetaData()
                .stream()
                .filter(item -> item.getProperty().equals("-rx-burst-color"))
                .findFirst()
                .orElseThrow();
        return metaData.getStyleableProperty(layer).getValue();
    }

    private static void onFx(Runnable body) throws InterruptedException {
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
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
