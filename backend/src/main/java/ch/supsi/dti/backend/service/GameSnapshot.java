package ch.supsi.dti.backend.service;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.model.RoundRecord;

import java.util.List;

public record GameSnapshot(
        int currentRoundNumber,
        int remainingDeckCards,
        List<PlayerSaveData> playersData,
        List<RoundRecord> roundHistory
) {
    public record PlayerSaveData(String name, int balance, boolean isBot) {}

    public static GameSnapshot fromGameManager(GameManager gm, int currentRoundNumber) {
        List<PlayerSaveData> players = gm.getPlayers().stream()
                .map(p -> new PlayerSaveData(p.getName(), p.getBalance(), p.isBot()))
                .toList();
        return new GameSnapshot(
                currentRoundNumber,
                gm.getDeckRemaining(),
                players,
                List.copyOf(gm.getHistory())
        );
    }
}
