package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.theme.RXTheme;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demonstrates {@link RXTheme}: toggling RxControls between its built-in light and
 * dark looks. The checkbox calls {@link RXTheme#install(Scene, RXTheme.Variant)}
 * with {@code DARK} / {@code LIGHT}; the dark overlay is self-contained (no host
 * theme needed) and turns the whole showroom — including controls still using raw
 * Modena {@code -fx-*} like the cascader — dark.
 */
public class RXThemeDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setCenter(buildShowroom());

        CheckBox dark = new CheckBox("Dark mode");
        dark.selectedProperty().addListener((obs, was, on) ->
                RXTheme.install(root.getScene(), on ? RXTheme.Variant.DARK : RXTheme.Variant.LIGHT));
        HBox toolbar = new HBox(12, new Label("RxControls theme:"), dark);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        root.setTop(toolbar);

        primaryStage.setScene(new Scene(root, 720, 560));
        primaryStage.setTitle("RxControls light / dark");
        primaryStage.show();
    }

    private VBox buildShowroom() {
        FlowPane buttons = new FlowPane(12, 12,
                new RXFillButton("Fill Button"), new RXButton("Ripple Button"));

        RXTimelineView timeline = new RXTimelineView(
                item("Created", RXTimelineItem.Type.PRIMARY),
                item("Shipped", RXTimelineItem.Type.SUCCESS),
                item("Delayed", RXTimelineItem.Type.WARNING),
                item("Failed", RXTimelineItem.Type.DANGER),
                item("Note", RXTimelineItem.Type.INFO));

        RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                new RXSegmentedItem<>("day", "Day"),
                new RXSegmentedItem<>("week", "Week"),
                new RXSegmentedItem<>("month", "Month"));

        RXTextView textView = new RXTextView(
                "RXTextView body text — readable in both light and dark.");

        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> asia = new RXCascaderItem<>("Asia");
        asia.getChildren().addAll(new RXCascaderItem<>("China"), new RXCascaderItem<>("Japan"));
        RXCascaderItem<String> europe = new RXCascaderItem<>("Europe");
        europe.getChildren().addAll(new RXCascaderItem<>("France"), new RXCascaderItem<>("Germany"));
        cascader.getRootItems().addAll(asia, europe);

        VBox showroom = new VBox(18,
                new Label("Buttons"), buttons,
                new Label("Segmented"), segmented,
                new Label("Text view"), textView,
                new Label("Timeline"), timeline,
                new Label("Cascader (open to see the popup follow)"), cascader);
        showroom.setPadding(new Insets(14));
        return showroom;
    }

    private static RXTimelineItem item(String title, RXTimelineItem.Type type) {
        RXTimelineItem item = new RXTimelineItem(title);
        item.setType(type);
        return item;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
