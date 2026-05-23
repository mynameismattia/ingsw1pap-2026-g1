package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Scene navigation that preserves the user's current window size.
 *
 * Each {@code stage.setScene(new Scene(loader.load(), W, H))} call in the old
 * code reset the window to a fixed size, which is why opening the tutorial or
 * starting a game would shrink the window. Going through {@link #navigate}
 * instead snapshots the stage dimensions before swapping the scene and
 * restores them after — so the window only resizes when the user resizes it.
 */
public final class Navigation {

    private Navigation() {}

    public static void navigate(Stage stage, String fxml) {
        try {
            boolean wasMaximized = stage.isMaximized();
            double w = stage.getWidth();
            double h = stage.getHeight();
            FXMLLoader loader = new FXMLLoader(Navigation.class.getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load());
            SoundManager.attachClickSfx(scene);

            // Senza questo lock, setScene rimpicciolisce la stage alla prefSize della
            // nuova scene per qualche frame prima che il runLater re-maximizi, e
            // l'utente vede un flash del desktop sotto. Usiamo getBounds() (non
            // getVisualBounds) per allinearci al comportamento di setMaximized su
            // stage UNDECORATED in Windows, che copre anche l'area della taskbar.
            if (wasMaximized) {
                Rectangle2D b = Screen.getPrimary().getBounds();
                stage.setX(b.getMinX());
                stage.setY(b.getMinY());
                stage.setWidth(b.getWidth());
                stage.setHeight(b.getHeight());
            }

            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            if (wasMaximized) {
                Platform.runLater(() -> stage.setMaximized(true));
            } else {
                stage.setResizable(true);
                if (w > 0 && !Double.isNaN(w)) stage.setWidth(w);
                if (h > 0 && !Double.isNaN(h)) stage.setHeight(h);
            }
            WindowControls.attach(scene, stage);
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
