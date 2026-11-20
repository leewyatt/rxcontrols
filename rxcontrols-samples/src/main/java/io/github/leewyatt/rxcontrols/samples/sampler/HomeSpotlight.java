package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.glyph;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.wrapped;

/**
 * The panel beside the home page title: a few components working together on cards
 * about the library itself, its author and a star on GitHub.
 */
final class HomeSpotlight {

    // The panel is half the hero at most, so it pairs its cards up at a smaller width.
    private static final RXBreakpointProfile PROFILE = RXBreakpointProfile.builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 360.0)
            .build();

    private static final Duration STAR_LINK_DELAY = Duration.seconds(2.0);

    private HomeSpotlight() {
    }

    /**
     * Builds the panel.
     *
     * @param controls    the number of controls in the sampler
     * @param transitions the number of page transition presets
     * @param browse      opens the first component page
     * @param openLink    opens a web address in the browser
     * @return the panel
     */
    static Node create(int controls, int transitions, Runnable browse, Consumer<String> openLink) {
        RXRow row = new RXRow();
        row.setBreakpointProfile(PROFILE);
        row.setGutter(14.0);
        row.setRowGap(14.0);
        row.setAlign(RXRow.Align.STRETCH);

        VBox side = new VBox(14.0, starCard(openLink), authorCard(openLink));
        VBox.setVgrow(side.getChildren().get(0), Priority.ALWAYS);
        row.getChildren().addAll(
                half(libraryCard(controls, transitions, browse)),
                half(side));
        row.getStyleClass().add("home-spotlight");
        return row;
    }

    private static RXCol half(Node content) {
        RXCol col = new RXCol(content);
        col.setXs(RXColSpec.of(24));
        col.setSm(RXColSpec.of(12));
        return col;
    }

    // ==================== Library ====================

    private static Node libraryCard(int controls, int transitions, Runnable browse) {
        HBox count = new HBox(6.0, text(String.valueOf(controls), "big-number"), text("components", "caption"));
        count.setAlignment(Pos.BASELINE_LEFT);
        RXButton all = new RXButton("See all components");
        all.getStyleClass().add("violet-button");
        all.setMaxWidth(Double.MAX_VALUE);
        all.setOnAction(event -> browse.run());

        Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);
        VBox card = new VBox(10.0,
                text("RXControls", "brand"),
                count,
                text("Apache 2.0 · open source", "caption"),
                check(transitions + " page transitions"),
                check("Virtualized list, tile and masonry views"),
                check("Responsive layout containers"),
                gap,
                all);
        card.getStyleClass().addAll("spotlight-card", "library-card");

        RXBorderBeam beam = new RXBorderBeam(card);
        beam.getStyleClass().add("violet-beam");
        beam.setLineWidth(2.0);
        beam.setBeamSize(120.0);
        beam.setCycleDuration(Duration.seconds(4.0));

        // The ribbon sits over the beam, so the beam runs round the card under it.
        RXRibbonBadge ribbon = new RXRibbonBadge(beam, "Open source");
        ribbon.getStyleClass().add("violet-ribbon");
        ribbon.setRibbonOffset(16.0);
        return ribbon;
    }

    private static Node check(String value) {
        HBox line = new HBox(8.0, glyph("check-glyph"), wrapped(value, "copy"));
        line.setAlignment(Pos.TOP_LEFT);
        return line;
    }

    // ==================== Star ====================

    private static Node starCard(Consumer<String> openLink) {
        Label hint = wrapped("A star helps others find it.", "caption");
        RXReactionButton star = new RXReactionButton("Star on GitHub");
        star.getStyleClass().addAll(RXReactionButton.STYLE_CLASS_STAR, "star-button");
        star.setBurstAnimation(BurstAnimation.SPARKLES);
        // The browser opens once the burst has played; un-starring before then cancels it.
        PauseTransition delay = new PauseTransition(STAR_LINK_DELAY);
        delay.setOnFinished(event -> openLink.accept(SamplerHome.REPO_URL));
        star.selectedProperty().addListener((obs, was, starred) -> {
            star.setText(starred ? "Starred" : "Star on GitHub");
            hint.setText(starred ? "Thank you!" : "A star helps others find it.");
            if (starred) {
                delay.playFromStart();
            } else {
                delay.stop();
            }
        });

        VBox card = new VBox(10.0, wrapped("Enjoying RXControls?", "strong"), star, hint);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("spotlight-card");
        return card;
    }

    // ==================== Author ====================

    private static Node authorCard(Consumer<String> openLink) {
        HBox head = new HBox(6.0, text(SamplerHome.AUTHOR, "name-pill"), text("Author", "caption"), spacer(),
                iconButton("github-glyph", "GitHub profile", () -> openLink.accept(SamplerHome.GITHUB_URL)));
        head.setAlignment(Pos.CENTER_LEFT);
        RXChipSet roles = new RXChipSet(new RXChip("Freelancer"), new RXChip("JavaFX consultant"));
        roles.setHgap(6.0);
        roles.setVgap(6.0);
        VBox card = new VBox(10.0, head, roles);
        card.getStyleClass().addAll("spotlight-card", "author-card");

        RXBorderBeam beam = new RXBorderBeam(card);
        beam.getStyleClass().add("green-beam");
        beam.setBeamCount(2);
        beam.setLineWidth(2.0);
        beam.setBeamSize(60.0);
        beam.setCycleDuration(Duration.seconds(3.0));
        return beam;
    }
}
