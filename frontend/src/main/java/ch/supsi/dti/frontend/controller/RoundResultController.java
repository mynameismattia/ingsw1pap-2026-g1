// Schermata di fine round (Vedi Risultati).
// Tre colonne: a sinistra la mano del banco con storico ultimi round, al centro il grafico dei bilanci con riga-per-giocatore (delta, outcome chip, descrizione), a destra i saldi aggiornati.

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
import javafx.scene.layout.FlowPane;
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
    @FXML private FlowPane dealerHistoryList;

    @FXML
    private void initialize() {
        MessageService msg = MessageService.getInstance();
        int round = GameController.sharedRoundNumber;
        boolean isFinalRound = round >= TOTAL_ROUNDS;

        titleLabel.setText(isFinalRound
                ? msg.getMessage("roundresult.title.final", round, TOTAL_ROUNDS)
                : msg.getMessage("roundresult.title.progress", round, TOTAL_ROUNDS));
        if (isFinalRound) titleLabel.getStyleClass().add("titlebar-title-final");
        renderProgressDots(round);

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

        renderSessionLine(gm, msg);

        renderDealerBanner(gm, msg);
        renderRoundMeta(round, lastByPlayer, msg);

        renderBalanceChart(gm);

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

    private void renderSessionLine(GameManager gm, MessageService msg) {

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

            }
        }
        String netStr = net >= 0 ? "+$" + net : "-$" + Math.abs(net);
        sessionLabel.setText(msg.getMessage("roundresult.session.line",
                wins, losses, netStr, currentStreak));
    }

    private void renderBalanceChart(GameManager gm) {
        chartContainer.getChildren().clear();
        List<Player> players = gm.getPlayers();
        List<RoundRecord> history = gm.getHistory();
        if (players.isEmpty()) return;

        List<List<RoundRecord>> rounds = groupHistoryIntoRounds(history);

        Map<String, Integer> totalDelta = new HashMap<>();
        for (RoundRecord r : history) {
            totalDelta.merge(r.playerName(), computeDelta(r.bet(), r.outcome()), Integer::sum);
        }

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

        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        for (XYChart.Series<Number, Number> s : seriesByName.values()) {
            for (XYChart.Data<Number, Number> d : s.getData()) {
                int v = d.getYValue().intValue();
                yMin = Math.min(yMin, v);
                yMax = Math.max(yMax, v);
            }
        }

        int yPad = Math.max(10, (yMax - yMin) / 10);
        int yLo = yMin - yPad;
        int yHi = yMax + yPad;
        int yStep = pickNiceStep(yHi - yLo);

        yLo = (int) Math.floor(yLo / (double) yStep) * yStep;
        yHi = (int) Math.ceil(yHi / (double) yStep) * yStep;

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

        Map<String, String> colourByName = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            colourByName.put(p.getName(), seatColourFor(p, i));
            chart.getData().add(seriesByName.get(p.getName()));
        }

        javafx.application.Platform.runLater(() -> applySeriesColours(chart, colourByName));

        chartContainer.getChildren().add(chart);
    }

    private static String seatColourFor(Player p, int index) {
        if (p.isBot()) return "#6b7280";
        return switch (index) {
            case 0 -> "#3b82f6";
            case 1 -> "#4ade80";
            case 2 -> "#f59e0b";
            case 3 -> "#a78bfa";
            default -> "#94a3b8";
        };
    }

    private static void applySeriesColours(LineChart<Number, Number> chart,
                                           Map<String, String> colourByName) {
        for (XYChart.Series<Number, Number> s : chart.getData()) {
            String colour = colourByName.getOrDefault(s.getName(), "#94a3b8");
            if (s.getNode() != null) {
                s.getNode().setStyle("-fx-stroke: " + colour + "; -fx-stroke-width: 3;");
            }

            for (XYChart.Data<Number, Number> d : s.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle(
                            "-fx-background-color: " + colour + ", #15181f;" +
                            "-fx-background-insets: 0, 3;" +
                            "-fx-background-radius: 7px;" +
                            "-fx-padding: 6px;");
                }
            }

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

    private static int pickNiceStep(int range) {
        if (range <= 0) return 25;
        int target = Math.max(1, range / 5);
        int[] steps = {5, 10, 25, 50, 100, 250, 500, 1000, 2500};
        for (int s : steps) {
            if (s >= target) return s;
        }
        return steps[steps.length - 1];
    }

    private void renderDealerBanner(GameManager gm, MessageService msg) {

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

    private void renderRoundMeta(int round, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        int totalWagered = 0;
        for (RoundRecord r : lastByPlayer.values()) {
            if (r != null) totalWagered += r.bet();
        }
        roundMetaLabel.setText(msg.getMessage("roundresult.round.meta",
                round, TOTAL_ROUNDS, totalWagered));
    }

    private void renderHeroes(GameManager gm, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        // 1. Pulisco l'heroBox dal render precedente, imposto spacing 8 tra le righe.
        heroBox.getChildren().clear();
        heroBox.setSpacing(8);

        // 2. Filtro solo i giocatori umani: i bot vanno nella sezione "others" più sotto.
        List<Player> players = gm.getPlayers();
        List<Player> humans = players.stream()
                .filter(p -> !p.isBot())
                .toList();
        if (humans.isEmpty()) return;

        // 3. Per ogni umano costruisco una "hero row" orizzontale (avatar | nome | delta | outcome chip | detail)
        //    e la aggiungo al heroBox come riga. Il seatIndex viene dal player ORDINE NELLA LISTA TOTALE
        //    (non solo umani), così il colore del seat sul felt e qui combaciano.
        for (Player p : humans) {
            int seatIndex = players.indexOf(p);
            heroBox.getChildren().add(buildHeroBlock(p, lastByPlayer.get(p.getName()),
                    seatIndex, msg));
        }
    }

    private HBox buildHeroBlock(Player player, RoundRecord r, int seatIndex,
                                MessageService msg) {
        // 1. Riga base: HBox 16px di spacing, allineata a sinistra, classi CSS .hero-block + .hero-block-seatN (colore stripe).
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("hero-block");
        if (seatIndex >= 0) {
            row.getStyleClass().add("hero-block-seat" + (seatIndex + 1));
        }

        // 2. Avatar mini 20px (cerchio con iniziale). Colore dalla palette seat-avatar-cpu/p2/p3/p4 (uguale al felt).
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("seat-card-avatar");
        if (player.isBot()) {
            avatar.getStyleClass().add("seat-avatar-cpu");
        } else if (seatIndex > 0) {
            avatar.getStyleClass().add("seat-avatar-p" + (seatIndex + 1));
        }
        String initial = player.getName().isEmpty()
                ? "?"
                : player.getName().substring(0, 1).toUpperCase();
        Label initLbl = new Label(initial);
        initLbl.getStyleClass().add("seat-card-avatar-initial");
        avatar.getChildren().add(initLbl);
        row.getChildren().add(avatar);

        // 3. Nome del player a sinistra (min-width 90 via CSS).
        Label name = new Label(player.getName());
        name.getStyleClass().add("hero-row-name");
        row.getChildren().add(name);

        // 4. Edge case: nessun RoundRecord (player non ha giocato la mano corrente). Mostro solo "—" allineato a destra.
        if (r == null) {
            javafx.scene.layout.Region flex = new javafx.scene.layout.Region();
            HBox.setHgrow(flex, Priority.ALWAYS);
            row.getChildren().add(flex);
            Label dash = new Label("—");
            dash.getStyleClass().add("hero-delta-neutral");
            row.getChildren().add(dash);
            return row;
        }

        javafx.scene.layout.Region beforeDelta = new javafx.scene.layout.Region();
        HBox.setHgrow(beforeDelta, Priority.SOMETIMES);
        row.getChildren().add(beforeDelta);

        int delta = computeDelta(r.bet(), r.outcome());
        Label deltaLbl = new Label(formatHeroDelta(delta));
        deltaLbl.getStyleClass().add(heroDeltaClass(r.outcome(), delta));
        row.getChildren().add(deltaLbl);

        Label chip = new Label(heroHeadline(r.outcome(), msg).toUpperCase());
        chip.getStyleClass().addAll("hero-chip", "hero-chip-" + heroChipModifier(r.outcome()));
        row.getChildren().add(chip);

        Label detail = new Label(heroDetail(r.outcome(), r.bet(), msg));
        detail.getStyleClass().add("hero-detail");
        HBox.setHgrow(detail, Priority.ALWAYS);
        row.getChildren().add(detail);

        return row;
    }

    private static String heroChipModifier(HandOutcome outcome) {
        return switch (outcome) {
            case BLACKJACK -> "bj";
            case WIN       -> "win";
            case LOSE      -> "loss";
            case PUSH      -> "push";
        };
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

    private static int[] countWinsLosses(GameManager gm, String playerName) {
        int w = 0, l = 0;
        for (RoundRecord r : gm.getHistory()) {
            if (!r.playerName().equals(playerName)) continue;
            switch (r.outcome()) {
                case WIN, BLACKJACK -> w++;
                case LOSE           -> l++;
                case PUSH           -> {  }
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
            dealerHistoryList.getChildren().add(buildDealerHistoryTile(round, r.dealerScore(), msg));
        }
    }

    private VBox buildDealerHistoryTile(int round, int score, MessageService msg) {
        boolean bust = score > 21;
        boolean blackjack = score == 21;

        VBox tile = new VBox(2);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("dealer-history-tile");
        if (bust) tile.getStyleClass().add("dealer-history-tile-bust");
        else if (blackjack) tile.getStyleClass().add("dealer-history-tile-blackjack");

        Label roundLbl = new Label(msg.getMessage("roundresult.dealer.history.tile.round", round));
        roundLbl.getStyleClass().add("dealer-history-tile-round");

        Label scoreLbl = new Label(String.valueOf(score));
        scoreLbl.getStyleClass().add("dealer-history-tile-score");

        tile.getChildren().addAll(roundLbl, scoreLbl);

        if (bust || blackjack) {
            Label tag = new Label(msg.getMessage(
                    bust ? "roundresult.dealer.history.tile.bust"
                         : "roundresult.dealer.history.tile.blackjack"));
            tag.getStyleClass().add("dealer-history-tile-tag");
            tile.getChildren().add(tag);
        }
        return tile;
    }

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
