package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveCol;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveRow;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Combined demo for {@link RXResponsiveRow} and {@link RXMasonryPane} showing the
 * classic gallery page: a responsive row splits the page into a main column and an
 * aside, and the main column hosts a masonry gallery.
 *
 * <p>The two responsive systems do orthogonal jobs. {@code RXResponsiveRow} decides
 * the page regions — below the {@code md} breakpoint the aside is hidden and the
 * gallery goes full width. {@code RXMasonryPane} decides how many waterfall columns
 * fit inside the main column. Resize the window to see both react at once.</p>
 */
public class RXMasonryResponsiveDemo extends Application {

    private static final double GALLERY_COLUMN_WIDTH = 230.0;
    private static final double GAP = 16.0;

    private static final Note[] NOTES = {
            new Note("DESIGN", "Whitespace is a feature",
                    "Generous spacing gives the eye room to breathe and makes dense "
                            + "information feel calm and scannable."),
            new Note("JAVAFX", "Content bias",
                    "A node with horizontal content bias reports its height as a "
                            + "function of width — exactly what a masonry pane needs."),
            new Note("LAYOUT", "Shortest column wins",
                    "Each card drops into whichever column is currently shortest, so the "
                            + "bottom edge stays roughly even."),
            new Note("TIP", "Two systems, one page",
                    "The page columns come from RXResponsiveRow; the card columns come "
                            + "from RXMasonryPane. They never fight."),
            new Note("PERF", "Cheap reflow",
                    "Placement is linear in items times columns over a flat array of "
                            + "column heights."),
            new Note("CSS", "Prefix your properties",
                    "Custom styleable properties use a project prefix so they never "
                            + "collide with the built-in -fx- namespace."),
            new Note("API", "One escape hatch",
                    "Set a fixed column count for an exact grid; leave it at zero to let "
                            + "the column width decide responsively."),
            new Note("DESIGN", "Cards, not rows",
                    "A waterfall layout lets each item keep its natural height, which "
                            + "suits photos, notes and feeds."),
            new Note("JAVAFX", "FLIP animation",
                    "Layout writes the final position; the animator only tweens translate "
                            + "back to zero, so motion never fights the layout."),
            new Note("LAYOUT", "Fill the width",
                    "With fill-width on, columns stretch to consume the whole region, so "
                            + "there is never a gap on the right edge."),
            new Note("PERF", "Measure once",
                    "The packed layout is cached per width and shared between the height "
                            + "query and the layout pass."),
            new Note("TIP", "Cross the md line",
                    "Drag the window across roughly 992px and watch the aside appear or "
                            + "disappear while the gallery reflows."),
            new Note("CSS", "Pseudo-classes",
                    "The active breakpoint is exposed as a pseudo-class, so a layout can "
                            + "be themed per width from CSS alone."),
            new Note("API", "Span a column",
                    "Mark a feature card to span two columns; the engine clamps the span "
                            + "to the available column count."),
    };

    @Override
    public void start(Stage primaryStage) {
        RXResponsiveCol mainCol = new RXResponsiveCol(buildMain());
        mainCol.setSpan(24);                       // base: full width on small screens
        mainCol.setMd(RXColSpec.of(16));           // md and up: 16 / 24

        RXResponsiveCol asideCol = new RXResponsiveCol(buildAside());
        asideCol.setSpan(8);                       // span used when visible
        asideCol.setXs(RXColSpec.builder().hidden(true).build());          // hidden from xs up...
        asideCol.setMd(RXColSpec.builder().span(8).hidden(false).build()); // ...visible again at md

        RXResponsiveRow row = new RXResponsiveRow(mainCol, asideCol);
        row.setGutter(GAP);
        row.setRowGap(GAP);
        row.setPadding(new Insets(8.0, 24.0, 24.0, 24.0));

        ScrollPane scroll = new ScrollPane(row);
        scroll.getStyleClass().add("page-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane root = new BorderPane(scroll);
        root.getStyleClass().add("root");
        root.setTop(buildHeader());

        Scene scene = new Scene(root, 1240.0, 760.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-masonry-responsive-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMasonryPane + RXResponsiveRow");
        primaryStage.show();
    }

    private Region buildHeader() {
        Label title = new Label("Gallery page");
        title.getStyleClass().add("demo-title");
        Label subtitle = new Label(
                "RXResponsiveRow lays out the page; RXMasonryPane fills the gallery — resize to see both react");
        subtitle.getStyleClass().add("demo-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("demo-header");
        return header;
    }

    private Region buildMain() {
        RXMasonryPane gallery = new RXMasonryPane();
        gallery.getStyleClass().add("gallery");
        gallery.setColumnWidth(GALLERY_COLUMN_WIDTH);
        gallery.setHgap(GAP);
        gallery.setVgap(GAP);
        for (Note note : NOTES) {
            gallery.getChildren().add(createNoteCard(note));
        }
        RXMasonryPane.setColumnSpan(gallery.getChildren().get(0), 2);

        VBox main = new VBox(10.0, sectionLabel("MAIN COLUMN · GALLERY"), gallery);
        main.getStyleClass().add("region-main");
        return main;
    }

    private Region buildAside() {
        VBox aside = new VBox(10.0,
                sectionLabel("ASIDE · HIDDEN BELOW md"),
                asideCard("Page layout",
                        "RXResponsiveRow splits this page into a main column and this "
                                + "aside. Below md the aside is hidden and the gallery "
                                + "spans the full width."),
                asideCard("The gallery",
                        "The gallery is an RXMasonryPane. Its column count follows the "
                                + "main column's width, so it reflows independently of the "
                                + "page columns."),
                asideCard("Try it",
                        "Drag the window narrower than ~992px to drop this aside, then "
                                + "wider to bring it back."));
        aside.getStyleClass().add("region-aside");
        return aside;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private Region createNoteCard(Note note) {
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

    private Region asideCard(String title, String body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("aside-card-title");
        titleLabel.setWrapText(true);
        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("aside-card-body");
        bodyLabel.setWrapText(true);
        VBox card = new VBox(4.0, titleLabel, bodyLabel);
        card.getStyleClass().add("aside-card");
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
