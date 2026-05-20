package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private final String name;
    private final boolean bot;
    private final PlayerStrategy strategy;
    private int balance;
    private boolean sittingOut;
    private final List<PlayerHand> hands;

    public Player(String name, int balance) {
        this(name, balance, false, null);
    }

    public Player(String name, int balance, boolean bot) {
        this(name, balance, bot, bot ? new DealerMimicStrategy() : null);
    }

    public Player(String name, int balance, boolean bot, PlayerStrategy strategy) {
        this.name = name;
        this.balance = balance;
        this.bot = bot;
        this.strategy = strategy;
        this.sittingOut = false;
        this.hands = new ArrayList<>();
        this.hands.add(new PlayerHand(this));
    }

    public PlayerStrategy getStrategy() {
        return strategy;
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

    public boolean isBot() {
        return bot;
    }

    public boolean isSittingOut() {
        return sittingOut;
    }

    public void setSittingOut(boolean sittingOut) {
        this.sittingOut = sittingOut;
    }
}
