package ch.supsi.dti.backend.game;

import ch.supsi.dti.backend.model.Dealer;
import ch.supsi.dti.backend.model.Deck;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.PlayerHand;

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
    private final Set<Integer> insuranceDecisions;
    private GameState state;
    private int currentPlayerIndex;
    private int currentHandIndex;

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
        this.insuranceDecisions = new HashSet<>();
        this.state = GameState.WAITING;
        this.currentPlayerIndex = 0;
        this.currentHandIndex = 0;
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
            player.resetForNewRound();
        }
        dealer.getHand().clear();
        dealer.setHandRevealed(false);
        insuranceDecisions.clear();
        currentPlayerIndex = 0;
        currentHandIndex = 0;

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
        PlayerHand mainHand = player.getHands().get(0);
        if (mainHand.getBet() != 0) {
            throw new IllegalStateException(
                    "Player " + player.getName() + " has already placed a bet");
        }
        if (amount < MIN_BET || amount > MAX_BET) {
            throw new IllegalArgumentException(
                    "Bet must be between " + MIN_BET + " and " + MAX_BET);
        }
        mainHand.placeBet(amount);
    }

    public void deal() {
        if (state != GameState.BETTING) {
            throw new IllegalStateException("Cannot deal in state " + state);
        }
        for (Player player : players) {
            if (player.getHands().get(0).getBet() == 0) {
                throw new IllegalStateException(
                        "Player " + player.getName() + " has not placed a bet");
            }
        }

        state = GameState.DEALING;

        // Two cards each, player first, dealer last (standard blackjack order).
        for (int i = 0; i < 2; i++) {
            for (Player player : players) {
                player.getHands().get(0).getHand().addCard(deck.draw());
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
        PlayerHand mainHand = players.get(playerIndex).getHands().get(0);
        mainHand.placeInsuranceBet(mainHand.getBet() / 2);
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
                PlayerHand mainHand = player.getHands().get(0);
                if (mainHand.getInsuranceBet() > 0) {
                    mainHand.winInsurance(2.0);
                }
            }
        }
        // Insurance bets are not refunded if the dealer didn't have BJ — they are lost.
        // (PlayerHand.placeInsuranceBet already debited the balance.)

        resolveNaturalBlackjacks();
    }

    private void resolveNaturalBlackjacks() {
        // If the dealer has a natural blackjack, the round ends immediately:
        // players with a blackjack push, everyone else loses.
        if (dealer.getHand().isBlackJack()) {
            dealer.setHandRevealed(true);
            for (Player player : players) {
                PlayerHand mainHand = player.getHands().get(0);
                if (mainHand.getHand().isBlackJack()) {
                    mainHand.push();
                }
                mainHand.setSettled(true);
            }
            state = GameState.ROUND_OVER;
            return;
        }

        // Pay out any player blackjacks 3:2 and mark them as settled.
        for (Player player : players) {
            PlayerHand mainHand = player.getHands().get(0);
            if (mainHand.getHand().isBlackJack()) {
                mainHand.win(BLACKJACK_PAYOUT);
                mainHand.setSettled(true);
            }
        }

        state = GameState.PLAYER_TURN;
        advanceToNextActiveHand();
    }

    public void hit() {
        if (state != GameState.PLAYER_TURN) {
            throw new IllegalStateException("Cannot hit in state " + state);
        }
        PlayerHand current = currentPlayerHand();
        current.getHand().addCard(deck.draw());

        if (current.getHand().isBusted()) {
            // Bet was already subtracted at placeBet time: nothing more to do.
            current.setSettled(true);
            advanceToNextActiveHand();
        } else if (current.getHand().getScore() == 21) {
            // 21: no further decision, auto-stand.
            currentHandIndex++;
            advanceToNextActiveHand();
        }
    }

    public void stand() {
        if (state != GameState.PLAYER_TURN) {
            throw new IllegalStateException("Cannot stand in state " + state);
        }
        currentHandIndex++;
        advanceToNextActiveHand();
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

        for (Player player : players) {
            for (PlayerHand ph : player.getHands()) {
                if (ph.isSettled()) {
                    continue;
                }
                int playerScore = ph.getHand().getScore();

                if (dealerBust || playerScore > dealerScore) {
                    ph.win(NORMAL_PAYOUT);
                } else if (playerScore == dealerScore) {
                    ph.push();
                }
                // else: dealer wins, bet is already lost
                ph.setSettled(true);
            }
        }

        state = GameState.ROUND_OVER;
    }

    // --- Advanced actions: ---

    public void doubleDown() { // (#7)
        if (!canDoubleDown()) {
            throw new IllegalStateException("Cannot double down in state " + state);
        }
        PlayerHand current = currentPlayerHand();
        current.doubleBet();
        current.getHand().addCard(deck.draw());

        if (current.getHand().isBusted()) {
            current.setSettled(true);
        }
        // Double-down always ends the hand: auto-stand on any non-bust result.
        currentHandIndex++;
        advanceToNextActiveHand();
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
        if (currentHandIndex >= current.getHands().size()) {
            return false;
        }
        PlayerHand h = current.getHands().get(currentHandIndex);
        return h.getHand().getCards().size() == 2
                && current.getBalance() >= h.getBet();
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

    public PlayerHand getCurrentHand() {
        if (state != GameState.PLAYER_TURN || currentPlayerIndex >= players.size()) {
            return null;
        }
        Player p = players.get(currentPlayerIndex);
        if (currentHandIndex >= p.getHands().size()) {
            return null;
        }
        return p.getHands().get(currentHandIndex);
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

    private PlayerHand currentPlayerHand() {
        return players.get(currentPlayerIndex).getHands().get(currentHandIndex);
    }

    private void advanceToNextActiveHand() {
        while (currentPlayerIndex < players.size()) {
            Player p = players.get(currentPlayerIndex);
            while (currentHandIndex < p.getHands().size()) {
                if (!p.getHands().get(currentHandIndex).isSettled()) {
                    return; // found an active hand
                }
                currentHandIndex++;
            }
            currentPlayerIndex++;
            currentHandIndex = 0;
        }
        beginDealerTurn();
    }

    private void beginDealerTurn() {
        // If no hand is still in contention (all already settled),
        // the dealer does not draw — but still reveal the hole card.
        boolean anyInContention = false;
        outer:
        for (Player p : players) {
            for (PlayerHand h : p.getHands()) {
                if (!h.isSettled()) {
                    anyInContention = true;
                    break outer;
                }
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
