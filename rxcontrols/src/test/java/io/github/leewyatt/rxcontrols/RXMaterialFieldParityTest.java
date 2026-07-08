package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the deliberately mirrored API surface of the two Material fields (the
 * duplication itself is the sanctioned design, mirroring TextField / TextArea):
 * the {@code -rx-*} CSS metadata must stay in sync — same names and initial
 * values, the password field may only add {@code -rx-echo-char} — and the
 * shared public default constants must stay equal, so neither sibling can
 * drift silently when one of them is edited alone.
 */
public class RXMaterialFieldParityTest {

    /**
     * Starts the toolkit: the controls' class initialization (via the Control
     * hierarchy) requires it even for static CSS-metadata access.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    @Test
    public void materialCssMetadataStaysInSync() {
        Map<String, CssMetaData<? extends Styleable, ?>> text =
                rxOnly(RXMaterialTextField.getClassCssMetaData());
        Map<String, CssMetaData<? extends Styleable, ?>> pwd =
                rxOnly(RXMaterialPasswordField.getClassCssMetaData());

        Set<String> extra = new HashSet<>(pwd.keySet());
        extra.removeAll(text.keySet());
        assertEquals(Set.of("-rx-echo-char"), extra,
                "the password field may only add -rx-echo-char on top of the text field");
        assertTrue(pwd.keySet().containsAll(text.keySet()),
                "every text-field -rx-* property must exist on the password field");
        for (Map.Entry<String, CssMetaData<? extends Styleable, ?>> e : text.entrySet()) {
            assertEquals(initial(e.getValue()), initial(pwd.get(e.getKey())),
                    "initial value drift on " + e.getKey());
            assertEquals(e.getValue().getConverter().getClass(),
                    pwd.get(e.getKey()).getConverter().getClass(),
                    "converter drift on " + e.getKey());
        }
        assertEquals(RXMaterialPasswordField.DEFAULT_ECHO_CHAR, initial(pwd.get("-rx-echo-char")),
                "-rx-echo-char metadata must default to DEFAULT_ECHO_CHAR");
    }

    @Test
    public void sharedDefaultConstantsStayEqual() {
        assertEquals(RXMaterialTextField.DEFAULT_ANIMATION_DURATION,
                RXMaterialPasswordField.DEFAULT_ANIMATION_DURATION,
                "the documented null-fallback duration must match across the family");
        assertEquals(RXMaterialTextField.DEFAULT_LABEL_FLOAT_SCALE,
                RXMaterialPasswordField.DEFAULT_LABEL_FLOAT_SCALE, 0.0,
                "the documented float-scale default must match across the family");
        assertEquals(RXPasswordField.DEFAULT_ECHO_CHAR, RXMaterialPasswordField.DEFAULT_ECHO_CHAR,
                "the mask-character default must match across the password family");
    }

    private static Map<String, CssMetaData<? extends Styleable, ?>> rxOnly(
            List<CssMetaData<? extends Styleable, ?>> metadata) {
        Map<String, CssMetaData<? extends Styleable, ?>> byName = new HashMap<>();
        for (CssMetaData<? extends Styleable, ?> m : metadata) {
            if (m.getProperty().startsWith("-rx-")) {
                byName.put(m.getProperty(), m);
            }
        }
        return byName;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object initial(CssMetaData<? extends Styleable, ?> metadata) {
        return ((CssMetaData) metadata).getInitialValue(null);
    }
}
