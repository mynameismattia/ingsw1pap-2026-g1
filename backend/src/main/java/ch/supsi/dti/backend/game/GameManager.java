package ch.supsi.dti.backend.game;

import ch.supsi.dti.backend.model.Dealer;
import ch.supsi.dti.backend.model.Deck;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.*;
import java.util.ArrayList;
import java.util.List;

import java.util.List;

public class GameManager {

    // Constants
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final double BLACKJACK_PAYOUT = 1.5;

    // Variables
    private List<Player> players;
    private Dealer dealer;
    private Deck deck;
    private GameState state;
    private int currentPlayerIndex;

    public GameManager(List<String> playersNames, int initialBalance) {
        this.players = new ArrayList<>();
        for (String name : playersNames) {
            players.add(new Player(name, initialBalance));
        }
        this.dealer = new Dealer();
        this.state = GameState.WAITING;
        this.deck = new Deck();
        this.currentPlayerIndex = 0;
    }

    // Methods
    public void startNewRound() {
        if (!(state == GameState.WAITING || state == GameState.ROUND_OVER)) {
            throw new IllegalStateException("Error state of the game");
        }

        for (Player player : players) {
            player.getHand().clear();
            player.resetBet();
        }
        dealer.getHand().clear();

        dealer.setHandRevealed(false);

        if(deck.needsReshuffle()){
            deck.reset();
        }
        state = GameState.BETTING;
    }

    public void placeBet(int playerIndex, int amount) {
        if (!(state == GameState.BETTING)) {
            throw new IllegalStateException("Error state of the game");
        }

        if (playerIndex >= players.size()) {
            throw new IllegalStateException("Player index out of bounds");
        }

        if (!(amount >= MIN_BET && amount <= MAX_BET)){
            throw new IllegalArgumentException("Amount must be between " + MIN_BET + " and " + MAX_BET);
        }

        players.get(playerIndex).placeBet(amount);
    }

    public void deal() {}

    public void hit() {}

    public void stand() {}

    public void doubleDown() {} // (#7)

    public void split() {} // (#6)

    public void insurance() {} // (#5)

    public void dealerPlay() {} // (#3)

    public void resolveRound() {} // (#4)

    public boolean canSplit() { return false; }

    public boolean canDoubleDown() { return false; }

    public boolean canInsure() { return false; }

    public List<?> getHistory() { return null; } // (#13)

    // TODO: return types will be Player, GameState, Dealer once model is ready
    public Object getCurrentPlayer() { return null; }

    public GameState getState() { return state; }

    public Object getDealer() { return null; }
}
