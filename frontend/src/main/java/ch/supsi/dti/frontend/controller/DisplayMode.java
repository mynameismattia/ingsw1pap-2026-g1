// Enum WINDOWED/FULLSCREEN per la modalità di visualizzazione.
// apply(stage) imposta la finestra; loadSaved() la rilegge dalle Preferences Java; la scelta dell'utente in Settings la persiste tra sessioni.

package ch.supsi.dti.frontend.controller;

import javafx.stage.Stage;

import java.util.prefs.Preferences;

public enum DisplayMode {
    WINDOWED,
    FULLSCREEN;

    private static final String PREF_KEY = "display.mode";
    private static final double DEFAULT_W = 1100;
    private static final double DEFAULT_H = 680;

    public void apply(Stage stage) {
        if (stage == null) return;
        switch (this) {
            case WINDOWED -> {
                // 1. Disattivo fullscreen e maximize, riabilito il resize manuale.
                stage.setFullScreen(false);
                stage.setMaximized(false);
                stage.setResizable(true);

                // 2. Se la finestra è inizializzata male o troppo piccola, la riporto alle dimensioni di default (1100×680).
                if (Double.isNaN(stage.getWidth()) || stage.getWidth() < DEFAULT_W) {
                    stage.setWidth(DEFAULT_W);
                }
                if (Double.isNaN(stage.getHeight()) || stage.getHeight() < DEFAULT_H) {
                    stage.setHeight(DEFAULT_H);
                }

                // 3. Centro la finestra sullo schermo.
                stage.centerOnScreen();
            }
            case FULLSCREEN -> {
                // 1. Assicuro che non sia "maximized" (sarebbe in conflitto), poi attivo il fullscreen vero.
                stage.setMaximized(false);
                stage.setFullScreen(true);
            }
        }
    }

    public static DisplayMode current(Stage stage) {
        if (stage == null) return WINDOWED;
        return stage.isFullScreen() ? FULLSCREEN : WINDOWED;
    }

    public static DisplayMode loadSaved() {
        try {
            // 1. Leggo la preference "display.mode" dalle Preferences Java (default = WINDOWED se non c'è).
            String name = Preferences.userNodeForPackage(DisplayMode.class)
                    .get(PREF_KEY, WINDOWED.name());
            // 2. Converto la stringa nell'enum corrispondente.
            return DisplayMode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            // 3. Fallback sicuro se la stringa salvata è corrotta o l'enum è cambiato.
            return WINDOWED;
        }
    }

    public void save() {
        try {
            Preferences.userNodeForPackage(DisplayMode.class).put(PREF_KEY, name());
        } catch (Exception ignored) {

        }
    }
}
