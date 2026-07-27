package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

public final class DuplicateModRule implements Rule {

    private static final Pattern[] SIGNS = {
            Pattern.compile("[Dd]uplicate mod(s)? (id|found)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Found duplicate mods", Pattern.CASE_INSENSITIVE),
            Pattern.compile("multiple mods (are trying|attempted) to (register|occupy)", Pattern.CASE_INSENSITIVE),
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

        String explanation = "The same mod appears to be loaded more than once - usually because there are two " +
                "jars for it in your mods folder (e.g. an old version you forgot to delete, or it got bundled " +
                "inside another mod/modpack jar too).";
        String fix = "Open your mods folder and look for two jars with similar names/versions for the same mod. " +
                "Delete the older/duplicate one and keep only one copy.";

        return new Diagnosis("Duplicate Mod", explanation, fix, mods, 88, "heuristic");
    }
}
