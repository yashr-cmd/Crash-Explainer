package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Catches Forge/NeoForge "missing or unsupported mandatory dependencies" and
 * Fabric "requires ... which is missing" style failures.
 */
public final class MissingDependencyRule implements Rule {

    private static final Pattern[] SIGNS = {
            Pattern.compile("missing or unsupported mandatory dependencies", Pattern.CASE_INSENSITIVE),
            Pattern.compile("requires\\s+\\S+\\s*@?[^\\n]{0,40}which is missing", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:^|\\R)\\s*(?:\\[[^]]+])?\\s*(?:ERROR[: ]+)?Missing Mods(?:\\s*:|\\R|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("is missing dependencies", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Incompatible mods found", Pattern.CASE_INSENSITIVE),
            Pattern.compile("requires version [^\\n]{0,60} of mod [^\\n]{0,60}but only the wrong version is present", Pattern.CASE_INSENSITIVE),
            Pattern.compile("HARD_DEP_NO_CANDIDATE"),
            Pattern.compile("Mod resolution failed", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public Diagnosis tryMatch(String crashText) {
        boolean matched = false;
        for (Pattern p : SIGNS) {
            if (p.matcher(crashText).find()) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return null;
        }

        List<String> mods = CrashReportUtils.extractLikelyModIds(crashText);

        String explanation = "One or more installed mods depend on another mod (or a specific version of one) " +
                "that isn't present in your mods folder, or is present but too old/new.";
        String fix = "Check the mod page(s) for the required dependency version and install it. " +
                "If you already have it installed, update it to the version the crash report asks for.";

        return new Diagnosis("Missing or Incompatible Dependency", explanation, fix, mods, 90, "heuristic");
    }
}
