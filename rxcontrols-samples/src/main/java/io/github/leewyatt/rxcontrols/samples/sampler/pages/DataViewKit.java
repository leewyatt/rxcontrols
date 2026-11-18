package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Icons, text helpers, avatars, sample images and the stylesheet shared by the data
 * view pages. Each icon is a shape-backed region whose shape comes from its style
 * class in the stylesheet.
 */
final class DataViewKit {

    static final String STYLESHEET =
            DataViewKit.class.getResource("rx-data-view-samples.css").toExternalForm();

    static final List<String> PALETTE = List.of(
            "rose", "orange", "amber", "lime", "teal", "sky", "indigo", "violet", "pink");

    private static final List<String> PALETTE_HEX = List.of(
            "#f43f5e", "#f97316", "#f59e0b", "#65a30d", "#14b8a6", "#0ea5e9", "#6366f1", "#8b5cf6", "#ec4899");

    private static final int SCENERY_COUNT = 4;

    private DataViewKit() {
    }

    static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static RXButton solid(String value, String... styleClasses) {
        RXButton button = new RXButton(value);
        button.getStyleClass().add("solid");
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    static RXButton plain(String value) {
        return new RXButton(value);
    }

    /**
     * A round label with initials; its color comes from a palette style class set
     * with {@link #paint(Label, String)}.
     */
    static Label avatar(double size) {
        Label avatar = new Label();
        avatar.getStyleClass().add("avatar");
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    static void paint(Label avatar, String paletteClass) {
        avatar.getStyleClass().removeAll(PALETTE);
        avatar.getStyleClass().add(paletteClass);
    }

    static String colorOf(String key) {
        return PALETTE.get(Math.floorMod(key.hashCode(), PALETTE.size()));
    }

    static Color colorValue(String paletteClass) {
        int index = PALETTE.indexOf(paletteClass);
        return Color.web(PALETTE_HEX.get(Math.max(index, 0)));
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    /**
     * Loads the sample landscape photos in the background.
     */
    static List<Image> scenery() {
        List<Image> images = new ArrayList<>();
        for (int i = 1; i <= SCENERY_COUNT; i++) {
            URL url = DataViewKit.class.getResource("/scenery/" + i + ".png");
            images.add(new Image(url.toExternalForm(), true));
        }
        return images;
    }

    /**
     * A converter for display-only text; the views never parse text back.
     */
    static <T> StringConverter<T> converter(Function<T, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toText.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    static <R extends Region> R sized(R region, double width, double height) {
        region.setPrefSize(width, height);
        region.setMinHeight(height);
        region.setMaxWidth(Region.USE_PREF_SIZE);
        return region;
    }
}
