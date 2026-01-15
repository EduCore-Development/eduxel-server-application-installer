package dev.educore.commands;

import dev.educore.core.CommandRunner;
import dev.educore.core.ConsoleUI;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Command(name = "logs", description = "Zeigt Logs (Datei oder Journal)")
public class LogsCommand implements Runnable {

    @Option(names = {"--tail"}, description = "Logausgabe folgen")
    boolean tail;

    @Option(names = {"--lines"}, description = "Anzahl Zeilen", defaultValue = "200")
    int lines;

    @Override
    public void run() {
        Path logFile = Path.of("/var/log/eduxel/eduxel.log");
        if (Files.exists(logFile)) {
            runTail(logFile.toString());
            return;
        }

        if (!CommandRunner.exists("journalctl")) {
            ConsoleUI.error("Keine Logs gefunden (Logfile fehlt, journalctl nicht verfuegbar)");
            return;
        }

        String cmd = tail
                ? "journalctl -u eduxel -f -n " + lines
                : "journalctl -u eduxel -n " + lines;
        CommandRunner.must(List.of("sh", "-lc", cmd));
    }

    private void runTail(String file) {
        if (!CommandRunner.exists("tail")) {
            ConsoleUI.error("tail nicht verfuegbar: " + file);
            return;
        }
        String cmd = tail
                ? "tail -n " + lines + " -f " + file
                : "tail -n " + lines + " " + file;
        CommandRunner.must(List.of("sh", "-lc", cmd));
    }
}