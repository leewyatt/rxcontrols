package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import javafx.application.Application;
import javafx.scene.control.SelectionMode;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal sample application demonstrating {@link RXCascader} with single,
 * multiple, lazy-loaded, and forced-branch (empty-column placeholder)
 * configurations.
 *
 * <p>The value type is a small {@link Option} record (a stand-in for a backend
 * object carrying both id and label); the visible text comes from
 * the {@code converter} ({@code Option::label}), not from the item — items no longer
 * store text.
 *
 * <p>For the field-property explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderShowcase};
 * for lazy loading see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderLazyShowcase}.
 */
public class RXCascaderDemo extends Application {

    /**
     * Backend-style value carrying an id and a display label.
     *
     * @param id stable identifier (what you would send back)
     * @param label human-facing text (what the {@code converter} renders)
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
        VBox root = new VBox(16);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        RXCascader<Option> single = new RXCascader<>();
        single.setMaxWidth(Double.MAX_VALUE);
        single.setPromptText("Choose a city");
        single.setClearable(true);
        single.setConverter(LABEL_CONVERTER);
        single.setPathTextFactory(path -> String.join(" -> ", pathTexts(single.getConverter(), path)));
        List<RXCascaderItem<Option>> cities = sampleOptions();
        single.getRootItems().setAll(cities);
        // Real form-restore pattern: on reload you look the saved id back up in the
        // tree and select that actual node (not a new detached item), so the field
        // shows the full path and opening the popup reveals/highlights the selection.
        single.select(findById(cities, "berlin"));

        RXCascader<Option> multiple = new RXCascader<>();
        multiple.setMaxWidth(Double.MAX_VALUE);
        multiple.setPromptText("Choose multiple cities");
        multiple.setSelectionMode(SelectionMode.MULTIPLE);
        multiple.setClearable(true);
        multiple.setConverter(LABEL_CONVERTER);
        multiple.setPathTextFactory(path -> {
            List<String> texts = pathTexts(multiple.getConverter(), path);
            return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
        });
        multiple.getRootItems().setAll(sampleOptions());

        RXCascader<Option> lazy = new RXCascader<>();
        lazy.setMaxWidth(Double.MAX_VALUE);
        lazy.setPromptText("Lazy load children");
        lazy.setSelectionMode(SelectionMode.MULTIPLE);
        lazy.setClearable(true);
        lazy.setConverter(LABEL_CONVERTER);
        lazy.setChildrenLoader(item -> CompletableFuture.supplyAsync(() -> loadChildren(item)));
        lazy.getRootItems().setAll(lazyRoot());

        // Forced-branch placeholder: "Antarctica" is declared a branch
        // (leafHint=false) yet has no children, so activating it opens an empty
        // frontier column showing the emptyText. An ordinary childless node is a
        // terminal leaf and would open no column at all.
        RXCascader<Option> forced = new RXCascader<>();
        forced.setMaxWidth(Double.MAX_VALUE);
        forced.setPromptText("Forced-branch placeholder");
        forced.setClearable(true);
        forced.setConverter(LABEL_CONVERTER);
        forced.setEmptyText("No cities yet");
        forced.getRootItems().setAll(forcedBranchOptions());

        root.getChildren().setAll(
                new Label("Single selection"), single,
                new Label("Multiple selection"), multiple,
                new Label("Lazy loading"), lazy,
                new Label("Forced-branch placeholder (open \"Antarctica\")"), forced
        );

        Scene scene = new Scene(root, 560, 520);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXCascader Demo");
        primaryStage.show();
    }

    private static List<String> pathTexts(StringConverter<Option> converter, RXCascaderPath<Option> path) {
        return path.getValues().stream()
                .map(value -> displayText(converter, value))
                .toList();
    }

    private static String displayText(StringConverter<Option> converter, Option value) {
        if (value == null) {
            return "";
        }
        String text = converter == null ? String.valueOf(value) : converter.toString(value);
        return text == null ? "" : text;
    }

    private static List<RXCascaderItem<Option>> sampleOptions() {
        RXCascaderItem<Option> asia = item("asia", "Asia");
        RXCascaderItem<Option> europe = item("europe", "Europe");
        RXCascaderItem<Option> china = item("china", "China");
        RXCascaderItem<Option> japan = item("japan", "Japan");
        RXCascaderItem<Option> germany = item("germany", "Germany");
        RXCascaderItem<Option> berlin = item("berlin", "Berlin");
        RXCascaderItem<Option> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisable(true);

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

    private static RXCascaderItem<Option> findById(List<RXCascaderItem<Option>> items, String id) {
        for (RXCascaderItem<Option> item : items) {
            if (id.equals(item.getValue().id())) {
                return item;
            }
            RXCascaderItem<Option> found = findById(item.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<RXCascaderItem<Option>> forcedBranchOptions() {
        // "Africa" is a normal branch with children; "Antarctica" is forced to a
        // branch (leafHint=false) but has none, so opening it shows the empty-column
        // placeholder instead of ending the cascade as a leaf.
        RXCascaderItem<Option> africa = item("africa", "Africa");
        africa.getChildren().setAll(List.of(
                leaf("cairo", "Cairo"),
                leaf("lagos", "Lagos")
        ));

        RXCascaderItem<Option> antarctica = item("antarctica", "Antarctica");
        antarctica.setLeafHint(false);

        return List.of(africa, antarctica);
    }

    private static List<RXCascaderItem<Option>> lazyRoot() {
        // Lazy mode: with a loader set, an unloaded node defaults to a branch
        // (Default B), so no flags are needed to make it expandable.
        return List.of(item("source", "Remote Source"));
    }

    private static List<RXCascaderItem<Option>> loadChildren(RXCascaderItem<Option> item) {
        sleep();
        Option value = item.getValue();
        if ("source".equals(value.id())) {
            // Unloaded branches by Default B; expanding them loads again.
            return List.of(item("group-a", "Group A"), item("group-b", "Group B"));
        }
        // Known leaves must be marked in lazy mode, otherwise they would be
        // treated as unloaded branches and trigger a useless load on expand.
        return List.of(
                leaf(value.id() + "-1", value.label() + " 1"),
                leaf(value.id() + "-2", value.label() + " 2")
        );
    }

    private static RXCascaderItem<Option> item(String id, String label) {
        return new RXCascaderItem<>(new Option(id, label));
    }

    private static RXCascaderItem<Option> leaf(String id, String label) {
        RXCascaderItem<Option> leaf = item(id, label);
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
