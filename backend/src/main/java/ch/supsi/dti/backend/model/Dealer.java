// Il banco. Una Hand + flag handRevealed (la seconda carta è scoperta?).
// Implementa la regola S17: il banco continua a pescare sotto 17, e sul soft-17 — tipico delle case "soft-17 hits".
// Espone getVisibleCard() (la upcard per le decisioni dei player) e showsAce() (trigger dell'insurance offer).

package ch.supsi.dti.backend.model;

public class Dealer {

    private Hand hand;
    private boolean handRevealed;

    public Dealer() {
        this.hand = new Hand();
        this.handRevealed = false;
    }

    public boolean shouldHit(){
        return GameRules.dealerShouldHit(hand.getScore(), hand.isSoft());
    }

    public Card getVisibleCard(){
        return hand.getCards().getFirst();
    }

    public boolean showsAce(){
        return !hand.getCards().isEmpty() && getVisibleCard().getRank() == Rank.ACE;
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
