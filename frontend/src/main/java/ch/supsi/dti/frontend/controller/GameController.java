package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.PlayerHand;
import ch.supsi.dti.backend.model.RoundRecord;
import ch.supsi.dti.frontend.MainApp;
import ch.supsi.dti.frontend.view.CardView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Locale;

public class GameController {

    private static final int INITIAL_BALANCE = 100;
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final int DEFAULT_BET = 10;
    private static final int BET_STEP = 1;
    private static final Duration DEALER_STEP_DELAY = Duration.millis(800);

    private Timeline dealerTimeline;

    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScoreLabel;
    @FXML private HBox playersRow;
    @FXML private Label messageLabel;
    @FXML private Label bettingPromptLabel;
    @FXML private Spinner<Integer> betSpinner;
    @FXML private Button dealButton;
    @FXML private Button hitButton;
    @FXML private Button standButton;
    @FXML private Button newRoundButton;
    @FXML private Button backToMenuButton;
    @FXML private Button doubleButton;
    @FXML private Button splitButton;
    @FXML private Button insureButton;
    @FXML private Button declineInsuranceButton;
    @FXML private Button historyButton;

    // Sequential betting / insurance state — tracked per-frontend, not in GameManager.
    private int bettingPlayerIndex = 0;
    private int insuranceAskingIndex = 0;

    // Survives FXML reloads (e.g. language change) so the active game isn't lost.
    private static GameManager sharedGameManager;
    private static int sharedBettingIndex;
    private static int sharedInsuranceIndex;
    // Injected by the menu before navigating to the game scene.
    private static GameManager pendingGameManager;

    private GameManager gameManager;

    public static void setPendingGameManager(GameManager gm) {
        pendingGameManager = gm;
    }

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

