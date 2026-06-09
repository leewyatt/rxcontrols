package io.github.leewyatt.rxcontrols.layout;

import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FXML smoke tests for {@link RXCol} responsive specs.
 */
public class RXColFxmlTest {

    /**
     * Verifies FXML attributes use RXColSpec.valueOf.
     *
     * @throws IOException if the FXML cannot be loaded
     */
    @Test
    public void fxmlAttributesSetResponsiveSpecs() throws IOException {
        RXCol col = loadCol("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import io.github.leewyatt.rxcontrols.layout.RXCol?>
                <RXCol xmlns="http://javafx.com/javafx/17"
                                 xs="24" md="12,2" lg="8,0,order=1"/>
                """);

        assertSpec(col.getXs(), 24, null, null, null);
        assertSpec(col.getMd(), 12, 2, null, null);
        assertSpec(col.getLg(), 8, 0, 1, null);
    }

    /**
     * Verifies FXML property elements use the @NamedArg constructor.
     *
     * @throws IOException if the FXML cannot be loaded
     */
    @Test
    public void fxmlElementsSetResponsiveSpecs() throws IOException {
        RXCol col = loadCol("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import io.github.leewyatt.rxcontrols.layout.RXColSpec?>
                <?import io.github.leewyatt.rxcontrols.layout.RXCol?>
                <RXCol xmlns="http://javafx.com/javafx/17">
                    <xs>
                        <RXColSpec span="24"/>
                    </xs>
                    <md>
                        <RXColSpec span="12" offset="2"/>
                    </md>
                </RXCol>
                """);

        assertSpec(col.getXs(), 24, null, null, null);
        assertSpec(col.getMd(), 12, 2, null, null);
    }

    private RXCol loadCol(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        ByteArrayInputStream input =
                new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8));
        return loader.load(input);
    }

    private void assertSpec(RXColSpec spec, Integer span, Integer offset,
                            Integer order, Boolean hidden) {
        assertEquals(span, spec.getSpan());
        assertEquals(offset, spec.getOffset());
        assertEquals(order, spec.getOrder());
        assertEquals(hidden, spec.getHidden());
    }
}
