package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPagination;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.PaginationSkin;
import javafx.scene.layout.HBox;

/**
 *
 *
 */
public class RXPaginationSkin extends PaginationSkin {

    private HBox controlBox;
    private Button btnNextPage;
    private Button btnOK;
    private TextField pageTextField;
    private Label labelGo;
    private Label labelPage;
    private Pagination pagination;

    private final ListChangeListener listChangeListener = change -> {
        while (change.next()) {
            //当添加了系统最后一个按钮(向后翻页按钮) ; 就可以添加用于跳转的标签,文本框和按钮
            if (change.wasAdded() && !change.wasRemoved()
                    && change.getAddedSize() == 1
                    && change.getAddedSubList().get(0) == btnNextPage) {
                addExtendNodes();
            }
        }
    };

    private void init() {
        pagination = getSkinnable();
        controlBox = (HBox) pagination.lookup(".control-box");
        btnNextPage = (Button) controlBox.getChildren().get(controlBox.getChildren().size() - 1);
        labelGo = new Label("前往");
        labelGo.getStyleClass().add("label-go");
        pageTextField = new TextField();
        pageTextField.getStyleClass().add("page-text-field");
        pageTextField.setPrefWidth(40);
        labelPage = new Label("页");
        labelPage.getStyleClass().add("label-page");
        btnOK = new Button("确定");
        btnOK.getStyleClass().add("ok-button");
        pageTextField.setOnAction(e -> gotoPage());
        btnOK.setOnAction(e -> gotoPage());
        addExtendNodes();
        controlBox.getChildren().addListener(listChangeListener);
    }

    //转到指定页面
    private void gotoPage() {
        int pageNum;
        try {
            pageNum = Integer.parseInt(pageTextField.getText());
            if (pageNum < 1) {
                pageNum = 1;
            } else if (pageNum > pagination.getPageCount()) {
                pageNum = pagination.getPageCount();
            }
        } catch (Exception e1) {//如果转换异常,就跳转到第一页
            pageNum = 1;
        }
        pageTextField.setText(String.valueOf(pageNum));
        pageTextField.selectAll();
        pagination.setCurrentPageIndex(pageNum - 1);
    }

    protected void addExtendNodes() {
        //防止重复添加
        if (controlBox.getChildren().contains(labelGo)) {
            return;
        }
        controlBox.getChildren().addAll(labelGo, pageTextField, labelPage, btnOK);
    }

    public RXPaginationSkin(RXPagination pagination) {
        super(pagination);
        init();
    }

    @Override
    public void dispose() {
        controlBox.getChildren().removeListener(listChangeListener);
        getChildren().clear();
        super.dispose();
    }
}