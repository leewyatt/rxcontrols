package io.github.leewyatt.rxcontrols.internal;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for how {@link BoundedClipSupport} insets the mirrored background geometry:
 * a border inset shrinks absolute corner radii side by side, while explicit insets,
 * explicit radii, percentage radii and border-less hosts keep the background radii.
 */
public class BoundedClipSupportTest {

    private static final double WIDTH = 100.0;
    private static final double HEIGHT = 40.0;
    private static final CornerRadii RADII = new CornerRadii(10.0);

    /**
     * Starts the JavaFX toolkit; the nodes stay detached, so the tests run on the caller thread.
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

    /** Each radius shrinks by the border width of its own side: horizontal by left/right, vertical by top/bottom. */
    @Test
    public void borderInsetShrinksEachRadiusByItsAdjacentSide() {
        Region host = host(RADII, Insets.EMPTY, new BorderWidths(1.0, 2.0, 3.0, 4.0));

        BackgroundFill clip = clipFill(host, null);

        assertEquals(new Insets(1.0, 2.0, 3.0, 4.0), clip.getInsets());
        CornerRadii radii = clip.getRadii();
        assertEquals(6.0, radii.getTopLeftHorizontalRadius(), "top-left horizontal - left");
        assertEquals(9.0, radii.getTopLeftVerticalRadius(), "top-left vertical - top");
        assertEquals(9.0, radii.getTopRightVerticalRadius(), "top-right vertical - top");
        assertEquals(8.0, radii.getTopRightHorizontalRadius(), "top-right horizontal - right");
        assertEquals(8.0, radii.getBottomRightHorizontalRadius(), "bottom-right horizontal - right");
        assertEquals(7.0, radii.getBottomRightVerticalRadius(), "bottom-right vertical - bottom");
        assertEquals(7.0, radii.getBottomLeftVerticalRadius(), "bottom-left vertical - bottom");
        assertEquals(6.0, radii.getBottomLeftHorizontalRadius(), "bottom-left horizontal - left");
    }

    /** A radius smaller than the border inset clamps at zero. */
    @Test
    public void radiusSmallerThanTheBorderClampsAtZero() {
        Region host = host(new CornerRadii(2.0), Insets.EMPTY, new BorderWidths(3.0));

        assertEquals(0.0, clipFill(host, null).getRadii().getTopLeftHorizontalRadius());
    }

    /** A fill's own insets add to the border inset, but only the border inset shrinks the radii. */
    @Test
    public void fillInsetsAddToTheBorderInsetWithoutShrinkingTheRadii() {
        Region host = host(RADII, new Insets(2.0), new BorderWidths(1.0));

        BackgroundFill clip = clipFill(host, null);

        assertEquals(new Insets(3.0), clip.getInsets());
        assertEquals(9.0, clip.getRadii().getTopLeftHorizontalRadius());
    }

    /** With several strokes each side takes its deepest inner edge, possibly from different strokes. */
    @Test
    public void severalStrokesInsetEachSideByItsDeepestStroke() {
        Region host = host(RADII, Insets.EMPTY, null);
        host.setBorder(new Border(
                new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, RADII, new BorderWidths(3.0, 1.0, 1.0, 1.0)),
                new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, RADII, new BorderWidths(1.0, 2.0, 1.0, 4.0))));

        BackgroundFill clip = clipFill(host, null);

        assertEquals(new Insets(3.0, 2.0, 1.0, 4.0), clip.getInsets());
        assertEquals(7.0, clip.getRadii().getTopLeftVerticalRadius(), "top from the first stroke");
        assertEquals(6.0, clip.getRadii().getTopLeftHorizontalRadius(), "left from the second stroke");
        assertEquals(8.0, clip.getRadii().getTopRightHorizontalRadius(), "right from the second stroke");
    }

    /** Explicit radii bypass the reduction and keep their values inside the border inset. */
    @Test
    public void explicitRadiiBypassTheReduction() {
        Region host = host(RADII, Insets.EMPTY, new BorderWidths(1.0));
        CornerRadii explicit = new CornerRadii(6.0);
        Pane owner = new Pane();

        new BoundedClipSupport(owner).updateClipFor(host, WIDTH, HEIGHT, null, explicit);

        BackgroundFill clip = ((Region) owner.getClip()).getBackground().getFills().get(0);
        assertEquals(new Insets(1.0), clip.getInsets());
        assertEquals(explicit, clip.getRadii());
    }

    /** Explicit insets replace the border inset and keep the background radii. */
    @Test
    public void explicitInsetsKeepTheRadii() {
        Region host = host(RADII, Insets.EMPTY, new BorderWidths(1.0));

        BackgroundFill clip = clipFill(host, new Insets(5.0));

        assertEquals(new Insets(5.0), clip.getInsets());
        assertEquals(RADII, clip.getRadii());
    }

    /** Without a border nothing is inset and the radii stay as they are. */
    @Test
    public void borderlessHostKeepsTheRadii() {
        Region host = host(RADII, Insets.EMPTY, null);

        BackgroundFill clip = clipFill(host, null);

        assertEquals(Insets.EMPTY, clip.getInsets());
        assertEquals(RADII, clip.getRadii());
    }

    /** Percentage radii keep their value; only the inset changes. */
    @Test
    public void percentageRadiiKeepTheirValue() {
        CornerRadii percent = new CornerRadii(0.5, true);
        Region host = host(percent, Insets.EMPTY, new BorderWidths(1.0));

        BackgroundFill clip = clipFill(host, null);

        assertEquals(new Insets(1.0), clip.getInsets());
        assertEquals(percent, clip.getRadii());
    }

    // ==================== Helpers ====================

    private static Region host(CornerRadii radii, Insets fillInsets, BorderWidths borderWidths) {
        Region host = new Region();
        host.setBackground(new Background(new BackgroundFill(Color.WHITE, radii, fillInsets)));
        if (borderWidths != null) {
            host.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, radii, borderWidths)));
        }
        return host;
    }

    private static BackgroundFill clipFill(Region host, Insets insetsOverride) {
        Pane owner = new Pane();
        new BoundedClipSupport(owner).updateClipFor(host, WIDTH, HEIGHT, insetsOverride);
        return ((Region) owner.getClip()).getBackground().getFills().get(0);
    }
}
