// Una carta da gioco = Suit + Rank. Oggetto immutabile, base di tutto il dominio.
// Espone getValue() che delega al Rank — pura aggregazione, niente logica di gioco.

package ch.supsi.dti.backend.model;

public class Card{
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public int getValue(){
        return rank.getValue();
    }

    @Override
    public String toString() {
        return "Card{" +
                "suit=" + suit +
                ", rank=" + rank +
                '}';
    }

}
