package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXPopupMenu;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.internal.color.HsbaCursor;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The input row of a color view: a format button opening a small menu, the
 * fields of the current format (hex, or three numbers), and the opacity field.
 *
 * <p>A field applies its text while the user types once it is a complete value,
 * and finishes the edit on Enter or focus loss (accepting hex shorthand and
 * clamping numbers then, or restoring the current text). A field the user is
 * editing is not rewritten by refreshes until the edit finishes, so typing is
 * never overwritten by the value it produces. Fields are built once; switching
 * the format only shows another set.
 */
final class ColorInputRow extends HBox {

    // ==================== Host ====================

    /** What the row needs from the view's skin. */
    interface Host {

        /** Whether user input is accepted now. */
        boolean editable();

        /** The color the fields show: the value, or the cursor's color without one. */
        Color shownColor();

        /** The view's editing cursor, for the HSB fields. */
        HsbaCursor cursor();

        /** The format in effect, never {@code null}. */
        RXColorFormat format();

        /** Applies a format the user picked. */
        void pickFormat(RXColorFormat format);

        /** Whether the opacity can be edited. */
        boolean alphaEnabled();

        /** Moves the cursor with the change, then writes its color as a user edit. */
        void editCursor(Runnable change);

        /** Writes a target color as a user edit. */
        void editColor(Color color);

        /** Writes a new opacity, keeping the RGB, as a user edit. */
        void editOpacity(double opacity);

        /** A commit point: ends the edit session. */
        void finishEdit();
    }

    // ==================== Constants ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final int HEX_MAX_DIGITS = 8;
    private static final int NUMBER_MAX_DIGITS = 3;
    private static final int BYTE_MAX = 255;
    private static final int PERCENT_MAX = 100;
    private static final int STEP = 1;
    private static final int LARGE_STEP = 10;

    private enum Channel {
        HEX("Hex", HEX_MAX_DIGITS),
        RED("Red", BYTE_MAX),
        GREEN("Green", BYTE_MAX),
        BLUE("Blue", BYTE_MAX),
        HUE("Hue", (int) ColorMath.MAX_HUE),
        SATURATION("Saturation", PERCENT_MAX),
        BRIGHTNESS("Brightness", PERCENT_MAX),
        ALPHA("Alpha", PERCENT_MAX);

        private final String label;
        private final int max;

        Channel(String label, int max) {
            this.label = label;
            this.max = max;
        }
    }

    // ==================== Nodes ====================

    private final Host host;
    private final StackPane formatButton = new StackPane();
    private final Label formatText = new Label();
    private final HBox fieldBox = new HBox();
    private final Map<Channel, Field> fields = new EnumMap<>(Channel.class);
    private final RXPopupMenu formatMenu = new RXPopupMenu();
    private final Map<RXColorFormat, RXMenuItem> formatItems = new EnumMap<>(RXColorFormat.class);

    /** Set while the row writes field text, so the write is not taken for typing. */
    private boolean writing;

    // ==================== Construction ====================

