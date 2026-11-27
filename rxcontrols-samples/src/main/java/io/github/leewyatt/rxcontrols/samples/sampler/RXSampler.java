package io.github.leewyatt.rxcontrols.samples.sampler;

import atlantafx.base.theme.PrimerDark;
import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXOverlayPane;
import io.github.leewyatt.rxcontrols.RXSelectionBox;
import io.github.leewyatt.rxcontrols.RXTaskButton;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.samples.demo.RXThemeGallery;
import io.github.leewyatt.rxcontrols.samples.support.PageAnimationPresets;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The component sampler: a searchable sidebar of every component, grouped by category, and
 * the selected component's page beside it, with the playground of its showcase, a
 * generated CSS reference and a launcher for its demo. Only the page on display and a
 * few recently visited ones are kept, so the scene stays small however many components
 * the library has. {@link RXThemeGallery} remains the one-screen overview for development.
 */
public class RXSampler extends Application {

    private static final Logger LOG = Logger.getLogger(RXSampler.class.getName());
    private static final int PAGE_CACHE_SIZE = 4;
    private static final String DEFAULT_TRANSITION = "Fade";
    // A plain fade reads best quick; the patterned effects need time to be seen.
    private static final Duration FADE_DURATION = Duration.millis(180.0);
    private static final Duration EFFECT_DURATION = Duration.millis(520.0);

