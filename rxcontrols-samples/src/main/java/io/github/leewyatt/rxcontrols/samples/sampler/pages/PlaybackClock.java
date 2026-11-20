package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;

/**
 * A pretend media player clock for the lyric pages: it advances a playback time while
 * playing, loops at the end of the track and pauses when its page is hidden.
 */
final class PlaybackClock {

    private static final Duration TICK = Duration.millis(50.0);

    private final Duration length;
    private final ObjectProperty<Duration> time = new SimpleObjectProperty<>(this, "time", Duration.ZERO);
    private final BooleanProperty playing = new SimpleBooleanProperty(this, "playing", false);
    private final Timeline ticker;
    private long lastNanos;

    PlaybackClock(Duration length) {
        this.length = length;
        ticker = new Timeline(new KeyFrame(TICK, event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Pauses the clock whenever the page is hidden, which also stops it for good when
     * the page is disposed; with autoplay, plays it whenever the page is shown.
     */
    PlaybackClock attach(SampleLifecycle life, boolean autoplay) {
        if (autoplay) {
            life.whenShown(this::play);
        }
        life.whenHidden(this::pause);
        return this;
    }

    ObjectProperty<Duration> timeProperty() {
        return time;
    }

    ReadOnlyBooleanProperty playingProperty() {
        return playing;
    }

    void play() {
        lastNanos = System.nanoTime();
        playing.set(true);
        ticker.play();
    }

    void pause() {
        ticker.stop();
        playing.set(false);
    }

    void toggle() {
        if (playing.get()) {
            pause();
        } else {
            play();
        }
    }

    void seek(Duration value) {
        time.set(value);
    }

    double progress() {
        return time.get().toMillis() / length.toMillis();
    }

    private void tick() {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1_000_000.0;
        lastNanos = now;
        double next = time.get().toMillis() + elapsed;
        if (next >= length.toMillis()) {
            next = 0.0;
        }
        time.set(Duration.millis(next));
    }

    static String format(Duration value) {
        long seconds = (long) Math.floor(value.toSeconds());
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
