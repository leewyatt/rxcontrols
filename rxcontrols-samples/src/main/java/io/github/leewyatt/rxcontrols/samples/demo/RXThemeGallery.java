package io.github.leewyatt.rxcontrols.samples.demo;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.samples.demo.ThemeGalleryCards.NamedControl;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFX;
import io.github.leewyatt.rxcontrols.theme.RXTheme;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Theme gallery: a scrollable, alphabetised grid of every color-relevant RxControl
 * (each in a labelled card) with a theme switcher to compare the built-in light/dark
 * looks ({@link RXTheme}) against the AtlantaFX themes ({@link RXAtlantaFX}). Pure
 * layout containers (RXBox / RXRow / RXCol / RXMasonryPane) are omitted; every other
 * control is built by {@link ThemeGalleryCards}.
 */
public class RXThemeGallery extends Application {

    private record ThemeOption(String label, Consumer<Scene> apply) {
    }

    private final List<ThemeOption> themes = List.of(
            new ThemeOption("RxControls — Light", scene -> rxControls(scene, RXTheme.Variant.LIGHT)),
            new ThemeOption("RxControls — Dark", scene -> rxControls(scene, RXTheme.Variant.DARK)),
            new ThemeOption("AtlantaFX — Primer Light", scene -> atlanta(scene, new PrimerLight())),
            new ThemeOption("AtlantaFX — Primer Dark", scene -> atlanta(scene, new PrimerDark())),
            new ThemeOption("AtlantaFX — Nord Light", scene -> atlanta(scene, new NordLight())),
            new ThemeOption("AtlantaFX — Nord Dark", scene -> atlanta(scene, new NordDark())),
            new ThemeOption("AtlantaFX — Cupertino Light", scene -> atlanta(scene, new CupertinoLight())),
            new ThemeOption("AtlantaFX — Cupertino Dark", scene -> atlanta(scene, new CupertinoDark())),
            new ThemeOption("AtlantaFX — Dracula", scene -> atlanta(scene, new Dracula())));

    @Override
    public void start(Stage primaryStage) {
        ScrollPane scroll = new ScrollPane(buildGallery());
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar(root));
        root.setCenter(scroll);

        Scene scene = new Scene(root, 1100, 820);
        themes.get(0).apply().accept(scene); // start on RxControls light

        primaryStage.setScene(scene);
        primaryStage.setTitle("RxControls Theme Gallery");
        primaryStage.show();
    }

    // ==================== Theme switching ====================

    private static void rxControls(Scene scene, RXTheme.Variant variant) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        RXAtlantaFX.uninstall(scene);
        RXTheme.install(scene, variant);
    }

    private static void atlanta(Scene scene, Theme theme) {
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        RXTheme.install(scene, RXTheme.Variant.LIGHT); // ensure the dark overlay is off
        RXAtlantaFX.install(scene);
    }

    private HBox buildToolbar(BorderPane root) {
        ComboBox<ThemeOption> picker = new ComboBox<>();
        picker.getItems().setAll(themes);
        picker.setValue(themes.get(0));
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(ThemeOption option) {
                return option == null ? "" : option.label();
            }

            @Override
            public ThemeOption fromString(String string) {
                return null;
            }
        });
        picker.valueProperty().addListener((obs, old, option) -> {
            if (option != null && root.getScene() != null) {
                option.apply().accept(root.getScene());
            }
        });

        HBox toolbar = new HBox(12, new Label("Theme:"), picker);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        return toolbar;
    }

    // ==================== Gallery ====================

    private FlowPane buildGallery() {
        FlowPane grid = new FlowPane(18, 18);
        grid.setPadding(new Insets(18));
        for (NamedControl control : ThemeGalleryCards.cards()) {
            grid.getChildren().add(card(control));
        }
        return grid;
    }

    private static Node card(NamedControl control) {
        Label name = new Label(control.name());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        StackPane stage = new StackPane(control.node());
        stage.setAlignment(Pos.CENTER);
        stage.setMinHeight(130);
        VBox.setVgrow(stage, Priority.ALWAYS);

        Separator divider = new Separator();
        VBox card = new VBox(10, name, divider, stage);
        card.setPadding(new Insets(16));
        card.setPrefWidth(360);
        card.setMinWidth(360);
        card.setMinHeight(Region.USE_PREF_SIZE);
        // Theme-neutral chrome: a translucent grey border reads on light and dark
        // surfaces alike, with a transparent fill so the themed background shows.
        card.setStyle("-fx-border-color: rgba(128, 128, 128, 0.35);"
                + " -fx-border-radius: 10; -fx-border-width: 1; -fx-background-radius: 10;");
        return card;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
