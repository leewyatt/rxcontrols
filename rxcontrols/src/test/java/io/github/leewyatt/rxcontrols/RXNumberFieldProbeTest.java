package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class RXNumberFieldProbeTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        latch.await(5, TimeUnit.SECONDS);
    }

    private static <T> T onFx(Supplier<T> body) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { out.set(body.get()); }
            catch (Throwable t) { err.set(t); }
            finally { latch.countDown(); }
        });
        try { latch.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
        if (err.get() != null) { throw new RuntimeException(err.get()); }
        return out.get();
    }

    @Test
    public void probe() {
        onFx(() -> {
            // ---- Probe 1: double value events during setMin convergence ----
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("0"));
            f.setMax(new BigDecimal("100"));
            f.setValue(new BigDecimal("50"));
            List<String> events = new ArrayList<>();
            f.valueProperty().addListener((o, ov, nv) -> events.add(ov + "->" + nv));
            f.setMin(new BigDecimal("200")); // converge max up to 200, value 50->200
            System.out.println("PROBE1 value=" + f.getValue() + " min=" + f.getMin()
                    + " max=" + f.getMax() + " events=" + events);

            // ---- Probe 2: double value events during setMax convergence ----
            RXNumberField g = new RXNumberField();
            g.setMin(new BigDecimal("10"));
            g.setMax(new BigDecimal("100"));
            g.setValue(new BigDecimal("50"));
            List<String> ev2 = new ArrayList<>();
            g.valueProperty().addListener((o, ov, nv) -> ev2.add(ov + "->" + nv));
            g.setMax(new BigDecimal("5")); // converge min down to 5, value 50->5
            System.out.println("PROBE2 value=" + g.getValue() + " min=" + g.getMin()
                    + " max=" + g.getMax() + " events=" + ev2);

            // ---- Probe 3: integer field, unbind a fractional value, then bound change ----
            RXIntegerField h = new RXIntegerField();
            SimpleObjectProperty<BigDecimal> src = new SimpleObjectProperty<>(new BigDecimal("1.5"));
            h.valueProperty().bind(src);
            System.out.println("PROBE3 bound value=" + h.getValue());
            h.valueProperty().unbind();
            h.setMax(new BigDecimal("10")); // triggers adjustValues on now-unbound 1.5
            BigDecimal v3 = h.getValue();
            System.out.println("PROBE3 after unbind+setMax value=" + v3
                    + " scale=" + (v3 == null ? "n/a" : v3.scale()));

            // ---- Probe 4: integer field value in-range invariant for a normal fractional range ----
            RXIntegerField k = new RXIntegerField(new BigDecimal("50"));
            k.setMin(new BigDecimal("2.5"));  // eff lo 3
            k.setMax(new BigDecimal("8.5"));  // eff hi 8
            System.out.println("PROBE4 value=" + k.getValue() + " (raw [2.5,8.5], eff [3,8])");

            // ---- Probe 5: converge into bound opposite throws, then value clamping state ----
            RXNumberField m = new RXNumberField();
            m.setValue(new BigDecimal("5"));
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("10"));
            m.maxProperty().bind(maxSrc);
            boolean threw = false;
            try { m.setMin(new BigDecimal("20")); } catch (RuntimeException ex) { threw = true; }
            System.out.println("PROBE5 threw=" + threw + " min=" + m.getMin() + " max=" + m.getMax()
                    + " value=" + m.getValue());
            // now set a new value in the inverted state
            m.setValue(new BigDecimal("7"));
            System.out.println("PROBE5 after setValue(7) value=" + m.getValue());

            // ---- Probe 6: integer field, setMin fractional above integral max (raw converge) ----
            RXIntegerField n = new RXIntegerField(new BigDecimal("2"));
            n.setMax(new BigDecimal("3"));
            n.setMin(new BigDecimal("5.5")); // converge max to 5.5, eff [6,5] empty
            BigDecimal v6 = n.getValue();
            System.out.println("PROBE6 value=" + v6 + " scale=" + (v6 == null ? "n/a" : v6.scale())
                    + " min=" + n.getMin() + " max=" + n.getMax());

            // ---- Probe 7: rejected edit reverts; check lastValid path with fractional bounds ----
            RXIntegerField p = new RXIntegerField(new BigDecimal("5"));
            p.setMin(new BigDecimal("2.5")); // eff lo 3, value 5 stays
            boolean threw7 = false;
            try { p.setValue(new BigDecimal("2.5")); } catch (RuntimeException ex) { threw7 = true; }
            System.out.println("PROBE7 threw=" + threw7 + " value=" + p.getValue());

            return null;
        });
    }
}
