package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.RoundRecord;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import ch.supsi.dti.frontend.view.CardView;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoundResultController {

    private static final int TOTAL_ROUNDS = 10;

    @FXML private Label titleLabel;
    @FXML private HBox progressDots;
    @FXML private Label sessionLabel;
    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScorePill;
    @FXML private Label dealerBanner;
    @FXML private Label roundMetaLabel;
    @FXML private VBox balancesList;
    @FXML private VBox heroBox;
    @FXML private Label othersHeader;
    @FXML private VBox othersList;
    @FXML private Button primaryActionBtn;
    @FXML private StackPane chartContainer;
    @FXML private Label dealerHistoryHeader;
    @FXML private VBox dealerHistoryList;

    @FXML
    private void initialize() {
        MessageService msg = MessageService.getInstance();
        int round = GameController.sharedRoundNumber;
        boolean isFinalRound = round >= TOTAL_ROUNDS;

        // (B) Titlebar — text + dot progress.
        titleLabel.setText(isFinalRound
                ? msg.getMessage("roundresult.title.final", round, TOTAL_ROUNDS)
                : msg.getMessage("roundresult.title.progress", round, TOTAL_ROUNDS));
        if (isFinalRound) titleLabel.getStyleClass().add("titlebar-title-final");
        renderProgressDots(round);

        // (D) Action button label flips on the final round.
        if (isFinalRound) {
            primaryActionBtn.setText(msg.getMessage("roundresult.action.session"));
            primaryActionBtn.setOnAction(e -> navigateTo("/ui/menu.fxml"));
        }

        GameManager gm = GameController.sharedGameManager;
        if (gm == null) {
            renderFallback(msg);
            return;
        }

        renderDealerCards(gm);
        renderDealerHistory(gm, msg);
        Map<String, RoundRecord> lastByPlayer = buildLastRecordMap(gm);

        // (C) Session band: cumulative human stats across all rounds.
        renderSessionLine(gm, msg);

        // New: dealer outcome banner + round-meta line above the heroes.
        renderDealerBanner(gm, msg);
        renderRoundMeta(round, lastByPlayer, msg);

        // Balance-over-rounds chart, one line per player.
        renderBalanceChart(gm);

        // (A) Hero net-delta: one block per human; others go in the strip below.
        renderHeroes(gm, lastByPlayer, msg);
        renderOthers(gm, lastByPlayer, msg);
        renderBalances(gm, lastByPlayer);
    }

    @FXML
    private void onNewRound() {
        if (GameController.sharedGameManager != null
                && GameController.sharedGameManager.getState() == GameState.ROUND_OVER) {
            GameController.sharedRoundNumber++;
            GameController.sharedGameManager.startNewRound();
        }
        navigateTo("/ui/game.fxml");
    }

    @FXML
    private void onBackToMenu() {
        navigateTo("/ui/menu.fxml");
    }

    @FXML
    private void onSaveClicked() {
        navigateTo("/ui/save.fxml");
    }

    // ── Title + progress dots ────────────────────────────────────

    private void renderProgressDots(int currentRound) {
        progressDots.getChildren().clear();
        for (int i = 1; i <= TOTAL_ROUNDS; i++) {
            Label dot = new Label("●");
            dot.getStyleClass().add("progress-dot");
            if (i < currentRound) {
                dot.getStyleClass().add("progress-dot-done");
            } else if (i == currentRound) {
                dot.getStyleClass().add("progress-dot-current");
            }
            progressDots.getChildren().add(dot);
        }
    }

    // ── Session summary band ─────────────────────────────────────

    private void renderSessionLine(GameManager gm, MessageService msg) {
        // Aggregate across the human player's history records (first human).
        String humanName = gm.getPlayers().stream()
                .filter(p -> !p.isBot())
                .map(Player::getName)
                .findFirst()
                .orElse(null);
        if (humanName == null) {
            sessionLabel.setText("");
            return;
        }
        int wins = 0, losses = 0, net = 0;
        int currentStreak = 0, bestStreak = 0;
        for (RoundRecord r : gm.getHistory()) {
            if (!r.playerName().equals(humanName)) continue;
            int d = computeDelta(r.bet(), r.outcome());
            net += d;
            HandOutcome o = r.outcome();
            if (o == HandOutcome.WIN || o == HandOutcome.BLACKJACK) {
                wins++;
                currentStreak++;
                bestStreak = Math.max(bestStreak, currentStreak);
            } else if (o == HandOutcome.LOSE) {
                losses++;
                currentStreak = 0;
            } else {
                // PUSH keeps the streak alive — neither a win nor a loss.
            }
        }
        String netStr = net >= 0 ? "+$" + net : "-$" + Math.abs(net);
        sessionLabel.setText(msg.getMessage("roundresult.session.line",
                wins, losses, netStr, currentStreak));
    }

    // ── Balance-over-rounds chart ────────────────────────────────

    /**
     * Builds a {@link LineChart} (one series per player) showing each player's
     * balance evolution across the rounds played so far. We reconstruct each
     * player's starting balance from {@code currentBalance - sumOfAllDeltas},
     * then walk the history in order accumulating per-player.
     *
     * <p>Series colours come from the same seat-avatar palette used everywhere
     * (blue / green / amber / purple for humans, grey for bots), applied via
     * inline {@code -fx-stroke} on each series' line node after the chart is
     * laid out — the only reliable way to colour-code series without writing
     * brittle "default-colorN" overrides.</p>
     */
    private void renderBalanceChart(GameManager gm) {
        chartContainer.getChildren().clear();
        List<Player> players = gm.getPlayers();
        List<RoundRecord> history = gm.getHistory();
        if (players.isEmpty()) return;

        // Group history records into rounds by timestamp gaps. Records within
        // a single round (including extra records from splits) are appended
        // in tight succession by GameManager; cross-round gaps include user
        // interaction and animation delays — well over our 100ms threshold.
        List<List<RoundRecord>> rounds = groupHistoryIntoRounds(history);

        // Total delta per player (used to back-derive initial balance).
        Map<String, Integer> totalDelta = new HashMap<>();
        for (RoundRecord r : history) {
            totalDelta.merge(r.playerName(), computeDelta(r.bet(), r.outcome()), Integer::sum);
        }

        // Per-player series, prefilled with their starting balance at "round 0".
        Map<String, XYChart.Series<Number, Number>> seriesByName = new LinkedHashMap<>();
        Map<String, Integer> runningBalance = new HashMap<>();
        for (Player p : players) {
            int initial = p.getBalance() - totalDelta.getOrDefault(p.getName(), 0);
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName(p.getName());
            s.getData().add(new XYChart.Data<>(0, initial));
            seriesByName.put(p.getName(), s);
            runningBalance.put(p.getName(), initial);
        }

        // One point per ROUND per player: sum all deltas the player accrued
        // that round (splits add multiple records that net into one point).
        // Sitting-out players carry their previous balance forward — the line
        // stays flat across rounds where they didn't play.
        for (int i = 0; i < rounds.size(); i++) {
            Map<String, Integer> deltaThisRound = new HashMap<>();
            for (RoundRecord r : rounds.get(i)) {
                deltaThisRound.merge(r.playerName(),
                        computeDelta(r.bet(), r.outcome()), Integer::sum);
            }
            for (Player p : players) {
                int newBal = runningBalance.get(p.getName())
                        + deltaThisRound.getOrDefault(p.getName(), 0);
                runningBalance.put(p.getName(), newBal);
                seriesByName.get(p.getName()).getData().add(
                        new XYChart.Data<>(i + 1, newBal));
            }
        }

        // Range-aware Y-axis: pick a tick step rounded to a "nice" number so we
        // don't end up with 122.5/120.0/117.5/... clutter on small swings.
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        for (XYChart.Series<Number, Number> s : seriesByName.values()) {
            for (XYChart.Data<Number, Number> d : s.getData()) {
                int v = d.getYValue().intValue();
                yMin = Math.min(yMin, v);
                yMax = Math.max(yMax, v);
            }
        }
        // Pad ~10% so the curve doesn't kiss the axis edge.
        int yPad = Math.max(10, (yMax - yMin) / 10);
        int yLo = yMin - yPad;
        int yHi = yMax + yPad;
        int yStep = pickNiceStep(yHi - yLo);
        // Snap bounds to the chosen step so labels read clean (e.g. 75, 100, 125).
        yLo = (int) Math.floor(yLo / (double) yStep) * yStep;
        yHi = (int) Math.ceil(yHi / (double) yStep) * yStep;

        // Fixed 0..TOTAL_ROUNDS so the scale is consistent across the whole
        // session — the curve grows into the chart rather than rescaling on
        // every round.
        NumberAxis xAxis = new NumberAxis(0, TOTAL_ROUNDS, 1);
        xAxis.setLabel("Round");
        xAxis.setMinorTickVisible(false);
        xAxis.setForceZeroInRange(true);

        NumberAxis yAxis = new NumberAxis(yLo, yHi, yStep);
        yAxis.setLabel("$");
        yAxis.setMinorTickVisible(false);
        yAxis.setForceZeroInRange(false);
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) { return "$" + n.intValue(); }
            @Override public Number fromString(String s) { return 0; }
        });

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("balance-chart");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(false);

        // Pre-compute each player's seat colour so it can be applied after
        // CSS skin pass completes (series nodes only exist post-layout).
        Map<String, String> colourByName = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            colourByName.put(p.getName(), seatColourFor(p, i));
            chart.getData().add(seriesByName.get(p.getName()));
        }

        // Style the series lines + symbols after the chart has been added to
        // the scene graph (otherwise their internal nodes aren't created yet).
        javafx.application.Platform.runLater(() -> applySeriesColours(chart, colourByName));

        chartContainer.getChildren().add(chart);
    }

    private static String seatColourFor(Player p, int index) {
        if (p.isBot()) return "#6b7280";                 // CPU grey
        return switch (index) {
            case 0 -> "#3b82f6";  // blue
            case 1 -> "#4ade80";  // green
            case 2 -> "#f59e0b";  // amber
            case 3 -> "#a78bfa";  // purple
            default -> "#94a3b8"; // fallback
        };
    }

    private static void applySeriesColours(LineChart<Number, Number> chart,
                                           Map<String, String> colourByName) {
        for (XYChart.Series<Number, Number> s : chart.getData()) {
            String colour = colourByName.getOrDefault(s.getName(), "#94a3b8");
            if (s.getNode() != null) {
                s.getNode().setStyle("-fx-stroke: " + colour + "; -fx-stroke-width: 3;");
            }
            // Symbol: filled outer disc in seat colour with a small dark inner
            // dot so it pops against the chart background.
            for (XYChart.Data<Number, Number> d : s.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle(
                            "-fx-background-color: " + colour + ", #15181f;" +
                            "-fx-background-insets: 0, 3;" +
                            "-fx-background-radius: 7px;" +
                            "-fx-padding: 6px;");
                }
            }
            // Recolour the legend swatch for this series.
            for (var item : chart.lookupAll(".chart-legend-item")) {
                if (item instanceof javafx.scene.control.Label lbl
                        && s.getName().equals(lbl.getText())
                        && lbl.getGraphic() != null) {
                    lbl.getGraphic().setStyle(
                            "-fx-background-color: " + colour + ";" +
                            "-fx-background-radius: 4;");
                }
            }
        }
    }

    /**
     * Splits a flat history list into per-round chunks using timestamp gaps.
     * Records within a single round are committed in tight succession by
     * GameManager.endRound() (sub-millisecond apart, all sharing a tight
     * cluster). Cross-round gaps include the user clicking "Nuovo round",
     * placing a bet, dealing — easily 100+ ms even on rapid play.
     */
    private static List<List<RoundRecord>> groupHistoryIntoRounds(List<RoundRecord> history) {
        List<List<RoundRecord>> rounds = new java.util.ArrayList<>();
        if (history.isEmpty()) return rounds;
        List<RoundRecord> current = new java.util.ArrayList<>();
        current.add(history.get(0));
        for (int i = 1; i < history.size(); i++) {
            long gap = java.time.Duration.between(
                    history.get(i - 1).timestamp(), history.get(i).timestamp()).toMillis();
            if (gap > 100) {
                rounds.add(current);
                current = new java.util.ArrayList<>();
            }
            current.add(history.get(i));
        }
        rounds.add(current);
        return rounds;
    }

    /** Pick a tick step (5/10/25/50/100/250/500/1000) that yields ~5 grid lines. */
    private static int pickNiceStep(int range) {
        if (range <= 0) return 25;
        int target = Math.max(1, range / 5);
        int[] steps = {5, 10, 25, 50, 100, 250, 500, 1000, 2500};
        for (int s : steps) {
            if (s >= target) return s;
        }
        return steps[steps.length - 1];
    }

    // ── Dealer outcome banner + round meta line ──────────────────

    /**
     * Big narrative banner above the heroes saying *why* this round ended the
     * way it did — "Banco sballato a 25", "Banco si ferma a 18", or "Blackjack
     * del banco". Coloured per state: red on bust, gold on blackjack, neutral
     * on a regular stand.
     */
    private void renderDealerBanner(GameManager gm, MessageService msg) {
        // Strip any previous state class so re-renders don't accumulate.
        dealerBanner.getStyleClass().removeAll(
                "dealer-banner-bust", "dealer-banner-stand", "dealer-banner-blackjack");

        var hand = gm.getDealer().getHand();
        int score = hand.getScore();
        String text;
        String styleClass;
        if (hand.isBlackJack()) {
            text = msg.getMessage("roundresult.dealer.blackjack");
            styleClass = "dealer-banner-blackjack";
        } else if (hand.isBusted()) {
            text = msg.getMessage("roundresult.dealer.bust", score);
            styleClass = "dealer-banner-bust";
        } else {
            text = msg.getMessage("roundresult.dealer.stand", score);
            styleClass = "dealer-banner-stand";
        }
        dealerBanner.setText(text);
        dealerBanner.getStyleClass().add(styleClass);
    }

    /** Sum of bets placed this round across every player, with current round / total. */
    private void renderRoundMeta(int round, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        int totalWagered = 0;
        for (RoundRecord r : lastByPlayer.values()) {
            if (r != null) totalWagered += r.bet();
        }
        roundMetaLabel.setText(msg.getMessage("roundresult.round.meta",
                round, TOTAL_ROUNDS, totalWagered));
    }

    // ── Hero net-delta + others ──────────────────────────────────

    /** Builds one hero block per human player and puts them in a row inside heroBox. */
    private void renderHeroes(GameManager gm, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        heroBox.getChildren().clear();
        List<Player> players = gm.getPlayers();
        List<Player> humans = players.stream()
                .filter(p -> !p.isBot())
                .toList();
        if (humans.isEmpty()) return;

        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER);
        boolean multi = humans.size() > 1;
        for (Player p : humans) {
            // Seat colour comes from the player's index in the full player list,
            // matching the seat-avatar palette used everywhere else (seat 1 blue,
            // seat 2 green, seat 3 amber, seat 4 purple).
            int seatIndex = players.indexOf(p);
            row.getChildren().add(buildHeroBlock(p, lastByPlayer.get(p.getName()),
                    seatIndex, msg, multi));
        }
        heroBox.getChildren().add(row);
    }

    private VBox buildHeroBlock(Player player, RoundRecord r, int seatIndex,
                                MessageService msg, boolean multi) {
        VBox block = new VBox(6);
        block.setAlignment(Pos.CENTER);
        block.getStyleClass().add("hero-block");
        // Seat-colour stripe is the block's top border, applied via this modifier.
        if (seatIndex >= 0) {
            block.getStyleClass().add("hero-block-seat" + (seatIndex + 1));
        }

        // In multiplayer, prepend the player's name so blocks aren't ambiguous.
        if (multi) {
            Label name = new Label(player.getName());
            name.getStyleClass().add("hero-name");
            block.getChildren().add(name);
        }

        if (r == null) {
            Label dash = new Label("—");
            dash.getStyleClass().add("hero-delta-neutral");
            block.getChildren().add(dash);
            return block;
        }

        int delta = computeDelta(r.bet(), r.outcome());
        Label deltaLbl = new Label(formatHeroDelta(delta));
        deltaLbl.getStyleClass().add(heroDeltaClass(r.outcome(), delta));
        block.getChildren().add(deltaLbl);

        Label headline = new Label(heroHeadline(r.outcome(), msg));
        headline.getStyleClass().add("hero-headline");
        block.getChildren().add(headline);

        Label detail = new Label(heroDetail(r.outcome(), r.bet(), msg));
        detail.getStyleClass().add("hero-detail");
        block.getChildren().add(detail);

        return block;
    }

    private static String formatHeroDelta(int delta) {
        if (delta > 0) return "+$" + delta;
        if (delta < 0) return "-$" + Math.abs(delta);
        return "$0";
    }

    private static String heroDeltaClass(HandOutcome outcome, int delta) {
        return switch (outcome) {
            case WIN, BLACKJACK -> "hero-delta-pos";
            case LOSE           -> "hero-delta-neg";
            case PUSH           -> "hero-delta-neutral";
        };
    }

    private static String heroHeadline(HandOutcome outcome, MessageService msg) {
        return switch (outcome) {
            case BLACKJACK -> msg.getMessage("roundresult.hero.blackjack");
            case WIN       -> msg.getMessage("roundresult.hero.victory");
            case LOSE      -> msg.getMessage("roundresult.hero.defeat");
            case PUSH      -> msg.getMessage("roundresult.hero.push");
        };
    }

    private static String heroDetail(HandOutcome outcome, int bet, MessageService msg) {
        return switch (outcome) {
            case BLACKJACK -> msg.getMessage("roundresult.hero.detail.blackjack", bet);
            case WIN       -> msg.getMessage("roundresult.hero.detail.win", bet);
            case LOSE      -> msg.getMessage("roundresult.hero.detail.lose", bet);
            case PUSH      -> msg.getMessage("roundresult.hero.detail.push", bet);
        };
    }

    /** Lists non-human players (bots) below the hero blocks; hides the strip if none. */
    private void renderOthers(GameManager gm, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        othersList.getChildren().clear();
        List<Player> bots = gm.getPlayers().stream()
                .filter(Player::isBot)
                .toList();
        boolean any = !bots.isEmpty();
        othersHeader.setVisible(any);
        othersHeader.setManaged(any);
        othersList.setVisible(any);
        othersList.setManaged(any);
        int idx = 0;
        for (Player p : bots) {
            othersList.getChildren().add(buildOtherRow(p, lastByPlayer.get(p.getName()), idx, msg));
            idx++;
        }
    }

    private HBox buildOtherRow(Player p, RoundRecord r, int index, MessageService msg) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("outcome-row");

        row.getChildren().add(buildAvatar(p, index));

        String displayName = "🤖 " + p.getName();
        if (r != null) displayName += " · " + r.playerScore();
        Label name = new Label(displayName);
        name.getStyleClass().add("outcome-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(name);

        if (r != null) {
            Label chip = new Label(msg.getMessage(roundresultOutcomeKey(r.outcome())));
            chip.getStyleClass().addAll("outcome-chip", outcomeChipClass(r.outcome()));
            row.getChildren().add(chip);
            row.getChildren().add(buildDeltaLabel(computeDelta(r.bet(), r.outcome())));
        } else {
            Label chip = new Label("—");
            chip.getStyleClass().addAll("outcome-chip", "outcome-chip-push");
            row.getChildren().add(chip);
        }
        return row;
    }

    // ── Right column: per-player balance after the round ─────────

    private void renderBalances(GameManager gm, Map<String, RoundRecord> lastByPlayer) {
        balancesList.getChildren().clear();
        int idx = 0;
        MessageService msg = MessageService.getInstance();
        for (Player p : gm.getPlayers()) {
            RoundRecord r = lastByPlayer.get(p.getName());
            int delta = r != null ? computeDelta(r.bet(), r.outcome()) : 0;
            int[] wl = countWinsLosses(gm, p.getName());
            balancesList.getChildren().add(buildBalanceRow(p, delta, idx, wl[0], wl[1], msg));
            idx++;
        }
    }

    /** Returns {wins, losses} across the player's history. PUSH counts as neither. */
    private static int[] countWinsLosses(GameManager gm, String playerName) {
        int w = 0, l = 0;
        for (RoundRecord r : gm.getHistory()) {
            if (!r.playerName().equals(playerName)) continue;
            switch (r.outcome()) {
                case WIN, BLACKJACK -> w++;
                case LOSE           -> l++;
                case PUSH           -> { /* neither */ }
            }
        }
        return new int[]{w, l};
    }

    private HBox buildBalanceRow(Player p, int delta, int index,
                                 int wins, int losses, MessageService msg) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("balance-mini-row");
        row.getChildren().add(buildAvatar(p, index));

        // Vertical info block: name (top) + W/L badge (bottom).
        VBox info = new VBox(1);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label((p.isBot() ? "🤖 " : "") + p.getName());
        name.getStyleClass().add("balance-mini-name");
        info.getChildren().add(name);

        if (wins > 0 || losses > 0) {
            Label wl = new Label(msg.getMessage("roundresult.stats.wl", wins, losses));
            wl.getStyleClass().add("balance-mini-stats");
            info.getChildren().add(wl);
        }
        row.getChildren().add(info);

        Label balance = new Label("$" + p.getBalance());
        balance.getStyleClass().add("balance-mini-value");
        row.getChildren().add(balance);

        if (delta != 0) row.getChildren().add(buildDeltaLabel(delta));
        return row;
    }

    // ── Dealer cards ─────────────────────────────────────────────

    private void renderDealerCards(GameManager gm) {
        List<Card> cards = gm.getDealer().getHand().getCards();
        dealerCardsBox.getChildren().clear();
        if (cards.isEmpty()) {
            dealerCardsBox.getChildren().addAll(new CardView(null), new CardView(null));
            dealerScorePill.setText("?");
            return;
        }
        for (Card c : cards) {
            dealerCardsBox.getChildren().add(new CardView(c));
        }
        dealerScorePill.setText(String.valueOf(gm.getDealer().getHand().getScore()));
    }

    /**
     * Lists the dealer's final score for every round played so far. Uses the
     * first player's records as the anchor sequence — every player in a round
     * sees the same dealer, so any non-sitting-out player's record carries the
     * round's dealerScore. Each row is plain text styled like the existing
     * mini-round entries on the felt's right panel.
     */
    private void renderDealerHistory(GameManager gm, MessageService msg) {
        dealerHistoryList.getChildren().clear();
        Player anchor = gm.getPlayers().stream().findFirst().orElse(null);
        boolean any = anchor != null && gm.getHistory().stream()
                .anyMatch(r -> r.playerName().equals(anchor.getName()));
        dealerHistoryHeader.setVisible(any);
        dealerHistoryHeader.setManaged(any);
        dealerHistoryList.setVisible(any);
        dealerHistoryList.setManaged(any);
        if (!any) return;

        int round = 0;
        for (RoundRecord r : gm.getHistory()) {
            if (!r.playerName().equals(anchor.getName())) continue;
            round++;
            int score = r.dealerScore();
            String key;
            if (score > 21) key = "roundresult.dealer.history.row.bust";
            else if (score == 21) key = "roundresult.dealer.history.row.blackjack";
            else key = "roundresult.dealer.history.row";
            Label row = new Label(msg.getMessage(key, round, score));
            row.getStyleClass().add("dealer-history-row");
            if (score > 21) row.getStyleClass().add("dealer-history-row-bust");
            else if (score == 21) row.getStyleClass().add("dealer-history-row-blackjack");
            dealerHistoryList.getChildren().add(row);
        }
    }

    // ── Misc helpers ─────────────────────────────────────────────

    private void renderFallback(MessageService msg) {
        sessionLabel.setText("");
        dealerCardsBox.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.ACE)),
                new CardView(new Card(Suit.HEARTS, Rank.SEVEN)));
        dealerScorePill.setText("18");
    }

    private Map<String, RoundRecord> buildLastRecordMap(GameManager gm) {
        Map<String, RoundRecord> result = new LinkedHashMap<>();
        for (Player p : gm.getPlayers()) {
            result.put(p.getName(), null);
        }
        for (RoundRecord r : gm.getHistory()) {
            if (result.containsKey(r.playerName())) {
                result.put(r.playerName(), r);
            }
        }
        return result;
    }

    private StackPane buildAvatar(Player p, int index) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("seat-avatar");
        if (p.isBot()) {
            avatar.getStyleClass().add("seat-avatar-cpu");
        } else if (index > 0) {
            avatar.getStyleClass().add("seat-avatar-p" + (index + 1));
        }
        String initial = p.getName().isEmpty()
                ? "?"
                : p.getName().substring(0, 1).toUpperCase();
        Label initLbl = new Label(initial);
        initLbl.getStyleClass().add("seat-avatar-initial");
        avatar.getChildren().add(initLbl);
        return avatar;
    }

    private Label buildDeltaLabel(int delta) {
        if (delta > 0) {
            Label l = new Label("+$" + delta);
            l.getStyleClass().add("delta-pos");
            return l;
        }
        if (delta < 0) {
            Label l = new Label("-$" + Math.abs(delta));
            l.getStyleClass().add("delta-neg");
            return l;
        }
        Label l = new Label("+$0");
        l.getStyleClass().add("balance-mini-value");
        return l;
    }

    private static int computeDelta(int bet, HandOutcome outcome) {
        return switch (outcome) {
            case WIN       -> bet;
            case BLACKJACK -> (int) Math.round(bet * 1.5);
            case LOSE      -> -bet;
            case PUSH      -> 0;
        };
    }

    private static String roundresultOutcomeKey(HandOutcome outcome) {
        return switch (outcome) {
            case WIN, BLACKJACK -> "roundresult.outcome.win";
            case LOSE           -> "roundresult.outcome.lose";
            case PUSH           -> "roundresult.outcome.push";
        };
    }

    private static String outcomeChipClass(HandOutcome outcome) {
        return switch (outcome) {
            case WIN, BLACKJACK -> "outcome-chip-win";
            case LOSE           -> "outcome-chip-loss";
            case PUSH           -> "outcome-chip-push";
        };
    }

    private void navigateTo(String fxml) {
        Navigation.navigate((Stage) titleLabel.getScene().getWindow(), fxml);
    }
}
