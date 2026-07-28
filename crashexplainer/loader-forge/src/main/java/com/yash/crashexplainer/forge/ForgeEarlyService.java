package com.yash.crashexplainer.forge;

import com.yash.crashexplainer.core.CrashAnalyzer;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * The earliest hook available to a non-agent, non-native mod on Forge/NeoForge:
 * ModLauncher discovers this class via META-INF/services/cpw.mods.modlauncher.api.ITransformationService
 * and loads it via ServiceLoader before ANY mod-loading machinery exists - before mod
 * scanning, before @Mod classes, before mixins apply. Class-loading a class runs its
 * static initializer automatically, so putting our hook there (rather than in one of the
 * interface methods below) means it fires the instant ModLauncher resolves this class,
 * with no dependency on which lifecycle method ModLauncher happens to call first.
 */
public final class ForgeEarlyService implements ITransformationService {

    static {
        try {
            Path gameDir = resolveGameDir();
            System.err.println("[CrashExplainer] Early Forge service loaded. gameDir=" + gameDir.toAbsolutePath());
            CrashAnalyzer.install(gameDir);
            System.err.println("[CrashExplainer] Crash analyzer installed.");
        } catch (Throwable t) {
            // Never make the game fail to boot, but NEVER hide our own initialization failure.
            System.err.println("[CrashExplainer] FAILED TO INITIALIZE: " + t);
            t.printStackTrace(System.err);
        }
    }

    private static Path resolveGameDir() {
        // "user.dir" is the working directory the launcher started the JVM from, which
        // for vanilla, MultiMC, Prism, CurseForge, and the Modrinth app alike is the
        // instance/game directory itself (the one containing crash-reports/, mods/, etc).
        String dir = System.getProperty("user.dir");
        return dir != null ? Paths.get(dir) : Paths.get(".");
    }

    @Override
    public String name() {
        return "crashexplainer";
    }

    @Override
    public void initialize(IEnvironment environment) {
        // Intentionally empty - the work already happened above in the static initializer,
        // which runs strictly before this method could possibly be called.
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        // Intentionally empty, same reason as above.
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<ITransformer> transformers() {
        // We don't transform any classes - we're only here for the early boot hook.
        return List.of();
    }
}
