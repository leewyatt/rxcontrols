package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXDecimalField;
import io.github.leewyatt.rxcontrols.RXDoubleField;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import javafx.fxml.FXML;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Controller for the number-field showcase page. Integer / Double values and
 * bounds land directly from FXML attribute strings; {@link NumberFormat}
 * instances cannot be expressed in FXML, so the formatted fields are
 * configured here.
 */
public class PaneNumberFieldController {

    @FXML
    private RXIntegerField integerField;

    @FXML
    private RXDoubleField doubleField;

    @FXML
    private RXDecimalField decimalField;

    @FXML
    private RXDecimalField groupingField;

    @FXML
    private RXDecimalField currencyField;

    @FXML
    private RXDecimalField percentField;

    @FXML
    private RXDecimalField leftSlotField;

    @FXML
    private RXDecimalField rightSlotField;

    @FXML
    private RXDecimalField dualSlotField;

    @FXML
    void initialize() {
        groupingField.setNumberFormat(NumberFormat.getNumberInstance(Locale.US));
        groupingField.setValue(new BigDecimal("1234567.89"));

        currencyField.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
        currencyField.setValue(new BigDecimal("8888.50"));

        NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
        percent.setMaximumFractionDigits(1);
        percentField.setNumberFormat(percent);
        percentField.setValue(new BigDecimal("0.875"));

        leftSlotField.setValue(new BigDecimal("1500"));
        rightSlotField.setValue(new BigDecimal("68.5"));
        dualSlotField.setValue(new BigDecimal("25"));
    }
}
