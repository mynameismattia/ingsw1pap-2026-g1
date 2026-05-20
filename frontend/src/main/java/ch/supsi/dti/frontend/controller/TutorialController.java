package ch.supsi.dti.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class TutorialController {

    @FXML private Button startBtn;
    @FXML private Button settingsBtn;

    @FXML
    private void onBack() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/menu.fxml");
    }

    @FXML
    private void onStartGame() {
        // "Back to menu and play" → return to the menu where the user picks a configuration.
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/menu.fxml");
    }

    @FXML
    private void onSettings() {
        LanguageDropdown.show(settingsBtn,
                () -> Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/tutorial.fxml"));
    }
}
