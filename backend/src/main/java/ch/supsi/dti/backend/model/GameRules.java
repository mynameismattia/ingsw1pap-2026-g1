// Regole e costanti centrali del Black Jack, in un unico posto.
// Limiti di puntata, saldo iniziale, payout (moltiplicatore blackjack) e regola di pesca del banco (S17).
// Evita che le stesse regole vivano duplicate fra backend e frontend, divergendo nel tempo.

package ch.supsi.dti.backend.model;

public final class GameRules {

    private GameRules() {}

    public static final int MIN_BET = 5;
    public static final int MAX_BET = 1000;
    public static final int DEFAULT_BALANCE = 100;

    public static final double BLACKJACK_PAYOUT = 1.5;

    public static int net(int bet, HandOutcome outcome) {
        return switch (outcome) {
            case WIN       -> bet;
            case BLACKJACK -> (int) (bet * BLACKJACK_PAYOUT);
            case LOSE      -> -bet;
            case PUSH      -> 0;
        };
    }

    public static boolean dealerShouldHit(int score, boolean soft) {
        return score < 17 || (score == 17 && soft);
    }
}
