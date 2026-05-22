package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.carousel.ImagePane;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demonstrates composing multiple Carousels into a complete application layout.
 *
 * <p>Structure:</p>
 * <ul>
 *   <li>Left sidebar: icon navigation controlling the main carousel (Slide animation)</li>
 *   <li>Main page 1 "Home": banner carousel (auto-play) + feature buttons
 *       that switch to sub-pages via a Box animation</li>
 *   <li>Main page 2 "News": message cards introducing CarouselFX features</li>
 *   <li>Main page 3 "Settings": static settings page</li>
 * </ul>
 *
 * @see SimpleAppDemo for a minimal version without CSS styling
 */
public class CarouselAppDemo extends Application {

    private static final int BANNER_COUNT = 4;
    private static final String[] BANNER_TITLES = {"Welcome to CarouselFX", "70+ Animations", "Fully Customizable", "Open Source"};
    private static final String[] BANNER_SUBTITLES = {
            "A powerful carousel component for JavaFX",
            "Slide, Fade, Flip, Box, Blinds, Wipe and more",
            "CSS styling, pluggable navigators, lifecycle events",
            "Apache 2.0 licensed, free to use, community driven"
    };

    private RXCarousel homePageCarousel;

    // Direct references for animated nodes a
    private final List<Node> homeAnimNodes = new ArrayList<>();
    private final List<Node> newsCards = new ArrayList<>();
    private final List<Node> settingsAnimNodes = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        // ===== Main RXCarousel (page container) =====
        RXCarousel mainCarousel = new RXCarousel();
        mainCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        mainCarousel.setNavigator(null);
        mainCarousel.setAnimationDuration(Duration.millis(400));

        mainCarousel.setPageCount(3);
        mainCarousel.setPageFactory(index -> {
            switch (index) {
                case 0:
                    return createHomePage();
                case 1:
                    return createMessagesPage();
                case 2:
                    return createSettingsPage();
                default:
                    return new StackPane(new Label("Page " + (index + 1)));
            }
        });

        // Page entrance animations via lifecycle events.
        // Filter by event target to ignore events bubbling up from nested carousels.
        mainCarousel.setOnPageOpened(e -> {
            if (e.getTarget() != mainCarousel) {
                return;
            }
            switch (e.getPageIndex()) {
                case 0:
                    // Home page: first OPENED fires before inner carousel is laid out,
                    // so delay to ensure nodes are in the scene graph.
                    Platform.runLater(() -> animateNodes(homeAnimNodes, true));
                    break;
                case 1:
                    animateNodes(newsCards, true);
                    break;
                case 2:
                    animateNodes(settingsAnimNodes, true);
                    break;
            }
        });
        mainCarousel.setOnPageClosed(e -> {
            if (e.getTarget() != mainCarousel) {
                return;
            }
            switch (e.getPageIndex()) {
                case 0:
                    resetNodes(homeAnimNodes);
                    break;
                case 1:
                    resetNodes(newsCards);
                    break;
                case 2:
                    resetNodes(settingsAnimNodes);
                    break;
            }
        });

        // ===== Left Sidebar =====
        VBox sidebar = createSidebar(mainCarousel);

        // ===== Root Layout =====
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(mainCarousel);

