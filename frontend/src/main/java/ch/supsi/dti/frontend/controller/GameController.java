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
    private static final Duration BOT_STEP_DELAY = Duration.millis(600);
    private static final int TOTAL_ROUNDS = 10;

    private Timeline dealerTimeline;
    private Timeline botTimeline;

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
    @FXML private VBox lastRoundsList;

    // Survives FXML reloads (e.g. language change) so the active game isn't lost.
    // Package-private so RoundResultController.onNewRound can call startNewRound() on it.
    // Sequential betting / insurance turn is derived from GameManager — no UI cursor needed.
    static GameManager sharedGameManager;
    // Injected by the menu before navigating to the game scene.
    private static GameManager pendingGameManager;

    // Round counter shared across FXML reloads. Reset to 1 on a fresh game,
    // incremented by RoundResultController when the user starts the next round.
    static int sharedRoundNumber = 1;

    private GameManager gameManager;
    private int currentBet;

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
            sharedRoundNumber = 1;
        }
        updateUI();
        tickAutoTurns(); // resume dealer/bot animation after a reload
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
            // Sequential betting: ask the backend whose turn it is, then advance.
            int bettingIdx = gameManager.currentBettingPlayerIndex();
            if (bettingIdx < 0) {
                return;
            }
            gameManager.placeBet(bettingIdx, currentBet);
            currentBet = 0;
            if (gameManager.currentBettingPlayerIndex() < 0) {
                // All active players have bet → deal and possibly enter insurance / dealer turn.
                gameManager.deal();
                tickAutoTurns();
            }
        });
    }

    @FXML
    private void onHitClicked() {
        runSafe(() -> {
            gameManager.hit();
            tickAutoTurns();
        });
    }

    @FXML
    private void onStandClicked() {
        runSafe(() -> {
            gameManager.stand();
            tickAutoTurns();
        });
    }

    @FXML
    private void onDoubleClicked() {
        runSafe(() -> {
            gameManager.doubleDown();
            tickAutoTurns();
        });
    }

    @FXML
    private void onSplitClicked() {
        runSafe(() -> {
            gameManager.split();
            tickAutoTurns();
        });
    }

    @FXML
    private void onInsureClicked() {
        runSafe(() -> {
            int idx = gameManager.currentInsurancePlayerIndex();
            if (idx < 0) {
                return;
            }
            gameManager.takeInsurance(idx);
            tickAutoTurns();
        });
    }

    @FXML
    private void onDeclineInsuranceClicked() {
        runSafe(() -> {
            int idx = gameManager.currentInsurancePlayerIndex();
            if (idx < 0) {
                return;
            }
            gameManager.declineInsurance(idx);
            tickAutoTurns();
        });
    }

    @FXML
    private void onViewResultsClicked() {
        navigateTo("/ui/roundresult.fxml", 1100, 680);
    }

    @FXML
    private void onBackToMenuClicked() {
        stopDealerTimeline();
        stopBotTimeline();
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

    private void navigateTo(String fxml, int w, int h) {
        Navigation.navigate((Stage) dealButton.getScene().getWindow(), fxml);
    }

    private void reloadGame() {
        stopDealerTimeline(); // prevent orphan ticks against the new controller instance
        stopBotTimeline();
        Navigation.navigate((Stage) dealButton.getScene().getWindow(), "/ui/game.fxml");
    }

    private void stopDealerTimeline() {
        if (dealerTimeline != null) {
            dealerTimeline.stop();
            dealerTimeline = null;
        }
    }

    private void stopBotTimeline() {
        if (botTimeline != null) {
            botTimeline.stop();
            botTimeline = null;
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

    /** Kicks off bot / dealer animation if the game state calls for it. No-op otherwise. */
    private void tickAutoTurns() {
        autoPlayBotIfNeeded();
        autoPlayDealerIfNeeded();
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

    private void autoPlayBotIfNeeded() {
        if (!gameManager.isCurrentPlayerBot()) {
            return;
        }
        if (botTimeline != null && botTimeline.getStatus() == Timeline.Status.RUNNING) {
            return;
        }
        botTimeline = new Timeline(new KeyFrame(BOT_STEP_DELAY, e -> {
            boolean more = gameManager.botStep();
            updateUI();
            if (!more) {
                botTimeline.stop();
                // bot turn ended → possibly chain into the dealer animation.
                autoPlayDealerIfNeeded();
            }
        }));
        botTimeline.setCycleCount(Timeline.INDEFINITE);
        botTimeline.play();
    }

    private void updateUI() {
        MessageService msg = MessageService.getInstance();
        GameState state = gameManager.getState();

        // Titlebar pills (parametric — set programmatically because FXML %key can't apply MessageFormat)
        roundLabel.setText(msg.getMessage("game.titlebar.round", sharedRoundNumber, TOTAL_ROUNDS));
        deckLabel.setText(msg.getMessage("game.titlebar.deck", gameManager.getDeckRemaining()));

        renderDealer();
        renderPlayers(msg, state);
        renderLastRounds();

        currentBetLabel.setText("$" + currentBet);

        // PLAYER_TURN on a bot → show "🤖 X is thinking…" instead of generic prompt.
        if (state == GameState.PLAYER_TURN && gameManager.isCurrentPlayerBot()) {
            Player bot = gameManager.getCurrentPlayer();
            messageLabel.setText(msg.getMessage("game.message.botThinking",
                    bot != null ? bot.getName() : ""));
        } else {
            messageLabel.setText(switch (state) {
                case WAITING, BETTING       -> msg.getMessage("game.message.placeBet");
                case DEALING, PLAYER_TURN   -> msg.getMessage("game.message.playerTurn");
                case INSURANCE_OFFER        -> msg.getMessage("game.message.offerInsurance");
                case DEALER_TURN, RESOLVING -> msg.getMessage("game.message.dealerTurn");
                case ROUND_OVER             -> "";
                case GAME_OVER              -> msg.getMessage("game.message.gameOver");
            });
        }

        // Per-turn prompt (who is betting / answering insurance)
        int bettingIdx = gameManager.currentBettingPlayerIndex();
        int insuranceIdx = gameManager.currentInsurancePlayerIndex();
        if (state == GameState.BETTING && bettingIdx >= 0) {
            String name = gameManager.getPlayers().get(bettingIdx).getName();
            bettingPromptLabel.setText(msg.getMessage("game.message.bettingTurn", name));
        } else if (state == GameState.INSURANCE_OFFER && insuranceIdx >= 0) {
            String name = gameManager.getPlayers().get(insuranceIdx).getName();
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
            case BETTING         -> gameManager.currentBettingPlayerIndex();
            case INSURANCE_OFFER -> gameManager.currentInsurancePlayerIndex();
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
            boolean thinking = player.isBot()
                    && gameManager.getState() == GameState.PLAYER_TURN
                    && gameManager.isCurrentPlayerBot();
            String badgeKey = thinking ? "game.panel.thinking" : "game.panel.turn";
            String badgeStyle = thinking ? "turn-badge-bot" : "turn-badge";
            row.getChildren().add(labeled(msg.getMessage(badgeKey), badgeStyle));
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

    /** Populates the right-panel "Last rounds" list with the human player's recent records. */
    private void renderLastRounds() {
        lastRoundsList.getChildren().clear();
        String humanName = gameManager.getPlayers().stream()
                .filter(p -> !p.isBot())
                .map(Player::getName)
                .findFirst()
                .orElse(null);
        if (humanName == null) {
            return;
        }

        List<RoundRecord> history = gameManager.getHistory();
        int totalForHuman = 0;
        for (RoundRecord r : history) {
            if (r.playerName().equals(humanName)) {
                totalForHuman++;
            }
        }
        // Walk in reverse chronological order; collect up to 3 of the human's records.
        java.util.ArrayList<RoundRecord> recent = new java.util.ArrayList<>(3);
        for (int i = history.size() - 1; i >= 0 && recent.size() < 3; i--) {
            RoundRecord r = history.get(i);
            if (r.playerName().equals(humanName)) {
                recent.add(r);
            }
        }

        // Build rows in the order they appear (latest first).
        for (int idx = 0; idx < recent.size(); idx++) {
            RoundRecord r = recent.get(idx);
            int displayRound = totalForHuman - idx;
            int delta = computeDelta(r.bet(), r.outcome());

            HBox row = new HBox();
            row.getStyleClass().add("mini-round-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLbl = labeled("Round " + displayRound, "mini-round-label");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);
            nameLbl.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().add(nameLbl);

            Label deltaLbl;
            if (delta > 0) {
                deltaLbl = labeled("+$" + delta, "delta-pos");
            } else if (delta < 0) {
                deltaLbl = labeled("-$" + Math.abs(delta), "delta-neg");
            } else {
                deltaLbl = labeled("$0", "mini-round-label");
            }
            row.getChildren().add(deltaLbl);

            lastRoundsList.getChildren().add(row);
        }
    }

    private static int computeDelta(int bet, HandOutcome outcome) {
        return switch (outcome) {
            case WIN       -> bet;
            case BLACKJACK -> (int) Math.round(bet * 1.5);
            case LOSE      -> -bet;
            case PUSH      -> 0;
        };
    }
}
