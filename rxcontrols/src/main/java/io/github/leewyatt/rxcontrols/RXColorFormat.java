package io.github.leewyatt.rxcontrols;

/**
 * How {@link RXColorView} and {@link RXColorPicker} show a color as text: in
 * the view's input fields and in the picker's trigger text. The format only
 * changes the display; the value is always a {@code Color}.
 */
public enum RXColorFormat {

    /** Hex digits, such as {@code #1677FF}; the opacity has its own field. */
    HEX,

    /** Red, green and blue from 0 to 255. */
    RGB,

    /** Hue in degrees, saturation and brightness in percent. */
    HSB
}
