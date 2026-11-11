package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXAvatar.DisplayState;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXAvatar}: defaults, the image &gt; text &gt; icon priority and its
 * pseudo-classes, the shape and arc clip including the null fallback, the outline
 * ring, CSS metadata, square centred layout, clip hand-over across state changes,
 * and disposal.
 */
public class RXAvatarTest {

    private static final double EPSILON = 1.0e-9;
    private static final PseudoClass SHOWING_IMAGE = PseudoClass.getPseudoClass("showing-image");
    private static final PseudoClass SHOWING_TEXT = PseudoClass.getPseudoClass("showing-text");
    private static final PseudoClass SHOWING_ICON = PseudoClass.getPseudoClass("showing-default-icon");

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

    /** Verifies the defaults: circle, arc 10, image role, no Tab stop, default icon shown. */
    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = new RXAvatar();
            assertEquals(ShapeType.CIRCLE, avatar.getShapeType());
            assertEquals(RXAvatar.DEFAULT_SHAPE_TYPE, avatar.getShapeType());
            assertEquals(10.0, avatar.getArcWidth(), EPSILON);
            assertEquals(10.0, avatar.getArcHeight(), EPSILON);
            assertEquals(AccessibleRole.IMAGE_VIEW, avatar.getAccessibleRole());
            assertFalse(avatar.isFocusTraversable());
            assertEquals(DisplayState.EMPTY, avatar.getDisplayState());
            assertTrue(avatar.getPseudoClassStates().contains(SHOWING_ICON));
        });
    }

    /** Verifies a ready image wins over text, text over the icon, with one pseudo-class at a time. */
    @Test
    public void displayFollowsImageThenTextThenIcon() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = new RXAvatar();
            avatar.setText("LW");
            assertState(avatar, DisplayState.TEXT, SHOWING_TEXT);

            avatar.setImage(new WritableImage(10, 10));
            assertState(avatar, DisplayState.IMAGE, SHOWING_IMAGE);

            avatar.setImage(null);
            assertState(avatar, DisplayState.TEXT, SHOWING_TEXT);

            avatar.setText("");
            assertState(avatar, DisplayState.EMPTY, SHOWING_ICON);
        });
    }

    /** Verifies an image that failed to load falls back to the text. */
    @Test
    public void failedImageFallsBackToText() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = new RXAvatar();
            avatar.setText("LW");
            Image broken = new Image("file:/rxcontrols-avatar-test-missing.png");
            assertTrue(broken.isError());
            avatar.setImage(broken);
            assertState(avatar, DisplayState.TEXT, SHOWING_TEXT);
        });
    }

    /** Verifies circle and square clips, rounded square corners, and a null shape drawing a circle. */
    @Test
    public void shapeTypeDrivesTheClip() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = laidOut(new RXAvatar(), 100.0, 100.0);
            Region icon = (Region) avatar.lookup(".default-icon-wrapper");
            assertTrue(icon.getClip() instanceof Circle);

            avatar.setShapeType(ShapeType.SQUARE);
            avatar.layout();
            Rectangle square = (Rectangle) icon.getClip();
            assertEquals(10.0, square.getArcWidth(), EPSILON);

            avatar.setArcWidth(-4.0);
            avatar.layout();
            assertEquals(0.0, ((Rectangle) icon.getClip()).getArcWidth(), EPSILON);

            avatar.setShapeType(null);
            avatar.layout();
            assertTrue(icon.getClip() instanceof Circle);

            SimpleObjectProperty<ShapeType> source = new SimpleObjectProperty<>(ShapeType.SQUARE);
            avatar.shapeTypeProperty().bind(source);
            avatar.layout();
            assertTrue(icon.getClip() instanceof Rectangle);
            source.set(null);
            avatar.layout();
            assertTrue(icon.getClip() instanceof Circle);
        });
    }

    /** Verifies the ring sits on top, covers the centred square, and follows the shape and arcs. */
    @Test
    public void ringOutlinesTheClipShape() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = laidOut(new RXAvatar(), 120.0, 80.0);
            Rectangle ring = (Rectangle) avatar.lookup(".ring");
            List<Node> children = avatar.getChildrenUnmodifiable();
            assertSame(ring, children.get(children.size() - 1));
            assertTrue(ring.isMouseTransparent());
            assertNull(ring.getFill());
            assertEquals(20.0, ring.getX(), EPSILON);
            assertEquals(0.0, ring.getY(), EPSILON);
            assertEquals(80.0, ring.getWidth(), EPSILON);
            assertEquals(80.0, ring.getArcWidth(), EPSILON);

            avatar.setShapeType(ShapeType.SQUARE);
            avatar.setArcWidth(16.0);
            avatar.setArcHeight(12.0);
            avatar.layout();
            assertEquals(16.0, ring.getArcWidth(), EPSILON);
            assertEquals(12.0, ring.getArcHeight(), EPSILON);
        });
    }

    /** Verifies the shape and arcs are settable from CSS. */
    @Test
    public void cssSetsShapeAndArcs() throws Exception {
        runOnFx(() -> {
            Set<String> properties = RXAvatar.getClassCssMetaData().stream()
                    .map(CssMetaData<? extends Styleable, ?>::getProperty)
                    .filter(property -> property.startsWith("-rx-"))
                    .collect(Collectors.toSet());
            assertEquals(Set.of("-rx-shape-type", "-rx-arc-width", "-rx-arc-height"), properties);

            RXAvatar avatar = new RXAvatar();
            avatar.setStyle("-rx-shape-type: square; -rx-arc-width: 16; -rx-arc-height: 12;");
            laidOut(avatar, 100.0, 100.0);
            assertEquals(ShapeType.SQUARE, avatar.getShapeType());
            assertEquals(16.0, avatar.getArcWidth(), EPSILON);
            assertEquals(12.0, avatar.getArcHeight(), EPSILON);
        });
    }

    /** Verifies the visible area is the largest centred square. */
    @Test
    public void visibleAreaIsACentredSquare() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = laidOut(new RXAvatar(), 120.0, 80.0);
            Region icon = (Region) avatar.lookup(".default-icon-wrapper");
            assertEquals(80.0, icon.getWidth(), EPSILON);
            assertEquals(80.0, icon.getHeight(), EPSILON);
            assertEquals(20.0, icon.getLayoutX(), EPSILON);
            assertEquals(0.0, icon.getLayoutY(), EPSILON);
        });
    }

    /** Verifies the shared clip moves to each newly shown node, with layout after every change. */
    @Test
    public void clipFollowsTheShownNodeAcrossStates() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = laidOut(new RXAvatar(), 100.0, 100.0);
            Region icon = (Region) avatar.lookup(".default-icon-wrapper");
            Region text = (Region) avatar.lookup(".text-wrapper");
            ImageView imageView = imageView(avatar);
            WritableImage image = new WritableImage(10, 10);

            Runnable[] steps = {
                () -> avatar.setText("LW"),
                () -> avatar.setImage(image),
                () -> avatar.setShapeType(ShapeType.SQUARE),
                () -> avatar.setImage(null),
                () -> avatar.setText(null),
                () -> avatar.setImage(image),
                () -> avatar.setShapeType(ShapeType.CIRCLE),
            };
            for (Runnable step : steps) {
                step.run();
                avatar.layout();
                List<Node> clipped = List.of(icon, text, imageView).stream()
                        .filter(node -> node.getClip() != null)
                        .collect(Collectors.toList());
                assertEquals(1, clipped.size(), "one clipped node in " + avatar.getDisplayState());
            }
            assertTrue(imageView.getClip() instanceof Circle);
        });
    }

    /** Verifies removing the skin releases the image and the clip. */
    @Test
    public void removingTheSkinReleasesTheImageAndClip() throws Exception {
        runOnFx(() -> {
            RXAvatar avatar = laidOut(new RXAvatar(new WritableImage(10, 10)), 100.0, 100.0);
            ImageView imageView = imageView(avatar);
            assertTrue(imageView.getClip() != null);
            avatar.setSkin(null);
            assertNull(imageView.getImage());
            assertNull(imageView.getClip());
            assertNull(imageView.getViewport());
        });
    }

    // ==================== Helpers ====================

    private static void assertState(RXAvatar avatar, DisplayState state, PseudoClass shown) {
        assertEquals(state, avatar.getDisplayState());
        for (PseudoClass pseudoClass : List.of(SHOWING_IMAGE, SHOWING_TEXT, SHOWING_ICON)) {
            assertEquals(pseudoClass == shown, avatar.getPseudoClassStates().contains(pseudoClass),
                    pseudoClass.getPseudoClassName());
        }
    }

    private static RXAvatar laidOut(RXAvatar avatar, double width, double height) {
        new Scene(new StackPane(avatar));
        avatar.applyCss();
        avatar.resize(width, height);
        avatar.layout();
        return avatar;
    }

    private static ImageView imageView(RXAvatar avatar) {
        Group wrapper = (Group) avatar.lookup(".image-wrapper");
        Node child = wrapper.getChildren().get(0);
        assertSame(ImageView.class, child.getClass());
        return (ImageView) child;
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
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }
}
