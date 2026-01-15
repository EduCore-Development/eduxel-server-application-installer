package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.ConsoleUI;
import dev.educore.runtime.CredentialServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "serve", description = "Startet den Credential-Server (wie frueher eduxel.py).")
public class ServeCommand implements Runnable {

    @Option(names = {"--config"}, description = "Pfad zur config.json")
    Path configPath;

    @Option(names = {"--port"}, description = "Port Override")
    Integer port;

    @Override
    public void run() {
        AppConfig cfg = configPath == null ? ConfigManager.readOrThrow() : ConfigManager.readOrThrow(configPath);
        if (port != null) {
            cfg = new AppConfig(cfg.mode(), cfg.database(), new AppConfig.App(port, cfg.app().secret()));
        }
        ConsoleUI.ok("Starte Server auf Port " + cfg.app().port());
        new CredentialServer(cfg).startBlocking();
    }
}