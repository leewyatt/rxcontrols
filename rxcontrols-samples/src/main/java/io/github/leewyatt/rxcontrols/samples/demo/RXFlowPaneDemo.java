package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
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
 * Side-by-side demo of {@link RXFlowPane} versus JavaFX {@link FlowPane}.
 *
 * <p>Both panes hold the same seven equal-width cards at the same gaps and width,
 * so three fit per row and the seventh lands alone on the last row. The JavaFX
 * {@code FlowPane} (alignment {@code CENTER}) centers that lone card by itself;
 * {@code RXFlowPane} (contentAlignment {@code TOP_CENTER} + lineAlignment
 * {@code LEFT}) centers the whole block once and keeps the last card at the
 * block's left edge — aligned under the first card.</p>
 */
public class RXFlowPaneDemo extends Application {

    private static final int CARD_COUNT = 7;
    private static final double CARD_WIDTH = 120.0;
    private static final double CARD_HEIGHT = 64.0;
    private static final double GAP = 12.0;
    // Wide enough that three cards fit per row with room left over, so the
    // centered content block shows a visible margin on both sides.
    private static final double PANE_WIDTH = 464.0;

    private static final String[] PALETTE = {
            "#6366f1", "#0ea5e9", "#14b8a6", "#22c55e", "#eab308", "#f97316", "#ef4444"
    };

    @Override
    public void start(Stage primaryStage) {
        RXFlowPane rxFlow = new RXFlowPane(cards());
        rxFlow.setHgap(GAP);
        rxFlow.setVgap(GAP);
        rxFlow.setContentAlignment(Pos.TOP_CENTER);
        rxFlow.setLineAlignment(HPos.LEFT);
        fixWidth(rxFlow);

        FlowPane jfxFlow = new FlowPane(GAP, GAP, cards());
        jfxFlow.setAlignment(Pos.CENTER);
        fixWidth(jfxFlow);

        HBox comparison = new HBox(28.0,
                panel("RXFlowPane", "contentAlignment = TOP_CENTER · lineAlignment = LEFT",
                        "Block centered once — the lone last card stays at the block's left edge.",
                        rxFlow),
                panel("JavaFX FlowPane", "alignment = CENTER",
                        "Each row is centered on its own — the lone last card is pushed to the middle.",
                        jfxFlow));
        comparison.setAlignment(Pos.TOP_CENTER);
        comparison.getStyleClass().add("comparison");

        VBox root = new VBox(createHeader(), comparison);
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 1040.0, 460.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-flow-pane-demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXFlowPane Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Last row, done right");
        title.getStyleClass().add("demo-title");
        Label subtitle = new Label(
                "Same seven cards, same width and gaps — only the container differs");
        subtitle.getStyleClass().add("demo-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("demo-header");
        return header;
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
