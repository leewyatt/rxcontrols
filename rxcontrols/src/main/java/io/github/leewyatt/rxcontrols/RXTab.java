package io.github.leewyatt.rxcontrols;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

import java.util.Collections;
import java.util.List;

/**
 * A single tab of an {@link RXTabPane}: a lightweight, non-{@code Node} model
 * holding the tab's text, graphic, page content and per-tab state. The skin
 * builds a visible header cell from this model.
 *
 * <p>{@code RXTab} implements {@link Styleable} (so {@code id} / {@code style} /
 * {@code styleClass} authored on the tab reach the header cell the skin mirrors
 * them onto) but is deliberately not a {@code Node} and not an
 * {@code EventTarget}: tab-level event dispatch would require the {@code com.sun}
 * {@code EventHandlerManager}. Close notifications are delivered through the two
 * handler properties {@link #onCloseRequestProperty() onCloseRequest} /
 * {@link #onClosedProperty() onClosed} (invoked directly) and as
 * {@link RXTabEvent}s fired on the owning {@code RXTabPane}.</p>
 */
public class RXTab implements Styleable {

    private static final String DEFAULT_STYLE_CLASS = "tab";
    private static final ContentDisplay DEFAULT_CONTENT_DISPLAY = ContentDisplay.LEFT;

    // ==================== Constructors ====================

