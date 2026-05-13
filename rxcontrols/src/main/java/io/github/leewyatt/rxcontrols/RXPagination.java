package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXPaginationSkin;
import javafx.scene.control.Pagination;
import javafx.scene.control.Skin;

/**
 *
 * 分页组件 带输入页码直接 跳转的按钮
 */
public class RXPagination extends Pagination {
    private static final String DEFAULT_STYLE_CLASS = "rx-pagination";

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXPaginationSkin(this);
    }

    public RXPagination(int pageCount, int pageIndex) {
       super(pageCount,pageIndex);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public RXPagination(int pageCount) {
        this(pageCount, 0);
    }
    public RXPagination(){
       super();
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

}
