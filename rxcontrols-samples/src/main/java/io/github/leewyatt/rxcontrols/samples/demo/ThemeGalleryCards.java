package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.RXLrcLineView;
import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXNumberField;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXRadioToggleButton;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarActionItem;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineItem.Type;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXToggleButton;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
import io.github.leewyatt.rxcontrols.enums.ImageFit;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import java.util.ArrayList;

/**
 * Generated demo-card builders for the theme gallery — one populated, size-capped
 * instance per control. Used by {@link RXThemeGallery}.
 */
final class ThemeGalleryCards {

    private ThemeGalleryCards() {
    }

    /** A control paired with its display name. */
    record NamedControl(String name, Node node) {
    }

    /** Every gallery control, sorted by name. */
    static List<NamedControl> cards() {
        List<NamedControl> list = new ArrayList<>();
        list.add(new NamedControl("RXAudioSpectrum", buildRXAudioSpectrum()));
        list.add(new NamedControl("RXAvatar", buildRXAvatar()));
        list.add(new NamedControl("RXBarSpinner", buildRXBarSpinner()));
        list.add(new NamedControl("RXButton", buildRXButton()));
        list.add(new NamedControl("RXCarousel", buildRXCarousel()));
        list.add(new NamedControl("RXCascader", buildRXCascader()));
        list.add(new NamedControl("RXCascaderView", buildRXCascaderView()));
        list.add(new NamedControl("RXCircularProgressIndicator", buildRXCircularProgressIndicator()));
        list.add(new NamedControl("RXClipPathImageView", buildRXClipPathImageView()));
        list.add(new NamedControl("RXDigit", buildRXDigit()));
        list.add(new NamedControl("RXDotPulse", buildRXDotPulse()));
        list.add(new NamedControl("RXDrawerPane", buildRXDrawerPane()));
        list.add(new NamedControl("RXDualPane", buildRXDualPane()));
        list.add(new NamedControl("RXFillButton", buildRXFillButton()));
        list.add(new NamedControl("RXFillLabel", buildRXFillLabel()));
        list.add(new NamedControl("RXFormattedNumberField", buildRXFormattedNumberField()));
        list.add(new NamedControl("RXHighlightTextView", buildRXHighlightTextView()));
        list.add(new NamedControl("RXImagePane", buildRXImagePane()));
        list.add(new NamedControl("RXImageView", buildRXImageView()));
        list.add(new NamedControl("RXIntegerField", buildRXIntegerField()));
        list.add(new NamedControl("RXLineButton", buildRXLineButton()));
        list.add(new NamedControl("RXLineLabel", buildRXLineLabel()));
        list.add(new NamedControl("RXLrcLineView", buildRXLrcLineView()));
        list.add(new NamedControl("RXLrcView", buildRXLrcView()));
        list.add(new NamedControl("RXNumberField", buildRXNumberField()));
        list.add(new NamedControl("RXPasswordField", buildRXPasswordField()));
        list.add(new NamedControl("RXRadioToggleButton", buildRXRadioToggleButton()));
        list.add(new NamedControl("RXRipplePane", buildRXRipplePane()));
        list.add(new NamedControl("RXSeekBar", buildRXSeekBar()));
        list.add(new NamedControl("RXSegmentedControl", buildRXSegmentedControl()));
        list.add(new NamedControl("RXSegmentedProgressBar", buildRXSegmentedProgressBar()));
        list.add(new NamedControl("RXSegmentedStepIndicator", buildRXSegmentedStepIndicator()));
        list.add(new NamedControl("RXSidebar", buildRXSidebar()));
        list.add(new NamedControl("RXSkeleton", buildRXSkeleton()));
        list.add(new NamedControl("RXSkeletonPane", buildRXSkeletonPane()));
        list.add(new NamedControl("RXTextField", buildRXTextField()));
        list.add(new NamedControl("RXTextView", buildRXTextView()));
        list.add(new NamedControl("RXTimelineView", buildRXTimelineView()));
        list.add(new NamedControl("RXToggleButton", buildRXToggleButton()));
        list.add(new NamedControl("RXTransitionButton", buildRXTransitionButton()));
        list.add(new NamedControl("RXTransitionLabel", buildRXTransitionLabel()));
        list.add(new NamedControl("RXTransitionPane", buildRXTransitionPane()));
        list.add(new NamedControl("RXWaveProgressIndicator", buildRXWaveProgressIndicator()));
        return list;
    }

