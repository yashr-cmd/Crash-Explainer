package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches the vanilla/Forge/NeoForge/Fabric "watchdog" timeout - a separate monitor thread
 * notices the main/server thread hasn't ticked in too long (default threshold 60s) and force
 * -kills the process via Runtime.halt(), specifically BECAUSE the hung thread can't be trusted
 * to shut down cleanly on its own. halt() skips shutdown hooks, but the watchdog itself writes
 * a normal crash-reports/*.txt file (with a snapshot of what the hung thread was doing) before
 * it kills the process - so this is still fully caught by the ordinary next-launch scan, and
 * (via the uncaught-exception bridge / live latest.log check) potentially live too if the
 * watchdog's fatal log lines land in latest.log before halt() actually fires.
 */
public final class WatchdogTimeoutRule implements Rule {

    private static final Pattern SIGN = Pattern.compile(
            "(?:ServerHangWatchdog|Server Watchdog).{0,80}?tick took|A single (?:server|game) tick took",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TICK_DURATION = Pattern.compile("tick took ([0-9.]+) seconds");

    private static final Pattern VANILLA_OR_LOADER_FRAME = Pattern.compile(
            "^(net\\.minecraft\\.|net\\.minecraftforge\\.|net\\.neoforged\\.|net\\.fabricmc\\.|com\\.mojang\\.|" +
                    "cpw\\.mods\\.|java\\.|javax\\.|jdk\\.|sun\\.)"
    );
    private static final Pattern STACK_FRAME = Pattern.compile("at ([a-zA-Z0-9_.$]+)\\.[a-zA-Z0-9_$<>]+\\(");

    @Override
    public Diagnosis tryMatch(String crashText) {
        if (!SIGN.matcher(crashText).find()) {
            return null;
        }

        String duration = "";
        Matcher durMatcher = TICK_DURATION.matcher(crashText);
        if (durMatcher.find()) {
            duration = durMatcher.group(1) + "s";
        }

        List<String> likelyCulprit = guessCulprit(crashText);

        String explanation = "The server (or, in singleplayer, the integrated server) froze for too long on a " +
                "single tick" + (duration.isEmpty() ? "" : (" (" + duration + " - normally each tick should take " +
                "about 0.05s)")) + ", so the watchdog assumed it was permanently hung and force-killed the game " +
                "before it could recover. This isn't a bug in the loader itself - something overwhelmed the " +
                "server in one go: a massive spike in entity count, a huge chunk of world generation or " +
                "redstone/explosions computing at once, or a mod stuck in a genuine infinite loop.";
        String fix = "Whatever was happening right when it froze is almost always the cause - check what was on " +
                "screen or what command just ran (mass entity spawns, huge builds, big explosions, chunk-loading " +
                "spikes). If it keeps happening in the same spot, that's a performance problem to address there, " +
                "not something CrashExplainer can fix for you.";

        return new Diagnosis("Server Watchdog Timeout (Overload/Hang)", explanation, fix, likelyCulprit, 92, "heuristic");
    }

    private List<String> guessCulprit(String crashText) {
        Matcher m = STACK_FRAME.matcher(crashText);
        while (m.find()) {
            String frame = m.group(1);
            if (!VANILLA_OR_LOADER_FRAME.matcher(frame).find()) {
                return List.of(frame);
            }
        }
        return List.of();
    }
}
