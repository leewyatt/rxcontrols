package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXRibbonBadge.Position;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXRibbonBadge page: a pricing table, color classes on both sides, a long ribbon
 * text and a flat ribbon.
 */
public final class RXRibbonBadgeSamples {

    private static final List<String> COLORS = List.of("primary", "success", "warning", "danger");
    private static final List<String> PLANS = List.of("Starter", "Team", "Trial", "Archive");
    private static final List<String> NOTES = List.of("One seat", "Up to 10 seats", "14 days left", "Read only");

    private RXRibbonBadgeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A text ribbon laid over the top edge of its content, with a folded corner.",
                List.of("Single out one card in a group: the recommended plan, a new arrival, an item on sale.",
                        "For a count or a status dot on an icon or avatar, use RXBadge."),
                List.of(
                        SampleCard.wide("Pricing table",
                                "Ribbons label every plan without changing the card layout; the gray info class keeps the free tier quiet.",
                                plan("Basic", "$0", "For personal notes.", "Free", "info", false),
                                plan("Pro", "$12", "Unlimited projects.", "Most popular", "primary", true),
                                plan("Team", "$29", "Shared workspaces.", "-30%", "danger", true)),
                        SampleCard.large("Start side",
                                "The four color classes with the position set to START; the fold moves with it.",
                                colorRibbons(Position.START)),
                        SampleCard.large("End side",
                                "The same four classes on the END side, which is the default.",
                                colorRibbons(Position.END)),
                        SampleCard.large("Long text",
                                "A ribbon never runs past the far side of its content; longer text ends in an ellipsis.",
                                ribbon(note("Spring sale", "Ends on Sunday."),
                                        "Free shipping on every order over $50 until Sunday night", "success", Position.END)),
                        SampleCard.large("Flat ribbon",
                                "Overhang 0 keeps the ribbon inside the card with no fold; the offset moves it down.",
                                flatRibbon())),
                SampleContent.class.getResource("rx-ribbon-badge-samples.css").toExternalForm());
    }

    private static Node colorRibbons(Position position) {
        GridPane grid = new GridPane();
        grid.setHgap(20.0);
        grid.setVgap(20.0);
        for (int i = 0; i < COLORS.size(); i++) {
            String color = COLORS.get(i);
            String text = Character.toUpperCase(color.charAt(0)) + color.substring(1);
            grid.add(ribbon(tile(PLANS.get(i), NOTES.get(i)), text, color, position), i % 2, i / 2);
        }
        return grid;
    }

    private static RXRibbonBadge ribbon(Node content, String text, String color, Position position) {
        RXRibbonBadge ribbon = new RXRibbonBadge(content, text);
        ribbon.getStyleClass().add(color);
        ribbon.setPosition(position);
        return ribbon;
    }

    private static Node flatRibbon() {
        RXRibbonBadge ribbon = ribbon(note("Weekly report", "Generated every Monday at 09:00."),
                "Draft", "warning", Position.END);
        ribbon.setOverhang(0.0);
        ribbon.setRibbonOffset(14.0);
        return ribbon;
    }

    private static Node tile(String name, String text) {
        Label title = new Label(name);
        title.getStyleClass().add("name");
        Label body = new Label(text);
        body.getStyleClass().add("note");
        VBox tile = new VBox(4.0, title, body);
        tile.getStyleClass().addAll("plan", "tile");
        return fixed(tile, 180.0);
    }

    private static Node note(String name, String text) {
        Label title = new Label(name);
        title.getStyleClass().add("name");
        Label body = new Label(text);
        body.getStyleClass().add("note");
        body.setWrapText(true);
        VBox note = new VBox(6.0, title, body);
        note.getStyleClass().addAll("plan", "sheet");
        return fixed(note, 280.0);
    }

    private static Node plan(String name, String price, String text, String ribbonText, String color,
                             boolean featured) {
        Label title = new Label(name);
        title.getStyleClass().add("name");
        Label amount = new Label(price);
        amount.getStyleClass().add("price");
        Label body = new Label(text);
        body.getStyleClass().add("note");
        body.setWrapText(true);
        VBox card = new VBox(6.0, title, amount, body);
        card.getStyleClass().add("plan");
        fixed(card, 160.0);
        if (featured) {
            card.getStyleClass().add("featured");
        }
        return ribbon(card, ribbonText, color, Position.END);
    }

    private static Region fixed(Region region, double width) {
        region.setPrefWidth(width);
        region.setMaxWidth(Region.USE_PREF_SIZE);
        return region;
    }
}
