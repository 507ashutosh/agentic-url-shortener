package com.agentic.orchestrator.console;

/**
 * Terminal colours, used only when the output is an interactive terminal (and NO_COLOR is not set),
 * so output redirected to a file or captured in sample runs stays plain text.
 */
public final class Ansi {

    private static final boolean ENABLED = System.console() != null && System.console().isTerminal()
            && System.getenv("NO_COLOR") == null;

    private Ansi() {
    }

    public static String green(String text) {
        return wrap("32", text);
    }

    public static String red(String text) {
        return wrap("31", text);
    }

    public static String yellow(String text) {
        return wrap("33", text);
    }

    public static String magenta(String text) {
        return wrap("35", text);
    }

    public static String cyan(String text) {
        return wrap("36", text);
    }

    public static String bold(String text) {
        return wrap("1", text);
    }

    public static String dim(String text) {
        return wrap("2", text);
    }

    private static String wrap(String code, String text) {
        return ENABLED && !text.isEmpty() ? "\u001B[" + code + "m" + text + "\u001B[0m" : text;
    }
}
