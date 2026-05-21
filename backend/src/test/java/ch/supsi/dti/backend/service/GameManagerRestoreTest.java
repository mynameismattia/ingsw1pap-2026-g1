package ch.supsi.dti.backend.service;

import ch.supsi.dti.backend.game.GameManager;
import ch.supsi.dti.backend.game.GameState;
import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.Player;
import ch.supsi.dti.backend.model.RoundRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameManagerRestoreTest {

    @Test
    void restoreLandsInRoundOverWithSeededHistory() {
        List<Player> players = List.of(
                new Player("Alice", 120),
                new Player("CPU-1", 95, true)
        );
        Instant t = Instant.parse("2026-05-20T19:29:00Z");
        List<RoundRecord> history = List.of(
                new RoundRecord("Alice", 20, HandOutcome.WIN, 19, 18, t),
                new RoundRecord("CPU-1", 10, HandOutcome.LOSE, 22, 18, t)
        );

        GameManager gm = GameManager.restore(players, history);

        assertEquals(GameState.ROUND_OVER, gm.getState());
        assertEquals(2, gm.getPlayers().size());
        assertEquals("Alice", gm.getPlayers().get(0).getName());
        assertEquals(120, gm.getPlayers().get(0).getBalance());
        assertEquals("CPU-1", gm.getPlayers().get(1).getName());
        assertTrue(gm.getPlayers().get(1).isBot());
        assertNotNull(gm.getPlayers().get(1).getStrategy(),
                "Restored bot should have its strategy auto-installed");
        assertEquals(history, gm.getHistory());
    }

    @Test
    void snapshotRoundtripPreservesPublicState() {
        List<Player> players = List.of(
                new Player("Alice", 120),
                new Player("CPU-1", 95, true)
        );
        Instant t = Instant.parse("2026-05-20T19:29:00Z");
        List<RoundRecord> history = List.of(
                new RoundRecord("Alice", 20, HandOutcome.WIN, 19, 18, t)
        );
        GameManager source = GameManager.restore(players, history);

        GameSnapshot snap = GameSnapshot.fromGameManager(source, 7);
        assertEquals(7, snap.currentRoundNumber());
        assertEquals(2, snap.playersData().size());
        assertEquals("CPU-1", snap.playersData().get(1).name());
        assertTrue(snap.playersData().get(1).isBot());
        assertEquals(history, snap.roundHistory());

        // Rebuild from the snapshot the same way MenuController will.
        List<Player> rebuilt = new ArrayList<>();
        for (GameSnapshot.PlayerSaveData pd : snap.playersData()) {
            rebuilt.add(new Player(pd.name(), pd.balance(), pd.isBot()));
        }
        GameManager restored = GameManager.restore(rebuilt, snap.roundHistory());

        assertEquals(GameState.ROUND_OVER, restored.getState());
        // startNewRound should accept a restored manager and roll into BETTING.
        restored.startNewRound();
        assertEquals(GameState.BETTING, restored.getState());
    }
}
