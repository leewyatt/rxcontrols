package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.scene.control.SelectionMode;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.StringJoiner;

/**
 * Minimal sample showing {@link RXCascaderView} used on its own — an inline
 * multi-column cascader with no input field and no popup. The view is dropped
 * straight into the scene graph and the application listens to its checked
 * paths to react to selection.
 *
 * <p>The value type is an {@link Option} record; visible text comes from
 * the {@code converter} ({@code Option::label}), and path text is joined from the
 * per-node texts in a local helper.
 *
 * <p>For the full property-driven explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderViewShowcase}.
 * To use the popup/input-field wrapper instead, see {@link RXCascaderDemo}.
 */
public class RXCascaderViewDemo extends Application {

    /**
     * Backend-style value carrying an id and a display label.
     *
     * @param id stable identifier
     * @param label human-facing text rendered by the {@code converter}
     */
    public record Option(String id, String label) {
    }

    private static final StringConverter<Option> LABEL_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Option option) {
            return option == null ? "" : option.label();
        }

        @Override
        public Option fromString(String text) {
            return null;
        }
    };

    @Override
    public void start(Stage primaryStage) {
        RXCascaderView<Option> view = new RXCascaderView<>();
        view.setSelectionMode(SelectionMode.MULTIPLE);
        view.setConverter(LABEL_CONVERTER);
        view.getRootItems().setAll(sampleOptions());

        Label result = new Label("(nothing checked)");
        result.setWrapText(true);
        view.getCheckedPaths().addListener(
                (ListChangeListener<RXCascaderPath<Option>>) change -> result.setText(describe(view)));

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

    private static String describe(RXCascaderView<Option> view) {
        if (view.getCheckedPaths().isEmpty()) {
            return "(nothing checked)";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (RXCascaderPath<Option> path : view.getCheckedPaths()) {
            joiner.add("- " + pathText(view.getConverter(), path));
        }
        return joiner.toString();
    }

    private static String pathText(StringConverter<Option> converter, RXCascaderPath<Option> path) {
        StringJoiner joiner = new StringJoiner(" / ");
        for (Option value : path.getValues()) {
            joiner.add(displayText(converter, value));
        }
        return joiner.toString();
    }

    private static String displayText(StringConverter<Option> converter, Option value) {
        if (value == null) {
            return "";
        }
        String text = converter == null ? String.valueOf(value) : converter.toString(value);
        return text == null ? "" : text;
    }

    private static List<RXCascaderItem<Option>> sampleOptions() {
        RXCascaderItem<Option> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisable(true);

        RXCascaderItem<Option> china = item("china", "China");
        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity));

        RXCascaderItem<Option> japan = item("japan", "Japan");
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")));

        RXCascaderItem<Option> asia = item("asia", "Asia");
        asia.getChildren().setAll(List.of(china, japan));

        RXCascaderItem<Option> germany = item("germany", "Germany");
        germany.getChildren().setAll(List.of(
                item("berlin", "Berlin"),
                item("munich", "Munich")));

        RXCascaderItem<Option> europe = item("europe", "Europe");
        europe.getChildren().setAll(List.of(germany));

        return List.of(asia, europe);
    }

    private static RXCascaderItem<Option> item(String id, String label) {
        return new RXCascaderItem<>(new Option(id, label));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
