package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSwitchButton;
import javafx.css.PseudoClass;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXSwitchButton page: a settings list, a master switch that enables its options,
 * the small size, colored switches, switches before their labels and a live dark-mode
 * preview.
 */
public final class RXSwitchButtonSamples {

    private static final PseudoClass DARK = PseudoClass.getPseudoClass("dark");

    private RXSwitchButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "An on/off switch with a sliding thumb, for a single setting that takes effect at once.",
                List.of("Settings that apply the moment they are flipped: Wi-Fi, notifications, dark mode.",
                        "When the choice is saved later with a form, a check box reads better."),
                List.of(
                        SampleCard.large("Settings list",
                                "A switch at the end of each row, next to an icon, a title and a short hint.",
                                settings()),
                        SampleCard.large("Master switch",
                                "Turning notifications off disables the switches that depend on it.",
                                master()),
                        SampleCard.medium("Dark mode",
                                "CSS swaps the check on the thumb for a sun or a moon; the preview flips at once.",
                                darkMode()),
                        SampleCard.medium("Colors",
                                "-rx-primary on a switch recolors its track and the check on the thumb.",
                                colors()),
                        SampleCard.medium("Small size",
                                "The small style class shrinks the track and thumb for dense toolbars and tables.",
                                small()),
                        SampleCard.medium("Switch first",
                                "switchPosition LEFT puts the switch before its label; RIGHT is the default.",
                                leading())),
                SelectionIcons.STYLESHEET);
    }

    private static Node settings() {
        VBox list = new VBox(0.0,
                setting("wifi-icon", "sky", "Wi-Fi", "Home network", true),
                setting("bluetooth-icon", "indigo", "Bluetooth", "2 devices connected", true),
                setting("airplane-icon", "orange", "Airplane mode", "Turns off all radios", false),
                setting("location-icon", "rose", "Location", "Used by Maps and Weather", false));
        list.getStyleClass().add("settings-list");
        list.setPrefWidth(340.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static HBox setting(String iconClass, String color, String title, String hint, boolean on) {
        Region glyph = icon(iconClass);
        glyph.setMouseTransparent(true);
        HBox badge = new HBox(glyph);
        badge.setAlignment(Pos.CENTER);
        badge.getStyleClass().addAll("icon-badge", color);
        VBox texts = new VBox(1.0, text(title, "body"), text(hint, "hint"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        RXSwitchButton toggle = new RXSwitchButton();
        toggle.setSelected(on);
        HBox row = new HBox(12.0, badge, texts, toggle);
        row.getStyleClass().add("setting-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node master() {
        RXSwitchButton all = new RXSwitchButton();
        all.setSelected(true);
        HBox head = row("Allow notifications", all);
        head.getStyleClass().add("master-row");
        VBox options = new VBox(0.0,
                option("Sounds", true),
                option("Badges on the app icon", true),
                option("Show previews", false));
        options.getStyleClass().add("dependent");
        options.disableProperty().bind(all.selectedProperty().not());
        VBox box = new VBox(4.0, head, options);
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static HBox option(String title, boolean on) {
        RXSwitchButton toggle = new RXSwitchButton();
        toggle.getStyleClass().add("small");
        toggle.setSelected(on);
        return row(title, toggle);
    }

    private static HBox row(String title, Node end) {
        Label label = text(title, "body");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8.0, label, spacer, end);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("switch-row");
        return row;
    }

    private static Node darkMode() {
        Label title = text("Lee Wyatt", "preview-title");
        Label body = text("@LeeWyatt_7788 · 2 h", "preview-hint");
        VBox preview = new VBox(4.0, title, body);
        preview.getStyleClass().add("theme-preview");
        preview.setPrefWidth(200.0);
        preview.setMaxWidth(Region.USE_PREF_SIZE);

        RXSwitchButton dark = new RXSwitchButton("Dark mode");
        dark.getStyleClass().add("theme-switch");
        dark.selectedProperty().addListener((obs, was, now) -> preview.pseudoClassStateChanged(DARK, now));
        dark.setSelected(true);
        VBox box = new VBox(14.0, dark, preview);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node colors() {
        VBox column = new VBox(8.0,
                colored("Auto-save", "teal"),
                colored("Focus mode", "violet"),
                colored("Low power", "amber"));
        column.setAlignment(Pos.CENTER_RIGHT);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }

    private static RXSwitchButton colored(String text, String color) {
        RXSwitchButton toggle = new RXSwitchButton(text);
        toggle.getStyleClass().add(color);
        toggle.setSelected(true);
        return toggle;
    }

    private static Node small() {
        RXSwitchButton regular = new RXSwitchButton("Default");
        regular.setSelected(true);
        RXSwitchButton compact = new RXSwitchButton("Small");
        compact.getStyleClass().add("small");
        compact.setSelected(true);
        VBox column = new VBox(10.0, regular, compact);
        column.setAlignment(Pos.CENTER_RIGHT);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }

    private static Node leading() {
        VBox column = new VBox(8.0,
                first("Remember me", true),
                first("Keep me signed in for 30 days", false));
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }

    private static RXSwitchButton first(String text, boolean on) {
        RXSwitchButton toggle = new RXSwitchButton(text);
        toggle.setSwitchPosition(HorizontalDirection.LEFT);
        toggle.setSelected(on);
        return toggle;
    }
}
