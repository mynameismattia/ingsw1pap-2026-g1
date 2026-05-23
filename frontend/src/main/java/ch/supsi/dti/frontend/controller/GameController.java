package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.PlayerHand;
import ch.supsi.dti.backend.model.RoundRecord;
import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import ch.supsi.dti.backend.service.SaveSlot;
import ch.supsi.dti.frontend.service.SoundManager;
import ch.supsi.dti.frontend.service.SoundManager.SoundEvent;
import ch.supsi.dti.frontend.view.CardView;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @FXML private Label phaseLabel;

    // Left panel
    @FXML private VBox sidePlayersList;
    @FXML private Button historyButton;

    // Center: dealer + player seats
    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScoreLabel;
    @FXML private HBox playersRow;
    @FXML private Label hintLabel;

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
    @FXML private Label balanceLabel;
    @FXML private Label balanceOwnerLabel;
    @FXML private Button dealButton;
    @FXML private Button settingsBtn;
    @FXML private VBox lastRoundsList;

    // In-scene history overlay
    @FXML private StackPane historyOverlay;
    @FXML private VBox historyCard;
    @FXML private Label historyTitleLabel;
    @FXML private TableView<RoundRecord> historyTable;

    // Survives FXML reloads (e.g. language change) so the active game isn't lost.
    // Package-private so RoundResultController.onNewRound can call startNewRound() on it.
    // Sequential betting / insurance turn is derived from GameManager — no UI cursor needed.
    static GameManager sharedGameManager;
    // Injected by the menu before navigating to the game scene.
    private static GameManager pendingGameManager;

    // Round counter shared across FXML reloads. Reset to 1 on a fresh game,
    // incremented by RoundResultController when the user starts the next round.
    static int sharedRoundNumber = 1;

    // If non-null when initialize() runs, the round counter is restored to this
    // value instead of being reset to 1. Used by MenuController.onContinue().
    static Integer pendingResumedRoundNumber;

    private GameManager gameManager;
    private int currentBet;

    // Tracks the previous state so autosave fires exactly once per ROUND_OVER transition.
    private GameState lastObservedState;

    // Card counts per stable key from the previous render — used to detect which
    // cards are NEW so only those get the deal-in animation. STATIC so the map
    // survives FXML reloads (e.g. language change). Without that, the freshly
    // initialised controller would see all on-table cards as new and replay
    // the staggered deal animation on every settings switch. Cleared on a
    // genuine new game.
    private static final Map<String, Integer> lastRenderedCardCount = new HashMap<>();
    private static final Duration DEAL_ANIM_DURATION = Duration.millis(280);
    private static final double DEAL_ANIM_OFFSET_Y = -36;
    // Spacing between consecutive cards when multiple new cards arrive in the
    // same UI tick (typical: opening deal of a round).
    private static final Duration DEAL_STAGGER_STEP = Duration.millis(300);

    // Per-render lookup of "this card slot is the Nth in the staggered deal sequence";
    // computed once at the start of updateUI() and used by renderDealer / buildTableSeat
    // to pick the right per-card delay. Key format: "<containerKey>@<cardIndex>".
    private Map<String, Integer> currentDealSchedule = java.util.Collections.emptyMap();

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
            sharedRoundNumber = (pendingResumedRoundNumber != null)
                    ? pendingResumedRoundNumber : 1;
            pendingResumedRoundNumber = null;
            // Wipe stale per-hand counters so the opening deal of a new game animates.
            lastRenderedCardCount.clear();
        }
        lastObservedState = gameManager.getState();
        updateUI();
        tickAutoTurns(); // resume dealer/bot animation after a reload
        installKeyboardGuard();
    }

    /**
     * Consumes SPACE / ENTER on the game-scene root so the currently-focused
     * button can't re-fire its onAction via keyboard activation. Holding space
     * after clicking, say, Hit would otherwise spam {@code gameManager.hit()}
     * in states where it's invalid — each call throws IllegalStateException,
     * leaking exception logs to stderr and partially mutating round state.
     * The filter lives on the FXML root (this game scene's StackPane), so it
     * disappears automatically when Navigation swaps the root for another scene.
     */
    private void installKeyboardGuard() {
        Platform.runLater(() -> {
            javafx.scene.Scene scene = dealButton.getScene();
            if (scene == null || scene.getRoot() == null) return;
            scene.getRoot().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                javafx.scene.input.KeyCode code = e.getCode();
                if (code == javafx.scene.input.KeyCode.SPACE
                        || code == javafx.scene.input.KeyCode.ENTER) {
                    e.consume();
                }
            });
        });
    }

    private void autosaveIfRoundOver(GameState state) {
        if (state != GameState.ROUND_OVER || lastObservedState == GameState.ROUND_OVER) {
            return;
        }
        try {
            GameSnapshot snap = GameSnapshot.fromGameManager(gameManager, sharedRoundNumber);
            new PersistenceService(SaveSlot.AUTO).save(snap);
        } catch (Exception e) {
            // Autosave is best-effort; never crash the UI on a save failure.
            System.err.println("Autosave failed: " + e.getMessage());
        }
    }

    // ── Action handlers ──────────────────────────────────────────

    @FXML
    private void onChipClicked(ActionEvent event) {
        Object data = ((Button) event.getSource()).getUserData();
        int value = Integer.parseInt(data.toString());
        int cap = Math.min(MAX_BET, currentBettingBalance());
        int newBet = Math.min(currentBet + value, cap);
        if (newBet > currentBet) {
            currentBet = newBet;
            SoundManager.getInstance().play(SoundEvent.CHIP);
            updateUI();
        }
    }

    @FXML
    private void onDoubleBet() {
        int cap = Math.min(MAX_BET, currentBettingBalance());
        int newBet = Math.min(currentBet * 2, cap);
        if (newBet > currentBet) {
            currentBet = newBet;
            SoundManager.getInstance().play(SoundEvent.CHIP);
            updateUI();
        }
    }

    @FXML
    private void onHalfBet() {
        if (currentBet > 0) {
            currentBet = currentBet / 2;
            SoundManager.getInstance().play(SoundEvent.CHIP);
            updateUI();
        }
    }

    @FXML
    private void onClearBet() {
        if (currentBet > 0) {
            currentBet = 0;
            SoundManager.getInstance().play(SoundEvent.CHIP);
            updateUI();
        }
    }

    @FXML
    private void onDealClicked() {
        if (currentBet < MIN_BET) {
            hintLabel.setText("⚠ " + MessageService.getInstance().getMessage("game.message.placeBet")
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
                // Per-card CARD_DEALT cues fire from inside playDealInAnimation as each card
                // animates in, so no single sound here.
                gameManager.deal();
                tickAutoTurns();
            }
        });
    }

    @FXML
    private void onHitClicked() {
        runSafe(() -> {
            gameManager.hit();
            SoundManager.getInstance().play(SoundEvent.CARD);
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
        Stage stage = (Stage) settingsBtn.getScene().getWindow();
        SettingsDialog.show(stage, this::reloadGame);
    }

    @FXML
    private void onQuitClicked() {
        Platform.exit();
    }

    @FXML
    private void onHistoryClicked() {
        MessageService msg = MessageService.getInstance();
        historyTitleLabel.setText(msg.getMessage("game.history.title"));

        historyTable.getColumns().clear();
        historyTable.setItems(FXCollections.observableArrayList(gameManager.getHistory()));
        historyTable.setPlaceholder(new Label(msg.getMessage("game.history.empty")));

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

        TableColumn<RoundRecord, String> colNet = new TableColumn<>(
                msg.getMessage("game.history.col.net"));
        colNet.setCellValueFactory(d -> {
            int delta = computeDelta(d.getValue().bet(), d.getValue().outcome());
            String text = delta > 0 ? "+$" + delta
                        : delta < 0 ? "-$" + Math.abs(delta)
                                    : "$0";
            return new ReadOnlyObjectWrapper<>(text);
        });
        colNet.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("delta-pos", "delta-neg");
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(value);
                    if (value.startsWith("+")) {
                        getStyleClass().add("delta-pos");
                    } else if (value.startsWith("-")) {
                        getStyleClass().add("delta-neg");
                    }
                }
            }
        });

        TableColumn<RoundRecord, String> colOutcome = new TableColumn<>(
                msg.getMessage("game.history.col.outcome"));
        colOutcome.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                msg.getMessage(outcomeKey(d.getValue().outcome()))));

        historyTable.getColumns().addAll(colTime, colPlayer, colBet, colScore, colDealer, colNet, colOutcome);

        historyOverlay.setVisible(true);
        historyOverlay.setManaged(true);
    }

    @FXML
    private void onHideHistoryClicked() {
        hideHistory();
    }

    @FXML
    private void onHistoryBackdropClicked(javafx.scene.input.MouseEvent e) {
        // Only dismiss when the click lands on the backdrop itself, not on the card.
        if (e.getTarget() == historyOverlay) {
            hideHistory();
        }
    }

    private void hideHistory() {
        historyOverlay.setVisible(false);
        historyOverlay.setManaged(false);
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

    private int totalDealtCards() {
        int total = gameManager.getDealer().getHand().getCards().size();
        for (Player p : gameManager.getPlayers()) {
            for (PlayerHand ph : p.getHands()) {
                total += ph.getHand().getCards().size();
            }
        }
        return total;
    }

    // VS_CPU (1 umano): WIN/LOSE in base all'esito dell'umano, silenzio su PUSH.
    // MULTI (>1 umani): un unico ROUND_OVER neutro — gli esiti possono divergere fra giocatori,
    // non ha senso favorirne uno suonando "vittoria".
    private void playRoundOutcomeSfx() {
        int humans = 0;
        boolean anyWin = false;
        boolean anyLose = false;
        for (Player p : gameManager.getPlayers()) {
            if (p.isBot()) continue;
            humans++;
            for (PlayerHand ph : p.getHands()) {
                HandOutcome o = ph.getOutcome();
                if (o == HandOutcome.WIN || o == HandOutcome.BLACKJACK) anyWin = true;
                else if (o == HandOutcome.LOSE) anyLose = true;
            }
        }
        if (humans > 1) {
            SoundManager.getInstance().play(SoundEvent.ROUND_OVER);
        } else if (anyWin) {
            SoundManager.getInstance().play(SoundEvent.WIN);
        } else if (anyLose) {
            SoundManager.getInstance().play(SoundEvent.LOSE);
        }
    }

    private void runSafe(Runnable action) {
        try {
            action.run();
            updateUI();
        } catch (RuntimeException e) {
            hintLabel.setText("⚠ " + e.getMessage());
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
            if (more) {
                SoundManager.getInstance().play(SoundEvent.CARD);
            }
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
            int cardsBefore = totalDealtCards();
            boolean more = gameManager.botStep();
            if (totalDealtCards() > cardsBefore) {
                SoundManager.getInstance().play(SoundEvent.CARD);
            }
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

          autosaveIfRoundOver(state);
          if (state == GameState.ROUND_OVER
                  && lastObservedState != null
                  && lastObservedState != GameState.ROUND_OVER) {
              playRoundOutcomeSfx();
          }

        // Titlebar pills (parametric — set programmatically because FXML %key can't apply MessageFormat)
        roundLabel.setText(msg.getMessage("game.titlebar.round", sharedRoundNumber, TOTAL_ROUNDS));
        deckLabel.setText(msg.getMessage("game.titlebar.deck", gameManager.getDeckRemaining()));

        currentDealSchedule = computeDealSchedule();
        renderDealer();
        renderPlayers(msg, state);
        renderLastRounds();

        currentBetLabel.setText("$" + currentBet);

        renderBalanceHero(msg, state);

        updatePhaseChrome(msg, state);

        boolean gameOver  = state == GameState.GAME_OVER;
        boolean betting   = state == GameState.BETTING;
        boolean playing   = state == GameState.PLAYER_TURN;
        boolean insurance = state == GameState.INSURANCE_OFFER;
        boolean roundOver = state == GameState.ROUND_OVER;

        // Chips: enabled only while betting, and only if adding wouldn't overflow MAX_BET
        updateChip(chip5,   betting);
        updateChip(chip10,  betting);
        updateChip(chip25,  betting);
        updateChip(chip50,  betting);
        updateChip(chip100, betting);
        updateChip(chip250, betting);

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

        lastObservedState = state;
    }

    private void updateChip(Button chip, boolean betting) {
        int cap = Math.min(MAX_BET, currentBettingBalance());
        chip.setDisable(!betting || currentBet >= cap);
    }

    private int currentBettingBalance() {
        int idx = gameManager.currentBettingPlayerIndex();
        if (idx < 0) {
            return Integer.MAX_VALUE;
        }
        return gameManager.getPlayers().get(idx).getBalance();
    }

    /**
     * Drives the right-panel balance hero. Shows the *active* player's balance
     * (whoever is currently betting / playing / answering insurance). When no
     * one is actively up — dealer turn, results — falls back to the first human.
     * In multi-player the owner caption shows the player's name so the change
     * isn't silent.
     */
    private void renderBalanceHero(MessageService msg, GameState state) {
        List<Player> players = gameManager.getPlayers();
        int activeIdx = activePlayerIndex(state);
        Player target = (activeIdx >= 0) ? players.get(activeIdx) : firstHumanOrNull();
        if (target == null && !players.isEmpty()) {
            target = players.get(0);
        }

        int balance = (target != null) ? target.getBalance() : 0;
        balanceLabel.setText("$" + balance);

        String caption = msg.getMessage("game.panel.balance");
        if (players.size() > 1 && target != null) {
            caption = caption + " · " + (target.isBot() ? "🤖 " : "") + target.getName();
        }
        balanceOwnerLabel.setText(caption);
    }

    private Player firstHumanOrNull() {
        for (Player p : gameManager.getPlayers()) {
            if (!p.isBot()) {
                return p;
            }
        }
        return null;
    }

    private boolean isHuman(int idx) {
        if (idx < 0 || idx >= gameManager.getPlayers().size()) return false;
        return !gameManager.getPlayers().get(idx).isBot();
    }

    /**
     * Drives the titlebar phase pill and the hint banner above the seats.
     * One source of truth for "what phase + what should I do".
     */
    private void updatePhaseChrome(MessageService msg, GameState state) {
        // Strip any previous phase variant class.
        phaseLabel.getStyleClass().removeAll(
                "phase-pill-bet", "phase-pill-play", "phase-pill-dealer", "phase-pill-result");

        String phaseText = "";
        String phaseClass = null;
        String hint = "";

        switch (state) {
            case WAITING, BETTING -> {
                phaseText = msg.getMessage("game.phase.bet");
                phaseClass = "phase-pill-bet";
                int bIdx = gameManager.currentBettingPlayerIndex();
                if (bIdx >= 0) {
                    String name = gameManager.getPlayers().get(bIdx).getName();
                    hint = isHuman(bIdx)
                            ? msg.getMessage("game.hint.bet.self")
                            : msg.getMessage("game.hint.bet.other", name);
                } else {
                    hint = msg.getMessage("game.hint.bet.self");
                }
            }
            case DEALING, PLAYER_TURN -> {
                Player current = gameManager.getCurrentPlayer();
                String name = current != null ? current.getName() : "";
                phaseText = msg.getMessage("game.phase.play", name);
                phaseClass = "phase-pill-play";
                if (gameManager.isCurrentPlayerBot()) {
                    hint = msg.getMessage("game.hint.botThinking", name);
                } else {
                    int cIdx = gameManager.getPlayers().indexOf(current);
                    hint = isHuman(cIdx)
                            ? msg.getMessage("game.hint.play.self")
                            : msg.getMessage("game.hint.play.other", name);
                }
            }
            case INSURANCE_OFFER -> {
                phaseText = msg.getMessage("game.phase.play",
                        currentInsuranceName());
                phaseClass = "phase-pill-play";
                hint = msg.getMessage("game.hint.insurance");
            }
            case DEALER_TURN, RESOLVING -> {
                phaseText = msg.getMessage("game.phase.dealer");
                phaseClass = "phase-pill-dealer";
                hint = msg.getMessage("game.hint.dealer");
            }
            case ROUND_OVER -> {
                phaseText = msg.getMessage("game.phase.result");
                phaseClass = "phase-pill-result";
                hint = msg.getMessage("game.hint.roundOver");
            }
            case GAME_OVER -> {
                phaseText = msg.getMessage("game.phase.result");
                phaseClass = "phase-pill-result";
                hint = msg.getMessage("game.message.gameOver");
            }
        }

        phaseLabel.setText(phaseText);
        if (phaseClass != null) {
            phaseLabel.getStyleClass().add(phaseClass);
        }
        boolean show = !phaseText.isEmpty();
        phaseLabel.setVisible(show);
        phaseLabel.setManaged(show);

        hintLabel.setText(hint);
    }

    private String currentInsuranceName() {
        int idx = gameManager.currentInsurancePlayerIndex();
        return (idx >= 0) ? gameManager.getPlayers().get(idx).getName() : "";
    }

    private void renderDealer() {
        dealerCardsBox.getChildren().clear();
        boolean revealed = gameManager.getDealer().isHandRevealed();
        List<Card> dealerCards = gameManager.getDealer().getHand().getCards();
        int animateFrom = animateFromIndex("dealer", dealerCards.size());
        for (int i = 0; i < dealerCards.size(); i++) {
            Card visible = (i == 1 && !revealed) ? null : dealerCards.get(i);
            CardView cv = new CardView(visible);
            if (i >= animateFrom) {
                playDealInAnimation(cv, dealDelayFor("dealer", i));
            }
            dealerCardsBox.getChildren().add(cv);
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

    /**
     * Returns the first card index that should be animated for a given hand
     * (dealer or per-player) given how many cards it had on the previous
     * render. Cards already shown last time keep their static look; only the
     * newly-arrived tail fades in from above. Also updates the tracker.
     * Returns {@code Integer.MAX_VALUE} (no animation) when the hand has not
     * grown — e.g. a round reset, an unrelated UI tick like a chip click, or
     * the initial render after a controller reload.
     */
    private int animateFromIndex(String key, int currentCount) {
        int prev = lastRenderedCardCount.getOrDefault(key, 0);
        lastRenderedCardCount.put(key, currentCount);
        return currentCount > prev ? prev : Integer.MAX_VALUE;
    }

    /**
     * Builds the deal-order schedule for the upcoming render: each newly-arrived
     * card gets a sequential step index, so multiple cards appearing in the same
     * tick (the opening deal of a round) animate one after the other instead of
     * all at once. Order matches a real-life dealer: round-by-round, each player
     * in seat order, with the dealer last in every round.
     *
     * Must be called BEFORE renderDealer/renderPlayers since those mutate
     * {@link #lastRenderedCardCount} via {@link #animateFromIndex}.
     */
    private Map<String, Integer> computeDealSchedule() {
        List<Player> players = gameManager.getPlayers();
        int dealerCount = gameManager.getDealer().getHand().getCards().size();
        int dealerPrev  = lastRenderedCardCount.getOrDefault("dealer", 0);

        int maxIdx = dealerCount;
        for (Player p : players) {
            for (PlayerHand ph : p.getHands()) {
                maxIdx = Math.max(maxIdx, ph.getHand().getCards().size());
            }
        }

        Map<String, Integer> schedule = new HashMap<>();
        int step = 0;
        for (int cardIdx = 0; cardIdx < maxIdx; cardIdx++) {
            // Players (in seat order) for this round.
            for (Player p : players) {
                int handIdx = 0;
                for (PlayerHand ph : p.getHands()) {
                    String key  = "p:" + p.getName() + "#" + handIdx;
                    int prev = lastRenderedCardCount.getOrDefault(key, 0);
                    int curr = ph.getHand().getCards().size();
                    if (cardIdx >= prev && cardIdx < curr) {
                        schedule.put(key + "@" + cardIdx, step++);
                    }
                    handIdx++;
                }
            }
            // Dealer goes last in each round.
            if (cardIdx >= dealerPrev && cardIdx < dealerCount) {
                schedule.put("dealer@" + cardIdx, step++);
            }
        }
        return schedule;
    }

    /** Per-card animation delay derived from the current deal schedule. */
    private Duration dealDelayFor(String containerKey, int cardIndex) {
        Integer step = currentDealSchedule.get(containerKey + "@" + cardIndex);
        return step != null ? DEAL_STAGGER_STEP.multiply(step) : Duration.ZERO;
    }

    /**
     * Fade + slide-down from ~36px above; runs once on next layout pulse.
     * Also fires a CARD_DEALT cue synced with the visual start, interrupting
     * any in-flight CARD_DEALT clip so rapid-fire deals don't layer.
     */
    private void playDealInAnimation(javafx.scene.Node node, Duration delay) {
        node.setOpacity(0);
        node.setTranslateY(DEAL_ANIM_OFFSET_Y);
        FadeTransition fade = new FadeTransition(DEAL_ANIM_DURATION, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(DEAL_ANIM_DURATION, node);
        slide.setFromY(DEAL_ANIM_OFFSET_Y);
        slide.setToY(0);
        ParallelTransition anim = new ParallelTransition(fade, slide);
        if (delay != null && delay.greaterThan(Duration.ZERO)) {
            anim.setDelay(delay);
        }
        anim.play();

        // Per-card CARD_DEALT cue — only during the staggered opening deal
        // (multiple new cards in the same render). Single-card events (hit,
        // dealer step, bot step) keep their existing CARD sound played by the
        // caller; firing CARD_DEALT here too would double up.
        if (currentDealSchedule.size() > 1) {
            PauseTransition audioCue = new PauseTransition(
                    (delay != null && delay.greaterThan(Duration.ZERO)) ? delay : Duration.millis(1));
            audioCue.setOnFinished(e ->
                    SoundManager.getInstance().playInterrupting(SoundEvent.CARD_DEALT));
            audioCue.play();
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
            // Benched: no cards rendered, seat is dimmed and nudged slightly
            // lower so it visually sits below the active players' card row
            // without crowding the action bar underneath.
            seat.getStyleClass().add("seat-card-sitting-out");
            seat.setTranslateY(18);
            seat.getChildren().add(labeled(displayName, "seat-card-name"));
            seat.getChildren().add(labeled("⏸ " + msg.getMessage("game.message.sittingOut"),
                    "seat-card-score"));
            return seat;
        }

        // Reserve the size the seat will need once two cards are dealt, so the
        // layout doesn't snap from a tiny name-only box into a card-sized one
        // when betting ends. Width = 2 cards (92×2) + spacing + padding; height
        // = card (130) + score label + name label + padding.
        seat.setMinSize(214, 192);

        PlayerHand activeHand = gameManager.getCurrentHand();
        HBox handsRow = new HBox(12);
        handsRow.setAlignment(Pos.CENTER);
        int handIdx = 0;
        for (PlayerHand ph : player.getHands()) {
            VBox handBox = new VBox(4);
            handBox.setAlignment(Pos.CENTER);
            HBox cardsRow = new HBox(4);
            cardsRow.setAlignment(Pos.CENTER);
            List<Card> handCards = ph.getHand().getCards();
            String key = "p:" + player.getName() + "#" + handIdx;
            int animateFrom = animateFromIndex(key, handCards.size());
            for (int i = 0; i < handCards.size(); i++) {
                CardView cv = new CardView(handCards.get(i));
                if (i >= animateFrom) {
                    playDealInAnimation(cv, dealDelayFor(key, i));
                }
                cardsRow.getChildren().add(cv);
            }
            handIdx++;
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
            avatar.getStyleClass().add("seat-avatar-p" + (index + 1));
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

        if (isActive && player.isBot()
                && gameManager.getState() == GameState.PLAYER_TURN
                && gameManager.isCurrentPlayerBot()) {
            row.getChildren().add(labeled(msg.getMessage("game.panel.thinking"), "turn-badge-bot"));
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
