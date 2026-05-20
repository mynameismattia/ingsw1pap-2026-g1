package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

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

        // Anchor the menu just below the button.
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private static MenuItem buildItem(String label, Locale locale, Locale current, Runnable onChanged) {
        boolean active = locale.getLanguage().equals(current.getLanguage());
        MenuItem item = new MenuItem(active ? "✓  " + label : "      " + label);
        item.getStyleClass().add("lang-dropdown-item");
        if (active) {
            item.getStyleClass().add("lang-dropdown-item-active");
        }
        item.setOnAction(e -> {
            if (!active) {
                MessageService.getInstance().setLocale(locale);
                if (onChanged != null) onChanged.run();
            }
        });
        return item;
    }
}
