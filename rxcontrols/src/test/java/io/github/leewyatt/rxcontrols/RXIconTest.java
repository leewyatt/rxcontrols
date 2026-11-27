package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXIcon}: the fixed box (min = pref = max), CSS-driven size,
 * no squeezing or stretching by parents, explicit overrides, and that the
 * built-in control icons use it.
 */
public class RXIconTest {

    private static final double EPSILON = 1.0e-6;
    private static final String ICON_CSS = "-fx-pref-width: 12px; -fx-pref-height: 8px;"
            + " -fx-shape: \"M 0 0 L 12 0 L 6 8 Z\"; -fx-background-color: black;";

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
        // Modena is installed when the first control class loads; these tests may
        // build only regions, so pin it for the -fx-* colors the icon reads.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Defaults ====================

    @Test
    public void defaultStyleClassAndStylesheet() throws Exception {
        runOnFx(() -> {
            RXIcon icon = new RXIcon();
            assertTrue(icon.getStyleClass().contains("rx-icon"));
            assertEquals(RXResources.USER_AGENT_STYLESHEET, icon.getUserAgentStylesheet());
        });
    }

    // ==================== Fixed box ====================

    @Test
    public void minAndMaxFollowTheCssPrefSize() throws Exception {
        runOnFx(() -> {
            RXIcon icon = styledIcon();
            StackPane root = new StackPane(icon);
            new Scene(root, 100, 100);
            root.applyCss();

            assertEquals(12.0, icon.prefWidth(-1), EPSILON);
            assertEquals(8.0, icon.prefHeight(-1), EPSILON);
            assertEquals(12.0, icon.minWidth(-1), EPSILON);
            assertEquals(8.0, icon.minHeight(-1), EPSILON);
            assertEquals(12.0, icon.maxWidth(-1), EPSILON);
            assertEquals(8.0, icon.maxHeight(-1), EPSILON);
        });
    }

    @Test
    public void notStretchedByAStackPane() throws Exception {
        runOnFx(() -> {
            RXIcon icon = styledIcon();
            StackPane root = new StackPane(icon);
            new Scene(root, 200, 200);
            root.applyCss();
            root.layout();

            assertEquals(12.0, icon.getWidth(), EPSILON);
            assertEquals(8.0, icon.getHeight(), EPSILON);
        });
    }

    @Test
    public void notSqueezedByAnHBoxShortOfSpace() throws Exception {
        runOnFx(() -> {
            RXIcon icon = styledIcon();
            HBox row = squeezedRow(icon);
            row.layout();

            assertEquals(12.0, icon.getWidth(), EPSILON);
            assertEquals(8.0, icon.getHeight(), EPSILON);
        });
    }

    @Test
    public void explicitMinSizeOverridesTheFixedBox() throws Exception {
        runOnFx(() -> {
            RXIcon icon = styledIcon();
            icon.setMinWidth(0.0);
            HBox row = squeezedRow(icon);
            row.layout();

            assertEquals(0.0, icon.minWidth(-1), EPSILON);
            assertTrue(icon.getWidth() < 12.0, "an explicit min lets the row shrink the icon: " + icon.getWidth());
        });
    }

    @Test
    public void paddingIsPartOfTheBox() throws Exception {
        runOnFx(() -> {
            RXIcon icon = styledIcon();
            icon.setStyle(ICON_CSS + " -fx-padding: 2px;");
            StackPane root = new StackPane(icon);
            new Scene(root, 100, 100);
            root.applyCss();

            // An explicit pref size already includes the padding.
            assertEquals(12.0, icon.minWidth(-1), EPSILON);

            RXIcon unsized = new RXIcon();
            unsized.setStyle("-fx-padding: 3px;");
            root.getChildren().add(unsized);
            root.applyCss();
            assertEquals(6.0, unsized.minWidth(-1), EPSILON);
            assertEquals(6.0, unsized.maxHeight(-1), EPSILON);
        });
    }

    // ==================== Built-in icons ====================

    @Test
    public void builtInControlIconsAreFixedBoxes() throws Exception {
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
            StackPane root = new StackPane(control);
            new Scene(root, 400, 300);
            root.applyCss();
            root.layout();
            Node icon = control.lookup(selector);
            String where = control.getClass().getSimpleName() + " " + selector;
            assertNotNull(icon, where + " not found");
            assertInstanceOf(RXIcon.class, icon, where);
        }));
    }

    // ==================== Helpers ====================

    private static RXIcon styledIcon() {
        RXIcon icon = new RXIcon();
        icon.setStyle(ICON_CSS);
        return icon;
    }

    private static HBox squeezedRow(RXIcon icon) {
        // The text keeps its width, so only the icon can give way.
        Label text = new Label("A long text that does not fit the row at all");
        text.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(text, icon);
        row.setMaxWidth(Region.USE_PREF_SIZE);
        Parent root = new StackPane(row);
        new Scene(root, 400, 100);
        row.setPrefWidth(30.0);
        root.applyCss();
        root.layout();
        return row;
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
