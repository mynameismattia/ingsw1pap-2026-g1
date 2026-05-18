package ch.supsi.dti.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BotNames {

    // Pool A — casino classics. Order is preserved for deterministic assignment.
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

    /**
     * Returns {@code n} distinct bot names not already in {@code taken}. If the
     * pool runs out, falls back to suffixed variants ("Vegas Vic 2", ...).
     */
    public static List<String> allocate(int n, Set<String> taken) {
        List<String> result = new ArrayList<>(n);
        int suffix = 1;
        int cursor = 0;
        while (result.size() < n) {
            String candidate = POOL.get(cursor % POOL.size());
            if (suffix > 1) {
                candidate = candidate + " " + suffix;
            }
            cursor++;
            if (cursor % POOL.size() == 0) {
                suffix++;
            }
            if (taken.contains(candidate) || result.contains(candidate)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    public static List<String> pool() {
        return POOL;
    }
}
