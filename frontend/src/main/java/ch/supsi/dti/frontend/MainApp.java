package ch.supsi.dti.frontend;

import ch.supsi.dti.backend.license.LicenseChecker;
import ch.supsi.dti.frontend.controller.LicenseController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("21 - Blackjack");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/ui/icon.png")));

        String saved = LicenseController.loadSavedLicense();
        if (saved != null && new LicenseChecker().checkLicense(saved)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
            stage.setScene(new Scene(loader.load(), 1280, 720));
        } else {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/license.fxml"));
            Scene scene = new Scene(loader.load(), 480, 550);
            stage.setScene(scene);
            stage.setResizable(false);
        }

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
