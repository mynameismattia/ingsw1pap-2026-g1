// Popup impostazioni: lingua (it/en), volumi (musica, effetti), modalità display (windowed/fullscreen).
// Costruito programmaticamente in Java, niente FXML.

package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.frontend.service.SoundManager;
import ch.supsi.dti.frontend.view.Icons;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;

public final class SettingsDialog {

    private SettingsDialog() {}

    public static void show(Stage stage, Runnable onLocaleChange) {
        if (stage == null) return;
        Scene scene = stage.getScene();
        if (scene == null) return;
        Parent root = scene.getRoot();
        if (!(root instanceof StackPane stack)) return;

        for (Node n : stack.getChildren()) {
            if ("settings-overlay".equals(n.getId())) return;
        }

        SoundManager sm = SoundManager.getInstance();
        Locale initialLocale = MessageService.getInstance().getLocale();

        StackPane overlay = new StackPane();
        overlay.setId("settings-overlay");
        overlay.getStyleClass().add("overlay-backdrop");

        Runnable closeOverlay = () -> {
            boolean localeChanged = !MessageService.getInstance().getLocale().equals(initialLocale);
            close(stack, overlay);
            if (localeChanged && onLocaleChange != null) {
                onLocaleChange.run();
            }
        };

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            VBox newCard = buildCard(MessageService.getInstance(), sm, stage, closeOverlay, rebuild[0]);
            overlay.getChildren().setAll(newCard);
        };

        overlay.setOnMouseClicked(e -> {

            if (e.getTarget() == overlay) {
                closeOverlay.run();
            }
        });

        VBox card = buildCard(MessageService.getInstance(), sm, stage, closeOverlay, rebuild[0]);
        overlay.getChildren().add(card);

        stack.getChildren().add(overlay);

