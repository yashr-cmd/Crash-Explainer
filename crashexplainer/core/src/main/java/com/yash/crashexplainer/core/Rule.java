package com.yash.crashexplainer.core;

/**
 * A single pattern-matcher in the heuristic diagnostic engine.
 * Implementations should be fast, allocation-light, and NEVER throw -
 * DiagnosticEngine will still guard with try/catch, but don't rely on it.
 */
public interface Rule {

    /**
     * @param crashText full text of the crash report
     * @return a Diagnosis if this rule recognizes the crash, otherwise null
     */
    Diagnosis tryMatch(String crashText);
}
