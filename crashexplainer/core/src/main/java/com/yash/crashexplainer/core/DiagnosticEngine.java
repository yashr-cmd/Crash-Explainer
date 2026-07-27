package com.yash.crashexplainer.core;

import com.yash.crashexplainer.core.rules.ClassNotFoundRule;
import com.yash.crashexplainer.core.rules.DuplicateModRule;
import com.yash.crashexplainer.core.rules.LinkageErrorRule;
import com.yash.crashexplainer.core.rules.MissingDependencyRule;
import com.yash.crashexplainer.core.rules.MixinApplyFailureRule;
import com.yash.crashexplainer.core.rules.ModLauncherTransformationServiceRule;
import com.yash.crashexplainer.core.rules.OutOfMemoryRule;
import com.yash.crashexplainer.core.rules.StackOverflowRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every registered Rule against a crash report's text and returns the
 * highest-confidence Diagnosis. Never throws - a broken rule is skipped, not fatal.
 */
public final class DiagnosticEngine {

    private final List<Rule> rules = new ArrayList<>();

    public DiagnosticEngine() {
        // Order doesn't matter much since we pick highest confidence, but roughly
        // most-specific-first keeps things readable.
        registerDefaultRules();
    }

    private void registerDefaultRules() {
        rules.add(new OutOfMemoryRule());
        rules.add(new ModLauncherTransformationServiceRule());
        rules.add(new MissingDependencyRule());
        rules.add(new DuplicateModRule());
        rules.add(new MixinApplyFailureRule());
        rules.add(new LinkageErrorRule());
        rules.add(new ClassNotFoundRule());
        rules.add(new StackOverflowRule());
    }

    public void addRule(Rule rule) {
        rules.add(rule);
    }

    public Diagnosis diagnose(String crashText) {
        if (crashText == null || crashText.isBlank()) {
            return Diagnosis.unknown();
        }

        Diagnosis best = null;
        for (Rule rule : rules) {
            Diagnosis d;
            try {
                d = rule.tryMatch(crashText);
            } catch (Throwable t) {
                // A rule should never take the whole analysis down with it.
                continue;
            }
            if (d != null && (best == null || d.confidence > best.confidence)) {
                best = d;
            }
        }

        return best != null ? best : Diagnosis.unknown();
    }
}
