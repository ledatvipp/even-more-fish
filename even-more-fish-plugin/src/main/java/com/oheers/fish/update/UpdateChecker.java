package com.oheers.fish.update;

import com.oheers.fish.EvenMoreFish;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {
    
    private final EvenMoreFish plugin;

    public UpdateChecker(final EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("UnstableApiUsage")
    public String getVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.modrinth.com/v2/project/vlh7rLCf/version"))
                    .header("User-Agent", "EvenMoreFish/" + plugin.getPluginMeta().getVersion())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }

            return parseVersionResponse(response.body(), plugin.getPluginMeta().getVersion());
        } catch (Exception e) {
            plugin.getLogger().warning("Update check failed: " + e.getMessage());
            plugin.getLogger().info("Manual update check: https://modrinth.com/plugin/evenmorefish/versions");
            return plugin.getPluginMeta().getVersion(); // Fallback
        }
    }

    static String parseVersionResponse(String body, String fallback) throws Exception {
        Object parsed = new JSONParser().parse(body);
        if (!(parsed instanceof JSONArray versions)) {
            throw new IOException("Expected a version array");
        }
        if (versions.isEmpty()) {
            return fallback;
        }
        if (!(versions.getFirst() instanceof JSONObject latest)
                || !(latest.get("version_number") instanceof String version)
                || version.isBlank()) {
            throw new IOException("Missing version number");
        }
        int separator = version.lastIndexOf('-');
        // Modrinth also returns plain release/SNAPSHOT versions without a Minecraft suffix.
        if (separator > 0 && version.substring(separator + 1).matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) {
            return version.substring(0, separator);
        }
        return version;
    }

    // Checks for updates, surprisingly
    @Contract(" -> new")
    @SuppressWarnings("UnstableApiUsage")
    public @NotNull CompletableFuture<Boolean> checkUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            ComparableVersion modrinthVersion = new ComparableVersion(new UpdateChecker(plugin).getVersion());
            ComparableVersion serverVersion = new ComparableVersion(plugin.getPluginMeta().getVersion());
            return modrinthVersion.compareTo(serverVersion) > 0;
        });
    }

}
