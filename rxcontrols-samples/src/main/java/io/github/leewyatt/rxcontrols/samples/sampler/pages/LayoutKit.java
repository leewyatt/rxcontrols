package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Icons, text helpers, choice pickers, the resizable width frame and the stylesheet
 * shared by the layout pages. Each icon is a shape-backed region whose shape comes from
 * its style class in the stylesheet.
 */
final class LayoutKit {

    static final String STYLESHEET =
            LayoutKit.class.getResource("rx-layout-samples.css").toExternalForm();

    /**
     * Breakpoints scaled down so every tier can be reached inside a sample card:
     * SM from 360 px, MD from 500 px and LG from 720 px, on 24 columns.
     */
    static final RXBreakpointProfile COMPACT_PROFILE = RXBreakpointProfile.builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 360.0)
            .breakpoint(RXBreakpoint.MD, 500.0)
            .breakpoint(RXBreakpoint.LG, 720.0)
            .build();

    private static final double HANDLE_WIDTH = 16.0;
    private static final double SLIDER_START = 4000.0;

    private LayoutKit() {
    }

    static Region icon(String styleClass, String... more) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        icon.getStyleClass().addAll(more);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    /** A clickable icon: a transparent wrapper takes the clicks, the icon inside ignores them. */
    static StackPane iconButton(String styleClass, String... more) {
        Region icon = icon(styleClass, more);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("icon-button");
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return button;
    }

    static Label text(String value, String... styleClasses) {
        Label label = new Label(value);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    static Label wrapped(String value, String... styleClasses) {
        Label label = text(value, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static RXButton solid(String value, String... styleClasses) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("solid");
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    static RXButton plain(String value) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("plain");
        return button;
    }

    /** Loads one of the four sample landscape photos in the background. */
    static Image scenery(int number) {
        return new Image(LayoutKit.class.getResource("/scenery/" + number + ".png").toExternalForm(), true);
    }

    /** A small colored pill. */
    static Label chip(String value, String color) {
        Label chip = text(value, "chip", color);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** A segmented picker over enum-like values; the first value starts selected. */
    @SafeVarargs
    static <T> RXSegmentedControl<T> picker(Consumer<T> onChange, T... values) {
        RXSegmentedControl<T> picker = new RXSegmentedControl<>();
        for (T value : values) {
            picker.getItems().add(RXSegmentedItem.of(value, label(value)));
        }
        picker.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                onChange.accept(value);
            }
        });
        picker.setValue(values[0]);
        return picker;
    }

    private static String label(Object value) {
        String name = String.valueOf(value);
        if (!(value instanceof Enum<?>)) {
            return name;
        }
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }

    /** A label that shows the current breakpoint of a responsive container. */
    static Label breakpointBadge(ObservableValue<RXBreakpoint> breakpoint) {
        Label badge = text("", "breakpoint-badge");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        breakpoint.addListener((obs, old, value) -> badge.setText(badgeText(value)));
        badge.setText(badgeText(breakpoint.getValue()));
        return badge;
    }

    private static String badgeText(RXBreakpoint value) {
        return value == null ? "" : value.name();
    }

    /**
     * Puts content in a frame whose width follows a slider or a drag on the handle at
     * its right edge, starting as wide as the card allows.
     *
     * @param content  the layout to resize
     * @param minWidth the narrowest width the slider allows
     * @param extras   nodes shown after the width readout, such as a breakpoint badge
     * @return the frame with its controls
     */
    static VBox widthFrame(Region content, double minWidth, Node... extras) {
        Slider slider = new Slider(minWidth, SLIDER_START, SLIDER_START);
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);

        Label size = text("", "frame-size");
        size.setMinWidth(Region.USE_PREF_SIZE);
        content.widthProperty().addListener((obs, old, width) ->
                size.setText(Math.round(width.doubleValue()) + " px"));

        HBox bar = new HBox(10.0, icon("width-icon"), text("Width", "frame-caption"), slider, size);
        bar.getChildren().addAll(extras);
        bar.setAlignment(Pos.CENTER_LEFT);

        StackPane frame = new StackPane(content);
        frame.getStyleClass().add("width-frame");
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        frame.prefWidthProperty().bind(slider.valueProperty());
        frame.setMaxWidth(Region.USE_PREF_SIZE);

        Region grip = icon("grip-icon");
        grip.setMouseTransparent(true);
        StackPane handle = new StackPane(grip);
        handle.getStyleClass().add("frame-handle");
        handle.setPrefWidth(HANDLE_WIDTH);
        handle.setMinWidth(HANDLE_WIDTH);
        handle.setCursor(Cursor.H_RESIZE);
        double[] drag = new double[2];
        handle.setOnMousePressed(event -> {
            drag[0] = event.getScreenX();
            drag[1] = slider.getValue();
        });
        handle.setOnMouseDragged(event -> slider.setValue(drag[1] + event.getScreenX() - drag[0]));

        HBox row = new HBox(frame, handle);
        VBox box = new VBox(12.0, bar, row);
        box.setPrefWidth(minWidth);
        box.widthProperty().addListener((obs, old, width) -> {
            double max = Math.max(minWidth + 1.0, width.doubleValue() - HANDLE_WIDTH);
            boolean atMax = slider.getValue() >= slider.getMax() - 0.5;
            slider.setMax(max);
            if (atMax) {
                slider.setValue(max);
            }
        });
        return box;
    }

    /** Faint stripes, one per grid column, that show where the columns of a grid fall. */
    static HBox columnGuide(ObservableValue<Number> gutter, int columns) {
        HBox guide = new HBox();
        guide.getStyleClass().add("column-guide");
        guide.spacingProperty().bind(gutter);
        for (int i = 0; i < columns; i++) {
            Region stripe = new Region();
            stripe.getStyleClass().add("stripe");
            stripe.setMinWidth(0.0);
            stripe.setPrefWidth(0.0);
            stripe.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(stripe, Priority.ALWAYS);
            guide.getChildren().add(stripe);
        }
        guide.setMouseTransparent(true);
        return guide;
    }
}
