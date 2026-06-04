package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
        // Default B: an unloaded node with a loader set is a branch, no flags.
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> enabled = leaf("enabled");
        RXCascaderItem<String> disabled = leaf("disabled");
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
        AtomicReference<RXCascaderItem<String>> erroredItem = new AtomicReference<>();
        AtomicReference<Throwable> erroredCause = new AtomicReference<>();
        panel.setOnChildrenLoadError((failedItem, cause) -> {
            erroredItem.set(failedItem);
            erroredCause.set(cause);
        });
        panel.setChildrenLoader(item ->
                CompletableFuture.failedFuture(new IllegalStateException("load failed")));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        // Wait on the callback's own product (AtomicReference, with happens-before)
        // rather than the loading flag: completeLoad flips loading off just before
        // firing the error callback, so polling !isLoading across threads can exit
        // in that gap and observe a not-yet-fired callback.
        waitForFxCondition(() -> erroredItem.get() != null);

        assertFalse(root.isLoaded());
        assertFalse(root.isLoading());
        assertFalse(root.isChecked());
        assertFalse(root.isIndeterminate());
        assertTrue(panel.getCheckedPaths().isEmpty());
        assertSame(root, erroredItem.get());
        assertTrue(erroredCause.get() instanceof IllegalStateException);
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

    /**
     * Verifies expanding another branch replaces the active path, so columns of
     * an unrelated, previously expanded branch are dropped (the mechanism behind
     * "checking a branch focuses it and hides unrelated child columns").
     */
    @Test
    public void expandingAnotherBranchReplacesActivePath() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> asia = item("asia");
        asia.getChildren().setAll(List.of(item("china"), item("japan")));
        RXCascaderItem<String> germany = item("germany");
        germany.getChildren().setAll(List.of(item("berlin"), item("munich")));
        RXCascaderItem<String> europe = item("europe");
        europe.getChildren().setAll(List.of(germany));
        panel.getRootItems().setAll(List.of(asia, europe));

        panel.expand(europe);
        panel.expand(germany);
        assertEquals(List.of(europe, germany), panel.getActivePath());

        panel.expand(asia);
        assertEquals(List.of(asia), panel.getActivePath());
    }

    /**
     * Verifies ragged eager trees of unequal depth work with zero flags: leaf
     * state is derived purely from empty children.
     */
    @Test
    public void raggedEagerTreeSupportsUnequalDepths() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> a = item("A");
        RXCascaderItem<String> a1 = item("A1");
        RXCascaderItem<String> a11 = item("A1-1");
        a1.getChildren().add(a11);
        a.getChildren().add(a1);
        RXCascaderItem<String> b = item("B");
        RXCascaderItem<String> b1 = item("B1");
        b.getChildren().add(b1);
        panel.getRootItems().setAll(List.of(a, b));

        assertFalse(panel.isLeaf(a));
        assertFalse(panel.isLeaf(a1));
        assertTrue(panel.isLeaf(a11));
        assertFalse(panel.isLeaf(b));
        assertTrue(panel.isLeaf(b1));

        panel.activate(a);
        panel.activate(a1);
        panel.activate(a11);
        assertEquals(List.of(a, a1, a11), panel.getSelectedPath().getItems());

        panel.activate(b);
        panel.activate(b1);
        assertEquals(List.of(b, b1), panel.getSelectedPath().getItems());
    }

    /**
     * Verifies an unloaded node with a loader set is a branch (Default B) and
     * expanding it loads children that flip {@code loaded} to true.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void lazyExpandLoadsChildrenMarkingLoaded() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        panel.setChildrenLoader(item ->
                CompletableFuture.completedFuture(List.of(leaf("c1"), leaf("c2"))));
        panel.getRootItems().add(root);

        assertFalse(panel.isLeaf(root));
        assertFalse(root.isLoaded());

        panel.expand(root);
        waitForFxCondition(root::isLoaded);

        assertFalse(root.isLoading());
        assertEquals(2, root.getChildren().size());
        assertTrue(panel.isLeaf(root.getChildren().get(0)));
    }

    /**
     * Verifies a {@code leafHint=null} lazy branch that loads to zero children
     * becomes a leaf and adds no column.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void loadedEmptyLazyBranchBecomesLeaf() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        panel.setChildrenLoader(item -> CompletableFuture.completedFuture(List.of()));
        panel.getRootItems().add(root);

        assertFalse(panel.isLeaf(root));
        panel.expand(root);
        waitForFxCondition(root::isLoaded);

        assertTrue(panel.isLeaf(root));
    }

    /**
     * Verifies no-arg reload resets the lazy tree to a blank slate.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void reloadResetsLazyTree() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        panel.setChildrenLoader(item -> CompletableFuture.completedFuture(List.of(leaf("c1"))));
        panel.getRootItems().add(root);
        panel.expand(root);
        waitForFxCondition(root::isLoaded);
        assertEquals(1, root.getChildren().size());

        panel.reload();

        assertFalse(root.isLoaded());
        assertTrue(root.getChildren().isEmpty());
        assertTrue(panel.getActivePath().isEmpty());
    }

    /**
     * Verifies no-arg reload is a no-op in eager mode (no loader set).
     */
    @Test
    public void reloadIsNoOpInEagerMode() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> child = item("child");
        root.getChildren().add(child);
        panel.getRootItems().add(root);
        panel.expand(root);

        panel.reload();

        assertEquals(List.of(child), root.getChildren());
        assertEquals(List.of(root), panel.getActivePath());
    }

    /**
     * Verifies swapping in a non-null loader resets the whole tree.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void changingLoaderToNonNullResetsTree() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        panel.setChildrenLoader(item -> CompletableFuture.completedFuture(List.of(leaf("c1"))));
        panel.getRootItems().add(root);
        panel.expand(root);
        waitForFxCondition(root::isLoaded);
        assertEquals(1, root.getChildren().size());

        panel.setChildrenLoader(item -> CompletableFuture.completedFuture(List.of(leaf("d1"), leaf("d2"))));

        assertFalse(root.isLoaded());
        assertTrue(root.getChildren().isEmpty());
        assertTrue(panel.getActivePath().isEmpty());
    }

    /**
     * Verifies clearing the loader keeps the current tree and item check state
     * but recomputes the derived checked paths: a pending-checked unloaded branch
     * becomes an eager leaf that now belongs to the checked paths (Finding 1).
     */
    @Test
    public void clearingLoaderKeepsTreeAndRefreshesPaths() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        panel.setChildrenLoader(item -> new CompletableFuture<>());
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> branch = item("branch");
        root.getChildren().add(branch);
        panel.getRootItems().setAll(List.of(root));

        panel.setCheckedCascade(branch, true);
        assertTrue(branch.isChecked());
        assertTrue(branch.isLoading());
        assertTrue(panel.getCheckedPaths().isEmpty());

        panel.setChildrenLoader(null);

        assertFalse(branch.isLoading());
        assertTrue(branch.isChecked());
        assertTrue(panel.isLeaf(branch));
        assertEquals(1, panel.getCheckedPaths().size());
        assertSame(branch, panel.getCheckedPaths().get(0).getLeaf());
    }

    /**
     * Verifies a synchronous throw from the loader is routed to the failure path
     * (retriable, callback fired) instead of being rethrown.
     */
    @Test
    public void synchronousLoaderThrowIsHandledNotRethrown() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        AtomicReference<Throwable> cause = new AtomicReference<>();
        panel.setOnChildrenLoadError((item, error) -> cause.set(error));
        panel.setChildrenLoader(item -> {
            throw new IllegalStateException("boom");
        });
        panel.getRootItems().add(root);

        panel.expand(root);

        assertFalse(root.isLoading());
        assertFalse(root.isLoaded());
        assertTrue(cause.get() instanceof IllegalStateException);
    }

    /**
     * Verifies a second check while a lazy branch is still loading overwrites the
     * pending intent, so the replay after load honors the user's latest action
     * instead of the stale first check.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void laterCheckWhileLoadingOverwritesPending() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        RXCascaderItem<String> enabled = leaf("enabled");
        panel.setChildrenLoader(item -> gate);
        RXCascaderItem<String> root = item("root");
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        assertTrue(root.isLoading());
        assertTrue(root.isChecked());

        panel.setCheckedCascade(root, false);
        assertTrue(root.isLoading());
        assertFalse(root.isChecked());

        gate.complete(List.of(enabled));
        waitForFxCondition(root::isLoaded);

        assertFalse(enabled.isChecked());
        assertFalse(root.isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies a loader that fails inline on the FX thread fires the error
     * callback after the active path has been retargeted, matching the async
     * path (the callback observes the new active path, not the stale one).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void inlineFailingLoaderFiresErrorAfterActivePathUpdated() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            RXCascaderItem<String> root = item("root");
            AtomicReference<Integer> activePathSizeAtError = new AtomicReference<>();
            panel.setOnChildrenLoadError((item, error) ->
                    activePathSizeAtError.set(panel.getActivePath().size()));
            panel.setChildrenLoader(item -> {
                throw new IllegalStateException("boom");
            });
            panel.getRootItems().add(root);

            panel.expand(root);

            assertEquals(Integer.valueOf(1), activePathSizeAtError.get(),
                    "error callback should see the active path already containing the branch");
            assertEquals(List.of(root), panel.getActivePath());
            assertFalse(root.isLoading());
            assertFalse(root.isLoaded());
        });
    }

    /**
     * Verifies the loader is not invoked when the load is canceled between
     * {@code startLoad} and {@code runLoad} — here an active-path listener calls
     * {@code reload()} during {@code expand}, superseding the in-flight load.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void loaderSkippedWhenLoadCanceledMidExpand() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            int[] loaderCalls = {0};
            panel.setChildrenLoader(item -> {
                loaderCalls[0]++;
                return new CompletableFuture<>();
            });
            RXCascaderItem<String> root = item("root");
            panel.getRootItems().add(root);
            panel.getActivePath().addListener((InvalidationListener) obs -> {
                if (!panel.getActivePath().isEmpty() && loaderCalls[0] == 0) {
                    panel.reload();
                }
            });

            panel.expand(root);

            assertEquals(0, loaderCalls[0],
                    "a load canceled mid-expand must not invoke the loader");
            assertFalse(root.isLoading());
        });
    }

    private static RXCascaderItem<String> item(String text) {
        return new RXCascaderItem<>(text, text);
    }

    private static RXCascaderItem<String> leaf(String text) {
        RXCascaderItem<String> leaf = new RXCascaderItem<>(text, text);
        leaf.setLeafHint(true);
        return leaf;
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
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
