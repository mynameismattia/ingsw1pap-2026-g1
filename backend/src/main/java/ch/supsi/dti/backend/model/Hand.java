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
        // 1. Sommo tutti i valori "nominali" delle carte (l'asso vale 11 di default in Rank).
        int score = 0;
        int aces = 0;
        for (Card card : cards) {
            score += card.getValue();
            if(card.getRank() == Rank.ACE){
                aces++;
            }
        }

        // 2. Finché sballo (>21) e ho ancora assi "soft" (contati come 11), degrado un asso a 1 togliendo 10.
        while (score > 21 && aces > 0){
            score -= 10;
            aces--;
        }
        return score;
    }

    public boolean isBusted(){
        return getScore() > 21;
    }

    public boolean isBlackJack(){
        return cards.size() == 2 && getScore() == 21;
    }

    public boolean isSoft(){
        // 1. Stessa contabilità di getScore: somma valori e conta assi.
        int score = 0;
        int aces = 0;
        for (Card card : cards) {
            score += card.getValue();
            if(card.getRank() == Rank.ACE){
                aces++;
            }
        }

        // 2. Degrado gli assi solo se sballo; gli assi che restano "11" sono i soft.
        while (score > 21 && aces > 0){
            score -= 10;
            aces--;
        }

        // 3. È soft se almeno un asso è ancora contato come 11.
        return aces > 0;
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public void clear(){
        cards.clear();
    }
}
