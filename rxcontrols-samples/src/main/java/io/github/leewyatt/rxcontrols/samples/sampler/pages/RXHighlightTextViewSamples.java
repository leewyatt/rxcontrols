package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXHighlightTextView.MatchRules;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXToggleButton;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXHighlightTextView page: a search field that highlights as you type, keywords
 * switched on and off, dates and prices found by regular expressions, case-sensitive and
 * case-insensitive matching side by side, a list of questions filtered by the matched
 * state, and highlight colors.
 */
public final class RXHighlightTextViewSamples {

    private static final double TEXT_WIDTH = 400.0;

    private static final String RELEASE = "This release makes the list view faster when it holds many rows. "
            + "Sticky headers now follow the theme, and a new option lets groups collapse. "
            + "The tile view and the masonry view share the same smooth scrolling.";

    private static final String INVOICE = "Invoice sent on 2026-03-14 for $1,250.00. A second payment of "
            + "$480.50 is due on 2026-04-01, and a late fee of $25.00 applies after 2026-04-15.";

    private static final String JAVA = "Write JAVA, Java or java: a search that ignores case finds all three.";

    private static final String[][] QUESTIONS = {
            {"How do I reset my password?", "Open the sign-in page and choose Forgot password. We send a link to your email."},
            {"Can I change my email address?", "Yes. Go to Settings, then Account, and enter the new address."},
            {"How long does shipping take?", "Most orders arrive in three to five working days."},
            {"Do you ship abroad?", "We ship to 40 countries. Shipping costs are shown at checkout."},
            {"How do I cancel an order?", "Orders can be cancelled from the order page until they ship."}};

    private RXHighlightTextViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "Selectable text that highlights every match of one or more keywords.",
                List.of("Showing where a search term appears: search results, help articles, logs, filters.",
                        "When nothing needs highlighting, RXTextView or a Label is enough."),
                List.of(
                        SampleCard.large("Search as you type",
                                "Each word in the field becomes a keyword; highlightRanges counts the matches.",
                                search()),
                        SampleCard.large("Toggle keywords",
                                "getKeywords is a plain list, so buttons can add and remove terms.",
                                toggles()),
                        SampleCard.large("Regular expressions",
                                "MatchRules REGEX finds patterns such as dates and prices.",
                                regex()),
                        SampleCard.large("Match case",
                                "The default ignores case; LITERAL_CASE_SENSITIVE matches “Java” only as written.",
                                matchCase()),
                        SampleCard.large("Filter questions",
                                "The read-only matched property hides every question that does not contain the search.",
                                questions()),
                        SampleCard.large("Highlight colors",
                                "highlightFill, or -rx-highlight-fill in CSS, changes the marker color.",
                                colors())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Search as you type ====================

    private static Node search() {
        RXHighlightTextView view = sized(new RXHighlightTextView(RELEASE));
        RXTextField field = searchField("Search the release notes");
        field.textProperty().addListener((obs, old, value) -> view.getKeywords().setAll(words(value)));
        field.setText("view rows");
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int matches = view.getHighlightRanges().size();
            return matches == 1 ? "1 match" : matches + " matches";
        }, view.highlightRangesProperty()));
        return column(field, view, count);
    }

    // ==================== Toggle keywords ====================

    private static Node toggles() {
        RXHighlightTextView view = sized(new RXHighlightTextView(RELEASE));
        HBox buttons = new HBox(8.0);
        for (String keyword : List.of("list", "tile", "masonry", "headers")) {
            RXToggleButton toggle = new RXToggleButton(keyword);
            toggle.selectedProperty().addListener((obs, was, on) -> {
                if (on) {
                    view.getKeywords().add(keyword);
                } else {
                    view.getKeywords().remove(keyword);
                }
            });
            toggle.setSelected(!keyword.equals("tile"));
            buttons.getChildren().add(toggle);
        }
        return column(buttons, view);
    }

    // ==================== Regular expressions ====================

    private static Node regex() {
        RXHighlightTextView view = sized(new RXHighlightTextView(INVOICE));
        view.getStyleClass().add("teal");
        view.setMatchRules(MatchRules.REGEX);
        view.getKeywords().setAll("\\d{4}-\\d{2}-\\d{2}", "\\$[\\d,]+\\.\\d{2}");
        Label patterns = text("\\d{4}-\\d{2}-\\d{2}    \\$[\\d,]+\\.\\d{2}", "value");
        return column(patterns, view);
    }

    // ==================== Match case ====================

    private static Node matchCase() {
        RXHighlightTextView ignore = sized(new RXHighlightTextView(JAVA, "java"));
        RXHighlightTextView exact = sized(new RXHighlightTextView(JAVA, "Java"));
        exact.setMatchRules(MatchRules.LITERAL_CASE_SENSITIVE);
        return column(text("Ignore case: “java”", "hint"), ignore, text("Match case: “Java”", "hint"), exact);
    }

    // ==================== Filter questions ====================

    private static Node questions() {
        RXTextField field = searchField("Search help");
        VBox list = new VBox(10.0);
        for (String[] question : QUESTIONS) {
            Label title = text(question[0], "name");
            RXHighlightTextView answer = sized(new RXHighlightTextView(question[1]));
            field.textProperty().addListener((obs, old, value) -> answer.getKeywords().setAll(words(value)));
            VBox entry = new VBox(2.0, title, answer);
            BooleanBinding shown = field.textProperty().isEmpty().or(answer.matchedProperty());
            entry.visibleProperty().bind(shown);
            entry.managedProperty().bind(shown);
            list.getChildren().add(entry);
        }
        field.setText("order");
        return column(field, list);
    }

    // ==================== Highlight colors ====================

    private static Node colors() {
        RXHighlightTextView yellow = sized(new RXHighlightTextView("The default marker is a bright yellow.", "yellow"));
        RXHighlightTextView pink = sized(new RXHighlightTextView("A pink marker draws the eye to warnings.", "pink"));
        pink.getStyleClass().add("pink");
        RXHighlightTextView sky = sized(new RXHighlightTextView("A sky blue marker feels calm and quiet.", "sky blue"));
        sky.getStyleClass().add("sky");
        return column(yellow, pink, sky);
    }

    // ==================== Helpers ====================

    private static List<String> words(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.asList(value.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    private static RXTextField searchField(String prompt) {
        RXTextField field = new RXTextField();
        field.setPromptText(prompt);
        field.setLeading(icon("search-icon"));
        return field;
    }

    private static RXHighlightTextView sized(RXHighlightTextView view) {
        view.setPrefWidth(TEXT_WIDTH);
        return view;
    }

    private static VBox column(Node... nodes) {
        VBox column = new VBox(12.0, nodes);
        column.setAlignment(Pos.TOP_LEFT);
        column.setPrefWidth(TEXT_WIDTH);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
