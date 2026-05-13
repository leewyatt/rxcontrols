package io.github.leewyatt.rxcontrols.samples.controller;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.ImagePane;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimBlinds;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCube;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFlip;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimIris;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimNone;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSlide;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimZoom;
import io.github.leewyatt.rxcontrols.carousel.animation.CarouselAnimation;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Callback;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController {

    private static final String CAROUSEL_DEMO_IMAGES = "/io/github/leewyatt/rxcontrols/samples/carousel/images/";
    private static final int CAROUSEL_DEMO_IMAGE_COUNT = 6;

    private static final String[] PAGE_FXML = {
            "/fxml/pane_about.fxml",
            "/fxml/pane_avatar.fxml",
            "/fxml/pane_buttons.fxml",
            null, // index 3: carousel demo built dynamically
            "/fxml/pane_digit.fxml",
            "/fxml/pane_highlight_text.fxml",
            "/fxml/pane_field.fxml",
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
        mainCarousel.setAnimation(new AnimNone());
        mainCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        mainCarousel.setNavigatorDisplayMode(DisplayMode.HIDE);
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
                mainCarousel.goToPage(index, false);
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
            Image image = new Image(getClass().getResourceAsStream(
                    CAROUSEL_DEMO_IMAGES + (index + 1) + ".png"));
            return new ImagePane(image);
        });
        demoCarousel.setAnimation(new AnimSlide());

        Map<String, CarouselAnimation> animations = buildDemoAnimations();
        ChoiceBox<String> animationBox = new ChoiceBox<>();
        animationBox.getItems().addAll(animations.keySet());
        animationBox.getSelectionModel().select("Slide");
        animationBox.setOnAction(e ->
                demoCarousel.setAnimation(animations.get(animationBox.getValue())));

        Label pageLabel = new Label();
        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Page " + (demoCarousel.getSelectedIndex() + 1) + " / " + demoCarousel.getPageCount(),
                demoCarousel.selectedIndexProperty(), demoCarousel.pageCountProperty()));

        Button prevBtn = new Button("Previous");
        prevBtn.setOnAction(e -> demoCarousel.previous());
        Button nextBtn = new Button("Next");
        nextBtn.setOnAction(e -> demoCarousel.next());

        HBox controls = new HBox(10,
                new Label("Animation:"), animationBox,
                prevBtn, nextBtn,
                pageLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8, 12, 8, 12));

        BorderPane root = new BorderPane();
        root.setTop(controls);
        StackPane carouselContainer = new StackPane(demoCarousel);
        carouselContainer.setPadding(new Insets(0, 12, 12, 12));
        VBox.setVgrow(carouselContainer, Priority.ALWAYS);
        root.setCenter(carouselContainer);
        return root;
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
