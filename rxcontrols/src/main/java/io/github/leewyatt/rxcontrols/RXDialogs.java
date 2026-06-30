package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Convenience factory for the most common {@link RXDialog} use cases — message,
 * confirmation, text input and busy dialogs — without hand-assembling content,
 * buttons and a result converter each time. A thin facade: every method builds a
 * plain {@code RXDialog}, so anything not covered here is still done directly on
 * the control. There is no blocking variant (an in-scene overlay cannot nest an
 * event loop); results are delivered through a {@link CompletableFuture}.
 *
 * <pre>{@code
 * RXDialogs.confirm(owner, "Delete?", "This cannot be undone.")
 *          .thenAccept(button -> { if (button == ButtonType.OK) delete(); });
 *
 * RXDialogs.error(owner, "Upload failed", exception.getMessage());
 *
 * RXDialogs.input(owner, "Rename", "New name:", current)
 *          .thenAccept(name -> { if (name != null) rename(name); });
 *
 * RXDialogs.Busy busy = RXDialogs.busy(owner, "Loading…");
 * task.whenComplete((r, e) -> Platform.runLater(busy::close));
 * }</pre>
 *
 * <p>For anything richer, {@link #create(Node)} returns a {@link Builder} that maps
 * to the same control (custom content, button sets, modality, and validity-gated
 * confirmation).</p>
 *
 * <p><b>Dismissal.</b> ESC and scrim clicks dismiss every dialog except {@link #busy}.
 * A dismissal resolves to the dialog's cancel-type button if it has one ({@code confirm}'s
 * Cancel), otherwise to {@code null} — a dismissal never activates an affirmative button,
 * so a stray ESC or scrim click cannot trigger an action. (A confirmation that must force an
 * explicit choice can switch this off via {@link Builder#closeOnEsc(boolean)} /
 * {@link Builder#closeOnScrimClick(boolean)}.)</p>
 */
public final class RXDialogs {

    private RXDialogs() {
    }

    /**
     * The kind of message a dialog carries. Adds a {@code rx-dialog-<type>} style
     * class so the stylesheet can accent the heading (information / warning / error /
     * confirmation); {@link #NONE} adds nothing.
     */
    public enum Type {
        /** No accent. */
        NONE,
        /** Informational message. */
        INFORMATION,
        /** Warning message. */
        WARNING,
        /** Error message. */
        ERROR,
        /** A question awaiting the user's decision. */
        CONFIRMATION
    }

    // ==================== One-line factories ====================

    /**
     * Shows an informational message. With no {@code buttons} a single OK is used;
     * pass button types to offer extra actions — the future reports which was clicked
     * (compare it against your own {@link ButtonType} instances).
     *
     * @param owner   a node in the target scene (the dialog attaches over its scene)
     * @param title   the heading text
     * @param message the body text
     * @param buttons the buttons to offer; empty for a single OK
     * @return a future completed with the clicked button (or the dismiss result)
     */
    public static CompletableFuture<ButtonType> information(Node owner, String title, String message,
                                                            ButtonType... buttons) {
        return message(owner, Type.INFORMATION, title, message, buttons);
    }

    /**
     * Shows a warning message. With no {@code buttons} a single OK is used; pass
     * button types to offer extra actions.
     *
     * @param owner   a node in the target scene
     * @param title   the heading text
     * @param message the body text
     * @param buttons the buttons to offer; empty for a single OK
     * @return a future completed with the clicked button (or the dismiss result)
     */
    public static CompletableFuture<ButtonType> warning(Node owner, String title, String message,
                                                        ButtonType... buttons) {
        return message(owner, Type.WARNING, title, message, buttons);
    }

    /**
     * Shows an error message. With no {@code buttons} a single OK is used; pass button
     * types to offer extra actions (for example an OK plus a "Retry" or "Report").
     *
     * @param owner   a node in the target scene
     * @param title   the heading text
     * @param message the body text
     * @param buttons the buttons to offer; empty for a single OK
     * @return a future completed with the clicked button (or the dismiss result)
     */
    public static CompletableFuture<ButtonType> error(Node owner, String title, String message,
                                                      ButtonType... buttons) {
        return message(owner, Type.ERROR, title, message, buttons);
    }

    /**
     * Shows a confirmation question with Cancel and OK buttons.
     *
     * @param owner   a node in the target scene
     * @param title   the heading text
     * @param message the body text
     * @return a future completed with the chosen button (or the dismiss result)
     */
    public static CompletableFuture<ButtonType> confirm(Node owner, String title, String message) {
        return confirm(owner, title, message, ButtonType.CANCEL, ButtonType.OK);
    }

    /**
     * Shows a confirmation question with an explicit button set.
     *
     * @param owner   a node in the target scene
     * @param title   the heading text
     * @param message the body text
     * @param buttons the buttons to offer
     * @return a future completed with the chosen button (or the dismiss result)
     */
    public static CompletableFuture<ButtonType> confirm(Node owner, String title, String message,
                                                        ButtonType... buttons) {
        return create(owner).type(Type.CONFIRMATION).title(title).message(message).buttons(buttons).show();
    }

    /**
     * Shows a single-line text-input dialog with Cancel and OK buttons.
     *
     * @param owner       a node in the target scene
     * @param title       the heading text
     * @param message     a prompt shown above the field, or {@code null}
     * @param initialText the field's initial text (selected for quick replacement), or {@code null}
     * @return a future completed with the entered text on OK, or {@code null} when cancelled / dismissed
     */
    public static CompletableFuture<String> input(Node owner, String title, String message, String initialText) {
        TextField field = new TextField(initialText == null ? "" : initialText);
        field.getStyleClass().add("dialog-input");

        RXDialogContent content = new RXDialogContent();
        content.setHeaderText(title);
        if (message != null && !message.isEmpty()) {
            Label label = new Label(message);
            label.setWrapText(true);
            content.setContent(new VBox(8.0, label, field));
        } else {
            content.setContent(field);
        }

        RXDialog<String> dialog = new RXDialog<>();
        dialog.setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? field.getText() : null);

        CompletableFuture<String> future = new CompletableFuture<>();
        dialog.setOnResult(future::complete);
        dialog.show(owner);
        field.requestFocus();
        field.selectAll();
        return future;
    }

    /**
     * Shows a single-choice dialog: a drop-down of {@code choices} with {@code defaultChoice}
     * pre-selected, plus Cancel and OK. The selection-input sibling of {@link #input}, mirroring
     * the native {@code ChoiceDialog}.
     *
     * @param owner         a node in the target scene
     * @param title         the heading text
     * @param message       a prompt shown above the chooser, or {@code null}
     * @param defaultChoice the initially selected choice, or {@code null}
     * @param choices       the available choices
     * @param <T>           the choice type
     * @return a future completed with the chosen value on OK, or {@code null} when cancelled / dismissed
     */
    public static <T> CompletableFuture<T> choice(Node owner, String title, String message,
                                                  T defaultChoice, Collection<T> choices) {
        ComboBox<T> combo = new ComboBox<>(FXCollections.observableArrayList(choices));
        combo.setValue(defaultChoice);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.getStyleClass().add("dialog-choice");

        RXDialogContent content = new RXDialogContent();
        content.setHeaderText(title);
        if (message != null && !message.isEmpty()) {
            Label label = new Label(message);
            label.setWrapText(true);
            content.setContent(new VBox(8.0, label, combo));
        } else {
            content.setContent(combo);
        }

        RXDialog<T> dialog = new RXDialog<>();
        dialog.setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(buttonType -> buttonType == ButtonType.OK ? combo.getValue() : null);

        CompletableFuture<T> future = new CompletableFuture<>();
        dialog.setOnResult(future::complete);
        dialog.show(owner);
        return future;
    }

    /**
     * Shows a single-choice dialog with the choices given inline.
     *
     * @param owner         a node in the target scene
     * @param title         the heading text
     * @param message       a prompt shown above the chooser, or {@code null}
     * @param defaultChoice the initially selected choice, or {@code null}
     * @param choices       the available choices
     * @param <T>           the choice type
     * @return a future completed with the chosen value on OK, or {@code null} when cancelled / dismissed
     */
    @SafeVarargs
    public static <T> CompletableFuture<T> choice(Node owner, String title, String message,
                                                  T defaultChoice, T... choices) {
        return choice(owner, title, message, defaultChoice, Arrays.asList(choices));
    }

    /**
     * Shows a non-dismissable busy dialog (a spinner over a scrim) and returns a
     * handle to close it when the work finishes. The dialog ignores ESC and scrim
     * clicks, so it stays up until {@link Busy#close()} is called.
     *
     * @param owner   a node in the target scene
     * @param message a label under the spinner, or {@code null}
     * @return a handle whose {@link Busy#close()} dismisses the dialog
     */
    public static Busy busy(Node owner, String message) {
        VBox box = new VBox(14.0, new RXBarSpinner());
        box.getStyleClass().add("busy-content");
        box.setAlignment(Pos.CENTER);
        if (message != null && !message.isEmpty()) {
            Label label = new Label(message);
            label.setWrapText(true);
            box.getChildren().add(label);
        }

        RXDialog<Void> dialog = new RXDialog<>();
        dialog.setContent(box);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnScrimClick(false);
        dialog.show(owner);
        return new Busy(dialog);
    }

    /**
     * Shows a busy dialog that closes itself when {@code task} completes (normally
     * or exceptionally). The dialog is closed on the JavaFX application thread.
     *
     * @param owner   a node in the target scene
     * @param message a label under the spinner, or {@code null}
     * @param task    the work whose completion dismisses the dialog
     * @return the busy handle (already wired to {@code task}; may also be closed manually)
     */
    public static Busy busy(Node owner, String message, CompletionStage<?> task) {
        Busy handle = busy(owner, message);
        task.whenComplete((result, error) -> Platform.runLater(handle::close));
        return handle;
    }

    // ==================== Builder ====================

    /**
     * Starts a fluent build for cases the one-line factories do not cover (custom
     * content, button sets, modality, validity gating).
     *
     * @param owner a node in the target scene
     * @return a new builder
     */
    public static Builder create(Node owner) {
        return new Builder(owner);
    }

    /**
     * Fluent builder for a one-off {@link RXDialog}. Unset options keep the control's
     * own defaults. Terminal {@link #show()} returns the result future.
     */
    public static final class Builder {

        private final Node owner;
        private Type type = Type.NONE;
        private String title;
        private String message;
        private Node content;
        private final List<ButtonType> buttons = new ArrayList<>();
        private Boolean modal;
        private Boolean closeOnEsc;
        private Boolean closeOnScrimClick;
        private Boolean draggable;
        private Boolean resizable;
        private DialogActionsLayout actionsLayout;
        private Boolean closeButton;
        private ObservableValue<Boolean> validCondition;

        Builder(Node owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        /**
         * Sets the message type (heading accent + style class). Defaults to {@link Type#NONE}.
         *
         * @param value the type, or {@code null} for {@link Type#NONE}
         * @return this builder
         */
        public Builder type(Type value) {
            this.type = value == null ? Type.NONE : value;
            return this;
        }

        /**
         * Sets the heading text used when no explicit {@link #content(Node)} is given.
         *
         * @param value the heading text
         * @return this builder
         */
        public Builder title(String value) {
            this.title = value;
            return this;
        }

        /**
         * Sets the body text used when no explicit {@link #content(Node)} is given.
         *
         * @param value the body text
         * @return this builder
         */
        public Builder message(String value) {
            this.message = value;
            return this;
        }

        /**
         * Sets an explicit content node, overriding {@link #title(String)} /
         * {@link #message(String)}.
         *
         * @param value the content node
         * @return this builder
         */
        public Builder content(Node value) {
            this.content = value;
            return this;
        }

        /**
         * Sets the buttons. When unset, a single OK button is used.
         *
         * @param value the button types
         * @return this builder
         */
        public Builder buttons(ButtonType... value) {
            buttons.clear();
            buttons.addAll(Arrays.asList(value));
            return this;
        }

        /**
         * Overrides the dialog's modality.
         *
         * @param value {@code true} for modal
         * @return this builder
         */
        public Builder modal(boolean value) {
            this.modal = value;
            return this;
        }

        /**
         * Overrides whether ESC closes the dialog.
         *
         * @param value {@code true} to close on ESC
         * @return this builder
         */
        public Builder closeOnEsc(boolean value) {
            this.closeOnEsc = value;
            return this;
        }

        /**
         * Overrides whether a scrim click closes the dialog.
         *
         * @param value {@code true} to close on scrim click
         * @return this builder
         */
        public Builder closeOnScrimClick(boolean value) {
            this.closeOnScrimClick = value;
            return this;
        }

        /**
         * Enables dragging the dialog by its title band.
         *
         * @param value {@code true} to allow dragging
         * @return this builder
         */
        public Builder draggable(boolean value) {
            this.draggable = value;
            return this;
        }

        /**
         * Enables resizing the dialog by its edges.
         *
         * @param value {@code true} to allow resizing
         * @return this builder
         */
        public Builder resizable(boolean value) {
            this.resizable = value;
            return this;
        }

        /**
         * Sets the action-bar layout (for example {@link DialogActionsLayout#PLATFORM}
         * to follow OS button ordering).
         *
         * @param value the actions layout
         * @return this builder
         */
        public Builder actionsLayout(DialogActionsLayout value) {
            this.actionsLayout = value;
            return this;
        }

        /**
         * Shows or hides the dialog's close (X) button. It is dialog chrome (overlaid at the
         * card's trailing top corner), so it works over any content.
         *
         * @param value {@code true} to show the close (X) button
         * @return this builder
         */
        public Builder closeButton(boolean value) {
            this.closeButton = value;
            return this;
        }

        /**
         * Gates the affirmative (default / first non-cancel) button on a validity
         * condition: while {@code condition} is not {@code true}, clicking that button
         * is vetoed and the dialog stays open. Other buttons (Cancel, ESC, scrim) are
         * unaffected.
         *
         * @param condition the validity condition
         * @return this builder
         */
        public Builder validWhen(ObservableValue<Boolean> condition) {
            this.validCondition = condition;
            return this;
        }

        /**
         * Builds, shows and returns the dialog's result future.
         *
         * @return a future completed with the chosen button (or the dismiss result)
         */
        public CompletableFuture<ButtonType> show() {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setContent(content != null ? content : new RXDialogContent(title, message));
            if (closeButton != null) {
                dialog.setShowCloseButton(closeButton);
            }
            if (buttons.isEmpty()) {
                dialog.getButtonTypes().setAll(ButtonType.OK);
            } else {
                dialog.getButtonTypes().setAll(buttons);
            }
            if (type != Type.NONE) {
                dialog.getStyleClass().add("rx-dialog-" + type.name().toLowerCase(Locale.ROOT));
            }
            if (actionsLayout != null) {
                dialog.setActionsLayout(actionsLayout);
            }
            if (modal != null) {
                dialog.setModal(modal);
            }
            if (closeOnEsc != null) {
                dialog.setCloseOnEsc(closeOnEsc);
            }
            if (closeOnScrimClick != null) {
                dialog.setCloseOnScrimClick(closeOnScrimClick);
            }
            if (draggable != null) {
                dialog.setEnableDraggable(draggable);
            }
            if (resizable != null) {
                dialog.setEnableResizable(resizable);
            }
            dialog.setResultConverter(buttonType -> buttonType);

            if (validCondition != null) {
                ButtonType affirmative = affirmativeButton(dialog.getButtonTypes());
                dialog.addEventHandler(RXDialogEvent.CLOSE_REQUEST, event -> {
                    // Gate only an explicit affirmative button click; ESC / scrim / close-X
                    // (any non-ACTION_BUTTON reason) always dismiss, so a gated dialog is never stuck.
                    if (event.getCloseReason() == CloseReason.ACTION_BUTTON
                            && event.getButtonType() == affirmative
                            && !Boolean.TRUE.equals(validCondition.getValue())) {
                        event.consume();
                    }
                });
            }

            CompletableFuture<ButtonType> future = new CompletableFuture<>();
            dialog.setOnResult(future::complete);
            dialog.show(owner);
            return future;
        }
    }

    // ==================== Busy handle ====================

    /**
     * Handle for a {@link #busy(Node, String) busy} dialog: close it when the work
     * it covers has finished.
     */
    public static final class Busy {

        private final RXDialog<?> dialog;

        Busy(RXDialog<?> dialog) {
            this.dialog = dialog;
        }

        /**
         * Dismisses the busy dialog. A no-op if it has already been dismissed.
         */
        public void close() {
            dialog.close();
        }

        /**
         * Returns whether the busy dialog is still showing.
         *
         * @return {@code true} while the dialog is showing
         */
        public boolean isShowing() {
            return dialog.isShowing();
        }
    }

    // ==================== Internals ====================

    private static CompletableFuture<ButtonType> message(Node owner, Type type, String title, String message,
                                                         ButtonType[] buttons) {
        Builder builder = create(owner).type(type).title(title).message(message);
        if (buttons.length > 0) {
            builder.buttons(buttons);
        }
        return builder.show();
    }

    // The button a validity gate applies to: the default button if there is one,
    // else the first non-cancel button, else the last button.
    private static ButtonType affirmativeButton(List<ButtonType> buttons) {
        for (ButtonType buttonType : buttons) {
            ButtonData data = buttonType.getButtonData();
            if (data != null && data.isDefaultButton()) {
                return buttonType;
            }
        }
        for (ButtonType buttonType : buttons) {
            ButtonData data = buttonType.getButtonData();
            if (data == null || !data.isCancelButton()) {
                return buttonType;
            }
        }
        return buttons.isEmpty() ? ButtonType.OK : buttons.get(buttons.size() - 1);
    }
}
