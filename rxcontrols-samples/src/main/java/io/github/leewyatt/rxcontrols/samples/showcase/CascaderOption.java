package io.github.leewyatt.rxcontrols.samples.showcase;

/**
 * Backend-style value shared by cascader showcase samples.
 *
 * @param id stable identifier
 * @param label human-facing text rendered by the item text factory
 */
record CascaderOption(String id, String label) {
}
