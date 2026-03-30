package ch.supsi.dti.backend.model;

public class Dealer {

    private Hand hand;
    private boolean handRevealed;

    public Dealer() {
        this.hand = new Hand();
        this.handRevealed = false;
    }

    boolean shouldHit(){
        if ( hand.getScore() < 17 || (hand.getScore() == 17 && hand.isSoft())) {
            return true;
        }
        return false;
    }

    Card getVisibleCard(){
        return hand.getCards().getFirst();
    }

    public Hand getHand() {
        return hand;
    }

    public boolean isHandRevealed() {
        return handRevealed;
    }

    public void setHandRevealed(boolean handRevealed) {
        this.handRevealed = handRevealed;
    }
}