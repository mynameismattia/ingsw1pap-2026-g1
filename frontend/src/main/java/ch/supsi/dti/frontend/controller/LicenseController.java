// Schermata di inserimento codice licenza all'avvio.
// Valida tramite LicenseChecker (chiamata native) e, se ok, salva il codice in ~/.blackjack/license per non chiedere più al prossimo lancio.

package ch.supsi.dti.frontend.controller;
import ch.supsi.dti.backend.license.LicenseChecker;
import ch.supsi.dti.frontend.view.Icons;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class LicenseController {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}");
    private static final int MAX_LEN = 23;
    private static final Path LICENSE_FILE = Paths.get(System.getProperty("user.home"), ".blackjack", "license");

    private static final Duration CHECK_DELAY = Duration.millis(1200);
    private static final Duration SUCCESS_HOLD = Duration.millis(700);
    private static final Duration ERROR_REVERT = Duration.millis(1500);

    @FXML private Label keyIcon;
    @FXML private TextField licenseField;
    @FXML private Button activateButton;
    @FXML private HBox statusBox;
    @FXML private Label statusIcon;
    @FXML private Label statusLabel;
    @FXML private CheckBox rememberCheck;

    private boolean formatting = false;
    private boolean checking = false;
    private RotateTransition spinnerAnim;
    private PauseTransition errorRevert;

    @FXML
    private void initialize() {
        keyIcon.setText(Icons.KEY);
        keyIcon.getStyleClass().add(Icons.STYLE_CLASS);
        // Listener sulla casella di testo: ogni volta che l'utente digita un carattere fa partire la pipeline qua sotto.
        licenseField.textProperty().addListener((obs, oldVal, newVal) -> {
            // 1. Guard anti-ricorsione: se stiamo formattando noi (setText interno), non rientrare.
            if (formatting) return;

            // 2. Pulisco l'input (uppercase + solo A-Z0-9) e ci infilo i trattini automatici ogni 5 char.
            String formatted = format(newVal);
            if (!formatted.equals(newVal)) {
                formatting = true;
                Platform.runLater(() -> {
                    licenseField.setText(formatted);
                    licenseField.positionCaret(formatted.length());
                    formatting = false;
                });
            }

            // 3. Se stiamo già verificando una chiave, non toccare button/status (evita race con l'animazione).
            if (checking) return;

            // 4. Se c'era un timer di rollback errore in corso, lo cancello: l'utente sta modificando di nuovo.
            if (errorRevert != null) {
                errorRevert.stop();
                errorRevert = null;
                resetButton();
            }

            // 5. Aggiorno bottone e status row in base al fatto che la stringa matcha il pattern XXXXX-XXXXX-XXXXX-XXXXX.
            boolean valid = KEY_PATTERN.matcher(formatted).matches();
            activateButton.setDisable(!valid);
            setStatus(StatusKind.NEUTRAL,
                    formatted.isEmpty() ? "Inserisci un codice per continuare"
                            : valid ? "Codice nel formato corretto"
                            : "Continua a inserire il codice...");
        });
    }

    private String format(String raw) {
        // 1. Tolgo qualsiasi carattere non sia A-Z o 0-9 (anche i trattini di prima vengono rimossi, li reinserisco sotto).
        String cleaned = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        // 2. Hard cap a 20 caratteri "veri" (4 gruppi × 5).
        if (cleaned.length() > 20) cleaned = cleaned.substring(0, 20);
        // 3. Ricostruisco la stringa con un trattino ogni 5 caratteri (XXXXX-XXXXX-XXXXX-XXXXX).
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            if (i > 0 && i % 5 == 0) sb.append('-');
            sb.append(cleaned.charAt(i));
        }
        // 4. Safety: max 23 char totali (20 lettere/cifre + 3 trattini).
        if (sb.length() > MAX_LEN) sb.setLength(MAX_LEN);
        return sb.toString();
    }

    @FXML
    private void onActivate() {
        // 1. Guard contro doppio-click: se stiamo già verificando ignoriamo nuove richieste.
        if (checking) return;
        String key = licenseField.getText();

        // 2. Lock della UI: disabilito bottone, input, checkbox "ricordami" e mostro lo spinner di caricamento.
        //    Senza il lock l'utente potrebbe modificare il codice durante la verifica e creare incoerenze.
        checking = true;
        activateButton.setDisable(true);
        licenseField.setDisable(true);
        rememberCheck.setDisable(true);
        showLoading();
        setStatus(StatusKind.NEUTRAL, "Verifica in corso...");

        // 3. PauseTransition simula un'attesa di ~1.2s per dare percepibilità al check (anche se la chiamata JNI è quasi istantanea).
        PauseTransition delay = new PauseTransition(CHECK_DELAY);
        delay.setOnFinished(e -> {
            // 4. Chiamo il LicenseChecker (JNI → libreria nativa C++). Cattura anche Throwable in caso la lib non sia caricata.
            boolean valid;
            try {
                valid = new LicenseChecker().verifyLicense(key);
            } catch (Throwable t) {
                stopSpinner();
                showError("Errore: " + t.getMessage());
                setStatus(StatusKind.ERROR, "Errore durante la verifica");
                scheduleErrorRevert();
                return;
            }

            stopSpinner();
            // 5a. Caso OK: opzionalmente salvo la chiave su disco (~/.blackjack/license), poi dopo 700ms vado al menu.
            if (valid) {
                if (rememberCheck.isSelected()) saveLicense(key);
                showSuccess("Licenza valida");
                setStatus(StatusKind.OK, "Licenza valida. Avvio del gioco...");
                PauseTransition hold = new PauseTransition(SUCCESS_HOLD);
                hold.setOnFinished(ev -> launchMenu());
                hold.play();
            } else {
                // 5b. Caso KO: mostro errore, poi dopo 1.5s sblocco la UI per permettere un nuovo tentativo.
                showError("Codice non valido");
                setStatus(StatusKind.ERROR, "Codice licenza non valido");
                scheduleErrorRevert();
            }
        });
        delay.play();
    }

    private void scheduleErrorRevert() {
        errorRevert = new PauseTransition(ERROR_REVERT);
        errorRevert.setOnFinished(e -> {
            errorRevert = null;
            resetButton();
            checking = false;
            licenseField.setDisable(false);
            rememberCheck.setDisable(false);
            boolean valid = KEY_PATTERN.matcher(licenseField.getText()).matches();
            activateButton.setDisable(!valid);
            setStatus(StatusKind.NEUTRAL,
                    licenseField.getText().isEmpty() ? "Inserisci un codice per continuare"
                            : valid ? "Codice nel formato corretto"
                            : "Continua a inserire il codice...");
        });
        errorRevert.play();
    }

    private void showLoading() {
        Arc arc = new Arc(0, 0, 8, 8, 0, 270);
        arc.setType(ArcType.OPEN);
        arc.getStyleClass().add("spinner-arc");
        spinnerAnim = new RotateTransition(Duration.millis(900), arc);
        spinnerAnim.setByAngle(360);
        spinnerAnim.setCycleCount(RotateTransition.INDEFINITE);
        spinnerAnim.setInterpolator(Interpolator.LINEAR);
        spinnerAnim.play();

        activateButton.setGraphic(arc);
        activateButton.setText("Verifica in corso...");
        setButtonState("loading");
    }

    private void stopSpinner() {
        if (spinnerAnim != null) {
            spinnerAnim.stop();
            spinnerAnim = null;
        }
        activateButton.setGraphic(null);
    }

    private void showSuccess(String msg) {
        activateButton.setGraphic(makeBtnIcon("✓"));
        activateButton.setText(msg);
        setButtonState("success");
    }

    private void showError(String msg) {
        activateButton.setGraphic(makeBtnIcon("✕"));
        activateButton.setText(msg);
        setButtonState("error");
    }

    private Label makeBtnIcon(String glyph) {
        Label l = new Label(glyph);
        l.getStyleClass().add("btn-icon");
        return l;
    }

    private void resetButton() {
        stopSpinner();
        activateButton.setText("Attiva");
        setButtonState(null);
    }

    private void setButtonState(String state) {
        activateButton.getStyleClass().removeAll("loading", "success", "error");
        if (state != null) activateButton.getStyleClass().add(state);
    }

    private void launchMenu() {
        Stage stage = (Stage) licenseField.getScene().getWindow();
        stage.setResizable(true);
        stage.setMaximized(true);
        Navigation.navigate(stage, "/ui/menu.fxml");
    }

    private void saveLicense(String key) {
        try {
            Files.createDirectories(LICENSE_FILE.getParent());
            Files.writeString(LICENSE_FILE, key);
        } catch (IOException ignored) {
        }
    }

    private enum StatusKind { NEUTRAL, ERROR, OK }

    private void setStatus(StatusKind kind, String text) {
        statusLabel.setText(text);
        statusBox.getStyleClass().removeAll("status-neutral", "status-error", "status-ok");
        switch (kind) {
            case OK -> {
                statusBox.getStyleClass().add("status-ok");
                statusIcon.setText("✓");
            }
            case ERROR -> {
                statusBox.getStyleClass().add("status-error");
                statusIcon.setText("✕");
            }
            default -> {
                statusBox.getStyleClass().add("status-neutral");
                statusIcon.setText("○");
            }
        }
    }

    public static String loadSavedLicense() {
        try {
            // 1. Se esiste il file ~/.blackjack/license, lo leggo e verifico solo che il formato sia ancora valido.
            //    Non ri-verifico la chiave qui: solo MainApp poi la passa al LicenseChecker nativo per il vero check.
            if (Files.exists(LICENSE_FILE)) {
                String key = Files.readString(LICENSE_FILE).trim();
                if (KEY_PATTERN.matcher(key).matches()) return key;
            }
        } catch (IOException ignored) {
            // 2. I/O error → ritorno null e l'utente vedrà la schermata licenza come la prima volta.
        }
        return null;
    }
}
