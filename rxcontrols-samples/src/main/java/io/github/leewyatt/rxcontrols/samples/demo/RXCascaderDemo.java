package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal sample application demonstrating {@link RXCascader} with single,
 * multiple, and lazy-loaded configurations.
 *
 * <p>For the full property-driven explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderShowcase}.
 */
public class RXCascaderDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        RXCascader<String> single = new RXCascader<>();
        single.setPromptText("Choose a city");
        single.setClearable(true);
        single.setPathTextFactory(path -> String.join(" -> ", path.getTexts()));
        single.getRootItems().setAll(sampleOptions());

        RXCascader<String> multiple = new RXCascader<>();
        multiple.setPromptText("Choose multiple cities");
        multiple.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        multiple.setClearable(true);
        multiple.setPathTextFactory(path -> path.getLeaf().getText());
        multiple.getRootItems().setAll(sampleOptions());

        RXCascader<String> lazy = new RXCascader<>();
        lazy.setPromptText("Lazy load children");
        lazy.setSelectionMode(RXCascaderSelectionMode.MULTIPLE);
        lazy.setClearable(true);
        lazy.setChildrenLoader(item -> CompletableFuture.supplyAsync(() -> loadChildren(item)));
        lazy.getRootItems().setAll(lazyRoot());

        root.getChildren().setAll(
                new Label("Single selection"), single,
                new Label("Multiple selection"), multiple,
                new Label("Lazy loading"), lazy
        );

        Scene scene = new Scene(root, 560, 420);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXCascader Demo");
        primaryStage.show();
    }

    private static List<RXCascaderItem<String>> sampleOptions() {
        RXCascaderItem<String> asia = item("asia", "Asia");
        RXCascaderItem<String> europe = item("europe", "Europe");
        RXCascaderItem<String> china = item("china", "China");
        RXCascaderItem<String> japan = item("japan", "Japan");
        RXCascaderItem<String> germany = item("germany", "Germany");
        RXCascaderItem<String> berlin = item("berlin", "Berlin");
        RXCascaderItem<String> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisabled(true);

        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity
        ));
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")
        ));
        germany.getChildren().add(berlin);
        asia.getChildren().setAll(List.of(china, japan));
        europe.getChildren().add(germany);
        return List.of(asia, europe);
    }

    private static List<RXCascaderItem<String>> lazyRoot() {
        // Lazy mode: with a loader set, an unloaded node defaults to a branch
        // (Default B), so no flags are needed to make it expandable.
        return List.of(item("source", "Remote Source"));
    }

    private static List<RXCascaderItem<String>> loadChildren(RXCascaderItem<String> item) {
        sleep();
        if ("source".equals(item.getValue())) {
            // Unloaded branches by Default B; expanding them loads again.
            return List.of(item("group-a", "Group A"), item("group-b", "Group B"));
        }
        // Known leaves must be marked in lazy mode, otherwise they would be
        // treated as unloaded branches and trigger a useless load on expand.
        return List.of(
                leaf(item.getValue() + "-1", item.getText() + " 1"),
                leaf(item.getValue() + "-2", item.getText() + " 2")
        );
    }

    private static RXCascaderItem<String> item(String value, String text) {
        return new RXCascaderItem<>(value, text);
    }

    private static RXCascaderItem<String> leaf(String value, String text) {
        RXCascaderItem<String> leaf = item(value, text);
        leaf.setLeafHint(true);
        return leaf;
    }

    private static void sleep() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
