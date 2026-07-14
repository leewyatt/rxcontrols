package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimBox;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube4;
import io.github.leewyatt.rxcontrols.animation.page.AnimCurtain;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGallery;
import io.github.leewyatt.rxcontrols.animation.page.AnimIris;
import io.github.leewyatt.rxcontrols.animation.page.AnimLouver;
import io.github.leewyatt.rxcontrols.animation.page.AnimNone;
import io.github.leewyatt.rxcontrols.animation.page.AnimPeel;
import io.github.leewyatt.rxcontrols.animation.page.AnimRandomTiles;
import io.github.leewyatt.rxcontrols.animation.page.AnimRipple;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.DisplayMode;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

public class MainController {

    private static final String CAROUSEL_DEMO_IMAGES = "/io/github/leewyatt/rxcontrols/samples/demo/carousel/images/";
    private static final int CAROUSEL_DEMO_IMAGE_COUNT = 6;
    private static final String CAROUSEL_DEMO_PAGE = "rx:carousel-demo";
    private static final String SPEED_DIAL_DEMO_PAGE = "rx:speed-dial-demo";

    private static final String[] PAGE_FXML = {
            "/fxml/pane-about.fxml",
            "/fxml/pane-avatar.fxml",
            "/fxml/pane-clip-path-image-view.fxml",
            "/fxml/pane-buttons.fxml",
            SPEED_DIAL_DEMO_PAGE,
            CAROUSEL_DEMO_PAGE,
            "/fxml/pane-digit.fxml",
            "/fxml/pane-highlight-text.fxml",
            "/fxml/pane-field.fxml",
            "/fxml/pane-number-field.fxml",
            "/fxml/pane-media.fxml",
            "/fxml/pane-menu.fxml",
            "/fxml/pane-css-reference.fxml"
    };

    @FXML
    private HBox topBar;

    @FXML
    private VBox navBar;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private ToggleGroup navGroup;

    @FXML
    private RXCarousel mainCarousel;

    private double offsetX, offsetY;

