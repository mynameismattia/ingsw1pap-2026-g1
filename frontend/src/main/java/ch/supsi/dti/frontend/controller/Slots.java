// Helper condiviso fra le schermate Salva e Carica: formato data, lookup i18n, etichetta dello slot
// e lo scheletro della riga-slot (titolo + box dettagli). I dettagli e i bottoni d'azione li riempie il chiamante,
// perché Salva e Carica mostrano contenuti/azioni diversi.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.service.SaveSlot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class Slots {

    private Slots() {}

    static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    static String msg(String key, Object... args) {
        return MessageService.getInstance().getMessage(key, args);
    }

    static String label(SaveSlot slot) {
        return switch (slot) {
            case AUTO   -> msg("load.slot.auto");
            case SLOT_1 -> msg("load.slot.n", 1);
            case SLOT_2 -> msg("load.slot.n", 2);
            case SLOT_3 -> msg("load.slot.n", 3);
        };
    }

    static Row base(SaveSlot slot) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("row");
        row.setPadding(new Insets(10));

        Label title = new Label(label(slot));
        title.getStyleClass().add("slot-title");
        title.setMinWidth(140);

        VBox details = new VBox(2);
        HBox.setHgrow(details, Priority.ALWAYS);

        row.getChildren().addAll(title, details);
        return new Row(row, details);
    }

    record Row(HBox row, VBox details) {}
}
