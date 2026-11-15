package io.github.leewyatt.rxcontrols.samples.sampler;

/**
 * The groups of the sampler navigation, in display order, each with a glyph drawn as a
 * filled 24 x 24 SVG path (a hole is a sub-path wound the other way).
 */
enum SamplerCategory {

    START("Start",
            "M12 3L21 10V21H14V15H10V21H3V10Z"),
    BUTTONS("Buttons",
            "M5 3L19 12L13 13.5L16.5 20L14 21.3L10.6 14.8L6 19Z"),
    SELECTION("Selection",
            "M4 4H20V20H4Z M5.6 13.9L10 18.3L18.4 9.9L17 8.5L10 15.5L7 12.5Z"),
    INPUT("Text & number input",
            "M2 6H22V18H2Z M4 8V16H20V8Z M7 10H8.5V14H7Z"),
    DATE_TIME("Date & time",
            "M3 5H21V21H3Z M5 10V19H19V10Z M7 2H9V6H7Z M15 2H17V6H15Z M7 12H11V16H7Z"),
    DATA_VIEWS("Data views",
            "M3 5H6V8H3Z M8 5H21V8H8Z M3 10.5H6V13.5H3Z M8 10.5H21V13.5H8Z M3 16H6V19H3Z M8 16H21V19H8Z"),
    FEEDBACK("Feedback & status",
            "M2 11H7L9.5 5L14 19L16.5 11H22V13H17.8L14 22L9.5 9L8.3 13H2Z"),
    NAVIGATION("Navigation & panes",
            "M3 4H21V20H3Z M10 6V18H19V6Z"),
    MEDIA("Media & display",
            "M3 4H21V20H3Z M5 6V18H19V6Z M6 17L10 11L13 15L15 13L18 17Z M15.5 7A1.5 1.5 0 1 1 15.49 7Z"),
    LAYOUT("Layout",
            "M3 3H10V10H3Z M14 3H21V10H14Z M3 14H10V21H3Z M14 14H21V21H14Z"),
    MOTION("Motion & effects",
            "M12 2L14 10L22 12L14 14L12 22L10 14L2 12L10 10Z");

    private final String title;
    private final String glyph;

    SamplerCategory(String title, String glyph) {
        this.title = title;
        this.glyph = glyph;
    }

    String title() {
        return title;
    }

    String glyph() {
        return glyph;
    }
}
