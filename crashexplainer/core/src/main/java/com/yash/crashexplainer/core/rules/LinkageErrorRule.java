package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

/**
 * NoSuchMethodError / NoSuchFieldError / IncompatibleClassChangeError - these fire when
 * code was compiled against one version of a class and runs against a different, incompatible
 * version of that same class. Classic symptom of a mod built for a different MC/API/library
 * version than what's actually installed.
 */
public final class LinkageErrorRule implements Rule {

    private static final Pattern[] SIGNS = {
            Pattern.compile("NoSuchMethodError"),
            Pattern.compile("NoSuchFieldError"),
            Pattern.compile("IncompatibleClassChangeError"),
            Pattern.compile("AbstractMethodError"),
    };

    private static final Pattern AT_LINE = Pattern.compile("at ([a-zA-Z0-9_.$]+)\\.");

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
            String snippet = CrashReportUtils.firstMatchSnippet(crashText, AT_LINE, 60);
            if (!snippet.isEmpty()) {
                mods = List.of(snippet.replace("at ", "").replace(".", ""));
            }
        }

        String explanation = "Some code is calling a method or field that doesn't exist in the version of a " +
                "class it actually got loaded at runtime. This is a strong sign that a mod was compiled against " +
                "a different Minecraft, Forge/NeoForge/Fabric API, or library version than what's installed - " +
                "the two are talking past each other.";
        String fix = "Update the mod(s) involved (and the underlying library if one is named in the stack trace) " +
                "to versions that all target the same Minecraft version. If it just updated, roll it back and " +
                "check its changelog for a matching MC version requirement.";

        return new Diagnosis("Version Mismatch (Linkage Error)", explanation, fix, mods, 82, "heuristic");
    }
}
