package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.bbcode.RXBBCodePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeParseWarning;
import io.github.leewyatt.rxcontrols.bbcode.RXBBDocument;
import io.github.leewyatt.rxcontrols.bbcode.RXBBWarningCode;
import io.github.leewyatt.rxcontrols.bbcode.RXLinkKind;
import io.github.leewyatt.rxcontrols.event.RXBBCodeLinkEvent;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link RXBBCodeView} control layer: property pass-through, the
 * content-&gt;document reparse pipeline (including route-B skip-rebuild on value-equal
 * content), lax {@code null} policy fallback, the {@code :empty} pseudo-class, the
 * read-only immutable warnings, and that exactly the four {@code -rx-} styleables are
 * exposed. All logic lives on the control and is headless-testable.
 */
public class RXBBCodeViewTest {

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");

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
    public void fourInAGroupPassThrough() {
        RXBBCodeView control = new RXBBCodeView();
        assertEquals(Orientation.HORIZONTAL, control.getContentBias());

        control.setContent("[b]x[/b]");
        assertEquals("[b]x[/b]", control.getContent());
        assertFalse(control.getDocument().isEmpty());

        control.setLenient(false);
        assertFalse(control.isLenient());
        control.setShowMalformedTagsAsText(true);
        assertTrue(control.isShowMalformedTagsAsText());
        control.setMaxNestingDepth(64);
        assertEquals(64, control.getMaxNestingDepth());

        Label placeholder = new Label("empty");
        control.setPlaceholder(placeholder);
        assertSame(placeholder, control.getPlaceholder());

        RXBBCodePolicy policy = RXBBCodePolicy.defaults();
        control.setPolicy(policy);
        assertSame(policy, control.getPolicy());
    }

    @Test
    public void nullContentIsTreatedAsEmpty() {
        // reparse() coerces null via getValueSafe(); the public parser rejects null, so
        // this guards the deliberate deviation from the §12.3 snippet.
        assertNull(new RXBBCodeView(null).getContent());
        RXBBCodeView control = new RXBBCodeView("[b]x[/b]");
        assertDoesNotThrow(() -> control.setContent(null));
        assertTrue(control.getDocument().isEmpty());
        assertTrue(control.getPseudoClassStates().contains(EMPTY));
    }

    @Test
    public void appearancePropertiesDoNotReparse() {
        RXBBCodeView control = new RXBBCodeView("[b]x[/b]");
        RXBBDocument doc = control.getDocument();
        List<RXBBCodeParseWarning> warnings = control.getWarnings();

        control.setPlaceholder(new Label("ph"));
        control.setParagraphSpacing(20);
        control.setImageMaxWidth(120);
        control.setImageMaxHeight(90);

        assertSame(doc, control.getDocument(), "appearance changes must not re-parse");
        assertSame(warnings, control.getWarnings());
    }

    @Test
    public void nullPolicyFallsBackToDefaults() {
        RXBBCodeView control = new RXBBCodeView();
        control.setPolicy(null);
        assertNull(control.getPolicy(), "policy is pure pass-through");

        // The default allow-list still applies, so a javascript URL is blocked.
        control.setContent("[url=javascript:alert(1)]x[/url]");
        assertTrue(codes(control).contains(RXBBWarningCode.BLOCKED_URL));
        assertFalse(control.getDocument().isEmpty(), "parsing does not throw on null policy");
    }

    @Test
    public void sameContentKeepsDocumentIdentity() {
        RXBBCodeView control = new RXBBCodeView();
        control.setContent("abc");
        RXBBDocument first = control.getDocument();

        // A value-equal but distinct string re-parses to a structurally equal document,
        // so the read-only document keeps its identity and the skin is not rebuilt.
        control.setContent(new String("abc"));
        assertSame(first, control.getDocument());
    }

    @Test
    public void emptyContentSetsEmptyPseudoClass() {
        RXBBCodeView control = new RXBBCodeView();
        assertTrue(control.getPseudoClassStates().contains(EMPTY));

        control.setContent("[b]hi[/b]");
        assertFalse(control.getPseudoClassStates().contains(EMPTY));

        control.setContent("");
        assertTrue(control.getPseudoClassStates().contains(EMPTY));
    }

    @Test
    public void warningsReadOnlyAndImmutable() {
        RXBBCodeView control = new RXBBCodeView();
        assertTrue(control.getWarnings().isEmpty());

        control.setContent("[foo]bar[/foo]");
        List<RXBBCodeParseWarning> produced = control.getWarnings();
        assertFalse(produced.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> produced.add(produced.get(0)));

        // Clean content clears the warnings again.
        control.setContent("plain");
        assertTrue(control.getWarnings().isEmpty());
    }

    @Test
    public void controlCssMetaDataHasExactlyFourRxProps() {
        Set<String> rxProps = cssPropertyNames(new RXBBCodeView()).stream()
                .filter(name -> name.startsWith("-rx-"))
                .collect(Collectors.toSet());
        assertEquals(Set.of("-rx-paragraph-spacing", "-rx-image-max-width", "-rx-image-max-height",
                "-rx-max-font-size"), rxProps);
    }

    @Test
    public void linkEventTypeRuntimeNames() {
        assertEquals("RX_BBCODE_LINK_ACTIVATED", RXBBCodeLinkEvent.LINK_ACTIVATED.getName());
        assertSame(RXBBCodeLinkEvent.ANY, RXBBCodeLinkEvent.LINK_ACTIVATED.getSuperType());
        assertEquals("RX_BBCODE_LINK", RXBBCodeLinkEvent.ANY.getName());
        assertSame(Event.ANY, RXBBCodeLinkEvent.ANY.getSuperType());
    }

    @Test
    public void onLinkActivatedBridgesToEventHandler() {
        RXBBCodeView control = new RXBBCodeView();
        AtomicReference<RXBBCodeLinkEvent> received = new AtomicReference<>();
        control.setOnLinkActivated(received::set);

        control.fireEvent(new RXBBCodeLinkEvent(control, RXBBCodeLinkEvent.LINK_ACTIVATED,
                "https://ex.com", RXLinkKind.URL));
        assertEquals("https://ex.com", received.get().getHref());
        assertEquals(RXLinkKind.URL, received.get().getLinkKind());
        assertSame(control, received.get().getView());

        // Clearing the convenience handler detaches it.
        received.set(null);
        control.setOnLinkActivated(null);
        control.fireEvent(new RXBBCodeLinkEvent(control, RXBBCodeLinkEvent.LINK_ACTIVATED,
                "https://ex.com", RXLinkKind.URL));
        assertNull(received.get());
    }

    private static List<RXBBWarningCode> codes(RXBBCodeView control) {
        return control.getWarnings().stream().map(RXBBCodeParseWarning::code).collect(Collectors.toList());
    }

    private static Set<String> cssPropertyNames(RXBBCodeView control) {
        Set<String> names = new HashSet<>();
        for (CssMetaData<? extends Styleable, ?> metaData : control.getControlCssMetaData()) {
            names.add(metaData.getProperty());
        }
        return names;
    }
}
