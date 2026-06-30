package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.MasonryViewActionEvent;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skeleton tests for {@link RXMasonryView}: default property values, the lenient
 * geometry contract, the breakpoint API, CSS metadata, scrolling handshake and the
 * empty render (placeholder / {@code :empty}) before the rendering phase adds
 * virtualization.
 */
public class RXMasonryViewTest {

    private static final double EPSILON = 1.0e-6;

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

    // ==================== Defaults ====================

    @Test
    public void defaultStateAndStyleClass() {
        RXMasonryView<String> view = new RXMasonryView<>();

        assertTrue(view.getStyleClass().contains("rx-masonry-view"));
        assertEquals(AccessibleRole.PARENT, view.getAccessibleRole());
        assertTrue(view.isFocusTraversable());

        assertEquals(260.0, view.getColumnWidth(), EPSILON);
        assertEquals(8.0, view.getHgap(), EPSILON);
        assertEquals(8.0, view.getVgap(), EPSILON);
        assertEquals(0, view.getColumnCount());
        assertEquals(3, view.getPrefColumns());
        assertEquals(0, view.getMaxColumns());
        assertTrue(view.isFillWidth());
        assertEquals(Pos.TOP_LEFT, view.getAlignment());
        assertSame(RXBreakpointProfile.ANT_DESIGN, view.getBreakpointProfile());
        assertEquals(200.0, view.getEstimatedCellHeight(), EPSILON);
        assertFalse(view.isAnimated());
        assertEquals(Duration.millis(200.0), view.getAnimationDuration());
        assertEquals(Interpolator.EASE_BOTH, view.getAnimationInterpolator());
        assertEquals(0, RXMasonryView.AUTO_COLUMNS);

        assertNotNull(view.getItems());
        assertTrue(view.getItems().isEmpty());
        assertNull(view.getCellFactory());
        assertNull(view.getCellHeightProvider());
        assertNull(view.getColumnSpanFactory());
        assertNull(view.getPlaceholder());
        assertNull(view.getActiveBreakpoint());
        assertEquals(0, view.getActualColumnCount());
        assertEquals(-1, view.getFirstVisibleIndex());
        assertEquals(-1, view.getLastVisibleIndex());
    }

    @Test
    public void defaultSelectionModelIsSingleIndexedModel() {
        MultipleSelectionModel<String> model = new RXMasonryView<String>().getSelectionModel();
        assertNotNull(model);
        assertInstanceOf(RXIndexedSelectionModel.class, model);
        assertSame(SelectionMode.SINGLE, model.getSelectionMode());
    }

    // ==================== Lenient geometry ====================

    @Test
    public void lenientSizingValuesAreAcceptedNotRejected() {
        RXMasonryView<String> view = new RXMasonryView<>();
        view.setColumnWidth(-5.0);
        view.setColumnWidth(Double.NaN);
        view.setEstimatedCellHeight(-1.0);
        view.setHgap(Double.POSITIVE_INFINITY);
        view.setVgap(-4.0);

        // No exception; the lenient values round-trip and are coerced only at layout.
        assertTrue(Double.isNaN(view.getColumnWidth()));
        assertEquals(-1.0, view.getEstimatedCellHeight(), EPSILON);
        assertEquals(-4.0, view.getVgap(), EPSILON);
    }

    // ==================== Breakpoint API ====================

    @Test
    public void breakpointOverridesRoundTrip() {
        RXMasonryView<String> view = new RXMasonryView<>();
        view.setMd(3);
        view.setBreakpointColumns("lg", 4);
        view.setXl(RXMasonryView.AUTO_COLUMNS);

        assertEquals(3, view.getMd());
        assertEquals(4, view.getBreakpointColumns("lg"));
        assertEquals(0, view.getXl());
        assertNull(view.getSm());

        assertEquals(3, view.getBreakpointColumnOverrides().get("md"));
        assertThrows(UnsupportedOperationException.class,
                () -> view.getBreakpointColumnOverrides().put("sm", 2));

        view.setMd(null);
        assertNull(view.getMd());
    }

    @Test
    public void breakpointSettersValidateName() {
        RXMasonryView<String> view = new RXMasonryView<>();
        assertThrows(NullPointerException.class, () -> view.setBreakpointColumns(null, 2));
        assertThrows(IllegalArgumentException.class, () -> view.setBreakpointColumns("  ", 2));
        assertThrows(IllegalArgumentException.class, () -> view.setBreakpointColumns("md", -1));
    }

