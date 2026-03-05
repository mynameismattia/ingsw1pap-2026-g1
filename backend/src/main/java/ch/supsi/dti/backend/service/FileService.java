package ch.supsi.dti.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileService {

    public void saveUtf8(Path file, String content) throws IOException {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (content == null) content = "";
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
