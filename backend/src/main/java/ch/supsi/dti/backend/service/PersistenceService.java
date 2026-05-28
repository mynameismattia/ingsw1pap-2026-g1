// Salva e carica una partita in JSON usando Jackson.
// Lavora per slot (uno SaveSlot per istanza); fornisce save(snapshot), load(), exists(), lastModified() e loadAll() (su tutti gli slot). Crea automaticamente la cartella se manca.

package ch.supsi.dti.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class PersistenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path path;

    public PersistenceService() {
        this(SaveSlot.AUTO);
    }

    public PersistenceService(SaveSlot slot) {
        this(slot.path());
    }

    public PersistenceService(Path path) {
        this.path = path;
    }

    public void save(GameSnapshot snapshot) throws IOException {
        // 1. Assicura che la cartella esista (es. frontend/saved/ al primo lancio).
        Files.createDirectories(path.toAbsolutePath().getParent());
        // 2. Scrive il JSON formattato (writerWithDefaultPrettyPrinter) così è leggibile a mano in caso di debug.
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshot);
    }

    public Optional<GameSnapshot> load() throws IOException {
        // 1. Se il file non esiste restituisco Optional.empty() — chiamante distingue "vuoto" da "presente".
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        // 2. Deserializzo il JSON in un GameSnapshot. Jackson + JavaTimeModule gestisce gli Instant.
        return Optional.of(MAPPER.readValue(path.toFile(), GameSnapshot.class));
    }

    public boolean exists() {
        return Files.exists(path);
    }

    public void delete() throws IOException {
        Files.deleteIfExists(path);
    }

    public Optional<Instant> lastModified() {
        try {
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path getPath() {
        return path;
    }

    public static Map<SaveSlot, Optional<GameSnapshot>> loadAll() {
        // 1. EnumMap come output: preserva l'ordine degli slot (AUTO, SLOT_1, SLOT_2, SLOT_3).
        Map<SaveSlot, Optional<GameSnapshot>> out = new EnumMap<>(SaveSlot.class);

        // 2. Provo a caricare ogni slot. Se uno fallisce (file corrotto, JSON invalido) lo segno come empty
        //    invece di interrompere tutto: gli altri slot devono comunque essere mostrati nella UI Carica/Salva.
        for (SaveSlot slot : SaveSlot.values()) {
            try {
                out.put(slot, new PersistenceService(slot).load());
            } catch (Exception e) {
                System.err.println("Failed to load " + slot + ": " + e.getMessage());
                out.put(slot, Optional.empty());
            }
        }
        return out;
    }
}
