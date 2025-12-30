package dev.educore.commands;

import dev.educore.core.ConsoleUI;
import dev.educore.update.UpdateChecker;
import picocli.CommandLine.Command;

@Command(name = "update-check", description = "Checkt Updates (GitHub Repo oder versionUrl aus resources).")
public class UpdateCheckCommand implements Runnable {
    @Override
    public void run() {
        UpdateChecker.Result r = UpdateChecker.check();
        if (r.status() == UpdateChecker.Status.SKIPPED) {
            ConsoleUI.warn("Update-Check übersprungen: " + r.message());
            return;
        }
        if (r.status() == UpdateChecker.Status.UP_TO_DATE) {
            ConsoleUI.ok("Up to date: " + r.message());
            return;
        }
        ConsoleUI.warn("Update verfügbar: " + r.message());
    }
}
