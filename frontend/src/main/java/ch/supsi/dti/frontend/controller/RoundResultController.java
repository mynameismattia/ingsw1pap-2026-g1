package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import ch.supsi.dti.frontend.view.CardView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;

public class RoundResultController {

    @FXML private Label titleLabel;
    @FXML private Label winnerLabel;
    @FXML private Label streakLabel;
    @FXML private HBox dealerCardsBox;
    @FXML private Button saveGameButton;

    @FXML
    private void initialize() {
        MessageService msg = MessageService.getInstance();

        // Parametric i18n strings (FXML %key can't apply MessageFormat)
        titleLabel.setText(msg.getMessage("roundresult.title", 3));
        winnerLabel.setText(msg.getMessage("roundresult.winnerMessage", "Alice"));
        streakLabel.setText(msg.getMessage("roundresult.streak", 3));

        // Hardcoded dealer cards (CardView requires Card POJO, can't live in FXML)
        dealerCardsBox.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.ACE)),
                new CardView(new Card(Suit.HEARTS, Rank.SEVEN))
        );
    }

    @FXML
    private void onNewRound() {
        // The shared GameManager is in ROUND_OVER after a finished round.
        // Roll it forward to BETTING so the reopened game.fxml starts fresh.
        if (GameController.sharedGameManager != null
                && GameController.sharedGameManager.getState() == GameState.ROUND_OVER) {
            GameController.sharedGameManager.startNewRound();
        }
        navigateTo("/ui/game.fxml", 1100, 680);
    }

    @FXML
    private void onSaveGame() {
        // UI-only: no real persistence yet. Visual feedback via button text swap.
        saveGameButton.setText(MessageService.getInstance().getMessage("roundresult.toast.saved"));
        saveGameButton.setDisable(true);
    }

    @FXML
    private void onBackToMenu() {
        navigateTo("/ui/menu.fxml", 1100, 680);
    }

    private void navigateTo(String fxml, int w, int h) {
        try {
            Stage stage = (Stage) titleLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            stage.setScene(new Scene(loader.load(), w, h));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
