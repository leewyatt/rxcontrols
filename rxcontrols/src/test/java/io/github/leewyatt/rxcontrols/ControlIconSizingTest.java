package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.carousel.DefaultNavigator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that built-in control icons keep their CSS preferred size: after CSS is
 * applied, their minimum and maximum size are still locked to the preferred size,
 * so a parent can neither squeeze nor stretch them. A {@code -fx-min-*} or
 * {@code -fx-max-*} rule for one of these icons in the user agent style sheet
 * would replace the lock and fail these tests.
 */
@Tag("ui")
public class ControlIconSizingTest {

    private static final double EPSILON = 1.0e-6;

    /**
     * Starts the JavaFX toolkit; the popup test shows a stage.
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
        Platform.setImplicitExit(false);
    }

    // ==================== Control Icons ====================

    @Test
    public void controlIconsAreLockedToTheirPreferredSize() throws Exception {
        Map<String, Supplier<Node>> cases = new LinkedHashMap<>();
        cases.put(".box > .mark", RXCheckBox::new);
        cases.put(".suffix > .icon", RXDatePicker::new);
        cases.put(".clear-button > .graphic", RXMaterialTextField::new);
        cases.put(".reveal-button > .graphic", RXMaterialPasswordField::new);
        cases.put(".indicator-button > .indicator", RXTitledPane::new);
        cases.put(".thumb > .icon", RXSwitchButton::new);
        cases.put(".item > .empty", RXRating::new);
        cases.put(".copy-face > .indicator", RXCopyButton::new);
        cases.put(".arrow-button > .arrow", RXSelectionBox::new);
        cases.put(".arrow", RXMenuButton::new);
        cases.put(".reaction-icon", RXReactionButton::new);
        cases.put(".default-icon", RXAvatar::new);
        runOnFx(() -> cases.forEach((selector, factory) -> {
            Node control = factory.get();
            layOut(new StackPane(control));
            String where = control.getClass().getSimpleName() + " " + selector;
            Node icon = control.lookup(selector);
            assertNotNull(icon, where + " not found");
            assertLocked(icon, where);
        }));
    }

    @Test
    public void carouselAndCompactNavigatorArrowsAreLocked() throws Exception {
        runOnFx(() -> {
            RXCarousel carousel = new RXCarousel();
            DefaultNavigator navigator = new DefaultNavigator();
            navigator.setMaxDots(1);
            carousel.setNavigator(navigator);
            carousel.setPages(new Label("A"), new Label("B"), new Label("C"));
            layOut(new StackPane(carousel));
            Set<Node> carouselArrows = new LinkedHashSet<>(carousel.lookupAll(".prev-button > .arrow"));
            carouselArrows.addAll(carousel.lookupAll(".next-button > .arrow"));
            Node compact = carousel.lookup(".carousel-navigator");
            assertNotNull(compact, "navigator not found");
            Set<Node> compactArrows = compact.lookupAll(".arrow");
            assertEquals(2, compactArrows.size(), "compact navigator arrows");
            carouselArrows.removeAll(compactArrows);
            assertEquals(2, carouselArrows.size(), "carousel arrows");
            carouselArrows.forEach(arrow -> assertLocked(arrow, "RXCarousel arrow"));
            compactArrows.forEach(arrow -> assertLocked(arrow, "DefaultNavigator compact arrow"));
        });
    }

    @Test
    public void selectionBoxPopupChecksAreLocked() throws Exception {
        runOnFx(() -> {
            RXSelectionBox<String> box = new RXSelectionBox<>(
                    FXCollections.observableArrayList("apple", "banana"));
            box.setSelectionMode(SelectionMode.MULTIPLE);
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(new StackPane(box), 320, 240));
                stage.show();
                box.show();
                PopupControl popup = findPopup();
                assertNotNull(popup, "popup not found");
                Parent root = popup.getScene().getRoot();
                root.applyCss();
                root.layout();
                Set<Node> checks = root.lookupAll(".select-box > .check");
                assertFalse(checks.isEmpty(), "popup checks not found");
                checks.forEach(check -> assertLocked(check, "RXSelectionBox popup check"));
            } finally {
                box.hide();
                stage.hide();
            }
        });
    }

    // ==================== Helpers ====================

    private static void assertLocked(Node node, String where) {
        Region icon = assertInstanceOf(Region.class, node, where);
        assertEquals(Region.USE_PREF_SIZE, icon.getMinWidth(), where + " min width");
        assertEquals(Region.USE_PREF_SIZE, icon.getMinHeight(), where + " min height");
        assertEquals(Region.USE_PREF_SIZE, icon.getMaxWidth(), where + " max width");
        assertEquals(Region.USE_PREF_SIZE, icon.getMaxHeight(), where + " max height");
        double width = icon.prefWidth(-1);
        double height = icon.prefHeight(-1);
        assertTrue(width > 0 && height > 0, where + " has no preferred size");
        assertEquals(width, icon.minWidth(-1), EPSILON, where + " computed min width");
        assertEquals(width, icon.maxWidth(-1), EPSILON, where + " computed max width");
        assertEquals(height, icon.minHeight(-1), EPSILON, where + " computed min height");
        assertEquals(height, icon.maxHeight(-1), EPSILON, where + " computed max height");
    }

    private static void layOut(Parent root) {
        new Scene(root, 400, 300);
        root.applyCss();
        root.layout();
    }

    private static PopupControl findPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl popup
                    && popup.getStyleClass().contains("rx-selection-box-popup")) {
                return popup;
            }
        }
        return null;
    }

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
