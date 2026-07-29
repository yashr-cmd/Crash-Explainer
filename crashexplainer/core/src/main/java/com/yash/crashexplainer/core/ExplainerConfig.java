package com.yash.crashexplainer.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional local config at <gamedir>/crashexplainer/config.properties.
 * Not present by default - CrashExplainer works fully offline out of the box.
 *
 * Recognized keys:
 *   llm.enabled=true
 *   llm.endpoint=https://api.groq.com/openai/v1/chat/completions   (any OpenAI-compatible /chat/completions URL)
 *   llm.model=llama-3.1-8b-instant
 *   llm.apiKey=sk-...                                              (YOUR key - never shipped, never read by anyone but you)
 */
public final class ExplainerConfig {

    public final boolean llmEnabled;
    public final String endpoint;
    public final String model;
    public final String apiKey;

    private ExplainerConfig(boolean llmEnabled, String endpoint, String model, String apiKey) {
        this.llmEnabled = llmEnabled;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
    }

    public static ExplainerConfig load(Path gameDir) {
        Path configFile = gameDir.resolve("crashexplainer").resolve("config.properties");
        Properties props = new Properties();

        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            } catch (IOException ignored) {
                // fall through to defaults - a broken config file must never break the game
            }
        }

        boolean enabled = Boolean.parseBoolean(props.getProperty("llm.enabled", "false"));
        String endpoint = props.getProperty("llm.endpoint", "https://api.groq.com/openai/v1/chat/completions");
        String model = props.getProperty("llm.model", "llama-3.1-8b-instant");
        String apiKey = props.getProperty("llm.apiKey", "");

        return new ExplainerConfig(enabled && !apiKey.isBlank(), endpoint, model, apiKey);
    }
}
