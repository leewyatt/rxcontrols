package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXAccordion.ExpandMode;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXAccordion}: the expansion rules of both modes, list validation,
 * membership, binding and reentrancy, pane navigation, nesting and layout.
 */
public class RXAccordionTest {

    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");
    private static final PseudoClass GAP_BEFORE = PseudoClass.getPseudoClass("gap-before");
    private static final PseudoClass GAP_AFTER = PseudoClass.getPseudoClass("gap-after");

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
        Platform.setImplicitExit(false);
    }

    // ==================== SINGLE mode ====================

    @Test
    public void singleModeKeepsAtMostOneExpandedAndAllowsNone() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            assertEquals(ExpandMode.SINGLE, accordion.getExpandMode());
            a.setExpanded(true);
            b.setExpanded(true);
            assertFalse(a.isExpanded());
            assertEquals(List.of(b), accordion.getExpandedPanes());
            b.setExpanded(false);
            assertTrue(accordion.getExpandedPanes().isEmpty());
        });
    }

    @Test
    public void expandedPanesChangesOncePerCoordination() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            a.setExpanded(true);
            AtomicInteger changes = new AtomicInteger();
            accordion.getExpandedPanes().addListener((ListChangeListener<RXTitledPane>) c -> changes.incrementAndGet());
            b.setExpanded(true);
            assertEquals(1, changes.get());
            assertThrows(UnsupportedOperationException.class, () -> accordion.getExpandedPanes().clear());
        });
    }

    @Test
    public void switchingToSingleKeepsFirstExpandedInListOrder() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXTitledPane c = pane("C");
            RXAccordion accordion = new RXAccordion(a, b, c);
            accordion.setExpandMode(ExpandMode.MULTIPLE);
            c.setExpanded(true);
            b.setExpanded(true);
            assertEquals(List.of(b, c), accordion.getExpandedPanes());
            accordion.setExpandMode(ExpandMode.SINGLE);
            assertEquals(List.of(b), accordion.getExpandedPanes());

            accordion.setExpandMode(null);
            c.setExpanded(true);
            assertEquals(List.of(c), accordion.getExpandedPanes(), "null mode behaves as SINGLE");
        });
    }

    @Test
    public void addingExpandedPanesKeepsTheExistingOne() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            a.setExpanded(true);
            RXAccordion accordion = new RXAccordion(a);
            RXTitledPane b = pane("B");
            b.setExpanded(true);
            accordion.getPanes().add(0, b);
            assertTrue(a.isExpanded());
            assertFalse(b.isExpanded());

            RXAccordion empty = new RXAccordion();
            RXTitledPane c = pane("C");
            RXTitledPane d = pane("D");
            c.setExpanded(true);
            d.setExpanded(true);
            empty.getPanes().addAll(d, c);
            assertTrue(d.isExpanded());
            assertFalse(c.isExpanded());
        });
    }

    // ==================== MULTIPLE mode ====================

    @Test
    public void expandAllAndCollapseAll() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            accordion.expandAll();
            assertTrue(accordion.getExpandedPanes().isEmpty(), "expandAll is a no-op in SINGLE");

            accordion.setExpandMode(ExpandMode.MULTIPLE);
            accordion.expandAll();
            assertEquals(List.of(a, b), accordion.getExpandedPanes());
            accordion.collapseAll();
            assertTrue(accordion.getExpandedPanes().isEmpty());
        });
    }

    // ==================== List validation & membership ====================

    @Test
    public void listRejectsNullAndDuplicatesAtomically() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a);
            assertThrows(NullPointerException.class, () -> accordion.getPanes().add(null));
            assertThrows(IllegalArgumentException.class, () -> accordion.getPanes().add(a));
            assertThrows(IllegalArgumentException.class, () -> accordion.getPanes().addAll(b, b));
            assertEquals(List.of(a), accordion.getPanes());

            accordion.getPanes().setAll(b, a);
            assertEquals(List.of(b, a), accordion.getPanes());
        });
    }

    @Test
    public void joiningAnotherAccordionLeavesTheOldOne() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion discarded = new RXAccordion(a, b);
            StackPane root = new StackPane(discarded);
            new Scene(root, 300, 300);
            root.applyCss();
            root.layout();

            RXAccordion rebuilt = new RXAccordion(a, b);
            assertTrue(discarded.getPanes().isEmpty());
            assertTrue(discarded.getChildrenUnmodifiable().isEmpty());
            assertSame(rebuilt, a.getAccordion());
            a.setExpanded(true);
            b.setExpanded(true);
            assertEquals(List.of(b), rebuilt.getExpandedPanes());
            assertTrue(discarded.getExpandedPanes().isEmpty());
        });
    }

    @Test
    public void reentrantMoveDuringMigrationIsRefusedWithoutDoubleOwnership() throws Exception {
        runOnFx(() -> {
            RXTitledPane p = pane("P");
            RXAccordion source = new RXAccordion(p);
            RXAccordion third = new RXAccordion();
            source.getPanes().addListener((ListChangeListener<RXTitledPane>) c -> {
                while (c.next()) {
                    if (c.wasRemoved() && c.getRemoved().contains(p)) {
                        third.getPanes().add(p);
                    }
                }
            });
            RXAccordion target = new RXAccordion();
            assertThrows(IllegalStateException.class, () -> target.getPanes().add(p));
            assertTrue(target.getPanes().isEmpty());
            assertTrue(source.getPanes().isEmpty());
            assertEquals(List.of(p), third.getPanes());
            assertSame(third, p.getAccordion());
        });
    }

    @Test
    public void groupIsNotAFocusStop() throws Exception {
        runOnFx(() -> {
            RXAccordion accordion = new RXAccordion(pane("A"));
            assertFalse(accordion.isFocusTraversable());
            assertFalse(accordion.getPanes().get(0).isFocusTraversable());
        });
    }

    @Test
    public void removedPaneBecomesIndependentAndKeepsItsState() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            a.setExpanded(true);
            accordion.getPanes().remove(a);
            assertTrue(a.isExpanded());
            assertFalse(b.isExpanded(), "no pane is expanded in its place");
            b.setExpanded(true);
            assertTrue(a.isExpanded(), "the old group no longer controls the pane");
            assertTrue(accordion.getExpandedPanes().contains(b));
            assertFalse(a.getPseudoClassStates().contains(FIRST));

            RXAccordion other = new RXAccordion(a);
            assertSame(other, a.getAccordion());
        });
    }

    @Test
    public void firstPseudoClassFollowsOrder() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            assertTrue(a.getPseudoClassStates().contains(FIRST));
            assertFalse(b.getPseudoClassStates().contains(FIRST));
            assertTrue(b.getPseudoClassStates().contains(LAST));
            accordion.getPanes().setAll(b, a);
            assertTrue(b.getPseudoClassStates().contains(FIRST));
            assertFalse(a.getPseudoClassStates().contains(FIRST));
            assertTrue(a.getPseudoClassStates().contains(LAST));
            assertFalse(b.getPseudoClassStates().contains(LAST));
        });
    }

    // ==================== Binding & reentrancy ====================

    @Test
    public void unidirectionallyBoundPaneIsSkippedWithoutException() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            BooleanProperty source = new SimpleBooleanProperty(true);
            a.expandedProperty().bind(source);
            RXAccordion accordion = new RXAccordion(a, b);
            b.setExpanded(true);
            assertTrue(a.isExpanded());
            assertTrue(b.isExpanded());
            accordion.collapseAll();
            assertTrue(a.isExpanded());
            assertFalse(b.isExpanded());
        });
    }

    @Test
    public void bidirectionallyBoundPaneIsCoordinated() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            BooleanProperty model = new SimpleBooleanProperty(true);
            a.expandedProperty().bindBidirectional(model);
            new RXAccordion(a, b);
            b.setExpanded(true);
            assertFalse(a.isExpanded());
            assertFalse(model.get());
        });
    }

    @Test
    public void newerExpansionFromListenerWins() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXTitledPane c = pane("C");
            RXAccordion accordion = new RXAccordion(a, b, c);
            a.setExpanded(true);
            b.expandedProperty().addListener((obs, was, is) -> {
                if (is) {
                    c.setExpanded(true);
                }
            });
            b.setExpanded(true);
            assertEquals(List.of(c), accordion.getExpandedPanes());
            assertFalse(a.isExpanded());
            assertFalse(b.isExpanded());
        });
    }

    @Test
    public void leavingSingleModeFromAListenerKeepsTheListenersExpansion() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            RXTitledPane b = pane("B");
            RXTitledPane c = pane("C");
            RXAccordion accordion = new RXAccordion(a, b, c);
            a.setExpanded(true);
            a.expandedProperty().addListener((obs, was, is) -> {
                if (!is) {
                    accordion.setExpandMode(ExpandMode.MULTIPLE);
                    c.setExpanded(true);
                }
            });
            b.setExpanded(true);
            assertEquals(List.of(b, c), accordion.getExpandedPanes());
        });
    }

    @Test
    public void panesRespectTheirMaxWidth() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            a.setMaxWidth(200);
            RXTitledPane b = pane("B");
            RXAccordion accordion = new RXAccordion(a, b);
            StackPane root = new StackPane(accordion);
            new Scene(root, 500, 300);
            root.applyCss();
            root.layout();
            assertEquals(200.0, a.getWidth(), 0.5);
            assertTrue(b.getWidth() > 400.0);
            assertEquals(b.getLayoutX(), a.getLayoutX(), 0.5);
        });
    }

    // ==================== Keyboard & nesting ====================

    @Test
    public void arrowKeysMoveFocusBetweenOwnHeadersOnly() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            runOnFx(() -> {
                RXTitledPane a = pane("A");
                RXTitledPane b = pane("B");
                RXTitledPane disabled = pane("Disabled");
                disabled.setDisable(true);
                RXTitledPane c = pane("C");
                RXTitledPane inner1 = pane("Inner 1");
                RXTitledPane inner2 = pane("Inner 2");
                RXAccordion inner = new RXAccordion(inner1, inner2);
                c.setContent(inner);
                c.setAnimated(false);
                c.setExpanded(true);
                RXAccordion accordion = new RXAccordion(a, b, disabled, c);
                Stage stage = new Stage();
                stageRef.set(stage);
                stage.setScene(new Scene(new StackPane(accordion), 400, 600));
                stage.show();
                Scene scene = stage.getScene();

                title(a).requestFocus();
                key(title(a), KeyCode.DOWN);
                assertSame(title(b), scene.getFocusOwner());
                key(title(b), KeyCode.DOWN);
                assertSame(title(c), scene.getFocusOwner(), "disabled pane is skipped");
                key(title(c), KeyCode.DOWN);
                assertSame(title(c), scene.getFocusOwner(), "no wrap at the end");
                key(title(c), KeyCode.HOME);
                assertSame(title(a), scene.getFocusOwner());
                key(title(a), KeyCode.END);
                assertSame(title(c), scene.getFocusOwner());

                title(inner1).requestFocus();
                key(title(inner1), KeyCode.DOWN);
                assertSame(title(inner2), scene.getFocusOwner());
                key(title(inner2), KeyCode.END);
                assertSame(title(inner2), scene.getFocusOwner(), "inner keys stay inside the inner group");

                inner1.setExpanded(true);
                assertTrue(c.isExpanded(), "inner coordination never touches the outer group");
            });
        } finally {
            runOnFx(() -> {
                if (stageRef.get() != null) {
                    stageRef.get().close();
                }
            });
        }
    }

    // ==================== Layout ====================

    @Test
    public void expandedSpacingOpensGapsOnlyAroundExpandedPanes() throws Exception {
        runOnFx(() -> {
            RXTitledPane a = pane("A");
            Region body = new Region();
            body.setPrefHeight(100);
            RXTitledPane b = new RXTitledPane("B", body);
            b.setAnimated(false);
            RXTitledPane c = pane("C");
            RXAccordion accordion = new RXAccordion(a, b, c);
            accordion.setStyle("-rx-expanded-spacing: 16;");
            StackPane root = new StackPane(accordion);
            new Scene(root, 400, 600);
            root.applyCss();
            root.layout();
            assertEquals(16.0, accordion.getExpandedSpacing(), 0.01);

            // Collapsed: one card, neighbours one divider apart, no gap pseudo-classes.
            assertEquals(1, visible(accordion, "segment"));
            assertEquals(2, visible(accordion, "divider"));
            double divider = 1.0;
            assertEquals(a.getBoundsInParent().getMaxY() + divider, b.getLayoutY(), 0.5);
            assertFalse(b.getPseudoClassStates().contains(GAP_BEFORE));

            b.setExpanded(true);
            root.layout();
            // Expanded B is its own card: three cards, no dividers, a gap on both sides.
            assertEquals(3, visible(accordion, "segment"));
            assertEquals(0, visible(accordion, "divider"));
            assertEquals(a.getBoundsInParent().getMaxY() + divider + 16.0, b.getLayoutY(), 0.5);
            assertEquals(b.getBoundsInParent().getMaxY() + divider + 16.0, c.getLayoutY(), 0.5);
            assertTrue(b.getPseudoClassStates().contains(GAP_BEFORE));
            assertTrue(b.getPseudoClassStates().contains(GAP_AFTER));
            assertTrue(a.getPseudoClassStates().contains(GAP_AFTER));
            assertTrue(c.getPseudoClassStates().contains(GAP_BEFORE));
            assertFalse(a.getPseudoClassStates().contains(GAP_BEFORE), "no gap at the group's ends");
            assertEquals(accordion.prefHeight(400), accordion.getHeight(), 0.5);

            accordion.setExpandedSpacing(0);
            root.layout();
            assertEquals(1, visible(accordion, "segment"));
            assertFalse(b.getPseudoClassStates().contains(GAP_BEFORE));
        });
    }

    private static int visible(RXAccordion accordion, String styleClass) {
        int count = 0;
        for (Node child : accordion.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(styleClass) && child.isVisible()) {
                count++;
            }
        }
        return count;
    }

    // ==================== Helpers ====================

    private static RXTitledPane pane(String text) {
        RXTitledPane pane = new RXTitledPane(text, new Label(text + " body"));
        pane.setAnimated(false);
        return pane;
    }

    // Takes the pane's own header: a plain lookup would find a nested pane's header first.
    private static Node title(RXTitledPane pane) {
        for (Node child : pane.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("header")) {
                return child;
            }
        }
        throw new AssertionError("no header in " + pane);
    }

    private static void key(Node target, KeyCode code) {
        fireKey(target, KeyEvent.KEY_PRESSED, code);
        fireKey(target, KeyEvent.KEY_RELEASED, code);
    }

    private static void fireKey(Node target, EventType<KeyEvent> type, KeyCode code) {
        target.fireEvent(new KeyEvent(type, "", "", code, false, false, false, false));
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
