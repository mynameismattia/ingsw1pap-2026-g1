  package ch.supsi.dti.frontend;

  import ch.supsi.dti.backend.i18n.MessageService;
  import ch.supsi.dti.backend.license.LicenseChecker;
  import ch.supsi.dti.frontend.controller.LicenseController;
  import ch.supsi.dti.frontend.controller.WindowControls;
  import ch.supsi.dti.frontend.service.SoundManager;
  import javafx.application.Application;
  import javafx.fxml.FXMLLoader;
  import javafx.scene.Parent;
  import javafx.scene.Scene;
  import javafx.scene.image.Image;
  import javafx.stage.Stage;
  import javafx.stage.StageStyle;

  import java.util.Objects;

  public class MainApp extends Application {

      @Override
      public void start(Stage stage) throws Exception {
          SoundManager.getInstance().preload();
          stage.initStyle(StageStyle.UNDECORATED);
          stage.setTitle(MessageService.getInstance().getMessage("app.title"));
          stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/ui/icon.png"))));
          stage.setMaximized(true);

          String saved = LicenseController.loadSavedLicense();
          if (saved != null && new LicenseChecker().verifyLicense(saved)) {
              stage.setScene(new Scene(loadMenuRoot(), 1100, 680));
          } else {
              FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/license.fxml"));
              stage.setScene(new Scene(loader.load(), 480, 550));
          }
          SoundManager.attachClickSfx(stage.getScene());
          WindowControls.attach(stage.getScene(), stage);

          stage.show();
      }

      private static Parent loadMenuRoot() throws Exception {
          FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/ui/menu.fxml"));
          loader.setResources(MessageService.getInstance().getBundle());
          return loader.load();
      }

      public static void main(String[] args) {
          launch(args);
      }
  }
