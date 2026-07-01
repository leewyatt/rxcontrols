package io.github.leewyatt.rxcontrols;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Public column model of an {@link RXKanbanView}: a property-bean holding one
 * column's ordered cards plus its title, WIP limit, collapsed state and user
 * data. A column's identity <em>is</em> its {@link #getCards() cards} list, which
 * is embedded and not replaceable (there is no {@code setCards}); mutating that
 * list directly changes the board. Cards stay the generic type {@code T} — the
 * view never wraps them.
 *
 * <p>{@code wipLimit} and {@code collapsed} are consumed by the skin for the
 * WIP count pill / {@code :over-limit} colouring and the collapse animation
 * respectively.
 *
 * @param <T> the card type
 */
public class RXKanbanColumn<T> {

    // ==================== Cards ====================

    private final ObservableList<T> cards;
    private final ReadOnlyIntegerWrapper cardCount = new ReadOnlyIntegerWrapper(this, "cardCount");

    // ==================== Constructors ====================

    /**
     * Creates an empty, untitled column.
     */
    public RXKanbanColumn() {
        this(null, null);
    }

    /**
     * Creates an empty column with the given title.
     *
     * @param title the column title, may be {@code null}
     */
    public RXKanbanColumn(String title) {
        this(title, null);
    }

    /**
     * Creates a column with the given title and cards.
     *
     * @param title the column title, may be {@code null}
     * @param cards the initial cards; when {@code null} a fresh empty observable
     *              list is created. The passed list becomes the column's identity
     *              (it is not copied) and cannot later be replaced.
     */
    public RXKanbanColumn(String title, ObservableList<T> cards) {
        this.cards = cards != null ? cards : FXCollections.observableArrayList();
        this.cardCount.bind(Bindings.size(this.cards));
        setTitle(title);
    }

    // ==================== Cards API ====================

    /**
     * Returns this column's ordered card list. Never {@code null}; the list is
     * embedded (not replaceable) and mutating it directly updates the board.
     *
     * @return the cards list
     */
    public final ObservableList<T> getCards() {
        return cards;
    }

    /**
     * The number of cards in this column, a read-only mirror of
     * {@code getCards().size()} for binding to header count pills / WIP indicators.
     *
     * @return the card-count property
     */
    public final ReadOnlyIntegerProperty cardCountProperty() {
        return cardCount.getReadOnlyProperty();
    }

    /**
     * Returns the number of cards in this column.
     *
     * @return the card count
     */
    public final int getCardCount() {
        return cardCount.get();
    }

    // ==================== Title ====================

    private final StringProperty title = new SimpleStringProperty(this, "title");

    /**
     * The column title, shown by the default column header.
     *
     * @return the title property
     */
    public final StringProperty titleProperty() {
        return title;
    }

    /**
     * Returns the column title.
     *
     * @return the title, may be {@code null}
     */
    public final String getTitle() {
        return title.get();
    }

    /**
     * Sets the column title.
     *
     * @param value the title, may be {@code null}
     */
    public final void setTitle(String value) {
        title.set(value);
    }

    // ==================== WIP Limit ====================

    private final IntegerProperty wipLimit = new SimpleIntegerProperty(this, "wipLimit", 0);

    /**
     * The work-in-progress limit for this column. {@code 0} (the default) means no
     * limit; any value {@code <= 0} is treated as no limit. This is a soft limit:
     * the skin colours the column {@code :over-limit} when
     * {@code wipLimit > 0 && cardCount > wipLimit} but never blocks a drop.
     *
     * @return the WIP-limit property
     */
    public final IntegerProperty wipLimitProperty() {
        return wipLimit;
    }

    /**
     * Returns the WIP limit.
     *
     * @return the WIP limit
     */
    public final int getWipLimit() {
        return wipLimit.get();
    }

    /**
     * Sets the WIP limit.
     *
     * @param value the WIP limit; {@code <= 0} means no limit
     */
    public final void setWipLimit(int value) {
        wipLimit.set(value);
    }

    // ==================== Collapsed ====================

    private final BooleanProperty collapsed = new SimpleBooleanProperty(this, "collapsed", false);

    /**
     * Whether the column is collapsed (its card area hidden). Consumed by the skin
     * for the collapse/expand animation and the {@code :collapsed} pseudo-class.
     *
     * @return the collapsed property
     */
    public final BooleanProperty collapsedProperty() {
        return collapsed;
    }

    /**
     * Returns whether the column is collapsed.
     *
     * @return {@code true} if collapsed
     */
    public final boolean isCollapsed() {
        return collapsed.get();
    }

    /**
     * Sets whether the column is collapsed.
     *
     * @param value {@code true} to collapse
     */
    public final void setCollapsed(boolean value) {
        collapsed.set(value);
    }

    // ==================== User Data ====================

    private final ObjectProperty<Object> userData = new SimpleObjectProperty<>(this, "userData");

    /**
     * Arbitrary user data attached to this column (e.g. a business status object).
     *
     * @return the user-data property
     */
    public final ObjectProperty<Object> userDataProperty() {
        return userData;
    }

    /**
     * Returns the user data.
     *
     * @return the user data, may be {@code null}
     */
    public final Object getUserData() {
        return userData.get();
    }

    /**
     * Sets the user data.
     *
     * @param value the user data, may be {@code null}
     */
    public final void setUserData(Object value) {
        userData.set(value);
    }
}
