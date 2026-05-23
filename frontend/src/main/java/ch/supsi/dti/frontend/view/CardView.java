package ch.supsi.dti.frontend.view;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Uses the bundled artwork as the card surface:
 * <ul>
 *   <li>face → {@code /ui/front_blank_card.png} (cream cardstock with navy filigree border)</li>
 *   <li>back → {@code /ui/cards/back_card.png} (navy lattice + central compass ornament)</li>
 * </ul>
 *
 * <p>On the face, the controller overlays rank + suit text in the top-left and
 * the bottom-right (rotated 180° — real playing-card anatomy) plus a large suit
 * symbol in the centre, all positioned inside the printed filigree frame.
 * Suit-coloured: deep red for ♥/♦, dark navy for ♠/♣ — matches the border ink.</p>
 *
 * <p>All visual styling lives in {@code menu.css} so future tweaks are a
 * single-file edit; menu.css is the universally-loaded stylesheet across every
 * scene that displays a card.</p>
 */
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
        this.card = card;
        getChildren().clear();
        getStyleClass().removeAll("card-face", "card-back",
                "card-suit-red", "card-suit-black");
        if (card == null) {
            // Back is a single self-contained PNG — no overlay needed.
            getStyleClass().add("card-back");
        } else {
            getStyleClass().addAll("card-face",
                    isRed(card.getSuit()) ? "card-suit-red" : "card-suit-black");
            renderFaceOverlay(card);
        }
    }

    public Card getCard() {
        return card;
    }

    // ── Face overlay ────────────────────────────────────────────────

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

    // ── Glyph maps ──────────────────────────────────────────────────

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
