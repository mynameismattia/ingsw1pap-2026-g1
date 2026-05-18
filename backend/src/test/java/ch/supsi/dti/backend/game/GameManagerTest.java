package ch.supsi.dti.backend.game;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Deck;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.PlayerHand;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.RoundRecord;
import ch.supsi.dti.backend.model.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        gm.declineInsurance(0);

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
        gm.declineInsurance(0);

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
        assertTrue(gm.getPlayers().get(0).getHands().get(0).getHand().isBusted());
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
        gm.declineInsurance(0);
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

    // --- Game over ---

    @Test
    void testGameOverWhenBalanceFallsBelowMinBet() {
        // Player 16 vs dealer 20 -> player loses 95 of 99, ends with 4 (< MIN_BET 5).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN)
        ));
        GameManager gm = new GameManager(List.of("Alice"), 99, deck);
        gm.startNewRound();
        gm.placeBet(0, 95);
        gm.deal();
        gm.stand();
        gm.dealerPlay();
        assertEquals(4, gm.getPlayers().get(0).getBalance());

        gm.startNewRound();
        assertEquals(GameState.GAME_OVER, gm.getState());
    }

    @Test
    void testStartNewRoundFromGameOverThrows() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN)
        ));
        GameManager gm = new GameManager(List.of("Alice"), 99, deck);
        gm.startNewRound();
        gm.placeBet(0, 95);
        gm.deal();
        gm.stand();
        gm.dealerPlay();
        gm.startNewRound(); // transitions to GAME_OVER
        assertThrows(IllegalStateException.class, gm::startNewRound);
    }

    @Test
    void testPlaceBetTwiceThrows() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        gm.startNewRound();
        gm.placeBet(0, BET);
        assertThrows(IllegalStateException.class, () -> gm.placeBet(0, BET));
    }

    @Test
    void testPlayerAutoStandsOnTwentyOne() {
        // Player starts 7+7 = 14, hits a 7 -> 21. Dealer stands on 18.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.SEVEN), c(Rank.TEN),
                c(Rank.SEVEN), c(Rank.EIGHT),
                c(Rank.SEVEN) // hit card -> player = 21
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();
        // After hitting to 21 the player auto-stands: dealer must play immediately.
        assertEquals(GameState.DEALER_TURN, gm.getState());
        assertThrows(IllegalStateException.class, gm::hit);
        gm.dealerPlay();
        // Player 21 > dealer 18 -> even-money win.
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
        // 21 from 3 cards is NOT a natural blackjack: only 3:2 payout for 2-card 21.
    }

    @Test
    void testDealerHandRevealedWhenPlayerBusts() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.QUEEN),
                c(Rank.SEVEN), c(Rank.TEN),
                c(Rank.TEN) // hit card: busts player at 27
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();
        assertTrue(gm.getDealer().isHandRevealed());
        assertEquals(GameState.ROUND_OVER, gm.getState());
    }

    // --- Insurance ---

    @Test
    void testInsuranceOfferedOnDealerAce() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),   // p1, d1 (Ace upcard)
                c(Rank.NINE), c(Rank.NINE)   // p2 -> 19, d2 -> 20 (no BJ)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertEquals(GameState.INSURANCE_OFFER, gm.getState());
        assertTrue(gm.canInsure());
    }

    @Test
    void testNoInsuranceWhenDealerHasNoAce() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.SEVEN),
                c(Rank.NINE), c(Rank.NINE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertFalse(gm.canInsure());
    }

    @Test
    void testInsurancePays2to1OnDealerBlackjack() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.NINE), c(Rank.ACE),   // p1=9, d1=A
                c(Rank.SEVEN), c(Rank.KING)  // p2 -> 16, d2 -> 21 (BJ)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.takeInsurance(0);
        // Main bet (10) lost, insurance (5) wins 10 → net = -10 + 10 = 0
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertTrue(gm.getDealer().isHandRevealed());
    }

    @Test
    void testInsuranceLostWhenDealerHasNoBlackjack() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),   // p1=10, d1=A
                c(Rank.NINE), c(Rank.NINE)   // p2 -> 19, d2 -> 20 (no BJ)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.takeInsurance(0);
        // After insurance phase, dealer didn't have BJ → continue with PLAYER_TURN.
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        // Balance is initial - main bet (10) - insurance bet (5) = 85
        assertEquals(INITIAL_BALANCE - BET - BET / 2, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDeclineInsuranceContinuesPlay() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.NINE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.declineInsurance(0);
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals(0, gm.getPlayers().get(0).getHands().get(0).getInsuranceBet());
    }

    @Test
    void testInsuranceCannotBeAnsweredTwice() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.NINE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.declineInsurance(0);
        assertThrows(IllegalStateException.class, () -> gm.takeInsurance(0));
    }

    // --- Double down ---

    @Test
    void testDoubleDownDoublesBetAndDealsOneCard() {
        // p1=5, p2=6 -> 11. Double, hit card K -> 21. Dealer stands on 20.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN),
                c(Rank.KING) // double-down card
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertTrue(gm.canDoubleDown());
        gm.doubleDown();
        // Only one card drawn after double; auto-stand → DEALER_TURN.
        assertEquals(3, gm.getPlayers().get(0).getHands().get(0).getHand().getCards().size());
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();
        // Player 21 > dealer 20 → wins on a doubled bet (20). Net = -10 (placeBet) -10 (double) + 40 = +20
        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDoubleDownBustForfeitsDoubledBet() {
        // p1=K, p2=K -> 20. Double, hit card K -> 30 bust.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.QUEEN),
                c(Rank.KING), c(Rank.TEN),
                c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.doubleDown();
        assertTrue(gm.getPlayers().get(0).getHands().get(0).getHand().isBusted());
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(INITIAL_BALANCE - 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDoubleDownNotAllowedAfterHit() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN),
                c(Rank.TWO) // hit card
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();
        assertFalse(gm.canDoubleDown());
        assertThrows(IllegalStateException.class, gm::doubleDown);
    }

    @Test
    void testDoubleDownRejectedWithoutEnoughBalance() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN)
        ));
        // Balance = 50, bet = 30 → can't double (would need another 30, only 20 left).
        GameManager gm = new GameManager(List.of("Alice"), 50, deck);
        gm.startNewRound();
        gm.placeBet(0, 30);
        gm.deal();
        assertFalse(gm.canDoubleDown());
        assertThrows(IllegalStateException.class, gm::doubleDown);
    }

    // --- Split ---

    @Test
    void testSplitCreatesTwoHands() {
        // p1=8, p2=8 -> pair of 8s. Dealer 9+9 = 18 (no Ace, no insurance).
        // Split: hand1 gets 8 + 3 = 11, hand2 gets 8 + 4 = 12.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.THREE), c(Rank.FOUR) // split cards: first hand, second hand
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertTrue(gm.canSplit());
        gm.split();

        List<PlayerHand> hands = gm.getPlayers().get(0).getHands();
        assertEquals(2, hands.size());
        assertEquals(2, hands.get(0).getHand().getCards().size());
        assertEquals(2, hands.get(1).getHand().getCards().size());
        assertEquals(BET, hands.get(0).getBet());
        assertEquals(BET, hands.get(1).getBet());
        // After split, balance is initial - 2*bet.
        assertEquals(INITIAL_BALANCE - 2 * BET, gm.getPlayers().get(0).getBalance());
        // First split hand is active (11, not 21): state stays PLAYER_TURN.
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals(hands.get(0), gm.getCurrentHand());
    }

    @Test
    void testCannotSplitDifferentRanks() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.SEVEN), c(Rank.NINE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertFalse(gm.canSplit());
        assertThrows(IllegalStateException.class, gm::split);
    }

    @Test
    void testCannotSplitWithoutBalance() {
        // Balance just enough for first bet, not for a second.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.EIGHT), c(Rank.NINE)
        ));
        GameManager gm = new GameManager(List.of("Alice"), 10, deck);
        gm.startNewRound();
        gm.placeBet(0, 10);
        gm.deal();
        assertFalse(gm.canSplit());
        assertThrows(IllegalStateException.class, gm::split);
    }

    @Test
    void testSplitAcesGetOneCardAndAutoStand() {
        // Player A+A → split. Each hand: A + drawn. Dealer 9+9 = 18 (no Ace upcard).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.NINE),
                c(Rank.ACE), c(Rank.NINE),
                c(Rank.FIVE), c(Rank.SEVEN) // split cards
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        List<PlayerHand> hands = gm.getPlayers().get(0).getHands();
        assertEquals(2, hands.size());
        // Each hand has exactly 2 cards (the original Ace + one drawn card).
        assertEquals(2, hands.get(0).getHand().getCards().size());
        assertEquals(2, hands.get(1).getHand().getCards().size());
        // Both hands auto-stand → state advances to DEALER_TURN.
        assertEquals(GameState.DEALER_TURN, gm.getState());
        assertThrows(IllegalStateException.class, gm::hit);
    }

    @Test
    void testSplitPlaysBothHandsThenDealer() {
        // Split 8s. Hand1: 8+3=11 → stand. Hand2: 8+5=13 → stand. Dealer 9+9 = 18.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.THREE), c(Rank.FIVE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        // Active hand = first (11)
        assertEquals(11, gm.getCurrentHand().getHand().getScore());
        gm.stand();
        // Active hand = second (13)
        assertEquals(13, gm.getCurrentHand().getHand().getScore());
        gm.stand();
        // Dealer's turn
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();
        // Dealer 18 beats both player hands (11, 13) → both bets lost.
        assertEquals(INITIAL_BALANCE - 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testSplitHandsResolveIndependently() {
        // Split 8s. Hand1: 8+K=18 → stand. Hand2: 8+3=11 → hit T → 21 (auto-stand).
        // Dealer 6+6=12 → must hit. Draws K → 22 bust.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.SIX),
                c(Rank.EIGHT), c(Rank.SIX),
                c(Rank.KING), c(Rank.THREE), // split cards (h1, h2)
                c(Rank.TEN),                  // hit on h2 -> 21 auto-stand
                c(Rank.KING)                  // dealer hit -> 22 bust
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();
        // h1 = 8+K = 18
        gm.stand();
        // h2 = 8+3 = 11 → hit
        gm.hit();
        // h2 = 11 + 10 = 21 → auto-stand → dealer turn
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();
        // Dealer busts → both player hands win. Net: -2*BET (placeBet+split) + 2*(2*BET) = +2*BET
        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testCannotSplitInWrongState() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertFalse(gm.canSplit());
        assertThrows(IllegalStateException.class, gm::split);
    }

    // --- Multiplayer flow ---

    @Test
    void testMultiplePlayersTakeTurnsSequentially() {
        // Deal order for 2 players: P1c1, P2c1, D1, P1c2, P2c2, D2.
        // Alice 10+9=19, Bob 10+8=18, Dealer 10+9=19 (no hits).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.TEN), c(Rank.TEN),
                c(Rank.NINE), c(Rank.EIGHT), c(Rank.NINE)));
        GameManager gm = new GameManager(List.of("Alice", "Bob"), INITIAL_BALANCE, deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();

        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals("Alice", gm.getCurrentPlayer().getName());
        gm.stand();
        assertEquals("Bob", gm.getCurrentPlayer().getName());
        gm.stand();
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();

        // Alice 19 pushes against dealer 19, Bob 18 loses.
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance());
    }

    @Test
    void testBrokePlayerSitsOutAndOthersContinue() {
        StackedDeck deck = new StackedDeck(List.of(
                // Bob alone gets dealt: Bc1, D1, Bc2, D2
                c(Rank.TEN), c(Rank.TEN), c(Rank.NINE), c(Rank.NINE)));
        GameManager gm = new GameManager(List.of("Alice", "Bob"), INITIAL_BALANCE, deck);
        gm.getPlayers().get(0).debit(98); // Alice balance = 2 (< MIN_BET=5)

        gm.startNewRound();
        assertTrue(gm.getPlayers().get(0).isSittingOut());
        assertFalse(gm.getPlayers().get(1).isSittingOut());

        assertThrows(IllegalStateException.class, () -> gm.placeBet(0, 5));
        gm.placeBet(1, BET);
        gm.deal();

        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals("Bob", gm.getCurrentPlayer().getName());
        gm.stand();
        gm.dealerPlay();
        // Bob push vs dealer 19; Alice did not play, balance unchanged.
        assertEquals(2, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(1).getBalance());
        // Sit-out leaves no history record.
        assertTrue(gm.getHistory().stream().noneMatch(r -> r.playerName().equals("Alice")));
    }

    // --- Round history (#13) ---

    @Test
    void testHistoryRecordedForNaturalBlackjack() {
        // Alice: A + K = BJ. Dealer: 10 + 5 = 15 (no Ace, no insurance phase).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.TEN), c(Rank.KING), c(Rank.FIVE)));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();

        assertEquals(GameState.ROUND_OVER, gm.getState());
        List<RoundRecord> history = gm.getHistory();
        assertEquals(1, history.size());
        RoundRecord rec = history.get(0);
        assertEquals("Alice", rec.playerName());
        assertEquals(HandOutcome.BLACKJACK, rec.outcome());
        assertEquals(BET, rec.bet());
        assertEquals(21, rec.playerScore());
    }

    @Test
    void testHistoryRecordedPerHandAfterSplit() {
        // Alice 8,8 (split); dealer 6,10. Hand1 draws 5 (=13), hand2 draws 9 (=17).
        // Dealer hits on 16 → draws 7 → busts at 23 → both Alice hands WIN.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.SIX), c(Rank.EIGHT), c(Rank.TEN), // deal
                c(Rank.FIVE), c(Rank.NINE),                              // post-split draws
                c(Rank.SEVEN)));                                          // dealer hit
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();
        gm.stand(); // stand on hand 1 (13)
        gm.stand(); // stand on hand 2 (17)
        gm.dealerPlay();

        List<RoundRecord> history = gm.getHistory();
        assertEquals(2, history.size());
        for (RoundRecord r : history) {
            assertEquals("Alice", r.playerName());
            assertEquals(HandOutcome.WIN, r.outcome());
            assertEquals(BET, r.bet());
        }
    }

    @Test
    void testHistoryIsUnmodifiable() {
        GameManager gm = singlePlayer(new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.TEN), c(Rank.KING), c(Rank.FIVE))));
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        List<RoundRecord> history = gm.getHistory();
        assertThrows(UnsupportedOperationException.class,
                () -> history.add(history.get(0)));
    }

    // --- Bug-fix coverage (B1/B2/B3 + multiplayer corner cases) ---

    @Test
    void testInsuranceFlowInMultiplayer() {
        // Deal order: A1,B1,C1,D1, A2,B2,C2,D2.
        // Alice 5+6=11, Bob 5+7=12, Charlie 5+8=13, Dealer Ace+7=18 (no BJ → insurance still asked).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.FIVE), c(Rank.ACE),
                c(Rank.SIX), c(Rank.SEVEN), c(Rank.EIGHT), c(Rank.SEVEN)));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob", "Charlie"), INITIAL_BALANCE, deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.placeBet(2, BET);
        gm.deal();
        assertEquals(GameState.INSURANCE_OFFER, gm.getState());

        gm.takeInsurance(0);
        gm.declineInsurance(1);
        gm.takeInsurance(2);

        // Dealer has no BJ → insurance bets are lost, round proceeds.
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        // Alice and Charlie each lost BET/2 on insurance.
        assertEquals(INITIAL_BALANCE - BET - BET / 2, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE - BET,           gm.getPlayers().get(1).getBalance());
        assertEquals(INITIAL_BALANCE - BET - BET / 2, gm.getPlayers().get(2).getBalance());
    }

    @Test
    void testInsuranceWithSitOutSkipped() {
        // Bob (idx 1) is broke → sit-out. Only Alice and Charlie are asked.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.ACE), // i=0: A,C,D
                c(Rank.SIX),  c(Rank.SIX),  c(Rank.SEVEN))); // i=1
        GameManager gm = new GameManager(
                List.of("Alice", "Bob", "Charlie"), INITIAL_BALANCE, deck);
        gm.getPlayers().get(1).debit(98); // Bob balance = 2 (< MIN_BET)

        gm.startNewRound();
        assertTrue(gm.getPlayers().get(1).isSittingOut());

        gm.placeBet(0, BET);
        gm.placeBet(2, BET);
        gm.deal();
        assertEquals(GameState.INSURANCE_OFFER, gm.getState());

        assertThrows(IllegalStateException.class, () -> gm.takeInsurance(1));

        gm.declineInsurance(0);
        gm.declineInsurance(2);
        // Phase ends after the two active players answer (Bob skipped).
        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals(2, gm.getPlayers().get(1).getBalance()); // Bob untouched
    }

    @Test
    void testConsecutiveRoundsInMultiplayer() {
        // Round 1 deal: A 10+9=19, B 9+8=17, Dealer 10+8=18 → Alice WIN, Bob LOSE.
        // Round 2 deal: A 5+5=10, B 5+5=10, Dealer 9+9=18 → Alice LOSE, Bob LOSE.
        StackedDeck deck = new StackedDeck(List.of(
                // Round 1
                c(Rank.TEN), c(Rank.NINE), c(Rank.TEN),
                c(Rank.NINE), c(Rank.EIGHT), c(Rank.EIGHT),
                // Round 2
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.NINE),
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.NINE)));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob"), INITIAL_BALANCE, deck);

        // Round 1
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();
        gm.stand(); // Alice
        gm.stand(); // Bob
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(2, gm.getHistory().size());
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance()); // Alice WIN
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance()); // Bob LOSE

        // Round 2 — verifies insuranceDecisions/clear works, sit-out re-evaluated.
        gm.startNewRound();
        assertEquals(GameState.BETTING, gm.getState());
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();
        gm.stand();
        gm.stand();
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(4, gm.getHistory().size());
        // Both lose round 2.
        assertEquals(HandOutcome.LOSE, gm.getHistory().get(2).outcome());
        assertEquals(HandOutcome.LOSE, gm.getHistory().get(3).outcome());
    }

    @Test
    void testNextActivePlayerSkipsMiddleSitOut() {
        // Bob (idx 1) is broke → sit-out. Turn order: Alice → Charlie (Bob skipped).
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.TEN), c(Rank.NINE),  // i=0: A, C, D
                c(Rank.NINE), c(Rank.NINE), c(Rank.NINE) // i=1
        ));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob", "Charlie"), INITIAL_BALANCE, deck);
        gm.getPlayers().get(1).debit(98);

        gm.startNewRound();
        gm.placeBet(0, BET);
        assertThrows(IllegalStateException.class, () -> gm.placeBet(1, BET));
        gm.placeBet(2, BET);
        gm.deal();

        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals("Alice", gm.getCurrentPlayer().getName());
        gm.stand();
        assertEquals("Charlie", gm.getCurrentPlayer().getName(), "Bob must be skipped");
        gm.stand();
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());

        // Both active players push against dealer 18.
        assertEquals(2, gm.getPlayers().get(1).getBalance(), "Bob balance untouched");
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance()); // Alice 19 wins
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(2).getBalance()); // Charlie 19 wins
    }

    @Test
    void testSplitSecondHandAuto21() {
        // Split 10s. Hand1: 10+5=15, Hand2: 10+A=21 (auto-stand on h2 expected).
        // Dealer 8+9=17 (hard, no hit). H1 stands at 15 → LOSE; H2=21 → WIN.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.EIGHT),    // deal: P1c1, D1
                c(Rank.TEN), c(Rank.NINE),     // deal: P1c2, D2
                c(Rank.FIVE),                  // split: h1 draw → 15
                c(Rank.ACE)));                 // split: h2 draw → 21
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        // Active hand must still be h1 (15) since only h2 hit 21.
        assertEquals(15, gm.getCurrentHand().getHand().getScore());
        gm.stand();

        // After standing h1, cursor must skip h2 (settled at 21) → DEALER_TURN.
        assertEquals(GameState.DEALER_TURN, gm.getState(),
                "Hand2 at 21 from split-deal must auto-stand");
        gm.dealerPlay();

        // h1 (15) loses to dealer 17; h2 (21) wins. Net: -BET + BET = 0 vs initial.
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testSplitBothHandsAuto21() {
        // Split 10s. Both hands draw an Ace → both 21. No player action needed.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.EIGHT),    // deal
                c(Rank.TEN), c(Rank.NINE),
                c(Rank.ACE),                   // h1 draw → 21
                c(Rank.ACE)));                 // h2 draw → 21
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        // Both hands auto-stand → no contention → dealer reveals and resolves.
        assertEquals(GameState.ROUND_OVER, gm.getState());
        // Both 21 vs dealer 17 → both WIN.
        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDoubleAcrossPlayersInMultiplayer() {
        // A1=5,B1=10,D1=5,A2=6,B2=9,D2=6 → Alice 11, Bob 19, Dealer 11.
        // Alice doubles, draws TEN → 21. Bob stands. Dealer hits, draws 9 → 20.
        // Alice 21 > 20 → WIN doubled bet; Bob 19 < 20 → LOSE.
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.TEN), c(Rank.FIVE),
                c(Rank.SIX),  c(Rank.NINE), c(Rank.SIX),
                c(Rank.TEN),  // Alice's double-down card
                c(Rank.NINE)  // dealer's hit card
        ));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob"), INITIAL_BALANCE, deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();
        assertEquals("Alice", gm.getCurrentPlayer().getName());
        assertTrue(gm.canDoubleDown());
        gm.doubleDown();
        // After double, Alice's hand is auto-settled (no bust) and turn passes to Bob.
        assertEquals("Bob", gm.getCurrentPlayer().getName(),
                "Turn must advance to next player after double-down");
        gm.stand();
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());
        // Alice: initial 100, -10 placeBet, -10 double → 80, win pays 20+20=40 → 120.
        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
        // Bob: -10 (lost), 90.
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance());
    }
}
