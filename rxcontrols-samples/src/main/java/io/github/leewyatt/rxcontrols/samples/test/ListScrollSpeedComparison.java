package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Side-by-side wheel and trackpad comparison of a JavaFX {@link ListView} and an
 * {@link RXListView}: both hold the numbers 1 to 1000 in rows of the same fixed height.
 *
 * <p>The toolbar switches the RXListView between MOMENTUM, TARGET and no smooth
 * scrolling, and sets its wheel multiplier; the JavaFX list always scrolls natively.
 * Each list shows its scroll offset in pixels and rows, so one wheel notch or one
 * trackpad swipe over each list can be compared by number as well as by feel. "Top"
 * scrolls both lists back to the first row.</p>
 */
public class ListScrollSpeedComparison extends Application {

    private static final double ROW_HEIGHT = 32.0;
    private static final int ITEM_COUNT = 1000;

    private enum Mode {
        MOMENTUM("Smooth: MOMENTUM"),
        TARGET("Smooth: TARGET"),
        OFF("Smooth scrolling off");

        private final String text;

        Mode(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final ListView<Integer> fxList = new ListView<>(items());
    private final RXListView<Integer> rxList = new RXListView<>(items());
    private final Label fxOffset = new Label();
    private final Label rxOffset = new Label();

    @Override
    public void start(Stage stage) {
        fxList.setFixedCellSize(ROW_HEIGHT);
        rxList.setFixedCellSize(ROW_HEIGHT);

        VBox root = new VBox(12, toolbar(), scrollControls(), new Label("Both lists use " + (int) ROW_HEIGHT
                + " px rows. Wait for motion to stop before comparing offsets or pressing Top."),
                lists());
        root.setPadding(new Insets(16));
        VBox.setVgrow(root.getChildren().get(3), Priority.ALWAYS);

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                fxOffset.setText(offsetText(fxOffset()));
                rxOffset.setText(offsetText(rxOffset()));
            }
        }.start();

        stage.setTitle("ListView vs RXListView: wheel scrolling");
        stage.setScene(new Scene(root, 860, 720));
        stage.show();
    }

    // ==================== Toolbar ====================

    private Node toolbar() {
        ChoiceBox<Mode> mode = new ChoiceBox<>(FXCollections.observableArrayList(Mode.values()));
        mode.valueProperty().addListener((obs, old, value) -> {
            rxList.setSmoothScrolling(value != Mode.OFF);
            if (value == Mode.MOMENTUM) {
                rxList.setSmoothScrollMode(SmoothScrollMode.MOMENTUM);
            } else if (value == Mode.TARGET) {
                rxList.setSmoothScrollMode(SmoothScrollMode.TARGET);
            }
        });
        mode.setValue(Mode.MOMENTUM);

        Slider multiplier = new Slider(0.1, 4.0, rxList.getWheelMultiplier());
        multiplier.setPrefWidth(220);
        rxList.wheelMultiplierProperty().bind(multiplier.valueProperty());
        Label multiplierValue = new Label();
        multiplierValue.setMinWidth(48);
        multiplierValue.textProperty().bind(multiplier.valueProperty().asString("%.2fx"));

        HBox presets = new HBox(4);
        for (double value : new double[]{0.25, 0.5, 1.0, 2.0}) {
            Button preset = new Button(value + "x");
            preset.setOnAction(event -> multiplier.setValue(value));
            presets.getChildren().add(preset);
        }

        Button top = new Button("Top");
        top.setOnAction(event -> {
            fxList.scrollTo(0);
            rxList.scrollTo(0);
        });

        HBox toolbar = new HBox(10, new Label("RXListView"), mode, new Label("wheelMultiplier"), multiplier,
                multiplierValue, presets, top);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    // ==================== Lists ====================

    private Node scrollControls() {
        Spinner<Integer> delta = new Spinner<>(1, 1000, 40, 10);
        delta.setEditable(true);
        delta.setPrefWidth(90);
        Button scroll = new Button("Scroll both once");
        scroll.setOnAction(event -> {
            delta.increment(0); // Commit a value typed into the editor.
            double pixels = delta.getValue();
            fireScroll(fxList.lookup(".virtual-flow"), pixels);
            fireScroll(rxList.lookup(".viewport"), pixels);
        });
        HBox controls = new HBox(10, new Label("Wheel delta (px)"), delta, scroll,
                new Label("Same synthetic downward scroll for both lists"));
        controls.setAlignment(Pos.CENTER_LEFT);
        return controls;
    }

    private static void fireScroll(Node target, double pixels) {
        if (target == null) {
            return;
        }
        Event.fireEvent(target, new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0, false, false, false, false, false, false,
                0, -pixels, 0, -pixels,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.LINES, -3, 0, null));
    }

    private Node lists() {
        HBox lists = new HBox(16, column("JavaFX ListView (native)", fxOffset, fxList),
                column("RXListView", rxOffset, rxList));
        for (Node child : lists.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }
        return lists;
    }

    private static VBox column(String title, Label offset, Region list) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-weight: bold;");
        offset.setStyle("-fx-font-family: monospace;");
        VBox column = new VBox(6, heading, offset, list);
        column.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(list, Priority.ALWAYS);
        return column;
    }

    private static ObservableList<Integer> items() {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 1; i <= ITEM_COUNT; i++) {
            items.add(i);
        }
        return items;
    }

    // ==================== Offsets ====================

    private static String offsetText(double offset) {
        return String.format("offset %8.1f px = %6.2f rows", offset, offset / ROW_HEIGHT);
    }

    private double fxOffset() {
        Node node = fxList.lookup(".virtual-flow");
        if (node instanceof VirtualFlow<?> flow && flow.getFirstVisibleCell() != null) {
            return offsetOf(flow.getFirstVisibleCell(), flow);
        }
        return 0.0;
    }

    private double rxOffset() {
        Node viewport = rxList.lookup(".viewport");
        if (viewport == null) {
            return 0.0;
        }
        for (Node node : rxList.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.isVisible() && !cell.isEmpty() && cell.getIndex() >= 0) {
                // Fixed-height rows: any shown row gives the same offset.
                return offsetOf(cell, viewport);
            }
        }
        return 0.0;
    }

    private static double offsetOf(IndexedCell<?> cell, Node viewport) {
        double viewportTop = viewport.localToScene(0, 0).getY();
        double cellTop = cell.localToScene(0, 0).getY();
        return cell.getIndex() * ROW_HEIGHT - (cellTop - viewportTop);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
