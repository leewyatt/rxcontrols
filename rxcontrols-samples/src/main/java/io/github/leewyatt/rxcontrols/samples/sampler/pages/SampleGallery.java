package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXRow;

import java.util.List;

/**
 * Lays the example cards of a page out on a responsive grid. Each card's size picks how
 * many columns it takes; on a narrow page every card falls back to the full row.
 */
public final class SampleGallery {

    // Thresholds are page-body widths, not window widths: the navigation takes the rest.
    private static final RXBreakpointProfile PROFILE = RXBreakpointProfile.builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 520.0)
            .breakpoint(RXBreakpoint.MD, 820.0)
            .build();

    private static final double GAP = 16.0;

    private SampleGallery() {
    }

    /**
     * Creates the grid for the given cards.
     *
     * @param cards the cards, in reading order
     * @return the grid
     */
    public static RXRow create(List<SampleCard> cards) {
        RXRow row = new RXRow();
        row.getStyleClass().add("example-gallery");
        row.setBreakpointProfile(PROFILE);
        row.setGutter(GAP);
        row.setRowGap(GAP);
        row.setAlign(RXRow.Align.STRETCH);
        for (SampleCard card : cards) {
            RXCol col = new RXCol(card);
            col.setXs(RXColSpec.of(24));
            switch (card.getSize()) {
                case SMALL -> {
                    col.setSm(RXColSpec.of(12));
                    col.setMd(RXColSpec.of(8));
                }
                case MEDIUM -> col.setSm(RXColSpec.of(12));
                case LARGE -> col.setMd(RXColSpec.of(12));
                case WIDE -> {
                }
            }
            row.getChildren().add(col);
        }
        return row;
    }
}
