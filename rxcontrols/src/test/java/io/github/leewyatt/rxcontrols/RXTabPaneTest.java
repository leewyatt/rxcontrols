package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXSkinBase;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Skin;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXTabPane}: property defaults, CSS metadata, the styleable
 * {@link RXTab} model, and the control-side maintenance of
 * {@code RXTab.tabPane} / {@code RXTab.selected} (including across a replaced
 * selection model).
 */
public class RXTabPaneTest {

    private static final double EPSILON = 0.0001;

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

    // ==================== Defaults & metadata ====================

    @Test
    public void defaultState() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            assertTrue(pane.getStyleClass().contains("rx-tab-pane"));
            assertNotNull(pane.getUserAgentStylesheet());
            assertTrue(pane.getTabs().isEmpty());
            assertNotNull(pane.getSelectionModel());
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
            assertEquals(Side.TOP, pane.getSide());
            assertEquals(Side.TOP, pane.effectiveSide());
            assertEquals(RXTabPane.Variant.STANDARD, pane.getVariant());
            assertEquals(RXTabPane.ScrollButtonPolicy.AUTO, pane.getScrollButtonPolicy());
            assertTrue(pane.isAnimated());
            assertEquals(Duration.millis(250.0), pane.getAnimationDuration());
            assertTrue(pane.isSelectionFollowsFocus());
            assertEquals(0.0, pane.getTabMinWidth(), EPSILON);
            assertEquals(Double.MAX_VALUE, pane.getTabMaxWidth(), EPSILON);
            assertEquals(RXTabPane.TabClosingPolicy.UNAVAILABLE, pane.getTabClosingPolicy());
        });
    }

    @Test
    public void settersArePassThrough() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            pane.setSide(Side.BOTTOM);
            assertEquals(Side.BOTTOM, pane.getSide());
            pane.setVariant(RXTabPane.Variant.FULL_WIDTH);
            assertEquals(RXTabPane.Variant.FULL_WIDTH, pane.getVariant());
            pane.setScrollButtonPolicy(RXTabPane.ScrollButtonPolicy.ALWAYS);
            assertEquals(RXTabPane.ScrollButtonPolicy.ALWAYS, pane.getScrollButtonPolicy());
            pane.setAnimated(false);
            assertFalse(pane.isAnimated());
            pane.setAnimationDuration(Duration.millis(90.0));
            assertEquals(Duration.millis(90.0), pane.getAnimationDuration());
            pane.setSelectionFollowsFocus(false);
            assertFalse(pane.isSelectionFollowsFocus());
            pane.setTabMinWidth(72.0);
            assertEquals(72.0, pane.getTabMinWidth(), EPSILON);
            pane.setTabMaxWidth(264.0);
            assertEquals(264.0, pane.getTabMaxWidth(), EPSILON);
            pane.setTabClosingPolicy(RXTabPane.TabClosingPolicy.ALL_TABS);
            assertEquals(RXTabPane.TabClosingPolicy.ALL_TABS, pane.getTabClosingPolicy());
        });
    }

    @Test
    public void cssMetadataExposesStyleableProperties() {
        Set<String> properties = RXTabPane.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-animation-duration"));
        assertTrue(properties.contains("-rx-tab-min-width"));
        assertTrue(properties.contains("-rx-tab-max-width"));
    }

    @Test
    public void effectiveSideTracksSideWithNullDefaultingToTop() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            for (Side side : Side.values()) {
                pane.setSide(side);
                assertEquals(side, pane.getSide());
                assertEquals(side, pane.effectiveSide());
            }
            // null falls back to TOP.
            pane.setSide(null);
            assertEquals(Side.TOP, pane.effectiveSide());
        });
    }

    // ==================== RXTab model ====================

    @Test
    public void tabDefaults() {
        RXTab tab = new RXTab("Overview");
        assertEquals("Overview", tab.getText());
        assertNull(tab.getGraphic());
        assertNull(tab.getContent());
        assertEquals(ContentDisplay.LEFT, tab.getContentDisplay());
        assertFalse(tab.isDisable());
        assertTrue(tab.isClosable());
        assertNull(tab.getTooltip());
        assertNull(tab.getAccessibleText());
        assertNull(tab.getOnCloseRequest());
        assertNull(tab.getOnClosed());
        assertFalse(tab.isSelected());
        assertNull(tab.getTabPane());
        assertEquals("RXTab", tab.getTypeSelector());
        assertTrue(tab.getStyleClass().contains("tab"));
        assertTrue(tab.getPseudoClassStates().isEmpty());
        assertTrue(tab.getCssMetaData().isEmpty());
    }

    @Test
    public void tabFactories() {
        StackPane content = new StackPane();
        StackPane graphic = new StackPane();
        RXTab full = RXTab.of("A", graphic, content);
        assertEquals("A", full.getText());
        assertSame(graphic, full.getGraphic());
        assertSame(content, full.getContent());
    }

    @Test
    public void tabStyleableParentIsOwningPane() throws Exception {
        runOnFx(() -> {
            RXTab tab = new RXTab("A");
            RXTabPane pane = new RXTabPane(tab);
            assertSame(pane, tab.getStyleableParent());
        });
    }

    @Test
    public void defaultPseudoClassesHoldBeforeSkinAttaches() throws Exception {
        runOnFx(() -> {
            // No scene / skin yet: the side + variant pseudo-classes are a control
            // contract and must reflect the defaults from construction.
            RXTabPane pane = new RXTabPane(new RXTab("A"));
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("top")));
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("standard")));
        });
    }

    // ==================== Back-pointer & selected flag maintenance ====================

    @Test
    public void addedTabsGetBackPointerRemovedTabsLoseIt() throws Exception {
        runOnFx(() -> {
            RXTab a = new RXTab("A");
            RXTab b = new RXTab("B");
            RXTabPane pane = new RXTabPane(a, b);
            assertSame(pane, a.getTabPane());
            assertSame(pane, b.getTabPane());

            pane.getTabs().remove(b);
            assertNull(b.getTabPane());
            assertSame(pane, a.getTabPane());
        });
    }

    @Test
    public void selectedFlagTracksSelection() throws Exception {
        runOnFx(() -> {
            RXTab a = new RXTab("A");
            RXTab b = new RXTab("B");
            RXTabPane pane = new RXTabPane(a, b);
            // First enabled tab auto-selected.
            assertTrue(a.isSelected());
            assertFalse(b.isSelected());

            pane.getSelectionModel().select(1);
            assertFalse(a.isSelected());
            assertTrue(b.isSelected());
        });
    }

    @Test
    public void duplicateTabKeepsBackPointerWhenOneOccurrenceRemoved() throws Exception {
        runOnFx(() -> {
            RXTab shared = new RXTab("Shared");
            RXTab other = new RXTab("Other");
            RXTabPane pane = new RXTabPane();
            pane.getTabs().addAll(shared, other, shared);
            assertSame(pane, shared.getTabPane());

            // Removing one occurrence must not clear the back-pointer while the
            // other occurrence is still in the list (the !contains guard).
            pane.getTabs().remove(2);
            assertSame(pane, shared.getTabPane());
        });
    }

    // ==================== Replaceable selection model ====================

    @Test
    public void customModelStillGetsSelectedAndBackPointerMaintenance() throws Exception {
        runOnFx(() -> {
            RXTab a = new RXTab("A");
            RXTab b = new RXTab("B");
            RXTab c = new RXTab("C");
            RXTabPane pane = new RXTabPane(a, b, c);

            // A foreign model that does NOT touch RXTab.selected itself.
            SingleSelectionModel<RXTab> custom = new PlainModel(pane);
            pane.setSelectionModel(custom);

            // Swapping models re-drives RXTab.selected: the built-in model's
            // auto-selected tab 'a' is cleared (the fresh custom model selects nothing).
            assertFalse(a.isSelected());
            assertEquals(-1, pane.getSelectedIndex());

            custom.select(1);
            assertTrue(b.isSelected());
            assertSame(b, pane.getSelectedItem());
            assertEquals(1, pane.getSelectedIndex());

            // Back-pointer maintenance still works after the swap.
            RXTab d = new RXTab("D");
            pane.getTabs().add(d);
            assertSame(pane, d.getTabPane());
        });
    }

    @Test
    public void replacedModelIsDetachedAndNoLongerDrivesPane() throws Exception {
        runOnFx(() -> {
            RXTab a = new RXTab("A");
            RXTab b = new RXTab("B");
            RXTabPane pane = new RXTabPane(a, b);
            SingleSelectionModel<RXTab> old = pane.getSelectionModel();

            pane.setSelectionModel(new PlainModel(pane));
            // The replaced model's listeners were removed: driving it must not move
            // the pane's projections.
            old.select(1);
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
        });
    }

    @Test
    public void nullSelectionModelClearsProjections() throws Exception {
        runOnFx(() -> {
            RXTab a = new RXTab("A");
            RXTabPane pane = new RXTabPane(a);
            assertTrue(a.isSelected());

            pane.setSelectionModel(null);
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
            assertFalse(a.isSelected());
        });
    }

    // ==================== Skin ====================

    @Test
    public void createsDefaultSkin() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(new RXTab("A"));
            StackPane root = new StackPane(pane);
            new Scene(root, 300, 200);
            root.applyCss();
            root.layout();
            Skin<?> skin = pane.getSkin();
            assertNotNull(skin);
            assertTrue(skin instanceof RXSkinBase);
        });
    }

    // ==================== Helpers ====================

    /** Minimal foreign model that reads the pane's tabs and does not self-manage flags. */
    private static final class PlainModel extends SingleSelectionModel<RXTab> {
        private final RXTabPane pane;

        PlainModel(RXTabPane pane) {
            this.pane = pane;
        }

        @Override
        protected RXTab getModelItem(int index) {
            return index >= 0 && index < pane.getTabs().size() ? pane.getTabs().get(index) : null;
        }

        @Override
        protected int getItemCount() {
            return pane.getTabs().size();
        }
    }

    private static void runOnFx(FxAction action) throws Exception {
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
