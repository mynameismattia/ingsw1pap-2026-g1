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

    public static synchronized MessageService getInstance() { //il sincronyzed da quanto ho capito e' per evitare che due thread chiamino allo stesso momento il metodo rompendo tutto il ciclo, java fx e' single threat ma u know flexing
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
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "???" + key + "???";
        }
    }

    public String getMessage(String key, Object... args) {
        String pattern = getMessage(key);
        if (pattern.startsWith("???")) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }
}