    @FXML
    void initialize() {
        mainCarousel.setAnimation(new AnimFlip());
        mainCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        mainCarousel.setNavigator(null);
        mainCarousel.setAutoPlay(false);
        mainCarousel.setHoverPause(false);
        mainCarousel.setPageCount(PAGE_FXML.length);
        mainCarousel.setPageFactory(buildPageFactory());

        navGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
            if (nv == null) {
                return;
            }
            int index = navGroup.getToggles().indexOf(nv);
            if (index >= 0 && index < mainCarousel.getPageCount()) {
                mainCarousel.goToPage(index);
            }
        });
    }

    private Callback<Integer, Node> buildPageFactory() {
        return index -> {
            String fxml = PAGE_FXML[index];
            if (CAROUSEL_DEMO_PAGE.equals(fxml)) {
                return buildCarouselDemoPane();
            }
            if (SPEED_DIAL_DEMO_PAGE.equals(fxml)) {
                return buildSpeedDialDemoPane();
            }
            try {
                return FXMLLoader.load(getClass().getResource(fxml));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    // ==================== Embedded Carousel Demo ====================

    private Pane buildCarouselDemoPane() {
        RXCarousel demoCarousel = new RXCarousel();
        demoCarousel.setPageCount(CAROUSEL_DEMO_IMAGE_COUNT);
        demoCarousel.setPageFactory(index -> {
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(
                    CAROUSEL_DEMO_IMAGES + (index + 1) + ".png")));
            RXImagePane imagePane = new RXImagePane(image);
            return imagePane;
        });
        demoCarousel.setArrowDisplayMode(DisplayMode.SHOW);
        demoCarousel.setAnimationDuration(Duration.seconds(2));
        demoCarousel.setAutoPlayInterval(Duration.seconds(1));
        demoCarousel.setHoverPause(false);
        demoCarousel.setAutoPlay(true);

        demoCarousel.setAnimation(AnimSelector.random(
                new AnimRandomTiles(), new AnimPeel(),  new AnimGallery(), new AnimCurtain(), new AnimCube4(),
                new AnimDissolve(), new AnimShatter(), new AnimRipple(5), new AnimCube(), new AnimBox(), new AnimLouver()));

        Rectangle rect = new Rectangle();
        rect.widthProperty().bind(demoCarousel.widthProperty());
        rect.heightProperty().bind(demoCarousel.heightProperty());
        rect.setArcWidth(50);
        rect.setArcHeight(50);
        demoCarousel.setClip(rect);

        StackPane carouselContainer = new StackPane(demoCarousel);
        carouselContainer.setStyle("-fx-padding: 80px 50px;");
        return carouselContainer;
    }

    // ==================== Embedded Speed Dial Demo ====================

    private Pane buildSpeedDialDemoPane() {
        Label title = new Label("Launch checklist");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        Label subtitle = new Label("One main FAB exposes related document actions.");
        subtitle.setStyle("-fx-text-fill: #64748b;");
        Label status = new Label("No quick action selected");
        status.setStyle("-fx-text-fill: #64748b; -fx-padding: 8 0 0 0;");

        VBox rows = new VBox(10.0,
                speedDialRow("Verify migration dry run", "Database owner"),
                speedDialRow("Freeze billing imports", "Finance system"),
                speedDialRow("Prepare status page", "Support"),
                speedDialRow("Schedule rollout window", "Platform"));
        VBox card = new VBox(18.0, new VBox(2.0, title, subtitle), rows, status);
        card.setMaxWidth(430.0);
        card.setStyle("-fx-background-color: white;"
                + " -fx-background-radius: 8px;"
                + " -fx-border-color: #d8dee9;"
                + " -fx-border-radius: 8px;"
                + " -fx-padding: 28px;"
                + " -fx-effect: dropshadow(gaussian, rgba(31, 41, 55, 0.16), 22, 0.16, 0, 8);");

        RXSpeedDial dial = new RXSpeedDial(speedDialIcon("M8 2 H11 V8 H17 V11 H11 V17 H8 V11 H2 V8 H8 Z"),
                speedDialAction(status, "Save", "M3 8 L7 12 L14 4", "Saved draft"),
                speedDialAction(status, "Duplicate", "M4 3 H11 V5 H6 V14 H4 Z M7 6 H14 V17 H7 Z",
                        "Duplicated checklist"),
                speedDialAction(status, "Share",
                        "M13 5 A2 2 0 1 0 13 4 M6 9 A2 2 0 1 0 6 8 M13 14 A2 2 0 1 0 13 13 M8 9 L11 6 M8 10 L11 13",
                        "Share link copied"),
                speedDialAction(status, "Delete", "M5 6 H14 M7 6 V15 M12 6 V15 M6 6 L7 17 H12 L13 6 M8 4 H11",
                        "Moved to trash"));
        dial.setOpenIcon(speedDialIcon("M4 4 L16 16 M16 4 L4 16"));
        dial.setLabelMode(RXSpeedDial.LabelMode.PERSISTENT);
        dial.setCloseOnFocusLoss(false);
        dial.setCloseOnClickOutside(false);

        StackPane page = new StackPane(card, dial);
        page.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8fafc, #eef2ff);");
        StackPane.setAlignment(dial, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(dial, new Insets(0.0, 34.0, 34.0, 0.0));
        return page;
    }

    private Node speedDialRow(String title, String owner) {
        Label name = new Label(title);
        name.setStyle("-fx-text-fill: #1f2937; -fx-font-weight: bold;");
        Label ownerLabel = new Label(owner);
        ownerLabel.setStyle("-fx-text-fill: #64748b;");
        HBox row = new HBox(12.0, name, ownerLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #f8fafc;"
                + " -fx-background-radius: 6px;"
                + " -fx-border-color: #e5e7eb;"
                + " -fx-border-radius: 6px;"
                + " -fx-padding: 12px 14px;");
        return row;
    }

    private RXSpeedDialAction speedDialAction(Label status, String text, String shape, String message) {
        return new RXSpeedDialAction(text, speedDialIcon(shape), event -> status.setText(message));
    }

    private Region speedDialIcon(String shape) {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setStyle("-fx-shape: \"" + shape + "\";");
        icon.setMinSize(18.0, 18.0);
        icon.setPrefSize(18.0, 18.0);
        icon.setMaxSize(18.0, 18.0);
        return icon;
    }

    private Map<String, PageAnimation> buildDemoAnimations() {
        Map<String, PageAnimation> map = new LinkedHashMap<>();
        map.put("Slide", new AnimSlide());
        map.put("None", new AnimNone());
        map.put("Fade", new AnimFade());
        map.put("Zoom", new AnimZoom());
        map.put("Flip", new AnimFlip());
        map.put("Cube", new AnimCube());
        map.put("Blinds", new AnimBlinds());
        map.put("Iris", new AnimIris());
        return map;
    }

    @FXML
    void exitAction(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    void topBarDraggedAction(MouseEvent event) {
        Window window = topBar.getScene().getWindow();
        window.setX(event.getScreenX() - offsetX);
        window.setY(event.getScreenY() - offsetY);
    }

    @FXML
    void topBarPressedAction(MouseEvent event) {
        offsetX = event.getSceneX();
        offsetY = event.getSceneY();
    }
}
