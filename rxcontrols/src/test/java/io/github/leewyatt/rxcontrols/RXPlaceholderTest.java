package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXPlaceholder.Status;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXPlaceholder} and its skin: defaults, status pseudo-classes,
 * slot collapse and {@code :filled} mirroring, the status-derived default icon
 * versus the graphic escape hatch, wrapped-description sizing, and disposal.
 */
public class RXPlaceholderTest {

    private static final PseudoClass FILLED = PseudoClass.getPseudoClass("filled");

    /**
     * Starts the JavaFX toolkit so the skin can be created and styled.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Defaults & API ====================

    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder();
            assertEquals(Status.NONE, placeholder.getStatus());
            assertNull(placeholder.getGraphic());
            assertNull(placeholder.getTitle());
            assertNull(placeholder.getDescription());
            assertTrue(placeholder.getActions().isEmpty());
            assertFalse(placeholder.isFocusTraversable());
            assertEquals(AccessibleRole.NODE, placeholder.getAccessibleRole());
            assertTrue(placeholder.getStyleClass().contains("rx-placeholder"));
        });
    }

    @Test
    public void convenienceConstructorsSeedStatusAndTitle() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder(Status.ERROR, "Something went wrong");
            assertEquals(Status.ERROR, placeholder.getStatus());
            assertEquals("Something went wrong", placeholder.getTitle());
        });
    }

    // ==================== Status pseudo-classes ====================

    @Test
    public void statusDrivesItsPseudoClass() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder();
            placeholder.setStatus(Status.NOT_FOUND);
            assertTrue(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass("not-found")));

            placeholder.setStatus(Status.SERVER_ERROR);
            assertTrue(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass("server-error")));
            assertFalse(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass("not-found")));

            // Lenient: null is stored (getter pass-through) and resolves to the
            // default NONE, which activates no status pseudo-class.
            placeholder.setStatus(null);
            assertNull(placeholder.getStatus());
            for (Status status : Status.values()) {
                if (status == Status.NONE) {
                    continue;
                }
                String name = status.name().toLowerCase().replace('_', '-');
                assertFalse(placeholder.getPseudoClassStates().contains(PseudoClass.getPseudoClass(name)),
                        "no status pseudo-class after null: " + name);
            }
        });
    }

    // ==================== Slots ====================

    @Test
    public void emptySlotsCollapseAndFilledSlotsShow() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder();
            attach(placeholder);

            Node title = slot(placeholder, ".title");
            Node description = slot(placeholder, ".description");
            Node actions = slot(placeholder, ".actions");
            Node graphic = slot(placeholder, ".graphic");
            for (Node node : new Node[] {title, description, actions, graphic}) {
                assertFalse(node.isVisible());
                assertFalse(node.isManaged());
                assertFalse(node.getPseudoClassStates().contains(FILLED));
            }

            placeholder.setTitle("No data");
            placeholder.setDescription("Nothing to show yet.");
            placeholder.getActions().add(new Button("Reload"));
            for (Node node : new Node[] {title, description, actions}) {
                assertTrue(node.isVisible());
                assertTrue(node.isManaged());
                assertTrue(node.getPseudoClassStates().contains(FILLED));
            }

            placeholder.setTitle("");
            assertFalse(title.isVisible());
            placeholder.getActions().clear();
            assertFalse(actions.isVisible());
            assertFalse(actions.getPseudoClassStates().contains(FILLED));
        });
    }

    @Test
    public void graphicSlotPrefersUserNodeOverStatusIcon() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder();
            attach(placeholder);
            Pane graphicSlot = (Pane) slot(placeholder, ".graphic");

            // NONE + null graphic: no icon, slot collapsed.
            assertTrue(graphicSlot.getChildren().isEmpty());

            // A non-NONE status shows the default status icon.
            placeholder.setStatus(Status.EMPTY);
            assertEquals(1, graphicSlot.getChildren().size());
            assertTrue(graphicSlot.getChildren().get(0).getStyleClass().contains("icon"));
            assertTrue(graphicSlot.isVisible());

            // The escape hatch replaces the icon; the getter stays pass-through.
            Rectangle custom = new Rectangle(10.0, 10.0);
            placeholder.setGraphic(custom);
            assertEquals(1, graphicSlot.getChildren().size());
            assertSame(custom, graphicSlot.getChildren().get(0));

            // Clearing the slot brings the status icon back.
            placeholder.setGraphic(null);
            assertTrue(graphicSlot.getChildren().get(0).getStyleClass().contains("icon"));

            placeholder.setStatus(Status.NONE);
            assertTrue(graphicSlot.getChildren().isEmpty());
            assertFalse(graphicSlot.isVisible());
        });
    }

    // ==================== Sizing ====================

    @Test
    public void wrappedDescriptionNeverTruncatesActions() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder(Status.ERROR, "Something went wrong");
            placeholder.setDescription("A fairly long description that has to wrap over several lines "
                    + "when the placeholder is laid out inside a narrow container so the footer "
                    + "actions must still stay below it.");
            placeholder.getActions().add(new Button("Retry"));

            new Scene(placeholder);
            placeholder.applyCss();

            // The wrapped height at a narrow width must exceed the single-line height.
            double narrow = placeholder.prefHeight(240.0);
            double wide = placeholder.prefHeight(2000.0);
            assertTrue(narrow > wide, "wrap raises pref height: " + narrow + " vs " + wide);
            assertEquals(narrow, placeholder.minHeight(240.0), 1.0e-6, "min height follows the wrap");

            // After layout at the narrow width the actions row sits fully below the
            // description (no overlap).
            placeholder.resize(240.0, narrow);
            placeholder.layout();
            Node description = slot(placeholder, ".description");
            Node actions = slot(placeholder, ".actions");
            double descriptionBottom = description.getBoundsInParent().getMaxY();
            double actionsTop = actions.getBoundsInParent().getMinY();
            assertTrue(actionsTop >= descriptionBottom,
                    "actions below description: " + actionsTop + " vs " + descriptionBottom);
        });
    }

    @Test
    public void contentBiasFollowsTheDescription() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder();
            assertNull(placeholder.getContentBias(), "no description, no bias");
            placeholder.setDescription("Wraps, so height depends on width.");
            assertEquals(Orientation.HORIZONTAL, placeholder.getContentBias());
        });
    }

    @Test
    public void containersMeasureTheWrappedHeightThroughTheBias() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder(Status.ERROR, "Something went wrong");
            placeholder.setDescription("A fairly long description that has to wrap over several "
                    + "lines inside a narrow container so the container must ask for the "
                    + "height at the real wrap width.");
            placeholder.getActions().add(new Button("Retry"));

            // The container protocol only passes a width hint to
            // HORIZONTAL-biased children; without the bias the icon and footer
            // would be crushed to the single-line-measured height.
            VBox root = new VBox(placeholder);
            new Scene(root);
            root.resize(240.0, 600.0);
            root.applyCss();
            root.layout();

            assertTrue(placeholder.getHeight() >= placeholder.prefHeight(240.0) - 1.0e-6,
                    "the container allocated the wrap-aware height");
            Node graphic = slot(placeholder, ".graphic");
            assertTrue(graphic.getLayoutBounds().getHeight() > 0.0, "status icon not crushed");
        });
    }

    // ==================== Dispose ====================

    @Test
    public void disposeDetachesListeners() throws Exception {
        runOnFx(() -> {
            RXPlaceholder placeholder = new RXPlaceholder(Status.EMPTY, "No data");
            attach(placeholder);
            Pane actionsBox = (Pane) slot(placeholder, ".actions");
            Node title = slot(placeholder, ".title");
            placeholder.getSkin().dispose();

            // Both the list listener and the property listeners are detached:
            // mutations after dispose must not throw or reach the old skin nodes.
            placeholder.getActions().add(new Button("Late"));
            assertTrue(actionsBox.getChildren().isEmpty());
            placeholder.setTitle("Changed");
            assertEquals("No data", ((Label) title).getText());
        });
    }

    // ==================== Helpers ====================

    private static Node slot(RXPlaceholder placeholder, String selector) {
        Node node = placeholder.lookup(selector);
        assertNotNull(node, "slot exists: " + selector);
        return node;
    }

    private static void attach(RXPlaceholder placeholder) {
        new Scene(placeholder);
        placeholder.resize(400.0, 300.0);
        placeholder.applyCss();
        placeholder.layout();
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
