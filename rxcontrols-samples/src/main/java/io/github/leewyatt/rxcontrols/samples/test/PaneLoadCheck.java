package io.github.leewyatt.rxcontrols.samples.test;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Temporary smoke check: loads pane_number_field.fxml on the FX thread to
 * confirm the FXML, its controller wiring, and initialize() succeed.
 */
public final class PaneLoadCheck {

    private PaneLoadCheck() {
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Object root = FXMLLoader.load(
                        PaneLoadCheck.class.getResource("/fxml/pane_number_field.fxml"));
                System.out.println("FXML-LOAD-OK root=" + root.getClass().getName());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        Throwable t = err.get();
        if (t != null) {
            System.out.println("FXML-LOAD-FAIL");
            t.printStackTrace(System.out);
            System.exit(1);
        }
        System.out.println("FXML-LOAD-DONE");
        System.exit(0);
    }
}
