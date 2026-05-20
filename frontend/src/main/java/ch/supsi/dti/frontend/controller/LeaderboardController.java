package ch.supsi.dti.frontend.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class LeaderboardController {

    @FXML private Button backBtn;

    @FXML
    private void onBack() {
        Navigation.navigate((Stage) backBtn.getScene().getWindow(), "/ui/menu.fxml");
    }
}
