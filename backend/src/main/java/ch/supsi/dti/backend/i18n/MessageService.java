// Carica i ResourceBundle delle traduzioni (it/en) dal classpath /i18n/ e fornisce getMessage(key, args) per la sostituzione di argomenti tipo {0}.
// Singleton globale. Cambiare lingua chiama setLocale() che ricarica il bundle.

package ch.supsi.dti.backend.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class MessageService {

    private static final String BUNDLE_BASENAME = "i18n.messages";
    private static final Locale DEFAULT_LOCALE = Locale.ITALIAN;

    private static MessageService instance;

    private Locale currentLocale;
    private ResourceBundle bundle;

    private MessageService() {
        setLocale(DEFAULT_LOCALE);
    }

    public static synchronized MessageService getInstance() {
        if (instance == null) {
            instance = new MessageService();
        }
        return instance;
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        this.bundle = ResourceBundle.getBundle(BUNDLE_BASENAME, locale);
    }

    public Locale getLocale() {
        return currentLocale;
    }

    public ResourceBundle getBundle() {
        return bundle;
    }

    public String getMessage(String key) {
        // 1. Provo a leggere la chiave dal bundle della lingua corrente.
        // 2. Se la chiave manca, restituisco "???key???" — placeholder visibile a video che segnala il problema senza far crashare la UI.
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "???" + key + "???";
        }
    }

    public String getMessage(String key, Object... args) {
        // 1. Recupero il pattern grezzo (es. "Hai vinto {0}$ in {1} mani!").
        String pattern = getMessage(key);
        // 2. Se è un placeholder ???key???, niente format: ritorno tale e quale.
        if (pattern.startsWith("???")) {
            return pattern;
        }
        // 3. Sostituisco gli argomenti {0}, {1}, ... usando MessageFormat (java.text).
        return MessageFormat.format(pattern, args);
    }
}
