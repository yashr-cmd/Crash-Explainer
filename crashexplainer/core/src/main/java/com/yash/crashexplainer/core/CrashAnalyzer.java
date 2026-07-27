package com.yash.crashexplainer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The single entry point loader shims call into. Handles three cases:
 *
 *  1. "It missed the crash" - install() immediately scans crash-reports/ for anything not
 *     yet processed (from a previous session where this mod may not have even been loaded
 *     yet, or the JVM died too hard for the shutdown hook to run) and reports on it right now.
 *
 *  2. "Live, at the end of everything" - install() also registers a shutdown hook that
 *     re-scans at JVM exit, so a crash from THIS session gets caught the moment the crash
 *     report file is written, for any exit path that lets shutdown hooks run.
 *
 *  3. "It failed before any mod code could even run" - some failures (e.g. Fabric Loader's
 *     own dependency resolver rejecting the mod set) happen before our hook, or anyone
 *     else's entrypoint, ever gets called - and they never produce a crash-reports/*.txt
 *     file either. Those still get written to the log before the loader gives up, and that
 *     log gets rotated to logs/*.log.gz on the next launch - so we also scan rotated logs
 *     for known failure signatures (gated on actually matching one, since unlike
 *     crash-reports, a rotated log exists after EVERY session, crash or not).
 *
 * Every public method here is wrapped so that nothing this class does can ever be the
 * reason someone's game fails to launch or crashes harder.
 */
public final class CrashAnalyzer {

    /** Below this confidence, a rotated-log "diagnosis" is treated as noise, not a real hit. */
    private static final int LOG_SCAN_CONFIDENCE_THRESHOLD = 40;

    /** Rotated logs are a fallback, not an archaeology mode. Only inspect the newest few. */
    private static final int MAX_ROTATED_LOGS_PER_STARTUP = 3;

    /** Console report captured at crash time and emitted during shutdown, after the real stack trace. */
    private static volatile PendingReport pendingReport;

    private static final class PendingReport {
        final CrashReport report;
        final Diagnosis diagnosis;
        PendingReport(CrashReport report, Diagnosis diagnosis) {
            this.report = report;
            this.diagnosis = diagnosis;
        }
    }

    private CrashAnalyzer() {}

