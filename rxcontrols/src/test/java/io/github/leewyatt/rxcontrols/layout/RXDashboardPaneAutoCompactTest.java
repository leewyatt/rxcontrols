package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXDashboardPane} keeping its cards packed upward ({@code autoCompact}), without gestures: adding,
 * removing, unlocking and resizing cards, moving them by code as a straight drag, the {@code BLOCK} policy, applying a
 * snapshot with holes, turning packing on and off, column-count round trips, changes from listeners and FXML.
 */
public class RXDashboardPaneAutoCompactTest {

    @BeforeAll
    public static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        fx(Label::new);
    }

    @Test
    public void cardsRiseAsFarAsTheyCanWhenAddedAndWhenOthersLeave() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard a = card("A", 0, 0, 2, 1);
            RXDashboardCard b = card("B", 0, 3, 1, 1);
            pane.getCards().addAll(a, b);
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
            pane.getCards().remove(a);
            assertEquals(cells("B:0,0"), cells(pane));
        });
    }

    @Test
    public void moveCardDragsAlongARowExchangingPlaces() throws Exception {
        fx(() -> {
            RXDashboardPane pane = rowOfFour();
            assertTrue(pane.moveCard(find(pane, "1"), 2, 0));
            assertEquals(cells("1:2,0 2:0,0 3:1,0 4:3,0 T:0,1 U:2,1"), cells(pane));

            RXDashboardPane other = rowOfFour();
            assertTrue(other.moveCard(find(other, "4"), 0, 0));
            assertEquals(cells("1:1,0 2:2,0 3:3,0 4:0,0 T:0,1 U:2,1"), cells(other));
        });
    }

    @Test
    public void movingAWideCardDownOneRowLetsTheCardsBelowTakeItsRow() throws Exception {
        fx(() -> {
            RXDashboardPane pane = screenshotBoard();
            assertTrue(pane.moveCard(find(pane, "7"), 1, 1));
            assertEquals(cells("9:0,0 2:1,0 8:3,0 4:5,0 1:0,1 7:1,1 11:0,2 5:0,3 6:0,4"), cells(pane));
        });
    }

    @Test
    public void movingIntoEmptySpaceDropsAtTheHighestRowAndReportsWhetherAnythingChanged() throws Exception {
        fx(() -> {
            RXDashboardPane pane = screenshotBoard();
            assertTrue(pane.moveCard(find(pane, "8"), 4, 5));
            assertEquals("4,1", cells(pane).get("8"));

            RXDashboardPane alone = pane(4);
            RXDashboardCard a = card("A", 0, 0, 1, 1);
            alone.getCards().add(a);
            assertFalse(alone.moveCard(a, 0, 5), "the card rises back to where it was");
            assertEquals(cells("A:0,0"), cells(alone));
        });
    }

    @Test
    public void lockedCardsStayAndHoldUpTheCardsBelowUntilUnlocked() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(6);
            RXDashboardCard a = card("A", 1, 0, 2, 1);
            RXDashboardCard locked = card("L", 1, 1, 1, 1);
            locked.setLocked(true);
            RXDashboardCard b = card("B", 1, 2, 1, 1);
            pane.getCards().addAll(a, locked, b);
            pane.getCards().remove(a);
            assertEquals(cells("L:1,1 B:1,2"), cells(pane));
            locked.setLocked(false);
            assertEquals(cells("L:1,0 B:1,1"), cells(pane));
        });
    }

    @Test
    public void aLockedCardMovedByCodeStaysWhereItIsPut() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard locked = card("L", 0, 0, 1, 1);
            locked.setLocked(true);
            pane.getCards().add(locked);
            assertTrue(pane.moveCard(locked, 2, 3));
            assertEquals(cells("L:2,3"), cells(pane));
        });
    }

    @Test
    public void shrinkingACardLetsThePushedCardsComeBack() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            RXDashboardCard a = card("A", 0, 0, 1, 1);
            pane.getCards().addAll(a, card("B", 0, 1, 1, 1));
            assertTrue(pane.resizeCard(a, 1, 2));
            assertEquals(cells("A:0,0 B:0,2"), cells(pane));
            assertTrue(pane.resizeCard(a, 1, 1));
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
        });
    }

    @Test
    public void blockRejectsCoveringCardsAndStillPacks() throws Exception {
        fx(() -> {
            RXDashboardPane pane = rowOfFour();
            pane.setCollisionPolicy(CollisionPolicy.BLOCK);
            Map<String, String> before = cells(pane);
            assertFalse(pane.moveCard(find(pane, "1"), 1, 0));
            assertEquals(before, cells(pane));

            RXDashboardPane stack = pane(4);
            stack.setCollisionPolicy(CollisionPolicy.BLOCK);
            RXDashboardCard a = card("A", 0, 0, 1, 1);
            stack.getCards().addAll(a, card("B", 0, 1, 1, 1));
            assertTrue(stack.moveCard(a, 1, 0));
            assertEquals(cells("A:1,0 B:0,0"), cells(stack));
        });
    }

    @Test
    public void anAppliedSnapshotWithHolesIsPacked() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            pane.getCards().addAll(card("A", -1, -1, 1, 1), card("B", -1, -1, 1, 1));
            RXDashboardLayout snapshot = RXDashboardLayout.builder(4).card("A", 0, 0, 1, 1).card("B", 0, 3, 1, 1)
                    .build();
            pane.applyLayout(snapshot);
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
            assertNotEquals(snapshot, pane.getLayout());
            pane.compact();
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
        });
    }

    @Test
    public void turningPackingOnPacksAtOnceAndTurningItOffMovesNothing() throws Exception {
        fx(() -> {
            RXDashboardPane pane = new RXDashboardPane();
            pane.setAutoCompact(false);
            pane.setColumnCount(4);
            pane.getCards().addAll(card("A", 0, 0, 1, 1), card("B", 0, 3, 1, 1));
            // Laid out: from now on a change applies to the layout as it is (before, it would place every card again).
            laidOut(pane);
            assertEquals(cells("A:0,0 B:0,3"), cells(pane));
            pane.setAutoCompact(true);
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
            pane.setAutoCompact(false);
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
            // The packed layout is the base a column-count round trip restores.
            pane.setColumnCount(2);
            pane.setColumnCount(4);
            assertEquals(cells("A:0,0 B:0,1"), cells(pane));
        });
    }

    @Test
    public void aColumnRoundTripRestoresThePackedLayout() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(12);
            pane.getCards().addAll(card("A", 0, 0, 5, 1), card("B", 5, 0, 7, 2), card("C", 0, 1, 3, 2),
                    card("D", 3, 1, 2, 1));
            Map<String, String> before = cells(pane);
            pane.setColumnCount(4);
            pane.setColumnCount(12);
            assertEquals(before, cells(pane));
        });
    }

    @Test
    public void turningPackingOnFromALayoutListenerWaitsForTheNotification() throws Exception {
        fx(() -> {
            RXDashboardPane pane = new RXDashboardPane();
            pane.setAutoCompact(false);
            pane.setColumnCount(4);
            RXDashboardCard a = card("A", 0, 0, 1, 1);
            pane.getCards().addAll(a, card("B", 0, 3, 1, 1));
            laidOut(pane);
            pane.layoutProperty().addListener((obs, old, value) -> pane.setAutoCompact(true));
            assertDoesNotThrow(() -> pane.moveCard(a, 1, 0));
            assertEquals(cells("A:1,0 B:0,0"), cells(pane));
        });
    }

    @Test
    public void fxmlKeepsHolesWhenItTurnsPackingOffAfterTheCards() throws Exception {
        fx(() -> {
            String cards = """
                        <RXDashboardCard cardId="a" column="0" row="0"/>
                        <RXDashboardCard cardId="b" column="0" row="3"/>
                        <RXDashboardCard cardId="c" column="1" row="0" columnSpan="2"/>
                    </RXDashboardPane>
                    """;
            RXDashboardPane off = load(header("columnCount=\"4\" autoCompact=\"false\"") + cards);
            assertEquals(cells("a:0,0 b:0,3 c:1,0"), cells(off));
            RXDashboardPane on = load(header("columnCount=\"4\"") + cards);
            assertEquals(cells("a:0,0 b:0,1 c:1,0"), cells(on));
        });
    }

    @Test
    public void turningPackingOffBeforeTheFirstLayoutKeepsWhatWasAskedFor() throws Exception {
        fx(() -> {
            RXDashboardPane late = pane(4);
            late.getCards().addAll(card("A", 0, 0, 1, 1), card("B", 0, 3, 1, 1), card("C", 2, 2, 1, 1));
            late.moveCard(find(late, "C"), 1, 0);
            late.setAutoCompact(false);

            RXDashboardPane first = new RXDashboardPane();
            first.setAutoCompact(false);
            first.setColumnCount(4);
            first.getCards().addAll(card("A", 0, 0, 1, 1), card("B", 0, 3, 1, 1), card("C", 2, 2, 1, 1));
            first.moveCard(find(first, "C"), 1, 0);
            assertEquals(first.getLayout(), late.getLayout());
        });
    }

    // ==================== Boards ====================

    // 4 columns: 1 2 3 4 in the first row, T and U (two columns each) below.
    private static RXDashboardPane rowOfFour() {
        RXDashboardPane pane = pane(4);
        pane.getCards().addAll(card("1", 0, 0, 1, 1), card("2", 1, 0, 1, 1), card("3", 2, 0, 1, 1),
                card("4", 3, 0, 1, 1), card("T", 0, 1, 2, 1), card("U", 2, 1, 2, 1));
        return pane;
    }

    // 6 columns: 9 and the five-column 7 on top, then 1 2 . 8 . 4, then 11, 5 and 6 down the first column.
    private static RXDashboardPane screenshotBoard() {
        RXDashboardPane pane = pane(6);
        pane.getCards().addAll(card("9", 0, 0, 1, 1), card("7", 1, 0, 5, 1), card("1", 0, 1, 1, 1),
                card("2", 1, 1, 1, 1), card("8", 3, 1, 1, 1), card("4", 5, 1, 1, 1), card("11", 0, 2, 1, 1),
                card("5", 0, 3, 1, 1), card("6", 0, 4, 1, 1));
        return pane;
    }

    // ==================== Helpers ====================

    private static RXDashboardPane pane(int columns) {
        RXDashboardPane pane = new RXDashboardPane();
        pane.setColumnCount(columns);
        return pane;
    }

    private static void laidOut(RXDashboardPane pane) {
        pane.resize(400.0, 600.0);
        pane.layout();
    }

    private static RXDashboardCard card(String id, int column, int row, int columnSpan, int rowSpan) {
        return new RXDashboardCard(id, null, column, row, columnSpan, rowSpan);
    }

    private static RXDashboardCard find(RXDashboardPane pane, String id) {
        for (RXDashboardCard card : pane.getCards()) {
            if (id.equals(card.getCardId())) {
                return card;
            }
        }
        throw new AssertionError("no card " + id);
    }

    // Each card's column and row, as "x,y", by ID.
    private static Map<String, String> cells(RXDashboardPane pane) {
        Map<String, String> cells = new TreeMap<>();
        for (RXDashboardCard card : pane.getCards()) {
            cells.put(card.getCardId(), card.getColumn() + "," + card.getRow());
        }
        return cells;
    }

    // "A:0,0 B:1,0" as a map.
    private static Map<String, String> cells(String text) {
        Map<String, String> cells = new TreeMap<>();
        for (String entry : text.split(" ")) {
            int colon = entry.indexOf(':');
            cells.put(entry.substring(0, colon), entry.substring(colon + 1));
        }
        return cells;
    }

    private static String header(String attributes) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <?import io.github.leewyatt.rxcontrols.layout.RXDashboardPane?>
                <?import io.github.leewyatt.rxcontrols.layout.RXDashboardCard?>
                <RXDashboardPane xmlns="http://javafx.com/javafx/17" %s>
                """.formatted(attributes);
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
