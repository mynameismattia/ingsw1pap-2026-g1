// Schermata Carica partita.
// Lista degli slot con timestamp e info; al click carica il GameSnapshot via PersistenceService, ricostruisce il GameManager e naviga alla scena di gioco.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import ch.supsi.dti.backend.service.SaveSlot;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LoadGameController {

    @FXML private VBox slotsContainer;

    @FXML
    private void initialize() {
        refresh();
    }

    @FXML
    private void onBack() {
        Stage stage = (Stage) slotsContainer.getScene().getWindow();
        Navigation.navigate(stage, "/ui/menu.fxml");
    }

    private void refresh() {
        slotsContainer.getChildren().clear();
        Map<SaveSlot, Optional<GameSnapshot>> all = PersistenceService.loadAll();
        for (SaveSlot slot : SaveSlot.values()) {
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
            String playersStr = snap.playersData().stream()
                    .map(p -> p.name() + " ($" + p.balance() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            Label roundLbl = new Label(Slots.msg("load.slot.round", snap.currentRoundNumber()));
            roundLbl.getStyleClass().add("row-value");
            Label playersLbl = new Label(Slots.msg("load.slot.players", playersStr));
            playersLbl.getStyleClass().add("row-label");
            details.getChildren().add(roundLbl);
            details.getChildren().add(playersLbl);
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

        Button loadBtn = new Button(Slots.msg("load.action.load"));
        loadBtn.getStyleClass().add("primary-button");
        loadBtn.setDisable(!occupied);
        loadBtn.setOnAction(e -> loadSlot(slot, snapshot.orElse(null)));

        Button deleteBtn = new Button(Slots.msg("load.action.delete"));
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setDisable(!occupied);
        deleteBtn.setOnAction(e -> deleteSlot(slot));

        row.getChildren().addAll(loadBtn, deleteBtn);
        return row;
    }

    private void loadSlot(SaveSlot slot, GameSnapshot snap) {
        // 1. Guard: se lo snapshot è null (slot vuoto) non faccio nulla.
        if (snap == null) return;

        // 2. Ricostruisco gli oggetti Player dai PlayerSaveData (nome, balance, isBot) — i Player veri non si serializzano,
        //    si rifanno da zero qui per non portarci dietro mani/strategie morte.
        List<Player> players = new ArrayList<>(snap.playersData().size());
        for (GameSnapshot.PlayerSaveData pd : snap.playersData()) {
            players.add(new Player(pd.name(), pd.balance(), pd.isBot()));
        }

        // 3. Inietto il GameManager restaurato + il numero di round salvato nel GameController (campi statici di handoff),
        //    poi navigo alla scena di gioco che leggerà questi pending in initialize().
        GameController.setPendingGameManager(GameManager.restore(players, snap.roundHistory()));
        GameController.pendingResumedRoundNumber = snap.currentRoundNumber();
        Navigation.navigate((Stage) slotsContainer.getScene().getWindow(), "/ui/game.fxml");
    }

    private void deleteSlot(SaveSlot slot) {
        boolean confirmed = confirm(
                Slots.msg("load.delete.confirm.title"),
                Slots.msg("load.delete.confirm.body", Slots.label(slot)));
        if (!confirmed) return;
        try {
            new PersistenceService(slot).delete();
        } catch (Exception ex) {
            System.err.println("Delete failed for " + slot + ": " + ex.getMessage());
        }
        refresh();
    }

    private boolean confirm(String title, String body) {
        Stage dialog = new Stage();
        dialog.initOwner(slotsContainer.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);
        root.setMinWidth(380);
        root.getStyleClass().add("dialog-root");

        Label header = new Label(body);
        header.setWrapText(true);
        header.setMaxWidth(360);
        header.getStyleClass().add("dialog-header");
        root.getChildren().add(header);

        Button ok = new Button(Slots.msg("common.confirm"));
        ok.getStyleClass().add("danger-button");
        Button cancel = new Button(Slots.msg("common.cancel"));
        cancel.getStyleClass().add("secondary-button");
        HBox actions = new HBox(10, cancel, ok);
        actions.setAlignment(Pos.CENTER);
        root.getChildren().add(actions);

        final boolean[] result = {false};
        ok.setOnAction(e -> { result[0] = true; dialog.close(); });
        cancel.setOnAction(e -> dialog.close());

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/ui/menu.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }
}
