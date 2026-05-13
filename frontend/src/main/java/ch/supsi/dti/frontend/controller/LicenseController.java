package ch.supsi.dti.frontend.controller;
import ch.supsi.dti.backend.i18n.MessageService;

import ch.supsi.dti.backend.license.LicenseChecker;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
        licenseField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (formatting) return;
            String formatted = format(newVal);
            if (!formatted.equals(newVal)) {
                formatting = true;
                licenseField.setText(formatted);
                licenseField.positionCaret(formatted.length());
                formatting = false;
            }
            if (checking) return;

            if (errorRevert != null) {
                errorRevert.stop();
                errorRevert = null;
                resetButton();
            }

            boolean valid = KEY_PATTERN.matcher(formatted).matches();
            activateButton.setDisable(!valid);
            setStatus(StatusKind.NEUTRAL,
                    formatted.isEmpty() ? "Inserisci un codice per continuare"
                            : valid ? "Codice nel formato corretto"
                            : "Continua a inserire il codice...");
        });
    }

    private String format(String raw) {
        String cleaned = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (cleaned.length() > 20) cleaned = cleaned.substring(0, 20);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            if (i > 0 && i % 5 == 0) sb.append('-');
            sb.append(cleaned.charAt(i));
        }
        if (sb.length() > MAX_LEN) sb.setLength(MAX_LEN);
        return sb.toString();
    }

    @FXML
    private void onActivate() {
        if (checking) return;
        String key = licenseField.getText();

        checking = true;
        activateButton.setDisable(true);
        licenseField.setDisable(true);
        rememberCheck.setDisable(true);
        showLoading();
        setStatus(StatusKind.NEUTRAL, "Verifica in corso...");

        PauseTransition delay = new PauseTransition(CHECK_DELAY);
        delay.setOnFinished(e -> {
            boolean valid;
            try {
                valid = new LicenseChecker().checkLicense(key);
            } catch (Throwable t) {
                stopSpinner();
                showError("Errore: " + t.getMessage());
                setStatus(StatusKind.ERROR, "Errore durante la verifica");
                scheduleErrorRevert();
                return;
            }

            stopSpinner();
            if (valid) {
                if (rememberCheck.isSelected()) saveLicense(key);
                showSuccess("Licenza valida");
                setStatus(StatusKind.OK, "Licenza valida. Avvio del gioco...");
                PauseTransition hold = new PauseTransition(SUCCESS_HOLD);
                hold.setOnFinished(ev -> launchGame());
                hold.play();
            } else {
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

    private void launchGame() {
        try {
            Stage stage = (Stage) licenseField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/game.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1280, 720);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            stopSpinner();
            showError("Errore avvio");
            setStatus(StatusKind.ERROR, "Impossibile avviare il gioco: " + e.getMessage());
        }
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
            if (Files.exists(LICENSE_FILE)) {
                String key = Files.readString(LICENSE_FILE).trim();
                if (KEY_PATTERN.matcher(key).matches()) return key;
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
