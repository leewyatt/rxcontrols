package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/**
 * Regression demo for the ControlsFX Issue #705 family of layout bugs in
 * {@code RXFieldBaseSkin}. Three text fields share the same exaggerated
 * padding ({@code 24 16}) and a red border so the padding region is visible
 * (yellow). After the fix, all three rows should look vertically aligned.
 *
 * 期望(修复后):
 *   [1] 原生 TextField           — 文字距上下红边各约 24px 黄色留白
 *   [2] RXTextField 无 left/right — 视觉与 [1] 一致
 *   [3] RXTextField 有 left/right — 蓝/粉 wrapper 上下各让出 24px 黄色 padding 带,
 *                                   文字 Y 位置与 [1]/[2] 持平
 *
 * 回归信号:若 [3] 行的蓝/粉块吃掉了上下黄色 padding 带、文字明显贴边,
 *           说明 RXFieldBaseSkin.layoutChildren 的 padding 修复已经退化。
 */
public class RXTextFieldPaddingBugDemo extends Application {

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(14);
        root.setStyle("-fx-padding: 24; -fx-background-color: #ececec;");

        Label ref1Label = new Label("[1] javafx.scene.control.TextField (参考)");
        TextField ref = new TextField("文字距上下红边应有 24px 留白");
        ref.getStyleClass().add("reference");
        ref.setPrefWidth(460);

        Label ref2Label = new Label("[2] RXTextField — 无 left/right(super 默认路径)");
        RXTextField noSides = new RXTextField("无 left/right,应与 [1] 一致");
        noSides.getStyleClass().add("reference");
        noSides.setPrefWidth(460);

        Label bugLabel = new Label("[3] RXTextField — 有 left+right(走我们的 layoutChildren)");
        RXTextField buggy = new RXTextField("文字 Y 应与上面两行持平");
        buggy.getStyleClass().add("bug-demo");
        buggy.setPrefWidth(460);
        buggy.setLeft(iconNode());
        Button rightBtn = new Button("✕");
        rightBtn.setFocusTraversable(false);
        buggy.setRight(rightBtn);

        Label hint = new Label(
                "看哪里(修复后):\n"
                + "  • 红色 = 控件外边框, 黄色 = 24px padding 区, 蓝/粉色 = wrapper 实际占据范围\n"
                + "  • [1] [2] [3] 文字 Y 视觉位置应当持平,三行都有清晰的上下 24px 黄色 padding\n"
                + "  • [3] 蓝色 left-wrapper、粉色 right-wrapper 顶端与底端 **不应** 贴到红框,\n"
                + "       它们也要让出与 [1][2] 相同的上下 24px 黄色 padding 带\n"
                + "\n"
                + "回归信号:[3] 行任一蓝/粉块从红框顶部一路贴到底部,或文字明显比 [1][2] 更靠\n"
                + "         上/下红边 — 说明 RXFieldBaseSkin.layoutChildren 的 y/h 透传修复退化。"
        );
        hint.setWrapText(true);
        hint.getStyleClass().add("hint");

        root.getChildren().addAll(
                ref1Label, ref,
                ref2Label, noSides,
                bugLabel, buggy,
                hint
        );

        Scene scene = new Scene(root, 560, 520);
        scene.getStylesheets().add(getClass().getResource("rx-text-field-padding-bug-demo.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("RXTextField -fx-padding regression (issue #705)");
        stage.show();
    }

    private static StackPane iconNode() {
        SVGPath path = new SVGPath();
        path.setContent("M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001c.03.04.062.078.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1.012 1.012 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0z");
        path.setFill(Color.WHITE);
        StackPane icon = new StackPane(path);
        icon.setMinWidth(Region.USE_PREF_SIZE);
        icon.setPrefWidth(24);
        return icon;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
