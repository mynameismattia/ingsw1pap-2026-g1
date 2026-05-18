package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class ProfileController {

    @FXML private Button backBtn;
    @FXML private Label winStreakBadge;

    @FXML
    private void initialize() {
        // Parametric i18n: "{0} vittoria" / "{0} win" — FXML %key can't apply MessageFormat
        winStreakBadge.setText(MessageService.getInstance().getMessage("profile.badge.winStreak", 1));
    }

    @FXML
    private void onBack() {
        try {
            Stage stage = (Stage) backBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/menu.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1100, 680);
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
