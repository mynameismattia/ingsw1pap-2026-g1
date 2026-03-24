package ch.supsi.dti.backend.game;

import java.util.List;

public class GameManager {
    // Constants
    private static final int MIN_BET = 5;
    private static final int MAX_BET = 1000;
    private static final double BLACKJACK_PAYOUT = 1.5;

    // Variables
    // private List<Player> players;  // TODO: uncomment when model is ready
    // private Dealer dealer;
    // private Deck deck;
    private GameState state;
    private int currentPlayerIndex;

    // Methods
    public void startNewRound() {}

    public void placeBet(int playerIndex, int amount) {}

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
