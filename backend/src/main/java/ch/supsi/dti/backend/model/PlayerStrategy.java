package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;

/**
 * Decision contract for non-human players. No implementation is shipped yet —
 * this interface exists so the future bot AI (and the persistence layer that
 * needs to round-trip player types) has a stable target to plug into.
 */
public interface PlayerStrategy {

    enum Action {
        HIT, STAND, DOUBLE, SPLIT, TAKE_INSURANCE, DECLINE_INSURANCE
    }

    Action decide(GameState state, PlayerHand currentHand, Card dealerUpcard);
}
