package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.PlayerHand;
import ch.supsi.dti.backend.model.RoundRecord;
import ch.supsi.dti.frontend.view.CardView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GameController {

    private static final int INITIAL_BALANCE = 100;
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final Duration DEALER_STEP_DELAY = Duration.millis(800);
    private static final int TOTAL_ROUNDS = 10;
    private static final int DECK_SIZE = 52;

    private Timeline dealerTimeline;

    // Titlebar
    @FXML private Label roundLabel;
    @FXML private Label deckLabel;

    // Left panel
    @FXML private VBox sidePlayersList;
    @FXML private Button historyButton;

    // Center: dealer + player seats
    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScoreLabel;
    @FXML private HBox playersRow;
    @FXML private Label messageLabel;
    @FXML private Label bettingPromptLabel;

    // Action bar
    @FXML private Button hitButton;
    @FXML private Button standButton;
    @FXML private Button doubleButton;
    @FXML private Button splitButton;
    @FXML private Button insureButton;
    @FXML private Button declineInsuranceButton;
    @FXML private Button viewResultsButton;

    // Right panel: chips + bet
    @FXML private Button chip5;
    @FXML private Button chip10;
    @FXML private Button chip25;
    @FXML private Button chip50;
    @FXML private Button chip100;
    @FXML private Button chip250;
    @FXML private Label currentBetLabel;
    @FXML private Button dealButton;

    // Sequential betting / insurance state — tracked per-frontend, not in GameManager.
    private int bettingPlayerIndex = 0;
    private int insuranceAskingIndex = 0;

    // Survives FXML reloads (e.g. language change) so the active game isn't lost.
    // Package-private so RoundResultController.onNewRound can call startNewRound() on it.
    static GameManager sharedGameManager;
    private static int sharedBettingIndex;
    private static int sharedInsuranceIndex;
    // Injected by the menu before navigating to the game scene.
    private static GameManager pendingGameManager;

    private GameManager gameManager;
    private int currentBet;
    private int roundNumber = 1;

    public static void setPendingGameManager(GameManager gm) {
        pendingGameManager = gm;
    }

    @FXML
    public void initialize() {
        boolean freshGame = false;
        if (pendingGameManager != null) {
            sharedGameManager = pendingGameManager;
            pendingGameManager = null;
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        if (sharedGameManager == null) {
            sharedGameManager = new GameManager(List.of(new Player("Player 1", INITIAL_BALANCE)));
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        gameManager = sharedGameManager;
        currentBet = 0;
        if (freshGame) {
            bettingPlayerIndex = nextActivePlayerIndex(0);
            insuranceAskingIndex = 0;
            persistIndices();
        } else {
            // FXML reload (e.g. language switch): preserve the in-flight indices.
            bettingPlayerIndex = sharedBettingIndex;
            insuranceAskingIndex = sharedInsuranceIndex;
        }
        updateUI();
        autoPlayDealerIfNeeded(); // resume dealer animation after a reload
    }

    // ── Action handlers ──────────────────────────────────────────

    @FXML
    private void onChipClicked(ActionEvent event) {
        Object data = ((Button) event.getSource()).getUserData();
        int value = Integer.parseInt(data.toString());
        if (currentBet + value <= MAX_BET) {
            currentBet += value;
            updateUI();
        }
    }

    @FXML
    private void onClearBet() {
        currentBet = 0;
        updateUI();
    }

    @FXML
    private void onDealClicked() {
        if (currentBet < MIN_BET) {
            messageLabel.setText("⚠ " + MessageService.getInstance().getMessage("game.message.placeBet")
                    + " (min $" + MIN_BET + ")");
            return;
        }
        runSafe(() -> {
            // Sequential betting: confirm current player's bet, advance to next active.
            gameManager.placeBet(bettingPlayerIndex, currentBet);
            currentBet = 0;
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
    private void onViewResultsClicked() {
        navigateTo("/ui/roundresult.fxml", 1100, 680);
    }

    @FXML
    private void onBackToMenuClicked() {
        stopDealerTimeline();
        navigateTo("/ui/menu.fxml", 1100, 680);
    }

    @FXML
    private void onSettingsClicked() {
        try {
            Stage dialog = new Stage();
            dialog.initOwner(dealButton.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/settings.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 480, 320);
            dialog.setScene(scene);
            dialog.setTitle(MessageService.getInstance().getMessage("settings.title"));
            dialog.setResizable(false);

            SettingsController ctrl = loader.getController();
            ctrl.setDialogStage(dialog);
            ctrl.setOnApply(this::reloadGame);

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onPauseClicked() {
        messageLabel.setText("⏸ " + MessageService.getInstance().getMessage("game.action.pause"));
    }

    @FXML
    private void onQuitClicked() {
        Platform.exit();
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

    // ── Helpers ──────────────────────────────────────────────────

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

    private void navigateTo(String fxml, int w, int h) {
        try {
            Stage stage = (Stage) dealButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            stage.setScene(new Scene(loader.load(), w, h));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reloadGame() {
        stopDealerTimeline(); // prevent orphan ticks against the new controller instance
        try {
            Stage stage = (Stage) dealButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/game.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            stage.setScene(new Scene(loader.load(), 1100, 680));
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
        } catch (IOException e) {
            e.printStackTrace();
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

        // Titlebar pills (parametric — set programmatically because FXML %key can't apply MessageFormat)
        roundLabel.setText(msg.getMessage("game.titlebar.round", roundNumber, TOTAL_ROUNDS));
        deckLabel.setText(msg.getMessage("game.titlebar.deck", DECK_SIZE));

        renderDealer();
        renderPlayers(msg, state);

        currentBetLabel.setText("$" + currentBet);

        messageLabel.setText(switch (state) {
            case WAITING, BETTING       -> msg.getMessage("game.message.placeBet");
            case DEALING, PLAYER_TURN   -> msg.getMessage("game.message.playerTurn");
            case INSURANCE_OFFER        -> msg.getMessage("game.message.offerInsurance");
            case DEALER_TURN, RESOLVING -> msg.getMessage("game.message.dealerTurn");
            case ROUND_OVER             -> "";
            case GAME_OVER              -> msg.getMessage("game.message.gameOver");
        });

        // Per-turn prompt (who is betting / answering insurance)
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

        boolean gameOver  = state == GameState.GAME_OVER;
        boolean betting   = state == GameState.BETTING;
        boolean playing   = state == GameState.PLAYER_TURN;
        boolean insurance = state == GameState.INSURANCE_OFFER;
        boolean roundOver = state == GameState.ROUND_OVER;

        // Chips: enabled only while betting, and only if adding wouldn't overflow MAX_BET
        updateChip(chip5,    5,   betting);
        updateChip(chip10,   10,  betting);
        updateChip(chip25,   25,  betting);
        updateChip(chip50,   50,  betting);
        updateChip(chip100,  100, betting);
        updateChip(chip250,  250, betting);

        dealButton.setDisable(gameOver || !betting || currentBet < MIN_BET);
        hitButton.setDisable(gameOver || !playing);
        standButton.setDisable(gameOver || !playing);

        doubleButton.setDisable(gameOver || !gameManager.canDoubleDown());
        doubleButton.setVisible(playing);
        doubleButton.setManaged(playing);

        splitButton.setDisable(gameOver || !gameManager.canSplit());
        splitButton.setVisible(playing);
        splitButton.setManaged(playing);

        insureButton.setDisable(gameOver || !insurance);
        insureButton.setVisible(insurance);
        insureButton.setManaged(insurance);

        declineInsuranceButton.setDisable(gameOver || !insurance);
        declineInsuranceButton.setVisible(insurance);
        declineInsuranceButton.setManaged(insurance);

        viewResultsButton.setVisible(roundOver);
        viewResultsButton.setManaged(roundOver);

        historyButton.setDisable(gameManager.getHistory().isEmpty());
    }

    private void updateChip(Button chip, int value, boolean betting) {
        chip.setDisable(!betting || currentBet + value > MAX_BET);
    }

    private void renderDealer() {
        dealerCardsBox.getChildren().clear();
        boolean revealed = gameManager.getDealer().isHandRevealed();
        List<Card> dealerCards = gameManager.getDealer().getHand().getCards();
        for (int i = 0; i < dealerCards.size(); i++) {
            Card visible = (i == 1 && !revealed) ? null : dealerCards.get(i);
            dealerCardsBox.getChildren().add(new CardView(visible));
        }
        if (revealed && !dealerCards.isEmpty()) {
            dealerScoreLabel.setText(String.valueOf(gameManager.getDealer().getHand().getScore()));
            dealerScoreLabel.setVisible(true);
            dealerScoreLabel.setManaged(true);
        } else {
            dealerScoreLabel.setText("");
            dealerScoreLabel.setVisible(false);
            dealerScoreLabel.setManaged(false);
        }
    }

    private void renderPlayers(MessageService msg, GameState state) {
        playersRow.getChildren().clear();
        sidePlayersList.getChildren().clear();
        List<Player> players = gameManager.getPlayers();
        int activeIdx = activePlayerIndex(state);
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            boolean isActive = (i == activeIdx);
            playersRow.getChildren().add(buildTableSeat(p, isActive, msg));
            sidePlayersList.getChildren().add(buildSideSeatRow(p, isActive, msg, i));
        }
    }

    private int activePlayerIndex(GameState state) {
        return switch (state) {
            case BETTING         -> bettingPlayerIndex < gameManager.getPlayers().size()
                                    ? bettingPlayerIndex : -1;
            case INSURANCE_OFFER -> insuranceAskingIndex < gameManager.getPlayers().size()
                                    ? insuranceAskingIndex : -1;
            case PLAYER_TURN     -> gameManager.getPlayers().indexOf(gameManager.getCurrentPlayer());
            default              -> -1;
        };
    }

    /** Center-table seat panel — cards box. Uses our seat-card CSS palette. */
    private VBox buildTableSeat(Player player, boolean isActive, MessageService msg) {
        VBox seat = new VBox(6);
        seat.setAlignment(Pos.CENTER);
        seat.getStyleClass().add("seat-card");
        if (isActive) {
            seat.getStyleClass().add("seat-card-active");
        }
        String displayName = (player.isBot() ? "🤖 " : "") + player.getName();

        if (player.isSittingOut()) {
            HBox cards = new HBox(4);
            cards.setAlignment(Pos.CENTER);
            cards.getChildren().addAll(new CardView(null), new CardView(null));
            seat.getChildren().add(cards);
            seat.getChildren().add(labeled(displayName, "seat-card-name"));
            seat.getChildren().add(labeled("⏸ " + msg.getMessage("game.message.sittingOut"),
                    "seat-card-score"));
            return seat;
        }

        PlayerHand activeHand = gameManager.getCurrentHand();
        HBox handsRow = new HBox(12);
        handsRow.setAlignment(Pos.CENTER);
        for (PlayerHand ph : player.getHands()) {
            VBox handBox = new VBox(4);
            handBox.setAlignment(Pos.CENTER);
            HBox cardsRow = new HBox(4);
            cardsRow.setAlignment(Pos.CENTER);
            for (Card c : ph.getHand().getCards()) {
                cardsRow.getChildren().add(new CardView(c));
            }
            handBox.getChildren().add(cardsRow);
            if (!ph.getHand().getCards().isEmpty()) {
                String text = String.valueOf(ph.getHand().getScore()) + "  ($" + ph.getBet() + ")";
                HandOutcome outcome = ph.getOutcome();
                if (outcome != null) {
                    text += "  · " + msg.getMessage(outcomeKey(outcome));
                }
                Label score = labeled(text, "seat-card-score");
                if (ph == activeHand) {
                    score.getStyleClass().add("seat-card-score-active");
                }
                handBox.getChildren().add(score);
            }
            handsRow.getChildren().add(handBox);
        }
        seat.getChildren().add(handsRow);
        seat.getChildren().add(labeled(displayName, "seat-card-name"));
        return seat;
    }

    /** Side-panel row — avatar + name + balance + Turn badge. */
    private HBox buildSideSeatRow(Player player, boolean isActive, MessageService msg, int index) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("seat-row");
        if (isActive) {
            row.getStyleClass().add("seat-row-active");
        }

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("seat-avatar");
        if (player.isBot()) {
            avatar.getStyleClass().add("seat-avatar-cpu");
        } else if (index > 0) {
            avatar.getStyleClass().add("seat-avatar-bob");
        }
        String initial = player.getName().isEmpty()
                ? "?"
                : player.getName().substring(0, 1).toUpperCase();
        Label initLbl = new Label(initial);
        initLbl.getStyleClass().add("seat-avatar-initial");
        avatar.getChildren().add(initLbl);
        row.getChildren().add(avatar);

        VBox info = new VBox();
        HBox.setHgrow(info, Priority.ALWAYS);
        info.getChildren().add(labeled((player.isBot() ? "🤖 " : "") + player.getName(), "seat-name"));
        info.getChildren().add(labeled("$" + player.getBalance(), "seat-balance"));
        row.getChildren().add(info);

        if (isActive) {
            row.getChildren().add(labeled(msg.getMessage("game.panel.turn"), "turn-badge"));
        }
        return row;
    }

    private static Label labeled(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private String outcomeKey(HandOutcome outcome) {
        return switch (outcome) {
            case WIN       -> "game.message.win";
            case LOSE      -> "game.message.lose";
            case PUSH      -> "game.message.push";
            case BLACKJACK -> "game.message.blackjack";
        };
    }
}
