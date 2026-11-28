package com.hung.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game log writes each line to the terminal and to this session's file in logs/. */
class GameLogTest {

    private static final Clock NOON = Clock.fixed(Instant.parse("2026-11-28T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private GameLog open(Path folder) {
        return GameLog.open(folder, NOON, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void aLineGoesToTheTerminalAndTheSessionFile() throws IOException {
        GameLog log = open(dir.resolve("logs"));
        log.log("Doors", "room-1 → room-2 via door-a");

        assertEquals(dir.resolve("logs").resolve("game-2026-11-28_12-00-00.log"), log.file());
        String line = "12:00:00.000 INFO  [Doors] room-1 → room-2 via door-a";
        assertTrue(read(log.file()).contains(line), read(log.file()));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains(line));
        assertTrue(read(log.file()).startsWith("12:00:00.000 INFO  [GameLog] Session started"));
    }

    @Test
    void errorsGoToStderrWithTheirStackTrace() throws IOException {
        GameLog log = open(dir);
        log.error("Save", "Could not write", new IllegalStateException("disk full"));

        String file = read(log.file());
        assertTrue(file.contains("ERROR [Save] Could not write"));
        assertTrue(file.contains("java.lang.IllegalStateException: disk full"));
        assertTrue(file.contains("at com.hung.util.GameLogTest"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("ERROR [Save] Could not write"));
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Could not write"));
    }

    @Test
    void twoSessionsInTheSameSecondGetTwoFiles() {
        Path first = open(dir).file();
        Path second = open(dir).file();
        assertNotNull(second);
        assertFalse(first.equals(second));
        assertTrue(Files.exists(first) && Files.exists(second));
    }

    @Test
    void onlyTheNewestFilesAreKept() throws IOException {
        for (int day = 1; day <= GameLog.KEEP_FILES + 5; day++) {
            Files.writeString(dir.resolve(String.format("game-2026-10-%02d_09-00-00.log", day)), "old");
        }
        Path other = Files.writeString(dir.resolve("notes.log"), "not a session log");
        GameLog log = open(dir);

        List<String> sessions;
        try (Stream<Path> files = Files.list(dir)) {
            sessions = files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("game-")).sorted().toList();
        }
        assertEquals(GameLog.KEEP_FILES, sessions.size());
        assertEquals("game-2026-10-07_09-00-00.log", sessions.get(0));
        assertEquals(log.file().getFileName().toString(), sessions.get(sessions.size() - 1));
        assertTrue(Files.exists(other));
    }

    @Test
    void anUnusableFolderLeavesTheLogTerminalOnly() throws IOException {
        Path notAFolder = Files.writeString(dir.resolve("logs"), "a file where the folder should be");
        GameLog log = open(notAFolder);
        log.log("Map", "still logged");

        assertNull(log.file());
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("logging to the terminal only"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("INFO  [Map] still logged"));
    }
}
