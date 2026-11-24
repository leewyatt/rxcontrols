package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXSpeedDial page: the card stage is the canvas, with a dial in every corner, each
 * opening away from its corner with a different label mode or trigger and captioned with how it
 * opens, and one place that reports the picked action.
 */
public final class RXSpeedDialSamples {

    private RXSpeedDialSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A floating action button that opens a short list of related actions.",
                List.of("A screen's few main create actions, kept out of the way until needed.",
                        "Three to six actions; more than that belongs in a menu."),
                List.of(
                        SampleCard.canvas("One dial per corner",
                                "Each dial opens away from its corner. Bottom right: labels always shown. "
                                        + "Top right: labels on hover. Top left: no labels. Bottom left: opens on hover.",
                                canvas())),
                ButtonIcons.STYLESHEET);
    }

    private static Node canvas() {
        Label title = new Label("Pick an action");
        title.getStyleClass().add("dial-status");
        Label hint = new Label("The action you pick shows here");
        hint.getStyleClass().add("result");
        VBox status = new VBox(6.0, title, hint);
        status.setAlignment(Pos.CENTER);

        // Hover labels open toward the middle of the card, so they stay inside it.
        RXSpeedDial shareDial = dial(RXSpeedDial.Direction.LEFT, RXSpeedDial.LabelMode.HOVER, "share-icon", "teal");
        shareDial.setLabelPlacement(RXSpeedDial.LabelPlacement.END);

        StackPane canvas = new StackPane(status,
                place(dial(RXSpeedDial.Direction.UP, RXSpeedDial.LabelMode.PERSISTENT, "add-icon", null),
                        Pos.BOTTOM_RIGHT, title),
                place(shareDial, Pos.TOP_RIGHT, title),
                place(dial(RXSpeedDial.Direction.DOWN, RXSpeedDial.LabelMode.NONE, "note-icon", "orange"),
                        Pos.TOP_LEFT, title),
                place(hoverDial(), Pos.BOTTOM_LEFT, title));
        canvas.setPrefHeight(420.0);
        return canvas;
    }

    private static Node place(RXSpeedDial dial, Pos corner, Label status) {
        for (RXSpeedDialAction action : dial.getActions()) {
            action.setOnAction(event -> status.setText(action.getText()));
        }
        Label caption = new Label(dial.getOpenTrigger() == RXSpeedDial.OpenTrigger.HOVER
                ? "Hover to open" : "Click to open");
        caption.getStyleClass().add("caption");
        // The caption takes the edge side: the actions and their labels open toward the middle.
        VBox spot = corner.getVpos() == VPos.TOP
                ? new VBox(4.0, caption, dial)
                : new VBox(4.0, dial, caption);
        spot.setAlignment(Pos.CENTER);
        spot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(spot, corner);
        return spot;
    }

    private static RXSpeedDial dial(RXSpeedDial.Direction direction, RXSpeedDial.LabelMode labels,
                                    String icon, String color) {
        RXSpeedDial dial = new RXSpeedDial(ButtonIcons.icon(icon),
                new RXSpeedDialAction("New note", ButtonIcons.icon("note-icon")),
                new RXSpeedDialAction("Add photo", ButtonIcons.icon("photo-icon")),
                new RXSpeedDialAction("Share link", ButtonIcons.icon("link-icon")));
        dial.setDirection(direction);
        dial.setLabelMode(labels);
        if (color != null) {
            dial.getStyleClass().add(color);
        }
        return dial;
    }

    private static RXSpeedDial hoverDial() {
        RXSpeedDial dial = dial(RXSpeedDial.Direction.RIGHT, RXSpeedDial.LabelMode.HOVER, "photo-icon", "violet");
        dial.setOpenTrigger(RXSpeedDial.OpenTrigger.HOVER);
        dial.setLabelPlacement(RXSpeedDial.LabelPlacement.START);
        return dial;
    }
}
