package io.github.leewyatt.rxcontrols.internal.slider;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Orientation-neutral tick layout shared by the single- and range-slider skins:
 * it positions the tick marks on the track centerline and the tick labels in the
 * cross-axis band, for both orientations, via a {@link SliderAxis}. The text
 * nodes themselves never rotate.
 */
public final class SliderTickLayout {

    private static final double HALF = 0.5;

    private SliderTickLayout() {
    }

    /**
     * Returns the cross-axis extent reserved for the tick-label band, or
     * {@code 0} when labels are hidden or absent.
     *
     * @param showLabels whether labels are shown
     * @param vertical   whether the slider is vertical (the band measures label
     *                   width rather than height)
     * @param labels     the tick-label nodes
     * @param gap        the gap between the bar and the label band
     * @return the cross-axis band extent
     */
    public static double labelBandExtent(boolean showLabels, boolean vertical,
                                         List<Label> labels, double gap) {
        if (!showLabels || labels.isEmpty()) {
            return 0.0;
        }
        double maxCross = 0.0;
        for (Label label : labels) {
            maxCross = Math.max(maxCross, vertical ? label.prefWidth(-1) : label.prefHeight(-1));
        }
        return gap + maxCross;
    }

    /**
     * Lays out the tick marks and labels.
     *
     * @param axis           the slider axis
     * @param trackMLo       the value-axis coordinate of the track start
     * @param trackLengthM   the value-axis track length
     * @param barCrossCenter the cross-axis center of the bar
     * @param crossBar       the cross-axis extent of the bar region (labels start past it)
     * @param gap            the gap between the bar and the label band
     * @param min            the slider minimum
     * @param max            the slider maximum
     * @param showMarks      whether marks are shown
     * @param marks          the mark nodes
     * @param markValues     the mark values
     * @param showLabels     whether labels are shown
     * @param labels         the label nodes
     * @param labelValues    the label values
     * @param snap           the pixel snapper
     */
    public static void layoutTicks(SliderAxis axis, double trackMLo, double trackLengthM,
                                   double barCrossCenter, double crossBar, double gap,
                                   double min, double max,
                                   boolean showMarks, List<Region> marks, double[] markValues,
                                   boolean showLabels, List<Label> labels, double[] labelValues,
                                   SliderSnapper snap) {
        boolean vertical = axis.isVertical();
        double labelCross = crossBar + gap;
        // Hidden ticks need no position, so skip the per-pass measure / relocate.
        if (showMarks) {
            for (int i = 0; i < marks.size(); i++) {
                Region mark = marks.get(i);
                double markW = snap.sizeX(mark.prefWidth(-1));
                double markH = snap.sizeY(mark.prefHeight(-1));
                mark.resize(markW, markH);
                double tickM = trackMLo + trackLengthM
                        * SliderGeometry.valueToFraction(markValues[i], min, max);
                double cx = axis.pointX(tickM, barCrossCenter);
                double cy = axis.pointY(tickM, barCrossCenter);
                mark.relocate(snap.posX(cx - markW * HALF), snap.posY(cy - markH * HALF));
            }
        }
        if (showLabels) {
            for (int i = 0; i < labels.size(); i++) {
                Label label = labels.get(i);
                double labelW = snap.sizeX(label.prefWidth(-1));
                double labelH = snap.sizeY(label.prefHeight(-1));
                label.resize(labelW, labelH);
                double tickM = trackMLo + trackLengthM
                        * SliderGeometry.valueToFraction(labelValues[i], min, max);
                // The label is centered on the tick along the value axis and sits
                // in the cross-axis band; the text node itself never rotates.
                double labelMain = vertical ? labelH : labelW;
                double labelCrossSize = vertical ? labelW : labelH;
                double mainLo = tickM - labelMain * HALF;
                label.relocate(
                        snap.posX(axis.rectX(mainLo, mainLo + labelMain, labelCross, labelCross + labelCrossSize)),
                        snap.posY(axis.rectY(mainLo, mainLo + labelMain, labelCross, labelCross + labelCrossSize)));
            }
        }
    }
}
