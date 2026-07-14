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

    private static final String PLUS_ICON = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    private static final String ARCHIVE_ICON =
            "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23"
                    + "C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27z"
                    + "M12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z";

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
        archiveAction = action("Archive", ARCHIVE_ICON);
        pinAction = action("Pin",
                "M16 9V4l1 0c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1l1 0v5"
                        + "c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z");
        commentAction = action("Comment",
                "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z");
        deleteAction = action("Delete",
                "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");

        speedDial = new RXSpeedDial(icon(PLUS_ICON),
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
        iconPair.getItems().setAll("Plus / Close", "Star / Settings", "Mail / Archive");
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

        ComboBox<RXSpeedDial.LabelPlacement> labelPlacement = new ComboBox<>();
        labelPlacement.getItems().setAll(RXSpeedDial.LabelPlacement.values());
        labelPlacement.setValue(speedDial.getLabelPlacement());
        labelPlacement.setMaxWidth(Double.MAX_VALUE);
        labelPlacement.valueProperty().bindBidirectional(speedDial.labelPlacementProperty());

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
                row("Label placement", labelPlacement),
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
        RXSpeedDialAction action = action("Extra " + index, PLUS_ICON);
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
            case "Star / Settings" -> {
                speedDial.setIcon(icon("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63"
                        + " 2 9.24l5.46 4.73L5.82 21z"));
                speedDial.setOpenIcon(icon("M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98"
                        + "l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1"
                        + "c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4"
                        + "c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1"
                        + "c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65"
                        + "c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64"
                        + "l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65"
                        + "c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98"
                        + "l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65z"
                        + "M12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z"));
            }
            case "Mail / Archive" -> {
                speedDial.setIcon(icon("M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6"
                        + "c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"));
                speedDial.setOpenIcon(icon(ARCHIVE_ICON));
            }
            default -> {
                speedDial.setIcon(icon(PLUS_ICON));
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
