package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveCol;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveRow;
import io.github.leewyatt.rxcontrols.layout.RXRowAlign;
import io.github.leewyatt.rxcontrols.layout.RXRowJustify;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
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
import javafx.stage.Stage;

/**
 * Demo for {@link RXResponsiveRow} and {@link RXResponsiveCol}.
 */
public class RXResponsiveLayoutDemo extends Application {

    private static final double[] WIDTH_PRESETS = {
            360.0, 575.0, 576.0, 767.0, 768.0, 991.0, 992.0,
            1199.0, 1200.0, 1399.0, 1400.0, 1919.0, 1920.0
    };

    @Override
    public void start(Stage primaryStage) {
        ResponsiveDemoNodes demoNodes = createResponsiveRow();
        RXResponsiveRow row = demoNodes.row();

        ComboBox<ProfilePreset> profileBox =
                new ComboBox<>(FXCollections.observableArrayList(ProfilePreset.values()));
        profileBox.setValue(ProfilePreset.ELEMENT);

        Slider columnsSlider = new Slider(4.0, 24.0, RXResponsiveRow.DEFAULT_COLUMNS);
        columnsSlider.setSnapToTicks(true);
        columnsSlider.setMajorTickUnit(4.0);
        columnsSlider.setMinorTickCount(3);
        columnsSlider.valueProperty().addListener((obs, oldV, newV) ->
                row.setColumns(newV.intValue()));
        Label columnsValue = new Label();
        columnsValue.getStyleClass().add("value-label");
        columnsValue.textProperty().bind(
                Bindings.format("%.0f", columnsSlider.valueProperty()));

        profileBox.valueProperty().addListener((obs, oldPreset, newPreset) -> {
            if (newPreset == null) {
                return;
            }
            row.setBreakpointProfile(newPreset.profile());
            columnsSlider.setValue(newPreset.columns());
        });

        Slider widthSlider = new Slider(320.0, 1920.0, 768.0);
        widthSlider.setShowTickMarks(true);
        widthSlider.setMajorTickUnit(400.0);
        Label widthValue = new Label();
        widthValue.getStyleClass().add("value-label");
        widthValue.textProperty().bind(Bindings.format("%.0f px", widthSlider.valueProperty()));

        Slider gutterSlider = new Slider(0.0, 48.0, 16.0);
        row.gutterProperty().bind(gutterSlider.valueProperty());
        Label gutterValue = new Label();
        gutterValue.getStyleClass().add("value-label");
        gutterValue.textProperty().bind(Bindings.format("%.0f px", gutterSlider.valueProperty()));

        Slider rowGapSlider = new Slider(0.0, 48.0, 16.0);
        row.rowGapProperty().bind(rowGapSlider.valueProperty());
        Label rowGapValue = new Label();
        rowGapValue.getStyleClass().add("value-label");
        rowGapValue.textProperty().bind(Bindings.format("%.0f px", rowGapSlider.valueProperty()));

        ComboBox<RXRowJustify> justifyBox =
                new ComboBox<>(FXCollections.observableArrayList(RXRowJustify.values()));
        justifyBox.setValue(RXRowJustify.START);
        row.justifyProperty().bind(justifyBox.valueProperty());

        ComboBox<RXRowAlign> alignBox =
                new ComboBox<>(FXCollections.observableArrayList(RXRowAlign.values()));
        alignBox.setValue(RXRowAlign.TOP);
        row.alignProperty().bind(alignBox.valueProperty());

        Slider shapeOrderSlider = new Slider(-2.0, 2.0, 0.0);
        shapeOrderSlider.setSnapToTicks(true);
        shapeOrderSlider.setMajorTickUnit(1.0);
        shapeOrderSlider.setMinorTickCount(0);
        shapeOrderSlider.valueProperty().addListener((obs, oldV, newV) ->
                demoNodes.shape().setOrder((int) Math.round(newV.doubleValue())));
        Label shapeOrderValue = new Label();
        shapeOrderValue.getStyleClass().add("value-label");
        shapeOrderValue.textProperty().bind(demoNodes.shape().orderProperty().asString());

        Slider mdOffsetSlider = new Slider(0.0, 4.0, 0.0);
        mdOffsetSlider.setSnapToTicks(true);
        mdOffsetSlider.setMajorTickUnit(1.0);
        mdOffsetSlider.setMinorTickCount(0);
        mdOffsetSlider.valueProperty().addListener((obs, oldV, newV) ->
                demoNodes.shape().setMd(RXColSpec.of(8, (int) Math.round(newV.doubleValue()))));
        Label mdOffsetValue = new Label();
        mdOffsetValue.getStyleClass().add("value-label");
        mdOffsetValue.textProperty().bind(
                Bindings.format("%.0f", mdOffsetSlider.valueProperty()));

        CheckBox hideImageLg = new CheckBox("lg+");
        hideImageLg.selectedProperty().addListener((obs, wasHidden, isHidden) ->
                setImageLgHidden(demoNodes.image(), isHidden));

        Label breakpointLabel = new Label();
        breakpointLabel.getStyleClass().add("breakpoint-label");
        breakpointLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            RXBreakpoint breakpoint = row.getActiveBreakpoint();
            if (breakpoint == null) {
                return "";
            }
            return breakpoint.getName() + " >= " + Math.round(breakpoint.getMinWidth());
        }, row.activeBreakpointProperty()));

        FlowPane presetButtons = new FlowPane(6.0, 6.0);
        presetButtons.setAlignment(Pos.CENTER_LEFT);
        for (double preset : WIDTH_PRESETS) {
            Button button = new Button(String.format("%.0f", preset));
            button.setOnAction(e -> widthSlider.setValue(preset));
            presetButtons.getChildren().add(button);
        }

        VBox controls = new VBox(10.0,
                controlRow("Profile", profileBox, new Label()),
                controlRow("Columns", columnsSlider, columnsValue),
                controlRow("Width", widthSlider, widthValue),
                controlRow("Gutter", gutterSlider, gutterValue),
                controlRow("Row gap", rowGapSlider, rowGapValue),
                controlRow("Justify", justifyBox, breakpointLabel),
                controlRow("Align", alignBox, new Label()),
                controlRow("Shape order", shapeOrderSlider, shapeOrderValue),
                controlRow("MD offset", mdOffsetSlider, mdOffsetValue),
                controlRow("Hide image", hideImageLg, new Label()),
                presetButtons);
        controls.getStyleClass().add("toolbar");

        StackPane rowFrame = new StackPane(row);
        rowFrame.getStyleClass().add("row-frame");
        rowFrame.prefWidthProperty().bind(widthSlider.valueProperty());
        rowFrame.setMinWidth(Region.USE_PREF_SIZE);
        rowFrame.setMaxWidth(Region.USE_PREF_SIZE);

        ScrollPane scroll = new ScrollPane(rowFrame);
        scroll.getStyleClass().add("demo-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);

        VBox root = new VBox(14.0, controls, scroll);
        root.getStyleClass().add("root");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 1080.0, 720.0);
        scene.getStylesheets().add(
                getClass().getResource("rx_responsive_layout_demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXResponsiveLayout Demo");
        primaryStage.show();
    }

    private ResponsiveDemoNodes createResponsiveRow() {
        RXResponsiveRow row = new RXResponsiveRow();
        row.getStyleClass().add("demo-row");
        row.setPadding(new Insets(16.0));

        RXResponsiveCol summary = col("Summary", buildStatusButton("Open"),
                RXColSpec.of(24), RXColSpec.of(12), RXColSpec.of(8), RXColSpec.of(6));
        RXResponsiveCol image = col("Image", buildImage(),
                RXColSpec.of(24), RXColSpec.of(12), RXColSpec.of(8), RXColSpec.of(6));
        RXResponsiveCol chart = col("Region", buildRegion(),
                RXColSpec.of(24), RXColSpec.of(24), RXColSpec.of(8), RXColSpec.of(12));
        RXResponsiveCol shape = col("Shape", buildShape(),
                RXColSpec.of(24), RXColSpec.of(12, 0), RXColSpec.of(8, 0), RXColSpec.of(6, 0));
        RXResponsiveCol base = new RXResponsiveCol();
        base.setSpan(6);
        base.getChildren().add(tile("Base span", buildStatusButton("Base"), base));

        row.getChildren().addAll(summary, image, chart, shape, base);
        return new ResponsiveDemoNodes(row, image, shape);
    }

    private void setImageLgHidden(RXResponsiveCol image, boolean hidden) {
        image.setLg(RXColSpec.builder()
                .span(6)
                .hidden(hidden)
                .build());
    }

    private RXResponsiveCol col(String title, Node body, RXColSpec xs, RXColSpec sm,
                                RXColSpec md, RXColSpec lg) {
        RXResponsiveCol col = new RXResponsiveCol();
        col.setXs(xs);
        col.setSm(sm);
        col.setMd(md);
        col.setLg(lg);
        col.getChildren().add(tile(title, body, col));
        return col;
    }

    private Node tile(String title, Node body, RXResponsiveCol owner) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tile-title");
        Label orderLabel = new Label();
        orderLabel.getStyleClass().add("order-label");
        orderLabel.textProperty().bind(owner.orderProperty().asString("order %d"));
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

    private Node buildStatusButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("status-button");
        return button;
    }

    private Node buildImage() {
        Image image = new Image(getClass().getResource("/scenery/2.png").toExternalForm(), true);
        RXImageView imageView = new RXImageView(image);
        imageView.setPrefSize(180.0, 96.0);
        imageView.setClipSvgPath(RXImageView.SHAPE_ROUNDED_RECT);
        return imageView;
    }

    private Node buildRegion() {
        Region region = new Region();
        region.getStyleClass().add("bar-region");
        region.setPrefSize(180.0, 84.0);
        return region;
    }

    private Node buildShape() {
        Circle circle = new Circle(34.0, Color.web("#4f8cff"));
        circle.setStroke(Color.web("#173b76"));
        circle.setStrokeWidth(3.0);
        return circle;
    }

    private Node controlRow(String label, Node control, Node value) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        HBox row = new HBox(10.0, fieldLabel, control, value);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private enum ProfilePreset {
        ELEMENT("Element 24", RXBreakpointProfile.ELEMENT,
                RXBreakpointProfile.ELEMENT.getColumns()),
        BOOTSTRAP("Bootstrap 12", RXBreakpointProfile.BOOTSTRAP,
                RXBreakpointProfile.BOOTSTRAP.getColumns());

        private final String text;
        private final RXBreakpointProfile profile;
        private final int columns;

        ProfilePreset(String text, RXBreakpointProfile profile, int columns) {
            this.text = text;
            this.profile = profile;
            this.columns = columns;
        }

        private RXBreakpointProfile profile() {
            return profile;
        }

        private int columns() {
            return columns;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private record ResponsiveDemoNodes(RXResponsiveRow row, RXResponsiveCol image,
                                       RXResponsiveCol shape) {
    }
}
