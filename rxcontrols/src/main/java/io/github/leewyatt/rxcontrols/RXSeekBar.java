package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSeekBarSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * Normalized interactive seek bar with a primary progress layer, a secondary
 * progress layer, and a draggable thumb.
 *
 * <p>The {@link #progressProperty() progress} and
 * {@link #secondaryProgressProperty() secondaryProgress} values are interpreted
 * in the visual range {@code [0, 1]}. The raw property values are preserved;
 * the default skin clamps them only when rendering or reporting accessibility
 * values. The primary progress is intentionally independent of the secondary
 * progress, so users may seek past the currently loaded or buffered range.</p>
 *
 * <p>{@code progress} is an interactive value: the default skin writes it while
 * the user presses, drags, uses the keyboard, or invokes accessibility actions.
 * Do not bind this property in interactive scenarios. Instead, mirror external
 * state into it while {@link #isSeeking()} is {@code false}, and commit back to
 * the external source on the {@code seeking true -> false} transition.</p>
 */
public class RXSeekBar extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-seek-bar";

    // ==================== Constructors ====================

    /**
     * Creates a seek bar with zero progress.
     */
    public RXSeekBar() {
        this(0.0);
    }

    /**
     * Creates a seek bar with the given initial progress.
     *
     * @param progress initial primary progress, visually clamped to
     *                 {@code [0,1]} by the skin
     */
    public RXSeekBar(@NamedArg("progress") double progress) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.SLIDER);
        setProgress(progress);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSeekBarSkin(this);
    }

    // ==================== Progress ====================

    private final DoubleProperty progress = new DoublePropertyBase(0.0) {
        @Override
        protected void invalidated() {
            notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
        }

        @Override
        public Object getBean() {
            return RXSeekBar.this;
        }

        @Override
        public String getName() {
            return "progress";
        }
    };

    /**
     * Primary progress. The skin renders this value clamped to {@code [0,1]}.
     *
     * @return primary progress property
     */
    public final DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Returns the raw primary progress value.
     *
     * @return raw primary progress
     */
    public final double getProgress() {
        return progress.get();
    }

    /**
     * Sets the raw primary progress value.
     *
     * @param value primary progress value
     */
    public final void setProgress(double value) {
        progress.set(value);
    }

    // ==================== Secondary Progress ====================

    private final DoubleProperty secondaryProgress =
            new SimpleDoubleProperty(this, "secondaryProgress", 0.0);

    /**
     * Secondary progress. The skin renders this value clamped to {@code [0,1]}.
     *
     * @return secondary progress property
     */
    public final DoubleProperty secondaryProgressProperty() {
        return secondaryProgress;
    }

    /**
     * Returns the raw secondary progress value.
     *
     * @return raw secondary progress
     */
    public final double getSecondaryProgress() {
        return secondaryProgress.get();
    }

    /**
     * Sets the raw secondary progress value.
     *
     * @param value secondary progress value
     */
    public final void setSecondaryProgress(double value) {
        secondaryProgress.set(value);
    }

    // ==================== Seeking ====================

    private final BooleanProperty seeking =
            new SimpleBooleanProperty(this, "seeking", false);

    /**
     * Whether the user is actively changing the value. The default skin
     * maintains this property around pointer, keyboard, and accessibility
     * interactions. External writes are advanced usage and may trigger commit
     * listeners installed by application code.
     *
     * @return seeking property
     */
    public final BooleanProperty seekingProperty() {
        return seeking;
    }

    /**
     * Returns whether the user is actively changing the value.
     *
     * @return {@code true} while a seek interaction is active
     */
    public final boolean isSeeking() {
        return seeking.get();
    }

    /**
     * Sets whether a seek interaction is active.
     *
     * @param value {@code true} while seeking
     */
    public final void setSeeking(boolean value) {
        seeking.set(value);
    }
}