        javafx.event.EventHandler<KeyEvent> escFilter = e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                closeOverlay.run();
                e.consume();
            }
        };
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escFilter);
        overlay.sceneProperty().addListener((obs, oldS, newS) -> {
            if (newS == null && oldS != null) {
                oldS.removeEventFilter(KeyEvent.KEY_PRESSED, escFilter);
            }
        });
        Platform.runLater(overlay::requestFocus);
    }

    private static void close(StackPane stack, StackPane overlay) {
        stack.getChildren().remove(overlay);
    }

    private static VBox buildCard(MessageService msg, SoundManager sm, Stage stage,
                                  Runnable closeOverlay, Runnable rebuild) {
        VBox card = new VBox(18);
        card.getStyleClass().add("overlay-card");
        card.setMaxWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(22));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(msg.getMessage("settings.title"));
        title.getStyleClass().add("dialog-header");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> closeOverlay.run());
        header.getChildren().addAll(title, spacer, close);

        VBox language = buildLanguageSection(msg, rebuild);

        VBox audio = buildAudioSection(msg, sm);

        VBox display = buildDisplaySection(msg, stage, rebuild);

        Button done = new Button(msg.getMessage("settings.close"));
        done.getStyleClass().add("primary-button");
        done.setMaxWidth(Double.MAX_VALUE);
        done.setOnAction(e -> closeOverlay.run());

        card.getChildren().addAll(
                header,
                divider(),
                language,
                divider(),
                audio,
                divider(),
                display,
                divider(),
                done
        );
        return card;
    }

    private static Region divider() {
        Region d = new Region();
        d.getStyleClass().add("settings-divider");
        d.setMinHeight(1);
        d.setPrefHeight(1);
        return d;
    }

    private static VBox buildLanguageSection(MessageService msg, Runnable rebuild) {
        VBox section = new VBox(10);

        Label heading = new Label(msg.getMessage("settings.section.language"));
        heading.getStyleClass().add("settings-section-label");

        Locale current = msg.getLocale();
        HBox pills = new HBox(8);
        pills.setAlignment(Pos.CENTER_LEFT);
        pills.getChildren().addAll(
                languagePill(msg.getMessage("settings.language.it"),
                        Locale.ITALIAN, current, rebuild),
                languagePill(msg.getMessage("settings.language.en"),
                        Locale.ENGLISH, current, rebuild)
        );

        section.getChildren().addAll(heading, pills);
        return section;
    }

    private static Button languagePill(String label, Locale locale, Locale current, Runnable rebuild) {
        Button b = new Button(label);
        b.getStyleClass().add("mode-pill");
        boolean active = locale.getLanguage().equals(current.getLanguage());
        if (active) {
            b.getStyleClass().add("mode-pill-active");
        }
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.SoundEvent.CLICK);
            if (!active) {
                MessageService.getInstance().setLocale(locale);
                if (rebuild != null) rebuild.run();
            }
        });
        return b;
    }

    private static VBox buildAudioSection(MessageService msg, SoundManager sm) {
        VBox section = new VBox(14);

        Label heading = new Label(msg.getMessage("settings.section.audio"));
        heading.getStyleClass().add("settings-section-label");

        HBox masterRow = new HBox(12);
        masterRow.setAlignment(Pos.CENTER_LEFT);
        Label masterIcon = new Label(Icons.SPEAKER);
        masterIcon.getStyleClass().addAll("settings-icon", Icons.STYLE_CLASS);
        Label masterText = new Label(msg.getMessage(
                sm.isMuted() ? "settings.audio.muted" : "settings.audio.master"));
        masterText.getStyleClass().add("settings-row-label");
        Region rowSpacer = new Region();
        HBox.setHgrow(rowSpacer, Priority.ALWAYS);

        ToggleButton masterToggle = new ToggleButton();
        masterToggle.getStyleClass().add("settings-master-toggle");
        masterToggle.setSelected(!sm.isMuted());
        applyMasterToggleText(masterToggle);
        masterToggle.selectedProperty().addListener((obs, oldV, newV) -> {
            sm.setMuted(!newV);
            masterText.setText(msg.getMessage(
                    sm.isMuted() ? "settings.audio.muted" : "settings.audio.master"));
            applyMasterToggleText(masterToggle);
        });
        masterRow.getChildren().addAll(masterIcon, masterText, rowSpacer, masterToggle);

        VBox sfxRow = sliderRow(Icons.SFX, msg.getMessage("settings.volume"),
                sm.getVolume(), sm::setVolume);

        VBox musicRow = sliderRow(Icons.MUSIC, msg.getMessage("settings.musicVolume"),
                sm.getMusicVolume(), sm::setMusicVolume);

        section.getChildren().addAll(heading, masterRow, sfxRow, musicRow);
        return section;
    }

    private static VBox buildDisplaySection(MessageService msg, Stage stage, Runnable rebuild) {
        VBox section = new VBox(10);

        Label heading = new Label(msg.getMessage("settings.section.display"));
        heading.getStyleClass().add("settings-section-label");

        DisplayMode current = DisplayMode.current(stage);
        HBox pills = new HBox(8);
        pills.setAlignment(Pos.CENTER_LEFT);
        pills.getChildren().addAll(
                displayPill(msg.getMessage("settings.display.windowed"),
                        DisplayMode.WINDOWED, current, stage, rebuild),
                displayPill(msg.getMessage("settings.display.fullscreen"),
                        DisplayMode.FULLSCREEN, current, stage, rebuild)
        );

        section.getChildren().addAll(heading, pills);
        return section;
    }

    private static Button displayPill(String label, DisplayMode mode, DisplayMode current,
                                      Stage stage, Runnable rebuild) {
        Button b = new Button(label);
        b.getStyleClass().add("mode-pill");
        boolean active = mode == current;
        if (active) {
            b.getStyleClass().add("mode-pill-active");
        }
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.SoundEvent.CLICK);
            if (!active) {

                if (mode == DisplayMode.FULLSCREEN) {
                    stage.setFullScreenExitHint(
                            MessageService.getInstance().getMessage("settings.display.fullscreen.hint"));
                }
                mode.apply(stage);
                if (mode == DisplayMode.FULLSCREEN) {
                    stage.setFullScreenExitHint("");
                }
                mode.save();

                if (rebuild != null) rebuild.run();
            }
        });
        return b;
    }

    private static void applyMasterToggleText(ToggleButton t) {
        t.setText(t.isSelected() ? "ON" : "OFF");
        if (t.isSelected()) {
            t.getStyleClass().remove("settings-master-toggle-off");
        } else if (!t.getStyleClass().contains("settings-master-toggle-off")) {
            t.getStyleClass().add("settings-master-toggle-off");
        }
    }

    private static VBox sliderRow(String iconText, String label, double initial,
                                  java.util.function.DoubleConsumer onChange) {
        Label icon = new Label(iconText);
        icon.getStyleClass().addAll("settings-icon", Icons.STYLE_CLASS);
        Label name = new Label(label);
        name.getStyleClass().add("settings-row-label");

        Slider slider = new Slider(0, 1, initial);
        slider.setBlockIncrement(0.05);
        HBox.setHgrow(slider, Priority.ALWAYS);
        slider.getStyleClass().add("settings-slider");

        Label pct = new Label(formatPct(slider.getValue()));
        pct.getStyleClass().add("settings-pct");
        pct.setMinWidth(48);

        Runnable repaintTrack = () -> {
            double p = slider.getValue() * 100.0;
            var track = slider.lookup(".track");
            if (track != null) {
                track.setStyle(String.format(Locale.US,
                        "-fx-background-color: linear-gradient(to right, " +
                        "#3b82f6 0%%, #3b82f6 %.2f%%, #353a47 %.2f%%, #353a47 100%%);",
                        p, p));
            }
        };
        slider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = newV.doubleValue();
            onChange.accept(v);
            pct.setText(formatPct(v));
            repaintTrack.run();
        });
        slider.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) Platform.runLater(repaintTrack);
        });

        HBox top = new HBox(10, icon, name);
        top.setAlignment(Pos.CENTER_LEFT);
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        top.getChildren().add(topSpacer);
        top.getChildren().add(pct);

        VBox box = new VBox(4, top, slider);
        return box;
    }

    private static String formatPct(double v) {
        return Math.round(v * 100) + "%";
    }
}
