package com.hung.util;

import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Gdx;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's log: every Gdx.app.log / error / debug line goes to the terminal and to a file of its
 * own per session, logs/game-yyyy-MM-dd_HH-mm-ss.log. Lines read "14:03:21.123 INFO  [Tag] message";
 * errors (and their stack traces) go to stderr. Each line is flushed at once, so a crash keeps
 * everything written before it, and {@link #install()} also logs uncaught exceptions.
 * The folder comes from the system property {@value #DIR_PROPERTY} (Gradle's run task points it at
 * the project's logs/), otherwise "logs" next to the working directory. Only the newest
 * {@value #KEEP_FILES} files are kept. If the file cannot be opened the log stays terminal-only.
 */
public final class GameLog implements ApplicationLogger {

    public static final String DIR_PROPERTY = "mygame.logDir";
    public static final String DEFAULT_DIR = "logs";
    /** Session files kept in the folder, this one included. */
    static final int KEEP_FILES = 20;
    static final String PREFIX = "game-";
    static final String SUFFIX = ".log";

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Clock clock;
    private final PrintStream out;
    private final PrintStream err;
    /** Null when the log is terminal-only. */
    private final PrintWriter file;
    private final Path path;

    private GameLog(Clock clock, PrintStream out, PrintStream err, PrintWriter file, Path path) {
        this.clock = clock;
        this.out = out;
        this.err = err;
        this.file = file;
        this.path = path;
    }

    /** Opens this session's log in the configured folder, makes it Gdx.app's logger and logs crashes. */
    public static GameLog install() {
        GameLog log = open(Paths.get(System.getProperty(DIR_PROPERTY, DEFAULT_DIR)), Clock.systemDefaultZone(), System.out, System.err);
        Gdx.app.setApplicationLogger(log);
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, crash) -> {
            log.error("Crash", "Uncaught exception on thread " + thread.getName(), crash);
            if (previous != null) previous.uncaughtException(thread, crash);
        });
        return log;
    }

    /** A new session file in folder (created if needed), after dropping the oldest files over the limit. */
    static GameLog open(Path folder, Clock clock, PrintStream out, PrintStream err) {
        PrintWriter writer = null;
        Path path = null;
        try {
            Files.createDirectories(folder);
            prune(folder, KEEP_FILES - 1);
            path = freeName(folder, LocalDateTime.now(clock).format(FILE_STAMP));
            writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8), true);
        } catch (IOException | RuntimeException e) {
            err.println("[GameLog] Could not open a log file in " + folder.toAbsolutePath() + " (" + e + "); logging to the terminal only");
            path = null;
        }
        GameLog log = new GameLog(clock, out, err, writer, path);
        log.log("GameLog", "Session started: Java " + System.getProperty("java.version") + ", "
            + System.getProperty("os.name") + (path != null ? ", file " + path.toAbsolutePath() : ""));
        return log;
    }

    /** The session file, or null when the log is terminal-only. */
    public Path file() {
        return path;
    }

    @Override public void log(String tag, String message) { write(out, "INFO ", tag, message, null); }
    @Override public void log(String tag, String message, Throwable exception) { write(out, "INFO ", tag, message, exception); }
    @Override public void error(String tag, String message) { write(err, "ERROR", tag, message, null); }
    @Override public void error(String tag, String message, Throwable exception) { write(err, "ERROR", tag, message, exception); }
    @Override public void debug(String tag, String message) { write(out, "DEBUG", tag, message, null); }
    @Override public void debug(String tag, String message, Throwable exception) { write(out, "DEBUG", tag, message, exception); }

    private synchronized void write(PrintStream console, String level, String tag, String message, Throwable exception) {
        String line = LocalDateTime.now(clock).format(LINE_STAMP) + " " + level + " [" + tag + "] " + message;
        console.println(line);
        if (exception != null) exception.printStackTrace(console);
        if (file != null) {
            file.println(line);
            if (exception != null) exception.printStackTrace(file);
            file.flush();
        }
    }

    /** Deletes the oldest session files until at most keep remain (names sort by time). */
    static void prune(Path folder, int keep) throws IOException {
        List<Path> logs = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, PREFIX + "*" + SUFFIX)) {
            for (Path file : files) logs.add(file);
        }
        logs.sort(null);
        for (int i = 0; i < logs.size() - Math.max(0, keep); i++) Files.deleteIfExists(logs.get(i));
    }

    /** game-stamp.log, or game-stamp-2.log... when two sessions start in the same second. */
    private static Path freeName(Path folder, String stamp) {
        Path path = folder.resolve(PREFIX + stamp + SUFFIX);
        for (int n = 2; Files.exists(path); n++) path = folder.resolve(PREFIX + stamp + "-" + n + SUFFIX);
        return path;
    }
}
