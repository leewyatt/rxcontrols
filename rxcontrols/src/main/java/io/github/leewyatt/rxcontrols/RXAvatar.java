/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXAvatarSkin;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An avatar control that displays an image clipped to a shape.
 *
 * <p>Supports two built-in shape types via {@link #shapeTypeProperty()}:</p>
 * <ul>
 *   <li>{@link ShapeType#CIRCLE} (default) — circular avatar</li>
 *   <li>{@link ShapeType#SQUARE} — rectangular avatar with optional rounded
 *       corners controlled by {@link #arcWidthProperty()} and
 *       {@link #arcHeightProperty()}</li>
 * </ul>
 *
 * <p>The image is scaled using a cover-fit strategy: it fills the entire
 * clipping area while preserving aspect ratio, cropping any overflow.</p>
 *
 * <p>Display priority: {@link #imageProperty() image} &gt;
 * {@link #textProperty() text} &gt; built-in default icon.
 * When the image is unavailable, the text (typically user initials) is
 * shown; when neither is set, a generic person icon is displayed.</p>
 *
 * <pre>{@code
 * // Circular avatar with image
 * RXAvatar avatar = new RXAvatar(image);
 *
 * // Text fallback when image is unavailable
 * avatar.setText("LW");
 *
 * // Rounded-rectangle avatar
 * avatar.setShapeType(ShapeType.SQUARE);
 * avatar.setArcWidth(20);
 * avatar.setArcHeight(20);
 * }</pre>
 */
public class RXAvatar extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-avatar";
    private static final double DEFAULT_ARC = 10;

    // ==================== Pseudo-classes ====================

    /**
     * Active when the avatar is displaying the image.
     */
    private static final PseudoClass SHOWING_IMAGE_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing-image");

    /**
     * Active when the avatar is displaying the fallback text.
     */
    private static final PseudoClass SHOWING_TEXT_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing-text");

    /**
     * Active when neither image nor text is available, so the default icon is shown.
     */
    private static final PseudoClass SHOWING_DEFAULT_ICON_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing-default-icon");

    private Image currentImage;

    private final InvalidationListener imageProgressListener = obs -> updateDisplayState();
    private final WeakInvalidationListener weakImageProgressListener =
            new WeakInvalidationListener(imageProgressListener);

    // ==================== Constructors ====================

    /**
     * Creates a new avatar with no image.
     */
    public RXAvatar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(false);
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        imageProperty().addListener(obs -> onImageChanged());
        textProperty().addListener(obs -> updateDisplayState());
        updateDisplayState();
    }

    /**
     * Creates a new avatar with the given image URL.
     *
     * @param imageUrl the image URL
     */
    public RXAvatar(String imageUrl) {
        this(new Image(imageUrl, true));
    }

    /**
     * Creates a new avatar with the given image.
     *
     * @param image the image to display
     */
    public RXAvatar(Image image) {
        this();
        setImage(image);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAvatarSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Display State ====================

    private final ReadOnlyObjectWrapper<DisplayState> displayState =
            new ReadOnlyObjectWrapper<>(this, "displayState", DisplayState.EMPTY);

    /**
     * The current display state of the avatar, determined by the availability
     * of the {@link #imageProperty() image} and {@link #textProperty() text}.
     *
     * @return the read-only display state property
     */
    public final ReadOnlyObjectProperty<DisplayState> displayStateProperty() {
        return displayState.getReadOnlyProperty();
    }

    /**
     * Returns the current display state.
     *
     * @return the display state
     */
    public final DisplayState getDisplayState() {
        return displayState.get();
    }

    private boolean isImageReady() {
        Image img = getImage();
        return img != null && !img.isError() && img.getProgress() >= 1.0
                && img.getWidth() > 0 && img.getHeight() > 0;
    }

    private void onImageChanged() {
        if (currentImage != null) {
            currentImage.progressProperty().removeListener(weakImageProgressListener);
            currentImage.errorProperty().removeListener(weakImageProgressListener);
        }
        currentImage = getImage();
        if (currentImage != null) {
            currentImage.progressProperty().addListener(weakImageProgressListener);
            currentImage.errorProperty().addListener(weakImageProgressListener);
        }
        updateDisplayState();
    }

    private void updateDisplayState() {
        boolean imageReady = isImageReady();
        String txt = getText();
        boolean hasText = txt != null && !txt.isEmpty();

        DisplayState state;
        if (imageReady) {
            state = DisplayState.IMAGE;
        } else if (hasText) {
            state = DisplayState.TEXT;
        } else {
            state = DisplayState.EMPTY;
        }

        displayState.set(state);
        pseudoClassStateChanged(SHOWING_IMAGE_PSEUDO_CLASS, state == DisplayState.IMAGE);
        pseudoClassStateChanged(SHOWING_TEXT_PSEUDO_CLASS, state == DisplayState.TEXT);
        pseudoClassStateChanged(SHOWING_DEFAULT_ICON_PSEUDO_CLASS, state == DisplayState.EMPTY);
    }

    // ==================== Image ====================

    private final ObjectProperty<Image> image =
            new SimpleObjectProperty<>(this, "image");

    /**
     * The image to display in the avatar.
     *
     * @return the image property
     */
    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Returns the image displayed in the avatar.
     *
     * @return the image
     */
    public final Image getImage() {
        return image.get();
    }

    /**
     * Sets the image to display in the avatar.
     *
     * @param image the image
     */
    public final void setImage(Image image) {
        this.image.set(image);
    }

    // ==================== Text ====================

    private final StringProperty text =
            new SimpleStringProperty(this, "text");

    /**
     * The text to display when the image is unavailable (typically user
     * initials such as {@code "LW"}). Styled via the {@code .text-wrapper}
     * CSS selector inside the avatar.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the fallback text.
     *
     * @return the text
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the fallback text.
     *
     * @param text the text
     */
    public final void setText(String text) {
        this.text.set(text);
    }

    // ==================== Shape Type ====================

    private final StyleableObjectProperty<ShapeType> shapeType =
            new StyleableObjectProperty<>(ShapeType.CIRCLE) {
                @Override
                public CssMetaData<? extends Styleable, ShapeType> getCssMetaData() {
                    return StyleableProperties.SHAPE_TYPE;
                }

                @Override
                public Object getBean() {
                    return RXAvatar.this;
                }

                @Override
                public String getName() {
                    return "shapeType";
                }
            };

    /**
     * The shape used to clip the avatar image.
     *
     * @return the shape type property
     */
    public final StyleableObjectProperty<ShapeType> shapeTypeProperty() {
        return shapeType;
    }

    /**
     * Returns the shape type used to clip the avatar image.
     *
     * @return the shape type
     */
    public final ShapeType getShapeType() {
        return shapeType.get();
    }

    /**
     * Sets the shape type used to clip the avatar image.
     *
     * @param type the shape type
     */
    public final void setShapeType(ShapeType type) {
        shapeType.set(type);
    }

    // ==================== Arc Width ====================

    private final StyleableDoubleProperty arcWidth =
            new StyleableDoubleProperty(DEFAULT_ARC) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.ARC_WIDTH;
                }

                @Override
                public Object getBean() {
                    return RXAvatar.this;
                }

                @Override
                public String getName() {
                    return "arcWidth";
                }
            };

    /**
     * The horizontal arc radius for rounded corners when shape type is
     * {@link ShapeType#SQUARE}. Ignored for other shape types.
     *
     * @return the arc width property
     */
    public final DoubleProperty arcWidthProperty() {
        return arcWidth;
    }

    /**
     * Returns the horizontal arc radius for rounded corners.
     *
     * @return the arc width
     */
    public final double getArcWidth() {
        return arcWidth.get();
    }

    /**
     * Sets the horizontal arc radius for rounded corners.
     *
     * @param value the arc width
     */
    public final void setArcWidth(double value) {
        arcWidth.set(value);
    }

    // ==================== Arc Height ====================

    private final StyleableDoubleProperty arcHeight =
            new StyleableDoubleProperty(DEFAULT_ARC) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.ARC_HEIGHT;
                }

                @Override
                public Object getBean() {
                    return RXAvatar.this;
                }

                @Override
                public String getName() {
                    return "arcHeight";
                }
            };

    /**
     * The vertical arc radius for rounded corners when shape type is
     * {@link ShapeType#SQUARE}. Ignored for other shape types.
     *
     * @return the arc height property
     */
    public final DoubleProperty arcHeightProperty() {
        return arcHeight;
    }

    /**
     * Returns the vertical arc radius for rounded corners.
     *
     * @return the arc height
     */
    public final double getArcHeight() {
        return arcHeight.get();
    }

    /**
     * Sets the vertical arc radius for rounded corners.
     *
     * @param value the arc height
     */
    public final void setArcHeight(double value) {
        arcHeight.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXAvatar, ShapeType> SHAPE_TYPE =
                new CssMetaData<>("-rx-shape-type",
                        new EnumConverter<>(ShapeType.class), ShapeType.CIRCLE) {
                    @Override
                    public boolean isSettable(RXAvatar control) {
                        return !control.shapeType.isBound();
                    }

                    @Override
                    public StyleableProperty<ShapeType> getStyleableProperty(RXAvatar control) {
                        return control.shapeTypeProperty();
                    }
                };

        private static final CssMetaData<RXAvatar, Number> ARC_WIDTH =
                new CssMetaData<>("-rx-arc-width",
                        SizeConverter.getInstance(), DEFAULT_ARC) {
                    @Override
                    public boolean isSettable(RXAvatar control) {
                        return !control.arcWidth.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(RXAvatar control) {
                        return control.arcWidth;
                    }
                };

        private static final CssMetaData<RXAvatar, Number> ARC_HEIGHT =
                new CssMetaData<>("-rx-arc-height",
                        SizeConverter.getInstance(), DEFAULT_ARC) {
                    @Override
                    public boolean isSettable(RXAvatar control) {
                        return !control.arcHeight.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(RXAvatar control) {
                        return control.arcHeight;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(SHAPE_TYPE);
            styleables.add(ARC_WIDTH);
            styleables.add(ARC_HEIGHT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * The shape types available for clipping the avatar image.
     */
    public enum ShapeType {
        /**
         * Circular clip.
         */
        CIRCLE,
        /**
         * Rectangular clip with optional rounded corners.
         */
        SQUARE
    }

    /**
     * The visual display states of the avatar.
     */
    public enum DisplayState {
        /**
         * Displaying the image.
         */
        IMAGE,
        /**
         * Displaying the fallback text.
         */
        TEXT,
        /**
         * Neither image nor text is available; showing the default icon.
         */
        EMPTY
    }
}
