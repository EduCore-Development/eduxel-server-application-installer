package dev.educore.update;

import picocli.CommandLine;

import java.io.InputStream;
import java.util.Properties;

public final class VersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[] { "eduxel " + UpdateChecker.localVersion() };
    }

    public static Properties props() {
        try (InputStream in = VersionProvider.class.getClassLoader().getResourceAsStream("eduxel-version.properties")) {
            Properties p = new Properties();
            if (in != null) p.load(in);
            return p;
        } catch (Exception e) {
            return new Properties();
        }
    }
}
