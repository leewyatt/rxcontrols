package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSmoothScrollSupport;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleLifecycle;
import io.github.leewyatt.rxcontrols.samples.support.PageAnimationPresets;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.col;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.glyph;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.HomeKit.wrapped;

/**
 * The first page: what the library is, live components at work, every category, how to
 * add the library to a build, what is new and who makes it.
 */
final class SamplerHome extends ScrollPane {

    static final String AUTHOR = "Lee Wyatt";
    static final String GITHUB_URL = "https://github.com/leewyatt";
    static final String REPO_URL = GITHUB_URL + "/rxcontrols";
    static final String EMAIL = "leewyatt7788@gmail.com";
    private static final String ISSUES_URL = REPO_URL + "/issues";
    private static final String RELEASES_URL = REPO_URL + "/releases";
    private static final String X_HANDLE = "LeeWyatt_7788";
    private static final String X_URL = "https://x.com/" + X_HANDLE;
    private static final String BILIBILI_URL = "https://space.bilibili.com/397562730";

    private static final String GROUP_ID = "io.github.leewyatt";
    private static final String ARTIFACT_ID = "rxcontrols";
    private static final String VERSION = "1.0.0";
    private static final String COORDINATES = GROUP_ID + ":" + ARTIFACT_ID + ":" + VERSION;
    private static final String MAVEN = "<dependency>\n"
            + "    <groupId>" + GROUP_ID + "</groupId>\n"
            + "    <artifactId>" + ARTIFACT_ID + "</artifactId>\n"
            + "    <version>" + VERSION + "</version>\n"
            + "</dependency>";
    private static final String GRADLE = "dependencies {\n"
            + "    implementation(\"" + COORDINATES + "\")\n"
            + "}";

