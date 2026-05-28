// Schermata Salva partita.
// Mostra 3 slot manuali + autosave, con anteprima (round, balance, timestamp) e bottone per sovrascrivere lo slot selezionato.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import ch.supsi.dti.backend.service.SaveSlot;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Optional;

public class SaveGameController {

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
        Slots.Row base = Slots.base(slot);
        HBox row = base.row();
        VBox details = base.details();

        boolean occupied = snapshot.isPresent();
        if (occupied) {
            GameSnapshot snap = snapshot.get();
            Label roundLbl = new Label(Slots.msg("load.slot.round", snap.currentRoundNumber()));
            roundLbl.getStyleClass().add("row-value");
            details.getChildren().add(roundLbl);
            new PersistenceService(slot).lastModified().ifPresent(t -> {
                Label d = new Label(Slots.DATE_FMT.format(t));
                d.getStyleClass().add("slot-meta");
                details.getChildren().add(d);
            });
        } else {
            Label empty = new Label(Slots.msg("load.slot.empty"));
            empty.getStyleClass().add("slot-empty");
            details.getChildren().add(empty);
        }

        Button save = new Button(occupied ? Slots.msg("save.action.overwrite") : Slots.msg("save.action.saveHere"));
        save.getStyleClass().add(occupied ? "danger-button" : "primary-button");
        save.setOnAction(e -> {
            if (occupied) {
                askOverwrite(() -> doSave(slot));
            } else {
                doSave(slot);
            }
        });

        row.getChildren().add(save);
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

    private void askOverwrite(Runnable onConfirm) {
        confirmBody.setText(Slots.msg("save.slot.overwrite.body"));
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
