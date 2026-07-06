package io.github.leewyatt.rxcontrols.utils;

import java.util.Locale;

/**
 * Detects the operating system the application is running on.
 *
 * <p>The result is resolved once from the {@code os.name} system property and
 * cached, since the host platform does not change during a run. Use
 * {@link #current()} for the detected value, or the {@link #isWindows()} /
 * {@link #isMacOS()} / {@link #isLinux()} shortcuts for common branches.</p>
 */
public enum RXOS {

    /** Microsoft Windows. */
    WINDOWS,

    /** Apple macOS (formerly OS X). */
    MACOS,

    /** Linux and other {@code *nix} desktop platforms. */
    LINUX,

    /** The platform could not be identified. */
    UNKNOWN;

    // ==================== Detection ====================

    private static final RXOS CURRENT = detect(System.getProperty("os.name", ""));

    /**
     * Returns the operating system this application is running on.
     *
     * @return the detected operating system, or {@link #UNKNOWN} if it could not
     *         be identified
     */
    public static RXOS current() {
        return CURRENT;
    }

    /**
     * Indicates whether the current operating system is Windows.
     *
     * @return {@code true} if running on Windows
     */
    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    /**
     * Indicates whether the current operating system is macOS.
     *
     * @return {@code true} if running on macOS
     */
    public static boolean isMacOS() {
        return CURRENT == MACOS;
    }

    /**
     * Indicates whether the current operating system is Linux or another
     * {@code *nix} platform.
     *
     * @return {@code true} if running on Linux
     */
    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    /**
     * Resolves an {@code os.name} value to a known operating system. Package
     * visibility allows the detection rules to be tested directly without
     * mutating system properties.
     *
     * @param osName the raw {@code os.name} value; may be empty but not null
     * @return the matching operating system, or {@link #UNKNOWN}
     */
    static RXOS detect(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return MACOS;
        }
        if (name.contains("nux") || name.contains("nix") || name.contains("aix")) {
            return LINUX;
        }
        return UNKNOWN;
    }
}
