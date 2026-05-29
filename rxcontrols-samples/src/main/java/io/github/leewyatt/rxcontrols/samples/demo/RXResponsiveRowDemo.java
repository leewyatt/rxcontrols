package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveCol;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveRow;
import io.github.leewyatt.rxcontrols.layout.RXRowAlign;
import io.github.leewyatt.rxcontrols.layout.RXRowJustify;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Web-style responsive page demo for {@link RXResponsiveRow}.
 */
public class RXResponsiveRowDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        RXResponsiveRow page = createResponsivePage();
        ScrollPane root = new ScrollPane(page);
        root.getStyleClass().add("responsive-page-scroll");
        root.setFitToWidth(true);
        root.setPannable(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene scene = new Scene(root, 1180.0, 760.0);
        scene.getStylesheets().add(
                getClass().getResource("rx_responsive_row_demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(360.0);
        primaryStage.setMinHeight(520.0);
        primaryStage.setTitle("RXResponsiveRow Demo");
        primaryStage.show();
    }

    private RXResponsiveRow createResponsivePage() {
        RXResponsiveRow row = new RXResponsiveRow();
        row.getStyleClass().add("responsive-row-demo");
        row.setAlign(RXRowAlign.STRETCH);
        row.setJustify(RXRowJustify.START);

        row.getChildren().addAll(
                col(createHeroCard(), spec(24), spec(24), spec(24), spec(13)),
                visibleFromLg(createHeroVisual()),
                col(createStatCard("Monthly revenue", "$128.4K", "+18.2%", "accent-mint"),
                        spec(24), spec(12), spec(8), spec(8)),
                col(createStatCard("Active customers", "8,420", "+642", "accent-coral"),
                        spec(24), spec(12), spec(8), spec(8)),
                col(createStatCard("SLA health", "99.97%", "Live", "accent-gold"),
                        spec(24), spec(24), spec(8), spec(8)),
                col(createChartCard(), spec(24), spec(24), spec(16), spec(15)),
                col(createActivityCard(), spec(24), spec(24), spec(8), spec(9)),
                col(createFeatureCard("Automations", "Route hot accounts before the first reply."),
                        spec(24), spec(12), spec(8), spec(8)),
                col(createFeatureCard("Segments", "Focus each team on the accounts they own."),
                        spec(24), spec(12), spec(8), spec(8)),
                hiddenUntilMd(createWideOnlyCard()));
        return row;
    }

    private Node createHeroCard() {
        Label eyebrow = label("NIMBUS CRM", "eyebrow");
        Label title = label("A calmer workspace for growing customer teams.", "hero-title");
        title.setWrapText(true);

        Label copy = label(
                "Track pipeline, service load, and campaign momentum in one adaptive surface.",
                "hero-copy");
        copy.setWrapText(true);

        Button primary = new Button("Review pipeline");
        primary.getStyleClass().add("primary-button");
        Button secondary = new Button("Open reports");
        secondary.getStyleClass().add("secondary-button");
        HBox actions = new HBox(10.0, primary, secondary);
        actions.getStyleClass().add("hero-actions");
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox metrics = new VBox(8.0,
                miniMetric("Qualified pipeline", "$4.2M"),
                miniMetric("Median response", "14 min"),
                miniMetric("Expansion forecast", "+22%"));
        metrics.getStyleClass().add("hero-metrics");

        VBox card = card("hero-card", eyebrow, title, copy, actions, metrics);
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    private Node createHeroVisual() {
        Image image = new Image(getClass().getResource("/scenery/4.png").toExternalForm(), true);
        RXImageView imageView = new RXImageView(image);
        imageView.setImageRadius(24.0);
        imageView.setPrefSize(420.0, 330.0);
        imageView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label responsiveNote = label(
                "This visual insight is shown only on large viewports and above.",
                "visual-note");
        responsiveNote.setWrapText(true);
        StackPane notePane = new StackPane(responsiveNote);
        notePane.getStyleClass().add("visual-note-pane");
        notePane.setMouseTransparent(true);

        Label caption = label("West coast enterprise launch", "visual-caption");
        Label value = label("73%", "visual-value");
        VBox overlay = new VBox(2.0, caption, value);
        overlay.getStyleClass().add("visual-overlay");
        overlay.setMouseTransparent(true);

        StackPane visual = new StackPane(imageView, notePane, overlay);
        visual.getStyleClass().add("hero-visual");
        StackPane.setAlignment(notePane, Pos.CENTER);
        StackPane.setAlignment(overlay, Pos.BOTTOM_LEFT);
        visual.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return visual;
    }

    private Node createStatCard(String title, String value, String change, String accentClass) {
        Region accent = new Region();
        accent.getStyleClass().addAll("stat-accent", accentClass);

        Label titleLabel = label(title, "stat-title");
        Label valueLabel = label(value, "stat-value");
        Label changeLabel = label(change, "stat-change");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10.0, titleLabel, spacer, accent);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = card("stat-card", header, valueLabel, changeLabel);
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    private Node createChartCard() {
        HBox bars = new HBox(8.0);
        bars.getStyleClass().add("chart-bars");
        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.setFillHeight(false);

        double[] heights = {58.0, 92.0, 76.0, 128.0, 108.0, 152.0, 138.0, 172.0};
        for (double height : heights) {
            Region bar = new Region();
            bar.getStyleClass().add("chart-bar");
            bar.setMinHeight(height);
            bar.setPrefHeight(height);
            bar.setMaxHeight(height);
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            bars.getChildren().add(bar);
        }

        Label title = label("Revenue pulse", "card-title");
        Label subtitle = label("Pipeline value by week", "card-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("card-header");

        VBox card = card("chart-card", header, bars);
        VBox.setVgrow(bars, Priority.ALWAYS);
        return card;
    }

    private Node createActivityCard() {
        Label title = label("Today", "card-title");
        Label subtitle = label("Account activity", "card-subtitle");
        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("card-header");

        VBox list = new VBox(0.0,
                activityItem("Acme renewal", "Contract reviewed"),
                activityDivider(),
                activityItem("Northstar pilot", "Security call booked"),
                activityDivider(),
                activityItem("Studio OS", "Expansion flagged"),
                activityDivider(),
                activityItem("Vertex Retail", "Invoice cleared"));
        list.getStyleClass().add("activity-list");

        VBox card = card("activity-card", header, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return card;
    }

    private Node createFeatureCard(String title, String text) {
        Region icon = new Region();
        icon.getStyleClass().add("feature-icon");

        Label titleLabel = label(title, "feature-title");
        Label textLabel = label(text, "feature-copy");
        textLabel.setWrapText(true);

        VBox card = card("feature-card", icon, titleLabel, textLabel);
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    private Node createWideOnlyCard() {
        Label title = label("Executive view", "feature-title");
        Label copy = label("Board-ready signals for pipeline risk, forecast, and account growth.",
                "feature-copy");
        copy.setWrapText(true);

        HBox chips = new HBox(8.0,
                label("Forecast", "signal-chip"),
                label("Risk", "signal-chip"),
                label("Growth", "signal-chip"));
        chips.getStyleClass().add("signal-chips");

        VBox card = card("wide-card", title, copy, chips);
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    private Node miniMetric(String title, String value) {
        Label titleLabel = label(title, "mini-title");
        Label valueLabel = label(value, "mini-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10.0, titleLabel, spacer, valueLabel);
        row.getStyleClass().add("mini-metric");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node activityItem(String title, String detail) {
        Region dot = new Region();
        dot.getStyleClass().add("activity-dot");

        Label titleLabel = label(title, "activity-title");
        Label detailLabel = label(detail, "activity-detail");
        VBox text = new VBox(1.0, titleLabel, detailLabel);

        HBox item = new HBox(10.0, dot, text);
        item.getStyleClass().add("activity-item");
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private Node activityDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("activity-divider");
        return divider;
    }

    private RXResponsiveCol col(Node content, RXColSpec xs, RXColSpec sm,
                                RXColSpec md, RXColSpec lg) {
        RXResponsiveCol col = new RXResponsiveCol(content);
        col.setXs(xs);
        col.setSm(sm);
        col.setMd(md);
        col.setLg(lg);
        return col;
    }

    private RXResponsiveCol hiddenUntilMd(Node content) {
        RXResponsiveCol col = new RXResponsiveCol(content);
        col.setXs(RXColSpec.builder()
                .span(24)
                .hidden(true)
                .build());
        col.setMd(RXColSpec.builder()
                .span(8)
                .hidden(false)
                .build());
        return col;
    }

    private RXResponsiveCol visibleFromLg(Node content) {
        RXResponsiveCol col = new RXResponsiveCol(content);
        col.setXs(RXColSpec.builder()
                .span(24)
                .hidden(true)
                .build());
        col.setLg(RXColSpec.builder()
                .span(11)
                .hidden(false)
                .build());
        return col;
    }

    private RXColSpec spec(int span) {
        return RXColSpec.of(span);
    }

    private VBox card(String styleClass, Node... children) {
        VBox card = new VBox(14.0, children);
        card.getStyleClass().addAll("content-card", styleClass);
        card.setFillWidth(true);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return card;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
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
