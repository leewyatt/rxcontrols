package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Side-by-side reproduction for the "complex content jitters while animating" claim:
 * a JavaFX {@link Accordion} and an {@link RXAccordion} host the same body (wrapping
 * text, a form, a grid of buttons) at the same width and the same animation duration.
 *
 * <p>Each body root counts its own {@code layoutChildren} passes and the number of
 * distinct heights it was given; the counters reset when "Reset counters" is pressed.
 * The native skin resizes the body on every animation frame, so its counters climb
 * during a toggle; the RX body keeps its natural height and is only revealed. Use the
 * "slow motion" check box to watch wrapped text reflow on the left.</p>
 */
public class RXAccordionJitterComparison extends Application {

    private final CountingBody nativeBody = new CountingBody();
    private final CountingBody rxBody = new CountingBody();
    private final Label nativeStats = new Label();
    private final Label rxStats = new Label();

    @Override
    public void start(Stage stage) {
        TitledPane nativePane = new TitledPane("Native TitledPane", nativeBody);
        nativePane.setExpanded(false);
        Accordion nativeAccordion = new Accordion(new TitledPane("Native other", new Label("other")), nativePane);

        RXTitledPane rxPane = new RXTitledPane("RXTitledPane", rxBody);
        RXAccordion rxAccordion = new RXAccordion(new RXTitledPane("RX other", new Label("other")), rxPane);

        CheckBox slow = new CheckBox("Slow motion (RX 2 s; native animation length is fixed by its skin)");
        slow.selectedProperty().addListener((obs, old, value) ->
                rxPane.setAnimationDuration(Duration.millis(value ? 2000 : 350)));
        rxPane.setAnimationDuration(Duration.millis(350));

        Button reset = new Button("Reset counters");
        reset.setOnAction(e -> {
            nativeBody.reset();
            rxBody.reset();
            refresh();
        });
        Button refresh = new Button("Refresh numbers");
        refresh.setOnAction(e -> refresh());

        VBox left = column("JavaFX Accordion", nativeAccordion, nativeStats);
        VBox right = column("RXAccordion", rxAccordion, rxStats);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox columns = new HBox(24.0, left, right);
        VBox root = new VBox(12.0, new HBox(12.0, reset, refresh, slow), columns);
        root.setPadding(new Insets(16.0));

        stage.setScene(new Scene(root, 980, 640));
        stage.setTitle("Accordion jitter comparison");
        stage.show();
        refresh();
    }

    private static VBox column(String title, Node accordion, Label stats) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-weight: bold;");
        return new VBox(8.0, heading, stats, accordion);
    }

    private void refresh() {
        nativeStats.setText(nativeBody.describe());
        rxStats.setText(rxBody.describe());
    }

    /**
     * Body with wrapping text, a form and a button grid that counts its layout passes
     * and the distinct heights it is resized to.
     */
    private static final class CountingBody extends VBox {

        private int layouts;
        private int heights;
        private double lastHeight = -1;

        CountingBody() {
            super(8.0);
            Label text = new Label("This paragraph wraps. When the body is squeezed every frame the "
                    + "lines reflow, and the form below is compressed, which reads as jitter.");
            text.setWrapText(true);
            GridPane form = new GridPane();
            form.setHgap(8.0);
            form.setVgap(6.0);
            form.addRow(0, new Label("Name"), new TextField());
            form.addRow(1, new Label("Email"), new TextField());
            GridPane grid = new GridPane();
            grid.setHgap(4.0);
            grid.setVgap(4.0);
            for (int i = 0; i < 12; i++) {
                grid.add(new Button("B" + i), i % 4, i / 4);
            }
            getChildren().addAll(text, form, grid);
        }

        @Override
        protected void layoutChildren() {
            layouts++;
            if (getHeight() != lastHeight) {
                heights++;
                lastHeight = getHeight();
            }
            super.layoutChildren();
        }

        void reset() {
            layouts = 0;
            heights = 0;
        }

        String describe() {
            return "layout passes: " + layouts + "   distinct heights: " + heights;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
