// Schermata Profilo (avatar, statistiche, ultimi giochi).
// Al momento non raggiungibile dal menu (bottone rimosso in iter passata) ma il file resta per riusi futuri.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class ProfileController {

    @FXML private Button backBtn;
    @FXML private Label winStreakBadge;

    @FXML
    private void initialize() {

        winStreakBadge.setText(MessageService.getInstance().getMessage("profile.badge.winStreak", 1));
    }

    @FXML
    private void onBack() {
        Navigation.navigate((Stage) backBtn.getScene().getWindow(), "/ui/menu.fxml");
    }
}
