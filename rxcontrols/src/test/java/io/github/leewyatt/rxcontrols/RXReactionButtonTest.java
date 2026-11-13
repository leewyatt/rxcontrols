package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.skins.RXToggleButtonSkin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXReactionButton}: defaults and CSS metadata, when the
 * burst plays and when it stays quiet, the pop transform, the burst canvas
 * geometry and lifetime, disposal and the themed icon colors.
 */
public class RXReactionButtonTest {

    private static final Duration SHORT_BURST = Duration.millis(120.0);

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
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ==================== Control API ====================

    @Test
    public void defaultsMatchContract() {
        RXReactionButton button = new RXReactionButton();

        assertTrue(button.getStyleClass().containsAll(List.of("rx-toggle-button", "rx-reaction-button")));
        assertSame(BurstAnimation.CLASSIC, button.getBurstAnimation());
        assertEquals(Duration.millis(800.0), button.getBurstDuration());
        assertNull(button.getBurstColor());
        assertNull(button.getBurstPalette());
        assertEquals(1.0, button.getBurstScale());
        assertTrue(button.isAnimated());
        assertFalse(button.isSelected());
        assertNull(button.getText());
        Region icon = assertInstanceOf(Region.class, button.getGraphic());
        assertTrue(icon.getStyleClass().contains("reaction-icon"));
    }

    @Test
    public void onlyTheTextlessAndTextConstructorsSupplyAnIcon() {
        RXReactionButton withText = new RXReactionButton("12");
        assertEquals("12", withText.getText());
        assertInstanceOf(Region.class, withText.getGraphic());
        assertNotSame(withText.getGraphic(), new RXReactionButton("12").getGraphic());

        assertNull(new RXReactionButton("12", null).getGraphic());
        Region custom = new Region();
        assertSame(custom, new RXReactionButton(null, custom).getGraphic());
    }

    @Test
    public void cssMetadataCoversTheBurstProperties() {
        RXReactionButton button = new RXReactionButton();
        List<CssMetaData<? extends Styleable, ?>> metaData = button.getCssMetaData();
        for (String property : List.of("-rx-burst-animation", "-rx-burst-duration", "-rx-burst-color",
                "-rx-burst-scale", "-rx-animated", "-rx-ripple-fill")) {
            assertTrue(metaData.stream().anyMatch(item -> item.getProperty().equals(property)), property);
        }
        assertTrue(metaData.stream().noneMatch(item -> item.getProperty().contains("palette")));

        CssMetaData<RXReactionButton, ?> animated = metaData(button, "-rx-animated");
        assertTrue(animated.isSettable(button));
        button.animatedProperty().bind(new SimpleBooleanProperty(false));
        assertFalse(animated.isSettable(button));
    }

    @Test
    public void cssKeywordsSelectPresetsAndNoneTurnsTheBurstOff() throws Exception {
        onFx(() -> {
            RXReactionButton button = laidOut(new RXReactionButton());
            button.setStyle("-rx-burst-animation: rays;");
            button.applyCss();
            assertSame(BurstAnimation.RAYS, button.getBurstAnimation());

            button.setStyle("-rx-burst-animation: no-such-burst;");
            button.applyCss();
            assertSame(BurstAnimation.CLASSIC, button.getBurstAnimation(), "a typo keeps the default burst");

            button.setStyle("-rx-burst-animation: none;");
            button.applyCss();
            assertNull(button.getBurstAnimation());

            button.setStyle("-rx-burst-animation: rays;");
            button.applyCss();
            button.setStyle("-rx-burst-animation: NONE;");
            button.applyCss();
            assertNull(button.getBurstAnimation(), "none in any case turns the burst off");
        });
    }

    // ==================== When the burst plays ====================

    @Test
    public void selectingByFireBurstsAndDeselectingDoesNot() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.fire();
            assertTrue(button.isSelected());
            assertTrue(burstLayer(button).isVisible(), "the user's selection bursts");

