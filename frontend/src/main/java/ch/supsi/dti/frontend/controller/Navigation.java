package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
            double w = stage.getWidth();
            double h = stage.getHeight();
            FXMLLoader loader = new FXMLLoader(Navigation.class.getResource(fxml));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load());
            SoundManager.attachClickSfx(scene);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            if (w > 0 && !Double.isNaN(w)) stage.setWidth(w);
            if (h > 0 && !Double.isNaN(h)) stage.setHeight(h);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
