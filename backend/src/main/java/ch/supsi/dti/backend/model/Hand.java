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
        int score = 0;
        int aces = 0;
        for (Card card : cards) {
            score += card.getValue();
            if(card.getRank() == Rank.ACE){
                aces++;
            }
        }
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
        if(cards.size() == 2 && getScore() == 21){
            return true;
        }
        return false;
    }

    public boolean isSoft(){
        int score = 0;
        int aces = 0;
        for (Card card : cards) {
            score += card.getValue();
            if(card.getRank() == Rank.ACE){
                aces++;
            }
        }
        while (score > 21 && aces > 0){
            score -= 10;
            aces--;
        }
        return aces > 0;
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards); //piu' sicuro non si sa mai
    }

    public void clear(){
        cards.clear();
    }
}