package io.github.leewyatt.rxcontrols.samples.sampler;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes;
import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.PopupWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * The theme switcher of the sampler's top bar: a light / dark switch for the current theme
 * family (Modena and Dracula, each with one variant only, stand in for each other's
 * missing one), and a button naming the family that opens a panel of every theme, a row
 * per family with a small preview of its light and dark variants.
 */
final class SamplerThemeSwitcher extends HBox {

    private static final double PREVIEW_WIDTH = 96.0;
    private static final double PREVIEW_HEIGHT = 56.0;
    // Popups carry no theme scope class, so the panel marks Modena for its focus ring.
    private static final PseudoClass MODENA = PseudoClass.getPseudoClass("modena");

    private final List<Family> families = List.of(
            new Family("Modena",
                    variant(Application.STYLESHEET_MODENA,
                            new Palette("#ffffff", "#f4f4f4", "#c8c8c8", "#333333", "#8a8a8a", "#0096c9")),
                    null),
            new Family("Primer",
                    variant(new PrimerLight().getUserAgentStylesheet(),
                            new Palette("#ffffff", "#f6f8fa", "#d0d7de", "#24292f", "#57606a", "#0969da")),
                    variant(new PrimerDark().getUserAgentStylesheet(),
                            new Palette("#0d1117", "#161b22", "#30363d", "#c9d1d9", "#8b949e", "#1f6feb"))),
            new Family("Nord",
                    variant(new NordLight().getUserAgentStylesheet(),
                            new Palette("#fafafc", "#eef1f5", "#c7ceda", "#2e3440", "#4c566a", "#476283")),
                    variant(new NordDark().getUserAgentStylesheet(),
                            new Palette("#2e3440", "#3b4252", "#5e6675", "#eceff4", "#979fae", "#5e81ac"))),
            new Family("Cupertino",
                    variant(new CupertinoLight().getUserAgentStylesheet(),
                            new Palette("#ffffff", "#f2f2f7", "#d1d1d6", "#000000", "#636366", "#007aff")),
                    variant(new CupertinoDark().getUserAgentStylesheet(),
                            new Palette("#1c1c1e", "#2c2c2e", "#48484a", "#ffffff", "#aeaeb2", "#0a84ff"))),
            new Family("Dracula",
                    null,
                    variant(new Dracula().getUserAgentStylesheet(),
                            new Palette("#282a36", "#3d3f4a", "#685ab3", "#f8f8f2", "#bfbfc3", "#9580ff"))));

    private final RXSegmentedControl<Boolean> mode = new RXSegmentedControl<>();
    private final RXSegmentedItem<Boolean> lightItem = modeItem(false, "Light", "sun-glyph");
    private final RXSegmentedItem<Boolean> darkItem = modeItem(true, "Dark", "moon-glyph");
    private final Label familyName = new Label();
    private final Button familyButton = new Button();
    private final PopupControl popup = new PopupControl();
    private final VBox panel = new VBox();
    private final List<Button> tiles = new ArrayList<>();

    private ThemeChoice pending;
    private boolean syncing;

    SamplerThemeSwitcher() {
        super(8.0);
        getStyleClass().add("theme-switcher");
        setAlignment(Pos.CENTER_LEFT);

        mode.getItems().setAll(lightItem, darkItem);
        mode.setEqualSegmentWidth(true);
        mode.valueProperty().addListener((obs, old, dark) -> {
            if (!syncing && dark != null) {
                ShowcaseThemes.apply(counterpart(familyOf(ShowcaseThemes.getCurrent()), dark).choice());
            }
        });

        Region chevron = glyph("chevron-down-glyph");
        HBox face = new HBox(8.0, glyph("palette-glyph"), familyName, chevron);
        face.setAlignment(Pos.CENTER_LEFT);
        familyButton.getStyleClass().add("family-button");
        familyButton.setGraphic(face);
        familyButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        familyButton.setAccessibleText("Choose a theme");
        familyButton.setOnAction(event -> togglePanel());

        buildPopup();
        getChildren().setAll(mode, familyButton);

        ShowcaseThemes.currentProperty().addListener((obs, old, choice) -> sync());
        sceneProperty().addListener((obs, old, scene) -> ShowcaseThemes.register(scene));
        sync();
    }

