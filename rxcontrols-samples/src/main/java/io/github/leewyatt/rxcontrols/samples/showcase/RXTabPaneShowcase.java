package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTab;
import io.github.leewyatt.rxcontrols.RXTabEvent;
import io.github.leewyatt.rxcontrols.RXTabPane;
import io.github.leewyatt.rxcontrols.RXTabPane.ScrollButtonPolicy;
import io.github.leewyatt.rxcontrols.RXTabPane.TabAlignment;
import io.github.leewyatt.rxcontrols.RXTabPane.TabClosingPolicy;
import io.github.leewyatt.rxcontrols.RXTabPane.Variant;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXTabPane}.
 *
 * <p>Exercises the sliding underline indicator, the three sizing variants
 * (STANDARD content-width / FULL_WIDTH equal / SCROLLABLE with scroll buttons and
 * wheel), the selection contract (roving keyboard focus, {@code selectionFollowsFocus},
 * whole-control and per-tab disabled), the closable pipeline ({@code tabClosingPolicy}
 * with the close button / middle-click / Delete and the CLOSE_REQUEST veto /
 * TAB_CLOSED lifecycle events), the text / text+icon / icon-only content variants,
 * the CSS-driven animation duration, and the {@code tabMinWidth}/{@code tabMaxWidth}
 * sizing bounds. The Content section drives the Should-tier {@code contentAnimation}
 * page transition, {@code tabAlignment}, and the {@code preserveContent} /
 * {@code dynamicHeight} sizing model; vertical sides (LEFT/RIGHT) and label wrapping
 * are reachable from the Side control and the tab-width sliders.
 */
public class RXTabPaneShowcase extends RXShowcaseApplication {

    private static final String TYPE_TEXT = "Text";
    private static final String TYPE_ICON = "Text + icon";
    private static final String TYPE_ICON_ONLY = "Icon only";

    private static final String ANIM_NONE = "None (direct cut)";
    private static final String ANIM_FADE = "Fade";
    private static final String ANIM_SLIDE = "Slide";
    private static final String ANIM_ZOOM = "Zoom";
    private static final String ANIM_FLIP = "Flip";

    private static final String[] ACCENTS = {"#4f6df5", "#12b886", "#f08c00", "#e8590c", "#ae3ec9", "#1098ad"};

