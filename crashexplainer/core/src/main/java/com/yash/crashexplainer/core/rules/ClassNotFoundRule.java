package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

public final class ClassNotFoundRule implements Rule {

    private static final Pattern[] SIGNS = {
            Pattern.compile("ClassNotFoundException"),
            Pattern.compile("NoClassDefFoundError"),
    };

    private static final Pattern CLASS_NAME = Pattern.compile("(?:ClassNotFoundException|NoClassDefFoundError):\\s*([a-zA-Z0-9_.$/]+)");

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
        String missingClass = CrashReportUtils.firstMatchSnippet(crashText, CLASS_NAME, 80)
                .replaceFirst("^(ClassNotFoundException|NoClassDefFoundError):\\s*", "");

        String explanation = "Code tried to load a Java class that isn't present at runtime. Usually this means " +
                "a required library/API mod is missing, wasn't packaged correctly, or you're running the wrong " +
                "loader (Forge jar on Fabric, etc.) for a mod that expects the other one." +
                (missingClass.isEmpty() ? "" : (" Missing class: " + missingClass));
        String fix = "If the missing class belongs to a library mod (common ones: Cloth Config, Architectury API, " +
                "GeckoLib, Fabric/Forge API), install that library at the version the crashing mod expects. " +
                "Also double check you downloaded the Forge/NeoForge/Fabric build that matches your loader.";

        return new Diagnosis("Missing Class (likely missing library/API mod)", explanation, fix, mods, 78, "heuristic");
    }
}
