package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSnackbarRequest;
import io.github.leewyatt.rxcontrols.RXSnackbarSeverity;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Test-side sampler for the per-control colors that differ between light and dark hosts: builds the
 * controls in a fresh scene, lets the caller theme that scene, applies CSS and reads the
 * final styled values. Values are reported as 8-bit hex so a derive() result and an
 * equal literal compare equal. Must be called on the FX thread.
 */
final class ThemeColorSamples {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");
    private static final PseudoClass FILLING = PseudoClass.getPseudoClass("filling");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private ThemeColorSamples() {
    }

    /**
     * Samples every control color, theming the scene with {@code themer} before CSS runs.
     *
     * @param themer adds stylesheets / style classes / the bridge to the scene
     * @return sample key to 8-bit hex color ({@code "null"} when nothing resolved)
     */
    static Map<String, String> sample(Consumer<Scene> themer) {
        Map<String, String> samples = new LinkedHashMap<>();

        RXCascader<String> cascader = new RXCascader<>();
        cascader.setPromptText("Select");
        RXCascaderView<String> cascaderView = new RXCascaderView<>();
        RXCascaderItem<String> parent = new RXCascaderItem<>("parent");
        parent.getChildren().add(new RXCascaderItem<>("child"));
        cascaderView.getRootItems().add(parent);

        RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                new RXSegmentedItem<>("on", "On"), new RXSegmentedItem<>("off", "Off"));
        segmented.select("on");

        RXSeekBar seekBar = new RXSeekBar();
        RXSlider slider = new RXSlider();
        RXWaveProgressIndicator wave = new RXWaveProgressIndicator();
        RXAvatar avatar = new RXAvatar();

        RXSnackbarHost plainHost = new RXSnackbarHost();
        RXSnackbarHost severityHost = new RXSnackbarHost();
        plainHost.setAnimated(false);
        severityHost.setAnimated(false);

        RXSegmentedProgressBar progressBar = new RXSegmentedProgressBar();
        RXSegmentedStepIndicator steps = new RXSegmentedStepIndicator();
        RXSegmentedStepIndicator navigatorSteps = new RXSegmentedStepIndicator();
        StackPane navigator = new StackPane(navigatorSteps);
        navigator.getStyleClass().add("segmented-progress-navigator");
        RXDigit digit = new RXDigit();

        RXFillButton fillButton = new RXFillButton("fill");
        RXTileView<String> tileView = new RXTileView<>();
        RXListView<String> listView = new RXListView<>();
        RXMasonryView<String> masonryView = new RXMasonryView<>();

        VBox root = new VBox(cascader, cascaderView, segmented, seekBar, slider, wave, avatar,
                plainHost, severityHost, progressBar, steps, navigator, digit, fillButton, tileView, listView,
                masonryView);
        Scene scene = new Scene(root, 800, 1600);
        themer.accept(scene);
        refresh(root);

        plainHost.show(RXSnackbarRequest.builder("plain").action("Undo", () -> { }).showCloseIcon(true).build());
        severityHost.show(RXSnackbarRequest.builder("info").severity(RXSnackbarSeverity.INFO)
                .showCloseIcon(true).build());
        refresh(root);

        // The skin re-derives :filling from the fill progress on every layout pass, so
        // force the state and read it after a CSS pass alone.
        fillButton.pseudoClassStateChanged(FILLING, true);
        fillButton.pseudoClassStateChanged(ARMED, true);
        fillButton.applyCss();
        String fillingArmedText = hex(fillButton.getTextFill());

        Node cell = cascaderView.lookup(".list-cell");
        if (cell != null) {
            cell.pseudoClassStateChanged(HOVER, true);
            refresh(root);
        }

        samples.put("cascader.prompt", text(cascader.lookup(".display .label")));
        Node column = cascaderView.lookup(".column");
        samples.put("cascader.column.border", column instanceof Region ? rightBorder((Region) column) : "null");
        samples.put("cascader.cell.hover", cell instanceof Region ? background((Region) cell) : "null");

        for (Node segment : segmented.lookupAll(".segment")) {
            String key = segment.getPseudoClassStates().contains(SELECTED) ? "segmented.selected" : "segmented.unselected";
            samples.put(key, text(((Parent) segment).lookup(".label")));
        }
        samples.put("segmented.indicator", background(segmented.lookup(".indicator")));

        samples.put("seek.track", background(seekBar.lookup(".track")));
        samples.put("seek.secondary", background(seekBar.lookup(".secondary-bar")));
        samples.put("slider.track", background(slider.lookup(".track")));
        samples.put("wave.container", background(wave.lookup(".wave-container")));
        samples.put("avatar.icon-disc", background(avatar.lookup(".default-icon-wrapper")));

        samples.put("snackbar.bar", background(plainHost.lookup(".snackbar")));
        samples.put("snackbar.message", text(plainHost.lookup(".snackbar > .message")));
        samples.put("snackbar.action", text(plainHost.lookup(".snackbar > .action")));
        samples.put("snackbar.close-icon", background(plainHost.lookup(".close-button > .icon")));
        samples.put("snackbar.info.bar", background(severityHost.lookup(".snackbar")));
        samples.put("snackbar.info.message", text(severityHost.lookup(".snackbar > .message")));
        samples.put("snackbar.info.close-icon", background(severityHost.lookup(".close-button > .icon")));

        samples.put("progress-bar.track", background(progressBar.lookup(".track")));
        samples.put("step-indicator.track", background(steps.lookup(".track")));
        samples.put("carousel-navigator.track", background(navigatorSteps.lookup(".track")));
        samples.put("digit.lit", hex(digit.getLitFill()));
        samples.put("digit.unlit", hex(digit.getUnlitFill()));

        samples.put("fill-button.filling-armed.text", fillingArmedText);

        for (Map.Entry<String, Region> view : Map.<String, Region>of(
                "tile-view", tileView, "list-view", listView, "masonry-view", masonryView).entrySet()) {
            List<String> layers = layers(view.getValue().getBackground());
            samples.put(view.getKey() + ".background", String.join(" ", layers));
        }
        return samples;
    }

    /** 8-bit hex of a paint, {@code "null"} for none, the paint's text for non-colors. */
    static String hex(Paint paint) {
        if (paint == null) {
            return "null";
        }
        if (!(paint instanceof Color)) {
            return paint.toString();
        }
        Color color = (Color) paint;
        return String.format("#%02x%02x%02x%02x", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255), Math.round(color.getOpacity() * 255));
    }

    private static void refresh(Parent root) {
        root.applyCss();
        root.layout();
    }

    private static String text(Node node) {
        return node instanceof Labeled ? hex(((Labeled) node).getTextFill()) : "missing";
    }

    private static String background(Node node) {
        if (!(node instanceof Region)) {
            return "missing";
        }
        Background background = ((Region) node).getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return "null";
        }
        return hex(background.getFills().get(0).getFill());
    }

    private static List<String> layers(Background background) {
        if (background == null) {
            return List.of("null");
        }
        return background.getFills().stream().map(fill -> hex(fill.getFill())).toList();
    }

    private static String rightBorder(Region region) {
        Border border = region.getBorder();
        if (border == null || border.getStrokes().isEmpty()) {
            return "null";
        }
        return hex(border.getStrokes().get(0).getRightStroke());
    }
}
