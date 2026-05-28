// Schermata Tutorial.
// Spiega le regole base del Black Jack con esempi visivi di carte (CardView statiche con piccole animazioni di entry per attirare l'attenzione).

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.model.Card;
import ch.supsi.dti.backend.model.Rank;
import ch.supsi.dti.backend.model.Suit;
import ch.supsi.dti.frontend.view.CardView;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

public class TutorialController {

    @FXML private Button startBtn;
    @FXML private Button settingsBtn;

    @FXML private HBox heroCards;
    @FXML private HBox valuesRow;
    @FXML private HBox exampleHit;
    @FXML private HBox exampleStand;
    @FXML private HBox exampleDouble;
    @FXML private HBox exampleSplit;
    @FXML private HBox exampleInsurance;

    @FXML
    private void initialize() {
        renderHeroCards();
        renderCardValues();
        renderActionExamples();
    }

    private void renderHeroCards() {
        // 1. Tre carte "vetrina" in alto: Asso ♠, Re ♥, Donna ♦.
        CardView c1 = new CardView(new Card(Suit.SPADES, Rank.ACE));
        CardView c2 = new CardView(new Card(Suit.HEARTS, Rank.KING));
        CardView c3 = new CardView(new Card(Suit.DIAMONDS, Rank.QUEEN));
        // 2. Le due laterali sono leggermente ruotate (-10° e +10°) per dare un effetto "ventaglio" visivo.
        c1.getTransforms().add(new Rotate(-10));
        c3.getTransforms().add(new Rotate(10));
        heroCards.getChildren().addAll(c1, c2, c3);
    }

    private void renderCardValues() {
        valuesRow.getChildren().addAll(
                cardWithCaption(new Card(Suit.HEARTS, Rank.TWO), "2"),
                cardWithCaption(new Card(Suit.DIAMONDS, Rank.SEVEN), "7"),
                cardWithCaption(new Card(Suit.CLUBS, Rank.TEN), "10"),
                cardWithCaption(new Card(Suit.SPADES, Rank.KING), "10"),
                cardWithCaption(new Card(Suit.DIAMONDS, Rank.ACE), "1 / 11")
        );
    }

    private void renderActionExamples() {

        exampleHit.getChildren().addAll(
                new CardView(new Card(Suit.CLUBS, Rank.FIVE)),
                new CardView(new Card(Suit.HEARTS, Rank.SEVEN))
        );

        exampleStand.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.TEN)),
                new CardView(new Card(Suit.HEARTS, Rank.EIGHT))
        );

        exampleDouble.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.FIVE)),
                new CardView(new Card(Suit.HEARTS, Rank.SIX))
        );

        exampleSplit.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.EIGHT)),
                new CardView(new Card(Suit.DIAMONDS, Rank.EIGHT))
        );

        exampleInsurance.getChildren().addAll(
                new CardView(new Card(Suit.SPADES, Rank.ACE)),
                new CardView(null)
        );
    }

    private static VBox cardWithCaption(Card card, String caption) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.getChildren().add(new CardView(card));
        Label label = new Label(caption);
        label.getStyleClass().add("tutorial-value-caption");
        box.getChildren().add(label);
        return box;
    }

    @FXML
    private void onBack() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/menu.fxml");
    }

    @FXML
    private void onStartGame() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/menu.fxml");
    }

    @FXML
    private void onSettings() {
        Stage stage = (Stage) startBtn.getScene().getWindow();
        SettingsDialog.show(stage,
                () -> Navigation.navigate(stage, "/ui/tutorial.fxml"));
    }
}
