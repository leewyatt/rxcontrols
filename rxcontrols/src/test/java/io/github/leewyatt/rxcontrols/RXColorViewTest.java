package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.internal.color.ColorViewAccess;
import io.github.leewyatt.rxcontrols.skins.RXColorViewSkin;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link RXColorView} contract with synthetic input: live quantized
 * writes, the one-action-per-edit session, hue kept through grays and black,
 * bound and locked values refusing input, keyboard steps, the opacity switch,
 * bounds that stay put while dragging, and gestures ended by hiding or
 * disposing the skin.
 */
public class RXColorViewTest {

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
        stage.setScene(new Scene(new StackPane(view), 400, 400));
        stage.show();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private Region part(String selector) {
        return (Region) view.lookup(selector);
    }

    private static void mouse(Node node, EventType<MouseEvent> type, double fx, double fy) {
        Region region = (Region) node;
        Point2D scene = node.localToScene(fx * region.getWidth(), fy * region.getHeight());
        boolean down = type != MouseEvent.MOUSE_RELEASED;
        node.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, true, false, false,
                null));
    }

    private static void key(Node node, EventType<KeyEvent> type, KeyCode code, boolean shift) {
        node.fireEvent(new KeyEvent(type, "", "", code, shift, false, false, false));
    }

    private static Color expected(double hue, double saturation, double brightness, double opacity) {
        return ColorMath.quantize(ColorMath.hsb(hue, saturation, brightness, opacity));
    }

    // ==================== Defaults ====================

    @Test
    public void defaultsToWhiteAndFlagsNoColorAsEmpty() throws InterruptedException {
        runOnFx(() -> {
            RXColorView colorView = new RXColorView();
            assertEquals(Color.WHITE, colorView.getValue());
            assertFalse(hasPseudoClass(colorView, "empty"));
            assertFalse(colorView.isFocusTraversable());
            colorView.setValue(null);
            assertTrue(hasPseudoClass(colorView, "empty"));
            assertTrue(hasPseudoClass(new RXColorView(null), "empty"));
        });
    }

    // ==================== Pointer ====================

    @Test
    public void draggingWritesQuantizedColorsLiveAndActsOnceOnRelease() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#1677ff")));
            Region area = part(".area");
            double hue = ColorMath.toHsb(Color.web("#1677ff"))[0];
            List<Color> seenWhenChangingEnds = new ArrayList<>();
            view.valueChangingProperty().addListener((obs, was, is) -> {
                if (!is) {
                    seenWhenChangingEnds.add(view.getValue());
                }
            });

            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.25);
            assertTrue(view.isValueChanging());
            assertEquals(expected(hue, 0.5, 0.75, 1.0), view.getValue());
            mouse(area, MouseEvent.MOUSE_DRAGGED, 0.25, 0.5);
            assertEquals(expected(hue, 0.25, 0.5, 1.0), view.getValue());
            assertEquals(0, actions.get());

            mouse(area, MouseEvent.MOUSE_RELEASED, 0.25, 0.5);
            assertFalse(view.isValueChanging());
            assertEquals(1, actions.get());
            assertEquals(List.of(expected(hue, 0.25, 0.5, 1.0)), seenWhenChangingEnds);
        });
    }

    @Test
    public void anEditThatEndsWhereItStartedFiresNothing() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region area = part(".area");
            // White sits at the top-left corner of the area.
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.0, 0.0);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.0, 0.0);
            assertEquals(Color.WHITE, view.getValue());
            assertEquals(0, actions.get());

            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_DRAGGED, 0.0, 0.0);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.0, 0.0);
            assertEquals(Color.WHITE, view.getValue());
            assertEquals(0, actions.get());
        });
    }

    @Test
    public void settingTheValueFromCodeFiresNothing() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            view.setValue(Color.RED);
            view.setValue(null);
            view.setValue(Color.color(0.123456, 0.5, 0.5, 0.7));
            assertEquals(Color.color(0.123456, 0.5, 0.5, 0.7), view.getValue());
            assertEquals(0, actions.get());
        });
    }

    @Test
    public void movingTheHueOnWhiteKeepsTheValueButMovesThePanel() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region hue = part(".hue-slider");
            mouse(hue, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(hue, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(Color.WHITE, view.getValue());
            assertEquals(0, actions.get());
            assertEquals("Hue 180°", hue.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            // Adding saturation now shows the hue the panel kept.
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 1.0, 0.0);
            mouse(area, MouseEvent.MOUSE_RELEASED, 1.0, 0.0);
            assertEquals(expected(180.0, 1.0, 1.0, 1.0), view.getValue());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void hueAndSaturationSurviveDraggingThroughBlack() throws InterruptedException {
        runOnFx(() -> {
            Color start = Color.web("#1677ff");
            show(new RXColorView(start));
            Region area = part(".area");
            double[] hsb = ColorMath.toHsb(start);
            mouse(area, MouseEvent.MOUSE_PRESSED, hsb[1], 0.5);
            mouse(area, MouseEvent.MOUSE_DRAGGED, hsb[1], 1.0);
            assertEquals(Color.BLACK, view.getValue());
            mouse(area, MouseEvent.MOUSE_DRAGGED, hsb[1], 0.0);
            mouse(area, MouseEvent.MOUSE_RELEASED, hsb[1], 0.0);
            assertEquals(expected(hsb[0], hsb[1], 1.0, 1.0), view.getValue());
        });
    }

    // ==================== Sync ====================

    @Test
    public void aValueSetBackAfterAnotherIsShown() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.8, 0.2);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.8, 0.2);
            Color edited = view.getValue();
            Object editedText = area.queryAccessibleAttribute(AccessibleAttribute.TEXT);

            view.setValue(Color.web("#2ddcb4"));
            assertNotEquals(editedText, area.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            view.setValue(edited);
            assertEquals(editedText, area.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            view.setValue(null);
            assertTrue(part(".preview").getPseudoClassStates().stream()
                    .anyMatch(p -> p.getPseudoClassName().equals("empty")));
            view.setValue(edited);
            assertEquals(editedText, area.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        });
    }

    @Test
    public void editingAfterClearingRestoresTheOpacity() throws InterruptedException {
        runOnFx(() -> {
            Color start = Color.rgb(10, 100, 200, 128 / 255.0);
            show(new RXColorView(start));
            view.setValue(null);
            Region area = part(".area");
            double[] hsb = ColorMath.toHsb(start);
            mouse(area, MouseEvent.MOUSE_PRESSED, hsb[1], 1.0 - hsb[2]);
            mouse(area, MouseEvent.MOUSE_RELEASED, hsb[1], 1.0 - hsb[2]);
            assertEquals(start, view.getValue());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void startingWithNoColorEditsFromRed() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(null));
            Region hue = part(".hue-slider");
            mouse(hue, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(hue, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(expected(180.0, 1.0, 1.0, 1.0), view.getValue());
            assertEquals(1, actions.get());
        });
    }

    // ==================== Bound and locked ====================

    @Test
    public void aBoundValueTakesNoInput() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            SimpleObjectProperty<Color> source = new SimpleObjectProperty<>(Color.RED);
            view.valueProperty().bind(source);
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            assertFalse(view.isValueChanging());
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            key(area, KeyEvent.KEY_PRESSED, KeyCode.LEFT, false);
            assertEquals(Color.RED, view.getValue());
            assertEquals(0, actions.get());

            view.valueProperty().unbind();
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(expected(0.0, 0.5, 0.5, 1.0), view.getValue());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void theInputLockIsEvaluatedWhenInputStarts() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            boolean[] locked = {true};
            ColorViewAccess.setInputLock(view, () -> locked[0]);
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(Color.WHITE, view.getValue());

            locked[0] = false;
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertNotEquals(Color.WHITE, view.getValue());
        });
    }

    // ==================== Keyboard ====================

    @Test
    public void aKeyboardChangeActsOnceWhenItsKeyIsReleased() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.web("#808080")));
            Region area = part(".area");
            key(area, KeyEvent.KEY_PRESSED, KeyCode.RIGHT, false);
            key(area, KeyEvent.KEY_PRESSED, KeyCode.RIGHT, false);
            key(area, KeyEvent.KEY_PRESSED, KeyCode.RIGHT, false);
            assertEquals(expected(0.0, 0.03, ColorMath.toHsb(Color.web("#808080"))[2], 1.0), view.getValue());
            assertEquals(0, actions.get());
            key(area, KeyEvent.KEY_RELEASED, KeyCode.SHIFT, false);
            assertEquals(0, actions.get());
            key(area, KeyEvent.KEY_RELEASED, KeyCode.RIGHT, false);
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void shiftAndPageKeysTakeLargeSteps() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.BLACK));
            Region area = part(".area");
            key(area, KeyEvent.KEY_PRESSED, KeyCode.PAGE_UP, false);
            key(area, KeyEvent.KEY_PRESSED, KeyCode.UP, true);
            key(area, KeyEvent.KEY_RELEASED, KeyCode.PAGE_UP, false);
            assertEquals(expected(0.0, 0.0, 0.2, 1.0), view.getValue());

            Region hue = part(".hue-slider");
            key(hue, KeyEvent.KEY_PRESSED, KeyCode.END, false);
            // 360 reads as 0 degrees, but the thumb stays at the right end.
            assertEquals("Hue 0°", hue.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            Region thumb = (Region) hue.lookup(".thumb");
            assertEquals(hue.getWidth() - hue.snappedRightInset(),
                    thumb.getLayoutX() + thumb.getWidth() / 2.0, 0.5);
        });
    }

    @Test
    public void opacityStepsNeverSkipAPercentage() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(10, 20, 30, 128 / 255.0)));
            Region alpha = part(".alpha-slider");
            List<Integer> shown = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                key(alpha, KeyEvent.KEY_PRESSED, KeyCode.RIGHT, false);
                shown.add(ColorMath.toPercent(view.getValue().getOpacity()));
            }
            key(alpha, KeyEvent.KEY_RELEASED, KeyCode.RIGHT, false);
            assertEquals(List.of(51, 52, 53, 54, 55), shown);
            assertEquals(1, actions.get());
        });
    }

    // ==================== Opacity switch ====================

    @Test
    public void withAlphaDisabledEditsWriteOpaqueColorsButCodeKeepsItsOpacity() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView(Color.rgb(10, 20, 30, 0.5)));
            view.setAlphaEnabled(false);
            assertTrue(hasPseudoClass(view, "alpha-disabled"));
            assertFalse(part(".alpha-slider").isVisible());
            assertEquals(0.5, view.getValue().getOpacity(), 1e-6);

            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertEquals(1.0, view.getValue().getOpacity());
            assertEquals(1, actions.get());
        });
    }

    // ==================== Bounds ====================

    @Test
    public void draggingToTheEdgesNeverMovesTheViewBounds() throws InterruptedException {
        runOnFx(() -> {
            RXColorView colorView = new RXColorView(Color.web("#1677ff80"));
            colorView.setStyle("-fx-padding: 0;");
            show(colorView);
            Bounds before = view.getBoundsInLocal();
            for (String selector : new String[] {".area", ".hue-slider", ".alpha-slider"}) {
                Region part = part(selector);
                double[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (double[] corner : corners) {
                    mouse(part, MouseEvent.MOUSE_PRESSED, corner[0], corner[1]);
                    mouse(part, MouseEvent.MOUSE_DRAGGED, corner[0] * 2 - 0.5, corner[1] * 2 - 0.5);
                    mouse(part, MouseEvent.MOUSE_RELEASED, corner[0], corner[1]);
                    view.applyCss();
                    view.layout();
                    assertEquals(before, view.getBoundsInLocal(), selector + " at " + corner[0] + "," + corner[1]);
                }
            }
        });
    }

    // ==================== Ending gestures ====================

    @Test
    public void disposingTheSkinMidDragClearsValueChanging() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            mouse(part(".area"), MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            assertTrue(view.isValueChanging());
            view.setSkin(null);
            assertFalse(view.isValueChanging());
        });
    }

    @Test
    public void disposingASkinThatWasNeverInstalledLeavesTheGestureAlone() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            new RXColorViewSkin(view).dispose();
            assertTrue(view.isValueChanging(), "the gesture belongs to the installed skin");
            mouse(area, MouseEvent.MOUSE_RELEASED, 0.5, 0.5);
            assertFalse(view.isValueChanging());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void aSkinOfAnotherClassFindsOnlyItsOwnNodes() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Label other = new Label("other");
            view.setSkin(new SkinBase<RXColorView>(view) {
                {
                    getChildren().add(other);
                }
            });
            assertEquals(List.of(other), view.getChildrenUnmodifiable());
        });
    }

    @Test
    public void hidingMidDragEndsTheGestureAndActsAfterTheHide() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            stage.hide();
            assertFalse(view.isValueChanging());
            assertEquals(0, actions.get());
        });
        runOnFx(() -> assertEquals(1, actions.get()));
    }

    @Test
    public void disablingMidDragEndsTheGesture() throws InterruptedException {
        runOnFx(() -> {
            show(new RXColorView());
            Region area = part(".area");
            mouse(area, MouseEvent.MOUSE_PRESSED, 0.5, 0.5);
            view.setDisable(true);
            assertFalse(view.isValueChanging());
            assertEquals(1, actions.get());
            Color afterDisable = view.getValue();
            mouse(area, MouseEvent.MOUSE_DRAGGED, 0.1, 0.1);
            assertEquals(afterDisable, view.getValue());
            assertEquals(1, actions.get());
        });
    }
}
