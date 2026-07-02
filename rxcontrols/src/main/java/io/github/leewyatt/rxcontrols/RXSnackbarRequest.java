package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSnackbarHost.DismissReason;

import javafx.scene.Node;
import javafx.util.Duration;

import java.util.function.BiConsumer;

/**
 * An immutable snackbar message handed to {@link RXSnackbarHost#show(RXSnackbarRequest)}.
 * Built through {@link #builder(String)}; every field is fixed at build time, so a
 * queued request can never drift while it waits.
 *
 * <pre>{@code
 * host.show(RXSnackbarRequest.builder("File deleted")
 *         .action("Undo", () -> restore(file))
 *         .onDismissed((request, reason) -> log(reason))
 *         .build());
 * }</pre>
 */
public final class RXSnackbarRequest {

    private final String message;
    private final Node graphic;
    private final Node content;
    private final RXSnackbarSeverity severity;
    private final Duration duration;
    private final String actionLabel;
    private final Runnable actionHandler;
    private final boolean showCloseIcon;
    private final String key;
    private final RXSnackbarStrategy strategy;
    private final BiConsumer<RXSnackbarRequest, DismissReason> onDismissed;

    private RXSnackbarRequest(Builder builder) {
        this.message = builder.message;
        this.graphic = builder.graphic;
        this.content = builder.content;
        this.severity = builder.severity == null ? RXSnackbarSeverity.NONE : builder.severity;
        this.duration = builder.duration;
        this.actionLabel = builder.actionLabel;
        this.actionHandler = builder.actionHandler;
        this.showCloseIcon = builder.showCloseIcon;
        this.key = builder.key;
        this.strategy = builder.strategy;
        this.onDismissed = builder.onDismissed;
    }

    /**
     * Returns the message text, possibly {@code null} (rendered empty; typically
     * replaced by {@link #getContent() content} in that case).
     *
     * @return the message text, or {@code null}
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the optional graphic shown before the message.
     *
     * @return the graphic node, or {@code null}
     */
    public Node getGraphic() {
        return graphic;
    }

    /**
     * Returns the optional custom content node. When non-{@code null} it replaces
     * the graphic + message area; the action and close icon are still managed by
     * the host so the bar always stays dismissable.
     *
     * @return the custom content node, or {@code null}
     */
    public Node getContent() {
        return content;
    }

    /**
     * Returns the visual severity, never {@code null} (a {@code null} given to the
     * builder normalizes to {@link RXSnackbarSeverity#NONE}).
     *
     * @return the severity
     */
    public RXSnackbarSeverity getSeverity() {
        return severity;
    }

    /**
     * Returns this request's auto-hide duration. {@code null} means "inherit the
     * host's {@code defaultDuration}" (the only delegating value); a non-positive,
     * indefinite, or unknown duration means the request is persistent.
     *
     * @return the duration, or {@code null} to inherit the host default
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Returns the action button label.
     *
     * @return the action label, or {@code null}
     */
    public String getActionLabel() {
        return actionLabel;
    }

    /**
     * Returns the action handler run before the bar dismisses with
     * {@link DismissReason#ACTION}.
     *
     * @return the action handler, or {@code null}
     */
    public Runnable getActionHandler() {
        return actionHandler;
    }

    /**
     * Returns whether this request asks for a close icon. The host may still
     * render one it did not ask for (a persistent request with no action gets a
     * forced close icon so it can always be dismissed) — that guard is a rendering
     * decision and never rewrites this value.
     *
     * @return whether a close icon was requested
     */
    public boolean isShowCloseIcon() {
        return showCloseIcon;
    }

    /**
     * Returns the optional identity key, used for exact
     * {@link RXSnackbarHost#dismiss(String) dismiss(key)}, in-place updates
     * (showing a new request with the same key), and duplicate prevention.
     *
     * @return the key, or {@code null}
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns this request's scheduling strategy override.
     *
     * @return the strategy, or {@code null} to use the host default
     */
    public RXSnackbarStrategy getStrategy() {
        return strategy;
    }

