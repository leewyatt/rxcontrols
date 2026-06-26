package io.github.leewyatt.rxcontrols.samples.test;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.stage.Stage;

import java.util.stream.IntStream;

/**
 * Simple test window showing a multiple-selection {@link ListView} with items
 * indexed from {@code 0} to {@code 10000}.
 */
public class MultiSelectListViewDemo extends Application {

    private static final int FIRST_ITEM_INDEX = 0;
    private static final int LAST_ITEM_INDEX = 10_000;

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        ListView<String> listView = new ListView<>(createItems());
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        primaryStage.setScene(new Scene(listView, 320.0, 640.0));
        primaryStage.setTitle("MultiSelectListViewDemo");
        primaryStage.show();
    }

    private ObservableList<String> createItems() {
        return FXCollections.observableArrayList(
                IntStream.rangeClosed(FIRST_ITEM_INDEX, LAST_ITEM_INDEX)
                        .mapToObj(Integer::toString)
                        .toList());
    }
}
