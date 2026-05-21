package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.BotNames;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import javafx.fxml.FXML;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class MenuController {

    private static final int DEFAULT_BALANCE = 100;
    private static final int MAX_SEATS = 4;

    private enum Mode { VS_CPU, MULTI, TUTORIAL }

    /** Sound preference — survives FXML reload (language switch). No audio system reads it yet. */
    private static boolean soundEnabled = true;
    public static boolean isSoundEnabled() { return soundEnabled; }

    @FXML private Button modeVsCpuBtn;
    @FXML private Button modeMultiBtn;
    @FXML private Button modeTutorialBtn;
    @FXML private Spinner<Integer> humansSpinner;
    @FXML private Spinner<Integer> cpusSpinner;
    @FXML private Spinner<Integer> balanceSpinner;
    @FXML private CheckBox soundToggle;
    @FXML private Label licenseCodeLabel;
    @FXML private Button startBtn;
    @FXML private Button continueBtn;
    @FXML private Button settingsBtn;

    private Mode selectedMode = Mode.VS_CPU;

    @FXML
    private void initialize() {
        humansSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 1));
        cpusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 4, 1));

        balanceSpinner.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(
                javafx.collections.FXCollections.observableArrayList(50, 100, 250, 500, 1000)));
        balanceSpinner.getValueFactory().setValue(DEFAULT_BALANCE);
        balanceSpinner.getValueFactory().setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer i) { return i == null ? "" : "$" + i; }
            @Override public Integer fromString(String s) { return Integer.parseInt(s.replace("$", "").trim()); }
        });

        soundToggle.setSelected(soundEnabled);
        soundToggle.selectedProperty().addListener((obs, oldVal, newVal) -> soundEnabled = newVal);

        String saved = LicenseController.loadSavedLicense();
        if (saved != null) {
            licenseCodeLabel.setText(saved);
        }

        continueBtn.setDisable(!new PersistenceService().exists());

        applyModeStyle();
    }

    @FXML
    private void onContinue() {
        PersistenceService ps = new PersistenceService();
        Optional<GameSnapshot> loaded;
        try {
            loaded = ps.load();
        } catch (Exception e) {
            System.err.println("Failed to load saved game: " + e.getMessage());
            continueBtn.setDisable(true);
            return;
        }
        if (loaded.isEmpty()) {
            continueBtn.setDisable(true);
            return;
        }
        GameSnapshot snap = loaded.get();
        List<Player> players = new ArrayList<>(snap.playersData().size());
        for (GameSnapshot.PlayerSaveData pd : snap.playersData()) {
            players.add(new Player(pd.name(), pd.balance(), pd.isBot()));
        }
        GameController.setPendingGameManager(GameManager.restore(players, snap.roundHistory()));
        GameController.pendingResumedRoundNumber = snap.currentRoundNumber();
        Navigation.navigate((Stage) continueBtn.getScene().getWindow(), "/ui/game.fxml");
    }

    @FXML
    private void onModeVsCpu()    { selectedMode = Mode.VS_CPU;    applyModeStyle(); }
    @FXML
    private void onModeMulti()    { selectedMode = Mode.MULTI;     applyModeStyle(); }
    @FXML
    private void onModeTutorial() { selectedMode = Mode.TUTORIAL;  applyModeStyle(); }

    /**
     * Highlights the selected mode pill and constrains the table spinners so
     * each mode produces a configuration that matches its name. The total
     * number of seats (humans + CPUs) is hard-capped at {@value #MAX_SEATS}.
     *  - VS_CPU: 1 human, 1..3 CPUs (humans locked, CPUs editable)
     *  - MULTI:  2..4 humans, 0 CPU (CPUs locked, humans editable)
     *  - TUTORIAL: navigates to the rules page (no game starts).
     */
    private void applyModeStyle() {
        modeVsCpuBtn.getStyleClass().remove("mode-pill-active");
        modeMultiBtn.getStyleClass().remove("mode-pill-active");
        modeTutorialBtn.getStyleClass().remove("mode-pill-active");

        SpinnerValueFactory.IntegerSpinnerValueFactory hf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) humansSpinner.getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory cf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) cpusSpinner.getValueFactory();

        switch (selectedMode) {
            case VS_CPU -> {
                modeVsCpuBtn.getStyleClass().add("mode-pill-active");
                // 1 human locked, CPUs 1..(MAX_SEATS-1)
                hf.setMin(1); hf.setMax(1); hf.setValue(1);
                cf.setMin(1); cf.setMax(MAX_SEATS - 1);
                if (cf.getValue() == null || cf.getValue() < 1) cf.setValue(1);
                if (cf.getValue() > MAX_SEATS - 1) cf.setValue(MAX_SEATS - 1);
                humansSpinner.setDisable(true);
                cpusSpinner.setDisable(false);
            }
            case MULTI -> {
                modeMultiBtn.getStyleClass().add("mode-pill-active");
                // 0 CPU locked, humans 2..MAX_SEATS
                cf.setMin(0); cf.setMax(0); cf.setValue(0);
                hf.setMin(2); hf.setMax(MAX_SEATS);
                if (hf.getValue() < 2) hf.setValue(2);
                cpusSpinner.setDisable(true);
                humansSpinner.setDisable(false);
            }
            case TUTORIAL -> modeTutorialBtn.getStyleClass().add("mode-pill-active");
        }
    }

    @FXML
    private void onStartGame() {
        startBtn.setDisable(true);
        Stage stage = (Stage) startBtn.getScene().getWindow();

        if (selectedMode == Mode.TUTORIAL) {
            Navigation.navigate(stage, "/ui/tutorial.fxml");
            return;
        }

        int humanCount = humansSpinner.getValue();
        int botCount = cpusSpinner.getValue();
        int balance = balanceSpinner.getValue();

        List<String> humanNames;
        if (humanCount > 1) {
            Optional<List<String>> names = promptHumanNames(humanCount);
            if (names.isEmpty()) {
                startBtn.setDisable(false);
                return; // user cancelled
            }
            humanNames = names.get();
        } else {
            humanNames = List.of("Player 1");
        }

        List<Player> players = new ArrayList<>(humanCount + botCount);
        Set<String> taken = new HashSet<>(humanNames);
        for (String name : humanNames) {
            players.add(new Player(name, balance));
        }
        if (botCount > 0) {
            for (String botName : BotNames.allocate(botCount, taken)) {
                players.add(new Player(botName, balance, true));
            }
        }

        GameController.setPendingGameManager(new GameManager(players));
        Navigation.navigate(stage, "/ui/game.fxml");
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
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/profile.fxml");
    }

    @FXML
    private void onLeaderboard() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/leaderboard.fxml");
    }

    @FXML
    private void onLicense() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/license.fxml");
    }

    @FXML
    private void onSettings() {
        LanguageDropdown.show(settingsBtn,
                () -> Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/menu.fxml"));
    }
}
