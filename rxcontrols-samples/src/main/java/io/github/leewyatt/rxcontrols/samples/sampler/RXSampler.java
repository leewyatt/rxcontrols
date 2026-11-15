package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.samples.demo.RXThemeGallery;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The component sampler: a searchable, grouped list of every component on the left and
 * the selected component's page on the right, with the playground of its showcase, a
 * generated CSS reference and a launcher for its demo. Only the page on display and a
 * few recently visited ones are kept, so the scene stays small however many components
 * the library has. {@link RXThemeGallery} remains the one-screen overview for development.
 */
public class RXSampler extends Application {

    private static final Logger LOG = Logger.getLogger(RXSampler.class.getName());
    private static final int PAGE_CACHE_SIZE = 4;

    private final List<SamplerEntry> entries = SamplerCatalog.entries();
    private final Map<SamplerEntry, Node> pages = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SamplerEntry, Node> eldest) {
            return size() > PAGE_CACHE_SIZE;
        }
    };

    private final RXTransitionPane host = new RXTransitionPane();
    private final Label crumbCategory = new Label();
    private final Label crumbName = new Label();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label position = new Label();

    private SamplerNav nav;
    private SamplerEntry current;

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        nav = new SamplerNav(entries);
        nav.currentProperty().addListener((obs, old, entry) -> show(entry));

        host.getStyleClass().add("page-host");
        host.setAnimation(new AnimFade());
        host.setAnimationDuration(Duration.millis(180.0));

        BorderPane main = new BorderPane(host);
        main.getStyleClass().add("sampler-main");
        main.setTop(buildTopBar());
        main.setBottom(buildPager());

        BorderPane root = new BorderPane(main);
        root.getStyleClass().add("sampler");
        root.setLeft(nav);

        Scene scene = new Scene(root, 1360.0, 880.0);
        scene.getStylesheets().addAll(RXShowcaseApplication.shellStylesheet(),
                RXSampler.class.getResource("rx-sampler.css").toExternalForm());
        // Some page animations of the carousel showcase rotate in 3D.
        scene.setCamera(new PerspectiveCamera());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                nav::focusSearch);

        show(entries.get(0));
        stage.setScene(scene);
        stage.setTitle("RxControls Sampler");
        stage.setMinWidth(960.0);
        stage.setMinHeight(600.0);
        stage.show();
    }

    // ==================== Chrome ====================

    private HBox buildTopBar() {
        crumbCategory.getStyleClass().add("crumb-category");
        crumbName.getStyleClass().add("crumb-name");
        Region separator = glyph("chevron-right-glyph");
        HBox crumbs = new HBox(6.0, crumbCategory, separator, crumbName);
        crumbs.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ComboBox<ThemeChoice> themePicker = new ComboBox<>();
        ShowcaseThemes.bindPicker(themePicker);
        Label themeLabel = new Label("Theme");
        themeLabel.getStyleClass().add("theme-label");
        themeLabel.setLabelFor(themePicker);

        HBox bar = new HBox(10.0, crumbs, spacer, themeLabel, themePicker);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private BorderPane buildPager() {
        previous.getStyleClass().addAll("pager-button", "previous");
        previous.setGraphic(glyph("chevron-left-glyph"));
        previous.setOnAction(event -> step(-1));
        next.getStyleClass().addAll("pager-button", "next");
        next.setGraphic(glyph("chevron-right-glyph"));
        next.setContentDisplay(ContentDisplay.RIGHT);
        next.setOnAction(event -> step(1));
        position.getStyleClass().add("pager-position");

        BorderPane pager = new BorderPane(position);
        pager.getStyleClass().add("pager");
        pager.setLeft(previous);
        pager.setRight(next);
        BorderPane.setAlignment(previous, Pos.CENTER_LEFT);
        BorderPane.setAlignment(next, Pos.CENTER_RIGHT);
        return pager;
    }

    private static Region glyph(String styleClass) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("glyph", styleClass);
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMouseTransparent(true);
        return glyph;
    }

    // ==================== Navigation ====================

    private void show(SamplerEntry entry) {
        if (entry == null || entry == current) {
            return;
        }
        int from = current == null ? -1 : entries.indexOf(current);
        int to = entries.indexOf(entry);
        current = entry;
        nav.currentProperty().set(entry);

        Node page = pages.computeIfAbsent(entry, this::createPage);
        host.transitionTo(page, to >= from ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD);

        crumbCategory.setText(entry.category().title());
        crumbName.setText(entry.name());
        SamplerEntry before = entries.get((to - 1 + entries.size()) % entries.size());
        SamplerEntry after = entries.get((to + 1) % entries.size());
        previous.setText(before.name());
        next.setText(after.name());
        position.setText((to + 1) + " / " + entries.size());
    }

    private void step(int delta) {
        int index = entries.indexOf(current);
        show(entries.get((index + delta + entries.size()) % entries.size()));
    }

    private Node createPage(SamplerEntry entry) {
        if (entry.isHome()) {
            return new SamplerHome(entries, this::show, RXSampler::openThemeGallery);
        }
        return new SamplerPage(entry);
    }

    private static void openThemeGallery() {
        try {
            new RXThemeGallery().start(new Stage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not open the theme gallery", e);
        }
    }

    /**
     * Launches the sampler.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
