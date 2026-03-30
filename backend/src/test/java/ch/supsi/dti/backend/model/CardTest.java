package ch.supsi.dti.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CardTest {

    @Test
    void testAceValue() {
        Card card = new Card(Suit.HEARTS, Rank.ACE);
        assertEquals(11, card.getValue());
    }

    @Test
    void testFaceCardsValue() {
        assertEquals(10, new Card(Suit.HEARTS, Rank.KING).getValue());
        assertEquals(10, new Card(Suit.HEARTS, Rank.QUEEN).getValue());
        assertEquals(10, new Card(Suit.HEARTS, Rank.JACK).getValue());
    }

    @Test
    void testNumberCardValue() {
        assertEquals(5, new Card(Suit.CLUBS, Rank.FIVE).getValue());
        assertEquals(2, new Card(Suit.CLUBS, Rank.TWO).getValue());
        assertEquals(9, new Card(Suit.CLUBS, Rank.NINE).getValue());
    }

    @Test
    void testAllSuitsExist() {
        assertEquals(4, Suit.values().length);
    }

    @Test
    void testAllRanksExist() {
        assertEquals(13, Rank.values().length);
    }

    @Test
    void testGetters() {
        Card card = new Card(Suit.SPADES, Rank.TEN);
        assertEquals(Suit.SPADES, card.getSuit());
        assertEquals(Rank.TEN, card.getRank());
    }
}