package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DealerMimicStrategyTest {

    private DealerMimicStrategy strategy;
    private Player owner;
    private Card dealerUpcard;

    @BeforeEach
    void setUp() {
        strategy = new DealerMimicStrategy();
        owner = new Player("Bot", 100, true);
        dealerUpcard = new Card(Suit.SPADES, Rank.SEVEN);
    }

    private PlayerHand handWith(Card... cards) {
        PlayerHand ph = new PlayerHand(owner);
        for (Card c : cards) {
            ph.getHand().addCard(c);
        }
        return ph;
    }


    // --- decide() during PLAYER_TURN ---

    @Test
    void hitsOnHardSixteen() {
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.TEN),
                new Card(Suit.CLUBS, Rank.SIX));
        assertEquals(PlayerStrategy.Action.HIT,
                strategy.decide(GameState.PLAYER_TURN, h, dealerUpcard));
    }

    @Test
    void standsOnHardSeventeen() {
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.TEN),
                new Card(Suit.CLUBS, Rank.SEVEN));
        assertEquals(PlayerStrategy.Action.STAND,
                strategy.decide(GameState.PLAYER_TURN, h, dealerUpcard));
    }

    @Test
    void standsOnSoftSeventeen() {
        // S17 rule: bot stands on Ace + 6 even though the dealer would hit it.
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.CLUBS, Rank.SIX));
        assertTrue(h.getHand().isSoft());
        assertEquals(17, h.getHand().getScore());
        assertEquals(PlayerStrategy.Action.STAND,
                strategy.decide(GameState.PLAYER_TURN, h, dealerUpcard));
    }

    @Test
    void standsOnTwentyOne() {
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.ACE),
                new Card(Suit.CLUBS, Rank.KING));
        assertEquals(PlayerStrategy.Action.STAND,
                strategy.decide(GameState.PLAYER_TURN, h, dealerUpcard));
    }

    @Test
    void neverReturnsDoubleOrSplitOnPair() {
        // Pair of 8s — basic strategy would split; dealer-mimic must not.
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.EIGHT),
                new Card(Suit.CLUBS, Rank.EIGHT));
        PlayerStrategy.Action action = strategy.decide(GameState.PLAYER_TURN, h, dealerUpcard);
        assertNotEquals(PlayerStrategy.Action.SPLIT, action);
        assertNotEquals(PlayerStrategy.Action.DOUBLE, action);
    }


    // --- decide() during INSURANCE_OFFER ---

    @Test
    void alwaysDeclinesInsurance() {
        PlayerHand h = handWith(
                new Card(Suit.HEARTS, Rank.TEN),
                new Card(Suit.CLUBS, Rank.NINE));
        Card ace = new Card(Suit.SPADES, Rank.ACE);
        assertEquals(PlayerStrategy.Action.DECLINE_INSURANCE,
                strategy.decide(GameState.INSURANCE_OFFER, h, ace));
    }


    // --- decideBet() rounding ---

    @Test
    void decideBetRoundsToNearestFive() {
        // 10% of balance, rounded to the nearest multiple of 5.
        assertEquals(10, strategy.decideBet(new Player("B", 111)));  // 11.1 -> 10
        assertEquals(10, strategy.decideBet(new Player("B", 117)));  // 11.7 -> 10
        assertEquals(15, strategy.decideBet(new Player("B", 130)));  // 13.0 -> 15
        assertEquals(100, strategy.decideBet(new Player("B", 1000))); // 100   -> 100
    }

    @Test
    void decideBetClampsToMinBet() {
        // Below the MIN_BET floor (5) the rule would round to 0; we clamp up.
        assertEquals(PlayerStrategy.MIN_BET, strategy.decideBet(new Player("B", 24)));
        assertEquals(PlayerStrategy.MIN_BET, strategy.decideBet(new Player("B", 10)));
        assertEquals(PlayerStrategy.MIN_BET, strategy.decideBet(new Player("B", 5)));
    }
}
