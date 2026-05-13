package ch.supsi.dti.frontend;

import ch.supsi.dti.backend.license.LicenseChecker;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void init() {
        LicenseChecker checker = new LicenseChecker();
        if (!checker.checkLicense("AAAA-AAAA-AAAA-sAAK")) {
            System.err.println("Licenza non valida - chiusura applicazione.");
            System.exit(1);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 720);

        stage.setTitle("BlackJack");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}