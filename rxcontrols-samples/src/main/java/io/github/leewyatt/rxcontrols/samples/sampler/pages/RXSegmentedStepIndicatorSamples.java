package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator.TitleSide;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.IntConsumer;

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
    private static final List<String> PROFILE_STEPS = List.of("Account", "Profile", "Review");
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
                "A row of step segments with optional titles: steps before the selected one are complete and the selected one fills partly.",
                List.of("Show where the user is in a short flow: checkout, onboarding, a multi-page form, chapters.",
                        "For a single task's progress without steps, use RXSegmentedProgressBar."),
                List.of(
                        SampleCard.wide("Checkout",
                                "Step names sit above the segments with titleSide TOP. Next and Back move selectedIndex, and placing the order fills the last step, which completes the indicator. Clicking a finished step or its name goes back to it, because the indicator only reports the click.",
                                checkout()),
                        SampleCard.large("Onboarding",
                                "A short tour on thin segments; Skip jumps to the last step.",
                                onboarding()),
                        SampleCard.large("Step in progress",
                                "Each step's segment fills as its fields are filled, and Next moves on once they all are; Save fills the last step, which completes the indicator. CSS shapes each title's mark by the step's state.",
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
        steps.setSegmentHeight(6.0);
        steps.setTitleSide(TitleSide.TOP);
        steps.setTitleFactory(i -> new Label(CHECKOUT.get(i)));
        steps.setMaxWidth(Double.MAX_VALUE);

        Label title = text("", "headline");
        Label body = text("", "copy");
        RXButton back = plain("Back");
        RXButton next = solid("Continue", "indigo");
        Runnable update = () -> {
            int index = steps.getSelectedIndex();
            boolean placed = steps.isCompleted();
            title.setText(placed ? "Order placed" : CHECKOUT.get(index));
            body.setText(placed ? "We sent the receipt to lee@example.com." : CHECKOUT_TEXT.get(index));
            back.setDisable(index == 0 || placed);
            next.setDisable(placed);
            next.setText(index == CHECKOUT.size() - 1 ? "Place order" : "Continue");
        };
        RXButton restart = plain("Start over");
        FocusHandOff.onDisable(back, next);
        FocusHandOff.onDisable(next, restart);
        back.setOnAction(event -> {
            steps.setSelectedIndex(steps.getSelectedIndex() - 1);
            update.run();
        });
        // Placing the order fills the last step, which completes the indicator.
        next.setOnAction(event -> {
            if (steps.getSelectedIndex() == CHECKOUT.size() - 1) {
                steps.setSegmentProgress(1.0);
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
        restart.setOnAction(event -> {
            steps.setSegmentProgress(0.0);
            steps.setSelectedIndex(0);
            update.run();
        });
        update.run();

        HBox actions = new HBox(10.0, restart, spacer(), back, next);
        VBox box = new VBox(10.0, steps, title, body, actions);
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
        FocusHandOff.onDisable(skip, next);
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
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator(PROFILE_STEPS.size());
        steps.getStyleClass().add("violet");
        steps.setTitleFactory(i -> new Label(PROFILE_STEPS.get(i), stepMark()));
        steps.setMaxWidth(Double.MAX_VALUE);

        TextField email = field("lee@example.com", "Email");
        PasswordField password = new PasswordField();
        password.setText("lighthouse42");
        password.setPromptText("Password");
        TextField name = field("Lee Wyatt", "Full name");
        TextField handle = field("@LeeWyatt_7788", "Handle");
        TextField city = field("", "City");
        Label summary = text("", "copy");
        summary.setWrapText(true);
        // The fields that fill each step's segment; Review has none.
        List<List<TextField>> stepFields = List.of(List.of(email, password), List.of(name, handle, city), List.of());
        List<VBox> pages = List.of(new VBox(10.0, email, password), new VBox(10.0, name, handle, city),
                new VBox(10.0, summary));
        // Every page stays managed, so the stack is as tall as the tallest one and the
        // card keeps its height from step to step.
        StackPane content = new StackPane();
        for (VBox page : pages) {
            page.setAlignment(Pos.TOP_LEFT);
            content.getChildren().add(page);
        }

        Label filled = text("", "muted");
        RXButton back = plain("Back");
        RXButton next = solid("Next", "violet");
        boolean[] saved = {false};

        Runnable update = () -> {
            int index = steps.getSelectedIndex();
            List<TextField> fields = stepFields.get(index);
            long count = fields.stream().filter(f -> !f.getText().isBlank()).count();
            boolean review = fields.isEmpty();
            // Review has no fields: saving fills it, which completes the indicator.
            steps.setSegmentProgress(review ? (saved[0] ? 1.0 : 0.0) : count / (double) fields.size());
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).setVisible(i == index);
            }
            summary.setText(saved[0] ? "Profile saved for " + name.getText() + "."
                    : email.getText() + "\n" + name.getText() + "  ·  " + handle.getText() + "  ·  " + city.getText());
            filled.setText(review ? "" : count + " of " + fields.size() + " fields");
            back.setVisible(index > 0);
            back.setDisable(saved[0]);
            next.setText(review ? "Save" : "Next");
            next.setDisable(review ? saved[0] : count < fields.size());
        };
        stepFields.forEach(fields -> fields.forEach(f -> f.textProperty().addListener((obs, o, n) -> update.run())));

        HBox header = new HBox(text("New account", "copy"), spacer(), filled);
        HBox actions = new HBox(10.0, back, spacer(), next);
        VBox box = new VBox(10.0, header, steps, content, actions);
        // A button that is disabled or hidden while it holds focus hands the focus on by
        // traversal, and the page's scroll pane scrolls to wherever it lands. So focus
        // moves first, straight to where the user goes on: the step's first empty field.
        IntConsumer goTo = index -> {
            // Progress first: arriving on the last step with the previous step's full
            // progress would complete the indicator until update() resets it.
            steps.setSegmentProgress(0.0);
            steps.setSelectedIndex(index);
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).setVisible(i == index);
            }
            focusFirstEmpty(stepFields.get(index), next);
            update.run();
        };
        back.setOnAction(event -> goTo.accept(steps.getSelectedIndex() - 1));
        next.setOnAction(event -> {
            if (steps.getSelectedIndex() == PROFILE_STEPS.size() - 1) {
                box.requestFocus();
                saved[0] = true;
                update.run();
            } else {
                goTo.accept(steps.getSelectedIndex() + 1);
            }
        });
        // The account step is already done, so the card opens on Profile with one field left.
        steps.setSelectedIndex(1);
        update.run();
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(content, new Insets(6.0, 0, 0, 0));
        VBox.setMargin(actions, new Insets(6.0, 0, 0, 0));
        return box;
    }



    // Focuses the first empty field, or the first field, or the fallback when there is none.
    private static void focusFirstEmpty(List<TextField> fields, Node fallback) {
        fields.stream().filter(f -> f.getText().isBlank()).findFirst()
                .or(() -> fields.stream().findFirst())
                .map(Node.class::cast)
                .orElse(fallback)
                .requestFocus();
    }

    // A title graphic whose shape and color follow the step's state through the title's pseudo-classes.
    private static Region stepMark() {
        Region mark = new Region();
        mark.getStyleClass().add("step-mark");
        mark.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return mark;
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
            boolean done = steps.isCompleted();
            state.setText(done ? "Workout done, well played" : "Set " + (steps.getSelectedIndex() + 1)
                    + " of " + SET_COUNT + ": 12 push-ups");
            finish.setText(done ? "Again" : "Finish set");
        };
        // Finishing the last set fills it, which completes the indicator.
        finish.setOnAction(event -> {
            int index = steps.getSelectedIndex();
            if (steps.isCompleted()) {
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
