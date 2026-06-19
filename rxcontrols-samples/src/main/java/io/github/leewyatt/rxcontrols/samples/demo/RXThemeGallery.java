package io.github.leewyatt.rxcontrols.samples.demo;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXRadioToggleButton;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXToggleButton;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
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
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Theme gallery: a scrollable panel of the color-relevant RxControls with a theme
 * switcher to compare the built-in light/dark looks ({@link RXTheme}) against the
 * AtlantaFX themes ({@link RXAtlantaFX}). Pure layout containers (RXBox / RXRow /
 * RXCol / RXMasonryPane) and fixture-heavy controls (audio spectrum, lyric views,
 * carousel, drawer, image views) are intentionally omitted.
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

        Scene scene = new Scene(root, 920, 760);
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

    private VBox buildGallery() {
        VBox gallery = new VBox(22,
                section("Buttons",
                        new RXButton("Button"),
                        new RXFillButton("Fill Button"),
                        new RXLineButton("Line Button"),
                        new RXTransitionButton("Transition")),
                section("Toggles",
                        new RXToggleButton("Toggle"),
                        radioGroup()),
                section("Labels (decorative fill / line)",
                        new RXFillLabel("Fill Label"),
                        new RXLineLabel("Line Label")),
                section("Text inputs",
                        promptField(),
                        new RXPasswordField("secret"),
                        new RXFormattedNumberField(),
                        new RXIntegerField()),
                section("Selection",
                        new RXSegmentedControl<>(
                                new RXSegmentedItem<>("day", "Day"),
                                new RXSegmentedItem<>("week", "Week"),
                                new RXSegmentedItem<>("month", "Month")),
                        cascader()),
                section("Progress & indicators",
                        determinateCircular(),
                        new RXCircularProgressIndicator(-1),
                        new RXSegmentedProgressBar(0.6),
                        new RXSegmentedStepIndicator(4),
                        new RXSeekBar(0.5),
                        new RXWaveProgressIndicator(0.6)),
                section("Loading",
                        new RXBarSpinner(),
                        new RXDotPulse(),
                        new RXSkeleton(),
                        new RXSkeleton(RXSkeleton.Variant.TEXT)),
                section("Text display",
                        new RXTextView("RXTextView body text — readable in light and dark."),
                        new RXHighlightTextView("RXHighlightTextView highlights keywords here.", "highlights", "keywords"),
                        new RXTransitionLabel("Transition Label")),
                section("Avatar & digit",
                        new RXAvatar(),
                        new RXDigit(),
                        ripple()),
                section("Timeline", timeline()),
                section("Sidebar", sidebar()));
        gallery.setPadding(new Insets(4, 14, 24, 14));
        return gallery;
    }

    private static VBox section(String title, Node... nodes) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-weight: bold;");
        FlowPane row = new FlowPane(14, 14, nodes);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, heading, row);
        return box;
    }

    private static RXTextField promptField() {
        RXTextField field = new RXTextField();
        field.setPromptText("Text field");
        return field;
    }

    private static RXCircularProgressIndicator determinateCircular() {
        RXCircularProgressIndicator indicator = new RXCircularProgressIndicator(0.6);
        indicator.setPrefSize(48, 48);
        return indicator;
    }

    private static Node radioGroup() {
        ToggleGroup group = new ToggleGroup();
        RXRadioToggleButton a = new RXRadioToggleButton("One");
        RXRadioToggleButton b = new RXRadioToggleButton("Two");
        RXRadioToggleButton c = new RXRadioToggleButton("Three");
        a.setToggleGroup(group);
        b.setToggleGroup(group);
        c.setToggleGroup(group);
        a.setSelected(true);
        return new HBox(8, a, b, c);
    }

    private static RXCascader<String> cascader() {
        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> asia = new RXCascaderItem<>("Asia");
        asia.getChildren().addAll(new RXCascaderItem<>("China"), new RXCascaderItem<>("Japan"));
        RXCascaderItem<String> europe = new RXCascaderItem<>("Europe");
        europe.getChildren().addAll(new RXCascaderItem<>("France"), new RXCascaderItem<>("Germany"));
        cascader.getRootItems().addAll(asia, europe);
        return cascader;
    }

    private static RXRipplePane ripple() {
        Label label = new Label("Ripple area");
        label.setPadding(new Insets(16, 24, 16, 24));
        return new RXRipplePane(label);
    }

    private static RXTimelineView timeline() {
        return new RXTimelineView(
                item("Created", RXTimelineItem.Type.PRIMARY),
                item("Shipped", RXTimelineItem.Type.SUCCESS),
                item("Delayed", RXTimelineItem.Type.WARNING),
                item("Failed", RXTimelineItem.Type.DANGER),
                item("Note", RXTimelineItem.Type.INFO));
    }

    private static RXTimelineItem item(String title, RXTimelineItem.Type type) {
        RXTimelineItem item = new RXTimelineItem(title);
        item.setType(type);
        return item;
    }

    private static RXSidebar sidebar() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem home = new RXSidebarNavItem("Home");
        sidebar.getItems().addAll(home, new RXSidebarNavItem("Files"), new RXSidebarNavItem("Settings"));
        sidebar.setPrefHeight(220);
        sidebar.setMaxWidth(240);
        HBox.setHgrow(sidebar, Priority.NEVER);
        return sidebar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
