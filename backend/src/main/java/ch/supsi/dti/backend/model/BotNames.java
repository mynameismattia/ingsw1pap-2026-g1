// Pool di 8 nomi tematici per i bot ("Croupier Carl", "Lucky Lou", ecc.).
// Il metodo allocate(n, taken) distribuisce n nomi unici evitando collisioni con quelli già presi; se finisce il pool aggiunge un suffisso "2", "3"… Pure utility statica.

package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BotNames {

    private static final List<String> POOL = List.of(
            "Croupier Carl",
            "Lucky Lou",
            "Diamond Doris",
            "Vegas Vic",
            "Snake-eyes Sam",
            "Ace Anna",
            "High-Roller Hank",
            "Bluff Betty"
    );

    private BotNames() {}

    public static List<String> allocate(int n, Set<String> taken) {
        // 1. Inizializzo l'output e i cursori: cursor scorre il POOL ciclicamente, suffix sale ogni giro completo.
        List<String> result = new ArrayList<>(n);
        int suffix = 1;
        int cursor = 0;

        // 2. Finché non ho i miei n nomi, prendo il prossimo nome dal pool. Se siamo oltre il primo giro,
        //    aggiungo un numero in coda ("Croupier Carl 2", "Croupier Carl 3", ...) per garantire unicità.
        while (result.size() < n) {
            String candidate = POOL.get(cursor % POOL.size());
            if (suffix > 1) {
                candidate = candidate + " " + suffix;
            }
            cursor++;
            if (cursor % POOL.size() == 0) {
                suffix++;
            }

            // 3. Salto i nomi già presi (taken) o già scelti in questa stessa allocazione.
            if (taken.contains(candidate) || result.contains(candidate)) {
                continue;
            }

            // 4. Accetto il candidato.
            result.add(candidate);
        }
        return result;
    }

    public static List<String> pool() {
        return POOL;
    }
}