    private RXTabPane tabPane;
    private ComboBox<Variant> variantBox;
    private Label selectionReadout;
    private Label eventReadout;
    private CheckBox disableTabBox;
    private int extraCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTabPane";
    }

    @Override
    protected String subtitle() {
        return "Content-owning tabs with a Material sliding indicator";
    }

    @Override
    protected String windowTitle() {
        return "RXTabPane Showcase";
    }

    @Override
    protected Node createPreview() {
        tabPane = new RXTabPane();
        tabPane.getTabs().setAll(textTabs());
        tabPane.setPrefSize(540.0, 340.0);
        tabPane.setMaxWidth(540.0);

        selectionReadout = new Label();
        selectionReadout.getStyleClass().add("value-readout");
        updateSelectionReadout();
        tabPane.selectedIndexProperty().addListener((obs, old, now) -> updateSelectionReadout());
        tabPane.selectedItemProperty().addListener((obs, old, now) -> updateSelectionReadout());

        eventReadout = new Label("No close events yet.");
        eventReadout.getStyleClass().add("hint-label");
        // Pane-level lifecycle handlers: prove the close pipeline fires on the pane.
        tabPane.addEventHandler(RXTabEvent.TAB_CLOSE_REQUEST, e ->
                eventReadout.setText("CLOSE_REQUEST: " + text(e.getTab())));
        tabPane.addEventHandler(RXTabEvent.TAB_CLOSED, e ->
                eventReadout.setText("CLOSED: " + text(e.getTab())));

        VBox preview = new VBox(16.0, tabPane, selectionReadout, eventReadout);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        preview.setMaxWidth(Double.MAX_VALUE);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Layout", buildLayoutGrid()),
                section("Animation", buildAnimationGrid()),
                section("Content", buildContentGrid()),
                section("Selection", buildSelectionGrid()),
                section("Closing", buildClosingGrid()),
                section("Scrolling", buildScrollingGrid()),
                section("Items", buildItemsGrid()));
    }

    // ==================== Sections ====================

    private Node buildLayoutGrid() {
        ComboBox<Side> sideBox = new ComboBox<>();
        sideBox.getItems().setAll(Side.values());
        sideBox.setValue(tabPane.getSide());
        sideBox.setMaxWidth(Double.MAX_VALUE);
        tabPane.sideProperty().bind(sideBox.valueProperty());

        variantBox = new ComboBox<>();
        variantBox.getItems().setAll(Variant.values());
        variantBox.setValue(tabPane.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);
        tabPane.variantProperty().bind(variantBox.valueProperty());

        Slider minSlider = createSlider(0.0, 200.0, tabPane.getTabMinWidth());
        tabPane.tabMinWidthProperty().bind(minSlider.valueProperty());

        Slider maxSlider = createSlider(80.0, 400.0, 400.0);
        tabPane.tabMaxWidthProperty().bind(maxSlider.valueProperty());

        return createGrid(
                row("Side", sideBox),
                row("Variant", variantBox),
                row("Tab min width", minSlider, createValueLabel(minSlider, "%.0f px")),
                row("Tab max width", maxSlider, createValueLabel(maxSlider, "%.0f px")));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox();
        animatedBox.setSelected(tabPane.isAnimated());
        tabPane.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider = createSlider(0.0, 600.0, tabPane.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, old, now) ->
                tabPane.setAnimationDuration(Duration.millis(now.doubleValue())));

        return createGrid(
                row("Animated", animatedBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildContentGrid() {
        ComboBox<TabAlignment> alignBox = new ComboBox<>();
        alignBox.getItems().setAll(TabAlignment.values());
        alignBox.setValue(tabPane.getTabAlignment());
        alignBox.setMaxWidth(Double.MAX_VALUE);
        tabPane.tabAlignmentProperty().bind(alignBox.valueProperty());

        ComboBox<String> animBox = new ComboBox<>();
        animBox.getItems().setAll(ANIM_NONE, ANIM_FADE, ANIM_SLIDE, ANIM_ZOOM, ANIM_FLIP);
        animBox.setValue(ANIM_NONE);
        animBox.setMaxWidth(Double.MAX_VALUE);
        // contentAnimation is an ObjectProperty<PageAnimation>, so map the name to a
        // fresh animation instead of binding to the combo's String value.
        animBox.valueProperty().addListener((obs, old, now) ->
                tabPane.setContentAnimation(contentAnimationFor(now)));

        CheckBox preserveBox = new CheckBox();
        preserveBox.setSelected(tabPane.isPreserveContent());
        tabPane.preserveContentProperty().bind(preserveBox.selectedProperty());

        CheckBox dynamicBox = new CheckBox();
        dynamicBox.setSelected(tabPane.isDynamicHeight());
        tabPane.dynamicHeightProperty().bind(dynamicBox.selectedProperty());

        Label hint = new Label("Content animation plays on tab switch (needs a positive Duration). "
                + "Preserve content keeps every page attached and locks the height to the tallest; "
                + "dynamic height then lets it follow the selected page.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        return createGrid(
                row("Tab alignment", alignBox),
                row("Content animation", animBox),
                row("Preserve content", preserveBox),
                row("Dynamic height", dynamicBox),
                row(hint));
    }

    private PageAnimation contentAnimationFor(String name) {
        switch (name) {
            case ANIM_FADE:
                return new AnimFade();
            case ANIM_SLIDE:
                return new AnimSlide();
            case ANIM_ZOOM:
                return new AnimZoom();
            case ANIM_FLIP:
                return new AnimFlip();
            default:
                return null;
        }
    }

    private Node buildSelectionGrid() {
        CheckBox followFocusBox = new CheckBox();
        followFocusBox.setSelected(tabPane.isSelectionFollowsFocus());
        tabPane.selectionFollowsFocusProperty().bind(followFocusBox.selectedProperty());

        CheckBox disableControlBox = new CheckBox();
        tabPane.disableProperty().bind(disableControlBox.selectedProperty());

        disableTabBox = new CheckBox();
        disableTabBox.selectedProperty().addListener((obs, old, now) -> applyTabDisable());

        return createGrid(
                row("Selection follows focus", followFocusBox),
                row("Disable control", disableControlBox),
                row("Disable 3rd tab", disableTabBox));
    }

    private Node buildClosingGrid() {
        ComboBox<TabClosingPolicy> policyBox = new ComboBox<>();
        policyBox.getItems().setAll(TabClosingPolicy.values());
        policyBox.setValue(tabPane.getTabClosingPolicy());
        policyBox.setMaxWidth(Double.MAX_VALUE);
        tabPane.tabClosingPolicyProperty().bind(policyBox.valueProperty());

        CheckBox vetoBox = new CheckBox();
        // Veto the first tab's close to demonstrate the CLOSE_REQUEST contract.
        vetoBox.selectedProperty().addListener((obs, old, now) -> applyFirstTabVeto(now));

        Label hint = new Label("Close via the ✕ button, middle-click, or Delete on the selected tab.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        return createGrid(
                row("Closing policy", policyBox),
                row("Veto 1st tab close", vetoBox),
                row(hint));
    }

    private Node buildScrollingGrid() {
        ComboBox<ScrollButtonPolicy> scrollBox = new ComboBox<>();
        scrollBox.getItems().setAll(ScrollButtonPolicy.values());
        scrollBox.setValue(tabPane.getScrollButtonPolicy());
        scrollBox.setMaxWidth(Double.MAX_VALUE);
        tabPane.scrollButtonPolicyProperty().bind(scrollBox.valueProperty());

        CheckBox wheelBox = new CheckBox();
        wheelBox.setSelected(tabPane.isWheelScrollEnabled());
        tabPane.wheelScrollEnabledProperty().bind(wheelBox.selectedProperty());

        Button fillButton = new Button("Add 8 tabs + go SCROLLABLE");
        fillButton.setMaxWidth(Double.MAX_VALUE);
        fillButton.setOnAction(event -> {
            for (int i = 0; i < 8; i++) {
                extraCount++;
                tabPane.getTabs().add(RXTab.of("Extra " + extraCount, page("Extra " + extraCount, accent(extraCount))));
            }
            // Switch to SCROLLABLE through the bound Layout combo (the pane's variant
            // property is bound, so it cannot be set directly), and narrow the pane so
            // the tabs overflow and the scroll buttons appear.
            variantBox.setValue(Variant.SCROLLABLE);
            tabPane.setMaxWidth(420.0);
        });

        Label hint = new Label("Adds tabs, switches to SCROLLABLE, and narrows the pane; "
                + "then scroll with the wheel or the ‹ › buttons.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        return createGrid(
                row("Scroll buttons", scrollBox),
                row("Wheel scroll", wheelBox),
                row(fillButton),
                row(hint));
    }

    private Node buildItemsGrid() {
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().setAll(TYPE_TEXT, TYPE_ICON, TYPE_ICON_ONLY);
        typeBox.setValue(TYPE_TEXT);
        typeBox.setMaxWidth(Double.MAX_VALUE);
        typeBox.valueProperty().addListener((obs, old, now) -> applyContentType(now));

        Button addButton = new Button("Add");
        Button removeButton = new Button("Remove last");
        Button clearButton = new Button("Clear");
        for (Button button : List.of(addButton, removeButton, clearButton)) {
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setMaxWidth(Double.MAX_VALUE);
        }
        addButton.setOnAction(event -> {
            extraCount++;
            tabPane.getTabs().add(RXTab.of("Extra " + extraCount, page("Extra " + extraCount, accent(extraCount))));
        });
        removeButton.setOnAction(event -> {
            List<RXTab> tabs = tabPane.getTabs();
            if (!tabs.isEmpty()) {
                tabs.remove(tabs.size() - 1);
            }
        });
        clearButton.setOnAction(event -> tabPane.getTabs().clear());

        return createGrid(
                row("Content", typeBox),
                row(new HBox(8.0, addButton, removeButton, clearButton)));
    }

    // ==================== Behaviour helpers ====================

    private void applyContentType(String type) {
        switch (type) {
            case TYPE_ICON:
                tabPane.getTabs().setAll(iconTabs(false));
                break;
            case TYPE_ICON_ONLY:
                tabPane.getTabs().setAll(iconTabs(true));
                break;
            default:
                tabPane.getTabs().setAll(textTabs());
        }
        applyTabDisable();
    }

    private void applyTabDisable() {
        if (disableTabBox != null && tabPane.getTabs().size() > 2) {
            tabPane.getTabs().get(2).setDisable(disableTabBox.isSelected());
        }
    }

    private void applyFirstTabVeto(boolean veto) {
        if (tabPane.getTabs().isEmpty()) {
            return;
        }
        RXTab first = tabPane.getTabs().get(0);
        first.setOnCloseRequest(veto ? RXTabEvent::consume : null);
    }

    private void updateSelectionReadout() {
        selectionReadout.setText(String.format(Locale.ROOT, "selectedIndex = %d,  selectedItem = %s",
                tabPane.getSelectedIndex(), text(tabPane.getSelectedItem())));
    }

    // ==================== Tab building ====================

    private List<RXTab> textTabs() {
        return List.of(
                RXTab.of("Overview", page("Overview", ACCENTS[0])),
                RXTab.of("Analytics", page("Analytics", ACCENTS[1])),
                RXTab.of("Reports", page("Reports", ACCENTS[2])),
                RXTab.of("Settings", page("Settings", ACCENTS[3])));
    }

    private List<RXTab> iconTabs(boolean iconOnly) {
        return List.of(
                iconTab("Home", ACCENTS[0], iconOnly),
                iconTab("Search", ACCENTS[1], iconOnly),
                iconTab("Favorites", ACCENTS[2], iconOnly),
                iconTab("Profile", ACCENTS[3], iconOnly));
    }

    private RXTab iconTab(String name, String accent, boolean iconOnly) {
        RXTab tab = RXTab.of(iconOnly ? null : name, dot(accent), page(name, accent));
        if (iconOnly) {
            // Icon-only tabs still need a screen-reader label (Core a11y contract).
            tab.setAccessibleText(name);
            tab.setTooltip(new Tooltip(name));
        }
        return tab;
    }

    private Region dot(String color) {
        Region dot = new Region();
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999;"
                + " -fx-min-width: 14; -fx-min-height: 14; -fx-pref-width: 14; -fx-pref-height: 14;");
        dot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dot.setMouseTransparent(true);
        return dot;
    }

    private Node page(String name, String accent) {
        Label heading = new Label(name);
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");
        Label body = new Label("This is the \"" + name + "\" page. Its content is owned by the tab and "
                + "attached only while the tab is selected.");
        body.setWrapText(true);
        body.setMaxWidth(440.0);
        Region bar = new Region();
        bar.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 4; -fx-pref-height: 6;");
        bar.setMaxWidth(120.0);
        VBox box = new VBox(12.0, heading, bar, body);
        box.setAlignment(Pos.TOP_LEFT);
        box.setStyle("-fx-padding: 24;");
        return box;
    }

    private static String accent(int index) {
        return ACCENTS[Math.abs(index) % ACCENTS.length];
    }

    private static String text(RXTab tab) {
        if (tab == null) {
            return "none";
        }
        return tab.getText() == null ? "(icon)" : tab.getText();
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
