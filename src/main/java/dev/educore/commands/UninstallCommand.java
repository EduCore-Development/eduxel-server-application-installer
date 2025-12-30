package dev.educore.commands;

import dev.educore.core.ConsoleUI;
import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.service.SystemdManager;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "uninstall", description = "Deinstalliert Eduxel (Config/Opt/Service).")
public class UninstallCommand implements Runnable {

    @Override
    public void run() {
        Root.requireRoot();

        String c = ConsoleUI.ask("Wirklich deinstallieren? (y/N): ");
        if (!"y".equalsIgnoreCase(c)) {
            ConsoleUI.warn("Abgebrochen.");
            return;
        }

        PackageManager pm = PackageManagers.detectOrThrow();
        SystemdManager systemd = new SystemdManager(pm);
        systemd.stopDisableRemoveService();

        try {
            Files.deleteIfExists(Path.of("/usr/local/bin/eduxel"));
        } catch (Exception ignored) {}

        deleteDir(Path.of("/etc/eduxel"));
        deleteDir(Path.of("/opt/eduxel"));

        ConsoleUI.ok("Uninstall OK");
    }

    private static void deleteDir(Path p) {
        try {
            if (!Files.exists(p)) return;
            try (var walk = Files.walk(p)) {
                walk.sorted((a,b) -> b.getNameCount() - a.getNameCount())
                        .forEach(x -> {
                            try { Files.deleteIfExists(x); } catch (Exception ignored) {}
                        });
            }
        } catch (Exception ignored) {}
    }
}
