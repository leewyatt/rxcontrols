package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Compact demo for {@link RXMasonryPane}: a responsive "notes" wall whose cards
 * take their height from wrapped text at the current column width.
 *
 * <p>There is no control panel by design — just resize the window and watch the
 * columns reflow, the text re-wrap, and the cards animate into place.</p>
 */
public class RXMasonryPaneDemo extends Application {

    private static final double COLUMN_WIDTH = 260.0;

    private static final Note[] NOTES = {
            new Note("DESIGN", "Whitespace is a feature",
                    "Generous spacing gives the eye room to breathe and makes dense "
                            + "information feel calm and scannable."),
            new Note("JAVAFX", "Content bias",
                    "A node with horizontal content bias reports its height as a "
                            + "function of width — exactly what a masonry pane needs to "
                            + "pack variable cards correctly."),
            new Note("CSS", "Prefix your properties",
                    "Custom styleable properties use a project prefix so they never "
                            + "collide with the built-in -fx- namespace."),
            new Note("LAYOUT", "Shortest column wins",
                    "Each card drops into whichever column is currently shortest, so "
                            + "the bottom edge stays roughly even without manual tuning."),
            new Note("TIP", "Resize me",
                    "Drag the window wider or narrower and watch the columns reflow. "
                            + "The text re-wraps, card heights change, and everything "
                            + "animates smoothly into its new position."),
            new Note("PERF", "Cheap reflow",
                    "Placement is linear in items times columns over a flat array of "
                            + "column heights — no occupancy matrix, no fixed row cap."),
            new Note("API", "One escape hatch",
                    "Set a fixed column count when you need an exact grid; leave it at "
                            + "zero to let the column width decide responsively."),
            new Note("DESIGN", "Cards, not rows",
                    "Unlike a grid, a waterfall layout lets each item keep its natural "
                            + "height, which suits photos, notes and feeds."),
            new Note("JAVAFX", "FLIP animation",
                    "Layout writes the final position; the animator only tweens "
                            + "translate back to zero, so motion never fights the layout."),
            new Note("CSS", "50% means round",
                    "For a perfect circle or pill, prefer a 50% background radius."),
            new Note("LAYOUT", "Fill the width",
                    "With fill-width on, columns stretch to consume the whole pane, so "
                            + "there is never an awkward gap on the right edge."),
            new Note("TIP", "Breakpoints optional",
                    "Pin a column count per breakpoint — xs, sm, md, lg, xl — or just "
                            + "let the target column width do the work."),
            new Note("PERF", "Measure once",
                    "The packed layout is cached per width and shared between the height "
                            + "query and the layout pass, so each child is measured once."),
            new Note("API", "Span a column",
                    "Mark a feature card to span two columns; the engine clamps the span "
                            + "to the available column count automatically."),
            new Note("DESIGN", "Hierarchy by size",
                    "Variable card heights create a natural rhythm that guides the eye "
                            + "down the page better than a rigid uniform grid."),
            new Note("JAVAFX", "No re-entrancy hacks",
                    "Reporting height through computePrefHeight keeps the sizing protocol "
                            + "clean — no setPrefHeight inside layout, no guard flag."),
            new Note("CSS", "Pseudo-classes",
                    "The active breakpoint is exposed as a pseudo-class, so a layout can "
                            + "be themed differently at each width from CSS alone."),
            new Note("TIP", "Smooth removals",
                    "Removing a card fades it out while the survivors slide up to fill "
                            + "the gap — no jarring jump."),
            new Note("LAYOUT", "Pinterest, natively",
                    "The waterfall look popularised on the web finally feels native on "
                            + "the desktop, with proper sizing and animation."),
            new Note("PERF", "Scales further",
                    "For tens of thousands of items a virtualized sibling recycles "
                            + "cells; until then, the plain pane stays delightfully simple."),
    };

    @Override
    public void start(Stage primaryStage) {
        RXMasonryPane masonry = new RXMasonryPane();
        masonry.getStyleClass().add("notes-masonry");
        masonry.setColumnWidth(COLUMN_WIDTH);
        masonry.setHgap(12);
        masonry.setVgap(12);
        masonry.setAnimationDuration(Duration.millis(100));
        masonry.setPadding(new Insets(20));
        for (Note note : NOTES) {
            masonry.getChildren().add(createCard(note));
        }
        // The first card spans two columns to highlight a feature note.
        RXMasonryPane.setColumnSpan(masonry.getChildren().get(0), 2);

        ScrollPane scroll = new ScrollPane(masonry);
        scroll.getStyleClass().add("notes-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane root = new BorderPane(scroll);
        root.getStyleClass().add("root");
        root.setTop(createHeader());

        Scene scene = new Scene(root, 980.0, 700.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-masonry-pane-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMasonryPane Demo");
        primaryStage.show();
    }

    private Region createHeader() {
        Label title = new Label("Responsive notes");
        title.getStyleClass().add("demo-title");
        Label subtitle = new Label("Resize the window — columns reflow and cards re-wrap and animate");
        subtitle.getStyleClass().add("demo-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("demo-header");
        return header;
    }

    private Region createCard(Note note) {
        Label category = new Label(note.category());
        category.getStyleClass().add("note-category");
        category.setStyle("-fx-background-color: " + colorFor(note.category()) + ";");
        category.setMaxWidth(Region.USE_PREF_SIZE);

        Label title = new Label(note.title());
        title.getStyleClass().add("note-title");
        title.setWrapText(true);

        Label body = new Label(note.body());
        body.getStyleClass().add("note-body");
        body.setWrapText(true);

        VBox card = new VBox(category, title, body);
        card.getStyleClass().add("note-card");
        return card;
    }

    private String colorFor(String category) {
        return switch (category) {
            case "DESIGN" -> "#8b5cf6";
            case "JAVAFX" -> "#2563eb";
            case "CSS" -> "#0891b2";
            case "LAYOUT" -> "#16a34a";
            case "TIP" -> "#d97706";
            case "PERF" -> "#dc2626";
            case "API" -> "#db2777";
            default -> "#64748b";
        };
    }

    private record Note(String category, String title, String body) {
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