    // ==================== Panel ====================

    private void buildPopup() {
        Label title = new Label("Theme");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label columns = new Label("Light · Dark");
        HBox header = new HBox(title, spacer, columns);
        header.getStyleClass().add("panel-header");

        panel.getChildren().add(header);
        panel.getStyleClass().add("theme-panel");
        for (Family family : families) {
            Region divider = new Region();
            divider.getStyleClass().add("divider");
            panel.getChildren().addAll(divider, familyRow(family));
        }

        popup.getStyleClass().add("theme-popup");
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_RIGHT);
        popup.setSkin(new Skin<>() {
            @Override
            public PopupControl getSkinnable() {
                return popup;
            }

            @Override
            public Node getNode() {
                return panel;
            }

            @Override
            public void dispose() {
            }
        });
        // Switching themes under an open popup logs CSS lookup warnings, so a pick is
        // applied once the panel has closed.
        popup.setOnHidden(event -> {
            if (pending != null) {
                ThemeChoice choice = pending;
                pending = null;
                ShowcaseThemes.apply(choice);
            }
        });
    }

    private HBox familyRow(Family family) {
        Label name = new Label(family.name());
        name.getStyleClass().add("family-name");
        HBox row = new HBox(10.0, name, tile(family, family.light(), "light"), tile(family, family.dark(), "dark"));
        row.getStyleClass().add("family-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node tile(Family family, Variant variant, String side) {
        if (variant == null) {
            Label none = new Label("—");
            StackPane empty = new StackPane(none);
            empty.getStyleClass().addAll("theme-tile", "empty");
            empty.setAccessibleText(family.name() + " has no " + side + " variant");
            return empty;
        }
        RXBadge badge = new RXBadge(preview(variant.palette()));
        badge.setVariant(RXBadge.Variant.DOT);
        badge.getStyleClass().addAll("success", "current-badge");
        badge.setPosition(RXBadge.Position.TOP_END);
        // Inside the preview's corner: outside it the badge would leave the panel.
        badge.setOffsetX(14.0);
        badge.setOffsetY(12.0);
        badge.setIndicatorVisible(false);
        Button tile = new Button();
        tile.getStyleClass().add("theme-tile");
        tile.setGraphic(badge);
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setAccessibleText(family.name() + " " + side);
        tile.setUserData(variant.choice());
        tile.setOnAction(event -> {
            pending = variant.choice();
            popup.hide();
        });
        tiles.add(tile);
        return tile;
    }

    /** A miniature window in the theme's colors: a sidebar with a picked item and a form. */
    private static Pane preview(Palette palette) {
        Pane pane = new Pane(
                rect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, palette.background()),
                rect(0, 0, 26, PREVIEW_HEIGHT, palette.sidebar()),
                rect(26, 0, 1, PREVIEW_HEIGHT, palette.border()),
                rect(6, 11, 13, 3, palette.muted()),
                rect(6, 18, 13, 3, palette.accent()),
                rect(6, 25, 13, 3, palette.muted()),
                rect(34, 11, 32, 4, palette.text()),
                rect(34, 19, 46, 3, palette.muted()),
                rect(34, 30, 54, 14, palette.border()),
                rect(35, 31, 52, 12, palette.background()),
                rect(70, 34, 14, 6, palette.accent()),
                outline(palette.border()));
        pane.setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        pane.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        pane.setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        Rectangle clip = new Rectangle(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        clip.setArcWidth(10.0);
        clip.setArcHeight(10.0);
        pane.setClip(clip);
        return pane;
    }

    // A light preview on a light panel needs its edge drawn.
    private static Rectangle outline(Color stroke) {
        Rectangle outline = new Rectangle(0.5, 0.5, PREVIEW_WIDTH - 1.0, PREVIEW_HEIGHT - 1.0);
        outline.setFill(null);
        outline.setStroke(stroke);
        outline.setArcWidth(9.0);
        outline.setArcHeight(9.0);
        return outline;
    }

    private static Rectangle rect(double x, double y, double width, double height, Color fill) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setFill(fill);
        if (height <= 6.0) {
            rect.setArcWidth(height);
            rect.setArcHeight(height);
        } else {
            rect.setArcWidth(4.0);
            rect.setArcHeight(4.0);
        }
        return rect;
    }

    private void togglePanel() {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds != null) {
            popup.show(familyButton, bounds.getMaxX(), bounds.getMaxY() + 6.0);
            focusCurrentTile();
        }
    }

    // Focus starts on the current theme, as a list's focus follows its selection;
    // otherwise the first tile takes it and its ring reads as the current theme.
    private void focusCurrentTile() {
        for (Button tile : tiles) {
            if (tile.getUserData() == ShowcaseThemes.getCurrent()) {
                tile.requestFocus();
                return;
            }
        }
    }

    // ==================== State ====================

    // Follows the current theme, whoever set it, without applying it again.
    private void sync() {
        ThemeChoice current = ShowcaseThemes.getCurrent();
        Family family = familyOf(current);
        syncing = true;
        try {
            familyName.setText(family.name());
            panel.pseudoClassStateChanged(MODENA, family.name().equals("Modena"));
            mode.setValue(family.dark() != null && family.dark().choice() == current);
        } finally {
            syncing = false;
        }
        for (Button tile : tiles) {
            ((RXBadge) tile.getGraphic()).setIndicatorVisible(tile.getUserData() == current);
        }
    }

    // Modena has no dark variant and Dracula no light one, so the switch pairs the two.
    private Variant counterpart(Family family, boolean dark) {
        Variant variant = dark ? family.dark() : family.light();
        if (variant != null) {
            return variant;
        }
        return dark ? familyNamed("Dracula").dark() : familyNamed("Modena").light();
    }

    private Family familyNamed(String name) {
        for (Family family : families) {
            if (family.name().equals(name)) {
                return family;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private Family familyOf(ThemeChoice choice) {
        for (Family family : families) {
            if ((family.light() != null && family.light().choice() == choice)
                    || (family.dark() != null && family.dark().choice() == choice)) {
                return family;
            }
        }
        return families.get(0);
    }

    // ==================== Parts ====================

    private static Variant variant(String stylesheet, Palette palette) {
        for (ThemeChoice choice : ShowcaseThemes.all()) {
            if (choice.userAgentStylesheet().equals(stylesheet)) {
                return new Variant(choice, palette);
            }
        }
        throw new IllegalStateException("No theme choice uses " + stylesheet);
    }

    private static RXSegmentedItem<Boolean> modeItem(boolean dark, String name, String glyph) {
        RXSegmentedItem<Boolean> item = new RXSegmentedItem<>(dark, name);
        item.setContent(glyph(glyph));
        item.setTooltip(new Tooltip(name));
        return item;
    }

    private static Region glyph(String styleClass) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("glyph", styleClass);
        glyph.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMouseTransparent(true);
        return glyph;
    }

    private record Family(String name, Variant light, Variant dark) {
    }

    private record Variant(ThemeChoice choice, Palette palette) {
    }

    private record Palette(Color background, Color sidebar, Color border, Color text, Color muted,
                           Color accent) {

        Palette(String background, String sidebar, String border, String text, String muted, String accent) {
            this(Color.web(background), Color.web(sidebar), Color.web(border), Color.web(text),
                    Color.web(muted), Color.web(accent));
        }
    }
}
