package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXPlacement;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXMenuButton page: an account menu with icons and shortcuts, a single choice, check
 * items that keep the menu open, and menus opening in other directions.
 */
public final class RXMenuButtonSamples {

    private RXMenuButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A button that opens a menu of commands, with icons, shortcuts, headers, separators and check or radio items.",
                List.of("Account menus, overflow (more) menus and sort or view options next to a list.",
                        "For one main action plus a few alternatives, put the alternatives in the menu."),
                List.of(
                        SampleCard.medium("Account menu",
                                "Items take an icon and a shortcut; a danger item stands apart after a separator.",
                                withStatus(accountMenu())),
                        SampleCard.medium("Single choice",
                                "Radio items in a toggle group; the button shows the current choice.",
                                sortMenu()),
                        SampleCard.medium("Check items",
                                "Check items keep the menu open, so several can be toggled in one go.",
                                columnsMenu()),
                        SampleCard.medium("Placement",
                                "placement opens the menu above, below, left or right, aligned to either edge.",
                                placements())),
                ButtonIcons.STYLESHEET);
    }

    private static Node withStatus(RXMenuButton button) {
        Label status = new Label("Pick a command");
        status.getStyleClass().add("result");
        for (RXMenuItem item : button.getItems()) {
            if (item.getText() != null && item.getOnAction() == null) {
                item.setOnAction(event -> status.setText(item.getText()));
            }
        }
        VBox box = new VBox(10.0, button, status);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static RXMenuButton accountMenu() {
        RXMenuButton account = new RXMenuButton("Ada Lovelace", ButtonIcons.icon("person-icon"));
        RXMenuItem profile = RXMenuItem.of("Profile", ButtonIcons.icon("person-icon"));
        profile.setAccelerator(KeyCombination.keyCombination("Shortcut+P"));
        RXMenuItem settings = RXMenuItem.of("Settings", ButtonIcons.icon("settings-icon"));
        settings.setAccelerator(KeyCombination.keyCombination("Shortcut+Comma"));
        RXMenuItem signOut = RXMenuItem.of("Sign out", ButtonIcons.icon("logout-icon"));
        signOut.setDanger(true);
        account.getItems().addAll(profile, settings, RXMenuItem.separator(), signOut);
        return account;
    }

    private static Node sortMenu() {
        RXMenuButton sort = new RXMenuButton("Sort: Newest", ButtonIcons.icon("sort-icon"));
        ToggleGroup group = new ToggleGroup();
        sort.getItems().add(RXMenuItem.header("Sort by"));
        for (String option : List.of("Newest", "Oldest", "Name", "Size")) {
            RXMenuItem item = RXMenuItem.radio(option, group);
            item.setKeepOpen(false);
            item.setSelected(option.equals("Newest"));
            item.setOnAction(event -> sort.setText("Sort: " + option));
            sort.getItems().add(item);
        }
        return sort;
    }

    private static Node columnsMenu() {
        RXMenuButton columns = new RXMenuButton("Columns");
        BooleanProperty owner = new SimpleBooleanProperty(true);
        BooleanProperty size = new SimpleBooleanProperty(true);
        BooleanProperty modified = new SimpleBooleanProperty(false);
        columns.getItems().addAll(RXMenuItem.header("Show columns"),
                RXMenuItem.checkbox("Owner", owner),
                RXMenuItem.checkbox("Size", size),
                RXMenuItem.checkbox("Last modified", modified));
        Label shown = new Label();
        shown.getStyleClass().add("result");
        Runnable update = () -> shown.setText("Name"
                + (owner.get() ? " · Owner" : "") + (size.get() ? " · Size" : "")
                + (modified.get() ? " · Last modified" : ""));
        owner.addListener(obs -> update.run());
        size.addListener(obs -> update.run());
        modified.addListener(obs -> update.run());
        update.run();
        VBox box = new VBox(10.0, columns, shown);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // Handed to the card as separate nodes: its flow wraps them on a narrow card and
    // centers every row.
    private static Node[] placements() {
        return new Node[]{placed("Above", RXPlacement.TOP_START), placed("Below", RXPlacement.BOTTOM_END),
                placed("Left", RXPlacement.LEFT_START), placed("Right", RXPlacement.RIGHT_START)};
    }

    private static RXMenuButton placed(String text, RXPlacement placement) {
        RXMenuButton button = new RXMenuButton(text);
        button.setPlacement(placement);
        button.getItems().addAll(RXMenuItem.of("Rename"), RXMenuItem.of("Duplicate"), RXMenuItem.of("Move to…"));
        return button;
    }
}
