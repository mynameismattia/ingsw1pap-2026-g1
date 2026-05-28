// Helper statico per cambiare scena dell'app.
// Un solo metodo navigate(stage, fxml): carica il file FXML, applica il MessageService bundle per le %label, e sostituisce il root della Scene. Tutti i controller lo usano.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class Navigation {

    private Navigation() {}

    public static void navigate(Stage stage, String fxml) {
        try {
            // 1. Carica l'FXML e attacca il ResourceBundle delle traduzioni (le %label vengono risolte qui).
            FXMLLoader loader = new FXMLLoader(Navigation.class.getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            Parent newRoot = loader.load();

            // 2. Se è la prima scena della finestra ne crea una nuova; altrimenti scambia solo il root
            //    (mantiene dimensioni/fullscreen impostati prima).
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(newRoot);
                stage.setScene(scene);
            } else {
                scene.setRoot(newRoot);
            }

            // 3. Aggancia gli effetti click globali, aggiorna il titolo della finestra e fa partire la musica giusta.
            SoundManager.attachClickSfx(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            SoundManager.getInstance().playMusic(musicFor(fxml));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SoundManager.MusicTrack musicFor(String fxml) {
        return switch (fxml) {
            case "/ui/game.fxml" -> SoundManager.MusicTrack.GAME;
            case "/ui/roundresult.fxml", "/ui/license.fxml" -> null;
            default -> SoundManager.MusicTrack.MENU;
        };
    }
}
