package io.github.leewyatt.rxcontrols;

import javafx.beans.DefaultProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXContentPane}.
 */
public class RXContentPaneTest {

    private static final double EPSILON = 0.0001;

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
     * Verifies the FXML default property is the content slot.
     */
    @Test
    public void defaultPropertyIsContent() {
        DefaultProperty annotation = RXContentPane.class.getAnnotation(DefaultProperty.class);

        assertEquals("content", annotation.value());
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

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
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
