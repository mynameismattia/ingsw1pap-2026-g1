package ch.supsi.dti.backend.game;

import ch.supsi.dti.backend.model.Dealer;
import ch.supsi.dti.backend.model.Deck;
import ch.supsi.dti.backend.model.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameManager {

    // Constants
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final double BLACKJACK_PAYOUT = 1.5;
    private static final double NORMAL_PAYOUT = 1.0;

    // Variables
    private final List<Player> players;
    private final Dealer dealer;
    private final Deck deck;
    private final Set<Integer> settledPlayers;
    private final Set<Integer> insuranceDecisions;
    private GameState state;
    private int currentPlayerIndex;

    public GameManager(List<String> playersNames, int initialBalance) {
        this(playersNames, initialBalance, new Deck());
    }

    // Package-private constructor: allows injecting a deck (used by tests).
    GameManager(List<String> playersNames, int initialBalance, Deck deck) {
        this.players = new ArrayList<>();
        for (String name : playersNames) {
            players.add(new Player(name, initialBalance));
        }
        this.dealer = new Dealer();
        this.deck = deck;
        this.settledPlayers = new HashSet<>();
        this.insuranceDecisions = new HashSet<>();
        this.state = GameState.WAITING;
        this.currentPlayerIndex = 0;
    }

    // --- Round flow ---

    public void startNewRound() {
        if (state == GameState.GAME_OVER) {
            throw new IllegalStateException("Game is over");
        }
        if (state != GameState.WAITING && state != GameState.ROUND_OVER) {
            throw new IllegalStateException("Cannot start a new round in state " + state);
        }

        boolean anyCanPlay = false;
        for (Player player : players) {
            if (player.getBalance() >= MIN_BET) {
                anyCanPlay = true;
                break;
            }
        }
        if (!anyCanPlay) {
            state = GameState.GAME_OVER;
            return;
        }

        for (Player player : players) {
            player.getHand().clear();
            player.resetBet();
        }
        dealer.getHand().clear();
        dealer.setHandRevealed(false);
        settledPlayers.clear();
        insuranceDecisions.clear();
        currentPlayerIndex = 0;

        if (deck.needsReshuffle()) {
            deck.reset();
        }

        state = GameState.BETTING;
    }

    public void placeBet(int playerIndex, int amount) {
        if (state != GameState.BETTING) {
            throw new IllegalStateException("Cannot place a bet in state " + state);
        }
        if (playerIndex < 0 || playerIndex >= players.size()) {
            throw new IndexOutOfBoundsException("Player index out of bounds: " + playerIndex);
        }
        Player player = players.get(playerIndex);
        if (player.getCurrentBet() != 0) {
            throw new IllegalStateException(
                    "Player " + player.getName() + " has already placed a bet");
        }
        if (amount < MIN_BET || amount > MAX_BET) {
            throw new IllegalArgumentException(
                    "Bet must be between " + MIN_BET + " and " + MAX_BET);
        }
        player.placeBet(amount);
    }

    public void deal() {
        if (state != GameState.BETTING) {
            throw new IllegalStateException("Cannot deal in state " + state);
        }
        for (Player player : players) {
            if (player.getCurrentBet() == 0) {
                throw new IllegalStateException(
                        "Player " + player.getName() + " has not placed a bet");
            }
        }

        state = GameState.DEALING;

        // Two cards each, player first, dealer last (standard blackjack order).
        for (int i = 0; i < 2; i++) {
            for (Player player : players) {
                player.getHand().addCard(deck.draw());
            }
            dealer.getHand().addCard(deck.draw());
        }

        // Dealer shows Ace → offer insurance to each player before peeking.
        if (dealer.showsAce()) {
            state = GameState.INSURANCE_OFFER;
            return;
        }

        resolveNaturalBlackjacks();
    }

    public void takeInsurance(int playerIndex) {
        if (state != GameState.INSURANCE_OFFER) {
            throw new IllegalStateException("Cannot take insurance in state " + state);
        }
        if (playerIndex < 0 || playerIndex >= players.size()) {
            throw new IndexOutOfBoundsException("Player index out of bounds: " + playerIndex);
        }
        if (insuranceDecisions.contains(playerIndex)) {
            throw new IllegalStateException(
                    "Player " + players.get(playerIndex).getName() + " has already answered");
        }
        Player player = players.get(playerIndex);
        player.placeInsuranceBet(player.getCurrentBet() / 2);
        insuranceDecisions.add(playerIndex);
        finishInsurancePhaseIfDone();
    }

    public void declineInsurance(int playerIndex) {
        if (state != GameState.INSURANCE_OFFER) {
            throw new IllegalStateException("Cannot decline insurance in state " + state);
        }
        if (playerIndex < 0 || playerIndex >= players.size()) {
            throw new IndexOutOfBoundsException("Player index out of bounds: " + playerIndex);
        }
        if (insuranceDecisions.contains(playerIndex)) {
            throw new IllegalStateException(
                    "Player " + players.get(playerIndex).getName() + " has already answered");
        }
        insuranceDecisions.add(playerIndex);
        finishInsurancePhaseIfDone();
    }

    private void finishInsurancePhaseIfDone() {
        if (insuranceDecisions.size() < players.size()) {
            return;
        }
        // All players have answered. Peek the hole card.
        if (dealer.getHand().isBlackJack()) {
            // Insurance pays 2:1 to those who took it.
            for (Player player : players) {
                if (player.getInsuranceBet() > 0) {
                    player.winInsurance(2.0);
                }
            }
        }
        // Insurance bets are not refunded if the dealer didn't have BJ — they are lost.
        // (Player.placeInsuranceBet already debited the balance.)

        resolveNaturalBlackjacks();
    }

    private void resolveNaturalBlackjacks() {
        // If the dealer has a natural blackjack, the round ends immediately:
        // players with a blackjack push, everyone else loses.
        if (dealer.getHand().isBlackJack()) {
            dealer.setHandRevealed(true);
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (player.getHand().isBlackJack()) {
                    player.push();
                }
                settledPlayers.add(i);
            }
            state = GameState.ROUND_OVER;
            return;
        }

        // Pay out any player blackjacks 3:2 and mark them as settled.
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.getHand().isBlackJack()) {
                player.win(BLACKJACK_PAYOUT);
                settledPlayers.add(i);
            }
        }

        state = GameState.PLAYER_TURN;
        advanceToNextActivePlayer();
    }

    public void hit() {
        if (state != GameState.PLAYER_TURN) {
            throw new IllegalStateException("Cannot hit in state " + state);
        }
        Player current = players.get(currentPlayerIndex);
        current.getHand().addCard(deck.draw());

        if (current.getHand().isBusted()) {
            // Bet was already subtracted at placeBet time: nothing more to do.
            settledPlayers.add(currentPlayerIndex);
            advanceToNextActivePlayer();
        } else if (current.getHand().getScore() == 21) {
            // 21: no further decision, auto-stand.
            currentPlayerIndex++;
            advanceToNextActivePlayer();
        }
    }

    public void stand() {
        if (state != GameState.PLAYER_TURN) {
            throw new IllegalStateException("Cannot stand in state " + state);
        }
        currentPlayerIndex++;
        advanceToNextActivePlayer();
    }

    public void dealerPlay() {
        if (state != GameState.DEALER_TURN) {
            throw new IllegalStateException("Cannot play dealer in state " + state);
        }
        dealer.setHandRevealed(true);
        while (dealer.shouldHit()) {
            dealer.getHand().addCard(deck.draw());
        }
        state = GameState.RESOLVING;
        resolveRound();
    }

    public void resolveRound() {
        if (state != GameState.RESOLVING) {
            throw new IllegalStateException("Cannot resolve round in state " + state);
        }

        final int dealerScore = dealer.getHand().getScore();
        final boolean dealerBust = dealer.getHand().isBusted();

        for (int i = 0; i < players.size(); i++) {
            if (settledPlayers.contains(i)) {
                continue;
            }
            Player player = players.get(i);
            int playerScore = player.getHand().getScore();

            if (dealerBust || playerScore > dealerScore) {
                player.win(NORMAL_PAYOUT);
            } else if (playerScore == dealerScore) {
                player.push();
            }
            // else: dealer wins, bet is already lost
            settledPlayers.add(i);
        }

        state = GameState.ROUND_OVER;
    }

    // --- Advanced actions: ---

    public void doubleDown() { // (#7)
        if (!canDoubleDown()) {
            throw new IllegalStateException("Cannot double down in state " + state);
        }
        Player current = players.get(currentPlayerIndex);
        current.doubleBet();
        current.getHand().addCard(deck.draw());

        if (current.getHand().isBusted()) {
            settledPlayers.add(currentPlayerIndex);
        }
        // Double-down always ends the hand: auto-stand on any non-bust result.
        currentPlayerIndex++;
        advanceToNextActivePlayer();
    }

    public void split() { // (#6)
        throw new UnsupportedOperationException("Split is not supported in v1");
    }

    public boolean canSplit() { return false; }

    public boolean canDoubleDown() {
        if (state != GameState.PLAYER_TURN || currentPlayerIndex >= players.size()) {
            return false;
        }
        Player current = players.get(currentPlayerIndex);
        return current.getHand().getCards().size() == 2
                && current.getBalance() >= current.getCurrentBet();
    }

    public boolean canInsure() {
        return state == GameState.INSURANCE_OFFER && dealer.showsAce();
    }

    public List<?> getHistory() { return null; } // (#13)

    // --- Queries ---

    public Player getCurrentPlayer() {
        if (state != GameState.PLAYER_TURN || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public GameState getState() {
        return state;
    }

    public Dealer getDealer() {
        return dealer;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    // --- Internal helpers ---

    private void advanceToNextActivePlayer() {
        while (currentPlayerIndex < players.size()
                && settledPlayers.contains(currentPlayerIndex)) {
            currentPlayerIndex++;
        }
        if (currentPlayerIndex >= players.size()) {
            beginDealerTurn();
        }
    }

    private void beginDealerTurn() {
        // If no player is still in contention (all already settled),
        // the dealer does not draw — but still reveal the hole card.
        boolean anyInContention = false;
        for (int i = 0; i < players.size(); i++) {
            if (!settledPlayers.contains(i)) {
                anyInContention = true;
                break;
            }
        }
        if (anyInContention) {
            state = GameState.DEALER_TURN;
        } else {
            dealer.setHandRevealed(true);
            state = GameState.RESOLVING;
            resolveRound();
        }
    }
}
