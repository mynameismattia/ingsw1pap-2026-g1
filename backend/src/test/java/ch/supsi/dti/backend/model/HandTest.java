// Test della Hand: score normale con e senza figure, asso soft (vale 11), asso che degrada a 1 quando la mano sballa, isBlackJack su 21+2carte, isBusted su >21.

package ch.supsi.dti.backend.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HandTest {

    private Hand hand;

    @BeforeEach
    void setUp() {
        hand = new Hand();
    }

    @Test
    void testScoreSimple() {
        hand.addCard(new Card(Suit.HEARTS, Rank.TWO));
        hand.addCard(new Card(Suit.CLUBS, Rank.THREE));
        assertEquals(5, hand.getScore());
    }

    @Test
    void testScoreFaceCards() {
        hand.addCard(new Card(Suit.HEARTS, Rank.KING));
        hand.addCard(new Card(Suit.CLUBS, Rank.QUEEN));
        assertEquals(20, hand.getScore());
    }

    @Test
    void testSingleAceHigh() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.SEVEN));
        assertEquals(18, hand.getScore());
    }

    @Test
    void testSingleAceLow() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.SEVEN));
        hand.addCard(new Card(Suit.SPADES, Rank.EIGHT));
        assertEquals(16, hand.getScore());
    }

    @Test
    void testDoubleAce() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.ACE));
        assertEquals(12, hand.getScore());
    }

    @Test
    void testTripleAce() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.ACE));
        hand.addCard(new Card(Suit.SPADES, Rank.ACE));
        assertEquals(13, hand.getScore());
    }

    @Test
    void testBlackjack() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.KING));
        assertTrue(hand.isBlackJack());
        assertEquals(21, hand.getScore());
    }

    @Test
    void testNotBlackjackThreeCards() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.FIVE));
        hand.addCard(new Card(Suit.SPADES, Rank.FIVE));
        assertEquals(21, hand.getScore());
        assertFalse(hand.isBlackJack());
    }

    @Test
    void testBusted() {
        hand.addCard(new Card(Suit.HEARTS, Rank.KING));
        hand.addCard(new Card(Suit.CLUBS, Rank.QUEEN));
        hand.addCard(new Card(Suit.SPADES, Rank.FIVE));
        assertTrue(hand.isBusted());
    }

    @Test
    void testNotBusted() {
        hand.addCard(new Card(Suit.HEARTS, Rank.KING));
        hand.addCard(new Card(Suit.CLUBS, Rank.QUEEN));
        assertFalse(hand.isBusted());
    }

    @Test
    void testSoft() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.SIX));
        assertTrue(hand.isSoft());
    }

    @Test
    void testNotSoftAceForced() {
        hand.addCard(new Card(Suit.HEARTS, Rank.ACE));
        hand.addCard(new Card(Suit.CLUBS, Rank.SIX));
        hand.addCard(new Card(Suit.SPADES, Rank.KING));
        assertFalse(hand.isSoft());
    }

    @Test
    void testNotSoftNoAce() {
        hand.addCard(new Card(Suit.HEARTS, Rank.TEN));
        hand.addCard(new Card(Suit.CLUBS, Rank.SEVEN));
        assertFalse(hand.isSoft());
    }

    @Test
    void testClear() {
        hand.addCard(new Card(Suit.HEARTS, Rank.KING));
        hand.addCard(new Card(Suit.CLUBS, Rank.QUEEN));
        hand.clear();
        assertEquals(0, hand.getScore());
        assertEquals(0, hand.getCards().size());
    }

    @Test
    void testGetCardsUnmodifiable() {
        hand.addCard(new Card(Suit.HEARTS, Rank.KING));
        assertThrows(UnsupportedOperationException.class, () -> {
            hand.getCards().add(new Card(Suit.CLUBS, Rank.ACE));
        });
    }
}
