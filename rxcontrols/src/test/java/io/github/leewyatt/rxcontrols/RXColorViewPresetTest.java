package io.github.leewyatt.rxcontrols;

import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the clear button and preset groups of {@link RXColorView}: clearing to
 * {@code null}, picking swatches with the pointer and the keyboard, selection
 * marks compared on the 8-bit grid, groups with and without headers, kept
 * expanded state, and the immutable {@link RXColorPresetGroup}.
 */
public class RXColorViewPresetTest {

    private Stage stage;
    private RXColorView view;
    private final AtomicInteger actions = new AtomicInteger();

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        PickerTestSupport.startToolkit();
    }

    @AfterEach
    public void closeStage() throws InterruptedException {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
            }
        });
    }

    private void show(RXColorView colorView) {
        view = colorView;
        view.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());
        stage = new Stage();
        stage.setScene(new Scene(new StackPane(view), 400, 700));
        stage.show();
        layout();
    }

    private void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private List<Node> swatches() {
        return new ArrayList<>(view.lookupAll(".preset"));
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, true, false, false, null));
    }

    private static void key(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    // ==================== Clear ====================

    @Test
    public void theClearButtonIsShownOnlyWhenClearable() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Node header = view.lookup(".header");
            assertFalse(header.isVisible());
            assertFalse(header.isManaged());
            view.setClearable(true);
            assertTrue(header.isVisible());
        });
    }

    @Test
    public void clearingSetsNoColorOnceAndEditingBringsTheColorBack() throws InterruptedException {
        runOnFx(() -> {
            Color start = Color.rgb(10, 100, 200, 128 / 255.0);
            show(new RXColorView(start));
            view.setClearable(true);
            layout();
            Node clear = view.lookup(".clear-button");
            click(clear);
            assertNull(view.getValue());
            assertEquals(1, actions.get());
            click(clear);
            assertEquals(1, actions.get());

            key(view.lookup(".area"), KeyCode.RIGHT);
            key(view.lookup(".area"), KeyCode.LEFT);
            assertEquals(start, view.getValue());
        });
    }

    // ==================== Presets ====================

    @Test
    public void presetsShowOnlyWhenThereAreGroups() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Node presets = view.lookup(".presets");
            assertFalse(presets.isVisible());
            view.getPresets().add(null);
            assertFalse(presets.isVisible());
            view.getPresets().add(new RXColorPresetGroup(null, Color.RED, Color.BLUE));
            assertTrue(presets.isVisible());
            layout();
            assertEquals(2, swatches().size());
            view.getPresets().clear();
            assertFalse(presets.isVisible());
        });
    }

    @Test
    public void clickingASwatchPicksItsColorAndMarksIt() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.getPresets().add(new RXColorPresetGroup("Brand", Color.web("#1769AA"), Color.web("#F57C00")));
            layout();
            List<Node> swatches = swatches();
            click(swatches.get(1));
            assertEquals(Color.web("#F57C00"), view.getValue());
            assertEquals(1, actions.get());
            assertTrue(hasPseudoClass(swatches.get(1), "selected"));
            assertFalse(hasPseudoClass(swatches.get(0), "selected"));

            click(swatches.get(1));
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void selectionIsComparedOnTheGrid() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(128, 128, 128)));
            view.getPresets().add(new RXColorPresetGroup(null, Color.color(0.5, 0.5, 0.501)));
            layout();
            assertTrue(hasPseudoClass(swatches().get(0), "selected"));
        });
    }

    @Test
    public void withAlphaDisabledAPickIsOpaqueAndStillMarked() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.setAlphaEnabled(false);
            view.getPresets().add(new RXColorPresetGroup(null, Color.rgb(200, 10, 10, 0.5)));
            layout();
            Node swatch = swatches().get(0);
            click(swatch);
            assertEquals(Color.rgb(200, 10, 10), view.getValue());
            assertTrue(hasPseudoClass(swatch, "selected"));
        });
    }

    @Test
    public void aLabeledGroupStartsAsConfiguredAndKeepsTheUsersChoice() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            RXColorPresetGroup recent = new RXColorPresetGroup("Recent", List.of(Color.RED), false);
            view.getPresets().add(recent);
            layout();
            RXTitledPane pane = (RXTitledPane) view.lookup(".presets .rx-titled-pane");
            assertFalse(pane.isExpanded());
            pane.setExpanded(true);

            view.getPresets().add(0, new RXColorPresetGroup("Brand", Color.BLUE));
            layout();
            List<Node> panes = new ArrayList<>(view.lookupAll(".presets .rx-titled-pane"));
            assertEquals(2, panes.size());
            assertSame(pane, panes.get(1));
            assertTrue(pane.isExpanded());
        });
    }

    @Test
    public void theKeyboardMovesACursorAndPicks() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.getPresets().add(new RXColorPresetGroup(null, Color.RED, Color.LIME, Color.BLUE));
            layout();
            Node grid = view.lookup(".presets .swatches");
            List<Node> swatches = swatches();
            key(grid, KeyCode.RIGHT);
            assertTrue(hasPseudoClass(swatches.get(0), "focused"));
            key(grid, KeyCode.RIGHT);
            assertTrue(hasPseudoClass(swatches.get(1), "focused"));
            assertFalse(hasPseudoClass(swatches.get(0), "focused"));
            key(grid, KeyCode.END);
            key(grid, KeyCode.ENTER);
            assertEquals(Color.BLUE, view.getValue());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void aBoundValueIgnoresPresetsAndClearing() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.setClearable(true);
            view.getPresets().add(new RXColorPresetGroup(null, Color.RED));
            layout();
            view.valueProperty().bind(new SimpleObjectProperty<>(Color.BLUE));
            click(swatches().get(0));
            click(view.lookup(".clear-button"));
            assertEquals(Color.BLUE, view.getValue());
            assertEquals(0, actions.get());
        });
    }

    // ==================== Group record ====================

    @Test
    public void aGroupCopiesItsColorsAndRejectsNull() {
        List<Color> colors = new ArrayList<>(List.of(Color.RED));
        RXColorPresetGroup group = new RXColorPresetGroup("A", colors, true);
        colors.add(Color.BLUE);
        assertEquals(List.of(Color.RED), group.colors());
        assertThrows(NullPointerException.class, () -> new RXColorPresetGroup("A", Arrays.asList(Color.RED, null), true));
        assertThrows(NullPointerException.class, () -> new RXColorPresetGroup("A", null, true));
        assertTrue(new RXColorPresetGroup("A", Color.RED).defaultExpanded());
    }
}
