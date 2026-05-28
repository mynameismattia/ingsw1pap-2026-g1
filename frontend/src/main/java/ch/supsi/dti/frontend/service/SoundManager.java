// Gestisce tutti i suoni e la musica del gioco (carte, chip, click, vittoria, sconfitta, fine round, musica menu/gioco).
// Pre-carica gli AudioClip all'avvio per evitare lag al primo play. Singleton.

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

    public enum MusicTrack {
        MENU("/audio/menu_music.wav"),
        GAME("/audio/game_music.wav");

        private final String resourcePath;
        MusicTrack(String resourcePath) { this.resourcePath = resourcePath; }
        public String getResourcePath() { return resourcePath; }
    }

    private static final String SCENE_MARKER = "clickSfxInstalled";

    private static final String NO_CLICK_SFX_CLASS = "no-click-sfx";

    private static SoundManager instance;

    private final Map<SoundEvent, AudioClip> registry = new EnumMap<>(SoundEvent.class);
    private final Map<MusicTrack, MediaPlayer> musicPlayers = new EnumMap<>(MusicTrack.class);

    private final Map<MediaPlayer, Timeline> fades = new HashMap<>();
    private MusicTrack currentTrack;
    private boolean muted = false;
    private boolean preloaded = false;
    private double volume = 0.5;
    private double musicVolume = 0.4;
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
        // 1. Idempotente: se ho già pre-caricato, non rifaccio (evita di creare due AudioClip per lo stesso file).
        if (preloaded) {
            return;
        }

        // 2. Per ogni SoundEvent (chip, card, click, ...) creo un AudioClip pronto al play immediato.
        //    Se il .wav manca dal classpath, log e continuo: l'app gira lo stesso, solo senza quel suono.
        for (SoundEvent event : SoundEvent.values()) {
            URL url = getClass().getResource(event.getResourcePath());
            if (url == null) {
                System.err.println("SoundManager: missing audio resource " + event.getResourcePath());
                continue;
            }
            registry.put(event, new AudioClip(url.toExternalForm()));
        }

        // 3. Per ogni MusicTrack (menu, game) creo un MediaPlayer in loop infinito al volume corrente.
        //    Diverso da AudioClip: MediaPlayer supporta fade/pause/loop, AudioClip è solo "fire and forget".
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

        // 4. Flag preloaded a true così le chiamate successive sono no-op.
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

    public void setMuted(boolean muted) {
        this.muted = muted;
        setMusicMuted(muted);
    }

    public double getVolume() { return volume; }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public void playMusic(MusicTrack track) {
        // 1. Se sto già suonando questa traccia, non faccio nulla (evita il "saltino" quando navighi nella stessa area).
        if (track == currentTrack) {
            return;
        }

        // 2. Fade-out della traccia precedente (se c'è) e stop al termine della transizione.
        MediaPlayer prev = (currentTrack != null) ? musicPlayers.get(currentTrack) : null;
        if (prev != null) {
            fadeVolume(prev, 0.0, prev::stop);
        }

        // 3. Aggiorno il "current". Se la nuova track è null (es. schermata di risultati silenziosa) mi fermo qui.
        currentTrack = track;
        if (track == null) {
            return;
        }

        // 4. Recupero il MediaPlayer per la nuova traccia e parto in fade-in dal volume 0 al target.
        MediaPlayer next = musicPlayers.get(track);
        if (next == null) {
            return;
        }
        double target = musicMuted ? 0.0 : musicVolume;
        next.setVolume(0.0);
        next.play();
        fadeVolume(next, target, null);
    }

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
        // 1. Idempotenza: uso una marker property sulla Scene per non installare il filter due volte.
        if (scene == null || scene.getProperties().containsKey(SCENE_MARKER)) {
            return;
        }
        // 2. Event filter globale: ogni ActionEvent (click su Button, ecc.) fa partire il click sound.
        //    Eccezione: i nodi marcati con la classe "no-click-sfx" lo saltano (es. chip che hanno il loro CHIP sound).
        scene.addEventFilter(ActionEvent.ACTION, e -> {
            if (e.getTarget() instanceof Node node && node.getStyleClass().contains(NO_CLICK_SFX_CLASS)) {
                return;
            }
            getInstance().play(SoundEvent.CLICK);
        });
        // 3. Marco la Scene come "click sfx installato" così attachClickSfx successivi sono no-op.
        scene.getProperties().put(SCENE_MARKER, Boolean.TRUE);
    }

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
