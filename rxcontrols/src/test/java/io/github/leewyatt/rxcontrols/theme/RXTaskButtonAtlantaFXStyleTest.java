package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXTaskButton;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests the AtlantaFX rules of {@link RXTaskButton} under a real Primer theme:
 * a result state turns the button into the success or danger variant of its
 * own kind, in every interaction state, and the stop mark stays visible on
 * every body. The colors resolve in every theme.
 */
public class RXTaskButtonAtlantaFXStyleTest {

    private static final Duration LONG = Duration.seconds(30);
    private static final List<String> INTERACTIONS = List.of("", "hover", "focused", "armed", "hover+focused");
    private static final List<Theme> THEMES = List.of(new PrimerLight(), new PrimerDark(), new NordLight(),
            new NordDark(), new CupertinoLight(), new CupertinoDark(), new Dracula());

    /**
     * Starts the JavaFX toolkit and installs the AtlantaFX theme.
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
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }

    /** Restores the shared-JVM default so later CSS tests that expect Modena pass. */
    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Text buttons ====================

    @Test
    public void plainButtonColorsItsTextInEveryInteractionState() throws InterruptedException {
        onFx(() -> {
            Fixture fixture = new Fixture();
            Paint idle = fixture.button.getTextFill();
            assertNotEquals(fixture.color("-color-success-fg"), idle);
            List<Paint> idleBody = fixture.body();

            fixture.succeed();
            for (String interaction : INTERACTIONS) {
                fixture.interact(interaction);
                assertEquals(fixture.color("-color-success-fg"), fixture.button.getTextFill(), interaction);
            }
            fixture.interact("");
            assertEquals(idleBody, fixture.body());

            fixture.fail();
            for (String interaction : INTERACTIONS) {
                fixture.interact(interaction);
                assertEquals(fixture.color("-color-danger-fg"), fixture.button.getTextFill(), interaction);
            }

            fixture.interact("");
            fixture.reset();
            assertEquals(idle, fixture.button.getTextFill());
        });
    }

    @Test
    public void outlinedAndFlatAccentButtonsColorTheirTextAndKeepTheirBody() throws InterruptedException {
        onFx(() -> {
            for (String result : List.of("success", "danger")) {
                Fixture outlined = new Fixture("accent", "button-outlined");
                outlined.end(result);
                assertEquals(outlined.color("-color-" + result + "-fg"), outlined.button.getTextFill(), result);
                assertEquals(outlined.color("-color-bg-default"), outlined.inside(), result);
                assertEquals(outlined.color("-color-" + result + "-emphasis"), outlined.edge(), result);
                // Hovered, an outlined color button fills up, now with the result color.
                outlined.interact("hover");
                assertEquals(outlined.color("-color-" + result + "-emphasis"), outlined.inside(), result);
                assertEquals(outlined.color("-color-fg-emphasis"), outlined.button.getTextFill(), result);

                Fixture flat = new Fixture("accent", "flat");
                flat.end(result);
                assertEquals(flat.color("-color-" + result + "-fg"), flat.button.getTextFill(), result);
                assertEquals(Color.TRANSPARENT, flat.inside(), result);
                assertEquals(Color.TRANSPARENT, flat.edge(), result);
                flat.interact("hover");
                assertEquals(flat.color("-color-" + result + "-subtle"), flat.inside(), result);
                assertEquals(flat.color("-color-" + result + "-fg"), flat.button.getTextFill(), result);
                flat.interact("focused");
                assertEquals(Color.TRANSPARENT, flat.inside(), result);
                assertEquals(Color.TRANSPARENT, flat.edge(), result);
            }
        });
    }

    // ==================== Filled buttons ====================

    @Test
    public void filledButtonsCarryTheResultOnTheirBody() throws InterruptedException {
        onFx(() -> {
            for (String variant : List.of("accent", "success", "danger", ":default")) {
                Fixture fixture = variant.startsWith(":") ? new Fixture() : new Fixture(variant);
                if (variant.startsWith(":")) {
                    fixture.button.setDefaultButton(true);
                    fixture.refresh();
                }
                Paint idleInside = fixture.inside();

                for (String result : List.of("success", "danger")) {
                    String where = variant + " " + result;
                    fixture.end(result);
                    for (String interaction : INTERACTIONS) {
                        fixture.interact(interaction);
                        assertEquals(fixture.color("-color-fg-emphasis"), fixture.button.getTextFill(),
                                where + " " + interaction);
                        // A pressed AtlantaFX button drops its edge, in every variant.
                        Paint edge = interaction.equals("armed")
                                ? Color.TRANSPARENT : fixture.color("-color-" + result + "-emphasis");
                        assertEquals(edge, fixture.edge(), where + " " + interaction);
                    }
                    fixture.interact("");
                    assertEquals(fixture.color("-color-" + result + "-emphasis"), fixture.inside(), where);
                    fixture.interact("hover");
                    assertEquals(fixture.color("-color-" + result + "-emphasis"), fixture.inside(), where);
                    fixture.interact("armed");
                    assertEquals(fixture.color("-color-" + result + "-emphasis"), fixture.inside(), where);
                    // Focus still shows: the body darkens inside its edge.
                    fixture.interact("focused");
                    assertEquals(fixture.color("-color-" + result + "-emphasis"), fixture.edge(), where);
                    assertNotEquals(fixture.edge(), fixture.inside(), where);
                    assertNotEquals(idleInside, fixture.inside(), where);
                    fixture.interact("");
                }

                fixture.reset();
                assertEquals(idleInside, fixture.inside(), variant);
            }
        });
    }

