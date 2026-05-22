package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXNumberField;
import javafx.fxml.FXML;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Controller for the number-field showcase page. FXML cannot coerce
 * {@link BigDecimal} or {@link NumberFormat} from attribute strings, so the
 * initial values and formats are assigned here.
 */
public class PaneNumberFieldController {

    @FXML
    private RXNumberField numberField;

    @FXML
    private RXIntegerField integerField;

    @FXML
    private RXFormattedNumberField formattedField;

    @FXML
    private RXFormattedNumberField groupingField;

    @FXML
    private RXFormattedNumberField currencyField;

    @FXML
    private RXFormattedNumberField percentField;

    @FXML
    private RXFormattedNumberField leftSlotField;

    @FXML
    private RXFormattedNumberField rightSlotField;

    @FXML
    private RXFormattedNumberField dualSlotField;

    @FXML
    void initialize() {
        numberField.setValue(new BigDecimal("3.14159"));

        integerField.setValue(new BigDecimal("2048"));

        formattedField.setValue(new BigDecimal("1234567.89"));

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
