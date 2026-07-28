package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches the JVM's own hs_err_pid<pid>.log crash-handler output - written when the JVM
 * itself dies (segfault, bad native library, bad native-agent injection), not a normal Java
 * exception. Only ever fed hs_err text by HsErrScanner, never crash-reports/log text, so this
 * is safe to register globally without risk of false-positiving on ordinary Java crashes.
 */
public final class NativeCrashRule implements Rule {

    private static final Pattern HEADER = Pattern.compile(
            "A fatal error has been detected by the Java Runtime Environment", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROBLEMATIC_FRAME =
            Pattern.compile("^#\\s*(?:Problematic frame:)?\\s*\\r?\\n#\\s*[A-Za-z]\\s+\\[([^+\\]]+)", Pattern.MULTILINE);

    // Fallback: the "C  [somelib.dll+0x1234]" style line on its own, in case the two-line
    // "Problematic frame:" header format above doesn't match this JVM/OS's exact layout.
    private static final Pattern C_FRAME_LINE =
            Pattern.compile("^#\\s*[CV]\\s+\\[([^+\\]]+)", Pattern.MULTILINE);

    private static final Pattern SIGNAL = Pattern.compile("SIGSEGV|SIGBUS|SIGILL|EXCEPTION_ACCESS_VIOLATION");

    @Override
    public Diagnosis tryMatch(String crashText) {
        if (!HEADER.matcher(crashText).find()) {
            return null;
        }

        String culpritModule = extractProblematicModule(crashText);
        String signal = extractSignal(crashText);

        List<String> mods = new ArrayList<>();
        boolean looksLikeGameCode = culpritModule != null &&
                (culpritModule.toLowerCase().contains("jvm.dll") || culpritModule.toLowerCase().contains("libjvm"));

        String explanation;
        String fix;

        if (culpritModule != null && !looksLikeGameCode) {
            mods.add(culpritModule);
            explanation = "The Java Virtual Machine itself crashed" +
                    (signal.isEmpty() ? "" : (" (" + signal + ")")) +
                    " - not a normal Minecraft/mod exception. The crash points at a specific native " +
                    "(non-Java) module: " + culpritModule + ". This is typically a graphics driver, a native " +
                    "library a mod shells out to, or in rarer cases a native agent/injector attached to the " +
                    "game process.";
            fix = "Update the driver/library named above first (GPU drivers are the most common cause here - " +
                    "check for a newer NVIDIA/AMD/Intel driver). If it's a native library bundled with a specific " +
                    "mod, that mod is the one to check for updates or report the crash to. If you knowingly have " +
                    "a third-party tool injected into the Minecraft process (overlay software, mod managers with " +
                    "native hooks, etc.), try running without it once to rule it out.";
        } else {
            explanation = "The Java Virtual Machine itself crashed" +
                    (signal.isEmpty() ? "" : (" (" + signal + ")")) +
                    ", inside the JVM's own native code rather than in Minecraft/mod Java code. This is less " +
                    "common and harder to pin on a specific mod from the crash data alone.";
            fix = "Check the full hs_err_pid log (saved next to this report) for the 'Problematic frame' section " +
                    "and any loaded native libraries listed near it. Updating your JVM/Java version and your " +
                    "graphics driver are the most common fixes for this category.";
        }

        return new Diagnosis("Native/JVM-Level Crash", explanation, fix, mods, 88, "heuristic");
    }

    private String extractProblematicModule(String text) {
        Matcher m1 = PROBLEMATIC_FRAME.matcher(text);
        if (m1.find()) {
            return m1.group(1).trim();
        }
        Matcher m2 = C_FRAME_LINE.matcher(text);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        return null;
    }

    private String extractSignal(String text) {
        Matcher m = SIGNAL.matcher(text);
        return m.find() ? m.group() : "";
    }
}
