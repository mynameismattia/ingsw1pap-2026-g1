package ch.supsi.dti.backend.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceTest {

    @Test
    void defaultLocaleIsItalianAndLoadsItalianStrings() {
        MessageService svc = MessageService.getInstance();
        svc.setLocale(Locale.ITALIAN);
        assertEquals(Locale.ITALIAN, svc.getLocale());
        assertEquals("Carta", svc.getMessage("game.button.hit"));
    }

    @Test
    void switchToEnglishLoadsEnglishStrings() {
        MessageService svc = MessageService.getInstance();
        svc.setLocale(Locale.ENGLISH);
        assertEquals("Hit", svc.getMessage("game.button.hit"));
    }

    @Test
    void missingKeyReturnsMarkedString() {
        MessageService svc = MessageService.getInstance();
        svc.setLocale(Locale.ITALIAN);
        assertEquals("???does.not.exist???", svc.getMessage("does.not.exist"));
    }

    @Test
    void parametricMessageFormatsArgs() {
        MessageService svc = MessageService.getInstance();
        svc.setLocale(Locale.ITALIAN);
        assertEquals("Benvenuto, Batman!", svc.getMessage("game.message.welcome", "Batman"));
    }
}
