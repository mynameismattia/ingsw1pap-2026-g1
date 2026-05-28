// Sei mazzi mescolati assieme (312 carte), come nei casinò veri.
// Espone draw() (pesca dall'ultima carta, O(1)), needsReshuffle() (sotto il 25% rimanente → reset+shuffle), e reset() per ricostruire da zero.
// Costanti: NUM_DECKS=6, RESHUFFLE_THRESHOLD=0.75.

package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private final List<Card> cards;
    private static final int NUM_DECKS = 6;
    private static final double RESHUFFLE_THRESHOLD = 0.75;

    public Deck() {
        this.cards = new ArrayList<>();
        reset();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        return cards.removeLast();
    }

    public boolean needsReshuffle() {
        // Restituisce true quando è rimasto meno del 25% del mazzo (RESHUFFLE_THRESHOLD = 0.75 = quota consumata prima di rimescolare).
        return cards.size() <= (1 - RESHUFFLE_THRESHOLD) * NUM_DECKS * 52;
    }

    public void reset() {
        // 1. Svuota completamente il mazzo corrente.
        cards.clear();

        // 2. Ricostruisce 6 mazzi standard (52 carte ciascuno = 312 totali) usando tutti i 4 semi × 13 ranghi.
        for (int i = 0; i < NUM_DECKS; i++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(suit, rank));
                }
            }
        }

        // 3. Mescola tutto insieme prima di restituirlo in uso.
        shuffle();
    }

    public int remainingCards() {
        return cards.size();
    }
}
