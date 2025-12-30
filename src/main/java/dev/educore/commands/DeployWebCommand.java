package dev.educore.commands;

import dev.educore.core.Root;
import dev.educore.core.os.PackageManager;
import dev.educore.core.os.PackageManagers;
import dev.educore.web.WebAppDeployer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "deploy-web", description = "Zieht Repo, baut (npm), deployed nach /var/www/<domain>.")
public class DeployWebCommand implements Runnable {

    @Option(names = {"--repo"}, required = true)
    String repo;

    @Option(names = {"--branch"}, defaultValue = "main")
    String branch;

    @Option(names = {"--domain"}, required = true)
    String domain;

    @Override
    public void run() {
        Root.requireRoot();
        PackageManager pm = PackageManagers.detectOrThrow();
        new WebAppDeployer(pm).deploy(repo, branch, Path.of("/var/www", domain));
    }
}
