package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Mixin failures almost always mean a mod's Mixin targets a class/method shape
 * that doesn't match the Minecraft version actually running - i.e. the mod
 * wasn't built for this MC version, or another mod already transformed the target
 * in an incompatible way.
 */
public final class MixinApplyFailureRule implements Rule {

    private static final Pattern[] SIGNS = {
            Pattern.compile("MixinApplyError", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Mixin apply (for )?.*failed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Critical injection failure", Pattern.CASE_INSENSITIVE),
            Pattern.compile("target class .*not found", Pattern.CASE_INSENSITIVE),
            Pattern.compile("MixinTransformerError", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern MIXIN_PACKAGE = Pattern.compile("mixins?[./]([a-zA-Z0-9_]+)[./]");

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
        if (mods.isEmpty()) {
            String snippet = CrashReportUtils.firstMatchSnippet(crashText, MIXIN_PACKAGE, 40);
            if (!snippet.isEmpty()) {
                mods = List.of(snippet);
            }
        }

        String explanation = "A mod's Mixin (a technique for patching Minecraft's own code) couldn't find the " +
                "code it was trying to modify. This almost always means the mod was built for a different " +
                "Minecraft version than the one you're running, or it's clashing with another mod editing the " +
                "same class.";
        String fix = "Double-check the mod's supported Minecraft version matches yours exactly. If it does, try " +
                "temporarily removing other mods that touch similar features to find the conflicting pair.";

        return new Diagnosis("Mixin / Mod Incompatibility", explanation, fix, mods, 85, "heuristic");
    }
}
