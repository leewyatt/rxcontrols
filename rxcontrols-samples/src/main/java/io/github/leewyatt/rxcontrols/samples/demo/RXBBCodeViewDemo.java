package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBBCodeViewShowcase;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXBBCodeView}.
 *
 * <p>A "release announcement" card rendered from a single BBCode string that exercises
 * the V1 tag set — headings, bold / italic, ordered-style lists (including the
 * {@code [ul]} / {@code [li]} aliases), a quote, a code block, a link, an image, and a
 * collapsible spoiler. Activating a link opens it in the system browser via
 * {@code HostServices}, and the whole view sits in a width-tracking {@link ScrollPane}
 * so long content wraps and scrolls. For the property explorer see
 * {@link RXBBCodeViewShowcase}.</p>
 */
public class RXBBCodeViewDemo extends Application {

    private static final String ANNOUNCEMENT = """
            [h2]RXControls 1.0 is here[/h2]
            We are happy to announce [b]RXControls 1.0[/b] — a set of [i]safe, bindable[/i] \
            JavaFX controls. This card itself is a single [b]BBCode[/b] string rendered by \
            [b]RXBBCodeView[/b].

            [h3]Highlights[/h3]
            [list]
            [*]A lightweight, sandboxed renderer for [b]untrusted[/b] rich text
            [*]Theme-aware styling across light, dark and AtlantaFX
            [*]No HTML and no scripting — [i]typed setters only[/i]
            [/list]

            [h3]Aliases work too[/h3]
            [ul]
            [li]This list uses the [b][ul][/b] / [b][li][/b] aliases
            [li]…and renders exactly like [b][list][/b] / [b][*][/b]
            [/ul]

            [quote=The Team]Ship small, ship safe, ship often.[/quote]

            [h3]Get it[/h3]
            Read the guide on [url=https://github.com/leewyatt/rxcontrols]the project page[/url], \
            or copy this snippet:
            [code]<dependency>
              <artifactId>rxcontrols</artifactId>
            </dependency>[/code]

            [img]https://picsum.photos/seed/rxcontrols/520/180[/img]

            [spoiler=Known issues]Nothing major yet — check the tracker for the full list.[/spoiler]
            """;

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXBBCodeView view = new RXBBCodeView(ANNOUNCEMENT);
        view.setOnLinkActivated(event -> getHostServices().showDocument(event.getHref()));

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);

        primaryStage.setScene(new Scene(scroll, 620.0, 660.0));
        primaryStage.setTitle("RXBBCodeView Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
