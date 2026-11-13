package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXHoldButton control contract, the three-caption skin structure
 * and its sizing.
 */
public class RXHoldButtonTest {

    private static final String LONG_TEXT = "A considerably longer caption";

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
        Platform.setImplicitExit(false);
    }

    // ==================== Control API ====================

    @Test
    public void defaultsMatchContract() {
        RXHoldButton button = new RXHoldButton("Hold to delete");

        assertEquals(List.of("rx-hold-button"), button.getStyleClass());
        assertEquals(Duration.millis(1500.0), button.getHoldDuration());
        assertEquals("Keep holding…", button.getHoldingText());
        assertEquals("Done", button.getCompletedText());
        assertNull(button.getHoldingGraphic());
        assertNull(button.getCompletedGraphic());
        assertEquals(Duration.INDEFINITE, button.getFeedbackDuration());
        assertNull(button.getAnimation());
        assertEquals(Duration.millis(200.0), button.getAnimationDuration());
        assertEquals(FillAnimation.LEFT_TO_RIGHT, button.getFillAnimation());
        assertFalse(button.isMnemonicParsing());
        assertEquals(AccessibleRole.BUTTON, button.getAccessibleRole());
        assertNull(button.getAccessibleRoleDescription());
    }

    @Test
    public void cssMetadataCoversStyleablePropertiesOnly() {
        RXHoldButton button = new RXHoldButton();
        List<CssMetaData<? extends Styleable, ?>> metaData = button.getCssMetaData();

        assertTrue(hasProperty(metaData, "-rx-feedback-duration"));
        assertTrue(hasProperty(metaData, "-rx-animation-duration"));
        assertTrue(hasProperty(metaData, "-rx-fill-animation"));
        assertFalse(metaData.stream().anyMatch(item -> item.getProperty().contains("hold-duration")));

        CssMetaData<RXHoldButton, ?> feedback = metaData(button, "-rx-feedback-duration");
        assertTrue(feedback.isSettable(button));
        button.feedbackDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        assertFalse(feedback.isSettable(button));
    }

    @Test
    public void fireDispatchesUnlessDisabled() {
        RXHoldButton button = new RXHoldButton("Hold");
        AtomicInteger fired = countActions(button);

        button.fire();
        assertEquals(1, fired.get());
        button.executeAccessibleAction(AccessibleAction.FIRE);
        assertEquals(2, fired.get());

        button.setDisable(true);
        button.fire();
        assertEquals(2, fired.get());
    }

    // ==================== Skin Structure ====================

    @Test
    public void captionsAreThreeUnlabeledNodesWithOnlyTheIdleOneShowing() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));

        Label idle = caption(button, ".caption");
        Label holding = caption(button, ".holding-caption");
        Label completed = caption(button, ".completed-caption");
        for (Label caption : List.of(idle, holding, completed)) {
            assertFalse(caption.getStyleClass().contains("label"));
            assertFalse(caption.isMnemonicParsing());
        }
        assertTrue(idle.isVisible() && idle.getParent().isVisible());
        assertFalse(holding.isVisible());
        assertFalse(completed.getParent().isVisible());
        assertEquals("Hold", idle.getText());
        assertEquals("Keep holding…", holding.getText());
        assertEquals("Done", completed.getText());

        button.setHoldingText(null);
        button.setCompletedText(null);
        assertNull(holding.getText());
        assertNull(completed.getText());
        button.getSkin().dispose();
    }

    @Test
    public void accessibleTextPrefersTheAccessibleTextOverTheFace() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));

        assertEquals("Hold", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.setAccessibleText("Delete the file");
        assertEquals("Delete the file", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.getSkin().dispose();
    }

    @Test
    public void resetBeforeTheSkinLeavesNoStaleSignal() {
        RXHoldButton button = new RXHoldButton("Hold");
        button.resetFeedback();
        assertTrue(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));

        laidOut(button);
        assertFalse(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));

        button.resetFeedback();
        assertFalse(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));
        button.getSkin().dispose();
        button.getSkin().dispose();
        assertTrue(button.getChildrenUnmodifiable().isEmpty());
    }

    // ==================== Sizing ====================

    @Test
    public void everyFaceCountsTowardTheSize() {
        assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT, button -> {
        });
    }

    @Test
    public void wrappedTextCountsUnderAWidthConstraint() {
        RXHoldButton wrapped = laidOut(withContentOn(new RXHoldButton(), Face.IDLE,
                LONG_TEXT + " " + LONG_TEXT));
        wrapped.setWrapText(true);
        assertTrue(wrapped.prefHeight(80.0) > wrapped.prefHeight(160.0),
                "a narrower width wraps into more lines");
        wrapped.getSkin().dispose();

        assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT + " " + LONG_TEXT,
                button -> button.setWrapText(true));
    }

    @Test
    public void everyContentDisplayKeepsTheFacesEquivalent() {
        for (ContentDisplay display : List.of(ContentDisplay.LEFT, ContentDisplay.TOP,
                ContentDisplay.GRAPHIC_ONLY)) {
            assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT, button -> button.setContentDisplay(display));
        }
    }

    @Test
    public void labelPaddingPadsEveryFace() {
        for (Face face : Face.values()) {
            RXHoldButton plain = laidOut(withContentOn(new RXHoldButton(), face));
            RXHoldButton padded = withContentOn(new RXHoldButton(), face);
            padded.setStyle("-fx-label-padding: 10;");
            laidOut(padded);

            assertEquals(plain.prefWidth(-1) + 20.0, padded.prefWidth(-1), 0.01, face.name());
            assertEquals(plain.prefHeight(-1) + 20.0, padded.prefHeight(-1), 0.01, face.name());
            plain.getSkin().dispose();
            padded.getSkin().dispose();
        }
    }

    @Test
    public void graphicChangesResizeTheButtonAtOnce() {
        RXHoldButton button = laidOut(new RXHoldButton(null));
        button.setHoldingText(null);
        button.setCompletedText(null);
        double empty = button.prefWidth(-1);

        Region icon = sizedRegion(40.0);
        button.setCompletedGraphic(icon);
        assertEquals(empty + 40.0, button.prefWidth(-1), 0.01);

        icon.setPrefWidth(90.0);
        assertEquals(empty + 90.0, button.prefWidth(-1), 0.01);

        Region holdingIcon = sizedRegion(120.0);
        button.setHoldingGraphic(holdingIcon);
        assertEquals(empty + 120.0, button.prefWidth(-1), 0.01);

        button.setHoldingGraphic(null);
        assertEquals(empty + 90.0, button.prefWidth(-1), 0.01);
        button.getSkin().dispose();
    }

    @Test
    public void iconOnlyButtonIsSizedByItsIdleFace() {
        RXHoldButton iconOnly = new RXHoldButton(null, sizedRegion(24.0));
        iconOnly.setHoldingText(null);
        iconOnly.setCompletedText(null);
        laidOut(iconOnly);
        RXHoldButton withDefaults = laidOut(new RXHoldButton(null, sizedRegion(24.0)));

        assertTrue(withDefaults.prefWidth(-1) > iconOnly.prefWidth(-1),
                "the default texts widen a button that keeps them");
        Region idle = caption(iconOnly, ".caption");
        assertEquals(iconOnly.snappedLeftInset() + idle.prefWidth(-1) + iconOnly.snappedRightInset(),
                iconOnly.prefWidth(-1), 0.01);
        iconOnly.getSkin().dispose();
        withDefaults.getSkin().dispose();
    }

    // ==================== Helpers ====================

    private enum Face {
        IDLE, HOLDING, COMPLETED
    }

    // The same content placed on each face in turn must size the button
    // identically: every face is measured, visible or not.
    private static void assertSameSizeWhicheverFaceHoldsTheContent(String text,
                                                                   Consumer<RXHoldButton> setup) {
        RXHoldButton[] buttons = new RXHoldButton[Face.values().length];
        for (Face face : Face.values()) {
            RXHoldButton button = withContentOn(new RXHoldButton(), face, text);
            setup.accept(button);
            buttons[face.ordinal()] = laidOut(button);
        }
        RXHoldButton reference = buttons[0];
        Label idle = caption(reference, ".caption");
        assertEquals(reference.snappedLeftInset() + reference.snapSizeX(idle.prefWidth(-1))
                + reference.snappedRightInset(), reference.prefWidth(-1), 0.01);
        assertEquals(reference.snappedTopInset() + reference.snapSizeY(idle.prefHeight(-1))
                + reference.snappedBottomInset(), reference.prefHeight(-1), 0.01);
        for (RXHoldButton button : buttons) {
            assertEquals(reference.prefWidth(-1), button.prefWidth(-1), 0.01);
            assertEquals(reference.prefHeight(-1), button.prefHeight(-1), 0.01);
            assertEquals(reference.minWidth(-1), button.minWidth(-1), 0.01);
            assertEquals(reference.minHeight(-1), button.minHeight(-1), 0.01);
            for (double width : new double[]{80.0, 160.0}) {
                assertEquals(reference.prefHeight(width), button.prefHeight(width), 0.01);
            }
        }
        for (RXHoldButton button : buttons) {
            button.getSkin().dispose();
        }
    }

    private static RXHoldButton withContentOn(RXHoldButton button, Face face) {
        return withContentOn(button, face, LONG_TEXT);
    }

    private static RXHoldButton withContentOn(RXHoldButton button, Face face, String text) {
        button.setText(null);
        button.setHoldingText(null);
        button.setCompletedText(null);
        Region icon = sizedRegion(30.0);
        switch (face) {
            case IDLE:
                button.setText(text);
                button.setGraphic(icon);
                break;
            case HOLDING:
                button.setHoldingText(text);
                button.setHoldingGraphic(icon);
                break;
            default:
                button.setCompletedText(text);
                button.setCompletedGraphic(icon);
                break;
        }
        return button;
    }

    private static Region sizedRegion(double width) {
        Region region = new Region();
        region.setPrefSize(width, 20.0);
        region.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
    }

    private static RXHoldButton laidOut(RXHoldButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 400.0, 200.0);
        root.resize(400.0, 200.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static Label caption(RXHoldButton button, String selector) {
        Node caption = button.lookup(selector);
        assertNotNull(caption, selector);
        return assertInstanceOf(Label.class, caption);
    }

    private static AtomicInteger countActions(RXHoldButton button) {
        AtomicInteger fired = new AtomicInteger();
        button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
        return fired;
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metaData, String property) {
        return metaData.stream().anyMatch(item -> property.equals(item.getProperty()));
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<RXHoldButton, ?> metaData(RXHoldButton button, String property) {
        for (CssMetaData<? extends Styleable, ?> item : button.getCssMetaData()) {
            if (property.equals(item.getProperty())) {
                return (CssMetaData<RXHoldButton, ?>) item;
            }
        }
        throw new AssertionError(property + " is not styleable");
    }
}