    /**
     * Returns the callback invoked exactly once when this request is removed,
     * displayed or not. It receives this request and the {@link DismissReason}.
     *
     * @return the dismissed callback, or {@code null}
     */
    public BiConsumer<RXSnackbarRequest, DismissReason> getOnDismissed() {
        return onDismissed;
    }

    /**
     * Returns whether this request carries an action.
     *
     * @return {@code true} when an action handler is set
     */
    public boolean hasAction() {
        return actionHandler != null;
    }

    /**
     * Starts building a request for the given message.
     *
     * @param message the message text; {@code null} renders empty
     * @return a new builder
     */
    public static Builder builder(String message) {
        return new Builder(message);
    }

    /**
     * Builder for {@link RXSnackbarRequest}. Not reusable across builds in the
     * sense of shared mutation — each {@link #build()} snapshots the current state
     * into an independent immutable request.
     */
    public static final class Builder {

        private final String message;
        private Node graphic;
        private Node content;
        private RXSnackbarSeverity severity = RXSnackbarSeverity.NONE;
        private Duration duration;
        private String actionLabel;
        private Runnable actionHandler;
        private boolean showCloseIcon;
        private String key;
        private RXSnackbarStrategy strategy;
        private BiConsumer<RXSnackbarRequest, DismissReason> onDismissed;

        private Builder(String message) {
            this.message = message;
        }

        /**
         * Sets the graphic shown before the message.
         *
         * @param value the graphic node, or {@code null}
         * @return this builder
         */
        public Builder graphic(Node value) {
            this.graphic = value;
            return this;
        }

        /**
         * Sets a custom content node replacing the graphic + message area. The
         * action and close icon remain host-managed.
         *
         * @param value the content node, or {@code null}
         * @return this builder
         */
        public Builder content(Node value) {
            this.content = value;
            return this;
        }

        /**
         * Sets the visual severity.
         *
         * @param value the severity, or {@code null} for {@link RXSnackbarSeverity#NONE}
         * @return this builder
         */
        public Builder severity(RXSnackbarSeverity value) {
            this.severity = value;
            return this;
        }

        /**
         * Sets the auto-hide duration. {@code null} (the default) inherits the
         * host's {@code defaultDuration}; a non-positive, indefinite, or unknown
         * value makes the request persistent.
         *
         * @param value the duration, or {@code null} to inherit the host default
         * @return this builder
         */
        public Builder duration(Duration value) {
            this.duration = value;
            return this;
        }

        /**
         * Sets the single action. Activating it runs {@code handler} and then
         * dismisses the bar with {@link DismissReason#ACTION} (even if the handler
         * throws).
         *
         * @param label   the action button label
         * @param handler the action handler, or {@code null} for no action
         * @return this builder
         */
        public Builder action(String label, Runnable handler) {
            this.actionLabel = label;
            this.actionHandler = handler;
            return this;
        }

        /**
         * Sets whether the bar shows a close icon.
         *
         * @param value whether to show a close icon
         * @return this builder
         */
        public Builder showCloseIcon(boolean value) {
            this.showCloseIcon = value;
            return this;
        }

        /**
         * Sets the identity key used for {@code dismiss(key)}, in-place updates,
         * and duplicate prevention.
         *
         * @param value the key, or {@code null}
         * @return this builder
         */
        public Builder key(String value) {
            this.key = value;
            return this;
        }

        /**
         * Overrides the host's scheduling strategy for this request.
         *
         * @param value the strategy, or {@code null} to use the host default
         * @return this builder
         */
        public Builder strategy(RXSnackbarStrategy value) {
            this.strategy = value;
            return this;
        }

        /**
         * Sets the callback invoked exactly once when the request is removed.
         *
         * @param value the callback, or {@code null}
         * @return this builder
         */
        public Builder onDismissed(BiConsumer<RXSnackbarRequest, DismissReason> value) {
            this.onDismissed = value;
            return this;
        }

        /**
         * Builds the immutable request. No coercion happens here beyond severity
         * {@code null} → {@code NONE}; persistence (and the forced-close-icon
         * guard) is resolved by the host at show time, where the host default
         * duration is known.
         *
         * @return the immutable request
         */
        public RXSnackbarRequest build() {
            return new RXSnackbarRequest(this);
        }
    }
}
