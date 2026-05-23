package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public class SaveGameController {

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
        Navigation.navigate(stage, "/ui/game.fxml");
    }

    private void refresh() {
        slotsContainer.getChildren().clear();
        Map<SaveSlot, Optional<GameSnapshot>> all = PersistenceService.loadAll();
        for (SaveSlot slot : SaveSlot.manualSlots()) {
            slotsContainer.getChildren().add(buildSlotRow(slot, all.get(slot)));
        }
    }

    private HBox buildSlotRow(SaveSlot slot, Optional<GameSnapshot> snapshot) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("row");
        row.setPadding(new Insets(10));

        Label title = new Label(slotLabel(slot));
        title.getStyleClass().add("slot-title");
        title.setMinWidth(140);

        VBox details = new VBox(2);
        HBox.setHgrow(details, Priority.ALWAYS);

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

        Button save = new Button(occupied ? msg("save.action.overwrite") : msg("save.action.saveHere"));
        save.getStyleClass().add(occupied ? "danger-button" : "primary-button");
        save.setOnAction(e -> {
            if (occupied && !confirmOverwrite()) {
                return;
            }
            doSave(slot);
        });

        row.getChildren().addAll(title, details, save);
        return row;
    }

    private void doSave(SaveSlot slot) {
        try {
            GameSnapshot snap = GameSnapshot.fromGameManager(
                    GameController.sharedGameManager,
                    GameController.sharedRoundNumber);
            new PersistenceService(slot).save(snap);
        } catch (Exception ex) {
            System.err.println("Manual save failed: " + ex.getMessage());
        }
        Stage stage = (Stage) slotsContainer.getScene().getWindow();
        Navigation.navigate(stage, "/ui/game.fxml");
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

    private boolean confirmOverwrite() {
        Stage dialog = new Stage();
        dialog.initOwner(slotsContainer.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(msg("save.slot.overwrite.title"));
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);
        root.setMinWidth(380);
        root.getStyleClass().add("dialog-root");

        Label body = new Label(msg("save.slot.overwrite.body"));
        body.setWrapText(true);
        body.setMaxWidth(360);
        body.getStyleClass().add("dialog-header");
        root.getChildren().add(body);

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

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/ui/menu.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
        return result[0];
    }
}
