// Piccoli costruttori di nodi UI riusati da più controller (avatar dei giocatori, formato dei delta di saldo).
// Centralizzati qui per evitare il copia-incolla che era sparso fra GameController e RoundResultController.

package ch.supsi.dti.frontend.view;

import ch.supsi.dti.backend.model.Player;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public final class UiFactory {

    private UiFactory() {}

    public static StackPane avatar(Player player, int seatIndex, String baseClass) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add(baseClass);
        if (player.isBot()) {
            avatar.getStyleClass().add("seat-avatar-cpu");
        } else if (seatIndex > 0) {
            avatar.getStyleClass().add("seat-avatar-p" + (seatIndex + 1));
        }
        String text = player.getName().isEmpty()
                ? "?"
                : player.getName().substring(0, 1).toUpperCase();
        Label initial = new Label(text);
        initial.getStyleClass().add(baseClass + "-initial");
        avatar.getChildren().add(initial);
        return avatar;
    }

    public static String formatDelta(int delta) {
        if (delta > 0) return "+$" + delta;
        if (delta < 0) return "-$" + Math.abs(delta);
        return "$0";
    }
}
