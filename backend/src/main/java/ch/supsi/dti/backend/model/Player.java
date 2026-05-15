package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private final String name;
    private int balance;
    private final List<PlayerHand> hands;

    public Player(String name, int balance) {
        this.name = name;
        this.balance = balance;
        this.hands = new ArrayList<>();
        this.hands.add(new PlayerHand(this));
    }

    public void resetForNewRound() {
        hands.clear();
        hands.add(new PlayerHand(this));
    }

    public PlayerHand insertHandAfter(int index) {
        PlayerHand newHand = new PlayerHand(this);
        hands.add(index + 1, newHand);
        return newHand;
    }

    public void debit(int amount) {
        balance -= amount;
    }

    public void credit(int amount) {
        balance += amount;
    }

    public String getName() {
        return name;
    }

    public int getBalance() {
        return balance;
    }

    public List<PlayerHand> getHands() {
        return Collections.unmodifiableList(hands);
    }
}
