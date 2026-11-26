package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXContentPane}.
 */
public class RXContentPaneTest {

    private static final double EPSILON = 0.0001;

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
        Platform.setImplicitExit(false);
    }

    /**
     * Verifies default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXContentPane pane = new RXContentPane();

        assertTrue(pane.getStyleClass().contains("rx-content-pane"));
        assertNull(pane.getContent());
        assertEquals(RXContentPane.DEFAULT_ALIGNMENT, pane.getAlignment());
        assertEquals(0, pane.getChildrenUnmodifiable().size());

        Set<String> properties = RXContentPane.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-fx-alignment"));
    }

    /**
     * Verifies FXML fills the content slot from a child element and from an
     * explicit content element.
     */
    @Test
    public void fxmlFillsTheContentSlot() throws Exception {
        onFx(() -> {
            for (String body : List.of("<Region/>", "<content><Region/></content>")) {
                RXContentPane pane = load("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <?import io.github.leewyatt.rxcontrols.RXContentPane?>
                        <?import javafx.scene.layout.Region?>
                        <RXContentPane xmlns="http://javafx.com/javafx/17">%s</RXContentPane>
                        """.formatted(body));
                Region content = assertInstanceOf(Region.class, pane.getContent(), body);
                assertEquals(List.of(content), pane.getChildrenUnmodifiable(), body);
            }
        });
    }

    /**
     * Verifies binding the content property swaps the single child without
     * hand-written listener code at the call site.
     */
    @Test
    public void boundContentPropertyDrivesChildren() {
        SimpleObjectProperty<Node> source = new SimpleObjectProperty<>();
        RXContentPane pane = new RXContentPane();
        Region first = new Region();
        Region second = new Region();

        pane.contentProperty().bind(source);
        source.set(first);

        assertSame(first, pane.getContent());
        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertSame(first, pane.getChildrenUnmodifiable().get(0));

        source.set(second);

        assertNull(first.getParent());
        assertSame(second, pane.getContent());
        assertSame(second, pane.getChildrenUnmodifiable().get(0));

        source.set(null);

        assertNull(second.getParent());
        assertNull(pane.getContent());
        assertEquals(0, pane.getChildrenUnmodifiable().size());
    }

    /**
     * Verifies managed content drives measurement and content bias.
     */
    @Test
    public void contentDrivesMeasurementAndBias() {
        FixedRegion content = new FixedRegion(20.0, 10.0, 120.0, 40.0,
                Orientation.HORIZONTAL);
        RXContentPane pane = new RXContentPane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertClose(26.0, pane.minWidth(-1.0), "min width");
        assertClose(14.0, pane.minHeight(-1.0), "min height");
        assertClose(126.0, pane.prefWidth(-1.0), "pref width");
        assertClose(44.0, pane.prefHeight(-1.0), "pref height");
        assertClose(Double.MAX_VALUE, pane.maxWidth(-1.0), "max width");
        assertClose(Double.MAX_VALUE, pane.maxHeight(-1.0), "max height");
    }

    /**
     * Verifies unmanaged content remains attached but is ignored by measurement,
     * content bias and layout.
     */
    @Test
    public void unmanagedContentIsIgnoredByMeasurementBiasAndLayout() {
        FixedRegion content = new FixedRegion(20.0, 10.0, 120.0, 40.0,
                Orientation.HORIZONTAL);
        content.setManaged(false);
        content.resizeRelocate(11.0, 12.0, 13.0, 14.0);
        RXContentPane pane = new RXContentPane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        assertNull(pane.getContentBias());
        assertClose(6.0, pane.minWidth(-1.0), "min width");
        assertClose(4.0, pane.minHeight(-1.0), "min height");
        assertClose(6.0, pane.prefWidth(-1.0), "pref width");
        assertClose(4.0, pane.prefHeight(-1.0), "pref height");

        layout(pane, 100.0, 50.0);

        assertClose(11.0, content.getLayoutX(), "content x");
        assertClose(12.0, content.getLayoutY(), "content y");
        assertClose(13.0, content.getLayoutBounds().getWidth(), "content width");
        assertClose(14.0, content.getLayoutBounds().getHeight(), "content height");
    }

    /**
     * Verifies preferred measurement uses the same child min/pref/max bounding
     * rule as JavaFX layout panes.
     */
    @Test
    public void preferredSizeIsBoundedByContentMax() {
        Region content = new Region();
        content.setMinSize(10.0, 5.0);
        content.setPrefSize(120.0, 80.0);
        content.setMaxSize(40.0, 20.0);
        RXContentPane pane = new RXContentPane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        assertClose(46.0, pane.prefWidth(-1.0), "pref width");
        assertClose(24.0, pane.prefHeight(-1.0), "pref height");

        layout(pane, 100.0, 50.0);

        assertClose(40.0, content.getLayoutBounds().getWidth(), "content width");
        assertClose(20.0, content.getLayoutBounds().getHeight(), "content height");
    }

    /**
     * Verifies alignment positions managed content that cannot fill the pane.
     */
    @Test
    public void alignmentPositionsNonFillingContent() {
        Region content = new Region();
        content.setPrefSize(40.0, 20.0);
        content.setMaxSize(40.0, 20.0);
        RXContentPane pane = new RXContentPane(content);

        layout(pane, 100.0, 50.0);

        assertClose(30.0, content.getLayoutX(), "centered x");
        assertClose(15.0, content.getLayoutY(), "centered y");

        pane.setAlignment(Pos.BOTTOM_RIGHT);
        layout(pane, 100.0, 50.0);

        assertClose(60.0, content.getLayoutX(), "bottom-right x");
        assertClose(30.0, content.getLayoutY(), "bottom-right y");
    }

    /**
     * Verifies a content node taken by another parent is no longer measured or
     * laid out, while the binding stays and shows the next node.
     */
    @Test
    public void contentTakenByAnotherParentIsLeftAlone() {
        FixedRegion graphic = new FixedRegion(20.0, 10.0, 80.0, 30.0,
                Orientation.HORIZONTAL);
        SimpleObjectProperty<Node> source = new SimpleObjectProperty<>(graphic);
        RXContentPane pane = new RXContentPane();
        pane.contentProperty().bind(source);

        Pane taker = new Pane(graphic);
        graphic.resizeRelocate(10.0, 15.0, 65.0, 35.0);
        layout(pane, 200.0, 100.0);

        assertSame(graphic, pane.getContent());
        assertNull(pane.getContentBias());
        assertClose(0.0, pane.prefWidth(-1.0), "pref width");
        assertArrayEquals(new double[] {10.0, 15.0, 65.0, 35.0}, geometry(graphic), EPSILON);

        Region next = new Region();
        source.set(next);

        assertEquals(List.of(next), pane.getChildrenUnmodifiable());
        assertSame(taker, graphic.getParent());
    }

    /**
     * Verifies a content set from a listener that runs while the pane attaches
     * or detaches a node replaces the one on its way, and no node keeps the scene.
     */
    @Test
    public void contentSetWhileAnotherIsOnItsWayIsTheOneThatStays() throws Exception {
        onFx(() -> {
            RXContentPane pane = new RXContentPane();
            Scene scene = new Scene(pane, 100.0, 80.0);

            Region cleared = new Region();
            cleared.parentProperty().addListener((obs, old, parent) -> {
                if (parent == pane) {
                    pane.setContent(null);
                }
            });
            pane.setContent(cleared);

            assertNull(pane.getContent());
            assertEquals(List.of(), pane.getChildrenUnmodifiable());
            assertNull(cleared.getParent());
            assertNull(cleared.getScene(), "cleared as the pane takes it");

            SimpleObjectProperty<Node> source = new SimpleObjectProperty<>();
            pane.contentProperty().bind(source);
            Region first = new Region();
            Region second = new Region();
            first.parentProperty().addListener((obs, old, parent) -> {
                if (parent == pane) {
                    source.set(second);
                }
            });
            source.set(first);

            assertEquals(List.of(second), pane.getChildrenUnmodifiable());
            assertNull(first.getScene(), "sent on as the pane takes it");
            assertSame(scene, second.getScene());

            Region asked = new Region();
            Region last = new Region();
            second.parentProperty().addListener((obs, old, parent) -> {
                if (parent == null) {
                    source.set(last);
                }
            });
            source.set(asked);

            assertSame(last, pane.getContent());
            assertEquals(List.of(last), pane.getChildrenUnmodifiable());
            assertNull(asked.getScene(), "sent elsewhere as the old node leaves");
        });
    }

    /**
     * Verifies a size asked for between two nested content changes is not kept.
     */
    @Test
    public void sizeAskedForBetweenNestedContentChangesIsNotKept() throws Exception {
        onFx(() -> {
            Region first = new Region();
            first.setPrefSize(70.0, 30.0);
            Region second = new Region();
            second.setPrefSize(140.0, 60.0);
            RXContentPane pane = new RXContentPane(first);
            HBox row = new HBox(pane);
            new Scene(row, 400.0, 300.0);
            row.layout();
            boolean[] done = new boolean[1];
            pane.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                if (!done[0]) {
                    done[0] = true;
                    pane.setContent(null);
                    pane.prefWidth(-1.0);
                    pane.setContent(second);
                }
            });

            pane.setContent(second);
            assertClose(140.0, pane.prefWidth(-1.0), "pref width");
            row.layout();
            assertClose(140.0, second.getWidth(), "width given by the row");
        });
    }

    /**
     * Verifies a rejected content node leaves a pane that still measures, lays
     * out nothing foreign and takes the next node.
     */
    @Test
    public void rejectedContentDoesNotBreakThePane() {
        RXContentPane pane = new RXContentPane(new Region());
        StackPane ancestor = new StackPane(pane);

        assertThrows(IllegalArgumentException.class, () -> pane.setContent(ancestor));
        ancestor.resizeRelocate(5.0, 6.0, 300.0, 200.0);
        layout(pane, 100.0, 50.0);
        pane.prefWidth(-1.0);

        assertArrayEquals(new double[] {5.0, 6.0, 300.0, 200.0}, geometry(ancestor), EPSILON);

        Region next = new Region();
        pane.setContent(next);

        assertEquals(List.of(next), pane.getChildrenUnmodifiable());
    }

    /**
     * Verifies a single node is laid out as a {@code StackPane} lays it out, for
     * every alignment, baseline included, and with one axis at zero.
     */
    @Test
    public void singleNodeIsLaidOutAsInAStackPane() throws Exception {
        onFx(() -> {
            List<Supplier<Node>> kinds = List.of(
                    Region::new,
                    () -> {
                        Region capped = new Region();
                        capped.setPrefSize(40.0, 20.0);
                        capped.setMaxSize(40.0, 20.0);
                        return capped;
                    },
                    () -> new Rectangle(30.0, 10.0),
                    () -> new Text("Text"));
            double[][] sizes = {{100.0, 60.0}, {150.0, 0.0}, {0.0, 60.0}};
            Insets padding = new Insets(1.0, 2.0, 3.0, 4.0);
            for (Supplier<Node> kind : kinds) {
                for (double[] size : sizes) {
                    for (Pos pos : Pos.values()) {
                        Node content = kind.get();
                        RXContentPane pane = new RXContentPane(content);
                        pane.setAlignment(pos);
                        pane.setPadding(padding);
                        Node expected = kind.get();
                        StackPane reference = new StackPane(expected);
                        reference.setAlignment(pos);
                        reference.setPadding(padding);

                        layout(pane, size[0], size[1]);
                        layout(reference, size[0], size[1]);

                        assertArrayEquals(geometry(expected), geometry(content), EPSILON,
                                content.getClass().getSimpleName() + " " + pos + " " + size[0] + "x" + size[1]);
                    }
                }
            }
        });
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    private static double[] geometry(Node node) {
        return new double[] {node.getLayoutX(), node.getLayoutY(),
                node.getLayoutBounds().getWidth(), node.getLayoutBounds().getHeight()};
    }

    private static RXContentPane load(String fxml) {
        try {
            return new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static final class FixedRegion extends Region {

        private final double minWidth;
        private final double minHeight;
        private final double prefWidth;
        private final double prefHeight;
        private final Orientation contentBias;

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight,
                            Orientation contentBias) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            this.contentBias = contentBias;
        }

        @Override
        public Orientation getContentBias() {
            return contentBias;
        }

        @Override
        protected double computeMinWidth(double height) {
            return minWidth;
        }

        @Override
        protected double computeMinHeight(double width) {
            return minHeight;
        }

        @Override
        protected double computePrefWidth(double height) {
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return prefHeight;
        }
    }
}
