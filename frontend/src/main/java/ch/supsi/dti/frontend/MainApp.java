  package ch.supsi.dti.frontend;

  import ch.supsi.dti.backend.i18n.MessageService;
  import ch.supsi.dti.backend.license.LicenseChecker;
  import ch.supsi.dti.frontend.controller.LicenseController;
  import javafx.application.Application;
  import javafx.fxml.FXMLLoader;
  import javafx.scene.Parent;
  import javafx.scene.Scene;
  import javafx.scene.image.Image;
  import javafx.stage.Stage;

  import java.util.Objects;

  public class MainApp extends Application {

      private static Stage primaryStage;

      @Override
      public void start(Stage stage) throws Exception {
          primaryStage = stage;
          stage.setTitle(MessageService.getInstance().getMessage("app.title"));
          stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/ui/icon.png"))));

          String saved = LicenseController.loadSavedLicense();
          if (saved != null && new LicenseChecker().checkLicense(saved)) {
              stage.setScene(new Scene(loadGameRoot(), 1280, 720));
          } else {
              FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/license.fxml"));
              stage.setScene(new Scene(loader.load(), 480, 550));
              stage.setResizable(false);
          }

          stage.show();
      }

      public static void reloadRoot() throws Exception {
          primaryStage.getScene().setRoot(loadGameRoot());
          primaryStage.setTitle(MessageService.getInstance().getMessage("app.title"));
      }

      private static Parent loadGameRoot() throws Exception {
          FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/game.fxml"));
          loader.setResources(MessageService.getInstance().getBundle());
          return loader.load();
      }

      public static void main(String[] args) {
          launch(args);
      }
  }