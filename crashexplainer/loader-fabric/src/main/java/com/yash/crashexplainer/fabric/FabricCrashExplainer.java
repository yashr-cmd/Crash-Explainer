package com.yash.crashexplainer.fabric;

import com.yash.crashexplainer.core.CrashAnalyzer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.file.Path;

/**
 * preLaunch is the earliest entrypoint Fabric Loader exposes to mods - it runs before
 * mod initializers, before mixins apply, right after Fabric Loader itself has finished
 * discovering mods. As with the Forge/NeoForge shim, the actual hook lives in the static
 * initializer so it fires the instant this class is loaded, not on whatever schedule
 * Fabric happens to call onPreLaunch().
 */
public final class FabricCrashExplainer implements PreLaunchEntrypoint {

    static {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            CrashAnalyzer.install(gameDir);
        } catch (Throwable ignored) {
            // CrashExplainer must never be the reason the game fails to boot.
        }
    }

    @Override
    public void onPreLaunch() {
        // Intentionally empty - the work already happened above in the static initializer.
    }
}
