package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private final List<Card> cards;
    private static final int NUM_DECKS = 6;
    private static final double RESHUFFLE_THRESHOLD = 0.75;  // 75%

    public Deck() {
        this.cards = new ArrayList<>();

        for (int i = 0; i < NUM_DECKS; i++) {

            for (Suit suit : Suit.values()) {

                for (Rank rank : Rank.values()) {
                    cards.add(new Card(suit, rank));
                }

            }

        }
        shuffle();

    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    //btw perché l'ultima e non la prima?
    // perché rimuovere l'ultimo da un ArrayList è O(1), rimuovere il primo è O(n)
    // algo insegna bene
    public Card draw() {
        return cards.removeLast();
    }

    public boolean needsReshuffle() {
        return cards.size() <= (1 - RESHUFFLE_THRESHOLD) * NUM_DECKS * 52;
        // cioè: cards.size() <= 78
    }

    public void reset() {
        cards.clear();

        for (int i = 0; i < NUM_DECKS; i++) {

            for (Suit suit : Suit.values()) {

                for (Rank rank : Rank.values()) {
                    cards.add(new Card(suit, rank));
                }

            }

        }
        shuffle();
    }

    public int remainingCards() {
        return cards.size();
    }
}