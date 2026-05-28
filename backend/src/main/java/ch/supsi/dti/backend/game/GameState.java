// Enum delle fasi di gioco: BETTING (raccolta puntate), INSURANCE_OFFER (proposta se dealer mostra Asso), PLAYER_TURN (i player giocano), DEALER_TURN (il banco gira la carta e pesca), ROUND_OVER (paga vincite/perdite), GAME_OVER (sessione finita).
// Il GameManager passa da una all'altra in sequenza ordinata.

package ch.supsi.dti.backend.game;

public enum GameState {
    WAITING,
    BETTING,
    DEALING,
    INSURANCE_OFFER,
    PLAYER_TURN,
    DEALER_TURN,
    RESOLVING,
    ROUND_OVER,
    GAME_OVER
}
