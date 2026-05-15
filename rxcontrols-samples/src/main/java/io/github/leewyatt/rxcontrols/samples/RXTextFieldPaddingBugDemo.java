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
import org.controlsfx.control.textfield.CustomTextField;

/**
 * Regression demo + cross-vendor comparison for the ControlsFX Issue #705
 * family of layout bugs. Five rows share the same exaggerated
 * padding ({@code 24 16}) and a red border so the padding region is visible
 * (yellow). Rows are paired so the RX / ControlsFX behaviour can be eyeballed
 * side by side under identical CSS:
 *
 * <pre>
 *   No side nodes (super-default-path comparison):
 *     [1] javafx.scene.control.TextField        (baseline)
 *     [2] RXTextField                           (no left/right)
 *     [3] ControlsFX CustomTextField            (no left/right)
 *
 *   With side nodes (algorithm comparison):
 *     [4] RXTextField with left + right         (RXFieldBaseSkin.layoutChildren)
 *     [5] ControlsFX CustomTextField with l + r (CustomTextFieldSkin)
 * </pre>
 *
 * Two distinct invariants are being checked at once, do not conflate them:
 *
 * <ol>
 *   <li><strong>Text Y baseline</strong> — rows [1]/[2]/[3]/[4]/[5] should all
 *       leave a ~24px yellow band above and below the glyphs. This is the
 *       Issue #705 fix proper. If [4]'s text hugs the red border, our
 *       {@code layoutChildren} regressed.</li>
 *   <li><strong>Horizontal gap between side wrapper and text</strong> — this
 *       is R3-#2 in {@code devdoc/rxfield-new-plan.md}, an open design
 *       question, NOT a bug. Expected difference under {@code -fx-padding: 24 16}:
 *       <ul>
 *         <li>[4] RX: gap ≈ 16px (control's horizontal {@code -fx-padding}
 *             still applies between the wrapper and the text)</li>
 *         <li>[5] CustomTextField: gap ≈ 3px (ControlsFX strips the
 *             control's left/right padding via the {@code :left-node-visible}
 *             / {@code :right-node-visible} pseudo-classes when side nodes are
 *             present, then adds a small wrapper-self padding back in)</li>
 *       </ul>
 *       Pick a strategy after seeing both rows running.</li>
 * </ol>
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

        Label ref3Label = new Label("[3] ControlsFX CustomTextField — 无 left/right(对照)");
        CustomTextField cfxNoSides = new CustomTextField();
        cfxNoSides.setText("ControlsFX, 应与 [1][2] 一致");
        cfxNoSides.getStyleClass().add("reference-cfx");
        cfxNoSides.setPrefWidth(460);

        Label bugLabel = new Label("[4] RXTextField — 有 left+right(走我们的 layoutChildren)");
        RXTextField buggy = new RXTextField("文字 Y 应与上面三行持平");
        buggy.getStyleClass().add("bug-demo");
        buggy.setPrefWidth(460);
        buggy.setLeft(iconNode());
        Button rightBtn = new Button("✕");
        rightBtn.setFocusTraversable(false);
        buggy.setRight(rightBtn);

        Label cfxLabel = new Label("[5] ControlsFX CustomTextField — 有 left+right(对照)");
        CustomTextField cfxBoth = new CustomTextField();
        cfxBoth.setText("文字 Y 应与 [1]-[4] 持平");
        cfxBoth.getStyleClass().add("bug-demo-cfx");
        cfxBoth.setPrefWidth(460);
        cfxBoth.setLeft(iconNode());
        Button cfxRightBtn = new Button("✕");
        cfxRightBtn.setFocusTraversable(false);
        cfxBoth.setRight(cfxRightBtn);

        Label hint = new Label(
                "怎么看(-fx-padding: 24 16 下):\n"
                + "  红色 = 控件外边框    黄色 = padding 区    蓝/粉 = wrapper 占据范围\n"
                + "  分组:[1][2][3] 无 side node 对照组,[4][5] 有 side node 对照组\n"
                + "\n"
                + "  注: author CSS '.bug-demo-cfx { -fx-padding: 24 16 }' 按 JavaFX origin\n"
                + "  priority (author > user-agent) 实际胜过 ControlsFX 内置的伪类清零规则,\n"
                + "  padding 实际仍是 24 16 — 五行都跑在同一 padding 设定下.\n"
                + "\n"
                + "① 文字 Y 基线 — 五行都应距上下红边 ~24px,文字在同一水平线\n"
                + "   → 如果 [4] 的文字明显比 [1][2][3][5] 更贴上/下红边, 是回归 bug.\n"
                + "\n"
                + "② wrapper 是否横跨全高 — [4] 与 [5] 的彩色矩形都应该贴红框顶/底\n"
                + "   → 这是 ControlsFX 的视觉约定, R3-#1 把我们对齐到这条.\n"
                + "\n"
                + "③ wrapper 距红框的关系 (★ 这里能直接看到 ControlsFX A1 quirk ★)\n"
                + "   → [4] RX:   两侧 wrapper 都贴红框 (R3-#1 对称设计)\n"
                + "   → [5] CFX:  左侧 wrapper 贴红框, 右侧 wrapper 距红框 ~16px\n"
                + "               (右侧能看到黄色 padding 带, 左侧看不到)\n"
                + "\n"
                + "   根因: ControlsFX CustomTextFieldSkin.java:112-118 在 Java 代码层不对称\n"
                + "     • leftStartX = 0 (硬编码, 忽略 leftInset, 覆盖左 padding)\n"
                + "     • rightStartX 化简 = control.width - rightInset - rightWidth (守 rightInset)\n"
                + "   不是 CSS 伪类的问题 — 伪类规则本身是对称设计.\n"
                + "   详见 devdoc/controlsfx-textfield-bug.md A1.\n"
                + "\n"
                + "④ wrapper 与文字之间的横向间距\n"
                + "   → [4] RX:   左 16px, 右 16px (对称 — R3-#1 wrapper 贴外边, padding 在内侧)\n"
                + "   → [5] CFX:  左 16px, 右  0px (不对称 — 同 ③ A1 quirk, 文字右侧紧贴 wrapper)\n"
                + "   R3-#2 间距策略讨论的视觉基础就在这里 — RX 左右各 16px 是对称留白,\n"
                + "   CFX 右侧紧贴/左侧 16 是不对称, 不是 CFX 主动做的 '紧凑视觉' 设计.\n"
                + "\n"
                + "⑤ wrapper 内部的图标位置 — [4] 蓝色框内 SVG 居中,[5] 蓝色框内 SVG 也居中\n"
                + "   → 两者一致, 都是 CENTER_LEFT/RIGHT 对齐.\n"
                + "\n"
                + "⑥ 整体宽度 — 设 prefWidth=460, 五行的红框右边应该都在同一垂直线\n"
                + "   → 任意一行明显更宽或更窄, 说明该实现的 computePref/MinWidth 没正确把\n"
                + "     wrapper 宽度加进去."
        );
        hint.setWrapText(true);
        hint.getStyleClass().add("hint");

        root.getChildren().addAll(
                ref1Label, ref,
                ref2Label, noSides,
                ref3Label, cfxNoSides,
                bugLabel, buggy,
                cfxLabel, cfxBoth,
                hint
        );

        Scene scene = new Scene(root, 620, 820);
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
