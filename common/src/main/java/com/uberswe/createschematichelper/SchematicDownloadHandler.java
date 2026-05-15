package com.uberswe.createschematichelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public class SchematicDownloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String SHARED_SECRET = "5a0841453e5c2588583da1fb215f4af88a5a7d4ee86a720aea4ae27c4065dace";

    private static final Pattern SHORT_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,6}$");

    @Nullable
    public static String downloadSchematic(String input) {
        String trimmed = input.trim();

        if (SHORT_CODE_PATTERN.matcher(trimmed).matches()) {
            return downloadSchematicBySlug(trimmed.toUpperCase(Locale.ROOT));
        }

        try {
            URL url = new URI(trimmed).toURL();
            String host = url.getHost();
            String baseHost = URI.create(ConfigValues.baseUrl).getHost();
            if (host.equals(baseHost)) {
                String slug = trimmed;
                String path = url.getPath();
                if (path.startsWith("/schematics/")) {
                    slug = path.substring("/schematics/".length());
                }
                return downloadSchematicBySlug(slug);
            }
            return downloadDirectNbt(url);
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException ignored) {
        }

        return downloadSchematicBySlug(trimmed);
    }

    @Nullable
    private static String downloadDirectNbt(URL url) {
        LOGGER.info("Downloading schematic directly from {}", url);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url.toURI())
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                LOGGER.error("Failed to download from {}: HTTP {}", url, response.statusCode());
                return null;
            }

            String name = extractFilename(url.getPath());
            Minecraft minecraft = Minecraft.getInstance();
            return writeSchematicFile(minecraft, name, response.body());
        } catch (Exception e) {
            LOGGER.error("Error downloading schematic from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static String extractFilename(String urlPath) {
        String segment = urlPath.substring(urlPath.lastIndexOf('/') + 1);
        if (segment.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            segment = segment.substring(0, segment.length() - 4);
        }
        segment = segment.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return segment.isEmpty() ? "download" : segment;
    }

    @Nullable
    private static String downloadSchematicBySlug(String slug) {
        Minecraft minecraft = Minecraft.getInstance();
        long timestamp = System.currentTimeMillis() / 1000;

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ConfigValues.baseUrl + "/api/mod/download"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(minecraft, slug, timestamp).toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                handleRequestError(response);
            }

            byte[] xorKey = deriveXorKey(timestamp);
            byte[] nbtBytes = xorDecode(response.body(), xorKey);
            return writeSchematicFile(minecraft, slug, nbtBytes);
        } catch (Exception e) {
            LOGGER.error("Error downloading schematic: {}", e.getMessage());
            return null;
        }
    }

    private static JsonObject buildRequestBody(Minecraft minecraft, String slug, long timestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        String username = minecraft.player != null ? minecraft.player.getGameProfile().getName() : "Unknown";
        String message = timestamp + ":" + ConfigValues.modVersion + ":" + username + ":" + slug;

        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("signature", hmacSha256(message));
        body.addProperty("type", "schematic");
        return body;
    }

    private static void handleRequestError(HttpResponse<byte[]> response) throws Exception {
        String errorBody = new String(response.body(), StandardCharsets.UTF_8);
        String errorMsg;

        try {
            JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
            errorMsg = errorJson.has("error") ? errorJson.get("error").getAsString() : errorBody;
        } catch (Exception e) {
            errorMsg = "HTTP " + response.statusCode();
        }

        throw new Exception("Error downloading schematic: " + errorMsg);
    }

    private static String writeSchematicFile(Minecraft minecraft, String slug, byte[] data) throws IOException {
        Path schematicsPath = minecraft.gameDirectory.toPath().resolve("schematics");
        Files.createDirectories(schematicsPath);
        String fileName = slug + ".nbt";
        Path outputFile = schematicsPath.resolve(fileName);
        int counter = 1;

        while (Files.exists(outputFile)) {
            fileName = slug + "_" + counter + ".nbt";
            outputFile = schematicsPath.resolve(fileName);
            counter++;
        }

        Files.write(outputFile, data);
        LOGGER.info("Saved schematic to {}", outputFile);
        return fileName;
    }

    static String hmacSha256(String message) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SHARED_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] deriveXorKey(long timestamp) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(SHARED_SECRET.getBytes(StandardCharsets.UTF_8));
        md.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }

    private static byte[] xorDecode(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return result;
    }
}
