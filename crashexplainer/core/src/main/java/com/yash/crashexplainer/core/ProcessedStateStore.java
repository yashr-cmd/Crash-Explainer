package com.yash.crashexplainer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks which filenames we've already reported on, so restarts don't re-show the same
 * crash/log forever. Backed by a plain text file under <gamedir>/crashexplainer/ - use a
 * distinct stateFileName per "kind" of thing being tracked (crash reports vs rotated logs)
 * so they don't share one namespace.
 */
public final class ProcessedStateStore {

    private final Path stateFile;

    public ProcessedStateStore(Path gameDir) {
        this(gameDir, "processed.txt");
    }

    public ProcessedStateStore(Path gameDir, String stateFileName) {
        this.stateFile = gameDir.resolve("crashexplainer").resolve(stateFileName);
    }

    public Set<String> load() {
        Set<String> result = new HashSet<>();
        if (!Files.isRegularFile(stateFile)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(stateFile, StandardCharsets.UTF_8);
            result.addAll(lines);
        } catch (IOException ignored) {
            // treat as empty - worst case we re-report a crash once, never worse than that
        }
        return result;
    }

    public void markProcessed(String fileName) {
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(
                    stateFile,
                    fileName + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // if we can't persist, worst case is we re-report next launch - not fatal
        }
    }
}
