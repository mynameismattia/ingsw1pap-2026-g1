package ch.supsi.dti.frontend;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        Parent root = loadRoot();
        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.setTitle(MessageService.getInstance().getMessage("app.title"));
        primaryStage.show();
    }

    public static void reloadRoot() throws Exception {
        Parent root = loadRoot();
        primaryStage.getScene().setRoot(root);
        primaryStage.setTitle(MessageService.getInstance().getMessage("app.title"));
    }

    private static Parent loadRoot() throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/game.fxml"));
        loader.setResources(MessageService.getInstance().getBundle());
        return loader.load();
    }

    public static void main(String[] args) {
        launch(args);
    }
}