    // ==================== Stop mark ====================

    @Test
    public void stopMarkIsRedOnAPlainBodyAndTakesTheTextColorOnAColoredOne() throws InterruptedException {
        onFx(() -> {
            List<List<String>> plainBodies = List.of(List.of(), List.of("button-outlined"), List.of("flat"),
                    List.of("accent", "button-outlined"), List.of("danger", "flat"));
            for (List<String> variant : plainBodies) {
                Fixture fixture = new Fixture(variant.toArray(new String[0]));
                fixture.pendCancellable();
                assertEquals(fixture.color("-color-danger-emphasis"), fixture.stopFill(), variant.toString());
            }

            for (String variant : List.of("accent", "success", "danger", ":default")) {
                Fixture fixture = variant.startsWith(":") ? new Fixture() : new Fixture(variant);
                fixture.button.setDefaultButton(variant.startsWith(":"));
                fixture.pendCancellable();
                for (String interaction : INTERACTIONS) {
                    fixture.interact(interaction);
                    assertEquals(fixture.button.getTextFill(), fixture.stopFill(), variant + " " + interaction);
                }
            }

            // An outlined color button fills up under the mouse; the mark follows its text then.
            for (String color : List.of("accent", "success", "danger")) {
                Fixture fixture = new Fixture(color, "button-outlined");
                fixture.pendCancellable();
                fixture.interact("hover");
                assertEquals(fixture.color("-color-fg-emphasis"), fixture.stopFill(), color);
                assertEquals(fixture.button.getTextFill(), fixture.stopFill(), color);
            }
        });
    }

    // ==================== Lookups ====================

    @Test
    public void everyVariantResolvesItsColorsInEveryStateOfEveryTheme() throws InterruptedException {
        CollectingHandler handler = new CollectingHandler();
        handler.setLevel(Level.ALL);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(handler);
        try {
            onFx(() -> {
                List<List<String>> variants = List.of(List.of(), List.of("button-outlined"), List.of("flat"),
                        List.of("accent"), List.of("accent", "button-outlined"), List.of("accent", "flat"),
                        List.of("success"), List.of("danger"));
                try {
                    for (Theme theme : THEMES) {
                        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
                        for (List<String> variant : variants) {
                            for (boolean defaultButton : new boolean[] {false, true}) {
                                Fixture fixture = new Fixture(variant.toArray(new String[0]));
                                fixture.button.setDefaultButton(defaultButton);
                                fixture.pendCancellable();
                                for (String interaction : INTERACTIONS) {
                                    fixture.interact(interaction);
                                }
                                fixture.succeed();
                                for (String interaction : INTERACTIONS) {
                                    fixture.interact(interaction);
                                }
                                fixture.fail();
                                for (String interaction : INTERACTIONS) {
                                    fixture.interact(interaction);
                                }
                                fixture.reset();
                            }
                        }
                    }
                } finally {
                    // The other tests of this class read the colors of this theme.
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                }
            });
        } finally {
            rootLogger.removeHandler(handler);
        }

        List<String> failures = new ArrayList<>();
        for (String message : handler.messages) {
            if (message.contains("while converting value for")
                    || message.contains("while resolving lookups for")
                    || message.contains("Could not resolve")) {
                failures.add(message);
            }
        }
        assertEquals(List.of(), failures);
    }

    // ==================== Helpers ====================

    /** A task button in a bridged scene, next to a probe that resolves theme colors. */
    private static final class Fixture {

        final RXTaskButton button = new RXTaskButton("Save");
        final Region probe = new Region();
        final StackPane root = new StackPane(button, probe);

        Fixture(String... styleClasses) {
            button.getStyleClass().addAll(styleClasses);
            button.setPendingDelay(Duration.ZERO);
            button.setPendingMinDuration(Duration.ZERO);
            button.setSucceededDuration(LONG);
            button.setFailedDuration(LONG);
            RXAtlantaFXThemeBridge.install(new Scene(root, 300.0, 120.0));
            refresh();
        }

        void refresh() {
            root.applyCss();
        }

        void succeed() {
            button.start().succeed();
            refresh();
        }

        void fail() {
            button.start().fail();
            refresh();
        }

        /** Ends a run with the result named like its theme color: "success" or "danger". */
        void end(String result) {
            if (result.equals("success")) {
                succeed();
            } else {
                fail();
            }
        }

        void reset() {
            button.resetFeedback();
            refresh();
        }

        void pendCancellable() {
            button.setCancellable(true);
            button.start();
            refresh();
        }

        Paint stopFill() {
            Region stop = (Region) button.lookup(".stop");
            return stop.getBackground().getFills().get(0).getFill();
        }

        void interact(String states) {
            for (String each : List.of("hover", "focused", "armed")) {
                button.pseudoClassStateChanged(PseudoClass.getPseudoClass(each),
                        List.of(states.split("\\+")).contains(each));
            }
            refresh();
        }

        Paint color(String token) {
            probe.setStyle("-fx-background-color: " + token + ";");
            refresh();
            return probe.getBackground().getFills().get(0).getFill();
        }

        List<Paint> body() {
            return button.getBackground().getFills().stream().map(BackgroundFill::getFill).toList();
        }

        // AtlantaFX paints a button as an edge fill with the body inset over it.
        Paint edge() {
            return body().get(0);
        }

        Paint inside() {
            return body().get(1);
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
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
