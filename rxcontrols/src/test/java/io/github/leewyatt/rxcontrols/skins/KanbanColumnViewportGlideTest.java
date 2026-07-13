package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glide identity tests for {@link KanbanColumnViewport}: the settle glide must
 * follow the card, not the index — a cards mutation while a glide is in flight
 * arrives as a reorder pass whose index-keyed carry-over map can point at a
 * different card. Lives in the skins package to drive the package-private drop
 * gap directly.
 */
public class KanbanColumnViewportGlideTest {

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
     * Verifies a cards mutation while a settle glide is in flight cancels every
     * stale glide instead of carrying the old card's motion onto the card that
     * now sits under the same index, and that a later slot change glides again.
     */
    @Test
    public void cardsMutationMidGlideDoesNotCarryMotionOntoAnotherCard() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = new RXKanbanView<>();
            RXKanbanColumn<String> column = new RXKanbanColumn<>("todo");
            for (int i = 0; i < 12; i++) {
                column.getCards().add("card-" + i);
            }
            board.getColumns().add(column);
            board.setAnimationDuration(Duration.seconds(30.0)); // freeze the mid-glide state
            StackPane root = new StackPane(board);
            new Scene(root, 400, 700);
            root.resize(400, 700);
            pump(root);

            KanbanColumnViewport<String> viewport = findViewport(root);
            viewport.setDropGap(2); // opens a one-row gap -> settle glide in flight
            pump(root);
            Map<RXKanbanCardCell<String>, String> cardBefore = new HashMap<>();
            int gliding = 0;
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (Math.abs(cell.getTranslateY()) > 0.5) {
                    gliding++;
                }
                if (cell.isVisible() && cell.getIndex() >= 0) {
                    cardBefore.put(cell, cell.getItem());
                }
            }
            assertTrue(gliding > 0, "setup: the drop gap engages a settle glide");

            column.getCards().add(0, "card-new"); // shifts every index mid-glide
            pump(root);
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (!cell.isVisible() || cell.getIndex() < 0) {
                    continue;
                }
                boolean moving = Math.abs(cell.getTranslateX()) + Math.abs(cell.getTranslateY()) > 0.5;
                String before = cardBefore.get(cell);
                assertTrue(!moving || before == null || before.equals(cell.getItem()),
                        "a rebound cell must not glide with another card's motion: "
                                + before + " -> " + cell.getItem());
                assertEquals(0.0, Math.abs(cell.getTranslateX()) + Math.abs(cell.getTranslateY()), 0.5,
                        "the index-shifting mutation leaves no carry-over, so every cell pops");
            }

            // Sanity: with stable cards a later slot change still glides.
            viewport.setDropGap(6);
            pump(root);
            boolean glidesAgain = false;
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (Math.abs(cell.getTranslateY()) > 0.5) {
                    glidesAgain = true;
                    break;
                }
            }
            assertTrue(glidesAgain, "same-card carry-overs keep gliding after the mutation settled");
        });
    }

    @SuppressWarnings("unchecked")
    private static KanbanColumnViewport<String> findViewport(Parent root) {
        for (Node node : root.lookupAll(".rx-kanban-card-cell")) {
            Node parent = node.getParent();
            while (parent != null && !(parent instanceof KanbanColumnViewport)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                return (KanbanColumnViewport<String>) parent;
            }
        }
        throw new IllegalStateException("viewport not found");
    }

    @SuppressWarnings("unchecked")
    private static List<RXKanbanCardCell<String>> cells(Parent root) {
        List<RXKanbanCardCell<String>> cells = new ArrayList<>();
        for (Node node : root.lookupAll(".rx-kanban-card-cell")) {
            cells.add((RXKanbanCardCell<String>) node);
        }
        return cells;
    }

    private static void pump(Parent root) {
        root.applyCss();
        root.layout();
    }

    private static void onFx(Runnable action) throws Exception {
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
