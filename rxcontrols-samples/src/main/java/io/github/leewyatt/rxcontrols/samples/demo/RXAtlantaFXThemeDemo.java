package io.github.leewyatt.rxcontrols.samples.demo;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.theme.RXAtlantaFX;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Demonstrates {@link RXAtlantaFX}: making RxControls follow an AtlantaFX theme.
 *
 * <p>The key ordering rule (see {@link RXAtlantaFX}) is shown at the top of
 * {@link #start(Stage)}: the AtlantaFX theme is installed as the Application
 * user-agent stylesheet <em>before</em> the scene is built, then the bridge is
 * applied to the scene with {@link RXAtlantaFX#install(Scene)}. The theme selector
 * swaps the AtlantaFX theme live, and the checkbox toggles the bridge so you can
 * see RxControls follow AtlantaFX colors versus keep their built-in palette.
 *
 * <p>The cascader and its popup follow the scene-level bridge too: beyond the role
 * tokens, the bridge supplies the Modena base colors AtlantaFX omits, so controls
 * not yet migrated to role tokens still theme correctly instead of rendering blank.
 */
public class RXAtlantaFXThemeDemo extends Application {

    private static final List<Theme> THEMES = List.of(
            new PrimerLight(), new PrimerDark(),
            new NordLight(), new NordDark(),
            new CupertinoLight(), new CupertinoDark(),
            new Dracula());

    @Override
    public void start(Stage primaryStage) {
        // 1) Install the AtlantaFX theme as the Application UA BEFORE building the
        //    scene, so the first CSS pass can resolve the bridge's -color-* lookups.
        Theme initial = THEMES.get(0);
        Application.setUserAgentStylesheet(initial.getUserAgentStylesheet());

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar(root, initial));
        root.setCenter(buildShowroom());
        root.setBottom(buildCaption());

        // 2) Install the bridge on the scene; popups opened afterwards follow it too.
        Scene scene = new Scene(root, 760, 580);
        RXAtlantaFX.install(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle("RxControls × AtlantaFX bridge");
        primaryStage.show();
    }

    private HBox buildToolbar(BorderPane root, Theme initial) {
        ComboBox<Theme> themeBox = new ComboBox<>();
        themeBox.getItems().setAll(THEMES);
        themeBox.setValue(initial);
        themeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme theme) {
                return theme == null ? "" : theme.getName();
            }

            @Override
            public Theme fromString(String string) {
                return null;
            }
        });
        themeBox.valueProperty().addListener((obs, old, theme) -> {
            if (theme != null) {
                Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
            }
        });

        CheckBox bridge = new CheckBox("RxControls follows the theme (bridge)");
        bridge.setSelected(true);
        bridge.selectedProperty().addListener((obs, old, on) -> {
            if (on) {
                RXAtlantaFX.install(root.getScene());
            } else {
                RXAtlantaFX.uninstall(root.getScene());
            }
        });

        HBox toolbar = new HBox(12, new Label("AtlantaFX theme:"), themeBox, bridge);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(14));
        return toolbar;
    }

    private VBox buildShowroom() {
        RXFillButton fill = new RXFillButton("Fill Button");
        RXButton ripple = new RXButton("Ripple Button");
        FlowPane buttons = new FlowPane(12, 12, fill, ripple);

        RXTimelineView timeline = new RXTimelineView(
                item("Created", RXTimelineItem.Type.PRIMARY),
                item("Shipped", RXTimelineItem.Type.SUCCESS),
                item("Delayed", RXTimelineItem.Type.WARNING),
                item("Failed", RXTimelineItem.Type.DANGER),
                item("Note", RXTimelineItem.Type.INFO));

        RXCascader<String> cascader = new RXCascader<>();
        RXCascaderItem<String> asia = new RXCascaderItem<>("Asia");
        asia.getChildren().addAll(new RXCascaderItem<>("China"), new RXCascaderItem<>("Japan"));
        RXCascaderItem<String> europe = new RXCascaderItem<>("Europe");
        europe.getChildren().addAll(new RXCascaderItem<>("France"), new RXCascaderItem<>("Germany"));
        cascader.getRootItems().addAll(asia, europe);

        VBox showroom = new VBox(18,
                new Label("Buttons"), buttons,
                new Label("Timeline"), timeline,
                new Label("Cascader (open to see the popup follow)"), cascader);
        showroom.setPadding(new Insets(14));
        return showroom;
    }

    private Label buildCaption() {
        Label caption = new Label(
                "Switch the AtlantaFX theme or toggle the bridge to compare. The bridge re-points "
                        + "RxControls' color role tokens at AtlantaFX -color-* tokens, and supplies the "
                        + "Modena base colors AtlantaFX omits so every control follows the theme.");
        caption.setWrapText(true);
        caption.setPadding(new Insets(14));
        return caption;
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
