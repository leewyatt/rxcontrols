package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveCol;
import javafx.fxml.FXML;

/**
 * Controller that applies responsive specs for {@link RXResponsiveRowFXMLDemo}.
 */
public class RXResponsiveRowFXMLDemoController {

    @FXML
    private RXResponsiveCol heroCol;

    @FXML
    private RXResponsiveCol statusCol;

    @FXML
    private RXResponsiveCol slaCol;

    @FXML
    private RXResponsiveCol responseCol;

    @FXML
    private RXResponsiveCol escalationCol;

    @FXML
    private RXResponsiveCol calloutCol;

    @FXML
    private void initialize() {
        setSpecs(heroCol, 24, 24, 16, 14);
        setSpecs(statusCol, 24, 24, 8, 10);
        setSpecs(slaCol, 24, 12, 12, 8);
        setSpecs(responseCol, 24, 12, 12, 8);
        setSpecs(escalationCol, 24, 12, 12, 8);

        calloutCol.setXs(RXColSpec.of(24));
        calloutCol.setSm(RXColSpec.of(24));
        calloutCol.setMd(RXColSpec.of(20, 2));
        calloutCol.setLg(RXColSpec.of(18, 3));
    }

    private void setSpecs(RXResponsiveCol col, int xs, int sm, int md, int lg) {
        col.setXs(RXColSpec.of(xs));
        col.setSm(RXColSpec.of(sm));
        col.setMd(RXColSpec.of(md));
        col.setLg(RXColSpec.of(lg));
    }
}
