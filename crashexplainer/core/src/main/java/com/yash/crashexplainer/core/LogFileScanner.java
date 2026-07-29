package com.yash.crashexplainer.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Finds both previous rotated logs and, when explicitly requested, the live latest.log. */
public final class LogFileScanner {

    private final Path logsDir;

    public LogFileScanner(Path gameDir) {
        this.logsDir = gameDir.resolve("logs");
    }

    public Path latestLog() {
        return logsDir.resolve("latest.log");
    }

    public String readLatestLogSnapshot() {
        Path latest = latestLog();
        if (!Files.isRegularFile(latest)) return "";
        try {
            // readAllBytes gives us a stable snapshot of bytes that existed at this instant;
            // the logger may append more later, which is fine for a shutdown-time diagnosis.
            return Files.readString(latest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public List<Path> findUnprocessedRotatedLogs(Set<String> alreadyProcessed) {
        List<Path> results = new ArrayList<>();
        if (!Files.isDirectory(logsDir)) return results;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log.gz")) {
            for (Path p : stream) {
                if (!alreadyProcessed.contains(p.getFileName().toString())) results.add(p);
            }
        } catch (IOException ignored) {
            return results;
        }

        results.sort(Comparator.comparing(this::safeLastModified));
        return results;
    }

    public static String readGzippedLog(Path gzFile) {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gzFile));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private long safeLastModified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException e) { return 0L; }
    }
}
