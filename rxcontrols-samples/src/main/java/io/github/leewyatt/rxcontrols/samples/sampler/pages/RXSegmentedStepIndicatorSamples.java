package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXSegmentedStepIndicator page: a checkout flow with step names, an onboarding tour,
 * a step that fills as its fields are completed, video chapters with hover and click,
 * and workout sets on thick segments.
 */
public final class RXSegmentedStepIndicatorSamples {

    private static final List<String> CHECKOUT = List.of("Cart", "Shipping", "Payment", "Review");
    private static final List<String> CHECKOUT_TEXT = List.of(
            "2 items, $86.00. Edit quantities or remove items.",
            "Deliver to 12 Harbor Street. Standard shipping, 3 to 5 days.",
            "Pay with the card ending in 4242.",
            "Check the order once more, then place it.");
    private static final List<String> CHAPTERS = List.of("Intro", "Scene graph", "Layout panes",
            "CSS styling", "Animations", "Wrap-up");
    private static final int SET_COUNT = 5;

    private RXSegmentedStepIndicatorSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A row of step segments: steps before the selected one are complete and the selected one fills partly.",
                List.of("Show where the user is in a short flow: checkout, onboarding, a multi-page form, chapters.",
                        "For a single task's progress without steps, use RXSegmentedProgressBar."),
                List.of(
                        SampleCard.wide("Checkout",
                                "Next and Back move selectedIndex; clicking a finished segment goes back to it, because the indicator only reports the click.",
                                checkout()),
                        SampleCard.large("Onboarding",
                                "A short tour on thin segments; Skip jumps to the last step.",
                                onboarding()),
                        SampleCard.large("Step in progress",
                                "segmentProgress follows how many fields of the current step are filled in.",
                                profileStep()),
                        SampleCard.large("Video chapters",
                                "Point at a segment to see its chapter name; click it to jump there.",
                                chapters()),
                        SampleCard.large("Workout sets",
                                "segmentHeight 14 and segmentGap 8 turn the segments into blocks, one per set.",
                                workout())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Checkout ====================

    private static Node checkout() {
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(CHECKOUT.size());
        steps.setSelectedIndex(1);
        steps.setSegmentProgress(1.0);
        steps.setSegmentHeight(6.0);
        steps.setMaxWidth(Double.MAX_VALUE);

        List<Label> names = new ArrayList<>();
        HBox nameRow = new HBox(4.0);
        for (String name : CHECKOUT) {
            Label label = text(name, "step-name");
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            names.add(label);
            nameRow.getChildren().add(label);
        }

        Label title = text("", "headline");
        Label body = text("", "copy");
        RXButton back = plain("Back");
        RXButton next = solid("Continue", "indigo");
        Runnable update = () -> {
            int index = steps.getSelectedIndex();
            boolean placed = index == CHECKOUT.size() - 1 && steps.getSegmentProgress() >= 1.0 && next.isDisabled();
            for (int i = 0; i < names.size(); i++) {
                names.get(i).getStyleClass().removeAll("done", "current", "todo");
                names.get(i).getStyleClass().add(i < index ? "done" : i == index ? "current" : "todo");
            }
            title.setText(placed ? "Order placed" : CHECKOUT.get(index));
            body.setText(placed ? "We sent the receipt to lee@example.com." : CHECKOUT_TEXT.get(index));
            back.setDisable(index == 0 || placed);
            next.setText(index == CHECKOUT.size() - 1 ? "Place order" : "Continue");
        };
        back.setOnAction(event -> {
            steps.setSelectedIndex(steps.getSelectedIndex() - 1);
            update.run();
        });
        next.setOnAction(event -> {
            if (steps.getSelectedIndex() == CHECKOUT.size() - 1) {
                next.setDisable(true);
            } else {
                steps.setSelectedIndex(steps.getSelectedIndex() + 1);
            }
            update.run();
        });
        steps.setOnSegmentClicked(event -> {
            if (event.getSegmentIndex() < steps.getSelectedIndex() && !next.isDisabled()) {
                steps.setSelectedIndex(event.getSegmentIndex());
                update.run();
            }
        });
        RXButton restart = plain("Start over");
        restart.setOnAction(event -> {
            next.setDisable(false);
            steps.setSelectedIndex(0);
            update.run();
        });
        update.run();

        HBox actions = new HBox(10.0, restart, spacer(), back, next);
        VBox box = new VBox(10.0, nameRow, steps, title, body, actions);
        box.setPrefWidth(540.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(title, new Insets(14.0, 0, 0, 0));
        VBox.setMargin(actions, new Insets(10.0, 0, 0, 0));
        return box;
    }

    // ==================== Onboarding ====================

    private static Node onboarding() {
        String[] titles = {"Welcome aboard", "Invite your team", "Connect a calendar"};
        String[] texts = {"Boards keep every project in one place.",
                "Work goes faster together; add people by email.",
                "See due dates next to your meetings."};
        String[] icons = {"star-icon", "person-icon", "calendar-icon"};

        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(titles.length);
        steps.getStyleClass().add("teal");
        steps.setSegmentProgress(1.0);
        steps.setSegmentHeight(4.0);
        steps.setMaxWidth(Double.MAX_VALUE);

        Region symbol = icon(icons[0]);
        symbol.getStyleClass().addAll("hero", "teal");
        Label title = text(titles[0], "headline");
        Label body = text(texts[0], "muted");
        RXButton skip = plain("Skip");
        RXButton next = solid("Next", "teal");
        Runnable update = () -> {
            int index = steps.getSelectedIndex();
            symbol.getStyleClass().removeAll(List.of(icons));
            symbol.getStyleClass().add(icons[index]);
            title.setText(titles[index]);
            body.setText(texts[index]);
            boolean last = index == titles.length - 1;
            next.setText(last ? "Start over" : "Next");
            skip.setDisable(last);
        };
        next.setOnAction(event -> {
            int index = steps.getSelectedIndex();
            steps.setSelectedIndex(index == titles.length - 1 ? 0 : index + 1);
            update.run();
        });
        skip.setOnAction(event -> {
            steps.setSelectedIndex(titles.length - 1);
            update.run();
        });

        HBox actions = new HBox(8.0, skip, spacer(), next);
        VBox box = new VBox(10.0, steps, symbol, title, body, actions);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().addAll("panel", "padded");
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(symbol, new Insets(18.0, 0, 4.0, 0));
        VBox.setMargin(actions, new Insets(12.0, 0, 0, 0));
        return box;
    }

    // ==================== Step in progress ====================

    private static Node profileStep() {
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(3);
        steps.getStyleClass().add("violet");
        steps.setSelectedIndex(1);
        steps.setMaxWidth(Double.MAX_VALUE);

        TextField name = field("Lee Wyatt", "Full name");
        TextField handle = field("@LeeWyatt_7788", "Handle");
        TextField city = field("", "City");
        List<TextField> fields = List.of(name, handle, city);
        Label filled = text("", "muted");
        Runnable update = () -> {
            long count = fields.stream().filter(f -> !f.getText().isBlank()).count();
            steps.setSegmentProgress(count / (double) fields.size());
            filled.setText(count + " of " + fields.size() + " fields");
        };
        fields.forEach(f -> f.textProperty().addListener((obs, oldValue, newValue) -> update.run()));
        update.run();

        HBox header = new HBox(text("Step 2 of 3: Profile", "copy"), spacer(), filled);
        VBox box = new VBox(10.0, header, steps, name, handle, city);
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(name, new Insets(6.0, 0, 0, 0));
        return box;
    }

    private static TextField field(String value, String prompt) {
        TextField field = new TextField(value);
        field.setPromptText(prompt);
        return field;
    }

    // ==================== Video chapters ====================

    private static Node chapters() {
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(CHAPTERS.size());
        steps.getStyleClass().add("rose");
        steps.setSelectedIndex(2);
        steps.setSegmentProgress(0.4);
        steps.setSegmentHeight(6.0);
        steps.setSegmentGap(3.0);
        steps.setMaxWidth(Double.MAX_VALUE);

        Label playing = text("", "headline");
        Label hover = text("", "muted");
        Runnable showPlaying = () -> playing.setText(
                "Chapter " + (steps.getSelectedIndex() + 1) + ": " + CHAPTERS.get(steps.getSelectedIndex()));
        steps.setOnSegmentEntered(event -> hover.setText("Jump to " + CHAPTERS.get(event.getSegmentIndex())));
        steps.setOnMouseExited(event -> hover.setText("Point at a segment"));
        steps.setOnSegmentClicked(event -> {
            steps.setSelectedIndex(event.getSegmentIndex());
            steps.setSegmentProgress(0.0);
            showPlaying.run();
        });
        showPlaying.run();
        hover.setText("Point at a segment");

        HBox header = new HBox(10.0, icon("play-icon"), playing);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10.0, header, steps, hover);
        box.setPrefWidth(340.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Workout sets ====================

    private static Node workout() {
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(SET_COUNT);
        steps.getStyleClass().add("lime");
        steps.setSelectedIndex(2);
        steps.setSegmentProgress(0.0);
        steps.setSegmentHeight(14.0);
        steps.setSegmentGap(8.0);
        steps.setMaxWidth(Double.MAX_VALUE);

        Label state = text("", "copy");
        RXButton finish = solid("Finish set", "lime");
        Runnable update = () -> {
            boolean done = steps.getSelectedIndex() == SET_COUNT - 1 && steps.getSegmentProgress() >= 1.0;
            state.setText(done ? "Workout done, well played" : "Set " + (steps.getSelectedIndex() + 1)
                    + " of " + SET_COUNT + ": 12 push-ups");
            finish.setText(done ? "Again" : "Finish set");
        };
        finish.setOnAction(event -> {
            int index = steps.getSelectedIndex();
            if (index == SET_COUNT - 1 && steps.getSegmentProgress() >= 1.0) {
                steps.setSelectedIndex(0);
                steps.setSegmentProgress(0.0);
            } else if (index == SET_COUNT - 1) {
                steps.setSegmentProgress(1.0);
            } else {
                steps.setSelectedIndex(index + 1);
            }
            update.run();
        });
        update.run();

        VBox box = new VBox(12.0, state, steps, finish);
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }
}
