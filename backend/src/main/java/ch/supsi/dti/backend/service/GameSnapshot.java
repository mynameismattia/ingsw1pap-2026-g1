// DTO immutabile (Java record) per save/load. Cattura il round corrente, le carte ancora nel mazzo, lo stato dei player (nome, balance, isBot via PlayerSaveData nested record) e lo storico round.
// Il factory fromGameManager(gm, roundNumber) lo costruisce dal vivo. Si serializza in JSON via Jackson nel PersistenceService.

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
        // 1. Riduco ogni Player ai 3 campi che ci interessa salvare (nome, balance, isBot).
        //    Niente carte in mano e niente strategie: sono ricostruite a fresco al load.
        List<PlayerSaveData> players = gm.getPlayers().stream()
                .map(p -> new PlayerSaveData(p.getName(), p.getBalance(), p.isBot()))
                .toList();

        // 2. Compongo lo snapshot finale con round, carte rimaste, players ridotti, copia immutabile dello storico.
        return new GameSnapshot(
                currentRoundNumber,
                gm.getDeckRemaining(),
                players,
                List.copyOf(gm.getHistory())
        );
    }
}
