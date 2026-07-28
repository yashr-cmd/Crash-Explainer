package com.yash.crashexplainer.core.rules;

import com.yash.crashexplainer.core.Diagnosis;
import com.yash.crashexplainer.core.Rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes crashes while ModLauncher is gathering/initializing transformation services. */
public final class ModLauncherTransformationServiceRule implements Rule {

    private static final Pattern SERVICE_LAYER = Pattern.compile("layer=([^\\r\\n]+?)\\s+modules=\\[([^]]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_MOD = Pattern.compile("Found mod file\\s+([^\\r\\n]+?\\.jar)", Pattern.CASE_INSENSITIVE);

    @Override
    public Diagnosis tryMatch(String crashText) {
        boolean inTransformInit = CrashReportUtils.containsAny(crashText,
                "TransformationServiceDecorator.gatherTransformers",
                "TransformationServicesHandler.initialiseServiceTransformers");
        if (!inTransformInit) return null;

        boolean nullTargets = CrashReportUtils.containsAny(crashText,
                "because \"targets\" is null",
                "Cannot invoke \"java.util.Set.isEmpty()\"");

        Set<String> suspects = new LinkedHashSet<>();

        Matcher layer = SERVICE_LAYER.matcher(crashText);
        while (layer.find()) {
            String[] tokens = layer.group(2).split(",");
            // Small service layers are the useful ones. The giant bootstrap layer contains
            // dozens of Minecraft/Forge libraries and would drown out the actual suspects.
            if (tokens.length <= 8) {
                for (String token : tokens) {
                    String s = token.trim();
                    if (!s.isEmpty() && !isInfrastructureModule(s)) suspects.add(s);
                }
            }
        }

        Matcher mods = FOUND_MOD.matcher(crashText);
        while (mods.find() && suspects.size() < 8) {
            String jar = mods.group(1).trim();
            if (!jar.contains("forge-") && !jar.contains("fmlcore-") && !jar.contains("language-") && !jar.startsWith("client-")) {
                suspects.add(jar);
            }
        }

        List<String> likely = new ArrayList<>(suspects);
        if (likely.size() > 6) likely = likely.subList(0, 6);

        String explanation = nullTargets
                ? "ModLauncher crashed while gathering early transformation-service transformers because a service supplied or left a null transformer-target set. This happens before normal mod loading and strongly points to an incompatible or broken early transformer/coremod interaction. Earlier warnings in the log may be secondary if execution continued past them."
                : "ModLauncher crashed while initializing early transformation services, before normal mod loading completed. This usually points to a broken transformation service/coremod or an incompatibility between multiple early bytecode transformers.";

        String fix = "Test the suspected transformation-service/coremod mods one at a time, then together, to identify the conflicting pair. Update or remove the incompatible transformer. If one mod intentionally patches another mod's transformers, disable that optional patching layer first and retry.";

        return new Diagnosis("ModLauncher Transformation-Service Conflict", explanation, fix, likely,
                nullTargets ? 96 : 90, "heuristic");
    }

    private static boolean isInfrastructureModule(String s) {
        String x = s.toLowerCase();
        return x.startsWith("loader.") || x.startsWith("cpw.") || x.equals("minecraftforge") || x.equals("fmlcore") || x.startsWith("net.minecraftforge")
                || x.startsWith("org.") || x.startsWith("com.google") || x.startsWith("io.netty")
                || x.startsWith("java.") || x.startsWith("jdk.");
    }
}
