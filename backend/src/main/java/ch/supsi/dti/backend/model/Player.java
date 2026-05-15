package ch.supsi.dti.backend.model;

public class Player {
    private final String name;
    private int balance;
    private final Hand hand;
    private int currentBet;
    private int insuranceBet;

    public Player(String name, int balance) {
        this.name = name;
        this.balance = balance;
        this.hand = new Hand();
        this.currentBet = 0;
        this.insuranceBet = 0;
    }


    public void placeBet(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("Bet not valid");
        }
        currentBet = amount;
        balance -= amount;
    }

    public void doubleBet() {
        if (currentBet <= 0 || currentBet > balance) {
            throw new IllegalArgumentException("Insufficient balance to double");
        }
        balance -= currentBet;
        currentBet *= 2;
    }

    public void placeInsuranceBet(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("Insurance bet not valid");
        }
        insuranceBet = amount;
        balance -= amount;
    }

    public void winInsurance(double multiplier) {
        balance += insuranceBet + (int)(insuranceBet * multiplier);
    }

    public void win(double multiplier) {
        balance += currentBet + (int)(currentBet * multiplier);
    }

    public void push(){
        balance += currentBet;
    }

    public void resetBet(){
        currentBet = 0;
        insuranceBet = 0;
    }

    public String getName() {
        return name;
    }

    public int getBalance() {
        return balance;
    }

    public Hand getHand() {
        return hand;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getInsuranceBet() {
        return insuranceBet;
    }
}