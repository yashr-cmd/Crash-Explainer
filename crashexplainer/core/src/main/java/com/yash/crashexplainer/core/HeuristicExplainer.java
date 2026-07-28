package com.yash.crashexplainer.core;

/**
 * Default, always-on Explainer. Zero setup, zero network access, works fully offline.
 */
public final class HeuristicExplainer implements Explainer {

    private final DiagnosticEngine engine = new DiagnosticEngine();

    @Override
    public Diagnosis explain(CrashReport report) {
        return engine.diagnose(report.text);
    }
}
