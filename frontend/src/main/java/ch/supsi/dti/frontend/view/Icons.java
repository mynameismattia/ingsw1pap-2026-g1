// Glyph icona presi dal font bundlato JetBrainsMono Nerd Font (monocromatici), usati al posto delle emoji a colori
// che JavaFX non renderizza. Vanno applicati a un nodo con la style class STYLE_CLASS ("nf-icon").

package ch.supsi.dti.frontend.view;

public final class Icons {

    private Icons() {}

    public static final String STYLE_CLASS = "nf-icon";

    private static String g(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    public static final String BOT     = g(0xF06A9);
    public static final String MEDAL   = g(0xF0987);
    public static final String SAVE    = g(0xF0C7);
    public static final String KEY     = g(0xF084);
    public static final String SPEAKER = g(0xF028);
    public static final String SFX     = g(0xF11B);
    public static final String MUSIC   = g(0xF001);
}
