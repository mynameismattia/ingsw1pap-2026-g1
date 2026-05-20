package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;

/**
 * Mirrors the house: stand on any 17 (S17 — bot does not chase soft 17s like
 * the dealer does), hit otherwise. Never doubles, never splits, never insures.
 */
public final class DealerMimicStrategy implements PlayerStrategy {

    @Override
    public Action decide(GameState state, PlayerHand currentHand, Card dealerUpcard) {
        if (state == GameState.INSURANCE_OFFER) {
            return Action.DECLINE_INSURANCE;
        }
        return currentHand.getHand().getScore() < 17 ? Action.HIT : Action.STAND;
    }
}
