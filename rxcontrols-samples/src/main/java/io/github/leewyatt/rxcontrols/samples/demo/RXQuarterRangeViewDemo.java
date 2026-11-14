package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXQuarterRange;
import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXQuarterRangeView} used on its own, without fields or a
 * popup: a range of quarters with the dates it covers.
 *
 * <p>Every option of the control is in {@code RXQuarterRangeViewShowcase}.
 */
public class RXQuarterRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearQuarter thisQuarter = RXYearQuarter.from(LocalDate.now());
        RXQuarterRangeView quarters = new RXQuarterRangeView(
                RXQuarterRange.of(thisQuarter, thisQuarter.plusQuarters(2)));

        Label period = new Label();
        period.textProperty().bind(Bindings.createStringBinding(
                () -> quarters.getValue() == null ? "Pick the first and the last quarter"
                        : "From " + quarters.getValue().toDateRange().start()
                        + " to " + quarters.getValue().toDateRange().end(),
                quarters.valueProperty()));

        VBox root = new VBox(12.0, quarters, period);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 560, 210));
        stage.setTitle("RXQuarterRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
