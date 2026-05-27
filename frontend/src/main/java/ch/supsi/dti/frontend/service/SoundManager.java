package ch.supsi.dti.frontend.service;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;


public class SoundManager {

    public enum SoundEvent {
        CHIP("/audio/chip.wav"),
        CARD("/audio/card.wav"),
        CARD_DEALT("/audio/card_dealt.wav"),
        WIN("/audio/win.wav"),
        LOSE("/audio/lose.wav"),
        ROUND_OVER("/audio/round_over.wav"),
        CLICK("/audio/click.wav");

        private final String resourcePath;
        SoundEvent(String resourcePath) { this.resourcePath = resourcePath; }
        public String getResourcePath() { return resourcePath; }
    }

    /** Looping background tracks played via MediaPlayer (heavier than AudioClip). */
    public enum MusicTrack {
        MENU("/audio/menu_music.wav"),
        GAME("/audio/game_music.wav");

        private final String resourcePath;
        MusicTrack(String resourcePath) { this.resourcePath = resourcePath; }
        public String getResourcePath() { return resourcePath; }
    }

    private static final String SCENE_MARKER = "clickSfxInstalled";
    // I bottoni con questa style class hanno un SFX dedicato (chip, hit, ...) → niente CLICK generico sopra.
    private static final String NO_CLICK_SFX_CLASS = "no-click-sfx";

    private static SoundManager instance;

