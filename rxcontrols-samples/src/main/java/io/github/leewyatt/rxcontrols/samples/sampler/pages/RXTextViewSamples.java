package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.RXTextView;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXTextView page: an article paragraph to select and copy, order details with codes
 * people copy, custom selection colors, a read-only text whose sentences are marked from
 * code, a centered quote and a justified paragraph.
 */
public final class RXTextViewSamples {

    private static final double TEXT_WIDTH = 400.0;

    private static final String ARTICLE = "Lighthouses were once kept by families who lived beside the lamp. "
            + "Every evening they trimmed the wick, polished the lens and wound the clockwork that turned the light. "
            + "Today most of them run on their own, but many still stand where ships can see them.";

    private static final String ORDER = "Order number    RX-20480-7731\n"
            + "Tracking code   1Z 999 AA1 01 2345 6784\n"
            + "Ship to         Lee Wyatt, Portland\n"
            + "Total           $128.40";

    private static final String[] SENTENCES = {
            "Preheat the oven to 200 degrees. ",
            "Cut the potatoes into thin slices. ",
            "Toss them with oil, salt and rosemary. ",
            "Bake for twenty-five minutes until golden."};

    private static final String QUOTE = "A good tool disappears in your hand, and all you notice is the work you made with it.";

    private static final String JUSTIFIED = "Justified text lines up with both edges of its column, the way a "
            + "printed book does. The spaces between words grow a little on every line except the last, which "
            + "keeps its natural spacing and starts at the left edge.";

    private RXTextViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A wrapping block of text that people can select and copy but not edit.",
                List.of("Text people may want to copy: messages, order numbers, error details, articles.",
                        "For text nobody copies a Label is enough; for text people edit use a TextArea."),
                List.of(
                        SampleCard.large("Select and copy",
                                "Drag, double-click a word or triple-click a line; selectedText updates as you go and an RXCopyButton copies it.",
                                article()),
                        SampleCard.large("Codes to copy",
                                "Order and tracking numbers that a Label would not let anyone select.",
                                order()),
                        SampleCard.large("Selection colors",
                                "selectionFill and selectedTextFill restyle the selection; selectRange sets it from code.",
                                colors()),
                        SampleCard.large("Marked from code",
                                "With selectable off the mouse cannot select, yet selectRange still marks the current step.",
                                steps()),
                        SampleCard.large("Centered quote",
                                "textAlignment CENTER and a larger lineSpacing for a pull quote.",
                                quote()),
                        SampleCard.large("Justified",
                                "textAlignment JUSTIFY spreads every full line to both edges.",
                                justified())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Select and copy ====================

    private static Node article() {
        RXTextView view = sized(new RXTextView(ARTICLE));
        view.setLineSpacing(4.0);
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int length = view.getSelectedText().length();
            return length == 0 ? "Nothing selected" : length + " characters selected";
        }, view.selectedTextProperty()));
        RXCopyButton copy = new RXCopyButton("Copy");
        copy.setContentFactory(() -> RXCopyButton.textContent(view.getSelectedText()));
        copy.disableProperty().bind(view.selectedTextProperty().isEmpty());
        view.selectRange(0, 11);
        HBox actions = new HBox(8.0, count, spacer(), copy);
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(view, actions);
    }

    // ==================== Codes to copy ====================

    private static Node order() {
        RXTextView view = sized(new RXTextView(ORDER));
        view.getStyleClass().add("mono");
        view.setLineSpacing(6.0);
        RXButton all = plain("Select all");
        all.setOnAction(event -> {
            view.requestFocus();
            view.selectAll();
        });
        VBox receipt = new VBox(view);
        receipt.getStyleClass().add("receipt");
        HBox actions = new HBox(8.0, text("Press Ctrl or Cmd + C to copy", "hint"), spacer(), all);
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(receipt, actions);
    }

    // ==================== Selection colors ====================

    private static Node colors() {
        RXTextView pink = sized(new RXTextView("Select any words in this line to see a pink selection."));
        pink.setSelectionFill(Color.web("#ec4899"));
        pink.setSelectedTextFill(Color.WHITE);
        select(pink, "pink selection");
        RXTextView amber = sized(new RXTextView("This one marks text with a soft amber, like a highlighter pen."));
        amber.setSelectionFill(Color.web("#fde68a"));
        amber.setSelectedTextFill(Color.web("#1f2937"));
        select(amber, "soft amber");
        return column(pink, amber);
    }

    // ==================== Marked from code ====================

    private static Node steps() {
        RXTextView view = sized(new RXTextView(String.join("", SENTENCES)));
        view.setSelectable(false);
        view.setLineSpacing(4.0);
        view.setSelectionFill(Color.web("#14b8a6", 0.28));
        int[] step = {0};
        Label counter = text("", "hint");
        Runnable mark = () -> {
            int start = 0;
            for (int i = 0; i < step[0]; i++) {
                start += SENTENCES[i].length();
            }
            view.selectRange(start, start + SENTENCES[step[0]].trim().length());
            counter.setText("Step " + (step[0] + 1) + " of " + SENTENCES.length);
        };
        mark.run();
        RXButton back = plain("Back");
        back.setOnAction(event -> {
            step[0] = Math.max(0, step[0] - 1);
            mark.run();
        });
        RXButton next = plain("Next step");
        next.setOnAction(event -> {
            step[0] = Math.min(SENTENCES.length - 1, step[0] + 1);
            mark.run();
        });
        HBox actions = new HBox(8.0, counter, spacer(), back, next);
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(view, actions);
    }

    // ==================== Centered quote ====================

    private static Node quote() {
        RXTextView view = sized(new RXTextView(QUOTE));
        view.getStyleClass().add("quote");
        view.setTextAlignment(TextAlignment.CENTER);
        view.setLineSpacing(8.0);
        Label author = text("Lee Wyatt", "hint");
        VBox box = column(view, author);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Justified ====================

    private static Node justified() {
        RXTextView view = sized(new RXTextView(JUSTIFIED));
        view.setTextAlignment(TextAlignment.JUSTIFY);
        view.setLineSpacing(4.0);
        return column(view);
    }

    // ==================== Helpers ====================

    private static void select(RXTextView view, String part) {
        int start = view.getText().indexOf(part);
        view.selectRange(start, start + part.length());
    }

    private static RXTextView sized(RXTextView view) {
        view.setPrefWidth(TEXT_WIDTH);
        return view;
    }

    private static VBox column(Node... nodes) {
        VBox column = new VBox(14.0, nodes);
        column.setPrefWidth(TEXT_WIDTH);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