            button.fire();
            assertFalse(button.isSelected());
            assertFalse(burstLayer(button).isVisible(), "deselecting stops any burst and plays none");
            button.getSkin().dispose();
        });
    }

    @Test
    public void deselectingInAnyWayEndsTheBurst() throws Exception {
        onFx(() -> {
            RXReactionButton fromCode = longBurstButton();
            fromCode.fire();
            fromCode.setSelected(false);
            assertFalse(burstLayer(fromCode).isVisible(), "deselected from code");
            fromCode.getSkin().dispose();

            RXReactionButton grouped = longBurstButton();
            ToggleGroup group = new ToggleGroup();
            RXReactionButton other = new RXReactionButton();
            grouped.setToggleGroup(group);
            other.setToggleGroup(group);
            grouped.fire();
            other.setSelected(true);
            assertFalse(burstLayer(grouped).isVisible(), "deselected by its toggle group");
            grouped.getSkin().dispose();

            RXReactionButton rolledBack = longBurstButton();
            rolledBack.setOnAction(event -> rolledBack.setSelected(false));
            rolledBack.fire();
            assertFalse(burstLayer(rolledBack).isVisible(), "rolled back in onAction");
            rolledBack.getSkin().dispose();
        });
    }

    @Test
    public void onlyTheButtonsOwnEventsCount() throws Exception {
        onFx(() -> {
            Button nested = new Button("nested");
            RXReactionButton button = new RXReactionButton(null, nested);
            button.setBurstDuration(SHORT_BURST);
            laidOut(button);
            nested.fire();
            assertFalse(burstLayer(button).isVisible(), "an action from inside the graphic is not a reaction");
            assertTrue(scales(nested).isEmpty());

            AtomicInteger bubbled = new AtomicInteger();
            button.getParent().addEventHandler(RXAnimationEvent.PLAY_ANIMATION, event -> bubbled.incrementAndGet());
            button.playBurst();
            assertTrue(burstLayer(button).isVisible());
            assertEquals(0, bubbled.get(), "the play request is consumed by the button");
            button.getSkin().dispose();
        });
    }

    @Test
    public void selectingFromCodeIsQuiet() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.setSelected(true);
            assertFalse(burstLayer(button).isVisible());
            assertTrue(scales(button.getGraphic()).isEmpty(), "no pop either");
            button.getSkin().dispose();
        });
    }

    @Test
    public void playBurstWorksUnselectedAndDisabled() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.setDisable(true);
            button.playBurst();
            assertTrue(burstLayer(button).isVisible());
            assertFalse(button.isSelected(), "playBurst does not select");
            button.getSkin().dispose();
        });
    }

    @Test
    public void nothingPlaysWithoutAnimationOrABurst() throws Exception {
        onFx(() -> {
            RXReactionButton notAnimated = quickButton();
            notAnimated.setAnimated(false);
            notAnimated.fire();
            notAnimated.playBurst();
            assertFalse(burstLayer(notAnimated).isVisible());
            assertTrue(scales(notAnimated.getGraphic()).isEmpty());
            notAnimated.getSkin().dispose();

            RXReactionButton noBurst = quickButton();
            noBurst.setBurstAnimation(null);
            noBurst.fire();
            assertFalse(burstLayer(noBurst).isVisible());
            assertFalse(scales(noBurst.getGraphic()).isEmpty(), "the pop still plays without a burst");
            noBurst.getSkin().dispose();

            RXReactionButton zero = quickButton();
            zero.setBurstDuration(Duration.ZERO);
            zero.playBurst();
            assertFalse(burstLayer(zero).isVisible());
            zero.getSkin().dispose();
        });
    }

    @Test
    public void turningAnimationOffStopsARunningBurst() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.setBurstDuration(Duration.seconds(5.0));
            button.fire();
            assertTrue(burstLayer(button).isVisible());
            button.setAnimated(false);
            assertFalse(burstLayer(button).isVisible());
            assertTrue(scales(button.getGraphic()).isEmpty());
            button.getSkin().dispose();
        });
    }

    // ==================== Burst lifetime and geometry ====================

    @Test
    public void theBurstHidesAndThePopDetachesWhenDone() throws Exception {
        AtomicReference<RXReactionButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.getGraphic().setScaleX(2.0);
            ref.set(button);
            button.fire();
            assertTrue(burstLayer(button).isVisible());
            assertEquals(1, scales(button.getGraphic()).size(), "the pop is an appended transform");
        });
        waitUntil(() -> !burstLayer(ref.get()).isVisible() && scales(ref.get().getGraphic()).isEmpty());
        onFx(() -> {
            RXReactionButton button = ref.get();
            assertEquals(2.0, button.getGraphic().getScaleX(), "the graphic's own scale is untouched");
            button.getSkin().dispose();
        });
    }

    @Test
    public void theLayerSurvivesChildRebuildsAndSkinReplacement() throws Exception {
        onFx(() -> {
            RXReactionButton button = longBurstButton();
            button.setText("12");
            button.setContentDisplay(ContentDisplay.TOP);
            button.applyCss();
            button.layout();
            assertEquals(1, button.lookupAll(".burst-layer").size(), "the rebuilt children keep one layer");

            button.fire();
            Node graphic = button.getGraphic();
            // Another skin class: JavaFX ignores a new skin of the same class.
            button.setSkin(new RXToggleButtonSkin(button));
            button.applyCss();
            button.layout();
            assertTrue(button.lookupAll(".burst-layer").isEmpty(), "the old skin took its layer along");
            assertTrue(scales(graphic).isEmpty(), "the old skin's pop is gone");
            button.getSkin().dispose();
        });
    }

    @Test
    public void focusKeepsTheRoundRippleShape() throws Exception {
        onFx(() -> {
            FocusableReactionButton button = new FocusableReactionButton();
            laidOut(button);
            button.focus();
            button.setSelected(true);
            button.applyCss();
            CornerRadii radii = button.getBackground().getFills().get(0).getRadii();
            assertEquals(999.0, radii.getTopLeftHorizontalRadius(),
                    "the toggle button's focused radius would square off the ripple clip");
        });
    }

    @Test
    public void replacingTheGraphicDetachesThePop() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            Node first = button.getGraphic();
            button.fire();
            assertFalse(scales(first).isEmpty());
            button.setGraphic(new Region());
            assertTrue(scales(first).isEmpty());
            button.getSkin().dispose();
        });
    }

    @Test
    public void anIdleBurstLayerAddsNothingToTheBounds() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            Bounds idle = button.getBoundsInParent();
            assertEquals(button.getWidth(), idle.getWidth(), 1.0);
            assertEquals(button.getHeight(), idle.getHeight(), 1.0);

            button.playBurst();
            assertTrue(button.getBoundsInParent().getWidth() > idle.getWidth(), "a playing burst may overflow");
            button.getSkin().dispose();
        });
    }

    @Test
    public void theBurstIsCenteredOnTheIconOrTheButton() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.setText("Like");
            button.applyCss();
            button.layout();
            button.playBurst();
            Canvas layer = burstLayer(button);
            Bounds icon = button.getGraphic().getBoundsInParent();
            assertEquals(icon.getCenterX(), layer.getLayoutX() + layer.getWidth() / 2.0, 0.5);
            assertEquals(icon.getCenterY(), layer.getLayoutY() + layer.getHeight() / 2.0, 0.5);

            button.setContentDisplay(ContentDisplay.TEXT_ONLY);
            button.applyCss();
            button.layout();
            button.playBurst();
            assertEquals(button.getWidth() / 2.0, layer.getLayoutX() + layer.getWidth() / 2.0, 0.5);
            assertEquals(button.getHeight() / 2.0, layer.getLayoutY() + layer.getHeight() / 2.0, 0.5);
            button.getSkin().dispose();
        });
    }

    @Test
    public void theBurstScaleIsClampedAndFallsBack() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            double defaultSide = sideAt(button, 1.0);
            assertEquals(defaultSide, sideAt(button, Double.NaN));
            assertEquals(defaultSide, sideAt(button, -2.0));
            assertEquals(sideAt(button, 4.0), sideAt(button, 400.0), "capped at 4");
            assertEquals(sideAt(button, 0.25), sideAt(button, 0.01), "floored at 0.25");
            assertTrue(sideAt(button, 2.0) > defaultSide);
            button.getSkin().dispose();
        });
    }

    // ==================== Disposal ====================

    @Test
    public void disposeRemovesTheLayerAndStopsListening() throws Exception {
        onFx(() -> {
            RXReactionButton button = quickButton();
            button.setBurstDuration(Duration.seconds(5.0));
            button.fire();
            Canvas layer = burstLayer(button);
            Node graphic = button.getGraphic();
            button.getSkin().dispose();

            assertFalse(button.getChildrenUnmodifiable().contains(layer));
            assertTrue(scales(graphic).isEmpty());
            button.fire();
            button.playBurst();
            assertTrue(scales(graphic).isEmpty(), "a disposed skin no longer reacts");
        });
    }

    // ==================== Theme ====================

    @Test
    public void theIconTakesTheReactionColorWhenSelected() throws Exception {
        onFx(() -> {
            RXReactionButton heart = laidOut(new RXReactionButton());
            Color idle = iconColor(heart);
            heart.setSelected(true);
            heart.applyCss();
            assertEquals(Color.web("#f56c6c"), iconColor(heart));
            assertNotEquals(idle, iconColor(heart));

            RXReactionButton star = new RXReactionButton();
            star.getStyleClass().add("star");
            laidOut(star);
            star.setSelected(true);
            star.applyCss();
            assertEquals(Color.web("#e6a23c"), iconColor(star));
            assertEquals(Color.web("#e6a23c"), star.getBurstColor(), "the burst follows the reaction color");
        });
    }

    // ==================== Helpers ====================

    private static RXReactionButton longBurstButton() {
        RXReactionButton button = new RXReactionButton();
        button.setBurstDuration(Duration.seconds(5.0));
        return laidOut(button);
    }

    private static RXReactionButton quickButton() {
        RXReactionButton button = new RXReactionButton();
        button.setBurstDuration(SHORT_BURST);
        return laidOut(button);
    }

    private static <T extends RXReactionButton> T laidOut(T button) {
        StackPane root = new StackPane(button);
        new Scene(root, 300.0, 200.0);
        root.resize(300.0, 200.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static double sideAt(RXReactionButton button, double scale) {
        button.setBurstScale(scale);
        button.playBurst();
        return burstLayer(button).getWidth();
    }

    private static Canvas burstLayer(RXReactionButton button) {
        Node layer = button.lookup(".burst-layer");
        assertNotNull(layer, "burst layer");
        return assertInstanceOf(Canvas.class, layer);
    }

    private static List<Scale> scales(Node node) {
        return node.getTransforms().stream()
                .filter(Scale.class::isInstance)
                .map(Scale.class::cast)
                .toList();
    }

    private static Color iconColor(RXReactionButton button) {
        Region icon = (Region) button.getGraphic();
        return assertInstanceOf(Color.class, icon.getBackground().getFills().get(0).getFill());
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<RXReactionButton, ?> metaData(RXReactionButton button, String property) {
        return (CssMetaData<RXReactionButton, ?>) button.getCssMetaData().stream()
                .filter(item -> item.getProperty().equals(property))
                .findFirst()
                .orElseThrow();
    }

    // Polls on the FX thread until the condition holds or the deadline passes.
    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicBoolean met = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            onFx(() -> met.set(condition.getAsBoolean()));
            if (met.get()) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("condition not met within 3 s");
    }

    /** Exposes the protected focus setter: the headless test window cannot take focus. */
    private static final class FocusableReactionButton extends RXReactionButton {

        void focus() {
            setFocused(true);
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
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
