package io.github.leewyatt.rxcontrols.enums;

/**
 * Defines how an image is fitted into an allocated image area.
 */
public enum ImageFit {
    /**
     * Scale the image to fill the area while preserving aspect ratio and crop
     * overflow from the image source.
     */
    COVER,
    /**
     * Scale the whole image to fit inside the area while preserving aspect ratio.
     */
    CONTAIN,
    /**
     * Stretch the image to fill the area without preserving aspect ratio.
     */
    STRETCH
}
