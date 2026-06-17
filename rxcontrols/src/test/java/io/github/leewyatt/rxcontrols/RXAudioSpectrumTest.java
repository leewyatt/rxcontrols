package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.spectrum.BandLayout;
import io.github.leewyatt.rxcontrols.spectrum.VisBars;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXAudioSpectrum}: defaults, the coerce-and-throw contracts
 * of {@code bandCount} / {@code minDecibels} (including the bound-property
 * variant), the copy semantics of {@code updateSpectrum}, the update-sequence
 * counter, the {@code :active} / {@code :silent} pseudo-classes, and the CSS
 * metadata surface.
 */
public class RXAudioSpectrumTest {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass SILENT = PseudoClass.getPseudoClass("silent");

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    @Test
    public void defaults() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();

        assertTrue(spectrum.getStyleClass().contains("rx-audio-spectrum"));
        assertInstanceOf(VisBars.class, spectrum.getVisualization());
        assertEquals(BandLayout.LINEAR, spectrum.getBandLayout());
        assertSame(RXAudioSpectrum.DEFAULT_BAR_FILL, spectrum.getBarFill());
        assertSame(RXAudioSpectrum.DEFAULT_PEAK_FILL, spectrum.getPeakFill());
        assertEquals(RXAudioSpectrum.DEFAULT_BAR_GAP_RATIO, spectrum.getBarGapRatio());
        assertEquals(RXAudioSpectrum.DEFAULT_SMOOTHING, spectrum.getSmoothing());
        assertTrue(spectrum.isShowPeaks());
        assertFalse(spectrum.isGlow());
        assertFalse(spectrum.isActive());
        assertEquals(0, spectrum.getMagnitudeCount());
        assertFalse(spectrum.isFocusTraversable());
    }

    @Test
    public void distinctInstancesGetDistinctDefaultVisualizations() {
        assertTrue(new RXAudioSpectrum().getVisualization() != new RXAudioSpectrum().getVisualization());
    }

    @Test
    public void bandCountBelowMinimumCoercesAndThrows() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        assertThrows(IllegalArgumentException.class, () -> spectrum.setBandCount(1));
        assertEquals(RXAudioSpectrum.MIN_BAND_COUNT, spectrum.getBandCount());
    }

    @Test
    public void bandCountBoundOnlyThrows() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        SimpleIntegerProperty source = new SimpleIntegerProperty(64);
        spectrum.bandCountProperty().bind(source);
        Throwable thrown = captureListenerException(() -> source.set(0));
        assertInstanceOf(IllegalArgumentException.class, thrown);
        // Bound properties are never written back; the invalid value remains
        // visible while the exception signals the contract violation.
        assertEquals(0, spectrum.getBandCount());
        assertTrue(spectrum.bandCountProperty().isBound());
    }

    @Test
    public void minDecibelsNonNegativeOrNaNCoercesAndThrows() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        assertThrows(IllegalArgumentException.class, () -> spectrum.setMinDecibels(0.0));
        assertEquals(-60.0, spectrum.getMinDecibels());

        assertThrows(IllegalArgumentException.class, () -> spectrum.setMinDecibels(Double.NaN));
        assertEquals(-60.0, spectrum.getMinDecibels());
    }

    @Test
    public void minDecibelsBoundOnlyThrows() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        SimpleDoubleProperty source = new SimpleDoubleProperty(-40.0);
        spectrum.minDecibelsProperty().bind(source);
        Throwable thrown = captureListenerException(() -> source.set(5.0));
        assertInstanceOf(IllegalArgumentException.class, thrown);
        assertEquals(5.0, spectrum.getMinDecibels());
        assertTrue(spectrum.minDecibelsProperty().isBound());
    }

    /**
     * Runs {@code action} while capturing exceptions that property listeners
     * route to the thread's uncaught-exception handler (JavaFX's
     * ExpressionHelper does not let them propagate to the caller).
     */
    private static Throwable captureListenerException(Runnable action) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
        Throwable[] captured = new Throwable[1];
        thread.setUncaughtExceptionHandler((t, e) -> captured[0] = e);
        try {
            action.run();
        } finally {
            thread.setUncaughtExceptionHandler(previous);
        }
        return captured[0];
    }

    @Test
    public void updateSpectrumCopiesTheFrame() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        float[] frame = {-10f, -20f, -30f};
        spectrum.updateSpectrum(frame);
        frame[0] = -55f;

        float[] copied = new float[3];
        assertEquals(3, spectrum.copyMagnitudesTo(copied));
        assertEquals(-10f, copied[0]);
        assertEquals(-20f, copied[1]);
        assertEquals(-30f, copied[2]);
    }

    @Test
    public void updateSpectrumRejectsNull() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        assertThrows(NullPointerException.class, () -> spectrum.updateSpectrum(null));
    }

    @Test
    public void updateSequenceIncrementsPerFrame() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        int before = spectrum.updateSequenceProperty().get();
        spectrum.updateSpectrum(new float[]{-30f});
        spectrum.updateSpectrum(new float[]{-30f});
        assertEquals(before + 2, spectrum.updateSequenceProperty().get());
    }

    @Test
    public void emptyArrayIsASilenceFrame() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.updateSpectrum(new float[]{-10f, -20f});
        int before = spectrum.updateSequenceProperty().get();

        spectrum.updateSpectrum(new float[0]);
        assertEquals(0, spectrum.getMagnitudeCount());
        assertEquals(before + 1, spectrum.updateSequenceProperty().get());
        assertEquals(0, spectrum.copyMagnitudesTo(new float[4]));
    }

    @Test
    public void copyMagnitudesToRespectsDestinationLength() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        spectrum.updateSpectrum(new float[]{-1f, -2f, -3f, -4f});
        float[] dest = new float[2];
        assertEquals(2, spectrum.copyMagnitudesTo(dest));
        assertEquals(-1f, dest[0]);
        assertEquals(-2f, dest[1]);
    }

    @Test
    public void activeDrivesPseudoClasses() {
        RXAudioSpectrum spectrum = new RXAudioSpectrum();
        assertTrue(spectrum.getPseudoClassStates().contains(SILENT));
        assertFalse(spectrum.getPseudoClassStates().contains(ACTIVE));

        spectrum.setActive(true);
        assertTrue(spectrum.getPseudoClassStates().contains(ACTIVE));
        assertFalse(spectrum.getPseudoClassStates().contains(SILENT));

        spectrum.setActive(false);
        assertTrue(spectrum.getPseudoClassStates().contains(SILENT));
        assertFalse(spectrum.getPseudoClassStates().contains(ACTIVE));
    }

    @Test
    public void cssMetadataIsSettableUnlessBoundForEveryProperty() {
        Map<String, Function<RXAudioSpectrum, Property<?>>> properties = new LinkedHashMap<>();
        properties.put("-rx-band-layout", RXAudioSpectrum::bandLayoutProperty);
        properties.put("-rx-bar-fill", RXAudioSpectrum::barFillProperty);
        properties.put("-rx-peak-fill", RXAudioSpectrum::peakFillProperty);
        properties.put("-rx-bar-gap-ratio", RXAudioSpectrum::barGapRatioProperty);
        properties.put("-rx-smoothing", RXAudioSpectrum::smoothingProperty);
        properties.put("-rx-show-peaks", RXAudioSpectrum::showPeaksProperty);
        properties.put("-rx-glow", RXAudioSpectrum::glowProperty);

        properties.forEach((name, accessor) -> {
            RXAudioSpectrum spectrum = new RXAudioSpectrum();
            CssMetaData<Styleable, ?> metadata = metadata(name);
            Property<?> property = accessor.apply(spectrum);

            assertTrue(metadata.isSettable(spectrum), name + " should be settable when unbound");
            assertSame(property, metadata.getStyleableProperty(spectrum),
                    name + " must expose its own backing property");

            bindToValueSnapshot(property);
            assertFalse(metadata.isSettable(spectrum), name + " should not be settable when bound");
        });
    }

    private static <T> void bindToValueSnapshot(Property<T> property) {
        property.bind(new SimpleObjectProperty<>(property.getValue()));
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<Styleable, ?> metadata(String property) {
        return RXAudioSpectrum.getClassCssMetaData().stream()
                .filter(m -> m.getProperty().equals(property))
                .map(m -> (CssMetaData<Styleable, ?>) m)
                .findFirst()
                .orElseThrow();
    }
}
