package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXFloatingActionButton;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import io.github.leewyatt.rxcontrols.samples.demo.RXSpeedDialDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Showcase application for {@link RXSpeedDial}.
 *
 * <p>Exposes the main FAB size, direction, open trigger, label mode, close
 * policies, animation timing, and per-action visibility / disabled state. For a
 * minimal quick-actions sample see {@link RXSpeedDialDemo}.</p>
 */
public class RXSpeedDialShowcase extends RXShowcaseApplication {

    private static final String MAIN_FAB_SMALL = "main-fab-small";
    private static final String MAIN_FAB_LARGE = "main-fab-large";

    private RXSpeedDial speedDial;
    private RXSpeedDialAction archiveAction;
    private RXSpeedDialAction pinAction;
    private RXSpeedDialAction commentAction;
    private RXSpeedDialAction deleteAction;
    private final List<RXSpeedDialAction> extraActions = new ArrayList<>();
    private final StringProperty lastAction = new SimpleStringProperty("none");
    private final BooleanProperty commandPress = new SimpleBooleanProperty();
    private Node commandBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSpeedDial";
    }

    @Override
    protected String subtitle() {
        return "Floating action button with secondary quick actions";
    }

    @Override
    protected String windowTitle() {
        return "RXSpeedDial Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-speed-dial-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        archiveAction = action("Archive", "M4 5 H16 V8 H4 Z M6 8 H14 V16 H6 Z M8 11 H12");
        pinAction = action("Pin", "M7 3 H13 L11 8 L14 12 L10 11 L7 17 L8 11 L4 12 L7 8 Z");
        commentAction = action("Comment", "M4 4 H16 V13 H8 L4 17 Z");
        deleteAction = action("Delete", "M5 6 H15 M7 6 V16 M13 6 V16 M6 6 L7 18 H13 L14 6 M8 4 H12");

        speedDial = new RXSpeedDial(icon("M8 2 H11 V8 H17 V11 H11 V17 H8 V11 H2 V8 H8 Z"),
                archiveAction, pinAction, commentAction, deleteAction);
        speedDial.setLabelMode(RXSpeedDial.LabelMode.PERSISTENT);
        speedDial.setCloseOnClickOutside(false);
        speedDial.setCloseOnFocusLoss(false);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "last action: " + lastAction.get(), lastAction));

        StackPane surface = new StackPane();
        surface.getStyleClass().add("preview-surface");
        surface.getChildren().addAll(stateLabel, speedDial);
        StackPane.setAlignment(speedDial, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(speedDial, new Insets(0.0, 28.0, 28.0, 0.0));
        return surface;
    }

    @Override
    protected void configureScene(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (commandBox != null && event.getTarget() instanceof Node node
                    && isDescendantOf(node, commandBox)) {
                commandPress.set(true);
                Platform.runLater(() -> commandPress.set(false));
            }
        });
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Main FAB", buildFabGrid()),
                section("Dial behavior", buildBehaviorGrid()),
                section("Animation", buildAnimationGrid()),
                section("Actions", buildActionGrid()),
                section("Commands", buildCommandBox()));
    }

    // ==================== Sections ====================

    private Node buildFabGrid() {
        ComboBox<RXFloatingActionButton.Size> size = new ComboBox<>();
        size.getItems().setAll(RXFloatingActionButton.Size.values());
        size.setValue(RXFloatingActionButton.Size.STANDARD);
        size.setMaxWidth(Double.MAX_VALUE);
        size.valueProperty().addListener((observable, was, is) -> applyMainFabSize(is));

        ComboBox<String> iconPair = new ComboBox<>();
        iconPair.getItems().setAll("Plus / Close", "Star / Check", "Mail / Archive");
        iconPair.setValue("Plus / Close");
        iconPair.setMaxWidth(Double.MAX_VALUE);
        iconPair.valueProperty().addListener((observable, was, is) -> applyIconPair(is));

        CheckBox disabled = new CheckBox("Disabled");
        speedDial.disableProperty().bind(disabled.selectedProperty());

        return createGrid(
                row("Size", size),
                row("Icons", iconPair),
                row(disabled));
    }

    private Node buildBehaviorGrid() {
        ComboBox<RXSpeedDial.Direction> direction = new ComboBox<>();
        direction.getItems().setAll(RXSpeedDial.Direction.values());
        direction.setValue(speedDial.getDirection());
        direction.setMaxWidth(Double.MAX_VALUE);
        direction.valueProperty().bindBidirectional(speedDial.directionProperty());

        ComboBox<RXSpeedDial.OpenTrigger> trigger = new ComboBox<>();
        trigger.getItems().setAll(RXSpeedDial.OpenTrigger.values());
        trigger.setValue(speedDial.getOpenTrigger());
        trigger.setMaxWidth(Double.MAX_VALUE);
        trigger.valueProperty().bindBidirectional(speedDial.openTriggerProperty());

        ComboBox<RXSpeedDial.LabelMode> labels = new ComboBox<>();
        labels.getItems().setAll(RXSpeedDial.LabelMode.values());
        labels.setValue(speedDial.getLabelMode());
        labels.setMaxWidth(Double.MAX_VALUE);
        labels.valueProperty().bindBidirectional(speedDial.labelModeProperty());

        Slider actionSpacing = createSlider(0.0, 24.0, speedDial.getActionSpacing());
        speedDial.actionSpacingProperty().bind(actionSpacing.valueProperty());
        Label actionSpacingValue = createValueLabel(actionSpacing, "%.0f px");

        Slider labelGap = createSlider(0.0, 24.0, speedDial.getLabelGap());
        speedDial.labelGapProperty().bind(labelGap.valueProperty());
        Label labelGapValue = createValueLabel(labelGap, "%.0f px");

        CheckBox closeFocus = new CheckBox("Close on focus loss");
        closeFocus.setSelected(speedDial.isCloseOnFocusLoss());
        speedDial.closeOnFocusLossProperty().bind(closeFocus.selectedProperty().and(commandPress.not()));

        CheckBox closeOutside = new CheckBox("Close on outside click");
        closeOutside.setSelected(speedDial.isCloseOnClickOutside());
        speedDial.closeOnClickOutsideProperty().bind(closeOutside.selectedProperty().and(commandPress.not()));

        return createGrid(
                row("Direction", direction),
                row("Open trigger", trigger),
                row("Labels", labels),
                row("Action spacing", actionSpacing, actionSpacingValue),
                row("Label gap", labelGap, labelGapValue),
                row(closeFocus),
                row(closeOutside));
    }

    private Node buildAnimationGrid() {
        CheckBox animated = new CheckBox("Animated");
        animated.setSelected(speedDial.isAnimated());
        speedDial.animatedProperty().bind(animated.selectedProperty());

        Slider duration = createSlider(0.0, 800.0, speedDial.getAnimationDuration().toMillis());
        speedDial.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(duration.getValue()), duration.valueProperty()));
        Label durationValue = createValueLabel(duration, "%.0f ms");

        Slider stagger = createSlider(0.0, 150.0, speedDial.getStaggerDelay().toMillis());
        speedDial.staggerDelayProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(stagger.getValue()), stagger.valueProperty()));
        Label staggerValue = createValueLabel(stagger, "%.0f ms");

        return createGrid(
                row(animated),
                row("Duration", duration, durationValue),
                row("Stagger", stagger, staggerValue));
    }

    private Node buildActionGrid() {
        CheckBox archiveVisible = actionVisibleBox(archiveAction, "Archive visible");
        CheckBox pinDisabled = actionDisableBox(pinAction, "Pin disabled");
        CheckBox commentCloses = new CheckBox("Comment closes");
        commentCloses.setSelected(commentAction.isCloseOnAction());
        commentAction.closeOnActionProperty().bind(commentCloses.selectedProperty());
        CheckBox deleteVisible = actionVisibleBox(deleteAction, "Delete visible");
        Button addAction = new Button("Add action");
        addAction.setMaxWidth(Double.MAX_VALUE);
        addAction.setOnAction(event -> addExtraAction());
        Button removeAction = new Button("Remove added action");
        removeAction.setMaxWidth(Double.MAX_VALUE);
        removeAction.setOnAction(event -> removeExtraAction());
        HBox actionButtons = new HBox(8.0, addAction, removeAction);

        return createGrid(
                row(archiveVisible),
                row(pinDisabled),
                row(commentCloses),
                row(deleteVisible),
                row("Action list", actionButtons));
    }

    private Node buildCommandBox() {
        Button open = new Button("open()");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(event -> speedDial.open());
        Button close = new Button("close()");
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(event -> speedDial.close());
        Button toggle = new Button("toggle()");
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setOnAction(event -> speedDial.toggle());
        commandBox = new VBox(8.0, open, close, toggle);
        return commandBox;
    }

    // ==================== Preview helpers ====================

    private RXSpeedDialAction action(String text, String shape) {
        return new RXSpeedDialAction(text, icon(shape), event -> lastAction.set(text));
    }

    private void addExtraAction() {
        int index = extraActions.size() + 1;
        RXSpeedDialAction action = action("Extra " + index, "M4 9 H16 V11 H4 Z M9 4 H11 V16 H9 Z");
        action.setCloseOnAction(false);
        extraActions.add(action);
        speedDial.getActions().add(action);
    }

    private void removeExtraAction() {
        if (extraActions.isEmpty()) {
            return;
        }
        RXSpeedDialAction action = extraActions.remove(extraActions.size() - 1);
        speedDial.getActions().remove(action);
    }

    private void applyMainFabSize(RXFloatingActionButton.Size size) {
        speedDial.getStyleClass().removeAll(MAIN_FAB_SMALL, MAIN_FAB_LARGE);
        if (size == RXFloatingActionButton.Size.SMALL) {
            speedDial.getStyleClass().add(MAIN_FAB_SMALL);
        } else if (size == RXFloatingActionButton.Size.LARGE) {
            speedDial.getStyleClass().add(MAIN_FAB_LARGE);
        }
    }

    private void applyIconPair(String value) {
        switch (value) {
            case "Star / Check" -> {
                speedDial.setIcon(icon("M10 2 L12.4 7.2 L18 7.8 L13.8 11.4 L15 17 L10 14.1 L5 17 L6.2 11.4 L2 7.8 L7.6 7.2 Z"));
                speedDial.setOpenIcon(icon("M9 16.2 L4.8 12 L3.4 13.4 L9 19 L21 7 L19.6 5.6 Z"));
            }
            case "Mail / Archive" -> {
                speedDial.setIcon(icon("M3 5 H17 V15 H3 Z M3 5 L10 11 L17 5"));
                speedDial.setOpenIcon(icon("M4 5 H16 V8 H4 Z M6 8 H14 V16 H6 Z M8 11 H12"));
            }
            default -> {
                speedDial.setIcon(icon("M8 2 H11 V8 H17 V11 H11 V17 H8 V11 H2 V8 H8 Z"));
                speedDial.setOpenIcon(null);
            }
        }
    }

    private CheckBox actionVisibleBox(RXSpeedDialAction action, String text) {
        CheckBox box = new CheckBox(text);
        box.setSelected(action.isVisible());
        action.visibleProperty().bind(box.selectedProperty());
        return box;
    }

    private CheckBox actionDisableBox(RXSpeedDialAction action, String text) {
        CheckBox box = new CheckBox(text);
        box.setSelected(action.isDisable());
        action.disableProperty().bind(box.selectedProperty());
        return box;
    }

    private static Region icon(String shape) {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setStyle("-fx-shape: \"" + shape + "\";");
        icon.setMinSize(18.0, 18.0);
        icon.setPrefSize(18.0, 18.0);
        icon.setMaxSize(18.0, 18.0);
        return icon;
    }

    private static boolean isDescendantOf(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
