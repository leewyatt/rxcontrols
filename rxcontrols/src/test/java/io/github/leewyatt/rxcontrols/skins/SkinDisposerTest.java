package io.github.leewyatt.rxcontrols.skins;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link SkinDisposer}.
 */
public class SkinDisposerTest {

    private static final EventType<Event> TEST_EVENT =
            new EventType<>(Event.ANY, "SKIN_DISPOSER_TEST_EVENT");

    /**
     * Verifies LIFO cleanup, complete cleanup after failures, and suppressed
     * exception collection.
     */
    @Test
    public void disposeRunsAllTasksAndRethrowsFirstFailure() {
        SkinDisposer disposer = new SkinDisposer();
        List<String> calls = new ArrayList<>();
        RuntimeException primary = new RuntimeException("primary");
        RuntimeException secondary = new RuntimeException("secondary");

        disposer.registerDisposeTask(() -> calls.add("first"));
        disposer.registerDisposeTask(() -> {
            calls.add("second");
            throw secondary;
        });
        disposer.registerDisposeTask(() -> {
            calls.add("third");
            throw primary;
        });

        RuntimeException exception = assertThrows(RuntimeException.class, disposer::dispose);

        assertSame(primary, exception);
        assertEquals(List.of("third", "second", "first"), calls);
        assertEquals(1, exception.getSuppressed().length);
        assertSame(secondary, exception.getSuppressed()[0]);

        disposer.dispose();
        assertEquals(List.of("third", "second", "first"), calls);
    }

    /**
     * Verifies shared dispose sequencing keeps the first failure as primary
     * when a later step also fails.
     */
    @Test
    public void disposeInOrderKeepsFirstFailureAsPrimary() {
        List<String> calls = new ArrayList<>();
        RuntimeException primary = new RuntimeException("primary");
        RuntimeException secondary = new RuntimeException("secondary");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> SkinDisposer.disposeInOrder(
                () -> {
                    calls.add("first");
                    throw primary;
                },
                () -> calls.add("second"),
                () -> {
                    calls.add("third");
                    throw secondary;
                }));

        assertSame(primary, exception);
        assertEquals(List.of("first", "second", "third"), calls);
        assertEquals(1, exception.getSuppressed().length);
        assertSame(secondary, exception.getSuppressed()[0]);
    }

    /**
     * Verifies null arguments fail at registration time.
     */
    @Test
    public void registerMethodsRejectNullImmediately() {
        SkinDisposer disposer = new SkinDisposer();
        StringProperty target = new SimpleStringProperty();
        StringProperty source = new SimpleStringProperty();

        assertThrows(NullPointerException.class, () -> disposer.registerDisposeTask(null));
        assertThrows(NullPointerException.class, () -> disposer.registerBinding(null, source));
        assertThrows(NullPointerException.class, () -> disposer.registerBinding(target, null));
        assertThrows(NullPointerException.class, () -> disposer.registerListener(target, (Runnable) null));
        assertThrows(NullPointerException.class, () -> disposer.registerListener(null, () -> {
        }));
        assertThrows(NullPointerException.class,
                () -> disposer.registerListener(target, (ChangeListener<Object>) null));

        Pane node = new Pane();
        EventHandler<Event> handler = event -> {
        };
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventHandler(null, TEST_EVENT, handler));
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventHandler(node, null, handler));
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventHandler(node, TEST_EVENT, null));
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventFilter(null, TEST_EVENT, handler));
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventFilter(node, null, handler));
        assertThrows(NullPointerException.class,
                () -> disposer.registerEventFilter(node, TEST_EVENT, null));
    }

    /**
     * Verifies the change-listener overload accepts supertype listeners and
     * removes them during dispose.
     */
    @Test
    public void changeListenerIsRemovedOnDispose() {
        SkinDisposer disposer = new SkinDisposer();
        StringProperty text = new SimpleStringProperty("a");
        AtomicInteger calls = new AtomicInteger();
        ChangeListener<Object> listener = (observable, oldValue, newValue) -> calls.incrementAndGet();

        disposer.registerListener(text, listener);

        text.set("b");
        disposer.dispose();
        text.set("c");

        assertEquals(1, calls.get());
    }

    /**
     * Verifies event handlers are removed during dispose.
     */
    @Test
    public void eventHandlerIsRemovedOnDispose() {
        SkinDisposer disposer = new SkinDisposer();
        Pane node = new Pane();
        AtomicInteger calls = new AtomicInteger();

        disposer.registerEventHandler(node, TEST_EVENT, event -> calls.incrementAndGet());

        node.fireEvent(new Event(TEST_EVENT));
        disposer.dispose();
        node.fireEvent(new Event(TEST_EVENT));

        assertEquals(1, calls.get());
    }

    /**
     * Verifies event filters are removed during dispose.
     */
    @Test
    public void eventFilterIsRemovedOnDispose() {
        SkinDisposer disposer = new SkinDisposer();
        Pane node = new Pane();
        AtomicInteger calls = new AtomicInteger();

        disposer.registerEventFilter(node, TEST_EVENT, event -> calls.incrementAndGet());

        node.fireEvent(new Event(TEST_EVENT));
        disposer.dispose();
        node.fireEvent(new Event(TEST_EVENT));

        assertEquals(1, calls.get());
    }
}
