package com.yash.crashexplainer.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Finds crash-reports/*.txt files that haven't been processed yet, oldest first.
 */
public final class CrashReportScanner {

    private final Path crashReportsDir;

    public CrashReportScanner(Path gameDir) {
        this.crashReportsDir = gameDir.resolve("crash-reports");
    }

    public List<Path> findUnprocessed(Set<String> alreadyProcessed) {
        List<Path> results = new ArrayList<>();
        if (!Files.isDirectory(crashReportsDir)) {
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(crashReportsDir, "*.txt")) {
            for (Path p : stream) {
                if (!alreadyProcessed.contains(p.getFileName().toString())) {
                    results.add(p);
                }
            }
        } catch (IOException ignored) {
            return results;
        }

        results.sort(Comparator.comparing(this::safeLastModified));
        return results;
    }

    private long safeLastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
