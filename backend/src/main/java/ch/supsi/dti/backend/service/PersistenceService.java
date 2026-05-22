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
        Files.createDirectories(path.toAbsolutePath().getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshot);
    }

    public Optional<GameSnapshot> load() throws IOException {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
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

    /**
     * Loads every slot in a single call. A slot that is missing or fails to
     * deserialize maps to {@link Optional#empty()} — one bad save must not
     * prevent the UI from listing the others.
     */
    public static Map<SaveSlot, Optional<GameSnapshot>> loadAll() {
        Map<SaveSlot, Optional<GameSnapshot>> out = new EnumMap<>(SaveSlot.class);
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
