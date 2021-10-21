/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.leewyatt.rxcontrols.skins;

import com.leewyatt.rxcontrols.controls.RXPagination;
import com.sun.javafx.scene.control.skin.PaginationSkin;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
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