package ch.supsi.dti.frontend.view;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

public class CardView extends Label {
    private static final double CARD_WIDTH = 76;
    private static final double CARD_HEIGHT = 112;

    private static final String STYLE_FACE_UP =
            "-fx-background-color: white;" +
                    "-fx-border-color: black;" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 7;" +
                    "-fx-background-radius: 7;" +
                    "-fx-font-size: 22px;" +
                    "-fx-font-weight: bold;";

    private static final String STYLE_FACE_DOWN =
            "-fx-background-color: #1a3a8a;" +
                    "-fx-border-color: white;" +
                    "-fx-border-width: 2;" +
                    "-fx-border-radius: 7;" +
                    "-fx-background-radius: 7;";

    private Card card;

    public CardView(Card card) {
        setMinSize(CARD_WIDTH, CARD_HEIGHT);
        setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        setAlignment(Pos.CENTER);
        setCard(card);
    }

    public void setCard(Card card) {
        this.card = card;
        if (card == null) {
            setText("");
            setStyle(STYLE_FACE_DOWN);
        } else {
            setText(rankSymbol(card.getRank()) + suitSymbol(card.getSuit()));
            String color = isRed(card.getSuit()) ? "red" : "black";
            setStyle(STYLE_FACE_UP + "-fx-text-fill: " + color + ";");
        }
    }
    public Card getCard() {
        return card;
    }

    private static String rankSymbol(Rank rank) {
        return switch (rank) {
            case ACE   -> "A";
            case TWO   -> "2";
            case THREE -> "3";
            case FOUR  -> "4";
            case FIVE  -> "5";
            case SIX   -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE  -> "9";
            case TEN   -> "10";
            case JACK  -> "J";
            case QUEEN -> "Q";
            case KING  -> "K";
        };
    }

    private static String suitSymbol(Suit suit) {
        return switch (suit) {
            case HEARTS   -> "\u2665"; // ♥
            case DIAMONDS -> "\u2666"; // ♦
            case CLUBS    -> "\u2663"; // ♣
            case SPADES   -> "\u2660"; // ♠
        };
    }

    private static boolean isRed(Suit suit) {
        return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
    }
}