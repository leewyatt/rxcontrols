package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSpeedDial.Direction;
import io.github.leewyatt.rxcontrols.RXSpeedDial.LabelMode;
import io.github.leewyatt.rxcontrols.RXSpeedDial.OpenTrigger;
import io.github.leewyatt.rxcontrols.event.RXSpeedDialEvent;
import io.github.leewyatt.rxcontrols.skins.RXSpeedDialSkin;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSpeedDial}.
 */
public class RXSpeedDialTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass UP = PseudoClass.getPseudoClass("up");
    private static final PseudoClass DOWN = PseudoClass.getPseudoClass("down");
    private static final PseudoClass LEFT = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT = PseudoClass.getPseudoClass("right");

    private Stage stage;

    /**
     * Starts the JavaFX toolkit so CSS and skins can be applied.
     *
     * @throws InterruptedException if startup is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        Platform.setImplicitExit(false);
    }

    /**
     * Closes any stage opened by focus-sensitive tests.
     *
     * @throws Exception if the FX-thread cleanup fails
     */
    @AfterEach
    public void cleanup() throws Exception {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * Verifies default public state and skin creation.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultStateMatchesContract() throws Exception {
        runOnFx(() -> {
            Region icon = new Region();
            RXSpeedDial dial = new RXSpeedDial(icon);
            attachAndApplyCss(dial);

            assertTrue(dial.getStyleClass().contains("rx-speed-dial"));
            assertNotNull(dial.getUserAgentStylesheet());
            assertTrue(dial.getSkin() instanceof RXSpeedDialSkin);
            assertSame(icon, dial.getIcon());
            assertNull(dial.getOpenIcon());
            assertEquals(Direction.UP, dial.getDirection());
            assertEquals(OpenTrigger.CLICK, dial.getOpenTrigger());
            assertEquals(LabelMode.HOVER, dial.getLabelMode());
            assertTrue(dial.isAnimated());
            assertEquals(RXSpeedDial.DEFAULT_ANIMATION_DURATION, dial.getAnimationDuration());
            assertEquals(RXSpeedDial.DEFAULT_STAGGER_DELAY, dial.getStaggerDelay());
            assertEquals(RXSpeedDial.DEFAULT_ACTION_SPACING, dial.getActionSpacing(), EPSILON);
            assertEquals(RXSpeedDial.DEFAULT_LABEL_GAP, dial.getLabelGap(), EPSILON);
            assertTrue(dial.isCloseOnFocusLoss());
            assertTrue(dial.isCloseOnClickOutside());
            assertFalse(dial.isShowing());
            assertEquals(Boolean.FALSE, dial.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertEquals(Boolean.FALSE,
                    mainFab(dial).queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertEquals(Region.USE_PREF_SIZE, dial.getMinWidth());
            assertEquals(Region.USE_PREF_SIZE, dial.getMinHeight());
            assertEquals(Region.USE_PREF_SIZE, dial.getMaxWidth());
            assertEquals(Region.USE_PREF_SIZE, dial.getMaxHeight());

            dial.setCloseOnFocusLoss(false);
            dial.setCloseOnClickOutside(false);
            assertFalse(dial.isCloseOnFocusLoss());
            assertFalse(dial.isCloseOnClickOutside());
        });
    }

    /**
     * Verifies action defaults and pass-through properties.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionModelStoresValues() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction();

            assertEquals("", action.getText());
            assertNull(action.getGraphic());
            assertNull(action.getOnAction());
            assertFalse(action.isDisable());
            assertTrue(action.isVisible());
            assertTrue(action.isCloseOnAction());

            Region graphic = new Region();
            EventHandler<ActionEvent> handler = event -> {
            };
            action.setText(null);
            action.setGraphic(graphic);
            action.setOnAction(handler);
            action.setDisable(true);
            action.setVisible(false);
            action.setCloseOnAction(false);

            assertNull(action.getText());
            assertSame(graphic, action.getGraphic());
            assertSame(handler, action.getOnAction());
            assertTrue(action.isDisable());
            assertFalse(action.isVisible());
            assertFalse(action.isCloseOnAction());

            Region constructedGraphic = new Region();
            RXSpeedDialAction constructed =
                    new RXSpeedDialAction("Upload", constructedGraphic, handler);
            assertEquals("Upload", constructed.getText());
            assertSame(constructedGraphic, constructed.getGraphic());
            assertSame(handler, constructed.getOnAction());
        });
    }

    /**
     * Verifies direction pseudo-classes and pass-through null behavior.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void directionPseudoClassesTrackDirection() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial();

            assertTrue(dial.getPseudoClassStates().contains(UP));
            assertFalse(dial.getPseudoClassStates().contains(DOWN));

            dial.setDirection(Direction.LEFT);
            assertEquals(Direction.LEFT, dial.getDirection());
            assertFalse(dial.getPseudoClassStates().contains(UP));
            assertTrue(dial.getPseudoClassStates().contains(LEFT));
            assertFalse(dial.getPseudoClassStates().contains(RIGHT));

            dial.setDirection(Direction.DOWN);
            assertEquals(Direction.DOWN, dial.getDirection());
            assertTrue(dial.getPseudoClassStates().contains(DOWN));
            assertFalse(dial.getPseudoClassStates().contains(LEFT));

            dial.setDirection(null);
            assertNull(dial.getDirection());
            assertTrue(dial.getPseudoClassStates().contains(UP));
            assertFalse(dial.getPseudoClassStates().contains(DOWN));
        });
    }

    /**
     * Verifies skin-only terminal callbacks are not exposed as public API.
     */
    @Test
    public void terminalNotifyMethodsAreNotPublicApi() {
        assertThrows(NoSuchMethodException.class, () -> RXSpeedDial.class.getMethod("notifyShown"));
        assertThrows(NoSuchMethodException.class, () -> RXSpeedDial.class.getMethod("notifyHidden"));
    }

    /**
     * Verifies CSS metadata for speed-dial animation properties.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssMetadataIncludesAnimationProperties() throws Exception {
        runOnFx(() -> {
            List<String> propertyNames = RXSpeedDial.getClassCssMetaData().stream()
                    .map(CssMetaData<? extends Styleable, ?>::getProperty)
                    .toList();
            Set<String> properties = propertyNames.stream().collect(Collectors.toSet());

            assertEquals(propertyNames.size(), properties.size());
            assertTrue(properties.contains("-rx-animation-duration"));
            assertTrue(properties.contains("-rx-stagger-delay"));
            assertTrue(properties.contains("-rx-action-spacing"));
            assertTrue(properties.contains("-rx-label-gap"));
            assertFalse(properties.contains("-rx-animated"));
            assertEquals(1L, propertyNames.stream()
                    .filter("-rx-animation-duration"::equals)
                    .count());
            assertEquals(1L, propertyNames.stream()
                    .filter("-rx-stagger-delay"::equals)
                    .count());
            assertEquals(1L, propertyNames.stream()
                    .filter("-rx-action-spacing"::equals)
                    .count());
            assertEquals(1L, propertyNames.stream()
                    .filter("-rx-label-gap"::equals)
                    .count());
        });
    }

    /**
     * Verifies speed-dial duration properties can be styled through CSS.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssAppliesAnimationDurations() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial();
            dial.setStyle("-rx-animation-duration: 123ms; -rx-stagger-delay: 7ms; "
                    + "-rx-action-spacing: 14px; -rx-label-gap: 6px;");
            attachAndApplyCss(dial);

            assertEquals(Duration.millis(123.0), dial.getAnimationDuration());
            assertEquals(Duration.millis(7.0), dial.getStaggerDelay());
            assertEquals(14.0, dial.getActionSpacing(), EPSILON);
            assertEquals(6.0, dial.getLabelGap(), EPSILON);
        });
    }

    /**
     * Verifies the snap branch reaches the same terminal action pose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void animatedFalseSnapsActionPose() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            dial.open();
            applyCssAndLayout(dial);
            Node cell = visibleActionCells(dial).get(0);
            assertTrue(actionsLayer(dial).isVisible());
            assertFalse(actionsLayer(dial).isMouseTransparent());
            assertEquals(1.0, cell.getOpacity(), EPSILON);
            assertEquals(1.0, cell.getScaleX(), EPSILON);
            assertEquals(1.0, cell.getScaleY(), EPSILON);

            dial.close();
            applyCssAndLayout(dial);
            cell = actionCells(dial).get(0);
            assertFalse(actionsLayer(dial).isVisible());
            assertTrue(actionsLayer(dial).isMouseTransparent());
            assertFalse(cell.isVisible());
            assertEquals(0.0, cell.getOpacity(), EPSILON);
            assertEquals(0.6, cell.getScaleX(), EPSILON);
            assertEquals(0.6, cell.getScaleY(), EPSILON);
        });
    }

    /**
     * Verifies unusable animation durations snap without throwing.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void unusableAnimationDurationsSnap() throws Exception {
        runOnFx(() -> {
            Duration[] durations = {
                    Duration.ZERO, Duration.millis(-1.0), Duration.UNKNOWN, Duration.INDEFINITE, null
            };
            for (Duration duration : durations) {
                RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
                dial.setAnimationDuration(duration);
                attachAndApplyCss(dial);
                List<String> log = new ArrayList<>();
                dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

                dial.open();
                applyCssAndLayout(dial);
                assertTrue(dial.isShowing());
                assertEquals(1, visibleActionCells(dial).size());
                assertEquals(1.0, visibleActionCells(dial).get(0).getOpacity(), EPSILON);
                assertTrue(actionsLayer(dial).isVisible());
                assertFalse(actionsLayer(dial).isMouseTransparent());

                dial.close();
                applyCssAndLayout(dial);
                assertFalse(dial.isShowing());
                assertFalse(actionsLayer(dial).isVisible());
                assertTrue(actionsLayer(dial).isMouseTransparent());
                assertEquals(0, visibleActionCells(dial).size());
                Node cell = actionCells(dial).get(0);
                assertEquals(0.0, cell.getOpacity(), EPSILON);
                assertEquals(0.6, cell.getScaleX(), EPSILON);
                assertEquals(0.6, cell.getScaleY(), EPSILON);
                assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null",
                        "RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                        "RX_SPEED_DIAL_HIDDEN:TOGGLE"), log);
            }
        });
    }

    /**
     * Verifies label modes govern rendered label nodes and visibility.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void labelModeControlsLabelRendering() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Named", new Region());
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            dial.open();
            applyCssAndLayout(dial);
            assertEquals(LabelMode.HOVER, dial.getLabelMode());
            assertEquals(1, actionLabels(dial).size());
            assertEquals(0, visibleActionLabels(dial).size());

            dial.setLabelMode(LabelMode.PERSISTENT);
            applyCssAndLayout(dial);
            assertEquals(1, actionLabels(dial).size());
            assertEquals(1, visibleActionLabels(dial).size());

            dial.setLabelMode(LabelMode.NONE);
            applyCssAndLayout(dial);
            assertEquals(0, actionLabels(dial).size());
            assertEquals(0, visibleActionLabels(dial).size());

            dial.setLabelMode(null);
            applyCssAndLayout(dial);
            assertEquals(1, actionLabels(dial).size());
            assertEquals(0, visibleActionLabels(dial).size());
        });
    }

    /**
     * Verifies hover labels are shown for focused action FABs.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverLabelShowsOnActionFocus() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Named", new Region()));
            dial.setAnimated(false);
            showInStage(dial, new Button("outside"));
            dial.open();
            applyCssAndLayout(dial);
            assertEquals(0, visibleActionLabels(dial).size());
            visibleActionFabs(dial).get(0).requestFocus();
            dialRef[0] = dial;
        });
        runOnFx(() -> assertEquals(1, visibleActionLabels(dialRef[0]).size()));
    }

    /**
     * Verifies openIcon morph state for null and non-null open icons.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void openIconMorphSnapsToTerminalState() throws Exception {
        runOnFx(() -> {
            Region closedIcon = new Region();
            Region openIcon = new Region();
            RXSpeedDial dial = new RXSpeedDial(closedIcon);
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            assertEquals(1, iconMorph(dial).getChildrenUnmodifiable().size());
            dial.open();
            assertEquals(45.0, iconMorph(dial).getRotate(), EPSILON);
            assertEquals(1.0, closedIcon.getOpacity(), EPSILON);

            dial.close();
            dial.setOpenIcon(openIcon);
            applyCssAndLayout(dial);
            assertEquals(2, iconMorph(dial).getChildrenUnmodifiable().size());

            dial.open();
            assertEquals(45.0, iconMorph(dial).getRotate(), EPSILON);
            assertEquals(0.0, closedIcon.getOpacity(), EPSILON);
            assertEquals(1.0, openIcon.getOpacity(), EPSILON);

            dial.close();
            assertEquals(0.0, iconMorph(dial).getRotate(), EPSILON);
            assertEquals(1.0, closedIcon.getOpacity(), EPSILON);
            assertEquals(0.0, openIcon.getOpacity(), EPSILON);
        });
    }

    /**
     * Verifies animated open starts from the closed morph pose and defers SHOWN.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void animatedOpenStartsFromClosedMorphPose() throws Exception {
        runOnFx(() -> {
            Region closedIcon = new Region();
            Region openIcon = new Region();
            RXSpeedDial dial = new RXSpeedDial(closedIcon, new RXSpeedDialAction("Run", new Region()));
            dial.setOpenIcon(openIcon);
            dial.setAnimationDuration(Duration.seconds(5.0));
            attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.open();

            assertTrue(dial.isShowing());
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null"), log);
            assertEquals(0.0, iconMorph(dial).getRotate(), EPSILON);
            assertEquals(1.0, closedIcon.getOpacity(), EPSILON);
            assertEquals(0.0, openIcon.getOpacity(), EPSILON);
            dial.skinProperty().set(null);
        });
    }

    /**
     * Verifies close-during-open cancels the stale open terminal path.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void closeDuringAnimatedOpenLatestWins() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        List<String> log = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimationDuration(Duration.millis(60.0));
            dial.setStaggerDelay(Duration.ZERO);
            attachAndApplyCss(dial);
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));
            dial.open();
            dial.close();
            dialRef[0] = dial;
        });
        Thread.sleep(180);
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertFalse(dial.isShowing());
            assertFalse(actionsLayer(dial).isVisible());
            assertTrue(actionsLayer(dial).isMouseTransparent());
            Node cell = actionCells(dial).get(0);
            assertFalse(cell.isVisible());
            assertEquals(0.0, cell.getOpacity(), EPSILON);
            assertEquals(0.6, cell.getScaleX(), EPSILON);
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null",
                    "RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                    "RX_SPEED_DIAL_HIDDEN:TOGGLE"), log);
        });
    }

    /**
     * Verifies open-during-close clears the canceled close reason.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void openDuringAnimatedCloseLatestWins() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        List<String> log = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimationDuration(Duration.millis(60.0));
            dial.setStaggerDelay(Duration.ZERO);
            attachAndApplyCss(dial);
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));
            dial.open();
            dialRef[0] = dial;
        });
        Thread.sleep(140);
        runOnFx(() -> {
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null"), log);
            log.clear();
            RXSpeedDial dial = dialRef[0];
            dial.close();
            dial.open();
        });
        Thread.sleep(140);
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertTrue(dial.isShowing());
            assertTrue(actionsLayer(dial).isVisible());
            assertFalse(actionsLayer(dial).isMouseTransparent());
            assertEquals(List.of("RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                    "RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null"), log);
        });
    }

    /**
     * Verifies open, close, toggle, pseudo-class, and lifecycle event order.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void openCloseToggleFireLifecycleEvents() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("One", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.open();
            applyCssAndLayout(dial);
            assertTrue(dial.isShowing());
            assertTrue(dial.getPseudoClassStates().contains(PseudoClass.getPseudoClass("showing")));
            assertEquals(1, visibleActionCells(dial).size());
            assertEquals(Boolean.TRUE, dial.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertEquals(Boolean.TRUE,
                    mainFab(dial).queryAccessibleAttribute(AccessibleAttribute.EXPANDED));

            dial.close();
            applyCssAndLayout(dial);
            assertFalse(dial.isShowing());
            assertFalse(dial.getPseudoClassStates().contains(PseudoClass.getPseudoClass("showing")));
            assertEquals(0, visibleActionCells(dial).size());
            assertEquals(Boolean.FALSE, dial.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertEquals(Boolean.FALSE,
                    mainFab(dial).queryAccessibleAttribute(AccessibleAttribute.EXPANDED));

            dial.toggle();
            assertTrue(dial.isShowing());
            dial.toggle();
            assertFalse(dial.isShowing());

            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null",
                    "RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                    "RX_SPEED_DIAL_HIDDEN:TOGGLE",
                    "RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null",
                    "RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                    "RX_SPEED_DIAL_HIDDEN:TOGGLE"), log);
        });
    }

    /**
     * Verifies the internal main FAB action handler toggles the dial.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void mainFabFireTogglesDial() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));
            RXFloatingActionButton mainFab = mainFab(dial);

            mainFab.fire();
            assertTrue(dial.isShowing());

            mainFab.fire();
            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.TOGGLE), reasons);
        });
    }

    /**
     * Verifies open and close guards are no-ops.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void stateGuardsDoNotFireDuplicateEvents() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial();
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(event.getEventType().getName()));

            dial.close();
            assertTrue(log.isEmpty());

            dial.open();
            dial.open();
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING", "RX_SPEED_DIAL_SHOWN"), log);

            dial.close();
            dial.setDisable(true);
            dial.open();
            assertFalse(dial.isShowing());
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING", "RX_SPEED_DIAL_SHOWN",
                    "RX_SPEED_DIAL_CLOSE_REQUEST", "RX_SPEED_DIAL_HIDING", "RX_SPEED_DIAL_HIDDEN"), log);
        });
    }

    /**
     * Verifies close-request veto keeps the dial open.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void closeRequestCanVetoClose() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial();
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.open();
            log.clear();
            dial.setOnCloseRequest(event -> event.consume());
            dial.close();

            assertTrue(dial.isShowing());
            assertEquals(List.of("RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE"), log);
        });
    }

    /**
     * Verifies onXxx event handler properties are pass-through.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void eventHandlerPropertiesArePassThrough() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial();
            EventHandler<RXSpeedDialEvent> handler = event -> {
            };

            dial.setOnShowing(handler);
            dial.setOnShown(handler);
            dial.setOnCloseRequest(handler);
            dial.setOnHiding(handler);
            dial.setOnHidden(handler);

            assertSame(handler, dial.getOnShowing());
            assertSame(handler, dial.getOnShown());
            assertSame(handler, dial.getOnCloseRequest());
            assertSame(handler, dial.getOnHiding());
            assertSame(handler, dial.getOnHidden());

            dial.setOnHidden(null);
            assertNull(dial.getOnHidden());
        });
    }

    /**
     * Verifies action list changes rebuild action cells.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionsListSynchronizesCells() throws Exception {
        runOnFx(() -> {
            Region firstGraphic = new Region();
            Region secondGraphic = new Region();
            RXSpeedDialAction first = new RXSpeedDialAction("First", firstGraphic);
            RXSpeedDialAction second = new RXSpeedDialAction("Second", secondGraphic);
            RXSpeedDial dial = new RXSpeedDial(new Region(), first);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertEquals(1, actionCells(dial).size());
            assertEquals(1, visibleActionCells(dial).size());

            dial.getActions().add(second);
            applyCssAndLayout(dial);
            assertEquals(2, actionCells(dial).size());
            assertEquals(2, visibleActionCells(dial).size());

            dial.getActions().remove(first);
            applyCssAndLayout(dial);
            assertEquals(1, actionCells(dial).size());
            assertEquals(1, visibleActionCells(dial).size());
            assertNull(firstGraphic.getParent());
            assertSame(second.getGraphic(), visibleActionFabs(dial).get(0).getGraphic());
        });
    }

    /**
     * Verifies list rebuild settles in-flight animation and releases old cells.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionsListChangeSettlesRunningAnimation() throws Exception {
        runOnFx(() -> {
            Region graphic = new Region();
            RXSpeedDialAction action = new RXSpeedDialAction("Run", graphic);
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimationDuration(Duration.seconds(5.0));
            attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.open();
            dial.getActions().remove(action);

            assertTrue(dial.isShowing());
            assertNull(graphic.getParent());
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null"), log);
        });
    }

    /**
     * Verifies null action entries are ignored by the default skin.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nullActionEntriesAreIgnoredBySkin() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region());
            dial.setAnimated(false);
            dial.getActions().add(null);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertEquals(0, visibleActionCells(dial).size());

            dial.getActions().add(new RXSpeedDialAction("Visible", new Region()));
            applyCssAndLayout(dial);
            assertEquals(1, visibleActionCells(dial).size());
        });
    }

    /**
     * Verifies action properties are bound into the rendered mini FAB.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionCellBindsToActionProperties() throws Exception {
        runOnFx(() -> {
            Region graphic = new Region();
            graphic.getStyleClass().add("icon");
            RXSpeedDialAction action = new RXSpeedDialAction("Edit", graphic);
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            RXFloatingActionButton fab = visibleActionFabs(dial).get(0);
            assertSame(graphic, fab.getGraphic());
            assertEquals("Edit", fab.getAccessibleText());
            assertFalse(fab.isDisable());
            assertEquals(RXFloatingActionButton.Size.SMALL, fab.getSize());
            assertEquals(40.0, fab.prefWidth(-1), 0.0001);
            assertEquals(40.0, fab.prefHeight(-1), 0.0001);
            assertEquals(Color.web("#333333"), iconFill(graphic));
            assertFalse(Color.WHITE.equals(iconFill(graphic)));

            Region replacement = new Region();
            action.setGraphic(replacement);
            action.setText("Rename");
            action.setDisable(true);
            applyCssAndLayout(dial);

            assertSame(replacement, fab.getGraphic());
            assertEquals("Rename", fab.getAccessibleText());
            assertTrue(fab.isDisable());
        });
    }

    /**
     * Verifies empty labels are not rendered as visible pills.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void emptyActionTextDoesNotRenderLabelPill() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction();
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            dial.setLabelMode(LabelMode.PERSISTENT);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertEquals(1, visibleActionCells(dial).size());
            assertEquals(0, visibleActionLabels(dial).size());

            action.setText("Named");
            applyCssAndLayout(dial);
            assertEquals(1, visibleActionLabels(dial).size());

            action.setText(null);
            applyCssAndLayout(dial);
            assertEquals(0, visibleActionLabels(dial).size());
        });
    }

    /**
     * Verifies hidden actions do not leave layout gaps.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void invisibleActionsDoNotReserveSlots() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction first = new RXSpeedDialAction("First", new Region());
            RXSpeedDialAction hidden = new RXSpeedDialAction("Hidden", new Region());
            RXSpeedDialAction third = new RXSpeedDialAction("Third", new Region());
            hidden.setVisible(false);
            RXSpeedDial dial = new RXSpeedDial(new Region(), first, hidden, third);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            List<RXFloatingActionButton> fabs = visibleActionFabs(dial);
            assertEquals(2, fabs.size());

            List<Double> centers = visibleActionCells(dial).stream()
                    .map(RXSpeedDialTest::centerY)
                    .sorted()
                    .toList();
            assertEquals(48.0, centers.get(1) - centers.get(0), 1.0);
        });
    }

    /**
     * Verifies dynamic action visibility relayout.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void visiblePropertyChangesRelayoutCells() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction first = new RXSpeedDialAction("First", new Region());
            RXSpeedDialAction second = new RXSpeedDialAction("Second", new Region());
            RXSpeedDialAction third = new RXSpeedDialAction("Third", new Region());
            RXSpeedDial dial = new RXSpeedDial(new Region(), first, second, third);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertEquals(3, visibleActionCells(dial).size());

            second.setVisible(false);
            applyCssAndLayout(dial);
            assertEquals(3, actionCells(dial).size());
            assertEquals(2, visibleActionCells(dial).size());
            assertAdjacentVisibleCellGap(dial, 48.0);

            second.setVisible(true);
            applyCssAndLayout(dial);
            assertEquals(3, visibleActionCells(dial).size());
            List<Double> centers = visibleActionCells(dial).stream()
                    .map(RXSpeedDialTest::centerY)
                    .sorted()
                    .toList();
            assertEquals(48.0, centers.get(1) - centers.get(0), 1.0);
            assertEquals(48.0, centers.get(2) - centers.get(1), 1.0);
        });
    }

    /**
     * Verifies action spacing controls the distance between adjacent actions.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionSpacingControlsActionGaps() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(),
                    new RXSpeedDialAction("First", new Region()),
                    new RXSpeedDialAction("Second", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);
            assertMainActionEdgeGap(dial, 8.0);
            assertAdjacentVisibleFabEdgeGap(dial, 8.0);

            dial.setActionSpacing(14.0);
            applyCssAndLayout(dial);
            assertMainActionEdgeGap(dial, 14.0);
            assertAdjacentVisibleFabEdgeGap(dial, 14.0);

            dial.setActionSpacing(-10.0);
            applyCssAndLayout(dial);
            assertMainActionEdgeGap(dial, 0.0);
            assertAdjacentVisibleFabEdgeGap(dial, 0.0);
        });
    }

    /**
     * Verifies label gap controls the distance between an action and its label.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void labelGapControlsActionLabelGap() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(),
                    new RXSpeedDialAction("Named", new Region()));
            dial.setAnimated(false);
            dial.setLabelMode(LabelMode.PERSISTENT);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertEquals(8.0, layoutHorizontalGap(visibleActionLabels(dial).get(0),
                    visibleActionFabs(dial).get(0)), 1.0);

            dial.setLabelGap(14.0);
            applyCssAndLayout(dial);
            assertEquals(14.0, layoutHorizontalGap(visibleActionLabels(dial).get(0),
                    visibleActionFabs(dial).get(0)), 1.0);

            dial.setLabelGap(Double.NaN);
            applyCssAndLayout(dial);
            assertEquals(0.0, layoutHorizontalGap(visibleActionLabels(dial).get(0),
                    visibleActionFabs(dial).get(0)), 1.0);
        });
    }

    /**
     * Verifies all directions place actions on the requested side.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionCellsLayoutInAllDirections() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(),
                    new RXSpeedDialAction("One", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            assertActionSide(dial, Direction.UP);
            assertActionSide(dial, Direction.DOWN);
            assertActionSide(dial, Direction.LEFT);
            assertActionSide(dial, Direction.RIGHT);
        });
    }

    /**
     * Verifies horizontal directions place labels off the action axis.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void horizontalDirectionLabelsUseVerticalSide() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(),
                    new RXSpeedDialAction("Long horizontal action label", new Region()));
            dial.setAnimated(false);
            dial.setLabelMode(LabelMode.PERSISTENT);
            attachAndApplyCss(dial);
            dial.open();

            dial.setDirection(Direction.RIGHT);
            applyCssAndLayout(dial);
            RXFloatingActionButton rightFab = visibleActionFabs(dial).get(0);
            Label rightLabel = visibleActionLabels(dial).get(0);
            assertEquals(centerX(rightFab), centerX(rightLabel), 1.0);
            assertTrue(centerY(rightLabel) > centerY(rightFab));

            dial.setDirection(Direction.LEFT);
            applyCssAndLayout(dial);
            RXFloatingActionButton leftFab = visibleActionFabs(dial).get(0);
            Label leftLabel = visibleActionLabels(dial).get(0);
            assertEquals(centerX(leftFab), centerX(leftLabel), 1.0);
            assertTrue(centerY(leftLabel) < centerY(leftFab));
        });
    }

    /**
     * Verifies secondary action fire invokes the handler and closes by default.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionFireClosesByDefaultWithActionReason() throws Exception {
        runOnFx(() -> {
            List<String> calls = new ArrayList<>();
            RXSpeedDialAction action =
                    new RXSpeedDialAction("Run", new Region(), event -> calls.add("handler"));
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));

            dial.open();
            applyCssAndLayout(dial);
            visibleActionFabs(dial).get(0).fire();

            assertEquals(List.of("handler"), calls);
            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.ACTION), reasons);
        });
    }

    /**
     * Verifies hover-triggered action close does not immediately reopen from
     * the focused action FAB.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverActionCloseDoesNotImmediatelyReopenFromActionFocus() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setOpenTrigger(OpenTrigger.HOVER);
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            RXFloatingActionButton actionFab = visibleActionFabs(dial).get(0);
            actionFab.requestFocus();
            actionFab.fire();

            assertFalse(dial.isShowing());
            dialRef[0] = dial;
        });
        runOnFx(() -> {
        });
        runOnFx(() -> assertFalse(dialRef[0].isShowing()));
    }

    /**
     * Verifies closeOnAction=false keeps the dial open.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionFireCanKeepDialOpen() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Run", new Region());
            action.setCloseOnAction(false);
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            dial.open();
            applyCssAndLayout(dial);
            visibleActionFabs(dial).get(0).fire();

            assertTrue(dial.isShowing());
        });
    }

    /**
     * Verifies a throwing action handler still closes the dial.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void throwingActionHandlerStillCloses() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Run", new Region(), event -> {
                throw new IllegalStateException("boom");
            });
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            dial.open();
            applyCssAndLayout(dial);

            assertThrows(IllegalStateException.class, () -> visibleActionFabs(dial).get(0).fire());
            assertFalse(dial.isShowing());
        });
    }

    /**
     * Verifies Escape closes the dial with the Escape reason.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void escapeClosesDial() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));

            dial.open();
            KeyEvent escape = keyPressed(KeyCode.ESCAPE);
            dial.fireEvent(escape);

            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.ESCAPE), reasons);
        });
    }

    /**
     * Verifies a vetoed Escape close does not move focus to the main FAB.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void vetoedEscapeDoesNotRefocusMainFab() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        RXFloatingActionButton[] actionFabRef = new RXFloatingActionButton[1];
        List<RXSpeedDial.CloseReason> requests = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setOnCloseRequest(event -> event.consume());
            dial.addEventHandler(RXSpeedDialEvent.CLOSE_REQUEST,
                    event -> requests.add(event.getCloseReason()));
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            RXFloatingActionButton actionFab = visibleActionFabs(dial).get(0);
            actionFab.requestFocus();
            dialRef[0] = dial;
            actionFabRef[0] = actionFab;
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            RXFloatingActionButton actionFab = actionFabRef[0];
            assertSame(actionFab, dial.getScene().getFocusOwner());

            actionFab.fireEvent(keyPressed(KeyCode.ESCAPE));

            assertTrue(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.ESCAPE), requests);
            assertSame(actionFab, dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies Escape from an action closes and refocuses the main FAB.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionEscapeClosesAndRefocusesMainFab() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        RXFloatingActionButton[] mainFabRef = new RXFloatingActionButton[1];
        RXFloatingActionButton[] actionFabRef = new RXFloatingActionButton[1];
        List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            RXFloatingActionButton actionFab = visibleActionFabs(dial).get(0);
            actionFab.requestFocus();
            dialRef[0] = dial;
            mainFabRef[0] = mainFab(dial);
            actionFabRef[0] = actionFab;
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertSame(actionFabRef[0], dial.getScene().getFocusOwner());

            actionFabRef[0].fireEvent(keyPressed(KeyCode.ESCAPE));

            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.ESCAPE), reasons);
            assertSame(mainFabRef[0], dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies hover trigger opens and mouse exit closes the dial.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverTriggerOpensAndMouseExitCloses() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setOpenTrigger(OpenTrigger.HOVER);
            attachAndApplyCss(dial);

            dial.fireEvent(mouse(MouseEvent.MOUSE_ENTERED, 130.0, 130.0));
            assertTrue(dial.isShowing());

            dial.fireEvent(mouse(MouseEvent.MOUSE_EXITED, 130.0, 130.0));
            assertFalse(dial.isShowing());
        });
    }

    /**
     * Verifies focus transfer inside a hover-triggered dial does not close it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverFocusTransferWithinDialKeepsOpen() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        RXFloatingActionButton[] actionFabRef = new RXFloatingActionButton[1];

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setOpenTrigger(OpenTrigger.HOVER);
            showInStage(dial);
            mainFab(dial).requestFocus();
            dialRef[0] = dial;
        });
        runOnFx(() -> {
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertTrue(dial.isShowing());
            applyCssAndLayout(dial);
            RXFloatingActionButton actionFab = visibleActionFabs(dial).get(0);
            actionFab.requestFocus();
            actionFabRef[0] = actionFab;
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertTrue(dial.isShowing());
            assertSame(actionFabRef[0], dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies focus leaving the dial closes with the focus-lost reason.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusLossClosesWhenEnabled() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        Button[] outsideRef = new Button[1];
        List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));
            Button outside = new Button("outside");
            showInStage(dial, outside);
            dial.open();
            applyCssAndLayout(dial);
            RXFloatingActionButton mainFab = mainFab(dial);
            mainFab.requestFocus();
            assertSame(mainFab, dial.getScene().getFocusOwner());
            outside.requestFocus();
            dialRef[0] = dial;
            outsideRef[0] = outside;
        });
        runOnFx(() -> {
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertSame(outsideRef[0], dial.getScene().getFocusOwner());
            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.FOCUS_LOST), reasons);
        });
    }

    /**
     * Verifies closeOnFocusLoss=false keeps the dial open after focus leaves.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusLossCanBeDisabled() throws Exception {
        RXSpeedDial[] dialRef = new RXSpeedDial[1];
        Button[] outsideRef = new Button[1];
        List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();

        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setCloseOnFocusLoss(false);
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));
            Button outside = new Button("outside");
            showInStage(dial, outside);
            dial.open();
            applyCssAndLayout(dial);
            RXFloatingActionButton mainFab = mainFab(dial);
            mainFab.requestFocus();
            assertSame(mainFab, dial.getScene().getFocusOwner());
            outside.requestFocus();
            dialRef[0] = dial;
            outsideRef[0] = outside;
        });
        runOnFx(() -> {
        });
        runOnFx(() -> {
            RXSpeedDial dial = dialRef[0];
            assertSame(outsideRef[0], dial.getScene().getFocusOwner());
            assertTrue(dial.isShowing());
            assertTrue(reasons.isEmpty());
        });
    }

    /**
     * Verifies outside scene clicks close when enabled.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void outsideClickClosesWhenEnabled() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));

            dial.open();
            dial.getScene().getRoot().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 1.0, 1.0));

            assertFalse(dial.isShowing());
            assertEquals(List.of(RXSpeedDial.CloseReason.CLICK_OUTSIDE), reasons);

            dial.open();
            dial.setCloseOnClickOutside(false);
            dial.getScene().getRoot().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 1.0, 1.0));
            assertTrue(dial.isShowing());
        });
    }

    /**
     * Verifies presses inside the dial are not treated as outside clicks.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void insideClickDoesNotClose() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            List<RXSpeedDial.CloseReason> reasons = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.HIDDEN, event -> reasons.add(event.getCloseReason()));

            dial.open();
            applyCssAndLayout(dial);
            visibleActionFabs(dial).get(0).fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 130.0, 82.0));
            mainFab(dial).fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 130.0, 130.0));

            assertTrue(dial.isShowing());
            assertTrue(reasons.isEmpty());
        });
    }

    /**
     * Verifies the outside-click filter is detached from a previous scene.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void oldSceneClickDoesNotCloseAfterSceneMove() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            StackPane oldRoot = attachAndApplyCss(dial);
            Scene oldScene = oldRoot.getScene();

            oldRoot.getChildren().clear();
            StackPane newRoot = new StackPane(dial);
            new Scene(newRoot, 260.0, 260.0);
            newRoot.applyCss();
            newRoot.layout();
            dial.open();
            applyCssAndLayout(dial);

            oldScene.getRoot().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 1.0, 1.0));

            assertTrue(dial.isShowing());
        });
    }

    /**
     * Verifies the keyboard scene filter is detached from a previous scene.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void oldSceneKeyDoesNotCloseAfterSceneMove() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            StackPane oldRoot = showInStage(dial);
            Scene oldScene = oldRoot.getScene();

            oldRoot.getChildren().clear();
            StackPane newRoot = new StackPane(dial);
            Scene newScene = new Scene(newRoot, 260.0, 260.0);
            stage.setScene(newScene);
            stage.show();
            newRoot.applyCss();
            newRoot.layout();
            dial.open();
            applyCssAndLayout(dial);
            mainFab(dial).requestFocus();

            oldScene.getRoot().fireEvent(keyPressed(KeyCode.ESCAPE));

            assertTrue(dial.isShowing());
        });
    }

    /**
     * Verifies scene detach settles an in-flight animation.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void sceneDetachSettlesRunningAnimation() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimationDuration(Duration.seconds(5.0));
            StackPane root = attachAndApplyCss(dial);
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.open();
            root.getChildren().clear();

            assertTrue(dial.isShowing());
            assertTrue(actionsLayer(dial).isVisible());
            assertEquals(List.of("RX_SPEED_DIAL_SHOWING:null", "RX_SPEED_DIAL_SHOWN:null"), log);
        });
    }

    /**
     * Verifies disposing the skin removes the outside-click scene filter.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposedSkinDetachesSceneFilter() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);

            dial.open();
            applyCssAndLayout(dial);
            dial.skinProperty().set(null);
            assertEquals(0, dial.getChildrenUnmodifiable().size());
            dial.getScene().getRoot().fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 1.0, 1.0));
            dial.fireEvent(keyPressed(KeyCode.ESCAPE));

            assertTrue(dial.isShowing());
        });
    }

    /**
     * Verifies skin disposal settles an in-flight close animation.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeSettlesRunningCloseAnimation() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            dial.setAnimated(true);
            dial.setAnimationDuration(Duration.seconds(5.0));
            List<String> log = new ArrayList<>();
            dial.addEventHandler(RXSpeedDialEvent.ANY, event -> log.add(eventSignature(event)));

            dial.close();
            dial.skinProperty().set(null);

            assertFalse(dial.isShowing());
            assertEquals(List.of("RX_SPEED_DIAL_CLOSE_REQUEST:TOGGLE", "RX_SPEED_DIAL_HIDING:TOGGLE",
                    "RX_SPEED_DIAL_HIDDEN:TOGGLE"), log);
        });
    }

    /**
     * Verifies the control footprint follows only the main FAB.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void footprintComesFromMainFabOnly() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region());
            attachAndApplyCss(dial);

            RXFloatingActionButton mainFab = mainFab(dial);
            double prefWidth = dial.prefWidth(-1);
            double prefHeight = dial.prefHeight(-1);
            assertEquals(mainFab.prefWidth(-1), prefWidth, 0.5);
            assertEquals(mainFab.prefHeight(-1), prefHeight, 0.5);
            assertEquals(prefWidth, dial.minWidth(-1), EPSILON);
            assertEquals(prefHeight, dial.minHeight(-1), EPSILON);
            assertEquals(prefWidth, dial.maxWidth(-1), EPSILON);
            assertEquals(prefHeight, dial.maxHeight(-1), EPSILON);

            dial.getActions().addAll(
                    new RXSpeedDialAction("One", new Region()),
                    new RXSpeedDialAction("Two", new Region()),
                    new RXSpeedDialAction("Three", new Region()));
            applyCssAndLayout(dial);

            assertEquals(prefWidth, dial.prefWidth(-1), EPSILON);
            assertEquals(prefHeight, dial.prefHeight(-1), EPSILON);

            dial.setPadding(new Insets(3.0, 5.0, 7.0, 11.0));
            applyCssAndLayout(dial);
            assertEquals(mainFab.prefWidth(-1) + 16.0, dial.prefWidth(-1), 0.5);
            assertEquals(mainFab.prefHeight(-1) + 10.0, dial.prefHeight(-1), 0.5);
        });
    }

    private static StackPane attachAndApplyCss(RXSpeedDial dial) {
        StackPane root = new StackPane(dial);
        new Scene(root, 260.0, 260.0);
        root.applyCss();
        root.applyCss();
        root.layout();
        return root;
    }

    private StackPane showInStage(Node... nodes) {
        StackPane root = new StackPane(nodes);
        stage = new Stage();
        stage.setScene(new Scene(root, 260.0, 260.0));
        stage.show();
        stage.requestFocus();
        root.applyCss();
        root.layout();
        return root;
    }

    private static void applyCssAndLayout(RXSpeedDial dial) {
        Parent root = dial.getScene().getRoot();
        root.applyCss();
        root.applyCss();
        root.layout();
    }

    private static List<Node> actionCells(RXSpeedDial dial) {
        return actionsLayer(dial).getChildrenUnmodifiable().stream().toList();
    }

    private static List<Node> visibleActionCells(RXSpeedDial dial) {
        return actionCells(dial).stream()
                .filter(Node::isVisible)
                .toList();
    }

    private static List<RXFloatingActionButton> visibleActionFabs(RXSpeedDial dial) {
        return visibleActionCells(dial).stream()
                .map(RXSpeedDialTest::actionFab)
                .toList();
    }

    private static List<Label> visibleActionLabels(RXSpeedDial dial) {
        return actionLabels(dial).stream()
                .filter(Node::isVisible)
                .toList();
    }

    private static List<Label> actionLabels(RXSpeedDial dial) {
        return actionCells(dial).stream()
                .flatMap(cell -> ((Parent) cell).getChildrenUnmodifiable().stream())
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .toList();
    }

    private static RXFloatingActionButton actionFab(Node actionCell) {
        Parent parent = (Parent) actionCell;
        return parent.getChildrenUnmodifiable().stream()
                .filter(RXFloatingActionButton.class::isInstance)
                .map(RXFloatingActionButton.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static RXFloatingActionButton mainFab(RXSpeedDial dial) {
        return dial.lookupAll(".rx-fab").stream()
                .filter(RXFloatingActionButton.class::isInstance)
                .map(RXFloatingActionButton.class::cast)
                .filter(fab -> fab.getSize() == RXFloatingActionButton.Size.STANDARD)
                .findFirst()
                .orElseThrow();
    }

    private static StackPane iconMorph(RXSpeedDial dial) {
        return dial.lookupAll(".icon-morph").stream()
                .filter(StackPane.class::isInstance)
                .map(StackPane.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static Pane actionsLayer(RXSpeedDial dial) {
        return dial.lookupAll(".actions").stream()
                .filter(Pane.class::isInstance)
                .map(Pane.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static Color iconFill(Region icon) {
        Background background = icon.getBackground();
        return (Color) background.getFills().get(0).getFill();
    }

    private static KeyEvent keyPressed(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static MouseEvent mouse(EventType<MouseEvent> eventType, double sceneX, double sceneY) {
        return new MouseEvent(eventType, sceneX, sceneY, sceneX, sceneY,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                true, false, false, null);
    }

    private static String eventSignature(RXSpeedDialEvent event) {
        return event.getEventType().getName() + ":" + event.getCloseReason();
    }

    private static void assertAdjacentVisibleCellGap(RXSpeedDial dial, double expectedGap) {
        List<Double> centers = visibleActionCells(dial).stream()
                .map(RXSpeedDialTest::centerY)
                .sorted()
                .toList();
        assertEquals(expectedGap, centers.get(1) - centers.get(0), 1.0);
    }

    private static void assertMainActionEdgeGap(RXSpeedDial dial, double expectedGap) {
        Bounds mainBounds = sceneLayoutBounds(mainFab(dial));
        Bounds actionBounds = sceneLayoutBounds(visibleActionFabs(dial).get(0));
        assertEquals(expectedGap, mainBounds.getMinY() - actionBounds.getMaxY(), 1.0);
    }

    private static void assertAdjacentVisibleFabEdgeGap(RXSpeedDial dial, double expectedGap) {
        List<Bounds> bounds = visibleActionFabs(dial).stream()
                .map(RXSpeedDialTest::sceneLayoutBounds)
                .sorted((left, right) -> Double.compare(left.getMinY(), right.getMinY()))
                .toList();
        assertEquals(expectedGap, bounds.get(1).getMinY() - bounds.get(0).getMaxY(), 1.0);
    }

    private static void assertActionSide(RXSpeedDial dial, Direction direction) {
        dial.setDirection(direction);
        applyCssAndLayout(dial);

        Node cell = visibleActionCells(dial).get(0);
        double mainX = centerX(mainFab(dial));
        double mainY = centerY(mainFab(dial));
        double actionX = centerX(cell);
        double actionY = centerY(cell);

        switch (direction) {
            case UP -> assertTrue(actionY < mainY);
            case DOWN -> assertTrue(actionY > mainY);
            case LEFT -> assertTrue(actionX < mainX);
            case RIGHT -> assertTrue(actionX > mainX);
        }
    }

    private static double centerX(Node node) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return (bounds.getMinX() + bounds.getMaxX()) / 2.0;
    }

    private static double centerY(Node node) {
        Bounds bounds = sceneBounds(node);
        return (bounds.getMinY() + bounds.getMaxY()) / 2.0;
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    private static Bounds sceneLayoutBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    private static double layoutHorizontalGap(Region left, Region right) {
        return right.getLayoutX() - (left.getLayoutX() + left.getWidth());
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
