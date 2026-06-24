package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogActionsLayout;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.internal.RXDialogLayer;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDialogSkin;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-scene, modal-by-default overlay dialog. {@code RXDialog} is not added to
 * the scene graph by the application; it is shown through {@link #show(Node)} /
 * {@link #showIn(Pane)}, which attach it — via a per-scene
 * {@link RXDialogLayer overlay layer} — over the target's scene as a centered card
 * on a dimmed scrim. Showing state is the read-only source of truth
 * {@link #showingProperty() showing}, driven by {@code show*} / {@code close*}.
 *
 * <p>The card hosts the {@link #contentProperty() content} node (often an
 * {@link RXDialogContent}, or any bare {@code Node}) above an action bar the skin
 * builds from {@link #getButtonTypes() buttonTypes}. Clicking an action button,
 * pressing ESC, clicking the scrim, or clicking a content-provided close (X) button
 * (e.g. {@link RXDialogContent#showCloseProperty() RXDialogContent's}) all flow
 * through one vetoable gate: a {@code CLOSE_REQUEST}
 * {@link RXDialogEvent} fires first (consume it to keep the dialog open); if not
 * vetoed, the {@link #resultProperty() result} is computed from the candidate
 * {@link ButtonType} via {@link #resultConverterProperty() resultConverter}, a
 * {@code HIDING} event fires, the close transition plays, and only after
 * {@code HIDDEN} is the result delivered to {@link #onResultProperty() onResult}.</p>
 *
 * <pre>{@code
 * RXDialog<ButtonType> dialog = new RXDialog<>();
 * dialog.setContent(new RXDialogContent("Delete file?", "This cannot be undone."));
 * dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
 * dialog.setOnResult(result -> { if (result == ButtonType.OK) delete(); });
 * dialog.show(anyNodeInTheScene);
 * }</pre>
 *
 * <p>The result is delivered asynchronously through {@code onResult} rather than a
 * blocking {@code showAndWait()}: an in-scene overlay animates, and a blocking
 * nested event loop cannot run during animation. A future {@code RXStageDialog}
 * may offer the blocking, cross-window variant.</p>
 *
 * @param <R> the result type produced by {@link #resultConverterProperty() resultConverter}
 */
public class RXDialog<R> extends Control {

    // ==================== Constants ====================

    /**
     * Default transition style (centered scale + fade).
     */
    public static final RXDialogTransition DEFAULT_TRANSITION = RXDialogTransition.CENTER;

    /**
     * Default action-button layout (a CSS-styled {@code RXBox} row).
     */
    public static final RXDialogActionsLayout DEFAULT_ACTIONS_LAYOUT = RXDialogActionsLayout.BOX;

    /**
     * Default show/hide animation enabled state.
     */
    private static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default show/hide animation duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(250.0);

    /**
     * Default show/hide animation interpolator, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final boolean DEFAULT_MODAL = true;
    private static final boolean DEFAULT_CLOSE_ON_ESC = true;
    private static final boolean DEFAULT_CLOSE_ON_SCRIM_CLICK = true;
    private static final boolean DEFAULT_ENABLE_RESIZABLE = false;
    private static final boolean DEFAULT_ENABLE_DRAGGABLE = false;

    /**
     * Default card minimum width (the lower bound of a width resize).
     */
    public static final double DEFAULT_CARD_MIN_WIDTH = 280.0;

    /**
     * Default card minimum height (the lower bound of a height resize).
     */
    public static final double DEFAULT_CARD_MIN_HEIGHT = 120.0;
    private static final boolean DEFAULT_SHOWING = false;

    private static final String DEFAULT_STYLE_CLASS = "rx-dialog";

    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    // ==================== Close gate context ====================

    // Captured when a non-vetoed close starts (requestClose) and replayed once the
    // close transition completes (hideCompleted), so HIDDEN carries the same payload
    // and onResult receives the result computed at close time.
    private ButtonType pendingButtonType;
    private CloseReason pendingCloseReason;
    private R pendingResult;
    // True between a non-vetoed requestClose and the matching hideCompleted, so a
    // stray external hideCompleted() cannot deliver a result / detach out of band.
    private boolean hideInProgress;
    // True while a requestClose pass is on the stack, so a re-entrant close()/requestClose()
    // from a CLOSE_REQUEST / HIDING handler or a result listener is a no-op (no recursion,
    // no second close sequence).
    private boolean closeGateActive;

    // ==================== Constructors ====================

    /**
     * Creates an empty dialog with default settings (centered, modal, animated).
     */
    public RXDialog() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DIALOG);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDialogSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Content ====================

    // The RXDialogAware content currently holding our back-reference, so it can be
    // cleared when the content changes.
    private RXDialogAware awareContent;

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content") {
        @Override
        protected void invalidated() {
            // Inject this dialog into RXDialogAware content (clearing the previous one) so the
            // content — e.g. an RXDialogContent header close button — can drive the dialog.
            // The tracked field is updated before each updateDialog notification so a re-entrant
            // setContent (from a dialogProperty listener) always observes a consistent state.
            RXDialogAware previous = awareContent;
            awareContent = null;
            if (previous != null) {
                previous.updateDialog(null);
            }
            if (get() instanceof RXDialogAware aware) {
                // A node lives in one place: if it is another dialog's tracked content, release
                // it from that dialog so the prior owner never later nulls this injection.
                RXDialog<?> prior = aware.getDialog();
                if (prior != null && prior != RXDialog.this) {
                    prior.releaseAwareContent(aware);
                }
                awareContent = aware;
                aware.updateDialog(RXDialog.this);
            }
        }
    };

    // Invoked by another dialog that is taking over this aware node as its content, so this
    // dialog stops tracking it and never later clears the new owner's injection.
    private void releaseAwareContent(RXDialogAware aware) {
        if (awareContent == aware) {
            awareContent = null;
        }
    }

    /**
     * The card's main content node — an {@link RXDialogContent} or any bare
     * {@code Node}. The skin renders it above the action bar built from
     * {@link #getButtonTypes() buttonTypes}. May be {@code null} for an empty card.
     *
     * <p>If the content implements {@link RXDialogAware} (as {@link RXDialogContent}
     * does), this dialog injects itself into it while it is the content, so the
     * content can drive the dialog.</p>
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Result ====================

    private final ObjectProperty<R> result = new SimpleObjectProperty<>(this, "result");

    /**
     * The dialog's result. Set by the close gate from the candidate
     * {@link ButtonType} via {@link #resultConverterProperty() resultConverter}, or
     * pre-set by the application. Unlike the native {@code Dialog}, setting it does
     * <strong>not</strong> close the dialog — closing is always explicit (an action
     * button, ESC, scrim, close button, or {@code close*}).
     *
     * @return the result property
     */
    public final ObjectProperty<R> resultProperty() {
        return result;
    }

    /**
     * Returns the current result.
     *
     * @return the result, or {@code null}
     */
    public final R getResult() {
        return result.get();
    }

    /**
     * Sets the result without closing the dialog.
     *
     * @param value the result, or {@code null}
     */
    public final void setResult(R value) {
        result.set(value);
    }

    // ==================== Result Converter ====================

    private final ObjectProperty<Callback<ButtonType, R>> resultConverter =
            new SimpleObjectProperty<>(this, "resultConverter");

    /**
     * Maps the candidate {@link ButtonType} of a close to the dialog's result. When
     * {@code null}, the candidate button type is used as the result directly (an
     * unchecked cast, valid when {@code R} is {@code ButtonType} / {@code Object}).
     * The candidate may be {@code null} (ESC / scrim / programmatic close with no
     * cancel button), in which case the converter receives {@code null}.
     *
     * @return the result converter property
     */
    public final ObjectProperty<Callback<ButtonType, R>> resultConverterProperty() {
        return resultConverter;
    }

    /**
     * Returns the result converter.
     *
     * @return the result converter, or {@code null}
     */
    public final Callback<ButtonType, R> getResultConverter() {
        return resultConverter.get();
    }

    /**
     * Sets the result converter.
     *
     * @param value the result converter, or {@code null}
     */
    public final void setResultConverter(Callback<ButtonType, R> value) {
        resultConverter.set(value);
    }

    // ==================== On Result ====================

    private final ObjectProperty<Consumer<R>> onResult = new SimpleObjectProperty<>(this, "onResult");

    /**
     * Called once with the result after the dialog has fully hidden ({@code HIDDEN}
     * has fired). Never called for a vetoed close. This is the asynchronous
     * replacement for a blocking {@code showAndWait()}.
     *
     * @return the on-result property
     */
    public final ObjectProperty<Consumer<R>> onResultProperty() {
        return onResult;
    }

    /**
     * Returns the on-result callback.
     *
     * @return the on-result callback, or {@code null}
     */
    public final Consumer<R> getOnResult() {
        return onResult.get();
    }

    /**
     * Sets the on-result callback.
     *
     * @param value the on-result callback, or {@code null}
     */
    public final void setOnResult(Consumer<R> value) {
        onResult.set(value);
    }

    // ==================== Button Types ====================

    private final ObservableList<ButtonType> buttonTypes = FXCollections.observableArrayList();

    /**
     * The button types the skin renders as an action bar at the bottom of the card,
     * laid out per {@link #actionsLayoutProperty() actionsLayout}. Empty means no
     * action bar; put a custom action row inside {@link #contentProperty() content}
     * instead.
     *
     * @return the live, modifiable list of button types
     */
    public final ObservableList<ButtonType> getButtonTypes() {
        return buttonTypes;
    }

    // ==================== Actions Layout ====================

    private final ObjectProperty<RXDialogActionsLayout> actionsLayout =
            new StyleableObjectProperty<>(DEFAULT_ACTIONS_LAYOUT) {
                @Override
                public CssMetaData<? extends Styleable, RXDialogActionsLayout> getCssMetaData() {
                    return StyleableProperties.ACTIONS_LAYOUT;
                }

                @Override
                public Object getBean() {
                    return RXDialog.this;
                }

                @Override
                public String getName() {
                    return "actionsLayout";
                }
            };

    /**
     * How the action buttons built from {@link #getButtonTypes() buttonTypes} are laid
     * out. {@link RXDialogActionsLayout#BOX} (default) is a CSS-styled {@code RXBox} row
     * in {@code buttonTypes} order; {@link RXDialogActionsLayout#PLATFORM} uses the native
     * {@code ButtonBar} (OS order, trailing-aligned). A {@code null} value resolves to
     * {@link #DEFAULT_ACTIONS_LAYOUT} at the use site.
     *
     * @return the actions-layout property
     */
    public final ObjectProperty<RXDialogActionsLayout> actionsLayoutProperty() {
        return actionsLayout;
    }

    /**
     * Returns the action-button layout.
     *
     * @return the action-button layout
     */
    public final RXDialogActionsLayout getActionsLayout() {
        return actionsLayout.get();
    }

    /**
     * Sets the action-button layout.
     *
     * @param value the action-button layout, or {@code null} to fall back to the default
     */
    public final void setActionsLayout(RXDialogActionsLayout value) {
        actionsLayout.set(value);
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing =
            new ReadOnlyBooleanWrapper(this, "showing", DEFAULT_SHOWING) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, get());
                }
            };

    /**
     * Whether the dialog is shown ({@code true}) or hidden ({@code false}). The
     * read-only, committed source of truth, driven through {@code show*} /
     * {@code close*}, not written directly. The skin observes it to run the
     * transition and the {@code :showing} pseudo-class tracks it.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the dialog is shown.
     *
     * @return {@code true} if the dialog is shown
     */
    public final boolean isShowing() {
        return showing.get();
    }

    // ==================== Owner ====================

    private final ObjectProperty<Node> owner = new SimpleObjectProperty<>(this, "owner");

    /**
     * The node whose scene the dialog attaches to when shown. Set automatically by
     * {@link #show(Node)}; may also be set ahead of a no-arg {@link #show()}. Not used
     * when the dialog is shown with an explicit container via {@link #showIn(Pane)}.
     *
     * @return the owner property
     */
    public final ObjectProperty<Node> ownerProperty() {
        return owner;
    }

    /**
     * Returns the owner node.
     *
     * @return the owner node, or {@code null}
     */
    public final Node getOwner() {
        return owner.get();
    }

    /**
     * Sets the owner node.
     *
     * @param value the owner node, or {@code null}
     */
    public final void setOwner(Node value) {
        owner.set(value);
    }

    // ==================== Modal ====================

    private final BooleanProperty modal = new SimpleBooleanProperty(this, "modal", DEFAULT_MODAL);

    /**
     * Whether the dialog is modal. A modal dialog shows the dimmed, input-blocking
     * scrim, traps focus, and steals focus on show / restores it on hide. A
     * non-modal dialog shows no scrim and leaves the rest of the scene interactive.
     *
     * @return the modal property
     */
    public final BooleanProperty modalProperty() {
        return modal;
    }

    /**
     * Returns whether the dialog is modal.
     *
     * @return whether the dialog is modal
     */
    public final boolean isModal() {
        return modal.get();
    }

    /**
     * Sets whether the dialog is modal.
     *
     * @param value whether the dialog is modal
     */
    public final void setModal(boolean value) {
        modal.set(value);
    }

    // ==================== Close On Esc ====================

    private final BooleanProperty closeOnEsc =
            new SimpleBooleanProperty(this, "closeOnEsc", DEFAULT_CLOSE_ON_ESC);

    /**
     * Whether pressing ESC while the dialog is shown requests a close.
     *
     * @return the close-on-esc property
     */
    public final BooleanProperty closeOnEscProperty() {
        return closeOnEsc;
    }

    /**
     * Returns whether ESC requests a close.
     *
     * @return whether ESC closes the dialog
     */
    public final boolean isCloseOnEsc() {
        return closeOnEsc.get();
    }

    /**
     * Sets whether ESC requests a close.
     *
     * @param value whether ESC closes the dialog
     */
    public final void setCloseOnEsc(boolean value) {
        closeOnEsc.set(value);
    }

    // ==================== Close On Scrim Click ====================

    private final BooleanProperty closeOnScrimClick =
            new SimpleBooleanProperty(this, "closeOnScrimClick", DEFAULT_CLOSE_ON_SCRIM_CLICK);

    /**
     * Whether clicking the scrim (the dimmed area outside the card) requests a
     * close. Only effective while modal (a non-modal dialog has no scrim).
     *
     * @return the close-on-scrim-click property
     */
    public final BooleanProperty closeOnScrimClickProperty() {
        return closeOnScrimClick;
    }

    /**
     * Returns whether a scrim click requests a close.
     *
     * @return whether a scrim click closes the dialog
     */
    public final boolean isCloseOnScrimClick() {
        return closeOnScrimClick.get();
    }

    /**
     * Sets whether a scrim click requests a close.
     *
     * @param value whether a scrim click closes the dialog
     */
    public final void setCloseOnScrimClick(boolean value) {
        closeOnScrimClick.set(value);
    }

    // ==================== Enable Resizable ====================

    // Named enableResizable, not resizable: Node.isResizable() is an existing layout-contract
    // method (Region returns true so the RXDialogLayer can stretch this control to fill the
    // scene). Overriding it with a user-gesture flag would collapse the scrim, so the
    // end-user-resize capability gets its own name.
    private final BooleanProperty enableResizable =
            new SimpleBooleanProperty(this, "enableResizable", DEFAULT_ENABLE_RESIZABLE);

    /**
     * Whether the user can resize the card by dragging its edges and corners
     * ({@code false} by default). The skin shows the eight resize cursors over the
     * border zones and grows / shrinks the card within its
     * {@link #cardMinWidthProperty() card size bounds} ({@code cardMinWidth} /
     * {@code cardMaxWidth} / {@code cardMinHeight} / {@code cardMaxHeight}) and the
     * available scene; the elevation shadow is never clipped. Only effective while the
     * dialog is shown and not animating. Turning it
     * off cancels an in-progress resize and stops new ones, but keeps the card at its
     * current size; the size resets to automatic only when the dialog has fully hidden.
     *
     * <p>Named {@code enableResizable} (not {@code resizable}) so it does not override
     * {@link javafx.scene.Node#isResizable()}, the unrelated layout-contract method.</p>
     *
     * @return the user-resizable property
     */
    public final BooleanProperty enableResizableProperty() {
        return enableResizable;
    }

    /**
     * Returns whether the card is user-resizable.
     *
     * @return whether the card is user-resizable
     */
    public final boolean isEnableResizable() {
        return enableResizable.get();
    }

    /**
     * Sets whether the card is user-resizable.
     *
     * @param value whether the card is user-resizable
     */
    public final void setEnableResizable(boolean value) {
        enableResizable.set(value);
    }

    // ==================== Enable Draggable ====================

    // Named enableDraggable for symmetry with enableResizable (both are end-user mouse
    // gestures); Node has no draggable member, so the name is otherwise free.
    private final BooleanProperty enableDraggable =
            new SimpleBooleanProperty(this, "enableDraggable", DEFAULT_ENABLE_DRAGGABLE);

    /**
     * Whether the user can move the card by dragging its top (title) band
     * ({@code false} by default). The skin clamps the card so it always stays within
     * the scene; presses on the close (X) button or other interactive header nodes
     * are excluded so they keep working. Only effective while the dialog is shown and
     * not animating. Turning it off cancels an in-progress drag and stops new ones,
     * but keeps the card at its current position; the position recenters only when the
     * dialog has fully hidden.
     *
     * @return the user-draggable property
     */
    public final BooleanProperty enableDraggableProperty() {
        return enableDraggable;
    }

    /**
     * Returns whether the card is user-draggable.
     *
     * @return whether the card is user-draggable
     */
    public final boolean isEnableDraggable() {
        return enableDraggable.get();
    }

    /**
     * Sets whether the card is user-draggable.
     *
     * @param value whether the card is user-draggable
     */
    public final void setEnableDraggable(boolean value) {
        enableDraggable.set(value);
    }

    // ==================== Card size bounds ====================

    // These six properties drive the dialog card's own min / pref / max width and height
    // (the skin binds the card's size properties to them), so they bound an interactive
    // resize and set the card's initial size. They are the card's bounds, deliberately NOT
    // the control's Region min/max (the control fills the scene to back the scrim). A value
    // of USE_COMPUTED_SIZE (-1) means "compute from content" (pref) / "no bound" (max); the
    // skin clamps with the same boundedSize contract used for layout, so an inverted
    // min > max never throws (min wins).

    private final DoubleProperty cardMinWidth = new StyleableDoubleProperty(DEFAULT_CARD_MIN_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_MIN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardMinWidth";
        }
    };

    /**
     * The card's minimum width — the lower bound when the user shrinks the card by
     * dragging. Defaults to {@link #DEFAULT_CARD_MIN_WIDTH}. {@code USE_COMPUTED_SIZE}
     * ({@code -1}) lets the content drive the minimum.
     *
     * @return the card minimum-width property
     */
    public final DoubleProperty cardMinWidthProperty() {
        return cardMinWidth;
    }

    /**
     * Returns the card minimum width.
     *
     * @return the card minimum width
     */
    public final double getCardMinWidth() {
        return cardMinWidth.get();
    }

    /**
     * Sets the card minimum width.
     *
     * @param value the card minimum width, or {@code USE_COMPUTED_SIZE} to compute from content
     */
    public final void setCardMinWidth(double value) {
        cardMinWidth.set(value);
    }

    private final DoubleProperty cardPrefWidth = new StyleableDoubleProperty(Region.USE_COMPUTED_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_PREF_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardPrefWidth";
        }
    };

    /**
     * The card's preferred (initial) width before any resize. Defaults to
     * {@code USE_COMPUTED_SIZE} ({@code -1}), which sizes the card to its content.
     *
     * @return the card preferred-width property
     */
    public final DoubleProperty cardPrefWidthProperty() {
        return cardPrefWidth;
    }

    /**
     * Returns the card preferred width.
     *
     * @return the card preferred width
     */
    public final double getCardPrefWidth() {
        return cardPrefWidth.get();
    }

    /**
     * Sets the card preferred width.
     *
     * @param value the card preferred width, or {@code USE_COMPUTED_SIZE} to size to content
     */
    public final void setCardPrefWidth(double value) {
        cardPrefWidth.set(value);
    }

    private final DoubleProperty cardMaxWidth = new StyleableDoubleProperty(Region.USE_COMPUTED_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_MAX_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardMaxWidth";
        }
    };

    /**
     * The card's maximum width — the upper bound when the user grows the card by dragging.
     * Defaults to {@code USE_COMPUTED_SIZE} ({@code -1}), i.e. no bound beyond the available
     * scene.
     *
     * @return the card maximum-width property
     */
    public final DoubleProperty cardMaxWidthProperty() {
        return cardMaxWidth;
    }

    /**
     * Returns the card maximum width.
     *
     * @return the card maximum width
     */
    public final double getCardMaxWidth() {
        return cardMaxWidth.get();
    }

    /**
     * Sets the card maximum width.
     *
     * @param value the card maximum width, or {@code USE_COMPUTED_SIZE} for no bound
     */
    public final void setCardMaxWidth(double value) {
        cardMaxWidth.set(value);
    }

    private final DoubleProperty cardMinHeight = new StyleableDoubleProperty(DEFAULT_CARD_MIN_HEIGHT) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_MIN_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardMinHeight";
        }
    };

    /**
     * The card's minimum height — the lower bound when the user shrinks the card by
     * dragging. Defaults to {@link #DEFAULT_CARD_MIN_HEIGHT}. {@code USE_COMPUTED_SIZE}
     * ({@code -1}) lets the content drive the minimum.
     *
     * @return the card minimum-height property
     */
    public final DoubleProperty cardMinHeightProperty() {
        return cardMinHeight;
    }

    /**
     * Returns the card minimum height.
     *
     * @return the card minimum height
     */
    public final double getCardMinHeight() {
        return cardMinHeight.get();
    }

    /**
     * Sets the card minimum height.
     *
     * @param value the card minimum height, or {@code USE_COMPUTED_SIZE} to compute from content
     */
    public final void setCardMinHeight(double value) {
        cardMinHeight.set(value);
    }

    private final DoubleProperty cardPrefHeight = new StyleableDoubleProperty(Region.USE_COMPUTED_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_PREF_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardPrefHeight";
        }
    };

    /**
     * The card's preferred (initial) height before any resize. Defaults to
     * {@code USE_COMPUTED_SIZE} ({@code -1}), which sizes the card to its content.
     *
     * @return the card preferred-height property
     */
    public final DoubleProperty cardPrefHeightProperty() {
        return cardPrefHeight;
    }

    /**
     * Returns the card preferred height.
     *
     * @return the card preferred height
     */
    public final double getCardPrefHeight() {
        return cardPrefHeight.get();
    }

    /**
     * Sets the card preferred height.
     *
     * @param value the card preferred height, or {@code USE_COMPUTED_SIZE} to size to content
     */
    public final void setCardPrefHeight(double value) {
        cardPrefHeight.set(value);
    }

    private final DoubleProperty cardMaxHeight = new StyleableDoubleProperty(Region.USE_COMPUTED_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.CARD_MAX_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "cardMaxHeight";
        }
    };

    /**
     * The card's maximum height — the upper bound when the user grows the card by dragging.
     * Defaults to {@code USE_COMPUTED_SIZE} ({@code -1}), i.e. no bound beyond the available
     * scene.
     *
     * @return the card maximum-height property
     */
    public final DoubleProperty cardMaxHeightProperty() {
        return cardMaxHeight;
    }

    /**
     * Returns the card maximum height.
     *
     * @return the card maximum height
     */
    public final double getCardMaxHeight() {
        return cardMaxHeight.get();
    }

    /**
     * Sets the card maximum height.
     *
     * @param value the card maximum height, or {@code USE_COMPUTED_SIZE} for no bound
     */
    public final void setCardMaxHeight(double value) {
        cardMaxHeight.set(value);
    }

    // ==================== Transition ====================

    private final ObjectProperty<RXDialogTransition> transition =
            new StyleableObjectProperty<>(DEFAULT_TRANSITION) {
                @Override
                public CssMetaData<? extends Styleable, RXDialogTransition> getCssMetaData() {
                    return StyleableProperties.TRANSITION;
                }

                @Override
                public Object getBean() {
                    return RXDialog.this;
                }

                @Override
                public String getName() {
                    return "transition";
                }
            };

    /**
     * The enter / exit transition style. A {@code null} value is not rejected; it
     * resolves to {@link #DEFAULT_TRANSITION} at the use site. Read when a
     * transition starts, so a change applies to the next show / hide.
     *
     * @return the transition property
     */
    public final ObjectProperty<RXDialogTransition> transitionProperty() {
        return transition;
    }

    /**
     * Returns the transition style.
     *
     * @return the transition style
     */
    public final RXDialogTransition getTransition() {
        return transition.get();
    }

    /**
     * Sets the transition style.
     *
     * @param value the transition style, or {@code null} to fall back to the default
     */
    public final void setTransition(RXDialogTransition value) {
        transition.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXDialog.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether show / hide transitions animate. When {@code false}, transitions snap
     * to their final state.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether transitions animate.
     *
     * @return whether transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether transitions animate.
     *
     * @param value whether transitions animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXDialog.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single show / hide transition. A {@code null}, non-positive,
     * unknown, or indefinite value is not rejected; it disables animation (the
     * transition snaps), like {@code animated=false}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration; {@code null} or non-positive disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for show / hide transitions. Accepts {@code null}, which the skin
     * treats as {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable: there is no
     * stable public CSS converter for an arbitrary {@link Interpolator}.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator, possibly {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the animation interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Show ====================

    /**
     * Shows the dialog over the previously set {@link #ownerProperty() owner}'s scene
     * (as a full-scene overlay). A no-op when already shown.
     *
     * @throws IllegalStateException if no owner is set, or the resolved owner is not in
     *                               a scene
     */
    public final void show() {
        if (isShowing()) {
            return;
        }
        doShow(null);
    }

    /**
     * Sets the owner and shows the dialog over the owner's scene (as a full-scene
     * overlay). A no-op when already shown.
     *
     * @param owner a node already in the target scene; if {@code null}, the current
     *              owner is used instead
     * @throws IllegalStateException if no owner resolves to a scene
     */
    public final void show(Node owner) {
        if (isShowing()) {
            return;
        }
        if (owner != null) {
            setOwner(owner);
        }
        doShow(null);
    }

    /**
     * Shows the dialog mounted into an explicit container pane, instead of as a
     * full-scene overlay over an owner's scene root. A no-op when already shown.
     *
     * <p>All dialogs shown over one scene share a single overlay layer (so they stack,
     * share a single scrim, and trap focus together), so the container is honored only
     * by the dialog that first installs that layer. Showing a later, still overlapping
     * dialog with a <em>different</em> container over the same scene throws rather than
     * silently ignoring it; once the earlier dialogs close and the layer uninstalls, a
     * fresh container is honored again.</p>
     *
     * @param container the pane to mount the dialog overlay into
     * @throws IllegalStateException if {@code container} is {@code null} and not in a
     *                               scene, or the scene already has an overlay layer
     *                               mounted elsewhere
     */
    public final void showIn(Pane container) {
        if (isShowing()) {
            return;
        }
        doShow(container);
    }

    private void doShow(Pane container) {
        // Attach to the per-scene overlay layer first, then force a CSS pass so the
        // skin exists and observes the showing flip below (which plays the transition).
        RXDialogLayer.attach(this, container);
        applyCss();
        // A fresh show supersedes any close still being bookkept (e.g. re-show during a
        // close transition), so the hideInProgress guard stays honest.
        clearPendingClose();
        showing.set(true);
    }

    // ==================== Close ====================

    /**
     * Requests a programmatic close with no candidate button type. Flows through the
     * {@code CLOSE_REQUEST} veto. A no-op when already hidden.
     */
    public final void close() {
        requestClose(null, CloseReason.PROGRAMMATIC);
    }

    /**
     * Requests a programmatic close attributing the given button type as the result
     * candidate. Flows through the {@code CLOSE_REQUEST} veto. A no-op when already
     * hidden.
     *
     * @param buttonType the candidate button type, or {@code null}
     */
    public final void close(ButtonType buttonType) {
        requestClose(buttonType, CloseReason.PROGRAMMATIC);
    }

    /**
     * The single close gate every dismissal path flows through. Resolves the
     * candidate button type, fires a vetoable {@code CLOSE_REQUEST}, and — if not
     * vetoed — computes and sets the result, fires {@code HIDING}, and starts the
     * hide transition (the result is delivered to {@code onResult} only after the
     * dialog has fully hidden). A no-op when already hidden.
     *
     * <p>For ESC / scrim / close-button reasons with a {@code null} candidate, the
     * cancel-type button in {@link #getButtonTypes() buttonTypes} (if any) becomes
     * the candidate. Used by the skin to attribute each dismissal path; applications
     * normally call {@link #close()} / {@link #close(ButtonType)} instead.</p>
     *
     * @param candidate the explicit candidate button type, or {@code null} to derive one
     * @param reason    why the close was requested; {@code null} is treated as
     *                  {@link CloseReason#PROGRAMMATIC}
     */
    public final void requestClose(ButtonType candidate, CloseReason reason) {
        // closeGateActive blocks a re-entrant close()/requestClose() from a CLOSE_REQUEST /
        // HIDING handler or a result listener, so a single dismissal yields exactly one
        // CLOSE_REQUEST -> HIDING -> HIDDEN sequence and can never recurse.
        if (!isShowing() || hideInProgress || closeGateActive) {
            return;
        }
        closeGateActive = true;
        try {
            CloseReason effectiveReason = reason == null ? CloseReason.PROGRAMMATIC : reason;
            ButtonType effective = candidate;
            if (effective == null && isDismissReason(effectiveReason)) {
                effective = findCancelButtonType();
            }

            RXDialogEvent request = new RXDialogEvent(RXDialogEvent.CLOSE_REQUEST, this, effective, effectiveReason);
            fireEvent(request);
            if (request.isConsumed()) {
                return;
            }

            hideInProgress = true;
            R computed = convertResult(effective);
            pendingButtonType = effective;
            pendingCloseReason = effectiveReason;
            pendingResult = computed;
            setResult(computed);

            fireEvent(new RXDialogEvent(RXDialogEvent.HIDING, this, effective, effectiveReason));
            showing.set(false);
        } finally {
            closeGateActive = false;
        }
    }

    /**
     * Invoked by the skin when the hide transition has fully completed: fires
     * {@code HIDDEN} with the close payload, delivers the result to
     * {@code onResult}, and detaches the dialog from its overlay layer. Not intended
     * to be called by applications.
     */
    public final void hideCompleted() {
        // Guard the public hook: only act when a real close is in flight, so a stray
        // external call cannot deliver a result or detach out of band.
        if (!hideInProgress) {
            return;
        }
        ButtonType bt = pendingButtonType;
        CloseReason reason = pendingCloseReason;
        R delivered = pendingResult;
        clearPendingClose();

        // Detach (fully commit the close) BEFORE notifying, so a re-show triggered
        // from a HIDDEN handler or the onResult callback (dialog chaining) re-attaches
        // cleanly and is not undone by a trailing detach.
        RXDialogLayer.detach(this);
        fireEvent(new RXDialogEvent(RXDialogEvent.HIDDEN, this, bt, reason));
        Consumer<R> callback = getOnResult();
        if (callback != null) {
            callback.accept(delivered);
        }
    }

    private void clearPendingClose() {
        hideInProgress = false;
        pendingButtonType = null;
        pendingCloseReason = null;
        pendingResult = null;
    }

    private static boolean isDismissReason(CloseReason reason) {
        return reason == CloseReason.ESC || reason == CloseReason.SCRIM || reason == CloseReason.CLOSE_BUTTON;
    }

    private ButtonType findCancelButtonType() {
        for (ButtonType buttonType : buttonTypes) {
            if (buttonType != null && buttonType.getButtonData() != null
                    && buttonType.getButtonData().isCancelButton()) {
                return buttonType;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private R convertResult(ButtonType candidate) {
        Callback<ButtonType, R> converter = getResultConverter();
        if (converter != null) {
            return converter.call(candidate);
        }
        // Mirror the native Dialog's behaviour when no converter is set.
        return (R) candidate;
    }

    // ==================== Events ====================

    private ObjectProperty<EventHandler<RXDialogEvent>> onShowing;

    /**
     * Handler called when a show transition starts.
     *
     * @return the onShowing property
     */
    public final ObjectProperty<EventHandler<RXDialogEvent>> onShowingProperty() {
        if (onShowing == null) {
            onShowing = newHandlerProperty("onShowing", RXDialogEvent.SHOWING);
        }
        return onShowing;
    }

    /**
     * Returns the onShowing handler.
     *
     * @return the onShowing handler, or {@code null}
     */
    public final EventHandler<RXDialogEvent> getOnShowing() {
        return onShowing == null ? null : onShowing.get();
    }

    /**
     * Sets the onShowing handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnShowing(EventHandler<RXDialogEvent> value) {
        onShowingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDialogEvent>> onShown;

    /**
     * Handler called when a show transition has fully completed.
     *
     * @return the onShown property
     */
    public final ObjectProperty<EventHandler<RXDialogEvent>> onShownProperty() {
        if (onShown == null) {
            onShown = newHandlerProperty("onShown", RXDialogEvent.SHOWN);
        }
        return onShown;
    }

    /**
     * Returns the onShown handler.
     *
     * @return the onShown handler, or {@code null}
     */
    public final EventHandler<RXDialogEvent> getOnShown() {
        return onShown == null ? null : onShown.get();
    }

    /**
     * Sets the onShown handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnShown(EventHandler<RXDialogEvent> value) {
        onShownProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDialogEvent>> onCloseRequest;

    /**
     * Handler called before any close proceeds; {@link javafx.event.Event#consume()
     * consuming} the event keeps the dialog open.
     *
     * @return the onCloseRequest property
     */
    public final ObjectProperty<EventHandler<RXDialogEvent>> onCloseRequestProperty() {
        if (onCloseRequest == null) {
            onCloseRequest = newHandlerProperty("onCloseRequest", RXDialogEvent.CLOSE_REQUEST);
        }
        return onCloseRequest;
    }

    /**
     * Returns the onCloseRequest handler.
     *
     * @return the onCloseRequest handler, or {@code null}
     */
    public final EventHandler<RXDialogEvent> getOnCloseRequest() {
        return onCloseRequest == null ? null : onCloseRequest.get();
    }

    /**
     * Sets the onCloseRequest handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnCloseRequest(EventHandler<RXDialogEvent> value) {
        onCloseRequestProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDialogEvent>> onHiding;

    /**
     * Handler called when a close transition starts (the close was not vetoed).
     *
     * @return the onHiding property
     */
    public final ObjectProperty<EventHandler<RXDialogEvent>> onHidingProperty() {
        if (onHiding == null) {
            onHiding = newHandlerProperty("onHiding", RXDialogEvent.HIDING);
        }
        return onHiding;
    }

    /**
     * Returns the onHiding handler.
     *
     * @return the onHiding handler, or {@code null}
     */
    public final EventHandler<RXDialogEvent> getOnHiding() {
        return onHiding == null ? null : onHiding.get();
    }

    /**
     * Sets the onHiding handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnHiding(EventHandler<RXDialogEvent> value) {
        onHidingProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDialogEvent>> onHidden;

    /**
     * Handler called when a close transition has fully completed, just before the
     * result is delivered to {@link #onResultProperty() onResult}.
     *
     * @return the onHidden property
     */
    public final ObjectProperty<EventHandler<RXDialogEvent>> onHiddenProperty() {
        if (onHidden == null) {
            onHidden = newHandlerProperty("onHidden", RXDialogEvent.HIDDEN);
        }
        return onHidden;
    }

    /**
     * Returns the onHidden handler.
     *
     * @return the onHidden handler, or {@code null}
     */
    public final EventHandler<RXDialogEvent> getOnHidden() {
        return onHidden == null ? null : onHidden.get();
    }

    /**
     * Sets the onHidden handler.
     *
     * @param value the handler, or {@code null} to clear
     */
    public final void setOnHidden(EventHandler<RXDialogEvent> value) {
        onHiddenProperty().set(value);
    }

    private ObjectProperty<EventHandler<RXDialogEvent>> newHandlerProperty(String name,
                                                                          EventType<RXDialogEvent> type) {
        return new ObjectPropertyBase<>() {
            @Override
            protected void invalidated() {
                setEventHandler(type, get());
            }

            @Override
            public Object getBean() {
                return RXDialog.this;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXDialog<?>, RXDialogTransition> TRANSITION =
                new CssMetaData<>("-rx-transition",
                        new EnumConverter<>(RXDialogTransition.class), DEFAULT_TRANSITION) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.transition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXDialogTransition> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<RXDialogTransition>) node.transitionProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, RXDialogActionsLayout> ACTIONS_LAYOUT =
                new CssMetaData<>("-rx-actions-layout",
                        new EnumConverter<>(RXDialogActionsLayout.class), DEFAULT_ACTIONS_LAYOUT) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.actionsLayout.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXDialogActionsLayout> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<RXDialogActionsLayout>) node.actionsLayoutProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_MIN_WIDTH =
                new CssMetaData<>("-rx-card-min-width", SizeConverter.getInstance(), DEFAULT_CARD_MIN_WIDTH) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardMinWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardMinWidthProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_PREF_WIDTH =
                new CssMetaData<>("-rx-card-pref-width", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardPrefWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardPrefWidthProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_MAX_WIDTH =
                new CssMetaData<>("-rx-card-max-width", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardMaxWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardMaxWidthProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_MIN_HEIGHT =
                new CssMetaData<>("-rx-card-min-height", SizeConverter.getInstance(), DEFAULT_CARD_MIN_HEIGHT) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardMinHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardMinHeightProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_PREF_HEIGHT =
                new CssMetaData<>("-rx-card-pref-height", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardPrefHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardPrefHeightProperty();
                    }
                };

        private static final CssMetaData<RXDialog<?>, Number> CARD_MAX_HEIGHT =
                new CssMetaData<>("-rx-card-max-height", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(RXDialog<?> node) {
                        return !node.cardMaxHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDialog<?> node) {
                        return (StyleableProperty<Number>) node.cardMaxHeightProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(TRANSITION);
            styleables.add(ACTIONS_LAYOUT);
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            styleables.add(CARD_MIN_WIDTH);
            styleables.add(CARD_PREF_WIDTH);
            styleables.add(CARD_MAX_WIDTH);
            styleables.add(CARD_MIN_HEIGHT);
            styleables.add(CARD_PREF_HEIGHT);
            styleables.add(CARD_MAX_HEIGHT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
