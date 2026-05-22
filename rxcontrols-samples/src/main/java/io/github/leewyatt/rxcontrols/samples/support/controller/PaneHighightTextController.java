package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXHighlightText;
import io.github.leewyatt.rxcontrols.RXHighlightText.MatchRules;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class PaneHighightTextController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private TextField tfKeywords;

    @FXML
    private Button btnClear;

    @FXML
    private ListView<RXHighlightText> listEmail;
    @FXML
    private ComboBox<MatchRules> rulesComboBox;

    private ObservableList<RXHighlightText> items = FXCollections.observableArrayList();

    @FXML
    void initialize() {
        tfKeywords.setText("[0-9]+");
        rulesComboBox.setItems(FXCollections.observableArrayList(MatchRules.values()));
        rulesComboBox.getSelectionModel().select(0);

        String[] infos = {
                "sky678hawabcak@wyx.com",
                "wdafsABC132t@qqxz.com",
                "star1321udy@xyz.com",
                "AbC1fa321afis@abc.com",
                "135931213112",
                "13232100453",
                "12322113533",
                "132664588",
                "97451835"};

        for (String info : infos) {
            RXHighlightText highlightText = new RXHighlightText(info);
            highlightText.keywordsProperty().bind(tfKeywords.textProperty());
            highlightText.matchRulesProperty().bind(rulesComboBox.valueProperty());
            items.add(highlightText);
        }
        listEmail.setItems(items);
        rulesComboBox.getSelectionModel().selectedItemProperty().addListener(listener);
        tfKeywords.textProperty().addListener(listener);
        btnClear.setOnAction(event -> tfKeywords.setText(""));
    }

    private ChangeListener listener = (ob, ov, nv) -> {
        if (tfKeywords.getText().isEmpty()) {
            listEmail.setItems(items);
            return;
        }
        FilteredList<RXHighlightText> mySubList = items.filtered(
                item ->item.isMatch()
        );
        listEmail.setItems(mySubList);
    };
}
