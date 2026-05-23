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
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoundResultController {

    @FXML private Label titleLabel;
    @FXML private Label winnerLabel;
    @FXML private Label winnerSubtitle;
    @FXML private Label streakLabel;
    @FXML private HBox dealerCardsBox;
    @FXML private Label dealerScorePill;
    @FXML private VBox balancesList;
    @FXML private VBox outcomeList;

    @FXML
    private void initialize() {
        MessageService msg = MessageService.getInstance();

        // Title with the current round number
        titleLabel.setText(msg.getMessage("roundresult.title", GameController.sharedRoundNumber));

        GameManager gm = GameController.sharedGameManager;
        if (gm == null) {
            // Defensive fallback: rare case where user reaches this scene without a live game.
            renderFallback(msg);
            return;
        }

        renderDealerCards(gm);
        Map<String, RoundRecord> lastByPlayer = buildLastRecordMap(gm);
        renderWinner(gm, lastByPlayer, msg);
        renderOutcomes(gm, lastByPlayer, msg);
        renderBalances(gm, lastByPlayer);
    }

    @FXML
    private void onNewRound() {
        // The shared GameManager is in ROUND_OVER after a finished round.
        // Roll it forward to BETTING so the reopened game.fxml starts fresh.
        if (GameController.sharedGameManager != null
                && GameController.sharedGameManager.getState() == GameState.ROUND_OVER) {
            GameController.sharedRoundNumber++;
            GameController.sharedGameManager.startNewRound();
        }
        navigateTo("/ui/game.fxml", 1100, 680);
    }

    @FXML
    private void onBackToMenu() {
        navigateTo("/ui/menu.fxml", 1100, 680);
    }

    @FXML
    private void onSaveClicked() {
        navigateTo("/ui/save.fxml", 1100, 680);
    }

    // ── Rendering helpers ────────────────────────────────────────

    private void renderFallback(MessageService msg) {
        winnerLabel.setText("—");
        winnerSubtitle.setText("");
        streakLabel.setText("");
        dealerCardsBox.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.ACE)),
                new CardView(new Card(Suit.HEARTS, Rank.SEVEN)));
        dealerScorePill.setText("18");
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

    /** Returns the most recent history record for each player (null if the player has none). */
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

    private void renderWinner(GameManager gm, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        Player winner = null;
        int winnerDelta = 0;
        RoundRecord winnerRecord = null;
        for (Player p : gm.getPlayers()) {
            RoundRecord r = lastByPlayer.get(p.getName());
            if (r == null) {
                continue;
            }
            int d = computeDelta(r.bet(), r.outcome());
            if (d > winnerDelta) {
                winnerDelta = d;
                winner = p;
                winnerRecord = r;
            }
        }

        if (winner != null) {
            winnerLabel.setText(msg.getMessage("roundresult.winnerMessage", winner.getName()));
            String outcomeText = msg.getMessage(roundresultOutcomeKey(winnerRecord.outcome()));
            winnerSubtitle.setText(outcomeText + " · +$" + winnerDelta);
            int streak = consecutiveWins(gm, winner.getName());
            streakLabel.setText(msg.getMessage("roundresult.streak", streak, winner.getName()));
        } else {
            winnerLabel.setText("—");
            winnerSubtitle.setText("");
            streakLabel.setText("");
        }
    }

    /** Counts the player's consecutive WIN/BLACKJACK records, walking backwards from latest. */
    private static int consecutiveWins(GameManager gm, String playerName) {
        int streak = 0;
        List<RoundRecord> history = gm.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            RoundRecord r = history.get(i);
            if (!r.playerName().equals(playerName)) {
                continue;
            }
            if (r.outcome() == HandOutcome.WIN || r.outcome() == HandOutcome.BLACKJACK) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private void renderOutcomes(GameManager gm, Map<String, RoundRecord> lastByPlayer, MessageService msg) {
        outcomeList.getChildren().clear();
        int idx = 0;
        for (Player p : gm.getPlayers()) {
            outcomeList.getChildren().add(buildOutcomeRow(p, lastByPlayer.get(p.getName()), idx, msg));
            idx++;
        }
    }

    private void renderBalances(GameManager gm, Map<String, RoundRecord> lastByPlayer) {
        balancesList.getChildren().clear();
        int idx = 0;
        for (Player p : gm.getPlayers()) {
            RoundRecord r = lastByPlayer.get(p.getName());
            int delta = r != null ? computeDelta(r.bet(), r.outcome()) : 0;
            balancesList.getChildren().add(buildBalanceRow(p, delta, idx));
            idx++;
        }
    }

    private HBox buildOutcomeRow(Player p, RoundRecord r, int index, MessageService msg) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("outcome-row");

        row.getChildren().add(buildAvatar(p, index));

        String displayName = (p.isBot() ? "🤖 " : "") + p.getName();
        if (r != null) {
            displayName += " " + r.playerScore();
        }
        Label name = new Label(displayName);
        name.getStyleClass().add("outcome-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(name);

        if (r != null) {
            Label chip = new Label(msg.getMessage(roundresultOutcomeKey(r.outcome())));
            chip.getStyleClass().addAll("outcome-chip", outcomeChipClass(r.outcome()));
            row.getChildren().add(chip);

            int delta = computeDelta(r.bet(), r.outcome());
            row.getChildren().add(buildDeltaLabel(delta));
        } else {
            Label chip = new Label("—");
            chip.getStyleClass().addAll("outcome-chip", "outcome-chip-push");
            row.getChildren().add(chip);
        }
        return row;
    }

    private HBox buildBalanceRow(Player p, int delta, int index) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("balance-mini-row");

        row.getChildren().add(buildAvatar(p, index));

        Label name = new Label((p.isBot() ? "🤖 " : "") + p.getName());
        name.getStyleClass().add("balance-mini-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(name);

        Label balance = new Label("$" + p.getBalance());
        balance.getStyleClass().add("balance-mini-value");
        row.getChildren().add(balance);

        if (delta != 0) {
            row.getChildren().add(buildDeltaLabel(delta));
        }
        return row;
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

    private void navigateTo(String fxml, int w, int h) {
        Navigation.navigate((Stage) titleLabel.getScene().getWindow(), fxml);
    }
}
