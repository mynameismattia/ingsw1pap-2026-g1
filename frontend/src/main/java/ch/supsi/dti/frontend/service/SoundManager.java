package ch.supsi.dti.frontend.service;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Spinner;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;


public class SoundManager {

    public enum SoundEvent {
        CHIP("/audio/chip.wav"),
        CARD("/audio/card.wav"),
        WIN("/audio/win.wav"),
        LOSE("/audio/lose.wav"),
        ROUND_OVER("/audio/round_over.wav"),
        CLICK("/audio/click.wav");

        private final String resourcePath;
        SoundEvent(String resourcePath) { this.resourcePath = resourcePath; }
        public String getResourcePath() { return resourcePath; }
    }

    private static final String SCENE_MARKER = "clickSfxInstalled";
    // I bottoni con questa style class hanno un SFX dedicato (chip, hit, ...) → niente CLICK generico sopra.
    private static final String NO_CLICK_SFX_CLASS = "no-click-sfx";

    private static SoundManager instance;

    private final Map<SoundEvent, AudioClip> registry = new EnumMap<>(SoundEvent.class);
    private boolean muted = false;
    private boolean preloaded = false;
    private double volume = 1.0;

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

    public boolean isMuted() { return muted; }

    public void setMuted(boolean muted) { this.muted = muted; }

    public double getVolume() { return volume; }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
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
