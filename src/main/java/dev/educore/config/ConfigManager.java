package dev.educore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private ConfigManager() {}

    public static final Path DIR = Path.of("/etc/eduxel");
    public static final Path FILE = DIR.resolve("config.json");

    private static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void write(AppConfig cfg) {
        try {
            Files.createDirectories(DIR);
            M.writeValue(FILE.toFile(), cfg);
        } catch (Exception e) {
            throw new IllegalStateException("Config schreiben fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    public static AppConfig readOrThrow() {
        try {
            if (!Files.exists(FILE)) throw new IllegalStateException("config.json fehlt: " + FILE);
            return M.readValue(FILE.toFile(), AppConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Config lesen fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}
