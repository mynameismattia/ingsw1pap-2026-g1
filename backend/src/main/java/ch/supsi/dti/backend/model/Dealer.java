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
        // Regola S17 (Soft-17 Hits): il banco pesca sotto 17, e anche sul 17 quando è soft (asso ancora a 11).
        // Sopra 17 hard sta sempre. È la variante più comune nelle case da gioco.
        if ( hand.getScore() < 17 || (hand.getScore() == 17 && hand.isSoft())) {
            return true;
        }
        return false;
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
