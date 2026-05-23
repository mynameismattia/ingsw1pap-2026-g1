package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.model.BotNames;
import ch.supsi.dti.backend.model.Player;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerNamesController {

    public static int pendingHumanCount;
    public static int pendingBotCount;
    public static int pendingBalance;

    @FXML private VBox nameFieldsBox;
    private final List<TextField> fields = new ArrayList<>();

    @FXML
    private void initialize() {
        fields.clear();
        for (int i = 0; i < pendingHumanCount; i++) {
            TextField tf = new TextField("Player " + (i + 1));
            tf.setPromptText("Player " + (i + 1));
            tf.setPrefWidth(280);
            tf.getStyleClass().add("text-field-dark");
            fields.add(tf);
            nameFieldsBox.getChildren().add(tf);
        }
    }

    @FXML
    private void onBack() {
        Stage stage = (Stage) nameFieldsBox.getScene().getWindow();
        Navigation.navigate(stage, "/ui/menu.fxml");
    }

    @FXML
    private void onConfirm() {
        List<String> humanNames = new ArrayList<>(pendingHumanCount);
        for (int i = 0; i < pendingHumanCount; i++) {
            String v = fields.get(i).getText().trim();
            humanNames.add(v.isEmpty() ? "Player " + (i + 1) : v);
        }

        List<Player> players = new ArrayList<>(pendingHumanCount + pendingBotCount);
        Set<String> taken = new HashSet<>(humanNames);
        for (String name : humanNames) {
            players.add(new Player(name, pendingBalance));
        }
        if (pendingBotCount > 0) {
            for (String botName : BotNames.allocate(pendingBotCount, taken)) {
                players.add(new Player(botName, pendingBalance, true));
            }
        }

        GameController.setPendingGameManager(new GameManager(players));
        Stage stage = (Stage) nameFieldsBox.getScene().getWindow();
        Navigation.navigate(stage, "/ui/game.fxml");
    }
}
