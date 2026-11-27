package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Temporary probe: sample icons with no background fill. */
public class TmpSampleIconColorProbeTest {

    @Test
    public void dump() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try {
            Platform.startup(start::countDown);
        } catch (IllegalStateException running) {
            start.countDown();
        }
        start.await(5, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        List<String> lines = new ArrayList<>();
        for (SamplerEntry entry : SamplerCatalog.entries()) {
            if (entry.isHome()) {
                continue;
            }
            run(() -> {
                mount("page " + entry.name(), new SamplerPage(entry), lines);
                for (SamplerVariant variant : entry.variants()) {
                    Node playground = variant.showcase() == null ? SamplerInstances.create(variant.type())
                            : variant.showcase().get().createEmbeddedContent();
                    if (playground != null) {
                        mount("variant " + entry.name(), playground, lines);
                    }
                }
            });
        }
        Path src = Paths.get("src", "main", "java");
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.walk(src)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String rel = src.relativize(file).toString();
                names.add(rel.substring(0, rel.length() - 5).replace('/', '.'));
            }
        }
        for (String name : names) {
            Class<?> type;
            try {
                type = Class.forName(name);
            } catch (Throwable e) {
                continue;
            }
            if (!Application.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            run(() -> {
                List<Window> before = new ArrayList<>(Window.getWindows());
                try {
                    ((Application) type.getDeclaredConstructor().newInstance()).start(new Stage());
                } catch (Throwable ignored) {
                    // probe
                }
                for (Window window : new ArrayList<>(Window.getWindows())) {
                    if (!before.contains(window) && window.getScene() != null) {
                        Parent root = window.getScene().getRoot();
                        root.applyCss();
                        root.layout();
                        collect("app " + type.getSimpleName(), root, "", lines);
                        window.hide();
                    }
                }
            });
        }
        Files.write(Paths.get("/private/tmp/claude-501/-Users-leewyatt-IdeaProjects-202501115-rxcontrols/"
                + "3285b5bb-73d8-43fa-9b51-de9a410526c4/scratchpad/sample-color.txt"), lines, StandardCharsets.UTF_8);
    }

    private static void mount(String key, Node node, List<String> lines) {
        StackPane root = new StackPane(node);
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().addAll(RXShowcaseApplication.shellStylesheet(),
                RXSampler.class.getResource("rx-sampler.css").toExternalForm());
        root.applyCss();
        root.layout();
        collect(key, root, "", lines);
        root.getChildren().clear();
    }

    private static void collect(String key, Node node, String path, List<String> lines) {
        String here = path + "/" + String.join(".", node.getStyleClass());
        if (node instanceof RXIcon icon) {
            String fill = icon.getBackground() == null || icon.getBackground().getFills().isEmpty() ? "NONE"
                    : icon.getBackground().getFills().get(0).getFill().toString();
            lines.add(key + " | " + here.substring(Math.max(0, here.length() - 100)) + " | " + fill
                    + " | shape " + (icon.getShape() != null) + " | " + icon.getWidth() + "x" + icon.getHeight());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(key, child, here, lines);
            }
        }
    }

    private static void run(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable ignored) {
                // probe
            } finally {
                latch.countDown();
            }
        });
        latch.await(60, TimeUnit.SECONDS);
    }
}
