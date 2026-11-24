package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXSegmentedStepIndicatorSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXSegmentedStepIndicator skin lifecycle and the state in which every step is completed.
 */
public class RXSegmentedStepIndicatorTest {

    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass UPCOMING = PseudoClass.getPseudoClass("upcoming");
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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

    // ==================== Skin lifecycle ====================

    @Test
    public void replacementSkinKeepsItsNodes() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        StackPane root = new StackPane(indicator);
        new Scene(root, 320.0, 80.0);
        root.applyCss();
        root.layout();
        // One track and one fill per step.
        assertEquals(6, indicator.getChildrenUnmodifiable().size());

        // A skin of the installed class is ignored by setSkin, hence the subclass.
        indicator.setSkin(new RXSegmentedStepIndicatorSkin(indicator) {
        });

        assertEquals(6, indicator.getChildrenUnmodifiable().size());
        indicator.getSkin().dispose();
    }

    @Test
    public void ignoredSkinOfTheInstalledClassLeavesTheSegmentsAlone() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        StackPane root = new StackPane(indicator);
        new Scene(root, 320.0, 80.0);
        root.applyCss();
        root.layout();
        List<Node> installed = List.copyOf(indicator.getChildrenUnmodifiable());

        // setSkin ignores a new skin of the installed class.
        indicator.setSkin(new RXSegmentedStepIndicatorSkin(indicator));
        root.layout();

        for (Node segment : indicator.lookupAll(".track")) {
            assertTrue(segment.getLayoutBounds().getWidth() > 0.0, "a segment lost its size");
        }
        assertEquals(installed, indicator.getChildrenUnmodifiable());
    }

    // ==================== Completed state ====================

    @Test
    public void selectedIndexAtTheStepCountCompletesEverySegment() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        indicator.setTitleFactory(i -> new Label("Step " + i));
        StackPane root = laidOut(indicator);
        indicator.setSelectedIndex(3);
        root.layout();

        assertTrue(indicator.getPseudoClassStates().contains(COMPLETED));
        for (Node part : indicator.getChildrenUnmodifiable()) {
            assertTrue(part.getPseudoClassStates().contains(COMPLETED), part + " is not completed");
            assertFalse(part.getPseudoClassStates().contains(SELECTED), part + " is still selected");
            assertFalse(part.getPseudoClassStates().contains(UPCOMING), part + " is still upcoming");
        }
        for (Region fill : parts(indicator, "segment-fill")) {
            assertTrue(fill.isVisible());
            assertEquals(fill.getWidth(), ((Rectangle) fill.getClip()).getWidth());
        }
    }

    @Test
    public void selectedIndexPastTheStepCountAlsoCompletes() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        laidOut(indicator);
        indicator.setSelectedIndex(7);

        assertTrue(indicator.getPseudoClassStates().contains(COMPLETED));
        assertTrue(parts(indicator, "track").get(2).getPseudoClassStates().contains(COMPLETED));
    }

    @Test
    public void completedStateFollowsTheStepCount() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        indicator.setSelectedIndex(3);
        assertTrue(indicator.getPseudoClassStates().contains(COMPLETED));

        indicator.setStepCount(5);
        assertFalse(indicator.getPseudoClassStates().contains(COMPLETED));

        indicator.setStepCount(0);
        assertFalse(indicator.getPseudoClassStates().contains(COMPLETED));
        assertTrue(indicator.getPseudoClassStates().contains(EMPTY));
    }

    @Test
    public void stepsBeyondTheRenderedSegmentsAreNotCompleted() {
        int stepCount = RXSegmentedStepIndicator.MAX_STEP_COUNT + 5;
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(stepCount);
        indicator.setSelectedIndex(stepCount - 3);
        laidOut(indicator);
        List<Region> tracks = parts(indicator, "track");
        Region last = tracks.get(tracks.size() - 1);

        assertEquals(RXSegmentedStepIndicator.MAX_STEP_COUNT, tracks.size());
        assertFalse(indicator.getPseudoClassStates().contains(COMPLETED));
        assertTrue(last.getPseudoClassStates().contains(SELECTED));

        indicator.setSelectedIndex(stepCount);
        assertTrue(indicator.getPseudoClassStates().contains(COMPLETED));
        assertTrue(last.getPseudoClassStates().contains(COMPLETED));
    }

    @Test
    public void negativeSelectedIndexSelectsTheFirstSegment() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(3);
        indicator.setSelectedIndex(-2);
        laidOut(indicator);

        assertFalse(indicator.getPseudoClassStates().contains(COMPLETED));
        assertTrue(parts(indicator, "track").get(0).getPseudoClassStates().contains(SELECTED));
    }

    // ==================== Helpers ====================

    private static StackPane laidOut(RXSegmentedStepIndicator indicator) {
        StackPane root = new StackPane(indicator);
        new Scene(root, 320.0, 80.0);
        root.applyCss();
        root.layout();
        return root;
    }

    private static List<Region> parts(RXSegmentedStepIndicator indicator, String styleClass) {
        List<Region> parts = new ArrayList<>();
        for (Node child : indicator.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(styleClass)) {
                parts.add((Region) child);
            }
        }
        return parts;
    }
}
