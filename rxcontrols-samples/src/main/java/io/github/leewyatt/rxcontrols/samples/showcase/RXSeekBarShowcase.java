package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.samples.demo.RXSeekBarDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXSeekBar}.
 *
 * <p>Exposes the normalized primary and secondary progress values and the
 * looked-up colors used by the default stylesheet. For a minimal example see
 * {@link RXSeekBarDemo}.</p>
 */
public class RXSeekBarShowcase extends RXShowcaseApplication {

    private RXSeekBar seekBar;
    private Slider progressSlider;
    private ColorPicker trackPicker;
    private ColorPicker secondaryPicker;
    private ColorPicker barPicker;
    private ColorPicker thumbPicker;
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
        seekBar = new RXSeekBar(0.72);
        seekBar.setSecondaryProgress(0.36);
        seekBar.setPrefWidth(460.0);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "seeking: " + seekBar.isSeeking(),
                seekBar.seekingProperty()));

        VBox box = new VBox(18.0, new StackPane(seekBar), stateLabel);
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

        return createGrid(
                row("Progress", progressSlider, progressValue),
                row("Secondary", secondarySlider, secondaryValue));
    }

    private Node buildColorGrid() {
        trackPicker = createColorPicker(Color.web("#eaeaea"));
        secondaryPicker = createColorPicker(Color.web("#dadada"));
        barPicker = createColorPicker(Color.web("#1ecc94"));
        thumbPicker = createColorPicker(Color.web("#1fdba4"));
        updateSeekBarStyle();

        return createGrid(
                row("Track", trackPicker),
                row("Secondary", secondaryPicker),
                row("Bar", barPicker),
                row("Thumb", thumbPicker));
    }

    private ColorPicker createColorPicker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setOnAction(e -> updateSeekBarStyle());
        return picker;
    }

    private void updateSeekBarStyle() {
        if (seekBar == null || trackPicker == null) {
            return;
        }
        seekBar.setStyle(String.format(Locale.ROOT,
                "-rx-track-fill: %s; -rx-secondary-fill: %s; -rx-bar-fill: %s; -rx-thumb-fill: %s;",
                toCss(trackPicker.getValue()),
                toCss(secondaryPicker.getValue()),
                toCss(barPicker.getValue()),
                toCss(thumbPicker.getValue())));
    }

    private static String toCss(Color color) {
        int red = (int) Math.round(color.getRed() * 255.0);
        int green = (int) Math.round(color.getGreen() * 255.0);
        int blue = (int) Math.round(color.getBlue() * 255.0);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                red, green, blue, color.getOpacity());
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
