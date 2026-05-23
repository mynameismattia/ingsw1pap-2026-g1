package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.BotNames;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.service.SaveSlot;

import java.nio.file.Files;
import java.util.Arrays;
import ch.supsi.dti.frontend.service.SoundManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MenuController {

    private static final int DEFAULT_BALANCE = 100;
    private static final int MAX_SEATS = 4;

    private enum Mode { VS_CPU, MULTI, TUTORIAL }

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

        SoundManager sound = SoundManager.getInstance();
        soundToggle.setSelected(!sound.isMuted());
        soundToggle.selectedProperty().addListener((obs, oldVal, newVal) -> sound.setMuted(!newVal));

        SoundManager.attachSpinnerClick(humansSpinner);
        SoundManager.attachSpinnerClick(cpusSpinner);
        SoundManager.attachSpinnerClick(balanceSpinner);

        String saved = LicenseController.loadSavedLicense();
        if (saved != null) {
            licenseCodeLabel.setText(saved);
        }

        continueBtn.setDisable(!anySaveExists());

        applyModeStyle();
    }

    private static boolean anySaveExists() {
        return Arrays.stream(SaveSlot.values()).anyMatch(s -> Files.exists(s.path()));
    }

    @FXML
    private void onContinue() {
        Navigation.navigate((Stage) continueBtn.getScene().getWindow(), "/ui/load.fxml");
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

        if (humanCount > 1) {
            PlayerNamesController.pendingHumanCount = humanCount;
            PlayerNamesController.pendingBotCount = botCount;
            PlayerNamesController.pendingBalance = balance;
            Navigation.navigate(stage, "/ui/playernames.fxml");
            return;
        }

        List<String> humanNames = List.of("Player 1");
        List<Player> players = new ArrayList<>(1 + botCount);
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
