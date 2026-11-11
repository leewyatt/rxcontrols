package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXPlaceholderSkin;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * A centered "graphic + title + description + actions" display view for empty
 * and error states. Every slot can be filled or cleared independently, and empty
 * slots collapse (they are hidden and take no space).
 *
 * <p>{@code RXPlaceholder} is a neutral display view: it ships no default
 * actions and no retry wiring of its own. It is the default empty/error UI of
 * {@code RXStatePane} and can equally be used standalone — for example inside
 * the {@code placeholder} slot of a virtualized view.</p>
 *
 * <pre>{@code
 * RXPlaceholder placeholder = new RXPlaceholder("No access", lockIcon);
 * placeholder.setDescription("Contact an administrator to request access.");
 * placeholder.getActions().add(new Button("Sign in"));
 * }</pre>
 *
 * <p><b>CSS.</b> The root style class is {@code rx-placeholder}. The skin
 * arranges the slots in a centered vertical box: {@code .rx-placeholder >
 * .content} holds {@code .graphic} (the graphic slot, holding the
 * {@link #graphicProperty() graphic} node), {@code .title}, {@code .description}
 * (whose text run is {@code .text}), and {@code .actions}. Each slot carries the
 * {@code :filled} pseudo-class while it shows something.</p>
 */
public class RXPlaceholder extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-placeholder";

    // ==================== Constructors ====================

    /**
     * Creates an empty placeholder.
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
     * Creates a placeholder with the given title.
     *
     * @param title the title text, or {@code null} for none
     */
    public RXPlaceholder(String title) {
        this();
        setTitle(title);
    }

    /**
     * Creates a placeholder with the given title and graphic.
     *
     * @param title   the title text, or {@code null} for none
     * @param graphic the graphic node, or {@code null} for none
     */
    public RXPlaceholder(String title, Node graphic) {
        this(title);
        setGraphic(graphic);
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

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The node shown in the top graphic slot, typically an icon. A {@code null}
     * value (the default) collapses the slot.
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
     * @param value the graphic node, or {@code null} for none
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
}
