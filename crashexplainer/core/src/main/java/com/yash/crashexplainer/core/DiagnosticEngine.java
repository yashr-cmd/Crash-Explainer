package com.yash.crashexplainer.core;

import com.yash.crashexplainer.core.rules.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Runs registered rules and returns the highest-confidence diagnosis. */
public final class DiagnosticEngine {
    private final List<Rule> rules = new ArrayList<>();

    // Log chatter is useful to humans but dangerous to keyword heuristics: documentation,
    // compatibility warnings, or a recovered old exception must not become the diagnosis.
    private static final Pattern NON_FATAL_LOG_LINE = Pattern.compile(
            "^\\s*(?:\\[[^]]+\\]\\s*)*(?:\\[[^]]+/(?:INFO|WARN|DEBUG|TRACE)\\]|\\[(?:INFO|WARN|DEBUG|TRACE)\\]|(?:INFO|WARN|DEBUG|TRACE)[: ])",
            Pattern.CASE_INSENSITIVE);

    public DiagnosticEngine() { registerDefaultRules(); }

    private void registerDefaultRules() {
        rules.add(new OutOfMemoryRule());
        rules.add(new ModLauncherTransformationServiceRule());
        rules.add(new WatchdogTimeoutRule());
        rules.add(new NativeCrashRule());
        rules.add(new MissingDependencyRule());
        rules.add(new DuplicateModRule());
        rules.add(new MixinApplyFailureRule());
        rules.add(new LinkageErrorRule());
        rules.add(new ClassNotFoundRule());
        rules.add(new StackOverflowRule());
    }

    public void addRule(Rule rule) { rules.add(rule); }

    public Diagnosis diagnose(String crashText) {
        if (crashText == null || crashText.isBlank()) return Diagnosis.unknown();

        String diagnosticText = removeNonFatalLogChatter(crashText);
        Diagnosis best = null;
        for (Rule rule : rules) {
            Diagnosis d;
            try { d = rule.tryMatch(diagnosticText); }
            catch (Throwable ignored) { continue; }
            if (d != null && (best == null || d.confidence > best.confidence)) best = d;
        }
        return best != null ? best : Diagnosis.unknown();
    }

    static String removeNonFatalLogChatter(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\\R", -1)) {
            if (!NON_FATAL_LOG_LINE.matcher(line).find()) out.append(line).append('\n');
        }
        return out.toString();
    }
}
