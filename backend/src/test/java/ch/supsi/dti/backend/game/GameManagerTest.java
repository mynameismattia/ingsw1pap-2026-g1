package ch.supsi.dti.backend.game;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Deck;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameManagerTest {

    private static final int INITIAL_BALANCE = 100;
    private static final int BET = 10;

    /**
     * Deck that draws cards in a predefined order. Used to make round flow
     * deterministic: draw(i) returns the i-th card supplied in the constructor.
     */
    private static class StackedDeck extends Deck {
        private final Deque<Card> stack;

        StackedDeck(List<Card> drawOrder) {
            super();
            this.stack = new ArrayDeque<>(drawOrder);
        }

        @Override
        public Card draw() {
            return stack.pop();
        }

        @Override
        public boolean needsReshuffle() {
            return false;
        }

        @Override
        public int remainingCards() {
            return stack.size();
        }
    }

    private static Card c(Rank rank) {
        return new Card(Suit.HEARTS, rank);
    }

    private static GameManager singlePlayer(Deck deck) {
        return new GameManager(List.of("Alice"), INITIAL_BALANCE, deck);
    }

    // --- startNewRound / state guards ---

    @Test
    void testStartNewRoundFromWaiting() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        gm.startNewRound();
        assertEquals(GameState.BETTING, gm.getState());
    }

    @Test
    void testStartNewRoundFromInvalidStateThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        gm.startNewRound();
        assertThrows(IllegalStateException.class, gm::startNewRound);
    }

    // --- placeBet validation ---

    @Test
    void testPlaceBetUnderMinimum() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        gm.startNewRound();
        assertThrows(IllegalArgumentException.class, () -> gm.placeBet(0, 1));
    }

    @Test
    void testPlaceBetOverMaximum() {
        GameManager gm = new GameManager(List.of("Alice"), 5000);
        gm.startNewRound();
        assertThrows(IllegalArgumentException.class, () -> gm.placeBet(0, 2000));
    }

    @Test
    void testPlaceBetOverBalance() {
        GameManager gm = new GameManager(List.of("Alice"), 20);
        gm.startNewRound();
        // Within table bounds but exceeds player balance: Player rejects it.
        assertThrows(IllegalArgumentException.class, () -> gm.placeBet(0, 50));
    }

    @Test
    void testPlaceBetInWrongStateThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertThrows(IllegalStateException.class, () -> gm.placeBet(0, BET));
    }

    @Test
    void testDealWithoutBetsThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        gm.startNewRound();
        assertThrows(IllegalStateException.class, gm::deal);
    }

    // --- Round outcomes ---

    @Test
    void testPlayerBlackjackPays3to2() {
        // Draw order: player1, dealer1, player2, dealer2
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.KING),   // player1, dealer1
                c(Rank.KING), c(Rank.QUEEN)  // player2, dealer2 -> dealer = 20, no BJ
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();

        Player alice = gm.getPlayers().get(0);
        // 100 - 10 bet + (10 + 15 payout) = 115
        assertEquals(INITIAL_BALANCE + (int) (BET * 1.5), alice.getBalance());
        // Only player has a BJ and is settled -> dealer is skipped, round ends.
        assertEquals(GameState.ROUND_OVER, gm.getState());
    }

    @Test
    void testDealerBlackjackEndsRoundImmediately() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.KING) // dealer = A+K = BJ
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertTrue(gm.getDealer().isHandRevealed());
        // Player had no BJ: loses the bet.
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testPushOnMutualBlackjack() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.ACE),
                c(Rank.KING), c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testPlayerBusts() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.SEVEN),
                c(Rank.QUEEN), c(Rank.TEN),
                // hit card: busts the player with 30
                c(Rank.TEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertTrue(gm.getPlayers().get(0).getHand().isBusted());
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testPushReturnsBet() {
        // Player 20, dealer 20
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.KING),
                c(Rank.QUEEN), c(Rank.QUEEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testPlayerWinsOnHigherScore() {
        // Player 20, dealer will stop on 17
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.SEVEN),
                c(Rank.QUEEN), c(Rank.TEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        // Even money: +10
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDealerWinsOnHigherScore() {
        // Player 18, dealer 20
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.KING),
                c(Rank.EIGHT), c(Rank.QUEEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDealerBustsPaysPlayer() {
        // Player 18; dealer 16 -> must hit -> gets K -> busts at 26
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.SIX),
                c(Rank.EIGHT), c(Rank.KING),
                c(Rank.KING) // dealer hit -> bust
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        assertTrue(gm.getDealer().getHand().isBusted());
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
    }

    // --- Dealer rules (hits soft 17, stands hard 17) ---

    @Test
    void testDealerHitsOnSoft17() {
        // Dealer starts A+6 (soft 17). Hit card = 3 -> hard 20. Stands.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.ACE),
                c(Rank.NINE), c(Rank.SIX),
                c(Rank.THREE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        assertEquals(20, gm.getDealer().getHand().getScore());
        assertEquals(3, gm.getDealer().getHand().getCards().size());
    }

    @Test
    void testDealerStandsOnHard17() {
        // Dealer K+7 = hard 17, must stand.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.KING),
                c(Rank.NINE), c(Rank.SEVEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();

        assertEquals(17, gm.getDealer().getHand().getScore());
        assertEquals(2, gm.getDealer().getHand().getCards().size());
    }

    // --- Turn management ---

    @Test
    void testGetCurrentPlayerReturnsNullOutsidePlayerTurn() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertNull(gm.getCurrentPlayer());
    }

    @Test
    void testTwoPlayersTurnSequence() {
        // Draw order inside deal() with 2 players is:
        //   round i=0: p1, p2, dealer ; round i=1: p1, p2, dealer
        StackedDeck ordered = new StackedDeck(List.of(
                c(Rank.KING),  // p1[0]
                c(Rank.NINE),  // p2[0]
                c(Rank.TEN),   // d[0]
                c(Rank.QUEEN), // p1[1]  -> p1 = 20
                c(Rank.EIGHT), // p2[1]  -> p2 = 17
                c(Rank.SEVEN)  // d[1]   -> dealer = 17
        ));
        GameManager gm = new GameManager(List.of("Alice", "Bob"), INITIAL_BALANCE, ordered);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();

        assertEquals("Alice", gm.getCurrentPlayer().getName());
        gm.stand();
        assertEquals("Bob", gm.getCurrentPlayer().getName());
        gm.stand();
        assertEquals(GameState.DEALER_TURN, gm.getState());
    }

    @Test
    void testMultiRoundSession() {
        StackedDeck deck = new StackedDeck(List.of(
                // Round 1: player 20 vs dealer 17 -> player wins
                c(Rank.KING), c(Rank.KING),
                c(Rank.QUEEN), c(Rank.SEVEN),
                // Round 2: player 18 vs dealer 20 -> player loses
                c(Rank.TEN), c(Rank.KING),
                c(Rank.EIGHT), c(Rank.QUEEN)
        ));
        GameManager gm = singlePlayer(deck);

        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();
        int afterRound1 = gm.getPlayers().get(0).getBalance();
        assertEquals(INITIAL_BALANCE + BET, afterRound1);

        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.stand();
        gm.dealerPlay();
        assertEquals(afterRound1 - BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testHitInWrongStateThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertThrows(IllegalStateException.class, gm::hit);
    }

    @Test
    void testStandInWrongStateThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertThrows(IllegalStateException.class, gm::stand);
    }

    @Test
    void testDealerPlayInWrongStateThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertThrows(IllegalStateException.class, gm::dealerPlay);
    }

    // --- Out of scope for v1 ---

    @Test @Disabled("Insurance is not supported in v1 (#5)")
    void testInsurancePays2to1() {}

    @Test @Disabled("Split is not supported in v1 (#6)")
    void testSplitCreates2Hands() {}

    @Test @Disabled("Resplit Aces is not supported in v1 (#9)")
    void testResplitAces() {}

    @Test @Disabled("Double down after split is not supported in v1 (#8)")
    void testDoubleDownAfterSplit() {}

    @Test @Disabled("Game history is not supported in v1 (#13)")
    void testGameHistory() {}
}
