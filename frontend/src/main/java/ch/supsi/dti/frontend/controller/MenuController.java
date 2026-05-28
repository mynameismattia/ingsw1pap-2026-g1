// Menu principale.
// Scelta modalità (vs CPU / multi / tutorial via mode-pill), numero di player umani/cpu, saldo iniziale (spinner), bottoni Start nuova partita o Continue dall'autosave.

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
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
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
    @FXML private VBox tableSection;
    @FXML private Label licenseCodeLabel;
    @FXML private Button startBtn;
    @FXML private Button continueBtn;
    @FXML private Button settingsBtn;

    private Mode selectedMode = Mode.VS_CPU;

    @FXML
    private void initialize() {
        // 1. Setup degli spinner: humans 1-4, cpus 0-4, balance da una lista fissa di valori (50, 100, 250, 500, 1000).
        humansSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 1));
        cpusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 4, 1));

        balanceSpinner.setValueFactory(new SpinnerValueFactory.ListSpinnerValueFactory<>(
                javafx.collections.FXCollections.observableArrayList(50, 100, 250, 500, 1000)));
        balanceSpinner.getValueFactory().setValue(DEFAULT_BALANCE);

        // 2. Converter custom per il balance: lo mostra come "$100" invece che "100" e accetta il prefisso $ in input.
        balanceSpinner.getValueFactory().setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer i) { return i == null ? "" : "$" + i; }
            @Override public Integer fromString(String s) { return Integer.parseInt(s.replace("$", "").trim()); }
        });

        // 3. Aggancio il click sound agli spinner (le frecce su/giù devono fare clic come gli altri bottoni).
        SoundManager.attachSpinnerClick(humansSpinner);
        SoundManager.attachSpinnerClick(cpusSpinner);
        SoundManager.attachSpinnerClick(balanceSpinner);

        // 4. Se c'è una licenza salvata, la mostro come pill in basso a sinistra ("XXXXX-XXXXX-XXXXX-XXXXX").
        String saved = LicenseController.loadSavedLicense();
        if (saved != null) {
            licenseCodeLabel.setText(saved);
        }

        // 5. Il bottone Continue va attivato solo se c'è almeno un save (autosave o slot manuale).
        continueBtn.setDisable(!anySaveExists());

        // 6. Applico lo stile della modalità di default (VS_CPU): selected pill + spinner constraint corretti.
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

    private void applyModeStyle() {
        // 1. Reset: tolgo la classe "mode-pill-active" da tutti, poi gliela rimetterò solo a quello selezionato.
        modeVsCpuBtn.getStyleClass().remove("mode-pill-active");
        modeMultiBtn.getStyleClass().remove("mode-pill-active");
        modeTutorialBtn.getStyleClass().remove("mode-pill-active");

        // 2. Riporto la UI alla configurazione base (table-section visibile, continue visibile, start con testo "Inizia").
        //    Le branch del switch sottostante poi modificano questo stato per il caso TUTORIAL.
        tableSection.setVisible(true);
        tableSection.setManaged(true);
        continueBtn.setVisible(true);
        continueBtn.setManaged(true);
        startBtn.setText(MessageService.getInstance().getMessage("mainmenu.action.start"));

        SpinnerValueFactory.IntegerSpinnerValueFactory hf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) humansSpinner.getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory cf =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) cpusSpinner.getValueFactory();

        switch (selectedMode) {
            case VS_CPU -> {
                modeVsCpuBtn.getStyleClass().add("mode-pill-active");

                hf.setMin(1); hf.setMax(1); hf.setValue(1);
                cf.setMin(1); cf.setMax(MAX_SEATS - 1);
                if (cf.getValue() == null || cf.getValue() < 1) cf.setValue(1);
                if (cf.getValue() > MAX_SEATS - 1) cf.setValue(MAX_SEATS - 1);
                humansSpinner.setDisable(true);
                cpusSpinner.setDisable(false);
            }
            case MULTI -> {
                modeMultiBtn.getStyleClass().add("mode-pill-active");

                cf.setMin(0); cf.setMax(0); cf.setValue(0);
                hf.setMin(2); hf.setMax(MAX_SEATS);
                if (hf.getValue() < 2) hf.setValue(2);
                cpusSpinner.setDisable(true);
                humansSpinner.setDisable(false);
            }
            case TUTORIAL -> {
                modeTutorialBtn.getStyleClass().add("mode-pill-active");
                tableSection.setVisible(false);
                tableSection.setManaged(false);
                continueBtn.setVisible(false);
                continueBtn.setManaged(false);
                startBtn.setText(MessageService.getInstance().getMessage("mainmenu.action.readManual"));
            }
        }
    }

    @FXML
    private void onStartGame() {
        // 1. Anti doppio-click: disabilito subito il bottone Start (riabilitato solo se l'utente annulla il dialog multi).
        startBtn.setDisable(true);
        Stage stage = (Stage) startBtn.getScene().getWindow();

        // 2. Modalità tutorial: niente partita, vado direttamente alla schermata di spiegazione delle regole.
        if (selectedMode == Mode.TUTORIAL) {
            Navigation.navigate(stage, "/ui/tutorial.fxml");
            return;
        }

        // 3. Leggo i parametri scelti dall'utente negli spinner.
        int humanCount = humansSpinner.getValue();
        int botCount = cpusSpinner.getValue();
        int balance = balanceSpinner.getValue();

        // 4. Multiplayer: serve raccogliere i nomi dei player umani (PlayerNamesDialog crea poi la partita).
        if (humanCount > 1) {
            PlayerNamesDialog.show(stage, humanCount, botCount, balance,
                    () -> startBtn.setDisable(false));
            return;
        }

        // 5. Solo player (VS_CPU): costruisco la lista di Player con 1 umano + N bot con nomi dal pool BotNames.
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

        // 6. Inietto il GameManager pendente nel GameController (statico) e navigo alla scena di gioco.
        GameController.setPendingGameManager(new GameManager(players));
        Navigation.navigate(stage, "/ui/game.fxml");
    }

    @FXML
    private void onLicense() {
        Navigation.navigate((Stage) startBtn.getScene().getWindow(), "/ui/license.fxml");
    }

    @FXML
    private void onSettings() {
        Stage stage = (Stage) startBtn.getScene().getWindow();
        SettingsDialog.show(stage,
                () -> Navigation.navigate(stage, "/ui/menu.fxml"));
    }
}
