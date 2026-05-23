package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Scene navigation that swaps only the root node of the existing scene.
 *
 * Calling {@code stage.setScene(new Scene(...))} would briefly snap the stage
 * to the new scene's {@code prefSize} before any re-apply of fullscreen /
 * maximize could correct it, producing a visible flicker (windowed → fullscreen
 * jump on every screen change). Swapping the root via {@link Scene#setRoot}
 * keeps the same {@code Scene} object alive — so stage geometry, fullscreen
 * state and the SoundManager click filter all stay in place untouched.
 */
public final class Navigation {

    private Navigation() {}

    public static void navigate(Stage stage, String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(Navigation.class.getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            Parent newRoot = loader.load();

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(newRoot);
                stage.setScene(scene);
            } else {
                scene.setRoot(newRoot);
            }
            // Idempotent; the existing scene keeps its filter via the marker check.
            SoundManager.attachClickSfx(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            SoundManager.getInstance().playMusic(musicFor(fxml));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Which looping track plays on each scene. {@code null} = silence.
     * Round-result and license deliberately stay quiet.
     */
    private static SoundManager.MusicTrack musicFor(String fxml) {
        return switch (fxml) {
            case "/ui/game.fxml" -> SoundManager.MusicTrack.GAME;
            case "/ui/roundresult.fxml", "/ui/license.fxml" -> null;
            default -> SoundManager.MusicTrack.MENU;
        };
    }
}
