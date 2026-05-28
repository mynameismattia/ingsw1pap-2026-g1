// Una mano *posseduta* da un Player con denaro associato — wrap di Hand + bet, insurance bet, outcome.
// Gestisce le mutazioni economiche di quella mano: placeBet, doubleBet, win(multiplier), push, insurance.
// Un Player ne ha 1 normalmente, 2 dopo lo split.

package ch.supsi.dti.backend.model;

public class PlayerHand {

    private final Player owner;
    private final Hand hand;
    private int bet;
    private int insuranceBet;
    private boolean settled;
    private HandOutcome outcome;

    public PlayerHand(Player owner) {
        this.owner = owner;
        this.hand = new Hand();
        this.bet = 0;
        this.insuranceBet = 0;
        this.settled = false;
        this.outcome = null;
    }

    public void placeBet(int amount) {
        // 1. Valida: importo positivo e non superiore al saldo disponibile.
        if (amount <= 0 || amount > owner.getBalance()) {
            throw new IllegalArgumentException("Bet not valid");
        }
        // 2. Registra la puntata e scala l'importo dal saldo del Player.
        bet = amount;
        owner.debit(amount);
    }

    public void doubleBet() {
        // 1. Per raddoppiare devo poter aggiungere un secondo bet uguale a quello già piazzato.
        if (bet <= 0 || bet > owner.getBalance()) {
            throw new IllegalArgumentException("Insufficient balance to double");
        }
        // 2. Scalo altri bet dal saldo, poi raddoppio l'importo della mano.
        owner.debit(bet);
        bet *= 2;
    }

    public void placeInsuranceBet(int amount) {
        // 1. Validazione identica alla puntata principale ma su una colonna separata (insuranceBet).
        if (amount <= 0 || amount > owner.getBalance()) {
            throw new IllegalArgumentException("Insurance bet not valid");
        }
        // 2. Registra e scala dal saldo; il payout sull'insurance lo fa winInsurance(multiplier).
        insuranceBet = amount;
        owner.debit(amount);
    }

    public void winInsurance(double multiplier) {
        owner.credit(insuranceBet + (int)(insuranceBet * multiplier));
    }

    public void win(double multiplier) {
        owner.credit(bet + (int)(bet * multiplier));
    }

    public void push() {
        owner.credit(bet);
    }

    public Hand getHand() {
        return hand;
    }

    public int getBet() {
        return bet;
    }

    public int getInsuranceBet() {
        return insuranceBet;
    }

    public boolean isSettled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public HandOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(HandOutcome outcome) {
        this.outcome = outcome;
    }
}
