package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.stage.Stage;

import java.util.Locale;

public class SettingsController {

    @FXML private RadioButton itRadio;
    @FXML private RadioButton enRadio;

    private Stage dialogStage;
    private Runnable onApplyCallback;

    @FXML
    private void initialize() {
        Locale current = MessageService.getInstance().getLocale();
        if (Locale.ENGLISH.getLanguage().equals(current.getLanguage())) {
            enRadio.setSelected(true);
        } else {
            itRadio.setSelected(true);
        }
    }

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setOnApply(Runnable callback) {
        this.onApplyCallback = callback;
    }

    @FXML
    private void onApply() {
        Locale chosen = enRadio.isSelected() ? Locale.ENGLISH : Locale.ITALIAN;
        MessageService.getInstance().setLocale(chosen);
        dialogStage.close();
        if (onApplyCallback != null) {
            onApplyCallback.run();
        }
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }
}
