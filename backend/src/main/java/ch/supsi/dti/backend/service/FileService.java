// Utility minimalista per scrivere file di testo UTF-8.
// Un solo metodo saveUtf8(path, content) che crea automaticamente le directory parent. La lettura vive in PersistenceService via Jackson.

package ch.supsi.dti.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileService {

    public void saveUtf8(Path file, String content) throws IOException {
        // 1. Validazione argomenti: il path non può essere null, il content sì (diventa stringa vuota).
        if (file == null) throw new IllegalArgumentException("file is null");
        if (content == null) content = "";

        // 2. Assicura che la cartella esista (Files.createDirectories non rilancia se già presente).
        Files.createDirectories(file.toAbsolutePath().getParent());

        // 3. Scrive il file in UTF-8 (sovrascrive se esisteva).
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
