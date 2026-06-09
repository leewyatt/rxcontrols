package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import io.github.leewyatt.rxcontrols.layout.RXRowAlign;
import io.github.leewyatt.rxcontrols.layout.RXRowJustify;
import io.github.leewyatt.rxcontrols.samples.demo.RXResponsiveLayoutDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/**
 * Showcase application for {@link RXRow} and {@link RXCol}.
 *
 * <p>Exercises the Row/Col layout surface: profile, columns, width, gutter,
 * row gap, justify, align, base span, offset, order, responsive order, and
 * responsive hidden. The preview keeps the row in a horizontally scrollable
 * viewport so breakpoint boundaries can be tested without resizing the window.</p>
 *
 * <p>For a compact standalone demo see {@link RXResponsiveLayoutDemo}.</p>
 */
public class RXResponsiveLayoutShowcase extends RXShowcaseApplication {

    private static final double[] WIDTH_PRESETS = {
            360.0, 575.0, 576.0, 767.0, 768.0, 991.0, 992.0,
            1199.0, 1200.0, 1399.0, 1400.0, 1919.0, 1920.0
    };

    private RXRow row;
    private RXCol summaryCol;
    private RXCol imageCol;
    private RXCol regionCol;
    private RXCol shapeCol;
    private RXCol baseCol;
    private ProfilePreset profilePreset = ProfilePreset.ANT_DESIGN;
    private Slider baseSpanSlider;
    private Slider mdOffsetSlider;
    private Slider mdOrderSlider;
    private CheckBox hideImageLgBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXResponsiveLayout";
    }

    @Override
    protected String subtitle() {
        return "Container-width responsive Row and Col layout";
    }

    @Override
    protected String windowTitle() {
        return "RXResponsiveLayout Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 740.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 450.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-responsive-layout-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        row = new RXRow();
        row.getStyleClass().add("showcase-row");
        row.setPadding(new Insets(16.0));
        row.setGutter(16.0);
        row.setRowGap(16.0);
        row.setPrefWidth(768.0);
        row.setMinWidth(Region.USE_PREF_SIZE);
        row.setMaxWidth(Region.USE_PREF_SIZE);

        summaryCol = createCol("Summary", createStatusButton("Open"));
        imageCol = createCol("Image", createImageView());
        regionCol = createCol("Region", createRegionBlock());
        shapeCol = createCol("Shape", createShape());
        baseCol = createCol("Base span", createStatusButton("Base"));
        row.getChildren().addAll(summaryCol, imageCol, regionCol, shapeCol, baseCol);
        applyProfileSpecs(true);

        StackPane rowFrame = new StackPane(row);
        rowFrame.getStyleClass().add("row-frame");
        rowFrame.setAlignment(Pos.TOP_LEFT);
        rowFrame.setMinWidth(Region.USE_PREF_SIZE);
        rowFrame.setMaxWidth(Region.USE_PREF_SIZE);

        ScrollPane scroll = new ScrollPane(rowFrame);
        scroll.getStyleClass().add("row-scroll");
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setPrefViewportWidth(660.0);
        scroll.setPrefViewportHeight(430.0);

        Label activeLabel = new Label();
        activeLabel.getStyleClass().add("status-label");
        activeLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            RXBreakpoint breakpoint = row.getActiveBreakpoint();
            if (breakpoint == null) {
                return "breakpoint ?";
            }
            return breakpoint.getName() + " >= " + Math.round(breakpoint.getMinWidth());
        }, row.activeBreakpointProperty()));

        Label widthLabel = new Label();
        widthLabel.getStyleClass().add("status-label");
        widthLabel.textProperty().bind(Bindings.format("row %.0f px", row.prefWidthProperty()));

        HBox status = new HBox(8.0, activeLabel, widthLabel);
        status.getStyleClass().add("status-strip");
        status.setAlignment(Pos.CENTER_LEFT);

        VBox preview = new VBox(12.0, status, scroll);
        preview.getStyleClass().add("responsive-preview");
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Profile", buildProfileGrid()),
                section("Row", buildRowGrid()),
                section("Columns", buildColumnGrid()));
    }

    // ==================== Sections ====================

    private Node buildProfileGrid() {
        ComboBox<ProfilePreset> profileBox = new ComboBox<>();
        profileBox.getItems().setAll(ProfilePreset.values());
        profileBox.setValue(profilePreset);
        profileBox.setMaxWidth(Double.MAX_VALUE);

        Slider columnsSlider = createSlider(4.0, 24.0, profilePreset.columns());
        configureIntegerSlider(columnsSlider);
        columnsSlider.setMajorTickUnit(4.0);
        columnsSlider.valueProperty().addListener((obs, oldV, newV) -> {
            row.setColumns(newV.intValue());
            configureMdOffsetSlider();
            applyProfileSpecs(false);
            syncBaseSpanSlider();
        });
        Label columnsValue = createValueLabel(columnsSlider, "%.0f");

        Slider widthSlider = createSlider(320.0, 1920.0, row.getPrefWidth());
        widthSlider.setMajorTickUnit(400.0);
        widthSlider.setShowTickMarks(true);
        row.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        profileBox.valueProperty().addListener((obs, oldPreset, newPreset) -> {
            if (newPreset == null) {
                return;
            }
            profilePreset = newPreset;
            row.setBreakpointProfile(newPreset.profile());
            columnsSlider.setValue(newPreset.columns());
            applyProfileSpecs(true);
            syncBaseSpanSlider();
            syncBaseSpanValue();
            configureMdOffsetSlider();
        });

        Label activeValue = new Label();
        activeValue.getStyleClass().add("value-label");
        activeValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        activeValue.setAlignment(Pos.CENTER_RIGHT);
        activeValue.textProperty().bind(Bindings.createStringBinding(() -> {
            RXBreakpoint breakpoint = row.getActiveBreakpoint();
            return breakpoint == null ? "" : breakpoint.getName();
        }, row.activeBreakpointProperty()));

        FlowPane presetButtons = new FlowPane(6.0, 6.0);
        presetButtons.setAlignment(Pos.CENTER_LEFT);
        for (double preset : WIDTH_PRESETS) {
            Button button = new Button(String.format("%.0f", preset));
            button.setOnAction(e -> widthSlider.setValue(preset));
            presetButtons.getChildren().add(button);
        }

        return createGrid(
                row("Profile", profileBox),
                row("Columns", columnsSlider, columnsValue),
                row("Width", widthSlider, widthValue),
                row("Active", activeValue),
                row("Presets", presetButtons));
    }

    private Node buildRowGrid() {
        Slider gutterSlider = createSlider(0.0, 48.0, row.getGutter());
        row.gutterProperty().bind(gutterSlider.valueProperty());
        Label gutterValue = createValueLabel(gutterSlider, "%.0f px");

        Slider rowGapSlider = createSlider(0.0, 48.0, row.getRowGap());
        row.rowGapProperty().bind(rowGapSlider.valueProperty());
        Label rowGapValue = createValueLabel(rowGapSlider, "%.0f px");

        ComboBox<RXRowJustify> justifyBox = new ComboBox<>();
        justifyBox.getItems().setAll(RXRowJustify.values());
        justifyBox.setValue(row.getJustify());
        justifyBox.setMaxWidth(Double.MAX_VALUE);
        row.justifyProperty().bind(justifyBox.valueProperty());

        ComboBox<RXRowAlign> alignBox = new ComboBox<>();
        alignBox.getItems().setAll(RXRowAlign.values());
        alignBox.setValue(row.getAlign());
        alignBox.setMaxWidth(Double.MAX_VALUE);
        row.alignProperty().bind(alignBox.valueProperty());

        return createGrid(
                row("Gutter", gutterSlider, gutterValue),
                row("Row gap", rowGapSlider, rowGapValue),
                row("Justify", justifyBox),
                row("Align", alignBox));
    }

    private Node buildColumnGrid() {
        baseSpanSlider = createSlider(0.0, row.getColumns(), baseCol.getSpan());
        configureIntegerSlider(baseSpanSlider);
        baseSpanSlider.valueProperty().addListener((obs, oldV, newV) ->
                baseCol.setSpan(newV.intValue()));
        Label baseSpanValue = createValueLabel(baseSpanSlider, "%.0f");

        Slider shapeOrderSlider = createSlider(-2.0, 2.0, shapeCol.getOrder());
        configureIntegerSlider(shapeOrderSlider);
        shapeOrderSlider.valueProperty().addListener((obs, oldV, newV) ->
                shapeCol.setOrder(newV.intValue()));
        Label shapeOrderValue = createValueLabel(shapeOrderSlider, "%.0f");

        mdOffsetSlider = createSlider(0.0, mdOffsetMax(), 0.0);
        configureIntegerSlider(mdOffsetSlider);
        mdOffsetSlider.valueProperty().addListener((obs, oldV, newV) -> updateShapeMdSpec());
        Label mdOffsetValue = createValueLabel(mdOffsetSlider, "%.0f");

        mdOrderSlider = createSlider(-2.0, 2.0, 0.0);
        configureIntegerSlider(mdOrderSlider);
        mdOrderSlider.valueProperty().addListener((obs, oldV, newV) -> updateShapeMdSpec());
        Label mdOrderValue = createValueLabel(mdOrderSlider, "%.0f");

        hideImageLgBox = new CheckBox("Hide image at lg and wider");
        hideImageLgBox.selectedProperty().addListener((obs, oldV, selected) ->
                updateImageLgSpec());

        return createGrid(
                row("Base span", baseSpanSlider, baseSpanValue),
                row("Shape order", shapeOrderSlider, shapeOrderValue),
                row("MD offset", mdOffsetSlider, mdOffsetValue),
                row("MD order", mdOrderSlider, mdOrderValue),
                row(hideImageLgBox));
    }

    // ==================== Preview helpers ====================

    private RXCol createCol(String title, Node body) {
        RXCol col = new RXCol();
        col.getChildren().add(createTile(title, body, col));
        return col;
    }

    private Node createTile(String title, Node body, RXCol owner) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tile-title");

        Label orderLabel = new Label();
        orderLabel.getStyleClass().add("order-label");
        orderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "order " + resolveEffectiveOrder(owner),
                row.activeBreakpointProperty(),
                row.breakpointProfileProperty(),
                owner.orderProperty(),
                owner.xsProperty(),
                owner.smProperty(),
                owner.mdProperty(),
                owner.lgProperty(),
                owner.xlProperty(),
                owner.xxlProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8.0, titleLabel, spacer, orderLabel);
        header.getStyleClass().add("tile-header");

        StackPane bodyPane = new StackPane(body);
        bodyPane.getStyleClass().add("tile-body");

        VBox tile = new VBox(8.0, header, bodyPane);
        tile.getStyleClass().add("tile");
        VBox.setVgrow(bodyPane, Priority.ALWAYS);
        return tile;
    }

    private Node createStatusButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("status-button");
        return button;
    }

    private Node createImageView() {
        Image image = new Image(getClass().getResource("/scenery/2.png").toExternalForm(), true);
        RXClipPathImageView imageView = new RXClipPathImageView(image);
        imageView.setPrefSize(200.0, 110.0);
        imageView.setClipSvg(RXClipPathImageView.SHAPE_ROUNDED_RECT);
        return imageView;
    }

    private Node createRegionBlock() {
        Region region = new Region();
        region.getStyleClass().add("bar-region");
        region.setPrefSize(200.0, 96.0);
        return region;
    }

    private Node createShape() {
        Circle circle = new Circle(36.0, Color.web("#4f8cff"));
        circle.setStroke(Color.web("#173b76"));
        circle.setStrokeWidth(3.0);
        return circle;
    }

    // ==================== Responsive spec helpers ====================

    private void applyProfileSpecs(boolean resetBaseSpan) {
        int full = row.getColumns();
        int half = Math.max(1, full / 2);
        int third = Math.max(1, full / 3);
        int quarter = Math.max(1, full / 4);

        summaryCol.setXs(RXColSpec.of(full));
        summaryCol.setSm(RXColSpec.of(half));
        summaryCol.setMd(RXColSpec.of(third));
        summaryCol.setLg(RXColSpec.of(quarter));

        imageCol.setXs(RXColSpec.of(full));
        imageCol.setSm(RXColSpec.of(half));
        imageCol.setMd(RXColSpec.of(third));
        updateImageLgSpec();

        regionCol.setXs(RXColSpec.of(full));
        regionCol.setSm(RXColSpec.of(full));
        regionCol.setMd(RXColSpec.of(third));
        regionCol.setLg(RXColSpec.of(half));

        shapeCol.setXs(RXColSpec.of(full));
        shapeCol.setSm(RXColSpec.of(half, 0));
        shapeCol.setLg(RXColSpec.of(quarter, 0));
        updateShapeMdSpec();

        if (resetBaseSpan) {
            baseCol.setSpan(quarter);
        }
    }

    private void updateImageLgSpec() {
        if (imageCol == null) {
            return;
        }
        int quarter = Math.max(1, row.getColumns() / 4);
        boolean hideImage = hideImageLgBox != null && hideImageLgBox.isSelected();
        imageCol.setLg(RXColSpec.builder()
                .span(quarter)
                .hidden(hideImage)
                .build());
    }

    private void updateShapeMdSpec() {
        if (shapeCol == null) {
            return;
        }
        int third = Math.max(1, row.getColumns() / 3);
        int offset = mdOffsetSlider == null ? 0 : (int) Math.round(mdOffsetSlider.getValue());
        int order = mdOrderSlider == null ? 0 : (int) Math.round(mdOrderSlider.getValue());
        shapeCol.setMd(RXColSpec.builder()
                .span(third)
                .offset(offset)
                .order(order)
                .build());
    }

    private int resolveEffectiveOrder(RXCol col) {
        int order = col.getOrder();
        RXBreakpoint active = row.getActiveBreakpoint();
        if (active == null) {
            return order;
        }
        double activeMinWidth = active.getMinWidth();
        for (RXBreakpoint breakpoint : row.getBreakpointProfile().getBreakpoints()) {
            if (breakpoint.getMinWidth() > activeMinWidth) {
                break;
            }
            RXColSpec spec = specFor(col, breakpoint.getName());
            if (spec != null && spec.getOrder() != null) {
                order = spec.getOrder();
            }
        }
        return order;
    }

    private RXColSpec specFor(RXCol col, String breakpointName) {
        return switch (breakpointName) {
            case "xs" -> col.getXs();
            case "sm" -> col.getSm();
            case "md" -> col.getMd();
            case "lg" -> col.getLg();
            case "xl" -> col.getXl();
            case "xxl" -> col.getXxl();
            default -> col.getBreakpointSpec(breakpointName);
        };
    }

    private void syncBaseSpanSlider() {
        if (baseSpanSlider == null) {
            return;
        }
        baseSpanSlider.setMax(row.getColumns());
        if (baseSpanSlider.getValue() > baseSpanSlider.getMax()) {
            baseSpanSlider.setValue(baseSpanSlider.getMax());
        }
    }

    private void syncBaseSpanValue() {
        if (baseSpanSlider != null) {
            baseSpanSlider.setValue(baseCol.getSpan());
        }
    }

    private void configureMdOffsetSlider() {
        if (mdOffsetSlider == null) {
            return;
        }
        mdOffsetSlider.setMax(mdOffsetMax());
        if (mdOffsetSlider.getValue() > mdOffsetSlider.getMax()) {
            mdOffsetSlider.setValue(mdOffsetSlider.getMax());
        }
    }

    private void configureIntegerSlider(Slider slider) {
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(1.0);
        slider.setMinorTickCount(0);
        slider.setShowTickMarks(true);
    }

    private double mdOffsetMax() {
        return Math.max(0.0, row.getColumns() / 6.0);
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private enum ProfilePreset {
        ANT_DESIGN("Ant Design 24", RXBreakpointProfile.ANT_DESIGN),
        ELEMENT("Element 24", RXBreakpointProfile.ELEMENT),
        BOOTSTRAP("Bootstrap 12", RXBreakpointProfile.BOOTSTRAP);

        private final String text;
        private final RXBreakpointProfile profile;

        ProfilePreset(String text, RXBreakpointProfile profile) {
            this.text = text;
            this.profile = profile;
        }

        private RXBreakpointProfile profile() {
            return profile;
        }

        private int columns() {
            return profile.getColumns();
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
