package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXCascaderItem.LoadState;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.scene.control.SelectionMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> china = item("china");
        RXCascaderItem<String> japan = item("japan");
        RXCascaderItem<String> shanghai = item("shanghai");
        RXCascaderItem<String> hangzhou = item("hangzhou");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisable(true);
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> first = item("first");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisable(true);
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> first = item("first");
        RXCascaderItem<String> second = item("second");
        RXCascaderItem<String> disabled = item("disabled");
        disabled.setDisable(true);
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        // Default B: an unloaded node with a loader set is a branch, no flags.
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> enabled = leaf("enabled");
        RXCascaderItem<String> disabled = leaf("disabled");
        disabled.setDisable(true);
        panel.setChildrenLoader(item ->
                CompletableFuture.completedFuture(List.of(enabled, disabled)));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        waitForFxCondition(() -> loaded(root) && panel.getCheckedPaths().size() == 1);

        assertTrue(loaded(root));
        assertFalse(loading(root));
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
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
        // rather than the load state: completeLoad transitions to FAILED just
        // before firing the error callback, so polling the state across threads can
        // exit in that gap and observe a not-yet-fired callback.
        waitForFxCondition(() -> erroredItem.get() != null);

        assertFalse(loaded(root));
        assertFalse(loading(root));
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
        assertEquals(List.of("root", "child"), panel.getSelectedPath().getValues());
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
     * Verifies activating a shorter leaf path trims stale deeper active branches,
     * so columns for a previously expanded sibling branch disappear.
     */
    @Test
    public void activatingShorterLeafRetargetsActivePathToAncestors() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> asia = item("Asia");
        RXCascaderItem<String> japan = item("Japan");
        japan.getChildren().setAll(List.of(item("Tokyo"), item("Osaka")));
        RXCascaderItem<String> singapore = item("Singapore");
        asia.getChildren().setAll(List.of(japan, singapore));
        panel.getRootItems().setAll(List.of(asia));

        panel.activate(asia);
        panel.activate(japan);
        assertEquals(List.of(asia, japan), panel.getActivePath());

        int revision = panel.getColumnsRevision();
        panel.activate(singapore);

        assertEquals(List.of(asia, singapore), panel.getSelectedPath().getItems());
        assertEquals(List.of(asia), panel.getActivePath(),
                "the active path must collapse to the selected leaf's ancestors");
        assertTrue(panel.getColumnsRevision() > revision,
                "trimming a stale branch must signal the skin to rebuild columns");
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
        assertFalse(loaded(root));

        panel.expand(root);
        waitForFxCondition(() -> loaded(root));

        assertFalse(loading(root));
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
        waitForFxCondition(() -> loaded(root));

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
        waitForFxCondition(() -> loaded(root));
        assertEquals(1, root.getChildren().size());

        panel.reload();

        assertFalse(loaded(root));
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
        waitForFxCondition(() -> loaded(root));
        assertEquals(1, root.getChildren().size());

        panel.setChildrenLoader(item -> CompletableFuture.completedFuture(List.of(leaf("d1"), leaf("d2"))));

        assertFalse(loaded(root));
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        panel.setChildrenLoader(item -> new CompletableFuture<>());
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> branch = item("branch");
        root.getChildren().add(branch);
        panel.getRootItems().setAll(List.of(root));

        panel.setCheckedCascade(branch, true);
        assertTrue(branch.isChecked());
        assertTrue(loading(branch));
        assertTrue(panel.getCheckedPaths().isEmpty());

        panel.setChildrenLoader(null);

        assertFalse(loading(branch));
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

        assertFalse(loading(root));
        assertFalse(loaded(root));
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
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        RXCascaderItem<String> enabled = leaf("enabled");
        panel.setChildrenLoader(item -> gate);
        RXCascaderItem<String> root = item("root");
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        assertTrue(loading(root));
        assertTrue(root.isChecked());

        panel.setCheckedCascade(root, false);
        assertTrue(loading(root));
        assertFalse(root.isChecked());

        gate.complete(List.of(enabled));
        waitForFxCondition(() -> loaded(root));

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
            assertFalse(loading(root));
            assertFalse(loaded(root));
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
            assertFalse(loading(root));
        });
    }

    /**
     * Verifies a {@code null} selection mode is accepted without throwing and
     * degrades to single-selection behavior at the use site.
     */
    @Test
    public void setSelectionModeNullDegradesToSingle() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> child = item("child");
        root.getChildren().add(child);
        panel.getRootItems().add(root);

        panel.setSelectionMode(null);
        assertNull(panel.getSelectionMode());

        // A null mode resolves to single-selection behavior: activating a leaf
        // selects its path rather than toggling a check.
        panel.activate(root);
        panel.activate(child);
        assertNotNull(panel.getSelectedPath(), "null mode must behave as single-selection");
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    @Test
    public void nullSelectionModeAllowsSelectViaSelectMethod() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> child = item("child");
        root.getChildren().add(child);
        panel.getRootItems().add(root);

        panel.setSelectionMode(null);
        // null mode resolves to single-selection: select(leaf) must select (the guard
        // used != SINGLE, which wrongly rejected null).
        panel.select(child);
        assertNotNull(panel.getSelectedPath(), "null mode select(leaf) selects the path");
    }

    @Test
    public void singleSelectionPreservedWhenSetToNull() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> child = item("child");
        root.getChildren().add(child);
        panel.getRootItems().add(root);

        panel.select(child);
        assertNotNull(panel.getSelectedPath(), "precondition: a single selection exists");

        // SINGLE -> null is an effective no-op (null resolves to SINGLE), so the
        // existing selection must be preserved, not cleared.
        panel.setSelectionMode(null);
        assertNotNull(panel.getSelectedPath(), "SINGLE -> null keeps the single selection");
    }

    /**
     * Verifies a lazy load that completes after its branch was detached from the
     * tree is dropped: the detached branch is neither populated nor marked loaded,
     * and no checked paths leak.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void lazyLoadIgnoredWhenBranchDetachedWhileLoading() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> branch = item("branch");
        root.getChildren().add(branch);
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> it == branch ? gate : CompletableFuture.completedFuture(List.of()));
        panel.getRootItems().add(root);

        panel.expand(branch);
        assertTrue(loading(branch));

        // Detach the loading branch WITHOUT going through a reset entry point.
        root.getChildren().remove(branch);
        assertNull(branch.getParent());

        gate.complete(List.of(leaf("x"), leaf("y")));
        waitForFxCondition(() -> !loading(branch));

        assertFalse(loaded(branch), "detached branch must not be marked loaded");
        assertTrue(branch.getChildren().isEmpty(), "detached branch must not be populated");
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies the tri-state machine never leaves a node both checked and
     * indeterminate, across eager rollup with a disabled sibling.
     */
    @Test
    public void checkedAndIndeterminateAreNeverBothTrue() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> a = item("a");
        RXCascaderItem<String> b = item("b");
        RXCascaderItem<String> a1 = leaf("a1");
        RXCascaderItem<String> a2 = leaf("a2");
        a2.setDisable(true);
        a.getChildren().addAll(List.of(a1, a2));
        RXCascaderItem<String> b1 = leaf("b1");
        b.getChildren().add(b1);
        root.getChildren().addAll(List.of(a, b));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(a1, true);
        panel.setCheckedCascade(b1, true);
        panel.setCheckedCascade(a, true);

        for (RXCascaderItem<String> node : List.of(root, a, b, a1, a2, b1)) {
            assertFalse(node.isChecked() && node.isIndeterminate(),
                    node.getValue() + " must not be both checked and indeterminate");
        }
    }

    /**
     * Verifies a lazy load completing on a real background thread is correctly
     * marshaled back to the FX thread (the {@code runOnFxThread} path).
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void lazyLoadCompletesFromBackgroundThread() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        CountDownLatch release = new CountDownLatch(1);
        panel.setChildrenLoader(it -> CompletableFuture.supplyAsync(() -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of(leaf("c1"), leaf("c2"));
        }));
        panel.getRootItems().add(root);

        panel.expand(root);
        assertTrue(loading(root));

        release.countDown();
        waitForFxCondition(() -> loaded(root));

        assertFalse(loading(root));
        assertEquals(2, root.getChildren().size());
    }

    /**
     * Verifies a pending check on a lazy branch that fails to load is not
     * silently replayed by a later plain expand. After the failure rolls the
     * optimistic check back, retrying with a now-succeeding load (same loader,
     * no loader swap so a tree reset cannot mask the bug) must leave the branch
     * unchecked — the pending intent must be cleared on the failure path.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void pendingCheckClearedAfterFailedLoadThenPlainExpand() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        // One loader: first call fails, the retry succeeds. Swapping the loader
        // would trigger resetTree() and clear the pending intent for us, masking
        // a regression where the failure path forgets to clear it.
        CompletableFuture<List<RXCascaderItem<String>>> firstAttempt = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? firstAttempt : CompletableFuture.completedFuture(List.of(leaf("c1")));
        });
        panel.getRootItems().add(branch);

        panel.setCheckedCascade(branch, true);
        assertTrue(branch.isChecked(), "branch is optimistically checked while loading");
        firstAttempt.completeExceptionally(new IllegalStateException("boom"));
        waitForFxCondition(() -> !loading(branch));
        assertFalse(branch.isChecked(), "failure rolls the optimistic check back");

        panel.expand(branch);
        waitForFxCondition(() -> branch.getChildren().size() == 1);

        assertFalse(branch.isChecked(), "a plain expand must not re-check from a stale pending intent");
        assertFalse(branch.getChildren().get(0).isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies two distinct branches loading concurrently and completing out of
     * order both end up correctly populated, including the branch that has since
     * left the active path. Structural attachment (still reachable from a root),
     * not the active path, decides whether a completion is applied.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void twoDistinctBranchesLoadOutOfOrder() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> x = item("x");
        RXCascaderItem<String> y = item("y");
        root.getChildren().setAll(List.of(x, y));
        CompletableFuture<List<RXCascaderItem<String>>> gateX = new CompletableFuture<>();
        CompletableFuture<List<RXCascaderItem<String>>> gateY = new CompletableFuture<>();
        panel.setChildrenLoader(it -> {
            if (it == x) {
                return gateX;
            }
            if (it == y) {
                return gateY;
            }
            return CompletableFuture.completedFuture(List.of());
        });
        panel.getRootItems().add(root);

        panel.expand(x);
        panel.expand(y);
        assertEquals(List.of(root, y), panel.getActivePath(), "y is the active frontier; x has left it");
        assertTrue(loading(x));
        assertTrue(loading(y));

        // Complete out of order: the inactive-path branch's gate (y) first, then x.
        gateY.complete(List.of(leaf("y1")));
        gateX.complete(List.of(leaf("x1")));
        waitForFxCondition(() -> loaded(x) && loaded(y));

        assertEquals(1, y.getChildren().size());
        assertEquals("y1", y.getChildren().get(0).getValue());
        assertEquals(1, x.getChildren().size());
        assertEquals("x1", x.getChildren().get(0).getValue(),
                "the branch that left the active path is still populated correctly");
        assertFalse(loading(x));
        assertFalse(loading(y));
    }

    /**
     * Verifies clearing the selection while a pending-checked lazy branch is
     * still loading leaves nothing checked once the load completes: the cleared
     * pending intent must not be replayed.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void clearSelectionDuringInflightPendingCheckLeavesNothing() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(branch);

        panel.setCheckedCascade(branch, true);
        assertTrue(branch.isChecked());
        assertTrue(loading(branch));

        panel.clearSelection();
        assertFalse(branch.isChecked(), "clearSelection clears the optimistic check");

        gate.complete(List.of(leaf("c1")));
        waitForFxCondition(() -> loaded(branch));

        assertFalse(branch.isChecked(), "no pending replay after clearSelection");
        assertFalse(branch.getChildren().get(0).isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Documents the checked-paths contract: while a checked lazy branch is still
     * loading, its check box is optimistically checked but
     * {@code getCheckedPaths()} reports nothing, because paths are derived only
     * from resolved checked leaves.
     */
    @Test
    public void checkedPathsEmptyWhileBranchStillLoading() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(branch);

        panel.setCheckedCascade(branch, true);

        assertTrue(branch.isChecked(), "the optimistic check is visible immediately");
        assertTrue(loading(branch));
        assertTrue(panel.getCheckedPaths().isEmpty(),
                "checked paths report only resolved checked leaves, not an unresolved branch");
    }

    /**
     * Documents the replay precedence when a loader returns pre-checked children
     * for a pending parent: the user's cascading intent wins for enabled
     * children, while a disabled pre-checked child keeps its loader-supplied
     * check (the cascade skips disabled nodes).
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void loaderSuppliedPreCheckedChildPrecedence() throws InterruptedException {
        // Scenario 1: pending-checked (true) parent; the loader returns an
        // enabled, pre-checked child -> it stays checked (cascade and pre-check
        // agree) and the parent rolls up to fully checked.
        RXCascaderView<String> checkedParent = new RXCascaderView<>();
        checkedParent.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch1 = item("branch1");
        RXCascaderItem<String> preCheckedEnabled = leaf("enabled1");
        preCheckedEnabled.setChecked(true);
        // A second, NOT pre-checked, child: only the pending-check replay can
        // check it, so asserting it ends up checked guards the replay itself
        // rather than merely the loader's pre-check surviving.
        RXCascaderItem<String> plainEnabled = leaf("plain1");
        checkedParent.setChildrenLoader(it ->
                CompletableFuture.completedFuture(List.of(preCheckedEnabled, plainEnabled)));
        checkedParent.getRootItems().add(branch1);

        checkedParent.setCheckedCascade(branch1, true);
        waitForFxCondition(() -> loaded(branch1));

        assertTrue(preCheckedEnabled.isChecked(), "enabled pre-checked child stays checked under a checked parent");
        assertTrue(plainEnabled.isChecked(), "the pending-check replay also checks the non-pre-checked child");
        assertTrue(branch1.isChecked());
        assertFalse(branch1.isIndeterminate());

        // Scenario 2: pending-unchecked (false) parent; the loader returns an
        // enabled pre-checked child AND a disabled pre-checked child. The cascade
        // wins for the enabled child (forced off), but the disabled child keeps
        // its loader check, so the parent rolls up to indeterminate.
        RXCascaderView<String> uncheckedParent = new RXCascaderView<>();
        uncheckedParent.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch2 = item("branch2");
        RXCascaderItem<String> preCheckedEnabled2 = leaf("enabled2");
        preCheckedEnabled2.setChecked(true);
        RXCascaderItem<String> preCheckedDisabled = leaf("disabled2");
        preCheckedDisabled.setChecked(true);
        preCheckedDisabled.setDisable(true);
        uncheckedParent.setChildrenLoader(it ->
                CompletableFuture.completedFuture(List.of(preCheckedEnabled2, preCheckedDisabled)));
        uncheckedParent.getRootItems().add(branch2);

        uncheckedParent.setCheckedCascade(branch2, false);
        waitForFxCondition(() -> loaded(branch2));

        assertFalse(preCheckedEnabled2.isChecked(), "cascade off wins over an enabled pre-check");
        assertTrue(preCheckedDisabled.isChecked(), "disabled pre-checked child keeps its loader check");
        assertFalse(branch2.isChecked());
        assertTrue(branch2.isIndeterminate(), "a disabled checked child makes the parent indeterminate");
    }

    /**
     * Verifies the load-state machine: a fresh lazy branch is EAGER, expanding
     * enters LOADING, a failure becomes FAILED, and a retry goes
     * LOADING-&gt;LOADED.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void loadStateTransitions() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> first = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? first : CompletableFuture.completedFuture(List.of(leaf("c1")));
        });
        panel.getRootItems().add(branch);

        assertEquals(LoadState.EAGER, branch.getLoadState(), "a fresh branch is EAGER until expanded");

        panel.expand(branch);
        assertEquals(LoadState.LOADING, branch.getLoadState(), "expand enters LOADING");

        first.completeExceptionally(new IllegalStateException("boom"));
        waitForFxCondition(() -> branch.getLoadState() == LoadState.FAILED);
        assertEquals(LoadState.FAILED, branch.getLoadState(), "a failed load is FAILED");

        panel.expand(branch);
        assertEquals(LoadState.LOADING, branch.getLoadState(), "retry re-enters LOADING from FAILED");
        waitForFxCondition(() -> branch.getLoadState() == LoadState.LOADED);
        assertEquals(LoadState.LOADED, branch.getLoadState(), "a successful load is LOADED");
        assertEquals(1, branch.getChildren().size());
    }

    /**
     * Verifies a FAILED branch stays a non-leaf branch and can be retried by
     * expanding it again.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void failedBranchIsRetriableAndNonLeaf() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> first = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? first : CompletableFuture.completedFuture(List.of(leaf("c1")));
        });
        panel.getRootItems().add(branch);

        panel.expand(branch);
        first.completeExceptionally(new IllegalStateException("boom"));
        waitForFxCondition(() -> branch.getLoadState() == LoadState.FAILED);

        assertFalse(panel.isLeaf(branch), "a failed branch is not a leaf");

        panel.expand(branch);
        waitForFxCondition(() -> branch.getChildren().size() == 1);
        assertEquals(LoadState.LOADED, branch.getLoadState());
        assertEquals(2, calls[0], "the failed branch was retried");
    }

    /**
     * Verifies a completion that arrives after a {@link RXCascaderView#reload()}
     * is dropped. The branch's token is unchanged across the reload, so a bare
     * token guard would not catch the stale completion — only the live-loads set
     * membership does.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void staleCompletionAfterReloadIsDropped() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(branch);

        panel.expand(branch);
        assertEquals(LoadState.LOADING, branch.getLoadState());

        panel.reload();
        assertEquals(LoadState.NOT_LOADED, branch.getLoadState(), "reload resets the branch");

        gate.complete(List.of(leaf("stale")));
        flushFxEvents();

        assertEquals(LoadState.NOT_LOADED, branch.getLoadState(),
                "a stale completion must not mark the branch loaded");
        assertTrue(branch.getChildren().isEmpty(), "a stale completion must not refill the tree");
    }

    /**
     * Verifies {@code cancelInFlight} is re-entrancy-safe: a {@code loadState}
     * listener that calls {@link RXCascaderView#reload()} while in-flight loads
     * are being cancelled must not throw and must leave a consistent state (red
     * team D2).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void cancelInFlightReentrantReloadIsSafe() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            RXCascaderItem<String> a = item("a");
            RXCascaderItem<String> b = item("b");
            panel.getRootItems().setAll(List.of(a, b));
            panel.setChildrenLoader(it -> new CompletableFuture<>()); // never completes

            panel.expand(a);
            panel.expand(b);
            assertEquals(LoadState.LOADING, b.getLoadState());

            boolean[] reentered = {false};
            a.loadStateProperty().addListener((obs, old, now) -> {
                if (now == LoadState.NOT_LOADED && !reentered[0]) {
                    reentered[0] = true;
                    panel.reload();
                }
            });

            panel.reload();

            assertTrue(reentered[0], "the re-entrant reload ran");
            assertEquals(LoadState.NOT_LOADED, a.getLoadState());
            assertEquals(LoadState.NOT_LOADED, b.getLoadState());
        });
    }

    /**
     * Verifies the view-level seed entry rolls a partial leaf seed up to its
     * ancestors and populates the checked paths in one pass.
     */
    @Test
    public void seedCheckedViaViewRollsUpAndPopulatesPaths() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> a = item("a");
        RXCascaderItem<String> a1 = leaf("a1");
        RXCascaderItem<String> a2 = leaf("a2");
        a.getChildren().setAll(List.of(a1, a2));
        root.getChildren().add(a);
        panel.getRootItems().add(root);

        panel.seedChecked(List.of(a1));

        assertTrue(a1.isChecked());
        assertFalse(a2.isChecked());
        assertFalse(a.isChecked());
        assertTrue(a.isIndeterminate(), "a partial leaf seed makes the parent indeterminate");
        assertTrue(root.isIndeterminate());
        assertEquals(1, panel.getCheckedPaths().size());
        assertSame(a1, panel.getCheckedPaths().get(0).getLeaf());
    }

    /**
     * Verifies seeding a branch cascades down to its leaves so the tree stays
     * consistent (the branch and its leaves agree, and the checked paths carry
     * the leaves), rather than leaving the branch checked over unchecked children.
     */
    @Test
    public void seedCheckedBranchCascadesToLeaves() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> a = item("a");
        RXCascaderItem<String> a1 = leaf("a1");
        RXCascaderItem<String> a2 = leaf("a2");
        a.getChildren().setAll(List.of(a1, a2));
        RXCascaderItem<String> b = leaf("b");
        root.getChildren().setAll(List.of(a, b));
        panel.getRootItems().add(root);

        panel.seedChecked(List.of(a));

        assertTrue(a.isChecked());
        assertTrue(a1.isChecked(), "seeding a branch cascades to its leaves");
        assertTrue(a2.isChecked());
        assertFalse(b.isChecked());
        assertTrue(root.isIndeterminate());
        assertEquals(2, panel.getCheckedPaths().size());
    }

    /**
     * Verifies seeded checks survive a later switch to MULTIPLE (red team B#3):
     * the natural build-tree -&gt; seed -&gt; setSelectionMode(MULTIPLE) order must
     * not wipe the seed when the mode is resolved.
     */
    @Test
    public void seededChecksSurviveModeSwitch() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        // Default SINGLE mode: seed first, then switch to MULTIPLE.
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> a = leaf("a");
        RXCascaderItem<String> b = leaf("b");
        root.getChildren().setAll(List.of(a, b));
        panel.getRootItems().add(root);

        panel.seedChecked(List.of(a, b));
        assertTrue(root.isChecked(), "seeding all leaves rolls the parent up to checked");
        assertEquals(2, panel.getCheckedPaths().size());

        panel.setSelectionMode(SelectionMode.MULTIPLE);

        assertTrue(a.isChecked(), "seeded checks must survive the switch to MULTIPLE");
        assertTrue(b.isChecked());
        assertTrue(root.isChecked());
        assertEquals(2, panel.getCheckedPaths().size());
    }

    /**
     * Verifies the integer rollup keeps disabled children in the denominator: a
     * cascade skips the disabled child (leaving it unchecked) but still counts it
     * in the total, so the parent rolls up to indeterminate, never fully checked.
     */
    @Test
    public void integerRollupKeepsDisabledInDenominator() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> enabled = leaf("enabled");
        RXCascaderItem<String> disabledLeaf = leaf("disabled");
        disabledLeaf.setDisable(true);
        root.getChildren().setAll(List.of(enabled, disabledLeaf));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);

        assertTrue(enabled.isChecked());
        assertFalse(disabledLeaf.isChecked(), "cascade excludes the disabled child");
        assertFalse(root.isChecked(), "the disabled child stays in the denominator (1 of 2)");
        assertTrue(root.isIndeterminate());
    }

    /**
     * Verifies a {@code reload()} re-entered from a children-list listener while a
     * load completes does not leave a stuck LOADED-but-empty branch: completeLoad
     * re-validates after populating children and yields to the reset.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reentrantReloadFromChildrenListenerDoesNotCorruptState() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            RXCascaderItem<String> branch = item("branch");
            boolean[] reloaded = {false};
            branch.getChildren().addListener((ListChangeListener<RXCascaderItem<String>>) change -> {
                if (!reloaded[0] && !branch.getChildren().isEmpty()) {
                    reloaded[0] = true;
                    panel.reload();
                }
            });
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of(leaf("c1"))));
            panel.getRootItems().add(branch);

            panel.expand(branch);

            assertTrue(reloaded[0], "the re-entrant reload ran");
            assertNotEquals(LoadState.LOADED, branch.getLoadState(),
                    "a re-entrant reload during completion must not leave a LOADED-empty branch");
            assertFalse(panel.isLeaf(branch), "the branch stays a retriable branch, not a stuck leaf");
        });
    }

    /**
     * Verifies a {@code reload()} re-entered from a loadState listener at the
     * LOADING transition (during expand) does not resurrect the active path that
     * the reload just cleared.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reentrantReloadDuringExpandRespectsClearedNavigation() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            RXCascaderItem<String> branch = item("branch");
            boolean[] reloaded = {false};
            branch.loadStateProperty().addListener((obs, old, now) -> {
                if (now == LoadState.LOADING && !reloaded[0]) {
                    reloaded[0] = true;
                    panel.reload();
                }
            });
            panel.setChildrenLoader(it -> new CompletableFuture<>()); // never completes
            panel.getRootItems().add(branch);

            panel.expand(branch);

            assertTrue(reloaded[0], "the re-entrant reload ran");
            assertTrue(panel.getActivePath().isEmpty(),
                    "reload's cleared navigation is respected, not resurrected by expand");
            assertEquals(LoadState.NOT_LOADED, branch.getLoadState());
        });
    }

    /**
     * Verifies a {@code reload()} re-entered from a loadState listener at the
     * LOADED transition does not replay the (already captured) pending check onto
     * the reset tree or kick off a second lazy load.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reentrantReloadFromLoadedListenerDropsPendingReplay() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            int[] loaderCalls = {0};
            boolean[] reloaded = {false};
            panel.setChildrenLoader(it -> {
                loaderCalls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("c1")));
            });
            branch.loadStateProperty().addListener((obs, old, now) -> {
                if (now == LoadState.LOADED && !reloaded[0]) {
                    reloaded[0] = true;
                    panel.reload();
                }
            });
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true); // pending check + load, completes inline

            assertTrue(reloaded[0], "the LOADED-listener reload ran");
            assertEquals(LoadState.NOT_LOADED, branch.getLoadState(), "reload reset the branch");
            assertFalse(branch.isChecked(), "the pending check was not replayed onto the reset tree");
            assertTrue(panel.getCheckedPaths().isEmpty());
            assertEquals(1, loaderCalls[0], "no second lazy load was kicked off by a stale replay");
        });
    }

    /**
     * Verifies a {@code clearSelection()} re-entered from a LOADED listener wins:
     * the pending-check replay runs before the LOADED write, so the listener's
     * clear is the final word and is not overwritten by the replay.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reentrantClearSelectionFromLoadedListenerWins() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            boolean[] cleared = {false};
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of(leaf("c1"))));
            branch.loadStateProperty().addListener((obs, old, now) -> {
                if (now == LoadState.LOADED && !cleared[0]) {
                    cleared[0] = true;
                    panel.clearSelection();
                }
            });
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true); // pending check + load, completes inline

            assertTrue(cleared[0], "the LOADED-listener clearSelection ran");
            assertFalse(branch.isChecked(), "clearSelection in the LOADED listener is not overwritten by the replay");
            assertFalse(branch.getChildren().get(0).isChecked(), "the child is not re-checked by a stale replay");
            assertTrue(panel.getCheckedPaths().isEmpty());
        });
    }

    /**
     * Verifies the original expand bails on its stale token when a LOADING listener
     * reloads and then re-loads the same item with a new token, instead of
     * resurrecting navigation for the superseded load.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reentrantReloadThenLoadDuringExpandUsesNewToken() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            RXCascaderItem<String> branch = item("branch");
            boolean[] done = {false};
            panel.setChildrenLoader(it -> new CompletableFuture<>()); // never completes
            branch.loadStateProperty().addListener((obs, old, now) -> {
                if (now == LoadState.LOADING && !done[0]) {
                    done[0] = true;
                    panel.reload();
                    panel.loadChildren(branch); // re-loads the same item with a new token
                }
            });
            panel.getRootItems().add(branch);

            panel.expand(branch);

            assertTrue(done[0], "the re-entrant reload + loadChildren ran");
            assertTrue(panel.getActivePath().isEmpty(),
                    "the original expand bails on its stale token and does not resurrect navigation");
        });
    }

    /**
     * Verifies a checked lazy branch whose loader returns no children settles as
     * a checked leaf: the optimistic pending check survives the replay (it is not
     * re-recorded as a still-pending intent), and the now-empty leaf is reported
     * in the checked paths. Regression guard for the replay-before-LOADED
     * ordering, where the loadState-keyed {@code isLeaf}/{@code
     * isUnresolvedLazyBranch} tests misread an empty-but-final branch (children
     * set, state still LOADING) as unresolved.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void checkedLazyBranchLoadingToEmptyBecomesCheckedLeaf() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of()));
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true); // pending check + load, completes inline

            assertTrue(loaded(branch), "the empty load completed");
            assertTrue(panel.isLeaf(branch), "an empty-loaded branch is a leaf");
            assertTrue(branch.isChecked(), "the optimistic check survives as a checked leaf");
            assertFalse(branch.isIndeterminate());
            assertNull(branch.getPendingCheck(), "the pending intent is consumed, not re-recorded");
            assertEquals(1, panel.getCheckedPaths().size(), "the checked empty leaf is reported in the paths");
            assertSame(branch, panel.getCheckedPaths().get(0).getLeaf());
        });
    }

    /**
     * Verifies the pending-check replay survives a {@code reload()} re-entered
     * from a child's checked listener: no ConcurrentModificationException from
     * the reset clearing the children mid-cascade (captured via the FX thread's
     * uncaught-exception handler, where an async completion would surface it),
     * and the reset owns the final state.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void replayReentrantReloadFromChildCheckedListenerDoesNotThrow() throws InterruptedException {
        AtomicReference<Throwable> fxError = new AtomicReference<>();
        runOnFx(() -> Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> fxError.set(error)));
        try {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            RXCascaderItem<String> first = leaf("c1");
            RXCascaderItem<String> second = leaf("c2");
            boolean[] reloaded = {false};
            first.checkedProperty().addListener((obs, old, now) -> {
                if (now && !reloaded[0]) {
                    reloaded[0] = true;
                    panel.reload();
                }
            });
            CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
            panel.setChildrenLoader(it -> gate);
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true);
            gate.complete(List.of(first, second));
            waitForFxCondition(() -> reloaded[0]);

            assertNull(fxError.get(), "the replay must survive the re-entrant reset without throwing");
            assertEquals(LoadState.NOT_LOADED, branch.getLoadState(), "the reset owns the final state");
            assertTrue(branch.getChildren().isEmpty(), "the reset tree is not repopulated");
            assertFalse(second.isChecked(), "the aborted replay does not re-check the remaining children");
            assertTrue(panel.getCheckedPaths().isEmpty());
        } finally {
            runOnFx(() -> Thread.currentThread().setUncaughtExceptionHandler(null));
        }
    }

    /**
     * Verifies a {@code reload()} re-entered from the branch's own checked
     * listener during the pending-check replay vetoes the LOADED commit: the
     * branch ends NOT_LOADED and stays a re-expandable branch instead of being
     * pinned as a LOADED empty leaf.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void replayReentrantReloadFromBranchCheckedListenerVetoesLoaded() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            RXCascaderItem<String> enabled = leaf("enabled");
            RXCascaderItem<String> disabled = leaf("disabled");
            disabled.setDisable(true);
            boolean[] reloaded = {false};
            // The replay's rollup drops the optimistic check (only 1 of 2
            // children ends checked); reload on that transition, mid-completion.
            branch.checkedProperty().addListener((obs, old, now) -> {
                if (!now && !reloaded[0]) {
                    reloaded[0] = true;
                    panel.reload();
                }
            });
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of(enabled, disabled)));
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true);

            assertTrue(reloaded[0], "the re-entrant reload ran");
            assertEquals(LoadState.NOT_LOADED, branch.getLoadState(),
                    "the reset is not overwritten with LOADED");
            assertFalse(panel.isLeaf(branch), "the branch is not pinned as a LOADED empty leaf");
            assertTrue(branch.getChildren().isEmpty());
        });
    }

    /**
     * Verifies a {@code clearSelection()} re-entered from a child's checked
     * listener during the pending-check replay has the final say: the remaining
     * replay is aborted instead of re-checking the siblings, while the load
     * itself still completes.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void replayReentrantClearSelectionAbortsRemainingReplay() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            RXCascaderItem<String> first = leaf("c1");
            RXCascaderItem<String> second = leaf("c2");
            boolean[] cleared = {false};
            first.checkedProperty().addListener((obs, old, now) -> {
                if (now && !cleared[0]) {
                    cleared[0] = true;
                    panel.clearSelection();
                }
            });
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of(first, second)));
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true);

            assertTrue(cleared[0], "the re-entrant clearSelection ran");
            assertTrue(loaded(branch), "clearSelection drops the intent but the load still completes");
            assertFalse(first.isChecked());
            assertFalse(second.isChecked(), "the aborted replay does not re-check the remaining children");
            assertFalse(branch.isChecked());
            assertTrue(panel.getCheckedPaths().isEmpty());
        });
    }

    /**
     * Verifies a root-items change while a pending-checked lazy branch is in
     * flight rolls the optimistic check back with the cancelled load, so a later
     * expand cannot settle into a checked parent over all-unchecked children.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void rootChangeDuringPendingCheckRollsBackOptimisticCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> a = item("a");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? gate
                    : CompletableFuture.completedFuture(List.of(leaf("a1"), leaf("a2")));
        });
        panel.getRootItems().add(a);

        panel.setCheckedCascade(a, true);
        assertTrue(a.isChecked(), "the optimistic check is visible while loading");
        assertTrue(loading(a));

        panel.getRootItems().add(item("b"));
        assertFalse(a.isChecked(), "cancelling the pending check rolls the optimistic check back");
        assertFalse(a.isIndeterminate());

        gate.complete(List.of(leaf("stale")));
        flushFxEvents();
        assertTrue(a.getChildren().isEmpty(), "the cancelled completion is dropped");

        panel.expand(a);
        waitForFxCondition(() -> loaded(a));
        assertFalse(a.isChecked(), "no checked-parent-over-unchecked-children residue");
        assertFalse(a.getChildren().get(0).isChecked());
        assertFalse(a.getChildren().get(1).isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies replacing the whole root list while a pending-checked branch is
     * in flight cancels the load: the detached branch is not populated or marked
     * loaded, its optimistic check is rolled back, and no paths leak.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void rootReplacementDropsInFlightPendingCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> oldRoot = item("old");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(oldRoot);

        panel.setCheckedCascade(oldRoot, true);
        assertTrue(loading(oldRoot));

        panel.getRootItems().setAll(List.of(item("new")));
        assertEquals(LoadState.NOT_LOADED, oldRoot.getLoadState());
        assertFalse(oldRoot.isChecked(), "the optimistic check is rolled back with the cancelled load");

        gate.complete(List.of(leaf("stale")));
        flushFxEvents();

        assertTrue(oldRoot.getChildren().isEmpty(), "the stale completion must not populate the old root");
        assertFalse(loaded(oldRoot));
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies a branch disabled while its pending-checked load is in flight has
     * the optimistic check rolled back on completion, mirroring the failure
     * path, instead of sticking as a checked parent over unchecked children.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void disableDuringPendingCheckLoadRollsBackCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> b = item("b");
        RXCascaderItem<String> sibling = leaf("sibling");
        root.getChildren().setAll(List.of(b, sibling));
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(root);

        panel.setCheckedCascade(b, true);
        assertTrue(b.isChecked());
        b.setDisable(true);

        gate.complete(List.of(leaf("b1"), leaf("b2")));
        waitForFxCondition(() -> loaded(b));

        assertFalse(b.isChecked(), "the unfulfillable intent is rolled back, not left half-honored");
        assertFalse(b.isIndeterminate());
        assertFalse(b.getChildren().get(0).isChecked());
        assertFalse(b.getChildren().get(1).isChecked());
        assertFalse(root.isIndeterminate(), "ancestors are repaired by the rollback");
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies the ancestor variant of the disable-during-flight rollback: the
     * loading branch itself stays enabled but an ancestor is disabled, making it
     * effectively disabled at completion time.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void ancestorDisableDuringPendingCheckLoadRollsBackCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> b = item("b");
        root.getChildren().setAll(List.of(b));
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(root);

        panel.setCheckedCascade(b, true);
        assertTrue(b.isChecked());
        root.setDisable(true);

        gate.complete(List.of(leaf("b1")));
        waitForFxCondition(() -> loaded(b));

        assertFalse(b.isChecked(), "an ancestor disable also voids the pending intent");
        assertFalse(b.isIndeterminate());
        assertFalse(b.getChildren().get(0).isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies a retry issued from a loadState listener at the FAILED transition
     * is not swallowed: the retry's pending check and optimistic state survive
     * the outer failure frame and are replayed when the retry completes.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void failedListenerRetryPendingCheckSurvives() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> firstAttempt = new CompletableFuture<>();
        CompletableFuture<List<RXCascaderItem<String>>> retryAttempt = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? firstAttempt : retryAttempt;
        });
        boolean[] retried = {false};
        branch.loadStateProperty().addListener((obs, old, now) -> {
            if (now == LoadState.FAILED && !retried[0]) {
                retried[0] = true;
                panel.setCheckedCascade(branch, true);
            }
        });
        panel.getRootItems().add(branch);

        panel.expand(branch);
        firstAttempt.completeExceptionally(new IllegalStateException("boom"));
        waitForFxCondition(() -> retried[0] && loading(branch));

        assertTrue(branch.isChecked(), "the retry's optimistic check survives the outer failure frame");

        retryAttempt.complete(List.of(leaf("c1")));
        waitForFxCondition(() -> loaded(branch));

        assertTrue(branch.isChecked(), "the retry's pending check was replayed");
        assertTrue(branch.getChildren().get(0).isChecked());
        assertEquals(1, panel.getCheckedPaths().size());
    }

    /**
     * Verifies a plain {@code expand} retry from the FAILED listener does not
     * resurrect the failed attempt's pending check. A plain expand carries no
     * check intent, so — like the non-reentrant case — it must leave the branch
     * unchecked, even though it re-registers the item in the live-load set.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void failedListenerPlainExpandDoesNotReplayStalePendingCheck() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        CompletableFuture<List<RXCascaderItem<String>>> firstAttempt = new CompletableFuture<>();
        CompletableFuture<List<RXCascaderItem<String>>> retryAttempt = new CompletableFuture<>();
        int[] calls = {0};
        panel.setChildrenLoader(it -> {
            calls[0]++;
            return calls[0] == 1 ? firstAttempt : retryAttempt;
        });
        boolean[] retried = {false};
        branch.loadStateProperty().addListener((obs, old, now) -> {
            if (now == LoadState.FAILED && !retried[0]) {
                retried[0] = true;
                panel.expand(branch);
            }
        });
        panel.getRootItems().add(branch);

        panel.setCheckedCascade(branch, true);
        assertTrue(branch.isChecked(), "branch is optimistically checked while loading");
        firstAttempt.completeExceptionally(new IllegalStateException("boom"));
        waitForFxCondition(() -> retried[0] && loading(branch));

        retryAttempt.complete(List.of(leaf("c1")));
        waitForFxCondition(() -> loaded(branch));

        assertFalse(branch.isChecked(),
                "a plain expand retry must not replay the failed attempt's pending check");
        assertFalse(branch.getChildren().get(0).isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies {@code emptyText} defaults to "No data" and is a plain pass-through
     * property that also accepts {@code null}.
     */
    @Test
    public void emptyTextDefaultsToNoData() {
        RXCascaderView<String> view = new RXCascaderView<>();
        assertEquals("No data", view.getEmptyText(), "default empty-column placeholder text");
        view.setEmptyText("暂无");
        assertEquals("暂无", view.getEmptyText());
        view.setEmptyText(null);
        assertNull(view.getEmptyText(), "null is accepted (renders a blank placeholder)");
    }

    /**
     * Verifies a lazy loader returning a {@code children} list containing a
     * {@code null} is treated as a retriable load failure — routed through the
     * FAILED path with the error callback — rather than letting the null-rejecting
     * list throw uncaught mid-populate and strand the branch in LOADING.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void loaderReturningNullChildIsTreatedAsRetriableFailure() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            List<RXCascaderItem<String>> withNull = new ArrayList<>();
            withNull.add(leaf("valid"));
            withNull.add(null);
            int[] calls = {0};
            view.setChildrenLoader(it -> {
                calls[0]++;
                return calls[0] == 1
                        ? CompletableFuture.completedFuture(withNull)
                        : CompletableFuture.completedFuture(List.of(leaf("ok")));
            });
            Throwable[] captured = {null};
            view.setOnChildrenLoadError((it, cause) -> captured[0] = cause);
            RXCascaderItem<String> branch = item("branch");
            view.getRootItems().add(branch);

            view.expand(branch); // completes inline: a null child must FAIL, not throw

            assertEquals(LoadState.FAILED, branch.getLoadState(),
                    "a loader result containing null is treated as a load failure");
            assertTrue(captured[0] instanceof NullPointerException,
                    "the error callback fires with an NPE cause");
            assertTrue(branch.getChildren().isEmpty(), "no partial children are populated");

            view.expand(branch); // retriable: the second attempt succeeds
            assertEquals(LoadState.LOADED, branch.getLoadState(),
                    "the branch is retriable after the null-child failure");
            assertEquals(1, branch.getChildren().size());
        });
    }

    /**
     * Verifies a {@code null} root item is rejected at the call site with a clear
     * message, not swallowed into a later obscure NPE.
     */
    @Test
    public void nullRootItemIsRejectedAtInsertion() {
        RXCascaderView<String> view = new RXCascaderView<>();

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> view.getRootItems().add(null));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("root item"),
                "the rejection names the offending element");
        assertTrue(view.getRootItems().isEmpty(), "a rejected add leaves the list empty");
    }

    /**
     * Verifies a bulk {@code setAll} containing a {@code null} is rejected
     * atomically — the list keeps its prior contents rather than partially applying.
     */
    @Test
    public void nullInBulkRootItemsIsRejectedAtomically() {
        RXCascaderView<String> view = new RXCascaderView<>();
        RXCascaderItem<String> keep = item("keep");
        view.getRootItems().add(keep);

        assertThrows(NullPointerException.class,
                () -> view.getRootItems().setAll(item("a"), null, item("b")));
        assertEquals(List.of(keep), view.getRootItems(),
                "a bulk set with a null must not partially mutate the list");
    }

    /**
     * Verifies a {@code null} child item is likewise rejected at the call site.
     */
    @Test
    public void nullChildItemIsRejectedAtInsertion() {
        RXCascaderItem<String> parent = item("parent");

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> parent.getChildren().add(null));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("child item"),
                "the rejection names the offending element");
        assertTrue(parent.getChildren().isEmpty(), "a rejected add leaves the list empty");
    }

    /**
     * Verifies revealing the selection sets the active path to the selected leaf's
     * ancestor branches (so its column opens), while {@code select} on its own does
     * not navigate.
     */
    @Test
    public void revealSelectedPathSetsActivePathToAncestors() {
        RXCascaderView<String> view = new RXCascaderView<>();
        RXCascaderItem<String> europe = item("europe");
        RXCascaderItem<String> germany = item("germany");
        RXCascaderItem<String> berlin = item("berlin");
        germany.getChildren().add(berlin);
        europe.getChildren().add(germany);
        view.getRootItems().setAll(List.of(europe));

        view.select(berlin);
        assertTrue(view.getActivePath().isEmpty(), "select must not navigate on its own");

        view.revealSelectedPath();

        assertEquals(List.of(europe, germany), view.getActivePath(),
                "reveal expands to the selection's ancestor branches");
    }

    /**
     * Verifies reveal is a no-op when there is no current selection.
     */
    @Test
    public void revealSelectedPathIsNoOpWithoutSelection() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getRootItems().setAll(List.of(item("a")));

        view.revealSelectedPath();

        assertTrue(view.getActivePath().isEmpty(), "no selection means nothing to reveal");
    }

    /**
     * Verifies a selection whose ancestor chain is not in the current tree is not
     * revealed — the guard against a stale path.
     */
    @Test
    public void revealSelectedPathIgnoresPathOutsideTree() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getRootItems().setAll(List.of(item("a")));
        RXCascaderItem<String> strayParent = item("strayParent");
        RXCascaderItem<String> strayLeaf = item("strayLeaf");
        strayParent.getChildren().add(strayLeaf);
        view.select(strayLeaf); // select accepts a leaf regardless of tree membership

        view.revealSelectedPath();

        assertTrue(view.getActivePath().isEmpty(),
                "a selection outside the current tree must not be revealed");
    }

    /**
     * Verifies runtime operations that drive navigation or checked state ignore
     * items outside the current root tree.
     */
    @Test
    public void runtimeOperationsIgnoreItemsOutsideTree() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getRootItems().setAll(List.of(item("root")));
        RXCascaderItem<String> strayLeaf = item("strayLeaf");
        RXCascaderItem<String> strayBranch = item("strayBranch");
        strayBranch.getChildren().setAll(List.of(item("child")));

        view.activate(strayLeaf);
        view.expand(strayBranch);

        assertNull(view.getSelectedPath(), "activating a detached leaf must not select it");
        assertTrue(view.getActivePath().isEmpty(), "expanding a detached branch must not navigate");

        view.setSelectionMode(SelectionMode.MULTIPLE);
        view.setCheckedCascade(strayLeaf, true);
        view.toggleCheck(strayLeaf);

        assertFalse(strayLeaf.isChecked(), "runtime checking must ignore detached items");
        assertTrue(view.getCheckedPaths().isEmpty());
    }

    /**
     * Verifies loading a detached branch does not invoke the view-owned loader.
     */
    @Test
    public void loadChildrenIgnoresItemsOutsideTree() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getRootItems().setAll(List.of(item("root")));
        int[] calls = {0};
        view.setChildrenLoader(item -> {
            calls[0]++;
            return CompletableFuture.completedFuture(List.of(leaf("child")));
        });

        RXCascaderItem<String> strayBranch = item("strayBranch");
        view.loadChildren(strayBranch);

        assertEquals(0, calls[0], "detached items must not trigger the loader");
        assertEquals(LoadState.EAGER, strayBranch.getLoadState());
    }

    /**
     * Verifies a detached lazy seed can pre-mark the item, but does not load until
     * the item is reachable and operated on.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void seedCheckedDetachedLazyBranchDefersLoadingUntilReachable() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("child")));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));

            assertTrue(strayBranch.isChecked());
            assertEquals(0, calls[0], "detached seed must not trigger loading");
            assertTrue(view.getCheckedPaths().isEmpty());

            view.getRootItems().setAll(strayBranch);
            view.expand(strayBranch);

            assertEquals(1, calls[0]);
            assertTrue(loaded(strayBranch));
            assertEquals(1, view.getCheckedPaths().size());
            assertSame(strayBranch.getChildren().get(0), view.getCheckedPaths().get(0).getLeaf());
        });
    }

    /**
     * Verifies clearing selection drops pending check intent that came from a
     * detached seed after the item becomes reachable.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void clearSelectionDropsPendingCheckFromReachableDetachedSeed() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("child")));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));
            view.getRootItems().setAll(strayBranch);
            view.clearSelection();
            view.expand(strayBranch);

            assertEquals(1, calls[0]);
            assertTrue(loaded(strayBranch));
            assertFalse(strayBranch.isChecked());
            assertTrue(view.getCheckedPaths().isEmpty());
            assertFalse(strayBranch.getChildren().get(0).isChecked());
        });
    }

    /**
     * Verifies clearing selection drops a detached seed before that item is
     * attached to the current roots.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void clearSelectionDropsDetachedSeedBeforeItBecomesReachable() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("child")));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));
            view.clearSelection();
            view.getRootItems().setAll(strayBranch);
            view.expand(strayBranch);

            assertEquals(1, calls[0]);
            assertTrue(loaded(strayBranch));
            assertFalse(strayBranch.isChecked());
            assertTrue(view.getCheckedPaths().isEmpty());
            assertFalse(strayBranch.getChildren().get(0).isChecked());
        });
    }

    /**
     * Verifies clearing selection still drops a detached seed after the item was
     * temporarily reachable and then removed from the roots again.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void clearSelectionDropsDetachedSeedAfterReachableItemIsDetachedAgain() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("child")));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));
            view.getRootItems().setAll(strayBranch);
            view.getRootItems().clear();
            view.clearSelection();
            view.getRootItems().setAll(strayBranch);
            view.expand(strayBranch);

            assertEquals(1, calls[0]);
            assertTrue(loaded(strayBranch));
            assertFalse(strayBranch.isChecked());
            assertTrue(view.getCheckedPaths().isEmpty());
            assertFalse(strayBranch.getChildren().get(0).isChecked());
        });
    }

    /**
     * Verifies an explicit cascade uncheck releases a detached seed reference once
     * that subtree no longer carries pending or checked state.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void setCheckedCascadeFalsePrunesClearedDetachedSeedReference() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.completedFuture(List.of(leaf("child")));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));
            view.getRootItems().setAll(strayBranch);
            view.expand(strayBranch);
            view.setCheckedCascade(strayBranch, false);
            view.getRootItems().clear();

            assertEquals(1, calls[0]);
            assertTrue(loaded(strayBranch));
            assertFalse(strayBranch.isChecked());
            assertTrue(view.getCheckedPaths().isEmpty());
            assertEquals(0, detachedSeedItemCount(view));
        });
    }

    /**
     * Verifies a failed lazy load releases a detached seed reference once the
     * pending optimistic check has been rolled back.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void failedLoadPrunesClearedDetachedSeedReference() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            int[] calls = {0};
            view.setChildrenLoader(item -> {
                calls[0]++;
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            });
            RXCascaderItem<String> strayBranch = item("strayBranch");

            view.seedChecked(List.of(strayBranch));
            view.getRootItems().setAll(strayBranch);
            view.expand(strayBranch);
            view.getRootItems().clear();

            assertEquals(1, calls[0]);
            assertEquals(LoadState.FAILED, strayBranch.getLoadState());
            assertFalse(strayBranch.isChecked());
            assertFalse(strayBranch.isIndeterminate());
            assertTrue(view.getCheckedPaths().isEmpty());
            assertEquals(0, detachedSeedItemCount(view));
        });
    }

    /**
     * Verifies a forced branch ({@code leafHint=false}) that loads to zero
     * children under a pending check does not become a checked non-leaf: the
     * rollup rule that an empty branch cannot be checked applies to the replay
     * exactly as it does to the eager cascade.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void forcedBranchLoadedEmptyDoesNotBecomeCheckedNonLeaf() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> panel = new RXCascaderView<>();
            panel.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> branch = item("branch");
            branch.setLeafHint(false);
            panel.setChildrenLoader(it -> CompletableFuture.completedFuture(List.of()));
            panel.getRootItems().add(branch);

            panel.setCheckedCascade(branch, true); // pending check + load, completes inline

            assertTrue(loaded(branch));
            assertFalse(panel.isLeaf(branch), "leafHint=false keeps the empty branch a non-leaf");
            assertFalse(branch.isChecked(), "an empty forced branch cannot be checked");
            assertFalse(branch.isIndeterminate());
            assertNull(branch.getPendingCheck());
            assertTrue(panel.getCheckedPaths().isEmpty());
        });
    }

    /**
     * Locks the null-stage contract: a loader returning {@code null} is treated
     * as an empty successful result — the branch becomes a loaded leaf and the
     * error callback is not invoked.
     */
    @Test
    public void nullLoaderStageIsEmptySuccessfulResult() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        RXCascaderItem<String> branch = item("branch");
        int[] errors = {0};
        panel.setOnChildrenLoadError((it, cause) -> errors[0]++);
        panel.setChildrenLoader(it -> null);
        panel.getRootItems().add(branch);

        panel.expand(branch);

        assertTrue(loaded(branch));
        assertTrue(panel.isLeaf(branch), "a null stage yields a loaded leaf");
        assertTrue(branch.getChildren().isEmpty());
        assertEquals(0, errors[0], "a null stage is not an error");
    }

    /**
     * Locks the symmetric null-children contract: a stage completing with
     * {@code null} children is an empty successful result, and a pending check
     * settles the branch as a checked leaf.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void nullCompletedChildrenIsEmptySuccessfulResult() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        int[] errors = {0};
        panel.setOnChildrenLoadError((it, cause) -> errors[0]++);
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        panel.setChildrenLoader(it -> gate);
        panel.getRootItems().add(branch);

        panel.setCheckedCascade(branch, true);
        gate.complete(null);
        waitForFxCondition(() -> loaded(branch));

        assertTrue(panel.isLeaf(branch));
        assertTrue(branch.isChecked(), "the pending check settles the empty-loaded branch as a checked leaf");
        assertEquals(1, panel.getCheckedPaths().size());
        assertEquals(0, errors[0]);
    }

    /**
     * Verifies an uncheck that resolves while a branch is loading does not
     * cascade loads into the freshly loaded children: replaying {@code false}
     * onto never-checked unresolved branches is a no-op, not a recursive
     * subtree prefetch.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void uncheckReplayDoesNotRecursivelyLoadSubtree() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        // No leafHint: once loaded these children read as unresolved branches,
        // the shape that used to amplify the replay into recursive loads.
        RXCascaderItem<String> childA = item("childA");
        RXCascaderItem<String> childB = item("childB");
        CompletableFuture<List<RXCascaderItem<String>>> gate = new CompletableFuture<>();
        ConcurrentHashMap<RXCascaderItem<String>, Integer> loaderCalls = new ConcurrentHashMap<>();
        panel.setChildrenLoader(it -> {
            loaderCalls.merge(it, 1, Integer::sum);
            return it == root ? gate : new CompletableFuture<>();
        });
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        panel.setCheckedCascade(root, false);
        gate.complete(List.of(childA, childB));
        waitForFxCondition(() -> loaded(root));

        assertEquals(1, loaderCalls.getOrDefault(root, 0));
        assertEquals(0, loaderCalls.getOrDefault(childA, 0),
                "replaying an uncheck must not load a never-checked fresh child");
        assertEquals(0, loaderCalls.getOrDefault(childB, 0));
        assertFalse(root.isChecked());
        assertFalse(childA.isChecked());
        assertTrue(panel.getCheckedPaths().isEmpty());
    }

    /**
     * Pins the intended deep resolve on check: checking an unloaded branch
     * loads it and, through the replay, also loads its not-yet-resolved branch
     * children until the checked paths resolve to leaves.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void checkReplayResolvesBranchChildrenRecursively() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = item("root");
        RXCascaderItem<String> branchChild = item("branch");
        RXCascaderItem<String> leafGrandchild = leaf("grandLeaf");
        panel.setChildrenLoader(it -> it == root
                ? CompletableFuture.completedFuture(List.of(branchChild))
                : CompletableFuture.completedFuture(List.of(leafGrandchild)));
        panel.getRootItems().add(root);

        panel.setCheckedCascade(root, true);
        waitForFxCondition(() -> loaded(branchChild) && panel.getCheckedPaths().size() == 1);

        assertTrue(leafGrandchild.isChecked());
        assertTrue(branchChild.isChecked());
        assertTrue(root.isChecked());
        assertFalse(root.isIndeterminate());
        assertSame(leafGrandchild, panel.getCheckedPaths().get(0).getLeaf());
    }

    /**
     * Verifies a stage failure delivered as a {@code CompletionException} (the
     * standard wrapper for a throwing async supplier) surfaces the cause, not
     * the wrapper, to the error callback.
     *
     * @throws InterruptedException if the FX event flush is interrupted
     */
    @Test
    public void loadErrorCallbackUnwrapsCompletionException() throws InterruptedException {
        RXCascaderView<String> panel = new RXCascaderView<>();
        AtomicReference<Throwable> erroredCause = new AtomicReference<>();
        panel.setOnChildrenLoadError((failedItem, cause) -> erroredCause.set(cause));
        panel.setChildrenLoader(it -> CompletableFuture.failedFuture(
                new CompletionException(new IllegalStateException("boom"))));
        RXCascaderItem<String> root = item("root");
        panel.getRootItems().add(root);

        panel.expand(root);
        waitForFxCondition(() -> erroredCause.get() != null);

        assertTrue(erroredCause.get() instanceof IllegalStateException,
                "callback should receive the cause, got " + erroredCause.get());
        assertEquals("boom", erroredCause.get().getMessage());
    }

    /**
     * Verifies derived checked-path refreshes fire no full-replace event when
     * the resolved path set is unchanged.
     */
    @Test
    public void unchangedCheckedPathsFireNoListChange() {
        RXCascaderView<String> panel = new RXCascaderView<>();
        panel.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> branch = item("branch");
        RXCascaderItem<String> leafA = item("leafA");
        RXCascaderItem<String> leafB = item("leafB");
        branch.getChildren().setAll(List.of(leafA, leafB));
        panel.getRootItems().add(branch);
        panel.setCheckedCascade(leafA, true);

        int[] changes = {0};
        panel.getCheckedPaths().addListener(
                (ListChangeListener<RXCascaderPath<String>>) change -> changes[0]++);

        panel.setCheckedCascade(leafA, true);
        assertEquals(0, changes[0], "re-deriving an identical path set must not fire");

        panel.setCheckedCascade(leafB, true);
        assertTrue(changes[0] > 0, "a real path-set change still fires");
    }

    private static RXCascaderItem<String> item(String text) {
        return new RXCascaderItem<>(text);
    }

    private static RXCascaderItem<String> leaf(String text) {
        RXCascaderItem<String> leaf = new RXCascaderItem<>(text);
        leaf.setLeafHint(true);
        return leaf;
    }

    private static boolean loaded(RXCascaderItem<?> item) {
        return item.getLoadState() == LoadState.LOADED;
    }

    private static boolean loading(RXCascaderItem<?> item) {
        return item.getLoadState() == LoadState.LOADING;
    }

    private static int detachedSeedItemCount(RXCascaderView<?> view) {
        try {
            Field field = RXCascaderView.class.getDeclaredField("detachedSeedItems");
            field.setAccessible(true);
            return ((Set<?>) field.get(view)).size();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
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
        while (System.nanoTime() < deadline) {
            // Flush first so every check sits behind the flush latch's
            // happens-before barrier and observes a fully-applied completeLoad
            // (loaded set AND loading cleared), not a half-visible intermediate
            // state across the JUnit/FX thread boundary.
            flushFxEvents();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("FX condition was not reached");
        }
    }
}
