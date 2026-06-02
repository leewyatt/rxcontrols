package io.github.leewyatt.rxcontrols;

/**
 * Selection mode used by {@link RXCascaderPanel} and {@link RXCascader}.
 */
public enum RXCascaderSelectionMode {
    /**
     * A single leaf path can be selected.
     */
    SINGLE,

    /**
     * Multiple paths can be checked with cascading tri-state check boxes.
     */
    MULTIPLE
}
