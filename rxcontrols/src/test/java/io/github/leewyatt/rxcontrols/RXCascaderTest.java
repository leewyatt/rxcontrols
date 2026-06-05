package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.util.Callback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
