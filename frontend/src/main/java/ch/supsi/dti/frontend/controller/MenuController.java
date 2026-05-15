package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.stage.Stage;

import java.io.IOException;

public class MenuController {

    private enum Mode { VS_CPU, MULTI, TUTORIAL }

    @FXML private Button modeVsCpuBtn;
    @FXML private Button modeMultiBtn;
    @FXML private Button modeTutorialBtn;
    @FXML private Spinner<Integer> humansSpinner;
    @FXML private CheckBox soundToggle;
    @FXML private Label licenseCodeLabel;
    @FXML private Button startBtn;

    private Mode selectedMode = Mode.VS_CPU;

    @FXML
    private void initialize() {
        humansSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 2));

        String saved = LicenseController.loadSavedLicense();
        if (saved != null) {
            licenseCodeLabel.setText(saved);
        }
        applyModeStyle();
    }

    @FXML
    private void onModeVsCpu()    { selectedMode = Mode.VS_CPU;    applyModeStyle(); }
    @FXML
    private void onModeMulti()    { selectedMode = Mode.MULTI;     applyModeStyle(); }
    @FXML
    private void onModeTutorial() { selectedMode = Mode.TUTORIAL;  applyModeStyle(); }

    private void applyModeStyle() {
        modeVsCpuBtn.getStyleClass().remove("mode-pill-active");
        modeMultiBtn.getStyleClass().remove("mode-pill-active");
        modeTutorialBtn.getStyleClass().remove("mode-pill-active");
        switch (selectedMode) {
            case VS_CPU   -> modeVsCpuBtn.getStyleClass().add("mode-pill-active");
            case MULTI    -> modeMultiBtn.getStyleClass().add("mode-pill-active");
            case TUTORIAL -> modeTutorialBtn.getStyleClass().add("mode-pill-active");
        }
    }

    @FXML
    private void onStartGame() {
        startBtn.setDisable(true);
        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/game.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1280, 720);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            startBtn.setDisable(false);
            e.printStackTrace();
        }
    }

    @FXML
    private void onProfile() {
        // TODO: navigazione alla schermata profilo (PR futura)
    }

    @FXML
    private void onLeaderboard() {
        // TODO: navigazione alla classifica (PR futura)
    }

    @FXML
    private void onSettings() {
        // TODO: apertura dialog impostazioni (PR futura)
    }
}
