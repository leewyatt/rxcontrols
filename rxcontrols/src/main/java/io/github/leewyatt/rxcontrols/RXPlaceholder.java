package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXPlaceholderSkin;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * A centered "icon + title + description + actions" display view for empty,
 * error, and result states. The {@link #statusProperty() status} preset drives
 * a default icon and accent color through CSS pseudo-classes; every slot can be
 * filled or cleared independently, and empty slots collapse (they are hidden and
 * take no space).
 *
 * <p>{@code RXPlaceholder} is a neutral display view: it ships no default
 * actions and no retry wiring of its own. It is the default empty/error UI of
 * {@code RXStatePane} and can equally be used standalone — for example inside
 * the {@code placeholder} slot of a virtualized view.</p>
 *
 * <pre>{@code
 * RXPlaceholder placeholder = new RXPlaceholder(RXPlaceholder.Status.FORBIDDEN, "No access");
 * placeholder.setDescription("Contact an administrator to request access.");
 * placeholder.getActions().add(new Button("Sign in"));
 * }</pre>
 *
 * <p><b>CSS.</b> The root style class is {@code rx-placeholder}. The skin
 * arranges the slots in a centered vertical box: {@code .rx-placeholder >
 * .content} holds {@code .graphic} (the graphic slot; the status-derived
 * default icon is a {@code .icon} region inside it), {@code .title},
 * {@code .description} (whose text run is {@code .text}), and
 * {@code .actions}. Each slot carries the {@code :filled} pseudo-class while
 * it shows something; for example the default not-found icon is styled with
 * {@code .rx-placeholder:not-found > .content > .graphic > .icon}.</p>
 */
public class RXPlaceholder extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-placeholder";

    // ==================== Constants ====================

    /**
     * Default status, also the {@code null} fallback.
     */
    public static final Status DEFAULT_STATUS = Status.NONE;

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass INFO_PSEUDO_CLASS = PseudoClass.getPseudoClass("info");
    private static final PseudoClass SUCCESS_PSEUDO_CLASS = PseudoClass.getPseudoClass("success");
    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");
    private static final PseudoClass NOT_FOUND_PSEUDO_CLASS = PseudoClass.getPseudoClass("not-found");
    private static final PseudoClass FORBIDDEN_PSEUDO_CLASS = PseudoClass.getPseudoClass("forbidden");
    private static final PseudoClass SERVER_ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("server-error");

    // ==================== Constructors ====================

    /**
     * Creates an empty placeholder with the default {@link Status#NONE} status.
     */
    public RXPlaceholder() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.NODE);
        // A pure display view is not a Tab stop (Control defaults to true);
        // focusability belongs to the nodes placed in the actions slot.
        setFocusTraversable(false);
        setAccessibleRoleDescription("placeholder");
    }

    /**
     * Creates a placeholder with the given status preset.
     *
     * @param status the status preset, or {@code null} for the default
     */
    public RXPlaceholder(Status status) {
        this();
        setStatus(status);
    }

    /**
     * Creates a placeholder with the given status preset and title.
     *
     * @param status the status preset, or {@code null} for the default
     * @param title  the title text, or {@code null} for none
     */
    public RXPlaceholder(Status status, String title) {
        this(status);
        setTitle(title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXPlaceholderSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Horizontally biased while a description is present: the description
     * wraps, so the placeholder's height depends on the width it is given.
     * Without the bias, containers would measure the wrapped height at a
     * single-line width and crush the slots (the {@code Labeled.wrapText}
     * precedent).</p>
     */
    @Override
    public Orientation getContentBias() {
        String value = getDescription();
        return (value == null || value.isEmpty()) ? null : Orientation.HORIZONTAL;
    }

    // ==================== Status ====================

    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(this, "status", DEFAULT_STATUS) {
        @Override
        protected void invalidated() {
            updateStatusPseudoClasses();
        }
    };

    /**
     * The status preset driving the default icon and accent color through the
     * matching pseudo-class ({@code :empty}, {@code :info}, {@code :not-found},
     * ...). {@link Status#NONE} activates no pseudo-class and shows no default
     * icon. A {@code null} value is not rejected; it resolves to
     * {@link #DEFAULT_STATUS} at the use site.
     *
     * @return the status property
     */
    public final ObjectProperty<Status> statusProperty() {
        return status;
    }

    /**
     * Returns the status preset.
     *
     * @return the status, possibly {@code null}
     */
    public final Status getStatus() {
        return status.get();
    }

    /**
     * Sets the status preset.
     *
     * @param value the status, or {@code null} to fall back to the default
     */
    public final void setStatus(Status value) {
        status.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The node shown in the top graphic slot. When {@code null} (the default),
     * the skin shows a status-derived icon instead — a shape and color given by
     * CSS under the active status pseudo-class; {@link Status#NONE} shows no
     * icon and the slot collapses.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the graphic node.
     *
     * @return the graphic node, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the graphic node.
     *
     * @param value the graphic node, or {@code null} for the status-derived default
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Title ====================

    private final StringProperty title = new SimpleStringProperty(this, "title") {
        @Override
        protected void invalidated() {
            updateAccessibleText();
        }
    };

    /**
     * The title text. A {@code null} or empty value hides the title line.
     *
     * @return the title property
     */
    public final StringProperty titleProperty() {
        return title;
    }

    /**
     * Returns the title text.
     *
     * @return the title text, possibly {@code null}
     */
    public final String getTitle() {
        return title.get();
    }

    /**
     * Sets the title text.
     *
     * @param value the title text, or {@code null} for none
     */
    public final void setTitle(String value) {
        title.set(value);
    }

    // ==================== Description ====================

    private final StringProperty description = new SimpleStringProperty(this, "description") {
        @Override
        protected void invalidated() {
            updateAccessibleText();
        }
    };

    /**
     * The description text shown under the title, wrapping over multiple lines
     * as needed. A {@code null} or empty value hides the description.
     *
     * @return the description property
     */
    public final StringProperty descriptionProperty() {
        return description;
    }

    /**
     * Returns the description text.
     *
     * @return the description text, possibly {@code null}
     */
    public final String getDescription() {
        return description.get();
    }

    /**
     * Sets the description text.
     *
     * @param value the description text, or {@code null} for none
     */
    public final void setDescription(String value) {
        description.set(value);
    }

    // ==================== Actions ====================

    private final ObservableList<Node> actions = FXCollections.observableArrayList();

    /**
     * The nodes shown in the footer action row, typically buttons. An empty
     * list collapses the footer.
     *
     * @return the modifiable list of action nodes
     */
    public final ObservableList<Node> getActions() {
        return actions;
    }

    // ==================== PseudoClass ====================

    private void updateStatusPseudoClasses() {
        Status current = getStatus() == null ? DEFAULT_STATUS : getStatus();
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, current == Status.EMPTY);
        pseudoClassStateChanged(INFO_PSEUDO_CLASS, current == Status.INFO);
        pseudoClassStateChanged(SUCCESS_PSEUDO_CLASS, current == Status.SUCCESS);
        pseudoClassStateChanged(WARNING_PSEUDO_CLASS, current == Status.WARNING);
        pseudoClassStateChanged(ERROR_PSEUDO_CLASS, current == Status.ERROR);
        pseudoClassStateChanged(NOT_FOUND_PSEUDO_CLASS, current == Status.NOT_FOUND);
        pseudoClassStateChanged(FORBIDDEN_PSEUDO_CLASS, current == Status.FORBIDDEN);
        pseudoClassStateChanged(SERVER_ERROR_PSEUDO_CLASS, current == Status.SERVER_ERROR);
    }

    // ==================== Accessibility ====================

    private void updateAccessibleText() {
        String titleText = getTitle();
        String descriptionText = getDescription();
        StringBuilder joined = new StringBuilder();
        if (titleText != null && !titleText.isEmpty()) {
            joined.append(titleText);
        }
        if (descriptionText != null && !descriptionText.isEmpty()) {
            if (joined.length() > 0) {
                joined.append(". ");
            }
            joined.append(descriptionText);
        }
        setAccessibleText(joined.length() == 0 ? null : joined.toString());
    }

    // ==================== Enums ====================

    /**
     * Presets driving the default icon and accent color of an
     * {@link RXPlaceholder}. Each value except {@link #NONE} activates the
     * matching pseudo-class ({@code :empty}, {@code :info}, {@code :not-found},
     * ...) on the placeholder root.
     */
    public enum Status {

        /**
         * No preset: no pseudo-class and no default icon.
         */
        NONE,

        /**
         * Neutral "no data" empty state.
         */
        EMPTY,

        /**
         * Informational note.
         */
        INFO,

        /**
         * Successful result.
         */
        SUCCESS,

        /**
         * Warning result.
         */
        WARNING,

        /**
         * Generic failure.
         */
        ERROR,

        /**
         * Resource not found (HTTP 404 semantics).
         */
        NOT_FOUND,

        /**
         * Access denied (HTTP 403 semantics).
         */
        FORBIDDEN,

        /**
         * Server failure (HTTP 500 semantics).
         */
        SERVER_ERROR
    }
}
