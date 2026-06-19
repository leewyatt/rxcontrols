package io.github.leewyatt.rxcontrols.samples.support;

import io.github.leewyatt.rxcontrols.samples.support.ShowcaseThemes.ThemeChoice;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for the "showcase" style sample applications — the verbose
 * variant that exposes every styleable property of a single RXControl
 * through sliders, color pickers, combo boxes, and check boxes.
 *
 * <p>The base class owns the chrome (preview pane on the left, scrollable
 * control panel on the right, section / grid / row helpers, shared
 * stylesheet). Subclasses only contribute the control-specific content via
 * {@link #title()}, {@link #subtitle()}, {@link #createPreview()},
 * {@link #createSections()}, and {@link #stylesheetPath()}.
 *
 * <p>The simpler {@code XxxDemo} classes that sit alongside each
 * {@code XxxShowcase} stay free of this scaffold so they can demonstrate
 * the minimum code required to instantiate the control.
 */
public abstract class RXShowcaseApplication extends Application {

    // ==================== Constants ====================

    protected static final double VALUE_LABEL_MIN_WIDTH = 60.0;

    private static final String SHELL_STYLESHEET =
            "/io/github/leewyatt/rxcontrols/samples/support/rx-showcase-shell.css";
    private static final double LABEL_COL_WIDTH = 92.0;

    // ==================== Application lifecycle ====================

    private Scene scene;
    private ComboBox<ThemeChoice> themePicker;

    @Override
    public final void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(buildPreviewPane());
        root.setRight(buildControlPane());

        scene = new Scene(root, sceneWidth(), sceneHeight());
        scene.getStylesheets().add(
                RXShowcaseApplication.class.getResource(SHELL_STYLESHEET).toExternalForm());
        scene.getStylesheets().add(stylesheetPath());
        if (themePicker != null) {
            themePicker.getSelectionModel().selectFirst(); // applies the initial theme
        }
        configureScene(scene);

        primaryStage.setScene(scene);
        primaryStage.setTitle(windowTitle());
        primaryStage.show();
    }

    // ==================== Subclass hooks ====================

    /**
     * @return the title shown at the top of the control panel
     */
    protected abstract String title();

    /**
     * @return the subtitle shown below the title
     */
    protected abstract String subtitle();

    /**
     * @return the preview node hosted in the centre StackPane
     */
    protected abstract Node createPreview();

    /**
     * @return the ordered list of property sections shown in the right pane
     */
    protected abstract List<Section> createSections();

    /**
     * @return an absolute stylesheet URL (typically
     *         {@code getClass().getResource("xxx.css").toExternalForm()})
     */
    protected abstract String stylesheetPath();

    /**
     * Gives subclasses a final chance to configure the scene before it is
     * attached to the stage.
     *
     * @param scene the scene created by this showcase shell
     */
    protected void configureScene(Scene scene) {
    }

    /**
     * @return the {@link Stage} title; defaults to {@link #title()}
     */
    protected String windowTitle() {
        return title();
    }

    /**
     * Whether to show the built-in theme picker at the top of the window (lets the
     * showcase be viewed under the RxControls light/dark and AtlantaFX themes).
     * Override to return {@code false} to opt out.
     *
     * @return {@code true} to show the theme picker
     */
    protected boolean enableTheme() {
        return true;
    }

    /**
     * @return scene width in pixels
     */
    protected double sceneWidth() {
        return 1000.0;
    }

    /**
     * @return scene height in pixels
     */
    protected double sceneHeight() {
        return 660.0;
    }

    /**
     * @return preferred width of the right-hand scrollable control pane
     */
    protected double controlPaneWidth() {
        return 430.0;
    }

    // ==================== Chrome assembly ====================

    private StackPane buildPreviewPane() {
        StackPane pane = new StackPane(createPreview());
        pane.getStyleClass().add("preview-pane");
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private Node buildThemeBar() {
        ComboBox<ThemeChoice> picker = new ComboBox<>();
        picker.getItems().setAll(ShowcaseThemes.all());
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.valueProperty().addListener((obs, old, choice) -> {
            if (choice != null && scene != null) {
                choice.apply().accept(scene);
            }
        });
        themePicker = picker;

        Label label = new Label("Theme");
        label.getStyleClass().add("section-label");
        VBox box = new VBox(10.0, label, picker);
        box.getStyleClass().add("section");
        box.setFillWidth(true);
        return box;
    }

    private Node buildControlPane() {
        Label titleLabel = new Label(title());
        titleLabel.getStyleClass().add("title-label");
        Label subtitleLabel = new Label(subtitle());
        subtitleLabel.getStyleClass().add("subtitle-label");
        VBox header = new VBox(2.0, titleLabel, subtitleLabel);
        header.getStyleClass().add("header-block");

        List<Node> children = new ArrayList<>();
        children.add(header);
        if (enableTheme()) {
            children.add(buildThemeBar());
        }
        for (Section s : createSections()) {
            children.add(buildSection(s));
        }
        VBox panel = new VBox(14.0);
        panel.getChildren().setAll(children);
        panel.setFillWidth(true);
        panel.getStyleClass().add("control-panel");

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("control-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(controlPaneWidth());
        return scroll;
    }

    private VBox buildSection(Section section) {
        Label label = new Label(section.title);
        label.getStyleClass().add("section-label");
        VBox vbox = new VBox(10.0, label, section.content);
        vbox.getStyleClass().add("section");
        vbox.setFillWidth(true);
        return vbox;
    }

    // ==================== Section / row helpers ====================

    /**
     * Convenience factory for {@link Section}.
     *
     * @param title   section header label
     * @param content section body (typically a grid built via {@link #createGrid})
     * @return the section descriptor
     */
    protected static Section section(String title, Node content) {
        return new Section(title, content);
    }

    /**
     * Builds the standard three-column control grid (label / control / value).
     * Rows of length 1 span all three columns, rows of length 2 span the last
     * two, rows of length 3 fill the three columns.
     *
     * @param rows array of rows produced by {@link #row}
     * @return the configured grid
     */
    protected GridPane createGrid(Node[]... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("control-grid");
        grid.setHgap(8.0);
        grid.setVgap(10.0);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(LABEL_COL_WIDTH);
        labelCol.setPrefWidth(LABEL_COL_WIDTH);
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        grid.getColumnConstraints().addAll(labelCol, controlCol, valueCol);

        for (int i = 0; i < rows.length; i++) {
            Node[] row = rows[i];
            if (row.length == 1) {
                grid.add(row[0], 0, i, 3, 1);
            } else if (row.length == 2) {
                grid.add(row[0], 0, i);
                grid.add(row[1], 1, i, 2, 1);
            } else {
                grid.addRow(i, row);
            }
        }
        return grid;
    }

    /**
     * Three-column row: label + control + value readout.
     *
     * @param label   field label text
     * @param control the interactive control (slider, picker, ...)
     * @param value   the value-readout label
     * @return the row triplet
     */
    protected Node[] row(String label, Node control, Node value) {
        return new Node[]{createFieldLabel(label), control, value};
    }

    /**
     * Two-column row: label + control (control spans the value column).
     *
     * @param label   field label text
     * @param control the interactive control
     * @return the row pair
     */
    protected Node[] row(String label, Node control) {
        return new Node[]{createFieldLabel(label), control};
    }

    /**
     * Full-width row: a single node spanning all three columns.
     *
     * @param control the node
     * @return the row singleton
     */
    protected Node[] row(Node control) {
        return new Node[]{control};
    }

    /**
     * Creates the field label used in the leftmost grid column.
     *
     * @param text label text
     * @return the configured label
     */
    protected Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    /**
     * Creates a horizontally-stretching slider preconfigured for the showcase grid.
     *
     * @param min   inclusive minimum
     * @param max   inclusive maximum
     * @param value initial value
     * @return the configured slider
     */
    protected Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setMaxWidth(Double.MAX_VALUE);
        return slider;
    }

    /**
     * Creates a right-aligned monospace value label bound to a slider via
     * {@link Bindings#format}.
     *
     * @param slider the slider whose value is rendered
     * @param format a {@code printf}-style format string (e.g. {@code "%.0f px"})
     * @return the configured value label
     */
    protected Label createValueLabel(Slider slider, String format) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.textProperty().bind(Bindings.format(format, slider.valueProperty()));
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    // ==================== Section descriptor ====================

    /**
     * Immutable descriptor of one labelled section in the right pane.
     */
    protected static final class Section {

        private final String title;
        private final Node content;

        private Section(String title, Node content) {
            this.title = title;
            this.content = content;
        }
    }
}
