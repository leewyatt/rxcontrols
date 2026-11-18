package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXRadioButton;
import javafx.css.PseudoClass;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXRadioButton page: plan cards that highlight the picked plan, a shipping method
 * with a live total, the three sizes, colored priorities and radios after their labels.
 */
public final class RXRadioButtonSamples {

    private static final PseudoClass CHOSEN = PseudoClass.getPseudoClass("chosen");

    private RXRadioButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A radio button with a Material ring and an animated dot, for picking one option of a group.",
                List.of("A short list of options that are all worth seeing at once, of which exactly one applies.",
                        "For two to five short options in a row try RXSegmentedControl; for long lists use RXSelectionBox."),
                List.of(
                        SampleCard.wide("Plan cards",
                                "Each card holds a radio in one ToggleGroup; the card of the selected radio is highlighted.",
                                plans()),
                        SampleCard.large("Shipping method",
                                "Picking a method updates the order total below the group.",
                                shipping()),
                        SampleCard.large("Sizes",
                                "The small and large style classes resize the ring, the dot and the halo.",
                                sizes()),
                        SampleCard.large("Priority colors",
                                "-rx-primary set on each radio gives the picked option the color of its meaning.",
                                priorities()),
                        SampleCard.large("Radio on the right",
                                "radioPosition RIGHT puts the ring after the label, for lists that read like menus.",
                                payment())),
                SelectionIcons.STYLESHEET);
    }

    private static Node plans() {
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(14.0,
                plan(group, "Hobby", "$0", "1 project, community support"),
                plan(group, "Pro", "$12", "Unlimited projects, email support"),
                plan(group, "Business", "$39", "SSO, audit log, priority support"));
        group.selectToggle(group.getToggles().get(1));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node plan(ToggleGroup group, String name, String price, String note) {
        RXRadioButton radio = new RXRadioButton(name);
        radio.setToggleGroup(group);
        Label amount = text(price, "price");
        Label per = text("/ month", "hint");
        HBox priceRow = new HBox(4.0, amount, per);
        priceRow.setAlignment(Pos.BASELINE_LEFT);
        Label body = text(note, "hint");
        body.setWrapText(true);
        priceRow.getStyleClass().add("indent");
        body.getStyleClass().add("indent");
        VBox card = new VBox(4.0, radio, priceRow, body);
        card.getStyleClass().add("plan-card");
        card.setPrefWidth(180.0);
        card.setOnMouseClicked(event -> radio.setSelected(true));
        radio.selectedProperty().addListener((obs, was, now) -> card.pseudoClassStateChanged(CHOSEN, now));
        return card;
    }

    private static Node shipping() {
        ToggleGroup group = new ToggleGroup();
        VBox options = new VBox(0.0,
                method(group, "Standard", "5 to 7 days", 0),
                method(group, "Express", "2 business days", 9),
                method(group, "Overnight", "Next morning", 24));
        Label total = text("", "total");
        total.setMaxWidth(Double.MAX_VALUE);
        group.selectedToggleProperty().addListener((obs, old, now) -> total.setText(total(now)));
        group.selectToggle(group.getToggles().get(0));
        VBox box = new VBox(8.0, options, total);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node method(ToggleGroup group, String name, String time, int price) {
        RXRadioButton radio = new RXRadioButton(name);
        radio.setToggleGroup(group);
        radio.setUserData(price);
        Label when = text(time, "hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label cost = text(price == 0 ? "Free" : "$" + price, "body");
        HBox row = new HBox(8.0, radio, when, spacer, cost);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String total(Toggle toggle) {
        int shipping = toggle == null ? 0 : (Integer) toggle.getUserData();
        return "Order total: $" + (58 + shipping) + ".00";
    }

    private static Node sizes() {
        HBox row = new HBox(16.0,
                sized("Small", "small"),
                sized("Default", null),
                sized("Large", "large"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node sized(String name, String styleClass) {
        ToggleGroup group = new ToggleGroup();
        RXRadioButton first = new RXRadioButton("Yes");
        RXRadioButton other = new RXRadioButton("No");
        for (RXRadioButton radio : List.of(first, other)) {
            radio.setToggleGroup(group);
            if (styleClass != null) {
                radio.getStyleClass().add(styleClass);
            }
        }
        first.setSelected(true);
        return new VBox(0.0, text(name, "hint"), first, other);
    }

    private static Node priorities() {
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(10.0,
                colored(group, "Low", "lime"),
                colored(group, "Medium", "amber"),
                colored(group, "High", "rose"));
        group.selectToggle(group.getToggles().get(2));
        Label caption = text("Priority of the bug report", "hint");
        VBox box = new VBox(6.0, caption, row);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXRadioButton colored(ToggleGroup group, String text, String color) {
        RXRadioButton radio = new RXRadioButton(text);
        radio.setToggleGroup(group);
        radio.getStyleClass().add(color);
        return radio;
    }

    private static Node payment() {
        ToggleGroup group = new ToggleGroup();
        VBox list = new VBox(0.0,
                trailing(group, "Credit card", "card-icon"),
                trailing(group, "Bank transfer", "bank-icon"),
                trailing(group, "Pay on delivery", "truck-icon"));
        list.getStyleClass().add("option-list");
        group.selectToggle(group.getToggles().get(0));
        list.setPrefWidth(280.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static RXRadioButton trailing(ToggleGroup group, String text, String iconClass) {
        RXRadioButton radio = new RXRadioButton(text, icon(iconClass));
        radio.setToggleGroup(group);
        radio.setRadioPosition(HorizontalDirection.RIGHT);
        radio.setMaxWidth(Double.MAX_VALUE);
        radio.getStyleClass().add("trailing");
        return radio;
    }
}
