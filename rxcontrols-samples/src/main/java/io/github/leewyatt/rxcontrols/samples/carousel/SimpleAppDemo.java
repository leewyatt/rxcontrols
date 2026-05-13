package io.github.leewyatt.rxcontrols.samples.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSlide;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Minimal demo showing RXCarousel as an application page-navigation skeleton.
 *
 * <p>Structure:</p>
 * <ul>
 *   <li>Left sidebar with navigation buttons controlling the main carousel</li>
 *   <li>"Home" page contains a nested carousel (banner auto-play + sub-page navigation)</li>
 *   <li>"Explore" and "Settings" are plain content pages</li>
 * </ul>
 *
 * @see CarouselAppDemo for a full-featured version with CSS styling and page animations
 */
public class SimpleAppDemo extends Application {

    private RXCarousel homeCarousel;

    @Override
    public void start(Stage stage) {
        // ===== Main RXCarousel =====
        RXCarousel mainCarousel = new RXCarousel();
        mainCarousel.setAnimation(new AnimSlide(Orientation.VERTICAL));
        mainCarousel.setAnimationDuration(Duration.millis(400));
        mainCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        mainCarousel.setNavigator(null);

        mainCarousel.setPages(
                createHomePage(),
                createSimplePage("Explore", "Browse content here."),
                createSimplePage("Settings", "App configuration goes here.")
        );

        // ===== Sidebar =====
        VBox sidebar = createSidebar(mainCarousel);

        // ===== Root =====
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(mainCarousel);

        Scene scene = new Scene(root, 700, 450);
        stage.setTitle("CarouselFX - Simple App");
        stage.setScene(scene);
        stage.show();
    }

    // ==================== Sidebar ====================

    private VBox createSidebar(RXCarousel mainCarousel) {
        ToggleGroup group = new ToggleGroup();
        ToggleButton homeBtn = new ToggleButton("Home");
        ToggleButton exploreBtn = new ToggleButton("Explore");
        ToggleButton settingsBtn = new ToggleButton("Settings");

        homeBtn.setToggleGroup(group);
        exploreBtn.setToggleGroup(group);
        settingsBtn.setToggleGroup(group);
        homeBtn.setSelected(true);

        homeBtn.setMaxWidth(Double.MAX_VALUE);
        exploreBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setMaxWidth(Double.MAX_VALUE);

        homeBtn.setOnAction(e -> mainCarousel.goToPage(0));
        exploreBtn.setOnAction(e -> mainCarousel.goToPage(1));
        settingsBtn.setOnAction(e -> mainCarousel.goToPage(2));

        mainCarousel.selectedIndexProperty().addListener((obs, o, n) -> {
            switch (n.intValue()) {
                case 0: homeBtn.setSelected(true); break;
                case 1: exploreBtn.setSelected(true); break;
                case 2: settingsBtn.setSelected(true); break;
            }
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(4, homeBtn, exploreBtn, settingsBtn, spacer);
        sidebar.setPadding(new Insets(8));
        sidebar.setPrefWidth(100);
        sidebar.setStyle("-fx-background-color: #f0f0f0;");
        return sidebar;
    }

    // ==================== Home Page (nested carousel) ====================

    private Node createHomePage() {
        homeCarousel = new RXCarousel();
        homeCarousel.setAnimation(new AnimFade());
        homeCarousel.setAnimationDuration(Duration.millis(300));
        homeCarousel.setArrowDisplayMode(DisplayMode.HIDE);
        homeCarousel.setNavigator(null);
        homeCarousel.setCircular(false);

        // Page 0: front page with banner + feature buttons
        // Pages 1..3: sub-pages
        String[] features = {"Dashboard", "Analytics", "Reports"};
        homeCarousel.setPageCount(1 + features.length);
        homeCarousel.setPageFactory(index -> {
            if (index == 0) {
                return createHomeFront(features);
            }
            return createSubPage(features[index - 1]);
        });

        return new StackPane(homeCarousel);
    }

    private Node createHomeFront(String[] features) {
        // Banner (auto-play nested carousel)
        RXCarousel banner = new RXCarousel();
        banner.setPages(
                createBannerSlide("Welcome", "#3498db"),
                createBannerSlide("75+ Animations", "#e74c3c"),
                createBannerSlide("Open Source", "#2ecc71")
        );
        banner.setAnimation(new AnimFade());
        banner.setAutoPlay(true);
        banner.setAutoPlayInterval(Duration.seconds(2));
        banner.setPrefHeight(150);
        banner.setMaxHeight(150);

        // Feature buttons
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10));
        for (int i = 0; i < features.length; i++) {
            int page = i + 1;
            Button btn = new Button(features[i]);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setOnAction(e -> homeCarousel.goToPage(page));
            buttons.getChildren().add(btn);
        }

        Label desc = new Label(
                "This demo shows RXCarousel as an application page-navigation skeleton "
                        + "with nested carousel support. For a more polished example with "
                        + "CSS styling and page animations, see CarouselAppDemo.");
        desc.setWrapText(true);
        desc.setPadding(new Insets(10));
        desc.setStyle("-fx-text-fill: #666;");

        VBox front = new VBox(banner, buttons, desc);
        VBox.setVgrow(banner, Priority.ALWAYS);
        return front;
    }

    private StackPane createBannerSlide(String text, String color) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 24; -fx-text-fill: white;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color: " + color + ";");
        return pane;
    }

    private Node createSubPage(String name) {
        Label title = new Label(name);
        title.setStyle("-fx-font-size: 20;");

        Button back = new Button("\u2190 Back");
        back.setOnAction(e -> homeCarousel.goToPage(0));

        VBox content = new VBox(10, back, title);
        content.setAlignment(Pos.CENTER);
        return new StackPane(content);
    }

    // ==================== Simple Content Page ====================

    private StackPane createSimplePage(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 22;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #666;");

        VBox box = new VBox(8, titleLabel, descLabel);
        box.setAlignment(Pos.CENTER);
        return new StackPane(box);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
