package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.AMBER;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.LIME;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ORANGE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.PINK;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ROSE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.TEAL;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.VIOLET;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.iconBadge;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.text;
/**
 * The RXBorderBeam page: a highlighted pricing plan, a deployment card whose beams run
 * while the job runs, a "what's new" panel with a counterclockwise beam, a panel that
 * lights up on hover and search fields that glow while focused.
 */
public final class RXBorderBeamSamples {

    private static final double PLAN_WIDTH = 160.0;

    private RXBorderBeamSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A wrapper that sends one or more fading highlights round the border of its content in an endless loop.",
                List.of("Draw the eye to one card, plan, field or call to action among many.",
                        "It carries no meaning of its own: for progress or validation use an indicator instead."),
                List.of(
                        SampleCard.wide("Highlighted plan",
                                "Two beams in a violet-to-pink gradient, lineWidth 2, mark the recommended plan; the corners follow the card's own radius, and an RXRibbonBadge tags it.",
                                plans()),
                        SampleCard.medium("Running job",
                                "Three fast beams with a 2 second cycleDuration run only while the switch is on.",
                                deploy()),
                        SampleCard.medium("What's new",
                                "reverse sends an orange-to-amber beam counterclockwise, with a slow 10 second lap.",
                                news()),
                        SampleCard.medium("Hover to light up",
                                "active is bound to hover, so the beam runs only while the pointer is over the panel.",
                                hover()),
                        SampleCard.medium("Focused field",
                                "active is bound to the field's focused property, so a beam runs round the field you are typing in.",
                                fields())),
                MotionKit.STYLESHEET);
    }

    // ==================== Highlighted plan ====================

    private static Node plans() {
        Node pro = plan("Pro", "$12", "For growing teams", "violet",
                List.of("Unlimited projects", "Shared calendars", "Priority support"), true);
        RXBorderBeam beam = new RXBorderBeam(pro);
        beam.setBeamFill(gradient(VIOLET, PINK));
        beam.setBeamCount(2);
        beam.setLineWidth(2.0);
        beam.setBeamSize(140.0);
        // The ribbon lies over the card instead of in it, so the three plans line up inside.
        RXRibbonBadge recommended = new RXRibbonBadge(beam, "Hot");
        recommended.setRibbonOffset(15);
        recommended.getStyleClass().add("recommended");

        HBox row = new HBox(14.0,
                plan("Starter", "$0", "For trying things out", "sky",
                        List.of("3 projects", "Personal calendar", "Community help"), false),
                recommended,
                plan("Business", "$29", "For whole companies", "teal",
                        List.of("Everything in Pro", "Single sign-on", "Audit log"), false));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node plan(String name, String price, String audience, String color, List<String> features,
                             boolean recommended) {
        VBox plan = new VBox(6.0);
        plan.getStyleClass().add("plan");
        HBox amount = new HBox(4.0, text(price, "price"), text("/ month", "muted"));
        amount.setAlignment(Pos.BASELINE_LEFT);
        plan.getChildren().addAll(text(name, "plan-name", color), amount, text(audience, "muted"));
        for (String feature : features) {
            HBox line = new HBox(6.0, icon("check-icon", color), text(feature, "copy"));
            line.setAlignment(Pos.CENTER_LEFT);
            plan.getChildren().add(line);
        }
        RXButton choose = recommended ? solid("Choose Pro", "violet") : plain("Choose");
        choose.setMaxWidth(Double.MAX_VALUE);
        plan.getChildren().addAll(spacer(), choose);
        plan.setPrefWidth(PLAN_WIDTH);
        return plan;
    }

    // ==================== Focused field ====================

    private static Node fields() {
        VBox box = new VBox(14.0,
                field("Search files", "search-icon", TEAL),
                field("Ask the assistant", "spark-icon", VIOLET));
        return fixedWidth(box, 240.0);
    }

    private static Node field(String prompt, String iconClass, Color color) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox line = new HBox(8.0, icon(iconClass), input);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("field-box");

        RXBorderBeam beam = new RXBorderBeam(line);
        beam.setBeamFill(color);
        beam.setBeamSize(24.0);
        beam.setLineWidth(1.5);
        beam.activeProperty().bind(input.focusedProperty());
        return beam;
    }

    // ==================== What's new ====================

    private static Node news() {
        HBox head = new HBox(10.0, iconBadge("gift-icon", "orange"), text("What's new in 4.2", "copy-strong"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(head);
        panel.getStyleClass().add("feature-panel");

        RXBorderBeam beam = new RXBorderBeam(panel);
        beam.setBeamFill(gradient(ORANGE, AMBER));
        beam.setReverse(true);
        beam.setCycleDuration(Duration.seconds(10.0));
        beam.setLineWidth(2.0);
        beam.setBeamSize(60.0);
        return fixedWidth(beam, 250.0);
    }

    // ==================== Hover ====================

    private static Node hover() {
        HBox head = new HBox(10.0, iconBadge("touch-icon", "rose"), text("Hover over me", "copy-strong"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(head);
        card.getStyleClass().add("feature-panel");

        RXBorderBeam beam = new RXBorderBeam(card);
        beam.setBeamFill(gradient(ROSE, AMBER));
        beam.setBeamSize(60.0);
        beam.setLineWidth(2.0);
        beam.activeProperty().bind(beam.hoverProperty());
        return fixedWidth(beam, 250.0);
    }

    // ==================== Running job ====================

    private static Node deploy() {
        Label status = text("Deploying v2.4", "muted");
        RXSwitchButton running = new RXSwitchButton();
        running.setSelected(true);
        running.selectedProperty().addListener((obs, old, on) ->
                status.setText(on ? "Deploying v2.4" : "Paused"));

        HBox head = new HBox(10.0, iconBadge("upload-icon", "lime"),
                new VBox(2.0, text("api-gateway", "copy-strong"), status), spacer(), running);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(head);
        card.getStyleClass().add("feature-panel");

        RXBorderBeam beam = new RXBorderBeam(card);
        beam.setBeamFill(gradient(LIME, TEAL));
        beam.setBeamCount(3);
        beam.setBeamSize(60.0);
        beam.setLineWidth(2.0);
        beam.setCycleDuration(Duration.seconds(2.0));
        beam.activeProperty().bind(running.selectedProperty());
        beam.setMaxWidth(Region.USE_PREF_SIZE);
        return fixedWidth(beam, 250.0);
    }

    // ==================== Helpers ====================

    private static Paint gradient(Color from, Color to) {
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, new Stop(0, from), new Stop(1, to));
    }
}
