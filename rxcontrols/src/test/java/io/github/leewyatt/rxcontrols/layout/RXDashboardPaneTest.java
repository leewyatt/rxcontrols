package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXDashboardPane} without gestures: defaults, adding and removing cards with and without a requested
 * position, the editing methods, applying snapshots and their corrections, column-count changes and the base layout
 * they convert from, invalid column counts, the notification order and what listeners may change, the pane's sizes,
 * the grid on the device-pixel grid at several render scales, content layout inside a card, and FXML.
 */
public class RXDashboardPaneTest {

    private static final double EPSILON = 1e-6;

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

    // ==================== Defaults ====================

    @Test
    public void defaults() throws Exception {
        fx(() -> {
            RXDashboardPane pane = new RXDashboardPane();
            assertTrue(pane.getStyleClass().contains("rx-dashboard-pane"));
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("editable")));
            assertEquals(12, pane.getColumnCount());
            assertEquals(80.0, pane.getRowHeight());
            assertEquals(10.0, pane.getHgap());
            assertEquals(10.0, pane.getVgap());
            assertEquals(0.0, pane.getMinColumnWidth());
            assertSame(CollisionPolicy.PUSH, pane.getCollisionPolicy());
            assertTrue(pane.isEditable());
            assertTrue(pane.isLiveResize());
            assertTrue(pane.isAnimated());
            assertEquals(Duration.millis(200.0), pane.getAnimationDuration());
            assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
            assertEquals(RXDashboardLayout.builder(12).build(), pane.getLayout());
            pane.setEditable(false);
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("editable")));

            RXDashboardCard card = new RXDashboardCard();
            assertTrue(card.getStyleClass().contains("rx-dashboard-card"));
            assertEquals(-1, card.getColumn());
            assertEquals(-1, card.getRow());
            assertEquals(1, card.getColumnSpan());
            assertEquals(1, card.getRowSpan());
            assertTrue(card.isMoveEnabled());
            assertTrue(card.isResizeEnabled());
            assertFalse(card.isLocked());
            assertEquals(1, card.getMinColumnSpan());
            assertEquals(Integer.MAX_VALUE, card.getMaxColumnSpan());
            card.setLocked(true);
            assertTrue(card.getPseudoClassStates().contains(PseudoClass.getPseudoClass("locked")));
        });
    }

    // ==================== Adding and removing ====================

    @Test
    public void addingWithoutPositionTakesTheFirstFreeArea() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard a = card("a", 2, 1);
            RXDashboardCard b = card("b", 1, 1);
            RXDashboardCard c = card("c", 2, 1);
            pane.getCards().addAll(a, b, c);
            assertPlacement(a, 0, 0, 2, 1);
            assertPlacement(b, 2, 0, 1, 1);
            assertPlacement(c, 0, 1, 2, 1);
            assertEquals(layout(4, "a", 0, 0, 2, 1, "b", 2, 0, 1, 1, "c", 0, 1, 2, 1), pane.getLayout());
        });
    }

    @Test
    public void addingWithPositionFollowsTheCollisionPolicy() throws Exception {
        fx(() -> {
            RXDashboardPane push = caseOne();
            RXDashboardCard d = new RXDashboardCard("d", null, 2, 0, 1, 1);
            push.getCards().add(d);
            assertPlacement(d, 2, 0, 1, 1);
            assertPlacement(find(push, "B"), 2, 1, 1, 1);
            assertPlacement(find(push, "C"), 2, 2, 1, 1);

            RXDashboardPane block = caseOne();
            block.setCollisionPolicy(CollisionPolicy.BLOCK);
            RXDashboardCard e = new RXDashboardCard("e", null, 2, 0, 1, 1);
            block.getCards().add(e);
            assertPlacement(e, 3, 0, 1, 1);
            assertPlacement(find(block, "B"), 2, 0, 1, 1);

            RXDashboardPane locked = caseOne();
            find(locked, "B").setLocked(true);
            RXDashboardCard f = new RXDashboardCard("f", null, 2, 0, 1, 1);
            locked.getCards().add(f);
            assertPlacement(f, 3, 0, 1, 1);

            RXDashboardPane clamped = caseOne();
            RXDashboardCard g = new RXDashboardCard("g", null, 9, 0, 1, 1);
            clamped.getCards().add(g);
            assertPlacement(g, 3, 0, 1, 1);
        });
    }

    @Test
    public void cardsAddedTogetherArePlacedInListOrder() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard first = new RXDashboardCard("first", null, 0, 0, 1, 1);
            RXDashboardCard second = new RXDashboardCard("second", null, 0, 0, 1, 1);
            pane.getCards().addAll(first, second);
            assertPlacement(second, 0, 0, 1, 1);
            assertPlacement(first, 0, 1, 1, 1);
        });
    }

    @Test
    public void nullsAndRepeatsAreIgnoredAndRemovingKeepsTheOthers() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            pane.getCards().add(null);
            pane.getCards().add(a);
            assertEquals(3, cardChildren(pane).size());
            pane.getCards().remove(pane.getCards().size() - 1);
            assertSame(pane, a.getParent());
            pane.getCards().removeIf(card -> card != null && "B".equals(card.getCardId()));
            assertPlacement(a, 0, 0, 2, 1);
            assertPlacement(find(pane, "C"), 2, 1, 1, 1);
            assertEquals(layout(4, "A", 0, 0, 2, 1, "C", 2, 1, 1, 1), pane.getLayout());
        });
    }

    @Test
    public void aCardAddedToAnotherDashboardLeavesThisOne() throws Exception {
        fx(() -> {
            RXDashboardPane first = caseOne();
            RXDashboardPane second = pane(4);
            RXDashboardCard a = find(first, "A");
            second.getCards().add(a);
            assertFalse(first.getCards().contains(a));
            assertFalse(first.getChildrenUnmodifiable().contains(a));
            assertSame(second, a.getParent());
            assertPlacement(a, 0, 0, 2, 1);
            assertTrue(first.moveCard(find(first, "B"), 0, 0));
            assertThrows(IllegalArgumentException.class, () -> first.moveCard(a, 0, 0));
        });
    }

    // ==================== Editing by code ====================

    @Test
    public void moveCardPushesAndRejects() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            assertTrue(pane.moveCard(a, 1, 0));
            assertPlacement(a, 1, 0, 2, 1);
            assertPlacement(find(pane, "B"), 2, 1, 1, 1);
            assertPlacement(find(pane, "C"), 2, 2, 1, 1);

            RXDashboardPane locked = caseOne();
            find(locked, "B").setLocked(true);
            RXDashboardLayout before = locked.getLayout();
            assertFalse(locked.moveCard(find(locked, "A"), 1, 0));
            assertEquals(before, locked.getLayout());

            RXDashboardPane block = caseOne();
            block.setCollisionPolicy(CollisionPolicy.BLOCK);
            assertFalse(block.moveCard(find(block, "A"), 1, 0));
            assertTrue(block.moveCard(find(block, "A"), 0, 2));

            RXDashboardPane clamped = caseOne();
            assertTrue(clamped.moveCard(find(clamped, "A"), 20, -3));
            assertPlacement(find(clamped, "A"), 2, 0, 2, 1);

            assertThrows(NullPointerException.class, () -> pane.moveCard(null, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> pane.moveCard(new RXDashboardCard(), 0, 0));
        });
    }

    @Test
    public void codeMovesALockedCard() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            a.setLocked(true);
            assertTrue(pane.moveCard(a, 1, 0));
            assertPlacement(find(pane, "B"), 2, 1, 1, 1);
            find(pane, "B").setLocked(true);
            assertFalse(pane.moveCard(a, 2, 1));
            assertPlacement(a, 1, 0, 2, 1);
        });
    }

    @Test
    public void resizeCardKeepsTheTopLeftAndClamps() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            assertTrue(pane.resizeCard(a, 3, 2));
            assertPlacement(a, 0, 0, 3, 2);
            assertPlacement(find(pane, "B"), 2, 2, 1, 1);
            assertPlacement(find(pane, "C"), 2, 3, 1, 1);
            RXDashboardCard c = find(pane, "C");
            assertTrue(pane.resizeCard(c, 9, 0));
            assertPlacement(c, 2, 3, 2, 1);
            c.setMinColumnSpan(3);
            assertPlacement(c, 1, 3, 3, 1);
            assertTrue(pane.resizeCard(c, 1, 1));
            assertPlacement(c, 1, 3, 3, 1);
        });
    }

    @Test
    public void compactClosesHoles() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            pane.resizeCard(a, 3, 2);
            pane.resizeCard(a, 2, 1);
            assertPlacement(find(pane, "B"), 2, 2, 1, 1);
            pane.compact();
            assertEquals(layout(4, "A", 0, 0, 2, 1, "B", 2, 0, 1, 1, "C", 2, 1, 1, 1), pane.getLayout());
        });
    }

    @Test
    public void raisingASpanLimitWidensTheCard() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard b = find(pane, "B");
            b.setMinColumnSpan(2);
            assertPlacement(b, 2, 0, 2, 1);
            b.setMaxRowSpan(0);
            assertPlacement(b, 2, 0, 2, 1);

            RXDashboardPane block = caseOne();
            block.setCollisionPolicy(CollisionPolicy.BLOCK);
            RXDashboardCard c = find(block, "C");
            c.setMinRowSpan(2);
            assertPlacement(c, 2, 1, 1, 2);
            RXDashboardCard blockedB = find(block, "B");
            blockedB.setMinRowSpan(2);
            assertPlacement(blockedB, 3, 0, 1, 2);
        });
    }

    // ==================== Snapshots ====================

    @Test
    public void applyLayoutRestoresTheExample() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            pane.applyLayout(RXDashboardLayout.decode("rxdash/1;columns=4;A=0,0,3,2;B=3,0,1,1;C=3,1,1,1;gone=0,9,1,1"));
            assertPlacement(find(pane, "A"), 0, 0, 3, 2);
            assertPlacement(find(pane, "B"), 3, 0, 1, 1);
            assertPlacement(find(pane, "C"), 3, 1, 1, 1);
            assertEquals("rxdash/1;columns=4;A=0,0,3,2;B=3,0,1,1;C=3,1,1,1", pane.getLayout().encode());
            assertThrows(NullPointerException.class, () -> pane.applyLayout(null));
        });
    }

    @Test
    public void applyLayoutCorrectsWhatDoesNotFit() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(card("A", 1, 1), card("B", 1, 1), card("C", 1, 1));
            pane.applyLayout(layout(12, "A", 0, 0, 6, 2, "B", 4, 1, 3, 1, "C", 10, 0, 5, 1));
            assertEquals(layout(12, "A", 0, 0, 6, 2, "B", 4, 2, 3, 1, "C", 7, 0, 5, 1), pane.getLayout());
            pane.applyLayout(layout(12, "A", -2, -1, 0, -5, "B", 0, 0, 1, 1, "C", 0, 0, 1, 1));
            assertEquals(layout(12, "A", 0, 0, 1, 1, "B", 0, 1, 1, 1, "C", 0, 2, 1, 1), pane.getLayout());
        });
    }

    @Test
    public void applyLayoutConvertsColumnCounts() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            pane.getCards().addAll(card("A", 1, 1), card("B", 1, 1), card("C", 1, 1));
            pane.applyLayout(layout(12, "A", 0, 0, 6, 1, "B", 6, 0, 3, 1, "C", 6, 1, 3, 1));
            assertEquals(layout(4, "A", 0, 0, 2, 1, "B", 2, 0, 1, 1, "C", 2, 1, 1, 1), pane.getLayout());
        });
    }

    @Test
    public void cardsTheSnapshotLacksTryTheirOwnPlace() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            RXDashboardCard d = new RXDashboardCard("D", null, 0, 3, 3, 1);
            RXDashboardCard noId = new RXDashboardCard(null, null, 0, 0, 3, 1);
            pane.getCards().addAll(card("A", 1, 1), card("B", 1, 1), card("C", 1, 1), d, noId);
            pane.applyLayout(layout(12, "A", 0, 0, 6, 1, "B", 6, 0, 3, 1, "C", 6, 1, 3, 1));
            assertPlacement(d, 0, 3, 3, 1);
            assertPlacement(noId, 9, 0, 3, 1);
        });
    }

    @Test
    public void duplicateIdsLetTheFirstCardWin() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard first = card("x", 1, 1);
            RXDashboardCard second = card("x", 1, 1);
            RXDashboardCard empty = card("", 1, 1);
            pane.getCards().addAll(first, second, empty);
            assertEquals(layout(4, "x", 0, 0, 1, 1), pane.getLayout());
            pane.applyLayout(layout(4, "x", 3, 0, 1, 1));
            assertPlacement(first, 3, 0, 1, 1);
            assertPlacement(second, 1, 0, 1, 1);
            second.setCardId("y");
            assertEquals(layout(4, "x", 3, 0, 1, 1, "y", 1, 0, 1, 1), pane.getLayout());
            pane.getCards().remove(first);
            second.setCardId("x");
            assertEquals(layout(4, "x", 1, 0, 1, 1), pane.getLayout());
        });
    }

    // ==================== Column count and the base layout ====================

    @Test
    public void columnRoundTripWithoutEditsRestoresTheLayout() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(new RXDashboardCard("A", null, 0, 0, 5, 1),
                    new RXDashboardCard("B", null, 5, 0, 7, 1), new RXDashboardCard("C", null, 1, 1, 3, 2));
            RXDashboardLayout original = pane.getLayout();
            pane.setColumnCount(4);
            assertEquals(4, pane.getLayout().getColumnCount());
            pane.getCards().add(card("D", 1, 1));
            pane.setColumnCount(12);
            RXDashboardLayout back = pane.getLayout();
            for (String id : List.of("A", "B", "C")) {
                assertEquals(original.getPlacement(id), back.getPlacement(id), id);
            }
        });
    }

    @Test
    public void afterTheFirstLayoutAColumnChangeConvertsAndRoundTrips() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(new RXDashboardCard("A", null, 0, 0, 6, 1),
                    new RXDashboardCard("B", null, 6, 0, 3, 1), new RXDashboardCard("C", null, 6, 1, 3, 1));
            RXDashboardLayout original = pane.getLayout();
            layout(pane, 600.0, 400.0);
            pane.setColumnCount(4);
            assertEquals(layout(4, "A", 0, 0, 2, 1, "B", 2, 0, 1, 1, "C", 2, 1, 1, 1), pane.getLayout());
            layout(pane, 600.0, 400.0);
            pane.setColumnCount(12);
            assertEquals(original, pane.getLayout());
        });
    }

    @Test
    public void beforeTheFirstLayoutAColumnChangePlacesCardsByTheirRequest() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(new RXDashboardCard("A", null, 0, 0, 2, 1), card("B", 2, 1),
                    new RXDashboardCard("C", null, 3, 0, 1, 2));
            pane.setColumnCount(4);
            // As if added at 4 columns in list order: B takes the first free area, then C's request pushes it.
            assertEquals(layout(4, "A", 0, 0, 2, 1, "B", 2, 2, 2, 1, "C", 3, 0, 1, 2), pane.getLayout());
        });
    }

    @Test
    public void anEditAtTheNarrowCountConvertsBackLossily() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(card("A", 1, 1), card("B", 1, 1), card("C", 1, 1));
            pane.applyLayout(layout(12, "A", 0, 0, 6, 1, "B", 6, 0, 3, 1, "C", 6, 1, 3, 1));
            pane.setColumnCount(4);
            assertTrue(pane.moveCard(find(pane, "B"), 3, 0));
            pane.setColumnCount(12);
            assertEquals(layout(12, "A", 0, 0, 6, 1, "B", 9, 0, 3, 1, "C", 6, 1, 3, 1), pane.getLayout());
        });
    }

    @Test
    public void aSnapshotAppliedBeforeTheColumnCountIsRestoredExactly() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(card("A", 1, 1), card("B", 1, 1), card("C", 1, 1), card("D", 1, 1));
            RXDashboardLayout saved = layout(24, "A", 1, 0, 3, 1, "B", 4, 0, 5, 2, "C", 13, 0, 11, 1,
                    "D", 9, 3, 1, 1);
            pane.applyLayout(saved);
            assertEquals(12, pane.getLayout().getColumnCount());
            stageFor(pane);
            pane.setStyle("-rx-column-count: 24;");
            pane.applyCss();
            assertEquals(24, pane.getColumnCount());
            assertEquals(saved, pane.getLayout());
        });
    }

    @Test
    public void invalidColumnCountsAreRejected() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            layout(pane, 400.0, 300.0);
            RXDashboardLayout before = pane.getLayout();
            assertThrows(IllegalArgumentException.class, () -> pane.setColumnCount(0));
            assertEquals(4, pane.getColumnCount());
            assertEquals(before, pane.getLayout());
            assertThrows(IllegalArgumentException.class, () -> pane.setColumnCount(-7));
            assertEquals(4, pane.getColumnCount());

            SimpleIntegerProperty source = new SimpleIntegerProperty(4);
            pane.columnCountProperty().bind(source);
            source.set(0);
            assertEquals(before, pane.getLayout());
            source.set(8);
            assertEquals(8, pane.getLayout().getColumnCount());
            assertPlacement(find(pane, "A"), 0, 0, 4, 1);
        });
    }

    @Test
    public void columnCountsAboveTheCapCountAsTheCap() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(5000);
            pane.getCards().add(card("A", 1, 1));
            assertEquals(4096, pane.getLayout().getColumnCount());
        });
    }

    // ==================== Notifications ====================

    @Test
    public void cardPropertiesAreNotifiedBeforeTheLayout() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            List<String> order = new ArrayList<>();
            a.columnProperty().addListener((obs, old, value) -> order.add("column " + value + " layout "
                    + pane.getLayout().getPlacement("A").column()));
            pane.layoutProperty().addListener((obs, old, value) -> order.add("layout " + value.getPlacement("A")
                    .column() + " card " + a.getColumn()));
            pane.moveCard(a, 1, 0);
            assertEquals(List.of("column 1 layout 0", "layout 1 card 1"), order);
        });
    }

    @Test
    public void editingFromAPlacementListenerThrowsAndThePaneRecovers() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            List<Throwable> errors = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, e) -> errors.add(e));
            try {
                a.columnProperty().addListener((obs, old, value) -> pane.compact());
                pane.layoutProperty().addListener((obs, old, value) -> pane.applyLayout(value));
                pane.moveCard(a, 1, 0);
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }
            assertEquals(2, errors.size());
            assertTrue(errors.stream().allMatch(e -> e instanceof IllegalStateException), errors.toString());
            assertPlacement(a, 1, 0, 2, 1);
            assertTrue(pane.moveCard(find(pane, "C"), 0, 3));
        });
    }

    @Test
    public void changesFromAListenerApplyAfterTheNotification() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard extra = card("extra", 1, 1);
            List<RXDashboardLayout> seen = new ArrayList<>();
            pane.layoutProperty().addListener((obs, old, value) -> {
                seen.add(value);
                if (seen.size() == 1) {
                    pane.getCards().add(extra);
                    pane.setColumnCount(8);
                    find(pane, "C").setMinColumnSpan(2);
                }
            });
            pane.moveCard(find(pane, "A"), 1, 0);
            assertEquals(2, seen.size());
            assertEquals(4, seen.get(0).getColumnCount());
            assertEquals(8, seen.get(1).getColumnCount());
            assertSame(pane, extra.getParent());
            assertEquals(pane.getLayout(), seen.get(1));
            assertTrue(pane.getLayout().getPlacement("C").columnSpan() >= 2);
            assertNoOverlap(pane);
        });
    }

    // ==================== Sizes ====================

    @Test
    public void sizesFollowTheGrid() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            assertEquals(0.0, pane.prefHeight(-1), EPSILON);
            pane.setPadding(new Insets(5, 6, 7, 8));
            pane.setRowHeight(100);
            pane.setHgap(10);
            pane.setVgap(20);
            pane.setMinColumnWidth(30);
            pane.getCards().addAll(new RXDashboardCard("A", null, 0, 2, 1, 3));
            assertEquals(8 + 4 * 30 + 3 * 10 + 6, pane.minWidth(-1), EPSILON);
            assertEquals(8 + 4 * 100 + 3 * 10 + 6, pane.prefWidth(-1), EPSILON);
            assertEquals(5 + 5 * 100 + 4 * 20 + 7, pane.prefHeight(-1), EPSILON);
            assertEquals(pane.prefHeight(-1), pane.minHeight(-1), EPSILON);
            pane.setRowHeight(Double.NaN);
            pane.setHgap(-4);
            assertEquals(8 + 4 * 80 + 6, pane.prefWidth(-1), EPSILON);
        });
    }

    // ==================== Geometry ====================

    @Test
    public void cardsFillTheirCellsOnTheDeviceGrid() throws Exception {
        fx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                RXDashboardPane pane = pane(12);
                pane.setAnimated(false);
                pane.setHgap(4);
                for (int i = 0; i < 12; i++) {
                    pane.getCards().add(new RXDashboardCard("c" + i, null, i % 6 * 2, i / 6, 1 + i % 2, 1 + i % 3));
                }
                Stage stage = stageFor(pane);
                stage.setRenderScaleX(scale);
                stage.setRenderScaleY(scale);
                for (double width = 600.0; width < 640.0; width += 0.7) {
                    layout(pane, width, 600.0);
                    DashboardGrid grid = pane.computeGrid();
                    for (RXDashboardCard card : pane.getCards()) {
                        Placement p = new Placement(card.getColumn(), card.getRow(), card.getColumnSpan(),
                                card.getRowSpan());
                        assertEquals(grid.columnX(p.column()), card.getLayoutX(), EPSILON);
                        assertEquals(grid.columnRight(p.column(), p.columnSpan()),
                                card.getLayoutX() + card.getWidth(), EPSILON);
                        assertEquals(grid.rowY(p.row()), card.getLayoutY(), EPSILON);
                        assertEquals(grid.rowBottom(p.row(), p.rowSpan()), card.getLayoutY() + card.getHeight(),
                                EPSILON);
                        assertOnGrid(card.getLayoutX(), scale);
                        assertOnGrid(card.getLayoutX() + card.getWidth(), scale);
                        assertOnGrid(card.getLayoutY(), scale);
                        assertOnGrid(card.getLayoutY() + card.getHeight(), scale);
                    }
                    assertTrue(grid.columnRight(0, 12) <= width + EPSILON);
                }
            }
        });
    }

    @Test
    public void twoColumnsFillTheir225DevicePixelSlot() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.setHgap(4);
            RXDashboardCard card = new RXDashboardCard("x", null, 2, 0, 2, 1);
            pane.getCards().add(card);
            Stage stage = stageFor(pane);
            stage.setRenderScaleX(1.75);
            stage.setRenderScaleY(1.75);
            layout(pane, 1385.0 / 1.75, 400.0);
            assertEquals(232.0, card.getLayoutX() * 1.75, EPSILON);
            assertEquals(457.0, (card.getLayoutX() + card.getWidth()) * 1.75, EPSILON);
        });
    }

    @Test
    public void withoutSnappingCardsSitOnTheExactLines() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(7);
            pane.setSnapToPixel(false);
            pane.setHgap(3.3);
            pane.setVgap(2.2);
            pane.setRowHeight(33.3);
            RXDashboardCard card = new RXDashboardCard("x", null, 2, 3, 3, 2);
            pane.getCards().add(card);
            layout(pane, 500.3, 400.0);
            double track = (500.3 - 6 * 3.3) / 7;
            assertEquals(2 * (track + 3.3), card.getLayoutX(), EPSILON);
            assertEquals(3 * track + 2 * 3.3, card.getWidth(), EPSILON);
            assertEquals(3 * (33.3 + 2.2), card.getLayoutY(), EPSILON);
            assertEquals(2 * 33.3 + 2.2, card.getHeight(), EPSILON);
        });
    }

    @Test
    public void aRenderScaleChangeLaysThePaneOutAgain() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            Stage stage = stageFor(pane);
            stage.setRenderScaleX(1.25);
            layout(pane, 401.0, 300.0);
            layout(pane, 401.0, 300.0);
            assertFalse(pane.isNeedsLayout());
            stage.setRenderScaleX(1.75);
            assertTrue(pane.isNeedsLayout());
            layout(pane, 401.0, 300.0);
            assertOnGrid(find(pane, "B").getLayoutX(), 1.75);
        });
    }

    @Test
    public void thePaneSettlesAndChildrenFollowReadingOrder() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            Stage stage = stageFor(pane);
            stage.getScene().getRoot().applyCss();
            layout(pane, 400.0, 400.0);
            RXDashboardCard a = find(pane, "A");
            pane.moveCard(a, 0, 3);
            layout(pane, 400.0, 400.0);
            layout(pane, 400.0, 400.0);
            assertFalse(pane.isNeedsLayout());
            pane.layout();
            assertFalse(pane.isNeedsLayout());
            assertEquals(List.of(find(pane, "B"), find(pane, "C"), a), cardChildren(pane));
            assertEquals("A", pane.getCards().get(0).getCardId());
        });
    }

    @Test
    public void aMovedCardGlidesAndAResizeDoesNot() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            stageFor(pane);
            layout(pane, 400.0, 400.0);
            layout(pane, 400.0, 400.0);
            RXDashboardCard b = find(pane, "B");
            double oldY = b.getLayoutY();
            pane.moveCard(find(pane, "A"), 1, 0);
            layout(pane, 400.0, 400.0);
            assertTrue(b.getLayoutY() > oldY);
            assertEquals(oldY, b.getLayoutY() + b.getTranslateY(), 0.5, "starts where it showed");
            pane.setAnimated(false);
            assertEquals(0.0, b.getTranslateY(), EPSILON);
            pane.setAnimated(true);
            layout(pane, 400.0, 400.0);
            layout(pane, 520.0, 400.0);
            assertEquals(0.0, b.getTranslateX(), EPSILON);
        });
    }

    // ==================== Content ====================

    @Test
    public void contentFillsTheCardAndOverflowLeavesSizesAlone() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            pane.setAnimated(false);
            pane.setHgap(0);
            Region fill = new Region();
            Region wide = new Region();
            wide.setMinWidth(300);
            Circle circle = new Circle(80);
            RXDashboardCard a = new RXDashboardCard("a", fill, 0, 0, 1, 1);
            RXDashboardCard b = new RXDashboardCard("b", wide, 1, 0, 1, 1);
            RXDashboardCard c = new RXDashboardCard("c", circle, 2, 0, 1, 1);
            for (RXDashboardCard card : List.of(a, b, c)) {
                card.setPadding(new Insets(5));
            }
            pane.getCards().addAll(a, b, c);
            double prefWidth = pane.prefWidth(-1);
            double prefHeight = pane.prefHeight(-1);
            layout(pane, 400.0, 300.0);
            for (RXDashboardCard card : List.of(a, b, c)) {
                card.layout();
                assertEquals(100.0, card.getWidth(), EPSILON);
                assertEquals(80.0, card.getHeight(), EPSILON);
            }
            assertEquals(90.0, fill.getWidth(), EPSILON);
            assertEquals(70.0, fill.getHeight(), EPSILON);
            assertEquals(5.0, fill.getLayoutX(), EPSILON);
            assertEquals(300.0, wide.getWidth(), EPSILON);
            assertEquals(5.0, wide.getLayoutX(), EPSILON);
            assertEquals(50.0, circle.getBoundsInParent().getCenterX(), EPSILON);
            assertEquals(40.0, circle.getBoundsInParent().getCenterY(), EPSILON);
            assertEquals(prefWidth, pane.prefWidth(-1), EPSILON);
            assertEquals(prefHeight, pane.prefHeight(-1), EPSILON);
            assertEquals(100.0, b.getLayoutX(), EPSILON);
            assertEquals(200.0, c.getLayoutX(), EPSILON);
        });
    }

    @Test
    public void replacingContentKeepsThePlacement() throws Exception {
        fx(() -> {
            RXDashboardPane pane = caseOne();
            RXDashboardCard a = find(pane, "A");
            Region first = new Region();
            Region second = new Region();
            a.setContent(first);
            a.setContent(second);
            assertFalse(a.getChildrenUnmodifiable().contains(first));
            assertSame(a, second.getParent());
            a.setContent(null);
            assertTrue(a.getChildrenUnmodifiable().isEmpty());
            assertPlacement(a, 0, 0, 2, 1);
        });
    }

    // ==================== FXML ====================

    @Test
    public void fxmlBuildsThePane() throws Exception {
        fx(() -> {
            RXDashboardPane pane = load("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <?import io.github.leewyatt.rxcontrols.layout.RXDashboardPane?>
                    <?import io.github.leewyatt.rxcontrols.layout.RXDashboardCard?>
                    <?import javafx.scene.layout.Region?>
                    <RXDashboardPane xmlns="http://javafx.com/javafx/17" columnCount="4" rowHeight="180">
                        <RXDashboardCard cardId="trend" column="0" row="0" columnSpan="2" rowSpan="1">
                            <content><Region/></content>
                        </RXDashboardCard>
                        <RXDashboardCard cardId="customers" column="2" row="0"/>
                        <RXDashboardCard cardId="kpi" columnSpan="2"/>
                    </RXDashboardPane>
                    """);
            assertEquals(4, pane.getColumnCount());
            assertEquals(180.0, pane.getRowHeight());
            assertEquals(layout(4, "trend", 0, 0, 2, 1, "customers", 2, 0, 1, 1, "kpi", 0, 1, 2, 1),
                    pane.getLayout());
            assertNotSame(null, find(pane, "trend").getContent());
        });
    }

    // ==================== Helpers ====================

    // Case 1 of the design research: 4 columns, A(0,0,2,1), B(2,0,1,1), C(2,1,1,1).
    private static RXDashboardPane caseOne() {
        RXDashboardPane pane = pane(4);
        pane.getCards().addAll(new RXDashboardCard("A", null, 0, 0, 2, 1),
                new RXDashboardCard("B", null, 2, 0, 1, 1), new RXDashboardCard("C", null, 2, 1, 1, 1));
        return pane;
    }

    private static RXDashboardPane pane(int columns) {
        RXDashboardPane pane = new RXDashboardPane();
        pane.setColumnCount(columns);
        return pane;
    }

    private static RXDashboardCard card(String id, int columnSpan, int rowSpan) {
        return new RXDashboardCard(id, null, -1, -1, columnSpan, rowSpan);
    }

    private static RXDashboardCard find(RXDashboardPane pane, String id) {
        for (RXDashboardCard card : pane.getCards()) {
            if (card != null && id.equals(card.getCardId())) {
                return card;
            }
        }
        throw new AssertionError("no card " + id);
    }

    private static RXDashboardLayout layout(int columns, Object... idsAndCells) {
        RXDashboardLayout.Builder builder = RXDashboardLayout.builder(columns);
        for (int i = 0; i < idsAndCells.length; i += 5) {
            builder.card((String) idsAndCells[i], (int) idsAndCells[i + 1], (int) idsAndCells[i + 2],
                    (int) idsAndCells[i + 3], (int) idsAndCells[i + 4]);
        }
        return builder.build();
    }

    private static void assertPlacement(RXDashboardCard card, int column, int row, int columnSpan, int rowSpan) {
        assertEquals(new Placement(column, row, columnSpan, rowSpan),
                new Placement(card.getColumn(), card.getRow(), card.getColumnSpan(), card.getRowSpan()),
                card.getCardId());
    }

    private static void assertNoOverlap(RXDashboardPane pane) {
        List<RXDashboardCard> cards = pane.getCards();
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                Placement a = placement(cards.get(i));
                Placement b = placement(cards.get(j));
                assertFalse(DashboardSolver.overlaps(a, b), a + " overlaps " + b);
            }
        }
    }

    // The pane's children without its placeholder, which is always the first child.
    private static List<Node> cardChildren(RXDashboardPane pane) {
        return pane.getChildrenUnmodifiable().subList(1, pane.getChildrenUnmodifiable().size());
    }

    private static Placement placement(RXDashboardCard card) {
        return new Placement(card.getColumn(), card.getRow(), card.getColumnSpan(), card.getRowSpan());
    }

    private static void assertOnGrid(double value, double scale) {
        double pixels = value * scale;
        assertEquals(Math.rint(pixels), pixels, 1e-6, value + " at scale " + scale);
    }

    // The stage is never shown: it only supplies a scene and the render scale the pane snaps to.
    private static Stage stageFor(Region root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        return stage;
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
    }

    private static RXDashboardPane load(String fxml) {
        try {
            return new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fx(Runnable action) throws Exception {
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
        if (!latch.await(60, TimeUnit.SECONDS)) {
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