    ColorInputRow(Host host) {
        this.host = host;
        getStyleClass().add("inputs");
        setAlignment(Pos.CENTER_LEFT);

        formatText.getStyleClass().add("text");
        formatText.setMouseTransparent(true);
        Region arrow = new Region();
        arrow.getStyleClass().add("arrow");
        RXSizing.lockToPrefSize(arrow);
        arrow.setMouseTransparent(true);
        HBox formatContent = new HBox(formatText, arrow);
        formatContent.getStyleClass().add("content");
        formatContent.setAlignment(Pos.CENTER_LEFT);
        formatContent.setMouseTransparent(true);
        formatButton.getStyleClass().add("format-button");
        formatButton.getChildren().add(formatContent);
        formatButton.setFocusTraversable(true);
        formatButton.setAccessibleRole(AccessibleRole.MENU_BUTTON);
        formatButton.setAccessibleText("Color format");

        formatMenu.getMenuList().getStyleClass().add("color-format-menu");
        ToggleGroup group = new ToggleGroup();
        for (RXColorFormat format : RXColorFormat.values()) {
            RXMenuItem item = new RXMenuItem(format.name());
            item.setToggleGroup(group);
            item.setOnAction(event -> host.pickFormat(format));
            formatItems.put(format, item);
            formatMenu.getItems().add(item);
        }

        fieldBox.getStyleClass().add("fields");
        fieldBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fieldBox, Priority.ALWAYS);
        for (Channel channel : Channel.values()) {
            Field field = new Field(channel);
            fields.put(channel, field);
            if (channel != Channel.ALPHA) {
                fieldBox.getChildren().add(field.input);
            }
        }
        getChildren().addAll(formatButton, fieldBox, fields.get(Channel.ALPHA).input);
    }

    /** Wires input; everything is released when the disposer runs. */
    void install(SkinDisposer disposer) {
        disposer.registerEventHandler(formatButton, MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                formatButton.requestFocus();
            }
        });
        disposer.registerEventHandler(formatButton, MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                event.consume();
                toggleMenu();
            }
        });
        disposer.registerEventHandler(formatButton, KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.DOWN) {
                event.consume();
                toggleMenu();
            }
        });
        for (Field field : fields.values()) {
            field.install(disposer);
        }
        disposer.registerDisposeTask(formatMenu::dispose);
    }

    // ==================== Refresh ====================

    /** Shows the fields of the current format and the current color in them. */
    void refresh() {
        RXColorFormat format = host.format();
        formatText.setText(format.name());
        formatItems.get(format).setSelected(true);
        boolean editable = host.editable();
        for (Field field : fields.values()) {
            boolean shown = field.channel == Channel.ALPHA ? host.alphaEnabled() : belongsTo(field.channel, format);
            field.input.setVisible(shown);
            field.input.setManaged(shown);
            field.input.setEditable(editable);
            if (!field.editing) {
                field.show();
            }
        }
    }

    /** Ends every field edit without applying it and shows the current color: the view was hidden. */
    void cancelEdits() {
        for (Field field : fields.values()) {
            field.editing = false;
            field.input.pseudoClassStateChanged(INVALID, false);
        }
        refresh();
    }

    private static boolean belongsTo(Channel channel, RXColorFormat format) {
        switch (format) {
            case RGB:
                return channel == Channel.RED || channel == Channel.GREEN || channel == Channel.BLUE;
            case HSB:
                return channel == Channel.HUE || channel == Channel.SATURATION || channel == Channel.BRIGHTNESS;
            case HEX:
            default:
                return channel == Channel.HEX;
        }
    }

    private void toggleMenu() {
        if (formatMenu.isShowing()) {
            formatMenu.hide();
        } else {
            formatMenu.show(formatButton);
        }
    }

    // ==================== Fields ====================

    private final class Field {

        private final Channel channel;
        private final RXTextField input = new RXTextField();
        /** True from the user's first keystroke until the edit is finished or cancelled. */
        private boolean editing;

        private Field(Channel channel) {
            this.channel = channel;
            input.setAccessibleText(channel.label);
            input.setPrefColumnCount(channel == Channel.HEX ? 5 : 2);
            HBox.setHgrow(input, channel == Channel.ALPHA ? Priority.NEVER : Priority.ALWAYS);
            if (channel == Channel.HEX) {
                Label hash = new Label("#");
                hash.getStyleClass().add("prefix");
                input.setLeading(hash);
            } else if (channel == Channel.ALPHA) {
                Label percent = new Label("%");
                percent.getStyleClass().add("suffix");
                input.setTrailing(percent);
            }
            input.getStyleClass().add(channel.name().toLowerCase(Locale.ROOT) + "-field");
            input.setTextFormatter(new TextFormatter<String>(this::filter));
        }

        // Pasted text keeps its usable characters instead of being rejected whole.
        private TextFormatter.Change filter(TextFormatter.Change change) {
            if (writing || !change.isContentChange()) {
                return change;
            }
            String added = change.getText();
            String kept = channel == Channel.HEX ? ColorMath.hexDigits(added) : digits(added);
            int limit = channel == Channel.HEX ? HEX_MAX_DIGITS : NUMBER_MAX_DIGITS;
            String others = change.getControlText().substring(0, change.getRangeStart())
                    + change.getControlText().substring(change.getRangeEnd());
            int room = Math.max(0, limit - others.length());
            if (kept.length() > room) {
                kept = kept.substring(0, room);
            }
            if (!kept.equals(added)) {
                change.setText(kept);
                int caret = change.getRangeStart() + kept.length();
                change.selectRange(caret, caret);
            }
            return change;
        }

        private void install(SkinDisposer disposer) {
            disposer.registerListener(input.textProperty(), this::onTyped);
            disposer.registerListener(input.focusedProperty(), () -> {
                if (input.isFocused()) {
                    input.setEditable(host.editable());
                } else {
                    finish();
                }
            });
            disposer.registerEventFilter(input, KeyEvent.KEY_PRESSED, this::onKey);
        }

        private void onTyped() {
            if (writing || !input.isEditable()) {
                return;
            }
            editing = true;
            boolean applied = apply(false);
            input.pseudoClassStateChanged(INVALID, !applied);
        }

        private void onKey(KeyEvent event) {
            KeyCode code = event.getCode();
            if (code == KeyCode.ENTER) {
                // Not consumed: an enclosing form's default button still sees Enter.
                finish();
            } else if (code == KeyCode.ESCAPE) {
                // The text field consumes Escape itself; a filter-only formatter does
                // not restore the text, so the row does.
                if (editing) {
                    editing = false;
                    input.pseudoClassStateChanged(INVALID, false);
                    show();
                }
            } else if ((code == KeyCode.UP || code == KeyCode.DOWN) && channel != Channel.HEX) {
                event.consume();
                if (host.editable()) {
                    int step = event.isShiftDown() ? LARGE_STEP : STEP;
                    int next = clamp(shownNumber() + (code == KeyCode.UP ? step : -step));
                    replaceText(Integer.toString(next));
                }
            }
        }

        /** Sets the text as if typed, so it applies live like typing does. */
        private void replaceText(String text) {
            input.setText(text);
            input.positionCaret(text.length());
        }

        private int shownNumber() {
            Integer typed = parse(input.getText());
            return typed != null ? typed : currentNumber();
        }

        /** Finishes the edit: applies the text leniently, then shows the current value. */
        private void finish() {
            if (editing) {
                editing = false;
                apply(true);
                input.pseudoClassStateChanged(INVALID, false);
                show();
            }
            host.finishEdit();
        }

        /** Applies the text; returns {@code false} if it is not a value. */
        private boolean apply(boolean finishing) {
            if (!host.editable()) {
                return false;
            }
            String text = input.getText();
            if (channel == Channel.HEX) {
                ColorMath.ParsedHex parsed = ColorMath.parseHex(ColorMath.hexDigits(text), finishing);
                if (parsed == null) {
                    return false;
                }
                Color color = parsed.color();
                if (!parsed.hasOpacity()) {
                    // Six digits keep the opacity the color has.
                    color = withOpacity(color, host.shownColor().getOpacity());
                }
                host.editColor(color);
                return true;
            }
            Integer number = parse(text);
            if (number == null || (number > channel.max && !finishing)) {
                return false;
            }
            applyNumber(clamp(number));
            return true;
        }

        private void applyNumber(int number) {
            Color shown = host.shownColor();
            HsbaCursor cursor = host.cursor();
            switch (channel) {
                case RED:
                    host.editColor(Color.rgb(number, ColorMath.toByte(shown.getGreen()),
                            ColorMath.toByte(shown.getBlue()), shown.getOpacity()));
                    break;
                case GREEN:
                    host.editColor(Color.rgb(ColorMath.toByte(shown.getRed()), number,
                            ColorMath.toByte(shown.getBlue()), shown.getOpacity()));
                    break;
                case BLUE:
                    host.editColor(Color.rgb(ColorMath.toByte(shown.getRed()), ColorMath.toByte(shown.getGreen()),
                            number, shown.getOpacity()));
                    break;
                case HUE:
                    host.editCursor(() -> cursor.changeHue(number));
                    break;
                case SATURATION:
                    host.editCursor(() -> cursor.changeSaturation(number / (double) PERCENT_MAX));
                    break;
                case BRIGHTNESS:
                    host.editCursor(() -> cursor.changeBrightness(number / (double) PERCENT_MAX));
                    break;
                case ALPHA:
                    host.editOpacity(number / (double) PERCENT_MAX);
                    break;
                default:
                    break;
            }
        }

        /** Shows the current value's text. */
        private void show() {
            writing = true;
            try {
                input.setText(channel == Channel.HEX ? ColorMath.toHex(host.shownColor(), false)
                        : Integer.toString(currentNumber()));
            } finally {
                writing = false;
            }
        }

        private int currentNumber() {
            Color shown = host.shownColor();
            HsbaCursor cursor = host.cursor();
            switch (channel) {
                case RED:
                    return ColorMath.toByte(shown.getRed());
                case GREEN:
                    return ColorMath.toByte(shown.getGreen());
                case BLUE:
                    return ColorMath.toByte(shown.getBlue());
                case HUE:
                    return ColorMath.toDegrees(cursor.getHue());
                case SATURATION:
                    return ColorMath.toPercent(cursor.getSaturation());
                case BRIGHTNESS:
                    return ColorMath.toPercent(cursor.getBrightness());
                case ALPHA:
                    return ColorMath.toPercent(shown.getOpacity());
                default:
                    return 0;
            }
        }

        private int clamp(int number) {
            return Math.max(0, Math.min(channel.max, number));
        }
    }

    // ==================== Helpers ====================

    private static String digits(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    private static Integer parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Color withOpacity(Color color, double opacity) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }
}
