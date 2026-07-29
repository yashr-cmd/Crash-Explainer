package com.yash.crashexplainer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds JVM-fatal-crash handler output (hs_err_pid<pid>.log) not yet processed. The JVM
 * writes these itself on a native crash - segfault, bad native library, bad agent injection -
 * before dying, and nothing else (no Java code, no shutdown hook, no uncaught-exception
 * handler) gets a chance to run when this happens. Location varies: usually the working
 * directory the JVM was launched from (the game dir, for most MC launchers), sometimes
 * java.io.tmpdir depending on -XX:ErrorFile and OS defaults, so both are checked.
 */
public final class HsErrScanner {

    private final List<Path> searchDirs;

    public HsErrScanner(Path gameDir) {
        Set<Path> dirs = new LinkedHashSet<>();
        dirs.add(gameDir);
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            dirs.add(Path.of(tmp));
        }
        this.searchDirs = new ArrayList<>(dirs);
    }

    public List<Path> findUnprocessed(Set<String> alreadyProcessed) {
        List<Path> results = new ArrayList<>();

        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "hs_err_pid*.log")) {
                for (Path p : stream) {
                    if (!alreadyProcessed.contains(p.getFileName().toString())) {
                        results.add(p);
                    }
                }
            } catch (IOException ignored) {
                // skip this directory, keep checking the others
            }
        }

        results.sort(Comparator.comparing(this::safeLastModified));
        return results;
    }

    public static String readHsErrFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private long safeLastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
