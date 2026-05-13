package io.github.leewyatt.rxcontrols.samples.carousel.control;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.StringConverter;

import java.util.Objects;

/**
 * A lightweight circular progress indicator that displays a progress arc
 * with a centered text label. This is a simple replacement for the GemsFX
 * {@code CircleProgressIndicator} to avoid pulling in the entire GemsFX
 * dependency.
 *
 * <p>Supported CSS style classes:</p>
 * <ul>
 *   <li>{@code .circle-progress-indicator} — the root pane</li>
 *   <li>{@code .track-circle} — the background track arc (full circle)</li>
 *   <li>{@code .progress-arc} — the foreground progress arc</li>
 *   <li>{@code .progress-label} — the centered text label</li>
 * </ul>
 */
public class CircleProgressIndicator extends Region {

    private static final String DEFAULT_STYLE_SHEET =
            Objects.requireNonNull(CircleProgressIndicator.class.getResource("circle-progress-indicator.css")).toExternalForm();

    private final Arc trackArc = new Arc();
    private final Arc progressArc = new Arc();
    private final Label progressLabel = new Label();

    public CircleProgressIndicator() {
        this(0);
    }

    public CircleProgressIndicator(double initialProgress) {
        getStyleClass().add("circle-progress-indicator");

        // Track arc (full background ring)
        trackArc.getStyleClass().add("track-circle");
        trackArc.setManaged(false);
        trackArc.setLength(360);
        trackArc.setType(ArcType.OPEN);
        trackArc.setFill(null);

        // Progress arc (foreground ring)
        progressArc.getStyleClass().add("progress-arc");
        progressArc.setManaged(false);
        progressArc.setStartAngle(90);
        progressArc.setLength(0);
        progressArc.setType(ArcType.OPEN);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);
        progressArc.setFill(null);

        // Center label
        progressLabel.getStyleClass().add("progress-label");

        getChildren().addAll(trackArc, progressArc, progressLabel);

        progressProperty().addListener((obs, oldVal, newVal) -> updateProgress(newVal.doubleValue()));
        setProgress(initialProgress);
    }

    private void updateProgress(double p) {
        double clamped = Math.max(0, Math.min(1, p));
        progressArc.setLength(-360 * clamped);

        StringConverter<Double> conv = getConverter();
        if (conv != null) {
            progressLabel.setText(conv.toString(clamped));
        } else {
            progressLabel.setText(String.format("%.0f%%", clamped * 100));
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double centerX = w / 2;
        double centerY = h / 2;

        double maxStroke = Math.max(trackArc.getStrokeWidth(), progressArc.getStrokeWidth());
        double radius = (Math.min(w, h) - maxStroke) / 2;

        // Layout both arcs at center
        trackArc.setCenterX(centerX);
        trackArc.setCenterY(centerY);
        trackArc.setRadiusX(radius);
        trackArc.setRadiusY(radius);

        progressArc.setCenterX(centerX);
        progressArc.setCenterY(centerY);
        progressArc.setRadiusX(radius);
        progressArc.setRadiusY(radius);

        // Layout label at center
        double labelW = progressLabel.prefWidth(h);
        double labelH = progressLabel.prefHeight(labelW);
        progressLabel.resizeRelocate(centerX - labelW / 2, centerY - labelH / 2, labelW, labelH);
    }

    @Override
    public String getUserAgentStylesheet() {
        return DEFAULT_STYLE_SHEET;
    }

    // ---- progress property ----

    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0);

    public final double getProgress() {
        return progress.get();
    }

    public final void setProgress(double value) {
        progress.set(value);
    }

    public final DoubleProperty progressProperty() {
        return progress;
    }

    // ---- converter property ----

    private final ObjectProperty<StringConverter<Double>> converter =
            new SimpleObjectProperty<>(this, "converter");

    public final StringConverter<Double> getConverter() {
        return converter.get();
    }

    public final void setConverter(StringConverter<Double> value) {
        converter.set(value);
    }

    public final ObjectProperty<StringConverter<Double>> converterProperty() {
        return converter;
    }
}
