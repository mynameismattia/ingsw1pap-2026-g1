package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.Player;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MenuController {

    private static final int INITIAL_BALANCE = 100;

    private enum Mode { VS_CPU, MULTI, TUTORIAL }

    @FXML private Button modeVsCpuBtn;
    @FXML private Button modeMultiBtn;
    @FXML private Button modeTutorialBtn;
    @FXML private Spinner<Integer> humansSpinner;
    @FXML private CheckBox soundToggle;
    @FXML private Label licenseCodeLabel;
    @FXML private Button startBtn;

    private Mode selectedMode = Mode.VS_CPU;

    @FXML
    private void initialize() {
        humansSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 2));

        String saved = LicenseController.loadSavedLicense();
        if (saved != null) {
            licenseCodeLabel.setText(saved);
        }
        applyModeStyle();
    }

    @FXML
    private void onModeVsCpu()    { selectedMode = Mode.VS_CPU;    applyModeStyle(); }
    @FXML
    private void onModeMulti()    { selectedMode = Mode.MULTI;     applyModeStyle(); }
    @FXML
    private void onModeTutorial() { selectedMode = Mode.TUTORIAL;  applyModeStyle(); }

    private void applyModeStyle() {
        modeVsCpuBtn.getStyleClass().remove("mode-pill-active");
        modeMultiBtn.getStyleClass().remove("mode-pill-active");
        modeTutorialBtn.getStyleClass().remove("mode-pill-active");
        switch (selectedMode) {
            case VS_CPU   -> modeVsCpuBtn.getStyleClass().add("mode-pill-active");
            case MULTI    -> modeMultiBtn.getStyleClass().add("mode-pill-active");
            case TUTORIAL -> modeTutorialBtn.getStyleClass().add("mode-pill-active");
        }
    }

    @FXML
    private void onStartGame() {
        startBtn.setDisable(true);

        List<Player> players;
        if (selectedMode == Mode.MULTI && humansSpinner.getValue() > 1) {
            Optional<List<String>> names = promptHumanNames(humansSpinner.getValue());
            if (names.isEmpty()) {
                startBtn.setDisable(false);
                return; // user cancelled
            }
            players = new ArrayList<>(names.get().size());
            for (String name : names.get()) {
                players.add(new Player(name, INITIAL_BALANCE));
            }
        } else {
            players = List.of(new Player("Player 1", INITIAL_BALANCE));
        }

        GameController.setPendingGameManager(new GameManager(players));

        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/game.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1280, 720);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            startBtn.setDisable(false);
            e.printStackTrace();
        }
    }

    private Optional<List<String>> promptHumanNames(int n) {
        Stage dialog = new Stage();
        dialog.initOwner(startBtn.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(MessageService.getInstance().getMessage("menu.names.title"));
        dialog.setResizable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        Label header = new Label(MessageService.getInstance().getMessage("menu.names.header"));
        header.setStyle("-fx-font-size: 14;");
        root.getChildren().add(header);

        List<TextField> fields = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            TextField tf = new TextField("Player " + (i + 1));
            tf.setPromptText("Player " + (i + 1));
            tf.setPrefWidth(240);
            fields.add(tf);
            root.getChildren().add(tf);
        }

        Button ok = new Button(MessageService.getInstance().getMessage("menu.names.ok"));
        Button cancel = new Button(MessageService.getInstance().getMessage("menu.names.cancel"));
        HBox actions = new HBox(10, cancel, ok);
        actions.setAlignment(Pos.CENTER);
        root.getChildren().add(actions);

        final List<String>[] result = new List[]{null};
        ok.setOnAction(e -> {
            List<String> names = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String v = fields.get(i).getText().trim();
                names.add(v.isEmpty() ? "Player " + (i + 1) : v);
            }
            result[0] = names;
            dialog.close();
        });
        cancel.setOnAction(e -> dialog.close());

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    @FXML
    private void onProfile() {
        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/profile.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1100, 680);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLeaderboard() {
        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/leaderboard.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 1100, 680);
            stage.setScene(scene);
            stage.setTitle(MessageService.getInstance().getMessage("app.title"));
            stage.setResizable(true);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onSettings() {
        try {
            Stage dialog = new Stage();
            dialog.initOwner(startBtn.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/settings.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            Scene scene = new Scene(loader.load(), 480, 320);
            dialog.setScene(scene);
            dialog.setTitle("Impostazioni");
            dialog.setResizable(false);

            SettingsController ctrl = loader.getController();
            ctrl.setDialogStage(dialog);
            ctrl.setOnApply(this::reloadMenu);

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reloadMenu() {
        try {
            Stage stage = (Stage) startBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/menu.fxml"));
            loader.setResources(MessageService.getInstance().getBundle());
            stage.setScene(new Scene(loader.load(), 1100, 680));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
