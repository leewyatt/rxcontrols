package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXToggleButton;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The RXToggleButton page: round icon toggles, format toggles, a joined single-choice
 * group and text that follows the state.
 */
public final class RXToggleButtonSamples {

    private RXToggleButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A standard ToggleButton with the Material ripple and state overlay of RXButton.",
                List.of("On and off states that should look like buttons: text formatting, view modes, follow.",
                        "For a setting that takes effect at once, an RXSwitchButton usually reads better."),
                List.of(
                        SampleCard.medium("Icon toggles",
                                "Round icon toggles with a ripple that grows from the middle.",
                                row(12.0, iconToggle("grid-icon", true), iconToggle("list-icon", false),
                                        iconToggle("pin-icon", false))),
                        SampleCard.medium("Format toggles",
                                "Independent toggles, each on or off on its own.",
                                row(6.0, toggle("Bold", true), toggle("Italic", false), toggle("Underline", false))),
                        SampleCard.medium("Joined group",
                                "Toggles in one ToggleGroup pick a single option; CSS joins them into one bar.",
                                joinedGroup()),
                        SampleCard.medium("Text follows state",
                                "Bind the text to the selected state to label each side of the toggle.",
                                row(10.0, follow(false), follow(true)))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(double gap, Node... nodes) {
        HBox row = new HBox(gap, nodes);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXToggleButton toggle(String text, boolean selected) {
        RXToggleButton toggle = new RXToggleButton(text);
        toggle.setSelected(selected);
        return toggle;
    }

    private static Node joinedGroup() {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox();
        bar.getStyleClass().add("joined");
        List<String> options = List.of("Day", "Week", "Month", "Year");
        for (int i = 0; i < options.size(); i++) {
            RXToggleButton toggle = toggle(options.get(i), i == 1);
            toggle.setToggleGroup(group);
            if (i == 0) {
                toggle.getStyleClass().add("first");
            } else if (i == options.size() - 1) {
                toggle.getStyleClass().add("last");
            }
            bar.getChildren().add(toggle);
        }
        // Clicking the selected toggle again would leave the group with no choice.
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private static RXToggleButton follow(boolean selected) {
        RXToggleButton toggle = new RXToggleButton();
        toggle.getStyleClass().add("follow");
        toggle.textProperty().bind(Bindings.when(toggle.selectedProperty()).then("Following").otherwise("Follow"));
        toggle.setSelected(selected);
        return toggle;
    }

    private static RXToggleButton iconToggle(String icon, boolean selected) {
        RXToggleButton toggle = new RXToggleButton(null, ButtonIcons.icon(icon));
        toggle.getStyleClass().add("round");
        toggle.setRippleCentered(true);
        toggle.setSelected(selected);
        return toggle;
    }
}
