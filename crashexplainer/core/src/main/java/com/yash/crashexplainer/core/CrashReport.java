package com.yash.crashexplainer.core;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A parsed handle to a single crash-reports/*.txt file. Kept intentionally dumb -
 * all the actual understanding happens in DiagnosticEngine / Explainer implementations.
 */
public final class CrashReport {

    public final Path file;
    public final String fileName;
    public final String text;
    public final Instant timestamp;

    public CrashReport(Path file, String text, Instant timestamp) {
        this.file = file;
        this.fileName = file.getFileName().toString();
        this.text = text;
        this.timestamp = timestamp;
    }
}
