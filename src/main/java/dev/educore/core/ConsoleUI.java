package dev.educore.core;

import org.fusesource.jansi.Ansi;

import java.io.Console;

public final class ConsoleUI {
    private ConsoleUI() {}

    public static void banner(String text) {
        System.out.println();
        System.out.println(Ansi.ansi().fgBrightCyan().a("────────────────────────────────────────").reset());
        System.out.println(Ansi.ansi().fgBrightCyan().a("  " + text).reset());
        System.out.println(Ansi.ansi().fgBrightCyan().a("────────────────────────────────────────").reset());
        System.out.println();
    }

    public static void ok(String msg) { System.out.println(Ansi.ansi().fgBrightGreen().a("✓ ").a(msg).reset()); }
    public static void info(String msg) { System.out.println(Ansi.ansi().fgBrightBlue().a("i ").a(msg).reset()); }
    public static void warn(String msg) { System.out.println(Ansi.ansi().fgBrightYellow().a("! ").a(msg).reset()); }

    public static String ask(String prompt) {
        Console c = System.console();
        if (c != null) return c.readLine(prompt);
        System.out.print(prompt);
        try { return new java.util.Scanner(System.in).nextLine(); }
        catch (Exception e) { throw new IllegalStateException("Keine Eingabe möglich."); }
    }

    public static String askHidden(String prompt) {
        Console c = System.console();
        if (c != null) {
            char[] pw = c.readPassword(prompt);
            return pw == null ? "" : new String(pw);
        }
        return ask(prompt);
    }
}
