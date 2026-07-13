package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXMenuList;
import io.github.leewyatt.rxcontrols.RXPopupMenu;
import io.github.leewyatt.rxcontrols.internal.popup.RXPlacement;
import io.github.leewyatt.rxcontrols.samples.demo.RXMenuButtonDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Showcase application for the command-menu family.
 *
 * <p>The preview hosts four targets driven from one shared item spec: an
 * {@link RXMenuButton} whose own popup honours {@code placement}; a plain-button
 * anchored {@link RXPopupMenu} that exercises entrance animation, initial focus,
 * and offset; a live always-open {@link RXMenuList} panel that exercises dense /
 * ripple / disabled-items-focusable / wrap-around and every item variant at a
 * glance; and an {@link RXMenuButton} card wired with
 * {@link RXMenuButton#installContextMenu} for right-click. The right pane toggles
 * every interactive property. For the minimal example see
 * {@link RXMenuButtonDemo}.</p>
 */
public class RXMenuButtonShowcase extends RXShowcaseApplication {

    private static final String FILE = "M6 2a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm7 1.5L18.5 9H13z";
    private static final String FOLDER = "M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8z";
    private static final String TRASH = "M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z";

    private final Map<String, Interpolator> interpolators = new LinkedHashMap<>();

    private RXMenuButton menuButton;
    private RXPopupMenu popup;
    private RXMenuList liveList;
    private RXMenuButton contextButton;
    private ComboBox<RXPlacement> placementPicker;

    // Content toggles shared by all item hosts.
    private boolean showIcons = true;
    private boolean showShortcuts = true;
    private boolean showDividers = true;
    private boolean showSubheaders = true;
    private boolean showCheckRadio = true;
    private boolean showDanger = true;
    private boolean showDisabled = true;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMenuButton";
    }

    @Override
    protected String subtitle() {
        return "Material command menu";
    }

    @Override
    protected String windowTitle() {
        return "RXMenuButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-menu-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        menuButton = new RXMenuButton("Menu");

        popup = new RXPopupMenu();
        Button trigger = new Button("Open popup ▾");
        trigger.setOnAction(e -> popup.show(trigger, currentPlacement()));

        contextButton = new RXMenuButton();
        contextButton.setVisible(false);
        contextButton.setManaged(false);
        Label cardHint = new Label("Right-click this card");
        cardHint.getStyleClass().add("card-hint");
        StackPane contextCard = new StackPane(cardHint, contextButton);
        contextCard.getStyleClass().add("context-card");
        contextCard.setPrefSize(240.0, 96.0);
        contextButton.installContextMenu(contextCard);

        HBox triggers = new HBox(24.0, menuButton, trigger, contextCard);
        triggers.setAlignment(Pos.CENTER);

        liveList = new RXMenuList();
        liveList.setMaxWidth(Region.USE_PREF_SIZE);
        Label panelLabel = new Label("Live menu list");
        panelLabel.getStyleClass().add("panel-label");
        StackPane panel = new StackPane(liveList);
        panel.getStyleClass().add("live-panel");

        VBox box = new VBox(20.0, triggers, panelLabel, panel);
        box.setAlignment(Pos.CENTER);
        rebuildAll();
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Placement", buildPlacementGrid()),
                section("Content", buildContentGrid()),
                section("Behavior", buildBehaviorGrid()),
                section("Animation", buildAnimationGrid()),
                section("Ripple", buildRippleGrid()));
    }

    // ==================== Sections ====================

    private Node buildPlacementGrid() {
        placementPicker = new ComboBox<>();
        placementPicker.getItems().setAll(RXPlacement.values());
        placementPicker.setValue(menuButton.getPlacement());
        placementPicker.setMaxWidth(Double.MAX_VALUE);
        placementPicker.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                menuButton.setPlacement(value);
            }
        });

        Slider offsetY = createSlider(-20.0, 20.0, popup.getOffsetY());
        popup.offsetYProperty().bind(offsetY.valueProperty());
        Label offsetValue = createValueLabel(offsetY, "%.0f px");

        return createGrid(
                row("Placement", placementPicker),
                row("Popup offset Y", offsetY, offsetValue));
    }

    private Node buildContentGrid() {
        return createGrid(
                row(contentToggle("Leading icons", showIcons, v -> showIcons = v)),
                row(contentToggle("Trailing shortcuts", showShortcuts, v -> showShortcuts = v)),
                row(contentToggle("Dividers (separator)", showDividers, v -> showDividers = v)),
                row(contentToggle("Subheaders (header)", showSubheaders, v -> showSubheaders = v)),
                row(contentToggle("Checkbox / radio items", showCheckRadio, v -> showCheckRadio = v)),
                row(contentToggle("Danger item", showDanger, v -> showDanger = v)),
                row(contentToggle("Disabled item", showDisabled, v -> showDisabled = v)));
    }

    private Node buildBehaviorGrid() {
        CheckBox dense = new CheckBox("dense (:dense)");
        dense.selectedProperty().addListener((obs, old, v) -> {
            liveList.setDense(v);
            popup.getMenuList().setDense(v);
        });

        CheckBox selectedFocus = new CheckBox("initialFocus = SELECTED");
        selectedFocus.selectedProperty().addListener((obs, old, v) -> {
            RXMenuList.InitialFocus focus = v ? RXMenuList.InitialFocus.SELECTED : RXMenuList.InitialFocus.FIRST;
            liveList.setInitialFocus(focus);
            popup.getMenuList().setInitialFocus(focus);
        });

        CheckBox disabledFocusable = new CheckBox("disabledItemsFocusable");
        disabledFocusable.selectedProperty().addListener((obs, old, v) -> {
            liveList.setDisabledItemsFocusable(v);
            popup.getMenuList().setDisabledItemsFocusable(v);
        });

        CheckBox wrap = new CheckBox("wrapAround");
        wrap.setSelected(liveList.isWrapAround());
        wrap.selectedProperty().addListener((obs, old, v) -> {
            liveList.setWrapAround(v);
            popup.getMenuList().setWrapAround(v);
        });

        return createGrid(row(dense), row(selectedFocus), row(disabledFocusable), row(wrap));
    }

    private Node buildAnimationGrid() {
        CheckBox animated = new CheckBox("animated");
        animated.setSelected(popup.getMenuList().isAnimated());
        animated.selectedProperty().addListener((obs, old, v) -> popup.getMenuList().setAnimated(v));

        Slider duration = createSlider(0.0, 600.0, popup.getMenuList().getAnimationDuration().toMillis());
        popup.getMenuList().animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(duration.getValue()), duration.valueProperty()));
        Label durationValue = createValueLabel(duration, "%.0f ms");

        interpolators.put("EASE_OUT", Interpolator.EASE_OUT);
        interpolators.put("EASE_BOTH", Interpolator.EASE_BOTH);
        interpolators.put("EASE_IN", Interpolator.EASE_IN);
        interpolators.put("LINEAR", Interpolator.LINEAR);
        ComboBox<String> interpolator = new ComboBox<>();
        interpolator.getItems().setAll(interpolators.keySet());
        interpolator.setValue("EASE_OUT");
        interpolator.setMaxWidth(Double.MAX_VALUE);
        interpolator.valueProperty().addListener((obs, old, value) ->
                popup.getMenuList().setAnimationInterpolator(interpolators.get(value)));

        Label note = new Label("Entrance animation plays when the popup opens.");
        note.getStyleClass().add("note-label");
        note.setWrapText(true);

        return createGrid(
                row(animated),
                row("Duration", duration, durationValue),
                row("Easing", interpolator),
                row(note));
    }

    private Node buildRippleGrid() {
        CheckBox rippleEnabled = new CheckBox("rippleEnabled");
        rippleEnabled.setSelected(liveList.isRippleEnabled());
        rippleEnabled.selectedProperty().addListener((obs, old, v) -> {
            liveList.setRippleEnabled(v);
            popup.getMenuList().setRippleEnabled(v);
        });

        CheckBox stateOverlay = new CheckBox("stateOverlayEnabled");
        stateOverlay.setSelected(liveList.isStateOverlayEnabled());
        stateOverlay.selectedProperty().addListener((obs, old, v) -> {
            liveList.setStateOverlayEnabled(v);
            popup.getMenuList().setStateOverlayEnabled(v);
        });

        Slider opacity = createSlider(0.0, 1.0, liveList.getRippleOpacity());
        opacity.valueProperty().addListener((obs, old, v) -> {
            liveList.setRippleOpacity(v.doubleValue());
            popup.getMenuList().setRippleOpacity(v.doubleValue());
        });
        Label opacityValue = createValueLabel(opacity, "%.2f");

        return createGrid(
                row(rippleEnabled),
                row(stateOverlay),
                row("Opacity", opacity, opacityValue));
    }

    // ==================== Item spec ====================

    private RXPlacement currentPlacement() {
        return placementPicker != null && placementPicker.getValue() != null
                ? placementPicker.getValue() : RXPlacement.BOTTOM_START;
    }

    private CheckBox contentToggle(String text, boolean initial, Consumer<Boolean> sink) {
        CheckBox box = new CheckBox(text);
        box.setSelected(initial);
        box.selectedProperty().addListener((obs, old, v) -> {
            sink.accept(v);
            rebuildAll();
        });
        return box;
    }

    private void rebuildAll() {
        menuButton.getItems().setAll(buildItems());
        popup.getItems().setAll(buildItems());
        liveList.getItems().setAll(buildItems());
        contextButton.getItems().setAll(buildItems());
    }

    /**
     * Builds a fresh, representative item list from the current content toggles.
     * Fresh instances are required per host — an {@link RXMenuItem} (and its
     * graphic {@link Node}) belongs to a single list at a time.
     *
     * @return the freshly built item list
     */
    private List<RXMenuItem> buildItems() {
        List<RXMenuItem> items = new ArrayList<>();
        if (showSubheaders) {
            items.add(RXMenuItem.header("File"));
        }
        items.add(command("New", FILE, "Shortcut+N"));
        items.add(command("Open…", FOLDER, "Shortcut+O"));

        if (showDividers) {
            items.add(RXMenuItem.separator());
        }
        if (showSubheaders) {
            items.add(RXMenuItem.header("View"));
        }
        if (showCheckRadio) {
            items.add(RXMenuItem.checkbox("Word wrap", new SimpleBooleanProperty(true)));
            ToggleGroup theme = new ToggleGroup();
            RXMenuItem light = RXMenuItem.radio("Light", theme);
            RXMenuItem dark = RXMenuItem.radio("Dark", theme);
            light.setSelected(true);
            items.add(light);
            items.add(dark);
        }
        if (showDisabled) {
            RXMenuItem disabled = command("Unavailable", null, null);
            disabled.setDisable(true);
            items.add(disabled);
        }
        if (showDanger) {
            if (showDividers) {
                items.add(RXMenuItem.separator());
            }
            RXMenuItem delete = command("Delete", TRASH, "Shortcut+D");
            delete.setDanger(true);
            items.add(delete);
        }
        return items;
    }

    private RXMenuItem command(String text, String iconPath, String accelerator) {
        RXMenuItem item = showIcons && iconPath != null
                ? RXMenuItem.of(text, icon(iconPath)) : RXMenuItem.of(text);
        if (showShortcuts && accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        return item;
    }

    private static Region icon(String svgPath) {
        Region region = new Region();
        region.getStyleClass().add("menu-icon");
        region.setStyle("-fx-shape: \"" + svgPath + "\";");
        return region;
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
