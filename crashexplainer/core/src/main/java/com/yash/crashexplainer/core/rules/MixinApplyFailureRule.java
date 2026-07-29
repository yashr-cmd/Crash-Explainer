package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MixinApplyFailureRule implements Rule {
    private static final Pattern[] SIGNS = {
            Pattern.compile("MixinApplyError", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Mixin apply (for )?.*failed", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Critical injection failure", Pattern.CASE_INSENSITIVE),
            Pattern.compile("target class .*not found", Pattern.CASE_INSENSITIVE),
            Pattern.compile("MixinTransformerError", Pattern.CASE_INSENSITIVE),
            Pattern.compile("InvalidInjectionException", Pattern.CASE_INSENSITIVE)
    };

    // Common forms: "Mixin [badmod.mixins.json:FooMixin]" and
    // "from mod badmod". Only return an actual mod/config identifier, never a Mixin framework class.
    private static final Pattern MIXIN_CONFIG = Pattern.compile(
            "Mixin\\s*\\[([a-zA-Z0-9_-]+)(?:[._-][a-zA-Z0-9_-]+)*\\.mixins?(?:\\.[a-zA-Z0-9_-]+)?\\.json(?::[^]]+)?]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_MOD = Pattern.compile("\\bfrom mod ['\"]?([a-zA-Z0-9_-]{2,40})", Pattern.CASE_INSENSITIVE);

    @Override public Diagnosis tryMatch(String crashText) {
        boolean matched = false;
        for (Pattern p : SIGNS) if (p.matcher(crashText).find()) { matched = true; break; }
        if (!matched) return null;

        List<String> mods = CrashReportUtils.extractLikelyModIds(crashText);
        if (mods.isEmpty()) {
            ArrayList<String> inferred = new ArrayList<>();
            Matcher fm = FROM_MOD.matcher(crashText);
            if (fm.find()) inferred.add(fm.group(1));
            else {
                Matcher mc = MIXIN_CONFIG.matcher(crashText);
                if (mc.find()) inferred.add(mc.group(1));
            }
            mods = inferred;
        }

        String explanation = "A mod's Mixin (a technique for patching Minecraft's own code) couldn't find the code it was trying to modify. This usually means the mod targets a different Minecraft/mod version, or another mod changed the same code incompatibly.";
        String fix = "Check that the named mod and its dependencies exactly match your Minecraft and loader versions. If they do, test without other mods that patch the same feature to find the conflicting pair.";
        return new Diagnosis("Mixin / Mod Incompatibility", explanation, fix, mods, 85, "heuristic");
    }
}
