package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.Collections;
import java.util.regex.Pattern;

public final class OutOfMemoryRule implements Rule {

    private static final Pattern SIGN = Pattern.compile("OutOfMemoryError|Java heap space|GC overhead limit exceeded");

    @Override
    public Diagnosis tryMatch(String crashText) {
        if (!SIGN.matcher(crashText).find()) {
            return null;
        }

        String explanation = "The JVM ran out of allocated memory. This is common with large modpacks, big " +
                "render distances, resource-heavy texture packs/shaders, or a memory leak in a specific mod.";
        String fix = "Increase the max memory allocation in your launcher profile (e.g. -Xmx4G or higher, depending " +
                "on how much RAM your PC has free). If you're already allocating a lot and it still happens, it's " +
                "worth checking whether one specific mod's memory usage keeps climbing over a session (a leak) " +
                "by disabling mods in batches.";

        return new Diagnosis("Out of Memory", explanation, fix, Collections.emptyList(), 95, "heuristic");
    }
}
