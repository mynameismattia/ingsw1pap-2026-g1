// Il cuore della UI di gioco.
// Renderizza dealer, seat dei player, chip slot, balance card e action bar.
// Gestisce gli handler di chip/hit/stand/double/split/insurance, le animazioni (deal-in, dealer flip 3D, chip-fly, breathing del seat attivo, balance tween) e l'autosave a fine round.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.GameRules;
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
import ch.supsi.dti.frontend.view.Icons;
import ch.supsi.dti.frontend.view.UiFactory;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
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
import javafx.scene.layout.Region;
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

    private static final int INITIAL_BALANCE = GameRules.DEFAULT_BALANCE;
    private static final int MIN_BET = GameRules.MIN_BET;
    private static final int MAX_BET = GameRules.MAX_BET;
    private static final Duration DEALER_STEP_DELAY = Duration.millis(800);
    private static final Duration BOT_STEP_DELAY = Duration.millis(600);
    static final int TOTAL_ROUNDS = 10;

    private Timeline dealerTimeline;
    private Timeline botTimeline;

    @FXML private Label roundLabel;
    @FXML private Label deckLabel;
    @FXML private Label phaseLabel;

    @FXML private VBox sidePlayersList;
    @FXML private Button historyButton;

    @FXML private VBox dealerTray;
    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScoreLabel;
    @FXML private HBox playersRow;
    @FXML private Label hintLabel;
    @FXML private StackPane bottomSlot;
    @FXML private HBox chipsSlot;
    @FXML private HBox actionBar;

    @FXML private Button hitButton;
    @FXML private Button standButton;
    @FXML private Button doubleButton;
    @FXML private Button splitButton;
    @FXML private Button insureButton;
    @FXML private Button declineInsuranceButton;
    @FXML private Button viewResultsButton;

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
    @FXML private StackPane rootPane;

    private Timeline activeSeatPulse;
    private Timeline balanceTween;
    private int lastBalanceShown = -1;

    private boolean lastDealerRevealed = false;

    @FXML private StackPane historyOverlay;
    @FXML private VBox historyCard;
    @FXML private Label historyTitleLabel;
    @FXML private TableView<RoundRecord> historyTable;

    @FXML private StackPane gameOverOverlay;
    @FXML private Label gameOverEyebrow;
    @FXML private Label gameOverTitle;
    @FXML private Label gameOverBody;
    @FXML private Label gameOverStandingsHeader;
    @FXML private VBox gameOverBalances;
    @FXML private Label gameOverFooter;

    static GameManager sharedGameManager;

    private static GameManager pendingGameManager;

    static int sharedRoundNumber = 1;

    static Integer pendingResumedRoundNumber;

    private GameManager gameManager;
    private int currentBet;

    private GameState lastObservedState;

    private static final Map<String, Integer> lastRenderedCardCount = new HashMap<>();
    private static final Duration DEAL_ANIM_DURATION = Duration.millis(280);
    private static final double DEAL_ANIM_OFFSET_Y = -36;

    private static final Duration DEAL_STAGGER_STEP = Duration.millis(300);

    private Map<String, Integer> currentDealSchedule = java.util.Collections.emptyMap();

    private int dealAnimsRunning = 0;

    public static void setPendingGameManager(GameManager gm) {
        pendingGameManager = gm;
    }

    @FXML
    public void initialize() {
        // 1. Se c'è un GameManager "pending" (iniettato da MenuController o LoadGameController) lo prendo come shared
        //    e gli faccio partire subito il primo round. Marca questo come "freshGame" per resettare lo stato.
        boolean freshGame = false;
        if (pendingGameManager != null) {
            sharedGameManager = pendingGameManager;
            pendingGameManager = null;
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        // 2. Fallback (es. dopo un reload FXML per cambio lingua): se shared è null, ne creo uno solo-player di default.
        if (sharedGameManager == null) {
            sharedGameManager = new GameManager(List.of(new Player("Player 1", INITIAL_BALANCE)));
            sharedGameManager.startNewRound();
            freshGame = true;
        }
        gameManager = sharedGameManager;
        currentBet = 0;
        dealAnimsRunning = 0;

        // 3. Solo per fresh game (no reload FXML): aggiorno il round number e azzero il tracker di animazioni carte.
        //    pendingResumedRoundNumber arriva da LoadGameController quando si riprende un save.
        if (freshGame) {
            sharedRoundNumber = (pendingResumedRoundNumber != null)
                    ? pendingResumedRoundNumber : 1;
            pendingResumedRoundNumber = null;
            lastRenderedCardCount.clear();
        }

        // 4. Render iniziale + auto-tick dei turni bot/dealer + filtro tastiera che evita SPAZIO/ENTER accidentali.
        lastObservedState = gameManager.getState();
        updateUI();
        tickAutoTurns();
        installKeyboardGuard();
    }

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
        // 1. Eseguo l'autosave solo sull'edge "passaggio a ROUND_OVER": una volta sola per round, non a ogni updateUI.
        if (state != GameState.ROUND_OVER || lastObservedState == GameState.ROUND_OVER) {
            return;
        }
        try {
            // 2. Costruisco lo snapshot dello stato attuale e lo salvo nello slot AUTO via PersistenceService.
            GameSnapshot snap = GameSnapshot.fromGameManager(gameManager, sharedRoundNumber);
            new PersistenceService(SaveSlot.AUTO).save(snap);
        } catch (Exception e) {
            // 3. Errore di scrittura non fatale: log su stderr, la partita continua senza save.
            System.err.println("Autosave failed: " + e.getMessage());
        }
    }

    @FXML
    private void onChipClicked(ActionEvent event) {
        // 1. Leggo il valore della chip cliccata dal suo userData FXML (es. "25").
        Button source = (Button) event.getSource();
        int value = Integer.parseInt(source.getUserData().toString());

        // 2. Calcolo il nuovo bet rispettando il cap (min tra MAX_BET globale e saldo disponibile del giocatore corrente).
        int cap = Math.min(MAX_BET, currentBettingBalance());
        int newBet = Math.min(currentBet + value, cap);

        // 3. Se il bet è effettivamente cresciuto: aggiorno, suono "chip", animo la mini-chip che vola nel bet display, refresh UI.
        if (newBet > currentBet) {
            currentBet = newBet;
            SoundManager.getInstance().play(SoundEvent.CHIP);
            flyChipFromButton(source);
            updateUI();
        }
    }

    private void flyChipFromButton(Button source) {
        if (rootPane == null) return;
        Bounds sBounds = source.localToScreen(source.getBoundsInLocal());
        Bounds eBounds = currentBetLabel.localToScreen(currentBetLabel.getBoundsInLocal());
        if (sBounds == null || eBounds == null) return;

        Point2D rootStart = rootPane.screenToLocal(
                sBounds.getMinX() + sBounds.getWidth() / 2.0,
                sBounds.getMinY() + sBounds.getHeight() / 2.0);
        Point2D rootEnd = rootPane.screenToLocal(
                eBounds.getMinX() + eBounds.getWidth() / 2.0,
                eBounds.getMinY() + eBounds.getHeight() / 2.0);
        if (rootStart == null || rootEnd == null) return;

        String chipClass = source.getStyleClass().stream()
                .filter(c -> c.startsWith("chip-") && !c.equals("chip-button"))
                .findFirst().orElse("chip-25");

        Region flyChip = new Region();
        flyChip.getStyleClass().addAll(chipClass, "chip-fly");
        flyChip.setMouseTransparent(true);
        StackPane.setAlignment(flyChip, Pos.TOP_LEFT);
        rootPane.getChildren().add(flyChip);

        flyChip.setTranslateX(rootStart.getX() - 20);
        flyChip.setTranslateY(rootStart.getY() - 20);

        TranslateTransition tt = new TranslateTransition(Duration.millis(280), flyChip);
        tt.setFromX(rootStart.getX() - 20);
        tt.setFromY(rootStart.getY() - 20);
        tt.setToX(rootEnd.getX() - 20);
        tt.setToY(rootEnd.getY() - 20);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition st = new ScaleTransition(Duration.millis(280), flyChip);
        st.setFromX(1.0); st.setToX(0.4);
        st.setFromY(1.0); st.setToY(0.4);

        FadeTransition ft = new FadeTransition(Duration.millis(280), flyChip);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        ParallelTransition fly = new ParallelTransition(tt, st, ft);
        fly.setOnFinished(e -> rootPane.getChildren().remove(flyChip));
        fly.play();
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

            int bettingIdx = gameManager.currentBettingPlayerIndex();
            if (bettingIdx < 0) {
                return;
            }
            gameManager.placeBet(bettingIdx, currentBet);
            currentBet = 0;
            if (gameManager.currentBettingPlayerIndex() < 0) {

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
        navigateTo("/ui/roundresult.fxml");
    }

    @FXML
    private void onBackToMenuClicked() {
        stopDealerTimeline();
        stopBotTimeline();
        navigateTo("/ui/menu.fxml");
    }

    @FXML
    private void onSettingsClicked() {
        Stage stage = (Stage) settingsBtn.getScene().getWindow();
        SettingsDialog.show(stage, this::reloadGame);
    }

    @FXML
    private void onQuitClicked() {
        showQuitConfirm();
    }

    private void showQuitConfirm() {
        if (rootPane == null) {
            Platform.exit();
            return;
        }
        MessageService msg = MessageService.getInstance();
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("overlay-backdrop");
        backdrop.setOnMouseClicked(e -> {
            if (e.getTarget() == backdrop) {
                rootPane.getChildren().remove(backdrop);
            }
        });

        VBox card = new VBox(14);
        card.getStyleClass().add("overlay-card");
        card.setMaxWidth(380);
        card.setMaxHeight(180);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new javafx.geometry.Insets(24, 28, 24, 28));

        Label title = new Label(msg.getMessage("game.quit.confirm.title"));
        title.getStyleClass().add("dialog-header");
        Label body = new Label(msg.getMessage("game.quit.confirm.body"));
        body.getStyleClass().add("dialog-body");
        body.setWrapText(true);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);
        Button cancel = new Button(msg.getMessage("common.cancel"));
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(e -> rootPane.getChildren().remove(backdrop));
        Button confirm = new Button(msg.getMessage("game.action.quit"));
        confirm.getStyleClass().add("danger-button");
        confirm.setOnAction(e -> Platform.exit());
        buttons.getChildren().addAll(cancel, confirm);

        card.getChildren().addAll(title, body, buttons);
        backdrop.getChildren().add(card);
        rootPane.getChildren().add(backdrop);
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
            int delta = d.getValue().net();
            return new ReadOnlyObjectWrapper<>(UiFactory.formatDelta(delta));
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

        if (e.getTarget() == historyOverlay) {
            hideHistory();
        }
    }

    private void hideHistory() {
        historyOverlay.setVisible(false);
        historyOverlay.setManaged(false);
    }

    private boolean isHumansBroke(GameState state) {

        if (state != GameState.BETTING && state != GameState.WAITING
                && state != GameState.ROUND_OVER && state != GameState.GAME_OVER) {
            return false;
        }
        boolean anyHuman = false;
        for (Player p : gameManager.getPlayers()) {
            if (p.isBot()) continue;
            anyHuman = true;
            if (p.getBalance() >= MIN_BET) return false;
        }
        return anyHuman;
    }

    private static final String[] PODIUM_MEDALS = {Icons.MEDAL, Icons.MEDAL, Icons.MEDAL};
    private static final String[] MEDAL_COLORS = {"#FFD700", "#C0C0C0", "#CD7F32"};

    private void renderGameOverOverlay(MessageService msg, boolean gameOver) {
        if (!gameOver) {
            gameOverOverlay.setVisible(false);
            gameOverOverlay.setManaged(false);
            return;
        }

        boolean backendOver = gameManager.getState() == GameState.GAME_OVER;
        gameOverEyebrow.setText(msg.getMessage("game.gameover.eyebrow"));
        gameOverTitle.setText(msg.getMessage("game.gameover.title"));
        gameOverBody.setText(msg.getMessage(backendOver
                ? "game.gameover.body.all"
                : "game.gameover.body.humans"));
        gameOverStandingsHeader.setText(msg.getMessage("game.gameover.standings"));

        gameOverBalances.getChildren().clear();
        List<Player> ranked = new java.util.ArrayList<>(gameManager.getPlayers());
        ranked.sort((a, b) -> Integer.compare(b.getBalance(), a.getBalance()));
        int topBalance = ranked.isEmpty() ? 0 : ranked.get(0).getBalance();
        for (int i = 0; i < ranked.size(); i++) {
            gameOverBalances.getChildren().add(buildStandingRow(ranked.get(i), i, topBalance));
        }

        int roundsPlayed = Math.max(0, sharedRoundNumber - 1);
        int biggestPot = 0;
        for (RoundRecord r : gameManager.getHistory()) {
            if (r.bet() > biggestPot) biggestPot = r.bet();
        }
        gameOverFooter.setText(msg.getMessage(
                "game.gameover.footer", roundsPlayed, TOTAL_ROUNDS, biggestPot));

        gameOverOverlay.setVisible(true);
        gameOverOverlay.setManaged(true);
        gameOverOverlay.toFront();
    }

    private HBox buildStandingRow(Player p, int rank, int topBalance) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("gameover-row");
        if (rank == 0 && topBalance > 0) {
            row.getStyleClass().add("gameover-row-winner");
        }

        Label medal = new Label(rank < PODIUM_MEDALS.length ? PODIUM_MEDALS[rank] : "  ");
        medal.getStyleClass().add("gameover-row-medal");
        if (rank < PODIUM_MEDALS.length) {
            medal.getStyleClass().add(Icons.STYLE_CLASS);
            medal.setStyle("-fx-text-fill: " + MEDAL_COLORS[rank] + ";");
        }

        Label name = new Label(p.getName());
        name.getStyleClass().add("gameover-row-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);

        Label bal = new Label("$" + p.getBalance());
        bal.getStyleClass().add(p.getBalance() < MIN_BET
                ? "gameover-row-broke"
                : "gameover-row-balance");

        row.getChildren().add(medal);
        if (p.isBot()) {
            row.getChildren().add(botIcon());
        }
        row.getChildren().addAll(name, bal);
        return row;
    }

    private void navigateTo(String fxml) {
        Navigation.navigate((Stage) dealButton.getScene().getWindow(), fxml);
    }

    private void reloadGame() {
        stopDealerTimeline();
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

    private void refreshActionAvailability() {
        if (gameManager == null) return;
        updateUI();
    }

    private void runSafe(Runnable action) {
        try {
            action.run();
            updateUI();
        } catch (RuntimeException e) {
            hintLabel.setText("⚠ " + e.getMessage());
        }
    }

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

        roundLabel.setText(msg.getMessage("game.titlebar.round", sharedRoundNumber, TOTAL_ROUNDS));
        deckLabel.setText(msg.getMessage("game.titlebar.deck", gameManager.getDeckRemaining()));

        currentDealSchedule = computeDealSchedule();
        renderDealer();
        renderPlayers(msg, state);

        currentBetLabel.setText("$" + currentBet);

        renderBalanceHero(msg, state);

        updatePhaseChrome(msg, state);

        boolean gameOver  = state == GameState.GAME_OVER || isHumansBroke(state);
        boolean betting   = state == GameState.BETTING;
        boolean playing   = state == GameState.PLAYER_TURN;
        boolean insurance = state == GameState.INSURANCE_OFFER;
        boolean roundOver = state == GameState.ROUND_OVER;

        renderGameOverOverlay(msg, gameOver);

        chipsSlot.setVisible(betting);
        chipsSlot.setManaged(betting);
        actionBar.setVisible(!betting);
        actionBar.setManaged(!betting);

        updateChip(chip5,   betting);
        updateChip(chip10,  betting);
        updateChip(chip25,  betting);
        updateChip(chip50,  betting);
        updateChip(chip100, betting);
        updateChip(chip250, betting);

        boolean botActive = gameManager.isCurrentPlayerBot();
        boolean dealingAnim = dealAnimsRunning > 0;
        boolean humanCanAct = playing && !botActive && !dealingAnim;

        dealButton.setDisable(gameOver || dealingAnim || !betting || currentBet < MIN_BET);
        boolean dealReady = !dealButton.isDisable();
        if (dealReady && !dealButton.getStyleClass().contains("deal-button-ready")) {
            dealButton.getStyleClass().add("deal-button-ready");
        } else if (!dealReady) {
            dealButton.getStyleClass().remove("deal-button-ready");
        }
        hitButton.setDisable(gameOver || !humanCanAct);
        standButton.setDisable(gameOver || !humanCanAct);

        doubleButton.setDisable(gameOver || dealingAnim || botActive || !gameManager.canDoubleDown());
        doubleButton.setVisible(playing);
        doubleButton.setManaged(playing);

        splitButton.setDisable(gameOver || dealingAnim || botActive || !gameManager.canSplit());
        splitButton.setVisible(playing);
        splitButton.setManaged(playing);

        insureButton.setDisable(gameOver || dealingAnim || !insurance || botActive);
        insureButton.setVisible(insurance);
        insureButton.setManaged(insurance);

        declineInsuranceButton.setDisable(gameOver || dealingAnim || !insurance || botActive);
        declineInsuranceButton.setVisible(insurance);
        declineInsuranceButton.setManaged(insurance);

        viewResultsButton.setVisible(roundOver);
        viewResultsButton.setManaged(roundOver);

        historyButton.setDisable(gameManager.getHistory().isEmpty());

        lastObservedState = state;
    }

    private void updateChip(Button chip, boolean betting) {
        int cap = Math.min(MAX_BET, currentBettingBalance());
        int chipValue = Integer.parseInt(chip.getUserData().toString());
        chip.setDisable(!betting || currentBet + chipValue > cap);
    }

    private int currentBettingBalance() {
        int idx = gameManager.currentBettingPlayerIndex();
        if (idx < 0) {
            return Integer.MAX_VALUE;
        }
        return gameManager.getPlayers().get(idx).getBalance();
    }

    private void renderBalanceHero(MessageService msg, GameState state) {
        List<Player> players = gameManager.getPlayers();
        int activeIdx = activePlayerIndex(state);
        Player target = (activeIdx >= 0) ? players.get(activeIdx) : firstHumanOrNull();
        if (target == null && !players.isEmpty()) {
            target = players.get(0);
        }

        int balance = (target != null) ? target.getBalance() : 0;
        tweenBalanceTo(balance);

        String caption = msg.getMessage("game.panel.balance");
        if (players.size() > 1 && target != null) {
            caption = caption + " · " + target.getName();
        }
        balanceOwnerLabel.setText(caption);
    }

    private void tweenBalanceTo(int newValue) {
        // 1. Se c'è un tween in corso lo fermo (evita race tra animazioni accavallate, es. due render veloci di seguito).
        if (balanceTween != null) {
            balanceTween.stop();
            balanceTween = null;
        }

        // 2. Primo render della partita o valore invariato: setText immediato, niente animazione.
        if (lastBalanceShown < 0 || lastBalanceShown == newValue) {
            balanceLabel.setText("$" + newValue);
            lastBalanceShown = newValue;
            return;
        }

        // 3. Tween numerico: una IntegerProperty interpolata da oldValue a newValue in 600ms; il listener aggiorna il label
        //    a ogni frame con "$N" intermedio (es. $100 → $108 → $123 → $215). Più "casino-feel" di un setText secco.
        int oldValue = lastBalanceShown;
        IntegerProperty counter = new SimpleIntegerProperty(oldValue);
        counter.addListener((obs, o, n) -> balanceLabel.setText("$" + n.intValue()));
        balanceTween = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(counter, oldValue)),
            new KeyFrame(Duration.millis(600), new KeyValue(counter, newValue))
        );
        balanceTween.play();
        lastBalanceShown = newValue;

        // 4. Color flash: verde se il saldo è salito, rosso se sceso. Tolto via PauseTransition dopo 800ms.
        String flashClass = (newValue > oldValue) ? "balance-flash-up" : "balance-flash-down";
        balanceLabel.getStyleClass().remove("balance-flash-up");
        balanceLabel.getStyleClass().remove("balance-flash-down");
        balanceLabel.getStyleClass().add(flashClass);
        PauseTransition clear = new PauseTransition(Duration.millis(800));
        clear.setOnFinished(e -> balanceLabel.getStyleClass().remove(flashClass));
        clear.play();
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

    private void updatePhaseChrome(MessageService msg, GameState state) {

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
        boolean revealed = gameManager.getDealer().isHandRevealed();
        List<Card> dealerCards = gameManager.getDealer().getHand().getCards();
        boolean showTray = !dealerCards.isEmpty();
        dealerTray.setVisible(showTray);
        dealerTray.setManaged(showTray);

        boolean justRevealed = revealed && !lastDealerRevealed
                && dealerCards.size() >= 2
                && dealerCardsBox.getChildren().size() == dealerCards.size();
        lastDealerRevealed = revealed;

        if (justRevealed) {
            flipDealerHoleCard(dealerCards.get(1));
        } else {
            dealerCardsBox.getChildren().clear();
            int animateFrom = animateFromIndex("dealer", dealerCards.size());
            for (int i = 0; i < dealerCards.size(); i++) {
                Card visible = (i == 1 && !revealed) ? null : dealerCards.get(i);
                CardView cv = new CardView(visible);
                if (i >= animateFrom) {
                    playDealInAnimation(cv, dealDelayFor("dealer", i));
                }
                dealerCardsBox.getChildren().add(cv);
            }
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

    private void flipDealerHoleCard(Card revealedCard) {
        // 1. Guard: serve la seconda carta (la hole card) presente nella box.
        if (dealerCardsBox.getChildren().size() < 2) return;

        // 2. Fase 1 del flip — la carta-retro si schiaccia da scale-X=1 a 0 in 220ms (sembra di taglio).
        Node back = dealerCardsBox.getChildren().get(1);
        ScaleTransition shrink = new ScaleTransition(Duration.millis(220), back);
        shrink.setFromX(1.0);
        shrink.setToX(0.0);
        shrink.setInterpolator(Interpolator.EASE_IN);
        shrink.setOnFinished(e -> {
            // 3. A metà del flip: swap istantaneo del retro con la carta-fronte vera (scale-X=0 per ora invisibile).
            int idx = dealerCardsBox.getChildren().indexOf(back);
            CardView face = new CardView(revealedCard);
            face.setScaleX(0.0);
            dealerCardsBox.getChildren().remove(back);
            dealerCardsBox.getChildren().add(idx, face);

            // 4. Fase 2 del flip — la carta-fronte cresce da scale-X=0 a 1 in altri 220ms (riemerge da taglio).
            ScaleTransition grow = new ScaleTransition(Duration.millis(220), face);
            grow.setFromX(0.0);
            grow.setToX(1.0);
            grow.setInterpolator(Interpolator.EASE_OUT);
            grow.play();
        });
        shrink.play();
    }

    private int animateFromIndex(String key, int currentCount) {
        int prev = lastRenderedCardCount.getOrDefault(key, 0);
        lastRenderedCardCount.put(key, currentCount);
        return currentCount > prev ? prev : Integer.MAX_VALUE;
    }

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

            if (cardIdx >= dealerPrev && cardIdx < dealerCount) {
                schedule.put("dealer@" + cardIdx, step++);
            }
        }
        return schedule;
    }

    private Duration dealDelayFor(String containerKey, int cardIndex) {
        Integer step = currentDealSchedule.get(containerKey + "@" + cardIndex);
        return step != null ? DEAL_STAGGER_STEP.multiply(step) : Duration.ZERO;
    }

    private void playDealInAnimation(javafx.scene.Node node, Duration delay) {
        double startRotation = (Math.random() - 0.5) * 6;
        node.setOpacity(0);
        node.setTranslateY(DEAL_ANIM_OFFSET_Y);
        node.setScaleX(0.92);
        node.setScaleY(0.92);
        node.setRotate(startRotation);

        FadeTransition fade = new FadeTransition(DEAL_ANIM_DURATION, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(DEAL_ANIM_DURATION, node);
        slide.setFromY(DEAL_ANIM_OFFSET_Y);
        slide.setToY(0);
        ScaleTransition scale = new ScaleTransition(DEAL_ANIM_DURATION, node);
        scale.setFromX(0.92); scale.setToX(1.0);
        scale.setFromY(0.92); scale.setToY(1.0);
        RotateTransition spin = new RotateTransition(DEAL_ANIM_DURATION, node);
        spin.setFromAngle(startRotation);
        spin.setToAngle(0);

        ParallelTransition anim = new ParallelTransition(fade, slide, scale, spin);
        anim.setInterpolator(Interpolator.EASE_OUT);
        if (delay != null && delay.greaterThan(Duration.ZERO)) {
            anim.setDelay(delay);
        }

        boolean isOpeningDeal = currentDealSchedule.size() > 1;
        if (isOpeningDeal) {
            dealAnimsRunning++;
            anim.setOnFinished(e -> {
                dealAnimsRunning = Math.max(0, dealAnimsRunning - 1);
                if (dealAnimsRunning == 0) {
                    refreshActionAvailability();
                }
            });
        }
        anim.play();

        if (currentDealSchedule.size() > 1) {
            PauseTransition audioCue = new PauseTransition(
                    (delay != null && delay.greaterThan(Duration.ZERO)) ? delay : Duration.millis(1));
            audioCue.setOnFinished(e ->
                    SoundManager.getInstance().playInterrupting(SoundEvent.CARD_DEALT));
            audioCue.play();
        }
    }

    private void renderPlayers(MessageService msg, GameState state) {
        // 1. Stoppo l'animazione di breathing del seat attivo precedente (sarà riattaccata sotto al nuovo).
        if (activeSeatPulse != null) {
            activeSeatPulse.stop();
            activeSeatPulse = null;
        }

        // 2. Svuoto entrambe le viste dei player: la riga sul felt (playersRow) e la sidebar a sinistra (sidePlayersList).
        playersRow.getChildren().clear();
        sidePlayersList.getChildren().clear();

        // 3. Per ogni player creo un seat sul felt + una row nella sidebar. Quello "attivo" (turno corrente) riceve il glow pulsante.
        List<Player> players = gameManager.getPlayers();
        int activeIdx = activePlayerIndex(state);
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            boolean isActive = (i == activeIdx);
            VBox seat = buildTableSeat(p, isActive, msg, i);
            if (isActive) {
                attachActiveSeatBreathing(seat);
            }
            playersRow.getChildren().add(seat);
            sidePlayersList.getChildren().add(buildSideSeatRow(p, isActive, msg, i));
        }
    }

    private void attachActiveSeatBreathing(VBox seat) {
        // 1. Definisco i due estremi del glow (alpha 0.45 → 0.65) sull'accent-blue della palette.
        Color base = Color.color(96 / 255.0, 165 / 255.0, 250 / 255.0, 0.45);
        Color peak = Color.color(96 / 255.0, 165 / 255.0, 250 / 255.0, 0.65);

        // 2. Applico un DropShadow al seat e lo metto come effect "vivo".
        DropShadow glow = new DropShadow();
        glow.setRadius(28);
        glow.setSpread(0.45);
        glow.setColor(base);
        seat.setEffect(glow);

        // 3. Timeline infinita che oscilla il colore del glow tra base e peak (ciclo 1.8s). È il "respiro" del seat attivo.
        activeSeatPulse = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.colorProperty(), base)),
            new KeyFrame(Duration.millis(900), new KeyValue(glow.colorProperty(), peak)),
            new KeyFrame(Duration.millis(1800), new KeyValue(glow.colorProperty(), base))
        );
        activeSeatPulse.setCycleCount(Animation.INDEFINITE);
        activeSeatPulse.play();
    }

    private static Region makeFlexSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private int activePlayerIndex(GameState state) {
        return switch (state) {
            case BETTING         -> gameManager.currentBettingPlayerIndex();
            case INSURANCE_OFFER -> gameManager.currentInsurancePlayerIndex();
            case PLAYER_TURN     -> gameManager.getPlayers().indexOf(gameManager.getCurrentPlayer());
            default              -> -1;
        };
    }

    private VBox buildTableSeat(Player player, boolean isActive, MessageService msg, int seatIndex) {
        VBox seat = new VBox(6);
        seat.setAlignment(Pos.CENTER);
        seat.getStyleClass().add("seat-card");
        if (isActive) {
            seat.getStyleClass().add("seat-card-active");
        }
        String displayName = player.getName();

        if (player.isSittingOut()) {

            seat.getStyleClass().add("seat-card-sitting-out");
            seat.setTranslateY(18);
            seat.getChildren().add(labeled(displayName, "seat-card-name"));
            seat.getChildren().add(labeled("⏸ " + msg.getMessage("game.message.sittingOut"),
                    "seat-card-score"));
            return seat;
        }

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

        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        nameRow.setMaxWidth(Double.MAX_VALUE);
        nameRow.getChildren().add(UiFactory.avatar(player, seatIndex, "seat-card-avatar"));
        nameRow.getChildren().add(labeled(displayName, "seat-card-name"));
        Region nameFlex = new Region();
        HBox.setHgrow(nameFlex, Priority.ALWAYS);
        nameRow.getChildren().add(nameFlex);
        nameRow.getChildren().add(labeled("$" + player.getBalance(), "seat-card-balance"));
        seat.getChildren().add(nameRow);

        int seatBet = 0;
        for (PlayerHand ph : player.getHands()) {
            seatBet += ph.getBet();
        }
        if (seatBet == 0
                && gameManager.getState() == GameState.BETTING
                && seatIndex == gameManager.currentBettingPlayerIndex()) {
            seatBet = currentBet;
        }
        if (seatBet > 0) {
            HBox betRow = new HBox(8);
            betRow.setAlignment(Pos.CENTER_LEFT);
            betRow.setMaxWidth(Double.MAX_VALUE);
            betRow.getChildren().add(labeled(msg.getMessage("game.panel.bet"),
                    "seat-card-bet-label"));
            Region betFlex = new Region();
            HBox.setHgrow(betFlex, Priority.ALWAYS);
            betRow.getChildren().add(betFlex);
            betRow.getChildren().add(labeled("$" + seatBet, "seat-card-bet-value"));
            seat.getChildren().add(betRow);
        }

        boolean hasCards = player.getHands().stream()
                .anyMatch(ph -> !ph.getHand().getCards().isEmpty());
        if (hasCards) {
            seat.setTranslateY(-25);
        }
        return seat;
    }

    private HBox buildSideSeatRow(Player player, boolean isActive, MessageService msg, int index) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("seat-row");
        if (isActive) {
            row.getStyleClass().add("seat-row-active");
        }

        row.getChildren().add(UiFactory.avatar(player, index, "seat-avatar"));

        VBox info = new VBox();
        HBox.setHgrow(info, Priority.ALWAYS);
        info.getChildren().add(labeled(player.getName(), "seat-name"));
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

    private static Label botIcon() {
        Label l = new Label(Icons.BOT);
        l.getStyleClass().add(Icons.STYLE_CLASS);
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