        Scene scene = new Scene(root, 960, 600);
        scene.getStylesheets().add(getClass().getResource("carousel-app-demo.css").toExternalForm());
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - App Demo");
        stage.setScene(scene);
        stage.show();
    }

    // ==================== Sidebar ====================

    private VBox createSidebar(RXCarousel mainCarousel) {
        ToggleGroup group = new ToggleGroup();

        ToggleButton homeBtn = createNavButton("Home", "nav-icon-home", group);
        ToggleButton galleryBtn = createNavButton("News", "nav-icon-messages", group);
        ToggleButton settingsBtn = createNavButton("Settings", "nav-icon-settings", group);

        homeBtn.setSelected(true);

        homeBtn.setOnAction(e -> mainCarousel.goToPage(0));
        galleryBtn.setOnAction(e -> mainCarousel.goToPage(1));
        settingsBtn.setOnAction(e -> mainCarousel.goToPage(2));

        mainCarousel.selectedIndexProperty().addListener((obs, o, n) -> {
            switch (n.intValue()) {
                case 0:
                    homeBtn.setSelected(true);
                    break;
                case 1:
                    galleryBtn.setSelected(true);
                    break;
                case 2:
                    settingsBtn.setSelected(true);
                    break;
            }
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(4, homeBtn, galleryBtn, settingsBtn, spacer);
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setAlignment(Pos.TOP_CENTER);
        return sidebar;
    }

    private ToggleButton createNavButton(String text, String iconStyleClass, ToggleGroup group) {
        Region icon = new Region();
        icon.getStyleClass().add(iconStyleClass);

        Label label = new Label(text);

        VBox content = new VBox(2, icon, label);
        content.setAlignment(Pos.CENTER);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setToggleGroup(group);
        btn.getStyleClass().add("app-nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    // ==================== Home Page ====================

    private StackPane createHomePage() {
        homePageCarousel = new RXCarousel();
        homePageCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        homePageCarousel.setNavigator(null);
        homePageCarousel.setCircular(false);


        String[] features = {"Dashboard", "Analytics", "Reports", "Messages"};
        String[] featureDescs = {
                "Real-time overview of system metrics and key performance indicators.",
                "Deep dive into data trends with interactive charts and filters.",
                "Generate and export formatted reports for stakeholders.",
                "Team communication hub with channels and direct messages."
        };
        String[] featureIconStyles = {"feature-icon-dashboard", "feature-icon-analytics",
                "feature-icon-reports", "feature-icon-messages"};

        homePageCarousel.setAnimation(new AnimFade());
        homePageCarousel.setAnimationDuration(Duration.millis(360));

        // Create home front eagerly so homeAnimNodes are populated
        // before mainCarousel's initial OPENED event fires.
        VBox homeFront = createHomeFront(features);

        homePageCarousel.setPageCount(1 + features.length);
        homePageCarousel.setPageFactory(index -> {
            if (index == 0) {
                return homeFront;
            } else {
                return createFeatureSubPage(features[index - 1], featureDescs[index - 1],
                        featureIconStyles[index - 1]);
            }
        });

        return new StackPane(homePageCarousel);
    }

    private VBox createHomeFront(String[] features) {
        // Banner RXCarousel with images
        RXCarousel banner = new RXCarousel();
        banner.setPageCount(BANNER_COUNT);
        banner.setPageFactory(i -> {
            String url = getClass().getResource("images/" + (i + 1) + ".png").toExternalForm();
            ImagePane imagePane = new ImagePane(new Image(url, true));

            Label title = new Label(BANNER_TITLES[i]);
            title.getStyleClass().add("banner-title");

            Label subtitle = new Label(BANNER_SUBTITLES[i]);
            subtitle.getStyleClass().add("banner-subtitle");

            VBox textOverlay = new VBox(4, title, subtitle);
            textOverlay.getStyleClass().add("banner-overlay");
            textOverlay.setMouseTransparent(true);
            textOverlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

            imagePane.getChildren().add(textOverlay);

            StackPane.setMargin(textOverlay, new Insets(0, 0, 10, 15));
            return imagePane;
        });
        banner.setAnimation(new AnimFade());
        banner.setAutoPlay(true);
        banner.setAnimationDuration(Duration.millis(800));
        banner.setAutoPlayInterval(Duration.seconds(1.5));
        banner.setPrefHeight(220);
        banner.setMaxHeight(220);

        // Feature Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(12, 15, 0, 15));
        buttonBar.setOpacity(0);
        buttonBar.setTranslateY(20);
        for (int i = 0; i < features.length; i++) {
            Button btn = createFeatureButton(features[i], i + 1);
            HBox.setHgrow(btn, Priority.ALWAYS);
            buttonBar.getChildren().add(btn);
        }

        // Welcome section
        Label welcomeTitle = new Label("CarouselFX App Demo");
        welcomeTitle.getStyleClass().add("home-welcome-title");

        Label welcomeDesc = new Label(
                "This demo shows how RXCarousel can be used as a general-purpose page container. "
                        + "The sidebar navigates between pages, the banner auto-plays with image transitions. "
                        + "Each feature button uses a different animation - try them all!\n\n"
                        + "For a minimal version without styling, run SimpleAppDemo.");
        welcomeDesc.getStyleClass().add("home-welcome-desc");
        welcomeDesc.setWrapText(true);

        VBox welcomeBox = new VBox(welcomeTitle, welcomeDesc);
        welcomeBox.getStyleClass().add("home-welcome-box");
        welcomeBox.setOpacity(0);
        welcomeBox.setTranslateY(20);
        VBox.setVgrow(welcomeBox, Priority.ALWAYS);

        homeAnimNodes.add(buttonBar);
        homeAnimNodes.add(welcomeBox);

        VBox front = new VBox(banner, buttonBar, welcomeBox);
        front.getStyleClass().add("home-front");
        return front;
    }

    private Button createFeatureButton(String name, int targetPage) {
        Button btn = new Button(name);
        btn.getStyleClass().add("feature-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            if (homePageCarousel != null) {
                homePageCarousel.goToPage(targetPage);
            }
        });
        return btn;
    }

    private BorderPane createFeatureSubPage(String name, String desc,
                                            String iconStyleClass) {
        Region iconRegion = new Region();
        iconRegion.getStyleClass().addAll("feature-icon", iconStyleClass);

        Label title = new Label(name);
        title.getStyleClass().add("feature-title");

        Label descLabel = new Label(desc);
        descLabel.getStyleClass().add("feature-desc");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(400);

        Button backBtn = new Button("\u2190  Back");
        backBtn.getStyleClass().add("feature-back-btn");
        backBtn.setOnAction(e -> {
            if (homePageCarousel != null) {
                homePageCarousel.goToPage(0);
            }
        });

        VBox center = new VBox(10, iconRegion, title, descLabel);
        center.setAlignment(Pos.CENTER);

        HBox topBar = new HBox(backBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));

        BorderPane page = new BorderPane();
        page.setTop(topBar);
        page.setCenter(center);
        page.getStyleClass().add("feature-page");
        return page;
    }

    // ==================== Messages Page ====================

    private StackPane createMessagesPage() {
        String[][] messages = {
                {"Nested Carousels", "This app uses nested Carousels — the sidebar switches main pages via Slide animation, while the Home page has its own Banner and sub-page Carousels."},
                {"70+ Animations", "CarouselFX ships with 70+ built-in transition animations: Slide, Fade, Flip, Box, Blinds, Wipe, Rotate, Zoom, and many more."},
                {"Auto Play", "Enable auto-play with configurable interval and hover-pause. The Home banner above demonstrates this feature."},
                {"Keyboard Navigation", "Press Left/Right arrow keys to switch pages when the RXCarousel has focus. Click any arrow button or navigator to grab focus."},
                {"Custom Navigator", "Replace the built-in dot navigator with your own implementation via the CarouselNavigator interface."},
                {"Resize Safe", "Most animations are progress-based and adapt seamlessly during window resize — no jumpToEnd needed."},
        };

        VBox list = new VBox(8);
        list.setPadding(new Insets(12));
        for (int i = 0; i < messages.length; i++) {
            list.getChildren().add(createMessageCard(messages[i][0], messages[i][1], i));
        }

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(list);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("messages-scroll");

        Label title = new Label("News");
        title.getStyleClass().add("messages-page-title");

        BorderPane wrapper = new BorderPane();
        wrapper.setTop(title);
        wrapper.setCenter(scrollPane);
        wrapper.getStyleClass().add("messages-page");

        return new StackPane(wrapper);
    }

    private HBox createMessageCard(String title, String body, int index) {
        String[] avatarColors = {"#3498DB", "#E74C3C", "#2ECC71", "#9B59B6", "#F39C12", "#1ABC9C"};

        Label avatar = new Label("\u2713");
        avatar.getStyleClass().add("msg-avatar");
        avatar.setStyle("-fx-background-color: " + avatarColors[index % avatarColors.length] + ";");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("msg-title");

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("msg-body");
        bodyLabel.setWrapText(true);

        VBox textBox = new VBox(2, titleLabel, bodyLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox card = new HBox(10, avatar, textBox);
        card.getStyleClass().add("msg-card");
        card.setOpacity(0);
        card.setTranslateX(40);
        newsCards.add(card);
        return card;
    }

    // ==================== Page Content Animations ====================

    /**
     * Animate nodes in with staggered delay. Each node animates from its
     * current (hidden) state to the visible state based on its properties:
     * <ul>
     *   <li>translateX != 0: slide from right (News cards)</li>
     *   <li>translateY != 0: float up (Home content)</li>
     *   <li>scaleX != 1: scale up (Settings content)</li>
     * </ul>
     */
    private void animateNodes(List<Node> nodes, boolean staggered) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            Duration delay = staggered ? Duration.millis(i * 80) : Duration.ZERO;

            FadeTransition fade = new FadeTransition(Duration.millis(300), node);
            fade.setToValue(1);
            fade.setDelay(delay);
            fade.play();

            if (node.getTranslateX() != 0) {
                TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
                slide.setToX(0);
                slide.setDelay(delay);
                slide.setInterpolator(Interpolator.EASE_OUT);
                slide.play();
            } else if (node.getTranslateY() != 0) {
                TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
                slide.setToY(0);
                slide.setDelay(delay);
                slide.setInterpolator(Interpolator.EASE_OUT);
                slide.play();
            } else if (node.getScaleX() != 1.0) {
                ScaleTransition scale = new ScaleTransition(Duration.millis(300), node);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.setDelay(delay);
                scale.setInterpolator(Interpolator.EASE_OUT);
                scale.play();
            }
        }
    }

    private void resetNodes(List<Node> nodes) {
        for (Node node : nodes) {
            node.setOpacity(0);
            // Restore to initial hidden state based on type
            if (node.getStyleClass().contains("msg-card")) {
                node.setTranslateX(40);
            } else if (node.getStyleClass().contains("settings-title")
                    || node.getStyleClass().contains("settings-info")) {
                node.setScaleX(0.85);
                node.setScaleY(0.85);
            } else {
                node.setTranslateY(20);
            }
        }
    }

    // ==================== Settings Page ====================

    private StackPane createSettingsPage() {
        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");
        title.setOpacity(0);
        title.setScaleX(0.85);
        title.setScaleY(0.85);

        Label info = new Label("This is a static page \u2014 no carousel here.\n\n"
                + "It demonstrates that a RXCarousel can serve as a\n"
                + "general-purpose page container, mixing carousels\n"
                + "with regular content pages.");
        info.getStyleClass().add("settings-info");
        info.setOpacity(0);
        info.setScaleX(0.85);
        info.setScaleY(0.85);

        settingsAnimNodes.add(title);
        settingsAnimNodes.add(info);

        VBox content = new VBox(20, title, info);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("settings-page");
        return new StackPane(content);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