    private final List<SamplerEntry> entries = SamplerCatalog.entries();
    // In access order the eldest page is never the one on display or the one fading out,
    // which was shown just before it, so a dropped page is out of view when disposed.
    private final Map<SamplerEntry, Node> pages = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SamplerEntry, Node> eldest) {
            if (size() <= PAGE_CACHE_SIZE) {
                return false;
            }
            dispose(eldest.getValue());
            return true;
        }
    };

    private final RXTransitionPane host = new RXTransitionPane();
    // The path is the category followed by the entry, so the item type is their common one.
    private final RXBreadcrumbBar<Object> crumbs = new RXBreadcrumbBar<>();
    private final Button previous = new Button();
    private final Button next = new Button();
    private final Label position = new Label();

    private SamplerNav nav;
    private SamplerEntry current;

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        ShowcaseThemes.apply(startTheme());
        nav = new SamplerNav(entries);
        nav.currentProperty().addListener((obs, old, entry) -> show(entry));

        host.getStyleClass().add("page-host");
        host.setAnimation(new AnimFade());
        host.setAnimationDuration(FADE_DURATION);

        BorderPane main = new BorderPane(host);
        main.getStyleClass().add("sampler-main");
        main.setTop(buildTopBar());
        main.setBottom(buildPager());

        BorderPane root = new BorderPane(main);
        root.getStyleClass().add("sampler");
        root.setLeft(nav);

        Scene scene = new Scene(new RXOverlayPane(root), 1360, 860);
        scene.getStylesheets().addAll(RXShowcaseApplication.shellStylesheet(),
                RXSampler.class.getResource("rx-sampler.css").toExternalForm());
        // Some page animations of the carousel showcase rotate in 3D.
        scene.setCamera(new PerspectiveCamera());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                nav::focusSearch);

        show(entries.get(0));
        stage.setOnHidden(event -> pages.values().forEach(RXSampler::dispose));
        stage.setScene(scene);
        stage.setTitle("RXControls Sampler");
        stage.setMinWidth(960.0);
        stage.setMinHeight(600.0);
        stage.show();
    }

    // ==================== Chrome ====================

    private HBox buildTopBar() {
        crumbs.setConverter(new StringConverter<>() {
            @Override
            public String toString(Object item) {
                if (item instanceof SamplerCategory category) {
                    return category.title();
                }
                return item instanceof SamplerEntry entry ? entry.name() : "";
            }

            @Override
            public Object fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        crumbs.setChildrenFactory(this::crumbChildren);
        crumbs.setOnNavigate(event -> show(entryAt(event.getItem())));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        RXSelectionBox<Transition> transitionPicker = buildTransitionPicker();
        Label transitionLabel = new Label("Transition");
        transitionLabel.getStyleClass().add("theme-label");
        transitionLabel.setLabelFor(transitionPicker);

        HBox bar = new HBox(10.0, crumbs, spacer, transitionLabel, transitionPicker, buildSnapshotButton(),
                new SamplerThemeSwitcher());
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** Picks the page transition from every preset, grouped and searchable. */
    private RXSelectionBox<Transition> buildTransitionPicker() {
        List<Transition> transitions = new ArrayList<>();
        PageAnimationPresets.byCategory().forEach((category, presets) ->
                presets.forEach((name, factory) -> transitions.add(new Transition(category, name, factory))));

        RXSelectionBox<Transition> picker = new RXSelectionBox<>();
        picker.getStyleClass().add("transition-picker");
        picker.getItems().setAll(transitions);
        picker.setSectionKeyFactory(Transition::category);
        picker.setSearchPromptText("Search transitions");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(Transition transition) {
                return transition == null ? "" : transition.name();
            }

            @Override
            public Transition fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        for (Transition transition : transitions) {
            if (transition.name().equals(DEFAULT_TRANSITION)) {
                picker.getSelectionModel().select(transition);
            }
        }
        picker.getSelectionModel().selectedItemProperty().addListener((obs, old, transition) -> {
            if (transition != null) {
                host.setAnimation(transition.factory().get());
                host.setAnimationDuration(transition.name().equals(DEFAULT_TRANSITION)
                        ? FADE_DURATION : EFFECT_DURATION);
            }
        });
        return picker;
    }

    /** A camera button that copies the page on display to the clipboard as an image. */
    private RXTaskButton buildSnapshotButton() {
        RXTaskButton button = new RXTaskButton(null, glyph("camera-glyph"));
        button.getStyleClass().add("snapshot-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        // A click leaves the focus in the page, so a focused control is captured as it was.
        button.setFocusTraversable(false);
        button.setAccessibleText("Copy a snapshot of the page");
        button.setTooltip(new Tooltip("Copy a snapshot of the page"));
        button.setOnAction(event -> {
            RXTaskButton.Run run = button.start();
            if (copySnapshot()) {
                run.succeed();
            } else {
                run.fail();
            }
        });
        return button;
    }

    // Snapshots the scroll content of the page rather than the page itself: a scroll pane
    // draws only its visible part. The window's pixel density keeps the image sharp on a
    // HiDPI display.
    private boolean copySnapshot() {
        Node area = snapshotArea(host.getContent());
        Window window = host.getScene().getWindow();
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(window.getOutputScaleX(), window.getOutputScaleY()));
        try {
            WritableImage image = area.snapshot(params, null);
            ClipboardContent content = new ClipboardContent();
            content.putImage(image);
            return Clipboard.getSystemClipboard().setContent(content);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not copy a snapshot of the page", e);
            return false;
        }
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
        RXSizing.lockToPrefSize(glyph);
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

        crumbs.getItems().setAll(entry.category(), entry);
        SamplerEntry before = entries.get((to - 1 + entries.size()) % entries.size());
        SamplerEntry after = entries.get((to + 1) % entries.size());
        previous.setText(before.name());
        next.setText(after.name());
        position.setText((to + 1) + " / " + entries.size());
    }

    private List<Object> crumbChildren(List<Object> path) {
        if (path.isEmpty()) {
            return List.of((Object[]) SamplerCategory.values());
        }
        Object parent = path.get(path.size() - 1);
        List<Object> children = new ArrayList<>();
        for (SamplerEntry entry : entries) {
            if (entry.category() == parent) {
                children.add(entry);
            }
        }
        return children;
    }

    // A category has no page of its own: another category stands for its first page,
    // and the category of the page on display leads nowhere.
    private SamplerEntry entryAt(Object crumb) {
        if (crumb instanceof SamplerEntry entry) {
            return entry;
        }
        if (current != null && current.category() == crumb) {
            return null;
        }
        for (SamplerEntry entry : entries) {
            if (entry.category() == crumb) {
                return entry;
            }
        }
        return null;
    }

    private void step(int delta) {
        int index = entries.indexOf(current);
        show(entries.get((index + delta + entries.size()) % entries.size()));
    }

    private Node createPage(SamplerEntry entry) {
        if (entry.isHome()) {
            return new SamplerHome(entries, this::show, RXSampler::openThemeGallery,
                    getHostServices()::showDocument);
        }
        return new SamplerPage(entry);
    }

    private static void dispose(Node page) {
        if (page instanceof SamplerPage samplerPage) {
            samplerPage.dispose();
        } else if (page instanceof SamplerHome home) {
            home.dispose();
        }
    }

    private static Node snapshotArea(Node page) {
        if (page instanceof SamplerPage samplerPage) {
            return samplerPage.snapshotArea();
        }
        if (page instanceof SamplerHome home) {
            return home.snapshotArea();
        }
        return page;
    }

    private static ThemeChoice startTheme() {
        String stylesheet = new PrimerDark().getUserAgentStylesheet();
        for (ThemeChoice choice : ShowcaseThemes.all()) {
            if (choice.userAgentStylesheet().equals(stylesheet)) {
                return choice;
            }
        }
        return ShowcaseThemes.getCurrent();
    }

    private static void openThemeGallery() {
        try {
            new RXThemeGallery().start(new Stage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not open the theme gallery", e);
        }
    }

    private record Transition(String category, String name, Supplier<PageAnimation> factory) {
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
