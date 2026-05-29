package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimBlinds;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimBox;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCube;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCube4;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCurtain;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimDissolve;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFlip;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimGallery;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimIris;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimLouver;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimNone;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimPeel;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimRandomTiles;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimRipple;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSelector;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimShatter;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSlide;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimZoom;
import io.github.leewyatt.rxcontrols.carousel.animation.CarouselAnimation;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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

    private static final String CAROUSEL_DEMO_IMAGES = "/io/github/leewyatt/rxcontrols/samples/carousel/images/";
    private static final int CAROUSEL_DEMO_IMAGE_COUNT = 6;

    private static final String[] PAGE_FXML = {
            "/fxml/pane_about.fxml",
            "/fxml/pane_avatar.fxml",
            "/fxml/pane_clip_path_image_view.fxml",
            "/fxml/pane_buttons.fxml",
            null, // carousel demo built dynamically
            "/fxml/pane_digit.fxml",
            "/fxml/pane_highlight_text.fxml",
            "/fxml/pane_field.fxml",
            "/fxml/pane_number_field.fxml",
            "/fxml/pane_svgview.fxml",
            "/fxml/pane_media.fxml",
            "/fxml/pane_css_reference.fxml"
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
            if (fxml == null) {
                return buildCarouselDemoPane();
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

    private Map<String, CarouselAnimation> buildDemoAnimations() {
        Map<String, CarouselAnimation> map = new LinkedHashMap<>();
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
