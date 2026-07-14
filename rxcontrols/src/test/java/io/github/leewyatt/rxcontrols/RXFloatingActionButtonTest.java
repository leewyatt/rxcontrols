package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXFloatingActionButton.Size;
import io.github.leewyatt.rxcontrols.skins.RXFloatingActionButtonSkin;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXFloatingActionButton}.
 */
public class RXFloatingActionButtonTest {

    private static final PseudoClass SMALL = PseudoClass.getPseudoClass("small");
    private static final PseudoClass LARGE = PseudoClass.getPseudoClass("large");

    /**
     * Starts the JavaFX toolkit so CSS and skins can be applied.
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
     * Verifies default public state and inherited control setup.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultStateMatchesContract() throws Exception {
        runOnFx(() -> {
            Region graphic = new Region();
            RXFloatingActionButton button = new RXFloatingActionButton(graphic);

            assertTrue(button.getStyleClass().contains("button"));
            assertTrue(button.getStyleClass().contains("rx-button"));
            assertTrue(button.getStyleClass().contains("rx-fab"));
            assertSame(graphic, button.getGraphic());
            assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
            assertEquals(Size.STANDARD, button.getSize());
            assertEquals(Region.USE_PREF_SIZE, button.getMinWidth());
            assertEquals(Region.USE_PREF_SIZE, button.getMinHeight());
            assertEquals(Region.USE_PREF_SIZE, button.getMaxWidth());
            assertEquals(Region.USE_PREF_SIZE, button.getMaxHeight());
            assertFalse(button.isPickOnBounds());
            assertNotNull(button.getUserAgentStylesheet());
        });
    }

    /**
     * Verifies the text-and-graphic constructor retains content and shared setup.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void textAndGraphicConstructorRetainsContentAndSetup() throws Exception {
        runOnFx(() -> {
            Region graphic = new Region();
            RXFloatingActionButton button = new RXFloatingActionButton("Create", graphic);

            assertEquals("Create", button.getText());
            assertSame(graphic, button.getGraphic());
            assertTrue(button.getStyleClass().contains("rx-fab"));
            assertEquals(ContentDisplay.GRAPHIC_ONLY, button.getContentDisplay());
            assertEquals(Size.STANDARD, button.getSize());
        });
    }

    /**
     * Verifies the size pseudo-classes and pass-through null behavior.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void sizePseudoClassesTrackSize() throws Exception {
        runOnFx(() -> {
            RXFloatingActionButton button = new RXFloatingActionButton();

            button.setSize(Size.SMALL);
            assertEquals(Size.SMALL, button.getSize());
            assertTrue(button.getPseudoClassStates().contains(SMALL));
            assertFalse(button.getPseudoClassStates().contains(LARGE));

            button.setSize(Size.LARGE);
            assertEquals(Size.LARGE, button.getSize());
            assertFalse(button.getPseudoClassStates().contains(SMALL));
            assertTrue(button.getPseudoClassStates().contains(LARGE));

            button.setSize(null);
            assertNull(button.getSize());
            assertFalse(button.getPseudoClassStates().contains(SMALL));
            assertFalse(button.getPseudoClassStates().contains(LARGE));
        });
    }

    /**
     * Verifies binding and CSS metadata for the size property.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssMetadataIncludesFabSizeAndInheritedRippleProperties() throws Exception {
        runOnFx(() -> {
            List<String> propertyNames = RXFloatingActionButton.getClassCssMetaData().stream()
                    .map(CssMetaData<? extends Styleable, ?>::getProperty)
                    .toList();
            Set<String> properties = propertyNames.stream().collect(Collectors.toSet());

            assertEquals(propertyNames.size(), properties.size());
            assertEquals(1L, propertyNames.stream()
                    .filter("-rx-fab-size"::equals)
                    .count());
            assertTrue(properties.contains("-rx-fab-size"));
            assertTrue(properties.contains("-rx-ripple-fill"));
            assertTrue(properties.contains("-rx-ripple-centered"));
            assertTrue(properties.contains("-fx-font"));

            RXFloatingActionButton button = new RXFloatingActionButton();
            ObjectProperty<Size> source = new SimpleObjectProperty<>(Size.LARGE);
            button.sizeProperty().bind(source);
            assertEquals(Size.LARGE, button.getSize());
            assertTrue(button.getPseudoClassStates().contains(LARGE));
            source.set(Size.SMALL);
            assertEquals(Size.SMALL, button.getSize());
            assertTrue(button.getPseudoClassStates().contains(SMALL));
        });
    }

    /**
     * Verifies user-agent CSS applies every FAB size.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssAppliesFabSize() throws Exception {
        runOnFx(() -> {
            assertCssFabSize(null, Size.STANDARD, null, 56.0);
            assertCssFabSize("-rx-fab-size: small;", Size.SMALL, SMALL, 40.0);
            assertCssFabSize("-rx-fab-size: large;", Size.LARGE, LARGE, 96.0);
        });
    }

    /**
     * Verifies FAB-specific ripple CSS overrides inherited RXButton defaults.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssAppliesFabRippleDefaults() throws Exception {
        runOnFx(() -> {
            RXFloatingActionButton button = new RXFloatingActionButton();
            attachAndApplyCss(button);

            assertEquals(Color.WHITE, button.getRippleFill());
            assertTrue(button.isRippleCentered());
        });
    }

    /**
     * Verifies focused FAB states keep circular background geometry.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusedCssKeepsCircularBackground() throws Exception {
        runOnFx(() -> {
            RXFloatingActionButton button = new RXFloatingActionButton();
            attachAndApplyCss(button);

            button.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
            button.applyCss();

            List<BackgroundFill> fills = button.getBackground().getFills();
            assertEquals(2, fills.size());
            assertEquals(0.0, fills.get(0).getInsets().getTop());
            assertEquals(2.0, fills.get(1).getInsets().getTop());
            assertPercentRadius(fills.get(0).getRadii());
            assertPercentRadius(fills.get(1).getRadii());
        });
    }

    /**
     * Verifies the elevation shadow does not expand the clickable area.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void shadowRingDoesNotPick() throws Exception {
        runOnFx(() -> {
            RXFloatingActionButton button = new RXFloatingActionButton();
            attachAndApplyCss(button);

            assertTrue(button.getLayoutBounds().getWidth() > 0.0);
            assertTrue(button.contains(button.getLayoutBounds().getWidth() / 2.0,
                    button.getLayoutBounds().getHeight() / 2.0));
            assertFalse(button.contains(-4.0, -4.0));
        });
    }

    /**
     * Verifies the default skin type.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultSkinIsFabSkin() throws Exception {
        runOnFx(() -> {
            RXFloatingActionButton button = new RXFloatingActionButton();
            StackPane root = new StackPane(button);
            new Scene(root, 120.0, 120.0);
            root.applyCss();

            assertTrue(button.getSkin() instanceof RXFloatingActionButtonSkin);
        });
    }

    private static void assertCssFabSize(String style,
                                         Size expectedSize,
                                         PseudoClass expectedPseudoClass,
                                         double expectedDimension) {
        RXFloatingActionButton button = new RXFloatingActionButton();
        if (style != null) {
            button.setStyle(style);
        }
        attachAndApplyCss(button);

        assertEquals(expectedSize, button.getSize());
        assertEquals(expectedPseudoClass == SMALL, button.getPseudoClassStates().contains(SMALL));
        assertEquals(expectedPseudoClass == LARGE, button.getPseudoClassStates().contains(LARGE));
        assertEquals(expectedDimension, button.prefWidth(-1), 0.0001);
        assertEquals(expectedDimension, button.prefHeight(-1), 0.0001);
    }

    private static void attachAndApplyCss(RXFloatingActionButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 160.0, 160.0);
        root.applyCss();
        root.applyCss();
        root.layout();
    }

    private static void assertPercentRadius(CornerRadii radii) {
        assertTrue(radii.isTopLeftHorizontalRadiusAsPercentage());
        assertTrue(radii.isTopLeftVerticalRadiusAsPercentage());
        assertTrue(radii.isTopRightHorizontalRadiusAsPercentage());
        assertTrue(radii.isTopRightVerticalRadiusAsPercentage());
        assertTrue(radii.isBottomRightHorizontalRadiusAsPercentage());
        assertTrue(radii.isBottomRightVerticalRadiusAsPercentage());
        assertTrue(radii.isBottomLeftHorizontalRadiusAsPercentage());
        assertTrue(radii.isBottomLeftVerticalRadiusAsPercentage());
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
            throw new AssertionError("FX action timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
