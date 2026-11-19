package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ImageFit;
import io.github.leewyatt.rxcontrols.RXBackdrop;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXImageView;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXBackdrop page: every card is a small screen with a backdrop over its content,
 * behind a filter panel, a photo preview, a busy cover, a sheet from the bottom and a
 * dialog with a slow fade or none.
 */
public final class RXBackdropSamples {

    private static final Duration SLIDE = Duration.millis(220.0);
    private static final double PANEL_WIDTH = 240.0;

    private RXBackdropSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A dimming layer that fades in over content and blocks clicks while it is shown.",
                List.of("Put behind your own panel, sheet or preview so the page underneath steps back.",
                        "For a ready-made modal card use RXDialog; for a side drawer, RXDrawerPane."),
                List.of(
                        SampleCard.canvas("Filter panel",
                                "Filters slides a panel in from the right over the backdrop; a click on the backdrop closes both.",
                                filters()),
                        SampleCard.large("Photo preview",
                                "A darker backdrop through a style class; click a photo to open it, click anywhere to close.",
                                preview()),
                        SampleCard.large("Busy cover",
                                "A light backdrop with dots blocks the form while it is sent.",
                                busy()),
                        SampleCard.large("Share sheet",
                                "A sheet rises from the bottom edge over the backdrop.",
                                sheet()),
                        SampleCard.large("Fade timing",
                                "fadeInDuration 600 ms makes a slow entrance; show(false) skips the fade.",
                                timing())),
                FeedbackKit.STYLESHEET);
    }

    private static RXBackdrop backdrop() {
        RXBackdrop backdrop = new RXBackdrop();
        backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return backdrop;
    }

    // ==================== Filter panel ====================

    private static Node filters() {
        GridPane grid = new GridPane();
        grid.setHgap(12.0);
        grid.setVgap(12.0);
        for (int c = 0; c < 3; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3.0);
            grid.getColumnConstraints().add(column);
        }
        String[][] products = {{"Desk lamp", "$39"}, {"Wool throw", "$58"}, {"Plant pot", "$18"},
                {"Wall clock", "$44"}, {"Linen pillow", "$26"}, {"Ceramic mug", "$12"}};
        String[] colors = {"amber", "rose", "lime", "sky", "violet", "teal"};
        for (int i = 0; i < products.length; i++) {
            Region swatch = new Region();
            swatch.getStyleClass().addAll("swatch", colors[i]);
            swatch.setMaxWidth(Double.MAX_VALUE);
            VBox tile = new VBox(6.0, swatch, text(products[i][0], "copy"), text(products[i][1], "muted"));
            tile.getStyleClass().addAll("panel", "padded-small");
            grid.add(tile, i % 3, i / 3);
        }
        RXButton open = solid("Filters", "indigo");
        open.setGraphic(icon("tune-icon"));
        HBox toolbar = new HBox(10.0, text("Home & living", "headline"), spacer(), open);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox page = new VBox(16.0, toolbar, grid);
        page.setPadding(new Insets(20.0));

        RXBackdrop backdrop = backdrop();
        RXButton apply = solid("Apply", "indigo");
        VBox panel = new VBox(12.0, text("Filters", "headline"),
                new RXCheckBox("In stock"), new RXCheckBox("Free shipping"), new RXCheckBox("On sale"),
                spacerV(), apply);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(PANEL_WIDTH);
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setTranslateX(PANEL_WIDTH + 20.0);
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);

        StackPane screen = FeedbackKit.screen(640.0, 380.0, page, backdrop, panel);
        screen.setMaxWidth(720.0);
        open.setOnAction(event -> {
            backdrop.show();
            slide(panel, 0.0);
        });
        Runnable close = () -> {
            backdrop.hide();
            slide(panel, PANEL_WIDTH + 20.0);
        };
        backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> close.run());
        apply.setOnAction(event -> close.run());
        StackPane canvas = new StackPane(screen);
        canvas.setPadding(new Insets(24.0));
        return canvas;
    }

    private static Region spacerV() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static void slide(Node node, double toX) {
        TranslateTransition transition = new TranslateTransition(SLIDE, node);
        transition.setToX(toX);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.play();
    }

    // ==================== Photo preview ====================

    private static Node preview() {
        RXBackdrop backdrop = backdrop();
        backdrop.getStyleClass().add("dark");
        RXImageView large = new RXImageView();
        large.setImageFit(ImageFit.CONTAIN);
        large.setMouseTransparent(true);
        large.visibleProperty().bind(backdrop.showingProperty());
        StackPane.setMargin(large, new Insets(20.0));

        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 4; i++) {
            Image image = SampleImages.scenery(i + 1);
            RXImageView thumb = new RXImageView(image);
            thumb.setImageFit(ImageFit.COVER);
            thumb.setImageRadius(8.0);
            thumb.setPrefSize(150.0, 90.0);
            thumb.getStyleClass().addAll("clickable", "thumb");
            thumb.setOnMouseClicked(event -> {
                large.setImage(image);
                backdrop.show();
            });
            grid.add(thumb, i % 2, i / 2);
        }
        backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> backdrop.hide());
        return FeedbackKit.screen(400.0, 260.0, grid, backdrop, large);
    }

    // ==================== Busy cover ====================

    private static Node busy() {
        TextField name = new TextField("Lee Wyatt");
        TextField handle = new TextField("@LeeWyatt_7788");
        RXButton send = solid("Send", "teal");
        Label state = text("", "muted");
        HBox actions = new HBox(12.0, send, state);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(8.0, text("Name", "muted"), name, text("Handle", "muted"), handle, actions);
        form.setMaxSize(260.0, Region.USE_PREF_SIZE);

        RXBackdrop backdrop = backdrop();
        backdrop.getStyleClass().add("light");
        RXDotPulse dots = new RXDotPulse();
        dots.getStyleClass().add("teal");
        dots.setDotSize(10.0);
        Label sending = text("Sending", "copy-strong");
        VBox cover = new VBox(12.0, dots, sending);
        cover.setAlignment(Pos.CENTER);
        cover.setMouseTransparent(true);
        cover.visibleProperty().bind(backdrop.showingProperty());

        send.setOnAction(event -> {
            state.setText("");
            backdrop.show();
            PauseTransition wait = new PauseTransition(Duration.seconds(1.8));
            wait.setOnFinished(done -> {
                backdrop.hide();
                state.setText("Sent");
            });
            wait.play();
        });
        return FeedbackKit.screen(400.0, 260.0, form, backdrop, cover);
    }

    // ==================== Share sheet ====================

    private static Node sheet() {
        RXButton share = solid("Share", "pink");
        share.setGraphic(icon("share-icon"));
        VBox page = new VBox(10.0, text("Trip to the coast", "headline"), text("42 photos, 3 videos", "muted"), share);
        page.setAlignment(Pos.CENTER);

        RXBackdrop backdrop = backdrop();
        HBox targets = new HBox(18.0, target("link-icon", "Copy link", "sky"), target("mail-icon", "Email", "orange"),
                target("chat-icon", "Message", "lime"), target("more-icon", "More", "violet"));
        targets.setAlignment(Pos.CENTER);
        VBox panel = new VBox(14.0, text("Share with", "copy-strong"), targets);
        panel.getStyleClass().add("bottom-sheet");
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(panel, Pos.BOTTOM_CENTER);

        StackPane screen = FeedbackKit.screen(400.0, 260.0, page, backdrop, panel);
        panel.translateYProperty().set(200.0);
        share.setOnAction(event -> {
            backdrop.show();
            slideY(panel, 0.0);
        });
        backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            backdrop.hide();
            slideY(panel, 200.0);
        });
        for (Node node : targets.getChildren()) {
            node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                backdrop.hide();
                slideY(panel, 200.0);
            });
        }
        return screen;
    }

    private static Node target(String iconClass, String name, String color) {
        StackPane circle = new StackPane(icon(iconClass));
        circle.getStyleClass().addAll("share-target", color);
        circle.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(6.0, circle, text(name, "muted"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("clickable");
        return box;
    }

    private static void slideY(Node node, double toY) {
        TranslateTransition transition = new TranslateTransition(SLIDE, node);
        transition.setToY(toY);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.play();
    }

    // ==================== Fade timing ====================

    private static Node timing() {
        RXButton slow = plain("Slow fade");
        RXButton instant = plain("No fade");
        HBox buttons = new HBox(10.0, slow, instant);
        buttons.setAlignment(Pos.CENTER);

        RXBackdrop backdrop = backdrop();
        backdrop.setFadeInDuration(Duration.millis(600.0));
        backdrop.setFadeOutDuration(Duration.millis(400.0));
        Label note = text("Click anywhere to close", "copy");
        VBox card = new VBox(6.0, text("Backdrop on", "headline"), note);
        card.getStyleClass().addAll("panel", "padded");
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setMouseTransparent(true);
        card.visibleProperty().bind(backdrop.showingProperty());

        slow.setOnAction(event -> backdrop.show());
        instant.setOnAction(event -> backdrop.show(false));
        backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> backdrop.hide());
        return FeedbackKit.screen(400.0, 260.0, buttons, backdrop, card);
    }
}
