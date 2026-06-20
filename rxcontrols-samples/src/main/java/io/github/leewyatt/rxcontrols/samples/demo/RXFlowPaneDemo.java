package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Side-by-side demo of {@link RXFlowPane} versus JavaFX {@link FlowPane}, in both
 * orientations.
 *
 * <p>The horizontal row keeps the same seven equal cards at the same gaps and width, so
 * three fit per row and the seventh lands alone on the last row. The JavaFX
 * {@code FlowPane} (alignment {@code CENTER}) centers that lone card by itself;
 * {@code RXFlowPane} (alignment {@code TOP_CENTER} + rowHalignment {@code LEFT}) centers
 * the whole block once and keeps the last card at the block's left edge — under the first
 * card.</p>
 *
 * <p>The vertical row transposes the same idea: at a fixed height three cards fit per
 * column and the seventh lands alone in the last column. {@code FlowPane}
 * ({@code VERTICAL}, alignment {@code CENTER}) centers that lone card vertically;
 * {@code RXFlowPane} ({@code VERTICAL}, alignment {@code CENTER_LEFT} + columnValignment
 * {@code TOP}) keeps it at the block's top edge — beside the first card.</p>
 */
public class RXFlowPaneDemo extends Application {

    private static final int CARD_COUNT = 7;
    private static final double CARD_WIDTH = 120.0;
    private static final double CARD_HEIGHT = 64.0;
    private static final double GAP = 12.0;
    // Wide enough that three cards fit per row with room left over, so the centered
    // content block shows a visible margin on both sides.
    private static final double PANE_WIDTH = 464.0;
    // Tall enough that three cards fit per column with room left over, so the centered
    // content block shows a visible margin above and below.
    private static final double PANE_HEIGHT = 264.0;

    private static final String[] PALETTE = {
            "#6366f1", "#0ea5e9", "#14b8a6", "#22c55e", "#eab308", "#f97316", "#ef4444"
    };

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(createHeader(), horizontalComparison(), verticalComparison());
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 1040.0, 880.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-flow-pane-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXFlowPane Demo");
        primaryStage.show();
    }

    private Region horizontalComparison() {
        RXFlowPane rxFlow = new RXFlowPane(cards());
        rxFlow.setHgap(GAP);
        rxFlow.setVgap(GAP);
        rxFlow.setAlignment(Pos.CENTER);
        rxFlow.setRowHalignment(HPos.LEFT);
        fixWidth(rxFlow);

        FlowPane jfxFlow = new FlowPane(GAP, GAP, cards());
        jfxFlow.setAlignment(Pos.CENTER);
        fixWidth(jfxFlow);

        return section("Horizontal flow — the last row",
                panel("RXFlowPane", "alignment = TOP_CENTER · rowHalignment = LEFT",
                        "Block centered once — the lone last card stays at the block's left edge.",
                        rxFlow),
                panel("JavaFX FlowPane", "alignment = CENTER",
                        "Each row is centered on its own — the lone last card is pushed to the middle.",
                        jfxFlow));
    }

    private Region verticalComparison() {
        RXFlowPane rxFlow = new RXFlowPane(Orientation.VERTICAL, cards());
        rxFlow.setHgap(GAP);
        rxFlow.setVgap(GAP);
        rxFlow.setAlignment(Pos.CENTER);
        rxFlow.setColumnValignment(VPos.TOP);
        fixHeight(rxFlow);

        FlowPane jfxFlow = new FlowPane(Orientation.VERTICAL, GAP, GAP, cards());
        jfxFlow.setAlignment(Pos.CENTER);
        fixHeight(jfxFlow);

        return section("Vertical flow — the last column",
                panel("RXFlowPane", "orientation = VERTICAL · alignment = CENTER_LEFT · columnValignment = TOP",
                        "Block centered once — the lone last card stays at the block's top edge.",
                        rxFlow),
                panel("JavaFX FlowPane", "orientation = VERTICAL · alignment = CENTER",
                        "Each column is centered on its own — the lone last card is pushed to the middle.",
                        jfxFlow));
    }

    private Region createHeader() {
        Label title = new Label("Last row & last column, done right");
        title.getStyleClass().add("demo-title");
        Label subtitle = new Label(
                "Same seven cards, same gaps — only the container differs, in both directions");
        subtitle.getStyleClass().add("demo-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("demo-header");
        return header;
    }

    private Region section(String title, Region rxPanel, Region jfxPanel) {
        Label sectionLabel = new Label(title);
        sectionLabel.getStyleClass().add("section-title");

        HBox comparison = new HBox(28.0, rxPanel, jfxPanel);
        comparison.setAlignment(Pos.TOP_CENTER);
        comparison.getStyleClass().add("comparison");

        VBox box = new VBox(8.0, sectionLabel, comparison);
        box.getStyleClass().add("section");
        return box;
    }

    private Region panel(String name, String config, String note, Pane pane) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("panel-name");
        Label configLabel = new Label(config);
        configLabel.getStyleClass().add("panel-config");

        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add("panel-note");
        noteLabel.setWrapText(true);
        noteLabel.setMaxWidth(PANE_WIDTH);

        VBox panel = new VBox(8.0, nameLabel, configLabel, pane, noteLabel);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private void fixWidth(Region pane) {
        pane.setMinWidth(PANE_WIDTH);
        pane.setPrefWidth(PANE_WIDTH);
        pane.setMaxWidth(PANE_WIDTH);
        pane.getStyleClass().add("flow");
    }

    private void fixHeight(Region pane) {
        pane.setMinHeight(PANE_HEIGHT);
        pane.setPrefHeight(PANE_HEIGHT);
        pane.setMaxHeight(PANE_HEIGHT);
        pane.getStyleClass().add("flow");
    }

    private Region[] cards() {
        Region[] cards = new Region[CARD_COUNT];
        for (int i = 0; i < CARD_COUNT; i++) {
            cards[i] = createCard(i);
        }
        return cards;
    }

    private Region createCard(int index) {
        Label label = new Label(String.valueOf(index + 1));
        label.getStyleClass().add("card-label");

        StackPane card = new StackPane(label);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: " + PALETTE[index % PALETTE.length] + ";");
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMinSize(CARD_WIDTH, CARD_HEIGHT);
        return card;
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
