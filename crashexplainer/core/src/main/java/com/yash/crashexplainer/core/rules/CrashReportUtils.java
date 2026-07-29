package com.yash.crashexplainer.core.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort text-mining helpers shared across rules. Minecraft crash report format
 * varies a lot between loaders/versions, so these are intentionally forgiving regexes
 * rather than a strict grammar. Never throws.
 */
public final class CrashReportUtils {

    private CrashReportUtils() {}

    private static final Pattern SUSPECTED_MOD =
            Pattern.compile("Suspected Mod:\\s*([^\\r\\n(]+)");

    private static final Pattern MOD_FILE_TOKEN =
            Pattern.compile("(?:Mod File|Mod ID|modid|mod id)[:\\s]+['\"]?([a-zA-Z][a-zA-Z0-9_.-]{1,40})['\"]?");

    // Fabric Loader's own dependency-resolution error format: Mod 'Display Name' (modid) ...
    // Captures the modid (the part in parentheses), which is far more reliable than the
    // display name for matching against actual jar/mod folder names.
    private static final Pattern FABRIC_QUOTED_MOD =
            Pattern.compile("'[^']+'\\s*\\(([a-zA-Z0-9_-]{2,40})\\)");

    private static final Pattern LOOSE_MODID_NEAR_KEYWORD = Pattern.compile(
            "\\b([a-z][a-z0-9_]{2,30})\\b(?=[^\\n]{0,60}?\\b(missing|required|requires|duplicate|incompatible|conflict)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    // words that show up constantly and are never actually a mod id
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "with", "this", "that", "mods", "mod", "minecraft", "forge", "neoforge",
            "fabric", "loader", "version", "file", "error", "java", "net", "com", "org", "info",
            "warn", "debug", "main", "thread", "class", "method", "caused", "exception"
    );

    public static List<String> extractLikelyModIds(String text) {
        Set<String> found = new LinkedHashSet<>();

        Matcher m1 = SUSPECTED_MOD.matcher(text);
        while (m1.find()) {
            found.add(m1.group(1).trim());
        }

        Matcher mFabric = FABRIC_QUOTED_MOD.matcher(text);
        while (mFabric.find()) {
            found.add(mFabric.group(1).trim());
        }

        Matcher m2 = MOD_FILE_TOKEN.matcher(text);
        while (m2.find()) {
            String candidate = m2.group(1).trim();
            if (!STOPWORDS.contains(candidate.toLowerCase())) {
                found.add(candidate);
            }
        }

        if (found.isEmpty()) {
            Matcher m3 = LOOSE_MODID_NEAR_KEYWORD.matcher(text);
            int matches = 0;
            while (m3.find() && matches < 5) {
                String candidate = m3.group(1);
                if (!STOPWORDS.contains(candidate.toLowerCase()) && candidate.length() > 3) {
                    found.add(candidate);
                    matches++;
                }
            }
        }

        return new ArrayList<>(found);
    }

    public static boolean containsAny(String text, String... needles) {
        String lower = text.toLowerCase();
        for (String n : needles) {
            if (lower.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** Grabs a short snippet around the first match of a pattern, for the report's "evidence" line. */
    public static String firstMatchSnippet(String text, Pattern pattern, int maxLen) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return "";
        }
        String snippet = m.group().trim().replaceAll("\\s+", " ");
        if (snippet.length() > maxLen) {
            snippet = snippet.substring(0, maxLen) + "...";
        }
        return snippet;
    }
}
