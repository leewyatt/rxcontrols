package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXSegmentedControl page: a time range driving a figure, a view switcher with
 * icons, a full-width sign-in switch, equal segment widths, colored indicators, and an
 * optional choice with a disabled segment.
 */
public final class RXSegmentedControlSamples {

    private static final Map<String, String> REVENUE = Map.of(
            "day", "$1,920", "week", "$12,480", "month", "$51,300", "year", "$604,000");

    private RXSegmentedControlSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A strip of segments with a sliding indicator that marks the one selected value.",
                List.of("Switching between two to five views or ranges of the same content, like a chart period.",
                        "For pages of different content use a tab pane; for a longer list of options use RXSelectionBox."),
                List.of(
                        SampleCard.medium("Time range",
                                "The value drives what the card shows; the indicator slides to the picked segment.",
                                timeRange()),
                        SampleCard.medium("View switcher",
                                "An item takes an icon next to its text, or an icon alone as its content.",
                                viewSwitcher()),
                        SampleCard.large("Full width",
                                "block stretches the strip across its container and shares the width equally.",
                                signIn()),
                        SampleCard.large("Equal segment widths",
                                "equalSegmentWidth sizes every segment like the widest one; the strip still fits its content.",
                                equalWidths()),
                        SampleCard.medium("Colors",
                                "-rx-primary on the control recolors its indicator.",
                                colors()),
                        SampleCard.medium("Optional and disabled",
                                "allowEmptySelection lets a second click clear a choice; disabled segments (XL, Red) cannot be picked.",
                                optional())),
                SelectionIcons.STYLESHEET);
    }

    private static Node timeRange() {
        RXSegmentedControl<String> range = new RXSegmentedControl<>(
                RXSegmentedItem.of("day", "Day"),
                RXSegmentedItem.of("week", "Week"),
                RXSegmentedItem.of("month", "Month"),
                RXSegmentedItem.of("year", "Year"));
        range.setValue("week");
        Label caption = text("", "hint");
        caption.textProperty().bind(Bindings.createStringBinding(
                () -> "Revenue this " + range.getValue(), range.valueProperty()));
        Label figure = text("", "figure");
        figure.textProperty().bind(Bindings.createStringBinding(
                () -> REVENUE.get(range.getValue()), range.valueProperty()));
        VBox numbers = new VBox(2.0, caption, figure);
        numbers.setAlignment(Pos.CENTER);
        VBox box = new VBox(14.0, range, numbers);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node viewSwitcher() {
        RXSegmentedControl<String> view = new RXSegmentedControl<>(
                RXSegmentedItem.of("grid", "Grid", icon("grid-icon")),
                RXSegmentedItem.of("list", "List", icon("list-icon")),
                RXSegmentedItem.of("board", "Board", icon("board-icon")));
        view.getStyleClass().add("with-icons");
        view.setValue("list");
        RXSegmentedControl<String> compact = new RXSegmentedControl<>(
                iconOnly("grid", "grid-icon", "Grid"),
                iconOnly("list", "list-icon", "List"),
                iconOnly("board", "board-icon", "Board"));
        compact.getStyleClass().add("with-icons");
        compact.valueProperty().bindBidirectional(view.valueProperty());
        VBox box = new VBox(14.0, view, compact);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static RXSegmentedItem<String> iconOnly(String value, String iconClass, String tip) {
        RXSegmentedItem<String> item = new RXSegmentedItem<>(value, tip);
        item.setContent(icon(iconClass));
        item.setTooltip(new Tooltip(tip));
        return item;
    }

    private static Node signIn() {
        RXSegmentedControl<String> mode = new RXSegmentedControl<>(
                RXSegmentedItem.of("in", "Sign in"),
                RXSegmentedItem.of("up", "Create account"));
        mode.setBlock(true);
        TextField email = new TextField();
        email.setPromptText("Email");
        TextField name = new TextField();
        name.setPromptText("Full name");
        name.visibleProperty().bind(mode.valueProperty().isEqualTo("up"));
        name.managedProperty().bind(name.visibleProperty());
        // Takes the name field's row while signing in, so switching modes does not
        // resize the card, and with it the row and the cards beside it.
        Label reserve = text("New here? Pick Create account above.", "hint");
        reserve.minHeightProperty().bind(email.heightProperty());
        reserve.prefHeightProperty().bind(email.heightProperty());
        reserve.visibleProperty().bind(name.visibleProperty().not());
        reserve.managedProperty().bind(reserve.visibleProperty());
        VBox form = new VBox(10.0, mode, name, email, reserve);
        form.getStyleClass().add("form-panel");
        form.setPrefWidth(300.0);
        form.setMaxWidth(Region.USE_PREF_SIZE);
        return form;
    }

    private static Node equalWidths() {
        RXSegmentedControl<String> natural = sizes();
        RXSegmentedControl<String> equal = sizes();
        equal.setEqualSegmentWidth(true);
        VBox box = new VBox(6.0, text("Default", "hint"), natural, text("equalSegmentWidth", "hint"), equal);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXSegmentedControl<String> sizes() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>(
                RXSegmentedItem.of("all", "All"),
                RXSegmentedItem.of("unread", "Unread"),
                RXSegmentedItem.of("mentions", "Mentions"),
                RXSegmentedItem.of("me", "Me"));
        control.setValue("unread");
        return control;
    }

    private static Node colors() {
        VBox box = new VBox(12.0,
                colored("teal", "Song", "Album", "Artist", "Playlist"),
                colored("orange", "Hot", "New", "Top", "Rising"),
                colored("violet", "Light", "Dark", "Dim", "Auto"));
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node colored(String color, String... texts) {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        for (String value : texts) {
            control.getItems().add(RXSegmentedItem.of(value, value));
        }
        // block lets the box stretch all three to the widest one.
        control.setBlock(true);
        control.getStyleClass().add(color);
        return control;
    }

    private static Node optional() {
        RXSegmentedControl<String> size = optionalFilter("M", "S", "M", "L", "XL");
        disable(size, 3, "Sold out");
        RXSegmentedControl<String> color = optionalFilter(null, "Black", "White", "Sand", "Red");
        disable(color, 3, "Out of stock");
        Label status = text("", "hint");
        status.textProperty().bind(Bindings.createStringBinding(
                () -> (size.getValue() == null ? "Any size" : "Size " + size.getValue())
                        + " · " + (color.getValue() == null ? "any color" : color.getValue()),
                size.valueProperty(), color.valueProperty()));
        VBox box = new VBox(10.0, size, color, status);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXSegmentedControl<String> optionalFilter(String value, String... texts) {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        for (String text : texts) {
            control.getItems().add(RXSegmentedItem.of(text, text));
        }
        control.setAllowEmptySelection(true);
        control.setBlock(true);
        control.setValue(value);
        return control;
    }

    private static void disable(RXSegmentedControl<String> control, int index, String reason) {
        control.getItems().get(index).setDisable(true);
        control.getItems().get(index).setTooltip(new Tooltip(reason));
    }

}
