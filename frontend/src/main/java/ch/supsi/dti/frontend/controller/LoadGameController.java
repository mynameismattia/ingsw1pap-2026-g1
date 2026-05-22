package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.i18n.MessageService;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LoadGameController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

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
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("row");
        row.setPadding(new Insets(10));

        Label title = new Label(slotLabel(slot));
        title.getStyleClass().add("field-label");
        title.setMinWidth(120);

        VBox details = new VBox(2);
        HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);

        boolean occupied = snapshot.isPresent();
        if (occupied) {
            GameSnapshot snap = snapshot.get();
            String playersStr = snap.playersData().stream()
                    .map(p -> p.name() + " ($" + p.balance() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            String roundLine = msg("load.slot.round", snap.currentRoundNumber());
            String playersLine = msg("load.slot.players", playersStr);
            String dateLine = new PersistenceService(slot).lastModified()
                    .map(DATE_FMT::format).orElse("");

            details.getChildren().add(new Label(roundLine));
            details.getChildren().add(new Label(playersLine));
            if (!dateLine.isEmpty()) {
                Label d = new Label(dateLine);
                d.getStyleClass().add("badge-soon");
                details.getChildren().add(d);
            }
        } else {
            Label empty = new Label(msg("load.slot.empty"));
            empty.getStyleClass().add("badge-soon");
            details.getChildren().add(empty);
        }

        Button loadBtn = new Button(msg("load.action.load"));
        loadBtn.getStyleClass().add("primary-button");
        loadBtn.setDisable(!occupied);
        loadBtn.setOnAction(e -> loadSlot(slot, snapshot.orElse(null)));

        Button deleteBtn = new Button(msg("load.action.delete"));
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setDisable(!occupied);
        deleteBtn.setOnAction(e -> deleteSlot(slot));

        row.getChildren().addAll(title, details, loadBtn, deleteBtn);
        return row;
    }

    private void loadSlot(SaveSlot slot, GameSnapshot snap) {
        if (snap == null) return;
        List<Player> players = new ArrayList<>(snap.playersData().size());
        for (GameSnapshot.PlayerSaveData pd : snap.playersData()) {
            players.add(new Player(pd.name(), pd.balance(), pd.isBot()));
        }
        GameController.setPendingGameManager(GameManager.restore(players, snap.roundHistory()));
        GameController.pendingResumedRoundNumber = snap.currentRoundNumber();
        Navigation.navigate((Stage) slotsContainer.getScene().getWindow(), "/ui/game.fxml");
    }

    private void deleteSlot(SaveSlot slot) {
        boolean confirmed = confirm(
                msg("load.delete.confirm.title"),
                msg("load.delete.confirm.body", slotLabel(slot)));
        if (!confirmed) return;
        try {
            new PersistenceService(slot).delete();
        } catch (Exception ex) {
            System.err.println("Delete failed for " + slot + ": " + ex.getMessage());
        }
        refresh();
    }

    private String slotLabel(SaveSlot slot) {
        return switch (slot) {
            case AUTO -> msg("load.slot.auto");
            case SLOT_1 -> msg("load.slot.n", 1);
            case SLOT_2 -> msg("load.slot.n", 2);
            case SLOT_3 -> msg("load.slot.n", 3);
        };
    }

    private static String msg(String key, Object... args) {
        return MessageService.getInstance().getMessage(key, args);
    }

    private boolean confirm(String title, String body) {
        Stage dialog = new Stage();
        dialog.initOwner(slotsContainer.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label header = new Label(body);
        header.setWrapText(true);
        header.setMaxWidth(360);
        root.getChildren().add(header);

        Button ok = new Button(msg("common.confirm"));
        ok.getStyleClass().add("danger-button");
        Button cancel = new Button(msg("common.cancel"));
        cancel.getStyleClass().add("secondary-button");
        HBox actions = new HBox(10, cancel, ok);
        actions.setAlignment(Pos.CENTER);
        root.getChildren().add(actions);

        final boolean[] result = {false};
        ok.setOnAction(e -> { result[0] = true; dialog.close(); });
        cancel.setOnAction(e -> dialog.close());

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return result[0];
    }
}
