package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests RXWaveProgressIndicator skin layout of the centre progress labels.
 */
public class RXWaveProgressIndicatorTest {

    private static final double EPSILON = 0.01;
    private static final PseudoClass PROBE = PseudoClass.getPseudoClass("probe");

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

    @Test
    public void progressLabelsResizeWhenTheFontChanges() {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(0.6);
        StackPane root = new StackPane(indicator);
        new Scene(root, 200.0, 200.0);
        indicator.setPrefSize(140.0, 140.0);
        root.applyCss();
        root.layout();

        try {
            Label above = (Label) indicator.lookup(".progress-label");
            Label below = (Label) indicator.lookup(".progress-label.below-water");
            double restingWidth = above.getWidth();

            root.setStyle("-fx-font-size: 20px;");
            root.applyCss();
            root.layout();

            assertTrue(above.prefWidth(-1) > restingWidth, "the larger font needs a wider label");
            assertEquals(above.prefWidth(-1), above.getWidth(), EPSILON);
            assertEquals(above.getWidth(), below.getWidth(), EPSILON);
            assertEquals(above.getLayoutX(), below.getLayoutX(), EPSILON);
        } finally {
            indicator.getSkin().dispose();
        }
    }

    @Test
    public void progressLabelsResizeWhenAStateRuleChangesTheirBorder() {
        RXWaveProgressIndicator indicator = laidOutIndicator(
                ".rx-wave-progress-indicator:probe .progress-label { -fx-border-color: red; -fx-border-width: 6px; }");
        StackPane root = (StackPane) indicator.getParent();

        try {
            Label above = (Label) indicator.lookup(".progress-label");
            Label below = (Label) indicator.lookup(".progress-label.below-water");
            double restingWidth = above.getWidth();

            indicator.pseudoClassStateChanged(PROBE, true);
            root.applyCss();
            root.layout();

            assertTrue(above.prefWidth(-1) > restingWidth, "the border needs a wider label");
            assertEquals(above.prefWidth(-1), above.getWidth(), EPSILON);
            assertEquals(above.getWidth(), below.getWidth(), EPSILON);
        } finally {
            indicator.getSkin().dispose();
        }
    }

    @Test
    public void labelsRecentreWhenAStateRuleChangesTheContainerInsets() {
        RXWaveProgressIndicator indicator = laidOutIndicator(
                ".rx-wave-progress-indicator:probe > .wave-container { -fx-padding: 8px 8px 8px 40px; }");
        StackPane root = (StackPane) indicator.getParent();

        try {
            Region container = (Region) indicator.lookup(".wave-container");
            Label above = (Label) indicator.lookup(".progress-label");
            double restingCentre = above.getLayoutX() + above.getWidth() / 2.0;

            indicator.pseudoClassStateChanged(PROBE, true);
            root.applyCss();
            root.layout();

            Insets insets = container.getInsets();
            double innerWidth = container.getWidth() - insets.getLeft() - insets.getRight();
            double expectedCentre = insets.getLeft() + innerWidth / 2.0;
            double centre = above.getLayoutX() + above.getWidth() / 2.0;
            assertTrue(Math.abs(centre - restingCentre) > 1.0, "asymmetric padding moves the centre");
            assertEquals(expectedCentre, centre, EPSILON);
        } finally {
            indicator.getSkin().dispose();
        }
    }

    // ==================== Helpers ====================

    // A state rule (a pseudo-class toggle) changes values without rebuilding the
    // labels' style helpers, which would reset their font and relayout by itself.
    private static RXWaveProgressIndicator laidOutIndicator(String stateRule) {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(0.6);
        StackPane root = new StackPane(indicator);
        Scene scene = new Scene(root, 200.0, 200.0);
        scene.getStylesheets().add("data:text/css;base64,"
                + Base64.getEncoder().encodeToString(stateRule.getBytes(StandardCharsets.UTF_8)));
        indicator.setPrefSize(140.0, 140.0);
        root.applyCss();
        root.layout();
        return indicator;
    }
}
