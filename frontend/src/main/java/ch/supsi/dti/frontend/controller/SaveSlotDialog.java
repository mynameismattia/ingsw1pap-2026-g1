package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.service.GameSnapshot;
import ch.supsi.dti.backend.service.PersistenceService;
import ch.supsi.dti.backend.service.SaveSlot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Modal dialog that lets the player pick one of the 3 manual save slots.
 * The autosave slot is intentionally excluded — it is reserved for the
 * automatic round-over save.
 */
public final class SaveSlotDialog {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private SaveSlotDialog() {}

    public static Optional<SaveSlot> show(Window owner) {
        Stage dialog = new Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(msg("save.dialog.title"));
        dialog.setResizable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label header = new Label(msg("save.dialog.header"));
        header.getStyleClass().add("field-label");
        root.getChildren().add(header);

        final SaveSlot[] picked = {null};

        for (SaveSlot slot : SaveSlot.manualSlots()) {
            root.getChildren().add(buildRow(slot, dialog, picked));
        }

        Button cancel = new Button(msg("common.cancel"));
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(e -> dialog.close());
        HBox actions = new HBox(10, cancel);
        actions.setAlignment(Pos.CENTER);
        root.getChildren().add(actions);

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return Optional.ofNullable(picked[0]);
    }

    private static HBox buildRow(SaveSlot slot, Stage dialog, SaveSlot[] picked) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.getStyleClass().add("row");
        row.setMinWidth(380);

        Label name = new Label(msg("load.slot.n", manualNumber(slot)));
        name.getStyleClass().add("field-label");
        name.setMinWidth(80);

        PersistenceService ps = new PersistenceService(slot);
        VBox details = new VBox(2);
        HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);

        Optional<GameSnapshot> existing;
        try {
            existing = ps.load();
        } catch (Exception ex) {
            existing = Optional.empty();
        }
        boolean occupied = existing.isPresent();
        if (occupied) {
            GameSnapshot snap = existing.get();
            details.getChildren().add(new Label(msg("load.slot.round", snap.currentRoundNumber())));
            ps.lastModified().ifPresent(t -> {
                Label d = new Label(DATE_FMT.format(t));
                d.getStyleClass().add("badge-soon");
                details.getChildren().add(d);
            });
        } else {
            Label empty = new Label(msg("load.slot.empty"));
            empty.getStyleClass().add("badge-soon");
            details.getChildren().add(empty);
        }

        Button pick = new Button(occupied ? msg("save.action.overwrite") : msg("save.action.saveHere"));
        pick.getStyleClass().add(occupied ? "danger-button" : "primary-button");
        pick.setOnAction(e -> {
            if (occupied && !confirmOverwrite(dialog)) {
                return;
            }
            picked[0] = slot;
            dialog.close();
        });

        row.getChildren().addAll(name, details, pick);
        return row;
    }

    private static int manualNumber(SaveSlot slot) {
        return switch (slot) {
            case SLOT_1 -> 1;
            case SLOT_2 -> 2;
            case SLOT_3 -> 3;
            default -> 0;
        };
    }

    private static boolean confirmOverwrite(Stage owner) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(msg("save.slot.overwrite.title"));
        dialog.setResizable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label body = new Label(msg("save.slot.overwrite.body"));
        body.setWrapText(true);
        body.setMaxWidth(360);
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

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return result[0];
    }

    private static String msg(String key, Object... args) {
        return MessageService.getInstance().getMessage(key, args);
    }
}
