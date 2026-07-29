package com.yash.crashexplainer.core;

import java.util.Collections;
import java.util.List;

/**
 * The result of diagnosing a crash: what we think went wrong, how sure we are,
 * which mod(s) look responsible, and what the user should try.
 */
public final class Diagnosis {

    public final String cause;              // short title, e.g. "Missing Dependency"
    public final String explanation;        // 1-3 sentence plain-English explanation
    public final String fix;                // suggested fix, plain-English
    public final List<String> offendingMods; // best-effort, may be empty
    public final int confidence;            // 0-100
    public final String source;             // "heuristic" or "llm"

    public Diagnosis(String cause, String explanation, String fix, List<String> offendingMods, int confidence, String source) {
        this.cause = cause;
        this.explanation = explanation;
        this.fix = fix;
        this.offendingMods = offendingMods == null ? Collections.emptyList() : offendingMods;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.source = source;
    }

    public static Diagnosis unknown() {
        return new Diagnosis(
                "Unrecognized Crash",
                "CrashExplainer couldn't match this crash against any known pattern. " +
                        "It might be a rare interaction, a bug in a specific mod, or something outside our rule set.",
                "Check the full crash report in the crash-reports folder, or share it in the mod's issue tracker / Discord for a human look.",
                Collections.emptyList(),
                5,
                "heuristic"
        );
    }
}
