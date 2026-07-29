package com.yash.crashexplainer.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders a Diagnosis as a boxed, unmissable banner - to any Consumer<String> line-sink
 * (so loader shims can route it through their own logger AND to stdout/stderr), and to
 * a plain-text report file next to the crash report.
 */
public final class BannerPrinter {

    private static final int WIDTH = 76; // inner width, not counting border chars
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BannerPrinter() {}

    public static void printBanner(CrashReport report, Diagnosis diagnosis, Consumer<String> lineSink) {
        for (String line : buildBanner(report, diagnosis)) {
            lineSink.accept(line);
        }
    }

    public static void printToConsole(CrashReport report, Diagnosis diagnosis) {
        List<String> lines = buildBanner(report, diagnosis);
        StringBuilder sb = new StringBuilder("\n");
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        System.err.print(sb);
        System.err.flush();
    }

    public static void writeReportFile(Path gameDir, CrashReport report, Diagnosis diagnosis) {
        try {
            Path outDir = gameDir.resolve("crashexplainer").resolve("reports");
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve(report.fileName + "-explained.txt");

            StringBuilder sb = new StringBuilder();
            for (String line : buildBanner(report, diagnosis)) {
                sb.append(line).append(System.lineSeparator());
            }
            Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // reporting must never itself throw and break the launch
        }
    }

    private static List<String> buildBanner(CrashReport report, Diagnosis diagnosis) {
        List<String> lines = new ArrayList<>();

        String top = "\u2554" + "\u2550".repeat(WIDTH) + "\u2557";
        String bottom = "\u255A" + "\u2550".repeat(WIDTH) + "\u255D";
        String divider = "\u2560" + "\u2550".repeat(WIDTH) + "\u2563";

        lines.add(top);
        lines.addAll(centered("CRASHEXPLAINER REPORT"));
        lines.add(divider);

        lines.addAll(field("Source", report.fileName));
        lines.addAll(field("Analyzed", report.timestamp.atZone(ZoneId.systemDefault()).format(TIME_FMT)));
        lines.addAll(field("Cause", diagnosis.cause + "  (" + diagnosis.confidence + "% confidence, " + diagnosis.source + ")"));

        if (!diagnosis.offendingMods.isEmpty()) {
            lines.addAll(field("Likely mod(s)", String.join(", ", diagnosis.offendingMods)));
        }

        lines.add(divider);
        lines.addAll(wrapped("What happened:", diagnosis.explanation));
        lines.add(emptyRow());
        lines.addAll(wrapped("How to fix it:", diagnosis.fix));

        lines.add(bottom);

        return lines;
    }

    private static List<String> centered(String text) {
        List<String> out = new ArrayList<>();
        int pad = Math.max(0, (WIDTH - text.length()) / 2);
        String content = " ".repeat(pad) + text;
        content = content + " ".repeat(Math.max(0, WIDTH - content.length()));
        out.add("\u2551" + content + "\u2551");
        return out;
    }

    private static List<String> field(String label, String value) {
        return wrapped(label + ":", value);
    }

    private static String emptyRow() {
        return "\u2551" + " ".repeat(WIDTH) + "\u2551";
    }

    /** Wraps "label value"-style text to fit inside the box, indenting continuation lines under the label. */
    private static List<String> wrapped(String label, String value) {
        List<String> out = new ArrayList<>();
        String indent = " ".repeat(label.length() + 2);

        List<String> chunks = wrapText(value, WIDTH - 1 - label.length() - 1);
        boolean first = true;
        for (String chunk : chunks) {
            String row = first ? (" " + label + " " + chunk) : (indent + chunk);
            first = false;
            row = row.length() > WIDTH ? row.substring(0, WIDTH) : row + " ".repeat(WIDTH - row.length());
            out.add("\u2551" + row + "\u2551");
        }
        return out;
    }

    private static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() > 0 && current.length() + 1 + word.length() > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }
}
