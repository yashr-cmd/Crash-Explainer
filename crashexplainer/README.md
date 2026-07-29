# CrashExplainer

A tiny cross-loader Minecraft library mod that watches for crash reports and dumps a
plain-English, boxed banner explaining what probably went wrong and how to fix it -
missing dependencies, duplicate mods, mixin/version incompatibilities, out-of-memory,
and more.

## How it works

- **`core`** - pure Java, zero Forge/NeoForge/Fabric/Minecraft imports. Contains the
  crash-report scanner, the rule-based diagnostic engine, the banner renderer, and the
  optional LLM hook. Because it has no loader dependencies at all, it's automatically
  compatible with every Minecraft version - there's nothing version-specific to break.
- **`loader-forge`** - a single class, `ForgeEarlyService`, registered as a
  `cpw.mods.modlauncher.api.ITransformationService` via `META-INF/services`. ModLauncher
  discovers and class-loads it before mod scanning, before `@Mod` classes, before mixins
  apply - a `static {}` block in that class is about as early as a non-agent, non-native
  hook can run. Covers both Forge and NeoForge, since NeoForge kept the same ModLauncher
  lineage after forking from Forge.
- **`loader-fabric`** - a single class, `FabricCrashExplainer`, registered as a
  `preLaunch` entrypoint in `fabric.mod.json` - Fabric Loader's earliest mod hook. Same
  static-block trick.

### Why it can still "see" a crash it technically missed

Every actual crash's evidence ends up written to `crash-reports/*.txt` regardless of
whether CrashExplainer was loaded in time to watch it happen live. So instead of racing
to attach mid-crash, CrashExplainer just scans that folder on every boot for anything it
hasn't reported on yet (tracked in `crashexplainer/processed.txt`) - including crashes
from a session where it never got the chance to load at all.

On top of that, each shim also registers a JVM shutdown hook at static-init time, so for
crashes that exit the JVM cleanly enough for shutdown hooks to run, you'll see the banner
printed to console/log in the same session, right as it happens.

*(Shutdown hooks don't run on a hard native/JVM-fatal crash - `hs_err_pidNNNN.log`
territory. Those still get caught on the next launch via the same scan.)*

## Output

- Printed to console/log as a boxed banner.
- Also written to `crashexplainer/reports/<crash-file-name>-explained.txt` so you have a
  copy even if you don't catch the console output in time.

## The "AI" part

Default mode is a fully offline, rule-based heuristic engine (`HeuristicExplainer`) -
zero setup, zero network calls, matches on patterns like missing-dependency messages,
Mixin apply failures, `NoSuchMethodError`/version mismatches, duplicate mod IDs,
`ClassNotFoundException`, OOM, and stack overflows.

If you want an actual LLM pass instead, drop a `crashexplainer/config.properties` file
in your instance directory with your **own** API key for any OpenAI-compatible
`/chat/completions` endpoint (Groq's free tier is a good fit):

```properties
llm.enabled=true
llm.endpoint=https://api.groq.com/openai/v1/chat/completions
llm.model=llama-3.1-8b-instant
llm.apiKey=sk-...
```

No key is ever bundled with the mod, nothing calls out anywhere unless you configure
this yourself, and any failure (bad key, no internet, endpoint down) silently falls back
to the heuristic engine rather than breaking anything.

## Building

```
./gradlew buildAll
```

Jars land in each subproject's `build/libs/`. Drop `loader-forge` or `loader-fabric`'s
jar into the relevant `mods/` folder depending on which loader you're running (Forge and
NeoForge both use the `loader-forge` jar).

## Known limitations (v1)

- Pre-1.13 Forge (the old FML/`IFMLLoadingPlugin` "coremod" system, not ModLauncher)
  isn't covered - `ITransformationService` didn't exist yet on those versions. Everything
  1.13+ Forge and all NeoForge versions are covered by the one `loader-forge` jar.
- Mod-id extraction from crash text is best-effort regex, not a strict parser - Minecraft
  crash report formatting varies enough between loaders/versions that a few crashes will
  show up with no mod name attached, or (rarely) the wrong one. The `cause` and `fix`
  text should still be useful even then.
- The LLM path is opt-in and BYO-key by design - there's no bundled/free fallback that
  calls out anywhere without you setting it up.

## Extending the rule set

Add a new class implementing `com.yash.crashexplainer.core.Rule` under `core/.../rules/`,
then register it in `DiagnosticEngine.registerDefaultRules()`. Each rule just needs to
return a `Diagnosis` (or `null` if it doesn't recognize the crash) - the engine keeps
whichever registered rule reports the highest confidence.

## Current-session log crash detection
CrashExplainer now inspects a snapshot of `logs/latest.log` from its JVM shutdown hook. This catches early-loader / ModLauncher failures that never create `crash-reports/*.txt`. Previous-session `*.log.gz` scanning remains as a fallback when the shutdown hook cannot run. Content hashes prevent the same session from being reported again after `latest.log` rotates.
