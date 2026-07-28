package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Pattern;

public final class StackOverflowRule implements Rule {

    private static final Pattern SIGN = Pattern.compile("StackOverflowError");
    private static final Pattern AT_LINE = Pattern.compile("at ([a-zA-Z0-9_.$]+)\\.");

    @Override
    public Diagnosis tryMatch(String crashText) {
        if (!SIGN.matcher(crashText).find()) {
            return null;
        }

        List<String> mods = CrashReportUtils.extractLikelyModIds(crashText);
        if (mods.isEmpty()) {
            String snippet = CrashReportUtils.firstMatchSnippet(crashText, AT_LINE, 60);
            if (!snippet.isEmpty()) {
                mods = List.of(snippet.replace("at ", "").replace(".", ""));
            }
        }

        String explanation = "Something recursed infinitely (or nearly so) until it blew the call stack. This is " +
                "almost always a bug in a specific mod's code - often two mods calling back into each other's " +
                "event handlers, or a mixin re-triggering the method it's attached to.";
        String fix = "Look at the repeating class names near the top of the stack trace in the full crash report - " +
                "that's very likely the mod at fault. Report it to that mod's issue tracker with the crash report " +
                "attached.";

        return new Diagnosis("Infinite Recursion (Stack Overflow)", explanation, fix, mods, 80, "heuristic");
    }
}
