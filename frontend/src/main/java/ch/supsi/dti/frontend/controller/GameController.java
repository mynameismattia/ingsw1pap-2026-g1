package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.frontend.MainApp;
import ch.supsi.dti.frontend.view.CardView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.Locale;

public class GameController {

    private static final int INITIAL_BALANCE = 100;
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final int DEFAULT_BET = 10;
    private static final int BET_STEP = 1;

    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScoreLabel;
    @FXML private HBox playerCardsBox;
    @FXML private Label playerScoreLabel;
    @FXML private Label messageLabel;
    @FXML private Label balanceLabel;
    @FXML private Spinner<Integer> betSpinner;
    @FXML private Button dealButton;
    @FXML private Button hitButton;
    @FXML private Button standButton;
    @FXML private Button newRoundButton;
    @FXML private Button backToMenuButton;
    @FXML private Button doubleButton;
    @FXML private Button insureButton;
    @FXML private Button declineInsuranceButton;

    // Survives FXML reloads (e.g. language change) so the active game isn't lost.
    private static GameManager sharedGameManager;

    private GameManager gameManager;

    @FXML
    public void initialize() {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_BET, MAX_BET, DEFAULT_BET, BET_STEP);
        betSpinner.setValueFactory(factory);
        betSpinner.setEditable(true);
        betSpinner.getEditor().setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));
        betSpinner.getEditor().setOnAction(e -> betSpinner.commitValue());
        betSpinner.focusedProperty().addListener((obs, was, isFocused) -> {
            if (!isFocused) betSpinner.commitValue();
        });

        if (sharedGameManager == null) {
            sharedGameManager = new GameManager(List.of("Player 1"), INITIAL_BALANCE);
            sharedGameManager.startNewRound();
        }
        gameManager = sharedGameManager;
        updateUI();
    }

    // --- Action handlers ---

    @FXML
    private void onDealClicked() {
        runSafe(() -> {
            gameManager.placeBet(0, betSpinner.getValue());
            gameManager.deal();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onHitClicked() {
        runSafe(() -> {
            gameManager.hit();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onStandClicked() {
        runSafe(() -> {
            gameManager.stand();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onDoubleClicked() {
        runSafe(() -> {
            gameManager.doubleDown();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onInsureClicked() {
        runSafe(() -> {
            gameManager.takeInsurance(0);
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onDeclineInsuranceClicked() {
        runSafe(() -> {
            gameManager.declineInsurance(0);
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onNewRoundClicked() {
        runSafe(gameManager::startNewRound);
    }

    @FXML
    private void onNewGameClicked() {
        sharedGameManager = new GameManager(List.of("Player 1"), INITIAL_BALANCE);
        sharedGameManager.startNewRound();
        gameManager = sharedGameManager;
        updateUI();
    }

    @FXML
    private void onExitClicked() {
        Platform.exit();
    }

    @FXML
    private void onBackToMenuClicked() {
        // TODO: navigate to menu screen once it exists.
        messageLabel.setText(MessageService.getInstance().getMessage("game.message.gameOver"));
    }

    @FXML
    private void onItalianClicked() {
        switchLocale(Locale.ITALIAN);
    }

    @FXML
    private void onEnglishClicked() {
        switchLocale(Locale.ENGLISH);
    }

    // --- Helpers ---

    private void switchLocale(Locale locale) {
        MessageService.getInstance().setLocale(locale);
        try {
            MainApp.reloadRoot();
        } catch (Exception e) {
            messageLabel.setText("⚠ " + e.getMessage());
        }
    }

    private void runSafe(Runnable action) {
        try {
            action.run();
            updateUI();
        } catch (RuntimeException e) {
            messageLabel.setText("⚠ " + e.getMessage());
        }
    }

    private void autoPlayDealerIfNeeded() {
        if (gameManager.getState() == GameState.DEALER_TURN) {
            gameManager.dealerPlay();
        }
    }

    private void updateUI() {
        MessageService msg = MessageService.getInstance();
        Player player = gameManager.getPlayers().getFirst();
        GameState state = gameManager.getState();

        renderDealer(state);
        renderPlayer(player, msg);
        balanceLabel.setText(msg.getMessage("game.balance") + ": " + player.getBalance());
        messageLabel.setText(stateMessage(state, player, msg));

        boolean gameOver = state == GameState.GAME_OVER;
        boolean insurance = state == GameState.INSURANCE_OFFER;
        dealButton.setDisable(gameOver || state != GameState.BETTING);
        hitButton.setDisable(gameOver || state != GameState.PLAYER_TURN);
        standButton.setDisable(gameOver || state != GameState.PLAYER_TURN);
        doubleButton.setDisable(gameOver || !gameManager.canDoubleDown());
        doubleButton.setVisible(state == GameState.PLAYER_TURN);
        doubleButton.setManaged(state == GameState.PLAYER_TURN);
        insureButton.setDisable(gameOver || !insurance);
        insureButton.setVisible(insurance);
        insureButton.setManaged(insurance);
        declineInsuranceButton.setDisable(gameOver || !insurance);
        declineInsuranceButton.setVisible(insurance);
        declineInsuranceButton.setManaged(insurance);
        newRoundButton.setDisable(gameOver || state != GameState.ROUND_OVER);
        betSpinner.setDisable(gameOver || state != GameState.BETTING);
        backToMenuButton.setVisible(gameOver);
        backToMenuButton.setManaged(gameOver);
    }

    private void renderDealer(GameState state) {
        dealerCardsBox.getChildren().clear();
        boolean revealed = gameManager.getDealer().isHandRevealed();
        List<Card> dealerCards = gameManager.getDealer().getHand().getCards();
        for (int i = 0; i < dealerCards.size(); i++) {
            Card visible = (i == 1 && !revealed) ? null : dealerCards.get(i);
            dealerCardsBox.getChildren().add(new CardView(visible));
        }
        if (revealed && !dealerCards.isEmpty()) {
            dealerScoreLabel.setText(
                    MessageService.getInstance().getMessage("game.score")
                            + ": " + gameManager.getDealer().getHand().getScore());
        } else {
            dealerScoreLabel.setText("");
        }
    }

    private void renderPlayer(Player player, MessageService msg) {
        playerCardsBox.getChildren().clear();
        for (Card c : player.getHands().get(0).getHand().getCards()) {
            playerCardsBox.getChildren().add(new CardView(c));
        }
        if (player.getHands().get(0).getHand().getCards().isEmpty()) {
            playerScoreLabel.setText("");
        } else {
            playerScoreLabel.setText(
                    msg.getMessage("game.score") + ": " + player.getHands().get(0).getHand().getScore());
        }
    }

    private String stateMessage(GameState state, Player player, MessageService msg) {
        return switch (state) {
            case WAITING, BETTING       -> msg.getMessage("game.message.placeBet");
            case DEALING, PLAYER_TURN   -> msg.getMessage("game.message.playerTurn");
            case INSURANCE_OFFER        -> msg.getMessage("game.message.offerInsurance");
            case DEALER_TURN, RESOLVING -> msg.getMessage("game.message.dealerTurn");
            case ROUND_OVER             -> resolveMessage(player, msg);
            case GAME_OVER              -> msg.getMessage("game.message.gameOver");
        };
    }

    private String resolveMessage(Player player, MessageService msg) {
        if (player.getHands().get(0).getHand().isBlackJack())   return msg.getMessage("game.message.blackjack");
        if (player.getHands().get(0).getHand().isBusted())      return msg.getMessage("game.message.bust");
        int playerScore = player.getHands().get(0).getHand().getScore();
        int dealerScore = gameManager.getDealer().getHand().getScore();
        boolean dealerBust = gameManager.getDealer().getHand().isBusted();
        if (dealerBust || playerScore > dealerScore) return msg.getMessage("game.message.win");
        if (playerScore == dealerScore)              return msg.getMessage("game.message.push");
        return msg.getMessage("game.message.lose");
    }
}