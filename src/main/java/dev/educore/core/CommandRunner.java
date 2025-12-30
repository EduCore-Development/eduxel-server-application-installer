package dev.educore.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CommandRunner {
    private CommandRunner() {}

    public static Result run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(baos);
            }
            int code = p.waitFor();
            String out = baos.toString(StandardCharsets.UTF_8);
            return new Result(code, out);
        } catch (Exception e) {
            return new Result(999, e.toString());
        }
    }

    public static void must(List<String> cmd) {
        Result r = run(cmd);
        if (r.code() != 0) throw new IllegalStateException("Command failed: " + String.join(" ", cmd) + "\n" + r.output());
    }

    public static boolean exists(String bin) {
        Result r = run(List.of("sh", "-lc", "command -v " + bin + " >/dev/null 2>&1; echo $?"));
        return r.output().trim().endsWith("0");
    }


    public record Result(int code, String output) {}
}
