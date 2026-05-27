package ch.supsi.dti.frontend.controller;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.i18n.MessageService;
import ch.supsi.dti.backend.model.BotNames;
import ch.supsi.dti.backend.model.Player;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multiplayer lobby as an in-scene overlay (same visual pattern as
 * {@link SettingsDialog} and the in-game history dialog: dimmed backdrop +
 * centred card). Replaces the old separate {@code playernames.fxml} scene
 * which left a vast empty fullscreen background. The user stays on the menu
 * underneath the dim, types names, confirms — only then do we navigate.
 */
public final class PlayerNamesDialog {

    private PlayerNamesDialog() {}

    /**
     * Shows the lobby overlay over the current scene's StackPane root.
     *
     * @param stage         the active stage; we look up its scene root to attach
     * @param humanCount    1–4 humans
     * @param botCount      0–4 bots
     * @param balance       starting balance applied to every player
     * @param onCancel      fired when the user cancels (Esc / backdrop / back button) —
     *                      use this to re-enable the originating button etc.
     */
    public static void show(Stage stage, int humanCount, int botCount, int balance,
                            Runnable onCancel) {
        if (stage == null) return;
        Scene scene = stage.getScene();
        if (scene == null) return;
        Parent root = scene.getRoot();
        if (!(root instanceof StackPane stack)) return;

        // Idempotent: skip if another lobby overlay is already open.
        for (Node n : stack.getChildren()) {
            if ("playernames-overlay".equals(n.getId())) return;
        }

        MessageService msg = MessageService.getInstance();

        // Preview bot names once, against the default "Player N" placeholders.
        // onConfirm only re-allocates if the user types a colliding name.
        Set<String> defaults = new HashSet<>();
        for (int i = 0; i < humanCount; i++) defaults.add("Player " + (i + 1));
        List<String> previewedBots = (botCount > 0)
                ? new ArrayList<>(BotNames.allocate(botCount, defaults))
                : new ArrayList<>();

        StackPane overlay = new StackPane();
        overlay.setId("playernames-overlay");
        overlay.getStyleClass().add("overlay-backdrop");

        List<TextField> fields = new ArrayList<>();

        Runnable closeOverlay = () -> {
            stack.getChildren().remove(overlay);
            if (onCancel != null) onCancel.run();
        };

        Runnable confirm = () -> {
            List<String> humanNames = new ArrayList<>(humanCount);
            for (int i = 0; i < humanCount; i++) {
                String v = fields.get(i).getText().trim();
                humanNames.add(v.isEmpty() ? "Player " + (i + 1) : v);
            }

            List<Player> players = new ArrayList<>(humanCount + botCount);
            Set<String> taken = new HashSet<>(humanNames);
            for (String name : humanNames) {
                players.add(new Player(name, balance));
            }
            if (botCount > 0) {
                // Reuse the previewed names unless a typed name now collides.
                List<String> botNames = previewedBots;
                boolean collision = botNames.stream().anyMatch(taken::contains)
                        || botNames.size() != botCount;
                if (collision) {
                    botNames = BotNames.allocate(botCount, taken);
                }
                for (String botName : botNames) {
                    players.add(new Player(botName, balance, true));
                }
            }

            // Don't fire the cancel callback — we *are* proceeding into the game.
            stack.getChildren().remove(overlay);
            GameController.setPendingGameManager(new GameManager(players));
            Navigation.navigate(stage, "/ui/game.fxml");
        };

        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeOverlay.run();
        });

        VBox card = buildCard(msg, humanCount, botCount, balance,
                previewedBots, fields, closeOverlay, confirm);
        overlay.getChildren().add(card);
        stack.getChildren().add(overlay);

        // Esc closes — scene-level filter, removed when the overlay is detached.
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

        // Focus the first name field for instant typing.
        if (!fields.isEmpty()) {
            Platform.runLater(() -> {
                TextField first = fields.get(0);
                first.requestFocus();
                first.selectAll();
            });
        }
    }

    // ── Card ────────────────────────────────────────────────────────

    private static VBox buildCard(MessageService msg,
                                  int humanCount, int botCount, int balance,
                                  List<String> previewedBots,
                                  List<TextField> fields,
                                  Runnable closeOverlay, Runnable confirm) {
        VBox card = new VBox(18);
        card.getStyleClass().add("overlay-card");
        card.setMinWidth(640);
        card.setMaxWidth(960);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(24));

        // Header: title + close ✕
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(msg.getMessage("menu.names.title"));
        title.getStyleClass().add("dialog-header");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> closeOverlay.run());
        header.getChildren().addAll(title, spacer, close);

        // Summary line
        Label summary = new Label(botCount > 0
                ? msg.getMessage("menu.names.summary", humanCount, botCount, balance)
                : msg.getMessage("menu.names.summary.noBots", humanCount, balance));
        summary.getStyleClass().add("lobby-summary");

        // Seat-cards
        HBox seatsRow = new HBox(18);
        seatsRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < humanCount; i++) {
            seatsRow.getChildren().add(buildSeatCard(i, msg, fields, confirm));
        }

        VBox content = new VBox(16, summary, seatsRow);
        content.setAlignment(Pos.CENTER);

        // Bot strip (only when bots are present)
        if (botCount > 0) {
            Label botsHeader = new Label(msg.getMessage("menu.names.botsHeader"));
            botsHeader.getStyleClass().add("field-label");

            HBox botsStrip = new HBox(10);
            botsStrip.setAlignment(Pos.CENTER);
            for (String botName : previewedBots) {
                botsStrip.getChildren().add(buildBotChip(botName));
            }
            content.getChildren().addAll(botsHeader, botsStrip);
        }

        // Confirm
        Button start = new Button(msg.getMessage("mainmenu.action.start"));
        start.getStyleClass().add("primary-button");
        start.setMaxWidth(Double.MAX_VALUE);
        start.setOnAction(e -> confirm.run());

        card.getChildren().addAll(header, content, start);
        return card;
    }

    /** Coloured avatar + "Giocatore N" caption + editable name field. */
    private static VBox buildSeatCard(int index, MessageService msg,
                                      List<TextField> fields, Runnable confirm) {
        VBox slot = new VBox(10);
        slot.setAlignment(Pos.CENTER);
        slot.getStyleClass().add("lobby-seat");
        HBox.setHgrow(slot, Priority.SOMETIMES);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().addAll("seat-avatar", "lobby-avatar");
        if (index > 0) {
            avatar.getStyleClass().add("seat-avatar-p" + (index + 1));
        }
        Label initial = new Label(String.valueOf(index + 1));
        initial.getStyleClass().add("seat-avatar-initial");
        avatar.getChildren().add(initial);

        Label slotLabel = new Label(msg.getMessage("menu.names.player", index + 1));
        slotLabel.getStyleClass().add("lobby-seat-label");

        TextField tf = new TextField("Player " + (index + 1));
        tf.setPromptText("Player " + (index + 1));
        tf.getStyleClass().add("text-field-dark");
        tf.setMaxWidth(220);
        // ENTER inside any name field = confirm. (The game-scene SPACE/ENTER
        // filter is scoped to game.fxml so it doesn't affect this dialog.)
        tf.setOnAction(e -> confirm.run());
        fields.add(tf);

        slot.getChildren().addAll(avatar, slotLabel, tf);
        return slot;
    }

    /** Pill-shaped read-only chip showing a bot's auto-allocated name. */
    private static HBox buildBotChip(String botName) {
        Label icon = new Label("🤖");
        icon.getStyleClass().add("bot-chip-icon");
        Label name = new Label(botName);
        name.getStyleClass().add("bot-chip-name");
        HBox chip = new HBox(6, icon, name);
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().add("bot-chip");
        return chip;
    }
}
