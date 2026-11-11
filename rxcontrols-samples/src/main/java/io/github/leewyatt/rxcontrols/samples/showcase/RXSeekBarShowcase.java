package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.samples.demo.RXSeekBarDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.SampleColors;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXSeekBar}.
 *
 * <p>Exposes the normalized primary and secondary progress values, a time preview
 * bubble for a sample track length (shown at the pointer while hovering), and the
 * looked-up colors used by the default stylesheet, including the hover layer. The colors follow the active
 * theme until "Custom colors" is checked. For a minimal example see
 * {@link RXSeekBarDemo}.</p>
 */
public class RXSeekBarShowcase extends RXShowcaseApplication {

    /** Sample track length for the time preview: 3:45. */
    private static final int SAMPLE_SECONDS = 225;

    private RXSeekBar seekBar;
    private Slider progressSlider;
    private boolean syncingProgress;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSeekBar";
    }

    @Override
    protected String subtitle() {
        return "Normalized dual-layer seek bar";
    }

    @Override
    protected String windowTitle() {
        return "RXSeekBar Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-seek-bar-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        seekBar = new RXSeekBar(0.36);
        seekBar.setSecondaryProgress(0.72);
        seekBar.setPrefWidth(460.0);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "seeking: " + seekBar.isSeeking(),
                seekBar.seekingProperty()));

        Label description = new Label("A playback progress bar: drag or click to jump to a position, "
                + "while the lighter layer shows how much is buffered. Typical in audio and video "
                + "players, podcast apps and any timeline the user can scrub.");
        description.getStyleClass().add("description");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setMaxWidth(460.0);

        VBox box = new VBox(18.0, new StackPane(seekBar), stateLabel, description);
        box.getStyleClass().add("seek-preview");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Progress", buildProgressGrid()),
                section("Colors", buildColorGrid()));
    }

    // ==================== Sections ====================

    private Node buildProgressGrid() {
        progressSlider = createSlider(0.0, 1.0, RXMath.clamp0To1(seekBar.getProgress()));
        progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!syncingProgress) {
                seekBar.setProgress(newV.doubleValue());
            }
        });
        seekBar.progressProperty().addListener((obs, oldV, newV) -> {
            if (!syncingProgress) {
                syncingProgress = true;
                progressSlider.setValue(RXMath.clamp0To1(newV.doubleValue()));
                syncingProgress = false;
            }
        });
        Label progressValue = createValueLabel(progressSlider, "%.2f");

        Slider secondarySlider = createSlider(0.0, 1.0, seekBar.getSecondaryProgress());
        seekBar.secondaryProgressProperty().bind(secondarySlider.valueProperty());
        Label secondaryValue = createValueLabel(secondarySlider, "%.2f");

        CheckBox timePreview = new CheckBox("Time preview (3:45 track)");
        timePreview.selectedProperty().addListener((obs, was, show) ->
                seekBar.setIndicatorTextFactory(show ? RXSeekBarShowcase::formatTime : null));
        timePreview.setSelected(true);

        return createGrid(
                row("Progress", progressSlider, progressValue),
                row("Secondary", secondarySlider, secondaryValue),
                row(timePreview));
    }

    private Node buildColorGrid() {
        CheckBox customColors = new CheckBox("Custom colors");
        BooleanBinding usingThemeColors = customColors.selectedProperty().not();
        ColorPicker trackPicker = createColorPicker(usingThemeColors);
        ColorPicker secondaryPicker = createColorPicker(usingThemeColors);
        ColorPicker hoverPicker = createColorPicker(usingThemeColors);
        ColorPicker barPicker = createColorPicker(usingThemeColors);
        ColorPicker thumbPicker = createColorPicker(usingThemeColors);

        // Inline styles beat every stylesheet, and clearing them gives the colors back
        // to the theme.
        Runnable applyColors = () -> seekBar.setStyle(customColors.isSelected()
                ? String.format(Locale.ROOT,
                        "-rx-track-fill: %s; -rx-secondary-fill: %s; -rx-hover-fill: %s; -rx-bar-fill: %s;"
                                + " -rx-thumb-fill: %s;",
                        SampleColors.toCss(trackPicker.getValue()),
                        SampleColors.toCss(secondaryPicker.getValue()),
                        SampleColors.toCss(hoverPicker.getValue()),
                        SampleColors.toCss(barPicker.getValue()),
                        SampleColors.toCss(thumbPicker.getValue()))
                : "");
        customColors.selectedProperty().addListener((obs, wasCustom, custom) -> {
            if (custom) {
                // Read every themed fill first: seeding a picker applies an inline style,
                // which resets the fills until CSS is applied again.
                Paint track = fillOf(".track");
                Paint secondary = fillOf(".secondary-bar");
                Paint hover = fillOf(".hover-bar");
                Paint bar = fillOf(".bar");
                Paint thumb = fillOf(".thumb");
                SampleColors.seed(trackPicker, track);
                SampleColors.seed(secondaryPicker, secondary);
                SampleColors.seed(hoverPicker, hover);
                SampleColors.seed(barPicker, bar);
                SampleColors.seed(thumbPicker, thumb);
            }
            applyColors.run();
        });
        for (ColorPicker picker : List.of(trackPicker, secondaryPicker, hoverPicker, barPicker, thumbPicker)) {
            picker.valueProperty().addListener((obs, oldColor, color) -> applyColors.run());
        }

        return createGrid(
                row(customColors),
                row("Track", trackPicker),
                row("Secondary", secondaryPicker),
                row("Hover", hoverPicker),
                row("Bar", barPicker),
                row("Thumb", thumbPicker));
    }

    private static String formatTime(double progress) {
        int seconds = (int) Math.round(progress * SAMPLE_SECONDS);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private ColorPicker createColorPicker(BooleanBinding disabled) {
        ColorPicker picker = new ColorPicker();
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.disableProperty().bind(disabled);
        return picker;
    }

    private Paint fillOf(String selector) {
        Region region = (Region) seekBar.lookup(selector);
        Background background = region == null ? null : region.getBackground();
        return background == null || background.getFills().isEmpty()
                ? null
                : background.getFills().get(0).getFill();
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
