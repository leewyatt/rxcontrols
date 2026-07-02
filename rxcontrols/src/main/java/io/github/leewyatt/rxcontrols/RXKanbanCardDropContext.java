package io.github.leewyatt.rxcontrols;

/**
 * Immutable value object passed to a kanban {@code dropValidator} while a card is
 * dragged over a candidate drop position. It describes the tentative move so the
 * validator can accept or reject it. {@code targetIndex} shares the coordinate
 * system of {@link io.github.leewyatt.rxcontrols.event.RXCardMovedEvent#getToIndex()}
 * — the target column index after the source removal.
 *
 * @param <T> the card type
 */
public final class RXKanbanCardDropContext<T> {

    private final T card;
    private final RXKanbanColumn<T> sourceColumn;
    private final int sourceIndex;
    private final RXKanbanColumn<T> targetColumn;
    private final int targetIndex;

    /**
     * Creates a drop context.
     *
     * @param card         the card being dragged, possibly {@code null}
     * @param sourceColumn the column the card started in
     * @param sourceIndex  the card's index in the source column
     * @param targetColumn the candidate target column
     * @param targetIndex  the candidate target index (source-removed coordinate system)
     */
    public RXKanbanCardDropContext(T card, RXKanbanColumn<T> sourceColumn, int sourceIndex,
                                   RXKanbanColumn<T> targetColumn, int targetIndex) {
        this.card = card;
        this.sourceColumn = sourceColumn;
        this.sourceIndex = sourceIndex;
        this.targetColumn = targetColumn;
        this.targetIndex = targetIndex;
    }

    /**
     * Returns the card being dragged.
     *
     * @return the card, possibly {@code null}
     */
    public T getCard() {
        return card;
    }

    /**
     * Returns the column the card started in.
     *
     * @return the source column
     */
    public RXKanbanColumn<T> getSourceColumn() {
        return sourceColumn;
    }

    /**
     * Returns the card's index in the source column.
     *
     * @return the source index
     */
    public int getSourceIndex() {
        return sourceIndex;
    }

    /**
     * Returns the candidate target column.
     *
     * @return the target column
     */
    public RXKanbanColumn<T> getTargetColumn() {
        return targetColumn;
    }

    /**
     * Returns the candidate target index in the target column, in the coordinate
     * system after the source removal.
     *
     * @return the target index
     */
    public int getTargetIndex() {
        return targetIndex;
    }
}
