package dev.educore;

import dev.educore.commands.*;
import dev.educore.update.VersionProvider;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "eduxel",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Eduxel Server Application Installer/Manager",
        subcommands = {
                InstallCommand.class,
                WebDashCommand.class,
                ServeCommand.class,
                InfoCommand.class,
                ResetCommand.class,
                UninstallCommand.class,
                UpdateCheckCommand.class
        }
)
public class EduxelServer implements Runnable {

    @Override
    public void run() {
        System.out.println("Nutze ein Subcommand. Z.B.: eduxel install --help");
    }

    public static void main(String[] args) {
        AnsiConsole.systemInstall();
        int code = new CommandLine(new EduxelServer()).execute(args);
        AnsiConsole.systemUninstall();
        System.exit(code);
    }
}
