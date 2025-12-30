package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import picocli.CommandLine.Command;

@Command(name = "info", description = "Zeigt config.json Infos.")
public class InfoCommand implements Runnable {
    @Override
    public void run() {
        AppConfig c = ConfigManager.readOrThrow();
        System.out.println("Mode: " + c.mode());
        System.out.println("API-Port: " + c.app().port());
        System.out.println("Secret: " + c.app().secret());
        System.out.println("DB: mysql://" + c.database().user() + "@" + c.database().host() + ":" + c.database().port() + "/" + c.database().database());
    }
}
