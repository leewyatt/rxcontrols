package io.github.leewyatt.rxcontrols.samples.controller;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class NewFieldController {

    @FXML
    private RXTextField searchField;

    @FXML
    private RXTextField combinedField;

    @FXML
    void clear(ActionEvent event) {
        combinedField.clear();
    }

    @FXML
    void submit(ActionEvent event) {
        System.out.println("Submitted: " + combinedField.getText());
    }
}
