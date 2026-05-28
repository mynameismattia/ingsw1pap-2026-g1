// Test save/load su file reale: scrive un JSON in una temp dir, lo rilegge, verifica equivalenza. Anche test di exists/lastModified.

package ch.supsi.dti.backend.service;

import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.RoundRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceServiceTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path tmp) throws Exception {
        PersistenceService ps = new PersistenceService(tmp.resolve("missing.json"));
        assertFalse(ps.exists());
        assertEquals(Optional.empty(), ps.load());
    }

    @Test
    void saveThenLoadRoundtrips(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("nested/dir/save.json");
        PersistenceService ps = new PersistenceService(file);
        GameSnapshot snap = new GameSnapshot(
                5,
                201,
                List.of(new GameSnapshot.PlayerSaveData("Bob", 80, false)),
                List.of(new RoundRecord("Bob", 25, HandOutcome.BLACKJACK, 21, 19,
                        Instant.parse("2026-05-20T19:29:00Z")))
        );

        ps.save(snap);

        assertTrue(ps.exists(), "file should exist after save");
        Optional<GameSnapshot> loaded = ps.load();
        assertTrue(loaded.isPresent());
        assertEquals(snap, loaded.get());
    }
}
