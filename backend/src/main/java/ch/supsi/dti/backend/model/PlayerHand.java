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
        if (amount <= 0 || amount > owner.getBalance()) {
            throw new IllegalArgumentException("Bet not valid");
        }
        bet = amount;
        owner.debit(amount);
    }

    public void doubleBet() {
        if (bet <= 0 || bet > owner.getBalance()) {
            throw new IllegalArgumentException("Insufficient balance to double");
        }
        owner.debit(bet);
        bet *= 2;
    }

    public void placeInsuranceBet(int amount) {
        if (amount <= 0 || amount > owner.getBalance()) {
            throw new IllegalArgumentException("Insurance bet not valid");
        }
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
