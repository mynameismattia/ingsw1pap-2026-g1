// I 13 valori delle carte (Asso → Re) con il loro punteggio in Black Jack.
// Figure = 10, Asso = 11 (poi la Hand lo degrada a 1 quando la mano sfora). Senza questo enum non saprebbe quanto somma una mano.

package ch.supsi.dti.backend.model;

public enum Rank {
    ACE(11),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(10),
    QUEEN(10),
    KING(10);

    private final int value;

    Rank(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
