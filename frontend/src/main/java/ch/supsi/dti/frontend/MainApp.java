// Punto di ingresso JavaFX.
// Carica i font custom da /fonts/, ripristina la modalità finestra salvata (windowed/fullscreen via DisplayMode), verifica la licenza con LicenseChecker e mostra la scena giusta: license.fxml se non valida, menu.fxml se ok.

  package ch.supsi.dti.frontend;

  import ch.supsi.dti.backend.i18n.MessageService;
  import ch.supsi.dti.backend.license.LicenseChecker;
  import ch.supsi.dti.frontend.controller.DisplayMode;
  import ch.supsi.dti.frontend.controller.LicenseController;
  import ch.supsi.dti.frontend.service.SoundManager;
  import javafx.application.Application;
  import javafx.fxml.FXMLLoader;
  import javafx.scene.Parent;
  import javafx.scene.Scene;
  import javafx.scene.image.Image;
  import javafx.scene.text.Font;
  import javafx.stage.Stage;

  import java.io.InputStream;
  import java.util.Objects;

  public class MainApp extends Application {

      @Override
      public void start(Stage stage) throws Exception {
          // 1. Pre-carico tutto ciò che renderebbe lenti i primi click: font custom (Manrope/Inter/JetBrains Mono) e AudioClip.
          loadCustomFonts();
          SoundManager.getInstance().preload();

          // 2. Titolo finestra + icona dell'app. Il titolo passa per i18n così è già tradotto.
          stage.setTitle(MessageService.getInstance().getMessage("app.title"));
          stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/ui/icon.png"))));

          // 3. Sopprimo l'hint nativo "Press ESC to exit fullscreen" (lo gestiamo noi quando serve).
          stage.setFullScreenExitHint("");

          // 4. Ripristino la modalità finestra (windowed/fullscreen) salvata nelle Preferences.
          DisplayMode.loadSaved().apply(stage);

          // 5. Check licenza: leggo il codice salvato in ~/.blackjack/license e lo passo al LicenseChecker nativo (JNI).
          String saved = LicenseController.loadSavedLicense();
          boolean licensed = saved != null && new LicenseChecker().verifyLicense(saved);

          // 6. Scena giusta in base all'esito: menu se licenziato, schermata licenza altrimenti.
          if (licensed) {
              stage.setScene(new Scene(loadMenuRoot(), 1100, 680));
          } else {
              FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/license.fxml"));
              stage.setScene(new Scene(loader.load(), 480, 550));
          }

          // 7. Aggancio i click SFX globali e mostro la finestra; se siamo licenziati parte la musica del menu.
          SoundManager.attachClickSfx(stage.getScene());
          stage.show();
          if (licensed) {
              SoundManager.getInstance().playMusic(SoundManager.MusicTrack.MENU);
          }
      }

      private static void loadCustomFonts() {
          // 1. Lista dei .ttf bundlati in /fonts/. Sono opzionali: se mancano, JavaFX usa i fallback CSS (Segoe UI, Menlo).
          String[] candidates = {
              "/fonts/Manrope-Bold.ttf",
              "/fonts/Manrope-Medium.ttf",
              "/fonts/Inter-Medium.ttf",
              "/fonts/Inter-Regular.ttf",
              "/fonts/JetBrainsMono-Medium.ttf"
          };
          // 2. Per ogni font provo a registrarlo in JavaFX. Se non lo trovo o non si carica, ignoro silenziosamente.
          for (String path : candidates) {
              try (InputStream in = MainApp.class.getResourceAsStream(path)) {
                  if (in != null) Font.loadFont(in, 12);
              } catch (Exception ignored) { }
          }
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
