package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIconPack;
import io.github.leewyatt.rxcontrols.RXIcons;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.icons.bootstrap.RXBootstrapIcons;
import io.github.leewyatt.rxcontrols.icons.material.RXMaterialIcons;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Showcase application for {@link RXIcon}: a browser of every icon in the
 * installed icon packs.
 *
 * <p>The grid is an {@code RXTileView} whose cells swap icon data as they are
 * reused while scrolling. Type to filter by name, choose a pack, and pick an icon
 * to see its name, its constant and the CSS that shows it, and to copy the name.
 * The control panel sets the icon size and color for the whole grid.</p>
 */
public class RXIconShowcase extends RXShowcaseApplication {

    private static final String ALL_PACKS = "All packs";
    private static final Map<String, String> CONSTANT_CLASSES = Map.of(
            "mi", RXMaterialIcons.class.getSimpleName(),
            "bi", RXBootstrapIcons.class.getSimpleName());

    private final ObservableList<RXIconData> allIcons = FXCollections.observableArrayList();
    private final FilteredList<RXIconData> shown = new FilteredList<>(allIcons);
    private final RXTextField search = new RXTextField();
    private final ComboBox<String> pack = new ComboBox<>();
    private final RXTileView<RXIconData> grid = new RXTileView<>(shown);
    private final RXIcon selectedIcon = new RXIcon();
    private final Label selectedName = new Label();
    private final Label selectedConstant = new Label();
    private final Label selectedCss = new Label();
    private Slider size;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXIcon";
    }

    @Override
    protected String subtitle() {
        return "Single-color icons from icon packs or CSS shapes";
    }

    @Override
    protected String windowTitle() {
        return "RXIcon Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-icon-showcase.css").toExternalForm();
    }

    @Override
    protected double sceneWidth() {
        return 1280.0;
    }

    @Override
    protected Node createPreview() {
        List<String> packs = new ArrayList<>();
        packs.add(ALL_PACKS);
        for (RXIconPack iconPack : RXIcons.getPacks()) {
            packs.add(iconPack.prefix());
            for (String name : iconPack.names()) {
                allIcons.add(iconPack.find(name));
            }
        }

        search.setPromptText("Search icons, e.g. arrow, home, check");
        RXIcon searchIcon = new RXIcon(RXMaterialIcons.SEARCH);
        searchIcon.getStyleClass().add("search-icon");
        searchIcon.setMouseTransparent(true);
        search.setLeading(searchIcon);
        HBox.setHgrow(search, Priority.ALWAYS);

        pack.getItems().setAll(packs);
        pack.setValue(ALL_PACKS);
        pack.setConverter(new PackNames());

        Label count = new Label();
        count.getStyleClass().add("count-label");
        count.textProperty().bind(Bindings.createStringBinding(
                () -> shown.size() + " of " + allIcons.size() + " icons", shown));

        search.textProperty().addListener(observable -> refilter());
        pack.valueProperty().addListener(observable -> refilter());

        HBox toolbar = new HBox(10.0, search, pack, count);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        grid.getStyleClass().add("icon-grid");
        grid.setPrefTileWidth(112.0);
        grid.setPrefTileHeight(84.0);
        grid.setHgap(6.0);
        grid.setVgap(6.0);
        DoubleProperty iconSize = size().valueProperty();
        grid.setCellFactory(view -> new IconCell(iconSize));
        grid.getSelectionModel().selectedItemProperty().addListener((obs, old, icon) -> select(icon));
        VBox.setVgrow(grid, Priority.ALWAYS);

        VBox browser = new VBox(12.0, toolbar, grid, buildDetails());
        browser.getStyleClass().add("icon-browser");
        browser.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (!shown.isEmpty()) {
            grid.getSelectionModel().select(0);
        }
        return browser;
    }

    @Override
    protected List<Section> createSections() {
        Label sizeValue = createValueLabel(size(), "%.0f px");

        ColorPicker color = new ColorPicker(Color.web("#37474f"));
        color.setMaxWidth(Double.MAX_VALUE);
        color.valueProperty().addListener((obs, old, value) -> applyColor(value));
        applyColor(color.getValue());

        Label note = new Label("The grid swaps the icon data of each reused cell while you scroll. "
                + "Icon data is set in code, so the new icon shows at once, with no CSS pass. "
                + "The box keeps each icon's view box proportions; the color is the region's background.");
        note.getStyleClass().add("panel-note");
        note.setWrapText(true);

        return List.of(
                section("Look", createGrid(row("Icon size", size(), sizeValue), row("Color", color))),
                section("About", note));
    }

    // ==================== Behavior ====================

    // The preview and the control panel both use the slider, in either build order.
    private Slider size() {
        if (size == null) {
            size = createSlider(12.0, 48.0, 24.0);
        }
        return size;
    }

    private void refilter() {
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String prefix = ALL_PACKS.equals(pack.getValue()) ? null : pack.getValue() + "-";
        shown.setPredicate(icon -> (prefix == null || icon.getName().startsWith(prefix))
                && (query.isEmpty() || icon.getName().contains(query.replace(' ', '-'))));
    }

    private void applyColor(Color value) {
        if (grid != null) {
            grid.setStyle("-icon-color: " + web(value) + ";");
        }
    }

    private Node buildDetails() {
        selectedIcon.setIconSize(56.0);
        selectedIcon.getStyleClass().add("selected-icon");
        selectedName.getStyleClass().add("selected-name");
        selectedConstant.getStyleClass().add("code");
        selectedCss.getStyleClass().add("code");

        RXCopyButton copy = new RXCopyButton("Copy name");
        copy.setContentFactory(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedName.getText());
            return content;
        });
        copy.disableProperty().bind(selectedIcon.iconProperty().isNull());

        VBox text = new VBox(4.0, selectedName, selectedConstant, selectedCss);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox details = new HBox(18.0, selectedIcon, text, copy);
        details.getStyleClass().add("details");
        details.setAlignment(Pos.CENTER_LEFT);
        details.setMinHeight(Region.USE_PREF_SIZE);
        return details;
    }

    private void select(RXIconData icon) {
        selectedIcon.setIcon(icon);
        if (icon == null) {
            selectedName.setText("");
            selectedConstant.setText("");
            selectedCss.setText("");
            return;
        }
        String name = icon.getName();
        int dash = name.indexOf('-');
        String constantClass = CONSTANT_CLASSES.get(name.substring(0, dash));
        selectedName.setText(name);
        selectedConstant.setText(constantClass == null ? "" : "new RXIcon(" + constantClass + "."
                + constantName(name.substring(dash + 1)) + ")");
        selectedCss.setText("-rx-icon: \"" + name + "\";");
    }

    // The constants of the generated packs: upper case with underscores, and a leading
    // underscore when the name starts with a digit.
    private static String constantName(String localName) {
        String constant = localName.toUpperCase(Locale.ROOT).replace('-', '_');
        return Character.isDigit(constant.charAt(0)) ? "_" + constant : constant;
    }

    private static String web(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
    }

    // ==================== Cell ====================

    /** A tile with the icon over its name; the nodes are created once and reused. */
    private static final class IconCell extends RXTileCell<RXIconData> {

        private final RXIcon icon = new RXIcon();
        private final Label name = new Label();
        private final VBox content = new VBox(8.0, icon, name);

        private IconCell(DoubleProperty iconSize) {
            getStyleClass().add("icon-cell");
            icon.iconSizeProperty().bind(iconSize);
            name.getStyleClass().add("icon-name");
            content.setAlignment(Pos.CENTER);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(RXIconData item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                icon.setIcon(null);
                name.setText(null);
                setGraphic(null);
            } else {
                icon.setIcon(item);
                name.setText(item.getName());
                setGraphic(content);
            }
        }
    }

    /** Shows a pack by its title; the value is its prefix. */
    private static final class PackNames extends StringConverter<String> {

        @Override
        public String toString(String prefix) {
            if (prefix == null || ALL_PACKS.equals(prefix)) {
                return ALL_PACKS;
            }
            return RXIcons.getPacks().stream().filter(candidate -> candidate.prefix().equals(prefix))
                    .map(candidate -> candidate.title() + " (" + prefix + ")").findFirst().orElse(prefix);
        }

        @Override
        public String fromString(String text) {
            return text;
        }
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
