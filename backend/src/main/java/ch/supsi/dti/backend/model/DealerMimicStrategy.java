// L'unica strategia bot del progetto: copia la regola del dealer (Hit se score < 17, altrimenti Stand; rifiuta sempre l'assicurazione).
// Semplice ma sufficiente perché i bot abbiano un comportamento coerente e prevedibile.

package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;

public final class DealerMimicStrategy implements PlayerStrategy {

    @Override
    public Action decide(GameState state, PlayerHand currentHand, Card dealerUpcard) {
        // 1. Fase di insurance: il bot non scommette mai sull'asso del dealer.
        if (state == GameState.INSURANCE_OFFER) {
            return Action.DECLINE_INSURANCE;
        }
        // 2. Altrimenti mimica il dealer: HIT sotto 17, STAND dal 17 in su.
        return currentHand.getHand().getScore() < 17 ? Action.HIT : Action.STAND;
    }
}
