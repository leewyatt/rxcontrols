package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.application.Platform;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the indicator-and-caption face: its structure, its size with and
 * without each part, the placement of the block and a replaced indicator.
 */
public class IndicatorFaceTest {

    private static final double INDICATOR_SIZE = 10.0;
    private static final double GAP = 6.0;
    private static final double EPSILON = 0.001;

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

    @Test
    public void faceHoldsAShapeIndicatorAndACaptionWithoutTheLabelClass() {
        IndicatorFace face = new IndicatorFace(owner(), "pending-face");

        assertEquals(List.of("pending-face"), face.getStyleClass());
        assertEquals(List.of(face.getIndicator(), face.getCaption()), face.getChildrenUnmodifiable());
        assertEquals(List.of("caption"), face.getCaption().getStyleClass());
        assertFalse(face.getCaption().isMnemonicParsing());

        Region indicator = (Region) face.getIndicator();
        assertEquals(List.of("indicator"), indicator.getStyleClass());
        assertEquals(Region.USE_PREF_SIZE, indicator.getMaxWidth());
        assertEquals(Region.USE_PREF_SIZE, indicator.getMaxHeight());
        assertTrue(indicator.isMouseTransparent());
        assertEquals(NodeOrientation.LEFT_TO_RIGHT, indicator.getNodeOrientation());
    }

    @Test
    public void gapNeedsBothAnIndicatorAndACaption() {
        IndicatorFace face = face("Save");
        // The face rounds each part up to whole pixels.
        double captionWidth = face.snapSizeX(face.getCaption().prefWidth(-1));
        assertTrue(captionWidth > 0.0);

        assertEquals(INDICATOR_SIZE + GAP + captionWidth, face.prefWidth(-1), EPSILON);

        face.getCaption().setText("");
        assertEquals(INDICATOR_SIZE, face.prefWidth(-1), EPSILON);

        face.getCaption().setText("Save");
        ((Region) face.getIndicator()).setPrefSize(0.0, 0.0);
        assertEquals(captionWidth, face.prefWidth(-1), EPSILON);
    }

    @Test
    public void heightIsTheTallerOfIndicatorAndCaption() {
        IndicatorFace face = face("Save");
        double captionHeight = face.snapSizeY(face.getCaption().prefHeight(-1));

        ((Region) face.getIndicator()).setPrefSize(INDICATOR_SIZE, captionHeight + 20.0);
        assertEquals(captionHeight + 20.0, face.prefHeight(-1), EPSILON);

        ((Region) face.getIndicator()).setPrefSize(INDICATOR_SIZE, 1.0);
        assertEquals(captionHeight, face.prefHeight(-1), EPSILON);
    }

    @Test
    public void blockIsPlacedByTheAlignmentOfTheOwner() {
        Button owner = owner();
        IndicatorFace face = face(owner, "Save");
        Node indicator = face.getIndicator();
        Label caption = face.getCaption();
        double blockWidth = face.prefWidth(-1);

        owner.setAlignment(Pos.TOP_LEFT);
        relayout(face);
        assertEquals(0.0, indicator.getLayoutX(), EPSILON);
        assertEquals(INDICATOR_SIZE + GAP, caption.getLayoutX(), EPSILON);
        assertEquals(0.0, caption.getLayoutY(), EPSILON);

        owner.setAlignment(Pos.BOTTOM_RIGHT);
        relayout(face);
        assertEquals(face.getWidth() - blockWidth, indicator.getLayoutX(), 1.0);
        assertEquals(face.getWidth(), caption.getLayoutX() + caption.getWidth(), 1.0);
        assertEquals(face.getHeight(), caption.getLayoutY() + caption.getHeight(), 1.0);

        owner.setAlignment(Pos.CENTER);
        relayout(face);
        assertEquals((face.getWidth() - blockWidth) / 2.0, indicator.getLayoutX(), 1.0);
        // The shorter indicator is centered on the caption.
        assertEquals(caption.getLayoutY() + caption.getHeight() / 2.0,
                indicator.getLayoutY() + INDICATOR_SIZE / 2.0, 1.0);
    }

    @Test
    public void replacedIndicatorTakesTheSlotAtItsPreferredSize() {
        IndicatorFace face = face("Save");
        // The face rounds each part up to whole pixels.
        double captionWidth = face.snapSizeX(face.getCaption().prefWidth(-1));
        Rectangle custom = new Rectangle(8.0, 6.0);

        face.setIndicator(custom);
        relayout(face);

        assertSame(custom, face.getIndicator());
        assertEquals(List.of(custom, face.getCaption()), face.getChildrenUnmodifiable());
        assertEquals(8.0 + GAP + captionWidth, face.prefWidth(-1), EPSILON);
        assertEquals(custom.getLayoutX() + 8.0 + GAP, face.getCaption().getLayoutX(), EPSILON);
    }

    @Test
    public void indicatorTakenAwayByAnotherParentIsReplacedWithoutLosingTheCaption() {
        IndicatorFace face = face("Save");
        Rectangle custom = new Rectangle(8.0, 6.0);
        face.setIndicator(custom);
        // A node has one parent: adding it elsewhere removes it from the face.
        new StackPane(custom);
        assertEquals(List.of(face.getCaption()), face.getChildrenUnmodifiable());

        Region replacement = IndicatorFace.createShapeIndicator();
        face.setIndicator(replacement);

        assertEquals(List.of(replacement, face.getCaption()), face.getChildrenUnmodifiable());
    }

    // ==================== Helpers ====================

    private static Button owner() {
        Button owner = new Button();
        owner.setGraphicTextGap(GAP);
        return owner;
    }

    private static IndicatorFace face(String text) {
        return face(owner(), text);
    }

    // A face with a 10 x 10 indicator, laid out in an area larger than it needs.
    private static IndicatorFace face(Button owner, String text) {
        IndicatorFace face = new IndicatorFace(owner, "face");
        ((Region) face.getIndicator()).setPrefSize(INDICATOR_SIZE, INDICATOR_SIZE);
        face.getCaption().setText(text);
        StackPane root = new StackPane(face);
        new Scene(root, 300.0, 100.0);
        root.resize(300.0, 100.0);
        root.applyCss();
        root.layout();
        return face;
    }

    private static void relayout(IndicatorFace face) {
        face.requestLayout();
        face.getParent().applyCss();
        face.getParent().layout();
    }
}
