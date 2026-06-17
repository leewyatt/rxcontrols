package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimCheckerboard;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Showcase application for {@link RXDualPane}.
 *
 * <p>Exercises the fixed two-slot flip: a flip card (KPI summary / detail), a
 * view/edit switch whose {@code showingSecond} state is bound to a toggle
 * button, and a login/register switch — plus a right-side panel to pick the
 * animation preset and duration. Multi-page display animations are excluded;
 * the pane falls back to a direct cut for those.</p>
 */
public class RXDualPaneShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_ANIMATION = "Fade";

    private final Map<String, Supplier<PageAnimation>> animationPresets = animationPresets();

    private RXDualPane dualPane;
    private ToggleButton flipToggle;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDualPane";
    }

    @Override
    protected String subtitle() {
        return "Fixed two-slot flip container";
    }

    @Override
    protected String windowTitle() {
        return "RXDualPane Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 620.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-dual-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        dualPane = new RXDualPane(createKpiCard(), createDetailCard());
        dualPane.getStyleClass().add("showcase-dual-pane");
        dualPane.setPrefSize(460.0, 250.0);
        dualPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        flipToggle = new ToggleButton("Show detail");
        flipToggle.selectedProperty().bindBidirectional(dualPane.showingSecondProperty());
        Button toggleButton = new Button("toggle()");
        toggleButton.setOnAction(event -> dualPane.toggle());
        HBox controls = new HBox(12.0, flipToggle, toggleButton);
        controls.setAlignment(Pos.CENTER);

        Label hint = new Label("The toggle is bound to showingSecond; the flip plays in both directions.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, dualPane, controls, hint);
        preview.getStyleClass().add("dual-pane-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Animation", buildAnimationGrid()),
                section("Faces", buildFacesGrid()));
    }

    // ==================== Sections ====================

    private Node buildAnimationGrid() {
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(animationPresets.keySet());
        animationBox.setValue(DEFAULT_ANIMATION);
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            Supplier<PageAnimation> preset = animationPresets.get(newValue);
            if (preset != null) {
                dualPane.setAnimation(preset.get());
            }
        });

        Slider durationSlider = createSlider(100.0, 1500.0,
                dualPane.getAnimationDuration().toMillis());
        dualPane.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()),
                durationSlider.valueProperty()));

        return createGrid(
                row("Animation", animationBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildFacesGrid() {
        Button kpiButton = new Button("KPI / Detail");
        kpiButton.setOnAction(event -> {
            dualPane.setFirstContent(createKpiCard());
            dualPane.setSecondContent(createDetailCard());
            flipToggle.setText("Show detail");
        });
        Button loginButton = new Button("Login / Register");
        loginButton.setOnAction(event -> {
            dualPane.setFirstContent(createLoginCard());
            dualPane.setSecondContent(createRegisterCard());
            flipToggle.setText("Show register");
        });

        HBox faces = new HBox(8.0, kpiButton, loginButton);
        faces.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Replacing a face's content never animates; the flip does.");
        hint.getStyleClass().add("hint-label");

        return createGrid(
                row(faces),
                row(hint));
    }

    // ==================== Content nodes ====================

    private static Node createKpiCard() {
        Label caption = new Label("Monthly revenue");
        caption.getStyleClass().add("face-caption");
        Label value = new Label("$48,250");
        value.getStyleClass().add("face-value");
        Label delta = new Label("+12.4% vs last month");
        delta.getStyleClass().add("face-delta");

        VBox card = new VBox(8.0, caption, value, delta);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("face-card", "kpi-card");
        return card;
    }

    private static Node createDetailCard() {
        Label title = new Label("Revenue breakdown");
        title.getStyleClass().add("face-title");
        Label l1 = new Label("Subscriptions   $31,400");
        Label l2 = new Label("One-time         $11,850");
        Label l3 = new Label("Add-ons           $5,000");
        VBox lines = new VBox(4.0, l1, l2, l3);
        lines.getStyleClass().add("face-lines");

        VBox card = new VBox(10.0, title, lines);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("face-card", "detail-card");
        return card;
    }

    private static Node createLoginCard() {
        Label title = new Label("Sign in");
        title.getStyleClass().add("face-title");
        TextField email = new TextField();
        email.setPromptText("Email");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Button submit = new Button("Sign in");
        submit.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(10.0, title, email, password, submit);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("face-card", "auth-card");
        return card;
    }

    private static Node createRegisterCard() {
        Label title = new Label("Create account");
        title.getStyleClass().add("face-title");
        TextField name = new TextField();
        name.setPromptText("Full name");
        TextField email = new TextField();
        email.setPromptText("Email");
        PasswordField password = new PasswordField();
        password.setPromptText("Choose a password");
        Button submit = new Button("Register");
        submit.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(10.0, title, name, email, password, submit);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("face-card", "auth-card");
        return card;
    }

    // ==================== Animation presets ====================

    private static Map<String, Supplier<PageAnimation>> animationPresets() {
        Map<String, Supplier<PageAnimation>> presets = new LinkedHashMap<>();
        presets.put("Fade", AnimFade::new);
        presets.put("Flip", AnimFlip::new);
        presets.put("Cube", AnimCube::new);
        presets.put("Slide", AnimSlide::new);
        presets.put("Slide (vertical)", () -> new AnimSlide(Orientation.VERTICAL));
        presets.put("Slide In", AnimSlideIn::new);
        presets.put("Zoom", AnimZoom::new);
        presets.put("Squeeze", AnimSqueeze::new);
        presets.put("Dissolve", AnimDissolve::new);
        presets.put("Blinds", AnimBlinds::new);
        presets.put("Checkerboard", AnimCheckerboard::new);
        presets.put("Gaussian Blur", AnimGaussianBlur::new);
        return presets;
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
