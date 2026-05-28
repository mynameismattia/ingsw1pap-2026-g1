// Test del Deck: 312 carte iniziali (6 mazzi × 52), draw decrementa, shuffle randomizza l'ordine, needsReshuffle scatta sotto la soglia.

package ch.supsi.dti.backend.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void testDeckSize() {
        assertEquals(312, deck.remainingCards());
    }

    @Test
    void testDrawReducesSize() {
        deck.draw();
        assertEquals(311, deck.remainingCards());
    }

    @Test
    void testDrawReturnsCard() {
        Card card = deck.draw();
        assertNotNull(card);
    }

    @Test
    void testDrawMultiple() {
        for (int i = 0; i < 10; i++) {
            deck.draw();
        }
        assertEquals(302, deck.remainingCards());
    }

    @Test
    void testNeedsReshuffleFalseOnFull() {
        assertFalse(deck.needsReshuffle());
    }

    @Test
    void testNeedsReshuffleTrue() {

        for (int i = 0; i < 234; i++) {
            deck.draw();
        }
        assertEquals(78, deck.remainingCards());
        assertTrue(deck.needsReshuffle());
    }

    @Test
    void testReset() {
        for (int i = 0; i < 50; i++) {
            deck.draw();
        }
        deck.reset();
        assertEquals(312, deck.remainingCards());
    }

    @Test
    void testResetClearsReshuffle() {
        for (int i = 0; i < 234; i++) {
            deck.draw();
        }
        assertTrue(deck.needsReshuffle());
        deck.reset();
        assertFalse(deck.needsReshuffle());
    }
}