    // Side by side needs room for the panel's two columns of cards next to the text.
    private static final RXBreakpointProfile HERO_PROFILE = RXBreakpointProfile.builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.MD, 1000.0)
            .build();

    private static final int SAMPLE_NAMES = 3;
    private static final double CONTENT_MAX_WIDTH = 1180.0;

    private final SampleLifecycle lifecycle = new SampleLifecycle();
    private final List<SamplerEntry> entries;
    private final Consumer<SamplerEntry> open;
    private final Consumer<String> openLink;

    /**
     * Creates the home page.
     *
     * @param entries     every entry of the sampler, this page's own included
     * @param open        shows the page of an entry
     * @param openGallery opens the theme gallery
     * @param openLink    opens a web address in the browser
     */
    SamplerHome(List<SamplerEntry> entries, Consumer<SamplerEntry> open, Runnable openGallery,
                Consumer<String> openLink) {
        this.entries = entries;
        this.open = open;
        this.openLink = openLink;
        getStyleClass().add("sampler-home");
        getStylesheets().add(SamplerHome.class.getResource("sampler-home.css").toExternalForm());
        setFitToWidth(true);
        RXSmoothScrollSupport.install(this);

        Map<SamplerCategory, List<SamplerEntry>> byCategory = new EnumMap<>(SamplerCategory.class);
        int controls = 0;
        for (SamplerEntry entry : entries) {
            if (entry.isHome()) {
                continue;
            }
            byCategory.computeIfAbsent(entry.category(), c -> new ArrayList<>()).add(entry);
            controls += entry.variants().size();
        }
        Runnable browse = () -> open.accept(byCategory.values().iterator().next().get(0));

        VBox column = new VBox(32.0,
                hero(byCategory, controls, browse, openGallery),
                section("See them working", "A few of the " + controls + " controls, live. Browse them all below.",
                        galleryLink(openGallery), HomeTiles.create(lifecycle, this::openType)),
                section("Browse by category", null, null, categories(byCategory)),
                resources(),
                author());
        column.getStyleClass().add("home-column");
        column.setMaxWidth(CONTENT_MAX_WIDTH);

        StackPane center = new StackPane(column);
        center.setAlignment(Pos.TOP_CENTER);
        center.getStyleClass().add("home-content");
        setContent(center);

        sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                lifecycle.hide();
            } else {
                lifecycle.show();
            }
        });
    }

    /**
     * Ends the life of the page; its running parts stop for good.
     */
    void dispose() {
        lifecycle.dispose();
    }

    /**
     * Returns the node a snapshot of the page renders: the whole page, including the part
     * scrolled out of view.
     *
     * @return the node to snapshot
     */
    Node snapshotArea() {
        return getContent();
    }

    // ==================== Hero ====================

    private Node hero(Map<SamplerCategory, List<SamplerEntry>> byCategory, int controls, Runnable browse,
                      Runnable openGallery) {
        RXChipSet facts = new RXChipSet(new RXChip("v" + VERSION), new RXChip("Java 17+ · JavaFX 17+"),
                new RXChip("Modena and AtlantaFX"));
        facts.getStyleClass().add("facts");
        facts.setHgap(8.0);
        facts.setVgap(8.0);

        Label title = text("RXControls", "home-title");
        Label lead = wrapped("The components JavaFX doesn't ship with: responsive layouts, kanban boards, "
                + "masonry walls, ribbons and bursts. Ready for Modena and every AtlantaFX theme.", "home-lead");

        int pages = byCategory.values().stream().mapToInt(List::size).sum();
        HBox stats = new HBox(32.0, stat(controls, "controls"), stat(pages, "pages"),
                stat(byCategory.size(), "categories"));

        RXButton browseButton = new RXButton("Browse components", glyph("arrow-right-glyph"));
        browseButton.getStyleClass().add("accent-button");
        browseButton.setContentDisplay(ContentDisplay.RIGHT);
        browseButton.setOnAction(event -> browse.run());
        RXButton gallery = new RXButton("Theme gallery");
        gallery.getStyleClass().add("outline-button");
        gallery.setOnAction(event -> openGallery.run());
        FlowPane actions = new FlowPane(10.0, 10.0, browseButton, gallery,
                iconButton("github-glyph", "RXControls on GitHub", () -> openLink.accept(REPO_URL)),
                iconButton("x-glyph", AUTHOR + " on X", () -> openLink.accept(X_URL)));
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(20.0, facts, title, lead, stats, actions);
        VBox.setMargin(title, new Insets(-4.0, 0.0, -8.0, 0.0));

        StackPane well = new StackPane(HomeSpotlight.create(controls, transitionCount(), browse, openLink));
        well.getStyleClass().add("spotlight-well");

        // The panel shows only beside the text; where the hero is too narrow for that, a
        // one-line strip stands in for it, so the tiles below show sooner.
        RXRow row = new RXRow();
        row.setBreakpointProfile(HERO_PROFILE);
        row.setGutter(40.0);
        row.setRowGap(24.0);
        row.setAlign(RXRow.Align.CENTER);
        row.setAnimated(true);
        // The narrow layout keeps only the essentials: the strip below links to GitHub,
        // and the categories further down are the way into the components.
        actions.visibleProperty().bind(row.activeBreakpointProperty().isNotEqualTo(RXBreakpoint.XS));
        actions.managedProperty().bind(actions.visibleProperty());
        row.getChildren().addAll(
                heroCol(text, 12, true, true),
                heroCol(well, 12, false, true),
                heroCol(HomeSpotlight.strip(openLink), 24, true, false));
        return row;
    }

    /** A hero column: full width when narrow, {@code wideSpan} when wide, shown where asked. */
    private static RXCol heroCol(Node content, int wideSpan, boolean narrow, boolean wide) {
        RXCol col = new RXCol(content);
        col.setXs(RXColSpec.builder().span(24).hidden(!narrow).build());
        col.setMd(RXColSpec.builder().span(wideSpan).hidden(!wide).build());
        return col;
    }

    private static Node stat(int value, String name) {
        return new VBox(2.0, text(String.valueOf(value), "stat-value"), text(name, "caption"));
    }

    private static int transitionCount() {
        return PageAnimationPresets.byCategory().values().stream().mapToInt(Map::size).sum();
    }

    // ==================== Sections ====================

    private static VBox section(String title, String subtitle, Node trailing, Node content) {
        VBox words = new VBox(6.0, text(title, "home-heading"));
        if (subtitle != null) {
            words.getChildren().add(wrapped(subtitle, "caption"));
        }
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox head = new HBox(16.0, words);
        head.setAlignment(Pos.BOTTOM_LEFT);
        if (trailing != null) {
            head.getChildren().add(trailing);
        }
        return new VBox(18.0, head, content);
    }

    private static Node galleryLink(Runnable openGallery) {
        RXButton link = new RXButton("Open theme gallery", glyph("arrow-right-glyph"));
        link.getStyleClass().add("link-button");
        link.setContentDisplay(ContentDisplay.RIGHT);
        link.setMinWidth(Region.USE_PREF_SIZE);
        link.setOnAction(event -> openGallery.run());
        return link;
    }

    // Tiles and news name a component by its type; a merged page lists it as a variant.
    private void openType(String typeName) {
        for (SamplerEntry entry : entries) {
            if (entry.name().equals(typeName)) {
                open.accept(entry);
                return;
            }
            for (SamplerVariant variant : entry.variants()) {
                if (variant.typeName().equals(typeName)) {
                    open.accept(entry);
                    return;
                }
            }
        }
    }

    // ==================== Categories ====================

    private RXRow categories(Map<SamplerCategory, List<SamplerEntry>> byCategory) {
        RXRow row = HomeKit.row(12.0, 12.0);
        row.setAlign(RXRow.Align.STRETCH);
        byCategory.forEach((category, list) -> row.getChildren().add(col(categoryCard(category, list), 24, 12, 6)));
        return row;
    }

    private Button categoryCard(SamplerCategory category, List<SamplerEntry> list) {
        Region glyph = new Region();
        glyph.getStyleClass().add("category-glyph");
        glyph.setShape(SamplerNav.glyphShape(category));
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane badge = new StackPane(glyph);
        badge.getStyleClass().add("glyph-badge");
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label name = wrapped(category.title(), "card-title");
        Label count = text(list.size() + (list.size() == 1 ? " page" : " pages"), "count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        HBox heading = new HBox(8.0, badge, name, spacer(), count);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label sample = wrapped(list.stream().limit(SAMPLE_NAMES).map(SamplerEntry::name)
                .collect(Collectors.joining(" · ")), "caption");
        VBox content = new VBox(10.0, heading, sample);

        Button card = new Button();
        // A button lays its graphic out at the preferred width; the column decides the
        // card's width, so following it lets the count sit at the trailing edge.
        content.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> card.getWidth() - card.getInsets().getLeft() - card.getInsets().getRight(),
                card.widthProperty(), card.insetsProperty()));
        card.getStyleClass().add("category-card");
        card.setGraphic(content);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        card.setOnAction(event -> open.accept(list.get(0)));
        return card;
    }

    // ==================== Get started and what's new ====================

    private RXRow resources() {
        RXRow row = HomeKit.row(16.0, 16.0);
        row.setAlign(RXRow.Align.STRETCH);
        row.getChildren().addAll(col(getStarted(), 24, 24, 12), col(whatsNew(), 24, 24, 12));
        return row;
    }

    private Node getStarted() {
        Label code = text(MAVEN, "code", "snippet");
        code.setMaxWidth(Double.MAX_VALUE);
        RXSegmentedControl<String> build = new RXSegmentedControl<>(
                RXSegmentedItem.of(MAVEN, "Maven"), RXSegmentedItem.of(GRADLE, "Gradle"));
        build.getStyleClass().add("build-switch");
        build.select(MAVEN);
        code.textProperty().bind(build.valueProperty());
        HBox head = new HBox(10.0, text("Get started", "card-heading"), spacer(), build);
        head.setAlignment(Pos.CENTER_LEFT);

        RXCopyButton copy = new RXCopyButton();
        copy.setCopiedText(null);
        copy.getStyleClass().add("snippet-copy");
        copy.setContentFactory(() -> RXCopyButton.textContent(code.getText()));
        StackPane snippet = new StackPane(code, copy);
        snippet.getStyleClass().add("snippet-box");
        StackPane.setAlignment(copy, Pos.TOP_RIGHT);

        HBox links = new HBox(14.0, link("README", REPO_URL + "#readme"), link("Releases", RELEASES_URL),
                link("Report an issue", ISSUES_URL));
        VBox card = new VBox(12.0, head, snippet, links);
        card.getStyleClass().add("home-card");
        return card;
    }

    private Node whatsNew() {
        VBox list = new VBox(newsRow("RXKanbanView", "RXKanbanView", "New", "success"),
                newsRow("RXMasonryView", "RXMasonryView", "New", "success"),
                newsRow("RXBorderBeam", "RXBorderBeam", "New", "success"),
                newsRow("Number fields", "Number fields", "Updated", "primary"));
        list.getStyleClass().add("news-list");
        list.getChildren().get(list.getChildren().size() - 1).getStyleClass().add("last");
        VBox card = new VBox(8.0, text("What's new in " + VERSION, "card-heading"), list);
        card.getStyleClass().add("home-card");
        return card;
    }

    private Button newsRow(String target, String name, String tag, String tagStyle) {
        // A badge without content is a standalone pill.
        RXBadge pill = new RXBadge();
        pill.setText(tag);
        pill.getStyleClass().add(tagStyle);
        HBox content = new HBox(text(name, "news-name"), spacer(), pill);
        content.setAlignment(Pos.CENTER_LEFT);

        Button row = new Button();
        content.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> row.getWidth() - row.getInsets().getLeft() - row.getInsets().getRight(),
                row.widthProperty(), row.insetsProperty()));
        row.getStyleClass().add("news-row");
        row.setGraphic(content);
        row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setOnAction(event -> openType(target));
        return row;
    }

    private Hyperlink link(String name, String url) {
        Hyperlink link = new Hyperlink(name);
        link.setOnAction(event -> openLink.accept(url));
        return link;
    }

    // ==================== Author ====================

    private Node author() {
        RXAvatar avatar = new RXAvatar(HomeKit.image("/image/avatar.png"));
        avatar.setPrefSize(64.0, 64.0);
        avatar.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        avatar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        RXChipSet roles = new RXChipSet(new RXChip("JavaFX consultant"), new RXChip("Freelancer"),
                new RXChip("Open-source author"));
        roles.getStyleClass().add("roles");
        roles.setHgap(6.0);
        roles.setVgap(6.0);
        FlowPane more = new FlowPane(6.0, 4.0, text("More from Lee:", "caption"),
                link("FxmlKit", GITHUB_URL + "/FxmlKit"),
                link("CarouselFX", "https://github.com/dlsc-software-consulting-gmbh/CarouselFX"),
                link("FlowFX", GITHUB_URL + "/FlowFX"),
                link("JavaFX Tools for IntelliJ IDEA", "https://plugins.jetbrains.com/search?search=JavaFX%20Tools"));
        more.setAlignment(Pos.CENTER_LEFT);
        VBox about = new VBox(8.0, text("Made by Lee", "card-heading"), roles,
                wrapped("JavaFX developer in Shizuoka, Japan. Open to freelance and consulting work.", "lead"),
                more);
        about.setMinWidth(0.0);
        HBox.setHgrow(about, Priority.ALWAYS);
        HBox person = new HBox(18.0, avatar, about);
        person.setAlignment(Pos.TOP_LEFT);

        RXButton github = profileButton("GitHub @leewyatt", "github-glyph", GITHUB_URL);
        github.getStyleClass().add("accent-button");
        FlowPane contacts = new FlowPane(8.0, 8.0, github,
                profileButton("X @" + X_HANDLE, "x-glyph", X_URL),
                profileButton("Bilibili", "play-glyph", BILIBILI_URL));
        contacts.setAlignment(Pos.CENTER_RIGHT);

        RXRow row = HomeKit.row(18.0, 14.0);
        row.setAlign(RXRow.Align.CENTER);
        row.getChildren().addAll(col(person, 24, 24, 15), col(contacts, 24, 24, 9));
        row.getStyleClass().addAll("home-card", "author-band");
        return row;
    }

    private RXButton profileButton(String name, String glyphClass, String url) {
        RXButton button = new RXButton(name, glyph(glyphClass));
        button.getStyleClass().add("outline-button");
        button.setOnAction(event -> openLink.accept(url));
        return button;
    }
}
