package io.github.leewyatt.rxcontrols.samples;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import io.github.leewyatt.rxcontrols.utils.TreeShowingProperty;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Interactive demo for {@link TreeShowingProperty}.
 */
public class TreeShowingPropertyDemo extends Application {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== Window geometry ====================

    private static final double CONTROL_W = 380;
    private static final double CONTROL_H = 700;
    private static final double PREVIEW_W = 480;
    private static final double PREVIEW_H = 280;
    private static final double WINDOW_GAP = 24;

    // ==================== Icons ====================

    private static final String EYE_OPEN_SVG =
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5"
                    + "c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24"
                    + " 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";

    private static final String EYE_CLOSED_SVG =
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89"
                    + " 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16"
                    + "C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12"
                    + "c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73"
                    + " 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3"
                    + " .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5"
                    + " 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    private static final Color ICON_ON = Color.web("#2a7a3e");
    private static final Color ICON_OFF = Color.web("#888888");
    private static final double ICON_SCALE = 0.7;

    private static final String LINK_SVG =
            "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7"
                    + "c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1"
                    + " 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z";

    private static final String LINK_OFF_SVG =
            "M14.39 11l2.06 2.06c.84-.42 1.66-.49 2.45-.49h.1c.94 0 2.94 0 5 .43V11h-9.61z"
                    + "M2 4.27l3.11 3.11C3.29 8.12 2 9.91 2 12c0 2.76 2.24 5 5 5h4v-1.9H7"
                    + "c-1.71 0-3.1-1.39-3.1-3.1 0-1.59 1.21-2.9 2.76-3.07L8.73 11H8v2h2.73"
                    + "L13 15.27V17h1.73l4.01 4 1.41-1.41L3.41 2.86 2 4.27z";

    private static final String PARENT_CHIP_SVG =
            "M4 2 h12 a2 2 0 0 1 2 2 v12 a2 2 0 0 1 -2 2 h-12 a2 2 0 0 1 -2 -2 v-12 a2 2 0 0 1 2 -2 Z";

    private static final Color CHIP_INNER = Color.web("#d49b00");
    private static final Color CHIP_ALTERNATE = Color.web("#2978a0");
    private static final Color CHIP_DETACHED = Color.web("#bdbdbd");

    // ==================== Visual tree (preview stage) ====================

    private Label target;
    private StackPane inner;
    private StackPane outer;
    private Pane alternateContainer;
    private Stage previewStage;

    // ==================== Property under test ====================

    private TreeShowingProperty prop;

    // ==================== Log ====================

    private final ObservableList<String> logEntries = FXCollections.observableArrayList();

