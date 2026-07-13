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
            boolean sameCardGlide = false;
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (!cell.isVisible() || cell.getIndex() < 0) {
                    continue;
                }
                boolean moving = Math.abs(cell.getTranslateX()) + Math.abs(cell.getTranslateY()) > 0.5;
                if (!moving) {
                    continue;
                }
                String before = cardBefore.get(cell);
                assertTrue(before == null || before.equals(cell.getItem()),
                        "a rebound cell must not glide with another card's motion: "
                                + before + " -> " + cell.getItem());
                if (before != null) {
                    sameCardGlide = true;
                }
            }
            assertTrue(sameCardGlide, "the settle glide follows the cards to their shifted slots");
        });
    }

    /**
     * Verifies a plain programmatic insert at rest glides the displaced cards
     * with their own nodes: the entering card must not steal a displaced card's
     * prior cell, which would cascade every later card into a pop.
     */
    @Test
    public void programmaticInsertGlidesDisplacedCardsByIdentity() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = new RXKanbanView<>();
            RXKanbanColumn<String> column = new RXKanbanColumn<>("todo");
            for (int i = 0; i < 12; i++) {
                column.getCards().add("card-" + i);
            }
            board.getColumns().add(column);
            board.setAnimationDuration(Duration.seconds(30.0));
            StackPane root = new StackPane(board);
            new Scene(root, 400, 700);
            root.resize(400, 700);
            pump(root);

            Map<String, RXKanbanCardCell<String>> nodeByCard = new HashMap<>();
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (cell.isVisible() && cell.getIndex() >= 0) {
                    nodeByCard.put(cell.getItem(), cell);
                }
            }

            column.getCards().add(0, "card-new"); // no animation in flight
            pump(root);
            boolean displacedGlide = false;
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (!cell.isVisible() || cell.getIndex() < 0) {
                    continue;
                }
                boolean moving = Math.abs(cell.getTranslateX()) + Math.abs(cell.getTranslateY()) > 0.5;
                RXKanbanCardCell<String> priorNode = nodeByCard.get(cell.getItem());
                assertTrue(!moving || priorNode == cell,
                        "a gliding cell must be the card's own prior node: " + cell.getItem());
                if (priorNode != null) {
                    assertTrue(priorNode == cell,
                            "a displaced card keeps its node across the insert: " + cell.getItem());
                    if (moving) {
                        displacedGlide = true;
                    }
                }
            }
            assertTrue(displacedGlide, "displaced cards glide to their shifted slots");
        });
    }

    /**
     * Verifies a same-column move commit (remove + add in one pass) glides each
     * displaced card with its own node — the moved card keeps its cell and slides
     * to the new slot, and no cell carries another card's motion.
     */
    @Test
    public void dropCommitGlidesTheMovedCardsByIdentity() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = new RXKanbanView<>();
            RXKanbanColumn<String> column = new RXKanbanColumn<>("todo");
            for (int i = 0; i < 12; i++) {
                column.getCards().add("card-" + i);
            }
            board.getColumns().add(column);
            board.setAnimationDuration(Duration.seconds(30.0));
            StackPane root = new StackPane(board);
            new Scene(root, 400, 700);
            root.resize(400, 700);
            pump(root);

            Map<String, RXKanbanCardCell<String>> nodeByCard = new HashMap<>();
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (cell.isVisible() && cell.getIndex() >= 0) {
                    nodeByCard.put(cell.getItem(), cell);
                }
            }

            // A same-column move commit lands as remove + add of the SAME card
            // instance in a single pass (mirroring the drag support's commitMove).
            String movedCard = column.getCards().get(0);
            column.getCards().remove(movedCard);
            column.getCards().add(3, movedCard);
            pump(root);

            RXKanbanCardCell<String> moved = null;
            for (RXKanbanCardCell<String> cell : cells(root)) {
                if (!cell.isVisible() || cell.getIndex() < 0) {
                    continue;
                }
                boolean moving = Math.abs(cell.getTranslateX()) + Math.abs(cell.getTranslateY()) > 0.5;
                RXKanbanCardCell<String> priorNode = nodeByCard.get(cell.getItem());
                assertTrue(!moving || priorNode == cell,
                        "a gliding cell must be the card's own prior node: " + cell.getItem());
                if (cell.getItem() == movedCard) {
                    moved = cell;
                }
            }
            assertTrue(moved != null, "the moved card is visible");
            assertTrue(moved == nodeByCard.get(movedCard), "the moved card keeps its node across the commit");
            assertTrue(Math.abs(moved.getTranslateY()) > 0.5, "the moved card glides to its new slot");
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
