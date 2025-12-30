package dev.educore.commands;

import dev.educore.config.AppConfig;
import dev.educore.config.ConfigManager;
import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.db.MariaDbManager;
import dev.educore.security.Secrets;
import picocli.CommandLine.Command;

@Command(name = "reset", description = "Rotiert Secret und (bei auto) DB-Passwort.")
public class ResetCommand implements Runnable {

    @Override
    public void run() {
        Root.requireRoot();

        PackageManager pm = PackageManagers.detectOrThrow();
        AppConfig cfg = ConfigManager.readOrThrow();

        String confirmId = "EDUXEL-RESET-" + System.currentTimeMillis();
        ConsoleUI.warn("Bestätigungs-ID: " + confirmId);
        String typed = ConsoleUI.ask("Tippe die ID exakt ein: ");
        if (!confirmId.equals(typed)) throw new IllegalStateException("Falsche ID.");

        String newSecret = Secrets.hex(32);
        AppConfig next = cfg.withNewSecret(newSecret);

        if ("auto".equalsIgnoreCase(cfg.mode())) {
            MariaDbManager db = new MariaDbManager(pm);
            next = db.rotatePasswordAuto(next);
        }

        ConfigManager.write(next);
        ConsoleUI.ok("Reset OK");
        ConsoleUI.info("Neues Secret: " + newSecret);
        if ("auto".equalsIgnoreCase(cfg.mode())) ConsoleUI.info("DB Passwort wurde ebenfalls rotiert (auto).");
    }
}
