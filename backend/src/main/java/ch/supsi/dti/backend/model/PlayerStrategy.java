package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;

/**
 * Decision contract for non-human players. Strategies are stateless: the
 * {@link GameManager} calls {@link #decide} during PLAYER_TURN / INSURANCE_OFFER
 * and {@link #decideBet} when the round opens.
 */
public interface PlayerStrategy {

    int MIN_BET = 5;

    enum Action {
        HIT, STAND, DOUBLE, SPLIT, TAKE_INSURANCE, DECLINE_INSURANCE
    }

    Action decide(GameState state, PlayerHand currentHand, Card dealerUpcard);

    /**
     * Default bet rule: 10% of the bot's balance, rounded to the nearest
     * multiple of 5, clamped to [MIN_BET, balance]. Callers must guarantee
     * the bot has at least {@code MIN_BET} (sitting-out is handled upstream).
     */
    default int decideBet(Player self) {
        int rounded = Math.round(self.getBalance() * 0.1f / 5f) * 5;
        return Math.max(MIN_BET, Math.min(rounded, self.getBalance()));
    }
}