    private final Map<SoundEvent, AudioClip> registry = new EnumMap<>(SoundEvent.class);
    private final Map<MusicTrack, MediaPlayer> musicPlayers = new EnumMap<>(MusicTrack.class);
    /** In-flight volume animations, keyed by the player they target. Cancelled
     *  on re-entry so rapid scene switches don't leave stale tweens fighting. */
    private final Map<MediaPlayer, Timeline> fades = new HashMap<>();
    private MusicTrack currentTrack;        // null = silent
    private boolean muted = false;
    private boolean preloaded = false;
    private double volume = 0.5;
    private double musicVolume = 0.4;       // independent of SFX volume
    private boolean musicMuted = false;
    private static final Duration MUSIC_FADE = Duration.millis(400);

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }

    public void preload() {
        if (preloaded) {
            return;
        }
        for (SoundEvent event : SoundEvent.values()) {
            URL url = getClass().getResource(event.getResourcePath());
            if (url == null) {
                System.err.println("SoundManager: missing audio resource " + event.getResourcePath());
                continue;
            }
            registry.put(event, new AudioClip(url.toExternalForm()));
        }
        for (MusicTrack track : MusicTrack.values()) {
            URL url = getClass().getResource(track.getResourcePath());
            if (url == null) {
                System.err.println("SoundManager: missing music resource " + track.getResourcePath());
                continue;
            }
            MediaPlayer player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(musicVolume);
            musicPlayers.put(track, player);
        }
        preloaded = true;
    }

    public void play(SoundEvent event) {
        if (muted || volume <= 0.0) {
            return;
        }
        AudioClip clip = registry.get(event);
        if (clip != null) {
            clip.play(volume);
        }
    }

    /**
     * Plays a SFX cutting off any in-flight playback of the same clip.
     * Useful for rapid-fire sounds (e.g. the per-card deal cue) where AudioClip's
     * default overlapping behaviour would stack copies on top of each other.
     */
    public void playInterrupting(SoundEvent event) {
        if (muted || volume <= 0.0) {
            return;
        }
        AudioClip clip = registry.get(event);
        if (clip != null) {
            clip.stop();
            clip.play(volume);
        }
    }

    public boolean isMuted() { return muted; }

    /** Master kill-switch: silences both SFX and music. */
    public void setMuted(boolean muted) {
        this.muted = muted;
        setMusicMuted(muted);
    }

    public double getVolume() { return volume; }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    // ── Music ───────────────────────────────────────────────────────

    /**
     * Switches the looping background track. {@code null} stops music entirely.
     * No-op when the requested track is already current — so navigating between
     * menu-family scenes does not restart the song. Outgoing and incoming
     * tracks crossfade over {@link #MUSIC_FADE} so transitions aren't abrupt.
     */
    public void playMusic(MusicTrack track) {
        if (track == currentTrack) {
            return;
        }
        MediaPlayer prev = (currentTrack != null) ? musicPlayers.get(currentTrack) : null;
        if (prev != null) {
            fadeVolume(prev, 0.0, prev::stop);
        }
        currentTrack = track;
        if (track == null) {
            return;
        }
        MediaPlayer next = musicPlayers.get(track);
        if (next == null) {
            return;
        }
        double target = musicMuted ? 0.0 : musicVolume;
        next.setVolume(0.0);
        next.play();
        fadeVolume(next, target, null);
    }

    /**
     * Animates {@code player.volumeProperty()} towards {@code target} over
     * {@link #MUSIC_FADE}. Any in-flight fade for this player is cancelled
     * first so back-to-back switches don't stack.
     */
    private void fadeVolume(MediaPlayer player, double target, Runnable onFinished) {
        Timeline existing = fades.remove(player);
        if (existing != null) {
            existing.stop();
        }
        Timeline t = new Timeline(new KeyFrame(MUSIC_FADE,
                new KeyValue(player.volumeProperty(), target)));
        t.setOnFinished(e -> {
            fades.remove(player);
            if (onFinished != null) onFinished.run();
        });
        fades.put(player, t);
        t.play();
    }

    public double getMusicVolume() { return musicVolume; }

    public void setMusicVolume(double v) {
        this.musicVolume = Math.max(0.0, Math.min(1.0, v));
        if (currentTrack != null && !musicMuted) {
            MediaPlayer p = musicPlayers.get(currentTrack);
            if (p != null) {
                // Cancel any in-flight fade so the slider feels live.
                Timeline existing = fades.remove(p);
                if (existing != null) existing.stop();
                p.setVolume(musicVolume);
            }
        }
    }

    public boolean isMusicMuted() { return musicMuted; }

    public void setMusicMuted(boolean musicMuted) {
        this.musicMuted = musicMuted;
        if (currentTrack != null) {
            MediaPlayer p = musicPlayers.get(currentTrack);
            if (p != null) {
                Timeline existing = fades.remove(p);
                if (existing != null) existing.stop();
                p.setVolume(musicMuted ? 0.0 : musicVolume);
            }
        }
    }

    public static void attachClickSfx(Scene scene) {
        if (scene == null || scene.getProperties().containsKey(SCENE_MARKER)) {
            return;
        }
        scene.addEventFilter(ActionEvent.ACTION, e -> {
            // getSource() lungo la dispatch chain diventa la Scene; usare getTarget() per il nodo originale.
            if (e.getTarget() instanceof Node node && node.getStyleClass().contains(NO_CLICK_SFX_CLASS)) {
                return;
            }
            getInstance().play(SoundEvent.CLICK);
        });
        scene.getProperties().put(SCENE_MARKER, Boolean.TRUE);
    }

    // Lo Spinner non emette ActionEvent — il filtro scene-wide non lo intercetta.
    // Uso un flag su MOUSE_PRESSED per distinguere variazioni user-initiated da quelle programmatiche
    // (es. quando applyModeStyle() reimposta i valori al cambio di modalità).
    public static void attachSpinnerClick(Spinner<?> spinner) {
        if (spinner == null) {
            return;
        }
        Object key = new Object();
        spinner.addEventFilter(MouseEvent.MOUSE_PRESSED,
                e -> spinner.getProperties().put(key, Boolean.TRUE));
        spinner.addEventFilter(MouseEvent.MOUSE_RELEASED,
                e -> spinner.getProperties().remove(key));
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (spinner.getProperties().remove(key) != null) {
                getInstance().play(SoundEvent.CLICK);
            }
        });
    }
}