        boolean freshGame = false;
        if (pendingGameManager != null) {
            sharedGameManager = pendingGameManager;
            pendingGameManager = null;
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        if (sharedGameManager == null) {
            sharedGameManager = new GameManager(List.of("Player 1"), INITIAL_BALANCE);
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        gameManager = sharedGameManager;
        if (freshGame) {
            bettingPlayerIndex = nextActivePlayerIndex(0);
            insuranceAskingIndex = 0;
            sharedBettingIndex = bettingPlayerIndex;
            sharedInsuranceIndex = insuranceAskingIndex;
        } else {
            // FXML reload (e.g. language switch): preserve the in-flight indices.
            bettingPlayerIndex = sharedBettingIndex;
            insuranceAskingIndex = sharedInsuranceIndex;
        }
        updateUI();
        autoPlayDealerIfNeeded(); // B3: resume dealer animation after a reload.
    }

    // --- Action handlers ---

    @FXML
    private void onDealClicked() {
        runSafe(() -> {
            // Sequential betting: confirm current player's bet, then advance.
            gameManager.placeBet(bettingPlayerIndex, betSpinner.getValue());
            bettingPlayerIndex = nextActivePlayerIndex(bettingPlayerIndex + 1);
            if (bettingPlayerIndex >= gameManager.getPlayers().size()) {
                gameManager.deal();
                bettingPlayerIndex = 0;
                insuranceAskingIndex = nextActivePlayerIndex(0);
                autoPlayDealerIfNeeded();
            }
            persistIndices();
        });
    }

    private void persistIndices() {
        sharedBettingIndex = bettingPlayerIndex;
        sharedInsuranceIndex = insuranceAskingIndex;
    }

    private int nextActivePlayerIndex(int from) {
        List<Player> players = gameManager.getPlayers();
        int i = from;
        while (i < players.size() && players.get(i).isSittingOut()) {
            i++;
        }
        return i;
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
    private void onSplitClicked() {
        runSafe(() -> {
            gameManager.split();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onInsureClicked() {
        runSafe(() -> {
            gameManager.takeInsurance(insuranceAskingIndex);
            insuranceAskingIndex = nextActivePlayerIndex(insuranceAskingIndex + 1);
            persistIndices();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onDeclineInsuranceClicked() {
        runSafe(() -> {
            gameManager.declineInsurance(insuranceAskingIndex);
            insuranceAskingIndex = nextActivePlayerIndex(insuranceAskingIndex + 1);
            persistIndices();
            autoPlayDealerIfNeeded();
        });
    }

    @FXML
    private void onNewRoundClicked() {
        runSafe(() -> {
            gameManager.startNewRound();
            bettingPlayerIndex = nextActivePlayerIndex(0);
            insuranceAskingIndex = 0;
            persistIndices();
        });
    }

    @FXML
    private void onNewGameClicked() {
        stopDealerTimeline(); // B2: avoid orphan ticks against the new manager.
        sharedGameManager = new GameManager(List.of("Player 1"), INITIAL_BALANCE);
        sharedGameManager.startNewRound();
        gameManager = sharedGameManager;
        bettingPlayerIndex = nextActivePlayerIndex(0);
        insuranceAskingIndex = 0;
        persistIndices();
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
        stopDealerTimeline(); // B3: prevent orphan Timeline ticking against the new controller.
        MessageService.getInstance().setLocale(locale);
        try {
            MainApp.reloadRoot();
        } catch (Exception e) {
            messageLabel.setText("⚠ " + e.getMessage());
        }
    }

    private void stopDealerTimeline() {
        if (dealerTimeline != null) {
            dealerTimeline.stop();
            dealerTimeline = null;
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
        if (gameManager.getState() != GameState.DEALER_TURN) {
            return;
        }
        if (dealerTimeline != null && dealerTimeline.getStatus() == Timeline.Status.RUNNING) {
            return;
        }
        dealerTimeline = new Timeline(new KeyFrame(DEALER_STEP_DELAY, e -> {
            boolean more = gameManager.dealerTakeTurnStep();
            updateUI();
            if (!more) {
                dealerTimeline.stop();
            }
        }));
        dealerTimeline.setCycleCount(Timeline.INDEFINITE);
        dealerTimeline.play();
    }

    private void updateUI() {
        MessageService msg = MessageService.getInstance();
        GameState state = gameManager.getState();

        renderDealer(state);
        renderPlayers(msg, state);

        messageLabel.setText(switch (state) {
            case WAITING, BETTING       -> msg.getMessage("game.message.placeBet");
            case DEALING, PLAYER_TURN   -> msg.getMessage("game.message.playerTurn");
            case INSURANCE_OFFER        -> msg.getMessage("game.message.offerInsurance");
            case DEALER_TURN, RESOLVING -> msg.getMessage("game.message.dealerTurn");
            case ROUND_OVER             -> "";
            case GAME_OVER              -> msg.getMessage("game.message.gameOver");
        });

        // Per-turn prompt below the message label.
        if (state == GameState.BETTING && bettingPlayerIndex < gameManager.getPlayers().size()) {
            String name = gameManager.getPlayers().get(bettingPlayerIndex).getName();
            bettingPromptLabel.setText(msg.getMessage("game.message.bettingTurn", name));
        } else if (state == GameState.INSURANCE_OFFER
                && insuranceAskingIndex < gameManager.getPlayers().size()) {
            String name = gameManager.getPlayers().get(insuranceAskingIndex).getName();
            bettingPromptLabel.setText(name);
        } else {
            bettingPromptLabel.setText("");
        }

        boolean gameOver = state == GameState.GAME_OVER;
        boolean insurance = state == GameState.INSURANCE_OFFER;
        boolean betting = state == GameState.BETTING;
        dealButton.setDisable(gameOver || !betting);
        dealButton.setVisible(betting);
        dealButton.setManaged(betting);
        hitButton.setDisable(gameOver || state != GameState.PLAYER_TURN);
        hitButton.setVisible(state == GameState.PLAYER_TURN);
        hitButton.setManaged(state == GameState.PLAYER_TURN);
        standButton.setDisable(gameOver || state != GameState.PLAYER_TURN);
        standButton.setVisible(state == GameState.PLAYER_TURN);
        standButton.setManaged(state == GameState.PLAYER_TURN);
        doubleButton.setDisable(gameOver || !gameManager.canDoubleDown());
        doubleButton.setVisible(state == GameState.PLAYER_TURN);
        doubleButton.setManaged(state == GameState.PLAYER_TURN);
        splitButton.setDisable(gameOver || !gameManager.canSplit());
        splitButton.setVisible(state == GameState.PLAYER_TURN);
        splitButton.setManaged(state == GameState.PLAYER_TURN);
        insureButton.setDisable(gameOver || !insurance);
        insureButton.setVisible(insurance);
        insureButton.setManaged(insurance);
        declineInsuranceButton.setDisable(gameOver || !insurance);
        declineInsuranceButton.setVisible(insurance);
        declineInsuranceButton.setManaged(insurance);
        newRoundButton.setDisable(gameOver || state != GameState.ROUND_OVER);
        betSpinner.setDisable(gameOver || !betting);
        betSpinner.setVisible(betting);
        betSpinner.setManaged(betting);
        historyButton.setDisable(gameManager.getHistory().isEmpty());
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

    private void renderPlayers(MessageService msg, GameState state) {
        playersRow.getChildren().clear();
        List<Player> players = gameManager.getPlayers();
        int activeIdx = activePlayerIndex(state);
        for (int i = 0; i < players.size(); i++) {
            playersRow.getChildren().add(buildPlayerPanel(players.get(i), i == activeIdx, msg));
        }
    }

    private int activePlayerIndex(GameState state) {
        return switch (state) {
            case BETTING          -> bettingPlayerIndex < gameManager.getPlayers().size()
                                     ? bettingPlayerIndex : -1;
            case INSURANCE_OFFER  -> insuranceAskingIndex < gameManager.getPlayers().size()
                                     ? insuranceAskingIndex : -1;
            case PLAYER_TURN      -> gameManager.getPlayers().indexOf(gameManager.getCurrentPlayer());
            default               -> -1;
        };
    }

    private VBox buildPlayerPanel(Player player, boolean isActive, MessageService msg) {
        VBox panel = new VBox(6);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setMinWidth(200);
        panel.setPrefWidth(220);
        panel.setPadding(new Insets(8));
        String border = isActive
                ? "-fx-border-color: yellow; -fx-border-width: 3; -fx-border-radius: 8;"
                : "-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 1; -fx-border-radius: 8;";
        panel.setStyle(border + " -fx-background-color: rgba(0,0,0,0.18); -fx-background-radius: 8;");
        if (isActive) {
            panel.setTranslateY(-25);
        }

        // Header: name (with bot prefix) + balance + status badge
        String displayName = (player.isBot() ? "🤖 " : "") + player.getName();
        Label nameLbl = new Label(displayName);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14;");
        Label balLbl = new Label("💰 " + player.getBalance());
        balLbl.setStyle("-fx-text-fill: white;");
        HBox header = new HBox(8, nameLbl, balLbl);
        header.setAlignment(Pos.CENTER);
        panel.getChildren().add(header);

        if (player.isSittingOut()) {
            Label sitOut = new Label("⏸ " + msg.getMessage("game.message.sittingOut"));
            sitOut.setStyle("-fx-text-fill: #ffb86b; -fx-font-style: italic;");
            panel.getChildren().add(sitOut);
            return panel;
        }

        // Hands (a player can have multiple after split).
        PlayerHand activeHand = gameManager.getCurrentHand();
        for (PlayerHand ph : player.getHands()) {
            VBox handBox = new VBox(2);
            handBox.setAlignment(Pos.CENTER);
            HBox cardsRow = new HBox(4);
            cardsRow.setAlignment(Pos.CENTER);
            for (Card c : ph.getHand().getCards()) {
                cardsRow.getChildren().add(new CardView(c));
            }
            handBox.getChildren().add(cardsRow);

            if (!ph.getHand().getCards().isEmpty()) {
                String label = msg.getMessage("game.score") + ": " + ph.getHand().getScore()
                        + "  (" + ph.getBet() + ")";
                HandOutcome outcome = ph.getOutcome();
                if (outcome != null) {
                    label += "  — " + msg.getMessage(outcomeKey(outcome));
                }
                Label scoreLbl = new Label(label);
                boolean handActive = ph == activeHand;
                scoreLbl.setStyle("-fx-text-fill: " + (handActive ? "yellow" : "white")
                        + (handActive ? "; -fx-font-weight: bold;" : ";"));
                handBox.getChildren().add(scoreLbl);
            }
            panel.getChildren().add(handBox);
        }

        return panel;
    }

    private String outcomeKey(HandOutcome outcome) {
        return switch (outcome) {
            case WIN       -> "game.message.win";
            case LOSE      -> "game.message.lose";
            case PUSH      -> "game.message.push";
            case BLACKJACK -> "game.message.blackjack";
        };
    }

    @FXML
    private void onHistoryClicked() {
        MessageService msg = MessageService.getInstance();
        Stage dialog = new Stage();
        dialog.initOwner(playersRow.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(msg.getMessage("game.history.title"));

        TableView<RoundRecord> table = new TableView<>(
                FXCollections.observableArrayList(gameManager.getHistory()));
        table.setPlaceholder(new Label(msg.getMessage("game.history.empty")));

        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

        TableColumn<RoundRecord, String> colTime = new TableColumn<>(
                msg.getMessage("game.history.col.time"));
        colTime.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(tf.format(d.getValue().timestamp())));

        TableColumn<RoundRecord, String> colPlayer = new TableColumn<>(
                msg.getMessage("game.history.col.player"));
        colPlayer.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().playerName()));

        TableColumn<RoundRecord, Number> colBet = new TableColumn<>(
                msg.getMessage("game.history.col.bet"));
        colBet.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().bet()));

        TableColumn<RoundRecord, Number> colScore = new TableColumn<>(
                msg.getMessage("game.history.col.score"));
        colScore.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().playerScore()));

        TableColumn<RoundRecord, Number> colDealer = new TableColumn<>(
                msg.getMessage("game.history.col.dealer"));
        colDealer.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().dealerScore()));

        TableColumn<RoundRecord, String> colOutcome = new TableColumn<>(
                msg.getMessage("game.history.col.outcome"));
        colOutcome.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                msg.getMessage(outcomeKey(d.getValue().outcome()))));

        table.getColumns().addAll(colTime, colPlayer, colBet, colScore, colDealer, colOutcome);

        VBox root = new VBox(table);
        root.setPadding(new Insets(10));
        dialog.setScene(new Scene(root, 640, 360));
        dialog.show();
    }
}