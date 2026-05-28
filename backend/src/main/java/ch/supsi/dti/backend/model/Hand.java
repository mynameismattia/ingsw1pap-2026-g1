// Contenitore di carte con la regola d'oro del Black Jack — somma punti + gestione asso "soft" (vale 11 finché non busta, poi diminuisce a 1).
// Espone anche isBlackJack (21 con esattamente 2 carte) e isSoft (almeno un asso ancora come 11).
// Niente bet/owner: è la mano "carte-only", riusata sia dal dealer sia in ogni PlayerHand.

package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hand {

    private final List<Card> cards;

    public Hand() {
        this.cards = new ArrayList<Card>();
    }

    public void addCard(Card card) {
        cards.add(card);
    }

    public int getScore(){
        return evaluate().score();
    }

    public boolean isBusted(){
        return getScore() > 21;
    }

    public boolean isBlackJack(){
        return cards.size() == 2 && getScore() == 21;
    }

    public boolean isSoft(){
        return evaluate().softAces() > 0;
    }

    private Eval evaluate() {
        int score = 0;
        int aces = 0;
        for (Card card : cards) {
            score += card.getValue();
            if (card.getRank() == Rank.ACE) {
                aces++;
            }
        }
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return new Eval(score, aces);
    }

    private record Eval(int score, int softAces) {}

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public void clear(){
        cards.clear();
    }
}
