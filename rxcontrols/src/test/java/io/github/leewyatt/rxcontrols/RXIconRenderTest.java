package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.transform.Transform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders icons to images: the view box corners draw nothing, the space around
 * the drawing is kept, and the even-odd rule leaves holes. Checked at scale 1
 * and at scale 2 (as on a HiDPI screen).
 */
public class RXIconRenderTest {

    /** The middle third of a 24 x 24 view box. */
    private static final String CENTER_SQUARE = "M8 8h8v8H8z";
    /** Two squares drawn in the same direction: the inner one is a hole only under even-odd. */
    private static final String RING = "M2 2h20v20H2z M8 8h8v8H8z";

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
        // Modena is installed when the first control class loads; these tests may
        // build only regions, so pin it for the -fx-* colors the icon reads.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void viewBoxCornersDrawNothingAndTheMarginIsKept() throws Exception {
        for (double scale : new double[]{1.0, 2.0}) {
            PixelReader pixels = render(new RXIconData("render-center", 24, 24, CENTER_SQUARE), scale);
            int size = (int) (24 * scale);
            // Corners, including the anchor points themselves.
            assertTransparent(pixels, 0, 0, scale);
            assertTransparent(pixels, size - 1, size - 1, scale);
            assertTransparent(pixels, size - 1, 0, scale);
            assertTransparent(pixels, 0, size - 1, scale);
            // The margin around the drawing: the square spans 8..16, not the whole box.
            assertTransparent(pixels, (int) (4 * scale), (int) (12 * scale), scale);
            assertTransparent(pixels, (int) (20 * scale), (int) (12 * scale), scale);
            assertOpaque(pixels, (int) (12 * scale), (int) (12 * scale), scale);
        }
    }

    @Test
    public void aPathStartingWithARelativeMovetoIsDrawnInPlace() throws Exception {
        // Optimized SVG often starts with "m": SVG measures it from the origin.
        PixelReader pixels = render(new RXIconData("render-relative", 24, 24, "m8 8h8v8H8z"), 1.0);
        assertOpaque(pixels, 12, 12, 1.0);
        assertTransparent(pixels, 4, 12, 1.0);
        assertTransparent(pixels, 20, 20, 1.0);
    }

    @Test
    public void controlPointsOutsideTheViewBoxDoNotMoveTheDrawing() throws Exception {
        // The arch peaks at y = 1.5 although its control points lie at y = -2.
        PixelReader pixels = render(new RXIconData("render-arch", 24, 24, "M2 12C2 -2 22 -2 22 12Z"), 1.0);
        assertOpaque(pixels, 12, 2, 1.0);
        assertOpaque(pixels, 12, 11, 1.0);
        assertTransparent(pixels, 12, 13, 1.0);
    }

    @Test
    public void drawingOutsideTheViewBoxIsClipped() throws Exception {
        // As in SVG: the part left of x = 0 is cut off and the rest stays in place.
        PixelReader pixels = render(new RXIconData("render-overflow", 24, 24, "M-4 8h8v8h-8z"), 1.0);
        assertOpaque(pixels, 2, 12, 1.0);
        assertTransparent(pixels, 5, 12, 1.0);
        assertTransparent(pixels, 2, 18, 1.0);
    }

    @Test
    public void evenOddLeavesAHole() throws Exception {
        for (double scale : new double[]{1.0, 2.0}) {
            PixelReader evenOdd = render(new RXIconData("render-eo", 0, 0, 24, 24, RING, FillRule.EVEN_ODD), scale);
            assertTransparent(evenOdd, (int) (12 * scale), (int) (12 * scale), scale);
            assertOpaque(evenOdd, (int) (5 * scale), (int) (12 * scale), scale);

            PixelReader nonZero = render(new RXIconData("render-nz", 0, 0, 24, 24, RING, FillRule.NON_ZERO), scale);
            assertOpaque(nonZero, (int) (12 * scale), (int) (12 * scale), scale);
        }
    }

    private static PixelReader render(RXIconData data, double scale) throws Exception {
        AtomicReference<WritableImage> image = new AtomicReference<>();
        runOnFx(() -> {
            RXIcon icon = new RXIcon(data);
            icon.setIconSize(24.0);
            icon.setStyle("-fx-background-color: black;");
            StackPane root = new StackPane(icon);
            root.setStyle("-fx-background-color: transparent;");
            new Scene(root, 40, 40, Color.TRANSPARENT);
            root.applyCss();
            root.layout();
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            parameters.setTransform(Transform.scale(scale, scale));
            image.set(icon.snapshot(parameters, null));
            assertEquals(Math.round(24 * scale), Math.round(image.get().getWidth()), "snapshot width");
        });
        return image.get().getPixelReader();
    }

    private static void assertTransparent(PixelReader pixels, int x, int y, double scale) {
        double alpha = pixels.getColor(x, y).getOpacity();
        assertEquals(0.0, alpha, 1.0e-9, "pixel " + x + "," + y + " at scale " + scale + " should be empty");
    }

    private static void assertOpaque(PixelReader pixels, int x, int y, double scale) {
        double alpha = pixels.getColor(x, y).getOpacity();
        assertTrue(alpha > 0.99, "pixel " + x + "," + y + " at scale " + scale + " should be filled: " + alpha);
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
