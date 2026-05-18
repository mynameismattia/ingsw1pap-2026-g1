package ch.supsi.dti.backend.model;

import java.time.Instant;

public record RoundRecord(
        String playerName,
        int bet,
        HandOutcome outcome,
        int playerScore,
        int dealerScore,
        Instant timestamp
) {}
