package ch.supsi.dti.frontend.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class WindowControls {

    private static final String INSTALLED_KEY = "windowControlsInstalled";

    private WindowControls() {}

    public static void attach(Scene scene, Stage stage) {
        if (scene == null || stage == null) return;
        Platform.runLater(() -> install(scene, stage));
    }

    private static void install(Scene scene, Stage stage) {
        Node titlebar = scene.lookup(".card-titlebar");
        if (titlebar instanceof HBox hbox && hbox.getProperties().get(INSTALLED_KEY) == null) {
            hbox.getChildren().addAll(
                    makeBtn("─", e -> stage.setIconified(true)),
                    makeBtn("✕", e -> Platform.exit()));
            hbox.getProperties().put(INSTALLED_KEY, true);
            return;
        }

        // La licenza non ha card-titlebar: aggancio un overlay floating in alto a destra.
        if (scene.getRoot() instanceof StackPane stack
                && stack.getProperties().get(INSTALLED_KEY) == null) {
            HBox floating = new HBox(4,
                    makeBtn("─", e -> stage.setIconified(true)),
                    makeBtn("✕", e -> Platform.exit()));
            floating.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            floating.setPickOnBounds(false);
            StackPane.setAlignment(floating, Pos.TOP_RIGHT);
            StackPane.setMargin(floating, new Insets(8));
            stack.getChildren().add(floating);
            stack.getProperties().put(INSTALLED_KEY, true);
        }
    }

    private static Button makeBtn(String text, EventHandler<ActionEvent> handler) {
        Button b = new Button(text);
        b.getStyleClass().addAll("icon-button", "no-click-sfx");
        b.setOnAction(handler);
        return b;
    }
}
