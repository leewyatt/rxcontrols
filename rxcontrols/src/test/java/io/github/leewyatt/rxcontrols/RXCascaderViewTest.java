package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXCascaderView}.
 */
public class RXCascaderViewTest {

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
    }

    /**
     * Verifies locked disabled children participate in display rollup.
     */
    @Test
    public void disabledUncheckedChildKeepsAncestorsIndeterminate() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> china = item("china");
        RXCascaderItem<String> japan = item("japan");
        RXCascaderItem<String> shanghai = item("shanghai");
        RXCascaderItem<String> hangzhou = item("hangzhou");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisabled(true);
        china.getChildren().setAll(List.of(shanghai, hangzhou, disabled));
        root.getChildren().setAll(List.of(china, japan));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);

        assertFalse(china.isChecked());
        assertTrue(china.isIndeterminate());
        assertFalse(root.isChecked());
        assertTrue(root.isIndeterminate());
        assertTrue(shanghai.isChecked());
        assertTrue(hangzhou.isChecked());
        assertTrue(japan.isChecked());
        assertFalse(disabled.isChecked());
        assertEquals(3, panel.getCheckedPaths().size());
    }

    /**
     * Verifies a locked checked child counts as checked in display rollup.
     */
    @Test
    public void disabledCheckedChildCountsAsChecked() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> first = item("first");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisabled(true);
        disabled.setChecked(true);
        root.getChildren().setAll(List.of(first, disabled));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);

        assertTrue(root.isChecked());
        assertFalse(root.isIndeterminate());
        assertTrue(first.isChecked());
        assertTrue(disabled.isChecked());
        assertEquals(2, panel.getCheckedPaths().size());
    }

    /**
     * Verifies branch toggle direction uses enabled leaves, not display checked.
     */
    @Test
    public void toggleIndeterminateBranchUsesEnabledLeaves() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> first = item("first");
        RXCascaderItem<String> second = item("second");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisabled(true);
        root.getChildren().setAll(List.of(first, second, disabled));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        panel.toggleCheck(root);

        assertFalse(root.isChecked());
        assertFalse(root.isIndeterminate());
        assertFalse(first.isChecked());
        assertFalse(second.isChecked());
        assertFalse(disabled.isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies child-to-parent indeterminate propagation.
     */
    @Test
    public void partialChildCheckMakesParentIndeterminate() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> first = item("first");
        RXCascaderItem<String> second = item("second");
        root.getChildren().setAll(List.of(first, second));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(first, true);

        assertFalse(root.isChecked());
        assertTrue(root.isIndeterminate());
        assertEquals(1, panel.getCheckedPaths().size());
        assertSame(first, panel.getCheckedPaths().get(0).getLeaf());
    }

    /**
     * Verifies pending checked state is replayed after lazy children load.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void pendingCheckReplaysAfterLazyLoad() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        root.setLoaded(false);
        root.setLeafHint(false);
        RXCascaderItem<String> enabled = item("enabled");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisabled(true);
        panel.setChildrenLoader(item ->
                CompletableFuture.completedFuture(List.of(enabled, disabled)));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        waitForFxCondition(() -> root.isLoaded() && panel.getCheckedPaths().size() == 1);

        assertTrue(root.isLoaded());
        assertFalse(root.isLoading());
        assertFalse(root.isChecked());
        assertTrue(root.isIndeterminate());
        assertTrue(enabled.isChecked());
        assertFalse(disabled.isChecked());
        assertEquals(1, panel.getCheckedPaths().size());
        assertSame(enabled, panel.getCheckedPaths().get(0).getLeaf());
    }

    /**
     * Verifies failed lazy loading rolls back pending checked state.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void failedLazyLoadClearsPendingCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        root.setLoaded(false);
        root.setLeafHint(false);
        panel.setChildrenLoader(item ->
                CompletableFuture.failedFuture(new IllegalStateException("load failed")));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        waitForFxCondition(() -> !root.isLoading());

        assertFalse(root.isLoaded());
        assertFalse(root.isLoading());
        assertFalse(root.isChecked());
        assertFalse(root.isIndeterminate());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies single-selection activation returns an immutable root-to-leaf path.
     */
    @Test
    public void singleSelectionActivationCreatesPath() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> child = item("child");
        root.getChildren().add(child);
        panel.getRootItems().add(root);

        panel.activate(root);
        panel.activate(child);

        assertEquals(List.of(root, child), panel.getSelectedPath().getItems());
        assertEquals(List.of("root", "child"), panel.getSelectedPath().getTexts());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    private static RXCascaderItem<String> item(String text) {
        return new RXCascaderItem<>(text, text);
    }

    private static void flushFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX event queue did not flush");
        }
    }

    private static void waitForFxCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            flushFxEvents();
            Thread.sleep(10);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("FX condition was not reached");
        }
    }
}
