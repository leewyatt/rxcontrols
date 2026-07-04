package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXBackgroundNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBBlockNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeImagePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeUrlPolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBInlineNode;
import io.github.leewyatt.rxcontrols.bbcode.RXCodeBlockNode;
import io.github.leewyatt.rxcontrols.bbcode.RXHeadingNode;
import io.github.leewyatt.rxcontrols.bbcode.RXHorizontalRuleNode;
import io.github.leewyatt.rxcontrols.bbcode.RXImageNode;
import io.github.leewyatt.rxcontrols.bbcode.RXLineBreakNode;
import io.github.leewyatt.rxcontrols.bbcode.RXLinkKind;
import io.github.leewyatt.rxcontrols.bbcode.RXLinkNode;
import io.github.leewyatt.rxcontrols.bbcode.RXListItemNode;
import io.github.leewyatt.rxcontrols.bbcode.RXListKind;
import io.github.leewyatt.rxcontrols.bbcode.RXListNode;
import io.github.leewyatt.rxcontrols.bbcode.RXParagraphNode;
import io.github.leewyatt.rxcontrols.bbcode.RXRawTextNode;
import io.github.leewyatt.rxcontrols.bbcode.RXSpoilerNode;
import io.github.leewyatt.rxcontrols.bbcode.RXStyleNode;
import io.github.leewyatt.rxcontrols.bbcode.RXStyleType;
import io.github.leewyatt.rxcontrols.bbcode.RXTableCellNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTableNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTableRowNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTextNode;
import io.github.leewyatt.rxcontrols.event.RXBBCodeLinkEvent;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link RXBBCodeViewSkin} skeleton: the permanent {@code .content} VBox, the
 * placeholder slot and empty-state toggle, the paragraph-spacing binding, the
 * wrap-aware pref-height proxy, and safe (idempotent) disposal. Lives in the {@code skins}
 * package so it can reach the skin's protected {@code getChildren()} / {@code computePrefHeight}.
 */
public class RXBBCodeViewSkinTest {

    private static ServerSocket serverSocket;
    private static volatile boolean serving;
    private static String imageUrl;
    private static String errorUrl;

    @BeforeAll
    public static void startServer() throws IOException {
        // A tiny in-process loopback HTTP server (java.base only, so no extra JPMS module):
        // serves a known 100x60 PNG at /x.png and 404s everything else, for zero-network
        // image tests over the http allow-list.
        byte[] png = pngBytes();
        serverSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        serving = true;
        Thread thread = new Thread(() -> serveLoop(png), "bbcode-image-fixture");
        thread.setDaemon(true);
        thread.start();
        int port = serverSocket.getLocalPort();
        imageUrl = "http://127.0.0.1:" + port + "/x.png";
        errorUrl = "http://127.0.0.1:" + port + "/missing.png";
    }

