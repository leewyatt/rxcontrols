package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.textfield.CustomTextField;

/**
 * Probe demo for {@code CustomTextFieldSkin.java:117}:
 *
 * <pre>
 *     final double rightStartX = w - rightWidth + snappedLeftInset();
 *                                                 ^^^^^^^^^^^^^^^^^^
 * </pre>
 *
 * Initial suspicion (incorrect): "rightPane uses leftInset offset, so with
 * asymmetric -fx-padding the right pane should overshoot the red border".
 *
 * Algebraic re-check (correct): {@code w == control.width - leftInset - rightInset},
 * so {@code w - rightWidth + leftInset = control.width - rightInset - rightWidth}.
 * The right pane's right edge always lands at {@code control.width - rightInset},
 * i.e. flush against the content area's right edge. It does NOT overshoot.
 *
 * <h2>What this probe actually verifies</h2>
 *
 * A real-but-different quirk: {@code leftPane.x = 0} ignores leftInset
 * (covers the left padding band), while rightPane's right edge respects
 * rightInset (preserves the right padding band). With ControlsFX's default
 * {@code customtextfield.css} this is invisible because the pseudo-class
 * rules zero both insets when side nodes are present. We bypass that with
 * inline {@code setStyle(...)} so the user's asymmetric {@code -fx-padding}
 * actually takes effect.
 *
 * <h2>Setup</h2>
 *
 * {@code -fx-padding: 5 10 5 20} (top=5 right=10 bottom=5 left=20).
 *
 * <h2>Where to look (inside the red border)</h2>
 *
 * <ul>
 *   <li><strong>Left side</strong>: the 20px yellow padding band between
 *       the red border and the L button.
 *       <ul>
 *         <li><em>If absent (L button hugs the red border)</em>: ControlsFX
 *             {@code leftStartX = 0} ignores leftInset, covers the padding.</li>
 *         <li><em>If present (20px yellow visible)</em>: the implementation
 *             respects leftInset.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Right side</strong>: the 10px yellow padding band between
 *       the X button and the red border.
 *       <ul>
 *         <li><em>If present (10px yellow visible)</em>: rightInset preserved.</li>
 *         <li><em>If absent (X hugs the red border)</em>: rightInset covered too.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Predicted outcome (from algebra)</h2>
 *
 * <ul>
 *   <li>[1] CFX: left yellow band absent (leftPane covers left padding),
 *       right yellow band present (rightPane respects rightInset).
 *       Asymmetric.</li>
 *   <li>[2] RX: both yellow bands present (RXFieldBaseSkin wraps the wrappers
 *       flush against the outer red border deliberately — R3-#1 design —
 *       but the text still respects all four padding sides). RX is
 *       symmetric in its rule, even if the rule is "wrappers hug outer".</li>
 * </ul>
 *
 * Run, screenshot, compare against prediction; then update
 * {@code devdoc/controlsfx-textfield-bug.md} A1 with the correct framing
 * (or report a ControlsFX issue if the asymmetry is observable and not
 * documented).
 */
public class ControlsFXAsymmetricPaddingProbe extends Application {

    private static final String SHARED_INLINE_STYLE =
            "-fx-padding: 5 10 5 20;"
            + "-fx-background-color: #fff8d4;"
            + "-fx-border-color: red;"
            + "-fx-border-width: 1;"
            + "-fx-background-radius: 0;"
            + "-fx-border-radius: 0;"
            + "-fx-font-size: 13;";

    @Override
    public void start(Stage stage) {
        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #ececec;");

        Label intro = new Label(
                "ControlsFX CustomTextFieldSkin.java:117 probe\n"
                + "    final double rightStartX = w - rightWidth + snappedLeftInset();\n"
                + "\n"
                + "Inline -fx-padding: 5 10 5 20 (top=5 right=10 bottom=5 left=20)\n"
                + "leftInset=20, rightInset=10 — asymmetric by 10px.\n"
                + "Inline setStyle() bypasses ControlsFX's pseudo-class padding clear-out.\n"
                + "\n"
                + "Watch the YELLOW padding band on both sides of the red border:\n"
                + "  • Left side (red border ↔ L button): 20px expected\n"
                + "  • Right side (X button ↔ red border): 10px expected"
        );
        intro.setStyle("-fx-font-family: monospace; -fx-font-size: 11; -fx-text-fill: #333;");

        Label cfxLabel = new Label("[1] ControlsFX CustomTextField  — algebra predicts: LEFT band absent, RIGHT band present");
        CustomTextField cfx = new CustomTextField();
        cfx.setText("L=20  R=10");
        cfx.setLeft(new Button("L"));
        cfx.setRight(new Button("X"));
        cfx.setStyle(SHARED_INLINE_STYLE);
        cfx.setPrefWidth(460);
        cfx.setMaxWidth(460);

        Label rxLabel = new Label("[2] RXTextField  — algebra predicts: both bands absent (wrappers hug outer, R3-#1 design)");
        RXTextField rx = new RXTextField();
        rx.setText("L=20  R=10");
        rx.setLeft(new Button("L"));
        rx.setRight(new Button("X"));
        rx.setStyle(SHARED_INLINE_STYLE);
        rx.setPrefWidth(460);
        rx.setMaxWidth(460);

        Label conclusion = new Label(
                "Interpretation guide:\n"
                + "  • If [1] shows ASYMMETRIC bands (left absent, right present):\n"
                + "      → confirmed quirk in CFX line 117 pair; report as docs / consistency issue\n"
                + "  • If [1] shows BOTH bands absent (left absent, right also absent):\n"
                + "      → there's more going on than the algebra captures (likely a CSS\n"
                + "        priority or layout-pass effect); investigate before reporting\n"
                + "  • If [1] shows BOTH bands present (no padding covered):\n"
                + "      → algebra correct, neither side is covered, no quirk visible — close A1\n"
                + "        as 'not reproducible' and move on"
        );
        conclusion.setStyle("-fx-font-family: monospace; -fx-font-size: 11; -fx-text-fill: #555;");

        root.getChildren().addAll(intro, cfxLabel, cfx, rxLabel, rx, conclusion);

        Scene scene = new Scene(root, 540, 520);
        stage.setScene(scene);
        stage.setTitle("ControlsFX asymmetric -fx-padding probe (CFX:117)");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
