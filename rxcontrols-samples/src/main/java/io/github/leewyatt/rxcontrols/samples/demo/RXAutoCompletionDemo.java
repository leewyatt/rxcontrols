package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAutoCompletion;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;

/**
 * Minimal sample application for {@link RXAutoCompletion}: binding an autocomplete
 * dropdown to plain {@link RXMaterialTextField}s (no dedicated control involved).
 *
 * <p>Shows the two headline scenarios: a rich-object field (books rendered with a
 * custom cover / title / ISBN cell, committing the title) filtered locally, and the
 * asynchronous recipe — an external debounce (a {@link PauseTransition} standing in
 * for a real remote search) pushes results into the live suggestions list, calls
 * {@code showSuggestions()} because arriving results continue the user's typing,
 * and disables client-side re-filtering via {@link RXAutoCompletion#acceptAll()}.
 *
 * <p>For the property explorer see
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXAutoCompletionShowcase}.
 */
public class RXAutoCompletionDemo extends Application {

    private record Book(String title, String author, String isbn) {
    }

    private static final List<Book> CATALOG = List.of(
            new Book("Effective Java", "Joshua Bloch", "978-0134685991"),
            new Book("Java Concurrency in Practice", "Brian Goetz", "978-0321349606"),
            new Book("Clean Code", "Robert C. Martin", "978-0132350884"),
            new Book("Refactoring", "Martin Fowler", "978-0134757599"),
            new Book("Design Patterns", "Erich Gamma", "978-0201633610"),
            new Book("The Pragmatic Programmer", "Andrew Hunt", "978-0135957059"),
            new Book("Domain-Driven Design", "Eric Evans", "978-0321125217"),
            new Book("Working Effectively with Legacy Code", "Michael Feathers", "978-0131177055"),
            new Book("Structure and Interpretation of Computer Programs", "Harold Abelson", "978-0262510875"),
            new Book("Introduction to Algorithms", "Thomas H. Cormen", "978-0262046305"));

    private static final StringConverter<Book> TITLE_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Book book) {
            return book == null ? "" : book.title();
        }

        @Override
        public Book fromString(String value) {
            return null;
        }
    };

    private static final Duration SIMULATED_LATENCY = Duration.millis(400);

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(24);
        root.setStyle("-fx-padding: 32; -fx-background-color: white;");

        // Rich-object binding: local filtering on the display text (the title),
        // custom cover / title / ISBN rows, default write-back = the title.
        RXMaterialTextField localField = new RXMaterialTextField();
        localField.setLabelText("Book title (local catalog)");
        RXAutoCompletion<Book> local = RXAutoCompletion.bind(localField, CATALOG);
        local.setConverter(TITLE_CONVERTER);
        local.setSuggestionCellFactory(bookCellFactory());
        local.setVisibleRowCount(6);

        // Asynchronous recipe: the component stays synchronous; an external debounce
        // (here a PauseTransition standing in for a remote call) pushes results into
        // the live list and opens the dropdown explicitly.
        RXMaterialTextField asyncField = new RXMaterialTextField();
        asyncField.setLabelText("Search books (simulated async)");
        RXAutoCompletion<Book> async = RXAutoCompletion.bind(asyncField);
        async.setConverter(TITLE_CONVERTER);
        async.setSuggestionCellFactory(bookCellFactory());
        // The server already matched fuzzily; a client-side contains filter would
        // wrongly re-filter its results.
        async.setFilterFunction(RXAutoCompletion.acceptAll());

        PauseTransition remoteSearch = new PauseTransition(SIMULATED_LATENCY);
        asyncField.textProperty().addListener((obs, oldText, text) -> {
            remoteSearch.stop();
            if (text == null || text.isEmpty()) {
                async.getSuggestions().clear();
                return;
            }
            remoteSearch.setOnFinished(event -> {
                async.getSuggestions().setAll(searchCatalog(text));
                // Results arriving are the continuation of the user's own typing, so
                // the orchestration opens the dropdown explicitly.
                async.showSuggestions();
            });
            remoteSearch.playFromStart();
        });
        // The commit write-back is itself a text change; cancel the queued re-search
        // so the dropdown does not pop open again right after choosing a book.
        async.setOnAutoCompleted(event -> remoteSearch.stop());

        root.getChildren().setAll(
                new Label("Rich suggestions on a plain Material field"), localField,
                new Label("External debounce → setAll → showSuggestions()"), asyncField);

        Scene scene = new Scene(root, 520, 320);
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXAutoCompletion Demo");
        primaryStage.show();
    }

    // Cover dot + title / ISBN rows. Sub-nodes are cached fields — updateItem runs on
    // every cell re-bind, so the callback only mutates state.
    private static Callback<RXListView<Book>, RXListCell<Book>> bookCellFactory() {
        return view -> new RXListCell<>() {
            private final Circle cover = new Circle(10);
            private final Label title = new Label();
            private final Label isbn = new Label();
            private final VBox text = new VBox(2, title, isbn);
            private final HBox row = new HBox(10, cover, text);

            {
                isbn.setStyle("-fx-font-size: 0.85em; -fx-text-fill: derive(-fx-text-base-color, 45%);");
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    double hue = (book.title().hashCode() % 360 + 360) % 360;
                    cover.setFill(Color.hsb(hue, 0.45, 0.85));
                    title.setText(book.title());
                    isbn.setText(book.author() + " · " + book.isbn());
                    setGraphic(row);
                }
            }
        };
    }

    private static List<Book> searchCatalog(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return CATALOG.stream()
                .filter(book -> book.title().toLowerCase(Locale.ROOT).contains(needle)
                        || book.author().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
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
