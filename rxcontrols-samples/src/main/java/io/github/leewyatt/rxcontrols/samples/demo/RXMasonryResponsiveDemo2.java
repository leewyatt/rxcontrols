package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Virtualized counterpart to {@link RXMasonryResponsiveDemo}. An
 * {@link RXMasonryView} responsively reflows a small note gallery while
 * recycling cells inside its viewport.
 */
public class RXMasonryResponsiveDemo2 extends Application {

    private static final double GALLERY_COLUMN_WIDTH = 230.0;
    private static final double GAP = 16.0;

    private static final Note FEATURED_NOTE = new Note("DESIGN", "Whitespace is a feature",
            "Generous spacing gives the eye room to breathe and makes dense "
                    + "information feel calm and scannable.");

    private static final Note[] NOTES = {
            FEATURED_NOTE,
            new Note("JAVAFX", "Content bias",
                    "A node with horizontal content bias reports its height as a "
                            + "function of width — exactly what a masonry view needs."),
            new Note("LAYOUT", "Shortest column wins",
                    "Each card drops into whichever column is currently shortest, so the "
                            + "bottom edge stays roughly even."),
            new Note("TIP", "Responsive by itself",
                    "RXMasonryView derives its card columns directly from the available "
                            + "width, so no outer responsive grid is needed."),
            new Note("PERF", "Virtualized cells",
                    "Only cards intersecting the viewport keep live cells, and those cells "
                            + "are recycled as the gallery scrolls."),
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
            new Note("PERF", "Measure visible cards",
                    "Without a height provider, realized cells are measured and the "
                            + "waterfall is repacked as their natural heights become known."),
            new Note("TIP", "Resize the viewport",
                    "Drag the window narrower or wider and watch the virtualized gallery "
                            + "resolve a new column count and reflow its cards."),
            new Note("CSS", "Pseudo-classes",
                    "The active breakpoint is exposed as a pseudo-class, so a layout can "
                            + "be themed per width from CSS alone."),
            new Note("API", "Span a column",
                    "Return two from the span factory for a feature card; the engine clamps "
                            + "the span to the available column count."),
    };

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane(buildMain());
        root.getStyleClass().add("root");
        root.setTop(buildHeader());

        Scene scene = new Scene(root, 1240.0, 760.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-masonry-responsive-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXMasonryView Responsive Demo");
        primaryStage.show();
    }

    private Region buildHeader() {
        Label title = new Label("Virtualized gallery page");
        title.getStyleClass().add("demo-title");
        Label subtitle = new Label(
                "RXMasonryView virtualizes and responsively reflows the gallery — resize the window to see it react");
        subtitle.getStyleClass().add("demo-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("demo-header");
        return header;
    }

    private Region buildMain() {
        RXMasonryView<Note> gallery = new RXMasonryView<>(FXCollections.observableArrayList(NOTES));
        gallery.getStyleClass().add("virtual-gallery");
        gallery.setColumnWidth(GALLERY_COLUMN_WIDTH);
        gallery.setHgap(GAP);
        gallery.setVgap(GAP);
        gallery.setCellFactory(view -> new NoteCell());
        gallery.setColumnSpanFactory(note -> note == FEATURED_NOTE ? 2 : 1);
        VBox.setVgrow(gallery, Priority.ALWAYS);

        VBox main = new VBox(10.0, sectionLabel("VIRTUALIZED GALLERY"), gallery);
        main.getStyleClass().add("region-main");
        main.setPadding(new Insets(8.0, 24.0, 24.0, 24.0));
        return main;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private static String colorFor(String category) {
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

    private static final class NoteCell extends RXMasonryCell<Note> {

        private final Label category = new Label();
        private final Label title = new Label();
        private final Label body = new Label();
        private final VBox content = new VBox(7.0, category, title, body);

        private NoteCell() {
            getStyleClass().add("virtual-note-card");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            category.getStyleClass().add("note-category");
            category.setMaxWidth(Region.USE_PREF_SIZE);
            title.getStyleClass().add("note-title");
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            body.getStyleClass().add("note-body");
            body.setWrapText(true);
            body.setMaxWidth(Double.MAX_VALUE);
            content.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(Note note, boolean empty) {
            super.updateItem(note, empty);
            if (empty || note == null) {
                category.setText(null);
                category.setStyle(null);
                title.setText(null);
                body.setText(null);
                setGraphic(null);
                return;
            }
            category.setText(note.category());
            category.setStyle("-fx-background-color: " + colorFor(note.category()) + ";");
            title.setText(note.title());
            body.setText(note.body());
            setGraphic(content);
        }
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
