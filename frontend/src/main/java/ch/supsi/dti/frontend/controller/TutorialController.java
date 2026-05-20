package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

public class TutorialController {

    @FXML private Button startBtn;

    @FXML
    private void onBack() {
        navigateTo("/ui/menu.fxml");
    }

    @FXML
    private void onStartGame() {
        // "Try it now" → bounce back to the menu so the user can pick a real
        // configuration (humans/CPUs) before launching.
        navigateTo("/ui/menu.fxml");
    }

    private void navigateTo(String fxml) {
        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            stage.setScene(new Scene(loader.load(), 1100, 680));
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
