// Componente JavaFX custom (estende StackPane) per disegnare una carta a video.
// Misura fissa 92×130 px, gestisce sia il fronte (rango + seme colorato in rosso/nero) che il retro. Si usa nelle mani di player e dealer.

package ch.supsi.dti.frontend.view;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CardView extends StackPane {

    private static final double CARD_WIDTH = 92;
    private static final double CARD_HEIGHT = 130;

    private Card card;

    public CardView(Card card) {
        setMinSize(CARD_WIDTH, CARD_HEIGHT);
        setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        getStyleClass().add("card-view");
        setCard(card);
    }

    public void setCard(Card card) {
        // 1. Salvo la nuova carta e pulisco lo stato visivo precedente (nodi figli + classi CSS).
        this.card = card;
        getChildren().clear();
        getStyleClass().removeAll("card-face", "card-back",
                "card-suit-red", "card-suit-black");

        // 2. Card == null → retro carta (la classe .card-back disegna il decoro via CSS).
        if (card == null) {
            getStyleClass().add("card-back");
        } else {
            // 3. Altrimenti front: applico card-face + variante colore (rosso per cuori/quadri, nero per fiori/picche),
            //    poi ci aggiungo sopra rango + simbolo del seme.
            getStyleClass().addAll("card-face",
                    isRed(card.getSuit()) ? "card-suit-red" : "card-suit-black");
            renderFaceOverlay(card);
        }
    }

    public Card getCard() {
        return card;
    }

    private void renderFaceOverlay(Card c) {
        Label rankLbl = new Label(rankSymbol(c.getRank()));
        rankLbl.getStyleClass().add("card-rank");

        Label suitLbl = new Label(suitSymbol(c.getSuit()));
        suitLbl.getStyleClass().add("card-suit");

        VBox content = new VBox(2, rankLbl, suitLbl);
        content.setAlignment(Pos.CENTER);
        StackPane.setAlignment(content, Pos.CENTER);
        getChildren().add(content);
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
            case HEARTS   -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS    -> "♣";
            case SPADES   -> "♠";
        };
    }

    private static boolean isRed(Suit suit) {
        return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
    }
}
