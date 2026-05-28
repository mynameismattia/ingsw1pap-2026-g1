// Interfaccia per la "AI" decisionale dei bot. Definisce l'enum Action (HIT/STAND/DOUBLE/SPLIT/TAKE_INSURANCE/DECLINE_INSURANCE) e decide(state, hand, dealerUpcard).
// Ha anche un decideBet di default che punta il 10% del bankroll arrotondato a 5. Solo i bot la implementano.

package ch.supsi.dti.backend.model;

import ch.supsi.dti.backend.game.GameState;

public interface PlayerStrategy {

    int MIN_BET = GameRules.MIN_BET;

    enum Action {
        HIT, STAND, DOUBLE, SPLIT, TAKE_INSURANCE, DECLINE_INSURANCE
    }

    Action decide(GameState state, PlayerHand currentHand, Card dealerUpcard);

    default int decideBet(Player self) {
        // 1. Punto base = 10% del saldo, arrotondato al multiplo di 5 più vicino.
        int rounded = Math.round(self.getBalance() * 0.1f / 5f) * 5;
        // 2. Clamp tra MIN_BET (5) e il saldo totale, così un bot povero punta almeno il minimo e uno ricco non sfora.
        return Math.max(MIN_BET, Math.min(rounded, self.getBalance()));
    }
}
