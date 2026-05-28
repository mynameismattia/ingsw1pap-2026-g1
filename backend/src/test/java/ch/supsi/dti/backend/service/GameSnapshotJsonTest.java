// Test serializzazione/deserializzazione GameSnapshot in JSON via Jackson: round-trip identità, gestione campi null/list vuote.

package ch.supsi.dti.backend.service;

import ch.supsi.dti.backend.model.HandOutcome;
import ch.supsi.dti.backend.model.RoundRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSnapshotJsonTest {

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void serializesInstantAsIso8601String() throws Exception {
        Instant fixed = Instant.parse("2026-05-20T19:29:00Z");
        GameSnapshot snap = new GameSnapshot(
                3,
                250,
                List.of(new GameSnapshot.PlayerSaveData("Alice", 120, false)),
                List.of(new RoundRecord("Alice", 20, HandOutcome.WIN, 19, 18, fixed))
        );
        String json = mapper().writeValueAsString(snap);
        assertTrue(json.contains("\"2026-05-20T19:29:00Z\""),
                "Timestamp should be ISO-8601, not numeric. Got: " + json);
    }

    @Test
    void roundtripPreservesEquality() throws Exception {
        Instant t = Instant.parse("2026-05-20T19:29:00Z");
        GameSnapshot original = new GameSnapshot(
                2,
                180,
                List.of(
                        new GameSnapshot.PlayerSaveData("Alice", 120, false),
                        new GameSnapshot.PlayerSaveData("CPU-1", 95, true)
                ),
                List.of(
                        new RoundRecord("Alice", 20, HandOutcome.WIN, 19, 18, t),
                        new RoundRecord("CPU-1", 10, HandOutcome.LOSE, 22, 18, t)
                )
        );
        ObjectMapper m = mapper();
        String json = m.writeValueAsString(original);
        GameSnapshot back = m.readValue(json, GameSnapshot.class);
        assertEquals(original, back);
    }
}
