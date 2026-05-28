// Snapshot immutabile (Java record) di una mano finita: chi ha giocato, quanto ha puntato, l'esito, il punteggio finale di player e dealer, il timestamp.
// Alimenta la history table (game.history) e il chart "balance evolution" nella schermata Vedi Risultati.

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
