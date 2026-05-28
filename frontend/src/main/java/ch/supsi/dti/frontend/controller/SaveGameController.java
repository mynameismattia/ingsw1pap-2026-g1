// Schermata Salva partita.
// Mostra 3 slot manuali + autosave, con anteprima (round, balance, timestamp) e bottone per sovrascrivere lo slot selezionato.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import ch.supsi.dti.backend.service.SaveSlot;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public class SaveGameController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @FXML private VBox slotsContainer;

    @FXML private StackPane confirmOverlay;
    @FXML private Label confirmBody;
    @FXML private Button confirmOk;
    @FXML private Button confirmCancel;

    private Runnable pendingConfirm;

    @FXML
    private void initialize() {
        refresh();
        confirmCancel.setOnAction(e -> hideConfirm());
        confirmOk.setOnAction(e -> {
            Runnable r = pendingConfirm;
            hideConfirm();
            if (r != null) r.run();
        });
    }

    @FXML
    private void onBack() {
        Stage stage = (Stage) slotsContainer.getScene().getWindow();
        Navigation.navigate(stage, "/ui/roundresult.fxml");
    }

    private void refresh() {
        slotsContainer.getChildren().clear();
        Map<SaveSlot, Optional<GameSnapshot>> all = PersistenceService.loadAll();
        for (SaveSlot slot : SaveSlot.manualSlots()) {
            slotsContainer.getChildren().add(buildSlotRow(slot, all.get(slot)));
        }
    }

    private HBox buildSlotRow(SaveSlot slot, Optional<GameSnapshot> snapshot) {
        // 1. Riga base: HBox con titolo (es. "Slot 1"), dettagli flex al centro, bottone Salva a destra.
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("row");
        row.setPadding(new Insets(10));

        Label title = new Label(slotLabel(slot));
        title.getStyleClass().add("slot-title");
        title.setMinWidth(140);

        VBox details = new VBox(2);
        HBox.setHgrow(details, Priority.ALWAYS);

        // 2. Se lo slot è già occupato mostro round + data ultima modifica; altrimenti la label "vuoto".
        boolean occupied = snapshot.isPresent();
        if (occupied) {
            GameSnapshot snap = snapshot.get();
            Label roundLbl = new Label(msg("load.slot.round", snap.currentRoundNumber()));
            roundLbl.getStyleClass().add("row-value");
            details.getChildren().add(roundLbl);
            new PersistenceService(slot).lastModified().ifPresent(t -> {
                Label d = new Label(DATE_FMT.format(t));
                d.getStyleClass().add("slot-meta");
                details.getChildren().add(d);
            });
        } else {
            Label empty = new Label(msg("load.slot.empty"));
            empty.getStyleClass().add("slot-empty");
            details.getChildren().add(empty);
        }

        // 3. Bottone Salva (o "Sovrascrivi" se lo slot è già occupato) — quest'ultimo apre prima un overlay di conferma.
        Button save = new Button(occupied ? msg("save.action.overwrite") : msg("save.action.saveHere"));
        save.getStyleClass().add(occupied ? "danger-button" : "primary-button");
        save.setOnAction(e -> {
            if (occupied) {
                askOverwrite(() -> doSave(slot));
            } else {
                doSave(slot);
            }
        });

        row.getChildren().addAll(title, details, save);
        return row;
    }

    private void doSave(SaveSlot slot) {
        try {
            // 1. Costruisco lo snapshot dal GameController condiviso (statico): contiene round + saldi + storico.
            GameSnapshot snap = GameSnapshot.fromGameManager(
                    GameController.sharedGameManager,
                    GameController.sharedRoundNumber);
            // 2. Lo serializzo in JSON nel file dello slot scelto via PersistenceService.
            new PersistenceService(slot).save(snap);
        } catch (Exception ex) {
            System.err.println("Manual save failed: " + ex.getMessage());
        }
        // 3. Torno alla schermata Vedi Risultati (da dove l'utente di solito apre il save).
        Stage stage = (Stage) slotsContainer.getScene().getWindow();
        Navigation.navigate(stage, "/ui/roundresult.fxml");
    }

    private String slotLabel(SaveSlot slot) {
        return switch (slot) {
            case SLOT_1 -> msg("load.slot.n", 1);
            case SLOT_2 -> msg("load.slot.n", 2);
            case SLOT_3 -> msg("load.slot.n", 3);
            default -> "";
        };
    }

    private static String msg(String key, Object... args) {
        return MessageService.getInstance().getMessage(key, args);
    }

    private void askOverwrite(Runnable onConfirm) {
        confirmBody.setText(msg("save.slot.overwrite.body"));
        pendingConfirm = onConfirm;
        confirmOverlay.setVisible(true);
        confirmOverlay.setManaged(true);
    }

    @FXML
    private void onConfirmBackdropClicked(MouseEvent e) {

        if (e.getTarget() == confirmOverlay) {
            hideConfirm();
        }
    }

    private void hideConfirm() {
        pendingConfirm = null;
        confirmOverlay.setVisible(false);
        confirmOverlay.setManaged(false);
    }
}
