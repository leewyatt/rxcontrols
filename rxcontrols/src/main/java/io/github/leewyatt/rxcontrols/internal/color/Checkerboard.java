package io.github.leewyatt.rxcontrols.internal.color;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;

/**
 * The checkerboard drawn behind translucent colors, so their opacity shows. A
 * light gray / white board of 4-pixel squares, the convention of image
 * editors; it is opaque so the same color reads the same on light and dark
 * themes. The tile image is twice the size of its 8-pixel repeat so the
 * squares stay crisp on a 2x screen.
 */
public final class Checkerboard {

    private static final double CELL = 4.0;
    private static final int SCALE = 2;
    private static final Color LIGHT = Color.WHITE;
    private static final Color DARK = Color.rgb(0xd9, 0xd9, 0xd9);

    private static ImagePattern pattern;

    private Checkerboard() {
    }

    /**
     * Returns the shared checkerboard pattern, created on first use.
     *
     * @return the pattern, anchored at the origin of the filled area
     */
    public static ImagePattern pattern() {
        if (pattern == null) {
            int cell = (int) CELL * SCALE;
            int size = cell * 2;
            WritableImage image = new WritableImage(size, size);
            PixelWriter writer = image.getPixelWriter();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean dark = (x / cell + y / cell) % 2 == 1;
                    writer.setColor(x, y, dark ? DARK : LIGHT);
                }
            }
            pattern = new ImagePattern(image, 0.0, 0.0, CELL * 2, CELL * 2, false);
        }
        return pattern;
    }
}
