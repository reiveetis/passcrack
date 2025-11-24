package util;

import java.text.SimpleDateFormat;

public class Logger {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String RESET = "\u001B[0m";

    enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static void log(String msg, LogLevel lvl) {
        String date = dateFormat.format(System.currentTimeMillis());
        String worker = Thread.currentThread().getName();
        String messagePrefix = "[" + date + "][" + worker + "] " + lvl + ": ";
        switch (lvl) {
            case DEBUG -> messagePrefix = BLUE + messagePrefix + RESET;
            case INFO -> messagePrefix = GREEN + messagePrefix + RESET;
            case WARN -> messagePrefix = YELLOW + messagePrefix + RESET;
            case ERROR -> messagePrefix = RED + messagePrefix + RESET;
        }
        System.out.println(messagePrefix + msg);
    }

    public static void debug(String msg) {
        log(msg, LogLevel.DEBUG);
    }

    public static void info(String msg) {
        log(msg, LogLevel.INFO);
    }

    public static void warn(String msg) {
        log(msg, LogLevel.WARN);
    }

    public static void error(String msg) {
        log(msg, LogLevel.ERROR);
    }
}
