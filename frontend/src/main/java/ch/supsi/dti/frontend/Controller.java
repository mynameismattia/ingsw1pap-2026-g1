package ch.supsi.dti.frontend;

import ch.supsi.dti.backend.service.FileService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.nio.file.Path;

public class Controller {

    @FXML private TextArea textArea;
    @FXML private Label statusLabel;

    private final FileService fileService = new FileService();

    @FXML
    private void onSaveClicked() {
        try {
            Path out = Path.of("saved", "textarea.txt");
            fileService.saveUtf8(out, textArea.getText());
            statusLabel.setText("Saved to: " + out.toAbsolutePath());
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }
}