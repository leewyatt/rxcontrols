package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sample photos, each loaded once and shared by every page. Many pages show the same
 * four landscapes; an image per use would decode the same file again, about a megabyte
 * each, and make every new node wait for its own background load.
 */
final class SampleImages {

    private static final int SCENERY_COUNT = 4;

    // Pages are built on the FX application thread, so a plain map is enough.
    private static final Map<String, Image> CACHE = new HashMap<>();

    private SampleImages() {
    }

    /**
     * Returns the classpath image at {@code path}, loading it in the background the
     * first time it is asked for.
     *
     * @param path the resource path, starting with a slash
     * @return the shared image
     */
    static Image image(String path) {
        return CACHE.computeIfAbsent(path,
                key -> new Image(SampleImages.class.getResource(key).toExternalForm(), true));
    }

    /**
     * Returns one of the sample landscape photos.
     *
     * @param number the photo number, from 1 to 4
     * @return the shared image
     */
    static Image scenery(int number) {
        return image("/images/scenery/" + number + ".png");
    }

    /**
     * Returns all the sample landscape photos, in order.
     *
     * @return the shared images
     */
    static List<Image> scenery() {
        List<Image> images = new ArrayList<>();
        for (int i = 1; i <= SCENERY_COUNT; i++) {
            images.add(scenery(i));
        }
        return images;
    }
}