    private static Node buildRXAudioSpectrum() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.setBandCount(48);
        spectrum.setPrefSize(300.0, 200.0);
        spectrum.setMaxSize(300.0, 200.0);
        int rawBands = 128;
        float[] frame = new float[rawBands];
        double minDb = spectrum.getMinDecibels();
        for (int i = 0; i < rawBands; i++) {
            double position = i / (double) rawBands;
            double envelope = Math.exp(-Math.pow((position - 0.28) * 3.2, 2.0));
            double ripple = 0.5 + 0.5 * Math.sin(position * 22.0);
            double level = Math.max(0.0, Math.min(1.0, 0.25 + 0.75 * envelope * ripple));
            frame[i] = (float) (minDb * (1.0 - level));
        }
        spectrum.updateSpectrum(frame);
        return spectrum;
    }

    private static Node buildRXAvatar() {
        Image image = new Image(ThemeGalleryCards.class.getResource("/scenery/1.png").toExternalForm(), true);
        RXAvatar avatar = new RXAvatar(image);
        avatar.setText("LW");
        avatar.setShapeType(ShapeType.CIRCLE);
        avatar.setPrefSize(96.0, 96.0);
        return avatar;
    }

    private static Node buildRXBarSpinner() {
        RXBarSpinner spinner = new RXBarSpinner(RXBarSpinner.AnimationMode.WAVE);
        spinner.setBarCount(7);
        spinner.setBarWidth(5.0);
        spinner.setBarHeight(36.0);
        spinner.setBarGap(5.0);
        spinner.setMinBarHeightRatio(0.2);
        spinner.setCycleDuration(Duration.millis(1100.0));
        StackPane container = new StackPane(spinner);
        container.setPrefSize(240.0, 120.0);
        container.setMaxSize(240.0, 120.0);
        return container;
    }

    private static Node buildRXButton() {
        Region icon = new Region();
        icon.setMinSize(14, 14);
        icon.setPrefSize(14, 14);
        icon.setMaxSize(14, 14);
        icon.setStyle("-fx-background-color: -fx-text-base-color; -fx-shape: \"M8 0 L10 6 L16 6 L11 9 L13 16 L8 12 L3 16 L5 9 L0 6 L6 6 Z\";");
        RXButton button = new RXButton("Assign incident", icon);
        button.setRippleFill(Color.web("#1e88e5"));
        button.setRippleOpacity(0.30);
        button.setStateOverlayEnabled(true);
        button.setMaxWidth(220);
        button.setPrefWidth(220);
        return button;
    }

    private static Node buildRXCarousel() {
        String[] colors = {"#4A90D9", "#E06C75", "#56B870"};
        Node[] pages = new Node[colors.length];
        for (int i = 0; i < colors.length; i++) {
            StackPane page = new StackPane();
            page.setStyle("-fx-background-color: " + colors[i] + ";");
            Label label = new Label("Page " + (i + 1));
            label.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
            page.getChildren().add(label);
            pages[i] = page;
        }
        RXCarousel carousel = new RXCarousel();
        carousel.setAnimation(new AnimSlide());
        carousel.setAnimationDuration(Duration.millis(500));
        carousel.setPages(pages);
        carousel.setPrefSize(280, 200);
        carousel.setMaxSize(280, 200);
        carousel.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return carousel;
    }

    private static Node buildRXCascader() {
        RXCascaderItem<String> shanghai = new RXCascaderItem<>("Shanghai");
        RXCascaderItem<String> hangzhou = new RXCascaderItem<>("Hangzhou");
        RXCascaderItem<String> china = new RXCascaderItem<>("China");
        china.getChildren().setAll(List.of(shanghai, hangzhou));
        RXCascaderItem<String> tokyo = new RXCascaderItem<>("Tokyo");
        RXCascaderItem<String> osaka = new RXCascaderItem<>("Osaka");
        RXCascaderItem<String> japan = new RXCascaderItem<>("Japan");
        japan.getChildren().setAll(List.of(tokyo, osaka));
        RXCascaderItem<String> asia = new RXCascaderItem<>("Asia");
        asia.getChildren().setAll(List.of(china, japan));
        RXCascaderItem<String> berlin = new RXCascaderItem<>("Berlin");
        RXCascaderItem<String> germany = new RXCascaderItem<>("Germany");
        germany.getChildren().add(berlin);
        RXCascaderItem<String> europe = new RXCascaderItem<>("Europe");
        europe.getChildren().add(germany);

        RXCascader<String> cascader = new RXCascader<>();
        cascader.setPromptText("Choose a location");
        cascader.setClearable(true);
        cascader.setItemTextFactory(value -> value);
        cascader.getRootItems().setAll(List.of(asia, europe));
        cascader.select(shanghai);
        cascader.setMaxWidth(260);
        cascader.setPrefWidth(260);

        StackPane card = new StackPane(cascader);
        card.setPadding(new Insets(16));
        return card;
    }

    private static Node buildRXCascaderView() {
        RXCascaderItem<String> shanghai = new RXCascaderItem<>("Shanghai");
        RXCascaderItem<String> hangzhou = new RXCascaderItem<>("Hangzhou");
        RXCascaderItem<String> disabledCity = new RXCascaderItem<>("Disabled City");
        disabledCity.setDisable(true);
        RXCascaderItem<String> china = new RXCascaderItem<>("China");
        china.getChildren().setAll(List.of(shanghai, hangzhou, disabledCity));
        RXCascaderItem<String> japan = new RXCascaderItem<>("Japan");
        japan.getChildren().setAll(List.of(new RXCascaderItem<>("Tokyo"), new RXCascaderItem<>("Osaka")));
        RXCascaderItem<String> asia = new RXCascaderItem<>("Asia");
        asia.getChildren().setAll(List.of(china, japan));
        RXCascaderItem<String> germany = new RXCascaderItem<>("Germany");
        germany.getChildren().setAll(List.of(new RXCascaderItem<>("Berlin"), new RXCascaderItem<>("Munich")));
        RXCascaderItem<String> europe = new RXCascaderItem<>("Europe");
        europe.getChildren().setAll(List.of(germany));

        RXCascaderView<String> view = new RXCascaderView<>();
        view.setSelectionMode(SelectionMode.MULTIPLE);
        view.setItemTextFactory(value -> value);
        view.setVisibleRowCount(5);
        view.setColumnWidth(150.0);
        view.getRootItems().setAll(List.of(asia, europe));
        view.activate(asia);
        view.activate(china);
        view.setMaxWidth(Region.USE_PREF_SIZE);
        view.setPrefWidth(300.0);
        return view;
    }

    private static Node buildRXCircularProgressIndicator() {
        RXCircularProgressIndicator indicator = new RXCircularProgressIndicator(0.7);
        indicator.setPrefSize(120.0, 120.0);
        indicator.setMaxSize(120.0, 120.0);
        indicator.setProgressStrokeWidth(8.0);
        indicator.setTrackStrokeWidth(8.0);
        indicator.setStrokeLineCap(StrokeLineCap.ROUND);
        indicator.setProgressStroke(Color.web("#616dff"));
        indicator.setTextFactory(progress -> Math.round(progress * 100.0) + "%");
        return indicator;
    }

    private static Node buildRXClipPathImageView() {
        Image image = new Image(ThemeGalleryCards.class.getResource("/scenery/1.png").toExternalForm());
        RXClipPathImageView view = new RXClipPathImageView(image);
        view.setClipSvg(RXClipPathImageView.SHAPE_SHIELD);
        view.setPrefSize(260, 180);
        view.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return view;
    }

    private static Node buildRXDigit() {
        Color lit = Color.web("#22dd66");
        Color unlit = Color.web("#1a3322");
        int[] values = {1, 2, 0, 8};
        HBox readout = new HBox(4.0);
        readout.setAlignment(Pos.CENTER);
        for (int i = 0; i < values.length; i++) {
            if (i == 2) {
                Label colon = new Label(":");
                colon.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #22dd66;");
                readout.getChildren().add(colon);
            }
            RXDigit glyph = new RXDigit(values[i]);
            glyph.setPrefSize(30.0, 60.0);
            glyph.setLitFill(lit);
            glyph.setUnlitFill(unlit);
            readout.getChildren().add(glyph);
        }
        readout.setMaxSize(280.0, 90.0);
        return readout;
    }

    private static Node buildRXDotPulse() {
        RXDotPulse pulse = new RXDotPulse(RXDotPulse.AnimationMode.BOUNCE);
        pulse.setDotCount(4);
        pulse.setDotSize(12.0);
        pulse.setDotGap(8.0);
        pulse.setStyle("-rx-dot-fill: -rx-primary;");
        StackPane box = new StackPane(pulse);
        box.setMinHeight(80.0);
        box.setPrefSize(240.0, 80.0);
        box.setMaxWidth(280.0);
        return box;
    }

    private static Node buildRXDrawerPane() {
        RXDrawerPane drawer = new RXDrawerPane();
        drawer.setSide(Side.RIGHT);
        drawer.setDrawerMode(RXDrawerPane.DrawerMode.PUSH);
        drawer.setPrefDrawerWidth(140.0);
        drawer.setScrimVisible(false);

        Button toggle = new Button("Toggle drawer");
        toggle.setOnAction(e -> drawer.toggle());
        Label heading = new Label("Main content");
        VBox main = new VBox(10.0, heading, toggle);
        main.setAlignment(Pos.CENTER);
        main.setPadding(new Insets(12.0));
        drawer.setContent(main);

        Label title = new Label("Edit item");
        Button close = new Button("Close");
        close.setOnAction(e -> drawer.close());
        VBox panel = new VBox(8.0, title, new TextField("Name"), new TextField("Email"), close);
        panel.setPadding(new Insets(12.0));
        drawer.setDrawerContent(panel);

        drawer.open();
        drawer.setPrefSize(300.0, 200.0);
        drawer.setMaxSize(300.0, 200.0);
        return drawer;
    }

    private static Node buildRXDualPane() {
        Label kpiCaption = new Label("Monthly revenue");
        Label kpiValue = new Label("$48,250");
        Label kpiDelta = new Label("+12.4% vs last month");
        VBox first = new VBox(8.0, kpiCaption, kpiValue, kpiDelta);
        first.setAlignment(Pos.CENTER);

        Label detailTitle = new Label("Revenue breakdown");
        Label detailLine1 = new Label("Subscriptions   $31,400");
        Label detailLine2 = new Label("One-time         $11,850");
        Label detailLine3 = new Label("Add-ons           $5,000");
        VBox second = new VBox(6.0, detailTitle, detailLine1, detailLine2, detailLine3);
        second.setAlignment(Pos.CENTER);

        RXDualPane pane = new RXDualPane(first, second);
        pane.setAnimation(new AnimFlip());
        pane.setAnimationDuration(Duration.millis(500.0));
        pane.setPrefSize(280.0, 200.0);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pane;
    }

    private static Node buildRXFillButton() {
        RXFillButton button = new RXFillButton("Deploy to production");
        Region icon = new Region();
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setStyle("-fx-min-width: 14px; -fx-min-height: 14px; -fx-pref-width: 14px; -fx-pref-height: 14px;"
                + " -fx-background-color: -fx-text-base-color; -fx-shape: \"M2 8 L14 8 M8 2 L8 14\";");
        button.setGraphic(icon);
        button.setFillAnimation(FillAnimation.CENTER_OUT);
        button.setFillCornerRadius(new CornerRadii(6.0));
        button.setStyle("-rx-fill: #616dff; -fx-background-radius: 6px; -fx-border-radius: 6px;");
        button.setAlignment(Pos.CENTER);
        button.setMaxWidth(260.0);
        button.setPrefWidth(260.0);
        return button;
    }

    private static Node buildRXFillLabel() {
        FlowPane tags = new FlowPane(8.0, 8.0);
        tags.setAlignment(Pos.CENTER);
        tags.setPadding(new Insets(12.0));
        tags.setPrefWrapLength(280.0);
        tags.setMaxWidth(280.0);
        String[] texts = {"JavaFX", "Animation", "Open Source", "RXControls"};
        FillAnimation[] sweeps = {
            FillAnimation.LEFT_TO_RIGHT,
            FillAnimation.CENTER_OUT,
            FillAnimation.CIRCLE,
            FillAnimation.EDGES_IN
        };
        for (int i = 0; i < texts.length; i++) {
            RXFillLabel tag = new RXFillLabel(texts[i]);
            tag.setFillAnimation(sweeps[i]);
            tag.setFillCornerRadius(new CornerRadii(12.0));
            tag.setStyle("-fx-padding: 6 14 6 14; -fx-background-color: -rx-primary, derive(-rx-primary, 80%); -fx-background-radius: 12; -fx-background-insets: 0, 1;");
            tags.getChildren().add(tag);
        }
        return tags;
    }

    private static Node buildRXFormattedNumberField() {
        RXFormattedNumberField field = new RXFormattedNumberField(new BigDecimal("1234567.89"));
        field.setNumberFormat(NumberFormat.getNumberInstance(Locale.US));
        field.setPrefColumnCount(14);
        Label badge = new Label("$");
        badge.setPadding(new Insets(0, 6, 0, 8));
        field.setLeft(badge);
        field.setAlignment(Pos.CENTER_RIGHT);
        field.setMaxWidth(280);
        return field;
    }

    private static Node buildRXHighlightTextView() {
        RXHighlightTextView view = new RXHighlightTextView(
                "JavaFX is a modern UI toolkit for desktop and rich client applications. "
                        + "It includes controls, CSS styling, property binding, and a skin "
                        + "architecture for building reusable user interface components.");
        view.getKeywords().setAll("JavaFX", "CSS", "skin", "binding");
        view.setMatchRules(RXHighlightTextView.MatchRules.LITERAL_IGNORE_CASE);
        view.setHighlightFill(Color.web("#fff1a8"));
        view.setLineSpacing(6.0);
        view.setPrefWidth(280.0);
        view.setMaxWidth(Region.USE_PREF_SIZE);
        return view;
    }

    private static Node buildRXImagePane() {
        Image image = new Image(ThemeGalleryCards.class.getResource("/scenery/1.png").toExternalForm());
        RXImagePane pane = new RXImagePane(image);
        pane.setImageFit(ImageFit.COVER);
        pane.setImageRadius(16.0);
        pane.setPrefSize(280.0, 200.0);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Region scrim = new Region();
        scrim.setStyle("-fx-background-color: linear-gradient(to top, rgba(0,0,0,0.65), transparent);");
        scrim.setMouseTransparent(true);

        Label eyebrow = new Label("SCENERY");
        eyebrow.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 10px;");
        Label title = new Label("Overlay children over an encapsulated image");
        title.setWrapText(true);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        VBox caption = new VBox(4.0, eyebrow, title);
        caption.setMaxWidth(220.0);

        pane.getOverlayChildren().addAll(scrim, caption);
        RXImagePane.setAlignment(caption, Pos.BOTTOM_LEFT);
        RXImagePane.setMargin(caption, new Insets(16.0));
        return pane;
    }

    private static Node buildRXImageView() {
        Image image = new Image(
                ThemeGalleryCards.class.getResource("/scenery/1.png").toExternalForm(),
                true);
        RXImageView imageView = new RXImageView(image);
        imageView.setImageFit(ImageFit.COVER);
        imageView.setImageRadius(16.0);
        imageView.setPrefSize(280.0, 180.0);
        imageView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return imageView;
    }

    private static Node buildRXIntegerField() {
        RXIntegerField field = new RXIntegerField(new BigDecimal("25"));
        field.setPromptText("Whole numbers only");
        field.setPrefColumnCount(10);
        field.setMin(new BigDecimal("0"));
        field.setMax(new BigDecimal("999"));
        Label badge = new Label("#");
        badge.getStyleClass().add("slot-badge");
        Label unit = new Label("pcs");
        unit.getStyleClass().add("slot-unit");
        field.setLeft(badge);
        field.setRight(unit);
        field.setMaxWidth(260);
        field.setPrefWidth(260);
        return field;
    }

    private static Node buildRXLineButton() {
        RXLineButton button = new RXLineButton("Explore the docs");
        button.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        button.setLineThickness(2.0);
        button.setLineGap(3.0);
        button.setStyle("-rx-line-color: #616dff;");
        button.setMaxWidth(Region.USE_PREF_SIZE);
        button.setPrefWidth(220.0);
        return button;
    }

    private static Node buildRXLineLabel() {
        RXLineLabel heading = new RXLineLabel("Hover this heading");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        heading.setLineAnimation(LineAnimation.UNDERLINE_CENTER_OUT);
        heading.setLineThickness(2.0);

        FlowPane links = new FlowPane(16.0, 8.0);
        links.setAlignment(Pos.CENTER);
        LineAnimation[] anims = {
                LineAnimation.UNDERLINE_LEFT_TO_RIGHT,
                LineAnimation.UNDERLINE_SLIDE_UP,
                LineAnimation.TOP_BOTTOM_CENTER_OUT,
                LineAnimation.LEFT_RIGHT_CONVERGE
        };
        String[] texts = {"JavaFX", "Animation", "RXControls", "UI"};
        for (int i = 0; i < texts.length; i++) {
            RXLineLabel link = new RXLineLabel(texts[i]);
            link.setLineAnimation(anims[i]);
            link.setAnimationTrigger(AnimationTrigger.HOVER);
            link.setAnimationDuration(Duration.millis(220));
            links.getChildren().add(link);
        }

        VBox box = new VBox(16.0, heading, links);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(280);
        box.setPrefWidth(280);
        return box;
    }

    private static Node buildRXLrcLineView() {
        RXLrcLineView lineView = new RXLrcLineView();
        lineView.setLyrics("""
                [00:00.80]Neon wakes above the avenue
                [00:04.50]Signals fold into the rain
                [00:08.20]Every window keeps a rhythm
                [00:12.00]Every headlight draws a lane
                [00:16.00]We move softly through the static
                """);
        lineView.setAnimated(false);
        lineView.setCurrentTime(Duration.millis(8500.0));
        lineView.setPrefSize(280.0, 88.0);
        lineView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return lineView;
    }

    private static Node buildRXLrcView() {
        RXLrcView lrcView = new RXLrcView();
        lrcView.setLyrics("""
                [00:00.80]Neon wakes above the avenue
                [00:04.50]Signals fold into the rain
                [00:08.20]Every window keeps a rhythm
                [00:12.00]Every headlight draws a lane
                [00:16.00]We move softly through the static
                [00:20.40]Past the towers and the signs
                [00:24.30]When the final chorus rises
                [00:28.10]The city keeps the time
                """);
        lrcView.setCurrentTime(Duration.millis(12_000.0));
        lrcView.setPrefSize(280.0, 220.0);
        lrcView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return lrcView;
    }

    private static Node buildRXNumberField() {
        RXNumberField field = new RXNumberField(new BigDecimal("42.50"));
        field.setPromptText("Type a number");
        field.setMin(new BigDecimal("-100"));
        field.setMax(new BigDecimal("100"));
        field.setPrefColumnCount(12);
        Label unit = new Label("USD");
        unit.setPadding(new Insets(0, 8, 0, 8));
        field.setRight(unit);
        field.setMaxWidth(280);
        return field;
    }

    private static Node buildRXPasswordField() {
        RXPasswordField field = new RXPasswordField("hunter2");
        field.setPromptText("Password");
        field.setMaxWidth(280);
        field.setPrefWidth(280);

        SVGPath lockPath = new SVGPath();
        lockPath.setContent("M8 1a3 3 0 0 0-3 3v3H4a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1h-1V4a3 3 0 0 0-3-3zm2 6H6V4a2 2 0 1 1 4 0v3z");
        lockPath.setFill(Color.web("#6c757d"));
        StackPane lock = new StackPane(lockPath);
        lock.setMinWidth(Region.USE_PREF_SIZE);
        lock.setPrefWidth(16);
        field.setLeft(lock);

        ToggleButton eye = new ToggleButton();
        eye.setFocusTraversable(false);
        eye.getStyleClass().add("eye-toggle");
        SVGPath eyePath = new SVGPath();
        eyePath.setContent("M16 8s-3-5.5-8-5.5S0 8 0 8s3 5.5 8 5.5S16 8 16 8zM1.173 8a13.133 13.133 0 0 1 1.66-2.043C4.12 4.668 5.88 3.5 8 3.5c2.12 0 3.879 1.168 5.168 2.457A13.133 13.133 0 0 1 14.828 8c-.058.087-.122.183-.195.288-.335.48-.83 1.12-1.465 1.755C11.879 11.332 10.119 12.5 8 12.5c-2.12 0-3.879-1.168-5.168-2.457A13.134 13.134 0 0 1 1.172 8zM8 5.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM4.5 8a3.5 3.5 0 1 1 7 0 3.5 3.5 0 0 1-7 0z");
        eyePath.setFill(Color.web("#495057"));
        eye.setGraphic(eyePath);
        field.showPasswordProperty().bind(eye.selectedProperty());
        field.setRight(eye);

        return field;
    }

    private static Node buildRXRadioToggleButton() {
        ToggleGroup group = new ToggleGroup();
        RXRadioToggleButton day = new RXRadioToggleButton("Day");
        RXRadioToggleButton week = new RXRadioToggleButton("Week");
        RXRadioToggleButton month = new RXRadioToggleButton("Month");
        day.setToggleGroup(group);
        week.setToggleGroup(group);
        month.setToggleGroup(group);
        day.setSelected(true);
        HBox row = new HBox(8.0, day, week, month);
        row.setAlignment(Pos.CENTER);
        row.setMaxWidth(280.0);
        row.setPrefWidth(280.0);
        return row;
    }

    private static Node buildRXRipplePane() {
        Label eyebrow = new Label("NOVA TEAM");
        eyebrow.setStyle("-fx-font-size: 11px; -fx-text-fill: #0f766e; -fx-font-weight: bold;");
        Label title = new Label("Incident response");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label metric = new Label("18 open  |  92% SLA");
        metric.setStyle("-fx-text-fill: #475569;");
        VBox body = new VBox(8.0, eyebrow, title, metric);
        body.setAlignment(Pos.CENTER_LEFT);
        body.setStyle("-fx-padding: 18px;");
        StackPane card = new StackPane(body);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16px;");

        RXRipplePane pane = new RXRipplePane(card);
        pane.setRippleFill(Color.web("#0f766e"));
        pane.setRippleOpacity(0.18);
        pane.setRippleCentered(false);
        pane.setAlignment(Pos.CENTER);
        pane.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 16px; -fx-border-radius: 16px;");
        pane.setPrefSize(280.0, 150.0);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pane;
    }

    private static Node buildRXSeekBar() {
        RXSeekBar seekBar = new RXSeekBar(0.72);
        seekBar.setSecondaryProgress(0.36);
        seekBar.setPrefWidth(280.0);
        seekBar.setMaxWidth(280.0);
        StackPane box = new StackPane(seekBar);
        box.setPadding(new Insets(16.0));
        box.setMaxWidth(300.0);
        return box;
    }

    private static Node buildRXSegmentedControl() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        control.getItems().setAll(
                RXSegmentedItem.of("daily", "Daily"),
                RXSegmentedItem.of("weekly", "Weekly"),
                RXSegmentedItem.of("monthly", "Monthly"),
                RXSegmentedItem.of("yearly", "Yearly"));
        control.setValue("weekly");
        control.setMaxWidth(300.0);
        return control;
    }

    private static Node buildRXSegmentedProgressBar() {
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(0.6);
        bar.setSegmentCount(5);
        bar.setSegmentHeight(8.0);
        bar.setSegmentGap(4.0);
        bar.setPrefWidth(260.0);
        bar.setMaxWidth(260.0);
        StackPane card = new StackPane(bar);
        card.setMinHeight(40.0);
        card.setMaxWidth(280.0);
        return card;
    }

    private static Node buildRXSegmentedStepIndicator() {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(5);
        indicator.setSelectedIndex(2);
        indicator.setSegmentProgress(0.45);
        indicator.setSegmentHeight(10.0);
        indicator.setPrefWidth(280.0);
        indicator.setMaxWidth(280.0);
        StackPane card = new StackPane(indicator);
        card.setPadding(new Insets(16.0));
        card.setMaxWidth(300.0);
        return card;
    }

    private static Node buildRXSidebar() {
        RXSidebar sidebar = new RXSidebar();
        Label header = new Label("RX App");
        header.setStyle("-fx-font-weight: bold; -fx-padding: 4 8;");
        sidebar.setHeader(header);
        RXSidebarNavItem dashboard = new RXSidebarNavItem("Dashboard", new Circle(8, Color.web("#5b8def")));
        RXSidebarNavItem inbox = new RXSidebarNavItem("Inbox", new Circle(8, Color.web("#3ec79b")));
        RXSidebarNavItem files = new RXSidebarNavItem("Files", new Circle(8, Color.web("#f2a73b")));
        sidebar.getTopItems().add(new RXSidebarNavItem("Favorites", new Circle(8, Color.web("#e25c5c"))));
        sidebar.getItems().addAll(dashboard, inbox, files);
        RXSidebarActionItem settings = new RXSidebarActionItem("Settings", new Circle(8, Color.web("#9b8cff")));
        sidebar.getBottomItems().addAll(settings, new RXSidebarNavItem("Help", new Circle(8, Color.web("#8a8f99"))));
        sidebar.setFooter(new Label("v1.0"));
        sidebar.setSelectedItem(dashboard);
        sidebar.setExpandedWidth(220);
        sidebar.setMaxSize(240, 230);
        sidebar.setPrefSize(220, 230);
        return sidebar;
    }

    private static Node buildRXSkeleton() {
        RXSkeleton avatar = new RXSkeleton(RXSkeleton.Variant.CIRCULAR);
        avatar.setMinSize(48.0, 48.0);
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);

        RXSkeleton title = new RXSkeleton(RXSkeleton.Variant.ROUNDED_RECTANGLE);
        title.setPrefSize(140.0, 14.0);
        title.setMaxWidth(140.0);

        RXSkeleton paragraph = new RXSkeleton(RXSkeleton.Variant.TEXT);
        paragraph.setLineCount(3);
        paragraph.setLineHeight(10.0);
        paragraph.setLineSpacing(7.0);
        paragraph.setLastLineFillPercent(60.0);
        paragraph.setMaxWidth(Double.MAX_VALUE);

        VBox textColumn = new VBox(8.0, title, paragraph);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(14.0, avatar, textColumn);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(16.0));
        row.setPrefSize(280.0, 110.0);
        row.setMaxSize(280.0, 110.0);
        return row;
    }

    private static Node buildRXSkeletonPane() {
        RXSkeleton avatarBone = new RXSkeleton(Variant.CIRCULAR);
        avatarBone.setMinSize(44.0, 44.0);
        avatarBone.setPrefSize(44.0, 44.0);
        avatarBone.setMaxSize(44.0, 44.0);

        RXSkeleton titleBone = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        titleBone.setPrefSize(110.0, 14.0);
        titleBone.setMaxWidth(110.0);

        RXSkeleton paragraphBone = new RXSkeleton(Variant.TEXT);
        paragraphBone.setLineCount(2);
        paragraphBone.setLineHeight(10.0);
        paragraphBone.setLineSpacing(6.0);
        paragraphBone.setLastLineFillPercent(70.0);

        VBox boneColumn = new VBox(8.0, titleBone, paragraphBone);
        HBox.setHgrow(boneColumn, Priority.ALWAYS);
        HBox skeleton = new HBox(14.0, avatarBone, boneColumn);
        skeleton.setAlignment(Pos.TOP_LEFT);

        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.setPrefSize(44.0, 44.0);

        Label name = new Label("Lee Wyatt");
        Label body = new Label("Today's weather is great. Took a walk and met a neighbor who shared a few useful cafe tips.");
        body.setWrapText(true);

        VBox textColumn = new VBox(6.0, name, body);
        HBox.setHgrow(textColumn, Priority.ALWAYS);
        HBox content = new HBox(14.0, avatar, textColumn);
        content.setAlignment(Pos.TOP_LEFT);

        RXSkeletonPane pane = new RXSkeletonPane(skeleton, content, true);
        pane.setPrefWidth(260.0);
        pane.setMaxWidth(260.0);
        return pane;
    }

    private static Node buildRXTextField() {
        RXTextField field = new RXTextField("Search query");
        field.setPromptText("Search...");
        field.setPrefWidth(260);
        field.setMaxWidth(260);

        SVGPath path = new SVGPath();
        path.setContent("M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001c.03.04.062.078.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1.012 1.012 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z");
        path.setFill(Color.web("#6c757d"));
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(16);
        field.setLeft(icon);

        Button clear = new Button("✕");
        clear.setFocusTraversable(false);
        clear.setOnAction(e -> field.clear());
        field.setRight(clear);

        return field;
    }

    private static Node buildRXTextView() {
        RXTextView textView = new RXTextView(
                "RXTextView is a non-editable, wrapping block of text the user can select and copy. "
                        + "Drag to select, double-click a word, then press Ctrl or Cmd + C to copy.");
        textView.setLineSpacing(6.0);
        textView.setTextAlignment(TextAlignment.LEFT);
        textView.setSelectionFill(Color.web("#0078d7"));
        textView.setSelectedTextFill(Color.WHITE);
        textView.selectRange(0, 11);
        textView.setPrefWidth(280.0);
        textView.setMaxWidth(Region.USE_PREF_SIZE);
        return textView;
    }

    private static Node buildRXTimelineView() {
        RXTimelineItem placed = new RXTimelineItem("Placed");
        placed.setDescription("Order created.");
        placed.setType(Type.PRIMARY);
        placed.setOppositeContent(new Label("09:24"));
        RXTimelineItem paid = new RXTimelineItem("Paid");
        paid.setDescription("Payment confirmed.");
        paid.setType(Type.SUCCESS);
        paid.setOppositeContent(new Label("09:31"));
        RXTimelineItem stock = new RXTimelineItem("Low stock");
        stock.setDescription("One SKU low.");
        stock.setType(Type.WARNING);
        RXTimelineItem delayed = new RXTimelineItem("Delayed");
        delayed.setDescription("Hub closed.");
        delayed.setType(Type.DANGER);
        delayed.setOppositeContent(new Label("08:40"));
        RXTimelineItem notified = new RXTimelineItem("Notified");
        notified.setDescription("Email sent.");
        notified.setType(Type.INFO);
        notified.setOppositeContent(new Label("19:15"));
        RXTimelineView timeline = new RXTimelineView(placed, paid, stock, delayed, notified);
        timeline.setShowOppositeContent(true);
        timeline.setMaxWidth(300);
        timeline.setPrefWidth(300);
        return timeline;
    }

    private static Node buildRXToggleButton() {
        ToggleGroup group = new ToggleGroup();
        RXToggleButton list = new RXToggleButton("List");
        RXToggleButton grid = new RXToggleButton("Grid");
        RXToggleButton gallery = new RXToggleButton("Gallery");
        for (RXToggleButton toggle : new RXToggleButton[]{list, grid, gallery}) {
            toggle.setToggleGroup(group);
        }
        list.setSelected(true);
        HBox box = new HBox(8.0, list, grid, gallery);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(300.0);
        return box;
    }

    private static Node buildRXTransitionButton() {
        RXTransitionButton button = new RXTransitionButton("@  Email");
        button.setPrefSize(240.0, 64.0);
        button.setMaxSize(240.0, 64.0);
        Label alternate = new Label("hello@example.com");
        button.setAlternateContent(alternate);
        button.setAnimation(new AnimSlide(Orientation.VERTICAL));
        return button;
    }

    private static Node buildRXTransitionLabel() {
        List<String> messages = List.of(
                "Build finished in 42 s",
                "3 new comments on your post",
                "Deploy to production succeeded",
                "Battery low: 15% remaining");
        RXTransitionLabel label = new RXTransitionLabel(messages.get(0));
        label.setAnimation(new AnimSlide(Orientation.VERTICAL));
        label.setAlignment(Pos.CENTER);
        label.setAnimationDuration(Duration.millis(450.0));
        label.setPrefSize(280.0, 56.0);
        label.setMaxSize(280.0, 56.0);
        int[] index = {0};
        Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(2.2), event -> {
            index[0] = (index[0] + 1) % messages.size();
            label.setText(messages.get(index[0]));
        }));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        return label;
    }

    private static Node buildRXTransitionPane() {
        Label heading = new Label("Step 1 — Welcome");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label body = new Label("Setting new content plays the configured page transition. Latest content wins.");
        body.setWrapText(true);
        VBox card = new VBox(8.0, heading, body);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16.0));
        RXTransitionPane pane = new RXTransitionPane(card);
        pane.setAnimation(new AnimFade());
        pane.setAnimationDuration(Duration.millis(500.0));
        pane.setPrefSize(280.0, 200.0);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return pane;
    }

    private static Node buildRXWaveProgressIndicator() {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(0.65);
        indicator.setPrefSize(150.0, 150.0);
        indicator.setMaxSize(150.0, 150.0);
        indicator.setFrontWaveFill(Color.web("#1E90FF"));
        indicator.setBackWaveFill(Color.web("#1E90FF", 0.4));
        indicator.setWaveAmplitude(7.0);
        return indicator;
    }

}