    @AfterAll
    public static void stopServer() throws IOException {
        serving = false;
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    private static void serveLoop(byte[] png) {
        while (serving) {
            try (Socket socket = serverSocket.accept()) {
                handleRequest(socket, png);
            } catch (IOException closed) {
                // socket closed on shutdown or a client aborted — keep serving
            }
        }
    }

    private static void handleRequest(Socket socket, byte[] png) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            // drain request headers
        }
        OutputStream out = socket.getOutputStream();
        if (requestLine != null && requestLine.contains("/x.png")) {
            out.write(("HTTP/1.0 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                    + png.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(png);
        } else {
            byte[] body = "not found".getBytes(StandardCharsets.US_ASCII);
            out.write(("HTTP/1.0 404 Not Found\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
        }
        out.flush();
    }

    private static byte[] pngBytes() throws IOException {
        // Hand-encode a 100x60 8-bit grayscale PNG using only java.base (the module does
        // not read java.desktop, so BufferedImage / ImageIO are unavailable).
        int width = 100;
        int height = 60;
        byte[] raw = new byte[height * (1 + width)]; // filter byte 0 + all-black pixels
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!deflater.finished()) {
            idat.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8); // bit depth
        ihdr.write(0); // colour type: grayscale
        ihdr.write(0); // compression
        ihdr.write(0); // filter
        ihdr.write(0); // interlace
        writeChunk(png, "IHDR", ihdr.toByteArray());
        writeChunk(png, "IDAT", idat.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

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
    public void rootHasContentVBox() {
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(new RXBBCodeView());
        VBox content = contentVBox(skin);
        assertNotNull(content, "skin must hold a .content VBox");
        assertTrue(content.isFillWidth());
    }

    @Test
    public void emptyContentShowsPlaceholder() {
        RXBBCodeView control = new RXBBCodeView();
        Label placeholder = new Label("empty");
        control.setPlaceholder(placeholder);
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(control);
        VBox content = contentVBox(skin);

        assertTrue(skin.getChildren().contains(placeholder));
        assertTrue(placeholder.getStyleClass().contains("placeholder"));
        assertTrue(placeholder.isVisible());
        assertFalse(content.isVisible(), "content is hidden while empty");

        control.setContent("[b]x[/b]");
        assertFalse(placeholder.isVisible());
        assertTrue(content.isVisible());
    }

    @Test
    public void paragraphSpacingBoundToContent() {
        RXBBCodeView control = new RXBBCodeView();
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(control);
        VBox content = contentVBox(skin);

        control.setParagraphSpacing(20);
        assertEquals(20, content.getSpacing(), 0.001);
    }

    @Test
    public void computePrefHeightUsesWrapWidth() {
        RXBBCodeView control = new RXBBCodeView();
        Label placeholder = new Label("word ".repeat(80));
        placeholder.setWrapText(true);
        control.setPlaceholder(placeholder);
        // A live scene + applyCss gives the placeholder a real font so it wraps; the
        // control's own skin proxies prefHeight to the shown node at the wrap width.
        new Scene(control);
        control.applyCss();
        control.layout();

        double narrow = control.prefHeight(80);
        double wide = control.prefHeight(4000);
        assertTrue(narrow > wide, "wrapping at a narrow width must be taller: " + narrow + " vs " + wide);
    }

    @Test
    public void replacingPlaceholderRemovesTheOldOne() {
        RXBBCodeView control = new RXBBCodeView();
        Label first = new Label("first");
        control.setPlaceholder(first);
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(control);
        assertTrue(skin.getChildren().contains(first));

        Label second = new Label("second");
        control.setPlaceholder(second);
        assertFalse(skin.getChildren().contains(first), "old placeholder must be detached");
        assertTrue(skin.getChildren().contains(second));
    }

    @Test
    public void disposeDetachesParagraphSpacingBinding() {
        RXBBCodeView control = new RXBBCodeView();
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(control);
        VBox content = contentVBox(skin);
        control.setParagraphSpacing(10);
        assertEquals(10, content.getSpacing(), 0.001);

        skin.dispose();
        // After dispose the binding is gone, so the control no longer drives the node.
        control.setParagraphSpacing(99);
        assertEquals(10, content.getSpacing(), 0.001);
    }

    @Test
    public void disposeCancelsLiveImages() {
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(new RXBBCodeView());
        // No live images yet (block rendering arrives in PR-10); dispose must be a safe
        // no-op and idempotent.
        assertDoesNotThrow(skin::dispose);
        assertDoesNotThrow(skin::dispose);
    }

    // ==================== Inline renderer (PR-08) ====================

    @Test
    public void boldItalicSynthesizedIntoSingleFont() {
        TextFlow flow = renderInline(List.of(
                new RXStyleNode(RXStyleType.BOLD, null, List.of(
                        new RXStyleNode(RXStyleType.ITALIC, null, List.of(new RXTextNode("x")))))));
        assertEquals(1, flow.getChildren().size());
        Text run = (Text) flow.getChildren().get(0);
        assertEquals("x", run.getText());
        assertEquals(Font.font(null, FontWeight.BOLD, FontPosture.ITALIC, -1), run.getFont());
    }

    @Test
    public void mergesAdjacentSameStyleRuns() {
        TextFlow merged = renderInline(List.of(new RXTextNode("a"), new RXTextNode("b")));
        assertEquals(1, merged.getChildren().size());
        assertEquals("ab", ((Text) merged.getChildren().get(0)).getText());

        TextFlow split = renderInline(List.of(
                new RXStyleNode(RXStyleType.BOLD, null, List.of(new RXTextNode("a"))),
                new RXTextNode("b")));
        assertEquals(2, split.getChildren().size());
    }

    @Test
    public void colorGoesThroughSetFillNotStyle() {
        TextFlow flow = renderInline(List.of(
                new RXStyleNode(RXStyleType.COLOR, "#ff0000", List.of(new RXTextNode("x")))));
        Text run = (Text) flow.getChildren().get(0);
        assertEquals(Color.web("#ff0000"), run.getFill());
        assertFalse(run.getStyle().contains("-fx-fill"), "colour must not be injected via setStyle");
    }

    @Test
    public void underlineStrikeApplied() {
        TextFlow flow = renderInline(List.of(
                new RXStyleNode(RXStyleType.UNDERLINE, null, List.of(
                        new RXStyleNode(RXStyleType.STRIKETHROUGH, null, List.of(new RXTextNode("x")))))));
        Text run = (Text) flow.getChildren().get(0);
        assertTrue(run.isUnderline());
        assertTrue(run.isStrikethrough());
    }

    @Test
    public void lineBreakBecomesNewline() {
        TextFlow flow = renderInline(List.of(
                new RXTextNode("a"), new RXLineBreakNode(), new RXTextNode("b")));
        StringBuilder text = new StringBuilder();
        for (Node child : flow.getChildren()) {
            text.append(((Text) child).getText());
        }
        assertEquals("a\nb", text.toString());
    }

    @Test
    public void colorInsideBoldKeepsBothAttributes() {
        TextFlow flow = renderInline(List.of(
                new RXStyleNode(RXStyleType.BOLD, null, List.of(
                        new RXStyleNode(RXStyleType.COLOR, "#00ff00", List.of(new RXTextNode("x")))))));
        Text run = (Text) flow.getChildren().get(0);
        assertEquals(Font.font(null, FontWeight.BOLD, null, -1), run.getFont());
        assertEquals(Color.web("#00ff00"), run.getFill());
    }

    @Test
    public void sizeGoesThroughFontNotStyle() {
        TextFlow flow = renderInline(List.of(
                new RXStyleNode(RXStyleType.SIZE, "20", List.of(new RXTextNode("x")))));
        Text run = (Text) flow.getChildren().get(0);
        assertEquals(20, run.getFont().getSize(), 0.001);
        assertFalse(run.getStyle().contains("-fx-font"), "size must not be injected via setStyle");
    }

    @Test
    public void explicitSizeIsCappedAtMaxFontSize() {
        RXBBCodeView control = new RXBBCodeView(); // default maxFontSize == 64
        TextFlow flow = renderInline(control, List.of(
                new RXStyleNode(RXStyleType.SIZE, "240", List.of(new RXTextNode("x")))));
        assertEquals(64, ((Text) flow.getChildren().get(0)).getFont().getSize(), 0.001);
    }

    @Test
    public void maxFontSizeZeroDisablesTheCap() {
        RXBBCodeView control = new RXBBCodeView();
        control.setMaxFontSize(0);
        TextFlow flow = renderInline(control, List.of(
                new RXStyleNode(RXStyleType.SIZE, "240", List.of(new RXTextNode("x")))));
        assertEquals(240, ((Text) flow.getChildren().get(0)).getFont().getSize(), 0.001);
    }

    @Test
    public void rawTextYieldsTextRun() {
        TextFlow flow = renderInline(List.of(new RXRawTextNode("[x]")));
        assertEquals(1, flow.getChildren().size());
        Text run = (Text) flow.getChildren().get(0);
        assertEquals("[x]", run.getText());
        assertTrue(run.getStyleClass().contains("text"));
    }

    // ==================== Link renderer (PR-09) ====================

    @Test
    public void linkRunIsTextNotHyperlink() {
        TextFlow flow = renderInline(List.of(
                new RXLinkNode("https://ex.com", RXLinkKind.URL, List.of(new RXTextNode("click")))));
        Node child = flow.getChildren().get(0);
        assertInstanceOf(Text.class, child);
        assertFalse(child instanceof Hyperlink, "links must never be Hyperlink nodes");
        assertTrue(child.getStyleClass().contains("link"));
        assertEquals(Cursor.HAND, child.getCursor());
    }

    @Test
    public void linkClickFiresEvent() {
        RXBBCodeView control = new RXBBCodeView();
        AtomicReference<RXBBCodeLinkEvent> received = new AtomicReference<>();
        control.setOnLinkActivated(received::set);
        TextFlow flow = renderInline(control, List.of(
                new RXLinkNode("https://ex.com", RXLinkKind.URL, List.of(new RXTextNode("click")))));

        flow.getChildren().get(0).fireEvent(clickEvent());
        assertEquals("https://ex.com", received.get().getHref());
        assertEquals(RXLinkKind.URL, received.get().getLinkKind());
    }

    @Test
    public void emailLinkCarriesMailtoHref() {
        RXBBCodeView control = new RXBBCodeView();
        AtomicReference<RXBBCodeLinkEvent> received = new AtomicReference<>();
        control.setOnLinkActivated(received::set);
        TextFlow flow = renderInline(control, List.of(
                new RXLinkNode("mailto:a@b.com", RXLinkKind.EMAIL, List.of(new RXTextNode("mail")))));

        flow.getChildren().get(0).fireEvent(clickEvent());
        assertEquals("mailto:a@b.com", received.get().getHref());
        assertEquals(RXLinkKind.EMAIL, received.get().getLinkKind());
    }

    @Test
    public void plainTextAdjacentToLinkIsNotClickable() {
        TextFlow flow = renderInline(List.of(
                new RXTextNode("before "),
                new RXLinkNode("https://ex.com", RXLinkKind.URL, List.of(new RXTextNode("link"))),
                new RXTextNode(" after")));
        assertEquals(3, flow.getChildren().size(), "link must not merge into surrounding plain runs");
        assertFalse(flow.getChildren().get(0).getStyleClass().contains("link"));
        assertTrue(flow.getChildren().get(1).getStyleClass().contains("link"));
        assertFalse(flow.getChildren().get(2).getStyleClass().contains("link"));
    }

    private static MouseEvent clickEvent() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    // ==================== Block renderer (PR-10) ====================

    @Test
    public void paragraphBecomesTextFlow() {
        Node node = renderBlock(new RXParagraphNode(List.of(new RXTextNode("hello"))));
        assertInstanceOf(TextFlow.class, node);
        assertTrue(node.getStyleClass().contains("paragraph"));
    }

    @Test
    public void headingSeedsSizeAndBold() {
        TextFlow h1 = (TextFlow) renderBlock(new RXHeadingNode(1, List.of(new RXTextNode("Title"))));
        assertTrue(h1.getStyleClass().contains("heading"));
        assertTrue(h1.getStyleClass().contains("heading-1"));
        Text run = (Text) h1.getChildren().get(0);
        assertEquals(Font.font(null, FontWeight.BOLD, null, 30), run.getFont());

        TextFlow h3 = (TextFlow) renderBlock(new RXHeadingNode(3, List.of(new RXTextNode("Sub"))));
        Text run3 = (Text) h3.getChildren().get(0);
        assertTrue(run3.getFont().getSize() < run.getFont().getSize(), "h3 must be smaller than h1");
    }

    @Test
    public void codeBlockPreservesWhitespace() {
        StackPane pane = (StackPane) renderBlock(new RXCodeBlockNode("  x\n[b]y[/b]", null));
        assertTrue(pane.getStyleClass().contains("code-block"));
        Text code = (Text) pane.getChildren().get(0);
        assertEquals("  x\n[b]y[/b]", code.getText());
    }

    @Test
    public void unorderedListHasMarkers() {
        VBox list = (VBox) renderBlock(new RXListNode(RXListKind.UNORDERED, List.of(
                new RXListItemNode(List.of(new RXParagraphNode(List.of(new RXTextNode("a"))))),
                new RXListItemNode(List.of(new RXParagraphNode(List.of(new RXTextNode("b"))))))));
        assertTrue(list.getStyleClass().contains("list"));
        assertEquals(2, list.getChildren().size());
        HBox item = (HBox) list.getChildren().get(0);
        assertTrue(item.getStyleClass().contains("item"));
        assertTrue(item.getChildren().get(0).getStyleClass().contains("marker"));
    }

    @Test
    public void orderedListIsGridPane() {
        Node list = renderBlock(new RXListNode(RXListKind.ORDERED, List.of(
                new RXListItemNode(List.of(new RXParagraphNode(List.of(new RXTextNode("a"))))))));
        assertInstanceOf(GridPane.class, list);
        assertTrue(list.getStyleClass().contains("list"));
    }

    @Test
    public void hrIsRegion() {
        Node hr = renderBlock(new RXHorizontalRuleNode());
        assertEquals(Region.class, hr.getClass());
        assertTrue(hr.getStyleClass().contains("hr"));
    }

    @Test
    public void rebuildAddsOneNodePerBlock() {
        RXBBCodeView control = new RXBBCodeView();
        RXBBCodeViewSkin skin = new RXBBCodeViewSkin(control);
        VBox content = contentVBox(skin);

        control.setContent("a[hr]b");
        assertEquals(3, content.getChildren().size(), "one node per top-level block");
    }

    @Test
    public void deeplyNestedContentRendersWithoutStackOverflow() {
        // The parse-time depth guard caps nesting at maxNestingDepth (512), so the render's
        // recursive block/inline visitors cannot overflow the stack even on pathological
        // input. Build the skin (which rebuilds eagerly) and assert it does not throw.
        String deepInline = "[b]".repeat(5000) + "x" + "[/b]".repeat(5000);
        String deepBlock = "[quote]".repeat(5000) + "x" + "[/quote]".repeat(5000);
        assertDoesNotThrow(() -> {
            RXBBCodeView inline = new RXBBCodeView(deepInline);
            new RXBBCodeViewSkin(inline);
            RXBBCodeView block = new RXBBCodeView(deepBlock);
            new RXBBCodeViewSkin(block);
        });
    }

    @Test
    public void listItemWrapsAtFiniteWidth() {
        RXBBCodeView control = new RXBBCodeView("[list][*]" + "word ".repeat(80) + "[/list]");
        new Scene(control);
        control.applyCss();
        double width = 140;
        control.resize(width, control.prefHeight(width));
        control.layout();

        TextFlow body = widestTextFlow(control);
        assertNotNull(body, "list item body TextFlow not found");
        assertTrue(body.getWidth() <= width + 1,
                "list body must wrap within the finite width, was " + body.getWidth());
        assertTrue(body.getHeight() > 30, "wrapped long text should span multiple lines");
    }

    private static TextFlow widestTextFlow(Parent root) {
        TextFlow best = null;
        for (Node node : root.getChildrenUnmodifiable()) {
            TextFlow candidate = null;
            if (node instanceof TextFlow flow) {
                candidate = flow;
            } else if (node instanceof Parent parent) {
                candidate = widestTextFlow(parent);
            }
            if (candidate != null && (best == null || textLength(candidate) > textLength(best))) {
                best = candidate;
            }
        }
        return best;
    }

    private static int textLength(TextFlow flow) {
        int length = 0;
        for (Node child : flow.getChildren()) {
            if (child instanceof Text text) {
                length += text.getText().length();
            }
        }
        return length;
    }

    // ==================== Image renderer (PR-12) ====================

    @Test
    public void imageBecomesImageView() throws Exception {
        Node child = getOnFx(() -> renderInline(List.of(image(0, 0))).getChildren().get(0));
        assertInstanceOf(ImageView.class, child);
        assertTrue(child.getStyleClass().contains("image"));
        assertTrue(((ImageView) child).isPreserveRatio());
    }

    @Test
    public void imageLoadsAtNaturalSizeNotUpscaled() throws Exception {
        ImageView view = (ImageView) getOnFx(() ->
                renderInline(List.of(image(0, 0))).getChildren().get(0));
        awaitLoaded(view.getImage());
        Image loaded = view.getImage();
        // Loaded like a bare ImageView: no requested decode size, so the 100x60 fixture
        // stays 100x60 and is never upscaled to fill a decode box.
        assertEquals(0, loaded.getRequestedWidth(), 0.001);
        assertEquals(100, getOnFx(loaded::getWidth), 0.5);
        assertEquals(60, getOnFx(loaded::getHeight), 0.5);
    }

    @Test
    public void loadImagesFalseSkipsNetwork() throws Exception {
        RXBBCodeView control = new RXBBCodeView();
        control.setPolicy(new RXBBCodePolicy(RXBBCodeUrlPolicy.defaults(),
                new RXBBCodeImagePolicy(Set.of("http", "https"), false)));
        Node child = getOnFx(() -> renderInline(control, List.of(image(0, 0))).getChildren().get(0));
        assertTrue(child.getStyleClass().contains("image-placeholder"));
        assertFalse(child instanceof ImageView);
    }

    @Test
    public void imageMaxWidthClampsFit() throws Exception {
        RXBBCodeView clamped = new RXBBCodeView();
        clamped.setImageMaxWidth(50);
        ImageView clampedView = (ImageView) getOnFx(() ->
                renderInline(clamped, List.of(image(0, 0))).getChildren().get(0));
        awaitLoaded(clampedView.getImage());
        assertEquals(50, getOnFx(clampedView::getFitWidth), 0.5);

        RXBBCodeView unbounded = new RXBBCodeView();
        unbounded.setImageMaxWidth(0);
        ImageView naturalView = (ImageView) getOnFx(() ->
                renderInline(unbounded, List.of(image(0, 0))).getChildren().get(0));
        awaitLoaded(naturalView.getImage());
        // maxW <= 0 means "no upper bound": the fit uses the full natural source width
        // uncapped — never clamped to the 50px limit and never collapsed to 0.
        double naturalWidth = getOnFx(() -> naturalView.getImage().getWidth());
        assertEquals(naturalWidth, getOnFx(naturalView::getFitWidth), 1.0);
        assertTrue(naturalWidth > 50, "fixture natural width must exceed the clamp for a real test");
    }

    @Test
    public void explicitSizeDrivesFit() throws Exception {
        RXBBCodeView control = new RXBBCodeView();
        ImageView view = (ImageView) getOnFx(() ->
                renderInline(control, List.of(image(30, 0))).getChildren().get(0));
        awaitLoaded(view.getImage());
        assertEquals(30, getOnFx(view::getFitWidth), 0.5);
    }

    @Test
    public void runtimeErrorSwapsToPlaceholder() throws Exception {
        RXBBCodeView control = new RXBBCodeView();
        Image[] holder = new Image[1];
        TextFlow flow = getOnFx(() -> {
            TextFlow rendered = renderInline(control, List.of(
                    new RXImageNode(errorUrl, "broken", 0, 0)));
            holder[0] = ((ImageView) rendered.getChildren().get(0)).getImage();
            return rendered;
        });
        awaitLoaded(holder[0]);
        Node child = getOnFx(() -> flow.getChildren().get(0));
        assertTrue(child.getStyleClass().contains("image-placeholder"));
        assertFalse(child instanceof ImageView);
    }

    @Test
    public void rebuildDropsPreviousImage() throws Exception {
        RXBBCodeView control = new RXBBCodeView("[img]" + imageUrl + "[/img]");
        RXBBCodeViewSkin skin = getOnFx(() -> new RXBBCodeViewSkin(control));
        assertEquals(1, (int) getOnFx(() -> countImageViews(skin)));

        runOnFx(() -> control.setContent("plain text"));
        assertEquals(0, (int) getOnFx(() -> countImageViews(skin)), "old image node dropped");

        runOnFx(() -> control.setContent("[img]" + imageUrl + "[/img]"));
        assertEquals(1, (int) getOnFx(() -> countImageViews(skin)), "no accumulation across rebuilds");
    }

    @Test
    public void malformedImageUrlDegradesToPlaceholderWithoutThrowing() throws Exception {
        RXBBCodeView control = new RXBBCodeView();
        // A parser-valid scheme with a malformed authority makes new Image(...) throw; the
        // render must degrade to a placeholder, never crash.
        Node child = assertDoesNotThrow(() -> getOnFx(() ->
                renderInline(control, List.of(new RXImageNode("http://host:abc", "alt", 0, 0)))
                        .getChildren().get(0)));
        assertTrue(child.getStyleClass().contains("image-placeholder"));
        assertFalse(child instanceof ImageView);
    }

    private static RXImageNode image(double width, double height) {
        return new RXImageNode(imageUrl, "alt", width, height);
    }

    private static int countImageViews(RXBBCodeViewSkin skin) {
        int count = 0;
        for (Node node : skin.getChildren()) {
            count += countImageViewsIn(node);
        }
        return count;
    }

    private static int countImageViewsIn(Node node) {
        int count = node instanceof ImageView ? 1 : 0;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countImageViewsIn(child);
            }
        }
        return count;
    }

    private static <T> T getOnFx(Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(supplier.get());
            } catch (RuntimeException ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        getOnFx(() -> {
            action.run();
            return null;
        });
    }

    private static void awaitLoaded(Image image) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            if (image.getProgress() >= 1.0 || image.isError()) {
                latch.countDown();
                return;
            }
            image.progressProperty().addListener((observable, old, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    latch.countDown();
                }
            });
            image.errorProperty().addListener((observable, old, isError) -> {
                if (Boolean.TRUE.equals(isError)) {
                    latch.countDown();
                }
            });
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("image did not settle");
        }
    }

    // ==================== Table renderer (PR-11) ====================

    @Test
    public void tableIsGridPaneWithColumnConstraints() {
        GridPane table = (GridPane) renderBlock(new RXTableNode(List.of(
                new RXTableRowNode(List.of(cell(false, "a"), cell(false, "b"))))));
        assertTrue(table.getStyleClass().contains("table"));
        assertTrue(table.getColumnConstraints().size() >= 2);
    }

    @Test
    public void tableHeaderCellHasHeaderClass() {
        GridPane table = (GridPane) renderBlock(new RXTableNode(List.of(
                new RXTableRowNode(List.of(cell(true, "H"))))));
        Node cellNode = table.getChildren().get(0);
        assertTrue(cellNode.getStyleClass().contains("cell"));
        assertTrue(cellNode.getStyleClass().contains("header"));
    }

    @Test
    public void tableCellWrapsAtFiniteWidth() {
        GridPane table = (GridPane) renderBlock(new RXTableNode(List.of(
                new RXTableRowNode(List.of(cell(false, "x"), cell(false, "word ".repeat(80)))))));
        new Scene(table);
        table.applyCss();
        double width = 200;
        table.resize(width, table.prefHeight(width));
        table.layout();

        Node longCell = table.getChildren().get(1);
        assertTrue(longCell.getBoundsInParent().getMaxX() <= width + 1,
                "cell must not overflow the table width, maxX=" + longCell.getBoundsInParent().getMaxX());
        assertTrue(((Region) longCell).getHeight() > 30, "long cell text should wrap to multiple lines");
    }

    private static RXTableCellNode cell(boolean header, String text) {
        return new RXTableCellNode(header, List.of(new RXParagraphNode(List.of(new RXTextNode(text)))));
    }

    // ==================== Spoiler renderer (PR-13) ====================

    @Test
    public void spoilerContentInitiallyCollapsed() {
        VBox spoiler = (VBox) renderBlock(new RXSpoilerNode("Details",
                List.of(new RXParagraphNode(List.of(new RXTextNode("secret"))))));
        assertTrue(spoiler.getStyleClass().contains("spoiler"));
        VBox body = spoilerContent(spoiler);
        assertTrue(body.getStyleClass().contains("content"));
        assertFalse(body.isVisible(), "spoiler body starts hidden");
        assertFalse(body.isManaged(), "hidden body must not occupy layout");
    }

    @Test
    public void spoilerHeaderClickTogglesRevealed() {
        PseudoClass revealed = PseudoClass.getPseudoClass("revealed");
        VBox spoiler = (VBox) renderBlock(new RXSpoilerNode(null,
                List.of(new RXParagraphNode(List.of(new RXTextNode("secret"))))));
        Node header = spoilerHeader(spoiler);
        VBox body = spoilerContent(spoiler);

        header.fireEvent(clickEvent());
        assertTrue(body.isVisible() && body.isManaged(), "click reveals the body");
        assertTrue(spoiler.getPseudoClassStates().contains(revealed), ":revealed set when open");

        header.fireEvent(clickEvent());
        assertFalse(body.isVisible() || body.isManaged(), "second click collapses the body");
        assertFalse(spoiler.getPseudoClassStates().contains(revealed), ":revealed cleared when closed");
    }

    @Test
    public void spoilerUsesLabelElseDefault() {
        VBox labelled = (VBox) renderBlock(new RXSpoilerNode("Answer", List.of()));
        assertEquals("Answer", spoilerHeaderText(labelled));

        VBox defaulted = (VBox) renderBlock(new RXSpoilerNode(null, List.of()));
        assertEquals("Spoiler", spoilerHeaderText(defaulted));
    }

    // ==================== Background renderer ====================

    @Test
    public void bgColorAppliesTypedBackgroundNotStyle() {
        VBox box = (VBox) renderBlock(new RXBackgroundNode("#eeeeee",
                List.of(new RXParagraphNode(List.of(new RXTextNode("hi"))))));
        assertTrue(box.getStyleClass().contains("background"));
        assertNotNull(box.getBackground());
        assertEquals(Color.web("#eeeeee"), box.getBackground().getFills().get(0).getFill());
        assertTrue(box.getStyle().isEmpty(), "colour must not be injected via setStyle");
    }

    @Test
    public void bgColorNullLeavesNoBackground() {
        VBox box = (VBox) renderBlock(new RXBackgroundNode(null,
                List.of(new RXParagraphNode(List.of(new RXTextNode("hi"))))));
        assertNull(box.getBackground());
    }

    private static Node spoilerHeader(VBox spoiler) {
        return spoiler.getChildren().get(0);
    }

    private static VBox spoilerContent(VBox spoiler) {
        return (VBox) spoiler.getChildren().get(1);
    }

    private static String spoilerHeaderText(VBox spoiler) {
        HBox header = (HBox) spoilerHeader(spoiler);
        return ((Text) header.getChildren().get(0)).getText();
    }

    // ==================== CSS / tokens (PR-14) ====================

    @Test
    public void bbcodeChildTokensResolve() throws Exception {
        // The baseline theme guard instantiates an empty control, so it never exercises
        // the child selectors (.link/.quote/.code-block). This resolves them against the
        // real user-agent stylesheet: a misspelled -rx-* token would drop the property
        // and surface here as a null fill/border/background.
        Object[] resolved = getOnFx(() -> {
            RXBBCodeView control = new RXBBCodeView(
                    "[url=https://example.com/]click[/url]\n[quote]quoted[/quote]\n[code]mono[/code]");
            new Scene(control);
            control.applyCss();
            control.layout();

            Text link = (Text) findNode(control, node -> node.getStyleClass().contains("link"));
            Region quote = (Region) findNode(control, node -> node.getStyleClass().contains("quote"));
            Region codeBlock = (Region) findNode(control, node -> node.getStyleClass().contains("code-block"));

            Paint linkFill = link == null ? null : link.getFill();
            boolean linkUnderline = link != null && link.isUnderline();
            Paint quoteBorder = quote == null || quote.getBorder() == null
                    || quote.getBorder().getStrokes().isEmpty()
                    ? null : quote.getBorder().getStrokes().get(0).getLeftStroke();
            Paint codeBackground = codeBlock == null || codeBlock.getBackground() == null
                    || codeBlock.getBackground().getFills().isEmpty()
                    ? null : codeBlock.getBackground().getFills().get(0).getFill();
            return new Object[]{linkFill, quoteBorder, codeBackground, linkUnderline};
        });

        assertNotNull(resolved[0], "link fill must resolve");
        assertEquals(Color.web("#616dff"), resolved[0], "link fill is the -rx-primary token");
        assertNotNull(resolved[1], "quote border colour must resolve (-rx-outline)");
        assertNotNull(resolved[2], "code-block background must resolve (-rx-surface-variant)");
        // The renderer leaves underline unset on a plain link so the CSS rule can apply;
        // if a direct setter ever pins it USER again, this catches the dead-rule regression.
        assertEquals(Boolean.TRUE, resolved[3], "the .link CSS rule must underline the link");
    }

    private static Node findNode(Parent root, Predicate<Node> match) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (match.test(child)) {
                return child;
            }
            if (child instanceof Parent parent) {
                Node found = findNode(parent, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Node renderBlock(RXBBBlockNode block) {
        RenderContext ctx = new RenderContext(new RXBBCodeView(), new ArrayList<>());
        return block.accept(new BBCodeBlockRenderer(ctx));
    }

    private static TextFlow renderInline(List<RXBBInlineNode> nodes) {
        return renderInline(new RXBBCodeView(), nodes);
    }

    private static TextFlow renderInline(RXBBCodeView control, List<RXBBInlineNode> nodes) {
        TextFlow flow = new TextFlow();
        RenderContext ctx = new RenderContext(control, new ArrayList<>());
        new BBCodeInlineRenderer(flow, ctx).render(nodes);
        return flow;
    }

    private static VBox contentVBox(RXBBCodeViewSkin skin) {
        for (Node child : skin.getChildren()) {
            if (child instanceof VBox box && box.getStyleClass().contains("content")) {
                return box;
            }
        }
        return null;
    }
}
