package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.ConsoleUI;
import dev.educore.runtime.CredentialServer;
import picocli.CommandLine.Command;

@Command(name = "serve", description = "Startet den Credential-Server (wie früher eduxel.py).")
public class ServeCommand implements Runnable {
    @Override
    public void run() {
        AppConfig cfg = ConfigManager.readOrThrow();
        ConsoleUI.ok("Starte Server auf Port " + cfg.app().port());
        new CredentialServer(cfg).startBlocking();
    }
}
