package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.util.Callback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXCascader}.
 */
public class RXCascaderTest {

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
     * Verifies the public showing requests update the read-only showing state.
     */
    @Test
    public void showAndHideUpdateShowingState() {
        RXCascader<String> cascader = new RXCascader<>();

        assertFalse(cascader.isShowing());

        cascader.show();
        assertTrue(cascader.isShowing());

        cascader.hide();
        assertFalse(cascader.isShowing());

        cascader.setDisable(true);
        cascader.show();
        assertFalse(cascader.isShowing());
    }

    /**
     * Verifies the wrapper exposes the cell factory it was given. The binding into
     * the private popup view and the actual popup rendering are covered against the
     * shared view in
     * {@code RXCascaderViewSkinTest.cellFactoryCreateContentOverrideRendersCustomNode}.
     */
    @Test
    public void cellFactoryIsExposedByWrapper() {
        RXCascader<String> cascader = new RXCascader<>();
        Callback<RXCascaderView<String>, ListCell<RXCascaderItem<String>>> factory =
                view -> new RXCascaderCell<>(view);

        cascader.setCellFactory(factory);

        assertSame(factory, cascader.getCellFactory(), "wrapper should report the factory");
    }

    /**
     * Verifies the wrapper's own properties report this control as their bean,
     * matching the JavaFX convention (the embedded view is private).
     */
    @Test
    public void propertyBeansAreTheControl() {
        RXCascader<String> cascader = new RXCascader<>();
        assertSame(cascader, cascader.selectionModeProperty().getBean());
        assertSame(cascader, cascader.itemTextFactoryProperty().getBean());
        assertSame(cascader, cascader.visibleRowCountProperty().getBean());
        assertSame(cascader, cascader.cellFactoryProperty().getBean());
        assertSame(cascader, cascader.selectedPathProperty().getBean());
    }

    /**
     * Verifies the programmatic selection entries drive the embedded view:
     * {@code select} sets the single path, {@code setCheckedCascade} checks it.
     */
    @Test
    public void programmaticSelectionDrivesTheView() {
        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        root.getChildren().add(child);
        cascader.getRootItems().add(root);

        cascader.select(child);
        assertEquals(List.of(root, child), cascader.getSelectedPath().getItems());

        cascader.setSelectionMode(SelectionMode.MULTIPLE);
        cascader.setCheckedCascade(child, true);
        assertEquals(1, cascader.getCheckedPaths().size());
        assertEquals(List.of(root, child), cascader.getCheckedPaths().get(0).getItems());
    }

    /**
     * Verifies the wrapper's {@code seedChecked} forwards to the embedded view,
     * seeding initial checked paths (the view is private, so the wrapper must
     * expose this entry point itself).
     */
    @Test
    public void seedCheckedForwardsToView() {
        RXCascader<String> cascader = new RXCascader<>();
        cascader.setSelectionMode(SelectionMode.MULTIPLE);
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> a = new RXCascaderItem<>("a");
        RXCascaderItem<String> b = new RXCascaderItem<>("b");
        root.getChildren().addAll(List.of(a, b));
        cascader.getRootItems().add(root);

        cascader.seedChecked(List.of(a, b));

        assertEquals(2, cascader.getCheckedPaths().size());
        assertEquals(List.of(root, a), cascader.getCheckedPaths().get(0).getItems());
        assertTrue(root.isChecked());
    }

    /**
     * Verifies each programmatic entry is ignored outside its own mode:
     * {@code setCheckedCascade} no-ops in single mode, {@code select} no-ops in
     * multiple mode, so public state never holds a result foreign to the mode.
     */
    @Test
    public void programmaticEntriesRespectSelectionMode() {
        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        root.getChildren().add(child);
        cascader.getRootItems().add(root);

        // SINGLE mode: setCheckedCascade is ignored, so checked paths stay empty.
        cascader.setCheckedCascade(child, true);
        assertTrue(cascader.getCheckedPaths().isEmpty());

        // MULTIPLE mode: select is ignored, so the single path stays null.
        cascader.setSelectionMode(SelectionMode.MULTIPLE);
        cascader.select(child);
        assertNull(cascader.getSelectedPath());
    }

    /**
     * Verifies a {@code null} selection mode on the wrapper is accepted without
     * throwing; the value resolves to the default at the use site.
     */
    @Test
    public void setSelectionModeNullDegradesToDefault() {
        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        root.getChildren().add(child);
        cascader.getRootItems().add(root);
        cascader.select(child);
        assertNotNull(cascader.getSelectedPath());

        cascader.setSelectionMode(null);

        assertNull(cascader.getSelectionMode());
    }

    /**
     * Verifies two paths over the same item instances are equal (identity-based on
     * the items), while a different leaf instance with an equal value is not.
     */
    @Test
    public void pathEqualityIsIdentityBasedOnItems() {
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        RXCascaderItem<String> otherChild = new RXCascaderItem<>("child");

        RXCascaderPath<String> a = new RXCascaderPath<>(List.of(root, child));
        RXCascaderPath<String> b = new RXCascaderPath<>(List.of(root, child));
        RXCascaderPath<String> c = new RXCascaderPath<>(List.of(root, otherChild));

        assertEquals(a, b, "paths over the same item instances are equal");
        assertEquals(a.hashCode(), b.hashCode(), "equal paths share a hash code");
        assertNotEquals(a, c, "a different leaf instance is unequal even with an equal value");
    }

    /**
     * Verifies path equality stays identity-based even when items override
     * {@code equals}: a subclass with value-based equality must not make two
     * distinct nodes' paths compare equal, which would suppress real selection
     * changes and merge distinct paths.
     */
    @Test
    public void pathEqualityIgnoresOverriddenItemEquals() {
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> a = new ValueEqualItem<>("same");
        RXCascaderItem<String> b = new ValueEqualItem<>("same");
        assertEquals(a, b, "precondition: the subclass makes equal-valued items compare equal");

        RXCascaderPath<String> pathA = new RXCascaderPath<>(List.of(root, a));
        RXCascaderPath<String> pathB = new RXCascaderPath<>(List.of(root, b));

        assertNotEquals(pathA, pathB,
                "distinct item instances must yield unequal paths despite value-based item equals");
        assertTrue(pathA.contains(a), "contains finds the exact instance");
        assertFalse(pathA.contains(b), "contains compares by identity, not value");
    }

    /** Item subclass with value-based equality, to prove path equality ignores it. */
    private static final class ValueEqualItem<T> extends RXCascaderItem<T> {
        ValueEqualItem(T value) {
            super(value);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RXCascaderItem<?> other
                    && Objects.equals(getValue(), other.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getValue());
        }
    }

    /**
     * Verifies re-selecting the same leaf fires no {@code selectedPath} change: the
     * new snapshot compares equal to the current one, so the change event is
     * suppressed (paths implement value equality on their item chain).
     */
    @Test
    public void reselectingSameLeafFiresNoSelectedPathChange() {
        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        root.getChildren().add(child);
        cascader.getRootItems().add(root);

        cascader.select(child);
        int[] changes = {0};
        cascader.selectedPathProperty().addListener((obs, old, now) -> changes[0]++);

        cascader.select(child);

        assertEquals(0, changes[0],
                "re-selecting the same leaf must not fire a selectedPath change");
    }
}
