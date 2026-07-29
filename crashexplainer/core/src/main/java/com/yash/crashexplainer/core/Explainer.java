package com.yash.crashexplainer.core;

/**
 * Anything that can turn a CrashReport into a Diagnosis. HeuristicExplainer (offline,
 * rule-based) is the always-available default. LlmExplainer is an optional plug-in that
 * only activates if the user configures their own API key locally - CrashExplainer never
 * ships or calls out with a bundled key.
 */
public interface Explainer {
    Diagnosis explain(CrashReport report);
}
