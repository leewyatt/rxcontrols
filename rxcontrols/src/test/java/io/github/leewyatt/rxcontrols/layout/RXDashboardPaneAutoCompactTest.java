package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 * snapshot with holes, turning packing on and off, column-count round trips, changes from listeners and FXML, cards
 * added together, and a late column count or autoCompact giving what setting it first gives.
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

    @Test
    public void aMoveNextToALockedCardNeverLeavesCardsOverlapping() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(5);
            RXDashboardCard locked = card("L", 2, 2, 3, 1);
            locked.setLocked(true);
            RXDashboardCard tall = card("T", 2, 3, 2, 3);
            pane.getCards().addAll(locked, card("S", 2, 0, 1, 1), tall);
            Map<String, String> before = cells(pane);
            pane.moveCard(tall, 4, 0);
            assertNoOverlap(pane);
            assertEquals(before, cells(pane), "the only way up would cover the locked card");
        });
    }

    @Test
    public void aCardAddedAfterASnapshotWithHolesKeepsTheColumnRoundTrip() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(4);
            pane.getCards().addAll(card("A", -1, -1, 1, 1), card("B", -1, -1, 1, 1));
            pane.applyLayout(RXDashboardLayout.builder(4).card("A", 0, 0, 1, 1).card("B", 0, 3, 1, 1).build());
            laidOut(pane);
            pane.getCards().add(card("C", 0, 2, 1, 1));
            Map<String, String> before = cells(pane);
            assertEquals(cells("A:0,0 B:0,1 C:0,2"), before);
            pane.setColumnCount(2);
            pane.setColumnCount(4);
            assertEquals(before, cells(pane));
        });
    }

    @Test
    public void cardsAddedTogetherGiveTheSameLayoutWhetherTheSettingsComeLateOrFirst() throws Exception {
        fx(() -> {
            for (boolean compact : new boolean[] {false, true}) {
                for (int late = 0; late < 3; late++) {
                    assertEquals(together(compact, -1), together(compact, late),
                            "packing " + compact + ", late setting " + late);
                }
            }
        });
    }

    // Cards added together (one with a removal in between) and a move, the 4 columns and autoCompact set first (-1) or
    // one of them arriving late before the first layout: 0 the count from a style sheet, 1 autoCompact, 2 both. Then a
    // column round trip after the first layout, which shows the base layout the history left.
    private static List<Map<String, String>> together(boolean compact, int late) {
        RXDashboardPane pane = new RXDashboardPane();
        if (late != 0 && late != 2) {
            pane.setColumnCount(4);
        } else {
            pane.setStyle("-rx-column-count: 4;");
        }
        if (late < 1) {
            pane.setAutoCompact(compact);
        }
        RXDashboardCard b = card("B", 0, 3, 1, 1);
        pane.getCards().addAll(card("A", 0, 0, 2, 1), b, card("C", 0, 2, 1, 1), card("D", 2, 4, 2, 2));
        pane.getCards().remove(b);
        pane.getCards().addAll(b, card("E", 3, 1, 1, 1));
        // A move made from the layout the additions left, packed or not.
        pane.moveCard(find(pane, "E"), 1, 2);
        if (late >= 1) {
            pane.setAutoCompact(compact);
        }
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(pane), 400, 600));
        pane.applyCss();
        laidOut(pane);
        List<Map<String, String>> results = new ArrayList<>();
        results.add(cells(pane));
        pane.setColumnCount(2);
        pane.setColumnCount(4);
        results.add(cells(pane));
        return results;
    }

    @Test
    public void aMoveRightAfterCardsAddedTogetherStartsFromThePackedLayoutLateOrFirst() throws Exception {
        fx(() -> {
            Map<String, String> first = moveAfterAdding(false);
            assertEquals(cells("c0:1,0 c1:2,4 c2:1,5 c3:2,2 c4:2,0"), first);
            assertEquals(first, moveAfterAdding(true));
        });
    }

    // Five cards added together, then one moved, with the 4 columns set first or from a style sheet late.
    private static Map<String, String> moveAfterAdding(boolean late) {
        RXDashboardPane pane = new RXDashboardPane();
        if (late) {
            pane.setStyle("-rx-column-count: 4;");
        } else {
            pane.setColumnCount(4);
        }
        RXDashboardCard moved = card("c2", 1, 1, 2, 1);
        pane.getCards().addAll(card("c0", 1, 2, 1, 1), card("c1", 2, 4, 1, 1), moved, card("c3", 2, 2, 1, 2),
                card("c4", 2, 2, 2, 2));
        pane.moveCard(moved, 1, 5);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(pane), 400, 600));
        pane.applyCss();
        laidOut(pane);
        return cells(pane);
    }

    @Test
    public void lockingACardOrTurningPackingOffKeepsTheRoundTrip() throws Exception {
        fx(() -> {
            for (boolean lock : new boolean[] {false, true}) {
                RXDashboardPane pane = pane(4);
                RXDashboardCard b = card("b", -1, -1, 2, 1);
                pane.getCards().addAll(card("a", -1, -1, 2, 1), b);
                pane.applyLayout(RXDashboardLayout.builder(4).card("a", 0, 0, 2, 1).card("b", 0, 3, 2, 1).build());
                laidOut(pane);
                if (lock) {
                    b.setLocked(true);
                } else {
                    pane.setAutoCompact(false);
                }
                Map<String, String> before = cells(pane);
                assertEquals(cells("a:0,0 b:0,1"), before);
                pane.setColumnCount(2);
                pane.setColumnCount(4);
                assertEquals(before, cells(pane), lock ? "locked" : "packing off");
            }
        });
    }

    @Test
    public void cardsASnapshotDoesNotNameComeBackAfterAColumnRoundTrip() throws Exception {
        fx(() -> {
            for (boolean compact : new boolean[] {false, true}) {
                RXDashboardPane pane = pane(4);
                pane.setAutoCompact(compact);
                pane.getCards().addAll(card("a", -1, -1, 2, 1), card("b", -1, -1, 2, 1), card(null, 0, 1, 1, 1));
                pane.applyLayout(RXDashboardLayout.builder(4).card("a", 0, 0, 2, 1).card("b", 2, 0, 2, 1).build());
                laidOut(pane);
                List<String> before = cellsInOrder(pane);
                pane.setColumnCount(1);
                pane.setColumnCount(4);
                assertEquals(before, cellsInOrder(pane), "packing " + compact);
            }
        });
    }

    @Test
    public void aRemovalAndALateCountTakenInTogetherGiveWhatSettingTheCountFirstGives() throws Exception {
        fx(() -> assertEquals(removalWithCount(false), removalWithCount(true)));
    }

    // A listener removes a card and sets the count while a move is being committed, so both are taken in together;
    // the count was set first, or arrives then. The layout, at 2 columns, and back.
    private static List<Map<String, String>> removalWithCount(boolean late) {
        RXDashboardPane pane = new RXDashboardPane();
        if (!late) {
            pane.setColumnCount(6);
        }
        RXDashboardCard b = card("b", 0, 0, 1, 1);
        RXDashboardCard z = card("z", 3, 0, 1, 1);
        pane.getCards().addAll(b, card("x", 0, 1, 1, 1), card("y", 1, 0, 1, 1), z);
        boolean[] once = {true};
        pane.layoutProperty().addListener((obs, old, value) -> {
            if (once[0]) {
                once[0] = false;
                pane.getCards().remove(b);
                pane.setColumnCount(6);
            }
        });
        pane.moveCard(z, 4, 0);
        laidOut(pane);
        List<Map<String, String>> results = new ArrayList<>();
        results.add(cells(pane));
        pane.setColumnCount(2);
        results.add(cells(pane));
        pane.setColumnCount(6);
        results.add(cells(pane));
        return results;
    }

    @Test
    public void manyCardsAddedOneByOneStillGiveWhatSettingTheCountFirstGives() throws Exception {
        fx(() -> assertEquals(oneByOne(false), oneByOne(true)));
    }

    // 300 cards added one at a time before the first layout, the 6 columns set first or from a style sheet late.
    private static Map<String, String> oneByOne(boolean late) {
        RXDashboardPane pane = new RXDashboardPane();
        if (late) {
            pane.setStyle("-rx-column-count: 6;");
        } else {
            pane.setColumnCount(6);
        }
        for (int i = 0; i < 300; i++) {
            pane.getCards().add(card("c" + i, (i * 5) % 7, i % 4, 1 + i % 3, 1));
        }
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(pane), 600, 600));
        pane.applyCss();
        laidOut(pane);
        return cells(pane);
    }

    @Test
    public void randomHistoriesGiveWhatSettingTheCountFirstGives() throws Exception {
        fx(() -> {
            for (boolean compact : new boolean[] {false, true}) {
                for (long seed = 1; seed <= 120; seed++) {
                    assertEquals(randomHistory(seed, compact, false), randomHistory(seed, compact, true),
                            "seed " + seed + ", packing " + compact);
                }
            }
        });
    }

    // Cards added together with requests that may overlap (and push), one more alone, one removed, one moved, the 4
    // columns set first or from a style sheet late; then the layout, at 2 columns, and back.
    private static List<Map<String, String>> randomHistory(long seed, boolean compact, boolean late) {
        Random random = new Random(seed);
        RXDashboardPane pane = new RXDashboardPane();
        pane.setAutoCompact(compact);
        if (late) {
            pane.setStyle("-rx-column-count: 4;");
        } else {
            pane.setColumnCount(4);
        }
        int count = 3 + random.nextInt(4);
        List<RXDashboardCard> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int span = 1 + random.nextInt(2);
            cards.add(card("c" + i, random.nextInt(5 - span), random.nextInt(4), span, 1 + random.nextInt(2)));
        }
        pane.getCards().addAll(cards);
        pane.getCards().add(card("solo", random.nextInt(3), random.nextInt(4), 2, 1));
        pane.getCards().remove(cards.get(random.nextInt(count)));
        RXDashboardCard moved = pane.getCards().get(random.nextInt(pane.getCards().size()));
        pane.moveCard(moved, random.nextInt(4), random.nextInt(5));
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(pane), 400, 600));
        pane.applyCss();
        laidOut(pane);
        List<Map<String, String>> results = new ArrayList<>();
        results.add(cells(pane));
        pane.setColumnCount(2);
        results.add(cells(pane));
        pane.setColumnCount(4);
        results.add(cells(pane));
        return results;
    }

    @Test
    public void compactingBeforePackingIsTurnedOffLateIsKept() throws Exception {
        fx(() -> {
            RXDashboardPane late = pane(4);
            late.getCards().addAll(card("A", 0, 0, 1, 1), card("B", 0, 3, 1, 1));
            late.compact();
            late.setAutoCompact(false);

            RXDashboardPane first = new RXDashboardPane();
            first.setAutoCompact(false);
            first.setColumnCount(4);
            first.getCards().addAll(card("A", 0, 0, 1, 1), card("B", 0, 3, 1, 1));
            first.compact();
            assertEquals(cells(first), cells(late));
            assertEquals(cells("A:0,0 B:0,1"), cells(late));
        });
    }

    @Test
    public void aFarMoveFollowsTheStraightLineAllTheWay() throws Exception {
        fx(() -> {
            RXDashboardPane pane = pane(6);
            RXDashboardCard x = card("X", 0, 0, 1, 1);
            pane.getCards().add(x);
            for (int i = 1; i <= 5; i++) {
                pane.getCards().add(card("r" + i, i, 0, 1, 1));
            }
            for (int i = 0; i <= 5; i++) {
                pane.getCards().add(card("s" + i, i, 1, 1, 1));
            }
            assertTrue(pane.moveCard(x, 5, 1000));
            Map<String, String> cells = cells(pane);
            assertEquals("5,2", cells.get("X"));
            assertEquals("0,0", cells.get("s0"));
            for (int i = 1; i <= 5; i++) {
                assertEquals(i + ",1", cells.get("s" + i), "s" + i);
            }
        });
    }

    @Test
    public void columnRoundTripsRestoreRandomPackedLayouts() throws Exception {
        fx(() -> {
            Random random = new Random(20261124);
            for (int board = 0; board < 200; board++) {
                RXDashboardPane pane = pane(12);
                int count = 3 + random.nextInt(10);
                for (int i = 0; i < count; i++) {
                    int span = 1 + random.nextInt(6);
                    pane.getCards().add(card("c" + i, random.nextInt(13 - span), random.nextInt(6), span,
                            1 + random.nextInt(3)));
                }
                laidOut(pane);
                Map<String, String> before = cells(pane);
                int narrow = 1 + random.nextInt(11);
                pane.setColumnCount(narrow);
                pane.setColumnCount(12);
                assertEquals(before, cells(pane), "board " + board + " via " + narrow + " columns");
            }
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

    private static void assertNoOverlap(RXDashboardPane pane) {
        List<RXDashboardCard> cards = pane.getCards();
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                RXDashboardCard a = cards.get(i);
                RXDashboardCard b = cards.get(j);
                boolean apart = a.getColumn() + a.getColumnSpan() <= b.getColumn()
                        || b.getColumn() + b.getColumnSpan() <= a.getColumn()
                        || a.getRow() + a.getRowSpan() <= b.getRow() || b.getRow() + b.getRowSpan() <= a.getRow();
                assertTrue(apart, a.getCardId() + " overlaps " + b.getCardId());
            }
        }
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

    // Each card's column and row, in list order (cards without an ID are told apart by place).
    private static List<String> cellsInOrder(RXDashboardPane pane) {
        List<String> cells = new ArrayList<>();
        for (RXDashboardCard card : pane.getCards()) {
            cells.add(card.getColumn() + "," + card.getRow() + "," + card.getColumnSpan());
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
