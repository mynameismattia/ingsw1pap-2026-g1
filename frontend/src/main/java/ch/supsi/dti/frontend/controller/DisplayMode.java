package ch.supsi.dti.frontend.controller;

import javafx.stage.Stage;

import java.util.prefs.Preferences;

/**
 * Window display mode (resizable window or true fullscreen).
 *
 * <p>The OS-native title bar (logo + game name + min/max/close) is visible in
 * {@link #WINDOWED} thanks to the {@code DECORATED} stage style set in
 * {@code MainApp}. Fullscreen mode hides all chrome.</p>
 *
 * <p>Persisted via {@link Preferences} so the chosen mode survives an app
 * restart; {@link #loadSaved()} reads it at startup, {@link #save()} writes it
 * whenever the user picks a different mode.</p>
 */
public enum DisplayMode {
    WINDOWED,
    FULLSCREEN;

    private static final String PREF_KEY = "display.mode";
    private static final double DEFAULT_W = 1100;
    private static final double DEFAULT_H = 680;

    /** Applies this mode to the given stage. Idempotent — safe to call repeatedly. */
    public void apply(Stage stage) {
        if (stage == null) return;
        switch (this) {
            case WINDOWED -> {
                stage.setFullScreen(false);
                stage.setMaximized(false);
                stage.setResizable(true);
                // Restore a sane default size if the stage has none / a tiny one
                // (e.g. coming back from fullscreen or on first boot).
                if (Double.isNaN(stage.getWidth()) || stage.getWidth() < DEFAULT_W) {
                    stage.setWidth(DEFAULT_W);
                }
                if (Double.isNaN(stage.getHeight()) || stage.getHeight() < DEFAULT_H) {
                    stage.setHeight(DEFAULT_H);
                }
                stage.centerOnScreen();
            }
            case FULLSCREEN -> {
                stage.setMaximized(false);
                stage.setFullScreen(true);
            }
        }
    }

    /** Snapshot the stage's current mode (used to highlight the right pill). */
    public static DisplayMode current(Stage stage) {
        if (stage == null) return WINDOWED;
        return stage.isFullScreen() ? FULLSCREEN : WINDOWED;
    }

    public static DisplayMode loadSaved() {
        try {
            String name = Preferences.userNodeForPackage(DisplayMode.class)
                    .get(PREF_KEY, WINDOWED.name());
            return DisplayMode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Stale value (e.g. legacy "MAXIMIZED") — fall back to a sane default.
            return WINDOWED;
        }
    }

    public void save() {
        try {
            Preferences.userNodeForPackage(DisplayMode.class).put(PREF_KEY, name());
        } catch (Exception ignored) {
            // Preferences can fail on locked-down environments; ignore.
        }
    }
}