    /**
     * Starts the demo application.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        buildPreview();

        prop = new TreeShowingProperty(target);
        prop.addListener((obs, oldV, newV) -> log("treeShowing → " + newV));

        primaryStage.setScene(buildControlPanel());
        primaryStage.setTitle("TreeShowingProperty Test");

        layoutWindows(primaryStage);

        previewStage.show();
        primaryStage.show();

        log("initial treeShowing = " + prop.get());
    }

    // ==================== Preview window ====================

    private void buildPreview() {
        target = new Label("TARGET");
        target.setStyle("-fx-background-color: white;"
                + " -fx-padding: 16 22 16 22;"
                + " -fx-font-size: 18;"
                + " -fx-font-weight: bold;"
                + " -fx-text-fill: #2b2f3a;"
                + " -fx-border-color: #2b2f3a;"
                + " -fx-border-width: 2;");

        inner = new StackPane(target);
        inner.setStyle("-fx-background-color: #ffe9a8;"
                + " -fx-border-color: #d49b00;"
                + " -fx-border-width: 3;");
        inner.setPadding(new Insets(20));

        outer = new StackPane(inner);
        outer.setStyle("-fx-background-color: #ffd1d1;"
                + " -fx-border-color: #c0392b;"
                + " -fx-border-width: 3;");
        outer.setPadding(new Insets(20));

        alternateContainer = new StackPane();
        alternateContainer.setStyle("-fx-background-color: #d2eef9;"
                + " -fx-border-color: #2978a0;"
                + " -fx-border-width: 3;"
                + " -fx-min-width: 140;");

        HBox root = new HBox(20, outer, alternateContainer);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #fafafa;");

        previewStage = new Stage();
        previewStage.setTitle("Preview — observed tree");
        previewStage.setScene(new Scene(root, PREVIEW_W, PREVIEW_H));
    }

    // ==================== Control panel ====================

    private Scene buildControlPanel() {
        Label statusTitle = new Label("Status");
        statusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        Circle dot = new Circle(10);
        dot.fillProperty().bind(Bindings.when(prop)
                .then(Color.LIMEGREEN)
                .otherwise(Color.CRIMSON));
        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.format("treeShowing = %s", prop));
        valueLabel.setStyle("-fx-font-family: 'Menlo'; -fx-font-size: 13;");
        HBox status = new HBox(8, dot, valueLabel);
        status.setAlignment(Pos.CENTER_LEFT);

        // ==================== Stage buttons ====================
        Button btnTogglePreview = toggleButton(
                "Toggle preview window show/hide",
                previewStage.showingProperty(),
                () -> {
                    if (previewStage.isShowing()) {
                        log("→ previewStage.hide()");
                        previewStage.hide();
                    } else {
                        log("→ previewStage.show()");
                        previewStage.show();
                    }
                });

        // ==================== Visibility buttons ====================
        Button btnToggleTarget = visibilityToggleButton("target", target);
        Button btnToggleInner = visibilityToggleButton("inner pane", inner);
        Button btnToggleOuter = visibilityToggleButton("outer pane", outer);

        // ==================== Topology buttons ====================
        Button btnReparent = reparentButton();
        Button btnAttach = attachToggleButton();

        // ==================== Lifecycle / static API ====================
        Button btnDispose = new Button("Dispose explicit-owner prop");
        btnDispose.setOnAction(e -> {
            prop.dispose();
            dot.fillProperty().unbind();
            dot.setFill(Color.GRAY);
            valueLabel.textProperty().unbind();
            valueLabel.setText("treeShowing = disposed");
            btnDispose.setDisable(true);
            log("→ prop.dispose(); prop.get() = " + prop.get());
        });

        Button btnVerifyCache = new Button("Verify TreeShowingProperty.of() cache");
        btnVerifyCache.setOnAction(e -> {
            ReadOnlyBooleanProperty a = TreeShowingProperty.of(target);
            ReadOnlyBooleanProperty b = TreeShowingProperty.of(target);
            log("→ TreeShowingProperty.of(target): same instance = " + (a == b)
                    + ", value = " + a.get());
        });

        Button btnOneShot = new Button("Static isTreeShowing(target)");
        btnOneShot.setOnAction(e -> {
            boolean snapshot = TreeShowingProperty.isTreeShowing(target);
            log("→ isTreeShowing() one-shot = " + snapshot);
        });

        for (Button b : new Button[] {btnTogglePreview, btnToggleTarget, btnToggleInner, btnToggleOuter,
                btnReparent, btnAttach, btnDispose, btnVerifyCache, btnOneShot}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }

        VBox controls = new VBox(6,
                sectionLabel("Preview window"), btnTogglePreview,
                sectionLabel("Visibility"), btnToggleTarget, btnToggleInner, btnToggleOuter,
                sectionLabel("Topology"), btnReparent, btnAttach,
                sectionLabel("Lifecycle / static API"), btnDispose, btnVerifyCache, btnOneShot
        );

        ListView<String> logView = new ListView<>(logEntries);
        logView.setPrefHeight(220);

        VBox root = new VBox(12,
                statusTitle, status,
                sectionDivider(),
                controls,
                sectionDivider(),
                sectionLabel("Event log"),
                logView
        );
        root.setPadding(new Insets(14));
        return new Scene(root, CONTROL_W, CONTROL_H);
    }

    private Button visibilityToggleButton(String name, Node node) {
        return toggleButton("Toggle " + name + " visible", node.visibleProperty(), () -> {
            boolean now = !node.isVisible();
            node.setVisible(now);
            log("→ " + name + ".setVisible(" + now + ")");
        });
    }

    private Button reparentButton() {
        Button b = new Button("Reparent target  inner ↔ alternate");
        SVGPath chip = new SVGPath();
        chip.setContent(PARENT_CHIP_SVG);
        chip.setScaleX(ICON_SCALE);
        chip.setScaleY(ICON_SCALE);

        Runnable refresh = () -> {
            Node parent = target.getParent();
            if (parent == inner) {
                chip.setFill(CHIP_INNER);
            } else if (parent == alternateContainer) {
                chip.setFill(CHIP_ALTERNATE);
            } else {
                chip.setFill(CHIP_DETACHED);
            }
        };
        target.parentProperty().addListener((obs, oldP, newP) -> refresh.run());
        refresh.run();

        b.setGraphic(chip);
        b.setOnAction(e -> {
            Node parent = target.getParent();
            if (parent == inner) {
                inner.getChildren().remove(target);
                alternateContainer.getChildren().add(target);
                log("→ reparented target: inner → alternate");
            } else if (parent == alternateContainer) {
                alternateContainer.getChildren().remove(target);
                inner.getChildren().add(target);
                log("→ reparented target: alternate → inner");
            } else {
                log("→ target is detached — click the link button first");
            }
        });
        return b;
    }

    private Button attachToggleButton() {
        Button b = new Button();
        SVGPath icon = new SVGPath();
        icon.setScaleX(ICON_SCALE);
        icon.setScaleY(ICON_SCALE);

        Runnable refresh = () -> {
            boolean attached = target.getParent() != null;
            if (attached) {
                icon.setContent(LINK_SVG);
                icon.setFill(ICON_ON);
                b.setText("Detach target (remove from scene)");
            } else {
                icon.setContent(LINK_OFF_SVG);
                icon.setFill(ICON_OFF);
                b.setText("Reattach target to inner");
            }
        };
        target.parentProperty().addListener((obs, oldP, newP) -> refresh.run());
        refresh.run();

        b.setGraphic(icon);
        b.setOnAction(e -> {
            Node parent = target.getParent();
            if (parent == inner) {
                inner.getChildren().remove(target);
                log("→ detached target from inner");
            } else if (parent == alternateContainer) {
                alternateContainer.getChildren().remove(target);
                log("→ detached target from alternate");
            } else {
                inner.getChildren().add(target);
                log("→ reattached target to inner");
            }
        });
        return b;
    }

    private Button toggleButton(String text, ObservableBooleanValue state, Runnable action) {
        Button b = new Button(text);
        SVGPath icon = new SVGPath();
        icon.setScaleX(ICON_SCALE);
        icon.setScaleY(ICON_SCALE);

        Runnable refresh = () -> {
            if (state.get()) {
                icon.setContent(EYE_OPEN_SVG);
                icon.setFill(ICON_ON);
            } else {
                icon.setContent(EYE_CLOSED_SVG);
                icon.setFill(ICON_OFF);
            }
        };
        state.addListener((obs, oldV, newV) -> refresh.run());
        refresh.run();

        b.setGraphic(icon);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: #555; -fx-padding: 6 0 0 0;");
        return l;
    }

    private Node sectionDivider() {
        Pane d = new Pane();
        d.setMinHeight(1);
        d.setStyle("-fx-background-color: #ddd;");
        return d;
    }

    private void layoutWindows(Stage primaryStage) {
        Rectangle2D b = Screen.getPrimary().getVisualBounds();
        double pairW = CONTROL_W + WINDOW_GAP + PREVIEW_W;
        double startX = b.getMinX() + (b.getWidth() - pairW) / 2.0;
        double primaryY = b.getMinY() + Math.max(0, (b.getHeight() - CONTROL_H) / 2.0);
        double previewY = primaryY + (CONTROL_H - PREVIEW_H) / 2.0;

        primaryStage.setX(startX);
        primaryStage.setY(primaryY);
        previewStage.setX(startX + CONTROL_W + WINDOW_GAP);
        previewStage.setY(previewY);
    }

    private void log(String msg) {
        logEntries.add(0, "[" + LocalTime.now().format(TIME_FORMATTER) + "] " + msg);
    }

    /**
     * Launches the demo application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
