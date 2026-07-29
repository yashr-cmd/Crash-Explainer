package com.yash.crashexplainer.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Optional Explainer that sends the crash text to a user-configured, OpenAI-compatible
 * /chat/completions endpoint (Groq's free tier, OpenAI, a local Ollama server, etc.) and
 * asks for a plain-English explanation. Falls back to a HeuristicExplainer diagnosis if
 * the call fails for any reason - this must never be the thing that breaks someone's game.
 *
 * Relies on Gson, which every modern Forge/NeoForge/Fabric runtime already ships on the
 * classpath, so this stays dependency-free at the mod-jar level (see core/build.gradle -
 * gson is compileOnly, provided by the game at runtime).
 */
public final class LlmExplainer implements Explainer {

    private static final String SYSTEM_PROMPT =
            "You are a Minecraft crash report analyst. Given a crash report, respond with EXACTLY three lines, " +
                    "no markdown, no extra commentary:\n" +
                    "CAUSE: <short title, 5 words max>\n" +
                    "EXPLANATION: <1-3 plain-English sentences a non-programmer can follow>\n" +
                    "FIX: <1-2 concrete, actionable sentences>";

    private final HeuristicExplainer fallback = new HeuristicExplainer();
    private final ExplainerConfig config;
    private final HttpClient client;

    public LlmExplainer(ExplainerConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    @Override
    public Diagnosis explain(CrashReport report) {
        if (!config.llmEnabled) {
            return fallback.explain(report);
        }

        try {
            String truncated = report.text.length() > 12000
                    ? report.text.substring(0, 12000) + "\n...[truncated]"
                    : report.text;

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", "Crash report:\n" + truncated);

            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", SYSTEM_PROMPT);

            JsonArray messages = new JsonArray();
            messages.add(sysMsg);
            messages.add(userMsg);

            JsonObject body = new JsonObject();
            body.addProperty("model", config.model);
            body.add("messages", messages);
            body.addProperty("temperature", 0.2);
            body.addProperty("max_tokens", 300);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return fallback.explain(report);
            }

            String content = JsonParser.parseString(response.body())
                    .getAsJsonObject()
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            return parseResponse(content);

        } catch (Throwable t) {
            // Network hiccup, bad key, endpoint down, whatever - never let this take the game with it.
            return fallback.explain(report);
        }
    }

    private Diagnosis parseResponse(String content) {
        String cause = "AI Diagnosis";
        String explanation = content.trim();
        String fix = "See explanation above.";
        List<String> mods = Collections.emptyList();

        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "CAUSE:", 0, 6)) {
                cause = trimmed.substring(6).trim();
            } else if (trimmed.regionMatches(true, 0, "EXPLANATION:", 0, 12)) {
                explanation = trimmed.substring(12).trim();
            } else if (trimmed.regionMatches(true, 0, "FIX:", 0, 4)) {
                fix = trimmed.substring(4).trim();
            }
        }

        return new Diagnosis(cause, explanation, fix, mods, 70, "llm");
    }
}
