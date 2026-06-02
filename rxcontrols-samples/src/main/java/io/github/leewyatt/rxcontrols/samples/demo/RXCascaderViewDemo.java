package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.StringJoiner;

/**
 * Minimal sample showing {@link RXCascaderView} used on its own — an inline
 * multi-column cascader with no input field and no popup. The view is dropped
 * straight into the scene graph and the application listens to its checked
 * paths to react to selection.
 *
 * <p>For the full property-driven explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderViewShowcase}.
 * To use the popup/input-field wrapper instead, see {@link RXCascaderDemo}.
 */
public class RXCascaderViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        view.getRootItems().setAll(sampleOptions());

        Label result = new Label("(nothing checked)");
        result.setWrapText(true);
        view.getCheckedPaths().addListener(
                (ListChangeListener<RXCascaderPath<String>>) change -> result.setText(describe(view)));

        VBox root = new VBox(12.0,
                new Label("Inline RXCascaderView (no input field, no popup):"),
                view,
                result);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        Scene scene = new Scene(root, 620, 380);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXCascaderView Demo");
        primaryStage.show();
    }

    private static String describe(RXCascaderView<String> view) {
        if (view.getCheckedPaths().isEmpty()) {
            return "(nothing checked)";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (RXCascaderPath<String> path : view.getCheckedPaths()) {
            joiner.add("- " + String.join(" / ", path.getTexts()));
        }
        return joiner.toString();
    }

    private static List<RXCascaderItem<String>> sampleOptions() {
        RXCascaderItem<String> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisabled(true);

        RXCascaderItem<String> china = item("china", "China");
        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity));

        RXCascaderItem<String> japan = item("japan", "Japan");
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")));

        RXCascaderItem<String> asia = item("asia", "Asia");
        asia.getChildren().setAll(List.of(china, japan));

        RXCascaderItem<String> germany = item("germany", "Germany");
        germany.getChildren().setAll(List.of(
                item("berlin", "Berlin"),
                item("munich", "Munich")));

        RXCascaderItem<String> europe = item("europe", "Europe");
        europe.getChildren().setAll(List.of(germany));

        return List.of(asia, europe);
    }

    private static RXCascaderItem<String> item(String value, String text) {
        return new RXCascaderItem<>(value, text);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