    /**
     * Creates an empty tab.
     */
    public RXTab() {
        styleClass.add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a tab with the given text.
     *
     * @param text the tab text, or {@code null}
     */
    public RXTab(String text) {
        this();
        setText(text);
    }

    /**
     * Creates a tab with the given text and page content.
     *
     * @param text    the tab text, or {@code null}
     * @param content the page content, or {@code null}
     */
    public RXTab(String text, Node content) {
        this(text);
        setContent(content);
    }

    // ==================== Factories ====================

    /**
     * Creates a tab with the given text.
     *
     * @param text the tab text, or {@code null}
     * @return the new tab
     */
    public static RXTab of(String text) {
        return new RXTab(text);
    }

    /**
     * Creates a tab with the given text and page content.
     *
     * @param text    the tab text, or {@code null}
     * @param content the page content, or {@code null}
     * @return the new tab
     */
    public static RXTab of(String text, Node content) {
        return new RXTab(text, content);
    }

    /**
     * Creates a tab with the given text, graphic and page content.
     *
     * @param text    the tab text, or {@code null}
     * @param graphic the header graphic, or {@code null}
     * @param content the page content, or {@code null}
     * @return the new tab
     */
    public static RXTab of(String text, Node graphic, Node content) {
        RXTab tab = new RXTab(text, content);
        tab.setGraphic(graphic);
        return tab;
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * The tab text. A {@code null} or blank value renders as an empty label.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the tab text.
     *
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the tab text.
     *
     * @param value the text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * The header graphic. A shape-backed {@code Region} is recommended over a
     * text glyph.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the header graphic.
     *
     * @return the graphic node, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the header graphic.
     *
     * @param value the graphic node, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * The page content shown while this tab is selected. {@code null} makes the
     * tab header-only.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the page content.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the page content.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Content Display ====================

    private final ObjectProperty<ContentDisplay> contentDisplay =
            new SimpleObjectProperty<>(this, "contentDisplay", DEFAULT_CONTENT_DISPLAY);

    /**
     * Placement of the {@link #graphicProperty() graphic} relative to the text
     * in the header (mirrors {@code Labeled.contentDisplay}). Initial value is
     * {@link ContentDisplay#LEFT}.
     *
     * @return the content-display property
     */
    public final ObjectProperty<ContentDisplay> contentDisplayProperty() {
        return contentDisplay;
    }

    /**
     * Returns the content display.
     *
     * @return the content display
     */
    public final ContentDisplay getContentDisplay() {
        return contentDisplay.get();
    }

    /**
     * Sets the content display.
     *
     * @param value the content display
     */
    public final void setContentDisplay(ContentDisplay value) {
        contentDisplay.set(value);
    }

    // ==================== Disable ====================

    private final BooleanProperty disable = new SimpleBooleanProperty(this, "disable", false);

    /**
     * Whether the tab is disabled. A disabled tab can still be selected
     * programmatically, but keyboard navigation and mouse clicks skip it.
     *
     * @return the disable property
     */
    public final BooleanProperty disableProperty() {
        return disable;
    }

    /**
     * Returns whether the tab is disabled.
     *
     * @return {@code true} if disabled
     */
    public final boolean isDisable() {
        return disable.get();
    }

    /**
     * Sets whether the tab is disabled.
     *
     * @param value {@code true} to disable
     */
    public final void setDisable(boolean value) {
        disable.set(value);
    }

    // ==================== Closable ====================

    private final BooleanProperty closable = new SimpleBooleanProperty(this, "closable", true);

    /**
     * Whether a close affordance may be shown for this tab. The pane's
     * {@code tabClosingPolicy} gates this further. Initial value is
     * {@code true}.
     *
     * @return the closable property
     */
    public final BooleanProperty closableProperty() {
        return closable;
    }

    /**
     * Returns whether the tab is closable.
     *
     * @return {@code true} if closable
     */
    public final boolean isClosable() {
        return closable.get();
    }

    /**
     * Sets whether the tab is closable.
     *
     * @param value {@code true} to allow closing
     */
    public final void setClosable(boolean value) {
        closable.set(value);
    }

    // ==================== Tooltip ====================

    private final ObjectProperty<Tooltip> tooltip = new SimpleObjectProperty<>(this, "tooltip");

    /**
     * Tooltip shown when hovering the tab header.
     *
     * @return the tooltip property
     */
    public final ObjectProperty<Tooltip> tooltipProperty() {
        return tooltip;
    }

    /**
     * Returns the tooltip.
     *
     * @return the tooltip, or {@code null}
     */
    public final Tooltip getTooltip() {
        return tooltip.get();
    }

    /**
     * Sets the tooltip.
     *
     * @param value the tooltip, or {@code null}
     */
    public final void setTooltip(Tooltip value) {
        tooltip.set(value);
    }

    // ==================== Accessible Text ====================

    private final StringProperty accessibleText = new SimpleStringProperty(this, "accessibleText");

    /**
     * Screen-reader text for the tab header. This <b>must</b> be set for an
     * icon-only tab (one with a graphic but no {@link #textProperty() text}),
     * otherwise the header announces nothing.
     *
     * @return the accessible-text property
     */
    public final StringProperty accessibleTextProperty() {
        return accessibleText;
    }

    /**
     * Returns the accessible text.
     *
     * @return the accessible text, or {@code null}
     */
    public final String getAccessibleText() {
        return accessibleText.get();
    }

    /**
     * Sets the accessible text.
     *
     * @param value the accessible text, or {@code null}
     */
    public final void setAccessibleText(String value) {
        accessibleText.set(value);
    }

    // ==================== On Close Request ====================

    private final ObjectProperty<EventHandler<RXTabEvent>> onCloseRequest =
            new SimpleObjectProperty<>(this, "onCloseRequest");

    /**
     * Handler invoked before this tab is closed; {@link RXTabEvent#consume()
     * consuming} the event vetoes the close.
     *
     * @return the on-close-request property
     */
    public final ObjectProperty<EventHandler<RXTabEvent>> onCloseRequestProperty() {
        return onCloseRequest;
    }

    /**
     * Returns the close-request handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTabEvent> getOnCloseRequest() {
        return onCloseRequest.get();
    }

    /**
     * Sets the close-request handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnCloseRequest(EventHandler<RXTabEvent> value) {
        onCloseRequest.set(value);
    }

    // ==================== On Closed ====================

    private final ObjectProperty<EventHandler<RXTabEvent>> onClosed =
            new SimpleObjectProperty<>(this, "onClosed");

    /**
     * Handler invoked after this tab has been removed from its pane.
     *
     * @return the on-closed property
     */
    public final ObjectProperty<EventHandler<RXTabEvent>> onClosedProperty() {
        return onClosed;
    }

    /**
     * Returns the closed handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXTabEvent> getOnClosed() {
        return onClosed.get();
    }

    /**
     * Sets the closed handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnClosed(EventHandler<RXTabEvent> value) {
        onClosed.set(value);
    }

    // ==================== Selected (read-only) ====================

    private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(this, "selected", false);

    /**
     * Whether this tab is the currently selected tab. Driven by the pane's
     * selection; change it via the selection model.
     *
     * @return the read-only selected property
     */
    public final ReadOnlyBooleanProperty selectedProperty() {
        return selected.getReadOnlyProperty();
    }

    /**
     * Returns whether this tab is selected.
     *
     * @return {@code true} if selected
     */
    public final boolean isSelected() {
        return selected.get();
    }

    final void setSelected(boolean value) {
        selected.set(value);
    }

    // ==================== Tab Pane (read-only) ====================

    private final ReadOnlyObjectWrapper<RXTabPane> tabPane = new ReadOnlyObjectWrapper<>(this, "tabPane");

    /**
     * The pane this tab currently belongs to.
     *
     * @return the read-only tab-pane property
     */
    public final ReadOnlyObjectProperty<RXTabPane> tabPaneProperty() {
        return tabPane.getReadOnlyProperty();
    }

    /**
     * Returns the pane this tab belongs to.
     *
     * @return the pane, or {@code null}
     */
    public final RXTabPane getTabPane() {
        return tabPane.get();
    }

    final void setTabPane(RXTabPane value) {
        tabPane.set(value);
    }

    // ==================== Styleable ====================

    private StringProperty id;
    private StringProperty style;
    private final ObservableList<String> styleClass = FXCollections.observableArrayList();

    /**
     * Sets the tab id, useful for looking up a specific tab.
     *
     * @param value the id, or {@code null}
     */
    public final void setId(String value) {
        idProperty().set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getId() {
        return id == null ? null : id.get();
    }

    /**
     * The id of this tab.
     *
     * @return the id property
     */
    public final StringProperty idProperty() {
        if (id == null) {
            id = new SimpleStringProperty(this, "id");
        }
        return id;
    }

    /**
     * Sets the inline CSS style string associated with this tab.
     *
     * @param value the style string, or {@code null}
     */
    public final void setStyle(String value) {
        styleProperty().set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getStyle() {
        return style == null ? null : style.get();
    }

    /**
     * The inline CSS style string associated with this tab.
     *
     * @return the style property
     */
    public final StringProperty styleProperty() {
        if (style == null) {
            style = new SimpleStringProperty(this, "style");
        }
        return style;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ObservableList<String> getStyleClass() {
        return styleClass;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "RXTab"}
     */
    @Override
    public String getTypeSelector() {
        return "RXTab";
    }

    /**
     * {@inheritDoc}
     *
     * @return the owning {@link RXTabPane}, or {@code null}
     */
    @Override
    public Styleable getStyleableParent() {
        return getTabPane();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ObservableSet<PseudoClass> getPseudoClassStates() {
        return FXCollections.emptyObservableSet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return Collections.emptyList();
    }
}
