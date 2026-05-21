package ch.supsi.dti.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class PersistenceService {

    private static final Path DEFAULT_PATH = Path.of("frontend", "saved", "autosave.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path path;

    public PersistenceService() {
        this(DEFAULT_PATH);
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

    public Path getPath() {
        return path;
    }
}