    @Test
    public void activeBreakpointSetterDrivesPseudoClass() {
        RXMasonryView<String> view = new RXMasonryView<>();
        assertNull(view.getActiveBreakpoint());

        RXBreakpoint md = new RXBreakpoint("md", 768.0);
        view.setActiveBreakpoint(md);
        assertSame(md, view.getActiveBreakpoint());
        assertTrue(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("md")));

        view.setActiveBreakpoint(null);
        assertNull(view.getActiveBreakpoint());
        assertFalse(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("md")));
    }

    // ==================== CSS metadata ====================

    @Test
    public void classCssMetadataExposesStyleableProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = RXMasonryView.getClassCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-column-width"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-column-count"));
        assertTrue(hasProperty(metadata, "-rx-pref-columns"));
        assertTrue(hasProperty(metadata, "-rx-max-columns"));
        assertTrue(hasProperty(metadata, "-rx-fill-width"));
        assertTrue(hasProperty(metadata, "-rx-alignment"));
        assertTrue(hasProperty(metadata, "-rx-animated"));
        assertTrue(hasProperty(metadata, "-rx-animation-duration"));

        RXMasonryView<String> view = new RXMasonryView<>();
        assertSame(metadata, view.getControlCssMetaData());
        assertTrue(metadata.containsAll(Control.getClassCssMetaData()));
        assertThrows(UnsupportedOperationException.class, metadata::clear);
    }

    @Test
    public void userAgentStylesheetIsPresent() {
        assertNotNull(new RXMasonryView<>().getUserAgentStylesheet());
    }

    // ==================== Scrolling handshake ====================

    @Test
    public void scrollToArmsAndClearsPendingScroll() {
        RXMasonryView<String> view = new RXMasonryView<>();
        assertFalse(view.hasPendingScroll());

        view.scrollTo(7, ScrollAlignment.CENTER);
        assertTrue(view.hasPendingScroll());
        assertEquals(7, view.getPendingScrollIndex());
        assertSame(ScrollAlignment.CENTER, view.getPendingScrollAlignment());

        view.clearPendingScroll();
        assertFalse(view.hasPendingScroll());

        // A null alignment falls back to START.
        view.scrollTo(2, null);
        assertSame(ScrollAlignment.START, view.getPendingScrollAlignment());
    }

    // ==================== On action ====================

    @Test
    public void onActionPropertyIsLazyAndStable() {
        RXMasonryView<String> view = new RXMasonryView<>();
        assertNull(view.getOnAction());
        assertSame(view.onActionProperty(), view.onActionProperty());
    }

    @Test
    public void onActionHandlerReceivesFiredEvent() throws Exception {
        AtomicReference<MasonryViewActionEvent<String>> received = new AtomicReference<>();
        runOnFx(() -> {
            RXMasonryView<String> view = new RXMasonryView<>(
                    javafx.collections.FXCollections.observableArrayList("a", "b"));
            view.setOnAction(received::set);
            Event.fireEvent(view, new MasonryViewActionEvent<>(view, "b", 1));
        });
        MasonryViewActionEvent<String> event = received.get();
        assertNotNull(event);
        assertEquals("b", event.getItem());
        assertEquals(1, event.getIndex());
        assertSame(MasonryViewActionEvent.ACTION, event.getEventType());
    }

    // ==================== Empty render ====================

    @Test
    public void emptyViewLaysOutInSceneWithoutError() throws Exception {
        runOnFx(() -> {
            RXMasonryView<String> view = new RXMasonryView<>();
            Scene scene = new Scene(view, 400.0, 300.0);
            view.applyCss();
            view.layout();
            assertNotNull(view.getSkin());
            assertNotNull(scene);
            // Empty view toggles the :empty pseudo-class.
            assertTrue(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));
        });
    }

    @Test
    public void placeholderShownOnlyWhenEmpty() throws Exception {
        runOnFx(() -> {
            RXMasonryView<String> view = new RXMasonryView<>();
            Region placeholder = new StackPane();
            view.setPlaceholder(placeholder);
            Scene scene = new Scene(view, 400.0, 300.0);
            view.applyCss();
            view.layout();
            assertSame(scene, view.getScene());
            assertTrue(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));

            view.getItems().add("one");
            view.applyCss();
            view.layout();
            assertFalse(view.getPseudoClassStates().contains(PseudoClass.getPseudoClass("empty")));
        });
    }

    // ==================== Helpers ====================

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metadata, String property) {
        return metadata.stream().anyMatch(meta -> property.equals(meta.getProperty()));
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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
