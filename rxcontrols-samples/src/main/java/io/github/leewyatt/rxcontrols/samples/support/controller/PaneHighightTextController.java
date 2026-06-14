package io.github.leewyatt.rxcontrols.samples.support.controller;

import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXHighlightTextView.MatchRules;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.List;

public class PaneHighightTextController {

    @FXML
    private TextField tfKeywords;

    @FXML
    private Button btnClear;

    @FXML
    private ListView<RXHighlightTextView> listEmail;

    @FXML
    private ComboBox<MatchRules> rulesComboBox;

    private final ObservableList<RXHighlightTextView> items = FXCollections.observableArrayList();

    @FXML
    void initialize() {
        rulesComboBox.setItems(FXCollections.observableArrayList(MatchRules.values()));
        rulesComboBox.getSelectionModel().select(MatchRules.REGEX);

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
            RXHighlightTextView item = new RXHighlightTextView(info);
            items.add(item);
        }
        listEmail.setItems(items);

        tfKeywords.textProperty().addListener((obs, old, value) -> refresh());
        rulesComboBox.valueProperty().addListener((obs, old, value) -> refresh());
        btnClear.setOnAction(event -> tfKeywords.clear());

        tfKeywords.setText("[0-9]+");
    }

    private void refresh() {
        String text = tfKeywords.getText();
        List<String> words = (text == null || text.isBlank())
                ? List.of()
                : Arrays.asList(text.trim().split("\\s+"));
        MatchRules rules = rulesComboBox.getValue();

        // Apply inputs first so each item's matched state is recomputed synchronously,
        // then filter on the fresh isMatched() — avoids depending on listener order.
        for (RXHighlightTextView item : items) {
            item.getKeywords().setAll(words);
            if (rules != null) {
                item.setMatchRules(rules);
            }
        }

        if (words.isEmpty()) {
            listEmail.setItems(items);
        } else {
            listEmail.setItems(items.filtered(RXHighlightTextView::isMatched));
        }
    }
}
