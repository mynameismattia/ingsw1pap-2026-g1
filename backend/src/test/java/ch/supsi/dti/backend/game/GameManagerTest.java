// Test del flusso di gioco end-to-end: placeBet, deal iniziale, hit/stand del player, dealer turn, payout corretto in base agli outcome, transizioni di fase.

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

    @Test
    void testPlayerBlackjackPays3to2() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.KING),
                c(Rank.KING), c(Rank.QUEEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();

        Player alice = gm.getPlayers().get(0);

        assertEquals(INITIAL_BALANCE + (int) (BET * 1.5), alice.getBalance());

        assertEquals(GameState.ROUND_OVER, gm.getState());
    }

    @Test
    void testDealerBlackjackEndsRoundImmediately() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.declineInsurance(0);

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertTrue(gm.getDealer().isHandRevealed());

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

        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDealerWinsOnHigherScore() {

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

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.SIX),
                c(Rank.EIGHT), c(Rank.KING),
                c(Rank.KING)
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

    @Test
    void testDealerHitsOnSoft17() {

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

    @Test
    void testGetCurrentPlayerReturnsNullOutsidePlayerTurn() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertNull(gm.getCurrentPlayer());
    }

    @Test
    void testTwoPlayersTurnSequence() {

        StackedDeck ordered = new StackedDeck(List.of(
                c(Rank.KING),
                c(Rank.NINE),
                c(Rank.TEN),
                c(Rank.QUEEN),
                c(Rank.EIGHT),
                c(Rank.SEVEN)
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

                c(Rank.KING), c(Rank.KING),
                c(Rank.QUEEN), c(Rank.SEVEN),

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

    @Test
    void testGameOverWhenBalanceFallsBelowMinBet() {

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
        gm.startNewRound();
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

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.SEVEN), c(Rank.TEN),
                c(Rank.SEVEN), c(Rank.EIGHT),
                c(Rank.SEVEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();

        assertEquals(GameState.DEALER_TURN, gm.getState());
        assertThrows(IllegalStateException.class, gm::hit);
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());

    }

    @Test
    void testDealerHandRevealedWhenPlayerBusts() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.QUEEN),
                c(Rank.SEVEN), c(Rank.TEN),
                c(Rank.TEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.hit();
        assertTrue(gm.getDealer().isHandRevealed());
        assertEquals(GameState.ROUND_OVER, gm.getState());
    }

    @Test
    void testInsuranceOfferedOnDealerAce() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.NINE)
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
                c(Rank.NINE), c(Rank.ACE),
                c(Rank.SEVEN), c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.takeInsurance(0);

        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertTrue(gm.getDealer().isHandRevealed());
    }

    @Test
    void testInsuranceLostWhenDealerHasNoBlackjack() {
        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.KING), c(Rank.ACE),
                c(Rank.NINE), c(Rank.NINE)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.takeInsurance(0);

        assertEquals(GameState.PLAYER_TURN, gm.getState());

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

    @Test
    void testDoubleDownDoublesBetAndDealsOneCard() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.KING),
                c(Rank.SIX), c(Rank.QUEEN),
                c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        assertTrue(gm.canDoubleDown());
        gm.doubleDown();

        assertEquals(3, gm.getPlayers().get(0).getHands().get(0).getHand().getCards().size());
        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDoubleDownBustForfeitsDoubledBet() {

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
                c(Rank.TWO)
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

        GameManager gm = new GameManager(List.of("Alice"), 50, deck);
        gm.startNewRound();
        gm.placeBet(0, 30);
        gm.deal();
        assertFalse(gm.canDoubleDown());
        assertThrows(IllegalStateException.class, gm::doubleDown);
    }

    @Test
    void testSplitCreatesTwoHands() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.EIGHT), c(Rank.NINE),
                c(Rank.THREE), c(Rank.FOUR)
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

        assertEquals(INITIAL_BALANCE - 2 * BET, gm.getPlayers().get(0).getBalance());

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

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.ACE), c(Rank.NINE),
                c(Rank.ACE), c(Rank.NINE),
                c(Rank.FIVE), c(Rank.SEVEN)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        List<PlayerHand> hands = gm.getPlayers().get(0).getHands();
        assertEquals(2, hands.size());

        assertEquals(2, hands.get(0).getHand().getCards().size());
        assertEquals(2, hands.get(1).getHand().getCards().size());

        assertEquals(GameState.DEALER_TURN, gm.getState());
        assertThrows(IllegalStateException.class, gm::hit);
    }

    @Test
    void testSplitPlaysBothHandsThenDealer() {

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

        assertEquals(11, gm.getCurrentHand().getHand().getScore());
        gm.stand();

        assertEquals(13, gm.getCurrentHand().getHand().getScore());
        gm.stand();

        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE - 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testSplitHandsResolveIndependently() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.SIX),
                c(Rank.EIGHT), c(Rank.SIX),
                c(Rank.KING), c(Rank.THREE),
                c(Rank.TEN),
                c(Rank.KING)
        ));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        gm.stand();

        gm.hit();

        assertEquals(GameState.DEALER_TURN, gm.getState());
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testCannotSplitInWrongState() {
        GameManager gm = new GameManager(List.of("Alice"), INITIAL_BALANCE);
        assertFalse(gm.canSplit());
        assertThrows(IllegalStateException.class, gm::split);
    }

    @Test
    void testMultiplePlayersTakeTurnsSequentially() {

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

        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance());
    }

    @Test
    void testBrokePlayerSitsOutAndOthersContinue() {
        StackedDeck deck = new StackedDeck(List.of(

                c(Rank.TEN), c(Rank.TEN), c(Rank.NINE), c(Rank.NINE)));
        GameManager gm = new GameManager(List.of("Alice", "Bob"), INITIAL_BALANCE, deck);
        gm.getPlayers().get(0).debit(98);

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

        assertEquals(2, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(1).getBalance());

        assertTrue(gm.getHistory().stream().noneMatch(r -> r.playerName().equals("Alice")));
    }

    @Test
    void testHistoryRecordedForNaturalBlackjack() {

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

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.EIGHT), c(Rank.SIX), c(Rank.EIGHT), c(Rank.TEN),
                c(Rank.FIVE), c(Rank.NINE),
                c(Rank.SEVEN)));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();
        gm.stand();
        gm.stand();
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

    @Test
    void testInsuranceFlowInMultiplayer() {

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

        assertEquals(GameState.PLAYER_TURN, gm.getState());

        assertEquals(INITIAL_BALANCE - BET - BET / 2, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE - BET,           gm.getPlayers().get(1).getBalance());
        assertEquals(INITIAL_BALANCE - BET - BET / 2, gm.getPlayers().get(2).getBalance());
    }

    @Test
    void testInsuranceWithSitOutSkipped() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.ACE),
                c(Rank.SIX),  c(Rank.SIX),  c(Rank.SEVEN)));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob", "Charlie"), INITIAL_BALANCE, deck);
        gm.getPlayers().get(1).debit(98);

        gm.startNewRound();
        assertTrue(gm.getPlayers().get(1).isSittingOut());

        gm.placeBet(0, BET);
        gm.placeBet(2, BET);
        gm.deal();
        assertEquals(GameState.INSURANCE_OFFER, gm.getState());

        assertThrows(IllegalStateException.class, () -> gm.takeInsurance(1));

        gm.declineInsurance(0);
        gm.declineInsurance(2);

        assertEquals(GameState.PLAYER_TURN, gm.getState());
        assertEquals(2, gm.getPlayers().get(1).getBalance());
    }

    @Test
    void testConsecutiveRoundsInMultiplayer() {

        StackedDeck deck = new StackedDeck(List.of(

                c(Rank.TEN), c(Rank.NINE), c(Rank.TEN),
                c(Rank.NINE), c(Rank.EIGHT), c(Rank.EIGHT),

                c(Rank.FIVE), c(Rank.FIVE), c(Rank.NINE),
                c(Rank.FIVE), c(Rank.FIVE), c(Rank.NINE)));
        GameManager gm = new GameManager(
                List.of("Alice", "Bob"), INITIAL_BALANCE, deck);

        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.placeBet(1, BET);
        gm.deal();
        gm.stand();
        gm.stand();
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(2, gm.getHistory().size());
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance());

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

        assertEquals(HandOutcome.LOSE, gm.getHistory().get(2).outcome());
        assertEquals(HandOutcome.LOSE, gm.getHistory().get(3).outcome());
    }

    @Test
    void testNextActivePlayerSkipsMiddleSitOut() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.TEN), c(Rank.NINE),
                c(Rank.NINE), c(Rank.NINE), c(Rank.NINE)
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

        assertEquals(2, gm.getPlayers().get(1).getBalance(), "Bob balance untouched");
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(0).getBalance());
        assertEquals(INITIAL_BALANCE + BET, gm.getPlayers().get(2).getBalance());
    }

    @Test
    void testSplitSecondHandAuto21() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.EIGHT),
                c(Rank.TEN), c(Rank.NINE),
                c(Rank.FIVE),
                c(Rank.ACE)));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        assertEquals(15, gm.getCurrentHand().getHand().getScore());
        gm.stand();

        assertEquals(GameState.DEALER_TURN, gm.getState(),
                "Hand2 at 21 from split-deal must auto-stand");
        gm.dealerPlay();

        assertEquals(INITIAL_BALANCE, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testSplitBothHandsAuto21() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.TEN), c(Rank.EIGHT),
                c(Rank.TEN), c(Rank.NINE),
                c(Rank.ACE),
                c(Rank.ACE)));
        GameManager gm = singlePlayer(deck);
        gm.startNewRound();
        gm.placeBet(0, BET);
        gm.deal();
        gm.split();

        assertEquals(GameState.ROUND_OVER, gm.getState());

        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());
    }

    @Test
    void testDoubleAcrossPlayersInMultiplayer() {

        StackedDeck deck = new StackedDeck(List.of(
                c(Rank.FIVE), c(Rank.TEN), c(Rank.FIVE),
                c(Rank.SIX),  c(Rank.NINE), c(Rank.SIX),
                c(Rank.TEN),
                c(Rank.NINE)
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

        assertEquals("Bob", gm.getCurrentPlayer().getName(),
                "Turn must advance to next player after double-down");
        gm.stand();
        gm.dealerPlay();
        assertEquals(GameState.ROUND_OVER, gm.getState());

        assertEquals(INITIAL_BALANCE + 2 * BET, gm.getPlayers().get(0).getBalance());

        assertEquals(INITIAL_BALANCE - BET, gm.getPlayers().get(1).getBalance());
    }
}