    /**
     * Call this exactly once, as early as your loader allows (ideally from a static
     * initializer block).
     */
    public static void install(Path gameDir) {
        try {
            checkAndReport(gameDir);
        } catch (Throwable t) {
            logInternalError(gameDir, t);
        }

        // Catch fatal uncaught exceptions while the JVM and console are still alive.
        // This is substantially more reliable than waiting for shutdown, especially for
        // ModLauncher/bootstrap failures where logging may be torn down during shutdown.
        try {
            installUncaughtExceptionBridge(gameDir);
        } catch (Throwable t) {
            logInternalError(gameDir, t);
        }

        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // The uncaught bridge captures the diagnosis BEFORE the normal exception
                    // handler runs, but deliberately does not print it there. Other shutdown hooks
                    // (Forge, launcher, mods) are concurrent, so give their final diagnostics a
                    // short head start and make our banner the epilogue instead of interrupting the
                    // stack trace.
                    try { Thread.sleep(1500L); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }

                    PendingReport pending = pendingReport;
                    if (pending != null) {
                        BannerPrinter.printToConsole(pending.report, pending.diagnosis);
                        pendingReport = null;
                    } else {
                        // Fallback for failures that bypass the uncaught bridge.
                        checkCurrentLatestLog(gameDir);
                    }

                    // Historical files are still scanned, but only after the current-session
                    // epilogue. Their reports are primarily useful on the next launch.
                    checkAndReport(gameDir);
                } catch (Throwable t) {
                    logInternalError(gameDir, t);
                }
            }, "CrashExplainer-ShutdownHook"));
        } catch (Throwable t) {
            logInternalError(gameDir, t);
        }
    }


    /**
     * Wrap the existing default uncaught-exception handler rather than replacing its behavior.
     * We diagnose first, then delegate to Forge/Modrinth/other mods' existing handler.
     */
    private static void installUncaughtExceptionBridge(Path gameDir) {
        // IMPORTANT: ModLauncher/other transformation services may replace the JVM's DEFAULT
        // handler after CrashExplainer installs. A default-only bridge can therefore silently
        // disappear (exactly what happened in the Pig2/T_I test). Pin a handler directly to
        // the current bootstrap/main thread as well. A thread-specific handler wins over the
        // default handler even if somebody replaces the default later.
        final Thread installThread = Thread.currentThread();
        final Thread.UncaughtExceptionHandler previousThread = installThread.getUncaughtExceptionHandler();
        final Thread.UncaughtExceptionHandler previousDefault = Thread.getDefaultUncaughtExceptionHandler();

        Thread.UncaughtExceptionHandler bridge = (thread, throwable) -> {
            try {
                diagnoseUncaught(gameDir, thread, throwable);
            } catch (Throwable t) {
                logInternalError(gameDir, t);
            } finally {
                Thread.UncaughtExceptionHandler delegate = previousThread;
                if (delegate == null || delegate == Thread.getDefaultUncaughtExceptionHandler()) {
                    delegate = previousDefault;
                }
                if (delegate != null && delegate != Thread.currentThread().getUncaughtExceptionHandler()) {
                    try { delegate.uncaughtException(thread, throwable); } catch (Throwable ignored) {}
                } else {
                    try { throwable.printStackTrace(System.err); } catch (Throwable ignored) {}
                }
            }
        };

        // Pin to the thread that is currently performing ModLauncher bootstrap.
        installThread.setUncaughtExceptionHandler(bridge);
        // Also cover other threads that do not have an explicit handler.
        Thread.setDefaultUncaughtExceptionHandler(bridge);
        System.err.println("[CrashExplainer] Armed uncaught bridge on thread '" + installThread.getName() + "'.");
    }

    private static void diagnoseUncaught(Path gameDir, Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String exceptionText = "Uncaught exception on thread " + thread.getName() + ":\n" + sw;

        // Add latest.log as context when available. The throwable remains first so a giant
        // startup log cannot bury the terminal exception.
        String context = "";
        try {
            context = new LogFileScanner(gameDir).readLatestLogSnapshot();
        } catch (Throwable ignored) {}
        String combined = exceptionText + "\n\n--- latest.log context ---\n" + context;

        Diagnosis diagnosis = new HeuristicExplainer().explain(
                new CrashReport(gameDir.resolve("logs").resolve("latest.log"), combined, Instant.now()));
        if (diagnosis.confidence < LOG_SCAN_CONFIDENCE_THRESHOLD) return;

        String hash = sha256(exceptionText);
        ProcessedStateStore contentStore = new ProcessedStateStore(gameDir, "processed-log-content.txt");
        boolean alreadyPersisted = contentStore.load().contains(hash);

        CrashReport report = new CrashReport(
                gameDir.resolve("logs").resolve("latest.log"), combined, Instant.now());

        // IMPORTANT: deduplication is for DISK/history, not for the current live crash.
        // The same crash may happen on several test launches; each launch still deserves
        // one console epilogue. Previously we returned here when V5 had already recorded
        // the same exception hash, which made V6 appear completely silent.
        if (!alreadyPersisted) {
            BannerPrinter.writeReportFile(gameDir, report, diagnosis);
            contentStore.markProcessed(hash);
        }
        pendingReport = new PendingReport(report, diagnosis);
    }

    /** Scans crash-reports/ and rotated logs/ for anything not already processed. */
    public static void checkAndReport(Path gameDir) {
        Explainer explainer = buildExplainer(gameDir);

        checkCrashReports(gameDir, explainer);
        checkRotatedLogs(gameDir, explainer);
    }

    private static void checkCrashReports(Path gameDir, Explainer explainer) {
        ProcessedStateStore stateStore = new ProcessedStateStore(gameDir, "processed-crashreports.txt");
        Set<String> processed = stateStore.load();

        CrashReportScanner scanner = new CrashReportScanner(gameDir);
        List<Path> unprocessed = scanner.findUnprocessed(processed);

        for (Path crashFile : unprocessed) {
            try {
                String text = Files.readString(crashFile, StandardCharsets.UTF_8);
                Instant timestamp = safeLastModified(crashFile);
                CrashReport report = new CrashReport(crashFile, text, timestamp);
                Diagnosis diagnosis = explainer.explain(report);

                // A crash-reports/*.txt file only ever exists because something genuinely
                // crashed, so we always show the banner here, even a low-confidence one -
                // "we don't know exactly what broke, but something did" is still useful.
                BannerPrinter.printToConsole(report, diagnosis);
                BannerPrinter.writeReportFile(gameDir, report, diagnosis);

                stateStore.markProcessed(report.fileName);
            } catch (Throwable t) {
                logInternalError(gameDir, t);
                stateStore.markProcessed(crashFile.getFileName().toString());
            }
        }
    }

    private static void checkRotatedLogs(Path gameDir, Explainer explainer) {
        ProcessedStateStore stateStore = new ProcessedStateStore(gameDir, "processed-logs.txt");
        Set<String> processed = stateStore.load();

        LogFileScanner scanner = new LogFileScanner(gameDir);
        List<Path> unprocessed = scanner.findUnprocessedRotatedLogs(processed);
        if (unprocessed.size() > MAX_ROTATED_LOGS_PER_STARTUP) {
            // Scanner returns oldest -> newest. Mark stale history processed silently and
            // only diagnose the newest few sessions.
            int staleCount = unprocessed.size() - MAX_ROTATED_LOGS_PER_STARTUP;
            for (int i = 0; i < staleCount; i++) {
                stateStore.markProcessed(unprocessed.get(i).getFileName().toString());
            }
            unprocessed = unprocessed.subList(staleCount, unprocessed.size());
        }

        for (Path logFile : unprocessed) {
            try {
                String text = LogFileScanner.readGzippedLog(logFile);
                if (text.isBlank()) {
                    stateStore.markProcessed(logFile.getFileName().toString());
                    continue;
                }

                // A rotated log exists after normal sessions too. Never diagnose one merely
                // because it contains scary Forge startup vocabulary such as "missing mods.toml".
                if (!looksLikeFatalLog(text)) {
                    stateStore.markProcessed(logFile.getFileName().toString());
                    continue;
                }

                Instant timestamp = safeLastModified(logFile);
                CrashReport asReport = new CrashReport(logFile, text, timestamp);
                Diagnosis diagnosis = explainer.explain(asReport);

                // latest.log may already have been explained by our shutdown hook. Use a
                // content hash so the same session is not reported again after log rotation.
                ProcessedStateStore contentStore = new ProcessedStateStore(gameDir, "processed-log-content.txt");
                String contentHash = sha256(text);
                boolean alreadyReportedContent = contentStore.load().contains(contentHash);

                if (diagnosis.confidence >= LOG_SCAN_CONFIDENCE_THRESHOLD && !alreadyReportedContent) {
                    BannerPrinter.printToConsole(asReport, diagnosis);
                    BannerPrinter.writeReportFile(gameDir, asReport, diagnosis);
                    contentStore.markProcessed(contentHash);
                }

                stateStore.markProcessed(logFile.getFileName().toString());
            } catch (Throwable t) {
                logInternalError(gameDir, t);
                stateStore.markProcessed(logFile.getFileName().toString());
            }
        }
    }

    /**
     * Shutdown-time path for failures from THIS session. Unlike startup scanning, this is
     * allowed to inspect logs/latest.log because the JVM is already exiting. We only surface
     * it when a real rule matches above the log threshold, so normal exits stay silent.
     */
    private static void checkCurrentLatestLog(Path gameDir) {
        try {
            LogFileScanner scanner = new LogFileScanner(gameDir);
            String text = scanner.readLatestLogSnapshot();
            if (text.isBlank()) return;

            if (!looksLikeFatalLog(text)) return;

            Explainer explainer = new HeuristicExplainer();
            Path latest = scanner.latestLog();
            CrashReport report = new CrashReport(latest, text, safeLastModified(latest));
            Diagnosis diagnosis = explainer.explain(report);
            if (diagnosis.confidence < LOG_SCAN_CONFIDENCE_THRESHOLD) return;

            String hash = sha256(text);
            ProcessedStateStore contentStore = new ProcessedStateStore(gameDir, "processed-log-content.txt");
            if (contentStore.load().contains(hash)) return;

            BannerPrinter.printToConsole(report, diagnosis);
            BannerPrinter.writeReportFile(gameDir, report, diagnosis);
            contentStore.markProcessed(hash);
        } catch (Throwable t) {
            logInternalError(gameDir, t);
        }
    }

    private static boolean looksLikeFatalLog(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return lower.contains("caused by:")
                || lower.contains("uncaught on thread")
                || lower.contains("exception in thread")
                || lower.contains("fatal error")
                || lower.contains("mod resolution failed")
                || lower.contains("incompatible mods found")
                || lower.contains("missing or unsupported mandatory dependencies")
                || lower.contains("hard_dep_no_candidate")
                || lower.contains("mixinapplyerror")
                || lower.contains("outofmemoryerror")
                || lower.contains("stackoverflowerror");
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            // Dedupe failure must never suppress diagnosis.
            return "sha256-unavailable-" + text.length() + "-" + text.hashCode();
        }
    }

    private static Explainer buildExplainer(Path gameDir) {
        try {
            ExplainerConfig config = ExplainerConfig.load(gameDir);
            if (config.llmEnabled) {
                return new LlmExplainer(config);
            }
        } catch (Throwable ignored) {
            // fall through to heuristic
        }
        return new HeuristicExplainer();
    }

    private static Instant safeLastModified(Path p) {
        try {
            FileTime ft = Files.getLastModifiedTime(p);
            return ft.toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }

    private static void logInternalError(Path gameDir, Throwable t) {
        try {
            Path debugLog = gameDir.resolve("crashexplainer").resolve("internal-errors.log");
            Files.createDirectories(debugLog.getParent());
            String entry = Instant.now() + " " + t + System.lineSeparator();
            Files.writeString(debugLog, entry, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // truly nothing more we can safely do here
        }
    }
}
