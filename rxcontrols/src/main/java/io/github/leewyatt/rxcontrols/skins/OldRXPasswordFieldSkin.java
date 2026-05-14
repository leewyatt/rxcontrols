package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.OldRXPasswordField;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.SimpleStyleableStringProperty;
import javafx.scene.Cursor;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Deprecated historical skin for {@link OldRXPasswordField}. Not intended for new code.
 */
@Deprecated
public class OldRXPasswordFieldSkin extends TextFieldSkin {
    public static final char BULLET = '●';

    private SimpleStyleableStringProperty echochar;
    private SimpleBooleanProperty showPassword;
    private OldRXPasswordField control;
    private StackPane btn;
    private ChangeListener<DisplayMode> displayModeChangeListener = (ob, ov, nv) -> {
        setButtonStatus(nv);
    };

    public final SimpleBooleanProperty showPasswordProperty() {
        if (showPassword == null) {
            showPassword = new SimpleBooleanProperty(false);
        }
        return this.showPassword;
    }

    public final boolean isShowPassword() {
        return this.showPasswordProperty().get();
    }

    public final void setShowPassword(final boolean showPassword) {
        this.showPasswordProperty().set(showPassword);
    }

    InvalidationListener updateListener = ob -> {
        control.setText(control.getText());
    };

    public OldRXPasswordFieldSkin(OldRXPasswordField control) {
        super(control);
        this.control = control;
        showPasswordProperty().addListener(updateListener);
        echocharProperty().addListener(updateListener);

        Pane topPane = new Pane();
        topPane.getStyleClass().add("tf-top-pane");
        btn = new StackPane();
        btn.getStyleClass().add("tf-button");
        btn.setMinSize(16, 16);
        Region region = new Region();
        region.getStyleClass().add("tf-button-shape");
        btn.getChildren().addAll(region);
        topPane.getChildren().addAll(btn);
        getChildren().addAll(topPane);

        btn.layoutXProperty().bind(topPane.widthProperty().subtract(btn.widthProperty()));
        btn.layoutYProperty().bind(topPane.heightProperty().subtract(btn.heightProperty()).divide(2));
        DisplayMode displayMode = control.getButtonDisplayMode();

        setButtonStatus(displayMode);
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                showPasswordProperty().set(!showPasswordProperty().get());
                control.end();
            }
        });
        control.buttonDisplayModeProperty().addListener(displayModeChangeListener);
    }

    private void setButtonStatus(DisplayMode displayMode) {
        if (displayMode == DisplayMode.SHOW) {
            btn.visibleProperty().unbind();
            btn.setVisible(true);
        } else if (displayMode == DisplayMode.HIDE) {
            btn.visibleProperty().unbind();
            btn.setVisible(false);
        } else if (displayMode == DisplayMode.AUTO) {
            btn.visibleProperty().bind(control.focusedProperty());
        }
    }

    @Override
    protected String maskText(String txt) {
        TextField textField = getSkinnable();
        if (showPasswordProperty().get()) {
            return textField.getText();
        } else {
            int n = textField.getLength();
            StringBuilder passwordBuilder = new StringBuilder(n);
            for (int i = 0; i < n; i++) {
                String str = this.echocharProperty().get();
                passwordBuilder.append((str == null || str.length() == 0) ? BULLET : str.charAt(0));
            }
            return passwordBuilder.toString();
        }
    }

    public final SimpleStyleableStringProperty echocharProperty() {
        if (echochar == null) {
            echochar = new SimpleStyleableStringProperty(OldRXPasswordField.StyleableProperties.ECHOCHAR, this, "enchochar",
                    String.valueOf(BULLET));
        }
        return this.echochar;
    }

    public final String getEchochar() {
        return this.echocharProperty().get();
    }

    public final void setEchochar(final String echochar) {
        this.echocharProperty().set(echochar);
    }

    @Override
    public void dispose() {
        control.buttonDisplayModeProperty().removeListener(displayModeChangeListener);
        showPasswordProperty().removeListener(updateListener);
        echocharProperty().removeListener(updateListener);

        btn.visibleProperty().unbind();
        btn.layoutXProperty().unbind();
        btn.layoutYProperty().unbind();

        getChildren().clear();
        super.dispose();
    }
}
