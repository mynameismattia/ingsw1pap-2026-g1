package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import ch.supsi.dti.frontend.service.SoundManager.SoundEvent;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Drops a small language picker from a topbar button instead of opening a
 * modal dialog. Each menu item applies the new locale immediately and then
 * fires the {@code onChanged} callback so the caller can reload the current
 * scene with the refreshed resource bundle.
 */
public final class LanguageDropdown {

    private LanguageDropdown() {}

    public static void show(Button anchor, Runnable onChanged) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("lang-dropdown");

        Locale current = MessageService.getInstance().getLocale();
        MessageService msg = MessageService.getInstance();

        menu.getItems().add(buildItem(msg.getMessage("settings.language.it"), Locale.ITALIAN, current, onChanged));
        menu.getItems().add(buildItem(msg.getMessage("settings.language.en"), Locale.ENGLISH, current, onChanged));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(buildVolumeItem(msg));

        // Anchor the menu just below the button.
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private static CustomMenuItem buildVolumeItem(MessageService msg) {
        Label header = new Label(msg.getMessage("settings.volume"));
        header.getStyleClass().add("lang-dropdown-header");

        Slider slider = new Slider(0, 1, SoundManager.getInstance().getVolume());
        slider.setBlockIncrement(0.05);
        slider.setPrefWidth(160);
        HBox.setHgrow(slider, Priority.ALWAYS);

        Label value = new Label(formatPct(slider.getValue()));
        value.getStyleClass().add("lang-dropdown-volume-value");

        slider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = newV.doubleValue();
            SoundManager.getInstance().setVolume(v);
            value.setText(formatPct(v));
        });

        HBox row = new HBox(10, slider, value);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(header, row);
        box.getStyleClass().add("lang-dropdown-volume");

        CustomMenuItem item = new CustomMenuItem(box);
        item.getStyleClass().add("custom-volume-item");
        // Trascinare lo slider non deve chiudere il menu.
        item.setHideOnClick(false);
        return item;
    }

    private static String formatPct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static MenuItem buildItem(String label, Locale locale, Locale current, Runnable onChanged) {
        boolean active = locale.getLanguage().equals(current.getLanguage());
        MenuItem item = new MenuItem(active ? "✓  " + label : "      " + label);
        item.getStyleClass().add("lang-dropdown-item");
        if (active) {
            item.getStyleClass().add("lang-dropdown-item-active");
        }
        item.setOnAction(e -> {
            SoundManager.getInstance().play(SoundEvent.CLICK);
            if (!active) {
                MessageService.getInstance().setLocale(locale);
                if (onChanged != null) onChanged.run();
            }
        });
        return item;
    }
}
