package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a caption follows the text appearance of its button, and stops
 * following when the disposer is disposed.
 */
public class CaptionBindingTest {

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
    public void captionFollowsTheButtonUntilDisposed() {
        Button button = new Button("Save");
        Label caption = new Label("Saving");
        SkinDisposer disposer = new SkinDisposer();

        CaptionBinding.bindAppearance(disposer, caption, button);
        Font font = Font.font(21.0);
        button.setFont(font);
        button.setTextFill(Color.CRIMSON);
        button.setEllipsisString("~");
        button.setContentDisplay(ContentDisplay.RIGHT);
        button.setGraphicTextGap(9.0);
        button.setAlignment(Pos.BOTTOM_RIGHT);
        button.setTextAlignment(TextAlignment.RIGHT);
        button.setTextOverrun(OverrunStyle.CLIP);
        button.setWrapText(true);
        button.setUnderline(true);
        button.setLineSpacing(3.0);

        assertEquals(font, caption.getFont());
        assertEquals(Color.CRIMSON, caption.getTextFill());
        assertEquals("~", caption.getEllipsisString());
        assertEquals(ContentDisplay.RIGHT, caption.getContentDisplay());
        assertEquals(9.0, caption.getGraphicTextGap());
        assertEquals(Pos.BOTTOM_RIGHT, caption.getAlignment());
        assertEquals(TextAlignment.RIGHT, caption.getTextAlignment());
        assertEquals(OverrunStyle.CLIP, caption.getTextOverrun());
        assertTrue(caption.isWrapText());
        assertTrue(caption.isUnderline());
        assertEquals(3.0, caption.getLineSpacing());
        // Each face has its own text and graphic.
        assertEquals("Saving", caption.getText());
        assertNull(caption.getGraphic());
        assertFalse(caption.textProperty().isBound());
        assertFalse(caption.graphicProperty().isBound());

        disposer.dispose();
        for (Property<?> property : appearance(caption)) {
            assertFalse(property.isBound(), property.getName());
        }
    }

    private static List<Property<?>> appearance(Label caption) {
        return List.of(caption.fontProperty(), caption.textFillProperty(),
                caption.ellipsisStringProperty(), caption.contentDisplayProperty(),
                caption.graphicTextGapProperty(), caption.alignmentProperty(),
                caption.textAlignmentProperty(), caption.textOverrunProperty(),
                caption.wrapTextProperty(), caption.underlineProperty(),
                caption.lineSpacingProperty());
    }
}
